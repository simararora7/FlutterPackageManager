package com.simararora7.flutterpackagemanager.statusbar

import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.StatusBar
import com.intellij.openapi.wm.StatusBarWidget
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.openapi.wm.WindowManager
import com.simararora7.flutterpackagemanager.services.ModuleManagerService
import com.intellij.util.Consumer
import java.awt.Component
import java.awt.event.MouseEvent

/**
 * Status bar widget showing "Packages: X / Y" in the bottom bar.
 * Clicking opens the Flutter Packages tool window.
 */
class ModuleStatusBarWidget(private val project: Project) :
    StatusBarWidget, StatusBarWidget.TextPresentation {

    private var myStatusBar: StatusBar? = null

    // Change listener stored for removal on dispose
    private val changeListener: () -> Unit = { myStatusBar?.updateWidget(ID()) }

    override fun ID(): String = WIDGET_ID

    override fun getPresentation(): StatusBarWidget.WidgetPresentation = this

    override fun install(statusBar: StatusBar) {
        myStatusBar = statusBar
        ModuleManagerService.getInstance(project).addChangeListener(changeListener)
    }

    override fun dispose() {
        try {
            ModuleManagerService.getInstance(project).removeChangeListener(changeListener)
        } catch (_: Exception) { /* project may already be disposed */ }
        myStatusBar = null
    }

    // ─── TextPresentation ─────────────────────────────────────────────────────

    override fun getText(): String {
        return try {
            val svc = ModuleManagerService.getInstance(project)
            if (!svc.isDiscoveryComplete) {
                "Packages: …"
            } else {
                val registered = svc.allPackages.count { svc.isRegistered(it) }
                "Packages: $registered/${svc.allPackages.size}"
            }
        } catch (_: Exception) {
            "Packages"
        }
    }

    override fun getTooltipText(): String = "Flutter Package Manager — click to open"

    override fun getAlignment(): Float = Component.CENTER_ALIGNMENT

    override fun getClickConsumer(): Consumer<MouseEvent>? = Consumer {
        ToolWindowManager.getInstance(project).getToolWindow("Flutter Packages")?.show()
    }

    companion object {
        const val WIDGET_ID = "FlutterModuleManager"

        /** Called by ModuleManagerService.notifyListeners via addChangeListener */
        fun updateFor(project: Project) {
            val bar = WindowManager.getInstance().getStatusBar(project) ?: return
            bar.updateWidget(WIDGET_ID)
        }
    }
}
