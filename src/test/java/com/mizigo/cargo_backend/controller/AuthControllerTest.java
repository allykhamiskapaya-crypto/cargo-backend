package com.mizigo.cargo_backend.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.mizigo.cargo_backend.model.CustomerType;
import com.mizigo.cargo_backend.model.User;
import com.mizigo.cargo_backend.model.UserRole;
import com.mizigo.cargo_backend.repository.UserRepository;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.mockito.ArgumentCaptor;

class AuthControllerTest {

    @Test
    void loginReturnsCustomerTypeAndAddsItToJwtClaims() {
        UserRepository userRepository = mock(UserRepository.class);
        PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
        JwtEncoder jwtEncoder = mock(JwtEncoder.class);
        AuthController controller = new AuthController(userRepository, passwordEncoder, jwtEncoder);
        User company = new User();
        company.setId(12L);
        company.setEmail("company@example.com");
        company.setPassword("$2a$encoded-password");
        company.setRole(UserRole.CUSTOMER);
        company.setCustomerType(CustomerType.COMPANY);

        when(userRepository.findByEmail(company.getEmail())).thenReturn(Optional.of(company));
        when(passwordEncoder.matches("password123", "$2a$encoded-password")).thenReturn(true);
        when(jwtEncoder.encode(any())).thenReturn(Jwt.withTokenValue("signed-token")
                .header("alg", "HS256")
                .claim("sub", company.getEmail())
                .build());

        ResponseEntity<?> response = controller.login(
                new AuthController.LoginRequest(company.getEmail(), "password123"));

        @SuppressWarnings("unchecked")
        Map<String, Object> body = (Map<String, Object>) response.getBody();
        assertSame(company, body.get("user"));
        assertEquals(CustomerType.COMPANY, ((User) body.get("user")).getCustomerType());
        assertEquals("signed-token", body.get("token"));

        ArgumentCaptor<JwtEncoderParameters> parameters = ArgumentCaptor.forClass(JwtEncoderParameters.class);
        verify(jwtEncoder).encode(parameters.capture());
        assertEquals("COMPANY", parameters.getValue().getClaims().getClaim("customerType"));
    }
}