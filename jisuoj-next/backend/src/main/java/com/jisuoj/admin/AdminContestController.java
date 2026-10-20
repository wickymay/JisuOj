package com.jisuoj.admin;

import com.jisuoj.auth.AuthSupport;
import com.jisuoj.common.ApiException;
import com.jisuoj.contest.ContestDtos;
import com.jisuoj.contest.ContestRepository;
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
@RequestMapping("/api/admin/contests")
@PreAuthorize("hasAnyRole('ADMIN','CONTEST_CREATOR')")
public class AdminContestController {
    private final ContestRepository repository;

    public AdminContestController(ContestRepository repository) {
        this.repository = repository;
    }

    @PostMapping
    public Map<String, Integer> create(@Valid @RequestBody ContestDtos.SaveRequest request,
                                       Authentication authentication) {
        validateTime(request);
        return Map.of("id", repository.create(request, AuthSupport.required(authentication).userId()));
    }

    @PutMapping("/{id}")
    public void update(@PathVariable int id, @Valid @RequestBody ContestDtos.SaveRequest request) {
        validateTime(request);
        if (!repository.update(id, request)) {
            throw ApiException.notFound("比赛不存在");
        }
    }

    @DeleteMapping("/{id}")
    public void hide(@PathVariable int id) {
        if (!repository.setHidden(id, true)) {
            throw ApiException.notFound("比赛不存在");
        }
    }

    private void validateTime(ContestDtos.SaveRequest request) {
        if (!request.endTime().isAfter(request.startTime())) {
            throw ApiException.badRequest("结束时间必须晚于开始时间");
        }
    }
}
