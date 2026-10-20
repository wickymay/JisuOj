package com.jisuoj.discussion;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;
import java.util.List;

public final class DiscussionDtos {
    private DiscussionDtos() {
    }

    public record TopicSummary(int id, String title, String author, int problemId, Integer contestId,
                               int replyCount, LocalDateTime lastReplyAt, int topLevel) {
    }

    public record Reply(int id, String author, String content, LocalDateTime createdAt) {
    }

    public record Topic(int id, String title, String author, int problemId, Integer contestId,
                        LocalDateTime createdAt, List<Reply> replies) {
    }

    public record CreateTopicRequest(
            @NotBlank @Size(max = 60) String title,
            @NotBlank @Size(max = 20_000) String content,
            Integer problemId,
            Integer contestId) {
    }

    public record CreateReplyRequest(@NotBlank @Size(max = 20_000) String content) {
    }
}
