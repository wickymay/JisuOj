package com.jisuoj.problemimport;

import com.jisuoj.common.ApiException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;

/**
 * Persists the problem-import assistant configuration in the database.
 * The API key is encrypted at rest and is never included in status responses.
 */
@Service
public class AiRuntimeConfigService {
    private static final int CONFIG_ID = 1;
    private static final int GCM_IV_BYTES = 12;
    private static final int GCM_TAG_BITS = 128;
    private static final byte[] AAD = "jisuoj-ai-config-v1".getBytes(StandardCharsets.UTF_8);

    private final JdbcTemplate jdbc;
    private final SecretKey encryptionKey;
    private final SecureRandom secureRandom = new SecureRandom();

    public AiRuntimeConfigService(JdbcTemplate jdbc,
                                  @Value("${jisuoj.ai.config-encryption-secret}") String encryptionSecret) {
        this.jdbc = jdbc;
        this.encryptionKey = deriveKey(requiredSecret(encryptionSecret));
    }

    public Status status() {
        List<Status> rows = jdbc.query(
                "SELECT base_url,model,updated_at FROM jisu_ai_config WHERE id=?",
                (rs, rowNum) -> new Status(true, rs.getString("base_url"), rs.getString("model"),
                        rs.getTimestamp("updated_at").toInstant()),
                CONFIG_ID);
        return rows.isEmpty() ? new Status(false, "", "", null) : rows.get(0);
    }

    public Status configure(String baseUrl, String model, String apiKey) {
        String endpoint = normalizeEndpoint(baseUrl);
        String normalizedModel = required(model, "模型名称不能为空");
        String normalizedKey = apiKey == null ? "" : apiKey.trim();
        if (normalizedKey.isBlank()) {
            Config previous = findConfig();
            if (previous != null) {
                normalizedKey = previous.apiKey();
            }
        }
        if (normalizedKey.isBlank()) {
            throw ApiException.badRequest("首次配置必须填写 API Key");
        }

        String encryptedKey = encrypt(normalizedKey);
        Instant configuredAt = Instant.now();
        jdbc.update("INSERT INTO jisu_ai_config(id,base_url,model,api_key_ciphertext,updated_at) " +
                        "VALUES(?,?,?,?,?) ON DUPLICATE KEY UPDATE base_url=VALUES(base_url),model=VALUES(model)," +
                        "api_key_ciphertext=VALUES(api_key_ciphertext),updated_at=VALUES(updated_at)",
                CONFIG_ID, endpoint, normalizedModel, encryptedKey, java.sql.Timestamp.from(configuredAt));
        return new Status(true, endpoint, normalizedModel, configuredAt);
    }

    public void clear() {
        jdbc.update("DELETE FROM jisu_ai_config WHERE id=?", CONFIG_ID);
    }

    public boolean configured() {
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM jisu_ai_config WHERE id=?", Integer.class, CONFIG_ID);
        return count != null && count > 0;
    }

    public Config requireConfig() {
        Config config = findConfig();
        if (config == null) {
            throw ApiException.badRequest("智能体尚未配置，请先在管理中心填写接口、模型和 API Key");
        }
        return config;
    }

    private Config findConfig() {
        List<Config> rows = jdbc.query(
                "SELECT base_url,model,api_key_ciphertext,updated_at FROM jisu_ai_config WHERE id=?",
                (rs, rowNum) -> new Config(rs.getString("base_url"), decrypt(rs.getString("api_key_ciphertext")),
                        rs.getString("model"), rs.getTimestamp("updated_at").toInstant()),
                CONFIG_ID);
        return rows.isEmpty() ? null : rows.get(0);
    }

    private String normalizeEndpoint(String value) {
        String endpoint = required(value, "智能体接口不能为空");
        URI uri;
        try {
            uri = URI.create(endpoint);
        } catch (IllegalArgumentException ex) {
            throw ApiException.badRequest("智能体接口必须是完整的 HTTP(S) URL");
        }
        String scheme = uri.getScheme();
        if (uri.getHost() == null || scheme == null ||
                !("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme))) {
            throw ApiException.badRequest("智能体接口必须是完整的 HTTP(S) URL");
        }
        return endpoint.replaceAll("/+$", "");
    }

    private String encrypt(String value) {
        try {
            byte[] iv = new byte[GCM_IV_BYTES];
            secureRandom.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, encryptionKey, new GCMParameterSpec(GCM_TAG_BITS, iv));
            cipher.updateAAD(AAD);
            byte[] encrypted = cipher.doFinal(value.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(ByteBuffer.allocate(iv.length + encrypted.length)
                    .put(iv).put(encrypted).array());
        } catch (Exception ex) {
            throw new IllegalStateException("无法加密智能体 API Key", ex);
        }
    }

    private String decrypt(String value) {
        try {
            byte[] payload = Base64.getDecoder().decode(value);
            if (payload.length <= GCM_IV_BYTES) {
                throw new IllegalArgumentException("密文长度无效");
            }
            byte[] iv = Arrays.copyOfRange(payload, 0, GCM_IV_BYTES);
            byte[] encrypted = Arrays.copyOfRange(payload, GCM_IV_BYTES, payload.length);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, encryptionKey, new GCMParameterSpec(GCM_TAG_BITS, iv));
            cipher.updateAAD(AAD);
            return new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8);
        } catch (Exception ex) {
            throw new IllegalStateException("智能体 API Key 无法解密，请由超级管理员重新保存配置", ex);
        }
    }

    private static SecretKey deriveKey(String secret) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(("jisuoj-ai-config\0" + secret).getBytes(StandardCharsets.UTF_8));
            return new SecretKeySpec(bytes, "AES");
        } catch (Exception ex) {
            throw new IllegalStateException("无法初始化智能体配置加密", ex);
        }
    }

    private static String required(String value, String message) {
        if (value == null || value.trim().isBlank()) {
            throw ApiException.badRequest(message);
        }
        return value.trim();
    }

    private static String requiredSecret(String value) {
        if (value == null || value.length() < 32) {
            throw new IllegalStateException("AI_CONFIG_SECRET 至少需要 32 个字符");
        }
        return value;
    }

    public record Config(String baseUrl, String apiKey, String model, Instant configuredAt) {
    }

    public record Status(boolean configured, String baseUrl, String model, Instant configuredAt) {
    }
}
