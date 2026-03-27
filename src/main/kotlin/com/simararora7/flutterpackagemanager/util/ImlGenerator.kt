package com.simararora7.flutterpackagemanager.util

import com.simararora7.flutterpackagemanager.model.FlutterPackage

object ImlGenerator {

    /**
     * Generates the content of a .iml file for the given Flutter package.
     * Mirrors the Python CLI's build_iml_content() function exactly.
     */
    fun buildContent(pkg: FlutterPackage, withTests: Boolean = false): String {
        val pkgDir = pkg.path.toFile()

        val innerLines = buildList {
            if (pkgDir.resolve("assets").isDirectory) {
                add("""      <sourceFolder url="file://${'$'}MODULE_DIR${'$'}/assets" type="java-resource" />""")
            }
            if (pkgDir.resolve("lib").isDirectory) {
                add("""      <sourceFolder url="file://${'$'}MODULE_DIR${'$'}/lib" isTestSource="false" />""")
            }
            add("""      <excludeFolder url="file://${'$'}MODULE_DIR${'$'}/.dart_tool" />""")
            add("""      <excludeFolder url="file://${'$'}MODULE_DIR${'$'}/.pub" />""")
            add("""      <excludeFolder url="file://${'$'}MODULE_DIR${'$'}/build" />""")
            if (pkgDir.resolve("test").isDirectory) {
                if (withTests) {
                    add("""      <sourceFolder url="file://${'$'}MODULE_DIR${'$'}/test" isTestSource="true" />""")
                } else {
                    add("""      <excludeFolder url="file://${'$'}MODULE_DIR${'$'}/test" />""")
                }
            }
        }

        return buildString {
            appendLine("""<?xml version="1.0" encoding="UTF-8"?>""")
            appendLine("""<module type="JAVA_MODULE" version="4">""")
            appendLine("""  <component name="NewModuleRootManager" inherit-compiler-output="true">""")
            appendLine("""    <exclude-output />""")
            appendLine("""    <content url="file://${'$'}MODULE_DIR${'$'}">""")
            innerLines.forEach { appendLine(it) }
            appendLine("""    </content>""")
            appendLine("""    <orderEntry type="inheritedJdk" />""")
            appendLine("""    <orderEntry type="sourceFolder" forTests="false" />""")
            appendLine("""    <orderEntry type="library" name="Dart SDK" level="project" />""")
            appendLine("""    <orderEntry type="library" name="Flutter Plugins" level="project" />""")
            appendLine("""  </component>""")
            append("""</module>""")
        }
    }
}
