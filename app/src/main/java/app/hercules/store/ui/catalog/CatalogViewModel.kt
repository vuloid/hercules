package app.hercules.store.ui.catalog

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.hercules.store.R
import app.hercules.store.data.GithubUrl
import app.hercules.store.data.InstalledSnapshot
import app.hercules.store.data.InstalledStore
import app.hercules.store.data.ModuleRepository
import app.hercules.store.data.model.ModuleItem
import app.hercules.store.data.model.RootManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

enum class RootFilter(@StringRes val label: Int) {
    ALL(R.string.filter_all),
    MAGISK(R.string.filter_magisk),
    KERNELSU(R.string.filter_kernelsu),
    APATCH(R.string.filter_apatch),
    LSPOSED(R.string.filter_lsposed);

    fun matches(module: ModuleItem): Boolean = when (this) {
        ALL -> true
        MAGISK -> RootManager.MAGISK in module.managers
        KERNELSU -> RootManager.KERNELSU in module.managers
        APATCH -> RootManager.APATCH in module.managers
        LSPOSED -> module.lsposed
    }
}

enum class SortOrder(@StringRes val label: Int) {
    RECENT(R.string.sort_recent),
    STARS(R.string.sort_stars)
}

sealed interface GithubLookup {
    data object Idle : GithubLookup
    data object Loading : GithubLookup
    data class Found(val module: ModuleItem) : GithubLookup
    data class Failed(val message: String?) : GithubLookup
}

data class CatalogUiState(
    val query: String = "",
    val rootFilter: RootFilter = RootFilter.ALL,
    val sort: SortOrder = SortOrder.RECENT,
    val hideOutdated: Boolean = false,
    val modules: List<ModuleItem> = emptyList(),
    val loading: Boolean = false,
    val error: String? = null,
    val lookup: GithubLookup = GithubLookup.Idle,
    val selected: ModuleItem? = null,
    val installed: InstalledSnapshot = InstalledSnapshot(),
    val stack: CuratedStack? = null,
    val featured: List<ModuleItem> = emptyList(),
    val recentlyUpdated: List<ModuleItem> = emptyList()
)

private data class Filters(
    val query: String = "",
    val root: RootFilter = RootFilter.ALL,
    val sort: SortOrder = SortOrder.RECENT,
    val hideOutdated: Boolean = false,
    val stack: CuratedStack? = null
)

class CatalogViewModel(
    private val repository: ModuleRepository,
    private val installedStore: InstalledStore
) : ViewModel() {
    private val filters = MutableStateFlow(Filters())
    private val lookup = MutableStateFlow<GithubLookup>(GithubLookup.Idle)
    private val selected = MutableStateFlow<ModuleItem?>(null)
    private var lookupJob: Job? = null

    val state: StateFlow<CatalogUiState> =
        combine(repository.catalog, filters, lookup, selected, installedStore.state) { catalog, f, l, s, installed ->
            CatalogUiState(
                query = f.query,
                rootFilter = f.root,
                sort = f.sort,
                hideOutdated = f.hideOutdated,
                modules = applyFilters(catalog.modules, f),
                loading = catalog.loading,
                error = catalog.error,
                lookup = l,
                selected = s,
                installed = installed,
                stack = f.stack,
                featured = pickFeatured(catalog.modules),
                recentlyUpdated = catalog.modules.sortedByDescending { it.updatedAt }.take(8)
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CatalogUiState(loading = true))

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch { repository.refresh() }
    }

    fun refreshInstalled() {
        viewModelScope.launch { installedStore.refresh() }
    }

    fun onQueryChange(value: String) {
        filters.update { it.copy(query = value) }
        lookupJob?.cancel()
        val target = GithubUrl.parse(value)
        if (target == null) {
            lookup.value = GithubLookup.Idle
            return
        }
        lookup.value = GithubLookup.Loading
        lookupJob = viewModelScope.launch {
            lookup.value = try {
                GithubLookup.Found(repository.lookupGithub(target.first, target.second))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                GithubLookup.Failed(e.message)
            }
        }
    }

    fun onRootFilter(value: RootFilter) = filters.update { it.copy(root = value) }

    fun onSort(value: SortOrder) = filters.update { it.copy(sort = value) }

    fun onHideOutdated(value: Boolean) = filters.update { it.copy(hideOutdated = value) }

    fun onStack(value: CuratedStack) = filters.update {
        it.copy(stack = if (it.stack == value) null else value)
    }

    fun select(module: ModuleItem?) {
        selected.value = module
    }

    private fun applyFilters(modules: List<ModuleItem>, f: Filters): List<ModuleItem> {
        val query = f.query.trim()
        val isUrl = GithubUrl.parse(query) != null
        val stack = f.stack.takeIf { query.isEmpty() }
        val cutoff = System.currentTimeMillis() - OUTDATED_MS
        val filtered = modules.filter { module ->
            f.root.matches(module) &&
                (stack?.matches(module) ?: true) &&
                (!f.hideOutdated || module.updatedAt >= cutoff) &&
                when {
                    query.isEmpty() -> true
                    isUrl -> module.repoUrl.trimEnd('/').equals(query.trimEnd('/'), ignoreCase = true)
                    else -> module.name.contains(query, ignoreCase = true) ||
                        module.author.contains(query, ignoreCase = true) ||
                        module.description.contains(query, ignoreCase = true)
                }
        }
        return when {
            stack?.ranksByStars == true -> filtered.sortedByDescending { it.stars }
            f.sort == SortOrder.RECENT -> filtered.sortedByDescending { it.updatedAt }
            else -> filtered.sortedByDescending { it.stars }
        }
    }

    private companion object {
        val OUTDATED_MS = TimeUnit.DAYS.toMillis(365)
    }
}
