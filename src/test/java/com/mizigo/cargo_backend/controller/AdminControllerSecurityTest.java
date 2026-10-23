package com.mizigo.cargo_backend.controller;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.mizigo.cargo_backend.config.CorsConfig;
import com.mizigo.cargo_backend.config.SecurityConfig;
import com.mizigo.cargo_backend.model.DriverApprovalStatus;
import com.mizigo.cargo_backend.model.CommissionPayment;
import com.mizigo.cargo_backend.model.DriverProfile;
import com.mizigo.cargo_backend.model.User;
import com.mizigo.cargo_backend.repository.AdminAuditLogRepository;
import com.mizigo.cargo_backend.repository.AdminNotificationRepository;
import com.mizigo.cargo_backend.repository.ComplaintRepository;
import com.mizigo.cargo_backend.repository.CommissionPaymentRepository;
import com.mizigo.cargo_backend.model.UserRole;
import com.mizigo.cargo_backend.repository.DriverProfileRepository;
import com.mizigo.cargo_backend.repository.ShipmentRepository;
import com.mizigo.cargo_backend.repository.TrackingHistoryRepository;
import com.mizigo.cargo_backend.repository.UserRepository;
import com.mizigo.cargo_backend.repository.VehicleRepository;
import com.mizigo.cargo_backend.security.SecureFieldCipher;
import com.mizigo.cargo_backend.service.AdminAuditService;
import com.mizigo.cargo_backend.service.AdminNotificationService;
import com.mizigo.cargo_backend.service.DriverCommissionService;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.math.BigDecimal;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(AdminController.class)
@Import({SecurityConfig.class, CorsConfig.class})
@TestPropertySource(properties = "app.security.jwt-secret=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=")
class AdminControllerSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtEncoder jwtEncoder;

    @MockBean
    private UserRepository userRepository;

    @MockBean
    private DriverProfileRepository driverProfileRepository;

    @MockBean
    private VehicleRepository vehicleRepository;

    @MockBean
    private SecureFieldCipher secureFieldCipher;

    @MockBean
    private ShipmentRepository shipmentRepository;

    @MockBean
    private TrackingHistoryRepository trackingHistoryRepository;

    @MockBean
    private ComplaintRepository complaintRepository;

    @MockBean
    private CommissionPaymentRepository commissionPaymentRepository;

    @MockBean
    private AdminNotificationRepository notificationRepository;

    @MockBean
    private AdminAuditLogRepository auditLogRepository;

    @MockBean
    private AdminNotificationService notificationService;

    @MockBean
    private AdminAuditService auditService;

    @MockBean
    private DriverCommissionService driverCommissionService;

    @MockBean
    private DataSource dataSource;

    @Test
    void nonAdminCannotAccessDriverApprovalList() throws Exception {
        mockMvc.perform(get("/api/admin/drivers")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token("CUSTOMER")))
                .andExpect(status().isForbidden());
    }

    @Test
    void nonAdminCannotReadAuditLogs() throws Exception {
        mockMvc.perform(get("/api/admin/audit-logs")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token("DRIVER")))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminCanMarkNotificationsAsRead() throws Exception {
        when(notificationRepository.markAllRead()).thenReturn(4);

        mockMvc.perform(patch("/api/admin/notifications/read-all")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.updatedCount").value(4));
    }

    @Test
    void adminCanFilterDriverApprovalList() throws Exception {
        when(userRepository.findByRole(UserRole.DRIVER)).thenReturn(List.of());

        mockMvc.perform(get("/api/admin/drivers")
                        .param("status", "PENDING_APPROVAL")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void adminCanApproveDriverWithIncompleteVerificationDocuments() throws Exception {
        User driver = new User();
        driver.setId(34L);
        driver.setRole(UserRole.DRIVER);
        when(userRepository.findById(34L)).thenReturn(Optional.of(driver));
        when(userRepository.save(driver)).thenReturn(driver);

        mockMvc.perform(put("/api/admin/drivers/34/approve")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.approvalStatus").value(DriverApprovalStatus.APPROVED.name()));
    }

            @Test
            void adminCanReadEncryptedNidaDocumentThroughReviewEndpoint() throws Exception {
            User driver = new User();
            driver.setId(34L);
            driver.setRole(UserRole.DRIVER);
            DriverProfile profile = new DriverProfile();
            profile.setNidaDocumentEncrypted("encrypted-nida-document");
            profile.setNidaDocumentContentType("image/png");
            when(userRepository.findById(34L)).thenReturn(Optional.of(driver));
            when(driverProfileRepository.findByDriverId(34L)).thenReturn(Optional.of(profile));
            when(secureFieldCipher.decrypt("encrypted-nida-document", "driver-nida-document"))
                .thenReturn("cG5nLWJ5dGVz");

            mockMvc.perform(get("/api/admin/drivers/34/nida-document")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contentType").value("image/png"))
                .andExpect(jsonPath("$.base64Data").value("cG5nLWJ5dGVz"));
            }

    @Test
    void adminVerificationClearsCommissionDebtAndRestriction() throws Exception {
        User driver = new User();
        driver.setId(34L);
        driver.setRole(UserRole.DRIVER);
        driver.setFullName("Asha Driver");
        DriverProfile profile = new DriverProfile();
        profile.setDriver(driver);
        profile.setCommissionBalance(20000.0);
        profile.setUnpaidJobCount(3);
        profile.setRestricted(true);
        CommissionPayment payment = new CommissionPayment();
        payment.setDriverProfile(profile);
        payment.setAmount(new BigDecimal("20000.00"));
        payment.setReference("MPESA-123");
        when(commissionPaymentRepository.findById(91L)).thenReturn(Optional.of(payment));
        when(driverCommissionService.reconcile(34L)).thenReturn(profile);
        when(commissionPaymentRepository.save(payment)).thenReturn(payment);
        when(driverProfileRepository.save(profile)).thenReturn(profile);

        mockMvc.perform(put("/api/admin/driver-commission-payments/91/verify")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.verified").value(true));

        org.junit.jupiter.api.Assertions.assertEquals(0.0, profile.getCommissionBalance());
        org.junit.jupiter.api.Assertions.assertEquals(0, profile.getUnpaidJobCount());
        org.junit.jupiter.api.Assertions.assertFalse(profile.isRestricted());
    }

    private String token(String role) {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer("mizigo-api")
                .issuedAt(now)
                .expiresAt(now.plusSeconds(300))
                .subject("admin-review@example.com")
                .claim("userId", 12L)
                .claim("role", role)
                .build();
        return jwtEncoder.encode(JwtEncoderParameters.from(
                JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();
    }
}
