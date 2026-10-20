package com.jisuoj.submission;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

public final class SubmissionDtos {
    private SubmissionDtos() {
    }

    public record Summary(
            long id,
            int problemId,
            String problemTitle,
            String userId,
            String nick,
            String result,
            int resultId,
            int memory,
            int time,
            String language,
            int languageId,
            int codeLength,
            LocalDateTime submittedAt,
            String judger,
            Integer contestId) {
    }

    public record CreateRequest(
            @NotNull @Min(1) Integer problemId,
            @NotNull @Min(0) @Max(24) Integer language,
            @NotBlank @Size(min = 2, max = 65_536) String source,
            @Min(1) Integer contestId) {
    }

    public record Created(long id) {
    }

    public record Source(long id, int problemId, String userId, String language, String source) {
    }

    public record Diagnostics(long id, String compileError, String runtimeError) {
    }
}
