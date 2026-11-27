package ch.afzlab.simulator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;

import ch.afzlab.common.CountEvent;
import org.junit.jupiter.api.Test;

class SimConfigTest {

    @Test
    void standardwerte() {
        SimConfig c = SimConfig.parse();

        assertThat(c.vehicleId()).isEqualTo("bus-101");
        assertThat(c.udpPort()).isEqualTo(17224);
        assertThat(c.cycle()).isEqualTo(Duration.ofMillis(100));
        assertThat(c.mqttEnabled()).isTrue();
    }

    @Test
    void liestOptionen() {
        SimConfig c = SimConfig.parse("--vehicle=tram-7", "--udp=192.168.1.5:20000", "--dwell=2.5s",
                "--mqtt=none", "--drop=0.1");

        assertThat(c.vehicleId()).isEqualTo("tram-7");
        assertThat(c.udpHost()).isEqualTo("192.168.1.5");
        assertThat(c.udpPort()).isEqualTo(20000);
        assertThat(c.dwell()).isEqualTo(Duration.ofMillis(2500));
        assertThat(c.mqttEnabled()).isFalse();
        assertThat(c.dropRate()).isEqualTo(0.1);
    }

    @Test
    void lehntUnbekannteUndUngueltigeOptionenAb() {
        assertThatThrownBy(() -> SimConfig.parse("--tueren=2")).hasMessageContaining("tueren");
        assertThatThrownBy(() -> SimConfig.parse("--doors=9")).hasMessageContaining("doors");
        assertThatThrownBy(() -> SimConfig.parse("--dwell=8")).hasMessageContaining("Einheit");
    }

    @Test
    void jsonFormatPasstZumBackend() {
        CountEvent e = new CountEvent("bus-101", 2, 3, 1, Instant.parse("2026-11-26T10:00:00.250Z"));

        assertThat(CountPublisher.topic(e)).isEqualTo("afz/vehicle/bus-101/door/2/count");
        assertThat(CountPublisher.toJson(e)).isEqualTo(
                "{\"vehicleId\":\"bus-101\",\"doorId\":2,\"boarding\":3,\"alighting\":1,"
                        + "\"timestamp\":\"2026-11-26T10:00:00.250Z\"}");
    }
}
