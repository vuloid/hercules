package app.hercules.store.ui.catalog

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.hercules.store.R
import app.hercules.store.data.model.ModuleItem
import app.hercules.store.ui.components.AccentButton
import app.hercules.store.ui.components.HerculesCard
import app.hercules.store.ui.components.ModuleBadges

@Composable
fun ModuleCard(
    module: ModuleItem,
    onClick: () -> Unit,
    onInstall: () -> Unit,
    modifier: Modifier = Modifier
) {
    HerculesCard(modifier = modifier.fillMaxWidth(), onClick = onClick) {
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
                Text(
                    text = module.author,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            AccentButton(
                text = stringResource(R.string.action_install),
                onClick = onInstall,
                enabled = module.downloadUrl.isNotBlank()
            )
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
        ModuleBadges(module)
    }
}
