package ch.afzlab.backend;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import ch.afzlab.backend.ingest.CountIngestService;
import ch.afzlab.backend.ingest.RejectReason;
import ch.afzlab.backend.persistence.CountRecordRepository;
import ch.afzlab.backend.persistence.RejectedMessageRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Ganzer Ablauf ohne Broker: Meldung -> Ingest -> H2 (in-memory) -> REST.
 * MQTT ist in src/test/resources/application.yaml abgeschaltet.
 */
@SpringBootTest
@AutoConfigureMockMvc
class BackendApplicationTest {

    private static final String TOPIC = "afz/vehicle/bus-101/door/1/count";

    @Autowired
    private CountIngestService ingest;

    @Autowired
    private CountRecordRepository counts;

    @Autowired
    private RejectedMessageRepository rejected;

    @Autowired
    private MockMvc mvc;

    @BeforeEach
    void leereDatenbank() {
        counts.deleteAll();
        rejected.deleteAll();
    }

    private static String payload(int door, int boarding, int alighting, Instant ts) {
        return """
                {"vehicleId":"bus-101","doorId":%d,"boarding":%d,"alighting":%d,"timestamp":"%s"}"""
                .formatted(door, boarding, alighting, ts);
    }

    @Test
    void erkenntDuplikateWieSieBeiQos1VorkommenKoennen() {
        String msg = payload(1, 3, 1, Instant.now().truncatedTo(ChronoUnit.SECONDS));

        assertThat(ingest.ingest(TOPIC, msg).accepted()).isTrue();
        assertThat(ingest.ingest(TOPIC, msg).reason()).isEqualTo(RejectReason.DUPLIKAT);

        assertThat(counts.count()).isEqualTo(1);
        assertThat(rejected.count()).isEqualTo(1);
    }

    @Test
    void speichertUngueltigeMeldungenMitGrund() {
        assertThat(ingest.ingest(TOPIC, "kaputt").reason()).isEqualTo(RejectReason.UNGUELTIGES_FORMAT);
        assertThat(ingest.ingest(TOPIC, payload(1, 99, 0, Instant.now())).reason())
                .isEqualTo(RejectReason.UNPLAUSIBEL);

        assertThat(counts.count()).isZero();
        assertThat(rejected.findAll()).extracting(m -> m.getPayload()).contains("kaputt");
    }

    @Test
    void restApiLiefertZaehlwerteUndBesetzung() throws Exception {
        Instant t = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        ingest.ingest(TOPIC, payload(1, 5, 0, t.minusSeconds(20)));
        ingest.ingest("afz/vehicle/bus-101/door/2/count", payload(2, 2, 1, t.minusSeconds(10)));
        ingest.ingest(TOPIC, "kaputt");

        mvc.perform(get("/api/vehicles"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0]").value("bus-101"));

        mvc.perform(get("/api/vehicles/bus-101/counts"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].doorId").value(2));

        mvc.perform(get("/api/vehicles/bus-101/occupancy"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.boarding").value(7))
                .andExpect(jsonPath("$.alighting").value(1))
                .andExpect(jsonPath("$.occupancy").value(6))
                .andExpect(jsonPath("$.plausible").value(true))
                .andExpect(jsonPath("$.messages").value(2));

        mvc.perform(get("/api/vehicles/unbekannt/occupancy"))
                .andExpect(status().isNotFound());

        mvc.perform(get("/api/rejected"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].reason").value("UNGUELTIGES_FORMAT"));
    }

    @Test
    void startseiteUndOpenApiSindErreichbar() throws Exception {
        mvc.perform(get("/index.html"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("AFZ-Lab Backend")));

        mvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.info.title").value("AFZ-Lab Backend"))
                .andExpect(jsonPath("$.paths['/api/vehicles/{vehicleId}/occupancy'].get.summary")
                        .value("Besetzung eines Fahrzeugs"))
                .andExpect(jsonPath("$.paths['/actuator/health']").exists());
    }

    @Test
    void healthEndpointIstErreichbar() throws Exception {
        mvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }
}
