package app.hercules.store.ui.catalog

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Extension
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material.icons.rounded.VerifiedUser
import androidx.compose.ui.graphics.vector.ImageVector
import app.hercules.store.data.model.ModuleItem

fun ModuleItem.domainIcon(): ImageVector {
    val words = "$name $id $description".lowercase()
    return when {
        isApk || lsposed || "xposed" in words || "hook" in words -> Icons.Rounded.Extension
        listOf("integrity", "zygisk", "shamiko", "root hide", "keybox").any { it in words } -> Icons.Rounded.VerifiedUser
        listOf("audio", "dolby", "viper", "sound", "dirac").any { it in words } -> Icons.Rounded.GraphicEq
        listOf("kernel", "battery", "performance", "thermal", "governor").any { it in words } -> Icons.Rounded.Bolt
        listOf("font", "theme", "launcher", "monet", "ui").any { it in words } -> Icons.Rounded.Palette
        listOf("dns", "adblock", "network", "firewall", "hosts").any { it in words } -> Icons.Rounded.Public
        else -> Icons.Rounded.Terminal
    }
}
