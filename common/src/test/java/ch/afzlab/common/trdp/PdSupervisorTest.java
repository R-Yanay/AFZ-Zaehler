package ch.afzlab.common.trdp;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;

import ch.afzlab.common.trdp.PdSupervisor.Verdict;
import org.junit.jupiter.api.Test;

class PdSupervisorTest {

    private static final Instant T0 = Instant.parse("2026-11-26T10:00:00Z");

    private final PdSupervisor sup = new PdSupervisor(Duration.ofMillis(300));

    private static Instant at(long ms) {
        return T0.plusMillis(ms);
    }

    @Test
    void erkenntLueckenUndDuplikate() {
        assertThat(sup.onTelegram(10, at(0))).isEqualTo(Verdict.FIRST);
        assertThat(sup.onTelegram(11, at(100))).isEqualTo(Verdict.OK);
        assertThat(sup.onTelegram(14, at(200))).isEqualTo(Verdict.GAP);
        assertThat(sup.onTelegram(14, at(300))).isEqualTo(Verdict.STALE);
        assertThat(sup.onTelegram(12, at(400))).isEqualTo(Verdict.STALE);

        assertThat(sup.lost()).isEqualTo(2);
        assertThat(sup.stale()).isEqualTo(2);
        assertThat(sup.lastSeq()).isEqualTo(14);
    }

    @Test
    void ueberlaufDerSequenznummerIstKeinFehler() {
        sup.onTelegram(0xFFFF_FFFFL, at(0));
        assertThat(sup.onTelegram(0, at(100))).isEqualTo(Verdict.OK);
    }

    @Test
    void timeoutWirdGenauEinmalGemeldet() {
        sup.onTelegram(1, at(0));
        assertThat(sup.checkTimeout(at(300))).isFalse();
        assertThat(sup.isValid()).isTrue();

        assertThat(sup.checkTimeout(at(301))).isTrue();
        assertThat(sup.checkTimeout(at(500))).isFalse();
        assertThat(sup.isValid()).isFalse();

        // nach dem Ausfall beginnt die Zaehlung neu, auch wenn der Sender neu gestartet hat
        assertThat(sup.onTelegram(1, at(600))).isEqualTo(Verdict.FIRST);
        assertThat(sup.isValid()).isTrue();
        assertThat(sup.timeouts()).isEqualTo(1);
    }
}
