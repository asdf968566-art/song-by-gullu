package com.sangeet.player.ui.library

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.sangeet.player.data.db.ArtistTime
import com.sangeet.player.data.db.TrackTime
import com.sangeet.player.ui.LocalAppContainer
import com.sangeet.player.ui.Routes
import com.sangeet.player.ui.components.EmptyState
import com.sangeet.player.ui.components.LoadingBox
import com.sangeet.player.ui.components.SectionHeader
import com.sangeet.player.ui.theme.Sangeet
import java.time.LocalDate
import com.sangeet.player.ui.theme.bottomBarPadding
import androidx.compose.material3.Button
import com.sangeet.player.ui.components.ShareCard
import androidx.compose.ui.platform.LocalContext

private data class Stats(
    val totalMs: Long,
    val artists: List<ArtistTime>,
    val tracks: List<TrackTime>,
    val topHour: Int?,
    val streak: Int,
)

/** "Your stats": listening time, top artists and songs, favourite hour, streak (Wrapped-style). */
@Composable
fun StatsScreen(nav: NavController) {
    val c = LocalAppContainer.current
    val spec = Sangeet.spec
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var period by rememberSaveable { mutableIntStateOf(0) } // 0 week, 1 month, 2 all time
    val periods = listOf("This week", "This month", "All time")

    val stats by produceState<Stats?>(initialValue = null, period) {
        val since = when (period) {
            0 -> System.currentTimeMillis() - 7 * 86_400_000L
            1 -> System.currentTimeMillis() - 30 * 86_400_000L
            else -> 0L
        }
        val dao = c.database.listenDao()
        val days = dao.activeDays(0L).mapNotNull { runCatching { LocalDate.parse(it) }.getOrNull() }.toSet()
        var streak = 0
        var d = LocalDate.now()
        if (d !in days) d = d.minusDays(1)
        while (d in days) { streak++; d = d.minusDays(1) }
        value = Stats(
            totalMs = dao.totalMs(since),
            artists = dao.topArtists(since, 5),
            tracks = dao.topTracks(since, 10),
            topHour = dao.byHour(since).firstOrNull()?.hour,
            streak = streak,
        )
    }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = bottomBarPadding()) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(4.dp)) {
                IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back", tint = spec.onSurface) }
                Text("Your Stats", style = MaterialTheme.typography.titleLarge, color = spec.onSurface)
            }
        }
        item {
            Row(Modifier.padding(horizontal = 16.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                periods.forEachIndexed { i, label ->
                    FilterChip(
                        selected = period == i,
                        onClick = { period = i },
                        label = { Text(label) },
                        shape = RoundedCornerShape(50),
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = spec.accent,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                        ),
                    )
                }
            }
        }
        val s = stats
        when {
            s == null -> item { LoadingBox() }
            s.totalMs < 60_000 -> item {
                EmptyState(Icons.Rounded.BarChart, "No stats yet", "Listen to a few songs and your stats will show up here.")
            }
            else -> {
                item {
                    // A picture of your year (minutes, top songs and singers, streak) for Instagram / WhatsApp.
                    Button(
                        onClick = {
                            c.scope.launch {
                                val cover = s.tracks.firstOrNull()?.let { c.library.find(it.trackId)?.artworkUrl }
                                runCatching {
                                    ShareCard.shareWrapped(
                                        context, s.totalMs / 60_000, s.tracks.map { it.title to it.artist },
                                        s.artists.map { it.artist.substringBefore(",").trim() }.distinct(), s.streak, cover,
                                    )
                                }
                            }
                        },
                        modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth(),
                    ) { Text("🎁 Share my Wrapped") }
                }
                item {
                    Column(
                        Modifier
                            .padding(16.dp)
                            .fillMaxWidth()
                            .background(
                                Brush.linearGradient(listOf(spec.accent, spec.accent.copy(alpha = 0.35f))),
                                RoundedCornerShape(20.dp),
                            )
                            .padding(20.dp),
                    ) {
                        Text("You listened for", color = Color.White.copy(alpha = 0.85f), style = MaterialTheme.typography.titleSmall)
                        Text(
                            formatListen(s.totalMs),
                            color = Color.White,
                            fontSize = 40.sp,
                            fontWeight = FontWeight.Black,
                        )
                        Spacer(Modifier.height(8.dp))
                        s.topHour?.let {
                            Text("Your favourite time: ${hourLabel(it)}", color = Color.White, style = MaterialTheme.typography.bodyMedium)
                        }
                        if (s.streak > 0) {
                            Text("🔥 ${s.streak}-day listening streak", color = Color.White, style = MaterialTheme.typography.bodyMedium)
                        }
                        s.artists.firstOrNull()?.let {
                            Text("Top artist: ${it.artist}", color = Color.White, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
                if (s.artists.isNotEmpty()) {
                    item { SectionHeader("Top artists") }
                    val max = s.artists.maxOf { it.ms }.coerceAtLeast(1)
                    itemsIndexed(s.artists) { i, a ->
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .clickable { nav.navigate(Routes.artist(a.artist)) }
                                .padding(horizontal = 16.dp, vertical = 6.dp),
                        ) {
                            Row {
                                Text("${i + 1}. ${a.artist}", color = spec.onSurface, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(formatListen(a.ms), color = spec.muted, style = MaterialTheme.typography.bodySmall)
                            }
                            Spacer(Modifier.height(4.dp))
                            Box(Modifier.fillMaxWidth().height(6.dp).background(spec.muted.copy(alpha = 0.2f), RoundedCornerShape(3.dp))) {
                                Box(Modifier.fillMaxWidth(a.ms.toFloat() / max).height(6.dp).background(spec.accent, RoundedCornerShape(3.dp)))
                            }
                        }
                    }
                }
                if (s.tracks.isNotEmpty()) {
                    item { SectionHeader("Top songs") }
                    itemsIndexed(s.tracks) { i, t ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable {
                                    scope.launch { (c.library.peek(t.trackId) ?: c.library.find(t.trackId))?.let { c.player.play(listOf(it)) } }
                                }
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text("${i + 1}", color = spec.muted, modifier = Modifier.width(28.dp))
                            Column(Modifier.weight(1f)) {
                                Text(t.title, color = spec.onSurface, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text("${t.artist} • ${if (t.plays == 1) "1 play" else "${t.plays} plays"}", color = spec.muted, style = MaterialTheme.typography.bodySmall, maxLines = 1)
                            }
                            Text(formatListen(t.ms), color = spec.muted, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

private fun formatListen(ms: Long): String {
    val mins = ms / 60_000
    return if (mins >= 60) "${mins / 60} hr ${mins % 60} min" else "$mins min"
}

private fun hourLabel(h: Int): String {
    val period = when (h) {
        in 5..11 -> "morning"
        in 12..16 -> "afternoon"
        in 17..20 -> "evening"
        else -> "night"
    }
    val h12 = if (h % 12 == 0) 12 else h % 12
    return "$h12 ${if (h < 12) "AM" else "PM"} ($period)"
}
