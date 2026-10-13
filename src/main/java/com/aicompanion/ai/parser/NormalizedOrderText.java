package com.aicompanion.ai.parser;

import java.text.Normalizer;
import java.util.regex.Pattern;

public class NormalizedOrderText {
    private static final Pattern DIACRITICS_PATTERN = Pattern.compile("\\p{InCombiningDiacriticalMarks}+");

    private final String originalText;
    private final String normalizedText;

    public NormalizedOrderText(String text) {
        this.originalText = text != null ? text : "";
        this.normalizedText = foldAccents(this.originalText);
    }

    public static String foldAccents(String input) {
        if (input == null) return "";
        String s = input.trim().toLowerCase();

        // 1. Remove Spanish question and exclamation marks
        s = s.replace("¿", "").replace("?", " ")
                .replace("¡", "").replace("!", " ");

        // 2. Normalize common phonetic typos and slang
        s = s.replaceAll("\\bporfa\\b|\\bporfa\\b|\\bxfa\\b|\\bxfavor\\b", "por favor")
                .replaceAll("\\bacha\\b", "hacha")
                .replaceAll("\\bvaul\\b", "baul")
                .replaceAll("\\bpika\\b", "pica")
                .replaceAll("\\bkiero\\b", "quiero");

        // 3. Decompose unicode characters into base letters + diacritic marks
        String decomposed = Normalizer.normalize(s, Normalizer.Form.NFD);
        // Remove diacritical marks (accents: á->a, é->e, í->i, ó->o, ú->u)
        String stripped = DIACRITICS_PATTERN.matcher(decomposed).replaceAll("");

        // 4. Clean multiple spaces and non-standard whitespace
        return stripped.replaceAll("\\s+", " ").trim();
    }

    public String getOriginalText() {
        return originalText;
    }

    public String getNormalizedText() {
        return normalizedText;
    }

    @Override
    public String toString() {
        return normalizedText;
    }
}
