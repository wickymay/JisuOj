package com.jisuoj.problemimport;

import com.jisuoj.common.ApiException;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TestDataZipServiceTest {
    private final TestDataZipService service = new TestDataZipService();

    @Test
    void readsPairedInputsAndAnswers() throws Exception {
        MockMultipartFile file = zip(Map.of("tests/1.in", "1 2\n", "tests/1.ans", "3\n"));

        Map<String, byte[]> result = service.read(file);

        assertThat(result).containsOnlyKeys("1.in", "1.out");
        assertThat(new String(result.get("1.out"), StandardCharsets.UTF_8)).isEqualTo("3\n");
    }

    @Test
    void blocksZipSlipAndIncompletePairs() throws Exception {
        assertThatThrownBy(() -> service.read(zip(Map.of("../1.in", "x", "../1.out", "y"))))
                .isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> service.read(zip(Map.of("1.in", "x"))))
                .isInstanceOf(ApiException.class);
    }

    private MockMultipartFile zip(Map<String, String> entries) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream output = new ZipOutputStream(bytes)) {
            for (Map.Entry<String, String> entry : entries.entrySet()) {
                output.putNextEntry(new ZipEntry(entry.getKey()));
                output.write(entry.getValue().getBytes(StandardCharsets.UTF_8));
                output.closeEntry();
            }
        }
        return new MockMultipartFile("testData", "tests.zip", "application/zip", bytes.toByteArray());
    }
}
