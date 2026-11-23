package app.hercules.store.ui.catalog

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
import app.hercules.store.R
import app.hercules.store.data.InstallStatus
import app.hercules.store.data.InstalledSnapshot
import app.hercules.store.data.model.ModuleItem
import app.hercules.store.ui.components.formatCount
import app.hercules.store.ui.theme.HerculesMono
import app.hercules.store.ui.theme.SileoAccent

private const val FEATURED_LIMIT = 5
private val ICONIC_KEYS = listOf("trickystore", "zygisknext", "shamiko", "lsposed", "playintegrityfork")
private val SpotlightShape = RoundedCornerShape(20.dp)

private fun compact(value: String): String = value.lowercase().filter { it.isLetterOrDigit() }

fun pickFeatured(modules: List<ModuleItem>, limit: Int = FEATURED_LIMIT): List<ModuleItem> {
    val available = modules.filter { it.downloadUrl.isNotBlank() }
    val chosen = LinkedHashMap<String, ModuleItem>()
    ICONIC_KEYS.forEach { key ->
        val candidates = available.filter {
            it.id !in chosen && (key in compact(it.name) || key in compact(it.id))
        }
        val match = candidates.firstOrNull { compact(it.name) == key || compact(it.id) == key }
            ?: candidates.maxByOrNull { it.stars }
        if (match != null) chosen[match.id] = match
    }
    available.sortedByDescending { it.stars }.forEach {
        if (chosen.size < limit && it.id !in chosen) chosen[it.id] = it
    }
    return chosen.values.take(limit)
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FeaturedRow(
    modules: List<ModuleItem>,
    installed: InstalledSnapshot,
    onOpen: (ModuleItem) -> Unit,
    onInstall: (ModuleItem) -> Unit,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()
    LazyRow(
        modifier = modifier.fillMaxWidth(),
        state = listState,
        flingBehavior = rememberSnapFlingBehavior(lazyListState = listState),
        contentPadding = PaddingValues(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(modules, key = { it.id }) { module ->
            FeaturedCard(
                module = module,
                status = installed.status(module),
                onClick = { onOpen(module) },
                onAction = { onInstall(module) }
            )
        }
    }
}

@Composable
private fun FeaturedCard(
    module: ModuleItem,
    status: InstallStatus,
    onClick: () -> Unit,
    onAction: () -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    Box(
        modifier = Modifier
            .width(280.dp)
            .height(156.dp)
            .shadow(6.dp, SpotlightShape)
            .clip(SpotlightShape)
            .background(scheme.surfaceContainerHigh)
            .border(1.dp, scheme.outlineVariant.copy(alpha = 0.5f), SpotlightShape)
            .clickable(onClick = onClick)
    ) {
        Icon(
            module.domainIcon(),
            contentDescription = null,
            modifier = Modifier.align(Alignment.CenterEnd).size(116.dp).padding(end = 8.dp),
            tint = scheme.onSurface.copy(alpha = 0.055f)
        )
        Column(modifier = Modifier.fillMaxSize().padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.spotlight_label),
                    style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 0.8.sp),
                    color = scheme.onSurface,
                    modifier = Modifier
                    .background(SileoAccent.copy(alpha = 0.14f), CircleShape)
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                )
                Spacer(Modifier.weight(1f))
                Icon(
                    Icons.Rounded.Star,
                    contentDescription = null,
                    modifier = Modifier.size(12.dp),
                    tint = SileoAccent
                )
                Spacer(Modifier.width(3.dp))
                Text(
                    text = formatCount(module.stars),
                    style = HerculesMono.label,
                    color = scheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text = module.name,
                style = MaterialTheme.typography.titleLarge.copy(fontSize = 18.sp, lineHeight = 24.sp),
                    color = scheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (module.distinctDescription.isNotBlank()) {
                Text(
                    text = module.distinctDescription,
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(Modifier.weight(1f))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = module.version,
                    style = HerculesMono.label,
                    color = scheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                SpotlightAction(status = status, onClick = onAction)
            }
        }
    }
}

@Composable
private fun SpotlightAction(
    status: InstallStatus,
    onClick: () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    val scheme = MaterialTheme.colorScheme
    val installed = status == InstallStatus.INSTALLED
    val label = stringResource(
        when (status) {
            InstallStatus.NOT_INSTALLED -> R.string.action_get
            InstallStatus.INSTALLED -> R.string.action_installed
            InstallStatus.UPDATE_AVAILABLE -> R.string.action_update
        }
    )
    Box(
        modifier = Modifier
            .height(30.dp)
            .clip(CircleShape)
            .background(if (installed) scheme.surfaceVariant.copy(alpha = 0.7f) else SileoAccent)
            .border(1.dp, if (installed) scheme.outlineVariant.copy(alpha = 0.55f) else SileoAccent, CircleShape)
            .clickable(enabled = !installed) {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onClick()
            }
            .padding(horizontal = 18.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = if (installed) scheme.onSurfaceVariant else Color.White,
            fontWeight = FontWeight.Bold
        )
    }
}
