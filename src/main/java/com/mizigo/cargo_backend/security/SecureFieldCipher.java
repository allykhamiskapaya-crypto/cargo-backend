package com.mizigo.cargo_backend.security;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import javax.crypto.SecretKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class SecureFieldCipher {

    private static final int IV_LENGTH = 12;
    private static final int TAG_LENGTH_BITS = 128;
    private final SecretKey masterKey;
    private final SecureRandom secureRandom = new SecureRandom();

    public SecureFieldCipher(@Value("${app.security.data-encryption-secret}") String encodedSecret) {
        byte[] keyBytes;
        try {
            keyBytes = Base64.getDecoder().decode(encodedSecret);
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException("DATA_ENCRYPTION_SECRET must be valid base64", exception);
        }
        if (keyBytes.length < 32) {
            throw new IllegalStateException("DATA_ENCRYPTION_SECRET must contain at least 32 bytes");
        }
        this.masterKey = new SecretKeySpec(keyBytes, "HmacSHA256");
    }

    public String encrypt(String value, String purpose) {
        if (value == null) return null;
        try {
            byte[] iv = new byte[IV_LENGTH];
            secureRandom.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, deriveKey(purpose), new GCMParameterSpec(TAG_LENGTH_BITS, iv));
            byte[] encrypted = cipher.doFinal(value.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(ByteBuffer.allocate(iv.length + encrypted.length)
                    .put(iv).put(encrypted).array());
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("Unable to encrypt protected field", exception);
        }
    }

    public String decrypt(String value, String purpose) {
        if (value == null) return null;
        try {
            byte[] packed = Base64.getDecoder().decode(value);
            if (packed.length <= IV_LENGTH) throw new IllegalArgumentException("Invalid encrypted value");
            ByteBuffer buffer = ByteBuffer.wrap(packed);
            byte[] iv = new byte[IV_LENGTH];
            buffer.get(iv);
            byte[] encrypted = new byte[buffer.remaining()];
            buffer.get(encrypted);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, deriveKey(purpose), new GCMParameterSpec(TAG_LENGTH_BITS, iv));
            return new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException | IllegalArgumentException exception) {
            throw new IllegalStateException("Unable to decrypt protected field", exception);
        }
    }

    private SecretKey deriveKey(String purpose) throws GeneralSecurityException {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(masterKey.getEncoded(), "HmacSHA256"));
        return new SecretKeySpec(mac.doFinal(("mizigo:" + purpose).getBytes(StandardCharsets.UTF_8)), "AES");
    }
}