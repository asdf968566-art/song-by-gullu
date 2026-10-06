package com.sangeet.player.ui.library

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.QueueMusic
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.sangeet.player.data.model.Track
import com.sangeet.player.data.playlist.PlaylistImporter
import com.sangeet.player.ui.LocalAppContainer
import com.sangeet.player.ui.components.EmptyState
import com.sangeet.player.ui.components.TrackOptionsSheet
import com.sangeet.player.ui.components.TrackRow
import com.sangeet.player.ui.theme.Sangeet
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.sangeet.player.ui.theme.bottomBarPadding

@Composable
fun PlaylistScreen(nav: NavController, playlistId: Long) {
    val c = LocalAppContainer.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val spec = Sangeet.spec
    val playlist by remember(playlistId) { c.library.playlist(playlistId) }.collectAsState(initial = null)
    val tracks by remember(playlistId) { c.library.playlistTracks(playlistId) }.collectAsState(initial = emptyList())
    var menuFor by remember { mutableStateOf<Track?>(null) }
    var showMenu by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    val name = playlist?.name ?: "Playlist"

    val exporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("audio/x-mpegurl")) { uri ->
        if (uri != null) scope.launch {
            val ok = withContext(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.openOutputStream(uri)?.use {
                        it.write(PlaylistImporter.toM3u(name, tracks).toByteArray())
                    }
                }.isSuccess
            }
            Toast.makeText(context, if (ok) "Playlist exported" else "Export failed", Toast.LENGTH_SHORT).show()
        }
    }

    menuFor?.let { TrackOptionsSheet(it, onDismiss = { menuFor = null }, playlistId = playlistId) }

    if (renaming) {
        var text by remember { mutableStateOf(name) }
        AlertDialog(
            onDismissRequest = { renaming = false },
            title = { Text("Rename") },
            text = { OutlinedTextField(text, { text = it }, singleLine = true, shape = RoundedCornerShape(12.dp)) },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch { c.library.renamePlaylist(playlistId, text.trim().ifBlank { name }) }
                    renaming = false
                }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { renaming = false }) { Text("Cancel") } },
        )
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete playlist?") },
            text = { Text("\"$name\" will be deleted. Your songs and downloads won't be affected.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    scope.launch {
                        c.library.deletePlaylist(playlistId)
                        nav.popBackStack()
                    }
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
    }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = bottomBarPadding()) {
        item {
            CollectionHeader(nav, name, "Playlist", tracks) {
                Box {
                    IconButton(onClick = { showMenu = true }) { Icon(Icons.Rounded.MoreVert, "Options", tint = spec.muted) }
                    DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                        DropdownMenuItem(text = { Text("Rename") }, onClick = { showMenu = false; renaming = true })
                        DropdownMenuItem(text = { Text("Add to queue") }, onClick = { showMenu = false; c.player.addToQueue(tracks) })
                        DropdownMenuItem(text = { Text("Export (.m3u8)") }, onClick = { showMenu = false; exporter.launch("$name.m3u8") })
                        DropdownMenuItem(text = { Text("Delete playlist") }, onClick = { showMenu = false; confirmDelete = true })
                    }
                }
            }
        }
        if (tracks.isEmpty()) {
            item {
                EmptyState(
                    Icons.Rounded.QueueMusic,
                    "This playlist is empty",
                    "Tap ⋮ on any song and choose 'Add to playlist'.",
                )
            }
        }
        itemsIndexed(tracks, key = { i, t -> "${i}_${t.id}" }) { i, t ->
            TrackRow(t, onClick = { c.player.play(tracks, i) }, index = i, onMore = { menuFor = t })
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}
