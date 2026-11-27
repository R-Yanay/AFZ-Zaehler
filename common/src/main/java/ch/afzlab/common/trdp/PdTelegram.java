package ch.afzlab.common.trdp;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.zip.CRC32;

/**
 * Prozessdaten-Telegramm (PD) nach dem Vorbild von TRDP (IEC 61375-2-3).
 *
 * <p>Header, 40 Byte, Big-Endian (Netzwerk-Byte-Reihenfolge):
 * <pre>
 *  Offset  Feld                     Typ   Wert hier
 *   0      sequenceCounter          u32   zaehlt pro Telegramm hoch
 *   4      protocolVersion          u16   0x0100
 *   6      msgType                  u16   0x5064 ("Pd")
 *   8      comId                    u32   Art der Daten, z. B. 1001 = Tuerstatus
 *  12      etbTopoCnt               u32   0 (keine Zugtopologie simuliert)
 *  16      opTrnTopoCnt             u32   0
 *  20      datasetLength            u32   Laenge der Nutzdaten in Byte
 *  24      reserved                 u32   0
 *  28      replyComId               u32   0 (nur fuer Pull-Anfragen)
 *  32      replyIpAddress           u32   0
 *  36      headerFcs                u32   CRC32 ueber Byte 0..35
 * </pre>
 * Danach folgen die Nutzdaten (Dataset), aufgefuellt auf ein Vielfaches von 4 Byte.
 *
 * @param sequenceCounter laufende Nummer (vorzeichenlos, 32 Bit)
 * @param comId           Kennung der Daten
 * @param dataset         Nutzdaten
 */
public record PdTelegram(long sequenceCounter, int comId, byte[] dataset) {

    public static final int HEADER_SIZE = 40;
    public static final int DEFAULT_PORT = 17224;
    public static final int MAX_DATASET = 1432;
    static final short PROTOCOL_VERSION = 0x0100;
    static final short MSG_TYPE_PD = 0x5064;

    public PdTelegram {
        if (sequenceCounter < 0 || sequenceCounter > 0xFFFF_FFFFL) {
            throw new IllegalArgumentException("sequenceCounter ausserhalb 0..2^32-1: " + sequenceCounter);
        }
        if (dataset.length > MAX_DATASET) {
            throw new IllegalArgumentException("Dataset zu gross: " + dataset.length);
        }
        dataset = dataset.clone();
    }

    @Override
    public byte[] dataset() {
        return dataset.clone();
    }

    /** Serialisiert Header und Dataset in ein UDP-Payload. */
    public byte[] encode() {
        int padded = (dataset.length + 3) & ~3;
        ByteBuffer buf = ByteBuffer.allocate(HEADER_SIZE + padded).order(ByteOrder.BIG_ENDIAN);
        buf.putInt((int) sequenceCounter);
        buf.putShort(PROTOCOL_VERSION);
        buf.putShort(MSG_TYPE_PD);
        buf.putInt(comId);
        buf.putInt(0);              // etbTopoCnt
        buf.putInt(0);              // opTrnTopoCnt
        buf.putInt(dataset.length);
        buf.putInt(0);              // reserved
        buf.putInt(0);              // replyComId
        buf.putInt(0);              // replyIpAddress
        buf.putInt((int) crc(buf.array(), 0, HEADER_SIZE - 4));
        buf.put(dataset);
        return buf.array();
    }

    /** Prueft und zerlegt ein empfangenes UDP-Payload. */
    public static PdTelegram decode(byte[] data, int length) throws TrdpFormatException {
        if (length < HEADER_SIZE) {
            throw new TrdpFormatException("Zu kurz: " + length + " Byte, Header braucht " + HEADER_SIZE);
        }
        ByteBuffer buf = ByteBuffer.wrap(data, 0, length).order(ByteOrder.BIG_ENDIAN);
        long seq = Integer.toUnsignedLong(buf.getInt(0));
        short version = buf.getShort(4);
        short msgType = buf.getShort(6);
        int comId = buf.getInt(8);
        long datasetLength = Integer.toUnsignedLong(buf.getInt(20));
        long fcs = Integer.toUnsignedLong(buf.getInt(36));

        long expectedFcs = crc(data, 0, HEADER_SIZE - 4);
        if (fcs != expectedFcs) {
            throw new TrdpFormatException(String.format("Header-CRC falsch: 0x%08X statt 0x%08X", fcs, expectedFcs));
        }
        if (msgType != MSG_TYPE_PD) {
            throw new TrdpFormatException(String.format("Kein PD-Telegramm: msgType 0x%04X", msgType));
        }
        if ((version & 0xFF00) != (PROTOCOL_VERSION & 0xFF00)) {
            throw new TrdpFormatException(String.format("Unbekannte Protokollversion 0x%04X", version));
        }
        if (datasetLength > MAX_DATASET || HEADER_SIZE + datasetLength > length) {
            throw new TrdpFormatException("Datasetlaenge " + datasetLength + " passt nicht zu "
                    + (length - HEADER_SIZE) + " empfangenen Byte");
        }
        byte[] dataset = Arrays.copyOfRange(data, HEADER_SIZE, HEADER_SIZE + (int) datasetLength);
        return new PdTelegram(seq, comId, dataset);
    }

    static long crc(byte[] data, int offset, int length) {
        CRC32 crc = new CRC32();
        crc.update(data, offset, length);
        return crc.getValue();
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof PdTelegram t && t.sequenceCounter == sequenceCounter && t.comId == comId
                && Arrays.equals(t.dataset, dataset);
    }

    @Override
    public int hashCode() {
        return 31 * (31 * Long.hashCode(sequenceCounter) + comId) + Arrays.hashCode(dataset);
    }

    @Override
    public String toString() {
        return "PdTelegram[seq=" + sequenceCounter + ", comId=" + comId + ", dataset=" + dataset.length + " Byte]";
    }
}
