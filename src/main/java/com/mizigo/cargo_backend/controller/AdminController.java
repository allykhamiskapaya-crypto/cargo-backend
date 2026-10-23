package com.mizigo.cargo_backend.controller;

import com.mizigo.cargo_backend.model.DriverApprovalStatus;
import com.mizigo.cargo_backend.model.DriverProfile;
import com.mizigo.cargo_backend.model.AdminAuditLog;
import com.mizigo.cargo_backend.model.AdminNotification;
import com.mizigo.cargo_backend.model.Complaint;
import com.mizigo.cargo_backend.model.ComplaintStatus;
import com.mizigo.cargo_backend.model.CommissionPayment;
import com.mizigo.cargo_backend.model.NotificationPriority;
import com.mizigo.cargo_backend.model.Shipment;
import com.mizigo.cargo_backend.model.ShipmentStatus;
import com.mizigo.cargo_backend.model.User;
import com.mizigo.cargo_backend.model.UserRole;
import com.mizigo.cargo_backend.model.Vehicle;
import com.mizigo.cargo_backend.repository.AdminAuditLogRepository;
import com.mizigo.cargo_backend.repository.AdminNotificationRepository;
import com.mizigo.cargo_backend.repository.ComplaintRepository;
import com.mizigo.cargo_backend.repository.CommissionPaymentRepository;
import com.mizigo.cargo_backend.repository.DriverProfileRepository;
import com.mizigo.cargo_backend.repository.ShipmentRepository;
import com.mizigo.cargo_backend.repository.TrackingHistoryRepository;
import com.mizigo.cargo_backend.repository.UserRepository;
import com.mizigo.cargo_backend.repository.VehicleRepository;
import com.mizigo.cargo_backend.security.SecureFieldCipher;
import com.mizigo.cargo_backend.security.SecurityIdentity;
import com.mizigo.cargo_backend.service.AdminAuditService;
import com.mizigo.cargo_backend.service.AdminNotificationService;
import com.mizigo.cargo_backend.service.DriverCommissionService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.LocalDateTime;
import java.util.Locale;
import java.util.List;
import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin")
@PreAuthorize("hasRole('ADMIN')")
public class AdminController {

    private final UserRepository userRepository;
    private final DriverProfileRepository driverProfileRepository;
    private final VehicleRepository vehicleRepository;
    private final SecureFieldCipher secureFieldCipher;
    private final ShipmentRepository shipmentRepository;
    private final TrackingHistoryRepository trackingHistoryRepository;
    private final ComplaintRepository complaintRepository;
        private final CommissionPaymentRepository commissionPaymentRepository;
    private final AdminNotificationRepository notificationRepository;
    private final AdminAuditLogRepository auditLogRepository;
    private final AdminNotificationService notificationService;
    private final AdminAuditService auditService;
        private final DriverCommissionService driverCommissionService;
    private final DataSource dataSource;
    private final int delayThresholdHours;

    public AdminController(UserRepository userRepository,
            DriverProfileRepository driverProfileRepository,
            VehicleRepository vehicleRepository,
            SecureFieldCipher secureFieldCipher,
            ShipmentRepository shipmentRepository,
            TrackingHistoryRepository trackingHistoryRepository,
            ComplaintRepository complaintRepository,
            CommissionPaymentRepository commissionPaymentRepository,
            AdminNotificationRepository notificationRepository,
            AdminAuditLogRepository auditLogRepository,
            AdminNotificationService notificationService,
            AdminAuditService auditService,
            DriverCommissionService driverCommissionService,
            DataSource dataSource,
            @Value("${app.admin.shipment-delay-hours:24}") int delayThresholdHours) {
        this.userRepository = userRepository;
        this.driverProfileRepository = driverProfileRepository;
        this.vehicleRepository = vehicleRepository;
        this.secureFieldCipher = secureFieldCipher;
        this.shipmentRepository = shipmentRepository;
        this.trackingHistoryRepository = trackingHistoryRepository;
        this.complaintRepository = complaintRepository;
        this.commissionPaymentRepository = commissionPaymentRepository;
        this.notificationRepository = notificationRepository;
        this.auditLogRepository = auditLogRepository;
        this.notificationService = notificationService;
        this.auditService = auditService;
        this.driverCommissionService = driverCommissionService;
        this.dataSource = dataSource;
        this.delayThresholdHours = delayThresholdHours;
    }

