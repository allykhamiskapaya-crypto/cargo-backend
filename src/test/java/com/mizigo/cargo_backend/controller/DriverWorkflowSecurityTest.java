package com.mizigo.cargo_backend.controller;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.mizigo.cargo_backend.config.CorsConfig;
import com.mizigo.cargo_backend.config.SecurityConfig;
import com.mizigo.cargo_backend.model.DriverApprovalStatus;
import com.mizigo.cargo_backend.model.CommissionPayment;
import com.mizigo.cargo_backend.model.DriverProfile;
import com.mizigo.cargo_backend.model.Shipment;
import com.mizigo.cargo_backend.model.User;
import com.mizigo.cargo_backend.model.UserRole;
import com.mizigo.cargo_backend.model.Vehicle;
import com.mizigo.cargo_backend.repository.DriverJobResponseRepository;
import com.mizigo.cargo_backend.repository.CommissionPaymentRepository;
import com.mizigo.cargo_backend.repository.DriverProfileRepository;
import com.mizigo.cargo_backend.repository.ShipmentRepository;
import com.mizigo.cargo_backend.repository.TrackingHistoryRepository;
import com.mizigo.cargo_backend.repository.UserRepository;
import com.mizigo.cargo_backend.repository.VehicleRepository;
import com.mizigo.cargo_backend.security.SecureFieldCipher;
import com.mizigo.cargo_backend.security.UploadValidator;
import com.mizigo.cargo_backend.service.DriverCommissionService;
import java.time.Instant;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(DriverController.class)
@Import({SecurityConfig.class, CorsConfig.class})
@TestPropertySource(properties = "app.security.jwt-secret=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=")
class DriverWorkflowSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtEncoder jwtEncoder;

    @MockBean
    private DriverProfileRepository driverProfileRepository;

    @MockBean
    private CommissionPaymentRepository commissionPaymentRepository;

    @MockBean
    private DriverJobResponseRepository driverJobResponseRepository;

    @MockBean
    private ShipmentRepository shipmentRepository;

    @MockBean
    private TrackingHistoryRepository trackingHistoryRepository;

    @MockBean
    private UserRepository userRepository;

    @MockBean
    private VehicleRepository vehicleRepository;

    @MockBean
    private SecureFieldCipher secureFieldCipher;

    @MockBean
    private UploadValidator uploadValidator;

    @MockBean
    private DriverCommissionService driverCommissionService;

    @Test
    void customerCannotAccessDriverProfileEndpoints() throws Exception {
        mockMvc.perform(get("/api/drivers/me/profile")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token(12L, "CUSTOMER")))
                .andExpect(status().isForbidden());
    }

            @Test
            void driverCanUploadNidaCardImage() throws Exception {
            User driver = new User();
            driver.setId(12L);
            driver.setFullName("Asha Driver");
            driver.setPhone("0700000012");
            driver.setDriverApprovalStatus(DriverApprovalStatus.PENDING_APPROVAL);
            DriverProfile profile = new DriverProfile();
            profile.setDriver(driver);
            profile.setNidaEncrypted("encrypted-nida");
            profile.setLicenseNumberEncrypted("encrypted-license");
            when(driverProfileRepository.findByDriverId(12L)).thenReturn(Optional.of(profile));
            when(uploadValidator.readNidaDocument(org.mockito.ArgumentMatchers.any()))
                .thenReturn(new UploadValidator.ValidatedDocument(new byte[] {1}, "image/png"));
            when(secureFieldCipher.encrypt(org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.eq("driver-nida-document"))).thenReturn("encrypted-nida-image");
            when(secureFieldCipher.decrypt("encrypted-nida", "driver-nida")).thenReturn("12345678901234567890");
            when(secureFieldCipher.decrypt("encrypted-license", "driver-license")).thenReturn("DL-123456");
            when(driverProfileRepository.save(profile)).thenReturn(profile);

            mockMvc.perform(multipart("/api/drivers/me/profile/nida-document")
                    .file(new MockMultipartFile("document", "nida.png", "image/png", new byte[] {1}))
                    .with(request -> {
                        request.setMethod("PUT");
                        return request;
                    })
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token(12L, "DRIVER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hasNidaDocument").value(true));
            }

    @Test
    void driverCanAccessOwnEmptyProfile() throws Exception {
        when(driverProfileRepository.findByDriverId(12L)).thenReturn(Optional.empty());
        User driver = new User();
        driver.setDriverApprovalStatus(DriverApprovalStatus.PENDING_APPROVAL);
        when(userRepository.findById(12L)).thenReturn(Optional.of(driver));

        mockMvc.perform(get("/api/drivers/me/profile")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token(12L, "DRIVER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.profileComplete").value(false))
                .andExpect(jsonPath("$.available").value(false))
                .andExpect(jsonPath("$.approvalStatus").value("PENDING_APPROVAL"));
    }

    @Test
    void offlineDriverCannotFetchOffers() throws Exception {
        when(driverProfileRepository.findByDriverId(12L)).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/drivers/me/jobs")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token(12L, "DRIVER")))
                .andExpect(status().isConflict());
    }

    @Test
    void offlineDriverCannotAcceptAnOffer() throws Exception {
        when(driverProfileRepository.findByDriverId(12L)).thenReturn(Optional.empty());

        mockMvc.perform(post("/api/drivers/me/jobs/44/accept")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token(12L, "DRIVER")))
                .andExpect(status().isConflict());
    }

    @Test
    void restrictedDriverCannotAcceptAnOffer() throws Exception {
        User driver = new User();
        driver.setId(12L);
        driver.setDriverApprovalStatus(DriverApprovalStatus.APPROVED);
        DriverProfile profile = new DriverProfile();
        profile.setAvailable(true);
        profile.setRestricted(true);
        Shipment shipment = new Shipment();
        shipment.setId(44L);
        shipment.setStatus(com.mizigo.cargo_backend.model.ShipmentStatus.PENDING);
        when(userRepository.findById(12L)).thenReturn(Optional.of(driver));
        when(driverProfileRepository.findByDriverId(12L)).thenReturn(Optional.of(profile));
        when(shipmentRepository.findByIdForUpdate(44L)).thenReturn(Optional.of(shipment));
        when(driverCommissionService.reconcile(12L)).thenReturn(profile);

        mockMvc.perform(post("/api/drivers/me/jobs/44/accept")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token(12L, "DRIVER")))
                .andExpect(status().isConflict());
    }

            @Test
            void driverCanSubmitCommissionPaymentReferenceForVerification() throws Exception {
            DriverProfile profile = new DriverProfile();
            profile.setId(55L);
            profile.setCommissionBalance(20000.0);
            profile.setUnpaidJobCount(2);
            when(driverCommissionService.reconcile(12L)).thenReturn(profile);
            when(commissionPaymentRepository.findFirstByDriverProfileIdAndVerifiedFalseAndRejectedFalseOrderByCreatedAtDesc(55L))
                .thenReturn(Optional.empty());
            when(commissionPaymentRepository.existsByReference("MPESA-123")).thenReturn(false);
            when(commissionPaymentRepository.save(org.mockito.ArgumentMatchers.any(CommissionPayment.class)))
                .thenAnswer(invocation -> {
                    CommissionPayment payment = invocation.getArgument(0);
                    payment.setAmount(new BigDecimal("20000.00"));
                    return payment;
                });

            mockMvc.perform(post("/api/drivers/me/pay-commission")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token(12L, "DRIVER"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"reference\":\"MPESA-123\"}"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.amount").value(20000.0))
                .andExpect(jsonPath("$.verified").value(false));
            }

    @Test
    void approvedDriverCanGoOnlineWithIncompleteVerificationDocuments() throws Exception {
        User driver = new User();
        driver.setId(12L);
        driver.setRole(UserRole.DRIVER);
        driver.setDriverApprovalStatus(DriverApprovalStatus.APPROVED);

        DriverProfile profile = new DriverProfile();
        profile.setDriver(driver);
        profile.setNidaEncrypted("encrypted-nida");
        profile.setLicenseNumberEncrypted("encrypted-license");
        profile.setLicenseExpiry(LocalDate.now().minusDays(1));

        when(userRepository.findById(12L)).thenReturn(Optional.of(driver));
        when(driverProfileRepository.findByDriverId(12L)).thenReturn(Optional.of(profile));
        when(driverCommissionService.reconcile(12L)).thenReturn(profile);
        when(vehicleRepository.findByDriverId(12L)).thenReturn(List.of(new Vehicle()));
        when(driverProfileRepository.save(profile)).thenReturn(profile);

        mockMvc.perform(put("/api/drivers/me/availability")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token(12L, "DRIVER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"available\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.available").value(true))
                .andExpect(jsonPath("$.approvalStatus").value("APPROVED"));
    }

    private String token(Long userId, String role) {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer("mizigo-api")
                .issuedAt(now)
                .expiresAt(now.plusSeconds(300))
                .subject("driver-workflow@example.com")
                .claim("userId", userId)
                .claim("role", role)
                .build();
        return jwtEncoder.encode(JwtEncoderParameters.from(
                JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();
    }
}