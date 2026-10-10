package com.sangeet.player.ui.nowplaying

import android.graphics.Bitmap
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Lyrics
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.Player
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.sangeet.player.data.lyrics.Lyrics
import com.sangeet.player.playback.SleepTimerMode
import com.sangeet.player.ui.LocalAppContainer
import com.sangeet.player.ui.components.Artwork
import com.sangeet.player.ui.components.TrackOptionsSheet
import com.sangeet.player.ui.components.formatDuration
import com.sangeet.player.ui.theme.Sangeet
import com.sangeet.player.ui.theme.playButtonStyle
import com.sangeet.player.ui.theme.playIconColor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.ui.graphics.lerp
import com.sangeet.player.data.settings.ThemeStyle
import com.sangeet.player.ui.theme.ThemedBackground
import com.sangeet.player.ui.theme.themedCard
import androidx.compose.material.icons.rounded.DirectionsCar
import com.sangeet.player.ui.components.ShareCard

/** Poori screen ka player (Spotify jaisa): cover ke rang ka gradient, seek bar, lyrics card. */
@Composable
fun NowPlayingScreen(onCollapse: () -> Unit) {
    val c = LocalAppContainer.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val spec = Sangeet.spec
    val state by c.player.state.collectAsStateWithLifecycle()
    val favorites by c.library.favoriteIds.collectAsStateWithLifecycle()
    val downloads by c.downloads.downloads.collectAsStateWithLifecycle()
    val settings by c.settings.settings.collectAsStateWithLifecycle()
    val network by c.network.status.collectAsStateWithLifecycle()
    val sleep by c.player.sleep.collectAsStateWithLifecycle()
    val pos by c.player.position.collectAsStateWithLifecycle()
    val track = state.current ?: return

    var lyrics by remember(track.id) { mutableStateOf<Lyrics?>(null) }
    var lyricsLoading by remember(track.id) { mutableStateOf(true) }
    var lyricsFull by remember { mutableStateOf(false) }
    var showQueue by remember { mutableStateOf(false) }
    var showSleep by remember { mutableStateOf(false) }
    var showSpeed by remember { mutableStateOf(false) }
    var showOptions by remember { mutableStateOf(false) }
    var showLyricsMenu by remember { mutableStateOf(false) }
    var carMode by remember { mutableStateOf(false) }
    var singAlong by remember { mutableStateOf(false) }
    val room by c.together.room.collectAsStateWithLifecycle()
    // Long-press a lyrics line: its meaning, or share it on the song's cover as a picture.
    var lineMenu by remember { mutableStateOf<String?>(null) }
    val shareLine: (String) -> Unit = { line -> lineMenu = line }
    lineMenu?.let { line ->
        LyricLineDialog(
            line, track,
            onShare = { c.scope.launch { runCatching { ShareCard.share(context, track, line) } } },
            onDismiss = { lineMenu = null },
        )
    }
    var dominant by remember(track.id) { mutableStateOf(spec.accent) }
    var dragging by remember { mutableStateOf(false) }
    var dragValue by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(track.id) {
        lyricsLoading = true
        lyrics = runCatching { c.lyrics.get(track, allowOnline = settings.autoLyrics) }.getOrNull()
        lyricsLoading = false
    }
    LaunchedEffect(track.artworkUrl) {
        dominant = dominantColor(context, track.artworkUrl) ?: spec.accent
    }
    // The cover's color over this theme's background; softened in light mode so dark text stays readable.
    val tint = if (spec.isDark) dominant else lerp(dominant, Color.White, 0.55f)
    val bg by animateColorAsState(tint, tween(600), label = "bg")

    val lrcPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            val text = withContext(Dispatchers.IO) {
                runCatching { context.contentResolver.openInputStream(uri)?.use { it.readBytes().decodeToString() } }.getOrNull()
            }
            if (text.isNullOrBlank()) {
                Toast.makeText(context, "File is empty", Toast.LENGTH_SHORT).show()
            } else {
                c.lyrics.save(track.id, text, "Imported .lrc")
                lyrics = c.lyrics.get(track, allowOnline = false)
                Toast.makeText(context, "Lyrics saved (available offline)", Toast.LENGTH_SHORT).show()
            }
        }
    }

    if (showQueue) QueueSheet { showQueue = false }
    if (showSleep) SleepTimerDialog { showSleep = false }
    if (showSpeed) SpeedDialog { showSpeed = false }
    if (showOptions) TrackOptionsSheet(track, onDismiss = { showOptions = false })

    val textColor = if (spec.isDark) Color.White else spec.onSurface
    val muted = textColor.copy(alpha = 0.7f)
    val glassy = spec.style in setOf(ThemeStyle.GLASS, ThemeStyle.AURORA, ThemeStyle.NEUMORPHISM)
    val quality = c.online.streamingQuality()
    val qualityText = when {
        downloads[track.id]?.filePath != null -> "Downloaded • ${downloads[track.id]?.quality?.label}"
        !track.source.isOnline -> "Phone file"
        else -> "${c.online.qualityLabel(track, quality)} • ${if (network.unmetered) "Wi-Fi" else "Mobile data"}"
    }

    // Same theme as every other page, with the cover's color on top (like Apple Music).
    ThemedBackground(
        // Neeche ki screen tak touch na jaye.
        Modifier.clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {},
    ) {
        val top = if (spec.isDark) 0.9f else 0.7f
        Box(Modifier.matchParentSize().background(Brush.verticalGradient(listOf(bg.copy(alpha = top), bg.copy(alpha = top * 0.45f), Color.Transparent))))
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .let { if (lyricsFull) it else it.verticalScroll(rememberScrollState()) },
        ) {
            // Top bar
            Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { if (lyricsFull) lyricsFull = false else onCollapse() }) {
                    Icon(Icons.Rounded.KeyboardArrowDown, "Close", tint = textColor, modifier = Modifier.size(32.dp))
                }
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        room?.let { "LISTENING TOGETHER · ${it.code}" } ?: "PLAYING FROM ${track.source.label.uppercase()}",
                        color = if (room != null) spec.accent else muted, style = MaterialTheme.typography.labelSmall,
                    )
                    Text(
                        track.album.ifBlank { track.artist },
                        color = textColor,
                        style = MaterialTheme.typography.labelLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (lyrics?.isSynced == true) {
                    IconButton(onClick = { singAlong = true }) { Icon(Icons.Rounded.Mic, "Sing along", tint = textColor) }
                }
                IconButton(onClick = { carMode = true }) { Icon(Icons.Rounded.DirectionsCar, "Car mode", tint = textColor) }
                IconButton(onClick = { showOptions = true }) { Icon(Icons.Rounded.MoreVert, "Options", tint = textColor) }
            }

            if (lyricsFull && lyrics != null) {
                LyricsView(
                    lyrics!!,
                    pos.positionMs,
                    onSeek = c.player::seekTo,
                    textColor = textColor,
                    modifier = Modifier.weight(1f),
                    onShareLine = shareLine,
                )
            } else {
                Spacer(Modifier.height(24.dp))
                Artwork(
                    track.artworkUrl,
                    modifier = Modifier
                        .padding(horizontal = 28.dp)
                        .fillMaxWidth()
                        .aspectRatio(1f),
                    shape = RoundedCornerShape(10.dp),
                    seed = track.title,
                )
                Spacer(Modifier.height(28.dp))
            }

            // Title + like
            Row(Modifier.padding(horizontal = 24.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(track.title, color = textColor, style = MaterialTheme.typography.headlineSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(track.artist, color = muted, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                val liked = track.id in favorites
                IconButton(onClick = { scope.launch { c.library.toggleFavorite(track) } }) {
                    Icon(
                        if (liked) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                        "Like",
                        tint = if (liked) spec.accent else textColor,
                        modifier = Modifier.size(28.dp),
                    )
                }
            }

            // Seek bar
            val duration = pos.durationMs.coerceAtLeast(1)
            val sliderValue = if (dragging) dragValue else (pos.positionMs.toFloat() / duration).coerceIn(0f, 1f)
            Slider(
                value = sliderValue,
                onValueChange = { dragging = true; dragValue = it },
                onValueChangeFinished = {
                    c.player.seekTo((dragValue * duration).toLong())
                    dragging = false
                },
                colors = SliderDefaults.colors(
                    thumbColor = textColor,
                    activeTrackColor = textColor,
                    inactiveTrackColor = textColor.copy(alpha = 0.25f),
                ),
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            Row(Modifier.padding(horizontal = 24.dp)) {
                Text(formatDuration(if (dragging) (dragValue * duration).toLong() else pos.positionMs), color = muted, style = MaterialTheme.typography.labelSmall)
                Spacer(Modifier.weight(1f))
                Text(formatDuration(pos.durationMs), color = muted, style = MaterialTheme.typography.labelSmall)
            }
            state.error?.let {
                Text("⚠ $it", color = Color(0xFFFF8A80), style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(horizontal = 24.dp))
            }

            // Controls
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = c.player::toggleShuffle) {
                    Icon(Icons.Rounded.Shuffle, "Shuffle", tint = if (state.shuffle) spec.accent else textColor)
                }
                IconButton(onClick = c.player::previous) {
                    Icon(Icons.Rounded.SkipPrevious, "Previous", tint = textColor, modifier = Modifier.size(40.dp))
                }
                Box(
                    Modifier
                        .size(68.dp)
                        .playButtonStyle(spec, 68.dp)
                        .clickable(onClick = c.player::togglePlay),
                    contentAlignment = Alignment.Center,
                ) {
                    if (state.isBuffering && !state.isPlaying) {
                        CircularProgressIndicator(Modifier.size(28.dp), color = playIconColor(spec), strokeWidth = 3.dp)
                    } else {
                        Icon(
                            if (state.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                            "Play/Pause",
                            tint = playIconColor(spec),
                            modifier = Modifier.size(40.dp),
                        )
                    }
                }
                IconButton(onClick = c.player::next) {
                    Icon(Icons.Rounded.SkipNext, "Next", tint = textColor, modifier = Modifier.size(40.dp))
                }
                IconButton(onClick = c.player::cycleRepeat) {
                    Icon(
                        if (state.repeatMode == Player.REPEAT_MODE_ONE) Icons.Rounded.RepeatOne else Icons.Rounded.Repeat,
                        "Repeat",
                        tint = if (state.repeatMode != Player.REPEAT_MODE_OFF) spec.accent else textColor,
                    )
                }
            }

            // Quality + tools
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // The label shrinks (…) on small screens so Speed, Sleep and Queue always fit.
                Box(Modifier.weight(1f)) {
                    Text(
                        qualityText,
                        color = spec.accent,
                        style = MaterialTheme.typography.labelMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .background(if (spec.isDark) Color.Black.copy(alpha = 0.3f) else Color.White.copy(alpha = 0.55f), RoundedCornerShape(50))
                            .padding(horizontal = 10.dp, vertical = 4.dp),
                    )
                }
                IconButton(onClick = { showSpeed = true }) {
                    Icon(Icons.Rounded.Speed, "Speed", tint = if (settings.playbackSpeed != 1f) spec.accent else textColor)
                }
                IconButton(onClick = { showSleep = true }) {
                    Icon(Icons.Rounded.Bedtime, "Sleep timer", tint = if (sleep.mode != SleepTimerMode.OFF) spec.accent else textColor)
                }
                IconButton(onClick = { showQueue = true }) { Icon(Icons.Rounded.QueueMusic, "Queue", tint = textColor) }
            }

            // Lyrics card
            if (!lyricsFull) {
                Column(
                    Modifier
                        .padding(16.dp)
                        .fillMaxWidth()
                        .then(
                            if (glassy) Modifier.themedCard(spec, RoundedCornerShape(14.dp), corner = 14.dp)
                            else Modifier.background(bg.copy(alpha = 0.55f), RoundedCornerShape(14.dp))
                        )
                        .padding(vertical = 12.dp),
                ) {
                    Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Lyrics, null, tint = textColor, modifier = Modifier.size(18.dp))
                        Text("  Lyrics", color = textColor, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                        lyrics?.let { Text(it.source, color = muted, style = MaterialTheme.typography.labelSmall) }
                        Box {
                            IconButton(onClick = { showLyricsMenu = true }) { Icon(Icons.Rounded.MoreVert, "Lyrics options", tint = textColor) }
                            DropdownMenu(expanded = showLyricsMenu, onDismissRequest = { showLyricsMenu = false }) {
                                DropdownMenuItem(text = { Text("Import .lrc file") }, onClick = {
                                    showLyricsMenu = false
                                    lrcPicker.launch(arrayOf("*/*"))
                                })
                                DropdownMenuItem(text = { Text("Reload from online") }, onClick = {
                                    showLyricsMenu = false
                                    scope.launch {
                                        lyricsLoading = true
                                        c.lyrics.delete(track.id)
                                        lyrics = c.lyrics.get(track, allowOnline = true)
                                        lyricsLoading = false
                                    }
                                })
                            }
                        }
                    }
                    when {
                        lyricsLoading -> Text("Searching for lyrics…", color = muted, modifier = Modifier.padding(16.dp))
                        lyrics == null -> Text(
                            if (c.online.canGoOnline) "No lyrics found for this song. You can import an .lrc file from ⋮."
                            else "You're offline and no lyrics are saved.",
                            color = muted,
                            modifier = Modifier.padding(16.dp),
                        )
                        else -> Box(
                            Modifier
                                .fillMaxWidth()
                                .height(180.dp)
                                .clickable { lyricsFull = true },
                        ) {
                            LyricsView(
                                lyrics!!,
                                pos.positionMs,
                                onSeek = { lyricsFull = true },
                                textColor = textColor,
                                compact = true,
                                modifier = Modifier.fillMaxSize(),
                            )
                        }
                    }
                    if (lyrics != null) {
                        Text(
                            "Tap to see full lyrics · long-press a line for its meaning",
                            color = muted,
                            style = MaterialTheme.typography.labelSmall,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                        )
                    }
                }
                Spacer(Modifier.height(24.dp))
            }
        }
        if (carMode) CarModeScreen { carMode = false }
        if (singAlong && lyrics?.isSynced == true) SingAlongScreen(lyrics!!) { singAlong = false }
    }
}

/** Cover art ka average rang (1x1 scale trick). */
private suspend fun dominantColor(context: android.content.Context, url: String?): Color? {
    if (url.isNullOrBlank()) return null
    return withContext(Dispatchers.IO) {
        runCatching {
            val result = context.imageLoader.execute(
                ImageRequest.Builder(context).data(url).allowHardware(false).size(64).build()
            )
            val bmp = (result as? SuccessResult)?.drawable?.toBitmap() ?: return@runCatching null
            val px = Bitmap.createScaledBitmap(bmp, 1, 1, true).getPixel(0, 0)
            val color = Color(px)
            // Bahut halka rang ho to thoda gehra karo taaki safed text padha jaye (light themes lighten it again).
            val lum = 0.2126f * color.red + 0.7152f * color.green + 0.0722f * color.blue
            if (lum > 0.6f) Color(color.red * 0.6f, color.green * 0.6f, color.blue * 0.6f) else color
        }.getOrNull()
    }
}
