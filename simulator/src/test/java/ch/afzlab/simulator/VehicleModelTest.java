package ch.afzlab.simulator;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import ch.afzlab.common.CountEvent;
import ch.afzlab.simulator.VehicleModel.Phase;
import ch.afzlab.simulator.VehicleModel.Tick;
import org.junit.jupiter.api.Test;

class VehicleModelTest {

    private static final Duration DT = Duration.ofMillis(100);

    private final SimConfig config = SimConfig.parse("--doors=2", "--stops=4", "--capacity=20",
            "--drive=2s", "--dwell=1s", "--seed=42");

    /** Laesst das Modell laufen und sammelt alle Ticks. */
    private List<Tick> run(VehicleModel model, Duration total) {
        List<Tick> ticks = new ArrayList<>();
        Instant now = Instant.parse("2026-11-26T10:00:00Z");
        for (long t = 0; t < total.toMillis(); t += DT.toMillis()) {
            now = now.plus(DT);
            ticks.add(model.tick(DT, now));
        }
        return ticks;
    }

    @Test
    void tuerenSindNurImStillstandOffen() {
        for (Tick t : run(new VehicleModel(config, new Random(1)), Duration.ofMinutes(2))) {
            if (t.status().anyOpen()) {
                assertThat(t.phase()).isEqualTo(Phase.TUEREN_OFFEN);
                assertThat(t.status().speedKmh10()).isZero();
            }
            if (t.phase() == Phase.FAHRT) {
                assertThat(t.status().doorMask()).isZero();
            }
        }
    }

    @Test
    void phasenFolgenAufeinander() {
        List<Phase> sequence = run(new VehicleModel(config, new Random(1)), Duration.ofSeconds(12)).stream()
                .filter(Tick::phaseChanged).map(Tick::phase).toList();

        assertThat(sequence).startsWith(Phase.HALT_ANKUNFT, Phase.TUEREN_OFFEN, Phase.HALT_ABFAHRT, Phase.FAHRT,
                Phase.HALT_ANKUNFT);
    }

    @Test
    void zaehlwerteKommenBeimSchliessenProTuer() {
        List<Tick> withCounts = run(new VehicleModel(config, new Random(1)), Duration.ofSeconds(6)).stream()
                .filter(t -> !t.counts().isEmpty()).toList();

        assertThat(withCounts).hasSize(1);
        assertThat(withCounts.getFirst().phase()).isEqualTo(Phase.HALT_ABFAHRT);
        assertThat(withCounts.getFirst().counts()).extracting(CountEvent::doorId).containsExactly(1, 2);
    }

    @Test
    void besetzungBleibtZwischenNullUndKapazitaetUndEndhaltestelleLeert() {
        VehicleModel model = new VehicleModel(config, new Random(7));
        int occupancy = 0;
        for (Tick t : run(model, Duration.ofMinutes(10))) {
            for (CountEvent c : t.counts()) {
                occupancy += c.boarding() - c.alighting();
            }
            assertThat(occupancy).isBetween(0, config.capacity());
            if (!t.counts().isEmpty() && t.status().stopId() == config.lineStops()) {
                assertThat(occupancy).as("nach der Endhaltestelle leer").isZero();
                assertThat(t.counts()).allSatisfy(c -> assertThat(c.boarding()).isZero());
            }
        }
        assertThat(occupancy).isEqualTo(model.occupancy());
    }

    @Test
    void haltestellenLaufenImKreis() {
        List<Integer> stops = run(new VehicleModel(config, new Random(1)), Duration.ofSeconds(30)).stream()
                .filter(t -> t.phaseChanged() && t.phase() == Phase.HALT_ANKUNFT)
                .map(t -> t.status().stopId()).toList();

        assertThat(stops).startsWith(1, 2, 3, 4, 1);
    }
}
