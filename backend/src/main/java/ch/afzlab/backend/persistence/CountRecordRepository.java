package ch.afzlab.backend.persistence;

import java.time.Instant;
import java.util.List;

import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface CountRecordRepository extends JpaRepository<CountRecord, Long> {

    boolean existsByVehicleIdAndDoorIdAndMeasuredAt(String vehicleId, int doorId, Instant measuredAt);

    List<CountRecord> findByVehicleIdOrderByMeasuredAtDesc(String vehicleId, Limit limit);

    @Query("select distinct c.vehicleId from CountRecord c order by c.vehicleId")
    List<String> findVehicleIds();

    /** Summen ueber alle Meldungen eines Fahrzeugs; null, wenn es keine gibt. */
    @Query("""
            select new ch.afzlab.backend.persistence.VehicleTotals(
                c.vehicleId, sum(c.boarding), sum(c.alighting), count(c), max(c.measuredAt))
            from CountRecord c
            where c.vehicleId = :vehicleId
            group by c.vehicleId
            """)
    VehicleTotals totalsFor(String vehicleId);
}
