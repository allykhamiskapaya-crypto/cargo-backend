package com.mizigo.cargo_backend.repository;

import com.mizigo.cargo_backend.model.DriverJobResponse;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DriverJobResponseRepository extends JpaRepository<DriverJobResponse, Long> {
    Optional<DriverJobResponse> findByShipmentIdAndDriverId(Long shipmentId, Long driverId);
}