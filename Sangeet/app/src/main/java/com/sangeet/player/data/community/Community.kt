package com.sangeet.player.data.community

import android.content.Context
import android.util.Base64
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.sangeet.player.BuildConfig
import com.sangeet.player.SangeetApplication
import com.sangeet.player.data.model.SourceType
import com.sangeet.player.data.model.Track
import com.sangeet.player.data.remote.Http
import com.sangeet.player.ui.search.RecentItem
import java.io.ByteArrayOutputStream
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import java.util.concurrent.TimeUnit
import java.util.zip.Deflater
import java.util.zip.DeflaterOutputStream
import java.util.zip.GZIPInputStream
import kotlin.random.Random
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.addJsonArray
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.int
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * Listening data shared between the app's listeners, so suggestions, the catalog and the DJ learn from everyone:
 *
 * - Upload: every few hours when online (at most every 6 hours) the phone sends what it searched, liked, played and put in
 *   playlists, under a random id (no name, number or contacts; phone files are never sent), as an issue in a
 *   private GitHub repo ([BuildConfig.DATA_REPO]). Settings → "Help improve suggestions" turns it off.
 * - Download: the catalog build merges everyone's data into community.json (only what two or more listeners
 *   share): songs played together, and what most listeners play.
 */
object Community {
    private const val PREFS = "community"
    private const val WORK = "community_upload"
    private const val MARK = "SANGEET-DATA v1"
    private const val MAX_BODY = 60_000

    // ------------------------------------------------------------ upload

    fun schedule(context: Context, enabled: Boolean) {
        val wm = WorkManager.getInstance(context)
        if (!enabled || BuildConfig.DATA_REPO.isBlank() || BuildConfig.REPORT_TOKEN.isBlank()) {
            wm.cancelUniqueWork(WORK)
            return
        }
        // Any time there's internet, every few hours (owner: "not only at night"); the worker skips it when the
        // last upload is under 6 hours old. Also once soon after the app opens.
        val online = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
        val req = PeriodicWorkRequestBuilder<UploadWorker>(6, TimeUnit.HOURS)
            .setInitialDelay(Random.nextLong(5, 30), TimeUnit.MINUTES)
            .setConstraints(online)
            .build()
        wm.enqueueUniquePeriodicWork(WORK, ExistingPeriodicWorkPolicy.KEEP, req)
        wm.enqueueUniqueWork(
            "${WORK}_now",
            androidx.work.ExistingWorkPolicy.KEEP,
            androidx.work.OneTimeWorkRequestBuilder<UploadWorker>().setInitialDelay(2, TimeUnit.MINUTES).setConstraints(online).build(),
        )
    }

    class UploadWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {
        override suspend fun doWork(): Result {
            val c = (applicationContext as SangeetApplication).container
            val s = c.settings.settings.first()
            if (!s.shareListening || s.offlineMode || BuildConfig.DATA_REPO.isBlank() || BuildConfig.REPORT_TOKEN.isBlank()) return Result.success()
            val prefs = applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val today = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
            if (System.currentTimeMillis() - prefs.getLong("sent_at", 0L) < 6 * 3_600_000L) return Result.success()
            return try {
                val since = prefs.getLong("sent_at", 0L)
                val body = payload(c, id(applicationContext), today, since)
                post(today, body)
                prefs.edit().putLong("sent_at", System.currentTimeMillis()).apply()
                android.util.Log.i("Sangeet", "community: sent ${body.length} chars")
                Result.success()
            } catch (e: Exception) {
                android.util.Log.w("Sangeet", "community: ${e.message}")
                if (runAttemptCount < 3) Result.retry() else Result.success()
            }
        }
    }

    /** A random id for this install (the same every day, so a listener's days count once). */
    private fun id(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return prefs.getString("id", null) ?: UUID.randomUUID().toString().also { prefs.edit().putString("id", it).apply() }
    }

