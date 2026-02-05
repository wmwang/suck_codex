package com.legacy.linker.scanner;

import java.nio.file.Path;
import java.util.EnumMap;
import java.util.Map;

public class AstAnalysisReport {

    private static final AstAnalysisReport INSTANCE = new AstAnalysisReport();

    private final Map<AstAnalyzer.SanitizationLevel, Integer> attempts = new EnumMap<>(AstAnalyzer.SanitizationLevel.class);
    private final Map<AstAnalyzer.SanitizationLevel, Integer> successes = new EnumMap<>(AstAnalyzer.SanitizationLevel.class);
    private int skipped = 0;
    private int failed = 0;

    private AstAnalysisReport() {
        for (AstAnalyzer.SanitizationLevel level : AstAnalyzer.SanitizationLevel.values()) {
            attempts.put(level, 0);
            successes.put(level, 0);
        }
    }

    public static AstAnalysisReport get() {
        return INSTANCE;
    }

    public synchronized void reset() {
        for (AstAnalyzer.SanitizationLevel level : AstAnalyzer.SanitizationLevel.values()) {
            attempts.put(level, 0);
            successes.put(level, 0);
        }
        skipped = 0;
        failed = 0;
    }

    public synchronized void recordAttempt(Path file, AstAnalyzer.SanitizationLevel level) {
        attempts.put(level, attempts.get(level) + 1);
    }

    public synchronized void recordSuccess(Path file, AstAnalyzer.SanitizationLevel level) {
        successes.put(level, successes.get(level) + 1);
    }

    public synchronized void recordFailure(Path file) {
        failed++;
    }

    public synchronized void recordSkipped(Path file) {
        skipped++;
    }

    public synchronized String toMarkdown() {
        int totalAttempts = attempts.values().stream().mapToInt(Integer::intValue).sum();
        int totalSuccess = successes.values().stream().mapToInt(Integer::intValue).sum();

        StringBuilder md = new StringBuilder();
        md.append("# AST Analysis Summary\n\n");
        md.append("This summary reports AST parsing outcomes for the current scan.\n\n");
        md.append("| Metric | Count |\n");
        md.append("| :--- | ---: |\n");
        md.append("| Attempts | ").append(totalAttempts).append(" |\n");
        md.append("| Success | ").append(totalSuccess).append(" |\n");
        md.append("| Failed | ").append(failed).append(" |\n");
        md.append("| Skipped | ").append(skipped).append(" |\n\n");

        md.append("## By Sanitization Level\n\n");
        md.append("| Level | Attempts | Success |\n");
        md.append("| :--- | ---: | ---: |\n");
        for (AstAnalyzer.SanitizationLevel level : AstAnalyzer.SanitizationLevel.values()) {
            md.append("| ").append(level.name()).append(" | ")
                    .append(attempts.get(level)).append(" | ")
                    .append(successes.get(level)).append(" |\n");
        }
        md.append("\n");

        return md.toString();
    }
}
