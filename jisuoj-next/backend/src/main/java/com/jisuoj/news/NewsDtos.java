package com.jisuoj.news;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

public final class NewsDtos {
    private NewsDtos() {
    }

    public record Summary(int id, String title, String author, LocalDateTime publishedAt, int importance) {
    }

    public record Detail(int id, String title, String content, String author,
                         LocalDateTime publishedAt, int importance, boolean menu, boolean hidden) {
    }

    public record SaveRequest(
            @NotBlank @Size(max = 200) String title,
            @NotBlank @Size(max = 4_000_000) String content,
            @Min(0) @Max(9) Integer importance,
            Boolean menu,
            Boolean hidden) {
    }
}
