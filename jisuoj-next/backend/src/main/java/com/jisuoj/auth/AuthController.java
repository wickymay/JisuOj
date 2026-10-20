package com.jisuoj.auth;

import com.jisuoj.config.JisuOjProperties;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.util.Map;

import static com.jisuoj.common.RequestSupport.clientIp;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final AuthService authService;
    private final JwtService jwtService;
    private final JisuOjProperties properties;

    public AuthController(AuthService authService, JwtService jwtService, JisuOjProperties properties) {
        this.authService = authService;
        this.jwtService = jwtService;
        this.properties = properties;
    }

    @GetMapping("/csrf")
    public Map<String, String> csrf(CsrfToken token) {
        return Map.of("token", token.getToken(), "headerName", token.getHeaderName());
    }

    @PostMapping("/login")
    public ResponseEntity<UserPrincipal> login(@Valid @RequestBody LoginRequest request) {
        UserPrincipal principal = authService.authenticate(request.userId().trim(), request.password());
        return withToken(principal);
    }

    @PostMapping("/register")
    public ResponseEntity<UserPrincipal> register(@Valid @RequestBody RegisterRequest request,
                                                  HttpServletRequest servletRequest) {
        UserPrincipal principal = authService.register(
                request.userId().trim(), request.password(), request.nick(), request.email(), clientIp(servletRequest));
        return withToken(principal);
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout() {
        ResponseCookie cookie = cookie("", Duration.ZERO);
        return ResponseEntity.noContent().header(HttpHeaders.SET_COOKIE, cookie.toString()).build();
    }

    @GetMapping("/me")
    public ResponseEntity<UserPrincipal> me(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof UserPrincipal principal)) {
            return ResponseEntity.noContent().build();
        }
        // Re-read legacy privilege rows so role changes take effect without waiting
        // for an old JWT to expire.
        return withToken(authService.refresh(principal.userId()));
    }

    private ResponseEntity<UserPrincipal> withToken(UserPrincipal principal) {
        ResponseCookie cookie = cookie(jwtService.create(principal), properties.getAuth().getTokenTtl());
        return ResponseEntity.ok().header(HttpHeaders.SET_COOKIE, cookie.toString()).body(principal);
    }

    private ResponseCookie cookie(String token, Duration maxAge) {
        return ResponseCookie.from(JwtAuthenticationFilter.COOKIE_NAME, token)
                .httpOnly(true)
                .secure(properties.getAuth().isSecureCookie())
                .sameSite("Strict")
                .path("/")
                .maxAge(maxAge)
                .build();
    }

    public record LoginRequest(
            @NotBlank @Size(max = 48) String userId,
            @NotBlank @Size(max = 128) String password) {
    }

    public record RegisterRequest(
            @NotBlank @Pattern(regexp = "[A-Za-z0-9_]{3,48}", message = "只能包含字母、数字和下划线，长度为 3–48") String userId,
            @NotBlank @Size(min = 8, max = 128, message = "长度应为 8–128") String password,
            @Size(max = 20) String nick,
            @Email @Size(max = 100) String email) {
    }
}
