package app.hercules.store.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import app.hercules.store.R

val InterFamily = FontFamily(
    Font(R.font.inter_regular, FontWeight.Normal),
    Font(R.font.inter_semibold, FontWeight.SemiBold)
)

val MonoFamily = FontFamily(
    Font(R.font.jetbrains_mono, FontWeight.Normal)
)

private val base = Typography()

private fun TextStyle.inter() = copy(fontFamily = InterFamily)

val HerculesTypography = Typography(
    displayLarge = base.displayLarge.inter(),
    displayMedium = base.displayMedium.inter(),
    displaySmall = base.displaySmall.inter(),
    headlineLarge = base.headlineLarge.inter(),
    headlineMedium = base.headlineMedium.inter(),
    headlineSmall = base.headlineSmall.inter(),
    titleLarge = TextStyle(fontFamily = InterFamily, fontWeight = FontWeight.SemiBold, fontSize = 20.sp, lineHeight = 26.sp),
    titleMedium = TextStyle(fontFamily = InterFamily, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, lineHeight = 20.sp),
    titleSmall = TextStyle(fontFamily = InterFamily, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, lineHeight = 18.sp),
    bodyLarge = TextStyle(fontFamily = InterFamily, fontSize = 14.sp, lineHeight = 20.sp),
    bodyMedium = TextStyle(fontFamily = InterFamily, fontSize = 13.sp, lineHeight = 18.sp),
    bodySmall = TextStyle(fontFamily = InterFamily, fontSize = 12.sp, lineHeight = 16.sp),
    labelLarge = TextStyle(fontFamily = InterFamily, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, lineHeight = 18.sp),
    labelMedium = TextStyle(fontFamily = InterFamily, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, lineHeight = 16.sp),
    labelSmall = TextStyle(fontFamily = InterFamily, fontWeight = FontWeight.SemiBold, fontSize = 11.sp, lineHeight = 14.sp)
)

object HerculesMono {
    val label = TextStyle(fontFamily = MonoFamily, fontSize = 11.sp, lineHeight = 14.sp)
    val body = TextStyle(fontFamily = MonoFamily, fontSize = 12.sp, lineHeight = 16.sp)
    val log = TextStyle(fontFamily = MonoFamily, fontSize = 11.sp, lineHeight = 15.sp)
}
