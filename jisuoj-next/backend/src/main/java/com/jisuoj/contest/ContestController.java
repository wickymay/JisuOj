package com.jisuoj.contest;

import com.jisuoj.auth.AuthSupport;
import com.jisuoj.auth.UserPrincipal;
import com.jisuoj.common.PageResponse;
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

import java.util.List;

import static com.jisuoj.common.RequestSupport.page;
import static com.jisuoj.common.RequestSupport.size;

@RestController
@RequestMapping("/api/contests")
public class ContestController {
    private final ContestService service;

    public ContestController(ContestService service) {
        this.service = service;
    }

    @GetMapping
    public PageResponse<ContestDtos.Summary> list(@RequestParam(defaultValue = "1") int page,
                                                  @RequestParam(defaultValue = "20") int size,
                                                  Authentication authentication) {
        return service.list(page(page), size(size), AuthSupport.optional(authentication));
    }

    @GetMapping("/{id}")
    public ContestDtos.Detail detail(@PathVariable int id, Authentication authentication) {
        return service.detail(id, AuthSupport.optional(authentication));
    }

    @GetMapping("/{id}/standings")
    public List<ContestDtos.Standing> standings(@PathVariable int id, Authentication authentication) {
        return service.standings(id, AuthSupport.optional(authentication));
    }

    @PostMapping("/{id}/join")
    @PreAuthorize("isAuthenticated()")
    public void join(@PathVariable int id, @Valid @RequestBody ContestDtos.JoinRequest request,
                     Authentication authentication) {
        service.join(id, request.password(), AuthSupport.required(authentication));
    }
}
