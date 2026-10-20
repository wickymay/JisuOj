package com.jisuoj.judge;

import java.util.List;

public final class JudgeCatalog {
    private JudgeCatalog() {
    }

    public record Language(int id, String name, String extension) {
    }

    public static final List<String> RESULT_CODES = List.of(
            "PD", "PR", "CI", "RJ", "AC", "PE", "WA", "TLE", "MLE",
            "OLE", "RE", "CE", "CO", "TR", "MC", "SUBMIT", "RP", "RJ"
    );

    public static final List<Language> LANGUAGES = List.of(
            new Language(0, "C", "c"),
            new Language(1, "C++", "cc"),
            new Language(2, "Pascal", "pas"),
            new Language(3, "Java", "java"),
            new Language(4, "Ruby", "rb"),
            new Language(5, "Bash", "sh"),
            new Language(6, "Python", "py"),
            new Language(7, "PHP", "php"),
            new Language(8, "Perl", "pl"),
            new Language(9, "C#", "cs"),
            new Language(10, "Objective-C", "m"),
            new Language(11, "FreeBasic", "bas"),
            new Language(12, "Scheme", "scm"),
            new Language(13, "Clang", "c"),
            new Language(14, "Clang++", "cc"),
            new Language(15, "Lua", "lua"),
            new Language(16, "JavaScript", "js"),
            new Language(17, "Go", "go"),
            new Language(18, "SQL", "sql"),
            new Language(19, "Fortran", "f95"),
            new Language(20, "Matlab", "m"),
            new Language(21, "Cobol", "cob"),
            new Language(22, "R", "R"),
            new Language(23, "Scratch3", "sb3"),
            new Language(24, "Cangjie", "cj")
    );

    public static String resultCode(int result) {
        return result >= 0 && result < RESULT_CODES.size() ? RESULT_CODES.get(result) : "UNKNOWN";
    }

    public static String languageName(int language) {
        return LANGUAGES.stream()
                .filter(item -> item.id() == language)
                .map(Language::name)
                .findFirst()
                .orElse("Unknown");
    }

    public static boolean supportsLanguage(int language) {
        return LANGUAGES.stream().anyMatch(item -> item.id() == language);
    }
}
