package com.jisuoj.problemimport;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ManualSourceAdapterTest {
    @Test
    void buildsAnEditableDraftFromPastedContent() {
        ManualSourceAdapter adapter = new ManualSourceAdapter(new StatementSanitizer());
        SourceReference reference = new SourceReference("manual", "manual", "手工导入题面", "");

        ProblemImportDtos.Draft draft = adapter.parse(reference,
                "<h1>两数之和</h1><p>求两个整数的和。</p><script>alert(1)</script>");

        assertThat(draft.title()).isEqualTo("两数之和");
        assertThat(draft.description()).contains("求两个整数的和").doesNotContain("script", "alert");
        assertThat(draft.hidden()).isTrue();
        assertThat(draft.warnings()).isNotEmpty();
    }
}
