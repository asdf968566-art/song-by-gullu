package com.sangeet.player.ui.library

import android.content.Context
import android.util.JsonReader
import android.util.JsonToken
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.LibraryAdd
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import android.widget.Toast
import androidx.navigation.NavController
import com.sangeet.player.ui.theme.selectedFill
import com.sangeet.player.BuildConfig
import com.sangeet.player.data.model.Track
import com.sangeet.player.data.remote.Http
import com.sangeet.player.ui.LocalAppContainer
import com.sangeet.player.ui.Routes
import com.sangeet.player.ui.components.EmptyState
import com.sangeet.player.ui.components.LoadingBox
import com.sangeet.player.ui.components.TrackOptionsSheet
import com.sangeet.player.ui.components.TrackRow
import com.sangeet.player.ui.settings.SettingsTopBar
import com.sangeet.player.ui.theme.Sangeet
import com.sangeet.player.ui.theme.bottomBarPadding
import java.io.File
import java.io.InputStreamReader
import java.util.zip.GZIPInputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.Request

/** An Indian film and who made it (from Wikidata, rebuilt weekly by the catalog build). */
data class Movie(
    val title: String,
    val year: Int,
    val languages: List<String>,
    val music: List<String>,
    val producers: List<String>,
    val directors: List<String>,
    val cast: List<String>,
) {
    /** Everything searchable, lower case: "Pritam", "Yash Raj Films", "Shah Rukh Khan"... */
    val key: String = (listOf(title) + languages + music + producers + directors + cast).joinToString(" ").lowercase()
}

/** The movies list: the catalog release's movies.json.gz, kept on the phone for a week. */
object MovieList {
    @Volatile private var cached: List<Movie>? = null
    private val lock = Mutex()

    suspend fun load(context: Context): List<Movie> = cached ?: lock.withLock {
        cached ?: withContext(Dispatchers.IO) {
            val file = File(context.filesDir, "catalog/movies.json.gz").apply { parentFile?.mkdirs() }
            if (!file.exists() || System.currentTimeMillis() - file.lastModified() > 7 * 86_400_000L) runCatching { download(file) }
            if (file.exists()) runCatching { parse(file) }.getOrDefault(emptyList()) else emptyList()
        }.also { if (it.isNotEmpty()) cached = it }
    }

    private fun download(file: File) {
        val url = "https://github.com/${BuildConfig.UPDATE_REPO}/releases/download/catalog/movies.json.gz"
        Http.client.newCall(Request.Builder().url(url).build()).execute().use { res ->
            if (!res.isSuccessful) return
            val tmp = File(file.parentFile, "movies.tmp")
            res.body?.byteStream()?.use { input -> tmp.outputStream().use { input.copyTo(it) } } ?: return
            tmp.renameTo(file)
        }
    }

    /** [[title, year, languages, music, producers, directors, cast], ...], names "|"-separated. */
    private fun parse(file: File): List<Movie> {
        val out = ArrayList<Movie>(30_000)
        fun split(s: String) = if (s.isBlank()) emptyList() else s.split("|")
        JsonReader(InputStreamReader(GZIPInputStream(file.inputStream()), Charsets.UTF_8)).use { r ->
            r.beginArray()
            while (r.hasNext()) {
                r.beginArray()
                val f = ArrayList<String>(6)
                var year = 0
                var i = 0
                while (r.hasNext()) {
                    when {
                        r.peek() == JsonToken.NULL -> { r.nextNull(); if (i != 1) f += "" }
                        i == 1 -> year = r.nextInt()
                        else -> f += r.nextString()
                    }
                    i++
                }
                r.endArray()
                while (f.size < 6) f += ""
                out += Movie(f[0], year, split(f[1]), split(f[2]), split(f[3]), split(f[4]), split(f[5]))
            }
            r.endArray()
        }
        return out
    }
}

private val DECADES = listOf("All" to (0..9999), "2020s" to (2020..2029), "2010s" to (2010..2019), "2000s" to (2000..2009),
    "90s" to (1990..1999), "80s" to (1980..1989), "70s" to (1970..1979), "Older" to (0..1969))

