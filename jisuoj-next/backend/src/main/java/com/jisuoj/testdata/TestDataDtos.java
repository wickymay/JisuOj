package com.jisuoj.testdata;

import com.jisuoj.problemimport.ProblemImportDtos;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.Instant;
import java.util.List;
import java.util.Map;

public final class TestDataDtos {
    private TestDataDtos() {}
    public record GenerateRequest(@NotNull @Valid ProblemImportDtos.Draft draft,
            @Min(1) @Max(20) int caseCount, @Min(0) @Max(2147483647) long seed,
            boolean useKimi, @Size(max = 4000) String extraPrompt) {}
    public record Event(String stage, String message, Instant at) {}
    public record CasePreview(String name, String label, int inputBytes, int outputBytes,
                              String inputPreview, String outputPreview) {}
    public record Result(String referenceSolutionCpp, String independentSolutionCpp, String generatorCpp,
                         String validatorCpp, String wrongSolutionCpp, List<CasePreview> cases,
                         List<String> notes, int mutantRejected, Map<String, String> models, long seed) {}
    public record JobView(String id, String state, String stage, String error, Instant createdAt,
                          List<Event> events, Result result) {}
}
