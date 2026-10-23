package com.mizigo.cargo_backend.controller;

import com.mizigo.cargo_backend.model.*;
import com.mizigo.cargo_backend.repository.ShipmentRepository;
import com.mizigo.cargo_backend.repository.TrackingHistoryRepository;
import com.mizigo.cargo_backend.repository.UserRepository;
import com.mizigo.cargo_backend.repository.PaymentRepository;
import com.mizigo.cargo_backend.repository.DriverProfileRepository;
import com.mizigo.cargo_backend.repository.VehicleRepository;
import com.mizigo.cargo_backend.security.DeliveryCodeService;
import com.mizigo.cargo_backend.security.SecurityIdentity;
import com.mizigo.cargo_backend.model.PayoutStatus;
import com.mizigo.cargo_backend.service.AdminAuditService;
import com.mizigo.cargo_backend.service.AdminNotificationService;
import com.mizigo.cargo_backend.service.DriverCommissionService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;

@RestController
@RequestMapping("/api/shipments")
public class ShipmentController {

    @Autowired
    private ShipmentRepository shipmentRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TrackingHistoryRepository trackingHistoryRepository;

    @Autowired
    private VehicleRepository vehicleRepository;

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private DriverProfileRepository driverProfileRepository;

    @Autowired
    private DeliveryCodeService deliveryCodeService;

    @Autowired
    private AdminAuditService auditService;

    @Autowired
    private AdminNotificationService notificationService;