    private fun shareable(t: Track) = t.source == SourceType.JIOSAAVN || t.source == SourceType.YOUTUBE

    /** [source, id, title, singers, language, cover, stream link (JioSaavn's, needed to play it), length ms]. */
    private fun compact(t: Track) = buildJsonArray {
        add(t.source.name); add(t.sourceId); add(t.title); add(t.artist); add(t.language); add(t.artworkUrl.orEmpty())
        add(if (t.source == SourceType.JIOSAAVN) t.streamUrl.orEmpty() else ""); add(t.durationMs)
    }

    /** The day's data as "SANGEET-DATA v1" + deflated, base64 JSON (an issue body holds 65,536 characters). */
    private suspend fun payload(c: com.sangeet.player.AppContainer, id: String, day: String, since: Long): String {
        val searches = runCatching {
            val saved = c.appContext.getSharedPreferences("search", Context.MODE_PRIVATE).getString("recent", null)
            if (saved == null) emptyList() else Http.json.decodeFromString(ListSerializer(RecentItem.serializer()), saved)
        }.getOrDefault(emptyList()).mapNotNull { it.query?.trim()?.takeIf { q -> q.length >= 2 } }.distinct().take(50)
        val likes = c.library.favoritesOnce().filter(::shareable)
        val played = c.library.playedHistory(500).filter { shareable(it.track) }
        val playlists = c.library.playlists.first().filter { !it.name.startsWith("✨") }.take(20)
            .map { it.name to c.library.playlistTracksOnce(it.id).filter(::shareable).take(200) }
        var keep = 1.0
        while (true) {
            val json = buildJsonObject {
                put("v", 1); put("id", id); put("p", "android"); put("app", BuildConfig.VERSION_NAME); put("day", day)
                putJsonArray("langs") { c.settings.current.languages.forEach { add(it) } }
                putJsonArray("searches") { searches.forEach { add(it) } }
                putJsonArray("likes") { likes.take((500 * keep).toInt()).forEach { add(compact(it)) } }
                // Recently played (since the last upload first), with how often.
                putJsonArray("plays") {
                    played.sortedByDescending { if (it.playedAt >= since) 1 else 0 }.take((400 * keep).toInt()).forEach { p ->
                        addJsonArray { compact(p.track).forEach { add(it) }; add(p.playCount) }
                    }
                }
                putJsonArray("playlists") {
                    playlists.take((20 * keep).toInt().coerceAtLeast(1)).forEach { (name, tracks) ->
                        add(buildJsonObject { put("n", name); putJsonArray("t") { tracks.forEach { add(compact(it)) } } })
                    }
                }
            }.toString()
            val body = "$MARK\n" + Base64.encodeToString(deflate(json), Base64.NO_WRAP)
            if (body.length <= MAX_BODY || keep < 0.1) return body
            keep *= 0.6
        }
    }

    private fun deflate(s: String): ByteArray {
        val out = ByteArrayOutputStream()
        DeflaterOutputStream(out, Deflater(Deflater.BEST_COMPRESSION, true)).use { it.write(s.toByteArray()) }
        return out.toByteArray()
    }

    private fun post(day: String, body: String) {
        val json = buildJsonObject { put("title", "sangeet-data $day"); put("body", body) }.toString()
        val req = Request.Builder()
            .url("https://api.github.com/repos/${BuildConfig.DATA_REPO}/issues")
            .header("Authorization", "Bearer ${BuildConfig.REPORT_TOKEN}")
            .header("Accept", "application/vnd.github+json")
            .post(json.toRequestBody("application/json".toMediaType()))
            .build()
        Http.client.newCall(req).execute().use { res ->
            if (!res.isSuccessful) throw java.io.IOException("GitHub said ${res.code}")
        }
    }

    // ------------------------------------------------------------ download (what everyone listens to)

