package com.sangeet.player.ui.settings

import android.util.Base64
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
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.sangeet.player.BuildConfig
import com.sangeet.player.data.remote.Http
import com.sangeet.player.ui.components.LoadingBox
import com.sangeet.player.ui.theme.Sangeet
import com.sangeet.player.ui.theme.bottomBarPadding
import com.sangeet.player.ui.theme.themedCard
import java.io.ByteArrayInputStream
import java.util.zip.Inflater
import java.util.zip.InflaterInputStream
import com.sangeet.player.data.Usage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.longOrNull

/**
 * The owner's numbers: APK downloads (GitHub, kept across builds) and the Community stats the catalog build keeps
 * in the private data repo ("sangeet-stats" issue): how many listeners, versions, languages, what they play and
 * search, and each listener (random id) with their singers, songs, playlists and searches.
 */
private object OwnerStats {
    data class Snapshot(
        val downloadsTotal: Int?, val downloadsThisBuild: Int?, val stats: JsonObject?, val error: String?,
        /** Hit counters (Usage): installs and daily users of the Android app and the iPhone website. */
        val counts: Map<String, Long?> = emptyMap(),
    )

    private suspend fun get(url: String, auth: Boolean = false): String? = runCatching {
        Http.getText(url, if (auth) mapOf("Authorization" to "Bearer ${BuildConfig.REPORT_TOKEN}", "Accept" to "application/vnd.github+json") else emptyMap())
    }.getOrNull()

    suspend fun load(): Snapshot = withContext(Dispatchers.IO) {
        val names = listOf("android-users", "web-users") +
            listOf(0, 1).flatMap { d -> listOf("android-day-${Usage.day(d)}", "web-day-${Usage.day(d)}") }
        val counts = names.map { n -> async { n to Usage.read(n) } }.awaitAll().toMap()
        loadStats().copy(counts = counts)
    }

    private suspend fun loadStats(): Snapshot = withContext(Dispatchers.IO) {
        val repo = BuildConfig.UPDATE_REPO
        val now = get("https://api.github.com/repos/$repo/releases/tags/latest")?.let { body ->
            (Http.json.parseToJsonElement(body).jsonObject["assets"] as? JsonArray).orEmpty()
                .mapNotNull { it as? JsonObject }
                .filter { (it["name"] as? JsonPrimitive)?.contentOrNull == "Sangeet.apk" }
                .sumOf { (it["download_count"] as? JsonPrimitive)?.intOrNull ?: 0 }
        }
        val before = get("https://github.com/$repo/releases/download/stats/downloads.json")?.let { body ->
            runCatching { (Http.json.parseToJsonElement(body).jsonObject["total"] as? JsonPrimitive)?.intOrNull }.getOrNull()
        }
        val total = if (now == null && before == null) null else (before ?: 0) + (now ?: 0)
        if (BuildConfig.DATA_REPO.isBlank()) return@withContext Snapshot(total, now, null, "No private data repo yet, so there are no listener stats.")
        val issues = get("https://api.github.com/repos/${BuildConfig.DATA_REPO}/issues?state=open&per_page=100", auth = true)
            ?: return@withContext Snapshot(total, now, null, "Couldn't reach GitHub. Check your internet.")
        val body = runCatching {
            Http.json.parseToJsonElement(issues).jsonArray.mapNotNull { it as? JsonObject }
                .firstOrNull { (it["title"] as? JsonPrimitive)?.contentOrNull == "sangeet-stats" }
                ?.get("body")?.let { (it as? JsonPrimitive)?.contentOrNull }
        }.getOrNull() ?: return@withContext Snapshot(total, now, null, "No stats yet: they appear after the next catalog build once listeners have sent data.")
        val packed = body.substringAfter("\n").trim()
        val json = runCatching {
            InflaterInputStream(ByteArrayInputStream(Base64.decode(packed, Base64.DEFAULT)), Inflater(true)).bufferedReader().use { it.readText() }
        }.getOrNull() ?: return@withContext Snapshot(total, now, null, "The stats couldn't be read.")
        Snapshot(total, now, Http.json.parseToJsonElement(json).jsonObject, null)
    }
}

