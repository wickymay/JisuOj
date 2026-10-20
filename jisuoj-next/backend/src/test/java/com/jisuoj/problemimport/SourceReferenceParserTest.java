package com.jisuoj.problemimport;

import com.jisuoj.common.ApiException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SourceReferenceParserTest {
    private final SourceReferenceParser parser = new SourceReferenceParser();

    @Test
    void parsesCodeforcesShortReferenceAndUrl() {
        assertThat(parser.parse("codeforces", "2263a"))
                .extracting(SourceReference::reference, SourceReference::url)
                .containsExactly("2263A", "https://codeforces.com/problemset/problem/2263/A");
        assertThat(parser.parse("cf", "https://codeforces.com/contest/4/problem/A").reference()).isEqualTo("4A");
    }

    @Test
    void parsesPtaReference() {
        SourceReference result = parser.parse("pta", "123456789/987654321");
        assertThat(result.reference()).isEqualTo("123456789/987654321");
        assertThat(result.url()).isEqualTo("https://pintia.cn/problem-sets/123456789/problems/987654321");
    }

    @Test
    void rejectsUnknownOrMalformedSources() {
        assertThatThrownBy(() -> parser.parse("unknown", "123")).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> parser.parse("codeforces", "not-a-problem")).isInstanceOf(ApiException.class);
    }
}
