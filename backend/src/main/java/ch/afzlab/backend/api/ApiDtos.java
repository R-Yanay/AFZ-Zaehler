package ch.afzlab.backend.api;

import java.time.Instant;

import ch.afzlab.backend.ingest.RejectReason;
import ch.afzlab.backend.persistence.CountRecord;
import ch.afzlab.backend.persistence.RejectedMessage;
import ch.afzlab.backend.persistence.VehicleTotals;

/** Antwortformate der REST-API (entkoppelt von den JPA-Entities). */
final class ApiDtos {

    private ApiDtos() {
    }

    record CountDto(int doorId, int boarding, int alighting, Instant measuredAt, Instant receivedAt) {
        static CountDto of(CountRecord r) {
            return new CountDto(r.getDoorId(), r.getBoarding(), r.getAlighting(), r.getMeasuredAt(), r.getReceivedAt());
        }
    }

    /**
     * Besetzung = Einsteiger - Aussteiger.
     * {@code plausible} ist false bei negativer Besetzung (z. B. Zaehlfehler oder Fahrt begann nicht leer).
     */
    record OccupancyDto(String vehicleId, long boarding, long alighting, long occupancy, boolean plausible,
                        long messages, Instant lastMeasuredAt) {
        static OccupancyDto of(VehicleTotals t) {
            long occupancy = t.boarding() - t.alighting();
            return new OccupancyDto(t.vehicleId(), t.boarding(), t.alighting(), occupancy, occupancy >= 0,
                    t.messages(), t.lastMeasuredAt());
        }
    }

    record RejectedDto(long id, String topic, String payload, RejectReason reason, String detail, Instant receivedAt) {
        static RejectedDto of(RejectedMessage m) {
            return new RejectedDto(m.getId(), m.getTopic(), m.getPayload(), m.getReason(), m.getDetail(),
                    m.getReceivedAt());
        }
    }
}
