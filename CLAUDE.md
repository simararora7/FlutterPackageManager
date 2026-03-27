# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What This Is

A native Android Studio plugin (`Flutter Module Manager`) that manages `.iml` module files and `modules.xml` for any Flutter monorepo.

Plugin ID: `com.simararora7.flutter-package-manager`
Target Android Studio build: `AI-253` (local install at `/Applications/Android Studio.app`)
Kotlin 2.2.x / JVM 17, Gradle 8.12, IntelliJ Platform Gradle Plugin v2.2.1

## Build & Run

```bash
# Compile Kotlin only (fastest feedback cycle)
./gradlew compileKotlin

# Build and launch a sandboxed Android Studio instance with the plugin loaded
./gradlew runIde

# Run plugin compatibility checks
./gradlew verifyPlugin

# Clean build outputs
./gradlew clean
```

> The build targets a **locally installed** Android Studio (`/Applications/Android Studio.app/Contents`) to avoid downloading the ~1GB SDK. If the app is missing, `compileKotlin` will fail.

There are no unit tests. Verification is done by running `./gradlew runIde` and smoke-testing against a Flutter monorepo.

## Gradle Configuration Notes

The `settings.gradle.kts` intentionally does **not** use the `org.jetbrains.intellij.platform.settings` settings plugin. That plugin's `intellijPlatform {}` DSL extension is unavailable during Kotlin DSL script compilation on Gradle 8.12 ("Unresolved reference: intellijPlatform"). Instead, repositories are declared directly in `build.gradle.kts` using the project plugin's `intellijPlatform { defaultRepositories() }` extension and the plugin version is pinned in the `plugins {}` block.

## IntelliJ Platform API Gotchas

- **Kotlin version**: Must be ≥ 2.2.0 to match the Kotlin metadata version compiled into Android Studio's JARs. Lower versions produce "was compiled with an incompatible version of Kotlin" errors.
- **`Consumer` type**: IntelliJ APIs use `com.intellij.util.Consumer`, not `java.util.function.Consumer`. Mismatching these causes return-type subtype errors.
- **`SimpleToolWindowPanel`**: `com.intellij.openapi.wm.impl.SimpleToolWindowPanel` is in an internal package and inaccessible. Use `JPanel(BorderLayout())` directly instead.

## Architecture

All source lives under `src/main/kotlin/com/simararora7/flutterpackagemanager/`.

```
model/           FlutterPackage data class
util/            Pure, project-independent logic
services/        Project-scoped services (state, I/O, settings)
toolwindow/      Tool Window panel and factory
statusbar/       Status bar widget and factory
listeners/       File editor event listener
settings/        Settings page (Configurable)
resources/META-INF/plugin.xml   All extension point registrations
```

### Data Flow

1. **`ModuleManagerService`** is the single source of truth. On project open it:
   - Resolves the repo root via `FlutterRepoDetector` (or `ModuleManagerSettings` override)
   - Reads `{projectBasePath}/.idea/modules.xml` → populates `registeredPaths: Set<String>`
   - Walks `{repoRoot}/packages/**` via `PackageDiscovery` → populates `allPackages`
   - Fires `notifyListeners()` after each step so the UI can update incrementally

2. **`ModulesPanel`** (Tool Window) and **`ModuleStatusBarWidget`** both register as change listeners on `ModuleManagerService`. When state changes (register/unregister/clear/reload), listeners are fired and the UI refreshes on the EDT via `invokeLater`.

3. **`FileNavigationListener`** fires on every file open. It uses `isRegisteredByRoot(Path)` — an O(1) `Set<Path>` lookup — for the hot path. If the package is unregistered, it shows a balloon notification with an "Add" action.

### Key Invariants

- `modules.xml` root entry (`$PROJECT_DIR$/.idea/${project.name}.iml`) is always first and always present.
- All other entries are sorted and deduplicated.
- All file writes are atomic: write to `.tmp` → `Files.move(ATOMIC_MOVE)`.
- Mutations go through `Mutex` to prevent concurrent writes; reads use `@Volatile` snapshots without locking for performance.
- The coroutine scope is `CoroutineScope(SupervisorJob() + Dispatchers.Default)`; IO work dispatched with `Dispatchers.IO`. Cancelled in `dispose()`.

### Auto-Detection Logic (`FlutterRepoDetector`)

Tries two layouts in order:
1. `{project.basePath}/packages/` — project is the monorepo root
2. `{project.basePath}/../packages/` — project opened in a subdirectory (e.g. `my_app/`)

Falls back to null; user must configure the path in Settings → Tools → Flutter Module Manager.

### Module Path Format

Packages are stored in `modules.xml` as:
```
$PROJECT_DIR$/../{relToRepo}/{name}.iml
```
where `relToRepo` is relative to the **repo root** (not the packages dir), e.g. `packages/features/auth`.

### Settings Persistence

`ModuleManagerSettings` is a `PersistentStateComponent` stored per-project in `.idea/flutterModuleManager.xml`. Fields:
- `packagesRootPath: String` — empty = auto-detect; non-empty overrides `FlutterRepoDetector`
- `suppressedPackages: MutableSet<String>` — packages whose balloon notifications are suppressed

Calling `ModuleManagerService.reload()` re-reads settings and re-runs full discovery.

## Android Studio Version

- Build: `AI-253.30387.90.2532.14935130`
- Platform: `AI` (Android Studio)
- `sinceBuild`: `253`
- Local path: `/Applications/Android Studio.app/Contents`

## IntelliJ Platform APIs in Use

| Purpose | API |
|---|---|
| Project service | `@Service(Level.PROJECT)`, `project.service<T>()` |
| Persistent state | `PersistentStateComponent`, `@State`, `@Storage` |
| Checkbox list | `com.intellij.ui.CheckBoxList<T>` |
| Fuzzy matching | `NameUtil.buildMatcher("*$query", NONE)` — same as "Go to File" |
| Search field | `com.intellij.ui.SearchTextField` |
| Status bar widget | `StatusBarWidgetFactory`, `StatusBarWidget.TextPresentation` |
| Click consumer | `com.intellij.util.Consumer<MouseEvent>` (not `java.util.function.Consumer`) |
| Notifications | `NotificationGroupManager`, `NotificationAction` |
| VFS refresh | `VfsUtil.markDirtyAndRefresh(false, false, false, vFile)` |
| Tool window | `ToolWindowFactory`, `JPanel(BorderLayout())` |
