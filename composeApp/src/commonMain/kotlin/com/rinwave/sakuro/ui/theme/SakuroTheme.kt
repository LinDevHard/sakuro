package com.rinwave.sakuro.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.jetbrains.compose.resources.Font
import sakuro.composeapp.generated.resources.Res
import sakuro.composeapp.generated.resources.inter_bold
import sakuro.composeapp.generated.resources.inter_medium
import sakuro.composeapp.generated.resources.inter_regular
import sakuro.composeapp.generated.resources.inter_semibold

/** Палитра бренда (BRAND.md §2). */
object SakuroColors {
    val Background = Color(0xFF120A17)
    val Surface = Color(0xFF1E1226)
    val SurfaceElevated = Color(0xFF2A1A34)
    val Twilight = Color(0xFF4A2A5A)
    val AccentSakura = Color(0xFFEC8FC0)
    val AccentLavender = Color(0xFFA57FD6)
    val GlowMagenta = Color(0xFFC94F9C)
    val TextPrimary = Color(0xFFF3E9F2)
    val TextMuted = Color(0xFFB9A7C4)
}

private val SakuroColorScheme = darkColorScheme(
    primary = SakuroColors.AccentSakura,
    onPrimary = SakuroColors.Background,
    secondary = SakuroColors.AccentLavender,
    onSecondary = SakuroColors.Background,
    tertiary = SakuroColors.GlowMagenta,
    background = SakuroColors.Background,
    onBackground = SakuroColors.TextPrimary,
    surface = SakuroColors.Surface,
    onSurface = SakuroColors.TextPrimary,
    surfaceVariant = SakuroColors.SurfaceElevated,
    onSurfaceVariant = SakuroColors.TextMuted,
    surfaceContainer = SakuroColors.Surface,
    surfaceContainerHigh = SakuroColors.SurfaceElevated,
    surfaceContainerHighest = SakuroColors.SurfaceElevated,
    outline = SakuroColors.Twilight,
    error = Color(0xFFF2789F),
    onError = SakuroColors.Background,
)

private val SakuroShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

/** Inter (variable-дизайн, статические начертания) — единый UI-шрифт (DESIGN.md §3). */
@Composable
private fun interFontFamily() = FontFamily(
    Font(Res.font.inter_regular, FontWeight.Normal),
    Font(Res.font.inter_medium, FontWeight.Medium),
    Font(Res.font.inter_semibold, FontWeight.SemiBold),
    Font(Res.font.inter_bold, FontWeight.Bold),
)

@Composable
private fun sakuroTypography(): Typography {
    val inter = interFontFamily()
    fun TextStyle.inter() = copy(fontFamily = inter)
    val base = Typography()
    return Typography(
        displayLarge = base.displayLarge.inter(),
        displayMedium = base.displayMedium.inter(),
        displaySmall = base.displaySmall.inter(),
        headlineLarge = base.headlineLarge.inter(),
        headlineMedium = base.headlineMedium.inter(),
        headlineSmall = base.headlineSmall.inter(),
        titleLarge = base.titleLarge.inter().copy(letterSpacing = 0.2.sp),
        titleMedium = base.titleMedium.inter().copy(letterSpacing = 0.2.sp),
        titleSmall = base.titleSmall.inter(),
        bodyLarge = base.bodyLarge.inter(),
        bodyMedium = base.bodyMedium.inter(),
        bodySmall = base.bodySmall.inter(),
        labelLarge = base.labelLarge.inter().copy(letterSpacing = 0.8.sp),
        labelMedium = base.labelMedium.inter(),
        labelSmall = base.labelSmall.inter(),
    )
}

@Composable
fun SakuroTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = SakuroColorScheme,
        typography = sakuroTypography(),
        shapes = SakuroShapes,
        content = content,
    )
}
