package com.jisuoj.contest;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public final class ContestDtos {
    private ContestDtos() {
    }

    public record Summary(
            int id,
            String title,
            LocalDateTime startTime,
            LocalDateTime endTime,
            String status,
            boolean privateContest,
            int type,
            String creator) {
    }

    public record Problem(int id, int number, String label, String title, int accepted, int submit) {
    }

    public record Detail(
            int id,
            String title,
            String description,
            LocalDateTime startTime,
            LocalDateTime endTime,
            String status,
            boolean privateContest,
            int type,
            boolean hidden,
            List<Problem> problems) {
    }

    public record ProblemResult(boolean accepted, int attempts, Long acceptedMinutes, String result) {
    }

    public record Standing(int rank, String userId, String nick, int solved, long penalty,
                           Map<Integer, ProblemResult> problems) {
    }

    public record JoinRequest(@NotBlank @Size(max = 32) String password) {
    }

    public record SaveRequest(
            @NotBlank @Size(max = 255) String title,
            @Size(max = 1_000_000) String description,
            @NotNull LocalDateTime startTime,
            @NotNull LocalDateTime endTime,
            Boolean privateContest,
            @Size(max = 16) String password,
            Integer type,
            Integer languageMask,
            Boolean hidden,
            List<Integer> problemIds) {
    }
}
