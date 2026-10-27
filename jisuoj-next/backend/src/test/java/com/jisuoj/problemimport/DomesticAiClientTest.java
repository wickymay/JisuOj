package com.jisuoj.problemimport;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jisuoj.common.ApiException;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import java.net.InetSocketAddress;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class DomesticAiClientTest {
    @Test void adaptsAllFourProvidersAndNeverSendsKeysInTheBody() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        AtomicReference<JsonNode> body = new AtomicReference<>();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/chat/completions", exchange -> {
            body.set(mapper.readTree(exchange.getRequestBody()));
            assertThat(exchange.getRequestHeaders().getFirst("Authorization")).isEqualTo("Bearer secret-fixture");
            byte[] response = "{\"choices\":[{\"message\":{\"content\":\"```json\\n{\\\"ok\\\":true}\\n```\"},\"finish_reason\":\"stop\"}]}".getBytes();
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response); exchange.close();
        });
        server.start();
        try {
            var configuration = mock(AiRuntimeConfigService.class);
            var client = new DomesticAiClient(configuration, mapper);
            for (var provider : AiProvider.values()) {
                when(configuration.requireConfig(provider)).thenReturn(new AiRuntimeConfigService.Config(
                        "http://127.0.0.1:" + server.getAddress().getPort() + "/v1", "secret-fixture", provider.model, Instant.now()));
                assertThat(client.call(provider, "Return JSON", "fixture").path("ok").asBoolean()).isTrue();
                assertThat(body.get().toString()).doesNotContain("secret-fixture");
                assertThat(body.get().path("model").asText()).isEqualTo(provider.model);
                if (provider == AiProvider.QWEN) {
                    assertThat(body.get().path("enable_thinking").asBoolean(true)).isFalse();
                    assertThat(body.get().has("thinking")).isFalse();
                }
                if (provider == AiProvider.DEEPSEEK) assertThat(body.get().path("thinking").path("type").asText()).isEqualTo("enabled");
                if (provider == AiProvider.GLM) {
                    assertThat(body.get().has("thinking")).isFalse();
                    assertThat(body.get().path("reasoning_effort").asText()).isEqualTo("high");
                    assertThat(body.get().path("response_format").path("type").asText()).isEqualTo("json_object");
                }
                if (provider == AiProvider.KIMI) {
                    assertThat(body.get().path("reasoning_effort").asText()).isEqualTo("high");
                    assertThat(body.get().has("temperature")).isFalse();
                }
            }
        } finally { server.stop(0); }
    }

    @Test void upstreamErrorsDoNotExposeKeysOrProviderErrorBodies() throws Exception {
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/chat/completions", exchange -> {
            byte[] bytes = "secret-fixture secret-provider-error".getBytes();
            exchange.sendResponseHeaders(401, bytes.length); exchange.getResponseBody().write(bytes); exchange.close();
        });
        server.start();
        try {
            var config = mock(AiRuntimeConfigService.class);
            when(config.requireConfig(AiProvider.GLM)).thenReturn(new AiRuntimeConfigService.Config(
                    "http://127.0.0.1:" + server.getAddress().getPort(), "secret-fixture", "test", Instant.now()));
            assertThatThrownBy(() -> new DomesticAiClient(config, new ObjectMapper()).call(AiProvider.GLM, "JSON", "test"))
                    .isInstanceOf(ApiException.class).hasMessageContaining("401").hasMessageNotContaining("secret");
        } finally { server.stop(0); }
    }

    @Test void glmThinkingErrorsAreNotReportedAsInsufficientBalance() throws Exception {
        assertGlmError(400, "1210", "该模型始终思考，不支持关闭思考；secret-fixture", "必须启用思考", "余额");
        assertGlmError(400, "1211", "模型不存在 secret-fixture", "模型名称", "额度");
        assertGlmError(429, "1113", "账户已欠费 secret-fixture", "余额", "频率");
    }

    @Test void acceptsWrappedJsonWithoutChangingBracesEscapesOrThinkTagsInsideCode() throws Exception {
        var mapper = new ObjectMapper();
        String code = "int main() { cout << \"<think>literal</think> { \\\"quoted\\\" }\"; }\n";
        String json = mapper.writeValueAsString(java.util.Map.of("independent_solution_cpp", code, "validator_cpp", "validator"));
        for (String content : java.util.List.of(json, "以下是所需 JSON：\n```JSON\n" + json + "\n```\n以上为生成结果。",
                "<think>这段思考含有 {非 JSON}，不应作为答案解析。</think>\n" + json)) {
            JsonNode result = callResponse(envelope(content, "stop"));
            assertThat(result.path("independent_solution_cpp").asText()).isEqualTo(code);
            assertThat(result.path("validator_cpp").asText()).isEqualTo("validator");
        }
    }

    @Test void rejectsAmbiguousMalformedOrNonObjectAnswersWithoutLeakingContent() throws Exception {
        for (String content : java.util.List.of("[]", "[{\"ok\":true}]", "{\"ok\":true} {\"different\":true}",
                "```json\n{\"code\":\"secret-fixture\ninvalid literal newline\"}\n```",
                "secret-fixture: not JSON", "{\"code\":\"truncated")) {
            assertThatThrownBy(() -> callResponse(envelope(content, "stop")))
                    .isInstanceOf(ApiException.class).hasMessageContaining("已响应").hasMessageContaining("JSON")
                    .hasMessageNotContaining("secret-fixture").hasMessageNotContaining("网络");
        }
    }

    @Test void distinguishesEmptyReasoningTruncationAndInvalidApiEnvelopes() throws Exception {
        assertThatThrownBy(() -> callResponse(envelope("", "stop")))
                .hasMessageContaining("最终内容为空");
        assertThatThrownBy(() -> callResponse("{\"choices\":[{\"message\":{\"content\":null,\"reasoning_content\":\"secret-fixture\"},\"finish_reason\":\"stop\"}]}"))
                .hasMessageContaining("仅返回思考过程").hasMessageNotContaining("secret-fixture");
        assertThatThrownBy(() -> callResponse(envelope("{\"incomplete\":", "length")))
                .hasMessageContaining("截断");
        assertThatThrownBy(() -> callResponse("<html>secret-fixture proxy error</html>"))
                .hasMessageContaining("接口返回的响应不是有效 JSON").hasMessageNotContaining("secret-fixture");
        assertThatThrownBy(() -> callResponse("{\"unexpected\":\"secret-fixture\"}"))
                .hasMessageContaining("Chat Completions").hasMessageNotContaining("secret-fixture");
    }

    @Test void connectionFailuresAreNotReportedAsInvalidJsonOrInsufficientBalance() throws Exception {
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        int port = server.getAddress().getPort();
        server.start();
        server.stop(0);
        var config = mock(AiRuntimeConfigService.class);
        when(config.requireConfig(AiProvider.GLM)).thenReturn(new AiRuntimeConfigService.Config(
                "http://127.0.0.1:" + port, "secret-fixture", "glm-5.3", Instant.now()));
        assertThatThrownBy(() -> new DomesticAiClient(config, new ObjectMapper()).call(AiProvider.GLM, "JSON", "test"))
                .hasMessageContaining("无法连接模型接口").hasMessageNotContaining("JSON").hasMessageNotContaining("余额");
    }

    private String envelope(String content, String finish) throws Exception {
        return new ObjectMapper().writeValueAsString(java.util.Map.of("choices", java.util.List.of(java.util.Map.of(
                "message", java.util.Map.of("content", content), "finish_reason", finish))));
    }

    private JsonNode callResponse(String body) throws Exception {
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/chat/completions", exchange -> {
            exchange.getRequestBody().readAllBytes();
            byte[] bytes = body.getBytes(java.nio.charset.StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, bytes.length); exchange.getResponseBody().write(bytes); exchange.close();
        });
        server.start();
        try {
            var config = mock(AiRuntimeConfigService.class);
            when(config.requireConfig(AiProvider.GLM)).thenReturn(new AiRuntimeConfigService.Config(
                    "http://127.0.0.1:" + server.getAddress().getPort(), "secret-fixture", "glm-5.3", Instant.now()));
            return new DomesticAiClient(config, new ObjectMapper()).call(AiProvider.GLM, "Return JSON", "test");
        } finally { server.stop(0); }
    }

    private void assertGlmError(int status, String code, String message, String expected, String absent) throws Exception {
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/chat/completions", exchange -> {
            byte[] bytes = new ObjectMapper().writeValueAsBytes(java.util.Map.of("error", java.util.Map.of("code", code, "message", message)));
            exchange.sendResponseHeaders(status, bytes.length); exchange.getResponseBody().write(bytes); exchange.close();
        });
        server.start();
        try {
            var config = mock(AiRuntimeConfigService.class);
            when(config.requireConfig(AiProvider.GLM)).thenReturn(new AiRuntimeConfigService.Config(
                    "http://127.0.0.1:" + server.getAddress().getPort(), "secret-fixture", "glm-5.3", Instant.now()));
            assertThatThrownBy(() -> new DomesticAiClient(config, new ObjectMapper()).call(AiProvider.GLM, "JSON", "test"))
                    .isInstanceOf(ApiException.class).hasMessageContaining(expected).hasMessageContaining(code)
                    .hasMessageNotContaining(absent).hasMessageNotContaining("secret-fixture");
        } finally { server.stop(0); }
    }
}
