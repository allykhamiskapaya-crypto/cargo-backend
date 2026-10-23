package com.mizigo.cargo_backend.security;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Locale;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class DeliveryCodeService {

    private static final int CODE_DIGITS = 6;
    private static final int MAX_ATTEMPTS = 5;
    private static final long VALID_HOURS = 8;
    private final SecureRandom secureRandom = new SecureRandom();
    private final PasswordEncoder passwordEncoder;
    private final SecureFieldCipher cipher;

    public DeliveryCodeService(PasswordEncoder passwordEncoder, SecureFieldCipher cipher) {
        this.passwordEncoder = passwordEncoder;
        this.cipher = cipher;
    }

    public IssuedCode issue() {
        String code = String.format(Locale.ROOT, "%06d", secureRandom.nextInt(1_000_000));
        return new IssuedCode(code, passwordEncoder.encode(code), cipher.encrypt(code, "delivery-otp"),
                LocalDateTime.now().plusHours(VALID_HOURS));
    }

    public String reveal(String encryptedCode) {
        return cipher.decrypt(encryptedCode, "delivery-otp");
    }

    public boolean matches(String submittedCode, String codeHash, LocalDateTime expiresAt, int attempts) {
        return submittedCode != null && submittedCode.matches("\\d{6}")
                && codeHash != null && expiresAt != null && expiresAt.isAfter(LocalDateTime.now())
                && attempts < MAX_ATTEMPTS && passwordEncoder.matches(submittedCode, codeHash);
    }

    public boolean isLocked(int attempts) {
        return attempts >= MAX_ATTEMPTS;
    }

    public record IssuedCode(String value, String hash, String encrypted, LocalDateTime expiresAt) {}
}