package ch.afzlab.backend.persistence;

import java.time.Instant;

import ch.afzlab.backend.ingest.RejectReason;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Eine abgelehnte Meldung, unveraendert aufbewahrt zur Nachverfolgung. */
@Entity
@Table(name = "rejected_message")
public class RejectedMessage {

    static final int MAX_PAYLOAD = 4000;
    static final int MAX_DETAIL = 1000;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 255)
    private String topic;

    @Column(length = MAX_PAYLOAD)
    private String payload;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private RejectReason reason;

    @Column(length = MAX_DETAIL)
    private String detail;

    @Column(name = "received_at", nullable = false)
    private Instant receivedAt;

    protected RejectedMessage() {
        // fuer JPA
    }

    public RejectedMessage(String topic, String payload, RejectReason reason, String detail, Instant receivedAt) {
        this.topic = truncate(topic, 255);
        this.payload = truncate(payload, MAX_PAYLOAD);
        this.reason = reason;
        this.detail = truncate(detail, MAX_DETAIL);
        this.receivedAt = receivedAt;
    }

    private static String truncate(String value, int max) {
        return value == null || value.length() <= max ? value : value.substring(0, max);
    }

    public Long getId() {
        return id;
    }

    public String getTopic() {
        return topic;
    }

    public String getPayload() {
        return payload;
    }

    public RejectReason getReason() {
        return reason;
    }

    public String getDetail() {
        return detail;
    }

    public Instant getReceivedAt() {
        return receivedAt;
    }
}
