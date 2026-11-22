package app.hercules.store.ui.catalog

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.hercules.store.R
import app.hercules.store.ui.components.HerculesShapes

@Composable
fun FilterRow(
    rootFilter: RootFilter,
    sort: SortOrder,
    hideOutdated: Boolean,
    onRootFilter: (RootFilter) -> Unit,
    onSort: (SortOrder) -> Unit,
    onHideOutdated: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RootFilter.entries.forEach { filter ->
            HerculesChip(stringResource(filter.label), filter == rootFilter) { onRootFilter(filter) }
        }
        ChipDivider()
        SortOrder.entries.forEach { order ->
            HerculesChip(stringResource(order.label), order == sort) { onSort(order) }
        }
        ChipDivider()
        HerculesChip(stringResource(R.string.filter_hide_outdated), hideOutdated) {
            onHideOutdated(!hideOutdated)
        }
    }
}

@Composable
private fun HerculesChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label, style = MaterialTheme.typography.labelMedium) },
        shape = HerculesShapes.Control,
        colors = FilterChipDefaults.filterChipColors(
            containerColor = scheme.surfaceContainer,
            labelColor = scheme.onSurfaceVariant,
            selectedContainerColor = scheme.primary,
            selectedLabelColor = scheme.onPrimary
        ),
        border = FilterChipDefaults.filterChipBorder(
            enabled = true,
            selected = selected,
            borderColor = scheme.outlineVariant.copy(alpha = 0.3f),
            selectedBorderColor = Color.Transparent
        )
    )
}

@Composable
private fun ChipDivider() {
    VerticalDivider(
        modifier = Modifier.height(18.dp),
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
    )
}
