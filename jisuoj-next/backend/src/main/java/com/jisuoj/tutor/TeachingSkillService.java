package com.jisuoj.tutor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/** Loads reviewed, bundled teaching instructions separately from retrieved book excerpts. */
@Service
public class TeachingSkillService {
    private static final Logger log = LoggerFactory.getLogger(TeachingSkillService.class);
    static final String ROOT = "teaching-skills/competitive-programming-tutor/";
    static final int MAX_PROMPT_CHARS = 12_000;
    static final int MAX_TOPICS = 2;
    private static final Pattern HINT_ONLY = Pattern.compile(
            "(?is)(只(?:要|需|给|想看).{0,8}(?:提示|思路|方向)|"
                    + "(?:不要|别|不用|不需要|不想|无需|不必|禁止)(?:(?!提示|思路|方向|[，。；;！!？?\\n]).){0,10}(?:代码|解法|答案|题解|实现)|"
                    + "(?:no|don't|do not).{0,20}(?:code|solution|answer)|hints? only)");
    private static final Pattern FULL_SOLUTION = Pattern.compile(
            "(?is)(完整.{0,6}(?:解法|代码|答案|题解|解答|实现)|详细题解|"
                    + "(?:给|看|写).{0,6}题解|(?:给|写).{0,8}(?:代码|实现)|"
                    + "(?:full|complete).{0,12}(?:solution|code|answer))");

    private final boolean enabled;
    private final String version;
    private final int sourceBooks;
    private final String core;
    private final List<Topic> topics;

    public TeachingSkillService(ObjectMapper mapper,
                               @Value("${jisuoj.ai.teaching-skills.enabled:true}") boolean enabled) {
        this.enabled = enabled;
        if (!enabled) {
            version = null;
            sourceBooks = 0;
            core = "";
            topics = List.of();
            return;
        }
        try {
            JsonNode manifest = mapper.readTree(readBytes("manifest.json"));
            if (manifest.path("schemaVersion").asInt() != 1
                    || !"competitive-programming-tutor".equals(manifest.path("id").asText())) {
                throw new IOException("Unsupported teaching skill manifest");
            }
            version = manifest.path("version").asText();
            sourceBooks = manifest.path("sourceBooks").asInt();
            core = readVerified(manifest, manifest.path("coreFile").asText());
            List<Topic> loaded = new ArrayList<>();
            for (JsonNode entry : manifest.path("topics")) {
                String path = entry.path("file").asText();
                if (!path.matches("chapters/ch\\d{2}-[a-z-]+\\.md")) {
                    throw new IOException("Invalid teaching topic path");
                }
                List<String> keywords = new ArrayList<>();
                entry.path("keywords").forEach(key -> keywords.add(key.asText()));
                String text = readVerified(manifest, path);
                if (core.length() + text.length() + 600 > MAX_PROMPT_CHARS) {
                    throw new IOException("Teaching topic exceeds context budget: " + path);
                }
                loaded.add(new Topic(entry.path("id").asText(), entry.path("title").asText(),
                        List.copyOf(keywords), text));
            }
            if (loaded.isEmpty() || sourceBooks != 9 || version.isBlank()) {
                throw new IOException("Incomplete teaching skill package");
            }
            topics = List.copyOf(loaded);
            log.info("AI teaching skill loaded: competitive-programming-tutor v{}, {} topics, {} source books",
                    version, topics.size(), sourceBooks);
        } catch (IOException ex) {
            throw new IllegalStateException("Cannot load bundled AI teaching skill", ex);
        }
    }

    public AiTeachingDtos.TeachingSkillStatus status() {
        return new AiTeachingDtos.TeachingSkillStatus(enabled ? "ready" : "disabled",
                "competitive-programming-tutor", version, topics.size(), sourceBooks);
    }

    public Selection select(String question, String relatedContext, boolean hasCode, boolean fullSolution) {
        String mode = fullSolution ? "full_solution" : "guided";
        if (!enabled) return new Selection("", new AiTeachingDtos.TeachingSkillUse("disabled", null, mode, List.of()));
        List<Ranked> ranked = new ArrayList<>();
        for (Topic topic : topics) {
            int score = matchScore(topic, question) * 4 + matchScore(topic, relatedContext);
            if (hasCode && "debugging".equals(topic.id())) score += 3;
            if (score > 0) ranked.add(new Ranked(topic, score));
        }
        ranked.sort(Comparator.comparingInt(Ranked::score).reversed());
        if (ranked.isEmpty()) ranked.add(new Ranked(topics.get(0), 0));

        StringBuilder prompt = new StringBuilder("\n\n【项目配置的教学技能】\n").append(core)
                .append("\n\n【本轮教学方式】\n")
                .append(fullSolution
                        ? "学生已明确请求完整解法，可以提供完整实现，并解释条件、正确性、复杂度和边界。\n"
                        : "本轮逐步引导：先补足当前卡点；可给必要的局部修正，不主动展示整题答案或完整实现。\n");
        List<AiTeachingDtos.TeachingTopic> selected = new ArrayList<>();
        for (Ranked candidate : ranked) {
            if (selected.size() >= MAX_TOPICS) break;
            String section = "\n【教学专题：" + candidate.topic().title() + "】\n" + candidate.topic().content();
            if (prompt.length() + section.length() > MAX_PROMPT_CHARS) continue;
            prompt.append(section);
            selected.add(new AiTeachingDtos.TeachingTopic(candidate.topic().id(), candidate.topic().title()));
        }
        return new Selection(prompt.toString(), new AiTeachingDtos.TeachingSkillUse("used", version, mode, List.copyOf(selected)));
    }

    static boolean fullSolutionRequested(String question, String previousQuestion) {
        if (HINT_ONLY.matcher(question).find()) return false;
        if (FULL_SOLUTION.matcher(question).find()) return true;
        return isContinuation(question)
                && previousQuestion != null && !HINT_ONLY.matcher(previousQuestion).find()
                && FULL_SOLUTION.matcher(previousQuestion).find();
    }

    static boolean isContinuation(String question) {
        return question.strip().matches("(?i)(?:继续|接着|请继续|继续讲|继续写|接着讲|接着写|continue)[。！!]?" );
    }

    private int matchScore(Topic topic, String value) {
        String lower = value == null ? "" : value.toLowerCase(Locale.ROOT);
        int score = 0;
        for (String keyword : topic.keywords()) {
            String key = keyword.toLowerCase(Locale.ROOT);
            boolean match = key.matches("[a-z_]+")
                    ? Pattern.compile("(?<![a-z0-9_])" + Pattern.quote(key) + "(?![a-z0-9_])").matcher(lower).find()
                    : lower.contains(key);
            if (match) score++;
        }
        return score;
    }

    private String readVerified(JsonNode manifest, String file) throws IOException {
        byte[] content = readBytes(file);
        try {
            String digest = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
            if (!digest.equals(manifest.path("contentHashes").path(file).asText())) {
                throw new IOException("Teaching skill checksum mismatch: " + file);
            }
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
        return new String(content, StandardCharsets.UTF_8);
    }

    private static byte[] readBytes(String file) throws IOException {
        try (var input = new ClassPathResource(ROOT + file).getInputStream()) {
            return input.readAllBytes();
        }
    }

    private record Topic(String id, String title, List<String> keywords, String content) { }
    private record Ranked(Topic topic, int score) { }
    public record Selection(String prompt, AiTeachingDtos.TeachingSkillUse usage) { }
}
