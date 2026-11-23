package app.hercules.store.ui.catalog

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.hercules.store.ui.components.HerculesShapes
import app.hercules.store.ui.theme.SileoAccent

@Composable
fun CuratedStacks(
    selected: CuratedStack?,
    onSelect: (CuratedStack) -> Unit,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current
    Row(
        modifier = modifier
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        CuratedStack.entries.forEach { stack ->
            StackChip(
                stack = stack,
                selected = stack == selected,
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onSelect(stack)
                }
            )
        }
    }
}

@Composable
private fun StackChip(stack: CuratedStack, selected: Boolean, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(stringResource(stack.label), style = MaterialTheme.typography.labelMedium) },
        leadingIcon = { Icon(stack.icon, contentDescription = null, modifier = Modifier.size(16.dp)) },
        shape = HerculesShapes.Control,
        colors = FilterChipDefaults.filterChipColors(
            containerColor = scheme.background,
            labelColor = scheme.onSurface,
            iconColor = scheme.onSurfaceVariant,
            selectedContainerColor = SileoAccent,
            selectedLabelColor = Color.White,
            selectedLeadingIconColor = Color.White
        ),
        border = FilterChipDefaults.filterChipBorder(
            enabled = true,
            selected = selected,
            borderColor = scheme.outlineVariant,
            selectedBorderColor = SileoAccent
        )
    )
}
