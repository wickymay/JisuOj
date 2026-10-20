package com.jisuoj.problemimport;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jisuoj.common.ApiException;
import com.jisuoj.config.JisuOjProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class ProblemDataStorage {
    private static final Logger log = LoggerFactory.getLogger(ProblemDataStorage.class);
    private final Path root;
    private final ObjectMapper objectMapper;

    public ProblemDataStorage(JisuOjProperties properties, ObjectMapper objectMapper) {
        this.root = properties.getDataDir().toAbsolutePath().normalize();
        this.objectMapper = objectMapper;
    }

    public void ensureWritable() {
        try {
            Files.createDirectories(root);
            Path probe = Files.createTempFile(root, ".jisuoj-write-", ".tmp");
            Files.delete(probe);
        } catch (IOException | SecurityException ex) {
            log.error("题目数据目录不可写：{}", root, ex);
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "题目数据目录不可写，请检查 JUDGE_DATA_DIR 配置和目录权限");
        }
    }

    public int store(int problemId, ProblemImportDtos.Draft draft, Map<String, byte[]> uploadedFiles) {
        Path directory = safeProblemDirectory(problemId);
        boolean directoryCreated = false;
        try {
            Files.createDirectories(root);
            if (Files.exists(directory)) {
                throw ApiException.badRequest("题目数据目录已存在，为防止覆盖已停止发布：" + directory);
            }
            Files.createDirectory(directory);
            directoryCreated = true;
            int caseCount = 0;
            if (!uploadedFiles.isEmpty()) {
                for (Map.Entry<String, byte[]> entry : uploadedFiles.entrySet()) {
                    Path target = directory.resolve(entry.getKey()).normalize();
                    if (!target.getParent().equals(directory)) {
                        throw ApiException.badRequest("测试数据文件名不安全：" + entry.getKey());
                    }
                    Files.write(target, entry.getValue(), StandardOpenOption.CREATE_NEW);
                    if (entry.getKey().endsWith(".in") && !entry.getKey().equals("sample.in")) {
                        caseCount++;
                    }
                }
            } else if (!draft.testInput().isBlank() || !draft.testOutput().isBlank()) {
                writeText(directory, "test.in", draft.testInput().isBlank() ? "0" : draft.testInput());
                writeText(directory, "test.out", draft.testOutput());
                caseCount = 1;
            }
            writeArtifact(directory, "reference_solution.cc", draft.referenceSolutionCpp());
            writeArtifact(directory, "validator.cc", draft.validatorCpp());
            writeArtifact(directory, "generator.cc", draft.generatorCpp());
            Files.writeString(directory.resolve("import-handoff.json"),
                    objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(handoff(draft)),
                    StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW);
            return caseCount;
        } catch (ApiException ex) {
            if (directoryCreated) {
                cleanup(problemId);
            }
            throw ex;
        } catch (Exception ex) {
            if (directoryCreated) {
                cleanup(problemId);
            }
            log.error("写入题目数据失败：{}", directory, ex);
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "写入题目数据失败，请检查 JUDGE_DATA_DIR 配置、目录权限和磁盘空间");
        }
    }

    public Map<String, Object> handoff(ProblemImportDtos.Draft draft) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("format", "judgedata-handoff-v1");
        payload.put("workflow", Map.of("test_data_provider", "judgedata-manual-zip", "requires_manual_review", true));
        payload.put("title", draft.title());
        payload.put("problem_description", draft.description());
        payload.put("data_range", draft.constraints());
        payload.put("code", draft.referenceSolutionCpp());
        payload.put("statement", Map.of(
                "description", draft.description(), "input", draft.input(), "output", draft.output(),
                "constraints", draft.constraints(), "special_properties", draft.specialProperties(), "hint", draft.hint()));
        payload.put("samples", Map.of("input", draft.sampleInput(), "output", draft.sampleOutput()));
        payload.put("existing_test_example", Map.of("input", draft.testInput(), "output", draft.testOutput()));
        payload.put("limits", Map.of("time_limit_seconds", draft.timeLimit(), "memory_limit_mib", draft.memoryLimit()));
        payload.put("problem_type", draft.problemType());
        payload.put("test_group_count", draft.testGroupCount());
        payload.put("reference_solution_cpp", draft.referenceSolutionCpp());
        payload.put("validator_cpp", draft.validatorCpp());
        payload.put("generator_cpp", draft.generatorCpp());
        payload.put("source", draft.source());
        payload.put("source_url", draft.sourceUrl());
        payload.put("warnings", draft.warnings());
        return payload;
    }

    public void cleanup(int problemId) {
        Path directory = safeProblemDirectory(problemId);
        if (!Files.exists(directory)) {
            return;
        }
        try (var paths = Files.walk(directory)) {
            paths.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException ignored) {
                }
            });
        } catch (IOException ignored) {
        }
    }

    public void delete(int problemId) {
        Path directory = safeProblemDirectory(problemId);
        try {
            if (Files.notExists(directory)) {
                return;
            }
            try (var paths = Files.walk(directory)) {
                for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                    Files.delete(path);
                }
            }
        } catch (IOException | SecurityException ex) {
            log.error("删除题目数据失败：{}", directory, ex);
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "删除题目数据失败，请检查 JUDGE_DATA_DIR 配置和目录权限");
        }
    }

    private Path safeProblemDirectory(int problemId) {
        if (problemId <= 0) {
            throw ApiException.badRequest("题目编号无效");
        }
        Path directory = root.resolve(String.valueOf(problemId)).normalize();
        if (!directory.startsWith(root) || directory.equals(root)) {
            throw ApiException.badRequest("题目数据目录无效");
        }
        return directory;
    }

    private void writeText(Path directory, String filename, String content) throws IOException {
        Files.writeString(directory.resolve(filename), normalize(content), StandardCharsets.UTF_8,
                StandardOpenOption.CREATE_NEW);
    }

    private void writeArtifact(Path directory, String filename, String content) throws IOException {
        if (content != null && !content.isBlank()) {
            writeText(directory, filename, content);
        }
    }

    private String normalize(String value) {
        return (value == null ? "" : value).replace("\r\n", "\n").replace('\r', '\n');
    }
}
