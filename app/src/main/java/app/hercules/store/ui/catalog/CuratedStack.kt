package app.hercules.store.ui.catalog

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.Extension
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.VerifiedUser
import androidx.compose.ui.graphics.vector.ImageVector
import app.hercules.store.R
import app.hercules.store.data.model.ModuleItem

private fun containing(vararg keywords: String): (ModuleItem) -> Boolean = { module ->
    val haystack = "${module.name} ${module.id} ${module.description}".lowercase()
    keywords.any { it in haystack }
}

enum class CuratedStack(
    @StringRes val label: Int,
    val icon: ImageVector,
    val ranksByStars: Boolean,
    private val predicate: (ModuleItem) -> Boolean
) {
    PLAY_INTEGRITY(
        R.string.stack_play_integrity,
        Icons.Rounded.VerifiedUser,
        false,
        containing("integrity", "tricky", "safetynet", "keybox", "playcurl")
    ),
    LSPOSED_PICKS(
        R.string.stack_lsposed,
        Icons.Rounded.Extension,
        true,
        { it.lsposed }
    ),
    AUDIO(
        R.string.stack_audio,
        Icons.Rounded.GraphicEq,
        false,
        containing("dolby", "viper", "audio", "sound", "dirac")
    ),
    BATTERY_KERNEL(
        R.string.stack_battery,
        Icons.Rounded.BatteryChargingFull,
        false,
        containing("thermal", "uperf", "governor", "battery", "doze", "swap")
    ),
    SYSTEM_UI(
        R.string.stack_system,
        Icons.Rounded.PhoneAndroid,
        false,
        containing("launcher", "pixelify", "font", "monet", "bootanimation")
    );

    fun matches(module: ModuleItem): Boolean = predicate(module)
}
