package ch.afzlab.common.trdp;

/** Ein empfangenes Telegramm ist kein gueltiges TRDP-PD-Telegramm. */
public class TrdpFormatException extends Exception {

    public TrdpFormatException(String message) {
        super(message);
    }
}
