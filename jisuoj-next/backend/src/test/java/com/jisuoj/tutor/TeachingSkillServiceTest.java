package com.jisuoj.tutor;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class TeachingSkillServiceTest {
    private final TeachingSkillService skills = new TeachingSkillService(new ObjectMapper(), true);

    @ParameterizedTest
    @CsvSource({
            "怎么建模并分析复杂度,modeling",
            "数组下标和循环如何理解,foundations",
            "BFS和DFS搜索怎么做,search",
            "二分的单调性和贪心证明,greedy-binary",
            "0/1背包动态规划状态转移,dp",
            "Dijkstra最短路能有负权吗,graphs",
            "并查集路径压缩如何实现,structures",
            "KMP模式串的前缀函数,strings",
            "gcd快速幂取模如何计算,math",
            "叉积和点积的几何意义,geometry",
            "代码WA怎么调试反例,debugging",
            "入门学习训练计划如何复盘,training"
    })
    void routesEveryTeachingTopic(String question, String expectedTopic) {
        var selected = skills.select(question, question, false, false);
        assertThat(selected.usage().topics()).extracting(AiTeachingDtos.TeachingTopic::id).contains(expectedTopic);
        assertThat(selected.prompt()).contains("逐步引导", "【教学专题：");
        assertThat(selected.prompt().length()).isLessThanOrEqualTo(TeachingSkillService.MAX_PROMPT_CHARS);
    }

    @Test
    void keepsFollowUpContextAndBoundsTopicCountWithoutLoadingWholeCorpus() {
        var selected = skills.select("继续", "继续\n之前的问题：动态规划背包状态转移", true, false);
        assertThat(selected.usage().topics()).extracting(AiTeachingDtos.TeachingTopic::id).contains("dp");
        assertThat(selected.usage().topics()).hasSizeLessThanOrEqualTo(2);
        assertThat(selected.prompt()).doesNotContain("# 几何与精度", "# 字符串与匹配状态");
        assertThat(skills.status().topics()).isEqualTo(12);
        assertThat(skills.status().sourceBooks()).isEqualTo(9);
    }

    @ParameterizedTest
    @CsvSource({
            "给我完整代码,true",
            "请给出完整解法和复杂度,true",
            "给一份题解,true",
            "不要完整代码只要提示,false",
            "我不需要代码请解释思路,false",
            "只给思路不要题解,false",
            "不需要提示请给完整代码,true",
            "怎么理解动态规划,false",
            "hints only no full solution,false",
            "show the complete solution,true"
    })
    void distinguishesExplicitSolutionFromNegatedRequests(String question, boolean expected) {
        assertThat(TeachingSkillService.fullSolutionRequested(question, null)).isEqualTo(expected);
    }

    @Test
    void continuesExplicitFullExplanationButHonorsNewHintOnlyRequest() {
        assertThat(TeachingSkillService.fullSolutionRequested("继续", "请给完整解法")).isTrue();
        assertThat(TeachingSkillService.fullSolutionRequested("现在只要提示", "请给完整解法")).isFalse();
        var full = skills.select("给完整代码", "动态规划背包", false, true);
        assertThat(full.usage().mode()).isEqualTo("full_solution");
        assertThat(full.prompt()).contains("学生已明确请求完整解法");
    }

    @Test
    void supportsDisablingAndFallsBackToGeneralModelingForUnmatchedQuestions() {
        var disabled = new TeachingSkillService(new ObjectMapper(), false);
        assertThat(disabled.status().status()).isEqualTo("disabled");
        assertThat(disabled.select("BFS", "", false, false).prompt()).isEmpty();
        assertThat(skills.select("这个问题怎么做", "", false, false).usage().topics())
                .extracting(AiTeachingDtos.TeachingTopic::id).containsExactly("modeling");
        assertThat(skills.select("https://example.org/http", "", false, false).usage().topics())
                .extracting(AiTeachingDtos.TeachingTopic::id).containsExactly("modeling");
    }
}
