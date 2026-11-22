package app.hercules.store.ui.installed

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.hercules.store.data.ModuleRepository
import app.hercules.store.data.model.InstalledModule
import app.hercules.store.data.model.ModuleItem
import app.hercules.store.root.RootExecutor
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class InstalledEntry(val module: InstalledModule, val update: ModuleItem?)

data class InstalledUiState(
    val updates: List<InstalledEntry> = emptyList(),
    val upToDate: List<InstalledEntry> = emptyList(),
    val loading: Boolean = false,
    val rootAvailable: Boolean? = null
)

private data class InstalledLoad(
    val modules: List<InstalledModule> = emptyList(),
    val loading: Boolean = false,
    val rootAvailable: Boolean? = null
)

class InstalledViewModel(
    repository: ModuleRepository,
    private val root: RootExecutor
) : ViewModel() {
    private val installed = MutableStateFlow(InstalledLoad())

    val state: StateFlow<InstalledUiState> =
        combine(installed, repository.catalog) { load, catalog ->
            val entries = load.modules.map { module ->
                InstalledEntry(
                    module = module,
                    update = catalog.modules.firstOrNull { it.id == module.id && it.versionCode > module.versionCode }
                )
            }
            InstalledUiState(
                updates = entries.filter { it.update != null }.sortedBy { it.module.name.lowercase() },
                upToDate = entries.filter { it.update == null }.sortedBy { it.module.name.lowercase() },
                loading = load.loading,
                rootAvailable = load.rootAvailable
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), InstalledUiState())

    fun refresh() {
        if (installed.value.loading) return
        installed.update { it.copy(loading = true) }
        viewModelScope.launch {
            val available = root.isRootAvailable()
            val modules = if (available) root.listInstalledModules() else emptyList()
            installed.value = InstalledLoad(modules, loading = false, rootAvailable = available)
        }
    }
}
