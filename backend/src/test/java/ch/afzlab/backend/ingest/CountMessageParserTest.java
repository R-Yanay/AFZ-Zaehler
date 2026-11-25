package ch.afzlab.backend.ingest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;

import ch.afzlab.common.CountEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import tools.jackson.databind.json.JsonMapper;

class CountMessageParserTest {

    private static final String TOPIC = "afz/vehicle/bus-101/door/1/count";

    private final CountMessageParser parser = new CountMessageParser(JsonMapper.builder().build());

    private static String payload(String vehicle, int door, int boarding, int alighting, String ts) {
        return """
                {"vehicleId":"%s","doorId":%d,"boarding":%d,"alighting":%d,"timestamp":"%s"}"""
                .formatted(vehicle, door, boarding, alighting, ts);
    }

    @Test
    void parstGueltigeMeldungVomSensorSimulator() throws Exception {
        CountEvent event = parser.parse(TOPIC, payload("bus-101", 1, 3, 2, "2026-11-23T18:55:11Z"));

        assertThat(event).isEqualTo(new CountEvent("bus-101", 1, 3, 2, Instant.parse("2026-11-23T18:55:11Z")));
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "afz/vehicle/bus-101/door/1",
        "afz/vehicle/bus-101/door/x/count",
        "andere/vehicle/bus-101/door/1/count"
    })
    void lehntUnbekanntesTopicAb(String topic) {
        assertRejected(topic, payload("bus-101", 1, 0, 0, "2026-11-23T18:55:11Z"), RejectReason.UNGUELTIGES_FORMAT);
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "kein json",
        "[1,2,3]",
        "{\"vehicleId\":\"bus-101\",\"doorId\":1,\"boarding\":3,\"timestamp\":\"2026-11-23T18:55:11Z\"}",
        "{\"vehicleId\":\"bus-101\",\"doorId\":1,\"boarding\":\"3\",\"alighting\":0,"
            + "\"timestamp\":\"2026-11-23T18:55:11Z\"}",
        "{\"vehicleId\":\"bus-101\",\"doorId\":1,\"boarding\":3,\"alighting\":0,\"timestamp\":\"gestern\"}"
    })
    void lehntKaputtenPayloadAb(String payload) {
        assertRejected(TOPIC, payload, RejectReason.UNGUELTIGES_FORMAT);
    }

    @Test
    void lehntNegativeWerteAlsUnplausibelAb() {
        assertRejected(TOPIC, payload("bus-101", 1, -1, 0, "2026-11-23T18:55:11Z"), RejectReason.UNPLAUSIBEL);
    }

    @Test
    void lehntAbWennTopicUndPayloadNichtZusammenpassen() {
        assertRejected(TOPIC, payload("bus-999", 1, 1, 1, "2026-11-23T18:55:11Z"), RejectReason.UNPLAUSIBEL);
        assertRejected(TOPIC, payload("bus-101", 2, 1, 1, "2026-11-23T18:55:11Z"), RejectReason.UNPLAUSIBEL);
    }

    private void assertRejected(String topic, String payload, RejectReason expected) {
        assertThatThrownBy(() -> parser.parse(topic, payload))
                .isInstanceOfSatisfying(InvalidMessageException.class, e -> assertThat(e.reason()).isEqualTo(expected));
    }
}
