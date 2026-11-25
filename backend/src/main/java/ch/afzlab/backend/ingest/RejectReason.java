package ch.afzlab.backend.ingest;

/** Warum eine Meldung nicht als Zaehlwert uebernommen wurde. */
public enum RejectReason {
    /** Topic oder JSON nicht lesbar bzw. Felder fehlen. */
    UNGUELTIGES_FORMAT,
    /** Lesbar, aber fachlich unplausibel (z. B. zu viele Personen, Zeit in der Zukunft). */
    UNPLAUSIBEL,
    /** Gleiche Meldung schon gespeichert (bei QoS 1 moeglich). */
    DUPLIKAT
}
