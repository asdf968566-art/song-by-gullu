package com.sangeet.player.ui.search

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.layout.PaddingValues
import com.sangeet.player.ui.library.ALBUM_PREFIX
import com.sangeet.player.ui.components.ShelfCard
import com.sangeet.player.data.model.OnlinePlaylist
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SearchOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavController
import com.sangeet.player.AppContainer
import com.sangeet.player.data.Categories
import com.sangeet.player.data.Festivals
import com.sangeet.player.data.Transliterate
import com.sangeet.player.data.remote.Http
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.NorthWest
import android.content.Context
import com.sangeet.player.data.SourceResult
import com.sangeet.player.data.model.Track
import com.sangeet.player.ui.LocalAppContainer
import com.sangeet.player.ui.Routes
import com.sangeet.player.ui.appViewModel
import com.sangeet.player.ui.components.EmptyState
import com.sangeet.player.ui.components.LoadingBox
import com.sangeet.player.ui.components.SectionHeader
import com.sangeet.player.ui.components.TrackOptionsSheet
import com.sangeet.player.ui.components.TrackRow
import com.sangeet.player.ui.library.ListKind
import com.sangeet.player.ui.theme.Sangeet
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import com.sangeet.player.data.settings.ThemeStyle
import com.sangeet.player.ui.theme.themedCard
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.ui.text.style.TextOverflow
import com.sangeet.player.ui.components.Artwork
import com.sangeet.player.ui.theme.bottomBarPadding
import com.sangeet.player.data.lyrics.LyricsSearch
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import android.content.Intent
import android.speech.RecognizerIntent
import android.widget.Toast
import android.app.Activity
import androidx.compose.ui.platform.LocalContext
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.rounded.Mic
import kotlinx.coroutines.Deferred

/** One entry of "Recent searches": a song you picked from results, or words you searched. */
@Serializable
data class RecentItem(val query: String? = null, val track: Track? = null)

@OptIn(FlowPreview::class)
class SearchViewModel(private val c: AppContainer) : ViewModel() {
    data class Ui(
        val query: String = "",
        val loading: Boolean = false,
        val local: List<Track> = emptyList(),
        val online: List<SourceResult> = emptyList(),
        /** Likhte waqt suggestions ("kes" -> "kesariya"). */
        val suggestions: List<String> = emptyList(),
        val recent: List<RecentItem> = emptyList(),
        /** Songs whose lyrics have the typed words (a line from the middle of a song). */
        val lyricsMatches: List<Track> = emptyList(),
        /** Movies / albums named like the search: all their songs, one after another. */
        val albums: List<OnlinePlaylist> = emptyList(),
    )

    private val _ui = MutableStateFlow(Ui())
    val ui: StateFlow<Ui> = _ui.asStateFlow()
    private val queries = MutableStateFlow("")
    private var downloaded: List<Track> = emptyList()
    private val prefs = c.appContext.getSharedPreferences("search", Context.MODE_PRIVATE)

