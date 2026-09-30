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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.Refresh
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
import com.sangeet.player.data.SourceResult
import com.sangeet.player.data.model.Track
import com.sangeet.player.ui.Routes
import com.sangeet.player.ui.appViewModel
import com.sangeet.player.ui.components.LoadingBox
import com.sangeet.player.ui.components.QuickTile
import com.sangeet.player.ui.components.SectionHeader
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
import kotlinx.coroutines.launch

class HomeViewModel(private val c: AppContainer) : ViewModel() {
    data class Ui(val loading: Boolean = false, val trending: List<SourceResult> = emptyList())

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

    fun refresh() {
        viewModelScope.launch {
            _ui.value = _ui.value.copy(loading = true)
            val results = c.online.trending()
            _ui.value = Ui(loading = false, trending = results)
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
                IconButton(onClick = vm::refresh) { Icon(Icons.Rounded.Refresh, "Refresh", tint = spec.onSurface) }
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
                        if (settings.offlineMode) "  Offline mode on hai — sirf downloaded aur phone ke gaane"
                        else "  Internet nahi hai — downloaded aur phone ke gaane chal rahe hain",
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
                add(Triple("Phone ke gaane", localSongs.firstOrNull()?.artworkUrl) { nav.navigate(Routes.list(ListKind.LOCAL)) })
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

        if (filter != 1 && recent.isNotEmpty()) {
            item { SectionHeader("Recently played") }
            item { TrackShelf(recent, onPlay = { c.player.play(recent, it) }, onMore = { menuFor = it }) }
        }
        if (filter != 1 && mostPlayed.size >= 3) {
            item { SectionHeader("Aapke favourite (sabse zyada suna)") }
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
            item { SectionHeader("Aapke downloads", action = "Sab dekho") { nav.navigate(Routes.list(ListKind.DOWNLOADS)) } }
            item { TrackShelf(downloaded, onPlay = { c.player.play(downloaded, it) }, onMore = { menuFor = it }) }
        }
        if (filter != 1 && localSongs.isNotEmpty()) {
            item { SectionHeader("Phone se", action = "Sab dekho") { nav.navigate(Routes.list(ListKind.LOCAL)) } }
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
