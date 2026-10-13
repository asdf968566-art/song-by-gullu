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
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
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
    /** Neon Glass colors, made from the accent. */
    val neon: NeonPalette? = null,
)

val LocalThemeSpec = staticCompositionLocalOf {
    ThemeSpec(
        ThemeStyle.SPOTIFY, true, Color(0xFF1DB954), Color(0xFF121212), Color(0xFF1E1E1E),
        Color.White, Color(0xFFB3B3B3), Color(0xFF000000),
    )
}

/** The fill of a chosen chip or tab: the accent, clear glass on Crystal, glowing color on Neon Glass. */
val ThemeSpec.selectedFill: Color
    get() = when (style) {
        ThemeStyle.CRYSTAL -> onSurface.copy(alpha = 0.22f)
        ThemeStyle.NEON -> (neon ?: NeonPalette.from(accent)).solid
        else -> accent
    }

/**
 * Neon Glass (owner, Oct 13, from an Instagram reel's "Liquid Glass" music app): a deep gradient page with soft
 * glows, everything on frosted glass with a bright edge, and the chosen tab / chips / play button in a glowing
 * gradient. The owner wants the reel's design in any color ("same colour nhi, design waisa"), so every color is
 * made from the accent: Violet gives the reel's blue-to-violet page with pink hearts. Always dark.
 */
@Immutable
data class NeonPalette(
    /** The page, top to bottom. */
    val page: List<Color>,
    /** Soft glows over the page (with their alpha): top right, left, right, bottom left. */
    val glows: List<Color>,
    /** Chosen tab, chip and the big play button. */
    val fill: List<Color>,
    /** A single color for the same (chips, filled buttons). */
    val solid: Color,
    /** Icons that are on (like, shuffle) and the seek bar: the accent's partner color, bright on the page. */
    val accent: Color,
    val muted: Color,
) {
    companion object {
        private fun hsv(h: Float, s: Float, v: Float) =
            Color(android.graphics.Color.HSVToColor(floatArrayOf(((h % 360f) + 360f) % 360f, s, v)))

        /** Darkens [c] until white text on it stays easy to read. */
        private fun fit(c: Color, maxLuminance: Float): Color {
            var x = c
            repeat(40) {
                if (x.luminance() <= maxLuminance) return x
                x = Color(x.red * 0.93f, x.green * 0.93f, x.blue * 0.93f)
            }
            return x
        }

        fun from(accent: Color): NeonPalette {
            val parts = FloatArray(3)
            android.graphics.Color.colorToHSV(accent.toArgb(), parts)
            val h = parts[0]
            val fill = listOf(fit(hsv(h + 25, 0.70f, 1f), 0.30f), fit(hsv(h - 5, 0.80f, 0.90f), 0.16f))
            return NeonPalette(
                page = listOf(fit(hsv(h - 35, 0.80f, 0.78f), 0.11f), fit(hsv(h - 10, 0.75f, 0.62f), 0.065f), fit(hsv(h + 15, 0.80f, 0.38f), 0.03f)),
                glows = listOf(
                    hsv(h - 60, 0.75f, 1f).copy(alpha = 0.45f),
                    hsv(h + 25, 0.75f, 1f).copy(alpha = 0.45f),
                    hsv(h + 50, 0.75f, 1f).copy(alpha = 0.35f),
                    hsv(h + 80, 0.70f, 1f).copy(alpha = 0.28f),
                ),
                fill = fill,
                solid = lerp(fill[0], fill[1], 0.5f),
                accent = hsv(h + 65, 0.55f, 1f),
                muted = hsv(h - 20, 0.15f, 0.97f),
            )
        }
    }
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

    // Liquid Glass (iOS 26): plain system background and cards; the glass is only on the floating controls.
    ThemeStyle.LIQUID_GLASS -> if (dark) ThemeSpec(
        style, true, accent, Color(0xFF000000), Color(0xFF1C1C1E), Color.White, Color(0xFF98989F), Color(0x00000000),
    ) else ThemeSpec(
        style, false, accent, Color(0xFFF2F2F7), Color(0xFFFFFFFF), Color(0xFF000000), Color(0xFF6C6C70), Color(0x00000000),
    )

    // Crystal (owner, Oct 10: "sb kuch transparent, saare tab saare button"): every surface is clear glass with a
    // hairline edge; the page behind is the playing song's cover, blurred (Surfaces.crystalBackdrop).
    ThemeStyle.CRYSTAL -> if (dark) ThemeSpec(
        style, true, accent, Color(0xFF0B0E1A), Color(0x14FFFFFF), Color.White, Color(0xB3FFFFFF), Color(0x00000000),
    ) else ThemeSpec(
        style, false, accent, Color(0xFFEFF3FA), Color(0x0F000000), Color(0xFF0E1222), Color(0xFF4A5068), Color(0x00000000),
    )

    ThemeStyle.AMOLED -> ThemeSpec(
        style, true, accent, Color.Black, Color(0xFF0E0E0E), Color.White, Color(0xFF9E9E9E), Color.Black,
    )

    ThemeStyle.NEON -> NeonPalette.from(accent).let { n ->
        ThemeSpec(style, true, n.accent, n.page[1], Color(0x24FFFFFF), Color.White, n.muted, Color(0x00000000), neon = n)
    }

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
        style == ThemeStyle.AMOLED || style == ThemeStyle.NEON -> true
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
    // Crystal: filled buttons and chosen chips are clear glass too, with the page's own text color on them.
    val crystal = style == ThemeStyle.CRYSTAL
    val neon = style == ThemeStyle.NEON
    val scheme = base.copy(
        primary = if (crystal || neon) spec.selectedFill else spec.accent,
        onPrimary = if (crystal) spec.onSurface else if (neon) Color.White else if (dark || style == ThemeStyle.SPOTIFY) Color.Black else Color.White,
        secondary = spec.accent,
        background = spec.background,
        onBackground = spec.onSurface,
        surface = spec.background,
        onSurface = spec.onSurface,
        surfaceVariant = spec.surface,
        onSurfaceVariant = spec.muted,
        surfaceContainer = spec.surface,
        // Sheets, menus and dialogs: a solid shade of this theme's own background (not the same grey everywhere).
        surfaceContainerHigh = sheetColor(spec, 0.09f).let { if (crystal) it.copy(alpha = 0.9f) else it },
        surfaceContainerHighest = sheetColor(spec, 0.14f).let { if (crystal) it.copy(alpha = 0.92f) else it },
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
