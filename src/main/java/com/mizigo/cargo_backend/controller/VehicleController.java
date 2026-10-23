package com.mizigo.cargo_backend.controller;

import com.mizigo.cargo_backend.model.User;
import com.mizigo.cargo_backend.model.UserRole;
import com.mizigo.cargo_backend.model.Vehicle;
import com.mizigo.cargo_backend.repository.UserRepository;
import com.mizigo.cargo_backend.repository.VehicleRepository;
import com.mizigo.cargo_backend.security.SecureFieldCipher;
import com.mizigo.cargo_backend.security.SecurityIdentity;
import com.mizigo.cargo_backend.security.UploadValidator;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Base64;
import java.util.List;
import java.util.Objects;

@RestController
@RequestMapping("/api/vehicles")
public class VehicleController {

    private final VehicleRepository vehicleRepository;
    private final UserRepository userRepository;
    private final SecureFieldCipher secureFieldCipher;
    private final UploadValidator uploadValidator;

    public VehicleController(VehicleRepository vehicleRepository, UserRepository userRepository,
            SecureFieldCipher secureFieldCipher, UploadValidator uploadValidator) {
        this.vehicleRepository = vehicleRepository;
        this.userRepository = userRepository;
        this.secureFieldCipher = secureFieldCipher;
        this.uploadValidator = uploadValidator;
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public List<Vehicle> getAllVehicles() {
        return vehicleRepository.findAll();
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> getVehicleById(@PathVariable Long id,
            @AuthenticationPrincipal Jwt jwt) {
        Vehicle vehicle = vehicleRepository.findById(id).orElse(null);
        if (vehicle == null) return ResponseEntity.notFound().build();
        if (!canAccessVehicle(jwt, vehicle)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        return ResponseEntity.ok(vehicle);
    }

    @GetMapping("/driver/{driverId}")
    public ResponseEntity<?> getVehiclesByDriver(@PathVariable Long driverId,
            @AuthenticationPrincipal Jwt jwt) {
        if (!SecurityIdentity.isAdmin(jwt)
                && (!SecurityIdentity.hasRole(jwt, "DRIVER")
                || !driverId.equals(SecurityIdentity.userId(jwt)))) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        return ResponseEntity.ok(vehicleRepository.findByDriverId(driverId));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('DRIVER', 'ADMIN')")
    public ResponseEntity<?> createVehicle(@Valid @RequestBody VehicleRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        if (!SecurityIdentity.isAdmin(jwt)
            && !Objects.equals(request.getDriverId(), SecurityIdentity.userId(jwt))) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        User driver = userRepository.findById(request.getDriverId()).orElse(null);
        if (driver == null) {
            return ResponseEntity.badRequest().body("Dereva hajapatikana");
        }
        if (vehicleRepository.findByPlateNumber(request.getPlateNumber()).isPresent()) {
            return ResponseEntity.badRequest().body("Namba ya gari tayari imesajiliwa");
        }

        Vehicle vehicle = new Vehicle();
        vehicle.setDriver(driver);
        vehicle.setPlateNumber(request.getPlateNumber());
        vehicle.setVehicleType(request.getVehicleType());
        vehicle.setCapacityKg(request.getCapacityKg());

        Vehicle saved = vehicleRepository.save(vehicle);
        return ResponseEntity.ok(saved);
    }

    @PostMapping(value = "/onboard", consumes = "multipart/form-data")
    @PreAuthorize("hasRole('DRIVER')")
    @Transactional
    public ResponseEntity<?> onboardVehicle(
            @Valid @RequestPart("vehicle") VehicleRequest request,
            @RequestPart("frontPhoto") MultipartFile frontPhoto,
            @RequestPart("rearPhoto") MultipartFile rearPhoto,
            @RequestPart("rightPhoto") MultipartFile rightPhoto,
            @RequestPart("leftPhoto") MultipartFile leftPhoto,
            @AuthenticationPrincipal Jwt jwt) {
        Long driverId = SecurityIdentity.userId(jwt);
        if (!Objects.equals(request.getDriverId(), driverId)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        User driver = userRepository.findById(driverId).orElse(null);
        if (driver == null) return ResponseEntity.notFound().build();
        if (vehicleRepository.findByPlateNumber(request.getPlateNumber()).isPresent()) {
            return ResponseEntity.badRequest().body("Namba ya gari tayari imesajiliwa");
        }

        try {
            Vehicle vehicle = new Vehicle();
            vehicle.setDriver(driver);
            vehicle.setPlateNumber(request.getPlateNumber());
            vehicle.setVehicleType(request.getVehicleType());
            vehicle.setCapacityKg(request.getCapacityKg());
            vehicle.setFrontPhotoEncrypted(encryptVehiclePhoto(frontPhoto, "vehicle-front"));
            vehicle.setRearPhotoEncrypted(encryptVehiclePhoto(rearPhoto, "vehicle-rear"));
            vehicle.setRightPhotoEncrypted(encryptVehiclePhoto(rightPhoto, "vehicle-right"));
            vehicle.setLeftPhotoEncrypted(encryptVehiclePhoto(leftPhoto, "vehicle-left"));
            return ResponseEntity.ok(vehicleRepository.save(vehicle));
        } catch (IllegalArgumentException exception) {
            return ResponseEntity.badRequest().body(exception.getMessage());
        } catch (java.io.IOException exception) {
            return ResponseEntity.badRequest().body("Unable to read vehicle photos");
        }
    }

    @GetMapping("/{id}/photos")
    public ResponseEntity<?> getVehiclePhotos(@PathVariable Long id,
            @AuthenticationPrincipal Jwt jwt) {
        Vehicle vehicle = vehicleRepository.findById(id).orElse(null);
        if (vehicle == null) return ResponseEntity.notFound().build();
        if (!SecurityIdentity.isAdmin(jwt)
                && (!SecurityIdentity.hasRole(jwt, UserRole.DRIVER.name())
                || !vehicle.getDriver().getId().equals(SecurityIdentity.userId(jwt)))) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        return ResponseEntity.ok(new VehiclePhotoResponse(
                decodeVehiclePhoto(vehicle.getFrontPhotoEncrypted(), "vehicle-front"),
                decodeVehiclePhoto(vehicle.getRearPhotoEncrypted(), "vehicle-rear"),
                decodeVehiclePhoto(vehicle.getRightPhotoEncrypted(), "vehicle-right"),
                decodeVehiclePhoto(vehicle.getLeftPhotoEncrypted(), "vehicle-left"),
                vehiclePhotoContentType(vehicle.getFrontPhotoEncrypted(), "vehicle-front"),
                vehiclePhotoContentType(vehicle.getRearPhotoEncrypted(), "vehicle-rear"),
                vehiclePhotoContentType(vehicle.getRightPhotoEncrypted(), "vehicle-right"),
                vehiclePhotoContentType(vehicle.getLeftPhotoEncrypted(), "vehicle-left")));
    }

    private String encryptVehiclePhoto(MultipartFile photo, String purpose) throws java.io.IOException {
        return secureFieldCipher.encrypt(
                Base64.getEncoder().encodeToString(uploadValidator.readVehiclePhoto(photo)), purpose);
    }

    private String decodeVehiclePhoto(String encryptedPhoto, String purpose) {
        return encryptedPhoto == null ? null : secureFieldCipher.decrypt(encryptedPhoto, purpose);
    }

    private String vehiclePhotoContentType(String encryptedPhoto, String purpose) {
        if (encryptedPhoto == null) return null;
        byte[] bytes = Base64.getDecoder().decode(secureFieldCipher.decrypt(encryptedPhoto, purpose));
        if (bytes.length >= 3 && (bytes[0] & 0xff) == 0xff
                && (bytes[1] & 0xff) == 0xd8 && (bytes[2] & 0xff) == 0xff) {
            return "image/jpeg";
        }
        if (bytes.length >= 8 && (bytes[0] & 0xff) == 0x89
                && bytes[1] == 0x50 && bytes[2] == 0x4e && bytes[3] == 0x47) {
            return "image/png";
        }
        if (bytes.length >= 12 && bytes[0] == 'R' && bytes[1] == 'I'
                && bytes[2] == 'F' && bytes[3] == 'F' && bytes[8] == 'W'
                && bytes[9] == 'E' && bytes[10] == 'B' && bytes[11] == 'P') {
            return "image/webp";
        }
        return null;
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> updateVehicle(@PathVariable Long id, @Valid @RequestBody VehicleRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        Vehicle vehicle = vehicleRepository.findById(id).orElse(null);
        if (vehicle == null) return ResponseEntity.notFound().build();
        if (!canAccessVehicle(jwt, vehicle)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        vehicle.setPlateNumber(request.getPlateNumber());
        vehicle.setVehicleType(request.getVehicleType());
        vehicle.setCapacityKg(request.getCapacityKg());
        return ResponseEntity.ok(vehicleRepository.save(vehicle));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteVehicle(@PathVariable Long id,
            @AuthenticationPrincipal Jwt jwt) {
        Vehicle vehicle = vehicleRepository.findById(id).orElse(null);
        if (vehicle == null) {
            return ResponseEntity.notFound().build();
        }
        if (!canAccessVehicle(jwt, vehicle)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        vehicleRepository.deleteById(id);
        return ResponseEntity.noContent().build();
    }

    private boolean canAccessVehicle(Jwt jwt, Vehicle vehicle) {
        return SecurityIdentity.isAdmin(jwt)
                || (SecurityIdentity.hasRole(jwt, "DRIVER")
                && vehicle.getDriver().getId().equals(SecurityIdentity.userId(jwt)));
    }

        public record VehiclePhotoResponse(String frontPhoto, String rearPhoto,
            String rightPhoto, String leftPhoto, String frontPhotoContentType,
            String rearPhotoContentType, String rightPhotoContentType, String leftPhotoContentType) {}

    public static class VehicleRequest {
        @NotNull
        private Long driverId;
        @NotBlank
        @Size(max = 30)
        private String plateNumber;
        @NotBlank
        @Pattern(regexp = "GARI_KUBWA|KIRIKUU|TOYO|TOYO_NDOGO_UMEME")
        private String vehicleType;
        @NotNull
        @Positive
        @Digits(integer = 8, fraction = 2)
        private java.math.BigDecimal capacityKg;

        public Long getDriverId() { return driverId; }
        public void setDriverId(Long driverId) { this.driverId = driverId; }

        public String getPlateNumber() { return plateNumber; }
        public void setPlateNumber(String plateNumber) { this.plateNumber = plateNumber; }

        public String getVehicleType() { return vehicleType; }
        public void setVehicleType(String vehicleType) { this.vehicleType = vehicleType; }

        public java.math.BigDecimal getCapacityKg() { return capacityKg; }
        public void setCapacityKg(java.math.BigDecimal capacityKg) { this.capacityKg = capacityKg; }
    }
}