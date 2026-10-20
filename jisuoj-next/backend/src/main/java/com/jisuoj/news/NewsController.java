package com.jisuoj.news;

import com.jisuoj.auth.AuthSupport;
import com.jisuoj.auth.UserPrincipal;
import com.jisuoj.common.ApiException;
import com.jisuoj.common.PageResponse;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import static com.jisuoj.common.RequestSupport.page;
import static com.jisuoj.common.RequestSupport.size;

@RestController
@RequestMapping("/api/news")
public class NewsController {
    private final NewsRepository repository;

    public NewsController(NewsRepository repository) {
        this.repository = repository;
    }

    @GetMapping
    public PageResponse<NewsDtos.Summary> list(@RequestParam(defaultValue = "1") int page,
                                               @RequestParam(defaultValue = "10") int size,
                                               Authentication authentication) {
        UserPrincipal principal = AuthSupport.optional(authentication);
        return repository.list(page(page), size(size), principal != null && principal.hasAnyRole("ROLE_ADMIN"));
    }

    @GetMapping("/{id}")
    public NewsDtos.Detail detail(@PathVariable int id, Authentication authentication) {
        UserPrincipal principal = AuthSupport.optional(authentication);
        NewsDtos.Detail detail = repository.detail(id, principal != null && principal.hasAnyRole("ROLE_ADMIN"));
        if (detail == null) {
            throw ApiException.notFound("公告不存在");
        }
        return detail;
    }
}
