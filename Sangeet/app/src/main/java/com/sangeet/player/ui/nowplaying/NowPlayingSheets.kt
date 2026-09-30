package com.sangeet.player.ui.nowplaying

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
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
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
    LaunchedEffect(Unit) { listState.scrollToItem(state.queueIndex.coerceAtLeast(0)) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Text(
            "Queue • ${state.queue.size} gaane",
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
        )
        LazyColumn(state = listState, modifier = Modifier.navigationBarsPadding().heightIn(max = 600.dp)) {
            itemsIndexed(state.queue, key = { i, t -> "$i-${t.id}" }) { i, t ->
                val current = i == state.queueIndex
                Row(
                    Modifier
                        .fillMaxWidth()
                        .background(if (current) spec.accent.copy(alpha = 0.12f) else androidx.compose.ui.graphics.Color.Transparent)
                        .clickable { c.player.skipTo(i) }
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
                    if (i > 0) IconButton(onClick = { c.player.move(i, i - 1) }) { Icon(Icons.Rounded.KeyboardArrowUp, "Upar") }
                    if (i < state.queue.lastIndex) IconButton(onClick = { c.player.move(i, i + 1) }) { Icon(Icons.Rounded.KeyboardArrowDown, "Neeche") }
                    if (!current) IconButton(onClick = { c.player.removeAt(i) }) { Icon(Icons.Rounded.Close, "Hatao") }
                }
            }
        }
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
                    Text("Chal raha hai — lagbhag $left min baaki", color = Sangeet.spec.accent)
                }
                options.forEach { m ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { c.player.startSleepTimer(m); onDismiss() }
                            .padding(vertical = 10.dp),
                    ) { Text("$m minute") }
                }
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { c.player.sleepAtEndOfTrack(); onDismiss() }
                        .padding(vertical = 10.dp),
                ) { Text("Gaana khatam hone par") }
            }
        },
        confirmButton = {
            if (sleep.mode != SleepTimerMode.OFF) {
                TextButton(onClick = { c.player.cancelSleepTimer(); onDismiss() }) { Text("Timer band karo") }
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
                Row(verticalAlignment = Alignment.CenterVertically) {
                    listOf(0.75f, 1f, 1.25f, 1.5f).forEach { v ->
                        RadioButton(selected = speed == v, onClick = { speed = v })
                        Text("${v}x")
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                scope.launch { c.settings.setPlaybackSpeed(speed) }
                onDismiss()
            }) { Text("Lagao") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
