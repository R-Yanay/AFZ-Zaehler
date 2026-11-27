package ch.afzlab.simulator;

import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import ch.afzlab.common.CountEvent;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;

/**
 * Zaehlsensoren: publiziert Zaehlwerte per MQTT mit QoS 1 ueber eine dauerhafte Verbindung.
 * Laeuft in einem eigenen Thread, damit ein langsamer Broker den Sendetakt des Tuerstatus nicht stoert.
 * Ist der Broker weg, werden Meldungen gepuffert (wie ein Sensor mit Speicher) und spaeter nachgesendet.
 */
public class CountPublisher implements AutoCloseable {

    static final int QOS = 1;
    static final int MAX_BUFFER = 1000;

    private final MqttClient client;
    private final MqttConnectOptions options = new MqttConnectOptions();
    private final ExecutorService worker = Executors.newSingleThreadExecutor(r -> new Thread(r, "mqtt-publisher"));
    private final Deque<CountEvent> buffer = new ArrayDeque<>();
    private boolean offlineLogged;

    public CountPublisher(String url, String vehicleId) throws MqttException {
        this.client = new MqttClient(url, "sim-" + vehicleId, new MemoryPersistence());
        options.setCleanSession(true);
        options.setKeepAliveInterval(60);
        options.setConnectionTimeout(5);
    }

    /** Nimmt die Zaehlwerte entgegen und sendet sie im Hintergrund. */
    public void publish(Iterable<CountEvent> events) {
        worker.execute(() -> {
            for (CountEvent e : events) {
                if (buffer.size() >= MAX_BUFFER) {
                    Log.warn("MQTT-Puffer voll, aelteste Meldung verworfen");
                    buffer.pollFirst();
                }
                buffer.addLast(e);
            }
            flush();
        });
    }

    private void flush() {
        if (!ensureConnected()) {
            return;
        }
        while (!buffer.isEmpty()) {
            CountEvent e = buffer.peekFirst();
            try {
                MqttMessage msg = new MqttMessage(toJson(e).getBytes(StandardCharsets.UTF_8));
                msg.setQos(QOS);
                client.publish(topic(e), msg);   // blockiert bis PUBACK
                buffer.pollFirst();
            } catch (MqttException ex) {
                Log.warn("MQTT-Senden fehlgeschlagen (" + ex.getMessage() + "), " + buffer.size()
                        + " Meldung(en) gepuffert");
                return;
            }
        }
    }

    private boolean ensureConnected() {
        if (client.isConnected()) {
            return true;
        }
        try {
            client.connect(options);
            Log.info("MQTT verbunden mit " + client.getServerURI());
            offlineLogged = false;
            return true;
        } catch (MqttException e) {
            if (!offlineLogged) {
                Log.warn("MQTT-Broker " + client.getServerURI() + " nicht erreichbar, Zaehlwerte werden gepuffert");
                offlineLogged = true;
            }
            return false;
        }
    }

    static String topic(CountEvent e) {
        return "afz/vehicle/" + e.vehicleId() + "/door/" + e.doorId() + "/count";
    }

    /** Gleiches Format wie scripts/sensor-sim.sh, damit das Backend beide versteht. */
    static String toJson(CountEvent e) {
        return "{\"vehicleId\":\"%s\",\"doorId\":%d,\"boarding\":%d,\"alighting\":%d,\"timestamp\":\"%s\"}"
                .formatted(e.vehicleId(), e.doorId(), e.boarding(), e.alighting(), e.timestamp());
    }

    @Override
    public void close() {
        worker.shutdown();
        try {
            if (!worker.awaitTermination(5, TimeUnit.SECONDS)) {
                Log.warn("Nicht alle Zaehlwerte gesendet");
            }
            if (client.isConnected()) {
                client.disconnect();
            }
            client.close();
        } catch (MqttException | InterruptedException e) {
            Log.warn("MQTT sauber beenden fehlgeschlagen: " + e.getMessage());
        }
    }
}
