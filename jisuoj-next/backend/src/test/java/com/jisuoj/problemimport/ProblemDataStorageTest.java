package com.jisuoj.problemimport;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jisuoj.common.ApiException;
import com.jisuoj.config.JisuOjProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProblemDataStorageTest {
    @TempDir
    Path root;

    @Test
    void neverDeletesAnExistingProblemDirectory() throws Exception {
        JisuOjProperties properties = new JisuOjProperties();
        properties.setDataDir(root);
        ProblemDataStorage storage = new ProblemDataStorage(properties, new ObjectMapper());
        Path existing = Files.createDirectories(root.resolve("1000"));
        Files.writeString(existing.resolve("official.in"), "do not delete");

        assertThatThrownBy(() -> storage.store(1000, draft(), Map.of())).isInstanceOf(ApiException.class);
        assertThat(existing.resolve("official.in")).exists().hasContent("do not delete");
    }

    @Test
    void samplesStayInMetadataAndAreNotMistakenForHiddenTests() throws Exception {
        JisuOjProperties properties = new JisuOjProperties();
        properties.setDataDir(root);
        ProblemDataStorage storage = new ProblemDataStorage(properties, new ObjectMapper());

        int cases = storage.store(1001, draft(), Map.of());

        assertThat(cases).isZero();
        assertThat(root.resolve("1001/sample.in")).doesNotExist();
        assertThat(root.resolve("1001/import-handoff.json")).exists();
    }

    private ProblemImportDtos.Draft draft() {
        return new ProblemImportDtos.Draft(
                "题目", "描述", "输入", "输出", "1 <= n <= 10", List.of(), "1", "1", "",
                BigDecimal.ONE, 128, "manual", "", "manual", "manual", "standard", 0, 10,
                "", "", "", "", "", List.of(), true).normalized();
    }
}
