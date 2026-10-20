package com.jisuoj.tutor;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

public final class AiTeachingDtos {
    private AiTeachingDtos() {
    }

    public record Message(
            @NotBlank String role,
            @NotBlank @Size(max = 6_000) String content) {
    }

    public record AskRequest(
            @Min(1) Integer problemId,
            @NotEmpty @Size(max = 15) List<@Valid Message> messages,
            @Size(max = 16_000) String sourceCode,
            @Size(max = 60) String sourceLanguage,
            boolean includeRecentSubmissions) {
    }

    public record KnowledgeSource(int number, String chunkId, String bookTitle, String chapter,
                                  String section, int pageStart, int pageEnd) {
    }

    public record KnowledgeStatus(String status, int chunks) {
    }

    public record TeachingTopic(String id, String title) {
    }

    public record TeachingSkillStatus(String status, String id, String version, int topics, int sourceBooks) {
    }

    public record TeachingSkillUse(String status, String version, String mode, List<TeachingTopic> topics) {
    }

    public record Answer(String content, List<KnowledgeSource> sources, String knowledgeStatus,
                         TeachingSkillUse teachingSkill) {
    }

    public record Status(boolean configured, KnowledgeStatus knowledgeBase, TeachingSkillStatus teachingSkill) {
    }
}
