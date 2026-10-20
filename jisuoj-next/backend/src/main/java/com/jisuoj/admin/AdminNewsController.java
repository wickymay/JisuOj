package com.jisuoj.admin;

import com.jisuoj.auth.AuthSupport;
import com.jisuoj.common.ApiException;
import com.jisuoj.news.NewsDtos;
import com.jisuoj.news.NewsRepository;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/admin/news")
@PreAuthorize("hasRole('ADMIN')")
public class AdminNewsController {
    private final NewsRepository repository;

    public AdminNewsController(NewsRepository repository) {
        this.repository = repository;
    }

    @PostMapping
    public Map<String, Integer> create(@Valid @RequestBody NewsDtos.SaveRequest request,
                                       Authentication authentication) {
        return Map.of("id", repository.create(request, AuthSupport.required(authentication).userId()));
    }

    @PutMapping("/{id}")
    public void update(@PathVariable int id, @Valid @RequestBody NewsDtos.SaveRequest request) {
        if (!repository.update(id, request)) {
            throw ApiException.notFound("公告不存在");
        }
    }

    @DeleteMapping("/{id}")
    public void hide(@PathVariable int id) {
        if (!repository.hide(id)) {
            throw ApiException.notFound("公告不存在");
        }
    }
}
