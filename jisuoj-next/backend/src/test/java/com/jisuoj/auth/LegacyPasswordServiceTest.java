package com.jisuoj.auth;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LegacyPasswordServiceTest {
    private final LegacyPasswordService service = new LegacyPasswordService();

    @Test
    void acceptsAndRejectsSaltedLegacyPasswords() {
        String saved = service.encode("correct horse battery staple");

        assertThat(service.matches("correct horse battery staple", saved)).isTrue();
        assertThat(service.matches("wrong", saved)).isFalse();
    }

    @Test
    void acceptsHistoricalPlainMd5Hash() {
        assertThat(service.matches("password", "5f4dcc3b5aa765d61d8327deb882cf99")).isTrue();
        assertThat(service.matches("not-password", "5f4dcc3b5aa765d61d8327deb882cf99")).isFalse();
    }
}
