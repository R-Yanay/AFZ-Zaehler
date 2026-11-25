package ch.afzlab.backend.ingest;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Grenzwerte fuer die Plausibilitaetspruefung (application.yaml: {@code afz.plausibility.*}).
 *
 * @param maxPersonsPerDoor  maximale Ein- bzw. Aussteiger pro Tuer und Meldung
 * @param maxFutureSkew      wie weit ein Zeitstempel in der Zukunft liegen darf (Uhrabweichung)
 * @param maxAge             wie alt eine Meldung hoechstens sein darf
 */
@ConfigurationProperties("afz.plausibility")
public record PlausibilityProperties(
        @DefaultValue("50") int maxPersonsPerDoor,
        @DefaultValue("1m") Duration maxFutureSkew,
        @DefaultValue("24h") Duration maxAge) {
}
