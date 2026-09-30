package com.sangeet.player.ui.library

import android.widget.Toast
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.LibraryAdd
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.sangeet.player.data.model.Track
import com.sangeet.player.ui.LocalAppContainer
import com.sangeet.player.ui.components.EmptyState
import com.sangeet.player.ui.components.TrackOptionsSheet
import com.sangeet.player.ui.components.TrackRow
import com.sangeet.player.ui.theme.Sangeet
import kotlinx.coroutines.launch

/** Track record se bani mix (Daily Mix, Artist Mix, On Repeat...). */
@Composable
fun MixScreen(nav: NavController, mixId: String) {
    val c = LocalAppContainer.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val mixes by c.recommendations.mixes.collectAsStateWithLifecycle()
    val mix = mixes.firstOrNull { it.id == mixId }
    var menuFor by remember { mutableStateOf<Track?>(null) }
    val tracks = mix?.tracks.orEmpty()

    menuFor?.let { TrackOptionsSheet(it, onDismiss = { menuFor = null }) }

    LazyColumn(Modifier.fillMaxSize()) {
        item {
            CollectionHeader(nav, mix?.title ?: "Mix", mix?.subtitle ?: "Made for you", tracks, artwork = mix?.artworkUrl) {
                if (mix != null) {
                    IconButton(onClick = {
                        scope.launch {
                            c.library.createPlaylist(mix.title, mix.tracks)
                            Toast.makeText(context, "Saved \"${mix.title}\" as a playlist", Toast.LENGTH_SHORT).show()
                        }
                    }) { Icon(Icons.Rounded.LibraryAdd, "Save as playlist", tint = Sangeet.spec.muted) }
                }
            }
        }
        if (tracks.isEmpty()) {
            item { EmptyState(Icons.Rounded.AutoAwesome, "This mix isn't ready yet", "Refresh on Home or listen to a few songs.") }
        }
        itemsIndexed(tracks, key = { _, t -> t.id }) { i, t ->
            TrackRow(t, onClick = { c.player.play(tracks, i) }, index = i, onMore = { menuFor = t })
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}
