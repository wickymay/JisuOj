package com.jisuoj.problemimport;

public interface ProblemSourceAdapter {
    String type();

    ProblemImportDtos.Draft parse(SourceReference reference, String content);
}
