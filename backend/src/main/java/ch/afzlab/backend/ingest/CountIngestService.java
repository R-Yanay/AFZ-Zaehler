package ch.afzlab.backend.ingest;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

import ch.afzlab.backend.persistence.CountRecord;
import ch.afzlab.backend.persistence.CountRecordRepository;
import ch.afzlab.backend.persistence.RejectedMessage;
import ch.afzlab.backend.persistence.RejectedMessageRepository;
import ch.afzlab.common.CountEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Verarbeitet eine eingehende Meldung: parsen, pruefen, Duplikat erkennen, speichern.
 * Ungueltige Meldungen werden nicht verworfen, sondern mit Grund als {@link RejectedMessage} abgelegt.
 */
@Service
public class CountIngestService {

    private static final Logger LOG = LoggerFactory.getLogger(CountIngestService.class);

    private final CountMessageParser parser;
    private final PlausibilityChecker checker;
    private final CountRecordRepository counts;
    private final RejectedMessageRepository rejected;
    private final Clock clock;

    public CountIngestService(CountMessageParser parser, PlausibilityChecker checker,
                              CountRecordRepository counts, RejectedMessageRepository rejected, Clock clock) {
        this.parser = parser;
        this.checker = checker;
        this.counts = counts;
        this.rejected = rejected;
        this.clock = clock;
    }

    /** Ergebnis der Verarbeitung; {@code reason} ist null, wenn die Meldung akzeptiert wurde. */
    public record Result(RejectReason reason, String detail) {
        public boolean accepted() {
            return reason == null;
        }
    }

    @Transactional
    public Result ingest(String topic, String payload) {
        Instant now = clock.instant();
        CountEvent event;
        try {
            event = parser.parse(topic, payload);
        } catch (InvalidMessageException e) {
            return reject(topic, payload, e.reason(), e.getMessage(), now);
        }

        List<String> violations = checker.check(event);
        if (!violations.isEmpty()) {
            return reject(topic, payload, RejectReason.UNPLAUSIBEL, String.join("; ", violations), now);
        }

        if (counts.existsByVehicleIdAndDoorIdAndMeasuredAt(event.vehicleId(), event.doorId(), event.timestamp())) {
            String detail = "Bereits gespeichert: " + event.vehicleId() + "/" + event.doorId()
                    + " @ " + event.timestamp();
            return reject(topic, payload, RejectReason.DUPLIKAT, detail, now);
        }

        counts.save(new CountRecord(event.vehicleId(), event.doorId(), event.boarding(), event.alighting(),
                event.timestamp(), now));
        LOG.debug("Akzeptiert: {}", event);
        return new Result(null, null);
    }

    private Result reject(String topic, String payload, RejectReason reason, String detail, Instant now) {
        rejected.save(new RejectedMessage(topic, payload, reason, detail, now));
        LOG.warn("Abgelehnt ({}): {} [{}]", reason, detail, topic);
        return new Result(reason, detail);
    }
}
