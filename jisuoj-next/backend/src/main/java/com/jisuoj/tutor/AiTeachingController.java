package com.jisuoj.tutor;

import com.jisuoj.auth.AuthSupport;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/ai/teach")
@PreAuthorize("isAuthenticated()")
public class AiTeachingController {
    private final AiTeachingService service;

    public AiTeachingController(AiTeachingService service) {
        this.service = service;
    }

    @GetMapping("/status")
    public AiTeachingDtos.Status status() {
        return new AiTeachingDtos.Status(service.configured(), service.knowledgeStatus(), service.teachingSkillStatus());
    }

    @PostMapping
    public AiTeachingDtos.Answer ask(@Valid @RequestBody AiTeachingDtos.AskRequest request,
                                     Authentication authentication) {
        return service.answer(request, AuthSupport.required(authentication));
    }
}
