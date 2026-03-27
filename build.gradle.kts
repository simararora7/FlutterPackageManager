import org.jetbrains.intellij.platform.gradle.TestFrameworkType

plugins {
    id("java")
    id("org.jetbrains.kotlin.jvm") version "2.2.0"
    id("org.jetbrains.intellij.platform") version "2.2.1"
}

group = "com.simararora7.flutterpackagemanager"
version = "1.0.0"

kotlin {
    jvmToolchain(17)
}

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

dependencies {
    intellijPlatform {
        // Use locally installed Android Studio to avoid downloading ~1GB SDK
        // On macOS, localPath should point to the Contents dir inside the .app bundle
        local(file("/Applications/Android Studio.app/Contents"))

        // Build tooling
        pluginVerifier()
        instrumentationTools()
        testFramework(TestFrameworkType.Platform)
    }
}

intellijPlatform {
    pluginConfiguration {
        name = "Flutter Package Manager"
        ideaVersion {
            sinceBuild = "253"
            untilBuild = provider { null }
        }
    }
}

tasks {
    withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
        compilerOptions.jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }

    // The IntelliJ Platform Gradle Plugin v2 generates -Xbootclasspath/a pointing to
    // /Applications/Android Studio.app/lib/nio-fs.jar (missing "Contents/"), which causes
    // a ClassNotFoundException for MultiRoutingFileSystemProvider at JVM startup.
    // Add the correct path so the bootclasspath search finds the JAR.
    named<org.jetbrains.intellij.platform.gradle.tasks.RunIdeTask>("runIde") {
        jvmArgs("-Xbootclasspath/a:/Applications/Android Studio.app/Contents/lib/nio-fs.jar")
    }

    // buildSearchableOptions launches a headless IDE instance which fails with the local
    // Android Studio platform (missing MultiRoutingFileSystemProvider in bootclasspath).
    // Disable it — searchable options are optional metadata for the plugin marketplace.
    named("buildSearchableOptions") {
        enabled = false
    }
}
