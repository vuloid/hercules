package app.hercules.store.ui.components

import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect

@Composable
fun Modifier.glassSurface(
    state: HazeState?,
    enabled: Boolean,
    shape: Shape = RoundedCornerShape(24.dp)
): Modifier {
    val scheme = MaterialTheme.colorScheme
    return if (enabled && state != null) {
        clip(shape)
            .hazeEffect(state = state, style = HazeStyle(
                backgroundColor = scheme.surface,
                blurRadius = 24.dp,
                tint = HazeTint(scheme.surface.copy(alpha = 0.28f))
            ))
            .drawWithContent {
                drawContent()
                drawLine(
                    brush = Brush.horizontalGradient(
                        listOf(Color.White.copy(alpha = 0.55f), scheme.onSurface.copy(alpha = 0.12f), Color.White.copy(alpha = 0.4f))
                    ),
                    start = Offset(0f, 0.5.dp.toPx()),
                    end = Offset(size.width, 0.5.dp.toPx()),
                    strokeWidth = 1.dp.toPx(),
                    cap = StrokeCap.Round
                )
            }
            .border(1.dp, scheme.outlineVariant.copy(alpha = 0.25f), shape)
    } else {
        background(scheme.surfaceContainer, shape)
            .border(1.dp, scheme.outlineVariant.copy(alpha = 0.25f), shape)
    }
}
