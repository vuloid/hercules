package app.hercules.store.ui.installed

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Update
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.hercules.store.R
import app.hercules.store.data.model.ModuleItem
import app.hercules.store.ui.HerculesViewModelFactory
import app.hercules.store.ui.components.AccentButton
import app.hercules.store.ui.components.HerculesCard
import app.hercules.store.ui.components.MonoBadge
import app.hercules.store.ui.components.SectionHeader

@Composable
fun InstalledScreen(
    onUpdate: (ModuleItem) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: InstalledViewModel = viewModel(factory = HerculesViewModelFactory)
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { viewModel.refresh() }

    Column(modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 8.dp, top = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(stringResource(R.string.installed_title), style = MaterialTheme.typography.titleLarge)
            IconButton(onClick = viewModel::refresh, enabled = !state.loading) {
                Icon(Icons.Rounded.Refresh, contentDescription = stringResource(R.string.action_refresh))
            }
        }

        val isEmpty = state.updates.isEmpty() && state.upToDate.isEmpty()
        when {
            state.loading && isEmpty -> CenteredBox { CircularProgressIndicator() }
            state.rootAvailable == false -> CenteredBox {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = stringResource(R.string.installed_no_root),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    TextButton(onClick = viewModel::refresh) { Text(stringResource(R.string.action_retry)) }
                }
            }
            isEmpty -> CenteredBox {
                Text(
                    text = stringResource(R.string.installed_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (state.updates.isNotEmpty()) {
                    item(key = "header_updates") {
                        SectionHeader(stringResource(R.string.installed_updates, state.updates.size))
                    }
                    items(state.updates, key = { "u_" + it.module.id }) { InstalledCard(it, onUpdate) }
                }
                if (state.upToDate.isNotEmpty()) {
                    item(key = "header_installed") {
                        SectionHeader(stringResource(R.string.installed_up_to_date, state.upToDate.size))
                    }
                    items(state.upToDate, key = { "i_" + it.module.id }) { InstalledCard(it, onUpdate) }
                }
            }
        }
    }
}

@Composable
private fun CenteredBox(content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) { content() }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun InstalledCard(entry: InstalledEntry, onUpdate: (ModuleItem) -> Unit) {
    val module = entry.module
    val update = entry.update
    HerculesCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = module.name,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (module.author.isNotBlank()) {
                    Text(
                        text = module.author,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            if (update != null) {
                AccentButton(
                    text = stringResource(R.string.action_update),
                    onClick = { onUpdate(update) },
                    enabled = update.downloadUrl.isNotBlank()
                )
            }
        }
        if (module.description.isNotBlank()) {
            Text(
                text = module.description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            MonoBadge(module.id)
            MonoBadge("${module.version} (${module.versionCode})")
            if (update != null) {
                MonoBadge("${update.version} (${update.versionCode})", icon = Icons.Rounded.Update, accent = true)
            }
            if (!module.enabled) MonoBadge(stringResource(R.string.badge_disabled))
        }
    }
}
