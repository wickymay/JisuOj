package com.jisuoj.problemimport;

import com.jisuoj.auth.AuthSupport;
import com.jisuoj.auth.UserPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import static com.jisuoj.common.RequestSupport.clientIp;

@RestController
@RequestMapping("/api/admin/problem-imports")
@PreAuthorize("hasAnyRole('ADMIN','PROBLEM_EDITOR')")
public class ProblemImportController {
    private final ProblemImportService service;
    private final com.fasterxml.jackson.databind.ObjectMapper objectMapper;

    public ProblemImportController(ProblemImportService service,
                                   com.fasterxml.jackson.databind.ObjectMapper objectMapper) {
        this.service = service;
        this.objectMapper = objectMapper;
    }

    @GetMapping("/capabilities")
    public Map<String, Object> capabilities() {
        return Map.of("sources", java.util.List.of("codeforces", "pta", "manual"),
                "aiConfigured", service.aiConfigured(), "maxZipMiB", 64, "maxExtractedMiB", 256);
    }

    @PostMapping("/preview")
    public ProblemImportDtos.Draft preview(@Valid @RequestBody ProblemImportDtos.PreviewRequest request) {
        return service.preview(request);
    }

    @PostMapping("/generate-artifacts")
    public ProblemImportDtos.Draft generateArtifacts(
            @Valid @RequestBody ProblemImportDtos.GenerateArtifactsRequest request) {
        return service.generateArtifacts(request);
    }

    @PostMapping(value = "/publish", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ProblemImportDtos.Published publish(
            @Valid @RequestPart("problem") ProblemImportDtos.Draft draft,
            @RequestPart(value = "testData", required = false) MultipartFile testData,
            Authentication authentication,
            HttpServletRequest request) {
        UserPrincipal actor = AuthSupport.required(authentication);
        return service.publish(draft, testData, actor, clientIp(request));
    }

    @PostMapping("/handoff")
    public ResponseEntity<byte[]> handoff(@Valid @RequestBody ProblemImportDtos.Draft draft) throws Exception {
        byte[] body = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(service.handoff(draft))
                .getBytes(StandardCharsets.UTF_8);
        String safeTitle = draft.title().replaceAll("[^A-Za-z0-9._-]", "_");
        if (safeTitle.isBlank()) safeTitle = "problem";
        ContentDisposition disposition = ContentDisposition.attachment()
                .filename(safeTitle + "-judgedata.json", StandardCharsets.UTF_8).build();
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .body(body);
    }
}
