package com.mizigo.cargo_backend.security;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

@Component
public class UploadValidator {

    private static final long MAX_FILE_BYTES = 1_000_000;

    public byte[] readVehiclePhoto(MultipartFile file) throws IOException {
        byte[] bytes = readLimited(file);
        if (!matchesImageContentType(file.getContentType(), bytes)) {
            throw new IllegalArgumentException("Vehicle photo content type does not match its data");
        }
        return bytes;
    }

    public ValidatedDocument readLicenseDocument(MultipartFile file) throws IOException {
        byte[] bytes = readLimited(file);
        String contentType = file.getContentType();
        if ("application/pdf".equals(contentType) && isPdf(bytes)) {
            return new ValidatedDocument(bytes, contentType);
        }
        if (matchesImageContentType(contentType, bytes)) {
            return new ValidatedDocument(bytes, contentType);
        }
        throw new IllegalArgumentException("Unsupported license document format");
    }

    public ValidatedDocument readNidaDocument(MultipartFile file) throws IOException {
        byte[] bytes = readLimited(file);
        String contentType = file.getContentType();
        if (!matchesImageContentType(contentType, bytes)) {
            throw new IllegalArgumentException("NIDA document must be a valid JPEG, PNG, or WebP image");
        }
        return new ValidatedDocument(bytes, contentType);
    }

    private byte[] readLimited(MultipartFile file) throws IOException {
        if (file == null || file.isEmpty() || file.getSize() > MAX_FILE_BYTES) {
            throw new IllegalArgumentException("Each upload must be between 1 byte and 1 MB");
        }
        return file.getBytes();
    }

    private boolean matchesImageContentType(String contentType, byte[] bytes) {
        return switch (contentType == null ? "" : contentType) {
            case "image/jpeg" -> isJpeg(bytes);
            case "image/png" -> isPng(bytes);
            case "image/webp" -> isWebp(bytes);
            default -> false;
        };
    }

    private boolean isJpeg(byte[] bytes) {
        return bytes.length >= 3 && (bytes[0] & 0xff) == 0xff
                && (bytes[1] & 0xff) == 0xd8 && (bytes[2] & 0xff) == 0xff;
    }

    private boolean isPng(byte[] bytes) {
        return bytes.length >= 8 && (bytes[0] & 0xff) == 0x89
                && bytes[1] == 0x50 && bytes[2] == 0x4e && bytes[3] == 0x47
                && bytes[4] == 0x0d && bytes[5] == 0x0a && bytes[6] == 0x1a && bytes[7] == 0x0a;
    }

    private boolean isWebp(byte[] bytes) {
        return bytes.length >= 12 && bytes[0] == 'R' && bytes[1] == 'I'
                && bytes[2] == 'F' && bytes[3] == 'F' && bytes[8] == 'W'
                && bytes[9] == 'E' && bytes[10] == 'B' && bytes[11] == 'P';
    }

    private boolean isPdf(byte[] bytes) {
        return bytes.length >= 5 && Arrays.equals(Arrays.copyOf(bytes, 5), "%PDF-".getBytes(StandardCharsets.US_ASCII));
    }

    public record ValidatedDocument(byte[] bytes, String contentType) {}
}