package app.hercules.store.ui.status

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.hercules.store.data.InstalledStore
import app.hercules.store.data.model.RootManager
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

data class RootStatus(
    val rootAvailable: Boolean? = null,
    val manager: RootManager? = null,
    val activeModules: Int = 0
)

class RootStatusViewModel(store: InstalledStore) : ViewModel() {
    val status: StateFlow<RootStatus> = store.state
        .map { RootStatus(it.rootAvailable, it.manager, it.modules.count { module -> module.enabled }) }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), RootStatus())
}
