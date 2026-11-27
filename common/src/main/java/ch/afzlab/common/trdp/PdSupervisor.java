package ch.afzlab.common.trdp;

import java.time.Duration;
import java.time.Instant;

/**
 * Ueberwacht einen zyklischen PD-Strom: Ausfall (Timeout), verlorene und veraltete Telegramme.
 * Wie bei TRDP gilt: Ein Telegramm ist nur gueltig, solange regelmaessig neue ankommen.
 * Nicht threadsicher; ein Empfaenger-Thread pro Instanz.
 */
public class PdSupervisor {

    /** Bewertung eines empfangenen Telegramms. */
    public enum Verdict {
        /** Erstes Telegramm oder direkt nach einem Ausfall. */
        FIRST,
        /** Naechste erwartete Nummer. */
        OK,
        /** Nummer uebersprungen: dazwischen sind Telegramme verloren gegangen. */
        GAP,
        /** Nummer gleich oder kleiner als die letzte: Duplikat oder falsche Reihenfolge, verwerfen. */
        STALE
    }

    private final Duration timeout;
    private long lastSeq = -1;
    private Instant lastReceived;
    private long received;
    private long lost;
    private long stale;
    private long timeouts;
    private boolean timedOut;

    /** @param timeout nach dieser Zeit ohne Telegramm gilt der Strom als ausgefallen (TRDP: typisch 3 Zyklen) */
    public PdSupervisor(Duration timeout) {
        this.timeout = timeout;
    }

    public Verdict onTelegram(long seq, Instant now) {
        received++;
        Verdict verdict;
        if (lastSeq < 0 || timedOut) {
            verdict = Verdict.FIRST;
        } else {
            long diff = (seq - lastSeq) & 0xFFFF_FFFFL;   // Ueberlauf bei 2^32 beruecksichtigen
            if (diff == 1) {
                verdict = Verdict.OK;
            } else if (diff == 0 || diff > 0x7FFF_FFFFL) {
                stale++;
                return Verdict.STALE;
            } else {
                lost += diff - 1;
                verdict = Verdict.GAP;
            }
        }
        lastSeq = seq;
        lastReceived = now;
        timedOut = false;
        return verdict;
    }

    /**
     * Regelmaessig aufrufen. Liefert true genau einmal beim Uebergang in den Ausfall.
     */
    public boolean checkTimeout(Instant now) {
        if (lastReceived == null || timedOut) {
            return false;
        }
        if (Duration.between(lastReceived, now).compareTo(timeout) > 0) {
            timedOut = true;
            timeouts++;
            return true;
        }
        return false;
    }

    public boolean isValid() {
        return lastReceived != null && !timedOut;
    }

    public long received() {
        return received;
    }

    public long lost() {
        return lost;
    }

    public long stale() {
        return stale;
    }

    public long timeouts() {
        return timeouts;
    }

    public long lastSeq() {
        return lastSeq;
    }
}
