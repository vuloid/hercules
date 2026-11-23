package app.hercules.store.ui.installed

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.hercules.store.data.InstalledStore
import app.hercules.store.data.ModuleRepository
import app.hercules.store.data.findForInstalled
import app.hercules.store.data.model.InstalledModule
import app.hercules.store.data.model.ModuleItem
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class InstalledEntry(val module: InstalledModule, val update: ModuleItem?)

data class InstalledUiState(
    val updates: List<InstalledEntry> = emptyList(),
    val upToDate: List<InstalledEntry> = emptyList(),
    val loading: Boolean = false,
    val rootAvailable: Boolean? = null
)

class InstalledViewModel(
    repository: ModuleRepository,
    private val store: InstalledStore
) : ViewModel() {
    val state: StateFlow<InstalledUiState> =
        combine(store.state, repository.catalog) { load, catalog ->
            val entries = load.withPackages(catalog.modules).map { module ->
                InstalledEntry(
                    module = module,
                    update = catalog.modules.findForInstalled(module.id)?.takeIf { it.versionCode > module.versionCode }
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
        viewModelScope.launch { store.refresh() }
    }
}
