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

    private Path createUtf8TempFile(Path original) throws IOException {
        List<String> lines = Files.readAllLines(original, java.nio.charset.StandardCharsets.ISO_8859_1);

        StringBuilder cleanContent = new StringBuilder();
        boolean codeStarted = false;
        boolean isFrm = original.toString().toLowerCase().endsWith(".frm");
        boolean isCls = original.toString().toLowerCase().endsWith(".cls");

        if (!isFrm && !isCls) {
            codeStarted = true;
        }

        for (String line : lines) {
            if (!codeStarted) {
                String trimmed = line.trim();
                if (trimmed.startsWith("Attribute VB_Name")) {
                    codeStarted = true;
                    cleanContent.append(line).append(System.lineSeparator());
                }
            } else {
                cleanContent.append(line).append(System.lineSeparator());
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

    public List<ProjectDependency> analyze(Path file) {
        List<ProjectDependency> dependencies = new ArrayList<>();
        try {
            Path tempFile = createUtf8TempFile(file);
            System.out.println("AST analyzing: " + file.getFileName());

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

        } catch (Exception e) {
            System.err.println("AST Parse Error for " + file + ": " + e.getMessage());
        }
        return dependencies;
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
