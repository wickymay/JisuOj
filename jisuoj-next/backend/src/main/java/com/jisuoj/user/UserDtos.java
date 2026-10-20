package com.jisuoj.user;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;
import java.time.LocalDate;
import java.util.List;

public final class UserDtos {
    private UserDtos() {
    }

    public record Profile(String userId, String nick, String school, String groupName, String email,
                          int solved, int submit, LocalDateTime registeredAt, LocalDateTime lastActiveAt,
                          long followerCount, long followingCount, boolean followedByMe, List<Integer> solvedProblems,
                          List<Integer> attemptedProblems, int rank, boolean starred,
                          int coinEarned, int coinBonus, int coinSpent,
                          List<ResultStat> resultStats, List<DailyStat> dailyStats, List<ContestStat> contestStats) {
    }

    public record ResultStat(int resultId, long count) {
    }

    public record DailyStat(LocalDate day, long submissions, long accepted) {
    }

    public record ContestStat(int contestId, String title, LocalDateTime startTime, long tried, long accepted) {
    }

    public record UpdateProfileRequest(
            @Size(max = 20) String nick,
            @Size(max = 20) String school,
            @Size(max = 16) String groupName,
            @Email @Size(max = 100) String email) {
    }

    public record Message(long id, String profileUserId, String authorId, String content, LocalDateTime createdAt) {
    }

    public record MessageRequest(@NotBlank @Size(max = 1000) String content) {
    }
}
