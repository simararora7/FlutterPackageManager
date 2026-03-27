package com.simararora7.flutterpackagemanager.notifications

import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.EditorNotificationPanel
import com.intellij.ui.EditorNotificationProvider
import com.intellij.ui.EditorNotifications
import com.simararora7.flutterpackagemanager.services.ModuleManagerService
import java.util.function.Function
import javax.swing.JComponent

/**
 * Shows an editor notification strip at the top of the editor when the opened
 * file belongs to a Flutter package that is not yet registered in modules.xml.
 *
 * Performance: all checks are in-memory (no disk I/O).
 *   - findPackageRoot(): walks VirtualFile parent chain — O(file depth)
 *   - repoRoot / isRegisteredByRoot(): reads @Volatile snapshots — no lock
 */
class UnregisteredPackageNotificationProvider : EditorNotificationProvider {

    override fun collectNotificationData(
        project: Project,
        file: VirtualFile
    ): Function<in FileEditor, out JComponent?>? {
        val svc = try {
            ModuleManagerService.getInstance(project)
        } catch (_: Exception) {
            return null
        }

        if (!svc.isDiscoveryComplete) return null

        val pkgRoot = findPackageRoot(file) ?: return null

        val pkgRootPath = try { pkgRoot.toNioPath() } catch (_: Exception) { return null }

        val repoRoot = svc.repoRoot ?: return null
        if (!pkgRootPath.startsWith(repoRoot)) return null

        if (svc.isRegisteredByRoot(pkgRootPath)) return null

        val pkg = svc.allPackages.find { it.path == pkgRootPath }
        val pkgName = pkgRoot.name

        return Function { _ ->
            EditorNotificationPanel().apply {
                text = "$pkgName is not registered in modules.xml"
                if (pkg != null) {
                    createActionLabel("Add (src)") {
                        svc.setRegistered(pkg, true, withTests = false)
                        EditorNotifications.getInstance(project).updateNotifications(file)
                    }
                    createActionLabel("Add (src+test)") {
                        svc.setRegistered(pkg, true, withTests = true)
                        EditorNotifications.getInstance(project).updateNotifications(file)
                    }
                }
            }
        }
    }

    private fun findPackageRoot(file: VirtualFile): VirtualFile? {
        var dir: VirtualFile? = if (file.isDirectory) file else file.parent
        while (dir != null) {
            if (dir.findChild("pubspec.yaml") != null) return dir
            dir = dir.parent
        }
        return null
    }
}
