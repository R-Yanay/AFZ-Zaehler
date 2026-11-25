package ch.afzlab.backend.mqtt;

import java.nio.charset.StandardCharsets;

import ch.afzlab.backend.ingest.CountIngestService;
import jakarta.annotation.PreDestroy;
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.MqttCallbackExtended;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Haelt eine dauerhafte MQTT-Verbindung zum Broker und gibt Zaehlmeldungen an den {@link CountIngestService}.
 *
 * <p>Wichtige Entscheidungen (siehe docs/analysen/):
 * <ul>
 *   <li>Eine Verbindung fuer alle Meldungen statt einer pro Meldung (Overhead, Analyse 01).</li>
 *   <li>Abo mit QoS 1, sonst stuft der Broker auf QoS 0 herab (Analyse 02).</li>
 *   <li>Clean Session = false: Der Broker puffert QoS-1-Meldungen, solange das Backend offline ist.</li>
 *   <li>Das PUBACK geht erst raus, wenn {@link #messageArrived} fertig ist, also nach dem Speichern.
 *       Wirft die Verarbeitung eine Exception, trennt Paho die Verbindung und der Broker stellt erneut zu.</li>
 * </ul>
 */
@Component
@ConditionalOnProperty(prefix = "afz.mqtt", name = "enabled", havingValue = "true", matchIfMissing = true)
public class MqttCountSubscriber implements MqttCallbackExtended {

    private static final Logger LOG = LoggerFactory.getLogger(MqttCountSubscriber.class);

    private final MqttProperties props;
    private final CountIngestService ingestService;
    private final MqttClient client;
    private boolean offlineLogged;

    public MqttCountSubscriber(MqttProperties props, CountIngestService ingestService) throws MqttException {
        this.props = props;
        this.ingestService = ingestService;
        this.client = new MqttClient(props.url(), props.clientId(), new MemoryPersistence());
        this.client.setCallback(this);
    }

    /**
     * Verbindet beim Start und nach Abbruechen erneut.
     * Paho-Autoreconnect bleibt aus, damit nur ein Mechanismus aktiv ist.
     */
    @Scheduled(initialDelay = 0, fixedDelayString = "${afz.mqtt.reconnect-delay:5s}")
    public synchronized void ensureConnected() {
        if (client.isConnected()) {
            return;
        }
        MqttConnectOptions options = new MqttConnectOptions();
        options.setCleanSession(false);
        options.setKeepAliveInterval((int) props.keepAlive().toSeconds());
        options.setAutomaticReconnect(false);
        try {
            client.connect(options);
            // Abo hier und nicht in connectComplete: blockierende Aufrufe im Paho-Callback-Thread koennen haengen
            client.subscribe(props.topic(), props.qos());
            offlineLogged = false;
            LOG.info("MQTT verbunden mit {}, Abo '{}' mit QoS {}", props.url(), props.topic(), props.qos());
        } catch (MqttException e) {
            if (!offlineLogged) {
                LOG.warn("MQTT-Broker {} nicht erreichbar ({}), neuer Versuch alle {}",
                        props.url(), e.getMessage(), props.reconnectDelay());
                offlineLogged = true;
            }
            disconnectQuietly();
        }
    }

    @Override
    public void connectComplete(boolean reconnect, String serverUri) {
        // Logging und Abo erfolgen in ensureConnected()
    }

    @Override
    public void connectionLost(Throwable cause) {
        LOG.warn("MQTT-Verbindung verloren: {}", cause.getMessage());
    }

    @Override
    public void messageArrived(String topic, MqttMessage message) {
        String payload = new String(message.getPayload(), StandardCharsets.UTF_8);
        try {
            ingestService.ingest(topic, payload);
        } catch (RuntimeException e) {
            // z. B. Datenbank nicht erreichbar: kein PUBACK, Broker stellt nach Reconnect erneut zu
            LOG.error("Verarbeitung fehlgeschlagen, Meldung wird erneut zugestellt: {}", topic, e);
            throw e;
        }
    }

    private void disconnectQuietly() {
        try {
            if (client.isConnected()) {
                client.disconnect();
            }
        } catch (MqttException ignored) {
            // Verbindung ist ohnehin unbrauchbar
        }
    }

    @Override
    public void deliveryComplete(IMqttDeliveryToken token) {
        // Das Backend publiziert nichts
    }

    @PreDestroy
    public synchronized void shutdown() {
        try {
            if (client.isConnected()) {
                client.disconnect();
            }
            client.close();
        } catch (MqttException e) {
            LOG.warn("MQTT sauber trennen fehlgeschlagen: {}", e.getMessage());
        }
    }
}
