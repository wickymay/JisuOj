package com.jisuoj.testdata;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jisuoj.common.ApiException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.Map;

@Component
public class TestDataRunnerClient {
    private final String url;
    private final String token;
    private final ObjectMapper mapper;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
    public TestDataRunnerClient(@Value("${jisuoj.test-data.runner-url:}") String url,
                               @Value("${jisuoj.test-data.runner-token:}") String token, ObjectMapper mapper) {
        this.url = url.replaceAll("/+$", "");
        this.token = token;
        this.mapper = mapper;
    }
    public boolean configured() { return !url.isBlank() && token.length() >= 32; }
    public void requireReady() {
        if (!configured()) throw ApiException.badRequest("测试数据执行器尚未启用，请按部署文档配置执行器地址与密钥");
        try {
            var request = HttpRequest.newBuilder(URI.create(url + "/health")).timeout(Duration.ofSeconds(18))
                    .header("Authorization", "Bearer " + token).GET().build();
            var response = http.send(request, HttpResponse.BodyHandlers.discarding());
            if (response.statusCode() != 200) throw new IllegalStateException();
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt(); throw ApiException.badRequest("任务已中断");
        } catch (Exception ex) {
            throw ApiException.badRequest("隔离执行器不可用，请检查服务、密钥与工具链镜像");
        }
    }
    public JsonNode generate(Map<String, Object> payload) {
        try {
            var request = HttpRequest.newBuilder(URI.create(url + "/generate")).timeout(Duration.ofMinutes(12))
                    .header("Authorization", "Bearer " + token).header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(payload))).build();
            var response = http.send(request, HttpResponse.BodyHandlers.ofInputStream());
            try (var input = response.body()) {
                byte[] bytes = input.readNBytes(32 * 1024 * 1024 + 1);
                if (bytes.length > 32 * 1024 * 1024) throw ApiException.badRequest("执行器返回的数据超过限制");
                var result = mapper.readTree(bytes);
                if (response.statusCode() != 200 || !result.path("ok").asBoolean()) {
                    String message = result.path("error").asText("隔离执行失败");
                    throw ApiException.badRequest(message.substring(0, Math.min(message.length(), 4000)));
                }
                return result;
            }
        } catch (ApiException ex) { throw ex;
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt(); throw ApiException.badRequest("生成任务已中断");
        } catch (Exception ex) {
            throw ApiException.badRequest("无法完成隔离执行，请检查执行器服务与运行日志");
        }
    }
}
