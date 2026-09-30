package com.sangeet.player.ui.library

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.LibraryAdd
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavController
import com.sangeet.player.AppContainer
import com.sangeet.player.data.model.OnlinePlaylist
import com.sangeet.player.data.model.Track
import com.sangeet.player.ui.LocalAppContainer
import com.sangeet.player.ui.Routes
import com.sangeet.player.ui.appViewModel
import com.sangeet.player.ui.components.Artwork
import com.sangeet.player.ui.components.EmptyState
import com.sangeet.player.ui.components.LoadingBox
import com.sangeet.player.ui.components.SectionHeader
import com.sangeet.player.ui.components.ShelfCard
import com.sangeet.player.ui.components.TrackOptionsSheet
import com.sangeet.player.ui.components.TrackRow
import com.sangeet.player.ui.theme.Sangeet
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class OnlineLibraryViewModel(private val c: AppContainer) : ViewModel() {
    data class Ui(
        val charts: List<OnlinePlaylist> = emptyList(),
        val playlists: List<OnlinePlaylist> = emptyList(),
        val loading: Boolean = true,
        val endReached: Boolean = false,
    )

    private val _ui = MutableStateFlow(Ui())
    val ui: StateFlow<Ui> = _ui.asStateFlow()
    private var page = 1
    private var busy = false

    init {
        viewModelScope.launch {
            val charts = runCatching { c.online.saavn.charts(c.settings.current) }.getOrDefault(emptyList())
            _ui.value = _ui.value.copy(charts = charts)
        }
        loadMore()
    }

    fun loadMore() {
        if (busy || _ui.value.endReached) return
        busy = true
        viewModelScope.launch {
            _ui.value = _ui.value.copy(loading = true)
            val more = runCatching { c.online.saavn.featuredPlaylists(c.settings.current, page) }.getOrDefault(emptyList())
            val have = _ui.value.playlists.mapTo(HashSet()) { it.id }
            val fresh = more.filter { it.id !in have }
            page++
            _ui.value = _ui.value.copy(
                playlists = _ui.value.playlists + fresh,
                loading = false,
                endReached = fresh.isEmpty(),
            )
            busy = false
        }
    }
}

/** Default online library: JioSaavn ke charts + hazaron playlists (lakhs gaane), bina search ke. */
@Composable
fun OnlineLibraryScreen(nav: NavController) {
    val c = LocalAppContainer.current
    val vm = appViewModel { OnlineLibraryViewModel(it) }
    val ui by vm.ui.collectAsStateWithLifecycle()
    val spec = Sangeet.spec
    val list = rememberLazyListState()

    // Neeche pahunchne se pehle hi agla page le aao (smooth scroll).
    LaunchedEffect(list) {
        snapshotFlow { list.layoutInfo.visibleItemsInfo.lastOrNull()?.index to list.layoutInfo.totalItemsCount }
            .collect { (last, total) -> if (last != null && last >= total - 8) vm.loadMore() }
    }

    LazyColumn(Modifier.fillMaxSize(), state = list) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(4.dp)) {
                IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back", tint = spec.onSurface) }
                Column {
                    Text("Online Library", style = MaterialTheme.typography.titleLarge, color = spec.onSurface)
                    Text("Charts, playlists and millions of songs", style = MaterialTheme.typography.bodySmall, color = spec.muted)
                }
            }
        }
        if (!c.online.canGoOnline) {
            item { EmptyState(Icons.Rounded.CloudOff, "You're offline", "Connect to the internet to browse the online library.") }
        }
        if (ui.charts.isNotEmpty()) {
            item { SectionHeader("📊 Top Charts") }
            item {
                LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    items(ui.charts, key = { "c" + it.id }) { p ->
                        ShelfCard(p.title, p.subtitle, p.artworkUrl, onClick = { nav.navigate(Routes.onlinePlaylist(p.id, p.title)) })
                    }
                }
            }
        }
        if (ui.playlists.isNotEmpty()) item { SectionHeader("🎶 Playlists") }
        items(ui.playlists, key = { "p" + it.id }) { p ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable { nav.navigate(Routes.onlinePlaylist(p.id, p.title)) }
                    .padding(horizontal = 16.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Artwork(p.artworkUrl, size = 64.dp, shape = RoundedCornerShape(6.dp), seed = p.title)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(p.title, style = MaterialTheme.typography.titleMedium, color = spec.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        listOfNotNull(p.subtitle.takeIf { it.isNotBlank() }, p.songCount.takeIf { it > 0 }?.let { if (it == 1) "1 song" else "$it songs" }).joinToString(" • "),
                        style = MaterialTheme.typography.bodyMedium,
                        color = spec.muted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        if (ui.loading) item { LoadingBox() }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

/** Ek online playlist / chart ke gaane. Library mein save bhi kar sakte ho. */
@Composable
fun OnlinePlaylistScreen(nav: NavController, id: String, title: String) {
    val c = LocalAppContainer.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var menuFor by remember { mutableStateOf<Track?>(null) }
    val tracks by produceState<List<Track>?>(initialValue = null, id) {
        value = runCatching { c.online.saavn.playlistTracks(id) }.getOrDefault(emptyList())
    }

    menuFor?.let { TrackOptionsSheet(it, onDismiss = { menuFor = null }) }

    LazyColumn(Modifier.fillMaxSize()) {
        item {
            CollectionHeader(nav, title, "JioSaavn", tracks.orEmpty()) {
                val list = tracks
                if (!list.isNullOrEmpty()) {
                    IconButton(onClick = {
                        scope.launch {
                            c.library.createPlaylist(title, list)
                            Toast.makeText(context, "Saved \"$title\" to your library", Toast.LENGTH_SHORT).show()
                        }
                    }) { Icon(Icons.Rounded.LibraryAdd, "Save to library", tint = Sangeet.spec.muted) }
                }
            }
        }
        val list = tracks
        when {
            list == null -> item { LoadingBox() }
            list.isEmpty() -> item { EmptyState(Icons.Rounded.CloudOff, "No songs found", "Check your connection and try again.") }
            else -> itemsIndexed(list, key = { _, t -> t.id }) { i, t ->
                TrackRow(t, onClick = { c.player.play(list, i) }, index = i, onMore = { menuFor = t })
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}
