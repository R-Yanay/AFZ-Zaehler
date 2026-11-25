package ch.afzlab.backend.ingest;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import ch.afzlab.common.CountEvent;
import org.springframework.stereotype.Component;

/** Fachliche Pruefung eines formal gueltigen Zaehlwerts. */
@Component
public class PlausibilityChecker {

    private final PlausibilityProperties limits;
    private final Clock clock;

    public PlausibilityChecker(PlausibilityProperties limits, Clock clock) {
        this.limits = limits;
        this.clock = clock;
    }

    /** Liefert alle Verstoesse; eine leere Liste heisst plausibel. */
    public List<String> check(CountEvent event) {
        List<String> violations = new ArrayList<>();
        int max = limits.maxPersonsPerDoor();
        if (event.boarding() > max) {
            violations.add("Einsteiger " + event.boarding() + " > Maximum " + max);
        }
        if (event.alighting() > max) {
            violations.add("Aussteiger " + event.alighting() + " > Maximum " + max);
        }

        Instant now = clock.instant();
        if (event.timestamp().isAfter(now.plus(limits.maxFutureSkew()))) {
            violations.add("Zeitstempel " + event.timestamp() + " liegt in der Zukunft");
        }
        if (event.timestamp().isBefore(now.minus(limits.maxAge()))) {
            violations.add("Zeitstempel " + event.timestamp() + " ist aelter als " + limits.maxAge());
        }
        return violations;
    }
}
