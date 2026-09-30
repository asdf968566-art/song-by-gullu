package com.sangeet.player.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CloudDone
import androidx.compose.material.icons.rounded.Downloading
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sangeet.player.data.model.DownloadState
import com.sangeet.player.data.model.Track
import com.sangeet.player.ui.LocalAppContainer
import com.sangeet.player.ui.theme.Sangeet
import com.sangeet.player.ui.theme.themedCard

fun formatDuration(ms: Long): String {
    if (ms <= 0) return "--:--"
    val total = ms / 1000
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}

/** Gaane ki ek line (Spotify list jaisi). */
@Composable
fun TrackRow(
    track: Track,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    index: Int? = null,
    onMore: (() -> Unit)? = null,
) {
    val container = LocalAppContainer.current
    val playing by container.player.state.collectAsStateWithLifecycle()
    val downloads by container.downloads.downloads.collectAsStateWithLifecycle()
    val isCurrent = playing.current?.id == track.id
    val spec = Sangeet.spec
    val dl = downloads[track.id]

    Row(
        modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (index != null) {
            Box(Modifier.width(28.dp), contentAlignment = Alignment.Center) {
                if (isCurrent && playing.isPlaying) {
                    Icon(Icons.Rounded.GraphicEq, null, tint = spec.accent, modifier = Modifier.size(18.dp))
                } else {
                    Text("${index + 1}", color = spec.muted, style = MaterialTheme.typography.bodyMedium)
                }
            }
            Spacer(Modifier.width(8.dp))
        }
        Artwork(track.artworkUrl, size = 52.dp, shape = RoundedCornerShape(6.dp), seed = track.title)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                track.title,
                style = MaterialTheme.typography.titleMedium,
                color = if (isCurrent) spec.accent else spec.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                when (dl?.state) {
                    DownloadState.DONE -> DownloadBadge(Icons.Rounded.CloudDone)
                    DownloadState.DOWNLOADING, DownloadState.QUEUED -> DownloadBadge(Icons.Rounded.Downloading)
                    else -> Unit
                }
                Text(
                    buildString {
                        append(track.artist)
                        if (track.source.isOnline) append(" • ").append(track.source.label)
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = spec.muted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (onMore != null) {
            IconButton(onClick = onMore) {
                Icon(Icons.Rounded.MoreVert, "More", tint = spec.muted)
            }
        }
    }
}

@Composable
private fun DownloadBadge(icon: ImageVector) {
    Icon(icon, null, tint = Sangeet.spec.accent, modifier = Modifier.padding(end = 4.dp).size(14.dp))
}

@Composable
fun SectionHeader(title: String, modifier: Modifier = Modifier, action: String? = null, onAction: (() -> Unit)? = null) {
    Row(
        modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 8.dp, top = 20.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            title,
            style = MaterialTheme.typography.titleLarge,
            color = Sangeet.spec.onSurface,
            modifier = Modifier.weight(1f),
        )
        if (action != null && onAction != null) {
            Text(
                action,
                style = MaterialTheme.typography.labelLarge,
                color = Sangeet.spec.muted,
                modifier = Modifier
                    .clickable(onClick = onAction)
                    .padding(8.dp),
            )
        }
    }
}

/** Home screen ki horizontal line: bade square cover + naam. */
@Composable
fun TrackShelf(tracks: List<Track>, onPlay: (Int) -> Unit, onMore: (Track) -> Unit) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        items(tracks.size, key = { tracks[it].id }) { i ->
            val t = tracks[i]
            ShelfCard(
                title = t.title,
                subtitle = t.artist,
                artwork = t.artworkUrl,
                onClick = { onPlay(i) },
                onLongClick = { onMore(t) },
            )
        }
    }
}

@Composable
fun ShelfCard(
    title: String,
    subtitle: String,
    artwork: String?,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    circle: Boolean = false,
) {
    val spec = Sangeet.spec
    Column(
        Modifier
            .width(150.dp)
            .combinedClickableCompat(onClick, onLongClick)
    ) {
        Artwork(
            artwork,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f),
            shape = if (circle) CircleShape else RoundedCornerShape(10.dp),
            seed = title,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            title,
            style = MaterialTheme.typography.titleSmall,
            color = spec.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = spec.muted,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Home ke upar wale chhote 2-column tiles (Spotify ke "recent" jaise). */
@Composable
fun QuickTile(title: String, artwork: String?, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val spec = Sangeet.spec
    Row(
        modifier
            .height(56.dp)
            .themedCard(spec, RoundedCornerShape(6.dp), corner = 6.dp, elevation = 4.dp)
            .clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Artwork(artwork, size = 56.dp, shape = RoundedCornerShape(topStart = 6.dp, bottomStart = 6.dp), seed = title)
        Text(
            title,
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
            color = spec.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 10.dp),
        )
    }
}

@Composable
fun EmptyState(icon: ImageVector, title: String, message: String, modifier: Modifier = Modifier, action: @Composable (() -> Unit)? = null) {
    val spec = Sangeet.spec
    Column(
        modifier
            .fillMaxWidth()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, null, tint = spec.muted, modifier = Modifier.size(56.dp))
        Spacer(Modifier.height(12.dp))
        Text(title, style = MaterialTheme.typography.titleMedium, color = spec.onSurface)
        Spacer(Modifier.height(6.dp))
        Text(message, style = MaterialTheme.typography.bodyMedium, color = spec.muted)
        if (action != null) {
            Spacer(Modifier.height(16.dp))
            action()
        }
    }
}

@Composable
fun LoadingBox(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = Sangeet.spec.accent)
    }
}
