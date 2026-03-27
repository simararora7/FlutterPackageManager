package com.simararora7.flutterpackagemanager.toolwindow

import com.intellij.icons.AllIcons
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.Disposer
import com.simararora7.flutterpackagemanager.settings.ModuleManagerConfigurable
import com.intellij.psi.codeStyle.MinusculeMatcher
import com.intellij.psi.codeStyle.NameUtil
import com.intellij.ui.DocumentAdapter
import com.intellij.ui.ScrollPaneFactory
import com.intellij.ui.SearchTextField
import com.intellij.ui.components.JBList
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import com.intellij.ui.JBColor
import com.simararora7.flutterpackagemanager.model.FlutterPackage
import com.simararora7.flutterpackagemanager.model.PackageState
import com.simararora7.flutterpackagemanager.services.ModuleManagerService
import java.awt.BorderLayout
import java.awt.Component
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.util.concurrent.atomic.AtomicBoolean
import javax.swing.DefaultListModel
import javax.swing.JButton
import javax.swing.JCheckBox
import javax.swing.JLabel
import javax.swing.JList
import javax.swing.JPanel
import javax.swing.ListCellRenderer
import javax.swing.ListSelectionModel
import javax.swing.SwingConstants
import javax.swing.event.DocumentEvent

/**
 * The main Tool Window panel.
 *
 * Layout:
 *   ┌─ TOP BAR ──────────────────────────────────────────────────────────────┐
 *   │  [Search…]                    [☐ Registered only] [2 / 14] [🗑] [⚙] │
 *   └────────────────────────────────────────────────────────────────────────┘
 *   ┌─ HEADER ───────────────────────────────────────────────────────────────┐
 *   │  Package Name                                           Include Tests  │
 *   └────────────────────────────────────────────────────────────────────────┘
 *   ┌─ LIST ─────────────────────────────────────────────────────────────────┐
 *   │  ☑ auth_module                                              ☑ (Tests)  │
 *   │  ☑ home_module                                              ☐ (Tests)  │
 *   │  ☐ cart_module                                              ☐ (grey)   │
 *   └────────────────────────────────────────────────────────────────────────┘
 *
 * Left checkbox  = package registered (Src included).
 * Right checkbox = tests included; greyed out when package is not registered.
 * Clicking the right column toggles tests; clicking anywhere else toggles registration.
 */
class ModulesPanel(private val project: Project) : JPanel(BorderLayout()), Disposable {

    private val service = ModuleManagerService.getInstance(project)

    /** Width reserved for the "Include Tests" toggle column. */
    private val toggleColW = JBUI.scale(80)

    // ─── Search field ─────────────────────────────────────────────────────────

    private val searchField = SearchTextField(false).apply {
        textEditor.emptyText.text = "Search packages…"
        isFocusable = true
    }

    // ─── Only-enabled filter ──────────────────────────────────────────────────

    private val onlyEnabledCheckbox = JCheckBox("Registered only").apply {
        isOpaque = false
        font = JBUI.Fonts.smallFont()
        addActionListener { applyFilter(searchField.text) }
    }

    // ─── Count label + Clear button ───────────────────────────────────────────

    private val countLabel = JLabel("…").apply {
        foreground = UIUtil.getContextHelpForeground()
        font = JBUI.Fonts.smallFont()
    }

    private val settingsButton = iconButton(AllIcons.General.Settings, "Open Flutter Package Manager settings") {
        // Class-based lookup fails in packaged plugins due to classloader isolation; use ID instead.
        ShowSettingsUtil.getInstance().showSettingsDialog(project, "com.simararora7.flutter-package-manager")
    }

    private val clearButton = iconButton(AllIcons.Actions.GC, "Unregister all packages from modules.xml") {
        onClear()
    }

    // ─── Package list ─────────────────────────────────────────────────────────

    private val listModel = DefaultListModel<FlutterPackage>()

