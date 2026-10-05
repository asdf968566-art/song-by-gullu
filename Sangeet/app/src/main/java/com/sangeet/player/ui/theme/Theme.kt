package com.sangeet.player.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.sangeet.player.data.settings.AppSettings
import com.sangeet.player.data.settings.DarkMode
import com.sangeet.player.data.settings.ThemeStyle

/** Har theme ke custom rang jo Material colorScheme mein nahi aate. */
@Immutable
data class ThemeSpec(
    val style: ThemeStyle,
    val isDark: Boolean,
    val accent: Color,
    val background: Color,
    val surface: Color,
    val onSurface: Color,
    val muted: Color,
    val navBar: Color,
    /** Neumorphism ke shadows. */
    val lightShadow: Color = Color.White,
    val darkShadow: Color = Color.Black,
)

val LocalThemeSpec = staticCompositionLocalOf {
    ThemeSpec(
        ThemeStyle.SPOTIFY, true, Color(0xFF1DB954), Color(0xFF121212), Color(0xFF1E1E1E),
        Color.White, Color(0xFFB3B3B3), Color(0xFF000000),
    )
}

object Sangeet {
    val spec: ThemeSpec
        @Composable get() = LocalThemeSpec.current
}

private fun specFor(style: ThemeStyle, dark: Boolean, accent: Color, scheme: ColorScheme?): ThemeSpec = when (style) {
    ThemeStyle.SPOTIFY -> if (dark) ThemeSpec(
        style, true, accent, Color(0xFF121212), Color(0xFF1F1F1F), Color.White, Color(0xFFB3B3B3), Color(0xF2000000),
    ) else ThemeSpec(
        style, false, accent, Color(0xFFF7F7F7), Color.White, Color(0xFF121212), Color(0xFF6A6A6A), Color(0xF2FFFFFF),
    )

    ThemeStyle.AURORA -> if (dark) ThemeSpec(
        style, true, accent, Color(0xFF070B1E), Color(0x1FFFFFFF), Color.White, Color(0xFFB8C0E0), Color(0x99070B1E),
    ) else ThemeSpec(
        style, false, accent, Color(0xFFF1F4FF), Color(0x99FFFFFF), Color(0xFF14172B), Color(0xFF5B6180), Color(0xB3FFFFFF),
    )

    ThemeStyle.GLASS -> if (dark) ThemeSpec(
        style, true, accent, Color(0xFF1A1033), Color(0x26FFFFFF), Color.White, Color(0xFFD9D2F2), Color(0x40FFFFFF),
    ) else ThemeSpec(
        style, false, accent, Color(0xFFDCD6FF), Color(0x73FFFFFF), Color(0xFF1C1433), Color(0xFF4F4670), Color(0x80FFFFFF),
    )

    ThemeStyle.NEUMORPHISM -> if (dark) ThemeSpec(
        style, true, accent, Color(0xFF2B2F36), Color(0xFF2B2F36), Color(0xFFE6E9EF), Color(0xFF9AA1AD), Color(0xFF2B2F36),
        lightShadow = Color(0x1AFFFFFF), darkShadow = Color(0x99000000),
    ) else ThemeSpec(
        style, false, accent, Color(0xFFE3E8EF), Color(0xFFE3E8EF), Color(0xFF3A4150), Color(0xFF7B8394), Color(0xFFE3E8EF),
        lightShadow = Color(0xFFFFFFFF), darkShadow = Color(0x55A3B1C6),
    )

    // Clear glass: panels let the song's colors (the background) show through, with a bright rim.
    ThemeStyle.LIQUID_GLASS -> if (dark) ThemeSpec(
        style, true, accent, Color(0xFF0C0E14), Color(0x1AFFFFFF), Color.White, Color(0xFFD0D4DE), Color(0x33FFFFFF),
    ) else ThemeSpec(
        style, false, accent, Color(0xFFEEF1F7), Color(0x8CFFFFFF), Color(0xFF111318), Color(0xFF4A505E), Color(0x99FFFFFF),
    )

    ThemeStyle.AMOLED -> ThemeSpec(
        style, true, accent, Color.Black, Color(0xFF0E0E0E), Color.White, Color(0xFF9E9E9E), Color.Black,
    )

    ThemeStyle.MATERIAL_YOU -> {
        val s = scheme ?: if (dark) darkColorScheme() else lightColorScheme()
        ThemeSpec(
            style, dark, s.primary, s.background, s.surfaceVariant, s.onSurface, s.onSurfaceVariant, s.surfaceContainer,
        )
    }
}

