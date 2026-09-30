package com.sangeet.player.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sangeet.player.ui.LocalAppContainer
import com.sangeet.player.ui.theme.Sangeet
import com.sangeet.player.ui.theme.themedCard
import kotlinx.coroutines.launch

/** Neeche chipka chhota player (Spotify jaisa). */
@Composable
fun MiniPlayer(onExpand: () -> Unit, modifier: Modifier = Modifier) {
    val container = LocalAppContainer.current
    val state by container.player.state.collectAsStateWithLifecycle()
    val favorites by container.library.favoriteIds.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val track = state.current ?: return
    val spec = Sangeet.spec
    val liked = track.id in favorites

    Box(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp)
            .themedCard(spec, RoundedCornerShape(10.dp), corner = 10.dp, elevation = 5.dp)
            .clickable(onClick = onExpand)
    ) {
        Column {
            Row(
                Modifier.padding(start = 8.dp, top = 8.dp, bottom = 6.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Artwork(track.artworkUrl, size = 42.dp, shape = RoundedCornerShape(6.dp), seed = track.title)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        track.title,
                        style = MaterialTheme.typography.titleSmall,
                        color = spec.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        state.error?.let { "⚠ $it" } ?: track.artist,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (state.error != null) Color(0xFFFF6B6B) else spec.muted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                IconButton(onClick = { scope.launch { container.library.toggleFavorite(track) } }) {
                    Icon(
                        if (liked) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                        "Like",
                        tint = if (liked) spec.accent else spec.onSurface,
                    )
                }
                IconButton(onClick = container.player::togglePlay) {
                    if (state.isBuffering && !state.isPlaying) {
                        CircularProgressIndicator(Modifier.size(22.dp), color = spec.onSurface, strokeWidth = 2.dp)
                    } else {
                        Icon(
                            if (state.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                            "Play/Pause",
                            tint = spec.onSurface,
                            modifier = Modifier.size(30.dp),
                        )
                    }
                }
                IconButton(onClick = container.player::next) {
                    Icon(Icons.Rounded.SkipNext, "Next", tint = spec.onSurface)
                }
            }
            val pos = container.player.position.collectAsStateWithLifecycle()
            LinearProgressIndicator(
                // Lambda draw ke waqt padhta hai: position badalne pe recomposition nahi hota.
                progress = {
                    val p = pos.value
                    (if (p.durationMs > 0) p.positionMs.toFloat() / p.durationMs else 0f).coerceIn(0f, 1f)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp)
                    .height(2.dp),
                color = spec.onSurface,
                trackColor = spec.muted.copy(alpha = 0.3f),
                drawStopIndicator = {},
            )
        }
    }
}
