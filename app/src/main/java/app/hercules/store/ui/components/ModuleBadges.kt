package app.hercules.store.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Star
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.hercules.store.data.model.ModuleItem

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ModuleBadges(module: ModuleItem, modifier: Modifier = Modifier) {
    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        if (module.version.isNotBlank()) MonoBadge(module.version)
        MonoBadge(formatCount(module.stars), icon = Icons.Rounded.Star)
        val compat = module.compatLabel()
        if (compat.isNotEmpty()) MonoBadge(compat)
        if (module.updatedAt > 0) MonoBadge(formatDate(module.updatedAt))
    }
}
