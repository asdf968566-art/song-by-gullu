package com.sangeet.player.ui.nowplaying

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitLongPressOrCancellation
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.rounded.DragHandle
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.zIndex
import com.sangeet.player.data.model.Track
import androidx.compose.material3.FilterChip
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sangeet.player.playback.SleepTimerMode
import com.sangeet.player.ui.LocalAppContainer
import com.sangeet.player.ui.components.Artwork
import com.sangeet.player.ui.theme.Sangeet
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QueueSheet(onDismiss: () -> Unit) {
    val c = LocalAppContainer.current
    val state by c.player.state.collectAsStateWithLifecycle()
    val spec = Sangeet.spec
    val listState = rememberLazyListState()
    val haptics = LocalHapticFeedback.current
    LaunchedEffect(Unit) { listState.scrollToItem(state.queueIndex.coerceAtLeast(0)) }
    // Report #55: long-press a song and drag it up or down. The rows move on screen while dragging; the queue
    // itself changes once, when the finger lifts.
    val rows = remember { mutableStateListOf<QueueRow>().apply { addAll(queueRows(state.queue)) } }
    val drag = remember { QueueDrag(listState, rows) { from, to -> c.player.move(from, to) } }
    LaunchedEffect(state.queue) {
        if (drag.key == null) {
            rows.clear()
            rows.addAll(queueRows(state.queue))
        }
    }
    val currentKey = queueRows(state.queue).getOrNull(state.queueIndex)?.key

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Text(
            "Queue • ${if (state.queue.size == 1) "1 song" else "${state.queue.size} songs"}",
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
        )
        LazyColumn(
            state = listState,
            modifier = Modifier
                .navigationBarsPadding()
                .heightIn(max = 600.dp)
                .pointerInput(Unit) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val long = awaitLongPressOrCancellation(down.id) ?: return@awaitEachGesture
                        if (!drag.start(long.position.y)) return@awaitEachGesture
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        // After the long press the finger's moves are taken first (Initial pass), so the list
                        // doesn't scroll instead, even when the first move is a big one.
                        while (true) {
                            val change = awaitPointerEvent(PointerEventPass.Initial).changes.firstOrNull { it.id == long.id } ?: break
                            if (change.changedToUpIgnoreConsumed()) {
                                change.consume()
                                break
                            }
                            val dy = change.positionChange().y
                            change.consume()
                            if (dy != 0f) drag.by(dy)
                        }
                        drag.end()
                    }
                },
        ) {
            itemsIndexed(rows, key = { _, r -> r.key }) { i, r ->
                val t = r.track
                val current = r.key == currentKey
                val dragged = r.key == drag.key
                Row(
                    Modifier
                        .then(
                            if (dragged) Modifier
                                .zIndex(1f)
                                .graphicsLayer { translationY = drag.offset }
                                .shadow(8.dp, RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.surfaceContainerHighest, RoundedCornerShape(12.dp))
                            else Modifier.animateItem()
                        )
                        .fillMaxWidth()
                        .background(if (current) spec.accent.copy(alpha = 0.12f) else androidx.compose.ui.graphics.Color.Transparent)
                        // The lift after a drag isn't a tap.
                        .clickable { if (drag.key == null) c.player.skipTo(i) }
                        .padding(start = 16.dp, top = 6.dp, bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Artwork(t.artworkUrl, size = 44.dp, seed = t.title)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            t.title,
                            color = if (current) spec.accent else MaterialTheme.colorScheme.onSurface,
                            style = MaterialTheme.typography.titleSmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(t.artist, color = spec.muted, style = MaterialTheme.typography.bodySmall, maxLines = 1)
                    }
                    if (!current) IconButton(onClick = { c.player.removeAt(i) }) { Icon(Icons.Rounded.Close, "Remove") }
                    Icon(Icons.Rounded.DragHandle, "Hold and drag to move", tint = spec.muted, modifier = Modifier.padding(horizontal = 12.dp))
                }
            }
        }
    }
}

/** A queue row; [key] stays the same while it moves (the same song twice gets #2). */
private class QueueRow(val key: String, val track: Track)

