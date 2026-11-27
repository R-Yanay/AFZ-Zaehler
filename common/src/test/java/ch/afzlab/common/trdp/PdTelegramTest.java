package ch.afzlab.common.trdp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.HexFormat;

import org.junit.jupiter.api.Test;

class PdTelegramTest {

    private static final DoorStatus STATUS =
            new DoorStatus("bus-101", 3, 0, 2, 0b01, Instant.parse("2026-11-26T10:00:00.123Z"));

    @Test
    void headerEntsprichtDemTrdpLayout() {
        byte[] bytes = new PdTelegram(7, 1001, new byte[] {1, 2, 3, 4, 5, 6, 7, 8}).encode();

        // Referenz: von Wiresharks TRDP-Dissector als "Seq 7, Pd, ComId 1001, Len 8" erkannt
        assertThat(HexFormat.of().formatHex(bytes, 0, PdTelegram.HEADER_SIZE)).isEqualTo(
                "00000007" + "0100" + "5064" + "000003e9" + "00000000" + "00000000"
                        + "00000008" + "00000000" + "00000000" + "00000000" + "035e161a");
        assertThat(bytes).hasSize(48);
    }

    @Test
    void hinUndZurueck() throws Exception {
        PdTelegram original = new PdTelegram(0xFFFF_FFFFL, DoorStatus.COM_ID, STATUS.encode());
        byte[] bytes = original.encode();

        PdTelegram decoded = PdTelegram.decode(bytes, bytes.length);

        assertThat(decoded).isEqualTo(original);
        assertThat(DoorStatus.decode(decoded.dataset())).isEqualTo(STATUS);
    }

    @Test
    void datasetWirdAufVielfachesVonVierAufgefuellt() throws Exception {
        byte[] bytes = new PdTelegram(1, 42, new byte[] {9, 9, 9}).encode();

        assertThat(bytes).hasSize(44);
        assertThat(PdTelegram.decode(bytes, bytes.length).dataset()).containsExactly(9, 9, 9);
    }

    @Test
    void erkenntBeschaedigtenHeader() {
        byte[] bytes = new PdTelegram(1, DoorStatus.COM_ID, STATUS.encode()).encode();
        bytes[11] ^= 0x01;   // ein Bit in der ComId kippt

        assertThatThrownBy(() -> PdTelegram.decode(bytes, bytes.length))
                .isInstanceOf(TrdpFormatException.class).hasMessageContaining("CRC");
    }

    @Test
    void lehntZuKurzeUndFremdeTelegrammeAb() {
        assertThatThrownBy(() -> PdTelegram.decode(new byte[10], 10)).hasMessageContaining("Zu kurz");

        byte[] bytes = new PdTelegram(1, DoorStatus.COM_ID, STATUS.encode()).encode();
        assertThatThrownBy(() -> PdTelegram.decode(bytes, 50)).hasMessageContaining("Datasetlaenge");
    }

    @Test
    void doorStatusPrueftTuerbits() throws Exception {
        assertThat(STATUS.isOpen(1)).isTrue();
        assertThat(STATUS.isOpen(2)).isFalse();
        assertThatThrownBy(() -> new DoorStatus("bus-101", 0, 0, 2, 0b100, Instant.EPOCH))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new DoorStatus("ein-viel-zu-langer-name", 0, 0, 2, 0, Instant.EPOCH))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
