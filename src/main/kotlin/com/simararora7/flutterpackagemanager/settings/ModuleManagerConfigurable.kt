package com.simararora7.flutterpackagemanager.settings

import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.FormBuilder
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import com.simararora7.flutterpackagemanager.services.ModuleManagerService
import com.simararora7.flutterpackagemanager.services.ModuleManagerSettings
import com.simararora7.flutterpackagemanager.util.FlutterRepoDetector
import javax.swing.JComponent
import javax.swing.JPanel

/**
 * Settings page: Settings → Tools → Flutter Package Manager
 */
class ModuleManagerConfigurable(private val project: Project) : Configurable {

    private var pathField: TextFieldWithBrowseButton? = null

    override fun getDisplayName(): String = "Flutter Package Manager"

    override fun createComponent(): JComponent {
        val settings = ModuleManagerSettings.getInstance(project)
        val detected = FlutterRepoDetector.detect(project)?.toString()

        val field = TextFieldWithBrowseButton().apply {
            addBrowseFolderListener(
                "Select Packages Root Directory",
                "Point to the monorepo root (the parent of the 'packages/' folder).",
                project,
                FileChooserDescriptorFactory.createSingleFolderDescriptor()
            )
            text = settings.packagesRootPath.ifBlank { detected ?: "" }
        }
        pathField = field

        val hint = JBLabel(
            if (detected != null) "Leave empty to auto-detect. Auto-detected: $detected"
            else "Auto-detection failed — set the path manually."
        ).apply {
            foreground = UIUtil.getContextHelpForeground()
            font = JBUI.Fonts.smallFont()
        }

        return FormBuilder.createFormBuilder()
            .addLabeledComponent("Packages root:", field)
            .addComponent(hint)
            .addComponentFillVertically(JPanel(), 0)
            .panel
    }

    override fun isModified(): Boolean {
        val settings = ModuleManagerSettings.getInstance(project)
        val detected = FlutterRepoDetector.detect(project)?.toString() ?: ""
        val currentField = pathField?.text?.trim() ?: ""
        val savedPath = settings.packagesRootPath
        return currentField != savedPath && !(savedPath.isBlank() && currentField == detected)
    }

    override fun apply() {
        val settings = ModuleManagerSettings.getInstance(project)
        val detected = FlutterRepoDetector.detect(project)?.toString() ?: ""
        val fieldValue = pathField?.text?.trim() ?: ""
        settings.packagesRootPath = if (fieldValue == detected) "" else fieldValue
        ModuleManagerService.getInstance(project).reload()
    }

    override fun reset() {
        val settings = ModuleManagerSettings.getInstance(project)
        val detected = FlutterRepoDetector.detect(project)?.toString() ?: ""
        pathField?.text = settings.packagesRootPath.ifBlank { detected }
    }

    override fun disposeUIResources() {
        pathField = null
    }
}
