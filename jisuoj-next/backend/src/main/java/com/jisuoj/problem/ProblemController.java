package com.jisuoj.problem;

import com.jisuoj.auth.AuthSupport;
import com.jisuoj.common.PageResponse;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

import static com.jisuoj.common.RequestSupport.page;
import static com.jisuoj.common.RequestSupport.size;

@RestController
@RequestMapping("/api/problems")
public class ProblemController {
    private final ProblemService service;

    public ProblemController(ProblemService service) {
        this.service = service;
    }

    @GetMapping
    public PageResponse<ProblemDtos.Summary> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String source,
            Authentication authentication) {
        return service.list(page(page), size(size), keyword, source, AuthSupport.optional(authentication));
    }

    @GetMapping("/{id}")
    public ProblemDtos.Detail detail(@PathVariable int id, Authentication authentication) {
        return service.detail(id, AuthSupport.optional(authentication));
    }

    @GetMapping("/{id}/accepted-users")
    public PageResponse<ProblemDtos.AcceptedUser> acceptedUsers(
            @PathVariable int id,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            Authentication authentication) {
        return service.acceptedUsers(id, page(page), size(size), AuthSupport.optional(authentication));
    }

    @GetMapping("/sources")
    public List<String> sources() {
        return service.sources();
    }
}
