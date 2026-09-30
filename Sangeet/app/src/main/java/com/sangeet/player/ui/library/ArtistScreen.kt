package com.sangeet.player.ui.library

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Radio
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
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
import com.sangeet.player.ui.Routes
import com.sangeet.player.ui.components.EmptyState
import com.sangeet.player.ui.components.LoadingBox
import com.sangeet.player.ui.components.SectionHeader
import com.sangeet.player.ui.components.ShelfCard
import com.sangeet.player.ui.components.TrackOptionsSheet
import com.sangeet.player.ui.components.TrackRow
import com.sangeet.player.ui.theme.Sangeet

private data class ArtistData(
    val top: List<Track>,
    val albums: List<Pair<String, List<Track>>>,
    val similar: List<String>,
)

/** Artist page: top gaane, albums, milte-julte artists, radio. */
@Composable
fun ArtistScreen(nav: NavController, name: String) {
    val c = LocalAppContainer.current
    val spec = Sangeet.spec
    var menuFor by remember { mutableStateOf<Track?>(null) }
    val localSongs by c.local.songs.collectAsStateWithLifecycle()
    val liked by c.library.favorites.collectAsState(initial = emptyList())

    val data by produceState<ArtistData?>(initialValue = null, name) {
        val me = norm(name)
        val online = runCatching { c.online.searchAll(name) }.getOrDefault(emptyList())
        val mine = (localSongs + liked).filter { matches(it.artist, me) }
        val top = (mine + online.filter { matches(it.artist, me) }).distinctBy { it.id }.take(40)
        val albums = top.filter { it.album.isNotBlank() && !it.album.startsWith("YouTube") && it.album != "Audius" }
            .groupBy { it.album }
            .filter { it.value.isNotEmpty() }
            .toList()
            .sortedByDescending { it.second.size }
            .take(12)
        // Saath gaane wale / search mein aaye doosre artists = "similar"
        val similar = (top + online)
            .flatMap { splitArtists(it.artist) }
            .filter { norm(it) != me && norm(it).isNotBlank() && !matches(it, me) }
            .groupingBy { it }.eachCount()
            .entries.sortedByDescending { it.value }
            .map { it.key }
            .take(10)
        value = ArtistData(top, albums, similar)
    }

    menuFor?.let { TrackOptionsSheet(it, onDismiss = { menuFor = null }) }

    val top = data?.top.orEmpty()
    LazyColumn(Modifier.fillMaxSize()) {
        item {
            CollectionHeader(nav, name, "Artist", top) {
                if (top.isNotEmpty()) {
                    IconButton(onClick = { c.player.startRadio(top.first()) }) {
                        Icon(Icons.Rounded.Radio, "$name Radio", tint = spec.muted)
                    }
                }
            }
        }
        val d = data
        when {
            d == null -> item { LoadingBox() }
            d.top.isEmpty() -> item { EmptyState(Icons.Rounded.Person, "No songs found", "Check your connection or try a different name.") }
            else -> {
                if (d.similar.isNotEmpty()) {
                    item { SectionHeader("Fans also like") }
                    item {
                        Row(
                            Modifier
                                .horizontalScroll(rememberScrollState())
                                .padding(horizontal = 12.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            d.similar.forEach { a ->
                                AssistChip(
                                    onClick = { nav.navigate(Routes.artist(a)) },
                                    label = { Text(a) },
                                    leadingIcon = { Icon(Icons.Rounded.Person, null) },
                                    colors = AssistChipDefaults.assistChipColors(labelColor = spec.onSurface, leadingIconContentColor = spec.muted),
                                )
                            }
                        }
                    }
                }
                if (d.albums.isNotEmpty()) {
                    item { SectionHeader("Albums / Movies") }
                    item {
                        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                            items(d.albums, key = { "al_" + it.first }) { (album, tracks) ->
                                ShelfCard(album, "${if (tracks.size == 1) "1 song" else "${tracks.size} songs"}", tracks.firstNotNullOfOrNull { it.artworkUrl }, onClick = { c.player.play(tracks) })
                            }
                        }
                    }
                }
                item { SectionHeader("Top songs") }
                itemsIndexed(d.top, key = { _, t -> t.id }) { i, t ->
                    TrackRow(t, onClick = { c.player.play(d.top, i) }, index = i, onMore = { menuFor = t })
                }
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

private fun norm(s: String) = s.lowercase().replace(Regex("[^\\p{L}\\p{N}]+"), " ").trim()

private fun matches(artistField: String, me: String): Boolean =
    me.isNotBlank() && splitArtists(artistField).any { norm(it) == me || norm(it).contains(me) } || norm(artistField) == me

/** "Pritam, Arijit Singh & Amitabh ft. X" -> [Pritam, Arijit Singh, Amitabh, X] */
private fun splitArtists(s: String): List<String> =
    s.split(Regex("""\s*(,|&| and | x | feat\.? | ft\.? )\s*""", RegexOption.IGNORE_CASE))
        .map { it.trim() }
        .filter { it.length > 1 && !it.equals("Unknown", true) && !it.equals("YouTube", true) }
