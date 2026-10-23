package com.mizigo.cargo_backend.controller;

import com.mizigo.cargo_backend.model.DriverJobResponse;
import com.mizigo.cargo_backend.model.DriverJobResponseStatus;
import com.mizigo.cargo_backend.model.DriverProfile;
import com.mizigo.cargo_backend.model.DriverApprovalStatus;
import com.mizigo.cargo_backend.model.CommissionPayment;
import com.mizigo.cargo_backend.model.Shipment;
import com.mizigo.cargo_backend.model.ShipmentStatus;
import com.mizigo.cargo_backend.model.User;
import com.mizigo.cargo_backend.repository.DriverJobResponseRepository;
import com.mizigo.cargo_backend.repository.DriverProfileRepository;
import com.mizigo.cargo_backend.repository.CommissionPaymentRepository;
import com.mizigo.cargo_backend.repository.ShipmentRepository;
import com.mizigo.cargo_backend.repository.TrackingHistoryRepository;
import com.mizigo.cargo_backend.repository.UserRepository;
import com.mizigo.cargo_backend.repository.VehicleRepository;
import com.mizigo.cargo_backend.security.SecureFieldCipher;
import com.mizigo.cargo_backend.security.SecurityIdentity;
import com.mizigo.cargo_backend.security.UploadValidator;
import com.mizigo.cargo_backend.service.DriverCommissionService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/drivers/me")
@PreAuthorize("hasRole('DRIVER')")
public class DriverController {

    private final DriverProfileRepository driverProfileRepository;
    private final CommissionPaymentRepository commissionPaymentRepository;
    private final DriverJobResponseRepository jobResponseRepository;
    private final ShipmentRepository shipmentRepository;
    private final TrackingHistoryRepository trackingHistoryRepository;
    private final UserRepository userRepository;
    private final VehicleRepository vehicleRepository;
    private final SecureFieldCipher secureFieldCipher;
    private final UploadValidator uploadValidator;
    private final DriverCommissionService driverCommissionService;

    public DriverController(DriverProfileRepository driverProfileRepository,
            CommissionPaymentRepository commissionPaymentRepository,
            DriverJobResponseRepository jobResponseRepository,
            ShipmentRepository shipmentRepository,
            TrackingHistoryRepository trackingHistoryRepository,
            UserRepository userRepository,
            VehicleRepository vehicleRepository,
            SecureFieldCipher secureFieldCipher,
            UploadValidator uploadValidator,
            DriverCommissionService driverCommissionService) {
        this.driverProfileRepository = driverProfileRepository;
        this.commissionPaymentRepository = commissionPaymentRepository;
        this.jobResponseRepository = jobResponseRepository;
        this.shipmentRepository = shipmentRepository;
        this.trackingHistoryRepository = trackingHistoryRepository;
        this.userRepository = userRepository;
        this.vehicleRepository = vehicleRepository;
        this.secureFieldCipher = secureFieldCipher;
        this.uploadValidator = uploadValidator;
        this.driverCommissionService = driverCommissionService;
    }

    @GetMapping("/profile")
    public ResponseEntity<DriverProfileResponse> getProfile(@AuthenticationPrincipal Jwt jwt) {
        Long driverId = SecurityIdentity.userId(jwt);
        DriverApprovalStatus approvalStatus = userRepository.findById(driverId)
                .map(User::getDriverApprovalStatus)
                .filter(status -> status != null)
                .orElse(DriverApprovalStatus.PENDING_APPROVAL);
        DriverProfile profile = driverCommissionService.reconcile(driverId);
        if (profile == null) {
            return ResponseEntity.ok(new DriverProfileResponse(
                    "", "", null, false, false, false, "", "", false, false, approvalStatus,
                    0.0, 0, 3, false, false, null));
        }
        return ResponseEntity.ok(toResponse(profile, approvalStatus));
    }

