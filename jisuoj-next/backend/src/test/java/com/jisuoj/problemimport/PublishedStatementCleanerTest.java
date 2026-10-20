package com.jisuoj.problemimport;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PublishedStatementCleanerTest {
    private final PublishedStatementCleaner cleaner = new PublishedStatementCleaner();

    @Test
    void removesInlineAndBlockMathDollarDelimitersFromPublishedMarkup() {
        String result = cleaner.clean("<p>当 $1 \\le n$ 时，计算 $$a+b$$。</p>");

        assertThat(result).isEqualTo("<p>当 1 \\le n 时，计算 a+b。</p>");
        assertThat(result).doesNotContain("$");
    }

    @Test
    void alsoRemovesEscapedDollarCharacters() {
        assertThat(cleaner.clean("字符 \\$ 不应出现在最终题面"))
                .isEqualTo("字符  不应出现在最终题面");
    }
}
