package com.jisuoj.submission;

import com.jisuoj.auth.UserPrincipal;
import com.jisuoj.common.ApiException;
import com.jisuoj.common.PageResponse;
import com.jisuoj.config.JisuOjProperties;
import com.jisuoj.judge.JudgeCatalog;
import com.jisuoj.judge.JudgeNotifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
public class SubmissionService {
    private final SubmissionRepository repository;
    private final JudgeNotifier notifier;
    private final JisuOjProperties properties;

    public SubmissionService(SubmissionRepository repository, JudgeNotifier notifier, JisuOjProperties properties) {
        this.repository = repository;
        this.notifier = notifier;
        this.properties = properties;
    }

    public PageResponse<SubmissionDtos.Summary> list(int page, int size, Integer problemId, String userId,
                                                      Integer language, Integer result, Integer contestId) {
        return repository.findPage(page, size, problemId, userId, language, result, contestId);
    }

    @Transactional
    public SubmissionDtos.Created submit(SubmissionDtos.CreateRequest request, UserPrincipal principal, String ip) {
        if (!JudgeCatalog.supportsLanguage(request.language())) {
            throw ApiException.badRequest("不支持的编程语言");
        }
        if (!repository.existsVisibleProblem(request.problemId())) {
            throw ApiException.notFound("题目不存在或尚未公开");
        }
        LocalDateTime last = repository.lastSubmissionAt(principal.userId());
        int cooldown = properties.getJudge().getSubmissionCooldownSeconds();
        if (last != null && last.plusSeconds(cooldown).isAfter(LocalDateTime.now())) {
            throw new ApiException(org.springframework.http.HttpStatus.TOO_MANY_REQUESTS,
                    "提交过于频繁，请等待 " + cooldown + " 秒");
        }
        int contestNum = -1;
        if (request.contestId() != null) {
            SubmissionRepository.ContestBinding binding = repository.contestBinding(
                    request.contestId(), request.problemId(), principal.userId(), principal.hasAnyRole("ROLE_ADMIN"));
            if (binding == null) {
                throw ApiException.forbidden("无权在该比赛中提交此题");
            }
            LocalDateTime now = LocalDateTime.now();
            if (now.isBefore(binding.startTime()) || !now.isBefore(binding.endTime())) {
                throw ApiException.badRequest("比赛当前不在提交时间内");
            }
            contestNum = binding.number();
        }
        long id = repository.create(request.problemId(), principal.userId(), repository.userNick(principal.userId()),
                request.language(), normalize(request.source()), ip, request.contestId(), contestNum);
        notifier.notifySubmission((int) id);
        return new SubmissionDtos.Created(id);
    }

    public SubmissionDtos.Source source(long id, UserPrincipal principal) {
        SubmissionDtos.Source source = repository.source(id);
        if (source == null) {
            throw ApiException.notFound("提交不存在");
        }
        if (!source.userId().equals(principal.userId()) &&
                !principal.hasAnyRole("ROLE_ADMIN", "ROLE_SOURCE_BROWSER")) {
            throw ApiException.forbidden("无权查看该提交的源码");
        }
        return source;
    }

    public SubmissionDtos.Diagnostics diagnostics(long id, UserPrincipal principal) {
        SubmissionDtos.Source source = repository.source(id);
        if (source == null) {
            throw ApiException.notFound("提交不存在");
        }
        if (!source.userId().equals(principal.userId()) &&
                !principal.hasAnyRole("ROLE_ADMIN", "ROLE_SOURCE_BROWSER")) {
            throw ApiException.forbidden("无权查看该提交的诊断信息");
        }
        return repository.diagnostics(id);
    }

    private String normalize(String source) {
        return source.replace("\r\n", "\n").replace('\r', '\n');
    }
}
