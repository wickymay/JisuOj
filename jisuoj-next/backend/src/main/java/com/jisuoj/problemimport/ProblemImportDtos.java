package com.jisuoj.problemimport;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import jakarta.validation.Valid;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

public final class ProblemImportDtos {
    private ProblemImportDtos() {
    }

    public record PreviewRequest(
            @NotBlank String sourceType,
            @Size(max = 500) String sourceRef,
            @Size(max = 2_000_000) String rawContent,
            @Size(max = 4_000) String extraPrompt,
            Boolean generateArtifacts) {
    }

    public record GenerateArtifactsRequest(
            @NotNull @Valid Draft draft,
            @Size(max = 4_000) String extraPrompt) {
    }

    public record Draft(
            @NotBlank @Size(max = 200) String title,
            @Size(max = 2_000_000) String description,
            @Size(max = 2_000_000) String input,
            @Size(max = 2_000_000) String output,
            @Size(max = 500_000) String constraints,
            List<String> specialProperties,
            @Size(max = 200_000) String sampleInput,
            @Size(max = 200_000) String sampleOutput,
            @Size(max = 2_000_000) String hint,
            @DecimalMin("0.001") @DecimalMax("300") BigDecimal timeLimit,
            @Min(1) @Max(4096) Integer memoryLimit,
            @Size(max = 100) String source,
            @Size(max = 500) String sourceUrl,
            String sourceType,
            String sourceRef,
            String problemType,
            @Min(0) @Max(3) Integer spj,
            @Min(1) @Max(20) Integer testGroupCount,
            @Size(max = 200_000) String testInput,
            @Size(max = 200_000) String testOutput,
            @Size(max = 120_000) String referenceSolutionCpp,
            @Size(max = 120_000) String validatorCpp,
            @Size(max = 120_000) String generatorCpp,
            List<String> warnings,
            Boolean hidden) {

        public Draft normalized() {
            List<String> normalizedWarnings = new ArrayList<>(warnings == null ? List.of() : warnings);
            if (blank(sampleInput) || blank(sampleOutput)) {
                normalizedWarnings.add("样例数据不完整，请人工补充。");
            }
            if (blank(testInput) || blank(testOutput)) {
                normalizedWarnings.add("没有随题面获取隐藏测试数据，发布前请上传有权使用的测试数据 ZIP。");
            }
            if (blank(constraints)) {
                normalizedWarnings.add("未识别出明确的数据范围，请人工核对。");
            }
            if ("standard".equalsIgnoreCase(defaultText(problemType, "standard")) && blank(referenceSolutionCpp)) {
                normalizedWarnings.add("尚未生成或填写 C++ 参考程序。");
            }
            return new Draft(
                    title == null ? "" : title.trim(), safe(description), safe(input), safe(output), safe(constraints),
                    unique(specialProperties), safe(sampleInput), safe(sampleOutput), safe(hint),
                    timeLimit == null ? BigDecimal.ONE : timeLimit.max(new BigDecimal("0.001")).min(new BigDecimal("300")),
                    memoryLimit == null ? 128 : Math.max(1, Math.min(4096, memoryLimit)),
                    defaultText(source, sourceType == null ? "手工导入" : sourceType), safe(sourceUrl),
                    defaultText(sourceType, "manual"), safe(sourceRef), defaultText(problemType, "standard"),
                    spj == null ? 0 : Math.max(0, Math.min(3, spj)),
                    testGroupCount == null ? 10 : Math.max(1, Math.min(20, testGroupCount)),
                    safe(testInput), safe(testOutput), safe(referenceSolutionCpp), safe(validatorCpp), safe(generatorCpp),
                    unique(normalizedWarnings), Boolean.TRUE.equals(hidden));
        }

        private static boolean blank(String value) {
            return value == null || value.isBlank();
        }

        private static String safe(String value) {
            return value == null ? "" : value.trim();
        }

        private static String defaultText(String value, String fallback) {
            return blank(value) ? fallback : value.trim();
        }

        private static List<String> unique(List<String> values) {
            if (values == null) {
                return List.of();
            }
            return values.stream().filter(value -> value != null && !value.isBlank())
                    .map(String::trim).distinct().toList();
        }
    }

    public record Published(int problemId, int testCaseCount, List<String> warnings) {
    }
}
