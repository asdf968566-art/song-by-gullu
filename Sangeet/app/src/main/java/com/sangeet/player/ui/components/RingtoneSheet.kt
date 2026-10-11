package com.sangeet.player.ui.components

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.sangeet.player.data.model.Track
import com.sangeet.player.data.ringtone.Ringtone
import com.sangeet.player.ui.LocalAppContainer
import com.sangeet.player.ui.theme.Sangeet
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Song menu → "Set as ringtone": pick the part (the chorus is chosen at first), hear it, set it. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RingtoneSheet(track: Track, onDone: () -> Unit) {
    val c = LocalAppContainer.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val spec = Sangeet.spec
    var progress by remember { mutableStateOf<Int?>(0) } // getting the song (null: ready or failed)
    var src by remember { mutableStateOf<Uri?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var length by remember { mutableLongStateOf(track.durationMs) }
    var start by remember { mutableLongStateOf(0L) }
    var use by remember { mutableStateOf(Ringtone.Use.RINGTONE) }
    var working by remember { mutableStateOf(false) }
    var made by remember { mutableStateOf<Pair<Long, Uri>?>(null) } // the saved part (its start) while Android asks
    var needAllow by remember { mutableStateOf(false) }
    var previewing by remember { mutableStateOf(false) }

    LaunchedEffect(track.id) {
        try {
            val uri = Ringtone.source(c, track) { p -> progress = p }
            val ms = withContext(Dispatchers.IO) { Ringtone.durationMs(context, uri) }.takeIf { it > 0 } ?: track.durationMs
            length = ms
            val lines = runCatching { c.lyrics.get(track)?.lines }.getOrNull().orEmpty()
            start = Ringtone.bestStart(lines, ms)
            src = uri
        } catch (e: Exception) {
            android.util.Log.w("Sangeet", "ringtone: ${e.javaClass.simpleName}: ${e.message}")
            error = e.message?.takeIf { it.isNotBlank() && !it.startsWith("HTTP") } ?: "Couldn't get this song now"
        }
        progress = null
    }

    // Hear the part with its own little player (the song that's playing is paused).
    val preview = remember { ExoPlayer.Builder(context).build() }
    DisposableEffect(preview) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_ENDED) previewing = false
            }
        }
        preview.addListener(listener)
        onDispose { preview.removeListener(listener); preview.release() }
    }
    fun hear() {
        val u = src ?: return
        if (c.player.state.value.isPlaying) c.player.setPlaying(false)
        val clip = MediaItem.ClippingConfiguration.Builder().setStartPositionMs(start).setEndPositionMs(start + Ringtone.CLIP_MS).build()
        preview.setMediaItem(MediaItem.Builder().setUri(u).setClippingConfiguration(clip).build())
        preview.prepare()
        preview.play()
        previewing = true
    }
    fun stop() { preview.stop(); previewing = false }

    fun setIt(uri: Uri) {
        runCatching { Ringtone.set(context, uri, use) }
            .onSuccess {
                Toast.makeText(context, "${use.label} set: ${track.title}", Toast.LENGTH_LONG).show()
                android.util.Log.i("Sangeet", "ringtone set (${use.name}): $uri")
                onDone()
            }
            .onFailure { error = "Couldn't set it: ${it.message}" }
    }
    fun make() {
        val u = src ?: return
        stop()
        working = true
        scope.launch {
            try {
                val uri = made?.takeIf { it.first == start }?.second ?: withContext(Dispatchers.IO) {
                    val dir = File(context.cacheDir, "ringtone-part")
                    val part = Ringtone.cut(context, u, start, Ringtone.CLIP_MS, dir)
                    try { Ringtone.save(context, part, track) } finally { part.delete() }
                }
                made = start to uri
                android.util.Log.i("Sangeet", "ringtone saved: $uri")
                if (Ringtone.canSet(context)) setIt(uri) else { needAllow = true }
            } catch (e: Exception) {
                android.util.Log.w("Sangeet", "ringtone: ${e.javaClass.simpleName}: ${e.message}")
                error = e.message ?: "Couldn't make the ringtone"
            }
            working = false
        }
    }
    // Android 8 and 9 put ringtones in shared storage, which needs this permission first.
    val storage = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (ok) make() else { error = "Allow storage to save the ringtone" }
    }
    fun tapSet() {
        val old = Build.VERSION.SDK_INT < Build.VERSION_CODES.Q
        if (old && ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            storage.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        } else make()
    }
    // Back from "Modify system settings": set it now.
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, e ->
            if (e == Lifecycle.Event.ON_RESUME && needAllow && Ringtone.canSet(context)) {
                needAllow = false
                made?.second?.let { setIt(it) }
            }
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }

    ModalBottomSheet(
        onDismissRequest = onDone,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Column(Modifier.navigationBarsPadding().padding(horizontal = 20.dp).padding(bottom = 20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Artwork(track.artworkUrl, size = 56.dp, seed = track.title)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("Set as ringtone", style = MaterialTheme.typography.titleMedium)
                    Text(track.title, style = MaterialTheme.typography.bodyMedium, color = spec.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            Spacer(Modifier.height(18.dp))
            val err = error
            when {
                err != null -> Text(err, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                progress != null -> {
                    Text("Getting the song… ${progress}%", color = spec.muted, style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(8.dp))
                    LinearProgressIndicator(progress = { (progress ?: 0) / 100f }, modifier = Modifier.fillMaxWidth(), color = spec.accent)
                }
                else -> {
                    val max = (length - Ringtone.CLIP_MS).coerceAtLeast(0L)
                    Text("${clock(start)} – ${clock((start + Ringtone.CLIP_MS).coerceAtMost(length.coerceAtLeast(Ringtone.CLIP_MS)))}", style = MaterialTheme.typography.titleLarge)
                    if (max > 0) {
                        Slider(
                            value = start.toFloat(),
                            onValueChange = { start = it.toLong() },
                            onValueChangeFinished = { if (previewing) hear() },
                            valueRange = 0f..max.toFloat(),
                            colors = SliderDefaults.colors(thumbColor = spec.accent, activeTrackColor = spec.accent),
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Ringtone.Use.entries.forEach { u ->
                            FilterChip(
                                selected = use == u,
                                onClick = { use = u },
                                label = { Text(u.label) },
                                shape = RoundedCornerShape(50),
                                colors = FilterChipDefaults.filterChipColors(selectedContainerColor = spec.selectedFill, selectedLabelColor = MaterialTheme.colorScheme.onPrimary),
                            )
                        }
                    }
                    Spacer(Modifier.height(14.dp))
                    if (needAllow) {
                        Text("Allow Sangeet to change system settings to set it.", style = MaterialTheme.typography.bodyMedium)
                        Spacer(Modifier.height(10.dp))
                        Button(onClick = { Ringtone.askToSet(context) }, modifier = Modifier.fillMaxWidth()) { Text("Allow") }
                    } else {
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            OutlinedButton(onClick = { if (previewing) stop() else hear() }, modifier = Modifier.weight(1f)) {
                                Icon(if (previewing) Icons.Rounded.Stop else Icons.Rounded.PlayArrow, null)
                                Spacer(Modifier.width(6.dp))
                                Text(if (previewing) "Stop" else "Listen")
                            }
                            Button(
                                onClick = { if (!working) tapSet() },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(containerColor = spec.accent),
                            ) {
                                if (working) CircularProgressIndicator(Modifier.width(18.dp).height(18.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                                else Text("Set")
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun clock(ms: Long): String {
    val s = ms / 1000
    return "%d:%02d".format(s / 60, s % 60)
}
