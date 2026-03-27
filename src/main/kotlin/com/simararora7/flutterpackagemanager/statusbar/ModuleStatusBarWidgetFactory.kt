package com.simararora7.flutterpackagemanager.statusbar

import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.StatusBar
import com.intellij.openapi.wm.StatusBarWidget
import com.intellij.openapi.wm.StatusBarWidgetFactory

class ModuleStatusBarWidgetFactory : StatusBarWidgetFactory {

    override fun getId(): String = ModuleStatusBarWidget.WIDGET_ID

    override fun getDisplayName(): String = "Flutter Package Manager"

    override fun isAvailable(project: Project): Boolean = project.basePath != null

    override fun createWidget(project: Project): StatusBarWidget =
        ModuleStatusBarWidget(project)

    override fun disposeWidget(widget: StatusBarWidget) {
        widget.dispose()
    }

    override fun canBeEnabledOn(statusBar: StatusBar): Boolean = true
}
