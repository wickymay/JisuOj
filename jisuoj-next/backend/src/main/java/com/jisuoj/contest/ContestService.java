package com.jisuoj.contest;

import com.jisuoj.auth.UserPrincipal;
import com.jisuoj.common.ApiException;
import com.jisuoj.common.PageResponse;
import com.jisuoj.judge.JudgeCatalog;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class ContestService {
    private final ContestRepository repository;

    public ContestService(ContestRepository repository) {
        this.repository = repository;
    }

    public PageResponse<ContestDtos.Summary> list(int page, int size, UserPrincipal principal) {
        return repository.findPage(page, size, canManage(principal));
    }

    public ContestDtos.Detail detail(int id, UserPrincipal principal) {
        ContestRepository.ContestRow row = requireContest(id, principal);
        checkAccess(row, principal);
        return new ContestDtos.Detail(row.id(), row.title(), row.description(), row.startTime(), row.endTime(),
                ContestRepository.status(row.startTime(), row.endTime()), row.privateContest(), row.type(),
                "Y".equalsIgnoreCase(row.defunct()), repository.problems(id));
    }

    public List<ContestDtos.Standing> standings(int id, UserPrincipal principal) {
        ContestRepository.ContestRow contest = requireContest(id, principal);
        checkAccess(contest, principal);
        if (LocalDateTime.now().isBefore(contest.startTime()) && !canManage(principal)) {
            throw ApiException.forbidden("比赛尚未开始");
        }
        List<ContestDtos.Problem> problems = repository.problems(id);
        Map<String, MutableStanding> users = new LinkedHashMap<>();
        for (ContestRepository.SubmissionRow submission : repository.submissions(id)) {
            MutableStanding standing = users.computeIfAbsent(submission.userId(),
                    ignored -> new MutableStanding(submission.userId(), submission.nick()));
            MutableProblem result = standing.problems.computeIfAbsent(submission.number(), ignored -> new MutableProblem());
            if (result.accepted) {
                continue;
            }
            result.lastResult = JudgeCatalog.resultCode(submission.result());
            if (submission.result() == 4) {
                result.accepted = true;
                result.acceptedMinutes = Math.max(0, Duration.between(contest.startTime(), submission.submittedAt()).toMinutes());
                standing.solved++;
                standing.penalty += result.acceptedMinutes + result.wrongAttempts * 20L;
            } else if (submission.result() >= 5 && submission.result() <= 12) {
                result.wrongAttempts++;
            }
        }
        List<MutableStanding> sorted = new ArrayList<>(users.values());
        sorted.sort(Comparator.comparingInt((MutableStanding row) -> row.solved).reversed()
                .thenComparingLong(row -> row.penalty).thenComparing(row -> row.userId));
        List<ContestDtos.Standing> result = new ArrayList<>();
        int rank = 0;
        int displayRank = 0;
        MutableStanding previous = null;
        for (MutableStanding row : sorted) {
            rank++;
            if (previous == null || previous.solved != row.solved || previous.penalty != row.penalty) {
                displayRank = rank;
            }
            Map<Integer, ContestDtos.ProblemResult> cells = new LinkedHashMap<>();
            for (ContestDtos.Problem problem : problems) {
                MutableProblem cell = row.problems.get(problem.number());
                if (cell != null) {
                    cells.put(problem.number(), new ContestDtos.ProblemResult(
                            cell.accepted, cell.wrongAttempts + (cell.accepted ? 1 : 0), cell.acceptedMinutes, cell.lastResult));
                }
            }
            result.add(new ContestDtos.Standing(displayRank, row.userId, row.nick, row.solved, row.penalty, cells));
            previous = row;
        }
        return result;
    }

    public void join(int id, String password, UserPrincipal principal) {
        ContestRepository.ContestRow contest = repository.find(id, false);
        if (contest == null) {
            throw ApiException.notFound("比赛不存在");
        }
        if (!contest.privateContest()) {
            return;
        }
        if (contest.password() == null || !contest.password().equals(password)) {
            throw ApiException.forbidden("比赛密码错误");
        }
        repository.grantAccess(id, principal.userId());
    }

    private ContestRepository.ContestRow requireContest(int id, UserPrincipal principal) {
        ContestRepository.ContestRow contest = repository.find(id, canManage(principal));
        if (contest == null) {
            throw ApiException.notFound("比赛不存在");
        }
        return contest;
    }

    private void checkAccess(ContestRepository.ContestRow contest, UserPrincipal principal) {
        if (!contest.privateContest() || canManage(principal)) {
            return;
        }
        if (principal == null || !repository.hasAccess(contest.id(), principal.userId())) {
            throw ApiException.forbidden("这是私有比赛，请先输入比赛密码或联系管理员授权");
        }
    }

    private boolean canManage(UserPrincipal principal) {
        return principal != null && principal.hasAnyRole("ROLE_ADMIN", "ROLE_CONTEST_CREATOR");
    }

    private static final class MutableStanding {
        private final String userId;
        private final String nick;
        private int solved;
        private long penalty;
        private final Map<Integer, MutableProblem> problems = new LinkedHashMap<>();

        private MutableStanding(String userId, String nick) {
            this.userId = userId;
            this.nick = nick == null || nick.isBlank() ? userId : nick;
        }
    }

    private static final class MutableProblem {
        private boolean accepted;
        private int wrongAttempts;
        private Long acceptedMinutes;
        private String lastResult;
    }
}
