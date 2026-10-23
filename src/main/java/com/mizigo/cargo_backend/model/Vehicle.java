package com.mizigo.cargo_backend.model;

import jakarta.persistence.*;
import com.fasterxml.jackson.annotation.JsonIgnore;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Set;

@Entity
@Table(name = "vehicles")
public class Vehicle {

    private static final Set<String> SUPPORTED_VEHICLE_TYPES = Set.of(
            "GARI_KUBWA", "KIRIKUU", "TOYO", "TOYO_NDOGO_UMEME");

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "driver_id", nullable = false)
    private User driver;

    @Column(name = "plate_number", nullable = false, unique = true, length = 30)
    private String plateNumber;

    @Column(name = "vehicle_type", nullable = false, length = 50)
    private String vehicleType;

    @JsonIgnore
    @Column(name = "front_photo_encrypted", columnDefinition = "text")
    private String frontPhotoEncrypted;

    @JsonIgnore
    @Column(name = "rear_photo_encrypted", columnDefinition = "text")
    private String rearPhotoEncrypted;

    @JsonIgnore
    @Column(name = "right_photo_encrypted", columnDefinition = "text")
    private String rightPhotoEncrypted;

    @JsonIgnore
    @Column(name = "left_photo_encrypted", columnDefinition = "text")
    private String leftPhotoEncrypted;

    @Column(name = "capacity_kg", nullable = false, precision = 10, scale = 2)
    private BigDecimal capacityKg;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    public Vehicle() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public User getDriver() { return driver; }
    public void setDriver(User driver) { this.driver = driver; }

    public String getPlateNumber() { return plateNumber; }
    public void setPlateNumber(String plateNumber) { this.plateNumber = plateNumber; }

    public String getVehicleType() { return vehicleType; }
    public void setVehicleType(String vehicleType) {
        if (!SUPPORTED_VEHICLE_TYPES.contains(vehicleType)) {
            throw new IllegalArgumentException("Unsupported vehicle type");
        }
        this.vehicleType = vehicleType;
    }
    public String getFrontPhotoEncrypted() { return frontPhotoEncrypted; }
    public void setFrontPhotoEncrypted(String frontPhotoEncrypted) { this.frontPhotoEncrypted = frontPhotoEncrypted; }
    public String getRearPhotoEncrypted() { return rearPhotoEncrypted; }
    public void setRearPhotoEncrypted(String rearPhotoEncrypted) { this.rearPhotoEncrypted = rearPhotoEncrypted; }
    public String getRightPhotoEncrypted() { return rightPhotoEncrypted; }
    public void setRightPhotoEncrypted(String rightPhotoEncrypted) { this.rightPhotoEncrypted = rightPhotoEncrypted; }
    public String getLeftPhotoEncrypted() { return leftPhotoEncrypted; }
    public void setLeftPhotoEncrypted(String leftPhotoEncrypted) { this.leftPhotoEncrypted = leftPhotoEncrypted; }

    public BigDecimal getCapacityKg() { return capacityKg; }
    public void setCapacityKg(BigDecimal capacityKg) { this.capacityKg = capacityKg; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}