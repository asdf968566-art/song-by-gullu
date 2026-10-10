package com.sangeet.player.ui.library

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.sangeet.player.data.remote.LiveRadio
import com.sangeet.player.ui.LocalAppContainer
import com.sangeet.player.ui.components.LoadingBox
import com.sangeet.player.ui.components.TrackRow
import com.sangeet.player.ui.settings.SettingsTopBar
import com.sangeet.player.ui.theme.Sangeet
import com.sangeet.player.ui.theme.bottomBarPadding

/** Live FM radio: India's stations (Mirchi, Red FM, Vividh Bharati, AIR…), by language. Tap to listen live. */
@Composable
fun RadioScreen(nav: NavController) {
    val c = LocalAppContainer.current
    val spec = Sangeet.spec
    val stations by produceState<List<LiveRadio.Station>?>(null) { value = LiveRadio.stations() }
    var filter by remember { mutableStateOf("all") }
    val all = stations
    LazyColumn(Modifier.fillMaxSize(), contentPadding = bottomBarPadding()) {
        item { SettingsTopBar(nav, "Live radio") }
        if (all == null) { item { LoadingBox() }; return@LazyColumn }
        if (all.isEmpty()) {
            item { Text("Couldn't load radio stations. Check your internet.", color = spec.muted, modifier = Modifier.padding(20.dp)) }
            return@LazyColumn
        }
        // The listener's languages first, then the rest that have stations.
        val mine = c.settings.current.languages
        val langs = (mine + all.flatMap { it.languages }.groupingBy { it }.eachCount().entries.sortedByDescending { it.value }.map { it.key })
            .distinct().filter { l -> all.any { l in it.languages } }.take(10)
        item {
            Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                (listOf("all") + langs).forEach { l ->
                    FilterChip(
                        selected = filter == l,
                        onClick = { filter = l },
                        label = { Text(if (l == "all") "All" else l.replaceFirstChar(Char::uppercase)) },
                        shape = RoundedCornerShape(50),
                        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = spec.accent, selectedLabelColor = MaterialTheme.colorScheme.onPrimary),
                    )
                }
            }
        }
        val shown = if (filter == "all") all else all.filter { filter in it.languages }
        items(shown, key = { it.track.id }) { s ->
            TrackRow(s.track, onClick = { c.player.play(listOf(s.track)) })
        }
    }
}
