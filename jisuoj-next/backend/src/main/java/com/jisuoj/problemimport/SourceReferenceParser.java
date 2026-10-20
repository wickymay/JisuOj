package com.jisuoj.problemimport;

import com.jisuoj.common.ApiException;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class SourceReferenceParser {
    private static final Pattern CF_SHORT = Pattern.compile("(?i)^\\s*(\\d{1,7})\\s*([A-Z][A-Z0-9]{0,3})\\s*$");
    private static final Pattern CF_URL = Pattern.compile(
            "(?i)(?:codeforces\\.com|mirror\\.codeforces\\.com)/(?:contest/(\\d{1,7})/problem|problemset/problem/(\\d{1,7}))" +
                    "(?:/|%2F)([A-Z][A-Z0-9]{0,3})");
    private static final Pattern PTA_PAIR = Pattern.compile("^\\s*(\\d{6,})\\s*[/,:-]\\s*(\\d{6,})\\s*$");
    private static final Pattern PTA_URL = Pattern.compile(
            "(?i)pintia\\.cn/problem-sets/(\\d+)/(?:exam/)?problems/(\\d+)");

    public SourceReference parse(String type, String value) {
        String normalizedType = type == null ? "" : type.trim().toLowerCase(Locale.ROOT);
        String reference = value == null ? "" : value.trim();
        if (reference.isBlank()) {
            throw ApiException.badRequest("请输入题号或题目 URL，也可以粘贴原始题面");
        }
        return switch (normalizedType) {
            case "codeforces", "cf" -> parseCodeforces(reference);
            case "pta", "pintia" -> parsePta(reference);
            default -> throw ApiException.badRequest("当前仅支持 Codeforces 和 PTA 来源");
        };
    }

    private SourceReference parseCodeforces(String value) {
        Matcher shortMatcher = CF_SHORT.matcher(value);
        String contest;
        String index;
        if (shortMatcher.matches()) {
            contest = shortMatcher.group(1);
            index = shortMatcher.group(2);
        } else {
            Matcher urlMatcher = CF_URL.matcher(value);
            if (!urlMatcher.find()) {
                throw ApiException.badRequest("Codeforces 请输入类似 4A 的题号或题目 URL");
            }
            contest = urlMatcher.group(1) == null ? urlMatcher.group(2) : urlMatcher.group(1);
            index = urlMatcher.group(3);
        }
        index = index.toUpperCase(Locale.ROOT);
        String reference = contest + index;
        return new SourceReference("codeforces", reference, "Codeforces " + reference,
                "https://codeforces.com/problemset/problem/" + contest + "/" + index);
    }

    private SourceReference parsePta(String value) {
        Matcher matcher = PTA_PAIR.matcher(value);
        if (!matcher.find()) {
            matcher = PTA_URL.matcher(value);
        }
        if (!matcher.find(0)) {
            throw ApiException.badRequest("PTA 请输入题集ID/题目ID或 pintia.cn 题目 URL");
        }
        String reference = matcher.group(1) + "/" + matcher.group(2);
        return new SourceReference("pta", reference, "PTA " + reference,
                "https://pintia.cn/problem-sets/" + matcher.group(1) + "/problems/" + matcher.group(2));
    }
}
