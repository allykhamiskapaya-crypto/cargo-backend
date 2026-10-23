package com.mizigo.cargo_backend.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.mizigo.cargo_backend.config.CorsConfig;
import com.mizigo.cargo_backend.config.SecurityConfig;
import com.mizigo.cargo_backend.model.User;
import com.mizigo.cargo_backend.repository.UserRepository;
import com.mizigo.cargo_backend.service.AdminNotificationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(UserController.class)
@Import({SecurityConfig.class, CorsConfig.class})
@TestPropertySource(properties = "app.security.jwt-secret=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=")
class RegistrationSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private UserRepository userRepository;

    @MockBean
    private AdminNotificationService notificationService;

    @Test
    void publicRegistrationCanCreateDriverPendingApproval() throws Exception {
        when(userRepository.existsByEmail("driver@example.com")).thenReturn(false);
        when(userRepository.existsByPhone("0712345678")).thenReturn(false);
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User saved = invocation.getArgument(0);
            saved.setId(1L);
            return saved;
        });

        mockMvc.perform(post("/api/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fullName":"Example Driver","email":"driver@example.com",
                                 "phone":"0712345678","password":"password123","role":"DRIVER"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("DRIVER"))
                .andExpect(jsonPath("$.customerType").value("INDIVIDUAL"))
                .andExpect(jsonPath("$.driverApprovalStatus").value("PENDING_APPROVAL"));
    }

    @Test
    void publicRegistrationDefaultsCustomerTypeToIndividual() throws Exception {
        when(userRepository.existsByEmail("customer@example.com")).thenReturn(false);
        when(userRepository.existsByPhone("0712345680")).thenReturn(false);
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User saved = invocation.getArgument(0);
            saved.setId(2L);
            return saved;
        });

        mockMvc.perform(post("/api/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fullName":"Example Customer","email":"customer@example.com",
                                 "phone":"0712345680","password":"password123","role":"CUSTOMER"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.customerType").value("INDIVIDUAL"));
    }

    @Test
    void publicCompanyRegistrationStoresCompanyDetails() throws Exception {
        when(userRepository.existsByEmail("company@example.com")).thenReturn(false);
        when(userRepository.existsByPhone("0712345681")).thenReturn(false);
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User saved = invocation.getArgument(0);
            saved.setId(3L);
            return saved;
        });

        mockMvc.perform(post("/api/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fullName":"Company Account","email":"company@example.com",
                                 "phone":"0712345681","password":"password123","role":"CUSTOMER",
                                 "customerType":"COMPANY","companyName":"Acme Logistics",
                                 "tinNumber":"123-456-789","vrnNumber":"VRN-123",
                                 "brelaNumber":"BRELA-123","contactPersonName":"Asha Manager",
                                 "officeAddress":"Dar es Salaam"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.customerType").value("COMPANY"))
                .andExpect(jsonPath("$.companyName").value("Acme Logistics"))
                .andExpect(jsonPath("$.tinNumber").value("123-456-789"))
                .andExpect(jsonPath("$.vrnNumber").value("VRN-123"))
                .andExpect(jsonPath("$.brelaNumber").value("BRELA-123"))
                .andExpect(jsonPath("$.contactPersonName").value("Asha Manager"))
                .andExpect(jsonPath("$.officeAddress").value("Dar es Salaam"));
    }

    @Test
    void publicCompanyRegistrationRequiresCompanyDetails() throws Exception {
        mockMvc.perform(post("/api/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fullName":"Company Account","email":"company@example.com",
                                 "phone":"0712345682","password":"password123","role":"CUSTOMER",
                                 "customerType":"COMPANY","companyName":" ","tinNumber":"",
                                 "contactPersonName":"","officeAddress":""}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void publicRegistrationCannotCreateAdmin() throws Exception {
        mockMvc.perform(post("/api/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fullName":"Example Admin","email":"admin@example.com",
                                 "phone":"0712345679","password":"password123","role":"ADMIN"}
                                """))
                .andExpect(status().isForbidden());
    }
}