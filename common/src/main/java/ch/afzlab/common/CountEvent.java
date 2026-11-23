package ch.afzlab.common;

import java.time.Instant;

/**
 * Ein Zaehlereignis eines Sensors an einer Tuer.
 *
 * @param vehicleId  Fahrzeugnummer
 * @param doorId     Tuernummer
 * @param boarding   Anzahl Einsteiger
 * @param alighting  Anzahl Aussteiger
 * @param timestamp  Zeitpunkt der Messung
 */
public record CountEvent(String vehicleId, int doorId, int boarding, int alighting, Instant timestamp) {
    public CountEvent {
        if (boarding < 0 || alighting < 0) {
            throw new IllegalArgumentException("Zaehlwerte duerfen nicht negativ sein");
        }
    }
}
