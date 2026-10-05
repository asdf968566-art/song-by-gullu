package com.sangeet.player.ui.theme

import android.os.Build
import androidx.compose.animation.Crossfade
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.sangeet.player.data.settings.ThemeStyle
import com.sangeet.player.ui.LocalAppContainer
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
                    ThemeStyle.SPOTIFY -> Modifier.background(
                        Brush.verticalGradient(
                            0f to spec.accent.copy(alpha = if (spec.isDark) 0.22f else 0.12f),
                            0.35f to Color.Transparent,
                        )
                    )
                    ThemeStyle.LIQUID_GLASS -> Modifier.liquidBlobs(spec)
                    else -> Modifier
                }
            ),
    ) {
        if (spec.style == ThemeStyle.LIQUID_GLASS) LiquidBackdrop(spec)
        content()
    }
}

/**
 * Liquid Glass: the playing song's cover, hugely soft, fills the screen and the glass panels sit on it,
 * so every page takes the song's colors. A tiny (24 px) copy stretched up is blurry by itself and cheap.
 */
@Composable
private fun BoxScope.LiquidBackdrop(spec: ThemeSpec) {
    val state by LocalAppContainer.current.player.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    Crossfade(state.current?.artworkUrl, animationSpec = tween(700), modifier = Modifier.matchParentSize(), label = "liquid") { url ->
        if (url != null) {
            AsyncImage(
                model = ImageRequest.Builder(context).data(url.replace("500x500", "50x50")).size(24).build(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .then(if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) Modifier.blur(24.dp) else Modifier)
                    .graphicsLayer { alpha = if (spec.isDark) 0.9f else 0.75f },
            )
        }
    }
    // A veil so text stays easy to read on any cover.
    Box(
        Modifier
            .matchParentSize()
            .background(
                if (spec.isDark) Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.30f), Color.Black.copy(alpha = 0.50f), Color.Black.copy(alpha = 0.72f)))
                else Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.40f), Color.White.copy(alpha = 0.52f), Color.White.copy(alpha = 0.70f)))
            )
    )
}

/** Liquid Glass when nothing is playing yet: soft pools of color. */
private fun Modifier.liquidBlobs(spec: ThemeSpec): Modifier = this
    .background(
        Brush.linearGradient(
            if (spec.isDark) listOf(Color(0xFF101A2E), Color(0xFF0C0E14), Color(0xFF231231))
            else listOf(Color(0xFFDDE8FF), Color(0xFFF4F1FF), Color(0xFFFFE3EC))
        )
    )
    .drawBehind {
        val w = size.width
        val h = size.height
        listOf(
            Triple(Offset(w * 0.1f, h * 0.12f), w * 0.7f, spec.accent),
            Triple(Offset(w * 0.95f, h * 0.5f), w * 0.6f, Color(0xFF38BDF8)),
            Triple(Offset(w * 0.25f, h * 0.92f), w * 0.7f, Color(0xFFF472B6)),
        ).forEach { (c, r, col) ->
            drawCircle(Brush.radialGradient(listOf(col.copy(alpha = if (spec.isDark) 0.35f else 0.45f), Color.Transparent), c, r), r, c)
        }
    }

/** A clear glass panel: see-through fill, a light sheen on top and a bright rim that catches the light. */
fun Modifier.liquidGlass(spec: ThemeSpec, shape: Shape): Modifier = this
    .clip(shape)
    .background(spec.surface)
    .background(Brush.verticalGradient(0f to Color.White.copy(alpha = if (spec.isDark) 0.10f else 0.35f), 0.6f to Color.Transparent))
    .border(
        1.dp,
        Brush.linearGradient(
            listOf(
                Color.White.copy(alpha = if (spec.isDark) 0.55f else 0.95f),
                Color.White.copy(alpha = 0.04f),
                Color.White.copy(alpha = if (spec.isDark) 0.22f else 0.5f),
            )
        ),
        shape,
    )

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
    ThemeStyle.LIQUID_GLASS -> this.liquidGlass(spec, shape)
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
        .background(spec.accent.copy(alpha = 0.85f))
        .liquidGlass(spec.copy(surface = Color.Transparent), RoundedCornerShape(50))
    else -> this
        .clip(RoundedCornerShape(50))
        .background(spec.accent)
}

/** Play button ke upar icon ka rang. */
fun playIconColor(spec: ThemeSpec): Color = when (spec.style) {
    ThemeStyle.NEUMORPHISM -> spec.accent
    ThemeStyle.GLASS -> if (spec.isDark) Color.White else Color(0xFF1C1433)
    ThemeStyle.AURORA -> Color.White
    else -> if (spec.accent.luminance() > 0.45f) Color.Black else Color.White
}

private fun Color.luminance(): Float = 0.2126f * red + 0.7152f * green + 0.0722f * blue