    init {
        _ui.value = _ui.value.copy(recent = loadRecent())
        // Live suggestions (YouTube suggest, free)
        viewModelScope.launch {
            queries.debounce(200).distinctUntilChanged().collectLatest { q ->
                val t = q.trim()
                if (t.length < 2 || !c.online.canGoOnline) {
                    _ui.value = _ui.value.copy(suggestions = emptyList())
                    return@collectLatest
                }
                val list = runCatching { fetchSuggestions(t) }.getOrDefault(emptyList())
                _ui.value = _ui.value.copy(suggestions = list)
            }
        }
        viewModelScope.launch { c.downloads.downloadedTracks.collect { downloaded = it } }
        viewModelScope.launch {
            queries.debounce(400).distinctUntilChanged().collectLatest { q ->
                if (q.isBlank()) {
                    // Keep the recent searches (a fresh Ui() used to wipe them until the screen was rebuilt).
                    _ui.value = Ui(query = q, recent = _ui.value.recent)
                    return@collectLatest
                }
                val needle = q.trim().lowercase()
                val local = (c.local.songs.value + downloaded)
                    .filter {
                        it.title.lowercase().contains(needle) ||
                            it.artist.lowercase().contains(needle) ||
                            it.album.lowercase().contains(needle)
                    }.take(30)
                _ui.value = _ui.value.copy(loading = true, local = local, lyricsMatches = emptyList(), albums = emptyList())
                val albumsJob: Deferred<List<OnlinePlaylist>>? = if (c.online.canGoOnline && c.settings.current.jiosaavnEnabled) {
                    viewModelScope.async { runCatching { movieAlbums(q.trim()) }.getOrDefault(emptyList<OnlinePlaylist>()) }
                } else null
                // A line from the lyrics: find which songs it is from, alongside the normal search.
                val lyricsJob: Deferred<List<Track>>? = if (LyricsSearch.looksLikeLine(q) && c.online.canGoOnline) {
                    viewModelScope.async { runCatching { songsFromLyrics(q.trim()) }.getOrDefault(emptyList<Track>()) }
                } else null
                // Hindi mein likha ho to Hinglish mein bhi dhoondho ("तुम ही हो" + "tum hi ho")
                val alt = q.trim().takeIf(Transliterate::hasDevanagari)?.let(Transliterate::toLatin)
                val online = if (alt.isNullOrBlank()) c.online.search(q.trim()) else {
                    val a = c.online.search(q.trim())
                    val b = c.online.search(alt)
                    (a + b).groupBy { it.source }.map { (src, rs) ->
                        SourceResult(src, rs.flatMap { it.tracks }.distinctBy { it.id }, rs.firstNotNullOfOrNull { it.error })
                    }
                }
                _ui.value = _ui.value.copy(loading = false, online = online)
                albumsJob?.await()?.let { _ui.value = _ui.value.copy(albums = it) }
                lyricsJob?.await()?.let { found ->
                    // Only songs the normal results don't already show at the top.
                    val shown = online.flatMap { it.tracks.take(5) }.map { it.title.lowercase() }.toSet()
                    _ui.value = _ui.value.copy(lyricsMatches = found.filterNot { it.title.lowercase() in shown })
                }
            }
        }
    }

    /**
     * Movies / albums whose name is what was typed ("aashiqui 2", "brahmastra songs"), not every album with one of
     * the words in it. Singles named like a song ("Kesariya (From Brahmastra)") are left out.
     */
    private suspend fun movieAlbums(q: String): List<OnlinePlaylist> {
        fun words(s: String) = s.lowercase().replace(Regex("""\(.*?\)|\[.*?]"""), " ")
            .replace(Regex("[^\\p{L}\\p{N}]+"), " ").split(" ").filter { it.isNotBlank() }
        val skip = setOf("songs", "song", "movie", "film", "album", "all", "ke", "gaane", "gane", "full", "jukebox")
        val want = words(q).filter { it !in skip }
        if (want.isEmpty()) return emptyList()
        return c.online.saavn.searchAlbums(want.joinToString(" "))
            .filter { a ->
                val name = words(a.title)
                // All typed words are in its name, or its whole name is in what was typed ("kesariya brahmastra").
                name.isNotEmpty() && (name.containsAll(want) || " ${want.joinToString(" ")} ".contains(" ${name.joinToString(" ")} "))
            }
            .filter { it.songCount == 0 || it.songCount >= 3 }
            .take(6)
    }

    /** Lyrics line -> song names (Genius) -> those songs on JioSaavn (else YouTube) to play. */
    private suspend fun songsFromLyrics(line: String): List<Track> = coroutineScope {
        LyricsSearch.find(line).map { hit ->
            async {
                val q = "${hit.title} ${hit.artist}".trim()
                runCatching { c.online.saavn.searchPage(q, 1) }.getOrDefault(emptyList()).firstOrNull()
                    ?: runCatching { c.online.searchAll(q) }.getOrDefault(emptyList()).firstOrNull()
            }
        }.awaitAll().filterNotNull().distinctBy { it.id }
    }

