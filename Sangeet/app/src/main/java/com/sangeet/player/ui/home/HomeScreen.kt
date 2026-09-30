package com.sangeet.player.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavController
import com.sangeet.player.AppContainer
import com.sangeet.player.data.Categories
import com.sangeet.player.data.Festivals
import com.sangeet.player.data.model.inLanguages
import com.sangeet.player.data.Category
import com.sangeet.player.data.model.OnlinePlaylist
import com.sangeet.player.data.SourceResult
import com.sangeet.player.data.model.Track
import com.sangeet.player.data.recommend.Suggestion
import com.sangeet.player.ui.Routes
import com.sangeet.player.ui.appViewModel
import com.sangeet.player.ui.components.LoadingBox
import com.sangeet.player.ui.components.QuickTile
import com.sangeet.player.ui.components.SectionHeader
import com.sangeet.player.ui.components.ShelfCard
import com.sangeet.player.ui.components.TrackOptionsSheet
import com.sangeet.player.ui.components.TrackShelf
import com.sangeet.player.ui.library.ListKind
import com.sangeet.player.ui.theme.Sangeet
import com.sangeet.player.ui.theme.themedCard
import java.util.Calendar
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch

class HomeViewModel(private val c: AppContainer) : ViewModel() {
    data class Ui(
        val loading: Boolean = false,
        val trending: List<SourceResult> = emptyList(),
        val suggestions: List<Suggestion> = emptyList(),
        /** "🎵 Hindi Romantic", "🥁 Punjabi Hits"... wali lines. */
        val categories: List<Pair<Category, List<Track>>> = emptyList(),
        val charts: List<OnlinePlaylist> = emptyList(),
        val playlists: List<OnlinePlaylist> = emptyList(),
        /** Aaj chal rahe tyohaar ke gaane (Navratri, Diwali...). */
        val festivals: List<Pair<com.sangeet.player.data.Festival, List<Track>>> = emptyList(),
    )

    private val _ui = MutableStateFlow(Ui())
    val ui: StateFlow<Ui> = _ui.asStateFlow()

    init {
        // Network ya source settings badle to trending dobara lao.
        viewModelScope.launch {
            c.network.status.distinctUntilChangedBy { it.online }.collect { refresh() }
        }
        viewModelScope.launch {
            c.settings.settings.distinctUntilChangedBy {
                listOf(it.offlineMode, it.audiusEnabled, it.jamendoClientId, it.subsonicUrl, it.subsonicToken)
            }.collect { refresh() }
        }
    }

    private var job: Job? = null

    /** [force] = user ne refresh dabaya: mixes aur auto playlists bhi abhi naye banao. */
    fun refresh(force: Boolean = false) {
        job?.cancel()
        job = viewModelScope.launch {
            _ui.value = _ui.value.copy(loading = true)
            launch {
                val picks = runCatching { c.recommendations.suggestions(limit = 20) }.getOrDefault(emptyList())
                _ui.value = _ui.value.copy(suggestions = picks)
            }
            launch {
                val charts = runCatching { c.online.saavn.charts(c.settings.current) }.getOrDefault(emptyList())
                _ui.value = _ui.value.copy(charts = charts)
            }
            launch {
                val pls = runCatching { c.online.saavn.featuredPlaylists(c.settings.current, 1) }.getOrDefault(emptyList())
                _ui.value = _ui.value.copy(playlists = pls.take(20))
            }
            launch {
                val fest = Festivals.active().take(2).map { f ->
                    async { f to runCatching { c.online.searchAll(f.query).filter { it.inLanguages(c.settings.current.languages) }.take(20) }.getOrDefault(emptyList()) }
                }.awaitAll().filter { it.second.isNotEmpty() }
                _ui.value = _ui.value.copy(festivals = fest)
            }
            launch {
                val langs = c.settings.current.languages
                val cats = Categories.ordered(langs).filter { it.language in langs }.take(6)
                val rows = cats.map { cat ->
                    async { cat to runCatching { c.online.searchAll(cat.query).filter { it.inLanguages(langs) }.take(20) }.getOrDefault(emptyList()) }
                }.awaitAll().filter { it.second.isNotEmpty() }
                _ui.value = _ui.value.copy(categories = rows)
            }
            launch {
                runCatching {
                    if (c.settings.current.autoPlaylists) c.recommendations.syncAutoPlaylists(force = force)
                    c.recommendations.refreshMixes(force = force)
                }
            }
            val langs = c.settings.current.languages
            val results = c.online.trending().map { r -> r.copy(tracks = r.tracks.filter { it.inLanguages(langs) }) }
            _ui.value = _ui.value.copy(loading = false, trending = results)
        }
    }
}