private fun ago(epochSec: Long?): String {
    if (epochSec == null || epochSec <= 0) return "?"
    val m = (System.currentTimeMillis() / 1000 - epochSec) / 60
    return when {
        m < 60 -> "${m.coerceAtLeast(1)} min ago"
        m < 60 * 24 -> "${m / 60} h ago"
        else -> "${m / (60 * 24)} days ago"
    }
}

private fun JsonElement?.str() = (this as? JsonPrimitive)?.contentOrNull.orEmpty()
private fun JsonElement?.num() = (this as? JsonPrimitive)?.longOrNull
private fun JsonElement?.list() = (this as? JsonArray).orEmpty()
/** [["name", count], ...] -> "name (count) · ..." */
private fun JsonElement?.pairs(n: Int = 12) = list().take(n).joinToString("  ·  ") { e ->
    val a = e.list()
    "${a.getOrNull(0).str()} (${a.getOrNull(1).num() ?: 0})"
}

@Composable
fun OwnerDashboard(nav: NavController) = PasswordGate(nav, "Owner dashboard", "Enter the password to see the app's numbers") {
    var reload by remember { mutableIntStateOf(0) }
    val snap by produceState<OwnerStats.Snapshot?>(initialValue = null, reload) { value = OwnerStats.load() }
    val spec = Sangeet.spec
    LazyColumn(Modifier.fillMaxSize(), contentPadding = bottomBarPadding()) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) { SettingsTopBar(nav, "Owner dashboard") }
                IconButton(onClick = { reload++ }) { Icon(Icons.Rounded.Refresh, "Refresh", tint = spec.onSurface) }
            }
        }
        val s = snap
        if (s == null) {
            item { LoadingBox() }
            return@LazyColumn
        }
        val st = s.stats
        item {
            Card("Downloads") {
                Line("APK downloads", s.downloadsTotal?.toString() ?: "?")
                Line("This build", s.downloadsThisBuild?.toString() ?: "?")
                Text("Counts the Sangeet.apk file on GitHub (in-app updates too). The iPhone app is a website: it has no download count.",
                    style = MaterialTheme.typography.bodySmall, color = spec.muted)
            }
        }
        item {
            fun n(k: String) = s.counts[k]?.toString() ?: "?"
            Card("Users of both apps") {
                Line("Android phones (all time)", n("android-users"))
                Line("Android today", n("android-day-${Usage.day()}"))
                Line("Android yesterday", n("android-day-${Usage.day(1)}"))
                Line("iPhone / website (all time)", n("web-users"))
                Line("iPhone today", n("web-day-${Usage.day()}"))
                Line("iPhone yesterday", n("web-day-${Usage.day(1)}"))
                Text("Counted from this update on; one count per phone or browser (test phones are left out).",
                    style = MaterialTheme.typography.bodySmall, color = spec.muted)
            }
        }
        if (st == null) {
            item { Text(s.error ?: "", color = spec.muted, modifier = Modifier.padding(20.dp)) }
            return@LazyColumn
        }
        val users = st["users"] as? JsonObject
        item {
            Card("Listeners") {
                Line("All (last 60 days)", users?.get("total").num()?.toString() ?: "0")
                Line("Today", users?.get("today").num()?.toString() ?: "0")
                Line("This week", users?.get("week").num()?.toString() ?: "0")
                Line("This month", users?.get("month").num()?.toString() ?: "0")
                Text("Android phones with \"Help improve suggestions\" on. Updated ${ago(st["built"].num())}.",
                    style = MaterialTheme.typography.bodySmall, color = spec.muted)
            }
        }
        item { Card("App versions") { Text(st["versions"].pairs(), color = spec.onSurface) } }
        item { Card("Most used screens") { Text(st["features"].pairs(20).ifBlank { "Not yet" }, color = spec.onSurface) } }
        (st["crashes"] as? JsonObject)?.let { cr ->
            item {
                Card("Crashes") {
                    Line("Crashes", cr["total"].num()?.toString() ?: "0")
                    Line("Phones that crashed", cr["phones"].num()?.toString() ?: "0")
                    cr["by_version"].pairs(8).takeIf { it.isNotBlank() }?.let { Text("By version: $it", color = spec.onSurface) }
                    cr["by_phone"].pairs(8).takeIf { it.isNotBlank() }?.let { Text("By phone: $it", color = spec.onSurface) }
                    cr["recent"].list().take(6).forEach { e ->
                        Text("• ${e.list().getOrNull(0).str()}", style = MaterialTheme.typography.bodySmall, color = spec.muted)
                    }
                }
            }
        }
        item { Card("Phones") { Text(st["phones"].pairs(15).ifBlank { "Not yet" }, color = spec.onSurface) } }
        item { Card("Song languages") { Text(st["languages"].pairs(), color = spec.onSurface) } }
        item { Card("Most played (listeners)") { Text(st["top_songs"].pairs(20), color = spec.onSurface) } }
        item { Card("Rising now") { Text(st["rising"].pairs(15).ifBlank { "Nothing yet" }, color = spec.onSurface) } }
        item { Card("Top singers (listeners)") { Text(st["top_artists"].pairs(20), color = spec.onSurface) } }
        item { Card("Top searches") { Text(st["top_searches"].pairs(30), color = spec.onSurface) } }
        val people = st["people"].list().mapNotNull { it as? JsonObject }
        item {
            Text("Listeners one by one (${people.size})", style = MaterialTheme.typography.titleMedium, color = spec.onSurface,
                modifier = Modifier.padding(start = 20.dp, top = 16.dp, bottom = 4.dp))
        }
        items(people, key = { it["id"].str() + it["seen"].num() }) { p ->
            Card("Listener ${p["id"].str()}") {
                Text("${p["p"].str().replaceFirstChar(Char::uppercase)} ${p["app"].str()} · seen ${ago(p["seen"].num())} · since ${ago(p["first"].num())} · ${p["uploads"].num() ?: 1} uploads",
                    style = MaterialTheme.typography.bodySmall, color = spec.muted)
                p["m"].str().takeIf { it.isNotBlank() }?.let { Text("Phone: $it" + ((p["crash"].num() ?: 0L).takeIf { c -> c > 0 }?.let { c -> " · $c crashes" } ?: ""), color = spec.onSurface) }
                p["use"].list().takeIf { it.isNotEmpty() }?.let { u -> Text("Uses: " + u.joinToString(", ") { e -> "${e.list().getOrNull(0).str()} ${e.list().getOrNull(1).num() ?: 0}" }, color = spec.muted) }
                Text("Languages: ${p["langs"].list().joinToString(", ") { it.str() }}", color = spec.onSurface)
                Text("Liked ${p["likes"].num() ?: 0} · played ${p["plays"].num() ?: 0} songs", color = spec.onSurface)
                p["artists"].list().takeIf { it.isNotEmpty() }?.let { Text("Singers: ${it.joinToString(", ") { a -> a.str() }}", color = spec.onSurface) }
                p["songs"].list().takeIf { it.isNotEmpty() }?.let { Text("Plays most: ${it.joinToString("  ·  ") { a -> a.str() }}", color = spec.onSurface) }
                p["playlists"].list().takeIf { it.isNotEmpty() }?.let { lists ->
                    Text("Playlists: " + lists.mapNotNull { it as? JsonObject }.joinToString("  ·  ") { "${it["n"].str()} (${it["c"].num() ?: 0})" }, color = spec.onSurface)
                }
                p["searches"].list().takeIf { it.isNotEmpty() }?.let { Text("Searched: ${it.joinToString(", ") { a -> a.str() }}", color = spec.muted) }
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun Card(title: String, content: @Composable () -> Unit) {
    val spec = Sangeet.spec
    Column(
        Modifier.padding(horizontal = 16.dp, vertical = 6.dp).fillMaxWidth()
            .themedCard(spec, RoundedCornerShape(16.dp)).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(title, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold), color = spec.onSurface)
        content()
    }
}

@Composable
private fun Line(label: String, value: String) {
    val spec = Sangeet.spec
    Row(Modifier.fillMaxWidth()) {
        Text(label, color = spec.muted, modifier = Modifier.weight(1f))
        Text(value, color = spec.onSurface, style = MaterialTheme.typography.titleMedium)
    }
}