    class Data(
        val tracks: List<Track>,
        private val together: Map<String, IntArray>,
        private val top: IntArray,
        /** Words listeners name playlists with ("gym", "drive") -> songs two or more of them put there. */
        private val words: Map<String, IntArray> = emptyMap(),
    ) {
        private val index = tracks.withIndex().associate { (i, t) -> t.id to i }

        /** Songs that listeners of [ids] also play / like, most shared first. */
        fun near(ids: List<String>): List<Track> {
            val score = HashMap<Int, Int>()
            ids.forEach { id ->
                val i = index[id] ?: return@forEach
                together[i.toString()]?.forEachIndexed { rank, j -> score[j] = (score[j] ?: 0) + (20 - rank).coerceAtLeast(1) }
            }
            ids.mapNotNull { index[it] }.forEach { score.remove(it) }
            return score.entries.sortedByDescending { it.value }.map { tracks[it.key] }
        }

        /** What the most listeners play right now. */
        fun popular(): List<Track> = top.map { tracks[it] }

        /** Songs listeners keep under this word in their playlists' names (what the DJ learned from them). */
        fun learned(word: String): List<Track> = words[word.lowercase()]?.map { tracks[it] }.orEmpty()
    }

    @Volatile private var data: Data? = null
    @Volatile private var loading = false
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** The shared data if it's loaded already (null otherwise). */
    fun cached(): Data? = data

    /** The shared data if it's on the phone already; starts fetching it otherwise (never waits). */
    fun current(context: Context): Data? {
        data?.let { return it }
        if (!loading) {
            loading = true
            scope.launch {
                runCatching { data = load(context.applicationContext) }
                loading = false
            }
        }
        return null
    }

    private fun load(context: Context): Data? {
        val file = File(context.filesDir, "catalog/community.json.gz").apply { parentFile?.mkdirs() }
        if (!file.exists() || System.currentTimeMillis() - file.lastModified() > 12 * 3_600_000L) {
            runCatching {
                val url = "https://github.com/${BuildConfig.UPDATE_REPO}/releases/download/catalog/community.json.gz"
                Http.client.newCall(Request.Builder().url(url).build()).execute().use { res ->
                    if (res.isSuccessful) res.body?.bytes()?.let { file.writeBytes(it) }
                }
            }
        }
        if (!file.exists()) return null
        val root = Http.json.parseToJsonElement(GZIPInputStream(file.inputStream()).bufferedReader().use { it.readText() }).jsonObject
        val tracks = root["tracks"]?.jsonArray.orEmpty().mapNotNull { el ->
            val a = el as? JsonArray ?: return@mapNotNull null
            fun str(i: Int) = (a.getOrNull(i) as? JsonPrimitive)?.contentOrNull.orEmpty()
            val type = runCatching { SourceType.valueOf(str(0)) }.getOrNull() ?: return@mapNotNull null
            // A JioSaavn song needs its stream link to play.
            if (type == SourceType.JIOSAAVN && str(6).isBlank()) return@mapNotNull null
            Track(
                id = Track.makeId(type, str(1)), source = type, sourceId = str(1), title = str(2), artist = str(3),
                durationMs = str(7).toLongOrNull() ?: 0L, artworkUrl = str(5).ifBlank { null },
                streamUrl = str(6).ifBlank { null }, language = str(4),
            )
        }
        val together = root["together"]?.jsonObject.orEmpty().mapValues { (_, v) ->
            v.jsonArray.mapNotNull { it.jsonPrimitive.intOrNull }.filter { it in tracks.indices }.toIntArray()
        }
        val top = root["top"]?.jsonArray.orEmpty().mapNotNull { it.jsonPrimitive.intOrNull }.filter { it in tracks.indices }.toIntArray()
        val words = root["words"]?.jsonObject.orEmpty().mapValues { (_, v) ->
            v.jsonArray.mapNotNull { it.jsonPrimitive.intOrNull }.filter { it in tracks.indices }.toIntArray()
        }
        return Data(tracks, together, top, words)
    }
}
