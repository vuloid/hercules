package app.hercules.store.ui.details

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.hercules.store.data.ModuleRepository
import app.hercules.store.data.model.ModuleItem
import app.hercules.store.data.model.ScriptReport
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface ScriptState {
    data object Idle : ScriptState
    data object Loading : ScriptState
    data class Ready(val report: ScriptReport) : ScriptState
    data class Failed(val message: String?) : ScriptState
}

class DetailsViewModel(private val repository: ModuleRepository) : ViewModel() {
    private val _scripts = MutableStateFlow<ScriptState>(ScriptState.Idle)
    val scripts: StateFlow<ScriptState> = _scripts.asStateFlow()

    fun inspect(module: ModuleItem) {
        if (_scripts.value is ScriptState.Loading || _scripts.value is ScriptState.Ready) return
        _scripts.value = ScriptState.Loading
        viewModelScope.launch {
            _scripts.value = try {
                ScriptState.Ready(repository.inspectScripts(module))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                ScriptState.Failed(e.message)
            }
        }
    }
}
