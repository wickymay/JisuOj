package com.jisuoj.problemimport;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jisuoj.auth.UserPrincipal;
import com.jisuoj.common.ApiException;
import com.jisuoj.problem.ProblemDtos;
import com.jisuoj.problem.ProblemRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class ProblemImportService {
    private static final Logger log = LoggerFactory.getLogger(ProblemImportService.class);
    private final SourceReferenceParser referenceParser;
    private final RemoteStatementFetcher fetcher;
    private final Map<String, ProblemSourceAdapter> adapters;
    private final AiProblemExtractor aiExtractor;
    private final TestDataZipService zipService;
    private final ProblemDataStorage storage;
    private final ProblemRepository problemRepository;
    private final PublishedStatementCleaner publishedStatementCleaner;
    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    public ProblemImportService(SourceReferenceParser referenceParser,
                                RemoteStatementFetcher fetcher,
                                List<ProblemSourceAdapter> adapters,
                                AiProblemExtractor aiExtractor,
                                TestDataZipService zipService,
                                ProblemDataStorage storage,
                                ProblemRepository problemRepository,
                                PublishedStatementCleaner publishedStatementCleaner,
                                JdbcTemplate jdbc,
                                ObjectMapper objectMapper) {
        this.referenceParser = referenceParser;
        this.fetcher = fetcher;
        this.adapters = adapters.stream().collect(java.util.stream.Collectors.toUnmodifiableMap(
                ProblemSourceAdapter::type, adapter -> adapter));
        this.aiExtractor = aiExtractor;
        this.zipService = zipService;
        this.storage = storage;
        this.problemRepository = problemRepository;
        this.publishedStatementCleaner = publishedStatementCleaner;
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    public ProblemImportDtos.Draft preview(ProblemImportDtos.PreviewRequest request) {
        if (!aiExtractor.configured()) {
            throw ApiException.badRequest("智能体尚未配置，请先到管理中心的“智能体配置”中填写 API");
        }
        String type = normalizeType(request.sourceType());
        ProblemSourceAdapter adapter = adapters.get(type);
        if (adapter == null) {
            throw ApiException.badRequest("当前不支持该题目来源");
        }
        boolean pasted = request.rawContent() != null && !request.rawContent().isBlank();
        SourceReference reference;
        if (request.sourceRef() == null || request.sourceRef().isBlank()) {
            if (!pasted) {
                throw ApiException.badRequest("请输入题号/URL，或粘贴原始题面");
            }
            String display = switch (type) {
                case "codeforces" -> "手工粘贴 Codeforces 题面";
                case "pta" -> "手工粘贴 PTA 题面";
                default -> "手工导入题面";
            };
            reference = new SourceReference(type, "manual", display, "");
        } else {
            reference = referenceParser.parse(type, request.sourceRef());
        }
        String content = pasted ? request.rawContent() : fetcher.fetch(reference);
        ProblemImportDtos.Draft baseline = adapter.parse(reference, content).normalized();
        return aiExtractor.extract(reference, content, baseline, request.extraPrompt(),
                Boolean.TRUE.equals(request.generateArtifacts())).normalized();
    }

    public ProblemImportDtos.Draft generateArtifacts(ProblemImportDtos.GenerateArtifactsRequest request) {
        if (!aiExtractor.configured()) {
            throw ApiException.badRequest("智能体尚未配置，请先到管理中心的“智能体配置”中填写 API");
        }
        return aiExtractor.generateArtifacts(request.draft().normalized(), request.extraPrompt()).normalized();
    }

    public ProblemImportDtos.Published publish(ProblemImportDtos.Draft incoming, MultipartFile testData,
                                                UserPrincipal actor, String ip) {
        ProblemImportDtos.Draft draft = incoming.normalized();
        Map<String, byte[]> files = zipService.read(testData);
        storage.ensureWritable();
        String description = draft.description();
        if (!draft.constraints().isBlank()) {
            description += "<section class=\"problem-constraints\"><h3>数据范围</h3>" + draft.constraints() + "</section>";
        }
        String hint = draft.hint();
        if (!draft.specialProperties().isEmpty()) {
            hint += "<p><strong>特殊性质：</strong>" + String.join("；", draft.specialProperties()) + "</p>";
        }
        ProblemDtos.SaveRequest request = new ProblemDtos.SaveRequest(
                publishedStatementCleaner.clean(draft.title()), publishedStatementCleaner.clean(description),
                publishedStatementCleaner.clean(draft.input()), publishedStatementCleaner.clean(draft.output()),
                draft.sampleInput(), draft.sampleOutput(), publishedStatementCleaner.clean(hint),
                truncate(draft.source(), 100), draft.timeLimit(), draft.memoryLimit(), draft.spj() > 0, draft.hidden());
        int problemId = problemRepository.create(request);
        boolean dataStored = false;
        try {
            int caseCount = storage.store(problemId, draft, files);
            dataStored = true;
            jdbc.update("INSERT INTO jisu_problem_source(problem_id,source_type,source_ref,source_url,imported_by,imported_at) " +
                            "VALUES(?,?,?,?,?,NOW()) ON DUPLICATE KEY UPDATE source_type=VALUES(source_type),source_ref=VALUES(source_ref)," +
                            "source_url=VALUES(source_url),imported_by=VALUES(imported_by),imported_at=NOW()",
                    problemId, draft.sourceType(), draft.sourceRef(), draft.sourceUrl(), actor.userId());
            grantProblem(problemId, actor.userId());
            audit(actor.userId(), "problem.import.publish", "problem", String.valueOf(problemId),
                    Map.of("sourceType", draft.sourceType(), "sourceRef", draft.sourceRef(), "testCaseCount", caseCount), ip);
            return new ProblemImportDtos.Published(problemId, caseCount, draft.warnings());
        } catch (RuntimeException ex) {
            cleanupFailedPublish(problemId, dataStored, ex);
            throw ex;
        }
    }

    private void cleanupFailedPublish(int problemId, boolean dataStored, RuntimeException originalError) {
        try {
            problemRepository.setHidden(problemId, true);
            if (dataStored) {
                storage.delete(problemId);
            }
            jdbc.update("DELETE FROM problem WHERE problem_id=?", problemId);
        } catch (RuntimeException cleanupError) {
            log.error("发布失败后清理题目 #{} 未完成，题目已尝试隐藏", problemId, cleanupError);
            originalError.addSuppressed(cleanupError);
            return;
        }
        try {
            jdbc.update("DELETE FROM jisu_problem_source WHERE problem_id=?", problemId);
            jdbc.update("DELETE FROM privilege WHERE rightstr=?", "p" + problemId);
        } catch (RuntimeException cleanupError) {
            log.warn("题目 #{} 已删除，但来源或权限记录清理未完成", problemId, cleanupError);
            originalError.addSuppressed(cleanupError);
        }
    }

    public Map<String, Object> handoff(ProblemImportDtos.Draft draft) {
        return storage.handoff(draft.normalized());
    }

    public boolean aiConfigured() {
        return aiExtractor.configured();
    }

    private void grantProblem(int problemId, String userId) {
        String right = "p" + problemId;
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM privilege WHERE user_id=? AND rightstr=?",
                Integer.class, userId, right);
        if (count == null || count == 0) {
            jdbc.update("INSERT INTO privilege(user_id,rightstr,valuestr,defunct) VALUES(?,?,'true','N')", userId, right);
        }
    }

    private void audit(String actor, String action, String targetType, String targetId,
                       Map<String, Object> detail, String ip) {
        try {
            jdbc.update("INSERT INTO jisu_audit_log(actor_id,action,target_type,target_id,detail,ip,created_at) " +
                            "VALUES(?,?,?,?,CAST(? AS JSON),?,NOW())",
                    actor, action, targetType, targetId, objectMapper.writeValueAsString(detail), ip);
        } catch (Exception ignored) {
            // Auditing must not make an otherwise complete problem import unusable.
        }
    }

    private String normalizeType(String type) {
        String value = type == null ? "" : type.trim().toLowerCase(Locale.ROOT);
        return switch (value) {
            case "cf" -> "codeforces";
            case "pintia" -> "pta";
            default -> value;
        };
    }

    private String truncate(String value, int max) {
        String safe = value == null ? "" : value;
        return safe.length() <= max ? safe : safe.substring(0, max);
    }
}
