package com.mizigo.cargo_backend.controller;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.mizigo.cargo_backend.config.CorsConfig;
import com.mizigo.cargo_backend.config.SecurityConfig;
import com.mizigo.cargo_backend.model.Complaint;
import com.mizigo.cargo_backend.model.User;
import com.mizigo.cargo_backend.repository.ComplaintRepository;
import com.mizigo.cargo_backend.repository.ShipmentRepository;
import com.mizigo.cargo_backend.repository.UserRepository;
import com.mizigo.cargo_backend.security.SecureFieldCipher;
import com.mizigo.cargo_backend.security.UploadValidator;
import com.mizigo.cargo_backend.service.AdminAuditService;
import com.mizigo.cargo_backend.service.AdminNotificationService;
import java.time.Instant;
import java.util.Optional;
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

@WebMvcTest(ComplaintController.class)
@Import({SecurityConfig.class, CorsConfig.class})
@TestPropertySource(properties = "app.security.jwt-secret=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=")
class ComplaintControllerSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtEncoder jwtEncoder;

    @MockBean
    private ComplaintRepository complaintRepository;
    @MockBean
    private ShipmentRepository shipmentRepository;
    @MockBean
    private UserRepository userRepository;
    @MockBean
    private UploadValidator uploadValidator;
    @MockBean
    private SecureFieldCipher secureFieldCipher;
    @MockBean
    private AdminNotificationService notificationService;
    @MockBean
    private AdminAuditService auditService;

    @Test
    void unauthenticatedUserCannotReadComplaints() throws Exception {
        mockMvc.perform(get("/api/complaints"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void reporterCannotReadAnotherUsersComplaint() throws Exception {
        User reporter = new User();
        reporter.setId(42L);
        Complaint complaint = new Complaint();
        complaint.setId(7L);
        complaint.setReporter(reporter);
        when(complaintRepository.findById(7L)).thenReturn(Optional.of(complaint));

        mockMvc.perform(get("/api/complaints/7")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token("CUSTOMER", 17L)))
                .andExpect(status().isForbidden());
    }

    private String token(String role, Long userId) {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer("mizigo-api")
                .issuedAt(now)
                .expiresAt(now.plusSeconds(300))
                .subject("complaint-test@example.com")
                .claim("userId", userId)
                .claim("role", role)
                .build();
        return jwtEncoder.encode(JwtEncoderParameters.from(
                JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();
    }
}
