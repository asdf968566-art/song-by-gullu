package com.sangeet.player.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.sangeet.player.ui.LocalAppContainer
import com.sangeet.player.ui.components.Artwork
import com.sangeet.player.ui.theme.Sangeet
import com.sangeet.player.ui.theme.bottomBarPadding
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Settings → Favourite singers (report #57): search or tap singers to pick them; Home shows them first. */
@Composable
fun SingersScreen(nav: NavController) {
    val c = LocalAppContainer.current
    val s by c.settings.settings.collectAsStateWithLifecycle()
    val spec = Sangeet.spec
    var query by remember { mutableStateOf("") }
    val results by produceState<List<Pair<String, String?>>>(emptyList(), query) {
        val q = query.trim()
        if (q.length < 2) {
            value = emptyList()
            return@produceState
        }
        delay(350)
        value = c.singers.search(q)
    }
    val picked = s.favoriteSingers
    val played by produceState(emptyList<String>(), picked) { value = c.singers.yours(20) }
    val isPicked: (String) -> Boolean = { name -> picked.any { it.equals(name, ignoreCase = true) } }
    val toggle: (String) -> Unit = { name ->
        val next = if (isPicked(name)) picked.filterNot { it.equals(name, ignoreCase = true) } else picked + name
        // The app's scope: the change is kept even if the screen closes right away.
        c.scope.launch { c.settings.setFavoriteSingers(next) }
    }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = bottomBarPadding()) {
        item { SettingsTopBar(nav, "Favourite singers") }
        item {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text("Search a singer") },
                leadingIcon = { Icon(Icons.Rounded.Search, null) },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }
        if (query.trim().length >= 2) {
            items(results, key = { "r_" + it.first }) { (name, photo) -> SingerRow(name, photo, isPicked(name)) { toggle(name) } }
        } else {
            val sections = listOf(
                "Your singers" to picked,
                "From your listening" to played.filterNot(isPicked),
                "Popular in your languages" to c.singers.popular().filterNot(isPicked),
            )
            sections.forEach { (title, names) ->
                if (names.isNotEmpty()) {
                    item(key = "h_$title") {
                        Text(
                            title.uppercase(),
                            style = MaterialTheme.typography.labelMedium,
                            color = spec.accent,
                            modifier = Modifier.padding(start = 24.dp, top = 16.dp, bottom = 4.dp),
                        )
                    }
                    items(names, key = { "${title}_$it" }) { name -> SingerRow(name, null, isPicked(name)) { toggle(name) } }
                }
            }
        }
    }
}

/** A singer with their photo; the mark on the right says if they are picked. */
@Composable
private fun SingerRow(name: String, photo: String?, picked: Boolean, onClick: () -> Unit) {
    val c = LocalAppContainer.current
    val spec = Sangeet.spec
    val url by produceState(photo, name) { if (photo == null) value = c.singers.photo(name) }
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 20.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Artwork(url, size = 48.dp, shape = CircleShape, seed = name)
        Spacer(Modifier.width(14.dp))
        Text(
            name,
            style = MaterialTheme.typography.titleMedium,
            color = spec.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Icon(
            if (picked) Icons.Rounded.CheckCircle else Icons.Rounded.Add,
            if (picked) "Picked" else "Pick",
            tint = if (picked) spec.accent else spec.muted,
        )
    }
}
