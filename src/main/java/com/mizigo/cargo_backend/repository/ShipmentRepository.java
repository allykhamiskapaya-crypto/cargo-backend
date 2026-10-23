package com.mizigo.cargo_backend.repository;

import com.mizigo.cargo_backend.model.Shipment;
import com.mizigo.cargo_backend.model.ShipmentStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;

public interface ShipmentRepository extends JpaRepository<Shipment, Long> {
    List<Shipment> findByCustomerId(Long customerId);
    List<Shipment> findByDriverId(Long driverId);
    List<Shipment> findByDriverIdAndStatus(Long driverId, ShipmentStatus status);
    List<Shipment> findByStatus(ShipmentStatus status);
    List<Shipment> findByCustomerIdAndStatus(Long customerId, ShipmentStatus status);

    @Query("select shipment from Shipment shipment where shipment.status = com.mizigo.cargo_backend.model.ShipmentStatus.PENDING "
            + "and shipment.driver is null and not exists (select response.id from DriverJobResponse response "
            + "where response.shipment = shipment and response.driver.id = :driverId)")
    List<Shipment> findAvailableOffersForDriver(@Param("driverId") Long driverId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select shipment from Shipment shipment where shipment.id = :shipmentId")
    Optional<Shipment> findByIdForUpdate(@Param("shipmentId") Long shipmentId);
}
