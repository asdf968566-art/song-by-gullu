package com.sangeet.player.ui.nowplaying

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sangeet.player.ui.LocalAppContainer
import com.sangeet.player.ui.theme.Sangeet
import kotlinx.coroutines.launch

/** Car mode: huge title and buttons, easy to hit while driving. */
@Composable
fun CarModeScreen(onClose: () -> Unit) {
    val c = LocalAppContainer.current
    val state by c.player.state.collectAsStateWithLifecycle()
    val favorites by c.library.favoriteIds.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val track = state.current
    BackHandler(onBack = onClose)

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {},
    ) {
        IconButton(onClick = onClose, modifier = Modifier.statusBarsPadding().padding(8.dp).size(64.dp)) {
            Icon(Icons.Rounded.KeyboardArrowDown, "Close car mode", tint = Color.White, modifier = Modifier.size(44.dp))
        }
        Column(
            Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                track?.title ?: "Nothing playing",
                color = Color.White,
                fontSize = 40.sp,
                lineHeight = 44.sp,
                fontWeight = FontWeight.ExtraBold,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(10.dp))
            Text(track?.artist.orEmpty(), color = Color.White.copy(alpha = 0.7f), fontSize = 24.sp, textAlign = TextAlign.Center, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(48.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                CarButton(Icons.Rounded.SkipPrevious, "Previous", 104, Color.White.copy(alpha = 0.14f), Color.White) { c.player.previous() }
                CarButton(
                    if (state.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, "Play/Pause", 144,
                    Color.White, Color.Black,
                ) { c.player.togglePlay() }
                CarButton(Icons.Rounded.SkipNext, "Next", 104, Color.White.copy(alpha = 0.14f), Color.White) { c.player.next() }
            }
            if (track != null) {
                Spacer(Modifier.height(36.dp))
                val liked = track.id in favorites
                IconButton(onClick = { scope.launch { c.library.toggleFavorite(track) } }, modifier = Modifier.size(80.dp)) {
                    Icon(
                        if (liked) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder, "Like",
                        tint = if (liked) Sangeet.spec.accent else Color.White, modifier = Modifier.size(48.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun CarButton(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, size: Int, bg: Color, tint: Color, onClick: () -> Unit) {
    Box(
        Modifier.size(size.dp).clip(CircleShape).background(bg).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, label, tint = tint, modifier = Modifier.size((size * 0.55f).dp)) }
}
