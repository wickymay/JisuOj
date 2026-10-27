package com.jisuoj.problemimport;

import com.jisuoj.common.ApiException;
import java.util.Arrays;

/** Stable IDs preserve the original DeepSeek configuration in row 1. */
public enum AiProvider {
    DEEPSEEK(1, "DeepSeek", "https://api.deepseek.com", "deepseek-flash"),
    QWEN(2, "Qwen", "https://dashscope.aliyuncs.com/compatible-mode/v1", "qwen3.7-plus"),
    GLM(3, "GLM", "https://open.bigmodel.cn/api/paas/v4", "glm-5.3"),
    KIMI(4, "Kimi", "https://api.moonshot.cn/v1", "kimi-k3.1-pro");

    public final int configId;
    public final String label;
    public final String baseUrl;
    public final String model;

    AiProvider(int configId, String label, String baseUrl, String model) {
        this.configId = configId;
        this.label = label;
        this.baseUrl = baseUrl;
        this.model = model;
    }

    public String key() { return name().toLowerCase(java.util.Locale.ROOT); }

    public static AiProvider parse(String value) {
        return Arrays.stream(values()).filter(p -> p.key().equalsIgnoreCase(value)).findFirst()
                .orElseThrow(() -> ApiException.badRequest("不支持的模型服务商"));
    }
}