@Composable
fun HomeScreen(nav: NavController) {
    val c = com.sangeet.player.ui.LocalAppContainer.current
    val vm = appViewModel { HomeViewModel(it) }
    val ui by vm.ui.collectAsStateWithLifecycle()
    val recent by c.library.recent.collectAsState(initial = emptyList())
    val mostPlayed by c.library.mostPlayed.collectAsState(initial = emptyList())
    val favorites by c.library.favorites.collectAsState(initial = emptyList())
    val downloaded by c.downloads.downloadedTracks.collectAsState(initial = emptyList())
    val localSongs by c.local.songs.collectAsStateWithLifecycle()
    val network by c.network.status.collectAsStateWithLifecycle()
    val settings by c.settings.settings.collectAsStateWithLifecycle()
    var filter by rememberSaveable { mutableStateOf(0) } // 0 all, 1 online, 2 offline
    var menuFor by remember { mutableStateOf<Track?>(null) }
    val spec = Sangeet.spec
    val offline = settings.offlineMode || !network.online
    val picks = remember(localSongs) { localSongs.shuffled().take(20) }
    val mixes by c.recommendations.mixes.collectAsStateWithLifecycle()

    menuFor?.let { TrackOptionsSheet(it, onDismiss = { menuFor = null }) }

    LazyColumn(Modifier.fillMaxSize()) {
        item {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 4.dp, top = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(greeting(), style = MaterialTheme.typography.headlineMedium, color = spec.onSurface, modifier = Modifier.weight(1f))
                IconButton(onClick = { vm.refresh(force = true) }) { Icon(Icons.Rounded.Refresh, "Refresh", tint = spec.onSurface) }
                IconButton(onClick = { nav.navigate(Routes.DJ) }) { Icon(Icons.Rounded.AutoAwesome, "AI DJ", tint = spec.onSurface) }
                IconButton(onClick = { nav.navigate(Routes.SETTINGS) }) { Icon(Icons.Rounded.Settings, "Settings", tint = spec.onSurface) }
            }
        }
        item {
            Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("All", "Online", "Offline").forEachIndexed { i, label ->
                    FilterChip(
                        selected = filter == i,
                        onClick = { filter = i },
                        label = { Text(label) },
                        shape = RoundedCornerShape(50),
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = spec.accent,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                            containerColor = spec.surface,
                            labelColor = spec.onSurface,
                        ),
                        border = null,
                    )
                }
            }
        }
        if (offline) {
            item {
                Row(
                    Modifier
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .fillMaxWidth()
                        .themedCard(spec, RoundedCornerShape(12.dp), corner = 12.dp)
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Rounded.CloudOff, null, tint = spec.accent)
                    Text(
                        if (settings.offlineMode) "  Offline mode is on — only downloads and songs on this phone"
                        else "  You're offline — playing downloads and songs on this phone",
                        style = MaterialTheme.typography.bodyMedium,
                        color = spec.onSurface,
                    )
                }
            }
        }

        // Upar ke quick tiles
        item {
            val tiles = buildList<Triple<String, String?, () -> Unit>> {
                add(Triple("Liked Songs", favorites.firstOrNull()?.artworkUrl) { nav.navigate(Routes.list(ListKind.LIKED)) })
                add(Triple("Downloads", downloaded.firstOrNull()?.artworkUrl) { nav.navigate(Routes.list(ListKind.DOWNLOADS)) })
                add(Triple("On this phone", localSongs.firstOrNull()?.artworkUrl) { nav.navigate(Routes.list(ListKind.LOCAL)) })
                add(Triple("Recently played", recent.firstOrNull()?.artworkUrl) { nav.navigate(Routes.list(ListKind.RECENT)) })
                recent.take(2).forEach { t -> add(Triple(t.title, t.artworkUrl) { c.player.play(listOf(t)) }) }
            }
            Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                tiles.chunked(2).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        row.forEach { (title, art, onClick) ->
                            QuickTile(title, art, onClick, Modifier.weight(1f))
                        }
                        if (row.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
            }
        }

        if (mixes.isNotEmpty()) {
            item { SectionHeader("Made for you") }
            item {
                LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    items(mixes, key = { it.id }) { m ->
                        ShelfCard(m.title, m.subtitle, m.artworkUrl, onClick = { nav.navigate(Routes.mix(m.id)) })
                    }
                }
            }
        }
        val suggested = ui.suggestions.map { it.track }.filter { !offline || !it.source.isOnline }
        if (suggested.isNotEmpty()) {
            item { SectionHeader("Recommended for you") }
            item { TrackShelf(suggested, onPlay = { c.player.play(suggested, it) }, onMore = { menuFor = it }) }
        }
        if (!offline) {
            ui.festivals.forEach { (f, tracks) ->
                item(key = "fest_h_${f.name}") {
                    SectionHeader("${f.emoji} ${f.name} special", action = "See all") { nav.navigate(Routes.list(ListKind.GENRE, f.name)) }
                }
                item(key = "fest_${f.name}") {
                    TrackShelf(tracks, onPlay = { c.player.play(tracks, it) }, onMore = { menuFor = it })
                }
            }
        }
        if (!offline && ui.playlists.isNotEmpty()) {
            item { SectionHeader("🎶 Playlists for you", action = "See all") { nav.navigate(Routes.ONLINE_LIBRARY) } }
            item {
                LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    items(ui.playlists, key = { "pl" + it.id }) { p ->
                        ShelfCard(p.title, p.subtitle, p.artworkUrl, onClick = { nav.navigate(Routes.onlinePlaylist(p.id, p.title)) })
                    }
                }
            }
        }
        if (!offline && ui.charts.isNotEmpty()) {
            item { SectionHeader("📊 Top Charts", action = "See all") { nav.navigate(Routes.ONLINE_LIBRARY) } }
            item {
                LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    items(ui.charts, key = { "chart" + it.id }) { p ->
                        ShelfCard(p.title, p.subtitle, p.artworkUrl, onClick = { nav.navigate(Routes.onlinePlaylist(p.id, p.title)) })
                    }
                }
            }
        }
        if (!offline) {
            ui.categories.forEach { (cat, tracks) ->
                item(key = "cat_h_${cat.name}") {
                    SectionHeader("${cat.emoji} ${cat.name}", action = "See all") {
                        nav.navigate(Routes.list(ListKind.GENRE, cat.name))
                    }
                }
                item(key = "cat_${cat.name}") {
                    TrackShelf(tracks, onPlay = { c.player.play(tracks, it) }, onMore = { menuFor = it })
                }
            }
        }
        if (filter != 1 && recent.isNotEmpty()) {
            item { SectionHeader("Recently played") }
            item { TrackShelf(recent, onPlay = { c.player.play(recent, it) }, onMore = { menuFor = it }) }
        }
        if (filter != 1 && mostPlayed.size >= 3) {
            item { SectionHeader("Your top songs") }
            item { TrackShelf(mostPlayed, onPlay = { c.player.play(mostPlayed, it) }, onMore = { menuFor = it }) }
        }
        if (filter != 2 && !offline) {
            if (ui.loading && ui.trending.isEmpty()) item { LoadingBox() }
            ui.trending.forEach { res ->
                if (res.tracks.isNotEmpty()) {
                    item(key = "trend_${res.source}") { SectionHeader("Trending on ${res.source.label}") }
                    item(key = "trend_shelf_${res.source}") {
                        TrackShelf(res.tracks, onPlay = { c.player.play(res.tracks, it) }, onMore = { menuFor = it })
                    }
                } else if (res.error != null) {
                    item(key = "trend_err_${res.source}") {
                        Text(
                            "${res.source.label}: ${res.error}",
                            color = spec.muted,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                        )
                    }
                }
            }
        }
        if (filter != 1 && downloaded.isNotEmpty()) {
            item { SectionHeader("Your downloads", action = "See all") { nav.navigate(Routes.list(ListKind.DOWNLOADS)) } }
            item { TrackShelf(downloaded, onPlay = { c.player.play(downloaded, it) }, onMore = { menuFor = it }) }
        }
        if (filter != 1 && localSongs.isNotEmpty()) {
            item { SectionHeader("From your phone", action = "See all") { nav.navigate(Routes.list(ListKind.LOCAL)) } }
            item { TrackShelf(picks, onPlay = { c.player.play(picks, it) }, onMore = { menuFor = it }) }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

private fun greeting(): String = when (Calendar.getInstance().get(Calendar.HOUR_OF_DAY)) {
    in 5..11 -> "Good morning"
    in 12..16 -> "Good afternoon"
    else -> "Good evening"
}
