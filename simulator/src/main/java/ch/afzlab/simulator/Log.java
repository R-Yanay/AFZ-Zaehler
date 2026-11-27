package ch.afzlab.simulator;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

/** Minimale Konsolenausgabe mit Uhrzeit; bewusst ohne Logging-Bibliothek. */
final class Log {

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss.SSS");

    private Log() {
    }

    static void info(String message) {
        System.out.println(LocalTime.now().format(TIME) + "  " + message);
    }

    static void warn(String message) {
        System.out.println(LocalTime.now().format(TIME) + "  WARNUNG " + message);
    }
}
