package com.jisuoj.auth;

import com.jisuoj.common.ApiException;
import org.springframework.security.core.Authentication;

public final class AuthSupport {
    private AuthSupport() {
    }

    public static UserPrincipal optional(Authentication authentication) {
        return authentication != null && authentication.getPrincipal() instanceof UserPrincipal principal
                ? principal : null;
    }

    public static UserPrincipal required(Authentication authentication) {
        UserPrincipal principal = optional(authentication);
        if (principal == null) {
            throw ApiException.unauthorized("请先登录");
        }
        return principal;
    }
}
