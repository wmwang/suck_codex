package com.legacy.linker;

import com.legacy.linker.generator.MarkdownGenerator;
import com.legacy.linker.model.VbProject;
import com.legacy.linker.scanner.ProjectScanner;
import com.legacy.linker.scanner.AstAnalysisReport;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.Callable;

@Command(name = "legacy-linker", mixinStandardHelpOptions = true, version = "1.0", description = "Analyzes VB6 projects and generates a Wiki.")
public class Main implements Callable<Integer> {

    @Parameters(index = "0", description = "The root directory to scan.")
    private Path rootDir;

    @Option(names = { "-o", "--output" }, description = "Output directory for the Wiki.", defaultValue = "wiki_output")
    private Path outputDir;

    @Override
    public Integer call() throws Exception {
        System.out.println("Analyzing directory: " + rootDir.toAbsolutePath());

        AstAnalysisReport.get().reset();
        ProjectScanner scanner = new ProjectScanner();
        List<VbProject> projects = scanner.scan(rootDir);

        System.out.println("Found " + projects.size() + " VB Projects.");

        // Generate Wiki
        System.out.println("Generating Wiki in: " + outputDir.toAbsolutePath());
        MarkdownGenerator generator = new MarkdownGenerator(outputDir);
        generator.generate(projects);

        System.out.println("Done! To view the wiki, run 'mkdocs serve' in the output directory.");
        return 0;
    }

    public static void main(String... args) {
        int exitCode = new CommandLine(new Main()).execute(args);
        System.exit(exitCode);
    }
}
