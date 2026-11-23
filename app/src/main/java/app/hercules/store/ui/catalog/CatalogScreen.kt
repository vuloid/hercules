package app.hercules.store.ui.catalog

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.hercules.store.R
import app.hercules.store.data.model.ModuleItem
import app.hercules.store.ui.HerculesViewModelFactory
import app.hercules.store.ui.components.HerculesCard
import app.hercules.store.ui.components.HerculesShapes
import app.hercules.store.ui.components.SectionHeader
import app.hercules.store.ui.components.systemNavigationBottomInset
import app.hercules.store.ui.details.ModuleDetailsSheet
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CatalogScreen(
    onInstall: (ModuleItem) -> Unit,
    onBrowse: () -> Unit = {},
    featuredMode: Boolean = false,
    modifier: Modifier = Modifier,
    hazeState: HazeState? = null,
    liquidGlassEnabled: Boolean = true,
    viewModel: CatalogViewModel = viewModel(factory = HerculesViewModelFactory)
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refreshInstalled() }

    Column(modifier.fillMaxSize()) {
        if (!featuredMode) {
            SearchField(query = state.query, onChange = viewModel::onQueryChange)
            FilterRow(
                rootFilter = state.rootFilter,
                sort = state.sort,
                hideOutdated = state.hideOutdated,
                onRootFilter = viewModel::onRootFilter,
                onSort = viewModel::onSort,
                onHideOutdated = viewModel::onHideOutdated
            )
            state.stack?.let { selectedStack ->
                CuratedStacks(selected = selectedStack, onSelect = viewModel::onStack)
            }
        }
        PullToRefreshBox(
            isRefreshing = state.loading,
            onRefresh = viewModel::refresh,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            if (featuredMode) {
                FeaturedLanding(
                    state = state,
                    onOpen = viewModel::select,
                    onInstall = onInstall,
                    onSelectCategory = viewModel::onStack,
                    onBrowse = onBrowse,
                    hazeState = hazeState,
                    liquidGlassEnabled = liquidGlassEnabled
                )
            } else {
                CatalogList(
                    state = state,
                    onOpen = viewModel::select,
                    onInstall = onInstall,
                    onRetry = viewModel::refresh,
                    hazeState = hazeState,
                    liquidGlassEnabled = liquidGlassEnabled
                )
            }
        }
    }

    state.selected?.let { module ->
        ModuleDetailsSheet(
            module = module,
            status = state.installed.status(module),
            onDismiss = { viewModel.select(null) },
            hazeState = hazeState,
            liquidGlassEnabled = liquidGlassEnabled,
            onInstall = {
                viewModel.select(null)
                onInstall(module)
            }
        )
    }
}

@Composable
private fun CatalogList(
    state: CatalogUiState,
    onOpen: (ModuleItem) -> Unit,
    onInstall: (ModuleItem) -> Unit,
    onRetry: () -> Unit,
    hazeState: HazeState?,
    liquidGlassEnabled: Boolean
) {
    val inset = Modifier.padding(horizontal = 12.dp)
    LazyColumn(
        modifier = Modifier.fillMaxSize().let { if (liquidGlassEnabled && hazeState != null) it.hazeSource(state = hazeState) else it },
        contentPadding = PaddingValues(top = 8.dp, bottom = 12.dp + systemNavigationBottomInset()),
        verticalArrangement = Arrangement.spacedBy(0.dp)
    ) {
        when (val lookup = state.lookup) {
            GithubLookup.Idle -> Unit
            GithubLookup.Loading -> item(key = "lookup") {
                LoadingRow(stringResource(R.string.github_resolving), inset)
            }
            is GithubLookup.Found -> item(key = "lookup") {
                ModuleCard(
                    module = lookup.module,
                    status = state.installed.status(lookup.module),
                    onClick = { onOpen(lookup.module) },
                    onInstall = { onInstall(lookup.module) },
                    modifier = inset
                )
            }
            is GithubLookup.Failed -> item(key = "lookup") {
                Text(
                    text = stringResource(R.string.github_failed, lookup.message.orEmpty()),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = inset.padding(4.dp)
                )
            }
        }
        state.error?.let { message ->
            item(key = "error") {
                HerculesCard(modifier = inset.fillMaxWidth()) {
                    Text(
                        text = stringResource(R.string.catalog_error, message),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                    TextButton(onClick = onRetry) { Text(stringResource(R.string.action_retry)) }
                }
            }
        }
        if (state.modules.isEmpty() && !state.loading && state.error == null && state.lookup !is GithubLookup.Found) {
            item(key = "empty") {
                Box(inset.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                    Text(
                        text = stringResource(R.string.catalog_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        if (state.modules.isNotEmpty()) {
            item(key = "header_modules") {
                SectionHeader(stringResource(R.string.section_modules, state.modules.size), modifier = inset)
            }
        }
        items(state.modules, key = { it.id }) { module ->
            ModuleCard(
                module = module,
                status = state.installed.status(module),
                onClick = { onOpen(module) },
                onInstall = { onInstall(module) },
                modifier = inset
            )
        }
    }
}

@Composable
private fun LoadingRow(text: String, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
        Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun SearchField(query: String, onChange: (String) -> Unit) {
    val clipboard = LocalClipboardManager.current
    val focus = LocalFocusManager.current
    val scheme = MaterialTheme.colorScheme
    OutlinedTextField(
        value = query,
        onValueChange = onChange,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        placeholder = {
            Text(
                text = stringResource(R.string.search_hint),
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
        trailingIcon = {
            if (query.isEmpty()) {
                IconButton(onClick = { clipboard.getText()?.text?.let(onChange) }) {
                    Icon(Icons.Rounded.ContentPaste, contentDescription = stringResource(R.string.search_paste))
                }
            } else {
                IconButton(onClick = { onChange("") }) {
                    Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.search_clear))
                }
            }
        },
        singleLine = true,
        shape = HerculesShapes.Card,
        textStyle = MaterialTheme.typography.bodyMedium,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { focus.clearFocus() }),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = scheme.primary,
            unfocusedBorderColor = scheme.outlineVariant.copy(alpha = 0.3f),
            focusedContainerColor = scheme.surfaceContainer,
            unfocusedContainerColor = scheme.surfaceContainer
        )
    )
}
