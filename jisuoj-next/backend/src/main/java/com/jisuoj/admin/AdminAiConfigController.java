package com.jisuoj.admin;

import com.jisuoj.auth.UserPrincipal;
import com.jisuoj.problemimport.AiRuntimeConfigService;
import com.jisuoj.problemimport.AiProvider;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/ai-config")
@PreAuthorize("hasRole('ADMIN')")
public class AdminAiConfigController {
    private final AiRuntimeConfigService configuration;
    private final JdbcTemplate jdbc;

    public AdminAiConfigController(AiRuntimeConfigService configuration, JdbcTemplate jdbc) {
        this.configuration = configuration;
        this.jdbc = jdbc;
    }

    @GetMapping
    public AiRuntimeConfigService.Status status() {
        return configuration.status();
    }

    @GetMapping("/providers")
    public java.util.List<ProviderStatus> providers() {
        return java.util.Arrays.stream(AiProvider.values()).map(provider ->
                new ProviderStatus(provider.key(), provider.label, provider.baseUrl, provider.model,
                        configuration.status(provider))).toList();
    }

    @PutMapping("/{provider}")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public AiRuntimeConfigService.Status configureProvider(@PathVariable String provider,
            @Valid @RequestBody SaveRequest request, @AuthenticationPrincipal UserPrincipal principal) {
        AiProvider selected = AiProvider.parse(provider);
        var status = configuration.configure(selected, request.baseUrl(), request.model(), request.apiKey());
        audit(principal.userId(), "AI_CONFIG_UPDATE_" + selected.name());
        return status;
    }

    @DeleteMapping("/{provider}")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public void clearProvider(@PathVariable String provider, @AuthenticationPrincipal UserPrincipal principal) {
        AiProvider selected = AiProvider.parse(provider);
        configuration.clear(selected);
        audit(principal.userId(), "AI_CONFIG_CLEAR_" + selected.name());
    }

    public record ProviderStatus(String provider, String label, String defaultBaseUrl, String defaultModel,
                                 AiRuntimeConfigService.Status status) {}

    @PutMapping
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public AiRuntimeConfigService.Status configure(@Valid @RequestBody SaveRequest request,
                                                    @AuthenticationPrincipal UserPrincipal principal) {
        AiRuntimeConfigService.Status status = configuration.configure(request.baseUrl(), request.model(), request.apiKey());
        audit(principal.userId(), "AI_CONFIG_UPDATE");
        return status;
    }

    @DeleteMapping
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public void clear(@AuthenticationPrincipal UserPrincipal principal) {
        configuration.clear();
        audit(principal.userId(), "AI_CONFIG_CLEAR");
    }

    private void audit(String actor, String action) {
        jdbc.update("INSERT INTO jisu_audit_log(actor_id,action,target_type,target_id,created_at) " +
                        "VALUES(?,?,'ai_config','global',NOW())",
                actor, action);
    }

    public record SaveRequest(
            @NotBlank @Size(max = 500) String baseUrl,
            @NotBlank @Size(max = 200) String model,
            @Size(max = 8_000) String apiKey) {
    }
}
