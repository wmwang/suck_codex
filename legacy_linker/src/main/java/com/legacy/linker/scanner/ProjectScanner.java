package com.legacy.linker.scanner;

import com.legacy.linker.model.VbProject;
import com.legacy.linker.model.ProjectDependency;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

public class ProjectScanner {

    public List<VbProject> scan(Path rootDir) throws IOException {
        try (Stream<Path> walk = Files.walk(rootDir)) {
            return walk.filter(p -> p.toString().toLowerCase().endsWith(".vbp"))
                    .map(this::parseVbp)
                    .toList();
        }
    }

    private VbProject parseVbp(Path vbpPath) {
        String name = "Unknown";
        String exeName = "Unknown";
        List<Path> forms = new ArrayList<>();
        List<Path> modules = new ArrayList<>();
        List<Path> classes = new ArrayList<>();

        try {
            List<String> lines = readFileSmart(vbpPath);

            for (String line : lines) {
                line = line.trim();
                if (line.startsWith("Name=")) {
                    name = getValue(line);
                } else if (line.startsWith("ExeName32=")) {
                    exeName = getValue(line);
                } else if (line.startsWith("Form=")) {
                    forms.add(resolvePath(vbpPath, getValue(line)));
                } else if (line.startsWith("Module=")) {
                    // Module=ModName; ModFile.bas
                    String val = getValue(line);
                    if (val.contains(";")) {
                        val = val.split(";")[1].trim();
                    }
                    modules.add(resolvePath(vbpPath, val));
                } else if (line.startsWith("Class=")) {
                    // Class=ClassName; ClassFile.cls
                    String val = getValue(line);
                    if (val.contains(";")) {
                        val = val.split(";")[1].trim();
                    }
                    classes.add(resolvePath(vbpPath, val));
                }
            }
        } catch (IOException e) {
            System.err.println("Error reading VBP: " + vbpPath + " - " + e.getMessage());
        }

        // Now scan the forms and modules for dependencies
        List<ProjectDependency> dependencies = scanForDependencies(forms, modules, classes);

        return new VbProject(name, vbpPath, exeName, forms, modules, classes, dependencies);
    }

    private List<ProjectDependency> scanForDependencies(List<Path> forms, List<Path> modules, List<Path> classes) {
        List<ProjectDependency> dependencies = new ArrayList<>();
        List<Path> allFiles = new ArrayList<>();
        allFiles.addAll(forms);
        allFiles.addAll(modules);
        allFiles.addAll(classes);

        for (Path source : allFiles) {
            if (!Files.exists(source))
                continue;

            try {
                // 1. Regex Scan using Smart Read
                List<String> lines = readFileSmart(source);
                int lineNum = 0;
                for (String line : lines) {
                    lineNum++;
                    String lower = line.toLowerCase();
                    if (lower.contains(".exe") || lower.contains("shell") || lower.contains("createobject")) {
                        String targetExe = extractTargetExe(line);
                        if (targetExe != null && !targetExe.isEmpty()) {
                            dependencies.add(new ProjectDependency(
                                    targetExe, source, lineNum, line.trim(), "Regex-Scan"));
                        }
                    }
                }

                // 2. AST Analysis (Context Aware)
                try {
                    List<ProjectDependency> astDeps = new com.legacy.linker.scanner.AstAnalyzer().analyze(source);
                    dependencies.addAll(astDeps);
                } catch (Exception e) {
                    // Log but don't fail the build
                    // System.err.println("Warning: AST analysis failed for " +
                    // source.getFileName());
                }

            } catch (IOException e) {
                System.err.println("Error reading file: " + source);
            }
        }
        return dependencies;
    }

    /**
     * Reads file content with automatic character set detection.
     * Uses ICU4J to detect encoding, with specific handling for Big5 legacy
     * projects.
     */
    private List<String> readFileSmart(Path path) throws IOException {
        byte[] fileData = Files.readAllBytes(path);

        // 1. Try ICU detection
        com.ibm.icu.text.CharsetDetector detector = new com.ibm.icu.text.CharsetDetector();
        detector.setText(fileData);
        com.ibm.icu.text.CharsetMatch match = detector.detect();

        String charsetName = "ISO-8859-1"; // Default fallback
        if (match != null && match.getConfidence() > 50) {
            charsetName = match.getName();
        } else {
            // 2. If detection is weak, try to see if it's Big5 compatible (common for TW
            // legacy)
            // This is a naive heuristic but effective for specific user request.
            try {
                java.nio.charset.Charset.forName("Big5").newDecoder().decode(java.nio.ByteBuffer.wrap(fileData));
                charsetName = "Big5";
            } catch (Exception e) {
                // Not valid Big5, stick to fallback
            }
        }

        try {
            String content = new String(fileData, charsetName);
            // Split by newlines (handle CRLF and LF)
            return java.util.Arrays.asList(content.split("\\r?\\n"));
        } catch (Exception e) {
            // Ultimate fallback
            return Files.readAllLines(path, java.nio.charset.StandardCharsets.ISO_8859_1);
        }
    }

    private boolean containsShellCommand(String line) {
        // Broaden scope: look for any .exe string literal.
        // This catches "shellCmd.Append 'djxl.exe'" or "Process.Start('calc.exe')"
        return line.toLowerCase().contains(".exe");
    }

    private String extractTargetExe(String line) {
        // Look for "filename.exe" pattern (case insensitive)
        // Group 1 matches the filename.
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("\"([^\"]*\\.exe)[^\"]*\"",
                java.util.regex.Pattern.CASE_INSENSITIVE);
        java.util.regex.Matcher matcher = pattern.matcher(line);
        if (matcher.find()) {
            String path = matcher.group(1);
            // Limit length to avoid false positives (e.g. extremely long strings)
            if (path.length() > 255)
                return null;

            // Return just the filename
            Path p = Paths.get(path.trim());
            return p.getFileName().toString();
        }
        return null;
    }

    private String getValue(String line) {
        String[] parts = line.split("=", 2);
        if (parts.length < 2)
            return "";
        return parts[1].replace("\"", "").trim();
    }

    private Path resolvePath(Path vbpPath, String relativePath) {
        // VB6 uses Windows backslashes. On non-Windows systems, we must convert them.
        String normalized = relativePath.replace("\\", "/");
        return vbpPath.getParent().resolve(normalized).normalize();
    }
}