private fun queueRows(queue: List<Track>): List<QueueRow> {
    val seen = HashMap<String, Int>()
    return queue.map { t ->
        val n = (seen[t.id] ?: 0) + 1
        seen[t.id] = n
        QueueRow(if (n == 1) t.id else "${t.id}#$n", t)
    }
}

/** Long-press drag in the queue: the dragged row follows the finger and swaps places with the row it passes. */
private class QueueDrag(
    private val list: LazyListState,
    private val rows: MutableList<QueueRow>,
    private val onMove: (from: Int, to: Int) -> Unit,
) {
    var key by mutableStateOf<String?>(null)
        private set
    var offset by mutableFloatStateOf(0f)
        private set
    private var from = -1

    private fun at(index: Int) = list.layoutInfo.visibleItemsInfo.firstOrNull { it.index == index }

    fun start(y: Float): Boolean {
        val hit = list.layoutInfo.visibleItemsInfo.firstOrNull { y.toInt() in it.offset until it.offset + it.size } ?: return false
        if (hit.index !in rows.indices) return false
        from = hit.index
        key = rows[hit.index].key
        offset = 0f
        return true
    }

    fun by(dy: Float) {
        val k = key ?: return
        offset += dy
        val i = rows.indexOfFirst { it.key == k }
        val cur = at(i) ?: return
        // Near the top or bottom edge the list scrolls along.
        val top = cur.offset + offset
        val edge = 48f
        val scroll = when {
            top < list.layoutInfo.viewportStartOffset + edge -> -12f
            top + cur.size > list.layoutInfo.viewportEndOffset - edge -> 12f
            else -> 0f
        }
        if (scroll != 0f) offset += list.dispatchRawDelta(scroll)
        val mid = cur.offset + offset + cur.size / 2f
        val target = list.layoutInfo.visibleItemsInfo.firstOrNull {
            it.index != i && it.index in rows.indices && mid >= it.offset && mid < it.offset + it.size
        } ?: return
        rows.add(target.index, rows.removeAt(i))
        offset += cur.offset - target.offset
    }

    fun end() {
        val k = key ?: return
        val to = rows.indexOfFirst { it.key == k }
        key = null
        offset = 0f
        if (from >= 0 && to >= 0 && from != to) onMove(from, to)
        from = -1
    }
}

@Composable
fun SleepTimerDialog(onDismiss: () -> Unit) {
    val c = LocalAppContainer.current
    val sleep by c.player.sleep.collectAsStateWithLifecycle()
    val options = listOf(5, 10, 15, 30, 45, 60, 90)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Sleep timer") },
        text = {
            Column {
                if (sleep.mode == SleepTimerMode.MINUTES) {
                    val left = ((sleep.endsAt - System.currentTimeMillis()) / 60_000).coerceAtLeast(0)
                    Text("Running — about $left min left", color = Sangeet.spec.accent)
                }
                options.forEach { m ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { c.player.startSleepTimer(m); onDismiss() }
                            .padding(vertical = 10.dp),
                    ) { Text("$m minutes") }
                }
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { c.player.sleepAtEndOfTrack(); onDismiss() }
                        .padding(vertical = 10.dp),
                ) { Text("End of song") }
            }
        },
        confirmButton = {
            if (sleep.mode != SleepTimerMode.OFF) {
                TextButton(onClick = { c.player.cancelSleepTimer(); onDismiss() }) { Text("Turn off timer") }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}

@Composable
fun SpeedDialog(onDismiss: () -> Unit) {
    val c = LocalAppContainer.current
    val scope = rememberCoroutineScope()
    val settings by c.settings.settings.collectAsStateWithLifecycle()
    var speed by remember { mutableFloatStateOf(settings.playbackSpeed) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Playback speed: ${"%.2f".format(speed)}x") },
        text = {
            Column {
                Slider(value = speed, onValueChange = { speed = it }, valueRange = 0.5f..2f, steps = 5)
                // One tap-able chip per speed, all the same width so nothing gets cut off.
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(0.75f, 1f, 1.25f, 1.5f).forEach { v ->
                        FilterChip(
                            selected = speed == v,
                            onClick = { speed = v },
                            label = { Text(if (v == 1f) "1x" else "${v}x", maxLines = 1) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                c.scope.launch { c.settings.setPlaybackSpeed(speed) } // app scope: the dialog closes right away
                onDismiss()
            }) { Text("Apply") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
