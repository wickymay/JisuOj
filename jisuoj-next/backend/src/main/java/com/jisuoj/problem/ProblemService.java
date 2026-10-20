package com.jisuoj.problem;

import com.jisuoj.auth.UserPrincipal;
import com.jisuoj.common.ApiException;
import com.jisuoj.common.PageResponse;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ProblemService {
    private final ProblemRepository repository;

    public ProblemService(ProblemRepository repository) {
        this.repository = repository;
    }

    public PageResponse<ProblemDtos.Summary> list(int page, int size, String keyword, String source,
                                                  UserPrincipal principal) {
        return repository.findPage(page, size, keyword, source, userId(principal), canEdit(principal));
    }

    public ProblemDtos.Detail detail(int id, UserPrincipal principal) {
        ProblemDtos.Detail detail = repository.findById(id, userId(principal), canEdit(principal));
        if (detail == null) {
            throw ApiException.notFound("题目不存在或尚未公开");
        }
        return detail;
    }

    public PageResponse<ProblemDtos.AcceptedUser> acceptedUsers(int id, int page, int size,
                                                                 UserPrincipal principal) {
        detail(id, principal);
        return repository.findAcceptedUsers(id, page, size);
    }

    public List<String> sources() {
        return repository.sources();
    }

    private String userId(UserPrincipal principal) {
        return principal == null ? null : principal.userId();
    }

    private boolean canEdit(UserPrincipal principal) {
        return principal != null && principal.hasAnyRole("ROLE_ADMIN", "ROLE_PROBLEM_EDITOR");
    }
}
