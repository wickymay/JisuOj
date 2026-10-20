package com.jisuoj.problemimport;

import org.springframework.stereotype.Component;

/**
 * Applies presentation-only cleanup immediately before a pulled problem is
 * written to the public problem set. Drafts deliberately keep their original
 * math delimiters so the extractor and artifact generator still have the full
 * source context.
 */
@Component
public class PublishedStatementCleaner {
    public String clean(String value) {
        if (value == null || value.isEmpty()) {
            return "";
        }
        return value.replace("\\$", "").replace("$", "");
    }
}
