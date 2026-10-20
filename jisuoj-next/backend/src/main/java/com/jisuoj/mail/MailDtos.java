package com.jisuoj.mail;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

public final class MailDtos {
    private MailDtos() {
    }

    public record Message(long id, String toUser, String fromUser, String title, String content,
                          boolean unread, LocalDateTime sentAt, boolean sentByMe) {
    }

    public record SendRequest(
            @NotBlank @Size(max = 48) String toUser,
            @NotBlank @Size(max = 200) String title,
            @NotBlank @Size(max = 20_000) String content,
            Long replyTo) {
    }
}