    @Autowired
    private DriverCommissionService driverCommissionService;

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public List<Shipment> getAllShipments() {
        return shipmentRepository.findAll();
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> getShipmentById(@PathVariable Long id,
            @AuthenticationPrincipal Jwt jwt) {
        return shipmentRepository.findById(id)
                .map(shipment -> canAccessShipment(jwt, shipment)
                        ? ResponseEntity.ok(shipment)
                        : ResponseEntity.status(HttpStatus.FORBIDDEN).build())
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/customer/{customerId}")
    @PreAuthorize("hasAnyRole('CUSTOMER', 'ADMIN')")
    public ResponseEntity<?> getShipmentsByCustomer(@PathVariable Long customerId,
            @AuthenticationPrincipal Jwt jwt) {
        if (!isAdmin(jwt) && (!isRole(jwt, "CUSTOMER")
                || !customerId.equals(SecurityIdentity.userId(jwt)))) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        return ResponseEntity.ok(shipmentRepository.findByCustomerId(customerId));
    }

    @GetMapping("/driver/{driverId}")
    public ResponseEntity<?> getShipmentsByDriver(@PathVariable Long driverId,
            @AuthenticationPrincipal Jwt jwt) {
        if (!isAdmin(jwt) && (!isRole(jwt, "DRIVER")
                || !driverId.equals(SecurityIdentity.userId(jwt)))) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        return ResponseEntity.ok(shipmentRepository.findByDriverId(driverId));
    }

        @PostMapping("/{id}/cancel")
        @org.springframework.transaction.annotation.Transactional
        public ResponseEntity<?> cancelCustomerShipment(@PathVariable Long id,
            @AuthenticationPrincipal Jwt jwt) {
        Shipment shipment = shipmentRepository.findByIdForUpdate(id).orElse(null);
        if (shipment == null) return ResponseEntity.notFound().build();
        if (!isRole(jwt, "CUSTOMER") || shipment.getCustomer() == null
            || !shipment.getCustomer().getId().equals(SecurityIdentity.userId(jwt))) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        if (shipment.getStatus() != ShipmentStatus.PENDING
            && shipment.getStatus() != ShipmentStatus.ASSIGNED) {
            return ResponseEntity.badRequest()
                .body("Huwezi kughairi mzigo uliopo njiani au uliokamilika.");
        }

        Long assignedDriverId = shipment.getDriver() == null ? null : shipment.getDriver().getId();
        shipment.setStatus(ShipmentStatus.CANCELLED);
        shipment.setCancelledAt(LocalDateTime.now());
        Shipment cancelled = shipmentRepository.save(shipment);

        TrackingHistory tracking = new TrackingHistory();
        tracking.setShipment(cancelled);
        tracking.setStatus(ShipmentStatus.CANCELLED);
        tracking.setLocation("Cancelled by customer");
        trackingHistoryRepository.save(tracking);

        auditService.record(SecurityIdentity.userId(jwt), "SHIPMENT_CANCELLED",
            "SHIPMENT", id.toString(), assignedDriverId == null
                ? "customer cancellation"
                : "customer cancellation; releasedDriverId=" + assignedDriverId);
        notificationService.notifyOnce("SHIPMENT_CANCELLED", NotificationPriority.MEDIUM,
            "Shipment " + id + " was cancelled by its customer."
                + (assignedDriverId == null ? "" : " Assigned driver " + assignedDriverId + " was released."),
            "SHIPMENT", id.toString(),
            "/admin-dashboard?section=shipments&shipmentId=" + id,
            "SHIPMENT_CANCELLED:" + id);

        return ResponseEntity.ok(cancelled);
        }

    @PostMapping
    @PreAuthorize("hasAnyRole('CUSTOMER', 'ADMIN')")
    @org.springframework.transaction.annotation.Transactional
    public ResponseEntity<?> createShipment(@Valid @RequestBody ShipmentRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        if (!isAdmin(jwt) && !request.getCustomerId().equals(SecurityIdentity.userId(jwt))) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        User customer = userRepository.findById(request.getCustomerId())
                .orElse(null);
        if (customer == null) {
            return ResponseEntity.badRequest().body("Mteja hajapatikana");
        }

        Shipment shipment = new Shipment();
        shipment.setCustomer(customer);
        shipment.setOrigin(request.getOrigin());
        shipment.setDestination(request.getDestination());
        shipment.setCargoType(request.getVehicleType() + ": " + request.getCargoType());
        BigDecimal weightKg = request.getWeightKg() == null
                ? BigDecimal.ONE
                : request.getWeightKg();
        shipment.setWeightKg(weightKg);
        shipment.setDistanceKm(request.getDistanceKm());
        shipment.setPrice(request.getDistanceKm() == null
                ? null
                : request.getDistanceKm().multiply(weightKg)
                        .multiply(new BigDecimal("1000"))
                        .add(new BigDecimal("5000"))
                        .setScale(0, RoundingMode.HALF_UP));
        shipment.setStatus(ShipmentStatus.PENDING);

        Shipment savedShipment = shipmentRepository.save(shipment);

        TrackingHistory tracking = new TrackingHistory();
        tracking.setShipment(savedShipment);
        tracking.setStatus(ShipmentStatus.PENDING);
        tracking.setLocation(request.getOrigin());
        trackingHistoryRepository.save(tracking);
        notificationService.notifyOnce("SHIPMENT_CREATED", NotificationPriority.MEDIUM,
                "Shipment " + savedShipment.getId() + " is available for driver assignment.",
                "SHIPMENT", savedShipment.getId().toString(),
                "/admin-dashboard?section=shipments&shipmentId=" + savedShipment.getId(),
                "SHIPMENT_CREATED:" + savedShipment.getId());

        return ResponseEntity.ok(savedShipment);
    }

    @PutMapping("/{id}/assign-driver/{driverId}")
    @PreAuthorize("hasRole('ADMIN')")
    @org.springframework.transaction.annotation.Transactional
    public ResponseEntity<?> assignDriver(@PathVariable Long id, @PathVariable Long driverId,
            @AuthenticationPrincipal Jwt jwt) {
        Shipment shipment = shipmentRepository.findById(id).orElse(null);
        User driver = userRepository.findById(driverId).orElse(null);

        if (shipment == null) return ResponseEntity.notFound().build();
        if (driver == null) return ResponseEntity.badRequest().body("Dereva hajapatikana");

        shipment.setDriver(driver);
        shipment.setStatus(ShipmentStatus.ASSIGNED);
        Shipment updated = shipmentRepository.save(shipment);
        auditService.record(SecurityIdentity.userId(jwt), "SHIPMENT_DRIVER_ASSIGNED",
                "SHIPMENT", id.toString(), "driverId=" + driverId);

        TrackingHistory tracking = new TrackingHistory();
        tracking.setShipment(updated);
        tracking.setStatus(ShipmentStatus.ASSIGNED);
        tracking.setLocation(shipment.getOrigin());
        trackingHistoryRepository.save(tracking);

        return ResponseEntity.ok(updated);
    }

    @PutMapping("/{id}/status")
    @org.springframework.transaction.annotation.Transactional
        public ResponseEntity<?> updateStatus(@PathVariable Long id,
            @Valid @RequestBody StatusUpdateRequest request, @AuthenticationPrincipal Jwt jwt) {
        Shipment shipment = shipmentRepository.findByIdForUpdate(id).orElse(null);
        if (shipment == null) return ResponseEntity.notFound().build();
        if (!isAdmin(jwt) && (!isRole(jwt, "DRIVER")
                || !isApprovedDriver(SecurityIdentity.userId(jwt))
                || shipment.getDriver() == null
                || !shipment.getDriver().getId().equals(SecurityIdentity.userId(jwt)))) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        ShipmentStatus previousStatus = shipment.getStatus();
        if (request.getStatus() == null || !isValidDriverTransition(shipment.getStatus(), request.getStatus())) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body("Invalid shipment status transition");
        }

        if (request.getStatus() == ShipmentStatus.DELIVERED) {
            if (deliveryCodeService.isLocked(shipment.getDeliveryOtpAttempts())) {
                return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body("Delivery code attempts exceeded");
            }
            if (!deliveryCodeService.matches(request.getDeliveryCode(), shipment.getDeliveryOtpHash(),
                    shipment.getDeliveryOtpExpiresAt(), shipment.getDeliveryOtpAttempts())) {
                shipment.setDeliveryOtpAttempts(shipment.getDeliveryOtpAttempts() + 1);
                shipmentRepository.save(shipment);
                return ResponseEntity.badRequest().body("Invalid or expired delivery code");
            }
        }

        if (request.getStatus() == ShipmentStatus.IN_TRANSIT
            && (shipment.getDeliveryOtpEncrypted() == null
            || shipment.getDeliveryOtpExpiresAt() == null
            || !shipment.getDeliveryOtpExpiresAt().isAfter(LocalDateTime.now()))) {
            DeliveryCodeService.IssuedCode issuedCode = deliveryCodeService.issue();
            shipment.setDeliveryOtpHash(issuedCode.hash());
            shipment.setDeliveryOtpEncrypted(issuedCode.encrypted());
            shipment.setDeliveryOtpExpiresAt(issuedCode.expiresAt());
            shipment.setDeliveryOtpAttempts(0);
        }

        if (request.getStatus() == ShipmentStatus.DELIVERED && shipment.getDriver() != null) {
            driverProfileRepository.findByDriverIdForUpdate(shipment.getDriver().getId());
        }

        shipment.setStatus(request.getStatus());
        if (request.getStatus() == ShipmentStatus.DELIVERED) {
            shipment.setDeliveredAt(LocalDateTime.now());
            shipment.setDeliveryOtpHash(null);
            shipment.setDeliveryOtpEncrypted(null);
            shipment.setDeliveryOtpExpiresAt(null);
        }
        Shipment updated = shipmentRepository.save(shipment);
        if (request.getStatus() == ShipmentStatus.DELIVERED && updated.getDriver() != null) {
            driverCommissionService.reconcile(updated.getDriver().getId());
        }
        auditService.record(SecurityIdentity.userId(jwt), "SHIPMENT_STATUS_CHANGED",
                "SHIPMENT", id.toString(), "from=" + previousStatus + ";to=" + request.getStatus());

        TrackingHistory tracking = new TrackingHistory();
        tracking.setShipment(updated);
        tracking.setStatus(request.getStatus());
        tracking.setLocation(request.getLocation());
        trackingHistoryRepository.save(tracking);

        if (request.getStatus() == ShipmentStatus.DELIVERED) {
            paymentRepository.findByShipmentId(id).ifPresent(payment -> {
                if (payment.getStatus() == PaymentStatus.PAID) {
                    payment.setPayoutStatus(PayoutStatus.READY_FOR_PAYOUT);
                } else if (payment.getStatus() == PaymentStatus.FAILED) {
                    payment.setPayoutStatus(PayoutStatus.FAILED);
                } else {
                    payment.setPayoutStatus(PayoutStatus.WAITING_FOR_PAYMENT);
                }
                paymentRepository.save(payment);
            });
        }

        return ResponseEntity.ok(updated);
    }

    @GetMapping("/{id}/delivery-code")
    public ResponseEntity<?> getCustomerDeliveryCode(@PathVariable Long id,
            @AuthenticationPrincipal Jwt jwt) {
        Shipment shipment = shipmentRepository.findById(id).orElse(null);
        if (shipment == null) return ResponseEntity.notFound().build();
        if (!isRole(jwt, "CUSTOMER") || shipment.getCustomer() == null
                || !shipment.getCustomer().getId().equals(SecurityIdentity.userId(jwt))) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        if ((shipment.getStatus() != ShipmentStatus.ASSIGNED
            && shipment.getStatus() != ShipmentStatus.PICKED_UP
            && shipment.getStatus() != ShipmentStatus.IN_TRANSIT)
                || shipment.getDeliveryOtpEncrypted() == null
                || shipment.getDeliveryOtpExpiresAt() == null
                || !shipment.getDeliveryOtpExpiresAt().isAfter(LocalDateTime.now())) {
            return ResponseEntity.status(HttpStatus.GONE).build();
        }
        return ResponseEntity.ok(new DeliveryCodeResponse(
                deliveryCodeService.reveal(shipment.getDeliveryOtpEncrypted()),
                shipment.getDeliveryOtpExpiresAt()));
    }

    @PostMapping("/{id}/delivery-code")
    @org.springframework.transaction.annotation.Transactional
    public ResponseEntity<?> generateOrRevealCustomerDeliveryCode(@PathVariable Long id,
            @AuthenticationPrincipal Jwt jwt) {
        Shipment shipment = shipmentRepository.findByIdForUpdate(id).orElse(null);
        if (shipment == null) return ResponseEntity.notFound().build();
        if (!isRole(jwt, "CUSTOMER") || shipment.getCustomer() == null
                || !shipment.getCustomer().getId().equals(SecurityIdentity.userId(jwt))) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        if (shipment.getStatus() != ShipmentStatus.ASSIGNED
                && shipment.getStatus() != ShipmentStatus.PICKED_UP
                && shipment.getStatus() != ShipmentStatus.IN_TRANSIT) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body("Shipment is not active");
        }

        if (shipment.getDeliveryOtpEncrypted() == null
                || shipment.getDeliveryOtpExpiresAt() == null
                || !shipment.getDeliveryOtpExpiresAt().isAfter(LocalDateTime.now())) {
            DeliveryCodeService.IssuedCode issuedCode = deliveryCodeService.issue();
            shipment.setDeliveryOtpHash(issuedCode.hash());
            shipment.setDeliveryOtpEncrypted(issuedCode.encrypted());
            shipment.setDeliveryOtpExpiresAt(issuedCode.expiresAt());
            shipment.setDeliveryOtpAttempts(0);
            shipmentRepository.save(shipment);
            auditService.record(SecurityIdentity.userId(jwt), "SHIPMENT_DELIVERY_CODE_GENERATED",
                    "SHIPMENT", id.toString(), "status=" + shipment.getStatus());
        }

        return ResponseEntity.ok(new DeliveryCodeResponse(
                deliveryCodeService.reveal(shipment.getDeliveryOtpEncrypted()),
                shipment.getDeliveryOtpExpiresAt()));
    }

