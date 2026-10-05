package com.sangeet.player.ui.library

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DownloadForOffline
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.sangeet.player.data.model.DownloadInfo
import com.sangeet.player.data.model.DownloadState
import com.sangeet.player.data.model.Track
import com.sangeet.player.ui.LocalAppContainer
import com.sangeet.player.ui.components.Artwork
import com.sangeet.player.ui.components.EmptyState
import com.sangeet.player.ui.components.SectionHeader
import com.sangeet.player.ui.theme.Sangeet
import kotlinx.coroutines.launch

/**
 * Download status: what is downloading right now (with %), what is waiting, what failed (retry),
 * and everything already saved on the phone (tap to play, works without internet).
 */
@Composable
fun DownloadsScreen(nav: NavController) {
    val c = LocalAppContainer.current
    val spec = Sangeet.spec
    val scope = rememberCoroutineScope()
    val items by c.downloads.items.collectAsState(initial = emptyList())

    val downloading = items.filter { it.second.state == DownloadState.DOWNLOADING }
    val waiting = items.filter { it.second.state == DownloadState.QUEUED }
    val failed = items.filter { it.second.state == DownloadState.FAILED }
    val done = items.filter { it.second.state == DownloadState.DONE }.map { it.first }

    LazyColumn(Modifier.fillMaxSize()) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(4.dp)) {
                IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back", tint = spec.onSurface) }
                Column(Modifier.weight(1f)) {
                    Text("Downloads", style = MaterialTheme.typography.titleLarge, color = spec.onSurface)
                    Text(
                        listOfNotNull(
                            "${done.size} saved",
                            (downloading.size + waiting.size).takeIf { it > 0 }?.let { "$it in progress" },
                            failed.size.takeIf { it > 0 }?.let { "$it failed" },
                        ).joinToString(" • "),
                        style = MaterialTheme.typography.bodySmall,
                        color = spec.muted,
                    )
                }
                if (done.isNotEmpty()) {
                    IconButton(onClick = { c.player.play(done) }) { Icon(Icons.Rounded.PlayArrow, "Play downloads", tint = spec.onSurface) }
                }
            }
        }

        if (items.isEmpty()) {
            item {
                EmptyState(
                    Icons.Rounded.DownloadForOffline,
                    "No downloads yet",
                    "Tap ⋮ on any song and choose Download. It will show up here with its progress.",
                )
            }
        }

        if (downloading.isNotEmpty()) {
            item { SectionHeader("Downloading now") }
            items(downloading, key = { "d_" + it.first.id }) { (t, info) ->
                DownloadRow(t, info, onCancel = { scope.launch { c.downloads.remove(t.id) } })
            }
        }
        if (waiting.isNotEmpty()) {
            item { SectionHeader("Waiting (${waiting.size})") }
            items(waiting, key = { "w_" + it.first.id }) { (t, info) ->
                DownloadRow(t, info, onCancel = { scope.launch { c.downloads.remove(t.id) } })
            }
        }
        if (failed.isNotEmpty()) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) { SectionHeader("Failed (${failed.size})") }
                    TextButton(onClick = { scope.launch { c.downloads.downloadAll(failed.map { it.first }) } }) { Text("Retry all") }
                }
            }
            items(failed, key = { "f_" + it.first.id }) { (t, info) ->
                DownloadRow(
                    t, info,
                    onCancel = { scope.launch { c.downloads.remove(t.id) } },
                    onRetry = { scope.launch { c.downloads.download(t) } },
                )
            }
        }
        if (done.isNotEmpty()) {
            item { SectionHeader("Saved on this phone (${done.size})") }
            items(done.size, key = { "s_${it}_${done[it].id}" }) { i ->
                val t = done[i]
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { c.player.play(done, i) }
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Artwork(t.artworkUrl, size = 48.dp, shape = RoundedCornerShape(6.dp), seed = t.title)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(t.title, color = spec.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(t.artist, color = spec.muted, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    IconButton(onClick = { scope.launch { c.downloads.remove(t.id) } }) { Icon(Icons.Rounded.Close, "Remove download", tint = spec.muted) }
                }
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun DownloadRow(t: Track, info: DownloadInfo, onCancel: () -> Unit, onRetry: (() -> Unit)? = null) {
    val spec = Sangeet.spec
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Artwork(t.artworkUrl, size = 48.dp, shape = RoundedCornerShape(6.dp), seed = t.title)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(t.title, color = spec.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
            when (info.state) {
                DownloadState.DOWNLOADING -> {
                    LinearProgressIndicator(
                        progress = { info.progress / 100f },
                        modifier = Modifier.fillMaxWidth(),
                        color = spec.accent,
                    )
                    Text("${info.progress}% • ${t.artist}", color = spec.muted, style = MaterialTheme.typography.bodySmall, maxLines = 1)
                }
                DownloadState.QUEUED -> {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), color = spec.muted)
                    Text("Waiting to start • ${t.artist}", color = spec.muted, style = MaterialTheme.typography.bodySmall, maxLines = 1)
                }
                DownloadState.FAILED ->
                    Text("Failed. Check your internet and retry", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                DownloadState.DONE -> Unit
            }
        }
        if (onRetry != null) IconButton(onClick = onRetry) { Icon(Icons.Rounded.Refresh, "Retry", tint = spec.accent) }
        IconButton(onClick = onCancel) { Icon(Icons.Rounded.Close, "Cancel download", tint = spec.muted) }
    }
}
