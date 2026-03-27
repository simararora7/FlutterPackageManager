package com.simararora7.flutterpackagemanager.services

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.ui.EditorNotifications
import com.simararora7.flutterpackagemanager.model.FlutterPackage
import com.simararora7.flutterpackagemanager.model.PackageState
import com.simararora7.flutterpackagemanager.util.FlutterRepoDetector
import com.simararora7.flutterpackagemanager.util.ImlGenerator
import com.simararora7.flutterpackagemanager.util.PackageDiscovery
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.w3c.dom.Element
import java.nio.file.*
import java.nio.file.attribute.BasicFileAttributes
import java.util.concurrent.CopyOnWriteArrayList
import javax.xml.parsers.DocumentBuilderFactory

@Service(Service.Level.PROJECT)
class ModuleManagerService(private val project: Project) : Disposable {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val mutex = Mutex()

    // ─── Public volatile state (read without lock for performance) ───────────

    @Volatile var allPackages: List<FlutterPackage> = emptyList()
        private set

    @Volatile var registeredPaths: Set<String> = emptySet()
        private set

    /** Set of package root Paths for O(1) lookup in the navigation listener */
    @Volatile private var registeredRoots: Set<Path> = emptySet()

    @Volatile var isDiscoveryComplete: Boolean = false
        private set

    @Volatile var repoRoot: Path? = null
        private set

    // ─── Listeners ────────────────────────────────────────────────────────────

    private val changeListeners = CopyOnWriteArrayList<() -> Unit>()

    fun addChangeListener(listener: () -> Unit) = changeListeners.add(listener)
    fun removeChangeListener(listener: () -> Unit) = changeListeners.remove(listener)

    private fun notifyListeners() = changeListeners.forEach { it() }

    // ─── Initialization ───────────────────────────────────────────────────────

    init {
        scope.launch(Dispatchers.IO) {
            // Step 1: resolve repo root
            val resolved = resolveRepoRoot()
            repoRoot = resolved

            // Step 2: read current modules.xml
            mutex.withLock { loadRegistered() }
            notifyListeners()

            // Step 3: discover packages in background
            if (resolved != null) {
                allPackages = PackageDiscovery.discover(resolved)
                mutex.withLock { rebuildRegisteredRoots() }
            }
            isDiscoveryComplete = true
            notifyListeners()
            // Files opened before discovery finished won't have been evaluated by the
            // EditorNotificationProvider (it returns null while !isDiscoveryComplete).
            // Re-evaluate all open editors now so the notification strip appears.
            EditorNotifications.getInstance(project).updateAllNotifications()
        }
    }

    // ─── Path helpers ─────────────────────────────────────────────────────────

    private val projectBasePath: Path?
        get() = project.basePath?.let(Path::of)

    val modulesXmlPath: Path?
        get() = projectBasePath?.resolve(".idea/modules.xml")

    val rootModuleEntry: String
        get() = "\$PROJECT_DIR\$/.idea/${project.name}.iml"

    private fun resolveRepoRoot(): Path? {
        val settings = ModuleManagerSettings.getInstance(project)
        val override = settings.packagesRootPath
        if (override.isNotBlank()) {
            val p = Path.of(override)
            return if (p.toFile().isDirectory) p else null
        }
        return FlutterRepoDetector.detect(project)
    }

    // ─── Read modules.xml ─────────────────────────────────────────────────────

    private fun loadRegistered() {
        val xmlPath = modulesXmlPath ?: return
        val xmlFile = xmlPath.toFile()
        if (!xmlFile.exists()) {
            registeredPaths = emptySet()
            return
        }
        try {
            val db = DocumentBuilderFactory.newInstance().newDocumentBuilder()
            val doc = db.parse(xmlFile)
            val modules = doc.getElementsByTagName("module")
            val paths = mutableSetOf<String>()
            for (i in 0 until modules.length) {
                val el = modules.item(i) as? Element ?: continue
                val fp = el.getAttribute("filepath")
                if (fp.isNotBlank()) paths.add(fp)
            }
            registeredPaths = paths
        } catch (_: Exception) {
            registeredPaths = emptySet()
        }
    }

    private fun rebuildRegisteredRoots() {
        val base = projectBasePath ?: return
        val roots = mutableSetOf<Path>()
        for (pkg in allPackages) {
            if (pkg.toFilepath(base) in registeredPaths) {
                roots.add(pkg.path)
            }
        }
        // The project root dir is registered via rootModuleEntry; add base so that opening
        // files like lib/main.dart doesn't trigger "unregistered package" notifications.
        if (rootModuleEntry in registeredPaths) {
            roots.add(base)
        }
        registeredRoots = roots
    }

    // ─── Queries (lock-free, reads volatile snapshots) ───────────────────────

    fun isRegistered(pkg: FlutterPackage): Boolean {
        val base = projectBasePath ?: return false
        return pkg.toFilepath(base) in registeredPaths
    }

    /** Fast path used by the file navigation listener */
    fun isRegisteredByRoot(pkgRoot: Path): Boolean = pkgRoot in registeredRoots

    // ─── Mutations ────────────────────────────────────────────────────────────

