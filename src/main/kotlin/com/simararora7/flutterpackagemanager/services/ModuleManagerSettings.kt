package com.simararora7.flutterpackagemanager.services

import com.intellij.openapi.components.*
import com.intellij.openapi.project.Project

/**
 * Project-level persistent settings. Stored in .idea/flutterModuleManager.xml.
 *
 * [packagesRootPath]: Override for the packages root directory.
 *   Leave blank to use auto-detection (FlutterRepoDetector).
 *
 * [packagesWithTests]: Names of registered packages whose .iml includes test/ as a test source root.
 *   Used to reconstruct SRC_TEST state on IDE restart without re-reading .iml files.
 */
@Service(Service.Level.PROJECT)
@State(
    name = "FlutterModuleManagerSettings",
    storages = [Storage("flutterModuleManager.xml")]
)
class ModuleManagerSettings : PersistentStateComponent<ModuleManagerSettings.State> {

    data class State(
        var packagesRootPath: String = "",
        var packagesWithTests: MutableSet<String> = mutableSetOf()
    )

    private var myState = State()

    override fun getState(): State = myState

    override fun loadState(state: State) {
        myState = state
    }

    var packagesRootPath: String
        get() = myState.packagesRootPath
        set(value) { myState.packagesRootPath = value }

    val packagesWithTests: MutableSet<String>
        get() = myState.packagesWithTests

    companion object {
        fun getInstance(project: Project): ModuleManagerSettings = project.service()
    }
}
