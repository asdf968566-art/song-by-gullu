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
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalConfiguration
import com.sangeet.player.data.settings.ThemeStyle
import com.sangeet.player.ui.theme.neonCircle
import com.sangeet.player.ui.theme.neonColors
import com.sangeet.player.ui.theme.neonGlass
import kotlinx.coroutines.launch

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
    if (spec.style == ThemeStyle.NEON) {
        NeonTrackRow(track, onClick, modifier, index, onMore, isCurrent, isCurrent && playing.isPlaying, dl?.state)
        return
    }

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

/**
 * Neon Glass song row (the reel's "Recommended for you" / "Trending Now"): a glass card with the cover, the
 * names, a heart and a round glass play button.
 */
@Composable
private fun NeonTrackRow(
    track: Track,
    onClick: () -> Unit,
    modifier: Modifier,
    index: Int?,
    onMore: (() -> Unit)?,
    isCurrent: Boolean,
    isPlaying: Boolean,
    dl: DownloadState?,
) {
    val c = LocalAppContainer.current
    val spec = Sangeet.spec
    val favorites by c.library.favoriteIds.collectAsStateWithLifecycle()
    // A live station can't be liked; small phones (320 dp) leave the heart out so the names still fit.
    val heart = !track.sourceId.startsWith("radio-") && LocalConfiguration.current.screenWidthDp >= 340
    Row(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .neonGlass(RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
            .padding(start = 8.dp, top = 8.dp, bottom = 8.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (index != null) {
            Text("${index + 1}", color = spec.muted, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.width(26.dp))
        }
        Artwork(track.artworkUrl, size = 50.dp, shape = RoundedCornerShape(12.dp), seed = track.title)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                track.title,
                style = MaterialTheme.typography.titleSmall,
                color = if (isCurrent) spec.accent else spec.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                when (dl) {
                    DownloadState.DONE -> DownloadBadge(Icons.Rounded.CloudDone)
                    DownloadState.DOWNLOADING, DownloadState.QUEUED -> DownloadBadge(Icons.Rounded.Downloading)
                    else -> Unit
                }
                Text(track.artist, style = MaterialTheme.typography.bodySmall, color = spec.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        if (isPlaying) Icon(Icons.Rounded.GraphicEq, null, tint = spec.accent, modifier = Modifier.padding(start = 4.dp).size(18.dp))
        if (heart) {
            val liked = track.id in favorites
            IconButton(onClick = { c.scope.launch { c.library.toggleFavorite(track) } }, modifier = Modifier.size(38.dp)) {
                Icon(
                    if (liked) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                    "Like",
                    tint = if (liked) spec.accent else spec.onSurface.copy(alpha = 0.85f),
                    modifier = Modifier.size(20.dp),
                )
            }
        }
        Box(
            Modifier
                .padding(horizontal = 2.dp)
                .size(34.dp)
                .neonCircle()
                .clickable { if (isCurrent) c.player.togglePlay() else onClick() },
            contentAlignment = Alignment.Center,
        ) {
            Icon(if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, if (isPlaying) "Pause" else "Play", tint = Color.White, modifier = Modifier.size(20.dp))
        }
        if (onMore != null) {
            IconButton(onClick = onMore, modifier = Modifier.size(38.dp)) {
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
        items(tracks.size, key = { "${it}_${tracks[it].id}" }) { i ->
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
    if (spec.style == ThemeStyle.NEON) {
        // Neon Glass (the reel's "Recently Played"): a glass card, the cover with a round glass play button on it.
        Column(
            Modifier
                .width(150.dp)
                .neonGlass(RoundedCornerShape(20.dp))
                .combinedClickableCompat(onClick, onLongClick)
                .padding(8.dp)
        ) {
            Box {
                Artwork(
                    artwork,
                    modifier = Modifier.fillMaxWidth().aspectRatio(1f),
                    shape = if (circle) CircleShape else RoundedCornerShape(14.dp),
                    seed = title,
                )
                if (!circle) {
                    Box(
                        Modifier.align(Alignment.BottomEnd).padding(6.dp).size(32.dp).neonCircle(),
                        contentAlignment = Alignment.Center,
                    ) { Icon(Icons.Rounded.PlayArrow, null, tint = Color.White, modifier = Modifier.size(20.dp)) }
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(title, style = MaterialTheme.typography.titleSmall, color = spec.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = spec.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        return
    }
    Column(
        Modifier
            .width(150.dp)
            .combinedClickableCompat(onClick, onLongClick)
    ) {
        // The cover takes the theme's card style too (glass rim, soft shadows…), like the other tiles.
        val shape = if (circle) CircleShape else RoundedCornerShape(10.dp)
        Artwork(
            artwork,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .themedCard(spec, shape, corner = if (circle) 75.dp else 10.dp, elevation = 4.dp),
            shape = shape,
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
    val r = if (spec.style == ThemeStyle.NEON) 16.dp else 6.dp
    Row(
        modifier
            .height(56.dp)
            .themedCard(spec, RoundedCornerShape(r), corner = r, elevation = 4.dp)
            .clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Artwork(artwork, size = 56.dp, shape = RoundedCornerShape(topStart = r, bottomStart = r), seed = title)
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

/**
 * Neon Glass's big card at the top of Home (the reel's "Good Vibes Only"): the names and a Play now button on
 * glass, with the cover on the right fading into it.
 */
@Composable
fun NeonHero(title: String, subtitle: String, artwork: String?, onPlay: () -> Unit, onOpen: () -> Unit) {
    val spec = Sangeet.spec
    val page = spec.neonColors.page
    Box(
        Modifier
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .fillMaxWidth()
            .height(164.dp)
            .neonGlass(RoundedCornerShape(26.dp))
            .clickable(onClick = onOpen)
    ) {
        Artwork(
            artwork,
            modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight().aspectRatio(1f),
            shape = RectangleShape,
            seed = title,
        )
        Box(
            Modifier.matchParentSize().background(
                Brush.horizontalGradient(0f to page[1].copy(alpha = 0.92f), 0.45f to page[1].copy(alpha = 0.7f), 0.8f to Color.Transparent)
            )
        )
        Column(Modifier.align(Alignment.CenterStart).fillMaxWidth(0.66f).padding(18.dp)) {
            Text(title, style = MaterialTheme.typography.headlineSmall, color = Color.White, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(4.dp))
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = spec.muted, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(12.dp))
            Row(
                Modifier
                    .clip(RoundedCornerShape(50))
                    .background(Color.White.copy(alpha = 0.9f))
                    .clickable(onClick = onPlay)
                    .padding(start = 10.dp, end = 14.dp, top = 7.dp, bottom = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Rounded.PlayArrow, null, tint = page[2], modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(4.dp))
                Text("Play now", style = MaterialTheme.typography.labelLarge, color = page[2])
            }
        }
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
