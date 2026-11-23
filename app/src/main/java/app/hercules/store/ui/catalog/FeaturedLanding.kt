package app.hercules.store.ui.catalog

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.hercules.store.R
import app.hercules.store.data.model.ModuleItem
import app.hercules.store.ui.components.SectionHeader
import app.hercules.store.ui.components.systemNavigationBottomInset
import app.hercules.store.ui.theme.SileoAccent
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource

@Composable
fun FeaturedLanding(
    state: CatalogUiState,
    onOpen: (ModuleItem) -> Unit,
    onInstall: (ModuleItem) -> Unit,
    onSelectCategory: (CuratedStack) -> Unit,
    onBrowse: () -> Unit,
    hazeState: HazeState?,
    liquidGlassEnabled: Boolean
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().let {
            if (liquidGlassEnabled && hazeState != null) it.hazeSource(state = hazeState) else it
        },
        contentPadding = PaddingValues(top = 14.dp, bottom = 24.dp + systemNavigationBottomInset()),
        verticalArrangement = Arrangement.spacedBy(0.dp)
    ) {
        item(key = "featured_heading") {
            Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(androidx.compose.ui.res.stringResource(R.string.tab_featured), style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold))
                Text(androidx.compose.ui.res.stringResource(R.string.featured_intro), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (state.featured.isNotEmpty()) {
            item(key = "spotlight") {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    SectionHeader(androidx.compose.ui.res.stringResource(R.string.section_spotlight), modifier = Modifier.padding(horizontal = 16.dp))
                    FeaturedRow(state.featured, state.installed, onOpen, onInstall)
                }
            }
        }
        item(key = "categories") {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                SectionHeader(androidx.compose.ui.res.stringResource(R.string.section_categories), modifier = Modifier.padding(horizontal = 16.dp))
                CategoryGrid { category ->
                    onSelectCategory(category)
                    onBrowse()
                }
            }
        }
        item(key = "recent_heading") {
            Row(
                Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(androidx.compose.ui.res.stringResource(R.string.section_recently_updated), style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                TextButton(onClick = onBrowse) { Text(androidx.compose.ui.res.stringResource(R.string.action_see_all), color = SileoAccent) }
            }
        }
        items(state.recentlyUpdated, key = { "recent_" + it.id }) { module ->
            ModuleCard(
                module = module,
                status = state.installed.status(module),
                onClick = { onOpen(module) },
                onInstall = { onInstall(module) },
                modifier = Modifier.padding(horizontal = 12.dp)
            )
        }
    }
}

@Composable
private fun CategoryGrid(onSelectCategory: (CuratedStack) -> Unit) {
    val categories = CuratedStack.entries.take(4)
    Column(
        modifier = Modifier.padding(horizontal = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        categories.chunked(2).forEach { rowItems ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                rowItems.forEach { category ->
                    CategoryTile(category, onSelectCategory, Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun CategoryTile(category: CuratedStack, onClick: (CuratedStack) -> Unit, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(14.dp)
    Row(
        modifier = modifier.height(72.dp)
            .background(scheme.surfaceContainer, shape)
            .border(1.dp, scheme.outlineVariant.copy(alpha = 0.35f), shape)
            .clickable { onClick(category) }
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Icon(category.icon, contentDescription = null, modifier = Modifier.size(20.dp), tint = SileoAccent)
        Text(
            text = androidx.compose.ui.res.stringResource(category.label),
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
            color = Color.White
        )
    }
}
