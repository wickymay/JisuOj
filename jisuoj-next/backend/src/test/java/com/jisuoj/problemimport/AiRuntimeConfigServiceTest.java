package com.jisuoj.problemimport;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Base64;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AiRuntimeConfigServiceTest {
    private static final String SECRET = "test-ai-config-secret-with-at-least-32-characters";

    @Test
    void persistsEncryptedKeyAndCanReadItAfterServiceRecreation() throws Exception {
        JdbcTemplate writerJdbc = mock(JdbcTemplate.class);
        AiRuntimeConfigService writer = new AiRuntimeConfigService(writerJdbc, SECRET);

        writer.configure("https://api.deepseek.com/", "deepseek-chat", "sk-sensitive-value");

        var ciphertext = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(writerJdbc).update(contains("INSERT INTO jisu_ai_config"), eq(1),
                eq("https://api.deepseek.com"), eq("deepseek-chat"), ciphertext.capture(), any(Timestamp.class));
        assertThat(ciphertext.getValue()).doesNotContain("sk-sensitive-value");
        assertThat(Base64.getDecoder().decode(ciphertext.getValue()).length).isGreaterThan(12);

        JdbcTemplate readerJdbc = mock(JdbcTemplate.class);
        Timestamp updatedAt = Timestamp.from(Instant.parse("2026-09-22T12:00:00Z"));
        when(readerJdbc.query(contains("api_key_ciphertext"),
                org.mockito.ArgumentMatchers.<RowMapper<AiRuntimeConfigService.Config>>any(), eq(1)))
                .thenAnswer(invocation -> {
                    RowMapper<AiRuntimeConfigService.Config> mapper = invocation.getArgument(1);
                    ResultSet rs = mock(ResultSet.class);
                    when(rs.getString("base_url")).thenReturn("https://api.deepseek.com");
                    when(rs.getString("model")).thenReturn("deepseek-chat");
                    when(rs.getString("api_key_ciphertext")).thenReturn(ciphertext.getValue());
                    when(rs.getTimestamp("updated_at")).thenReturn(updatedAt);
                    return List.of(mapper.mapRow(rs, 0));
                });

        AiRuntimeConfigService reader = new AiRuntimeConfigService(readerJdbc, SECRET);
        AiRuntimeConfigService.Config restored = reader.requireConfig();

        assertThat(restored.baseUrl()).isEqualTo("https://api.deepseek.com");
        assertThat(restored.model()).isEqualTo("deepseek-chat");
        assertThat(restored.apiKey()).isEqualTo("sk-sensitive-value");
        assertThat(restored.configuredAt()).isEqualTo(updatedAt.toInstant());
    }

    @Test
    void statusNeverReadsOrReturnsTheApiKey() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        Timestamp updatedAt = Timestamp.from(Instant.parse("2026-09-22T12:00:00Z"));
        when(jdbc.query(anyString(),
                org.mockito.ArgumentMatchers.<RowMapper<AiRuntimeConfigService.Status>>any(), eq(1)))
                .thenAnswer(invocation -> {
                    RowMapper<AiRuntimeConfigService.Status> mapper = invocation.getArgument(1);
                    ResultSet rs = mock(ResultSet.class);
                    when(rs.getString("base_url")).thenReturn("https://api.deepseek.com");
                    when(rs.getString("model")).thenReturn("deepseek-chat");
                    when(rs.getTimestamp("updated_at")).thenReturn(updatedAt);
                    return List.of(mapper.mapRow(rs, 0));
                });

        AiRuntimeConfigService.Status status = new AiRuntimeConfigService(jdbc, SECRET).status();

        assertThat(status.configured()).isTrue();
        assertThat(status.baseUrl()).isEqualTo("https://api.deepseek.com");
        assertThat(status.model()).isEqualTo("deepseek-chat");
    }
}
