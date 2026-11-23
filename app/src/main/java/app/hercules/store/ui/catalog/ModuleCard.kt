package app.hercules.store.ui.catalog

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.material3.HorizontalDivider
import androidx.compose.ui.draw.clip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.hercules.store.R
import app.hercules.store.data.InstallStatus
import app.hercules.store.data.model.ModuleItem
import app.hercules.store.ui.components.AccentButton
import app.hercules.store.ui.components.InstalledPill
import app.hercules.store.ui.theme.IconTileSurface
import app.hercules.store.ui.theme.SileoAccent

@Composable
fun ModuleCard(
    module: ModuleItem,
    status: InstallStatus,
    onClick: () -> Unit,
    onInstall: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 10.dp)) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val shape = RoundedCornerShape(12.dp)
            androidx.compose.foundation.layout.Box(
                Modifier.size(42.dp).clip(shape)
                    .background(IconTileSurface)
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f), shape),
                contentAlignment = Alignment.Center
            ) {
                androidx.compose.material3.Icon(module.domainIcon(), contentDescription = null, modifier = Modifier.size(21.dp), tint = SileoAccent)
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = module.name,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Bold),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = module.distinctDescription.ifBlank { module.author },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (status == InstallStatus.INSTALLED) {
                InstalledPill(stringResource(R.string.action_installed))
            } else {
                AccentButton(
                    text = stringResource(
                        if (status == InstallStatus.UPDATE_AVAILABLE) R.string.action_update else R.string.action_install
                    ),
                    onClick = onInstall,
                    enabled = module.downloadUrl.isNotBlank(),
                    accentColor = SileoAccent
                )
            }
        }
        HorizontalDivider(Modifier.padding(start = 52.dp, top = 10.dp), thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
    }
}
