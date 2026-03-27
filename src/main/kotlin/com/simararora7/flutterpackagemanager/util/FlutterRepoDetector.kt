package com.simararora7.flutterpackagemanager.util

import com.intellij.openapi.project.Project
import java.io.File
import java.nio.file.Path

/**
 * Detects the Flutter monorepo root from the open project.
 *
 * Tries two layouts:
 *  1. Project IS the repo root — packages/ is a direct child of project base path
 *  2. Project is a subdirectory (e.g. my_app/) — packages/ is a sibling
 */
object FlutterRepoDetector {

    fun detect(project: Project): Path? {
        val basePath = project.basePath ?: return null
        val base = File(basePath)

        // Layout 1: project is the monorepo root
        val packagesDir1 = base.resolve("packages")
        if (packagesDir1.isDirectory) return base.toPath()

        // Layout 2: project is a subdirectory of the monorepo
        val packagesDir2 = base.parentFile?.resolve("packages")
        if (packagesDir2?.isDirectory == true) return base.parentFile!!.toPath()

        return null
    }
}
