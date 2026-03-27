package com.simararora7.flutterpackagemanager.util

import com.simararora7.flutterpackagemanager.model.FlutterPackage
import java.io.IOException
import java.nio.file.*
import java.nio.file.attribute.BasicFileAttributes

object PackageDiscovery {

    private val PRUNE_DIRS = setOf(
        ".dart_tool", ".pub", "build", ".idea",
        "android", "ios", "example", ".git", ".fvm"
    )

    /**
     * Walks the packages/ directory under [repoRoot], finding all Flutter packages
     * (directories containing a pubspec.yaml).
     *
     * Returns packages sorted by name.
     */
    fun discover(repoRoot: Path): List<FlutterPackage> {
        val packagesRoot = repoRoot.resolve("packages")
        if (!packagesRoot.toFile().isDirectory) return emptyList()

        val result = mutableListOf<FlutterPackage>()

        Files.walkFileTree(packagesRoot, object : SimpleFileVisitor<Path>() {
            override fun preVisitDirectory(dir: Path, attrs: BasicFileAttributes): FileVisitResult {
                val name = dir.fileName?.toString() ?: return FileVisitResult.CONTINUE
                return if (name in PRUNE_DIRS || name.startsWith(".")) {
                    FileVisitResult.SKIP_SUBTREE
                } else {
                    FileVisitResult.CONTINUE
                }
            }

            override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
                if (file.fileName.toString() == "pubspec.yaml") {
                    val pkgDir = file.parent
                    val pkgName = pkgDir.fileName.toString()
                    if (pkgName == "example") return FileVisitResult.CONTINUE
                    // relToRepo is relative to the repo root (e.g. "packages/features/auth/auth_module")
                    val relToRepo = repoRoot.relativize(pkgDir).toString().replace('\\', '/')
                    result.add(FlutterPackage(name = pkgName, path = pkgDir, relToRepo = relToRepo))
                }
                return FileVisitResult.CONTINUE
            }

            override fun visitFileFailed(file: Path, exc: IOException): FileVisitResult =
                FileVisitResult.CONTINUE
        })

        return result.sortedBy { it.name }
    }
}
