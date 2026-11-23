package app.hercules.store.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

@Composable
fun HerculesTheme(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val colorScheme = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        dynamicDarkColorScheme(context).copy(
            background = Obsidian,
            surface = Obsidian,
            surfaceVariant = CharcoalSlate,
            surfaceContainerLowest = Color(0xFF0D0F15),
            surfaceContainerLow = Color(0xFF10131A),
            surfaceContainer = CharcoalSlate,
            surfaceContainerHigh = Color(0xFF1A1E29),
            surfaceContainerHighest = Color(0xFF222836),
            outlineVariant = Color(0xFF303746)
        )
    } else GraphiteColorScheme
    MaterialTheme(colorScheme = colorScheme, typography = HerculesTypography, content = content)
}
