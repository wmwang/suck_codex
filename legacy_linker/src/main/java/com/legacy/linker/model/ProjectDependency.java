package com.legacy.linker.model;

import java.nio.file.Path;

public record ProjectDependency(
                String targetExeName,
                Path sourceFile,
                int lineNumber,
                String rawLineContent,
                String scope // e.g. "cmdLogin_Click" or "Module_Level"
) {
}
