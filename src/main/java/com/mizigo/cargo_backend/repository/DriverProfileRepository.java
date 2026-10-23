package com.mizigo.cargo_backend.repository;

import com.mizigo.cargo_backend.model.DriverProfile;
import java.util.Optional;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.Query;

public interface DriverProfileRepository extends JpaRepository<DriverProfile, Long> {
    Optional<DriverProfile> findByDriverId(Long driverId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select profile from DriverProfile profile where profile.driver.id = :driverId")
    Optional<DriverProfile> findByDriverIdForUpdate(@Param("driverId") Long driverId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select profile from DriverProfile profile where profile.id = :profileId")
    Optional<DriverProfile> findByIdForUpdate(@Param("profileId") Long profileId);
}