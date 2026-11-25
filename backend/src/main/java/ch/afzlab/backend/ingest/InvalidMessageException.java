package ch.afzlab.backend.ingest;

/** Eine MQTT-Meldung konnte nicht in einen Zaehlwert umgewandelt werden. */
public class InvalidMessageException extends Exception {

    private final RejectReason reason;

    public InvalidMessageException(RejectReason reason, String message) {
        super(message);
        this.reason = reason;
    }

    public RejectReason reason() {
        return reason;
    }
}
