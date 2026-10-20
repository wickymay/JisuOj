package com.jisuoj.common;

import java.util.List;

public record PageResponse<T>(List<T> items, int page, int size, long total) {
    public static <T> PageResponse<T> of(List<T> items, int page, int size, long total) {
        return new PageResponse<>(items, page, size, total);
    }
}
