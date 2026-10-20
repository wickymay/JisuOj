package com.jisuoj.discussion;

import com.jisuoj.auth.AuthSupport;
import com.jisuoj.common.ApiException;
import com.jisuoj.common.PageResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

import static com.jisuoj.common.RequestSupport.clientIp;
import static com.jisuoj.common.RequestSupport.page;
import static com.jisuoj.common.RequestSupport.size;

@RestController
@RequestMapping("/api/discussions")
public class DiscussionController {
    private final DiscussionRepository repository;

    public DiscussionController(DiscussionRepository repository) {
        this.repository = repository;
    }

    @GetMapping
    public PageResponse<DiscussionDtos.TopicSummary> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) Integer problemId,
            @RequestParam(required = false) Integer contestId) {
        return repository.list(page(page), size(size), problemId, contestId);
    }

    @GetMapping("/{id}")
    public DiscussionDtos.Topic detail(@PathVariable int id) {
        DiscussionDtos.Topic topic = repository.detail(id);
        if (topic == null) {
            throw ApiException.notFound("讨论不存在");
        }
        return topic;
    }

    @PostMapping
    @PreAuthorize("isAuthenticated()")
    public Map<String, Integer> create(@Valid @RequestBody DiscussionDtos.CreateTopicRequest request,
                                       Authentication authentication, HttpServletRequest servletRequest) {
        return Map.of("id", repository.createTopic(
                request, AuthSupport.required(authentication).userId(), clientIp(servletRequest)));
    }

    @PostMapping("/{id}/replies")
    @PreAuthorize("isAuthenticated()")
    public Map<String, Integer> reply(@PathVariable int id,
                                      @Valid @RequestBody DiscussionDtos.CreateReplyRequest request,
                                      Authentication authentication, HttpServletRequest servletRequest) {
        if (!repository.exists(id)) {
            throw ApiException.notFound("讨论不存在");
        }
        return Map.of("id", repository.createReply(
                id, request.content(), AuthSupport.required(authentication).userId(), clientIp(servletRequest)));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public void hide(@PathVariable int id) {
        if (!repository.hideTopic(id)) {
            throw ApiException.notFound("讨论不存在");
        }
    }
}
