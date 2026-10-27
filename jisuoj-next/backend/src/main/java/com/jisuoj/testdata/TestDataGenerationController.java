package com.jisuoj.testdata;

import com.jisuoj.auth.AuthSupport;
import jakarta.validation.Valid;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/test-data")
@PreAuthorize("hasAnyRole('ADMIN','PROBLEM_EDITOR')")
public class TestDataGenerationController {
    private final TestDataGenerationService service;
    public TestDataGenerationController(TestDataGenerationService service) { this.service = service; }
    @GetMapping("/capabilities") public Map<String, Object> capabilities() { return service.capabilities(); }
    @PostMapping("/jobs") public ResponseEntity<TestDataDtos.JobView> start(
            @Valid @RequestBody TestDataDtos.GenerateRequest request, Authentication authentication) {
        return ResponseEntity.accepted().body(service.start(request, AuthSupport.required(authentication).userId()));
    }
    @GetMapping("/jobs/{id}") public TestDataDtos.JobView status(@PathVariable String id, Authentication auth) {
        return service.status(id, AuthSupport.required(auth).userId());
    }
    @GetMapping("/jobs/{id}/zip") public ResponseEntity<byte[]> zip(@PathVariable String id, Authentication auth) {
        return ResponseEntity.ok().contentType(MediaType.parseMediaType("application/zip"))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename("test-data-" + id + ".zip").build().toString())
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(service.download(id, AuthSupport.required(auth).userId()));
    }
}
