package com.jisuoj.auth;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jisuoj.config.JisuOjProperties;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

@Service
public class JwtService {
    private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder DECODER = Base64.getUrlDecoder();
    private final ObjectMapper objectMapper;
    private final JisuOjProperties properties;

    public JwtService(ObjectMapper objectMapper, JisuOjProperties properties) {
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    public String create(UserPrincipal principal) {
        try {
            Instant now = Instant.now();
            Map<String, Object> header = Map.of("alg", "HS256", "typ", "JWT");
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("sub", principal.userId());
            payload.put("nick", principal.nick());
            payload.put("roles", principal.roles().stream().sorted().toList());
            payload.put("iat", now.getEpochSecond());
            payload.put("exp", now.plus(properties.getAuth().getTokenTtl()).getEpochSecond());
            String unsigned = encodeJson(header) + "." + encodeJson(payload);
            return unsigned + "." + ENCODER.encodeToString(sign(unsigned));
        } catch (Exception ex) {
            throw new IllegalStateException("无法创建登录令牌", ex);
        }
    }

    public UserPrincipal parse(String token) {
        try {
            String[] parts = token.split("\\.");
            if (parts.length != 3) {
                return null;
            }
            String unsigned = parts[0] + "." + parts[1];
            if (!java.security.MessageDigest.isEqual(sign(unsigned), DECODER.decode(parts[2]))) {
                return null;
            }
            Map<String, Object> claims = objectMapper.readValue(DECODER.decode(parts[1]), new TypeReference<>() {
            });
            long expiresAt = ((Number) claims.getOrDefault("exp", 0)).longValue();
            if (expiresAt <= Instant.now().getEpochSecond()) {
                return null;
            }
            String userId = String.valueOf(claims.getOrDefault("sub", ""));
            if (userId.isBlank()) {
                return null;
            }
            String nick = String.valueOf(claims.getOrDefault("nick", userId));
            Set<String> roles = new TreeSet<>();
            Object roleValue = claims.get("roles");
            if (roleValue instanceof List<?> values) {
                values.forEach(value -> roles.add(String.valueOf(value)));
            }
            return new UserPrincipal(userId, nick, Set.copyOf(roles));
        } catch (Exception ignored) {
            return null;
        }
    }

    private String encodeJson(Object value) throws Exception {
        return ENCODER.encodeToString(objectMapper.writeValueAsBytes(value));
    }

    private byte[] sign(String value) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(properties.getAuth().getSecret().getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return mac.doFinal(value.getBytes(StandardCharsets.US_ASCII));
    }
}
