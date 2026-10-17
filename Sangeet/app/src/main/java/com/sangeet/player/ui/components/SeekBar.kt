package com.sangeet.player.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.sangeet.player.playback.PlaybackPosition

/**
 * The player's seek bar (report #55): a rounded track that shows how much of the song has loaded, the played part as a
 * glowing gradient of [color], and a round thumb that grows while it is dragged. Tap or drag to seek.
 * [note] (e.g. "Waiting for the internet…") sits between the two times, so it never moves the page.
 */
@Composable
fun SeekBar(
    position: PlaybackPosition,
    onSeek: (Long) -> Unit,
    color: Color,
    textColor: Color,
    modifier: Modifier = Modifier,
    note: String? = null,
) {
    var dragging by remember { mutableStateOf(false) }
    var dragValue by remember { mutableFloatStateOf(0f) }
    val dur = position.durationMs.coerceAtLeast(1)
    val seek by rememberUpdatedState<(Float) -> Unit>({ f -> onSeek((f.coerceIn(0f, 1f) * dur).toLong()) })
    val played = if (dragging) dragValue else (position.positionMs.toFloat() / dur).coerceIn(0f, 1f)
    val loaded = (position.bufferedMs.toFloat() / dur).coerceIn(0f, 1f).coerceAtLeast(played)
    val grow by animateFloatAsState(if (dragging) 1f else 0f, tween(160), label = "seekGrow")
    val light = remember(color) { lerp(color, Color.White, 0.45f) }
    val muted = textColor.copy(alpha = 0.7f)

    Column(modifier.fillMaxWidth()) {
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(28.dp)
                .pointerInput(Unit) { detectTapGestures { o -> seek(o.x / size.width) } }
                .pointerInput(Unit) {
                    detectHorizontalDragGestures(
                        onDragStart = { o -> dragging = true; dragValue = (o.x / size.width).coerceIn(0f, 1f) },
                        onDragEnd = { seek(dragValue); dragging = false },
                        onDragCancel = { dragging = false },
                    ) { change, _ ->
                        change.consume()
                        dragValue = (change.position.x / size.width).coerceIn(0f, 1f)
                    }
                }
                .semantics {
                    contentDescription = "Seek bar"
                    progressBarRangeInfo = ProgressBarRangeInfo(played, 0f..1f)
                },
        ) {
            val h = (5.dp + 3.dp * grow).toPx()
            val top = size.height / 2 - h / 2
            val corner = CornerRadius(h / 2)
            val w = size.width
            // Track, then the part that has loaded, then the played part.
            drawRoundRect(textColor.copy(alpha = 0.16f), Offset(0f, top), Size(w, h), corner)
            if (loaded > 0f) drawRoundRect(textColor.copy(alpha = 0.34f), Offset(0f, top), Size(w * loaded, h), corner)
            val x = w * played
            if (x > 0f) {
                // A soft glow under the played part.
                val gh = h * 2.4f
                drawRoundRect(color.copy(alpha = 0.22f), Offset(0f, size.height / 2 - gh / 2), Size(x, gh), CornerRadius(gh / 2))
                drawRoundRect(
                    Brush.horizontalGradient(listOf(light, color), startX = 0f, endX = x.coerceAtLeast(1f)),
                    Offset(0f, top), Size(x, h), corner,
                )
            }
            val center = Offset(x, size.height / 2)
            drawCircle(color.copy(alpha = 0.25f + 0.15f * grow), (10.dp + 5.dp * grow).toPx(), center)
            drawCircle(Color.White, (6.dp + 2.dp * grow).toPx(), center)
            drawCircle(color, (6.dp + 2.dp * grow).toPx(), center, style = Stroke(2.dp.toPx()))
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 2.dp)) {
            Text(
                formatDuration(if (dragging) (dragValue * dur).toLong() else position.positionMs),
                color = if (dragging) color else muted,
                style = MaterialTheme.typography.labelSmall,
            )
            Text(
                note?.let { "⚠ $it" } ?: "",
                color = Color(0xFFFF8A80),
                style = MaterialTheme.typography.labelSmall,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
            )
            Text(formatDuration(position.durationMs), color = muted, style = MaterialTheme.typography.labelSmall)
        }
    }
}
