package com.sangeet.player.ui.theme

import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.RenderEffect
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/*
 * Liquid Glass, as Apple describes it: glass is only for the controls that float above the content
 * (tab bar, mini player, round buttons). It shows the content scrolling underneath, blurred a little,
 * more colorful, and bent like a lens near its edges, with a bright rim where light catches it.
 *
 * How: the page content is recorded once per frame into a layer (glassSource). Each glass control draws
 * that layer again, shifted to its own position, through a RenderEffect: blur + saturation, and on
 * Android 13+ an AGSL lens shader. Older phones get a plain see-through tint.
 */

/** What the floating glass controls see through: the page content. */
@Stable
class GlassSource {
    internal var layer: GraphicsLayer? = null
    internal var origin by mutableStateOf(Offset.Zero)
    /** Bumped every time the content redraws, so the glass redraws with it (scrolling). */
    internal var frame by mutableIntStateOf(0)
}

val LocalGlassSource = staticCompositionLocalOf<GlassSource?> { null }

/** Room the floating bars take at the bottom; scrolling pages add it so their last row isn't hidden. */
val LocalBottomBarSpace = compositionLocalOf { 0.dp }

/** Bottom padding for a page's list (0 unless the bars float over the content). */
@Composable
fun bottomBarPadding(): PaddingValues = PaddingValues(bottom = LocalBottomBarSpace.current)

@Composable
fun rememberGlassSource(): GlassSource = remember { GlassSource() }

/** Records this content so glass controls above it can show it. */
fun Modifier.glassSource(source: GlassSource): Modifier = composed {
    val layer = rememberGraphicsLayer()
    this
        .onGloballyPositioned { source.origin = it.positionInRoot() }
        .drawWithContent {
            layer.record { this@drawWithContent.drawContent() }
            drawLayer(layer)
            source.layer = layer
            // Not read here (no redraw loop), only by the glass.
            Snapshot.withoutReadObservation { source.frame++ }
        }
}

/**
 * A Liquid Glass control surface. [clear] = the more see-through variant (over busy media).
 * Falls back to a tinted surface when there is no recorded content (e.g. a preview) or on old Android.
 */
fun Modifier.liquidGlass(
    spec: ThemeSpec,
    shape: Shape,
    blur: Dp = 4.dp,
    lens: Dp = 16.dp,
    clear: Boolean = false,
): Modifier = composed {
    val source = LocalGlassSource.current
    val glass = rememberGraphicsLayer()
    var pos by remember { mutableStateOf(Offset.Zero) }
    val cache = remember { EffectCache() }
    this
        // Light mode needs a clearer shadow: white glass on a light page otherwise disappears.
        .shadow(
            if (spec.isDark) 10.dp else 14.dp,
            shape,
            ambientColor = Color.Black.copy(alpha = if (spec.isDark) 0.18f else 0.22f),
            spotColor = Color.Black.copy(alpha = if (spec.isDark) 0.22f else 0.30f),
        )
        .onGloballyPositioned { pos = it.positionInRoot() }
        .drawWithContent {
            val outline = shape.createOutline(size, layoutDirection, this)
            val path = outline.toPath()
            val content = source?.layer
            val effects = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
            if (source != null && content != null) {
                source.frame.let { } // read it: redraw when the content does
                val radius = outline.cornerRadius(size)
                glass.renderEffect = if (effects) cache.get(size, radius, blur.toPx(), lens.toPx()) else null
                val dx = source.origin.x - pos.x
                val dy = source.origin.y - pos.y
                glass.record { translate(dx, dy) { drawLayer(content) } }
                clipPath(path) { drawLayer(glass) }
            }
            // Tint: keeps text readable; stronger where the content can't be blurred.
            val tintAlpha = when {
                content == null || !effects -> if (spec.isDark) 0.72f else 0.78f
                clear -> if (spec.isDark) 0.12f else 0.30f
                else -> if (spec.isDark) 0.28f else 0.55f
            }
            // Light glass is slightly cool (like iOS), so it reads as glass and not as a white card.
            drawOutline(outline, (if (spec.isDark) Color(0xFF1C1C1E) else Color(0xFFF7F9FC)).copy(alpha = tintAlpha))
            if (!spec.isDark) {
                // A soft darker band along the bottom edge gives the light glass its thickness.
                drawOutline(outline, Brush.verticalGradient(0.55f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.06f)))
            }
            // Sheen across the top, and the bright rim where light catches the edge.
            drawOutline(outline, Brush.verticalGradient(0f to Color.White.copy(alpha = if (spec.isDark) 0.10f else 0.65f), 0.5f to Color.Transparent))
            if (!spec.isDark) {
                // White rim can't be seen on a light page: a thin dark hairline outlines the glass first.
                drawOutline(outline, Color.Black.copy(alpha = 0.10f), style = Stroke(1.dp.toPx()))
            }
            drawOutline(
                outline,
                Brush.linearGradient(
                    0f to Color.White.copy(alpha = if (spec.isDark) 0.55f else 1f),
                    0.45f to Color.White.copy(alpha = if (spec.isDark) 0.04f else 0.25f),
                    1f to Color.White.copy(alpha = if (spec.isDark) 0.28f else 0.9f),
                    start = Offset.Zero,
                    end = Offset(size.width, size.height),
                ),
                style = Stroke(if (spec.isDark) 1.2.dp.toPx() else 1.6.dp.toPx()),
            )
            drawContent()
        }
}