    /** Spoken words from voice search: search them right away and remember them. */
    fun onVoice(text: String) {
        onQuery(text)
        rememberQuery()
    }

    fun onQuery(q: String) {
        _ui.value = _ui.value.copy(query = q)
        queries.value = q
    }

    /** Searched (keyboard Search key) or picked a result: remember the words, and the song if one was picked. */
    fun rememberQuery(picked: Track? = null) {
        val q = _ui.value.query.trim()
        val add = listOfNotNull(picked?.let { RecentItem(track = it) }, q.takeIf { it.length >= 2 }?.let { RecentItem(query = it) })
        if (add.isEmpty()) return
        saveRecent(add + _ui.value.recent.filterNot { old -> add.any { same(it, old) } })
    }

    fun removeRecent(item: RecentItem) = saveRecent(_ui.value.recent.filterNot { same(it, item) })

    fun clearRecent() = saveRecent(emptyList())

    private fun same(a: RecentItem, b: RecentItem) =
        if (a.track != null || b.track != null) a.track?.id == b.track?.id else a.query.equals(b.query, true)

    private fun saveRecent(list: List<RecentItem>) {
        val keep = list.take(15)
        prefs.edit().putString("recent_items", Http.json.encodeToString(ListSerializer(RecentItem.serializer()), keep)).apply()
        _ui.value = _ui.value.copy(recent = keep)
    }

    private fun loadRecent(): List<RecentItem> {
        prefs.getString("recent_items", null)?.let { saved ->
            runCatching { return Http.json.decodeFromString(ListSerializer(RecentItem.serializer()), saved) }
        }
        // Older versions kept only the words.
        return prefs.getString("recent", null)?.split("\n")?.filter { it.isNotBlank() }.orEmpty().map { RecentItem(query = it) }
    }

    private suspend fun fetchSuggestions(q: String): List<String> {
        val url = "https://suggestqueries.google.com/complete/search?client=firefox&ds=yt&hl=hi&q=" +
            java.net.URLEncoder.encode(q, "UTF-8")
        val body = Http.getText(url) ?: return emptyList()
        val arr = Http.json.parseToJsonElement(body) as? JsonArray ?: return emptyList()
        return (arr.getOrNull(1) as? JsonArray).orEmpty()
            .mapNotNull { (it as? JsonPrimitive)?.content }
            .filter { !it.equals(q, true) }
            .take(8)
    }
}

private val globalGenres = listOf(
    "Electronic" to 0xFF8D67AB, "Hip-Hop/Rap" to 0xFFBA5D07, "Lo-Fi" to 0xFF477D95,
    "Pop" to 0xFF148A08, "Rock" to 0xFFE91429, "Ambient" to 0xFF1E3264,
    "Alternative" to 0xFFDC148C, "R&B/Soul" to 0xFF503750, "House" to 0xFF0D73EC,
    "Techno" to 0xFF7358FF, "Jazz" to 0xFFB06239, "Acoustic" to 0xFF27856A,
    "Classical" to 0xFF8C1932, "Dubstep" to 0xFFE8115B, "World" to 0xFF608108, "Soundtrack" to 0xFF777777,
)

