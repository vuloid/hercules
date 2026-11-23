package app.hercules.store.ui.status

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.hercules.store.R
import app.hercules.store.ui.theme.HerculesMono
import app.hercules.store.ui.theme.StatusOnline
import app.hercules.store.ui.theme.rootAccent
import app.hercules.store.ui.components.glassSurface
import dev.chrisbanes.haze.HazeState

@Composable
fun RootStatusPill(status: RootStatus, modifier: Modifier = Modifier, hazeState: HazeState? = null, glassEnabled: Boolean = false) {
    val scheme = MaterialTheme.colorScheme
    val available = status.rootAvailable
    val accent = rootAccent(status.manager)
    val label = when (available) {
        null -> stringResource(R.string.status_checking)
        false -> stringResource(R.string.status_no_root)
        true -> stringResource(
            R.string.status_active,
            status.manager?.displayName ?: stringResource(R.string.status_root_generic),
            status.activeModules
        )
    }
    val dot = when (available) {
        null -> scheme.onSurfaceVariant
        false -> scheme.error
        true -> status.manager?.let { rootAccent(it) } ?: StatusOnline
    }
    Row(
        modifier = modifier
            .glassSurface(hazeState, glassEnabled, CircleShape)
            .background(if (available == true) accent.copy(alpha = 0.08f) else Color.Transparent, CircleShape)
            .border(1.dp, if (available == true) accent.copy(alpha = 0.28f) else scheme.outlineVariant.copy(alpha = 0.25f), CircleShape)
            .padding(start = 6.dp, end = 10.dp, top = 3.dp, bottom = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        GlowDot(dot)
        Text(
            text = label,
            style = HerculesMono.label,
            color = if (available == true) accent else scheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun GlowDot(color: Color) {
    val glow by rememberInfiniteTransition(label = "glow").animateFloat(
        initialValue = 0.16f,
        targetValue = 0.42f,
        animationSpec = infiniteRepeatable(tween(1400, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "glowAlpha"
    )
    Box(
        modifier = Modifier
            .size(14.dp)
            .drawBehind { drawCircle(color.copy(alpha = glow)) },
        contentAlignment = Alignment.Center
    ) {
        Box(Modifier.size(7.dp).background(color, CircleShape))
    }
}