@Composable
fun SangeetTheme(settings: AppSettings, styleOverride: ThemeStyle? = null, content: @Composable () -> Unit) {
    val style = styleOverride ?: settings.theme
    val systemDark = isSystemInDarkTheme()
    val dark = when {
        style == ThemeStyle.AMOLED -> true
        settings.darkMode == DarkMode.SYSTEM -> systemDark
        else -> settings.darkMode == DarkMode.DARK
    }
    val context = LocalContext.current
    val dynamic = if (style == ThemeStyle.MATERIAL_YOU && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    } else null

    val accent = Color(settings.accent.argb)
    val spec = specFor(style, dark, accent, dynamic)

    val base = dynamic ?: if (dark) darkColorScheme() else lightColorScheme()
    val scheme = base.copy(
        primary = spec.accent,
        onPrimary = if (dark || style == ThemeStyle.SPOTIFY) Color.Black else Color.White,
        secondary = spec.accent,
        background = spec.background,
        onBackground = spec.onSurface,
        surface = spec.background,
        onSurface = spec.onSurface,
        surfaceVariant = spec.surface,
        onSurfaceVariant = spec.muted,
        surfaceContainer = spec.surface,
        // Sheets, menus and dialogs: a solid shade of this theme's own background (not the same grey everywhere).
        surfaceContainerHigh = sheetColor(spec, 0.09f),
        surfaceContainerHighest = sheetColor(spec, 0.14f),
        outline = spec.muted.copy(alpha = 0.5f),
    ).let { if (style == ThemeStyle.MATERIAL_YOU && dynamic != null) dynamic else it }

    CompositionLocalProvider(LocalThemeSpec provides spec) {
        MaterialTheme(colorScheme = scheme, typography = SangeetTypography, content = content)
    }
}

/** Solid sheet/dialog color from the theme background: a little lighter in dark mode, near white in light mode. */
private fun sheetColor(spec: ThemeSpec, lift: Float): Color {
    val base = spec.background
    val t = if (spec.isDark) lift else 0.75f - lift
    return Color(base.red + (1f - base.red) * t, base.green + (1f - base.green) * t, base.blue + (1f - base.blue) * t, 1f)
}

val SangeetTypography = Typography(
    displaySmall = TextStyle(fontWeight = FontWeight.Black, fontSize = 32.sp, letterSpacing = (-0.5).sp),
    headlineMedium = TextStyle(fontWeight = FontWeight.Bold, fontSize = 26.sp, letterSpacing = (-0.3).sp),
    headlineSmall = TextStyle(fontWeight = FontWeight.Bold, fontSize = 22.sp),
    titleLarge = TextStyle(fontWeight = FontWeight.Bold, fontSize = 20.sp),
    titleMedium = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 16.sp),
    titleSmall = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 14.sp),
    bodyLarge = TextStyle(fontSize = 16.sp),
    bodyMedium = TextStyle(fontSize = 14.sp),
    bodySmall = TextStyle(fontSize = 12.sp),
    labelLarge = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 14.sp),
    labelMedium = TextStyle(fontWeight = FontWeight.Medium, fontSize = 12.sp),
    labelSmall = TextStyle(fontWeight = FontWeight.Medium, fontSize = 11.sp),
)
