package com.sangeet.player.ui.theme

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.shadow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.sangeet.player.data.settings.ThemeStyle
import kotlin.math.cos
import kotlin.math.sin

/** Poori screen ka background, theme ke hisaab se. */
@Composable
fun ThemedBackground(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    val spec = Sangeet.spec
    Box(
        modifier
            .fillMaxSize()
            .background(spec.background)
            .then(
                when (spec.style) {
                    ThemeStyle.AURORA -> Modifier.auroraBackdrop(spec)
                    ThemeStyle.GLASS -> Modifier.glassBackdrop(spec)
                    ThemeStyle.CRYSTAL -> Modifier.glassBackdrop(spec)
                    ThemeStyle.NEON -> Modifier.neonBackdrop(spec.neonColors)
                    ThemeStyle.LIQUID_GLASS -> Modifier.liquidBackdrop(spec)
                    ThemeStyle.SPOTIFY -> Modifier.background(
                        Brush.verticalGradient(
                            0f to spec.accent.copy(alpha = if (spec.isDark) 0.22f else 0.12f),
                            0.35f to Color.Transparent,
                        )
                    )
                    else -> Modifier
                }
            ),
    ) {
        if (spec.style == ThemeStyle.CRYSTAL) CrystalBackdrop(spec)
        content()
    }
}

/**
 * Crystal: the playing song's cover fills the page, blurred, under a soft veil, so the clear tabs, buttons and
 * cards show its colors through them. With nothing playing, the colorful glass backdrop stays.
 */
@Composable
private fun BoxScope.CrystalBackdrop(spec: ThemeSpec) {
    val c = com.sangeet.player.ui.LocalAppContainer.current
    val state by c.player.state.collectAsStateWithLifecycle()
    val art = state.current?.artworkUrl ?: return
    // A tiny copy stretched over the screen is already soft; Android 12+ blurs it properly too.
    com.sangeet.player.ui.components.Artwork(
        art.replace("500x500", "150x150"),
        modifier = Modifier
            .matchParentSize()
            .then(if (android.os.Build.VERSION.SDK_INT >= 31) Modifier.blur(36.dp) else Modifier),
        shape = RectangleShape,
        seed = art,
    )
    Box(Modifier.matchParentSize().background(if (spec.isDark) Color.Black.copy(alpha = 0.42f) else Color.White.copy(alpha = 0.5f)))
}

