package com.jisuoj.common;

import jakarta.servlet.http.HttpServletRequest;

public final class RequestSupport {
    private RequestSupport() {
    }

    public static String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        String value = forwarded == null || forwarded.isBlank()
                ? request.getRemoteAddr()
                : forwarded.split(",", 2)[0].trim();
        return value == null ? "" : value.substring(0, Math.min(46, value.length()));
    }

    public static int page(int page) {
        return Math.max(1, page);
    }

    public static int size(int size) {
        return Math.max(1, Math.min(100, size));
    }
}
