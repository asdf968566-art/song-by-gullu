package com.sangeet.player.ui.library

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.DownloadForOffline
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FileOpen
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.Radio
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material3.Button
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.sangeet.player.ui.LocalAppContainer
import com.sangeet.player.ui.Routes
import com.sangeet.player.ui.audioPermission
import com.sangeet.player.ui.components.AddToPlaylistDialog
import com.sangeet.player.ui.components.Artwork
import com.sangeet.player.ui.theme.Sangeet
import com.sangeet.player.ui.theme.themedCard
import kotlinx.coroutines.launch
import com.sangeet.player.ui.theme.bottomBarPadding
import androidx.compose.material.icons.rounded.People
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import androidx.compose.ui.platform.LocalContext

@Composable
fun LibraryScreen(nav: NavController) {
    val c = LocalAppContainer.current
    val spec = Sangeet.spec
    val scope = rememberCoroutineScope()
    val playlists by c.library.playlists.collectAsState(initial = emptyList())
    val favorites by c.library.favorites.collectAsState(initial = emptyList())
    val downloaded by c.downloads.downloadedTracks.collectAsState(initial = emptyList())
    val activeDownloads by c.downloads.activeCount.collectAsState(initial = 0)
    val localSongs by c.local.songs.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableStateOf(0) } // 0 playlists, 1 albums, 2 artists
    var creating by remember { mutableStateOf(false) }
    var blendAsk by remember { mutableStateOf(false) }
    if (blendAsk) BlendDialog { blendAsk = false }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) scope.launch { c.local.scan() }
    }

    if (creating) AddToPlaylistDialog(emptyList(), startCreating = true) { creating = false }

    val albums = remember(localSongs) { c.local.albums(localSongs) }
    val artists = remember(localSongs) { c.local.artists(localSongs) }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = bottomBarPadding()) {
        item {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 4.dp, top = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Your Library", style = MaterialTheme.typography.headlineMedium, color = spec.onSurface, modifier = Modifier.weight(1f))
                IconButton(onClick = { nav.navigate(Routes.IMPORT) }) { Icon(Icons.Rounded.FileOpen, "Import playlist", tint = spec.onSurface) }
                IconButton(onClick = { creating = true }) { Icon(Icons.Rounded.Add, "New playlist", tint = spec.onSurface) }
            }
        }
        item {
            Row(
                Modifier
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                listOf("Playlists", "Albums", "Artists").forEachIndexed { i, label ->
                    FilterChip(
                        selected = tab == i,
                        onClick = { tab = i },
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

        if (localSongs.isEmpty()) {
            item {
                Column(
                    Modifier
                        .padding(16.dp)
                        .fillMaxWidth()
                        .themedCard(spec, RoundedCornerShape(14.dp), corner = 14.dp)
                        .padding(16.dp)
                ) {
                    Text("Show songs on this phone?", style = MaterialTheme.typography.titleMedium, color = spec.onSurface)
                    Text(
                        "Allow access to audio files to play music stored on your phone.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = spec.muted,
                    )
                    Spacer(Modifier.height(10.dp))
                    Button(onClick = { permissionLauncher.launch(audioPermission) }) { Text("Allow") }
                }
            }
        }

        when (tab) {
            0 -> {
                item {
                    LibraryRow(
                        title = "Online Library",
                        subtitle = "Charts, playlists and millions of songs",
                        gradient = listOf(Color(0xFFE13300), Color(0xFFF59B23)),
                        icon = Icons.Rounded.Public,
                    ) { nav.navigate(Routes.ONLINE_LIBRARY) }
                }
                item {
                    LibraryRow(
                        title = "Movies",
                        subtitle = "Every film's songs: by year, music director, producer, actor",
                        gradient = listOf(Color(0xFFB45309), Color(0xFFF59E0B)),
                        icon = Icons.Rounded.Movie,
                    ) { nav.navigate(Routes.movies()) }
                }
                item {
                    LibraryRow(
                        title = "Live radio",
                        subtitle = "Mirchi, Red FM, Vividh Bharati and more, live",
                        gradient = listOf(Color(0xFF0EA5E9), Color(0xFF6366F1)),
                        icon = Icons.Rounded.Radio,
                    ) { nav.navigate(Routes.RADIO) }
                }
                item {
                    LibraryRow(
                        title = "Listen together",
                        subtitle = "The same song on your friends' phones",
                        gradient = listOf(Color(0xFFDB2777), Color(0xFFF97316)),
                        icon = Icons.Rounded.Groups,
                    ) { nav.navigate(Routes.together()) }
                }
                item {
                    LibraryRow(
                        title = "Your Stats",
                        subtitle = "Listening time, top artists, streak",
                        gradient = listOf(Color(0xFF7C4DFF), Color(0xFF00E5C3)),
                        icon = Icons.Rounded.BarChart,
                    ) { nav.navigate(Routes.STATS) }
                }
                item {
                    LibraryRow(
                        title = "Blend with a friend",
                        subtitle = "One playlist from your taste and a friend's",
                        gradient = listOf(Color(0xFFFF5F6D), Color(0xFFFFC371)),
                        icon = Icons.Rounded.People,
                    ) { blendAsk = true }
                }
                item {
                    LibraryRow(
                        title = "Liked Songs",
                        subtitle = "Playlist • ${if (favorites.size == 1) "1 song" else "${favorites.size} songs"}",
                        gradient = listOf(Color(0xFF4A2BD8), Color(0xFF8EC5E8)),
                        icon = Icons.Rounded.Favorite,
                    ) { nav.navigate(Routes.list(ListKind.LIKED)) }
                }
                item {
                    LibraryRow(
                        title = "Downloads",
                        subtitle = "Offline • ${if (downloaded.size == 1) "1 song" else "${downloaded.size} songs"}" +
                            if (activeDownloads > 0) " • $activeDownloads downloading" else "",
                        gradient = listOf(Color(0xFF0B6E4F), Color(0xFF1DB954)),
                        icon = Icons.Rounded.DownloadForOffline,
                    ) { nav.navigate(Routes.DOWNLOADS) }
                }
                item {
                    LibraryRow(
                        title = "On this phone",
                        subtitle = "Local • ${if (localSongs.size == 1) "1 song" else "${localSongs.size} songs"}",
                        gradient = listOf(Color(0xFFB2458C), Color(0xFFF7B267)),
                        icon = Icons.Rounded.PhoneAndroid,
                    ) { nav.navigate(Routes.list(ListKind.LOCAL)) }
                }
                item {
                    LibraryRow(
                        title = "Recently played",
                        subtitle = "History",
                        gradient = listOf(Color(0xFF2E3A59), Color(0xFF7C8DB5)),
                        icon = Icons.Rounded.History,
                    ) { nav.navigate(Routes.list(ListKind.RECENT)) }
                }
                items(playlists, key = { "p${it.id}" }) { p ->
                    LibraryRow(title = p.name, subtitle = "Playlist • ${if (p.trackCount == 1) "1 song" else "${p.trackCount} songs"}", artwork = p.coverUrl) {
                        nav.navigate(Routes.playlist(p.id))
                    }
                }
            }
            1 -> items(albums, key = { "a${it.id}" }) { a ->
                LibraryRow(title = a.title, subtitle = "Album • ${a.artist}", artwork = a.artworkUrl) {
                    nav.navigate(Routes.list(ListKind.ALBUM, a.id.toString()))
                }
            }
            else -> items(artists, key = { "r${it.name}" }) { a ->
                LibraryRow(title = a.name, subtitle = "Artist • ${if (a.tracks.size == 1) "1 song" else "${a.tracks.size} songs"}", artwork = a.artworkUrl, circle = true) {
                    nav.navigate(Routes.list(ListKind.ARTIST, a.name))
                }
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun LibraryRow(
    title: String,
    subtitle: String,
    artwork: String? = null,
    gradient: List<Color>? = null,
    icon: ImageVector? = null,
    circle: Boolean = false,
    onClick: () -> Unit,
) {
    val spec = Sangeet.spec
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val shape = if (circle) CircleShape else RoundedCornerShape(6.dp)
        if (gradient != null && icon != null) {
            Box(
                Modifier
                    .size(64.dp)
                    .background(Brush.linearGradient(gradient), shape),
                contentAlignment = Alignment.Center,
            ) { Icon(icon, null, tint = Color.White, modifier = Modifier.size(28.dp)) }
        } else {
            Artwork(artwork, size = 64.dp, shape = shape, seed = title)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = spec.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = spec.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/**
 * Blend: sends a link with your taste. The friend opens it on iPhone (Sangeet web app) or pastes it in
 * Import playlist on Android, and gets one playlist mixed from both.
 */
@Composable
private fun BlendDialog(onDone: () -> Unit) {
    val c = LocalAppContainer.current
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("blend", android.content.Context.MODE_PRIVATE) }
    var name by remember { mutableStateOf(prefs.getString("name", "").orEmpty()) }
    AlertDialog(
        onDismissRequest = onDone,
        title = { Text("Blend with a friend") },
        text = {
            Column {
                Text("Send your friend a link with your taste. When they open it, Sangeet mixes your songs with theirs into one playlist. Ask them for theirs too, and paste it in Import playlist.")
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Your name") }, singleLine = true)
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val who = name.trim().ifBlank { "Friend" }
                prefs.edit().putString("name", who).apply()
                c.scope.launch {
                    val link = com.sangeet.player.data.LibrarySync.blendLink(c.library, who)
                    val send = android.content.Intent(android.content.Intent.ACTION_SEND).setType("text/plain")
                        .putExtra(android.content.Intent.EXTRA_TEXT, "Let's Blend on Sangeet 🎵 Open this to get a playlist from both our tastes:\n$link")
                    context.startActivity(android.content.Intent.createChooser(send, "Send Blend link").addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
                }
                onDone()
            }) { Text("Send link") }
        },
        dismissButton = { TextButton(onClick = onDone) { Text("Cancel") } },
    )
}
