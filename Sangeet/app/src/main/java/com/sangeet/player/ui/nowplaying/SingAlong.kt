package com.sangeet.player.ui.nowplaying

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sangeet.player.BuildConfig
import com.sangeet.player.data.ai.FreeAi
import com.sangeet.player.data.lyrics.Lyrics
import com.sangeet.player.data.model.Track
import com.sangeet.player.ui.LocalAppContainer

/** Sing along: the line being sung, very big in the middle; the next one under it; tap a line's time by tapping it. */
@Composable
fun SingAlongScreen(lyrics: Lyrics, onClose: () -> Unit) {
    val c = LocalAppContainer.current
    val pos by c.player.position.collectAsStateWithLifecycle()
    val state by c.player.state.collectAsStateWithLifecycle()
    BackHandler(onBack = onClose)
    val lines = lyrics.lines
    val i = lines.indexOfLast { it.timeMs <= pos.positionMs + 300 }.coerceAtLeast(0)
    val now = lines.getOrNull(i)?.text?.ifBlank { "♪" } ?: "♪"
    val next = lines.getOrNull(i + 1)?.text?.ifBlank { "♪" }.orEmpty()
    val before = if (i > 0) lines.getOrNull(i - 1)?.text.orEmpty() else ""
    Box(
        Modifier.fillMaxSize().background(Color.Black)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {},
    ) {
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(24.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(state.current?.title.orEmpty(), color = Color.White.copy(alpha = 0.6f), modifier = Modifier.weight(1f), maxLines = 1)
                IconButton(onClick = { c.player.togglePlay() }) {
                    Icon(if (state.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, "Play or pause", tint = Color.White)
                }
                IconButton(onClick = onClose) { Icon(Icons.Rounded.Close, "Close", tint = Color.White) }
            }
            Column(Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                Text(before, color = Color.White.copy(alpha = 0.3f), fontSize = 20.sp, textAlign = TextAlign.Center, maxLines = 2)
                Spacer(Modifier.height(20.dp))
                AnimatedContent(now, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "line") { text ->
                    Text(text, color = Color.White, fontSize = 38.sp, lineHeight = 46.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                }
                Spacer(Modifier.height(24.dp))
                Text(next, color = Color.White.copy(alpha = 0.45f), fontSize = 24.sp, lineHeight = 30.sp, textAlign = TextAlign.Center, maxLines = 3)
            }
        }
    }
}

/** Long-press on a lyrics line: share it as a picture, or see what it means (Gemini, else the free AI). */
@Composable
fun LyricLineDialog(line: String, track: Track, onShare: () -> Unit, onDismiss: () -> Unit) {
    val c = LocalAppContainer.current
    var tries by remember { mutableIntStateOf(0) }
    val asked = tries > 0
    var meaning by remember { mutableStateOf<String?>(null) }
    var failed by remember { mutableStateOf(false) }
    LaunchedEffect(tries) {
        if (tries == 0) return@LaunchedEffect
        failed = false
        meaning = null
        val key = c.settings.current.geminiApiKey.ifBlank { BuildConfig.GEMINI_API_KEY }
        meaning = runCatching { FreeAi.meaning(line, track.title, track.artist, key) }.getOrNull()
        failed = meaning == null
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(line, style = MaterialTheme.typography.titleMedium) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                when {
                    meaning != null -> Text(meaning!!)
                    failed -> Text("Couldn't get the meaning right now. Try again in a minute.")
                    asked -> Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.padding(end = 12.dp))
                        Text("Finding the meaning…")
                    }
                }
            }
        },
        confirmButton = {
            if (!asked || failed) TextButton(onClick = { tries++ }) { Text("Meaning") }
            else TextButton(onClick = onDismiss) { Text("OK") }
        },
        dismissButton = { TextButton(onClick = { onShare(); onDismiss() }) { Text("Share as picture") } },
    )
}
