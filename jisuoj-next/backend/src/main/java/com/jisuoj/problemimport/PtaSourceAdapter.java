package com.jisuoj.problemimport;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;

@Component
public class PtaSourceAdapter implements ProblemSourceAdapter {
    private final StatementSanitizer sanitizer;

    public PtaSourceAdapter(StatementSanitizer sanitizer) {
        this.sanitizer = sanitizer;
    }

    @Override
    public String type() {
        return "pta";
    }

    @Override
    public ProblemImportDtos.Draft parse(SourceReference reference, String content) {
        var document = Jsoup.parse(content, reference.url());
        Element article = document.selectFirst("article, .problem-content, [class*=problemDescription], main");
        String title = document.select("h1,h2,[class*=problemTitle]").stream()
                .map(Element::text).filter(value -> !value.isBlank()).findFirst().orElse(document.title());
        String body = article == null ? content : article.html();
        return new ProblemImportDtos.Draft(
                title == null || title.isBlank() ? reference.displayName() : title.trim(), sanitizer.clean(body),
                "", "", "", List.of(), "", "", "", BigDecimal.ONE, 128,
                reference.displayName(), reference.url(), type(), reference.reference(), "standard", 0, 10,
                "", "", "", "", "",
                List.of("PTA 题面结构可能动态变化，请重点核对输入、输出、样例和限制。",
                        "PTA 隐藏测试不会被抓取，请上传有权使用的数据。"), false).normalized();
    }
}