    @PostMapping("/{id}/location")
    public ResponseEntity<?> updateLocation(@PathVariable Long id,
            @Valid @RequestBody LocationUpdateRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        Shipment shipment = shipmentRepository.findById(id).orElse(null);
        if (shipment == null) return ResponseEntity.notFound().build();
        if (!isRole(jwt, "DRIVER") || !isApprovedDriver(SecurityIdentity.userId(jwt))
                || shipment.getDriver() == null
                || !shipment.getDriver().getId().equals(SecurityIdentity.userId(jwt))) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        if (shipment.getStatus() != ShipmentStatus.ASSIGNED
                && shipment.getStatus() != ShipmentStatus.IN_TRANSIT) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body("Shipment is not assigned or in transit");
        }
        TrackingHistory latestLocation = trackingHistoryRepository
            .findFirstByShipmentIdAndLatitudeIsNotNullAndLongitudeIsNotNullOrderByTimestampDesc(id)
            .orElse(null);
        if (latestLocation != null
            && latestLocation.getTimestamp().isAfter(LocalDateTime.now().minusSeconds(10))) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).build();
        }

        TrackingHistory location = new TrackingHistory();
        location.setShipment(shipment);
        location.setStatus(shipment.getStatus());
        location.setLatitude(request.latitude());
        location.setLongitude(request.longitude());
        return ResponseEntity.ok(trackingHistoryRepository.save(location));
    }

    @GetMapping("/{id}/location")
    public ResponseEntity<?> getLatestLocation(@PathVariable Long id,
            @AuthenticationPrincipal Jwt jwt) {
        Shipment shipment = shipmentRepository.findById(id).orElse(null);
        if (shipment == null) return ResponseEntity.notFound().build();
        if (!canAccessShipment(jwt, shipment)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        TrackingHistory latestLocation = trackingHistoryRepository
                .findFirstByShipmentIdAndLatitudeIsNotNullAndLongitudeIsNotNullOrderByTimestampDesc(id)
                .orElse(null);
        return latestLocation == null
                ? ResponseEntity.noContent().build()
                : ResponseEntity.ok(new DriverLocation(
                        latestLocation.getLatitude(), latestLocation.getLongitude(),
                        latestLocation.getTimestamp()));
    }

    @GetMapping("/{id}/driver-info")
    public ResponseEntity<?> getShipmentDriverInfo(@PathVariable Long id,
            @AuthenticationPrincipal Jwt jwt) {
        Shipment shipment = shipmentRepository.findById(id).orElse(null);
        if (shipment == null) return ResponseEntity.notFound().build();
        if (!canAccessShipment(jwt, shipment)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        if (shipment.getStatus() != ShipmentStatus.ASSIGNED
                && shipment.getStatus() != ShipmentStatus.PICKED_UP
                && shipment.getStatus() != ShipmentStatus.IN_TRANSIT) {
            return ResponseEntity.notFound().build();
        }

        User driver = shipment.getDriver();
        if (driver == null) return ResponseEntity.notFound().build();
        String vehicleRegistrationNumber = vehicleRepository.findByDriverId(driver.getId()).stream()
                .min(java.util.Comparator.comparing(
                        Vehicle::getCreatedAt, java.util.Comparator.nullsLast(java.util.Comparator.naturalOrder())))
                .map(Vehicle::getPlateNumber)
                .orElse(null);
        return ResponseEntity.ok(new ShipmentDriverInfo(
                driver.getFullName(), driver.getPhone(), vehicleRegistrationNumber));
    }

    @GetMapping("/{id}/tracking")
    public ResponseEntity<?> getTrackingHistory(@PathVariable Long id,
            @AuthenticationPrincipal Jwt jwt) {
        Shipment shipment = shipmentRepository.findById(id).orElse(null);
        if (shipment == null) return ResponseEntity.notFound().build();
        if (!canAccessShipment(jwt, shipment)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        return ResponseEntity.ok(trackingHistoryRepository.findByShipmentIdOrderByTimestampDesc(id));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> deleteShipment(@PathVariable Long id) {
        if (!shipmentRepository.existsById(id)) {
            return ResponseEntity.notFound().build();
        }
        shipmentRepository.deleteById(id);
        return ResponseEntity.noContent().build();
    }

    private boolean isAdmin(Jwt jwt) {
        return isRole(jwt, "ADMIN");
    }

    private boolean isRole(Jwt jwt, String role) {
        return role.equals(jwt.getClaimAsString("role"));
    }

    private boolean isApprovedDriver(Long driverId) {
        return userRepository.findById(driverId)
                .map(User::getDriverApprovalStatus)
                .filter(DriverApprovalStatus.APPROVED::equals)
                .isPresent();
    }

    private boolean canAccessShipment(Jwt jwt, Shipment shipment) {
        if (isAdmin(jwt)) return true;
        Long userId = SecurityIdentity.userId(jwt);
        if (isRole(jwt, "CUSTOMER")) {
            return shipment.getCustomer().getId().equals(userId);
        }
        return isRole(jwt, "DRIVER") && shipment.getDriver() != null
                && shipment.getDriver().getId().equals(userId);
    }

    private boolean isValidDriverTransition(ShipmentStatus current, ShipmentStatus next) {
        return switch (current) {
            case ASSIGNED -> next == ShipmentStatus.PICKED_UP;
            case PICKED_UP -> next == ShipmentStatus.IN_TRANSIT;
            case IN_TRANSIT -> next == ShipmentStatus.DELIVERED;
            default -> false;
        };
    }

    public static class ShipmentRequest {
        @NotNull
        private Long customerId;
        @NotBlank
        @Size(max = 255)
        private String origin;
        @NotBlank
        @Size(max = 255)
        private String destination;
        @NotBlank
        @Size(max = 100)
        private String cargoType;
        @NotBlank
        @Pattern(regexp = "GARI_KUBWA|KIRIKUU|TOYO|TOYO_NDOGO_UMEME")
        private String vehicleType;
        @Positive
        @Digits(integer = 8, fraction = 2)
        private java.math.BigDecimal weightKg;
        @Positive
        @Digits(integer = 8, fraction = 2)
        private java.math.BigDecimal distanceKm;
        @PositiveOrZero
        @Digits(integer = 10, fraction = 2)
        private java.math.BigDecimal price;

        public Long getCustomerId() { return customerId; }
        public void setCustomerId(Long customerId) { this.customerId = customerId; }

        public String getOrigin() { return origin; }
        public void setOrigin(String origin) { this.origin = origin; }

        public String getDestination() { return destination; }
        public void setDestination(String destination) { this.destination = destination; }

        public String getCargoType() { return cargoType; }
        public void setCargoType(String cargoType) { this.cargoType = cargoType; }

        public String getVehicleType() { return vehicleType; }
        public void setVehicleType(String vehicleType) { this.vehicleType = vehicleType; }

        public java.math.BigDecimal getWeightKg() { return weightKg; }
        public void setWeightKg(java.math.BigDecimal weightKg) { this.weightKg = weightKg; }

        public java.math.BigDecimal getDistanceKm() { return distanceKm; }
        public void setDistanceKm(java.math.BigDecimal distanceKm) { this.distanceKm = distanceKm; }

        public java.math.BigDecimal getPrice() { return price; }
        public void setPrice(java.math.BigDecimal price) { this.price = price; }
    }

    public record DriverLocation(BigDecimal driverLatitude, BigDecimal driverLongitude,
            LocalDateTime timestamp) {}

    public record ShipmentDriverInfo(String driverName, String phoneNumber,
            String vehicleRegistrationNumber) {}

    public static class StatusUpdateRequest {
        private ShipmentStatus status;
        @Size(max = 255)
        private String location;
        @Pattern(regexp = "\\d{6}")
        private String deliveryCode;

        public ShipmentStatus getStatus() { return status; }
        public void setStatus(ShipmentStatus status) { this.status = status; }

        public String getLocation() { return location; }
        public void setLocation(String location) { this.location = location; }
        public String getDeliveryCode() { return deliveryCode; }
        public void setDeliveryCode(String deliveryCode) { this.deliveryCode = deliveryCode; }
    }

    public record LocationUpdateRequest(
            @NotNull @DecimalMin("-90.0") @DecimalMax("90.0") @Digits(integer = 3, fraction = 7) BigDecimal latitude,
            @NotNull @DecimalMin("-180.0") @DecimalMax("180.0") @Digits(integer = 3, fraction = 7) BigDecimal longitude) {}

    public record DeliveryCodeResponse(String code, LocalDateTime expiresAt) {}
}