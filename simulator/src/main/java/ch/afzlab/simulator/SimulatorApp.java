package ch.afzlab.simulator;

import java.io.IOException;
import java.time.Instant;
import java.util.Random;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.random.RandomGenerator;

import ch.afzlab.common.CountEvent;
import ch.afzlab.common.trdp.DoorStatus;
import org.eclipse.paho.client.mqttv3.MqttException;

/**
 * Simuliert ein Fahrzeug: Fahrzeugsteuerung sendet den Tuerstatus zyklisch per UDP (TRDP-aehnlich),
 * die Zaehlsensoren melden beim Schliessen der Tueren ihre Zaehlwerte per MQTT.
 * Beenden mit Ctrl+C.
 */
public final class SimulatorApp {

    private SimulatorApp() {
    }

    public static void main(String[] args) throws Exception {
        SimConfig config;
        try {
            config = SimConfig.parse(args);
        } catch (IllegalArgumentException e) {
            System.err.println(e.getMessage());
            System.err.println(SimConfig.USAGE);
            System.exit(2);
            return;
        }

        RandomGenerator random = config.seed() == 0 ? new Random() : new Random(config.seed());
        VehicleModel model = new VehicleModel(config, random);
        DoorStatusSender sender = new DoorStatusSender(config.udpHost(), config.udpPort(), config.dropRate(), random);
        CountPublisher publisher = config.mqttEnabled() ? createPublisher(config) : null;

        Log.info("Simulator " + config.vehicleId() + ": " + config.doors() + " Tueren, " + config.lineStops()
                + " Haltestellen, Tuerstatus alle " + config.cycle().toMillis() + " ms an " + config.udpHost() + ":"
                + config.udpPort() + (publisher != null ? ", Zaehlwerte an " + config.mqttUrl() : ", MQTT aus"));

        ScheduledExecutorService cycle = Executors.newSingleThreadScheduledExecutor(r -> new Thread(r, "pd-cycle"));
        cycle.scheduleAtFixedRate(() -> step(config, model, sender, publisher),
                0, config.cycle().toMillis(), TimeUnit.MILLISECONDS);

        CountDownLatch stopped = new CountDownLatch(1);
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            cycle.shutdownNow();
            sender.close();
            if (publisher != null) {
                publisher.close();
            }
            Log.info("Beendet. Telegramme gesendet: " + sender.sent() + ", absichtlich verworfen: "
                    + sender.dropped());
            stopped.countDown();
        }));
        stopped.await();
    }

    private static CountPublisher createPublisher(SimConfig config) throws MqttException {
        return new CountPublisher(config.mqttUrl(), config.vehicleId());
    }

    private static void step(SimConfig config, VehicleModel model, DoorStatusSender sender, CountPublisher publisher) {
        try {
            VehicleModel.Tick tick = model.tick(config.cycle(), Instant.now());
            sender.send(tick.status());
            if (tick.phaseChanged()) {
                logPhase(tick, model);
            }
            if (!tick.counts().isEmpty() && publisher != null) {
                publisher.publish(tick.counts());
            }
        } catch (IOException e) {
            Log.warn("UDP-Senden fehlgeschlagen: " + e.getMessage());
        } catch (RuntimeException e) {
            // Eine Exception wuerde den Takt still beenden, deshalb abfangen und melden
            Log.warn("Fehler im Zyklus: " + e);
        }
    }

    private static void logPhase(VehicleModel.Tick tick, VehicleModel model) {
        DoorStatus s = tick.status();
        switch (tick.phase()) {
            case HALT_ANKUNFT -> Log.info("Haltestelle " + s.stopId() + " erreicht");
            case TUEREN_OFFEN -> Log.info("Tueren offen");
            case HALT_ABFAHRT -> {
                StringBuilder sb = new StringBuilder("Tueren zu:");
                for (CountEvent c : tick.counts()) {
                    sb.append(" Tuer ").append(c.doorId()).append(" +").append(c.boarding())
                            .append("/-").append(c.alighting()).append(';');
                }
                Log.info(sb + " Besetzung " + model.occupancy());
            }
            case FAHRT -> Log.info("Abfahrt");
            default -> {
            }
        }
    }
}
