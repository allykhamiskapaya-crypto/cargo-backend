package com.mizigo.cargo_backend.repository;

import com.mizigo.cargo_backend.model.Vehicle;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface VehicleRepository extends JpaRepository<Vehicle, Long> {
    List<Vehicle> findByDriverId(Long driverId);
    Optional<Vehicle> findByPlateNumber(String plateNumber);
}
