package com.sangeet.player.ui.components

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PlaylistAdd
import androidx.compose.material.icons.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.DownloadDone
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.RemoveCircleOutline
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sangeet.player.data.model.DownloadState
import com.sangeet.player.data.model.Track
import com.sangeet.player.ui.LocalAppContainer
import com.sangeet.player.ui.LocalNav
import com.sangeet.player.ui.Routes
import androidx.compose.material.icons.rounded.Radio
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Share
import com.sangeet.player.ui.theme.Sangeet
import kotlinx.coroutines.launch

/** Gaane ke "..." menu: next play, queue, like, download, playlist. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrackOptionsSheet(
    track: Track,
    onDismiss: () -> Unit,
    playlistId: Long? = null,
) {
    val container = LocalAppContainer.current
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val favorites by container.library.favoriteIds.collectAsStateWithLifecycle()
    val downloads by container.downloads.downloads.collectAsStateWithLifecycle()
    var showPlaylists by remember { mutableStateOf(false) }
    val spec = Sangeet.spec
    val liked = track.id in favorites
    val dl = downloads[track.id]

    if (showPlaylists) {
        AddToPlaylistDialog(listOf(track)) { showPlaylists = false; onDismiss() }
        return
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(),
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Column(Modifier.navigationBarsPadding().padding(bottom = 12.dp)) {
            Row(Modifier.padding(horizontal = 20.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Artwork(track.artworkUrl, size = 56.dp, seed = track.title)
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(track.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        "${track.artist} • ${track.source.label}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = spec.muted,
                        maxLines = 1,
                    )
                }
            }
            SheetItem(if (liked) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder, if (liked) "Liked Songs se hatao" else "Like karo") {
                scope.launch { container.library.toggleFavorite(track) }
                onDismiss()
            }
            SheetItem(Icons.Rounded.SkipNext, "Agla bajao (Play next)") {
                container.player.playNext(track); onDismiss()
            }
            SheetItem(Icons.Rounded.QueueMusic, "Queue mein daalo") {
                container.player.addToQueue(listOf(track)); onDismiss()
            }
            SheetItem(Icons.Rounded.PlaylistAdd, "Playlist mein daalo") { showPlaylists = true }
            SheetItem(Icons.Rounded.Radio, "Start radio") {
                container.player.startRadio(track); onDismiss()
            }
            val nav = LocalNav.current
            if (nav != null && track.artist.isNotBlank()) {
                SheetItem(Icons.Rounded.Person, "Go to artist") {
                    onDismiss(); nav.navigate(Routes.artist(track.artist))
                }
            }
            SheetItem(Icons.Rounded.Share, "Share") {
                scope.launch { runCatching { ShareCard.share(context, track) } }
                onDismiss()
            }
            if (playlistId != null) {
                SheetItem(Icons.Rounded.RemoveCircleOutline, "Is playlist se hatao") {
                    scope.launch { container.library.removeFromPlaylist(playlistId, track.id) }
                    onDismiss()
                }
            }
            if (track.source.isOnline) {
                when (dl?.state) {
                    DownloadState.DONE -> SheetItem(Icons.Rounded.Delete, "Download delete karo") {
                        scope.launch { container.downloads.remove(track.id) }
                        onDismiss()
                    }
                    DownloadState.QUEUED, DownloadState.DOWNLOADING -> SheetItem(Icons.Rounded.DownloadDone, "Download ho raha hai… ${dl?.progress ?: 0}% (cancel)") {
                        scope.launch { container.downloads.remove(track.id) }
                        onDismiss()
                    }
                    else -> SheetItem(Icons.Rounded.Download, "Download karo (offline ke liye)") {
                        scope.launch {
                            container.downloads.download(track)
                            Toast.makeText(context, "Download shuru: ${track.title}", Toast.LENGTH_SHORT).show()
                        }
                        onDismiss()
                    }
                }
            }
        }
    }
}

@Composable
private fun SheetItem(icon: ImageVector, label: String, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = Sangeet.spec.onSurface)
        Spacer(Modifier.width(20.dp))
        Text(label, style = MaterialTheme.typography.bodyLarge)
    }
}

/** Playlist chuno ya nayi banao. */
@Composable
fun AddToPlaylistDialog(tracks: List<Track>, startCreating: Boolean = false, onDone: () -> Unit) {
    val container = LocalAppContainer.current
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val playlists by container.library.playlists.collectAsState(initial = emptyList())
    var newName by remember { mutableStateOf("") }
    var creating by remember { mutableStateOf(startCreating) }

    AlertDialog(
        onDismissRequest = onDone,
        title = { Text(if (creating) "Nayi playlist" else "Playlist mein daalo") },
        text = {
            if (creating) {
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    label = { Text("Naam") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                )
            } else {
                LazyColumn(Modifier.heightIn(max = 360.dp)) {
                    item {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable { creating = true }
                                .padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Icons.Rounded.Add, null, tint = Sangeet.spec.accent)
                            Spacer(Modifier.width(12.dp))
                            Text("Nayi playlist banao", color = Sangeet.spec.accent)
                        }
                    }
                    items(playlists, key = { it.id }) { p ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable {
                                    scope.launch {
                                        container.library.addToPlaylist(p.id, tracks)
                                        Toast.makeText(context, "${p.name} mein add hua", Toast.LENGTH_SHORT).show()
                                        onDone()
                                    }
                                }
                                .padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Artwork(p.coverUrl, size = 44.dp, seed = p.name)
                            Spacer(Modifier.width(12.dp))
                            Column {
                                Text(p.name, style = MaterialTheme.typography.titleSmall)
                                Text("${p.trackCount} gaane", style = MaterialTheme.typography.bodySmall, color = Sangeet.spec.muted)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (creating) {
                TextButton(onClick = {
                    scope.launch {
                        container.library.createPlaylist(newName.trim(), tracks)
                        Toast.makeText(context, "Playlist bani", Toast.LENGTH_SHORT).show()
                        onDone()
                    }
                }) { Text("Banao") }
            }
        },
        dismissButton = { TextButton(onClick = onDone) { Text("Cancel") } },
    )
}