/** Every Indian film, to filter by language, decade, music director, producer, director or actor. */
@Composable
fun MoviesScreen(nav: NavController, initial: String) {
    val context = LocalContext.current
    val spec = Sangeet.spec
    val all by produceState<List<Movie>?>(initialValue = null) { value = MovieList.load(context) }
    var query by rememberSaveable { mutableStateOf(initial) }
    var lang by rememberSaveable { mutableStateOf("") }
    var decade by rememberSaveable { mutableIntStateOf(0) }
    var shown by remember { mutableIntStateOf(60) }
    val list = all
    val langs = remember(list) {
        list.orEmpty().flatMap { it.languages }.groupingBy { it }.eachCount().entries.sortedByDescending { it.value }.map { it.key }.take(10)
    }
    val found = remember(list, query, lang, decade) {
        val words = query.lowercase().split(Regex("\\s+")).filter { it.isNotBlank() }
        val years = DECADES[decade].second
        list.orEmpty().filter { m -> (lang.isEmpty() || lang in m.languages) && m.year in years && words.all { it in m.key } }
    }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = bottomBarPadding()) {
        item { SettingsTopBar(nav, "🎬 Movies") }
        item {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it; shown = 60 },
                placeholder = { Text("Movie, music director, producer, actor…") },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            )
        }
        item {
            LazyRow(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(listOf("") + langs) { l ->
                    FilterChip(
                        selected = lang == l, onClick = { lang = l; shown = 60 },
                        label = { Text(l.ifEmpty { "All languages" }) },
                        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = spec.selectedFill),
                    )
                }
            }
        }
        item {
            LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                itemsIndexed(DECADES) { i, (name, _) ->
                    FilterChip(
                        selected = decade == i, onClick = { decade = i; shown = 60 },
                        label = { Text(name) },
                        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = spec.selectedFill),
                    )
                }
            }
        }
        when {
            list == null -> item { LoadingBox() }
            list.isEmpty() -> item { EmptyState(Icons.Rounded.CloudOff, "Movies aren't ready yet", "Check your connection and try again.") }
            else -> {
                item {
                    Text(if (found.size == 1) "1 movie" else "${found.size} movies", style = MaterialTheme.typography.bodySmall, color = spec.muted,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp))
                }
                // Index in the key: two films can share a name and year.
                itemsIndexed(found.take(shown), key = { i, m -> "${i}_${m.title}_${m.year}" }) { _, m ->
                    MovieRow(m) { nav.navigate(Routes.movie(m.title, m.year)) }
                }
                if (found.size > shown) item {
                    TextButton(onClick = { shown += 100 }, modifier = Modifier.padding(horizontal = 12.dp)) { Text("Show more") }
                }
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun MovieRow(m: Movie, onClick: () -> Unit) {
    val spec = Sangeet.spec
    val by = listOfNotNull(
        m.music.takeIf { it.isNotEmpty() }?.let { "Music: ${it.take(2).joinToString(", ")}" },
        m.directors.firstOrNull()?.let { "Director: $it" },
    ).joinToString(" · ")
    Column(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 20.dp, vertical = 10.dp)) {
        Text(
            if (m.year > 0) "${m.title}  ·  ${m.year}" else m.title,
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
            color = spec.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
        Text(listOf(m.languages.joinToString(", "), by).filter { it.isNotBlank() }.joinToString(" · "),
            style = MaterialTheme.typography.bodySmall, color = spec.muted, maxLines = 2, overflow = TextOverflow.Ellipsis)
        if (m.cast.isNotEmpty()) {
            Text(m.cast.take(3).joinToString(", "), style = MaterialTheme.typography.bodySmall, color = spec.muted,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/**
 * One film: its details (tap a name for that person's other films) and all its songs, to play one after another.
 * [albumId]: the JioSaavn album when it came from search.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MovieScreen(nav: NavController, title: String, year: Int, albumId: String) {
    val c = LocalAppContainer.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val spec = Sangeet.spec
    var menuFor by remember { mutableStateOf<Track?>(null) }
    val movie by produceState<Movie?>(initialValue = null, title, year) {
        value = MovieList.load(context).firstOrNull { it.title.equals(title, true) && (year == 0 || it.year == 0 || kotlin.math.abs(it.year - year) <= 1) }
    }
    val tracks by produceState<List<Track>?>(initialValue = null, title, year, albumId) {
        value = runCatching { c.online.movieSongs(title, year, albumId) }.getOrDefault(emptyList())
    }
    menuFor?.let { TrackOptionsSheet(it, onDismiss = { menuFor = null }) }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = bottomBarPadding()) {
        item {
            val sub = listOfNotNull(year.takeIf { it > 0 }?.toString(), movie?.languages?.joinToString(", ")?.ifBlank { null }).joinToString(" · ")
            CollectionHeader(nav, title, sub.ifBlank { "Movie" }, tracks.orEmpty()) {
                val list = tracks
                if (!list.isNullOrEmpty()) {
                    IconButton(onClick = {
                        scope.launch {
                            c.library.createPlaylist(title, list)
                            Toast.makeText(context, "Saved \"$title\" to your library", Toast.LENGTH_SHORT).show()
                        }
                    }) { Icon(Icons.Rounded.LibraryAdd, "Save to library", tint = spec.muted) }
                }
            }
        }
        movie?.let { m ->
            item {
                Column(Modifier.padding(horizontal = 16.dp)) {
                    listOf("Music" to m.music, "Producers" to m.producers, "Director" to m.directors, "Cast" to m.cast)
                        .filter { it.second.isNotEmpty() }
                        .forEach { (label, names) ->
                            Text(label, style = MaterialTheme.typography.labelMedium, color = spec.muted, modifier = Modifier.padding(top = 6.dp))
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                names.forEach { n ->
                                    AssistChip(
                                        onClick = { nav.navigate(Routes.movies(n)) },
                                        label = { Text(n) },
                                        colors = AssistChipDefaults.assistChipColors(labelColor = spec.onSurface),
                                    )
                                }
                            }
                        }
                }
            }
        }
        val list = tracks
        when {
            list == null -> item { LoadingBox() }
            list.isEmpty() -> item { EmptyState(Icons.Rounded.CloudOff, "No songs found", "This movie's songs aren't online yet.") }
            else -> itemsIndexed(list, key = { i, t -> "${i}_${t.id}" }) { i, t ->
                TrackRow(t, onClick = { c.player.play(list, i) }, index = i, onMore = { menuFor = t })
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}
