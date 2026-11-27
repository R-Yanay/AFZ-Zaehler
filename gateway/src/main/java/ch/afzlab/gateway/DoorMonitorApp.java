package ch.afzlab.gateway;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.SocketTimeoutException;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

import ch.afzlab.common.trdp.DoorStatus;
import ch.afzlab.common.trdp.PdSupervisor;
import ch.afzlab.common.trdp.PdTelegram;
import ch.afzlab.common.trdp.TrdpFormatException;

/**
 * Diagnosewerkzeug: empfaengt den Tuerstatus (TRDP-aehnliche PD-Telegramme), prueft ihn und zeigt Aenderungen an.
 * Meldet verlorene Telegramme (Luecke in der Sequenznummer), Duplikate, CRC-Fehler und Ausfall des Senders.
 * Aufruf: java -jar gateway.jar [port] [timeout-ms]
 */
public final class DoorMonitorApp {

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss.SSS");
    private static final Duration STATS_INTERVAL = Duration.ofSeconds(10);

    private DoorMonitorApp() {
    }

    public static void main(String[] args) throws IOException {
        int port = args.length > 0 ? Integer.parseInt(args[0]) : PdTelegram.DEFAULT_PORT;
        // TRDP-Praxis: Ausfall nach etwa 3 verpassten Zyklen (3 x 100 ms)
        Duration timeout = Duration.ofMillis(args.length > 1 ? Long.parseLong(args[1]) : 300);

        PdSupervisor supervisor = new PdSupervisor(timeout);
        byte[] buffer = new byte[1500];
        DoorStatus last = null;
        long formatErrors = 0;
        Instant nextStats = Instant.now().plus(STATS_INTERVAL);

        try (DatagramSocket socket = new DatagramSocket(port)) {
            socket.setSoTimeout(50);
            log("Warte auf Tuerstatus (ComId " + DoorStatus.COM_ID + ") auf UDP-Port " + port
                    + ", Timeout " + timeout.toMillis() + " ms");
            while (true) {
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                try {
                    socket.receive(packet);
                } catch (SocketTimeoutException e) {
                    packet = null;
                }
                Instant now = Instant.now();

                if (packet != null) {
                    String source = packet.getAddress().getHostAddress() + ":" + packet.getPort();
                    try {
                        PdTelegram telegram = PdTelegram.decode(packet.getData(), packet.getLength());
                        if (telegram.comId() != DoorStatus.COM_ID) {
                            continue;   // andere Prozessdaten interessieren hier nicht
                        }
                        DoorStatus status = DoorStatus.decode(telegram.dataset());
                        switch (supervisor.onTelegram(telegram.sequenceCounter(), now)) {
                            case FIRST -> log("Sender " + source + " aktiv (" + status.vehicleId() + ", Seq "
                                    + telegram.sequenceCounter() + ")");
                            case GAP -> log("LUECKE: Telegramme verloren, jetzt Seq " + telegram.sequenceCounter()
                                    + " (insgesamt verloren: " + supervisor.lost() + ")");
                            case STALE -> {
                                log("VERALTET: Seq " + telegram.sequenceCounter() + " verworfen");
                                continue;
                            }
                            default -> {
                            }
                        }
                        if (last == null || changed(last, status)) {
                            log(describe(status));
                        }
                        last = status;
                    } catch (TrdpFormatException e) {
                        formatErrors++;
                        log("UNGUELTIG von " + source + ": " + e.getMessage());
                    }
                }

                if (supervisor.checkTimeout(now)) {
                    log("AUSFALL: seit " + timeout.toMillis() + " ms kein Tuerstatus, Daten ungueltig");
                    last = null;
                }
                if (now.isAfter(nextStats)) {
                    log(String.format("Statistik: empfangen %d, verloren %d, veraltet %d, ungueltig %d, Ausfaelle %d",
                            supervisor.received(), supervisor.lost(), supervisor.stale(), formatErrors,
                            supervisor.timeouts()));
                    nextStats = now.plus(STATS_INTERVAL);
                }
            }
        }
    }

    /** Nur fachliche Aenderungen anzeigen, nicht jedes der 10 Telegramme pro Sekunde. */
    private static boolean changed(DoorStatus a, DoorStatus b) {
        return a.doorMask() != b.doorMask() || a.stopId() != b.stopId()
                || (a.speedKmh10() == 0) != (b.speedKmh10() == 0);
    }

    private static String describe(DoorStatus s) {
        StringBuilder doors = new StringBuilder();
        for (int d = 1; d <= s.doorCount(); d++) {
            doors.append(" T").append(d).append(s.isOpen(d) ? "=offen" : "=zu");
        }
        return String.format("%s Halt %d  %5.1f km/h %s", s.vehicleId(), s.stopId(), s.speedKmh10() / 10.0, doors);
    }

    private static void log(String message) {
        System.out.println(LocalTime.now().format(TIME) + "  " + message);
    }
}
