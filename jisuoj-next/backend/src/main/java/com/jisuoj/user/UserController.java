package com.jisuoj.user;

import com.jisuoj.auth.AuthSupport;
import com.jisuoj.auth.UserPrincipal;
import com.jisuoj.common.ApiException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

import static com.jisuoj.common.RequestSupport.clientIp;

@RestController
@RequestMapping("/api/users")
public class UserController {
    private final UserRepository repository;

    public UserController(UserRepository repository) {
        this.repository = repository;
    }

    @GetMapping("/{userId}")
    public UserDtos.Profile profile(@PathVariable String userId, Authentication authentication) {
        UserPrincipal viewer = AuthSupport.optional(authentication);
        UserDtos.Profile profile = repository.profile(userId, viewer == null ? null : viewer.userId());
        if (profile == null) {
            throw ApiException.notFound("用户不存在");
        }
        boolean canSeeEmail = viewer != null && (viewer.userId().equals(userId) || viewer.hasAnyRole("ROLE_ADMIN"));
        if (!canSeeEmail) {
            profile = new UserDtos.Profile(
                    profile.userId(), profile.nick(), profile.school(), profile.groupName(), null,
                    profile.solved(), profile.submit(), profile.registeredAt(), profile.lastActiveAt(),
                    profile.followerCount(), profile.followingCount(), profile.followedByMe(), profile.solvedProblems(),
                    profile.attemptedProblems(), profile.rank(), profile.starred(), profile.coinEarned(),
                    profile.coinBonus(), profile.coinSpent(), profile.resultStats(), profile.dailyStats(), profile.contestStats());
        }
        return profile;
    }

    @PutMapping("/me")
    @PreAuthorize("isAuthenticated()")
    public void update(@Valid @RequestBody UserDtos.UpdateProfileRequest request, Authentication authentication) {
        repository.update(AuthSupport.required(authentication).userId(), request);
    }

    @PostMapping("/{userId}/follow")
    @PreAuthorize("isAuthenticated()")
    public void follow(@PathVariable String userId, Authentication authentication) {
        String actor = AuthSupport.required(authentication).userId();
        if (actor.equals(userId)) {
            throw ApiException.badRequest("不能关注自己");
        }
        if (repository.profile(userId, actor) == null) {
            throw ApiException.notFound("用户不存在");
        }
        repository.follow(actor, userId);
    }

    @DeleteMapping("/{userId}/follow")
    @PreAuthorize("isAuthenticated()")
    public void unfollow(@PathVariable String userId, Authentication authentication) {
        repository.unfollow(AuthSupport.required(authentication).userId(), userId);
    }

    @GetMapping("/{userId}/messages")
    public List<UserDtos.Message> messages(@PathVariable String userId) {
        return repository.messages(userId, 50);
    }

    @PostMapping("/{userId}/messages")
    @PreAuthorize("isAuthenticated()")
    public void message(@PathVariable String userId, @Valid @RequestBody UserDtos.MessageRequest request,
                        Authentication authentication, HttpServletRequest servletRequest) {
        UserPrincipal actor = AuthSupport.required(authentication);
        if (repository.profile(userId, actor.userId()) == null) {
            throw ApiException.notFound("用户不存在");
        }
        repository.addMessage(userId, actor.userId(), request.content(), clientIp(servletRequest));
    }

    @DeleteMapping("/messages/{id}")
    @PreAuthorize("isAuthenticated()")
    public void deleteMessage(@PathVariable long id, Authentication authentication) {
        UserPrincipal principal = AuthSupport.required(authentication);
        if (!repository.deleteMessage(id, principal.userId(), principal.hasAnyRole("ROLE_ADMIN"))) {
            throw ApiException.notFound("留言不存在或无权删除");
        }
    }
}
