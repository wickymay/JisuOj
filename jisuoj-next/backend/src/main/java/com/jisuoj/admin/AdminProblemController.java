package com.jisuoj.admin;

import com.jisuoj.auth.AuthSupport;
import com.jisuoj.common.ApiException;
import com.jisuoj.common.PageResponse;
import com.jisuoj.problem.ProblemDtos;
import com.jisuoj.problem.ProblemRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

import static com.jisuoj.common.RequestSupport.clientIp;
import static com.jisuoj.common.RequestSupport.page;
import static com.jisuoj.common.RequestSupport.size;

@RestController
@RequestMapping("/api/admin/problems")
@PreAuthorize("hasAnyRole('ADMIN','PROBLEM_EDITOR')")
public class AdminProblemController {
    private final ProblemRepository repository;
    private final ProblemDeletionService deletionService;

    public AdminProblemController(ProblemRepository repository, ProblemDeletionService deletionService) {
        this.repository = repository;
        this.deletionService = deletionService;
    }

    @GetMapping
    public PageResponse<ProblemDtos.AdminSummary> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String keyword) {
        return repository.findAdminPage(page(page), size(size), keyword);
    }

    @PostMapping
    public Map<String, Integer> create(@Valid @RequestBody ProblemDtos.SaveRequest request) {
        return Map.of("id", repository.create(request));
    }

    @PutMapping("/{id}")
    public void update(@PathVariable int id, @Valid @RequestBody ProblemDtos.SaveRequest request) {
        if (!repository.update(id, request)) {
            throw ApiException.notFound("题目不存在");
        }
    }

    @DeleteMapping("/{id}")
    public void hide(@PathVariable int id) {
        if (!repository.setHidden(id, true)) {
            throw ApiException.notFound("题目不存在");
        }
    }

    @PostMapping("/{id}/restore")
    public void restore(@PathVariable int id) {
        if (!repository.setHidden(id, false)) {
            throw ApiException.notFound("题目不存在");
        }
    }

    @DeleteMapping("/{id}/permanent")
    public void deletePermanent(@PathVariable int id, Authentication authentication, HttpServletRequest request) {
        deletionService.deleteHiddenUnused(id, AuthSupport.required(authentication).userId(), clientIp(request));
    }
}
