package com.jisuoj.problemimport;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;

@Component
public class ManualSourceAdapter implements ProblemSourceAdapter {
    private final StatementSanitizer sanitizer;

    public ManualSourceAdapter(StatementSanitizer sanitizer) {
        this.sanitizer = sanitizer;
    }

    @Override
    public String type() {
        return "manual";
    }

    @Override
    public ProblemImportDtos.Draft parse(SourceReference reference, String content) {
        Document document = Jsoup.parse(content == null ? "" : content);
        Element heading = document.selectFirst("h1, h2");
        String title = heading == null ? document.title() : heading.text();
        if (heading != null) {
            heading.remove();
        }
        if (title == null || title.isBlank()) {
            title = "待完善题目";
        }
        return new ProblemImportDtos.Draft(
                title.trim(), sanitizer.clean(document.body().html()), "", "", "", List.of(),
                "", "", "", BigDecimal.ONE, 128, reference.displayName(), "", type(), "manual",
                "standard", 0, 10, "", "", "", "", "",
                List.of("手工导入只生成基础草稿，请补充输入输出、样例、限制和测试数据。"), true
        ).normalized();
    }
}
