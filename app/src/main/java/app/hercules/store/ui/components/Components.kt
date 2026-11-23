package app.hercules.store.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.font.FontWeight
import app.hercules.store.ui.theme.HerculesMono
import app.hercules.store.ui.theme.SileoAccent

object HerculesShapes {
    val Card = RoundedCornerShape(14.dp)
    val Badge = RoundedCornerShape(6.dp)
    val Control = RoundedCornerShape(10.dp)
}

@Composable
fun HerculesCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
    val elevation = CardDefaults.cardElevation(
        defaultElevation = 2.dp,
        pressedElevation = 0.dp,
        focusedElevation = 2.dp,
        hoveredElevation = 3.dp,
        draggedElevation = 4.dp,
        disabledElevation = 0.dp
    )
    val border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    val body: @Composable ColumnScope.() -> Unit = {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            content()
        }
    }
    if (onClick != null) {
        Card(
            onClick = onClick,
            modifier = modifier,
            shape = HerculesShapes.Card,
            colors = colors,
            elevation = elevation,
            border = border,
            content = body
        )
    } else {
        Card(
            modifier = modifier,
            shape = HerculesShapes.Card,
            colors = colors,
            elevation = elevation,
            border = border,
            content = body
        )
    }
}

@Composable
fun MonoBadge(
    text: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    accent: Boolean = false
) {
    val scheme = MaterialTheme.colorScheme
    val tint = if (accent) scheme.primary else scheme.onSurfaceVariant
    val edge = (if (accent) scheme.primary else scheme.outlineVariant).copy(alpha = 0.3f)
    Row(
        modifier = modifier
            .background(scheme.surfaceVariant.copy(alpha = 0.4f), HerculesShapes.Badge)
            .border(1.dp, edge, HerculesShapes.Badge)
            .padding(horizontal = 6.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(11.dp), tint = tint)
        }
        Text(text, style = HerculesMono.label, color = tint, maxLines = 1)
    }
}

@Composable
fun AccentButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null,
    accentColor: Color = SileoAccent
) {
    val haptic = LocalHapticFeedback.current
    val scheme = MaterialTheme.colorScheme
    Button(
        onClick = {
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            onClick()
        },
        modifier = modifier.height(32.dp),
        enabled = enabled,
        shape = CircleShape,
        colors = ButtonDefaults.buttonColors(
            containerColor = accentColor,
            contentColor = Color.White,
            disabledContainerColor = scheme.surfaceContainerHigh,
            disabledContentColor = scheme.onSurfaceVariant.copy(alpha = 0.55f)
        ),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 0.dp)
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(6.dp))
        }
        Text(text, style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold))
    }
}

@Composable
fun InstalledPill(text: String, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier = modifier.height(30.dp)
            .background(scheme.surfaceVariant.copy(alpha = 0.72f), CircleShape)
            .border(1.dp, scheme.outlineVariant.copy(alpha = 0.55f), CircleShape)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text, style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold), color = scheme.onSurfaceVariant)
    }
}

@Composable
fun QuietButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.height(32.dp),
        enabled = enabled,
        shape = HerculesShapes.Control,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp)
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(6.dp))
        }
        Text(text, style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
fun SectionHeader(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier
            .padding(horizontal = 4.dp)
            .padding(top = 10.dp, bottom = 2.dp)
    )
}
