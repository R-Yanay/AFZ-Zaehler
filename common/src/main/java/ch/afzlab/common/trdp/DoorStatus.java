package ch.afzlab.common.trdp;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;

/**
 * Dataset "Tuerstatus" (ComId {@value #COM_ID}), 32 Byte, Big-Endian.
 * <pre>
 *  Offset  Feld        Typ        Bedeutung
 *   0      vehicleId   char[16]   ASCII, mit 0 aufgefuellt
 *  16      stopId      u16        aktuelle bzw. letzte Haltestelle (0 = keine)
 *  18      speed       u16        Geschwindigkeit in 0.1 km/h
 *  20      doorCount   u8         Anzahl Tueren (1..8)
 *  21      doorMask    u8         Bit n = Tuer n+1 offen
 *  22      reserved    u16        0
 *  24      timestamp   i64        Millisekunden seit 1970 (UTC)
 * </pre>
 *
 * @param vehicleId  Fahrzeug, hoechstens 16 ASCII-Zeichen
 * @param stopId     Haltestelle
 * @param speedKmh10 Geschwindigkeit in 0.1 km/h
 * @param doorCount  Anzahl Tueren
 * @param doorMask   offene Tueren als Bitfeld
 * @param timestamp  Zeitpunkt des Zustands
 */
public record DoorStatus(String vehicleId, int stopId, int speedKmh10, int doorCount, int doorMask, Instant timestamp) {

    public static final int COM_ID = 1001;
    public static final int SIZE = 32;
    public static final int MAX_DOORS = 8;
    private static final int VEHICLE_ID_LENGTH = 16;

    public DoorStatus {
        byte[] id = vehicleId.getBytes(StandardCharsets.US_ASCII);
        if (id.length == 0 || id.length > VEHICLE_ID_LENGTH) {
            throw new IllegalArgumentException("vehicleId muss 1..16 ASCII-Zeichen haben: " + vehicleId);
        }
        if (doorCount < 1 || doorCount > MAX_DOORS) {
            throw new IllegalArgumentException("doorCount ausserhalb 1..8: " + doorCount);
        }
        if ((doorMask & ~((1 << doorCount) - 1)) != 0) {
            throw new IllegalArgumentException("doorMask " + doorMask + " passt nicht zu " + doorCount + " Tueren");
        }
        if (stopId < 0 || stopId > 0xFFFF || speedKmh10 < 0 || speedKmh10 > 0xFFFF) {
            throw new IllegalArgumentException("stopId/speed ausserhalb 0..65535");
        }
    }

    /** Ist Tuer {@code door} (1-basiert) offen? */
    public boolean isOpen(int door) {
        return (doorMask & (1 << (door - 1))) != 0;
    }

    public boolean anyOpen() {
        return doorMask != 0;
    }

    public byte[] encode() {
        ByteBuffer buf = ByteBuffer.allocate(SIZE).order(ByteOrder.BIG_ENDIAN);
        byte[] id = vehicleId.getBytes(StandardCharsets.US_ASCII);
        buf.put(id).position(VEHICLE_ID_LENGTH);
        buf.putShort((short) stopId);
        buf.putShort((short) speedKmh10);
        buf.put((byte) doorCount);
        buf.put((byte) doorMask);
        buf.putShort((short) 0);
        buf.putLong(timestamp.toEpochMilli());
        return buf.array();
    }

    public static DoorStatus decode(byte[] dataset) throws TrdpFormatException {
        if (dataset.length != SIZE) {
            throw new TrdpFormatException("Tuerstatus braucht " + SIZE + " Byte, erhalten " + dataset.length);
        }
        ByteBuffer buf = ByteBuffer.wrap(dataset).order(ByteOrder.BIG_ENDIAN);
        int idLength = 0;
        while (idLength < VEHICLE_ID_LENGTH && dataset[idLength] != 0) {
            idLength++;
        }
        String vehicleId = new String(dataset, 0, idLength, StandardCharsets.US_ASCII);
        int stopId = Short.toUnsignedInt(buf.getShort(16));
        int speed = Short.toUnsignedInt(buf.getShort(18));
        int doorCount = Byte.toUnsignedInt(buf.get(20));
        int doorMask = Byte.toUnsignedInt(buf.get(21));
        Instant timestamp = Instant.ofEpochMilli(buf.getLong(24));
        try {
            return new DoorStatus(vehicleId, stopId, speed, doorCount, doorMask, timestamp);
        } catch (IllegalArgumentException e) {
            throw new TrdpFormatException("Ungueltiger Tuerstatus: " + e.getMessage());
        }
    }
}
