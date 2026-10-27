package com.jisuoj.testdata;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jisuoj.common.ApiException;
import com.jisuoj.problemimport.*;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class TestDataGenerationServiceTest {
    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
    private final AiRuntimeConfigService config = mock(AiRuntimeConfigService.class);
    private final DomesticAiClient ai = mock(DomesticAiClient.class);
    private final TestDataRunnerClient runner = mock(TestDataRunnerClient.class);
    private final TestDataGenerationService service = new TestDataGenerationService(config, ai, runner, mapper, mock(JdbcTemplate.class));

    private ProblemImportDtos.Draft draft(int spj) {
        return new ProblemImportDtos.Draft("A+B", "求和", "两个整数", "和", "0<=a,b<=100", List.of(),
                "1 2\n", "3\n", "", null, null, "manual", "", "manual", "", "standard", spj, 2,
                "", "", "", "", "", List.of(), true);
    }
    private TestDataDtos.GenerateRequest request(boolean kimi) { return new TestDataDtos.GenerateRequest(draft(0), 2, 42, kimi, ""); }

    private void fixtures() throws Exception {
        for (var p : AiProvider.values()) when(config.requireConfig(p)).thenReturn(
                new AiRuntimeConfigService.Config("https://fixture.invalid", "secret", p.model, Instant.now()));
        when(ai.call(eq(AiProvider.QWEN), anyString(), anyString())).thenReturn(mapper.readTree("{\"generator_cpp\":\"generator\",\"coverage\":[\"边界\"]}"));
        when(ai.call(eq(AiProvider.DEEPSEEK), anyString(), anyString())).thenReturn(mapper.readTree("{\"reference_solution_cpp\":\"private-reference\"}"));
        when(ai.call(eq(AiProvider.GLM), anyString(), anyString())).thenAnswer(call -> {
            assertThat(call.getArgument(2, String.class)).doesNotContain("private-reference");
            return mapper.readTree("{\"independent_solution_cpp\":\"independent\",\"validator_cpp\":\"validator\",\"attack_inputs\":[]}");
        });
        when(runner.generate(anyMap())).thenReturn(mapper.valueToTree(Map.of("ok", true, "cases", List.of(
                Map.of("input", "1 2\n", "output", "3\n", "label", "样例")), "notes", List.of(), "mutantRejected", 0)));
    }
    private TestDataDtos.JobView completed(String id) throws Exception {
        for (int i = 0; i < 200; i++) {
            var view = service.status(id, "owner");
            if (List.of("SUCCEEDED", "FAILED").contains(view.state())) return view;
            Thread.sleep(10);
        }
        throw new AssertionError("Job did not finish");
    }
    @Test void producesImportCompatibleZipAndProtectsJobOwnership() throws Exception {
        try {
            fixtures();
            var job = completed(service.start(request(false), "owner").id());
            assertThat(job.state()).isEqualTo("SUCCEEDED");
            byte[] zip = service.download(job.id(), "owner");
            var files = new TestDataZipService().read(new MockMultipartFile("testData", "test.zip", "application/zip", zip));
            assertThat(files).containsOnlyKeys("01.in", "01.out");
            assertThat(new String(files.get("01.out"))).isEqualTo("3\n");
            assertThatThrownBy(() -> service.download(job.id(), "other")).isInstanceOf(ApiException.class).hasMessageContaining("不存在");
            verify(ai, never()).call(eq(AiProvider.KIMI), anyString(), anyString());
        } finally { service.close(); }
    }
    @Test void kimiRejectionStopsExecutionAndDownload() throws Exception {
        try {
            fixtures();
            when(ai.call(eq(AiProvider.KIMI), anyString(), anyString())).thenReturn(mapper.readTree("{\"approved\":false,\"warnings\":[\"遗漏边界\"]}"));
            var job = completed(service.start(request(true), "owner").id());
            assertThat(job.state()).isEqualTo("FAILED");
            assertThat(job.error()).contains("遗漏边界");
            verify(runner, never()).generate(anyMap());
            assertThatThrownBy(() -> service.download(job.id(), "owner")).isInstanceOf(ApiException.class);
        } finally { service.close(); }
    }
    @Test void executorFailureNeverBecomesAnExportableSuccess() throws Exception {
        try {
            fixtures();
            when(runner.generate(anyMap())).thenThrow(ApiException.badRequest("两份解法输出不一致"));
            var job = completed(service.start(request(false), "owner").id());
            assertThat(job.state()).isEqualTo("FAILED");
            assertThat(job.result()).isNull();
        } finally { service.close(); }
    }
    @Test void unsupportedProblemsAndMissingExecutorFailBeforeModelCalls() {
        try {
            assertThatThrownBy(() -> service.start(new TestDataDtos.GenerateRequest(draft(1), 2, 42, false, ""), "owner"))
                    .isInstanceOf(ApiException.class).hasMessageContaining("标准输入输出");
            doThrow(ApiException.badRequest("执行器不可用")).when(runner).requireReady();
            assertThatThrownBy(() -> service.start(request(false), "owner")).isInstanceOf(ApiException.class).hasMessageContaining("执行器");
            verifyNoInteractions(ai);
        } finally { service.close(); }
    }
}
