package com.legacy.linker.generator;

import com.legacy.linker.model.VbProject;
import com.legacy.linker.scanner.AstAnalysisReport;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class MarkdownGenerator {

    private static final int CONTEXT_LINES = 2;
    private static final int LIST_COLLAPSE_THRESHOLD = 40;
    private final Path outputDir;
    private final AstAnalysisReport astReport;
    private final Map<Path, List<String>> sourceCache = new HashMap<>();

    public MarkdownGenerator(Path outputDir) {
        this(outputDir, AstAnalysisReport.get());
    }

    public MarkdownGenerator(Path outputDir, AstAnalysisReport astReport) {
        this.outputDir = outputDir;
        this.astReport = astReport;
    }

    public void generate(List<VbProject> projects) throws IOException {
        Path docsDir = outputDir.resolve("docs");
        Path sourcesDir = docsDir.resolve("sources");
        Files.createDirectories(docsDir);
        Files.createDirectories(sourcesDir);

        // 1. Generate mkdocs.yml
        generateMkDocsConfig(projects);
        generateThemeAssets(docsDir);

        // 2. Generate Home Page
        generateIndex(docsDir, projects);

        // 3. Generate Global Dependency Overview
        generateDependenciesOverview(docsDir, projects);

        // 4. Generate AST Summary
        generateAstSummary(docsDir);

        // 5. Generate Project Pages
        for (VbProject project : projects) {
            generateProjectPage(docsDir, project, projects);
            generateSourceDocs(sourcesDir, project);
        }
    }

    private void generateMkDocsConfig(List<VbProject> projects) throws IOException {
        StringBuilder yaml = new StringBuilder();
        yaml.append("site_name: Legacy VB Wiki\n");
        yaml.append("theme:\n");
        yaml.append("  name: material\n");
        yaml.append("  font:\n");
        yaml.append("    text: IBM Plex Sans\n");
        yaml.append("    code: IBM Plex Mono\n");
        yaml.append("  features:\n");
        yaml.append("    - navigation.expand\n");
        yaml.append("    - navigation.sections\n");
        yaml.append("    - navigation.tabs\n");
        yaml.append("    - navigation.top\n");
        yaml.append("    - search.highlight\n");
        yaml.append("    - search.suggest\n");
        yaml.append("    - search.share\n");
        yaml.append("    - content.code.copy\n");
        yaml.append("    - toc.follow\n");
        yaml.append("extra_css:\n");
        yaml.append("  - stylesheets/legacy-theme.css\n");
        yaml.append("extra_javascript:\n");
        yaml.append("  - https://unpkg.com/mermaid@10/dist/mermaid.min.js\n");
        yaml.append("  - javascripts/mermaid-init.js\n");
        yaml.append("markdown_extensions:\n");
        yaml.append("  - admonition\n");
        yaml.append("  - pymdownx.details\n");
        yaml.append("  - pymdownx.tabbed:\n");
        yaml.append("      alternate_style: true\n");
        yaml.append("  - toc:\n");
        yaml.append("      permalink: true\n");
        yaml.append("  - pymdownx.superfences:\n");
        yaml.append("      custom_fences:\n");
        yaml.append("        - name: mermaid\n");
        yaml.append("          class: mermaid\n");
        yaml.append("          format: !!python/name:pymdownx.superfences.fence_code_format\n");
        yaml.append("nav:\n");
        yaml.append("  - Home: index.md\n");
        yaml.append("  - Projects:\n");

        // Sorting projects by name for better navigation
        projects.stream()
                .sorted((p1, p2) -> p1.name().compareToIgnoreCase(p2.name()))
                .forEach(p -> {
                    String filename = getSafeFilename(p.name());
                    yaml.append("      - ").append(p.name()).append(": ").append(filename).append("\n");
                });
        yaml.append("  - Dependencies: dependencies.md\n");
        yaml.append("  - Analysis: ast_summary.md\n");

        Files.writeString(outputDir.resolve("mkdocs.yml"), yaml.toString());
    }

    private void generateIndex(Path docsDir, List<VbProject> projects) throws IOException {
        StringBuilder md = new StringBuilder();
        md.append("# Legacy System Overview\n\n");
        md.append("Welcome to the mapped documentation of the Legacy VB system.\n\n");
        md.append("!!! info \"How to use this site\"\n");
        md.append("    Use the **Projects** tab to browse a specific executable. Each project page includes:\n");
        md.append("    - A quick summary\n");
        md.append("    - Components (forms/modules)\n");
        md.append("    - Connections (outbound calls and dependency graph)\n\n");
        md.append("## Quick Links\n");
        for (VbProject p : projects) {
            md.append(String.format("- [%s](%s)\n", p.name(), getSafeFilename(p.name())));
        }
        md.append("\n");
        md.append("## Project Statistics\n\n");
        md.append("| Project Name | Exe Name | Forms | Modules | Outbound Calls |\n");
        md.append("| :--- | :--- | :---: | :---: | :---: |\n");

        for (VbProject p : projects) {
            md.append(String.format("| [%s](%s) | `%s` | %d | %d | %d |\n",
                    p.name(), getSafeFilename(p.name()), p.exeName(),
                    p.forms().size(), p.modules().size(),
                    p.dependencies() != null ? p.dependencies().size() : 0));
        }

        md.append("\n\nGenerated by LegacyLinker.");

        Files.writeString(docsDir.resolve("index.md"), md.toString());
    }

    private void generateProjectPage(Path docsDir, VbProject p, List<VbProject> projects) throws IOException {
        StringBuilder md = new StringBuilder();
        md.append("# ").append(p.name()).append("\n\n");

        md.append("## Basic Information\n");
        md.append("- **Exe Name**: `").append(p.exeName()).append("`\n");
        md.append("- **Project Path**: `").append(p.path().toString()).append("`\n\n");

        int outboundCalls = p.dependencies() != null ? p.dependencies().size() : 0;
        md.append("## At a Glance\n");
        md.append("- **Executable**: `").append(p.exeName()).append("`\n");
        md.append("- **Forms**: ").append(p.forms().size()).append("\n");
        md.append("- **Modules**: ").append(p.modules().size()).append("\n");
        md.append("- **Outbound calls**: ").append(outboundCalls).append("\n");
        md.append("- **Jump to**: [Components](#components) | [Connections](#connections)\n\n");

        md.append("## Components\n");
        if (p.forms().size() > LIST_COLLAPSE_THRESHOLD || p.modules().size() > LIST_COLLAPSE_THRESHOLD) {
            md.append("\nLarge lists are collapsed for readability. Expand a section to view full details.\n");
        }
        md.append("\n=== \"Forms (").append(p.forms().size()).append(")\"\n");
        appendListOrCollapsed(md, p.forms(), "Forms");
        md.append("\n");

        md.append("=== \"Modules (").append(p.modules().size()).append(")\"\n");
        appendListOrCollapsed(md, p.modules(), "Modules");

        md.append("## Connections\n");
        if (p.dependencies().isEmpty()) {
            md.append("_No outgoing shell calls detected._\n");
        } else {
            md.append("### Outbound Calls\n");
            md.append("This project calls the following external executables:\n\n");
            md.append("| Target | Source File | Line | Context | Content |\n");
            md.append("| :--- | :--- | :---: | :--- | :--- |\n");

            for (com.legacy.linker.model.ProjectDependency dep : p.dependencies()) {
                // Try to resolve the target exe to a project name
                String targetLink = resolveLink(dep.targetExeName(), projects);
                String sourceLink = buildSourceLink(p, dep);
                String contextSnippet = buildContextSnippet(dep);
                md.append(String.format("| %s | %s | %s | %s | `%s` |\n",
                        targetLink,
                        sourceLink,
                        formatLineNumber(dep.lineNumber()),
                        contextSnippet,
                        escapeTableCell(dep.rawLineContent()).trim() // Escape pipe table char
                ));
            }

            md.append("\n### Dependency Graph\n");
            md.append("```mermaid\n");
            md.append("graph LR\n");
            md.append("  Current[\"").append(escapeMermaidLabel(p.name())).append("\"]\n");
            for (com.legacy.linker.model.ProjectDependency dep : p.dependencies()) {
                String targetName = resolveName(dep.targetExeName(), projects);
                String safeTarget = toMermaidId(targetName);
                md.append("  Current --> ").append(safeTarget)
                        .append("[\"").append(escapeMermaidLabel(targetName)).append("\"]\n");
            }
            md.append("```\n");
        }

        Files.writeString(docsDir.resolve(getSafeFilename(p.name())), md.toString());
    }

    private void appendListOrCollapsed(StringBuilder md, List<Path> items, String label) {
        if (items.isEmpty()) {
            md.append("    _No ").append(label.toLowerCase()).append(" found._\n");
            return;
        }

        if (items.size() > LIST_COLLAPSE_THRESHOLD) {
            md.append("    ??? \"Show ").append(label.toLowerCase()).append(" list\"\n");
            for (Path item : items) {
                md.append("        - `").append(item.getFileName().toString()).append("`\n");
            }
            return;
        }

        for (Path item : items) {
            md.append("    - `").append(item.getFileName().toString()).append("`\n");
        }
    }

    private void generateDependenciesOverview(Path docsDir, List<VbProject> projects) throws IOException {
        StringBuilder md = new StringBuilder();
        md.append("# Global Dependency Graph\n\n");
        md.append("This view shows all detected outbound calls across projects.\n\n");
        md.append("```mermaid\n");
        md.append("graph LR\n");

        Set<String> nodes = new HashSet<>();
        Set<String> edges = new HashSet<>();

        for (VbProject project : projects) {
            String safeProject = sanitizeNodeId(project.name());
            nodes.add(String.format("  %s[\"%s\"]", safeProject, escapeMermaidLabel(project.name())));

            for (com.legacy.linker.model.ProjectDependency dep : project.dependencies()) {
                String targetName = resolveName(dep.targetExeName(), projects);
                String safeTarget = sanitizeNodeId(targetName);
                nodes.add(String.format("  %s[\"%s\"]", safeTarget, escapeMermaidLabel(targetName)));
                edges.add(String.format("  %s --> |Calls| %s", safeProject, safeTarget));
            }
        }

        for (String node : nodes) {
            md.append(node).append("\n");
        }
        for (String edge : edges) {
            md.append(edge).append("\n");
        }

        md.append("```\n");
        Files.writeString(docsDir.resolve("dependencies.md"), md.toString());
    }

    private void generateAstSummary(Path docsDir) throws IOException {
        Files.writeString(docsDir.resolve("ast_summary.md"), astReport.toMarkdown());
    }

    private void generateThemeAssets(Path docsDir) throws IOException {
        Path stylesDir = docsDir.resolve("stylesheets");
        Files.createDirectories(stylesDir);
        Path cssPath = stylesDir.resolve("legacy-theme.css");

        String css = "@import url('https://fonts.googleapis.com/css2?family=IBM+Plex+Sans:wght@300;400;500;600;700&family=IBM+Plex+Mono:wght@400;500;600&display=swap');\n\n"
                + ":root {\n"
                + "  --md-primary-fg-color: #1f3a5f;\n"
                + "  --md-primary-fg-color--light: #3a5f8a;\n"
                + "  --md-primary-fg-color--dark: #172a43;\n"
                + "  --md-accent-fg-color: #e84a5f;\n"
                + "  --md-default-fg-color: #1e2330;\n"
                + "  --md-default-fg-color--light: #4b5565;\n"
                + "  --md-default-bg-color: #f7f4ef;\n"
                + "}\n\n"
                + ".md-header {\n"
                + "  background: linear-gradient(135deg, #1f3a5f 0%, #2c4d7a 45%, #3c6e8f 100%);\n"
                + "  box-shadow: 0 6px 18px rgba(0, 0, 0, 0.12);\n"
                + "}\n\n"
                + ".md-main {\n"
                + "  background:\n"
                + "    radial-gradient(1200px 800px at 10% -10%, rgba(232, 74, 95, 0.08), transparent 60%),\n"
                + "    radial-gradient(900px 600px at 90% 0%, rgba(31, 58, 95, 0.08), transparent 55%),\n"
                + "    var(--md-default-bg-color);\n"
                + "}\n\n"
                + ".md-typeset h1 {\n"
                + "  letter-spacing: -0.02em;\n"
                + "  font-weight: 700;\n"
                + "}\n\n"
                + ".md-typeset h2 {\n"
                + "  margin-top: 1.6em;\n"
                + "  padding-top: 0.3em;\n"
                + "  border-top: 1px solid rgba(31, 58, 95, 0.12);\n"
                + "}\n\n"
                + ".md-typeset table:not([class]) {\n"
                + "  border-radius: 10px;\n"
                + "  overflow: hidden;\n"
                + "  box-shadow: 0 8px 20px rgba(15, 23, 42, 0.08);\n"
                + "}\n\n"
                + ".md-typeset table:not([class]) th {\n"
                + "  background: rgba(31, 58, 95, 0.08);\n"
                + "}\n\n"
                + ".md-typeset code {\n"
                + "  font-weight: 500;\n"
                + "  background: rgba(31, 58, 95, 0.08);\n"
                + "}\n\n"
                + ".md-typeset .admonition,\n"
                + ".md-typeset details {\n"
                + "  border-radius: 12px;\n"
                + "  box-shadow: 0 6px 16px rgba(15, 23, 42, 0.08);\n"
                + "}\n\n"
                + ".md-typeset .tabbed-set > label {\n"
                + "  font-weight: 600;\n"
                + "  letter-spacing: 0.01em;\n"
                + "}\n\n"
                + ".md-nav__link--active {\n"
                + "  font-weight: 600;\n"
                + "}\n";

        Files.writeString(cssPath, css);
    }

    private void generateSourceDocs(Path sourcesDir, VbProject project) throws IOException {
        List<Path> sourceFiles = new ArrayList<>();
        sourceFiles.addAll(project.forms());
        sourceFiles.addAll(project.modules());
        sourceFiles.addAll(project.classes());

        Path projectDir = sourcesDir.resolve(getSafeFilenameBase(project.name()));
        Files.createDirectories(projectDir);

        for (Path source : sourceFiles) {
            if (!Files.exists(source)) {
                continue;
            }

            List<String> lines = readFileSmart(source);
            StringBuilder md = new StringBuilder();
            md.append("# ").append(source.getFileName()).append("\n\n");
            md.append("Source path: `").append(source.toString()).append("`\n\n");
            md.append("| Line | Code |\n");
            md.append("| :---: | :--- |\n");

            int lineNumber = 1;
            for (String line : lines) {
                String safeLine = escapeHtmlForTable(line);
                md.append("| <a id=\"L").append(lineNumber).append("\"></a>")
                        .append(lineNumber)
                        .append(" | <code>")
                        .append(safeLine)
                        .append("</code> |\n");
                lineNumber++;
            }

            Path outputPath = projectDir.resolve(getSafeFilename(source.getFileName().toString()));
            Files.writeString(outputPath, md.toString());
        }
    }

    private String buildSourceLink(VbProject project, com.legacy.linker.model.ProjectDependency dep) {
        String filename = getSafeFilename(dep.sourceFile().getFileName().toString());
        String projectDir = getSafeFilenameBase(project.name());
        String anchor = dep.lineNumber() > 0 ? "#L" + dep.lineNumber() : "";
        return String.format("[%s](sources/%s/%s%s)", dep.sourceFile().getFileName(), projectDir, filename, anchor);
    }

    private String buildContextSnippet(com.legacy.linker.model.ProjectDependency dep) {
        if (dep.lineNumber() <= 0) {
            return "_N/A_";
        }

        List<String> lines = sourceCache.computeIfAbsent(dep.sourceFile(), path -> {
            try {
                return readFileSmart(path);
            } catch (IOException e) {
                return List.of();
            }
        });

        if (lines.isEmpty()) {
            return "_Unavailable_";
        }

        int lineIndex = dep.lineNumber() - 1;
        int start = Math.max(0, lineIndex - CONTEXT_LINES);
        int end = Math.min(lines.size() - 1, lineIndex + CONTEXT_LINES);
        StringBuilder snippet = new StringBuilder();

        for (int i = start; i <= end; i++) {
            if (i > start) {
                snippet.append(" / ");
            }
            snippet.append(escapeTableCell(lines.get(i).trim()));
        }

        return "`" + snippet + "`";
    }

    private String formatLineNumber(int lineNumber) {
        return lineNumber > 0 ? Integer.toString(lineNumber) : "-";
    }

    private List<String> readFileSmart(Path path) throws IOException {
        byte[] fileData = Files.readAllBytes(path);

        com.ibm.icu.text.CharsetDetector detector = new com.ibm.icu.text.CharsetDetector();
        detector.setText(fileData);
        com.ibm.icu.text.CharsetMatch match = detector.detect();

        String charsetName = "ISO-8859-1";
        if (match != null && match.getConfidence() > 50) {
            charsetName = match.getName();
        } else {
            try {
                java.nio.charset.Charset.forName("Big5").newDecoder().decode(java.nio.ByteBuffer.wrap(fileData));
                charsetName = "Big5";
            } catch (Exception e) {
                // fall back
            }
        }

        try {
            String content = new String(fileData, charsetName);
            return java.util.Arrays.asList(content.split("\\r?\\n"));
        } catch (Exception e) {
            return Files.readAllLines(path, StandardCharsets.ISO_8859_1);
        }
    }

    // Naive lookup: find the first project that produces this exe
    private String resolveLink(String exeName, List<VbProject> projects) {
        return projects.stream()
                .filter(p -> p.exeName().equalsIgnoreCase(exeName))
                .findFirst()
                .map(p -> String.format("[%s](%s)", p.name(), getSafeFilename(p.name())))
                .orElse(String.format("`%s` (Unknown)", exeName));
    }

    private String resolveName(String exeName, List<VbProject> projects) {
        return projects.stream()
                .filter(p -> p.exeName().equalsIgnoreCase(exeName))
                .findFirst()
                .map(VbProject::name)
                .orElse(exeName);
    }

    private String getSafeFilenameBase(String name) {
        return name.replaceAll("[^a-zA-Z0-9_\\-]", "_");
    }

    private String getSafeFilename(String name) {
        return getSafeFilenameBase(name) + ".md";
    }

    private String toMermaidId(String name) {
        String base = name.replaceAll("[^a-zA-Z0-9]", "");
        if (base.isEmpty() || Character.isDigit(base.charAt(0)) || base.length() > 64) {
            return "N" + Math.abs(name.hashCode());
        }
        return base;
    }

    private String sanitizeNodeId(String name) {
        return toMermaidId(name);
    }

    private String escapeMermaidLabel(String label) {
        String cleaned = label.replace("\\", "\\\\").replace("\"", "\\\"");
        cleaned = cleaned.replace("\r", " ").replace("\n", " ").trim();
        if (cleaned.length() > 80) {
            cleaned = cleaned.substring(0, 77) + "...";
        }
        return cleaned;
    }

    private String escapeTableCell(String cell) {
        if (cell == null) {
            return "";
        }
        return cell.replace("\\", "\\\\")
                .replace("|", "\\|")
                .replace("`", "\\`")
                .replace("\r", " ")
                .replace("\n", " ");
    }

    private String escapeHtmlForTable(String cell) {
        if (cell == null) {
            return "";
        }
        String escaped = cell.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("|", "&#124;")
                .replace("\r", " ")
                .replace("\n", " ");
        return escaped;
    }
}
