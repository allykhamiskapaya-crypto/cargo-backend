package com.mizigo.cargo_backend.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Base64;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

class DeliveryCodeServiceTest {

    @Test
    void issuedCodeIsSixDigitsHashedAndEncrypted() {
        SecureFieldCipher cipher = new SecureFieldCipher("MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=");
        DeliveryCodeService service = new DeliveryCodeService(new BCryptPasswordEncoder(), cipher);

        DeliveryCodeService.IssuedCode code = service.issue();

        assertTrue(code.value().matches("\\d{6}"));
        assertNotEquals(code.value(), code.hash());
        assertNotEquals(code.value(), code.encrypted());
        assertEquals(code.value(), service.reveal(code.encrypted()));
        assertTrue(code.expiresAt().isAfter(java.time.LocalDateTime.now()));
        assertTrue(service.matches(code.value(), code.hash(), code.expiresAt(), 0));
    }
}