package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.platform.LocalContext

/** Theme modes selectable in Settings. Stored by name in DataStore. */
enum class ThemeMode(val label: String) {
    AMOLED("AMOLED Dark"),
    LIGHT("Light"),
    MATERIAL_YOU("Material You"),
    SYSTEM("Follow system");

    companion object {
        fun fromName(name: String?): ThemeMode = entries.firstOrNull { it.name == name } ?: AMOLED
    }
}

/**
 * Pulse design tokens. Every screen reads colors from here (via [PulseTheme.colors]) so the
 * AMOLED, Light and Material You variants share identical spacing, shapes and components.
 */
@Immutable
data class PulseColors(
    val isDark: Boolean,
    val background: Color,
    val surface: Color,
    val surfaceHigh: Color,
    val glass: Color,
    val glassStrong: Color,
    val glassBorder: Color,
    val glassHighlight: Color,
    val scrim: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textTertiary: Color,
    val accent: Color,
    val accentPartner: Color,
    val onAccent: Color,
    val auroraA: Color,
    val auroraB: Color,
    val auroraC: Color,
    val success: Color,
    val warning: Color
) {
    /** Pink → violet signature gradient used on play buttons, progress, active chips. */
    val accentGradient: Brush get() = Brush.linearGradient(listOf(accentPartner, accent))
    val accentGradientColors: List<Color> get() = listOf(accentPartner, accent)
    val accentSoft: Color get() = accent.copy(alpha = if (isDark) 0.18f else 0.14f)
}

private fun amoledColors(accent: AccentOption) = PulseColors(
    isDark = true,
    background = PureBlack,
    surface = Surface1,
    surfaceHigh = Surface2,
    glass = Color.White.copy(alpha = 0.06f),
    glassStrong = Color.White.copy(alpha = 0.10f),
    glassBorder = Color.White.copy(alpha = 0.10f),
    glassHighlight = Color.White.copy(alpha = 0.22f),
    scrim = Color.Black.copy(alpha = 0.55f),
    textPrimary = Color.White,
    textSecondary = Color.White.copy(alpha = 0.62f),
    textTertiary = Color.White.copy(alpha = 0.38f),
    accent = accent.primary,
    accentPartner = accent.partner,
    onAccent = Color.White,
    auroraA = accent.primary,
    auroraB = accent.partner,
    auroraC = Indigo,
    success = PulseEmerald,
    warning = PulseAmber
)

private fun lightColors(accent: AccentOption) = PulseColors(
    isDark = false,
    background = Color(0xFFF5F3FA),
    surface = Color(0xFFFFFFFF),
    surfaceHigh = Color(0xFFEDE9F6),
    glass = Color.White.copy(alpha = 0.58f),
    glassStrong = Color.White.copy(alpha = 0.78f),
    glassBorder = Color.White.copy(alpha = 0.85f),
    glassHighlight = Color.White,
    scrim = Color(0xFF1A1033).copy(alpha = 0.25f),
    textPrimary = Color(0xFF15121E),
    textSecondary = Color(0xFF15121E).copy(alpha = 0.64f),
    textTertiary = Color(0xFF15121E).copy(alpha = 0.42f),
    accent = accent.primary,
    accentPartner = accent.partner,
    onAccent = Color.White,
    auroraA = accent.primary.copy(alpha = 0.55f),
    auroraB = accent.partner.copy(alpha = 0.45f),
    auroraC = Color(0xFF93C5FD),
    success = Color(0xFF059669),
    warning = Color(0xFFD97706)
)

private fun dynamicColors(scheme: ColorScheme, dark: Boolean): PulseColors {
    val base = if (dark) amoledColors(AccentOption.VIOLET) else lightColors(AccentOption.VIOLET)
    return base.copy(
        background = if (dark) Color.Black else scheme.background,
        surface = if (dark) scheme.primary.copy(alpha = 0.06f).compositeOver(Surface1) else scheme.surface,
        surfaceHigh = if (dark) scheme.primary.copy(alpha = 0.10f).compositeOver(Surface2) else scheme.surfaceVariant,
        textPrimary = scheme.onBackground,
        accent = scheme.primary,
        accentPartner = scheme.tertiary,
        onAccent = scheme.onPrimary,
        auroraA = scheme.primary,
        auroraB = scheme.tertiary,
        auroraC = scheme.secondary
    )
}

private fun PulseColors.toMaterialScheme(): ColorScheme {
    val base = if (isDark) darkColorScheme() else lightColorScheme()
    return base.copy(
        primary = accent,
        onPrimary = onAccent,
        primaryContainer = accentSoft.compositeOver(surfaceHigh),
        onPrimaryContainer = textPrimary,
        secondary = accentPartner,
        onSecondary = onAccent,
        secondaryContainer = accentPartner.copy(alpha = 0.18f).compositeOver(surfaceHigh),
        onSecondaryContainer = textPrimary,
        tertiary = accentPartner,
        background = background,
        onBackground = textPrimary,
        surface = surface,
        onSurface = textPrimary,
        surfaceVariant = surfaceHigh,
        onSurfaceVariant = textSecondary,
        surfaceContainerLowest = background,
        surfaceContainerLow = surface,
        surfaceContainer = surface,
        surfaceContainerHigh = surfaceHigh,
        surfaceContainerHighest = surfaceHigh,
        outline = glassBorder,
        outlineVariant = glassBorder,
        inverseSurface = textPrimary,
        inverseOnSurface = background
    )
}

val LocalPulseColors = staticCompositionLocalOf { amoledColors(AccentOption.VIOLET) }

/** 0f = static & calm, 1f = full aurora motion and glow. Driven by Settings → Animation intensity. */
val LocalAnimationIntensity = staticCompositionLocalOf { 1f }

object PulseTheme {
    val colors: PulseColors
        @Composable get() = LocalPulseColors.current
    val motion: Float
        @Composable get() = LocalAnimationIntensity.current
}

@Composable
fun PulseMusicTheme(
    themeMode: ThemeMode = ThemeMode.AMOLED,
    accent: AccentOption = AccentOption.VIOLET,
    animationIntensity: Float = 1f,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val systemDark = isSystemInDarkTheme()
    val colors = when (themeMode) {
        ThemeMode.AMOLED -> amoledColors(accent)
        ThemeMode.LIGHT -> lightColors(accent)
        ThemeMode.SYSTEM -> if (systemDark) amoledColors(accent) else lightColors(accent)
        ThemeMode.MATERIAL_YOU -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val scheme = if (systemDark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
            dynamicColors(scheme, systemDark)
        } else {
            if (systemDark) amoledColors(accent) else lightColors(accent)
        }
    }

    CompositionLocalProvider(
        LocalPulseColors provides colors,
        LocalAnimationIntensity provides animationIntensity.coerceIn(0f, 1f)
    ) {
        MaterialTheme(
            colorScheme = colors.toMaterialScheme(),
            typography = Typography,
            content = content
        )
    }
}
