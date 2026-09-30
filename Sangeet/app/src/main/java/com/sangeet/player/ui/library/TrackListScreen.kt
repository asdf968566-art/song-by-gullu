package com.sangeet.player.ui.library

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.sangeet.player.data.model.Track
import com.sangeet.player.ui.LocalAppContainer
import com.sangeet.player.ui.components.EmptyState
import com.sangeet.player.ui.components.LoadingBox
import com.sangeet.player.ui.components.TrackOptionsSheet
import com.sangeet.player.ui.components.TrackRow
import kotlinx.coroutines.flow.flowOf

enum class ListKind { LIKED, DOWNLOADS, RECENT, LOCAL, ALBUM, ARTIST, GENRE }

/** Liked / Downloads / Recent / Phone / Album / Artist / Genre ki list. */
@Composable
fun TrackListScreen(nav: NavController, kind: ListKind, arg: String) {
    val c = LocalAppContainer.current
    val localSongs by c.local.songs.collectAsStateWithLifecycle()
    var menuFor by remember { mutableStateOf<Track?>(null) }

    val flow = remember(kind) {
        when (kind) {
            ListKind.LIKED -> c.library.favorites
            ListKind.DOWNLOADS -> c.downloads.downloadedTracks
            ListKind.RECENT -> c.library.recent
            else -> flowOf(emptyList())
        }
    }
    val fromDb by flow.collectAsState(initial = null)

    val genreTracks by produceState<List<Track>?>(initialValue = null, kind, arg) {
        value = if (kind == ListKind.GENRE) c.online.trending(arg).flatMap { it.tracks } else emptyList()
    }

    val tracks: List<Track>? = when (kind) {
        ListKind.LOCAL -> localSongs
        ListKind.ALBUM -> localSongs.filter { it.albumId.toString() == arg }
        ListKind.ARTIST -> localSongs.filter { it.artist == arg }
        ListKind.GENRE -> genreTracks
        else -> fromDb
    }

    val (title, subtitle) = when (kind) {
        ListKind.LIKED -> "Liked Songs" to "Aapke pasandida"
        ListKind.DOWNLOADS -> "Downloads" to "Bina internet ke chalenge"
        ListKind.RECENT -> "Recently played" to "History"
        ListKind.LOCAL -> "Phone ke gaane" to "Local files"
        ListKind.ALBUM -> (tracks?.firstOrNull()?.album?.ifBlank { null } ?: "Album") to (tracks?.firstOrNull()?.artist ?: "")
        ListKind.ARTIST -> arg to "Artist"
        ListKind.GENRE -> arg to "Trending online"
    }

    menuFor?.let { TrackOptionsSheet(it, onDismiss = { menuFor = null }) }

    LazyColumn(Modifier.fillMaxSize()) {
        item { CollectionHeader(nav, title, subtitle, tracks.orEmpty()) }
        when {
            tracks == null -> item { LoadingBox() }
            tracks.isEmpty() -> item {
                EmptyState(
                    Icons.Rounded.LibraryMusic,
                    "Yahan abhi kuch nahi",
                    when (kind) {
                        ListKind.LIKED -> "Kisi gaane pe ♥ dabao, yahan aa jayega."
                        ListKind.DOWNLOADS -> "Online gaane ke menu mein 'Download' dabao."
                        ListKind.GENRE -> "Internet check karo ya Settings mein source on karo."
                        else -> "Gaane bajao, yahan dikhenge."
                    },
                )
            }
            else -> itemsIndexed(tracks, key = { _, t -> t.id }) { i, t ->
                TrackRow(t, onClick = { c.player.play(tracks, i) }, index = i, onMore = { menuFor = t })
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}
