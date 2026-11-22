package app.hercules.store.ui.install

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.hercules.store.R
import app.hercules.store.data.ModuleRepository
import app.hercules.store.data.model.DownloadState
import app.hercules.store.data.model.ModuleItem
import app.hercules.store.root.RootExecutor
import app.hercules.store.root.ShellEvent
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File

enum class InstallPhase(@StringRes val label: Int) {
    DOWNLOADING(R.string.phase_downloading),
    INSTALLING(R.string.phase_installing),
    SUCCESS(R.string.phase_success),
    FAILED(R.string.phase_failed)
}

sealed interface LogEntry {
    data class Shell(val text: String, val isError: Boolean) : LogEntry
    data class System(@StringRes val res: Int, val args: List<String> = emptyList()) : LogEntry
}

data class InstallUiState(
    val module: ModuleItem,
    val phase: InstallPhase = InstallPhase.DOWNLOADING,
    val progress: Float? = null,
    val log: List<LogEntry> = emptyList()
) {
    val running: Boolean
        get() = phase == InstallPhase.DOWNLOADING || phase == InstallPhase.INSTALLING
}

class InstallViewModel(
    private val repository: ModuleRepository,
    private val root: RootExecutor
) : ViewModel() {
    private val _state = MutableStateFlow<InstallUiState?>(null)
    val state: StateFlow<InstallUiState?> = _state.asStateFlow()
    private var job: Job? = null

    fun install(module: ModuleItem) {
        if (job?.isActive == true) return
        _state.value = InstallUiState(module)
        job = viewModelScope.launch { run(module) }
    }

    fun dismiss() {
        if (_state.value?.running == true) return
        _state.value = null
    }

    fun reboot() {
        viewModelScope.launch { root.reboot() }
    }

    private suspend fun run(module: ModuleItem) {
        log(LogEntry.System(R.string.log_downloading, listOf(module.name)))
        val file = download(module) ?: return
        log(LogEntry.System(R.string.log_downloaded))

        val manager = root.detectManager()
        if (manager == null) {
            fail(LogEntry.System(R.string.log_no_root))
            return
        }
        if (module.managers.isNotEmpty() && manager !in module.managers) {
            log(LogEntry.System(R.string.log_manager_mismatch, listOf(manager.displayName)))
        }

        _state.update { it?.copy(phase = InstallPhase.INSTALLING, progress = null) }
        log(LogEntry.System(R.string.log_installing_with, listOf(manager.displayName)))

        var exitCode = -1
        root.installModule(manager, file).collect { event ->
            when (event) {
                is ShellEvent.Line -> log(LogEntry.Shell(event.text, event.isError))
                is ShellEvent.Exit -> exitCode = event.code
            }
        }
        if (exitCode == 0) {
            _state.update { s -> s?.copy(phase = InstallPhase.SUCCESS, progress = null) }
            log(LogEntry.System(R.string.log_success))
        } else {
            fail(LogEntry.System(R.string.log_exit_code, listOf(exitCode.toString())))
        }
    }

    private suspend fun download(module: ModuleItem): File? {
        var result: File? = null
        repository.download(module).collect { event ->
            when (event) {
                is DownloadState.Progress -> _state.update { s ->
                    s?.copy(progress = if (event.total > 0) event.bytes.toFloat() / event.total else null)
                }
                is DownloadState.Done -> result = event.file
                is DownloadState.Failed -> fail(
                    if (event.reason == null) {
                        LogEntry.System(R.string.log_download_failed_generic)
                    } else {
                        LogEntry.System(R.string.log_download_failed, listOf(event.reason))
                    }
                )
            }
        }
        return result
    }

    private fun log(entry: LogEntry) {
        _state.update { s -> s?.copy(log = (s.log + entry).takeLast(MAX_LOG)) }
    }

    private fun fail(entry: LogEntry) {
        _state.update { s -> s?.copy(phase = InstallPhase.FAILED, progress = null, log = (s.log + entry).takeLast(MAX_LOG)) }
    }

    private companion object {
        const val MAX_LOG = 1000
    }
}