    @GetMapping("/overview")
    public AdminOverview getOverview() {
        LocalDateTime today = LocalDate.now().atStartOfDay();
        LocalDateTime tomorrow = today.plusDays(1);
        LocalDateTime delayThreshold = LocalDateTime.now().minusHours(delayThresholdHours);
        List<Shipment> activeShipments = shipmentRepository.findAll().stream()
                .filter(shipment -> isActive(shipment.getStatus()))
                .toList();
        List<Shipment> delayedShipments = activeShipments.stream()
                .filter(shipment -> shipment.getUpdatedAt().isBefore(delayThreshold))
                .toList();
        delayedShipments.forEach(shipment -> notificationService.notifyOnce(
                "SHIPMENT_DELAYED", NotificationPriority.HIGH,
                "Shipment " + shipment.getId() + " has had no update for "
                        + delayThresholdHours + " hours.",
                "SHIPMENT", shipment.getId().toString(),
                "/admin-dashboard?section=shipments&shipmentId=" + shipment.getId(),
                "SHIPMENT_DELAYED:" + shipment.getId()));

        List<User> drivers = userRepository.findByRole(UserRole.DRIVER);
        long pendingApprovals = drivers.stream()
                .filter(driver -> approvalStatus(driver) == DriverApprovalStatus.PENDING_APPROVAL).count();
        long completedToday = shipmentRepository.findByStatus(ShipmentStatus.DELIVERED).stream()
                .filter(shipment -> shipment.getDeliveredAt() != null
                        && !shipment.getDeliveredAt().isBefore(today)
                        && shipment.getDeliveredAt().isBefore(tomorrow)).count();
        long unresolvedComplaints = complaintRepository.findAll().stream()
                .filter(complaint -> complaint.getStatus() == ComplaintStatus.OPEN
                        || complaint.getStatus() == ComplaintStatus.IN_REVIEW)
                .count();
        return new AdminOverview(drivers.size(), pendingApprovals, activeShipments.size(),
                completedToday, delayedShipments.size(), unresolvedComplaints,
                notificationRepository.countByReadFalse(), delayThresholdHours);
    }

