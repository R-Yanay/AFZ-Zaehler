package ch.afzlab.backend.persistence;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/**
 * Ein akzeptierter Zaehlwert.
 * Fahrzeug + Tuer + Messzeitpunkt sind eindeutig: so erkennen wir Duplikate aus QoS 1.
 */
@Entity
@Table(name = "count_record",
        uniqueConstraints = @UniqueConstraint(name = "uk_count_vehicle_door_time",
                columnNames = {"vehicle_id", "door_id", "measured_at"}),
        indexes = @Index(name = "ix_count_vehicle_time", columnList = "vehicle_id, measured_at"))
public class CountRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "vehicle_id", nullable = false, length = 64)
    private String vehicleId;

    @Column(name = "door_id", nullable = false)
    private int doorId;

    @Column(nullable = false)
    private int boarding;

    @Column(nullable = false)
    private int alighting;

    /** Zeitpunkt laut Sensor. */
    @Column(name = "measured_at", nullable = false)
    private Instant measuredAt;

    /** Zeitpunkt des Empfangs im Backend. */
    @Column(name = "received_at", nullable = false)
    private Instant receivedAt;

    protected CountRecord() {
        // fuer JPA
    }

    public CountRecord(String vehicleId, int doorId, int boarding, int alighting,
                       Instant measuredAt, Instant receivedAt) {
        this.vehicleId = vehicleId;
        this.doorId = doorId;
        this.boarding = boarding;
        this.alighting = alighting;
        this.measuredAt = measuredAt;
        this.receivedAt = receivedAt;
    }

    public Long getId() {
        return id;
    }

    public String getVehicleId() {
        return vehicleId;
    }

    public int getDoorId() {
        return doorId;
    }

    public int getBoarding() {
        return boarding;
    }

    public int getAlighting() {
        return alighting;
    }

    public Instant getMeasuredAt() {
        return measuredAt;
    }

    public Instant getReceivedAt() {
        return receivedAt;
    }
}
