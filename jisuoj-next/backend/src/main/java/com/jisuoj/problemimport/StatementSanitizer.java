package com.jisuoj.problemimport;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.safety.Safelist;
import org.springframework.stereotype.Component;

@Component
public class StatementSanitizer {
    private final Safelist safelist = Safelist.relaxed()
            .addTags("pre", "code", "span", "sup", "sub", "details", "summary")
            .addAttributes(":all", "class")
            .addProtocols("a", "href", "http", "https")
            .addProtocols("img", "src", "http", "https", "data");

    public String clean(String html) {
        if (html == null || html.isBlank()) {
            return "";
        }
        Document.OutputSettings output = new Document.OutputSettings().prettyPrint(false);
        return Jsoup.clean(html, "", safelist, output).trim();
    }

    public String text(String html) {
        return html == null ? "" : Jsoup.parse(html).text().trim();
    }
}
