package ch.afzlab.backend.persistence;

import java.time.Instant;

/** Aufsummierte Zaehlwerte eines Fahrzeugs (Ergebnis einer JPQL-Abfrage). */
public record VehicleTotals(String vehicleId, long boarding, long alighting, long messages, Instant lastMeasuredAt) {
}
