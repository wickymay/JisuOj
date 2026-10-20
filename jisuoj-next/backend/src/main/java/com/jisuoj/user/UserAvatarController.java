package com.jisuoj.user;

import com.jisuoj.common.ApiException;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@RestController
@RequestMapping("/api/users")
public class UserAvatarController {
    private static final Pattern QQ_EMAIL = Pattern.compile("^([0-9]{5,12})@qq\\.com$", Pattern.CASE_INSENSITIVE);
    private static final int MAX_AVATAR_BYTES = 2 * 1024 * 1024;

    private final JdbcTemplate jdbc;
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    public UserAvatarController(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @GetMapping("/{userId}/avatar")
    public ResponseEntity<byte[]> avatar(@PathVariable String userId) {
        List<String> emails = jdbc.query(
                "SELECT email FROM users WHERE user_id=? AND defunct='N' LIMIT 1",
                (rs, rowNum) -> rs.getString(1), userId);
        if (emails.isEmpty() || emails.get(0) == null) {
            throw ApiException.notFound("用户头像不存在");
        }
        Matcher qq = QQ_EMAIL.matcher(emails.get(0).trim());
        if (!qq.matches()) {
            throw ApiException.notFound("用户头像不存在");
        }

        HttpRequest request = HttpRequest.newBuilder(
                        URI.create("https://q1.qlogo.cn/g?b=qq&nk=" + qq.group(1) + "&s=5"))
                .timeout(Duration.ofSeconds(8))
                .header("User-Agent", "JISUOJ/1.0")
                .GET()
                .build();
        try {
            HttpResponse<byte[]> response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
            String contentType = response.headers().firstValue("Content-Type").orElse("");
            byte[] body = response.body();
            if (response.statusCode() != 200 || !contentType.toLowerCase().startsWith("image/") ||
                    body == null || body.length == 0 || body.length > MAX_AVATAR_BYTES) {
                throw ApiException.notFound("用户头像不存在");
            }
            MediaType mediaType;
            try {
                mediaType = MediaType.parseMediaType(contentType);
            } catch (IllegalArgumentException ignored) {
                mediaType = MediaType.IMAGE_JPEG;
            }
            return ResponseEntity.ok()
                    .cacheControl(CacheControl.maxAge(Duration.ofHours(6)).cachePublic())
                    .contentType(mediaType)
                    .body(body);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw ApiException.notFound("用户头像暂时不可用");
        } catch (IOException ex) {
            throw ApiException.notFound("用户头像暂时不可用");
        }
    }
}