/** Aurora: dheere-dheere ghoomte hue rang ke badal. */
private fun Modifier.auroraBackdrop(spec: ThemeSpec): Modifier = composed {
    val t = rememberInfiniteTransition(label = "aurora")
    val phase by t.animateFloat(
        initialValue = 0f,
        targetValue = (2 * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(tween(18_000, easing = LinearEasing), RepeatMode.Restart),
        label = "phase",
    )
    val a = if (spec.isDark) 0.55f else 0.35f
    val colors = listOf(
        Color(0xFF00E5A8).copy(alpha = a),
        spec.accent.copy(alpha = a),
        Color(0xFF7C4DFF).copy(alpha = a),
        Color(0xFFFF4FA3).copy(alpha = a * 0.8f),
    )
    drawBehind {
        val w = size.width
        val h = size.height
        colors.forEachIndexed { i, c ->
            val p = phase + i * 1.7f
            val center = Offset(
                w * (0.5f + 0.38f * cos(p * (1 + i * 0.15f))),
                h * (0.28f + 0.22f * sin(p * 0.8f + i)),
            )
            val radius = w * (0.75f + 0.1f * sin(p + i))
            drawCircle(Brush.radialGradient(listOf(c, Color.Transparent), center, radius), radius, center)
        }
    }
}

/** Glass: rangeen gradient + blobs jinke upar frosted cards dikhte hain. */
private fun Modifier.glassBackdrop(spec: ThemeSpec): Modifier = this
    .background(
        Brush.linearGradient(
            if (spec.isDark) listOf(Color(0xFF2B1055), Color(0xFF1A1033), Color(0xFF0F2A4A))
            else listOf(Color(0xFFE0C3FC), Color(0xFFC2D6FF), Color(0xFFFBD3E9))
        )
    )
    .drawBehind {
        val w = size.width
        val h = size.height
        val blobs = listOf(
            Triple(Offset(w * 0.15f, h * 0.18f), w * 0.55f, spec.accent),
            Triple(Offset(w * 0.9f, h * 0.45f), w * 0.5f, Color(0xFFFF6FB5)),
            Triple(Offset(w * 0.3f, h * 0.85f), w * 0.6f, Color(0xFF4FC3F7)),
        )
        blobs.forEach { (c, r, col) ->
            drawCircle(Brush.radialGradient(listOf(col.copy(alpha = 0.55f), Color.Transparent), c, r), r, c)
        }
    }

/** Neon Glass: the page goes from the palette's light top to its deep bottom, with four soft glows. */
private fun Modifier.neonBackdrop(p: NeonPalette): Modifier = this
    .background(Brush.verticalGradient(p.page))
    .drawBehind {
        val w = size.width
        val h = size.height
        val spots = listOf(
            Offset(w * 0.95f, h * 0.06f) to w * 0.7f,
            Offset(w * 0.0f, h * 0.42f) to w * 0.75f,
            Offset(w * 1.0f, h * 0.62f) to w * 0.7f,
            Offset(w * 0.15f, h * 0.98f) to w * 0.8f,
        )
        spots.zip(p.glows).forEach { (spot, col) ->
            val (c, r) = spot
            drawCircle(Brush.radialGradient(listOf(col, Color.Transparent), c, r), r, c)
        }
    }

/** Neon Glass colors of this theme (made from the accent). */
val ThemeSpec.neonColors: NeonPalette get() = neon ?: NeonPalette.from(accent)

/**
 * Liquid Glass: soft glows of the accent and two neighbouring colors under the page, so the glass rows and cards
 * have something to show through them (like the wallpaper behind iOS glass).
 */
private fun Modifier.liquidBackdrop(spec: ThemeSpec): Modifier {
    val parts = FloatArray(3)
    android.graphics.Color.colorToHSV(spec.accent.toArgb(), parts)
    fun hue(shift: Float, alpha: Float) = Color(
        android.graphics.Color.HSVToColor(floatArrayOf((((parts[0] + shift) % 360f) + 360f) % 360f, 0.55f, 1f))
    ).copy(alpha = alpha)
    val a = if (spec.isDark) 0.30f else 0.22f
    val glows = listOf(spec.accent.copy(alpha = a), hue(45f, a * 0.8f), hue(-50f, a * 0.7f))
    return drawBehind {
        val w = size.width
        val h = size.height
        val spots = listOf(Offset(w * 0.95f, h * 0.08f) to w * 0.75f, Offset(w * 0.0f, h * 0.48f) to w * 0.7f, Offset(w * 0.9f, h * 0.9f) to w * 0.8f)
        spots.zip(glows).forEach { (spot, col) ->
            val (c, r) = spot
            drawCircle(Brush.radialGradient(listOf(col, Color.Transparent), c, r), r, c)
        }
    }
}

/**
 * Neon Glass and Liquid Glass put every song row, card, tile and tool on glass (report #51: "jo neon glass theme
 * ka effect hai … same liquid glass theme ke liye bhi kar de").
 */
val ThemeSpec.glassEverywhere: Boolean get() = style == ThemeStyle.NEON || style == ThemeStyle.LIQUID_GLASS

/**
 * Liquid Glass on the page itself (rows, cards): a frosted pane, cool white in light mode and graphite in dark,
 * with a sheen over its top half and the bright rim of the floating glass. [strong] = less see-through (buttons).
 */
fun Modifier.liquidPane(spec: ThemeSpec, shape: Shape, strong: Boolean = false): Modifier = this
    .then(
        if (spec.isDark) Modifier
        else Modifier.shadow(3.dp, shape, ambientColor = Color.Black.copy(alpha = 0.08f), spotColor = Color.Black.copy(alpha = 0.14f))
    )
    .clip(shape)
    .background((if (spec.isDark) Color(0xFF2C2C2E) else Color(0xFFF7F9FC)).copy(alpha = if (strong) 0.78f else if (spec.isDark) 0.52f else 0.6f))
    .background(Brush.verticalGradient(0f to Color.White.copy(alpha = if (spec.isDark) 0.08f else 0.5f), 0.5f to Color.Transparent))
    .then(if (spec.isDark) Modifier else Modifier.border(1.dp, Color.Black.copy(alpha = 0.08f), shape))
    .border(
        1.dp,
        Brush.linearGradient(
            listOf(
                Color.White.copy(alpha = if (spec.isDark) 0.5f else 1f),
                Color.White.copy(alpha = if (spec.isDark) 0.04f else 0.15f),
                Color.White.copy(alpha = if (spec.isDark) 0.25f else 0.85f),
            )
        ),
        shape,
    )

/** This theme's glass panel: Neon's tinted glass or Liquid Glass's frosted pane. */
fun Modifier.glassPanel(spec: ThemeSpec, shape: Shape): Modifier =
    if (spec.style == ThemeStyle.NEON) neonGlass(shape) else liquidPane(spec, shape)

/** This theme's round glass button (play in rows, the heart on the cover, Now Playing's tools…). */
fun Modifier.glassCircle(spec: ThemeSpec): Modifier =
    if (spec.style == ThemeStyle.NEON) neonCircle() else liquidPane(spec, CircleShape, strong = true)

/** Icons and names on this theme's glass: white on Neon's dark glass, the page's text color on Liquid Glass. */
fun glassInk(spec: ThemeSpec): Color = if (spec.style == ThemeStyle.NEON) Color.White else spec.onSurface

/**
 * A Neon Glass panel: see-through and lighter at the top left, a soft shine over its upper half, and a bright
 * edge where the light catches it (top left) fading round the sides.
 */
fun Modifier.neonGlass(shape: Shape): Modifier = this
    .clip(shape)
    .background(Brush.linearGradient(listOf(Color.White.copy(alpha = 0.20f), Color.White.copy(alpha = 0.07f))))
    .background(Brush.verticalGradient(0f to Color.White.copy(alpha = 0.10f), 0.45f to Color.Transparent))
    .border(
        1.dp,
        Brush.linearGradient(listOf(Color.White.copy(alpha = 0.6f), Color.White.copy(alpha = 0.08f), Color.White.copy(alpha = 0.3f))),
        shape,
    )

/** Neon Glass's round glass button (play in rows and on covers, the heart on the cover…). */
fun Modifier.neonCircle(): Modifier = this
    .clip(CircleShape)
    .background(Brush.linearGradient(listOf(Color.White.copy(alpha = 0.34f), Color.White.copy(alpha = 0.14f))))
    .border(1.dp, Brush.linearGradient(listOf(Color.White.copy(alpha = 0.8f), Color.White.copy(alpha = 0.2f))), CircleShape)

/** Neon Glass's chosen tab / chip / play button: a glowing gradient with a bright rim. */
fun Modifier.neonFill(spec: ThemeSpec, shape: Shape, glow: Dp = 12.dp): Modifier {
    val p = spec.neonColors
    return this
        .shadow(glow, shape, ambientColor = p.fill[1], spotColor = p.fill[0])
        .clip(shape)
        .background(Brush.linearGradient(p.fill))
        .background(Brush.verticalGradient(0f to Color.White.copy(alpha = 0.18f), 0.5f to Color.Transparent))
        .border(1.dp, Brush.linearGradient(listOf(Color.White.copy(alpha = 0.65f), Color.White.copy(alpha = 0.1f), Color.White.copy(alpha = 0.35f))), shape)
}

/**
 * Card / tile ki styling. Har theme ka alag look:
 * Neumorphism = do shadow wala ubhra hua, Glass = frosted + border, baaki = flat.
 */
fun Modifier.themedCard(
    spec: ThemeSpec,
    shape: Shape = RoundedCornerShape(16.dp),
    corner: Dp = 16.dp,
    elevation: Dp = 6.dp,
): Modifier = when (spec.style) {
    ThemeStyle.NEUMORPHISM -> this
        .neumorphic(spec, corner, elevation)
        .clip(shape)
    ThemeStyle.GLASS, ThemeStyle.AURORA -> this
        .clip(shape)
        .background(spec.surface)
        .border(
            1.dp,
            Brush.linearGradient(
                listOf(Color.White.copy(alpha = if (spec.isDark) 0.35f else 0.8f), Color.White.copy(alpha = 0.05f))
            ),
            shape,
        )
    ThemeStyle.AMOLED -> this
        .clip(shape)
        .background(spec.surface)
        .border(1.dp, Color(0xFF1C1C1C), shape)
    // Crystal: clear, with only a bright hairline edge.
    ThemeStyle.CRYSTAL -> this
        .clip(shape)
        .background(spec.surface)
        .border(1.dp, if (spec.isDark) Color.White.copy(alpha = 0.3f) else Color.Black.copy(alpha = 0.12f), shape)
    ThemeStyle.NEON -> this.neonGlass(shape)
    // Liquid Glass: cards are glass too, like Neon Glass (report #51).
    ThemeStyle.LIQUID_GLASS -> this.liquidPane(spec, shape)
    else -> this
        .clip(shape)
        .background(spec.surface)
}

/** Neumorphic soft shadows (upar-baayen roshni, neeche-daayen parchhai). Android 9+ pe best dikhta hai. */
fun Modifier.neumorphic(spec: ThemeSpec, corner: Dp, elevation: Dp, pressed: Boolean = false): Modifier =
    drawBehind {
        val r = corner.toPx()
        val e = elevation.toPx()
        drawIntoCanvas { canvas ->
            val paint = Paint()
            val fp = paint.asFrameworkPaint()
            fp.isAntiAlias = true
            fp.color = spec.background.toArgb()
            if (!pressed) {
                fp.setShadowLayer(e * 1.6f, -e * 0.7f, -e * 0.7f, spec.lightShadow.toArgb())
                canvas.drawRoundRect(0f, 0f, size.width, size.height, r, r, paint)
                fp.setShadowLayer(e * 1.6f, e * 0.7f, e * 0.7f, spec.darkShadow.toArgb())
                canvas.drawRoundRect(0f, 0f, size.width, size.height, r, r, paint)
            } else {
                fp.setShadowLayer(e * 0.8f, e * 0.3f, e * 0.3f, spec.darkShadow.toArgb())
                canvas.drawRoundRect(0f, 0f, size.width, size.height, r, r, paint)
            }
            fp.clearShadowLayer()
            canvas.drawRoundRect(0f, 0f, size.width, size.height, r, r, paint)
        }
    }

/** Bade play button ka look. */
fun Modifier.playButtonStyle(spec: ThemeSpec, size: Dp): Modifier = when (spec.style) {
    ThemeStyle.NEUMORPHISM -> this.neumorphic(spec, size / 2, 8.dp)
    ThemeStyle.GLASS -> this
        .clip(RoundedCornerShape(50))
        .background(Color.White.copy(alpha = if (spec.isDark) 0.25f else 0.6f))
        .border(1.dp, Color.White.copy(alpha = 0.6f), RoundedCornerShape(50))
    ThemeStyle.AURORA -> this
        .clip(RoundedCornerShape(50))
        .background(Brush.linearGradient(listOf(spec.accent, Color(0xFF7C4DFF))))
    ThemeStyle.LIQUID_GLASS -> this
        .clip(RoundedCornerShape(50))
        .background(spec.accent)
        .glassRim(spec, RoundedCornerShape(50))
    ThemeStyle.CRYSTAL -> this
        .clip(RoundedCornerShape(50))
        .background(spec.onSurface.copy(alpha = 0.12f))
        .border(1.5.dp, spec.onSurface.copy(alpha = 0.7f), RoundedCornerShape(50))
    ThemeStyle.NEON -> this.neonFill(spec, RoundedCornerShape(50), glow = size / 4)
    else -> this
        .clip(RoundedCornerShape(50))
        .background(spec.accent)
}

/** Play button ke upar icon ka rang. */
fun playIconColor(spec: ThemeSpec): Color = when (spec.style) {
    ThemeStyle.NEUMORPHISM -> spec.accent
    ThemeStyle.GLASS -> if (spec.isDark) Color.White else Color(0xFF1C1433)
    ThemeStyle.AURORA -> Color.White
    ThemeStyle.CRYSTAL -> spec.onSurface
    ThemeStyle.NEON -> Color.White
    else -> if (spec.accent.luminance() > 0.45f) Color.Black else Color.White
}

private fun Color.luminance(): Float = 0.2126f * red + 0.7152f * green + 0.0722f * blue
