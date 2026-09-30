package com.sangeet.player.ui.library

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.DownloadForOffline
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.sangeet.player.data.model.Track
import com.sangeet.player.ui.LocalAppContainer
import com.sangeet.player.ui.components.Artwork
import com.sangeet.player.ui.components.formatDuration
import com.sangeet.player.ui.theme.Sangeet
import com.sangeet.player.ui.theme.playButtonStyle
import com.sangeet.player.ui.theme.playIconColor
import kotlinx.coroutines.launch

/** Playlist / album ke upar ka bada header: cover, naam, play + shuffle + download-all. */
@Composable
fun CollectionHeader(
    nav: NavController,
    title: String,
    subtitle: String,
    tracks: List<Track>,
    artwork: String? = tracks.firstOrNull()?.artworkUrl,
    actions: @Composable RowScope.() -> Unit = {},
) {
    val c = LocalAppContainer.current
    val spec = Sangeet.spec
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val totalMs = tracks.sumOf { it.durationMs }
    val onlineTracks = tracks.filter { it.source.isOnline }

    Column(
        Modifier
            .fillMaxWidth()
            .background(Brush.verticalGradient(listOf(spec.accent.copy(alpha = 0.35f), Color.Transparent)))
    ) {
        IconButton(onClick = { nav.popBackStack() }, modifier = Modifier.padding(4.dp)) {
            Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back", tint = spec.onSurface)
        }
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Artwork(artwork, size = 220.dp, shape = RoundedCornerShape(6.dp), seed = title)
        }
        Spacer(Modifier.height(16.dp))
        Text(
            title,
            style = MaterialTheme.typography.headlineSmall,
            color = spec.onSurface,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        Text(
            "$subtitle • ${tracks.size} gaane${if (totalMs > 0) " • " + formatDuration(totalMs) else ""}",
            style = MaterialTheme.typography.bodyMedium,
            color = spec.muted,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
        )
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = 8.dp, end = 16.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (onlineTracks.isNotEmpty()) {
                IconButton(onClick = {
                    scope.launch {
                        c.downloads.downloadAll(onlineTracks)
                        Toast.makeText(context, "${onlineTracks.size} gaane download ho rahe hain", Toast.LENGTH_SHORT).show()
                    }
                }) { Icon(Icons.Rounded.DownloadForOffline, "Sab download karo", tint = spec.muted, modifier = Modifier.size(28.dp)) }
            }
            actions()
            Spacer(Modifier.weight(1f))
            IconButton(onClick = { c.player.play(tracks, 0, shuffle = true) }, enabled = tracks.isNotEmpty()) {
                Icon(Icons.Rounded.Shuffle, "Shuffle", tint = spec.accent, modifier = Modifier.size(28.dp))
            }
            Box(
                Modifier
                    .size(56.dp)
                    .playButtonStyle(spec, 56.dp)
                    .clickable(enabled = tracks.isNotEmpty()) { c.player.play(tracks, 0) },
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Rounded.PlayArrow, "Play", tint = playIconColor(spec), modifier = Modifier.size(32.dp))
            }
        }
    }
}