/** The rim + sheen alone (no content behind), for a prominent glass button filled with a color. */
fun Modifier.glassRim(spec: ThemeSpec, shape: Shape): Modifier = drawWithContent {
    val outline = shape.createOutline(size, layoutDirection, this)
    drawContent()
    drawOutline(outline, Brush.verticalGradient(0f to Color.White.copy(alpha = 0.28f), 0.55f to Color.Transparent))
    drawOutline(
        outline,
        Brush.linearGradient(
            listOf(Color.White.copy(alpha = 0.85f), Color.White.copy(alpha = 0.05f), Color.White.copy(alpha = 0.4f)),
            start = Offset.Zero,
            end = Offset(size.width, size.height),
        ),
        style = Stroke(1.2.dp.toPx()),
    )
}

private fun Outline.toPath(): Path = when (this) {
    is Outline.Rectangle -> Path().apply { addRect(rect) }
    is Outline.Rounded -> Path().apply { addRoundRect(roundRect) }
    is Outline.Generic -> path
}

private fun Outline.cornerRadius(size: Size): Float = when (this) {
    is Outline.Rounded -> roundRect.topLeftCornerRadius.x
    else -> 0f
}.coerceAtMost(minOf(size.width, size.height) / 2f)

/** The RenderEffect depends only on the size and settings: build it once per size. */
private class EffectCache {
    private var key: List<Float>? = null
    private var effect: RenderEffect? = null

    fun get(size: Size, radius: Float, blur: Float, lens: Float): RenderEffect? {
        val k = listOf(size.width, size.height, radius, blur, lens)
        if (k != key) {
            key = k
            effect = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) build(size, radius, blur, lens) else null
        }
        return effect
    }

    @RequiresApi(Build.VERSION_CODES.S)
    private fun build(size: Size, radius: Float, blur: Float, lens: Float): RenderEffect {
        // Glass makes what's behind it a little more colorful.
        val vivid = android.graphics.RenderEffect.createColorFilterEffect(
            ColorMatrixColorFilter(ColorMatrix().apply { setSaturation(1.6f) })
        )
        var effect = if (blur > 0f) {
            android.graphics.RenderEffect.createChainEffect(
                vivid,
                android.graphics.RenderEffect.createBlurEffect(blur, blur, Shader.TileMode.CLAMP),
            )
        } else vivid
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && lens > 0f && size.minDimension > 0f) {
            effect = android.graphics.RenderEffect.createChainEffect(lensEffect(size, radius, lens), effect)
        }
        return effect.asComposeRenderEffect()
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private fun lensEffect(size: Size, radius: Float, lens: Float): android.graphics.RenderEffect {
        val shader = RuntimeShader(LENS).apply {
            setFloatUniform("size", size.width, size.height)
            setFloatUniform("radius", radius)
            setFloatUniform("height", minOf(lens, size.minDimension / 2f))
            setFloatUniform("amount", lens * 1.6f)
        }
        return android.graphics.RenderEffect.createRuntimeShaderEffect(shader, "content")
    }
}

/**
 * Lens: near the rounded edge, pixels are taken from further inside, so the content behind looks
 * magnified and bent along the edge (like the thick rim of a glass drop). The middle is left as is.
 */
private const val LENS = """
uniform shader content;
uniform float2 size;
uniform float radius;
uniform float height;
uniform float amount;

float sd(float2 p, float2 b, float r) {
    float2 q = abs(p) - b + float2(r);
    return min(max(q.x, q.y), 0.0) + length(max(q, float2(0.0))) - r;
}

half4 main(float2 coord) {
    float2 c = size * 0.5;
    float2 p = coord - c;
    float d = sd(p, c, radius);
    float t = clamp(1.0 + d / height, 0.0, 1.0);
    float2 n = float2(
        sd(p + float2(1.0, 0.0), c, radius) - sd(p - float2(1.0, 0.0), c, radius),
        sd(p + float2(0.0, 1.0), c, radius) - sd(p - float2(0.0, 1.0), c, radius));
    float l = length(n);
    n = l > 0.0 ? n / l : float2(0.0);
    return content.eval(coord - n * (t * t * amount));
}
"""
