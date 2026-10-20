package com.jisuoj.problemimport;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class CodeforcesSourceAdapter implements ProblemSourceAdapter {
    private static final Pattern NUMBER = Pattern.compile("([0-9]+(?:\\.[0-9]+)?)");
    private final StatementSanitizer sanitizer;

    public CodeforcesSourceAdapter(StatementSanitizer sanitizer) {
        this.sanitizer = sanitizer;
    }

    @Override
    public String type() {
        return "codeforces";
    }

    @Override
    public ProblemImportDtos.Draft parse(SourceReference reference, String content) {
        var document = Jsoup.parse(content, reference.url());
        Element statement = document.selectFirst(".problem-statement");
        if (statement == null) {
            return fallback(reference, content, "未找到 Codeforces 标准题面结构，请核对粘贴内容。");
        }
        String title = text(statement.selectFirst(".header .title"));
        title = title.replaceFirst("^[A-Z][A-Z0-9]*\\.\\s*", "").trim();
        String input = section(statement.selectFirst(".input-specification"), "Input", "输入");
        String output = section(statement.selectFirst(".output-specification"), "Output", "输出");
        String hint = section(statement.selectFirst(".note"), "Note", "提示");
        String description = description(statement);
        Element firstInput = statement.selectFirst(".sample-test .input pre");
        Element firstOutput = statement.selectFirst(".sample-test .output pre");
        BigDecimal time = number(text(statement.selectFirst(".time-limit")), BigDecimal.valueOf(2));
        int memory = number(text(statement.selectFirst(".memory-limit")), BigDecimal.valueOf(256)).intValue();
        List<String> warnings = new ArrayList<>();
        warnings.add("Codeforces 的隐藏测试数据不会被抓取，请上传有权使用的数据或自行生成测试。");
        return new ProblemImportDtos.Draft(
                title.isBlank() ? reference.displayName() : title,
                sanitizer.clean(description), sanitizer.clean(input), sanitizer.clean(output), "", List.of(),
                preText(firstInput), preText(firstOutput), sanitizer.clean(hint), time, memory,
                reference.displayName(), reference.url(), type(), reference.reference(), "standard", 0, 10,
                "", "", "", "", "", warnings, false).normalized();
    }

    private String description(Element statement) {
        StringBuilder html = new StringBuilder();
        for (Element child : statement.children()) {
            if (child.hasClass("header")) {
                continue;
            }
            if (child.hasClass("input-specification") || child.hasClass("output-specification") ||
                    child.hasClass("sample-tests") || child.hasClass("note")) {
                break;
            }
            html.append(child.outerHtml());
        }
        return html.toString();
    }

    private String section(Element element, String... headings) {
        if (element == null) {
            return "";
        }
        Element copy = element.clone();
        Element title = copy.selectFirst(".section-title");
        if (title != null) {
            title.remove();
        }
        return copy.html();
    }

    private String preText(Element element) {
        return element == null ? "" : element.wholeText().replace('\u00a0', ' ').strip();
    }

    private String text(Element element) {
        return element == null ? "" : element.text().trim();
    }

    private BigDecimal number(String value, BigDecimal fallback) {
        Matcher matcher = NUMBER.matcher(value == null ? "" : value);
        return matcher.find() ? new BigDecimal(matcher.group(1)) : fallback;
    }

    private ProblemImportDtos.Draft fallback(SourceReference reference, String content, String warning) {
        String title = Jsoup.parse(content).title();
        return new ProblemImportDtos.Draft(
                title.isBlank() ? reference.displayName() : title, sanitizer.clean(content), "", "", "", List.of(),
                "", "", "", BigDecimal.valueOf(2), 256, reference.displayName(), reference.url(), type(),
                reference.reference(), "standard", 0, 10, "", "", "", "", "", List.of(warning), false).normalized();
    }
}
