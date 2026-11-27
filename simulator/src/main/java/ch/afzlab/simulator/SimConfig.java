package ch.afzlab.simulator;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import ch.afzlab.common.trdp.PdTelegram;

/**
 * Einstellungen des Simulators, per Kommandozeile {@code --name=wert}.
 *
 * @param vehicleId    Fahrzeug
 * @param doors        Anzahl Tueren (1..8)
 * @param lineStops    Haltestellen pro Linie; an der letzten steigen alle aus
 * @param capacity     maximale Fahrgaeste
 * @param drive        Fahrzeit zwischen zwei Haltestellen
 * @param dwell        Zeit mit offenen Tueren
 * @param cycle        Sendetakt des Tuerstatus
 * @param udpHost      Empfaenger des Tuerstatus
 * @param udpPort      Port (TRDP-PD: 17224)
 * @param mqttUrl      Broker fuer die Zaehlwerte; "none" schaltet MQTT ab
 * @param dropRate     Anteil absichtlich nicht gesendeter Telegramme (0..1) fuer Tests der Ueberwachung
 * @param seed         Startwert fuer Zufallszahlen; 0 = zufaellig
 */
public record SimConfig(String vehicleId, int doors, int lineStops, int capacity, Duration drive, Duration dwell,
                        Duration cycle, String udpHost, int udpPort, String mqttUrl, double dropRate, long seed) {

    static final String USAGE = """
            Aufruf: java -jar simulator.jar [--name=wert ...]
              --vehicle=bus-101     Fahrzeug
              --doors=2             Anzahl Tueren (1..8)
              --stops=8             Haltestellen pro Linie
              --capacity=80         maximale Fahrgaeste
              --drive=12s           Fahrzeit zwischen Haltestellen
              --dwell=8s            Tueroeffnungszeit
              --cycle=100ms         Sendetakt Tuerstatus
              --udp=localhost:%d  Empfaenger Tuerstatus
              --mqtt=tcp://localhost:1883   Broker fuer Zaehlwerte, "none" = aus
              --drop=0              Anteil verworfener UDP-Telegramme (0..1)
              --seed=0              Zufallsstartwert (0 = zufaellig)
            """.formatted(PdTelegram.DEFAULT_PORT);

    public SimConfig {
        if (doors < 1 || doors > 8) {
            throw new IllegalArgumentException("--doors muss 1..8 sein");
        }
        if (lineStops < 2) {
            throw new IllegalArgumentException("--stops muss mindestens 2 sein");
        }
        if (dropRate < 0 || dropRate > 1) {
            throw new IllegalArgumentException("--drop muss zwischen 0 und 1 liegen");
        }
        if (cycle.isZero() || cycle.isNegative()) {
            throw new IllegalArgumentException("--cycle muss positiv sein");
        }
    }

    public static SimConfig parse(String... args) {
        Map<String, String> m = new HashMap<>();
        for (String arg : args) {
            if (!arg.startsWith("--") || !arg.contains("=")) {
                throw new IllegalArgumentException("Unbekanntes Argument: " + arg);
            }
            int eq = arg.indexOf('=');
            m.put(arg.substring(2, eq), arg.substring(eq + 1));
        }
        String udp = m.getOrDefault("udp", "localhost:" + PdTelegram.DEFAULT_PORT);
        int colon = udp.lastIndexOf(':');
        SimConfig config = new SimConfig(
                m.getOrDefault("vehicle", "bus-101"),
                Integer.parseInt(m.getOrDefault("doors", "2")),
                Integer.parseInt(m.getOrDefault("stops", "8")),
                Integer.parseInt(m.getOrDefault("capacity", "80")),
                duration(m.getOrDefault("drive", "12s")),
                duration(m.getOrDefault("dwell", "8s")),
                duration(m.getOrDefault("cycle", "100ms")),
                colon > 0 ? udp.substring(0, colon) : udp,
                colon > 0 ? Integer.parseInt(udp.substring(colon + 1)) : PdTelegram.DEFAULT_PORT,
                m.getOrDefault("mqtt", "tcp://localhost:1883"),
                Double.parseDouble(m.getOrDefault("drop", "0")),
                Long.parseLong(m.getOrDefault("seed", "0")));
        m.keySet().removeAll(Set.of("vehicle", "doors", "stops", "capacity", "drive", "dwell", "cycle",
                "udp", "mqtt", "drop", "seed"));
        if (!m.isEmpty()) {
            throw new IllegalArgumentException("Unbekannte Option(en): " + m.keySet());
        }
        return config;
    }

    /** "250ms", "8s", "2m" */
    static Duration duration(String text) {
        if (text.endsWith("ms")) {
            return Duration.ofMillis(Long.parseLong(text.substring(0, text.length() - 2)));
        }
        if (text.endsWith("s")) {
            return Duration.ofMillis(Math.round(Double.parseDouble(text.substring(0, text.length() - 1)) * 1000));
        }
        if (text.endsWith("m")) {
            return Duration.ofSeconds(Long.parseLong(text.substring(0, text.length() - 1)) * 60);
        }
        throw new IllegalArgumentException("Dauer braucht Einheit ms, s oder m: " + text);
    }

    public boolean mqttEnabled() {
        return !"none".equalsIgnoreCase(mqttUrl);
    }
}
