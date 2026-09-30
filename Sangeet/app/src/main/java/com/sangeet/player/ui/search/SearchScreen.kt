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

@OptIn(FlowPreview::class)
class SearchViewModel(private val c: AppContainer) : ViewModel() {
    data class Ui(
        val query: String = "",
        val loading: Boolean = false,
        val local: List<Track> = emptyList(),
        val online: List<SourceResult> = emptyList(),
        /** Likhte waqt suggestions ("kes" -> "kesariya"). */
        val suggestions: List<String> = emptyList(),
        val recent: List<String> = emptyList(),
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
                    _ui.value = Ui(query = q)
                    return@collectLatest
                }
                val needle = q.trim().lowercase()
                val local = (c.local.songs.value + downloaded)
                    .filter {
                        it.title.lowercase().contains(needle) ||
                            it.artist.lowercase().contains(needle) ||
                            it.album.lowercase().contains(needle)
                    }.take(30)
                _ui.value = _ui.value.copy(loading = true, local = local)
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
            }
        }
    }

    fun onQuery(q: String) {
        _ui.value = _ui.value.copy(query = q)
        queries.value = q
    }

    /** Result pe tap kiya -> ye search "Recent" mein yaad rakho. */
    fun rememberQuery() {
        val q = _ui.value.query.trim()
        if (q.length < 2) return
        val list = (listOf(q) + loadRecent().filterNot { it.equals(q, true) }).take(10)
        prefs.edit().putString("recent", list.joinToString("\n")).apply()
        _ui.value = _ui.value.copy(recent = list)
    }

    fun clearRecent() {
        prefs.edit().remove("recent").apply()
        _ui.value = _ui.value.copy(recent = emptyList())
    }

    private fun loadRecent(): List<String> =
        prefs.getString("recent", null)?.split("\n")?.filter { it.isNotBlank() }.orEmpty()

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

    LazyColumn(Modifier.fillMaxSize()) {
        item {
            Text(
                "Search",
                style = MaterialTheme.typography.headlineMedium,
                color = spec.onSurface,
                modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 12.dp),
            )
        }
        item {
            TextField(
                value = ui.query,
                onValueChange = vm::onQuery,
                placeholder = { Text("What do you want to listen to?", color = Color(0xFF535353)) },
                leadingIcon = { Icon(Icons.Rounded.Search, null, tint = Color(0xFF121212)) },
                trailingIcon = {
                    if (ui.query.isNotEmpty()) {
                        IconButton(onClick = { vm.onQuery("") }) { Icon(Icons.Rounded.Close, "Clear", tint = Color(0xFF121212)) }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(8.dp),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.White,
                    unfocusedContainerColor = Color.White,
                    focusedTextColor = Color(0xFF121212),
                    unfocusedTextColor = Color(0xFF121212),
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    cursorColor = Color(0xFF121212),
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
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
            item { SectionHeader("Recent searches", action = "Clear") { vm.clearRecent() } }
            item {
                Row(
                    Modifier
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    ui.recent.forEach { r ->
                        AssistChip(
                            onClick = { vm.onQuery(r) },
                            label = { Text(r) },
                            leadingIcon = { Icon(Icons.Rounded.History, null, Modifier.height(16.dp)) },
                            colors = AssistChipDefaults.assistChipColors(labelColor = spec.onSurface, leadingIconContentColor = spec.muted),
                        )
                    }
                }
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
            if (ui.local.isNotEmpty()) {
                item { SectionHeader("On this phone") }
                items(ui.local, key = { "l_" + it.id }) { t ->
                    TrackRow(t, onClick = { vm.rememberQuery(); c.player.play(ui.local, ui.local.indexOf(t)) }, onMore = { menuFor = t })
                }
            }
            ui.online.forEach { res ->
                if (res.tracks.isNotEmpty()) {
                    item(key = "h_${res.source}") { SectionHeader(res.source.label) }
                    items(res.tracks, key = { "o_" + it.id }) { t ->
                        TrackRow(t, onClick = { vm.rememberQuery(); c.player.play(res.tracks, res.tracks.indexOf(t)) }, onMore = { menuFor = t })
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
