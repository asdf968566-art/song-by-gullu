package com.sangeet.player.ui.library

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.FileOpen
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavController
import com.sangeet.player.AppContainer
import com.sangeet.player.data.playlist.ImportEntry
import com.sangeet.player.ui.Routes
import com.sangeet.player.ui.appViewModel
import com.sangeet.player.ui.theme.Sangeet
import com.sangeet.player.ui.theme.themedCard
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ImportViewModel(private val c: AppContainer) : ViewModel() {
    data class Ui(
        val name: String = "",
        val entries: List<ImportEntry> = emptyList(),
        val searchOnline: Boolean = true,
        val working: Boolean = false,
        val progress: Float = 0f,
        val matched: Int = 0,
        val missing: List<ImportEntry> = emptyList(),
        val createdId: Long? = null,
        val error: String? = null,
    )

    private val _ui = MutableStateFlow(Ui())
    val ui: StateFlow<Ui> = _ui.asStateFlow()

    fun load(uri: Uri) = viewModelScope.launch {
        _ui.value = Ui(working = true)
        _ui.value = try {
            val (name, entries) = c.importer.read(uri)
            if (entries.isEmpty()) Ui(error = "No songs found in this file.")
            else Ui(name = name, entries = entries)
        } catch (e: Exception) {
            Ui(error = e.message ?: "Couldn't read the file")
        }
    }

    /** Spotify / YouTube Music / YouTube / JioSaavn link. */
    fun loadLink(link: String) = viewModelScope.launch {
        if (link.isBlank()) return@launch
        _ui.value = Ui(working = true)
        _ui.value = try {
            val r = c.importer.readLink(link)
            when {
                r.tracks.isNotEmpty() -> Ui(name = r.name, matched = r.tracks.size, createdId = c.library.createPlaylist(r.name, r.tracks))
                r.entries.isNotEmpty() -> Ui(name = r.name, entries = r.entries)
                else -> Ui(error = "No songs found in that playlist.")
            }
        } catch (e: Exception) {
            Ui(error = e.message ?: "Couldn't open that link")
        }
    }

    fun setName(n: String) { _ui.value = _ui.value.copy(name = n) }
    fun setSearchOnline(v: Boolean) { _ui.value = _ui.value.copy(searchOnline = v) }

    fun runImport() = viewModelScope.launch {
        val s = _ui.value
        _ui.value = s.copy(working = true, progress = 0f)
        val result = c.importer.match(s.name, s.entries, s.searchOnline) { done, total ->
            _ui.value = _ui.value.copy(progress = done.toFloat() / total)
        }
        val id = c.library.createPlaylist(s.name.ifBlank { result.name }, result.matched)
        _ui.value = _ui.value.copy(
            working = false,
            matched = result.matched.size,
            missing = result.missing,
            createdId = id,
        )
    }
}

@Composable
fun ImportPlaylistScreen(nav: NavController) {
    val vm = appViewModel { ImportViewModel(it) }
    val ui by vm.ui.collectAsStateWithLifecycle()
    val spec = Sangeet.spec
    var link by remember { mutableStateOf("") }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.load(uri)
    }

    LazyColumn(Modifier.fillMaxSize()) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(4.dp)) {
                IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back", tint = spec.onSurface) }
                Text("Import playlist", style = MaterialTheme.typography.titleLarge, color = spec.onSurface)
            }
        }
        item {
            Column(
                Modifier
                    .padding(16.dp)
                    .fillMaxWidth()
                    .themedCard(spec, RoundedCornerShape(16.dp))
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("Paste a playlist link", style = MaterialTheme.typography.titleMedium, color = spec.onSurface)
                OutlinedTextField(
                    value = link,
                    onValueChange = { link = it },
                    placeholder = { Text("Spotify, YouTube Music, YouTube or JioSaavn") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth(),
                )
                Button(onClick = { vm.loadLink(link) }, enabled = !ui.working && link.isNotBlank()) { Text("Import link") }
                if (ui.working && ui.entries.isEmpty()) LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), color = spec.accent)
                Spacer(Modifier.height(8.dp))
                Text("Or choose a file", style = MaterialTheme.typography.titleMedium, color = spec.onSurface)
                Text(
                    "M3U / M3U8 / PLS, CSV (Exportify, TuneMyMusic) or TXT with one \"Artist - Title\" per line.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = spec.muted,
                )
                OutlinedButton(onClick = { picker.launch(arrayOf("*/*")) }, enabled = !ui.working) {
                    Icon(Icons.Rounded.FileOpen, null)
                    Text("  Choose file")
                }
            }
        }

        ui.error?.let { err ->
            item { Text(err, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(16.dp)) }
        }

        if (ui.entries.isNotEmpty() && ui.createdId == null) {
            item {
                Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = ui.name,
                        onValueChange = vm::setName,
                        label = { Text("Playlist name") },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = ui.searchOnline, onCheckedChange = vm::setSearchOnline)
                        Text("Search online if not on phone", color = spec.onSurface)
                    }
                    Text("${if (ui.entries.size == 1) "1 song" else "${ui.entries.size} songs"} found in file", color = spec.muted)
                    if (ui.working) {
                        LinearProgressIndicator(progress = { ui.progress }, modifier = Modifier.fillMaxWidth(), color = spec.accent)
                        Text("Matching songs… ${(ui.progress * 100).toInt()}%", color = spec.muted)
                    } else {
                        Button(onClick = { vm.runImport() }, modifier = Modifier.fillMaxWidth()) { Text("Import") }
                    }
                }
            }
            items(ui.entries.take(200)) { e ->
                Text(
                    listOf(e.artist, e.title).filter { it.isNotBlank() }.joinToString(" - ").ifBlank { e.location ?: "?" },
                    color = spec.muted,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 3.dp),
                )
            }
        }

        ui.createdId?.let { id ->
            item {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.CheckCircle, null, tint = spec.accent)
                        Text(
                            "  ${if (ui.matched == 1) "1 song" else "${ui.matched} songs"} imported, ${ui.missing.size} not found",
                            style = MaterialTheme.typography.titleMedium,
                            color = spec.onSurface,
                        )
                    }
                    Button(onClick = {
                        nav.popBackStack()
                        nav.navigate(Routes.playlist(id))
                    }) { Text("Open playlist") }
                    OutlinedButton(onClick = { picker.launch(arrayOf("*/*")) }) { Text("Import another") }
                    if (ui.missing.isNotEmpty()) Text("Not found:", color = spec.muted)
                }
            }
            items(ui.missing) { e ->
                Text(
                    listOf(e.artist, e.title).filter { it.isNotBlank() }.joinToString(" - "),
                    color = spec.muted,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 3.dp),
                )
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}
