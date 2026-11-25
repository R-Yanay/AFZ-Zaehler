package ch.afzlab.backend.ingest;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import ch.afzlab.common.CountEvent;
import org.junit.jupiter.api.Test;

class PlausibilityCheckerTest {

    private static final Instant NOW = Instant.parse("2026-11-25T12:00:00Z");

    private final PlausibilityChecker checker = new PlausibilityChecker(
            new PlausibilityProperties(50, Duration.ofMinutes(1), Duration.ofHours(24)),
            Clock.fixed(NOW, ZoneOffset.UTC));

    private static CountEvent event(int boarding, int alighting, Instant ts) {
        return new CountEvent("bus-101", 1, boarding, alighting, ts);
    }

    @Test
    void normaleMeldungIstPlausibel() {
        assertThat(checker.check(event(3, 2, NOW.minusSeconds(5)))).isEmpty();
    }

    @Test
    void grenzwerteSindNochPlausibel() {
        assertThat(checker.check(event(50, 50, NOW.plusSeconds(60)))).isEmpty();
        assertThat(checker.check(event(0, 0, NOW.minus(Duration.ofHours(24))))).isEmpty();
    }

    @Test
    void zuVielePersonenSindUnplausibel() {
        assertThat(checker.check(event(51, 0, NOW))).singleElement().asString().contains("Einsteiger");
        assertThat(checker.check(event(0, 99, NOW))).singleElement().asString().contains("Aussteiger");
    }

    @Test
    void zeitstempelInDerZukunftIstUnplausibel() {
        assertThat(checker.check(event(1, 1, NOW.plusSeconds(61)))).singleElement().asString().contains("Zukunft");
    }

    @Test
    void veralteteMeldungIstUnplausibel() {
        assertThat(checker.check(event(1, 1, NOW.minus(Duration.ofHours(25))))).singleElement().asString()
                .contains("aelter");
    }

    @Test
    void meldetAlleVerstoesseGleichzeitig() {
        assertThat(checker.check(event(60, 70, NOW.plus(Duration.ofHours(1))))).hasSize(3);
    }
}
