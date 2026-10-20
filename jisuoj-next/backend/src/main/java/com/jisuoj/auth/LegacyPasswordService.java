package com.jisuoj.auth;

import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

@Component
public class LegacyPasswordService {
    private static final SecureRandom RANDOM = new SecureRandom();

    public boolean matches(String rawPassword, String saved) {
        if (rawPassword == null || saved == null || saved.isBlank()) {
            return false;
        }
        String normalized = isMd5(rawPassword) ? rawPassword.toLowerCase() : md5(rawPassword);
        if (isMd5(saved)) {
            return MessageDigest.isEqual(
                    normalized.getBytes(StandardCharsets.US_ASCII),
                    saved.toLowerCase().getBytes(StandardCharsets.US_ASCII)
            );
        }
        try {
            byte[] decoded = Base64.getDecoder().decode(saved);
            if (decoded.length < 24) {
                return false;
            }
            byte[] salt = new byte[decoded.length - 20];
            System.arraycopy(decoded, 20, salt, 0, salt.length);
            return MessageDigest.isEqual(encode(normalized, salt).getBytes(StandardCharsets.US_ASCII),
                    saved.getBytes(StandardCharsets.US_ASCII));
        } catch (IllegalArgumentException ex) {
            return false;
        }
    }

    public String encode(String rawPassword) {
        byte[] salt = new byte[4];
        byte[] random = new byte[2];
        RANDOM.nextBytes(random);
        byte[] hex = HexFormat.of().formatHex(random).getBytes(StandardCharsets.US_ASCII);
        System.arraycopy(hex, 0, salt, 0, salt.length);
        return encode(md5(rawPassword), salt);
    }

    private String encode(String md5Password, byte[] salt) {
        MessageDigest sha1 = digest("SHA-1");
        sha1.update(md5Password.getBytes(StandardCharsets.US_ASCII));
        byte[] hash = sha1.digest(salt);
        byte[] combined = new byte[hash.length + salt.length];
        System.arraycopy(hash, 0, combined, 0, hash.length);
        System.arraycopy(salt, 0, combined, hash.length, salt.length);
        return Base64.getEncoder().encodeToString(combined);
    }

    private String md5(String value) {
        return HexFormat.of().formatHex(digest("MD5").digest(value.getBytes(StandardCharsets.UTF_8)));
    }

    private boolean isMd5(String value) {
        return value != null && value.matches("(?i)[0-9a-f]{32}");
    }

    private MessageDigest digest(String algorithm) {
        try {
            return MessageDigest.getInstance(algorithm);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException(algorithm + " is not available", ex);
        }
    }
}
