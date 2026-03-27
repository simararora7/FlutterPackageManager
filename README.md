# Flutter Package Manager

A native Android Studio plugin that manages `.iml` module files and `modules.xml` for Flutter monorepos — no CLI, no scripts, no environment variables.

## Features

- **Checkbox list** — all packages in your monorepo visible at a glance; checked = registered in Android Studio
- **Fuzzy search** — filter packages by name (same algorithm as "Go to File")
- **Reactive UI** — toggling a checkbox updates `modules.xml` immediately
- **Status bar widget** — shows `Modules: X / Y` at the bottom of the IDE; click to open the panel
- **Auto-detection** — finds your `packages/` directory automatically; no configuration needed for standard monorepo layouts
- **Balloon notifications** — when you open a file in an unregistered package, a balloon offers to add it in one click
- **Atomic writes** — `modules.xml` is always written safely (write to `.tmp` → atomic move)

## Requirements

- Android Studio Meerkat (build `AI-253`) or later
- JVM 17+

## Installation

### From zip (recommended)

1. Download `flutter-package-manager-1.0.0.zip` from the [latest release](../../releases/latest)
2. In Android Studio: **Settings → Plugins → ⚙ → Install Plugin from Disk…**
3. Select the downloaded zip → **OK** → restart Android Studio

### From source

```bash
git clone https://github.com/simararora7/FlutterPackageManager.git
cd FlutterPackageManager
./gradlew buildPlugin
```

Install the zip from `build/distributions/` as described above.

## Usage

1. Open your Flutter monorepo (or any subdirectory) in Android Studio
2. The **Flutter Modules** panel appears in the right tool window strip
3. Check packages to register them; uncheck to remove them from `modules.xml`
4. Use the search box to filter by package name
5. The status bar widget at the bottom shows `Modules: registered / total`

### Settings

**Settings → Tools → Flutter Module Manager**

- **Packages root** — override the auto-detected `packages/` path (leave blank for auto-detect)
- **Reload** — re-scan the packages directory

### Auto-detection

The plugin tries two layouts in order:

1. `{projectRoot}/packages/` — project is the monorepo root
2. `{projectRoot}/../packages/` — project opened in a subdirectory (e.g. `my_app/`)

If neither is found, configure the path in Settings.

## Building from source

```bash
# Compile only (fastest feedback)
./gradlew compileKotlin

# Launch a sandboxed Android Studio with the plugin loaded
./gradlew runIde

# Run plugin compatibility checks
./gradlew verifyPlugin

# Build distributable zip
./gradlew buildPlugin
```

> Requires Android Studio installed at `/Applications/Android Studio.app`. The build uses the local installation to avoid downloading the ~1 GB SDK.

## License

MIT
