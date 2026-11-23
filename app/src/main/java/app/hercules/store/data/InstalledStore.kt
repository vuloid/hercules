package app.hercules.store.data

import app.hercules.store.data.model.InstalledModule
import app.hercules.store.data.model.ModuleItem
import app.hercules.store.data.model.RootManager
import app.hercules.store.root.RootExecutor
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

enum class InstallStatus { NOT_INSTALLED, INSTALLED, UPDATE_AVAILABLE }

fun normalizeId(id: String): String = id.trim().lowercase().replace('-', '_')

fun List<ModuleItem>.findForInstalled(id: String): ModuleItem? {
    firstOrNull { it.id == id }?.let { return it }
    val normalized = normalizeId(id)
    return firstOrNull { normalizeId(it.id) == normalized }
}

data class InstalledSnapshot(
    val modules: List<InstalledModule> = emptyList(),
    val packages: Map<String, Int> = emptyMap(),
    val loading: Boolean = false,
    val rootAvailable: Boolean? = null,
    val manager: RootManager? = null
) {
    private val modulesById by lazy { modules.associateBy { it.id } }
    private val modulesByNormalized by lazy { modules.reversed().associateBy { normalizeId(it.id) } }
    private val packagesByNormalized by lazy {
        packages.entries.reversed().associate { normalizeId(it.key) to it.value }
    }

    fun installedVersionCode(item: ModuleItem): Int? {
        val module = modulesById[item.id] ?: modulesByNormalized[normalizeId(item.id)]
        if (module != null) return module.versionCode
        if (!item.isApk) return null
        return packages[item.id] ?: packagesByNormalized[normalizeId(item.id)]
    }

    fun status(item: ModuleItem): InstallStatus {
        val installed = installedVersionCode(item) ?: return InstallStatus.NOT_INSTALLED
        return if (item.versionCode > installed) InstallStatus.UPDATE_AVAILABLE else InstallStatus.INSTALLED
    }

    fun withPackages(catalog: List<ModuleItem>): List<InstalledModule> {
        val known = modules.map { normalizeId(it.id) }.toSet()
        val apks = catalog.asSequence()
            .filter { it.isApk && normalizeId(it.id) !in known }
            .mapNotNull { item ->
                val code = packages[item.id] ?: packagesByNormalized[normalizeId(item.id)]
                code?.let {
                    InstalledModule(
                        id = item.id,
                        name = item.name,
                        version = "",
                        versionCode = it,
                        author = item.author,
                        description = item.description,
                        enabled = true
                    )
                }
            }
        return modules + apks
    }
}

class InstalledStore(private val root: RootExecutor) {
    private val mutex = Mutex()
    private val _state = MutableStateFlow(InstalledSnapshot())
    val state: StateFlow<InstalledSnapshot> = _state.asStateFlow()

    suspend fun refresh() {
        mutex.withLock { load() }
    }

    private suspend fun load() {
        _state.update { it.copy(loading = true) }
        try {
            val available = root.isRootAvailable()
            val modules = if (available) root.listInstalledModules() else emptyList()
            val packages = if (available) root.listInstalledPackages() else emptyMap()
            val manager = if (available) root.detectManager() else null
            _state.value = InstalledSnapshot(
                modules = modules,
                packages = packages,
                loading = false,
                rootAvailable = available,
                manager = manager
            )
        } finally {
            _state.update { it.copy(loading = false) }
        }
    }
}