    private val packageList = JBList(listModel).apply {
        emptyText.text = "No packages found"
        cellRenderer = PackageStateRenderer()
        selectionMode = ListSelectionModel.SINGLE_SELECTION
        addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) {
                val index = locationToIndex(e.point)
                if (index < 0 || index >= listModel.size) return
                val pkg = listModel.getElementAt(index)
                val cellBounds = getCellBounds(index, index) ?: return
                val relX = e.point.x - cellBounds.x
                val state = service.getPackageState(pkg)

                if (relX > cellBounds.width - toggleColW) {
                    // Right column: toggle tests.
                    // If package isn't registered yet, clicking test registers both src + test.
                    if (state == PackageState.NONE) {
                        service.setRegistered(pkg, registered = true, withTests = true)
                    } else {
                        service.setRegistered(pkg, registered = true, withTests = state != PackageState.SRC_TEST)
                    }
                } else {
                    // Left/center: toggle registration; preserve test-state for re-enable
                    val registered = state != PackageState.NONE
                    service.setRegistered(pkg, registered = !registered, withTests = state == PackageState.SRC_TEST)
                }
            }
        })
    }

    // ─── Change listener ──────────────────────────────────────────────────────

    private val refreshPending = AtomicBoolean(false)

    private val changeListener: () -> Unit = {
        if (refreshPending.compareAndSet(false, true)) {
            ApplicationManager.getApplication().invokeLater {
                refreshPending.set(false)
                refresh()
            }
        }
    }

    // ─── Init ─────────────────────────────────────────────────────────────────

    init {
        // Top bar: [Search…]           [☐ Registered only] [2/14] [🗑] [⚙]
        val rightCluster = JPanel(FlowLayout(FlowLayout.RIGHT, 4, 0)).apply {
            isOpaque = false
            add(onlyEnabledCheckbox)
            add(countLabel)
            add(clearButton)
            add(settingsButton)
        }
        val topBar = JPanel(BorderLayout()).apply {
            border = JBUI.Borders.empty(4, 6, 4, 6)
            add(searchField, BorderLayout.CENTER)
            add(rightCluster, BorderLayout.EAST)
        }

        // Column header sits between the top bar and the scrollable list
        val topSection = JPanel(BorderLayout()).apply {
            add(topBar, BorderLayout.NORTH)
            add(buildHeaderPanel(), BorderLayout.SOUTH)
        }

        add(topSection, BorderLayout.NORTH)
        add(ScrollPaneFactory.createScrollPane(packageList), BorderLayout.CENTER)

        searchField.addDocumentListener(object : DocumentAdapter() {
            override fun textChanged(e: DocumentEvent) { applyFilter(searchField.text) }
        })

        service.addChangeListener(changeListener)
        Disposer.register(this, Disposable { service.removeChangeListener(changeListener) })

        refresh()
    }

    private fun buildHeaderPanel(): JPanel = JPanel(BorderLayout()).apply {
        border = JBUI.Borders.compound(
            JBUI.Borders.customLine(JBColor.border(), 0, 0, 1, 0),
            JBUI.Borders.empty(2, 4)
        )
        val dimFg = UIUtil.getContextHelpForeground()
        val smallFont = JBUI.Fonts.smallFont()

        // Left indent: panel border (4px) + srcCheckBox width (24px) + nameLabel border (2px) = 30px
        add(JLabel("Package Name").apply {
            border = JBUI.Borders.emptyLeft(30)
            font = smallFont
            foreground = dimFg
        }, BorderLayout.CENTER)

        add(JLabel("Include Tests").apply {
            preferredSize = Dimension(toggleColW, 0)
            font = smallFont
            foreground = dimFg
            horizontalAlignment = SwingConstants.CENTER
        }, BorderLayout.EAST)
    }

    // ─── Filter & refresh ─────────────────────────────────────────────────────

    private fun applyFilter(query: String) {
        val all = service.allPackages
        val matcher: MinusculeMatcher? = if (query.isBlank()) null else {
            try { NameUtil.buildMatcher("*$query", NameUtil.MatchingCaseSensitivity.NONE) }
            catch (_: Exception) { null }
        }

        var filtered = if (matcher == null) all else all.filter { matcher.matches(it.name) }
        if (onlyEnabledCheckbox.isSelected) {
            filtered = filtered.filter { service.getPackageState(it) != PackageState.NONE }
        }

        val sorted = filtered.sortedBy { it.name }

        listModel.clear()
        sorted.forEach { listModel.addElement(it) }

        updateCountLabel()
    }

    private fun refresh() { applyFilter(searchField.text) }

    private fun updateCountLabel() {
        val total = service.allPackages.size
        val registered = service.allPackages.count { service.isRegistered(it) }
        countLabel.text = when {
            !service.isDiscoveryComplete -> "…"
            else -> "$registered / $total"
        }
    }

    // ─── Clear action ─────────────────────────────────────────────────────────

    private fun onClear() {
        val confirm = Messages.showYesNoDialog(
            project,
            "Remove all packages from modules.xml (except the root entry)?",
            "Clear All Packages",
            Messages.getWarningIcon()
        )
        if (confirm == Messages.YES) service.clearAll()
    }

    override fun dispose() { /* listener removal handled by Disposer */ }

    // ─── Cell renderer ────────────────────────────────────────────────────────

    private inner class PackageStateRenderer : ListCellRenderer<FlutterPackage> {

        private val panel = JPanel(BorderLayout()).apply { isOpaque = true }

        private val srcCheckBox = JCheckBox().apply {
            isOpaque = false
            preferredSize = Dimension(JBUI.scale(24), 0)
        }

        private val nameLabel = JLabel().apply {
            border = JBUI.Borders.emptyLeft(2)
        }

        private val testCheckBox = JCheckBox().apply {
            isOpaque = false
            preferredSize = Dimension(toggleColW, 0)
            horizontalAlignment = SwingConstants.CENTER
        }

        init {
            panel.border = JBUI.Borders.empty(2, 4)
            panel.add(srcCheckBox, BorderLayout.WEST)
            panel.add(nameLabel, BorderLayout.CENTER)
            panel.add(testCheckBox, BorderLayout.EAST)
        }

        override fun getListCellRendererComponent(
            list: JList<out FlutterPackage>,
            value: FlutterPackage,
            index: Int,
            isSelected: Boolean,
            cellHasFocus: Boolean
        ): Component {
            val state = service.getPackageState(value)
            val isRegistered = state != PackageState.NONE

            val bg = if (isSelected) list.selectionBackground else list.background
            val fg = if (isSelected) list.selectionForeground else list.foreground

            panel.background = bg

            nameLabel.text = value.name
            nameLabel.foreground = fg

            srcCheckBox.isSelected = isRegistered
            srcCheckBox.isEnabled = true
            srcCheckBox.background = bg

            testCheckBox.isSelected = state == PackageState.SRC_TEST
            testCheckBox.isEnabled = isRegistered
            testCheckBox.background = bg

            return panel
        }
    }
}

private fun iconButton(icon: javax.swing.Icon, tooltip: String, action: () -> Unit) =
    JButton(icon).apply {
        toolTipText = tooltip
        isBorderPainted = false
        isContentAreaFilled = false
        preferredSize = Dimension(JBUI.scale(22), JBUI.scale(22))
        addActionListener { action() }
    }
