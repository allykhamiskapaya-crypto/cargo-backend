package com.mizigo.cargo_backend.repository;

import com.mizigo.cargo_backend.model.TrackingHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface TrackingHistoryRepository extends JpaRepository<TrackingHistory, Long> {
    List<TrackingHistory> findByShipmentIdOrderByTimestampDesc(Long shipmentId);
    Optional<TrackingHistory> findFirstByShipmentIdAndLatitudeIsNotNullAndLongitudeIsNotNullOrderByTimestampDesc(Long shipmentId);
}
