package com.jisuoj.problemimport;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.jisuoj.common.ApiException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import javax.net.ssl.SSLException;
import java.io.IOException;
import java.net.ConnectException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpConnectTimeoutException;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** One-shot structured calls; errors expose normalized codes, never raw bodies or credentials. */
@Component
public class DomesticAiClient {
    private static final Logger log = LoggerFactory.getLogger(DomesticAiClient.class);
    private final AiRuntimeConfigService configuration;
    private final ObjectMapper mapper;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).build();
    public DomesticAiClient(AiRuntimeConfigService configuration, ObjectMapper mapper) {
        this.configuration = configuration;
        this.mapper = mapper;
    }
    public JsonNode call(AiProvider provider, String system, String user) {
        var config = configuration.requireConfig(provider);
        long started = System.nanoTime();
        int status = 0, responseBytes = 0, contentCharacters = 0;
        String phase = "request", finishReason = "";
        try {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("model", config.model());
            body.put("max_tokens", 16000);
            if (provider == AiProvider.QWEN) body.put("enable_thinking", false);
            else if (provider == AiProvider.KIMI) body.put("reasoning_effort", "high");
            else if (provider == AiProvider.DEEPSEEK) {
                body.put("thinking", Map.of("type", "enabled"));
                body.put("reasoning_effort", "high");
            } else if (provider == AiProvider.GLM) {
                // GLM-5.3 always reasons and rejects thinking.type=disabled. Let the model
                // choose its supported default mode, including older GLM versions.
                String model = config.model().toLowerCase(java.util.Locale.ROOT);
                if (model.startsWith("glm-5.2") || model.startsWith("glm-5.3"))
                    body.put("reasoning_effort", "high");
            }
            if (provider == AiProvider.QWEN || provider == AiProvider.DEEPSEEK || provider == AiProvider.GLM)
                body.put("response_format", Map.of("type", "json_object"));
            body.put("messages", List.of(Map.of("role", "system", "content", system),
                    Map.of("role", "user", "content", user)));
            String endpoint = config.baseUrl();
            if (!endpoint.endsWith("/chat/completions")) endpoint += "/chat/completions";
            var request = HttpRequest.newBuilder(URI.create(endpoint)).timeout(Duration.ofSeconds(240))
                    .header("Authorization", "Bearer " + config.apiKey()).header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body))).build();
            var response = http.send(request, HttpResponse.BodyHandlers.ofInputStream());
            status = response.statusCode();
            try (var stream = response.body()) {
                if (response.statusCode() < 200 || response.statusCode() >= 300)
                    throw upstreamError(provider, response.statusCode(), stream.readNBytes(64000));
                byte[] bytes = stream.readNBytes(2_000_001);
                responseBytes = bytes.length;
                if (bytes.length > 2_000_000) throw ApiException.badRequest(provider.label + " 返回内容过大");
                phase = "response-json";
                var envelope = mapper.readTree(bytes);
                if (envelope == null || !envelope.path("choices").isArray() || envelope.path("choices").isEmpty())
                    throw ApiException.badRequest(provider.label + " 接口未返回对话结果，请核对 Chat Completions 接口地址");
                var choice = envelope.path("choices").path(0);
                finishReason = choice.path("finish_reason").asText("");
                if ("length".equals(finishReason))
                    throw ApiException.badRequest(provider.label + " 输出被截断，请精简题面或更换模型");
                if ("content_filter".equals(finishReason) || "sensitive".equals(finishReason))
                    throw ApiException.badRequest(provider.label + " 未输出结果：模型平台拦截了请求内容，请检查题面及补充要求");
                var message = choice.path("message");
                String content = message.path("content").asText("").trim();
                contentCharacters = content.length();
                if (content.isBlank()) {
                    if (!message.path("reasoning_content").asText("").isBlank())
                        throw ApiException.badRequest(provider.label + " 仅返回思考过程，未返回最终 JSON，请精简任务或增加输出预算");
                    throw ApiException.badRequest(provider.label + " 返回的最终内容为空，请稍后重新生成");
                }
                phase = "answer-json";
                return parseAnswer(content);
            }
        } catch (ApiException ex) {
            logFailure(provider, phase, status, responseBytes, contentCharacters, finishReason, started, ex);
            throw ex;
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw ApiException.badRequest("生成任务已中断");
        } catch (HttpConnectTimeoutException ex) {
            logFailure(provider, phase, status, responseBytes, contentCharacters, finishReason, started, ex);
            throw ApiException.badRequest(provider.label + " 连接模型接口超时（15秒），请检查服务器网络与接口地址");
        } catch (HttpTimeoutException ex) {
            logFailure(provider, phase, status, responseBytes, contentCharacters, finishReason, started, ex);
            throw ApiException.badRequest(provider.label + " 请求超时（240秒），模型尚未完成响应，请稍后重试或精简任务");
        } catch (JsonProcessingException | InvalidAnswerException ex) {
            logFailure(provider, phase, status, responseBytes, contentCharacters, finishReason, started, ex);
            throw ApiException.badRequest(provider.label + ("answer-json".equals(phase)
                    ? " 已响应，但最终内容不是单个有效 JSON 对象，请重新生成"
                    : " 接口返回的响应不是有效 JSON，请检查接口地址或代理服务"));
        } catch (ConnectException ex) {
            logFailure(provider, phase, status, responseBytes, contentCharacters, finishReason, started, ex);
            throw ApiException.badRequest(provider.label + " 无法连接模型接口，请检查后端服务器网络与接口地址");
        } catch (SSLException ex) {
            logFailure(provider, phase, status, responseBytes, contentCharacters, finishReason, started, ex);
            throw ApiException.badRequest(provider.label + " 接口 TLS 连接失败，请检查服务器证书、系统时间或代理配置");
        } catch (IOException ex) {
            logFailure(provider, phase, status, responseBytes, contentCharacters, finishReason, started, ex);
            throw ApiException.badRequest(provider.label + " 网络连接中断或响应读取失败，请稍后重试并检查服务器网络");
        } catch (Exception ex) {
            logFailure(provider, phase, status, responseBytes, contentCharacters, finishReason, started, ex);
            throw ApiException.badRequest(provider.label + " 请求处理失败，请联系管理员查看模型调用诊断日志");
        }
    }

    private JsonNode parseAnswer(String content) throws JsonProcessingException, InvalidAnswerException {
        String text = content.strip();
        // Remove only a leading reasoning wrapper; tags inside generated C++ strings are data.
        while (text.startsWith("<think>")) {
            int end = text.indexOf("</think>");
            if (end < 0) throw new InvalidAnswerException();
            text = text.substring(end + "</think>".length()).stripLeading();
        }
        try {
            var parsed = readSingleJson(text);
            if (parsed == null || !parsed.isObject()) throw new InvalidAnswerException();
            return parsed;
        } catch (JsonProcessingException ignored) {
            // Some compatible endpoints still wrap JSON in Markdown or a short explanation.
            // Extract balanced objects, respecting braces and escapes inside program strings.
        }
        JsonNode object = null;
        int depth = 0, start = -1;
        boolean quoted = false, escaped = false;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (depth == 0) {
                if (c == '{') { start = i; depth = 1; quoted = false; escaped = false; }
                continue;
            }
            if (quoted) {
                if (escaped) escaped = false;
                else if (c == '\\') escaped = true;
                else if (c == '"') quoted = false;
            } else if (c == '"') quoted = true;
            else if (c == '{') depth++;
            else if (c == '}' && --depth == 0) {
                var candidate = readSingleJson(text.substring(start, i + 1));
                if (!candidate.isObject() || object != null) throw new InvalidAnswerException();
                object = candidate;
            }
        }
        if (object == null || depth != 0) throw new InvalidAnswerException();
        return object;
    }
    private JsonNode readSingleJson(String text) throws JsonProcessingException {
        return mapper.reader().with(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).readTree(text);
    }
    private void logFailure(AiProvider provider, String phase, int status, int responseBytes,
                            int contentCharacters, String finishReason, long started, Exception ex) {
        // Do not log exception messages/stacks: JSON parser exceptions may include generated text.
        String finish = List.of("stop", "length", "content_filter", "sensitive").contains(finishReason) ? finishReason : "other";
        log.warn("AI call failed provider={} phase={} http={} bytes={} contentChars={} finish={} elapsedMs={} exception={}",
                provider.key(), phase, status, responseBytes, contentCharacters, finish,
                (System.nanoTime() - started) / 1_000_000, ex.getClass().getSimpleName());
    }
    private static class InvalidAnswerException extends Exception { }

    private ApiException upstreamError(AiProvider provider, int status, byte[] body) {
        String code = "", upstreamMessage = "";
        try {
            JsonNode error = mapper.readTree(body).path("error");
            code = error.path("code").asText("");
            upstreamMessage = error.path("message").asText("");
        } catch (Exception ignored) { /* Never return an untrusted error body. */ }
        String reason = switch (status) {
            case 400 -> "请求参数或模型配置不兼容，请核对模型与接口";
            case 401 -> "身份验证失败，请核对 API Key";
            case 403 -> "当前密钥没有访问权限，请核对模型授权";
            case 404 -> "接口路径或模型不存在，请核对接口地址与模型名称";
            case 429 -> "触发频率或用量限制，请稍后重试或查看平台限制";
            default -> status >= 500 ? "模型服务暂时异常，请稍后重试" : "模型服务拒绝请求，请核对接口配置";
        };
        if (provider == AiProvider.GLM) {
            reason = switch (code) {
                case "1113" -> "账户已欠费，请检查该 API Key 对应账户的余额";
                case "1211" -> "接口不识别配置的模型名称，请选择该账户可用的模型";
                case "1212" -> "模型不支持当前调用方式，请核对模型与接口";
                case "1210", "1213", "1214", "1215" ->
                    upstreamMessage.contains("不支持关闭思考") ? "该模型必须启用思考，当前请求的思考参数不兼容" : "请求参数不兼容，请核对模型支持的参数";
                case "1220", "1311", "1315" -> "当前密钥或套餐没有访问权限，请核对授权与产品类型";
                case "1221", "1222" -> "接口已下线或不存在，请核对接口地址";
                case "1261" -> "题面上下文超过模型长度限制，请精简题面";
                case "1301" -> "模型平台拒绝了请求内容，请检查题面及附加说明";
                case "1302", "1305" -> "模型繁忙或触发调用频率限制，请稍后重试";
                case "1308", "1310" -> "已达到平台用量上限，请查看套餐限制及重置时间";
                case "1309" -> "编程套餐已到期，请核对当前密钥使用的套餐";
                default -> reason;
            };
        }
        // Only numeric provider business codes are safe to surface; never echo error.message.
        String business = provider == AiProvider.GLM && code.matches("[0-9]{4}") ? "，智谱错误码 " + code : "";
        return ApiException.badRequest(provider.label + "：" + reason + "（HTTP " + status + business + "）");
    }
}
