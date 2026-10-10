package com.sangeet.player.ui.home

import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.delay
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.material3.CircularProgressIndicator
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
import com.sangeet.player.data.topicOf
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
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import com.sangeet.player.ui.theme.bottomBarPadding
import androidx.compose.material.icons.rounded.Language
import com.sangeet.player.ui.settings.LanguageSheet

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
        /** New songs (this year) by the singers you listen to most. */
        val newFromSingers: List<Track> = emptyList(),
        /** The refresh button was tapped and Home is still loading (its icon spins). */
        val refreshing: Boolean = false,
    )

    private val _ui = MutableStateFlow(Ui())
    val ui: StateFlow<Ui> = _ui.asStateFlow()

    init {
        // One refresh at start, then again only when the network or the music sources change.
        viewModelScope.launch {
            combine(
                c.network.status.map { it.online },
                c.settings.settings.map { listOf(it.offlineMode, it.audiusEnabled, it.jamendoClientId, it.subsonicUrl, it.subsonicToken) },
            ) { online, sources -> online to sources }
                .distinctUntilChanged()
                .collect { refresh() }
        }
    }

    private var job: Job? = null
    /** How many times refresh was tapped: each tap brings other songs, playlists and categories. */
    private var round = 0

    /** [force] = user ne refresh dabaya: mixes aur auto playlists bhi abhi naye banao. */
    fun refresh(force: Boolean = false) {
        job?.cancel()
        if (force) round++
        val turn = round
        // Tapped refresh: new picks instead of the ones on screen (owner's report, Oct 10: "refresh kaam nhi kr rha").
        val onScreen = if (force) _ui.value.suggestions.map { it.track.id }.toSet() else emptySet()
        job = viewModelScope.launch {
            _ui.value = _ui.value.copy(loading = true, refreshing = force)
            launch {
                val picks = runCatching { c.recommendations.suggestions(limit = 20, exclude = onScreen) }.getOrDefault(emptyList())
                    .ifEmpty { runCatching { c.recommendations.suggestions(limit = 20) }.getOrDefault(emptyList()) }
                _ui.value = _ui.value.copy(suggestions = picks)
            }
            launch {
                val charts = runCatching { c.online.saavn.charts(c.settings.current) }.getOrDefault(emptyList())
                _ui.value = _ui.value.copy(charts = charts)
            }
            launch {
                val pls = runCatching { c.online.saavn.featuredPlaylists(c.settings.current, 1 + turn % 3) }.getOrDefault(emptyList())
                    .ifEmpty { runCatching { c.online.saavn.featuredPlaylists(c.settings.current, 1) }.getOrDefault(emptyList()) }
                _ui.value = _ui.value.copy(playlists = pls.take(20))
            }
            // Lower shelves come a moment later, from JioSaavn only (one quick call each instead of every source),
            // so the top of Home appears at once and scrolling stays smooth.
            launch {
                delay(800)
                val fest = Festivals.active().take(2).map { f ->
                    async { f to runCatching { c.online.topicTracks(topicOf(f.query, c.settings.current.languages), limit = 20) }.getOrDefault(emptyList()) }
                }.awaitAll().filter { it.second.isNotEmpty() }
                _ui.value = _ui.value.copy(festivals = fest)
            }
            launch {
                delay(1200)
                // The singers you listen to most, and their newest songs on JioSaavn.
                val now = java.util.Calendar.getInstance()
                val since = now.get(java.util.Calendar.YEAR) - if (now.get(java.util.Calendar.MONTH) < 3) 1 else 0
                val singers = runCatching { c.database.listenDao().topArtists(0L, 6) }.getOrDefault(emptyList())
                    .map { it.artist.substringBefore(",").trim() }.filter { it.isNotBlank() && !it.equals("Unknown", true) }.distinct().take(4)
                val fresh = singers.map { a -> async { runCatching { c.online.saavn.newSongsBy(a, since, 8) }.getOrDefault(emptyList()) } }
                    .awaitAll()
                // Take turns between singers, newest first for each.
                val mixed = (0 until (fresh.maxOfOrNull { it.size } ?: 0)).flatMap { i -> fresh.mapNotNull { it.getOrNull(i) } }.distinctBy { it.id }
                _ui.value = _ui.value.copy(newFromSingers = mixed.take(20))
            }
            launch {
                delay(800)
                val langs = c.settings.current.languages
                // Special categories (Haryanvi Badmashi) always get a row when their language is chosen.
                val ordered = Categories.ordered(langs).filter { it.language in langs }
                val shift = if (ordered.isEmpty()) 0 else (turn * 4) % ordered.size
                val cats = ((ordered.drop(shift) + ordered.take(shift)).take(4) +
                    Categories.all.filter { (it.more.isNotEmpty() || it.youtube.isNotEmpty()) && it.language in langs }).distinct()
                val rows = cats.map { cat ->
                    async {
                        cat to runCatching {
                            when {
                                cat.youtube.isNotEmpty() -> c.online.youtubeCategory(cat).take(20)
                                cat.more.isNotEmpty() -> c.online.saavn.searchPage(cat.query, 1).filter { it.inLanguages(langs) }.take(20)
                                else -> c.online.topicTracks(topicOf(cat.query, langs, cat.singer), limit = 20)
                            }
                        }.getOrDefault(emptyList())
                    }
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
            val results = c.online.trending().map { r ->
                r.copy(tracks = r.tracks.filter { it.inLanguages(langs) }.let { if (turn > 0) it.shuffled() else it })
            }
            _ui.value = _ui.value.copy(loading = false, trending = results)
        }
        val mine = job
        // The spinner stops when every shelf has loaded.
        mine?.invokeOnCompletion { if (job === mine) _ui.value = _ui.value.copy(loading = false, refreshing = false) }
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
    var showLanguages by remember { mutableStateOf(false) }
    // Song languages, right from Home: when they change, Home loads songs in the new languages.
    if (showLanguages) LanguageSheet { changed -> showLanguages = false; if (changed) vm.refresh(force = true) }
    val spec = Sangeet.spec
    val offline = settings.offlineMode || !network.online
    val picks = remember(localSongs) { localSongs.shuffled().take(20) }
    val mixes by c.recommendations.mixes.collectAsStateWithLifecycle()

    menuFor?.let { TrackOptionsSheet(it, onDismiss = { menuFor = null }) }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = bottomBarPadding()) {
        item {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 4.dp, top = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(greeting(), style = MaterialTheme.typography.headlineMedium, color = spec.onSurface, modifier = Modifier.weight(1f))
                IconButton(onClick = { showLanguages = true }) { Icon(Icons.Rounded.Language, "Song languages", tint = spec.onSurface) }
                IconButton(onClick = { if (!ui.refreshing) vm.refresh(force = true) }) {
                    if (ui.refreshing) CircularProgressIndicator(Modifier.size(20.dp), color = spec.onSurface, strokeWidth = 2.dp)
                    else Icon(Icons.Rounded.Refresh, "Refresh", tint = spec.onSurface)
                }
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
                add(Triple("Downloads", downloaded.firstOrNull()?.artworkUrl) { nav.navigate(Routes.DOWNLOADS) })
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
                    items(mixes.distinctBy { it.id }, key = { it.id }) { m ->
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
                    items(ui.playlists.distinctBy { it.id }, key = { "pl" + it.id }) { p ->
                        ShelfCard(p.title, p.subtitle, p.artworkUrl, onClick = { nav.navigate(Routes.onlinePlaylist(p.id, p.title)) })
                    }
                }
            }
        }
        if (!offline && ui.charts.isNotEmpty()) {
            item { SectionHeader("📊 Top Charts", action = "See all") { nav.navigate(Routes.ONLINE_LIBRARY) } }
            item {
                LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    items(ui.charts.distinctBy { it.id }, key = { "chart" + it.id }) { p ->
                        ShelfCard(p.title, p.subtitle, p.artworkUrl, onClick = { nav.navigate(Routes.onlinePlaylist(p.id, p.title)) })
                    }
                }
            }
        }
        if (!offline && ui.newFromSingers.isNotEmpty()) {
            item(key = "new_h") { SectionHeader("🆕 New from your singers") }
            item(key = "new_row") {
                TrackShelf(ui.newFromSingers, onPlay = { c.player.play(ui.newFromSingers, it) }, onMore = { menuFor = it })
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
            item { SectionHeader("Your downloads", action = "See all") { nav.navigate(Routes.DOWNLOADS) } }
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
