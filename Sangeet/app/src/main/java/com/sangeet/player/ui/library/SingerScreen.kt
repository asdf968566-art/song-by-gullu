package com.sangeet.player.ui.library

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.sangeet.player.data.model.Track
import com.sangeet.player.ui.LocalAppContainer
import com.sangeet.player.ui.Routes
import com.sangeet.player.ui.components.Artwork
import com.sangeet.player.ui.components.EmptyState
import com.sangeet.player.ui.components.LoadingBox
import com.sangeet.player.ui.components.TrackOptionsSheet
import com.sangeet.player.ui.components.TrackRow
import com.sangeet.player.ui.theme.Sangeet
import com.sangeet.player.ui.theme.bottomBarPadding
import com.sangeet.player.ui.theme.themedCard

/**
 * A singer's page (report #69: "pahle playlist khule fir play button pe click ho tab song play ho"): all their songs
 * in the listener's languages, like a playlist. Play / Shuffle start them; nothing plays by itself.
 */
@Composable
fun SingerScreen(nav: NavController, name: String) {
    val c = LocalAppContainer.current
    var menuFor by remember { mutableStateOf<Track?>(null) }
    val songs by produceState<List<Track>?>(null, name) {
        value = c.singers.songs(name).also { android.util.Log.i("Sangeet", "singer page: ${it.size} songs by $name") }
    }
    val photo by produceState<String?>(null, name) { value = c.singers.photo(name) }

    menuFor?.let { TrackOptionsSheet(it, onDismiss = { menuFor = null }) }

    val list = songs.orEmpty()
    LazyColumn(Modifier.fillMaxSize(), contentPadding = bottomBarPadding()) {
        item { CollectionHeader(nav, name, "Singer", list, artwork = photo ?: list.firstOrNull()?.artworkUrl, playShuffled = true) }
        when {
            songs == null -> item { LoadingBox() }
            list.isEmpty() -> item { EmptyState(Icons.Rounded.Person, "No songs found", "No songs by $name in your languages.") }
            else -> itemsIndexed(list, key = { i, t -> "${i}_${t.id}" }) { i, t ->
                TrackRow(t, onClick = { c.player.play(list, i) }, onMore = { menuFor = t })
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

/** Home → Your singers → See all: every picked singer, then the ones played most (report #69). */
@Composable
fun AllSingersScreen(nav: NavController) {
    val c = LocalAppContainer.current
    val spec = Sangeet.spec
    val settings by c.settings.settings.collectAsStateWithLifecycle()
    val singers by produceState<List<String>?>(null, settings.favoriteSingers, settings.languages) {
        value = c.singers.yours(limit = 100).ifEmpty { c.singers.popular() }
    }
    LazyVerticalGrid(
        columns = GridCells.Adaptive(110.dp),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = bottomBarPadding().calculateBottomPadding()),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 4.dp)) {
                IconButton(onClick = { nav.popBackStack() }) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back", tint = spec.onSurface)
                }
                Text("Your singers", style = MaterialTheme.typography.titleLarge, color = spec.onSurface, modifier = Modifier.weight(1f))
                IconButton(onClick = { nav.navigate(Routes.SINGERS) }) {
                    Icon(Icons.Rounded.Edit, "Edit singers", tint = spec.onSurface)
                }
            }
        }
        val list = singers
        if (list == null) {
            item(span = { GridItemSpan(maxLineSpan) }) { LoadingBox() }
        } else {
            items(list, key = { "all_$it" }) { name -> SingerTile(name) { nav.navigate(Routes.singer(name)) } }
        }
    }
}

/** A singer: round photo and name (Home's "Your singers" and its See all page). */
@Composable
internal fun SingerTile(name: String, onClick: () -> Unit) {
    val c = LocalAppContainer.current
    val spec = Sangeet.spec
    val photo by produceState<String?>(null, name) { value = c.singers.photo(name) }
    Column(
        Modifier.width(100.dp).clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Artwork(
            photo,
            modifier = Modifier.themedCard(spec, CircleShape, corner = 50.dp),
            size = 96.dp,
            shape = CircleShape,
            seed = name,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            name,
            style = MaterialTheme.typography.labelLarge,
            color = spec.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
    }
}
