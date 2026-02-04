package com.legacy.linker.model;

import java.nio.file.Path;
import java.util.List;

public record VbProject(
                String name,
                Path path,
                String exeName,
                List<Path> forms,
                List<Path> modules,
                List<Path> classes,
                List<ProjectDependency> dependencies) {
        // Compact constructor to allow creating with empty lists easily if needed,
        // or we can just rely on the main constructor.
}
