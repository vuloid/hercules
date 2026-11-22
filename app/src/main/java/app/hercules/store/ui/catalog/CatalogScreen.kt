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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.hercules.store.R
import app.hercules.store.data.model.ModuleItem
import app.hercules.store.ui.HerculesViewModelFactory
import app.hercules.store.ui.components.HerculesCard
import app.hercules.store.ui.components.HerculesShapes
import app.hercules.store.ui.details.ModuleDetailsSheet

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CatalogScreen(
    onInstall: (ModuleItem) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: CatalogViewModel = viewModel(factory = HerculesViewModelFactory)
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Column(modifier.fillMaxSize()) {
        SearchField(query = state.query, onChange = viewModel::onQueryChange)
        FilterRow(
            rootFilter = state.rootFilter,
            sort = state.sort,
            hideOutdated = state.hideOutdated,
            onRootFilter = viewModel::onRootFilter,
            onSort = viewModel::onSort,
            onHideOutdated = viewModel::onHideOutdated
        )
        PullToRefreshBox(
            isRefreshing = state.loading,
            onRefresh = viewModel::refresh,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            CatalogList(
                state = state,
                onOpen = viewModel::select,
                onInstall = onInstall,
                onRetry = viewModel::refresh
            )
        }
    }

    state.selected?.let { module ->
        ModuleDetailsSheet(
            module = module,
            onDismiss = { viewModel.select(null) },
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
    onRetry: () -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        when (val lookup = state.lookup) {
            GithubLookup.Idle -> Unit
            GithubLookup.Loading -> item(key = "lookup") {
                LoadingRow(stringResource(R.string.github_resolving))
            }
            is GithubLookup.Found -> item(key = "lookup") {
                ModuleCard(
                    module = lookup.module,
                    onClick = { onOpen(lookup.module) },
                    onInstall = { onInstall(lookup.module) }
                )
            }
            is GithubLookup.Failed -> item(key = "lookup") {
                Text(
                    text = stringResource(R.string.github_failed, lookup.message.orEmpty()),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(4.dp)
                )
            }
        }
        state.error?.let { message ->
            item(key = "error") {
                HerculesCard(modifier = Modifier.fillMaxWidth()) {
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
                Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                    Text(
                        text = stringResource(R.string.catalog_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        items(state.modules, key = { it.id }) { module ->
            ModuleCard(
                module = module,
                onClick = { onOpen(module) },
                onInstall = { onInstall(module) }
            )
        }
    }
}

@Composable
private fun LoadingRow(text: String) {
    Row(
        modifier = Modifier.padding(4.dp),
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
