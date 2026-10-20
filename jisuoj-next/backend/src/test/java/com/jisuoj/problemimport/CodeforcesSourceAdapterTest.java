package com.jisuoj.problemimport;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CodeforcesSourceAdapterTest {
    private final CodeforcesSourceAdapter adapter = new CodeforcesSourceAdapter(new StatementSanitizer());

    @Test
    void extractsStructuredStatementAndStripsUnsafeMarkup() {
        String html = """
                <div class="problem-statement">
                  <div class="header"><div class="title">A. Watermelon</div>
                    <div class="time-limit">time limit per test 1 second</div>
                    <div class="memory-limit">memory limit per test 64 megabytes</div>
                  </div>
                  <div><p>Split a watermelon.</p><script>alert(1)</script></div>
                  <div class="input-specification"><div class="section-title">Input</div><p>An integer.</p></div>
                  <div class="output-specification"><div class="section-title">Output</div><p>YES or NO.</p></div>
                  <div class="sample-tests"><div class="sample-test">
                    <div class="input"><pre>8</pre></div><div class="output"><pre>YES</pre></div>
                  </div></div>
                </div>
                """;
        SourceReference reference = new SourceReference("codeforces", "4A", "Codeforces 4A",
                "https://codeforces.com/problemset/problem/4/A");

        ProblemImportDtos.Draft draft = adapter.parse(reference, html);

        assertThat(draft.title()).isEqualTo("Watermelon");
        assertThat(draft.description()).contains("Split a watermelon").doesNotContain("script", "alert");
        assertThat(draft.sampleInput()).isEqualTo("8");
        assertThat(draft.sampleOutput()).isEqualTo("YES");
        assertThat(draft.timeLimit()).isEqualByComparingTo("1");
        assertThat(draft.memoryLimit()).isEqualTo(64);
    }
}
