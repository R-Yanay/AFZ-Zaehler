package ch.afzlab.backend.api;

import java.util.List;

import ch.afzlab.backend.api.ApiDtos.CountDto;
import ch.afzlab.backend.api.ApiDtos.OccupancyDto;
import ch.afzlab.backend.api.ApiDtos.RejectedDto;
import ch.afzlab.backend.persistence.CountRecordRepository;
import ch.afzlab.backend.persistence.RejectedMessageRepository;
import ch.afzlab.backend.persistence.VehicleTotals;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Limit;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Lesender Zugriff auf Zaehlwerte, Besetzung und abgelehnte Meldungen. */
@RestController
@RequestMapping("/api")
@Transactional(readOnly = true)
@Tag(name = "Zaehlwerte", description = "Akzeptierte und abgelehnte Meldungen der Zaehlsensoren")
public class CountController {

    private static final String VEHICLE_EXAMPLE = "bus-101";

    private static final int MAX_LIMIT = 1000;

    private final CountRecordRepository counts;
    private final RejectedMessageRepository rejected;

    public CountController(CountRecordRepository counts, RejectedMessageRepository rejected) {
        this.counts = counts;
        this.rejected = rejected;
    }

    @GetMapping("/vehicles")
    @Operation(summary = "Fahrzeuge", description = "Alle Fahrzeuge, von denen mindestens ein Zaehlwert vorliegt.")
    public List<String> vehicles() {
        return counts.findVehicleIds();
    }

    @GetMapping("/vehicles/{vehicleId}/counts")
    @Operation(summary = "Zaehlwerte eines Fahrzeugs", description = "Letzte akzeptierte Meldungen, neueste zuerst.")
    public List<CountDto> counts(@PathVariable @Parameter(example = VEHICLE_EXAMPLE) String vehicleId,
                                 @RequestParam(defaultValue = "50")
                                 @Parameter(description = "Anzahl Eintraege (1-1000)") int limit) {
        return counts.findByVehicleIdOrderByMeasuredAtDesc(vehicleId, limit(limit)).stream()
                .map(CountDto::of)
                .toList();
    }

    @GetMapping("/vehicles/{vehicleId}/occupancy")
    @Operation(summary = "Besetzung eines Fahrzeugs",
            description = "Summen der Ein- und Aussteiger; plausible=false bei negativer Besetzung.")
    @ApiResponse(responseCode = "200", description = "Besetzung berechnet")
    @ApiResponse(responseCode = "404", description = "Keine Zaehlwerte fuer dieses Fahrzeug", content = @Content)
    public ResponseEntity<OccupancyDto> occupancy(
            @PathVariable @Parameter(example = VEHICLE_EXAMPLE) String vehicleId) {
        VehicleTotals totals = counts.totalsFor(vehicleId);
        return totals == null ? ResponseEntity.notFound().build() : ResponseEntity.ok(OccupancyDto.of(totals));
    }

    @GetMapping("/rejected")
    @Operation(summary = "Abgelehnte Meldungen",
            description = "Ungueltige, unplausible und doppelte Meldungen mit Originalpayload und Grund.")
    public List<RejectedDto> rejected(@RequestParam(defaultValue = "50")
                                      @Parameter(description = "Anzahl Eintraege (1-1000)") int limit) {
        return rejected.findAllByOrderByReceivedAtDesc(limit(limit)).stream()
                .map(RejectedDto::of)
                .toList();
    }

    private static Limit limit(int requested) {
        return Limit.of(Math.clamp(requested, 1, MAX_LIMIT));
    }
}