    @PutMapping("/driver-commission-payments/{paymentId}/verify")
    @Transactional
    public ResponseEntity<?> verifyCommissionPayment(@PathVariable Long paymentId) {
        CommissionPayment payment = commissionPaymentRepository.findById(paymentId).orElse(null);
        if (payment == null) return ResponseEntity.notFound().build();
                if (payment.isVerified() || payment.isRejected()) {
                        return ResponseEntity.status(HttpStatus.CONFLICT).body("Payment is no longer pending");
                }
        DriverProfile profile = driverCommissionService.reconcile(payment.getDriverProfile().getDriver().getId());
        if (profile == null) return ResponseEntity.notFound().build();
        java.math.BigDecimal currentBalance = java.math.BigDecimal.valueOf(profile.getCommissionBalance())
                .setScale(2, java.math.RoundingMode.HALF_UP);
        if (currentBalance.compareTo(payment.getAmount()) != 0) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body("Commission balance changed; submit a payment for the current balance");
        }
        payment.setVerified(true);
        payment.setVerifiedAt(LocalDateTime.now());
        commissionPaymentRepository.save(payment);
        profile.setCommissionBalance(0.0);
        profile.setUnpaidJobCount(0);
        profile.setRestricted(false);
        driverProfileRepository.save(profile);
        return ResponseEntity.ok(new DriverController.CommissionPaymentResponse(
                payment.getId(), payment.getAmount(), payment.getReference(), true, payment.getCreatedAt()));
    }

        @GetMapping("/driver-commission-payments")
        @Transactional(readOnly = true)
        public List<AdminCommissionPaymentResponse> getPendingCommissionPayments() {
                return commissionPaymentRepository.findByVerifiedFalseAndRejectedFalseOrderByCreatedAtAsc().stream()
                                .map(payment -> new AdminCommissionPaymentResponse(payment.getId(),
                                                payment.getDriverProfile().getDriver().getId(),
                                                payment.getDriverProfile().getDriver().getFullName(), payment.getAmount(),
                                                payment.getReference(), payment.getCreatedAt()))
                                .toList();
        }

        @PutMapping("/driver-commission-payments/{paymentId}/reject")
        @Transactional
        public ResponseEntity<?> rejectCommissionPayment(@PathVariable Long paymentId) {
                CommissionPayment payment = commissionPaymentRepository.findById(paymentId).orElse(null);
                if (payment == null) return ResponseEntity.notFound().build();
                if (payment.isVerified() || payment.isRejected()) {
                        return ResponseEntity.status(HttpStatus.CONFLICT).body("Payment is no longer pending");
                }
                payment.setRejected(true);
                commissionPaymentRepository.save(payment);
                return ResponseEntity.noContent().build();
        }

        public record AdminCommissionPaymentResponse(Long id, Long driverId, String driverName,
                        java.math.BigDecimal amount, String reference, LocalDateTime submittedAt) {}

    @GetMapping("/shipments")
    @Transactional(readOnly = true)
    public List<ShipmentSummary> getShipments(
            @RequestParam(required = false) ShipmentStatus status,
            @RequestParam(required = false) String query,
            @RequestParam(defaultValue = "false") boolean delayedOnly) {
        LocalDateTime delayThreshold = LocalDateTime.now().minusHours(delayThresholdHours);
        String normalizedQuery = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        return shipmentRepository.findAll().stream()
                .filter(shipment -> status == null || shipment.getStatus() == status)
                .filter(shipment -> !delayedOnly || (isActive(shipment.getStatus())
                        && shipment.getUpdatedAt().isBefore(delayThreshold)))
                .filter(shipment -> normalizedQuery.isEmpty()
                        || shipmentMatches(shipment, normalizedQuery))
                .map(this::toShipmentSummary)
                .toList();
    }

    @GetMapping("/shipments/{shipmentId}/history")
    public ResponseEntity<?> getShipmentHistory(@PathVariable Long shipmentId) {
        if (!shipmentRepository.existsById(shipmentId)) return ResponseEntity.notFound().build();
        return ResponseEntity.ok(trackingHistoryRepository
                .findByShipmentIdOrderByTimestampDesc(shipmentId).stream()
                .map(history -> new ShipmentHistoryEntry(history.getStatus(), history.getTimestamp(),
                        history.getLocation(), history.getLatitude(), history.getLongitude()))
                .toList());
    }

    @GetMapping("/notifications")
    public NotificationFeed getNotifications() {
        return new NotificationFeed(notificationRepository.countByReadFalse(),
                notificationRepository.findTop100ByOrderByTimestampDesc().stream()
                        .map(this::toNotification).toList());
    }

    @PatchMapping("/notifications/{notificationId}/read")
    public ResponseEntity<?> markNotificationRead(@PathVariable Long notificationId) {
        AdminNotification notification = notificationRepository.findById(notificationId).orElse(null);
        if (notification == null) return ResponseEntity.notFound().build();
        notification.setRead(true);
        notificationRepository.save(notification);
        return ResponseEntity.ok(toNotification(notification));
    }

    @PatchMapping("/notifications/read-all")
    @Transactional
    public MarkAllReadResult markAllNotificationsRead() {
        return new MarkAllReadResult(notificationRepository.markAllRead());
    }

    @GetMapping("/audit-logs")
    public List<AuditEntry> getAuditLogs() {
        return auditLogRepository.findTop100ByOrderByTimestampDesc().stream()
                .map(this::toAuditEntry).toList();
    }

    @GetMapping("/health")
    public SystemHealth getSystemHealth() {
        String databaseStatus;
        try (Connection connection = dataSource.getConnection()) {
            databaseStatus = connection.isValid(2) ? "UP" : "DEGRADED";
        } catch (SQLException exception) {
            databaseStatus = "DOWN";
        }
        return new SystemHealth("UP", databaseStatus, "UNVERIFIED", null);
    }

    @GetMapping("/complaints")
    @Transactional(readOnly = true)
    public List<ComplaintSummary> getComplaints(
            @RequestParam(required = false) ComplaintStatus status) {
        List<Complaint> complaints = status == null
                ? complaintRepository.findAllByOrderByCreatedAtDesc()
                : complaintRepository.findByStatusOrderByCreatedAtDesc(status);
        return complaints.stream().map(this::toComplaintSummary).toList();
    }

    @GetMapping("/complaints/{complaintId}")
    @Transactional(readOnly = true)
    public ResponseEntity<?> getComplaint(@PathVariable Long complaintId) {
        Complaint complaint = complaintRepository.findById(complaintId).orElse(null);
        if (complaint == null) return ResponseEntity.notFound().build();
        List<ShipmentHistoryEntry> history = trackingHistoryRepository
                .findByShipmentIdOrderByTimestampDesc(complaint.getShipment().getId()).stream()
                .map(event -> new ShipmentHistoryEntry(event.getStatus(), event.getTimestamp(),
                        event.getLocation(), event.getLatitude(), event.getLongitude()))
                .toList();
        return ResponseEntity.ok(new ComplaintDetail(toComplaintSummary(complaint), history));
    }

    @GetMapping("/complaints/{complaintId}/evidence")
    public ResponseEntity<?> getComplaintEvidence(@PathVariable Long complaintId) {
        Complaint complaint = complaintRepository.findById(complaintId).orElse(null);
        if (complaint == null) return ResponseEntity.notFound().build();
        if (complaint.getEvidenceEncrypted() == null) return ResponseEntity.noContent().build();
        String purpose = "complaint-evidence-" + complaintId;
        return ResponseEntity.ok(new ComplaintEvidence(complaint.getEvidenceContentType(),
                secureFieldCipher.decrypt(complaint.getEvidenceEncrypted(), purpose)));
    }

    @PutMapping("/complaints/{complaintId}")
    @Transactional
    public ResponseEntity<?> updateComplaint(@PathVariable Long complaintId,
            @Valid @RequestBody ComplaintUpdateRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        Complaint complaint = complaintRepository.findById(complaintId).orElse(null);
        if (complaint == null) return ResponseEntity.notFound().build();
        if ((request.status() == ComplaintStatus.RESOLVED
                || request.status() == ComplaintStatus.REJECTED)
                && (request.resolutionNote() == null || request.resolutionNote().isBlank())) {
            return ResponseEntity.badRequest().body("A resolution note is required for a closed complaint");
        }
        ComplaintStatus previousStatus = complaint.getStatus();
        Long actorId = SecurityIdentity.userId(jwt);
        User reviewer = userRepository.findById(actorId).orElse(null);
        if (reviewer == null || reviewer.getRole() != UserRole.ADMIN) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        complaint.setStatus(request.status());
        complaint.setAssignedAdmin(reviewer);
        complaint.setResolutionNote(request.resolutionNote());
        complaint = complaintRepository.save(complaint);
        auditService.record(actorId, "COMPLAINT_STATUS_CHANGED", "COMPLAINT",
                complaintId.toString(), "from=" + previousStatus + ";to=" + request.status());
        return ResponseEntity.ok(toComplaintSummary(complaint));
    }

    @GetMapping("/complaints/{complaintId}/actions")
    public ResponseEntity<?> getComplaintActions(@PathVariable Long complaintId) {
        if (!complaintRepository.existsById(complaintId)) return ResponseEntity.notFound().build();
        return ResponseEntity.ok(auditLogRepository
                .findTop100ByRecordTypeAndRecordIdOrderByTimestampDesc("COMPLAINT", complaintId.toString()).stream()
                .map(this::toAuditEntry).toList());
    }

    @GetMapping("/drivers")
    public List<DriverSummary> getDrivers(
            @RequestParam(required = false) DriverApprovalStatus status) {
        return userRepository.findByRole(UserRole.DRIVER).stream()
                .filter(driver -> status == null || approvalStatus(driver) == status)
                .map(driver -> new DriverSummary(
                        driver.getId(), driver.getFullName(), driver.getEmail(), driver.getPhone(),
                        approvalStatus(driver), driver.getCreatedAt()))
                .toList();
    }

    @GetMapping("/drivers/{driverId}")
    public ResponseEntity<?> getDriver(@PathVariable Long driverId) {
        User driver = userRepository.findById(driverId).orElse(null);
        if (driver == null || driver.getRole() != UserRole.DRIVER) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(toDriverReview(driver));
    }

    @GetMapping("/drivers/{driverId}/license-document")
    public ResponseEntity<?> getLicenseDocument(@PathVariable Long driverId) {
        if (!isDriver(driverId)) return ResponseEntity.notFound().build();
        DriverProfile profile = driverProfileRepository.findByDriverId(driverId).orElse(null);
        if (profile == null || profile.getLicenseDocumentEncrypted() == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(new LicenseDocument(
                profile.getLicenseDocumentContentType(),
                secureFieldCipher.decrypt(profile.getLicenseDocumentEncrypted(), "driver-license-document")));
    }

        @GetMapping("/drivers/{driverId}/nida-document")
        public ResponseEntity<?> getNidaDocument(@PathVariable Long driverId) {
                if (!isDriver(driverId)) return ResponseEntity.notFound().build();
                DriverProfile profile = driverProfileRepository.findByDriverId(driverId).orElse(null);
                if (profile == null || profile.getNidaDocumentEncrypted() == null) {
                        return ResponseEntity.notFound().build();
                }
                return ResponseEntity.ok(new LicenseDocument(
                                profile.getNidaDocumentContentType(),
                                secureFieldCipher.decrypt(profile.getNidaDocumentEncrypted(), "driver-nida-document")));
        }

    @PutMapping("/drivers/{driverId}/approve")
    @Transactional
    public ResponseEntity<?> approveDriver(@PathVariable Long driverId,
            @AuthenticationPrincipal Jwt jwt) {
        User driver = userRepository.findById(driverId).orElse(null);
        if (driver == null || driver.getRole() != UserRole.DRIVER) {
            return ResponseEntity.notFound().build();
        }
        DriverApprovalStatus previousStatus = approvalStatus(driver);
        driver.setDriverApprovalStatus(DriverApprovalStatus.APPROVED);
        userRepository.save(driver);
        auditService.record(SecurityIdentity.userId(jwt), "DRIVER_APPROVED",
                "DRIVER", driverId.toString(), "from=" + previousStatus + ";to=APPROVED");
        return ResponseEntity.ok(new ApprovalResult(driver.getId(), approvalStatus(driver)));
    }

    @PutMapping("/drivers/{driverId}/reject")
    @Transactional
    public ResponseEntity<?> rejectDriver(@PathVariable Long driverId,
            @AuthenticationPrincipal Jwt jwt) {
        User driver = userRepository.findById(driverId).orElse(null);
        if (driver == null || driver.getRole() != UserRole.DRIVER) {
            return ResponseEntity.notFound().build();
        }
        DriverApprovalStatus previousStatus = approvalStatus(driver);
        driver.setDriverApprovalStatus(DriverApprovalStatus.REJECTED);
        userRepository.save(driver);
        driverProfileRepository.findByDriverId(driverId).ifPresent(profile -> {
            profile.setAvailable(false);
            driverProfileRepository.save(profile);
        });
        auditService.record(SecurityIdentity.userId(jwt), "DRIVER_REJECTED",
                "DRIVER", driverId.toString(), "from=" + previousStatus + ";to=REJECTED");
        return ResponseEntity.ok(new ApprovalResult(driver.getId(), approvalStatus(driver)));
    }

    private DriverReview toDriverReview(User driver) {
        DriverProfile profile = driverProfileRepository.findByDriverId(driver.getId()).orElse(null);
        List<VehicleReview> vehicles = vehicleRepository.findByDriverId(driver.getId()).stream()
                .map(vehicle -> new VehicleReview(
                        vehicle.getId(), vehicle.getPlateNumber(), vehicle.getVehicleType(),
                        vehicle.getCapacityKg(), hasFourPhotos(vehicle)))
                .toList();
        return new DriverReview(
                driver.getId(), driver.getFullName(), driver.getEmail(), driver.getPhone(),
                approvalStatus(driver), driver.getCreatedAt(),
                profile == null ? null : secureFieldCipher.decrypt(profile.getNidaEncrypted(), "driver-nida"),
                profile == null ? null : secureFieldCipher.decrypt(profile.getLicenseNumberEncrypted(), "driver-license"),
                profile == null ? null : profile.getLicenseExpiry(),
                profile != null && profile.getFacePhotoEncrypted() != null,
                profile != null && profile.getLicenseDocumentEncrypted() != null,
                profile != null && profile.getNidaDocumentEncrypted() != null,
                vehicles);
    }

    private boolean hasFourPhotos(Vehicle vehicle) {
        return vehicle.getFrontPhotoEncrypted() != null
                && vehicle.getRearPhotoEncrypted() != null
                && vehicle.getRightPhotoEncrypted() != null
                && vehicle.getLeftPhotoEncrypted() != null;
    }

    private DriverApprovalStatus approvalStatus(User driver) {
        return driver.getDriverApprovalStatus() == null
                ? DriverApprovalStatus.PENDING_APPROVAL
                : driver.getDriverApprovalStatus();
    }

    private boolean isDriver(Long driverId) {
        return userRepository.findById(driverId)
                .filter(user -> user.getRole() == UserRole.DRIVER)
                .isPresent();
    }

    private boolean isActive(ShipmentStatus status) {
        return status == ShipmentStatus.ASSIGNED || status == ShipmentStatus.PICKED_UP
                || status == ShipmentStatus.IN_TRANSIT;
    }

    private boolean shipmentMatches(Shipment shipment, String query) {
        return shipment.getId().toString().contains(query)
                || shipment.getOrigin().toLowerCase(Locale.ROOT).contains(query)
                || shipment.getDestination().toLowerCase(Locale.ROOT).contains(query)
                || shipment.getCustomer().getFullName().toLowerCase(Locale.ROOT).contains(query)
                || (shipment.getDriver() != null
                        && shipment.getDriver().getFullName().toLowerCase(Locale.ROOT).contains(query));
    }

    private ShipmentSummary toShipmentSummary(Shipment shipment) {
        boolean delayed = isActive(shipment.getStatus())
                && shipment.getUpdatedAt().isBefore(LocalDateTime.now().minusHours(delayThresholdHours));
        return new ShipmentSummary(shipment.getId(), shipment.getCustomer().getFullName(),
                shipment.getDriver() == null ? null : shipment.getDriver().getFullName(),
                null, shipment.getOrigin(), shipment.getDestination(), shipment.getStatus(),
                shipment.getCreatedAt(), shipment.getUpdatedAt(), delayed);
    }

    private NotificationEntry toNotification(AdminNotification notification) {
        return new NotificationEntry(notification.getId(), notification.getType(),
                notification.getPriority(), notification.getMessage(), notification.getTimestamp(),
                notification.isRead(), notification.getRelatedType(), notification.getRelatedId(),
                notification.getRelatedLink());
    }

    private AuditEntry toAuditEntry(AdminAuditLog entry) {
        return new AuditEntry(entry.getId(), entry.getActorId(), entry.getAction(),
                entry.getRecordType(), entry.getRecordId(), entry.getMetadata(), entry.getTimestamp());
    }

    private ComplaintSummary toComplaintSummary(Complaint complaint) {
        return new ComplaintSummary(complaint.getId(), complaint.getDescription(),
                complaint.getStatus(), complaint.getCreatedAt(), complaint.getUpdatedAt(),
                complaint.getReporter().getId(), complaint.getReporter().getFullName(),
                complaint.getShipment().getId(),
                complaint.getAssignedAdmin() == null ? null : complaint.getAssignedAdmin().getId(),
                complaint.getAssignedAdmin() == null ? null : complaint.getAssignedAdmin().getFullName(),
                complaint.getEvidenceEncrypted() != null, complaint.getResolutionNote());
    }

    private boolean isUnresolved(ComplaintStatus status) {
        return status == ComplaintStatus.OPEN || status == ComplaintStatus.IN_REVIEW;
    }

    public record DriverReview(Long id, String fullName, String email, String phone,
            DriverApprovalStatus approvalStatus, LocalDateTime createdAt, String nidaNumber,
            String licenseNumber, LocalDate licenseExpiry, boolean hasFacePhoto,
            boolean hasLicenseDocument, boolean hasNidaDocument, List<VehicleReview> vehicles) {}

    public record DriverSummary(Long id, String fullName, String email, String phone,
            DriverApprovalStatus approvalStatus, LocalDateTime createdAt) {}

    public record VehicleReview(Long id, String plateNumber, String vehicleType,
            java.math.BigDecimal capacityKg, boolean hasFourPhotos) {}

    public record LicenseDocument(String contentType, String base64Data) {}

    public record ApprovalResult(Long driverId, DriverApprovalStatus approvalStatus) {}

    public record AdminOverview(long totalDrivers, long pendingApprovals, long activeShipments,
            long completedToday, long delayedShipments, long unresolvedComplaints,
            long unreadNotifications, int delayThresholdHours) {}

    public record ShipmentSummary(Long id, String customerName, String driverName,
            String vehiclePlateNumber, String origin, String destination, ShipmentStatus status,
            LocalDateTime createdAt, LocalDateTime updatedAt, boolean delayed) {}

    public record ShipmentHistoryEntry(ShipmentStatus status, LocalDateTime timestamp,
            String location, java.math.BigDecimal latitude, java.math.BigDecimal longitude) {}

    public record NotificationFeed(long unreadCount, List<NotificationEntry> notifications) {}

    public record NotificationEntry(Long id, String type, NotificationPriority priority,
            String message, LocalDateTime timestamp, boolean read, String relatedType,
            String relatedId, String relatedLink) {}

    public record MarkAllReadResult(int updatedCount) {}

    public record AuditEntry(Long id, Long actorId, String action, String recordType,
            String recordId, String metadata, LocalDateTime timestamp) {}

    public record SystemHealth(String api, String database, String backups, String lastBackupSuccess) {}

    public record ComplaintSummary(Long id, String description, ComplaintStatus status,
            LocalDateTime createdAt, LocalDateTime updatedAt, Long reporterId, String reporterName,
            Long shipmentId, Long assignedReviewerId, String assignedReviewerName,
            boolean hasEvidence, String resolutionNote) {}

    public record ComplaintDetail(ComplaintSummary complaint, List<ShipmentHistoryEntry> shipmentHistory) {}

    public record ComplaintEvidence(String contentType, String base64Data) {}

    public record ComplaintUpdateRequest(@NotNull ComplaintStatus status,
            @Size(max = 1000) String resolutionNote) {}
}
