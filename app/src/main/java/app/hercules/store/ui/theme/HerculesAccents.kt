package app.hercules.store.ui.theme

import androidx.compose.ui.graphics.Color
import app.hercules.store.data.model.RootManager

fun rootAccent(manager: RootManager?): Color = when (manager) {
    RootManager.KERNELSU -> Color(0xFF10B981)
    RootManager.MAGISK -> Color(0xFF3B82F6)
    RootManager.APATCH -> Color(0xFFF59E0B)
    null -> Color(0xFF8B5CF6)
}

