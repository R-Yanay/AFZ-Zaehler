package ch.afzlab.backend.mqtt;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * MQTT-Verbindung des Backends (application.yaml: {@code afz.mqtt.*}).
 *
 * @param enabled        false schaltet den MQTT-Empfang ab (z. B. in Tests)
 * @param url            Broker-Adresse
 * @param clientId       feste Client-ID, damit der Broker die Sitzung wiedererkennt
 * @param topic          Abo mit Wildcards
 * @param qos            1 = mindestens einmal (siehe docs/analysen/02-mqtt-qos.md)
 * @param keepAlive      Keep-Alive-Intervall
 * @param reconnectDelay Wartezeit zwischen Verbindungsversuchen
 */
@ConfigurationProperties("afz.mqtt")
public record MqttProperties(
        @DefaultValue("true") boolean enabled,
        @DefaultValue("tcp://localhost:1883") String url,
        @DefaultValue("afz-backend") String clientId,
        @DefaultValue("afz/vehicle/+/door/+/count") String topic,
        @DefaultValue("1") int qos,
        @DefaultValue("60s") Duration keepAlive,
        @DefaultValue("5s") Duration reconnectDelay) {
}
