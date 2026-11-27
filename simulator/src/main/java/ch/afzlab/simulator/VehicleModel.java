package ch.afzlab.simulator;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.random.RandomGenerator;

import ch.afzlab.common.CountEvent;
import ch.afzlab.common.trdp.DoorStatus;

/**
 * Fahrzeug als Zustandsautomat, rein rechnerisch (kein Netzwerk, keine echte Zeit) und damit testbar.
 *
 * <pre>
 *   FAHRT ──► HALT (Tueren zu) ──► TUEREN_OFFEN ──► HALT (Tueren zu) ──► FAHRT ...
 * </pre>
 * Beim Schliessen der Tueren meldet jeder Zaehlsensor die Summe seiner Ein- und Aussteiger.
 * Es steigen nie mehr Personen aus, als im Fahrzeug sind; an der Endhaltestelle steigen alle aus.
 */
public class VehicleModel {

    /** Phase des Fahrzeugs. */
    public enum Phase { FAHRT, HALT_ANKUNFT, TUEREN_OFFEN, HALT_ABFAHRT }

    /** Ergebnis eines Zeitschritts. */
    public record Tick(DoorStatus status, Phase phase, boolean phaseChanged, List<CountEvent> counts) {
    }

    static final Duration STANDSTILL = Duration.ofSeconds(1);
    static final int MAX_SPEED_KMH10 = 500;
    static final int MAX_BOARDING_PER_DOOR = 8;

    private final SimConfig config;
    private final RandomGenerator random;

    private Phase phase = Phase.FAHRT;
    private Duration inPhase = Duration.ZERO;
    private int stopId = 0;          // 0 = Depot, danach 1..lineStops
    private int occupancy = 0;
    private int[] plannedBoarding;
    private int[] plannedAlighting;

    public VehicleModel(SimConfig config, RandomGenerator random) {
        this.config = config;
        this.random = random;
    }

    /** Rueckt die Simulation um {@code dt} weiter. */
    public Tick tick(Duration dt, Instant now) {
        inPhase = inPhase.plus(dt);
        Phase before = phase;
        List<CountEvent> counts = List.of();

        switch (phase) {
            case FAHRT -> {
                if (passed(config.drive())) {
                    stopId = stopId % config.lineStops() + 1;
                    enter(Phase.HALT_ANKUNFT);
                }
            }
            case HALT_ANKUNFT -> {
                if (passed(STANDSTILL)) {
                    planPassengers();
                    enter(Phase.TUEREN_OFFEN);
                }
            }
            case TUEREN_OFFEN -> {
                if (passed(config.dwell())) {
                    counts = closeDoors(now);
                    enter(Phase.HALT_ABFAHRT);
                }
            }
            case HALT_ABFAHRT -> {
                if (passed(STANDSTILL)) {
                    enter(Phase.FAHRT);
                }
            }
            default -> throw new IllegalStateException("Unbekannte Phase " + phase);
        }

        int doorMask = phase == Phase.TUEREN_OFFEN ? (1 << config.doors()) - 1 : 0;
        DoorStatus status = new DoorStatus(config.vehicleId(), stopId, speed(), config.doors(), doorMask, now);
        return new Tick(status, phase, phase != before, counts);
    }

    private boolean passed(Duration duration) {
        return inPhase.compareTo(duration) >= 0;
    }

    private void enter(Phase next) {
        phase = next;
        inPhase = Duration.ZERO;
    }

    /** Trapezprofil: 25 % beschleunigen, 50 % konstant, 25 % bremsen. */
    private int speed() {
        if (phase != Phase.FAHRT) {
            return 0;
        }
        double t = Math.min(1.0, (double) inPhase.toMillis() / config.drive().toMillis());
        double factor = t < 0.25 ? t / 0.25 : t > 0.75 ? (1 - t) / 0.25 : 1.0;
        return (int) Math.round(MAX_SPEED_KMH10 * factor);
    }

    private void planPassengers() {
        int doors = config.doors();
        plannedBoarding = new int[doors];
        plannedAlighting = new int[doors];
        boolean terminus = stopId == config.lineStops();

        int alighting = terminus ? occupancy : (int) Math.round(occupancy * random.nextDouble(0, 0.5));
        int free = config.capacity() - (occupancy - alighting);
        int boarding = terminus ? 0 : Math.min(free, random.nextInt(0, MAX_BOARDING_PER_DOOR * doors + 1));

        distribute(alighting, plannedAlighting);
        distribute(boarding, plannedBoarding);
    }

    private void distribute(int total, int[] perDoor) {
        for (int i = 0; i < total; i++) {
            perDoor[random.nextInt(perDoor.length)]++;
        }
    }

    private List<CountEvent> closeDoors(Instant now) {
        List<CountEvent> counts = new ArrayList<>();
        for (int d = 0; d < config.doors(); d++) {
            occupancy += plannedBoarding[d] - plannedAlighting[d];
            counts.add(new CountEvent(config.vehicleId(), d + 1, plannedBoarding[d], plannedAlighting[d], now));
        }
        return counts;
    }

    public int occupancy() {
        return occupancy;
    }

    public int stopId() {
        return stopId;
    }
}
