package com.mizigo.cargo_backend.controller;

import com.mizigo.cargo_backend.model.Complaint;
import com.mizigo.cargo_backend.model.ComplaintStatus;
import com.mizigo.cargo_backend.model.NotificationPriority;
import com.mizigo.cargo_backend.model.Shipment;
import com.mizigo.cargo_backend.model.User;
import com.mizigo.cargo_backend.repository.ComplaintRepository;
import com.mizigo.cargo_backend.repository.ShipmentRepository;
import com.mizigo.cargo_backend.repository.UserRepository;
import com.mizigo.cargo_backend.security.SecureFieldCipher;
import com.mizigo.cargo_backend.security.SecurityIdentity;
import com.mizigo.cargo_backend.security.UploadValidator;
import com.mizigo.cargo_backend.service.AdminAuditService;
import com.mizigo.cargo_backend.service.AdminNotificationService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.io.IOException;
import java.util.Base64;
import java.util.List;
import java.util.Objects;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/complaints")
@PreAuthorize("hasAnyRole('CUSTOMER', 'DRIVER')")
public class ComplaintController {

    private final ComplaintRepository complaintRepository;
    private final ShipmentRepository shipmentRepository;
    private final UserRepository userRepository;
    private final UploadValidator uploadValidator;
    private final SecureFieldCipher secureFieldCipher;
    private final AdminNotificationService notificationService;
    private final AdminAuditService auditService;

    public ComplaintController(ComplaintRepository complaintRepository,
            ShipmentRepository shipmentRepository, UserRepository userRepository,
            UploadValidator uploadValidator, SecureFieldCipher secureFieldCipher,
            AdminNotificationService notificationService, AdminAuditService auditService) {
        this.complaintRepository = complaintRepository;
        this.shipmentRepository = shipmentRepository;
        this.userRepository = userRepository;
        this.uploadValidator = uploadValidator;
        this.secureFieldCipher = secureFieldCipher;
        this.notificationService = notificationService;
        this.auditService = auditService;
    }

    @PostMapping(consumes = "multipart/form-data")
    @Transactional
    public ResponseEntity<?> createComplaint(
            @Valid @RequestPart("complaint") ComplaintRequest request,
            @RequestPart(value = "evidence", required = false) MultipartFile evidence,
            @AuthenticationPrincipal Jwt jwt) {
        Long reporterId = SecurityIdentity.userId(jwt);
        User reporter = userRepository.findById(reporterId).orElse(null);
        Shipment shipment = shipmentRepository.findById(request.shipmentId()).orElse(null);
        if (reporter == null || shipment == null) return ResponseEntity.notFound().build();
        boolean ownsShipment = Objects.equals(shipment.getCustomer().getId(), reporterId)
                || (shipment.getDriver() != null
                        && Objects.equals(shipment.getDriver().getId(), reporterId));
        if (!ownsShipment) return ResponseEntity.status(HttpStatus.FORBIDDEN).build();

        UploadValidator.ValidatedDocument validatedEvidence = null;
        if (evidence != null && !evidence.isEmpty()) {
            try {
                validatedEvidence = uploadValidator.readLicenseDocument(evidence);
            } catch (IllegalArgumentException exception) {
                return ResponseEntity.badRequest().body(exception.getMessage());
            } catch (IOException exception) {
                return ResponseEntity.badRequest().body("Unable to read complaint evidence");
            }
        }

        Complaint complaint = new Complaint();
        complaint.setReporter(reporter);
        complaint.setShipment(shipment);
        complaint.setDescription(request.description().trim());
        complaint = complaintRepository.save(complaint);
        if (validatedEvidence != null) {
            complaint.setEvidenceEncrypted(secureFieldCipher.encrypt(
                    Base64.getEncoder().encodeToString(validatedEvidence.bytes()),
                    "complaint-evidence-" + complaint.getId()));
            complaint.setEvidenceContentType(validatedEvidence.contentType());
            complaint = complaintRepository.save(complaint);
        }
        notificationService.notifyOnce("COMPLAINT_CREATED", NotificationPriority.HIGH,
                "A new complaint was filed for shipment " + shipment.getId() + ".",
                "COMPLAINT", complaint.getId().toString(),
                "/admin-dashboard?section=complaints&complaintId=" + complaint.getId(),
                "COMPLAINT_CREATED:" + complaint.getId());
        auditService.record(reporterId, "COMPLAINT_OPENED", "COMPLAINT",
                complaint.getId().toString(), "shipmentId=" + shipment.getId());
        return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(complaint));
    }

    @GetMapping
    public List<ComplaintResponse> getMyComplaints(@AuthenticationPrincipal Jwt jwt) {
        return complaintRepository.findByReporterIdOrderByCreatedAtDesc(
                        SecurityIdentity.userId(jwt)).stream()
                .map(this::toResponse).toList();
    }

    @GetMapping("/{complaintId}")
    public ResponseEntity<?> getMyComplaint(@PathVariable Long complaintId,
            @AuthenticationPrincipal Jwt jwt) {
        Complaint complaint = complaintRepository.findById(complaintId).orElse(null);
        if (complaint == null) return ResponseEntity.notFound().build();
        if (!Objects.equals(complaint.getReporter().getId(), SecurityIdentity.userId(jwt))) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        return ResponseEntity.ok(toResponse(complaint));
    }

    private ComplaintResponse toResponse(Complaint complaint) {
        return new ComplaintResponse(complaint.getId(), complaint.getShipment().getId(),
                complaint.getDescription(), complaint.getStatus(), complaint.getCreatedAt(),
                complaint.getUpdatedAt(), complaint.getEvidenceEncrypted() != null);
    }

    public record ComplaintRequest(@NotNull Long shipmentId,
            @NotBlank @Size(max = 2000) String description) {}

    public record ComplaintResponse(Long id, Long shipmentId, String description,
            ComplaintStatus status, java.time.LocalDateTime createdAt,
            java.time.LocalDateTime updatedAt, boolean hasEvidence) {}
}
