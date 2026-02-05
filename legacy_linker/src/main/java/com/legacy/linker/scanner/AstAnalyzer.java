package com.legacy.linker.scanner;

import com.legacy.linker.model.ProjectDependency;
import io.proleap.vb6.asg.metamodel.Module;
import io.proleap.vb6.asg.metamodel.Program;
import io.proleap.vb6.asg.metamodel.Procedure;
import io.proleap.vb6.asg.metamodel.statement.Statement;
import io.proleap.vb6.asg.runner.impl.VbParserRunnerImpl;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class AstAnalyzer {

    public enum SanitizationLevel {
        STRICT,
        RELAXED,
        HEAVY
    }

    private Path createUtf8TempFile(Path original, SanitizationLevel level) throws IOException {
        List<String> lines = Files.readAllLines(original, java.nio.charset.StandardCharsets.ISO_8859_1);

        StringBuilder cleanContent = new StringBuilder();
        boolean codeStarted = false;
        boolean isFrm = original.toString().toLowerCase().endsWith(".frm");
        boolean isCls = original.toString().toLowerCase().endsWith(".cls");

        if (!isFrm && !isCls) {
            codeStarted = true;
        }

        StringBuilder pendingLine = new StringBuilder();
        for (String line : lines) {
            if (!codeStarted) {
                String trimmed = line.trim();
                if (trimmed.startsWith("Attribute VB_Name")) {
                    codeStarted = true;
                    cleanContent.append(line).append(System.lineSeparator());
                }
            } else {
                if (pendingLine.length() > 0) {
                    pendingLine.append(" ").append(line.trim());
                } else {
                    pendingLine.append(line);
                }

                if (isLineContinuation(pendingLine.toString())) {
                    stripLineContinuation(pendingLine);
                    continue;
                }

                for (String sanitized : sanitizeLine(pendingLine.toString(), level)) {
                    if (!sanitized.isEmpty()) {
                        cleanContent.append(sanitized).append(System.lineSeparator());
                    }
                }
                pendingLine.setLength(0);
            }
        }

        if (pendingLine.length() > 0) {
            for (String sanitized : sanitizeLine(pendingLine.toString(), level)) {
                if (!sanitized.isEmpty()) {
                    cleanContent.append(sanitized).append(System.lineSeparator());
                }
            }
        }

        if (cleanContent.length() == 0) {
            cleanContent.append(String.join(System.lineSeparator(), lines));
        }

        String moduleName = "Unknown";
        for (String line : lines) {
            if (line.trim().startsWith("Attribute VB_Name")) {
                String[] parts = line.split("=");
                if (parts.length > 1) {
                    moduleName = parts[1].trim().replace("\"", "");
                }
                break;
            }
        }

        String ext = original.toString().toLowerCase().endsWith(".cls") ? ".cls"
                : (original.toString().toLowerCase().endsWith(".frm") ? ".frm" : ".bas");

        Path tempDir = Files.createTempDirectory("legacy_ast_ctx");
        Path temp = tempDir.resolve(moduleName + ext);
        Files.writeString(temp, cleanContent.toString());
        return temp;
    }

    private List<String> sanitizeLine(String line, SanitizationLevel level) {
        String trimmed = line.trim();
        String lowerTrimmed = trimmed.toLowerCase();
        if (trimmed.startsWith("#")) {
            return List.of();
        }
        if (lowerTrimmed.startsWith("attribute ")) {
            return List.of();
        }
        if (lowerTrimmed.startsWith("'") || lowerTrimmed.startsWith("rem ") || lowerTrimmed.equals("rem")) {
            return List.of(line);
        }
        if (level == SanitizationLevel.RELAXED || level == SanitizationLevel.HEAVY) {
            if (lowerTrimmed.startsWith("declare ")
                    || lowerTrimmed.startsWith("type ")
                    || lowerTrimmed.startsWith("enum ")
                    || lowerTrimmed.startsWith("with ")
                    || lowerTrimmed.equals("end with")
                    || lowerTrimmed.startsWith("implements ")
                    || lowerTrimmed.contains("ptrsafe")
                    || lowerTrimmed.contains("addressof")
                    || lowerTrimmed.startsWith("defobj ")) {
                return List.of();
            }
        }
        if (level == SanitizationLevel.HEAVY) {
            if (lowerTrimmed.startsWith("version ")
                    || lowerTrimmed.startsWith("object=")
                    || lowerTrimmed.startsWith("begin ")
                    || lowerTrimmed.startsWith("end ")
                    || lowerTrimmed.startsWith("beginproperty ")
                    || lowerTrimmed.startsWith("endproperty ")
                    || lowerTrimmed.startsWith("clientheight")
                    || lowerTrimmed.startsWith("clientwidth")
                    || lowerTrimmed.startsWith("clienttop")
                    || lowerTrimmed.startsWith("clientleft")) {
                return List.of();
            }
        }

        List<String> parts = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inString = false;

        for (int i = 0; i < line.length(); i++) {
            char ch = line.charAt(i);
            if (ch == '"') {
                if (i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    current.append("\"\"");
                    i++;
                    continue;
                }
                inString = !inString;
                current.append(ch);
                continue;
            }

            if (ch == ':' && !inString) {
                parts.add(current.toString());
                current.setLength(0);
                continue;
            }

            current.append(ch);
        }

        if (current.length() > 0) {
            parts.add(current.toString());
        }

        return parts;
    }

    private boolean isLineContinuation(String line) {
        String trimmed = line.trim();
        String lower = trimmed.toLowerCase();
        if (lower.startsWith("'") || lower.startsWith("rem ") || lower.equals("rem")) {
            return false;
        }
        return trimmed.endsWith("_");
    }

    private void stripLineContinuation(StringBuilder line) {
        int lastUnderscore = line.lastIndexOf("_");
        if (lastUnderscore >= 0) {
            line.delete(lastUnderscore, line.length());
        }
    }

    private boolean shouldSkipAst(Path file) {
        String lower = file.toString().toLowerCase();
        return !(lower.endsWith(".bas") || lower.endsWith(".frm") || lower.endsWith(".cls"));
    }

    public List<ProjectDependency> analyze(Path file) {
        if (shouldSkipAst(file)) {
            AstAnalysisReport.get().recordSkipped(file);
            return List.of();
        }

        System.out.println("AST analyzing: " + file.getFileName());
        for (SanitizationLevel level : new SanitizationLevel[] { SanitizationLevel.STRICT, SanitizationLevel.RELAXED,
                SanitizationLevel.HEAVY }) {
            List<ProjectDependency> dependencies = new ArrayList<>();
            try {
                AstAnalysisReport.get().recordAttempt(file, level);
                Path tempFile = createUtf8TempFile(file, level);

                Program program = new VbParserRunnerImpl().analyzeFile(tempFile.toFile());

                // Collect all modules
                List<Module> allModules = new ArrayList<>();
                allModules.addAll(program.getClazzModules().values());
                allModules.addAll(program.getStandardModules().values());

                if (allModules.isEmpty()) {
                    allModules.addAll(program.getModules());
                }

                for (Module module : allModules) {
                    // Process all procedures in the module
                    for (Procedure procedure : module.getProcedures()) {
                        String scopeName = procedure.getName();
                        analyzeStatements(scopeName, procedure.getStatements(), dependencies, file);
                    }
                }

                // Cleanup
                Files.deleteIfExists(tempFile);
                Files.deleteIfExists(tempFile.getParent());

                AstAnalysisReport.get().recordSuccess(file, level);
                return dependencies;
            } catch (Exception e) {
                // Try next sanitization level.
            }
        }

        AstAnalysisReport.get().recordFailure(file);
        return List.of();
    }

    private void analyzeStatements(String scopeName, List<Statement> statements, List<ProjectDependency> dependencies,
            Path sourceFile) {
        if (statements == null)
            return;

        for (Statement stmt : statements) {
            String code = stmt.toString();

            // 1. Detect Shell
            if (code.toLowerCase().contains("shell")) {
                dependencies.add(new ProjectDependency(
                        "Shell_Call",
                        sourceFile,
                        0,
                        code.trim(),
                        scopeName));
            }

            // 2. Detect CreateObject
            if (code.toLowerCase().contains("createobject")) {
                dependencies.add(new ProjectDependency(
                        "CreateObject",
                        sourceFile,
                        0,
                        code.trim(),
                        scopeName));
            }

            // Note: In a more advanced version, we would check for Block statements
            // and recursively call analyzeStatements on their sub-scopes.
        }
    }
}
