package com.jisuoj.problem;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public final class ProblemDtos {
    private ProblemDtos() {
    }

    public record Summary(
            int id,
            String title,
            String source,
            int accepted,
            int submit,
            double acceptanceRate,
            String userStatus) {
    }

    public record AdminSummary(
            int id,
            String title,
            String source,
            boolean hidden,
            int accepted,
            int submit,
            LocalDateTime createdAt) {
    }

    public record Detail(
            int id,
            String title,
            String description,
            String input,
            String output,
            String sampleInput,
            String sampleOutput,
            String hint,
            String source,
            BigDecimal timeLimit,
            int memoryLimit,
            boolean specialJudge,
            boolean hidden,
            int accepted,
            int submit,
            LocalDateTime createdAt,
            String userStatus) {
    }

    public record AcceptedUser(
            String userId,
            String nick,
            String school,
            LocalDateTime firstAcceptedAt) {
    }

    public record SaveRequest(
            @NotBlank @Size(max = 200) String title,
            @Size(max = 2_000_000) String description,
            @Size(max = 2_000_000) String input,
            @Size(max = 2_000_000) String output,
            @Size(max = 200_000) String sampleInput,
            @Size(max = 200_000) String sampleOutput,
            @Size(max = 2_000_000) String hint,
            @Size(max = 100) String source,
            @DecimalMin("0.001") @DecimalMax("3600") BigDecimal timeLimit,
            @Min(1) @Max(4_194_304) Integer memoryLimit,
            Boolean specialJudge,
            Boolean hidden) {
    }
}
