package com.jisuoj.judge;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

@RestController
@RequestMapping("/api/judge")
public class JudgeCatalogController {
    @GetMapping("/catalog")
    public Map<String, Object> catalog() {
        List<Map<String, Object>> results = IntStream.range(0, JudgeCatalog.RESULT_CODES.size())
                .mapToObj(index -> Map.<String, Object>of("id", index, "code", JudgeCatalog.RESULT_CODES.get(index)))
                .toList();
        return Map.of("results", results, "languages", JudgeCatalog.LANGUAGES);
    }
}