@Composable
fun SearchScreen(nav: NavController) {
    val c = LocalAppContainer.current
    val vm = appViewModel { SearchViewModel(it) }
    val ui by vm.ui.collectAsStateWithLifecycle()
    var menuFor by remember { mutableStateOf<Track?>(null) }
    val spec = Sangeet.spec

    menuFor?.let { TrackOptionsSheet(it, onDismiss = { menuFor = null }) }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = bottomBarPadding()) {
        item {
            Text(
                "Search",
                style = MaterialTheme.typography.headlineMedium,
                color = spec.onSurface,
                modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 12.dp),
            )
        }
        item {
            // Classic Dark keeps the white Spotify-style box; other themes use their own surface (glass, soft 3D...).
            val classic = spec.style == ThemeStyle.SPOTIFY || spec.style == ThemeStyle.AMOLED
            val focus = LocalFocusManager.current
            val context = LocalContext.current
            // Voice search: the phone's own speech recognizer (no extra permission needed).
            val voice = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
                val said = r.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
                if (r.resultCode == Activity.RESULT_OK && !said.isNullOrBlank()) { vm.onVoice(said); focus.clearFocus() }
            }
            val fieldText = if (classic) Color(0xFF121212) else spec.onSurface
            val shape = RoundedCornerShape(if (spec.style == ThemeStyle.LIQUID_GLASS) 24.dp else 8.dp)
            TextField(
                value = ui.query,
                onValueChange = vm::onQuery,
                placeholder = { Text("What do you want to listen to?", color = if (classic) Color(0xFF535353) else spec.muted) },
                leadingIcon = { Icon(Icons.Rounded.Search, null, tint = fieldText) },
                trailingIcon = {
                    Row {
                        IconButton(onClick = {
                            val ask = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                                .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                                // Hindi + English (Hinglish song names, singers, lines of lyrics)
                                .putExtra(RecognizerIntent.EXTRA_LANGUAGE, "hi-IN")
                                .putExtra(RecognizerIntent.EXTRA_PROMPT, "Say a song, singer or a line of the lyrics")
                            runCatching { voice.launch(ask) }.onFailure {
                                Toast.makeText(context, "Voice search isn't available on this phone", Toast.LENGTH_SHORT).show()
                            }
                        }) { Icon(Icons.Rounded.Mic, "Voice search", tint = fieldText) }
                        if (ui.query.isNotEmpty()) {
                            IconButton(onClick = { vm.onQuery("") }) { Icon(Icons.Rounded.Close, "Clear", tint = fieldText) }
                        }
                    }
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { vm.rememberQuery(); focus.clearFocus() }),
                shape = shape,
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = if (classic) Color.White else Color.Transparent,
                    unfocusedContainerColor = if (classic) Color.White else Color.Transparent,
                    focusedTextColor = fieldText,
                    unfocusedTextColor = fieldText,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    cursorColor = if (classic) Color(0xFF121212) else spec.accent,
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .then(if (classic) Modifier else Modifier.themedCard(spec, shape, corner = if (spec.style == ThemeStyle.LIQUID_GLASS) 24.dp else 8.dp, elevation = 4.dp)),
            )
        }

        if (ui.query.isNotBlank() && ui.suggestions.isNotEmpty()) {
            item {
                Row(
                    Modifier
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    ui.suggestions.forEach { s ->
                        AssistChip(
                            onClick = { vm.onQuery(s) },
                            label = { Text(s) },
                            leadingIcon = { Icon(Icons.Rounded.NorthWest, null, Modifier.height(16.dp)) },
                            colors = AssistChipDefaults.assistChipColors(labelColor = spec.onSurface, leadingIconContentColor = spec.muted),
                        )
                    }
                }
            }
        }

        if (ui.query.isBlank() && ui.recent.isNotEmpty()) {
            item { SectionHeader("Recent searches", action = "Clear all") { vm.clearRecent() } }
            items(ui.recent.size, key = { "r_${it}_${ui.recent[it].track?.id ?: ui.recent[it].query}" }) { i ->
                RecentRow(
                    ui.recent[i],
                    onClick = {
                        val r = ui.recent[i]
                        if (r.track != null) c.player.startRadio(r.track) else vm.onQuery(r.query.orEmpty())
                    },
                    onRemove = { vm.removeRecent(ui.recent[i]) },
                )
            }
        }

        if (ui.query.isBlank()) {
            val festivals = Festivals.all
            item { SectionHeader("🎉 Festivals & seasons") }
            item {
                Row(
                    Modifier
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    festivals.forEach { f ->
                        AssistChip(
                            onClick = { nav.navigate(Routes.list(ListKind.GENRE, f.name)) },
                            label = { Text("${f.emoji} ${f.name}") },
                            colors = AssistChipDefaults.assistChipColors(labelColor = spec.onSurface),
                        )
                    }
                }
            }
            item { SectionHeader("Browse all") }
            val genres = Categories.ordered(c.settings.current.languages).map { "${it.emoji} ${it.name}" to it.color } +
                globalGenres
            items(genres.chunked(2)) { row ->
                Row(
                    Modifier.padding(horizontal = 16.dp, vertical = 5.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    row.forEach { (name, color) ->
                        Box(
                            Modifier
                                .weight(1f)
                                .height(96.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(color))
                                .clickable { nav.navigate(Routes.list(ListKind.GENRE, name.substringAfter(' ').takeIf { Categories.find(it) != null } ?: name)) }
                                .padding(12.dp),
                        ) {
                            Text(
                                name,
                                color = Color.White,
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            )
                        }
                    }
                }
            }
        } else {
            if (ui.albums.isNotEmpty()) {
                item(key = "albums_h") { SectionHeader("💿 Movies & albums") }
                item(key = "albums") {
                    LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        items(ui.albums, key = { "al_" + it.id }) { a ->
                            ShelfCard(
                                a.title,
                                listOfNotNull(a.subtitle.ifBlank { null }, a.songCount.takeIf { it > 0 }?.let { "$it songs" }).joinToString(" · "),
                                a.artworkUrl,
                                onClick = { nav.navigate(Routes.onlinePlaylist(ALBUM_PREFIX + a.id, a.title)) },
                            )
                        }
                    }
                }
            }
            if (ui.lyricsMatches.isNotEmpty()) {
                item { SectionHeader("🎤 Songs with these lyrics") }
                items(ui.lyricsMatches, key = { "ly_" + it.id }) { t ->
                    TrackRow(t, onClick = { vm.rememberQuery(t); c.player.startRadio(t) }, onMore = { menuFor = t })
                }
            }
            if (ui.local.isNotEmpty()) {
                item { SectionHeader("On this phone") }
                items(ui.local, key = { "l_" + it.id }) { t ->
                    TrackRow(t, onClick = { vm.rememberQuery(t); c.player.startRadio(t) }, onMore = { menuFor = t })
                }
            }
            ui.online.forEach { res ->
                if (res.tracks.isNotEmpty()) {
                    item(key = "h_${res.source}") { SectionHeader(res.source.label) }
                    items(res.tracks.size, key = { "o_${res.source}_${it}_${res.tracks[it].id}" }) { idx -> val t = res.tracks[idx]
                        TrackRow(t, onClick = { vm.rememberQuery(t); c.player.startRadio(t) }, onMore = { menuFor = t })
                    }
                }
            }
            if (ui.loading) item { LoadingBox() }
            if (!ui.loading && ui.local.isEmpty() && ui.online.all { it.tracks.isEmpty() }) {
                item {
                    EmptyState(
                        Icons.Rounded.SearchOff,
                        "No results",
                        if (c.online.canGoOnline) "Try different keywords." else "You're offline — only songs on this phone were searched.",
                    )
                }
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

/** A recent search: the song (cover, "Song • singer") or the words (clock icon), with ✕ to remove it. */
@Composable
private fun RecentRow(item: RecentItem, onClick: () -> Unit, onRemove: () -> Unit) {
    val spec = Sangeet.spec
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(start = 16.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val t = item.track
        if (t != null) {
            Artwork(t.artworkUrl, size = 48.dp, shape = RoundedCornerShape(6.dp), seed = t.title)
        } else {
            Box(Modifier.size(48.dp).clip(RoundedCornerShape(6.dp)).background(spec.surface), contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.History, null, tint = spec.muted)
            }
        }
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(t?.title ?: item.query.orEmpty(), color = spec.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                if (t != null) "Song • ${t.artist}" else "Search",
                color = spec.muted,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        IconButton(onClick = onRemove) { Icon(Icons.Rounded.Close, "Remove from recent", tint = spec.muted) }
    }
}
