package com.jisuoj.submission;

import com.jisuoj.auth.AuthSupport;
import com.jisuoj.auth.UserPrincipal;
import com.jisuoj.common.PageResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import static com.jisuoj.common.RequestSupport.clientIp;
import static com.jisuoj.common.RequestSupport.page;
import static com.jisuoj.common.RequestSupport.size;

@RestController
@RequestMapping("/api/submissions")
public class SubmissionController {
    private final SubmissionService service;

    public SubmissionController(SubmissionService service) {
        this.service = service;
    }

    @GetMapping
    public PageResponse<SubmissionDtos.Summary> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) Integer problemId,
            @RequestParam(required = false) String userId,
            @RequestParam(required = false) Integer language,
            @RequestParam(required = false) Integer result,
            @RequestParam(required = false) Integer contestId) {
        return service.list(page(page), size(size), problemId, userId, language, result, contestId);
    }

    @PostMapping
    @PreAuthorize("isAuthenticated()")
    public SubmissionDtos.Created submit(@Valid @RequestBody SubmissionDtos.CreateRequest request,
                                         Authentication authentication,
                                         HttpServletRequest servletRequest) {
        UserPrincipal principal = AuthSupport.required(authentication);
        return service.submit(request, principal, clientIp(servletRequest));
    }

    @GetMapping("/{id}/source")
    @PreAuthorize("isAuthenticated()")
    public SubmissionDtos.Source source(@PathVariable long id, Authentication authentication) {
        return service.source(id, AuthSupport.required(authentication));
    }

    @GetMapping("/{id}/diagnostics")
    @PreAuthorize("isAuthenticated()")
    public SubmissionDtos.Diagnostics diagnostics(@PathVariable long id, Authentication authentication) {
        return service.diagnostics(id, AuthSupport.required(authentication));
    }
}
