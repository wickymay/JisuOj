package com.jisuoj.auth;

import java.util.Set;

public record UserPrincipal(String userId, String nick, Set<String> roles) {
    public boolean hasAnyRole(String... expected) {
        for (String role : expected) {
            if (roles.contains(role)) {
                return true;
            }
        }
        return false;
    }
}