    @PutMapping("/profile/photo")
    public ResponseEntity<?> uploadFacePhoto(@Valid @RequestBody FacePhotoRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        DriverProfile profile = driverProfileRepository
                .findByDriverId(SecurityIdentity.userId(jwt)).orElse(null);
        if (profile == null) return ResponseEntity.status(HttpStatus.CONFLICT).body("Save driver profile first");

        byte[] photoBytes;
        try {
            photoBytes = Base64.getDecoder().decode(request.base64Data());
        } catch (IllegalArgumentException exception) {
            return ResponseEntity.badRequest().body("Invalid image data");
        }
        if (photoBytes.length == 0 || photoBytes.length > 1_000_000
                || !isSupportedImage(photoBytes, request.contentType())) {
            return ResponseEntity.badRequest().body("Only valid JPEG, PNG, or WebP photos up to 1 MB are accepted");
        }
        profile.setFacePhotoEncrypted(secureFieldCipher.encrypt(
                Base64.getEncoder().encodeToString(photoBytes), "driver-face-photo"));
        profile.setFacePhotoContentType(request.contentType());
        return ResponseEntity.ok(toResponse(driverProfileRepository.save(profile)));
    }

    @GetMapping("/profile/photo")
    public ResponseEntity<?> getFacePhoto(@AuthenticationPrincipal Jwt jwt) {
        DriverProfile profile = driverProfileRepository
                .findByDriverId(SecurityIdentity.userId(jwt)).orElse(null);
        if (profile == null || profile.getFacePhotoEncrypted() == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(new FacePhotoResponse(profile.getFacePhotoContentType(),
                secureFieldCipher.decrypt(profile.getFacePhotoEncrypted(), "driver-face-photo")));
    }

    @PostMapping("/pay-commission")
    public ResponseEntity<?> requestCommissionPayment(@Valid @RequestBody CommissionPaymentRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        DriverProfile profile = driverCommissionService.reconcile(SecurityIdentity.userId(jwt));
        if (profile == null) return ResponseEntity.status(HttpStatus.CONFLICT).body("Driver profile not found");
        if (profile.getCommissionBalance() <= 0 && profile.getUnpaidJobCount() <= 0) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body("No outstanding commission to pay");
        }
        var pending = commissionPaymentRepository.findFirstByDriverProfileIdAndVerifiedFalseAndRejectedFalseOrderByCreatedAtDesc(
                profile.getId());
        if (pending.isPresent()) return ResponseEntity.accepted().body(toPaymentResponse(pending.get()));
        String reference = request.reference().trim();
        if (commissionPaymentRepository.existsByReference(reference)) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body("Transaction reference has already been submitted");
        }
        CommissionPayment payment = new CommissionPayment();
        payment.setDriverProfile(profile);
        payment.setAmount(java.math.BigDecimal.valueOf(profile.getCommissionBalance())
                .setScale(2, java.math.RoundingMode.HALF_UP));
        payment.setReference(reference);
        return ResponseEntity.accepted().body(toPaymentResponse(commissionPaymentRepository.save(payment)));
    }

    @PutMapping(value = "/profile/license-document", consumes = "multipart/form-data")
    public ResponseEntity<?> uploadLicenseDocument(@RequestParam("document") MultipartFile document,
            @AuthenticationPrincipal Jwt jwt) {
        DriverProfile profile = driverProfileRepository
                .findByDriverId(SecurityIdentity.userId(jwt)).orElse(null);
        if (profile == null) return ResponseEntity.status(HttpStatus.CONFLICT).body("Save driver profile first");
        try {
            UploadValidator.ValidatedDocument validated = uploadValidator.readLicenseDocument(document);
            profile.setLicenseDocumentEncrypted(secureFieldCipher.encrypt(
                    Base64.getEncoder().encodeToString(validated.bytes()), "driver-license-document"));
            profile.setLicenseDocumentContentType(validated.contentType());
            return ResponseEntity.ok(toResponse(driverProfileRepository.save(profile)));
        } catch (IllegalArgumentException exception) {
            return ResponseEntity.badRequest().body(exception.getMessage());
        } catch (java.io.IOException exception) {
            return ResponseEntity.badRequest().body("Unable to read license document");
        }
    }

    @PutMapping(value = "/profile/nida-document", consumes = "multipart/form-data")
    public ResponseEntity<?> uploadNidaDocument(@RequestParam("document") MultipartFile document,
            @AuthenticationPrincipal Jwt jwt) {
        DriverProfile profile = driverProfileRepository
                .findByDriverId(SecurityIdentity.userId(jwt)).orElse(null);
        if (profile == null) return ResponseEntity.status(HttpStatus.CONFLICT).body("Save driver profile first");
        try {
            UploadValidator.ValidatedDocument validated = uploadValidator.readNidaDocument(document);
            profile.setNidaDocumentEncrypted(secureFieldCipher.encrypt(
                    Base64.getEncoder().encodeToString(validated.bytes()), "driver-nida-document"));
            profile.setNidaDocumentContentType(validated.contentType());
            return ResponseEntity.ok(toResponse(driverProfileRepository.save(profile)));
        } catch (IllegalArgumentException exception) {
            return ResponseEntity.badRequest().body(exception.getMessage());
        } catch (java.io.IOException exception) {
            return ResponseEntity.badRequest().body("Unable to read NIDA document");
        }
    }

    @PutMapping("/profile")
    public ResponseEntity<?> saveProfile(@Valid @RequestBody DriverProfileRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        Long driverId = SecurityIdentity.userId(jwt);
        User driver = userRepository.findById(driverId).orElse(null);
        if (driver == null) return ResponseEntity.notFound().build();
        if (userRepository.findByPhone(request.phone())
            .filter(existingUser -> !existingUser.getId().equals(driverId)).isPresent()) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body("Phone number is already registered");
        }

        DriverProfile profile = driverProfileRepository.findByDriverId(driverId)
                .orElseGet(DriverProfile::new);
        profile.setDriver(driver);
        driver.setFullName(request.fullName());
        driver.setPhone(request.phone());
        userRepository.save(driver);
        profile.setNidaEncrypted(secureFieldCipher.encrypt(request.nidaNumber(), "driver-nida"));
        profile.setLicenseNumberEncrypted(secureFieldCipher.encrypt(request.licenseNumber(), "driver-license"));
        profile.setLicenseExpiry(request.licenseExpiry());
        profile = driverProfileRepository.save(profile);
        return ResponseEntity.ok(toResponse(profile));
    }

    @PutMapping("/availability")
    public ResponseEntity<?> setAvailability(@Valid @RequestBody AvailabilityRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        Long driverId = SecurityIdentity.userId(jwt);
        DriverProfile profile = driverCommissionService.reconcile(driverId);
        if (!isApprovedDriver(driverId)) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body("Driver account is not approved");
        }
        if (profile == null) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body("Save your driver profile before going online");
        }
        if (request.available() && vehicleRepository.findByDriverId(driverId).isEmpty()) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body("Register a vehicle before going online");
        }
        if (request.available() && profile.isRestricted()) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                .body("Pay outstanding commission before going online");
        }
        profile.setAvailable(request.available());
        return ResponseEntity.ok(toResponse(driverProfileRepository.save(profile)));
    }

    @GetMapping("/jobs")
    public ResponseEntity<?> getJobOffers(@AuthenticationPrincipal Jwt jwt) {
        Long driverId = SecurityIdentity.userId(jwt);
        DriverProfile profile = driverCommissionService.reconcile(driverId);
        if (!isApprovedDriver(driverId) || profile == null || !profile.isAvailable()) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body("Driver is offline");
        }
        if (profile.isRestricted()) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body("Pay outstanding commission before accepting jobs");
        }
        return ResponseEntity.ok(shipmentRepository.findAvailableOffersForDriver(driverId));
    }

    @GetMapping("/available-jobs")
    public ResponseEntity<?> getAvailableJobs(@AuthenticationPrincipal Jwt jwt) {
        Long driverId = SecurityIdentity.userId(jwt);
        DriverProfile profile = driverCommissionService.reconcile(driverId);
        if (!isApprovedDriver(driverId) || profile == null) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body("Driver account is not approved");
        }
        if (!profile.isAvailable() || profile.isRestricted()) return ResponseEntity.ok(List.of());
        return ResponseEntity.ok(shipmentRepository.findAvailableOffersForDriver(driverId));
    }

    @PostMapping("/jobs/{shipmentId}/accept")
    @Transactional
    public ResponseEntity<?> acceptJob(@PathVariable Long shipmentId,
            @AuthenticationPrincipal Jwt jwt) {
        Long driverId = SecurityIdentity.userId(jwt);
        DriverProfile profile = driverProfileRepository.findByDriverId(driverId).orElse(null);
        if (!isApprovedDriver(driverId) || profile == null || !profile.isAvailable()) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body("Driver is offline");
        }
        Shipment shipment = shipmentRepository.findByIdForUpdate(shipmentId).orElse(null);
        if (shipment == null) return ResponseEntity.notFound().build();
        profile = driverCommissionService.reconcile(driverId);
        if (profile == null || !profile.isAvailable()) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body("Driver is offline");
        }
        if (profile.isRestricted()) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body("Pay outstanding commission before accepting jobs");
        }
        if (shipment.getStatus() != ShipmentStatus.PENDING || shipment.getDriver() != null) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body("Shipment is no longer available");
        }
        if (jobResponseRepository.findByShipmentIdAndDriverId(shipmentId, driverId)
                .filter(response -> response.getStatus() == DriverJobResponseStatus.REJECTED).isPresent()) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body("Shipment was already rejected");
        }

        User driver = userRepository.findById(driverId).orElse(null);
        if (driver == null) return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        shipment.setDriver(driver);
        shipment.setStatus(ShipmentStatus.ASSIGNED);
        Shipment assigned = shipmentRepository.save(shipment);
        DriverJobResponse response = jobResponseRepository
                .findByShipmentIdAndDriverId(shipmentId, driverId).orElseGet(DriverJobResponse::new);
        response.setShipment(assigned);
        response.setDriver(driver);
        response.setStatus(DriverJobResponseStatus.ACCEPTED);
        response.setRespondedAt(LocalDateTime.now());
        jobResponseRepository.save(response);

        var history = new com.mizigo.cargo_backend.model.TrackingHistory();
        history.setShipment(assigned);
        history.setStatus(ShipmentStatus.ASSIGNED);
        history.setLocation(assigned.getOrigin());
        trackingHistoryRepository.save(history);
        return ResponseEntity.ok(assigned);
    }

    @PostMapping("/jobs/{shipmentId}/reject")
    @Transactional
    public ResponseEntity<?> rejectJob(@PathVariable Long shipmentId,
            @AuthenticationPrincipal Jwt jwt) {
        Long driverId = SecurityIdentity.userId(jwt);
        DriverProfile profile = driverProfileRepository.findByDriverId(driverId).orElse(null);
        if (!isApprovedDriver(driverId) || profile == null || !profile.isAvailable()) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body("Driver is offline");
        }
        Shipment shipment = shipmentRepository.findByIdForUpdate(shipmentId).orElse(null);
        if (shipment == null) return ResponseEntity.notFound().build();
        if (shipment.getStatus() != ShipmentStatus.PENDING || shipment.getDriver() != null) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body("Shipment is no longer available");
        }

        User driver = userRepository.findById(driverId).orElse(null);
        if (driver == null) return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        DriverJobResponse response = jobResponseRepository
                .findByShipmentIdAndDriverId(shipmentId, driverId).orElseGet(DriverJobResponse::new);
        if (response.getStatus() == DriverJobResponseStatus.ACCEPTED) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body("Shipment was already accepted");
        }
        response.setShipment(shipment);
        response.setDriver(driver);
        response.setStatus(DriverJobResponseStatus.REJECTED);
        response.setRespondedAt(LocalDateTime.now());
        jobResponseRepository.save(response);
        return ResponseEntity.noContent().build();
    }

    private DriverProfileResponse toResponse(DriverProfile profile) {
        DriverApprovalStatus approvalStatus = profile.getDriver().getDriverApprovalStatus();
        if (approvalStatus == null) approvalStatus = DriverApprovalStatus.PENDING_APPROVAL;
        return toResponse(profile, approvalStatus);
    }

    private DriverProfileResponse toResponse(DriverProfile profile, DriverApprovalStatus approvalStatus) {
        String nida = secureFieldCipher.decrypt(profile.getNidaEncrypted(), "driver-nida");
        String license = secureFieldCipher.decrypt(profile.getLicenseNumberEncrypted(), "driver-license");
        return new DriverProfileResponse(nida, license, profile.getLicenseExpiry(),
            profile.isAvailable(), isProfileComplete(profile), profile.getFacePhotoEncrypted() != null,
            profile.getDriver().getFullName(), profile.getDriver().getPhone(),
            profile.getLicenseDocumentEncrypted() != null, profile.getNidaDocumentEncrypted() != null,
            approvalStatus,
            profile.getCommissionBalance(), profile.getUnpaidJobCount(),
                profile.getMaxUnpaidJobs(), profile.isRestricted(),
                commissionPaymentRepository.findFirstByDriverProfileIdAndVerifiedFalseAndRejectedFalseOrderByCreatedAtDesc(
                    profile.getId()).isPresent(),
            commissionPaymentRepository.findFirstByDriverProfileIdAndVerifiedFalseAndRejectedFalseOrderByCreatedAtDesc(
                    profile.getId()).map(CommissionPayment::getReference).orElse(null));
    }

            private CommissionPaymentResponse toPaymentResponse(CommissionPayment payment) {
            return new CommissionPaymentResponse(payment.getId(), payment.getAmount(), payment.getReference(),
                payment.isVerified(), payment.getCreatedAt());
            }

    private boolean isProfileComplete(DriverProfile profile) {
        return profile.getNidaEncrypted() != null && profile.getLicenseNumberEncrypted() != null
            && profile.getNidaDocumentEncrypted() != null
            && profile.getFacePhotoEncrypted() != null
                && profile.getLicenseDocumentEncrypted() != null
                && profile.getLicenseExpiry() != null
                && !profile.getLicenseExpiry().isBefore(LocalDate.now());
    }

    private boolean isApprovedDriver(Long driverId) {
        return userRepository.findById(driverId)
                .map(User::getDriverApprovalStatus)
                .filter(DriverApprovalStatus.APPROVED::equals)
                .isPresent();
    }

        private boolean isSupportedImage(byte[] data, String contentType) {
        if (contentType == null) return false;
        return switch (contentType) {
            case "image/jpeg" -> data.length >= 3 && (data[0] & 0xff) == 0xff
                && (data[1] & 0xff) == 0xd8 && (data[2] & 0xff) == 0xff;
            case "image/png" -> data.length >= 8 && (data[0] & 0xff) == 0x89
                && data[1] == 0x50 && data[2] == 0x4e && data[3] == 0x47;
            case "image/webp" -> data.length >= 12 && data[0] == 'R' && data[1] == 'I'
                && data[2] == 'F' && data[3] == 'F' && data[8] == 'W'
                && data[9] == 'E' && data[10] == 'B' && data[11] == 'P';
            default -> false;
        };
        }

    public record DriverProfileRequest(
            @NotBlank @Size(max = 150) String fullName,
            @NotBlank @Pattern(regexp = "[0-9+ -]{7,20}") String phone,
            @NotBlank @Pattern(regexp = "\\d{20}") String nidaNumber,
            @NotBlank @Pattern(regexp = "[A-Za-z0-9/ -]{3,80}") String licenseNumber,
            @NotNull @FutureOrPresent LocalDate licenseExpiry) {}

            public record FacePhotoRequest(@NotBlank @Size(max = 1_400_000) String base64Data,
                @NotBlank @Pattern(regexp = "image/(jpeg|png|webp)") String contentType) {}

            public record FacePhotoResponse(String contentType, String base64Data) {}

    public record DriverProfileResponse(String nidaNumber, String licenseNumber,
                LocalDate licenseExpiry, boolean available, boolean profileComplete,
                boolean hasFacePhoto, String fullName, String phone, boolean hasLicenseDocument,
                boolean hasNidaDocument, DriverApprovalStatus approvalStatus, Double commissionBalance,
                Integer unpaidJobCount, Integer maxUnpaidJobs,
                boolean isRestricted, boolean commissionPaymentPending,
                String commissionPaymentReference) {}

    public record CommissionPaymentRequest(@NotBlank @Size(max = 80) String reference) {}

    public record CommissionPaymentResponse(Long id, java.math.BigDecimal amount,
            String reference, boolean verified, LocalDateTime createdAt) {}

    public record AvailabilityRequest(@NotNull Boolean available) {}
}