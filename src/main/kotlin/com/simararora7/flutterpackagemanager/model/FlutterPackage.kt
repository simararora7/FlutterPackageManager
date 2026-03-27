package com.simararora7.flutterpackagemanager.model

import java.nio.file.Path

data class FlutterPackage(
    val name: String,
    val path: Path,         // absolute path to the package directory
    val relToRepo: String   // relative to repo root, e.g. "packages/features/auth/auth_module"
) {
    /**
     * Returns the filepath string used in modules.xml.
     * Uses projectBasePath to compute a relative path so $PROJECT_DIR$ resolves correctly.
     *
     * Examples:
     *   - Project is repo root:   "$PROJECT_DIR$/packages/features/home/home.iml"
     *   - Project is a subdir:    "$PROJECT_DIR$/../packages/features/auth/auth.iml"
     */
    fun toFilepath(projectBasePath: Path): String {
        val relFromProject = projectBasePath.relativize(path)
        // Normalize path separators to forward slashes for XML
        val normalized = relFromProject.toString().replace('\\', '/')
        return "\$PROJECT_DIR\$/$normalized/${name}.iml"
    }
}