    fun setRegistered(pkg: FlutterPackage, registered: Boolean, withTests: Boolean = false) {
        launchIO {
            var writtenImlPath: Path? = null
            mutex.withLock {
                val base = projectBasePath ?: return@withLock
                val filepath = pkg.toFilepath(base)
                val paths = registeredPaths.toMutableSet()

                if (registered) {
                    // Write .iml before registering
                    val imlPath = pkg.path.resolve("${pkg.name}.iml")
                    atomicWrite(imlPath, ImlGenerator.buildContent(pkg, withTests))
                    writtenImlPath = imlPath
                    paths.add(filepath)
                } else {
                    paths.remove(filepath)
                }

                writeModulesXml(paths)
                registeredPaths = paths
                rebuildRegisteredRoots()

                // Persist test-inclusion state so it survives IDE restarts
                val settings = ModuleManagerSettings.getInstance(project)
                if (registered && withTests) settings.packagesWithTests.add(pkg.name)
                else settings.packagesWithTests.remove(pkg.name)
            }
            // Refresh both the .iml (content may have changed) and modules.xml
            writtenImlPath?.let { refreshVfs(it) }
            refreshVfs(modulesXmlPath)
            // Immediately sync with the IDE module system — don't wait for focus regain
            syncModuleWithIde(pkg, registered, writtenImlPath)
            notifyListeners()
        }
    }

    fun getPackageState(pkg: FlutterPackage): PackageState = when {
        !isRegistered(pkg) -> PackageState.NONE
        pkg.name in ModuleManagerSettings.getInstance(project).packagesWithTests -> PackageState.SRC_TEST
        else -> PackageState.SRC
    }

    fun clearAll() {
        launchIO {
            val packagesToClear = allPackages.filter { isRegistered(it) }
            mutex.withLock {
                writeModulesXml(emptySet())
                registeredPaths = emptySet()
                rebuildRegisteredRoots()
            }
            refreshVfs(modulesXmlPath)
            // Dispose all previously-registered modules from the IDE immediately
            ApplicationManager.getApplication().invokeLater {
                runWriteAction {
                    val mm = ModuleManager.getInstance(project)
                    for (pkg in packagesToClear) {
                        mm.findModuleByName(pkg.name)?.let { mm.disposeModule(it) }
                    }
                }
            }
            notifyListeners()
        }
    }

    /**
     * Full reload: re-resolves repo root, re-reads modules.xml, re-discovers packages.
     * Called when the user changes settings (packagesRootPath override).
     */
    fun reload() {
        launchIO {
            val newRoot = resolveRepoRoot()
            repoRoot = newRoot

            mutex.withLock { loadRegistered() }

            if (newRoot != null) {
                isDiscoveryComplete = false
                notifyListeners() // show "scanning…" state in UI
                allPackages = PackageDiscovery.discover(newRoot)
                mutex.withLock { rebuildRegisteredRoots() }
            }

            isDiscoveryComplete = true
            notifyListeners()
            EditorNotifications.getInstance(project).updateAllNotifications()
        }
    }

    fun launchIO(block: suspend CoroutineScope.() -> Unit) {
        scope.launch(Dispatchers.IO, block = block)
    }

    // ─── Write modules.xml ────────────────────────────────────────────────────

    private fun writeModulesXml(extraPaths: Set<String>) {
        val xmlPath = modulesXmlPath ?: return

        // Root entry always first, then sorted non-root entries
        val others = extraPaths
            .filter { it != rootModuleEntry }
            .sorted()
            .distinct()
        val all = listOf(rootModuleEntry) + others

        val moduleLines = all.joinToString("\n") {
            """      <module fileurl="file://$it" filepath="$it" />"""
        }

        val content = """<?xml version="1.0" encoding="UTF-8"?>
<project version="4">
  <component name="ProjectModuleManager">
    <modules>
$moduleLines
    </modules>
  </component>
</project>"""

        atomicWrite(xmlPath, content)
    }

    // ─── File I/O helpers ─────────────────────────────────────────────────────

    private fun atomicWrite(target: Path, content: String) {
        target.parent.toFile().mkdirs()
        val tmp = target.resolveSibling("${target.fileName}.tmp")
        tmp.toFile().writeText(content, Charsets.UTF_8)
        Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
    }

    private fun refreshVfs(path: Path?) {
        if (path == null) return
        // refreshAndFindFileByPath is synchronous and handles both cached and new files.
        // This ensures the IDE picks up the updated content immediately, whether the file
        // already existed in the VFS cache or was just created for the first time.
        LocalFileSystem.getInstance().refreshAndFindFileByPath(path.toString())
    }

    // ─── IDE module system sync ───────────────────────────────────────────────

    /**
     * Immediately add or remove the module from the IDE's live module list.
     * Without this, IntelliJ only picks up modules.xml changes on window focus regain.
     */
    private fun syncModuleWithIde(pkg: FlutterPackage, registered: Boolean, imlPath: Path?) {
        ApplicationManager.getApplication().invokeLater {
            runWriteAction {
                val mm = ModuleManager.getInstance(project)
                // Always dispose first: handles both first-add and .iml content changes
                // (loadModule throws if a module with the same name already exists)
                mm.findModuleByName(pkg.name)?.let { mm.disposeModule(it) }
                if (registered && imlPath != null) {
                    try {
                        mm.loadModule(imlPath.toAbsolutePath())
                    } catch (_: Exception) {
                        // modules.xml write is still valid; IDE will pick it up on next open
                    }
                }
            }
        }
    }

    // ─── Disposable ───────────────────────────────────────────────────────────

    override fun dispose() {
        scope.cancel()
    }

    companion object {
        fun getInstance(project: Project): ModuleManagerService = project.service()
    }
}
