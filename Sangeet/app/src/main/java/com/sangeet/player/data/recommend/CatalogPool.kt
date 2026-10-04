package com.sangeet.player.data.recommend

import android.content.Context
import android.util.JsonReader
import com.sangeet.player.BuildConfig
import com.sangeet.player.data.model.SourceType
import com.sangeet.player.data.model.Track
import com.sangeet.player.data.remote.Http
import java.io.File
import java.io.InputStreamReader
import java.util.concurrent.ConcurrentHashMap
import java.util.zip.GZIPInputStream
import kotlin.random.Random
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.Request

/**
 * Popular songs per language from the nightly catalog (GitHub release "catalog", built from JioSaavn).
 *
 * Kept light on purpose: it never makes the feed wait (files download in the background and are used
 * from the next refresh on), and only [KEEP] songs per language stay in memory, so low-RAM phones are fine.
 */
class CatalogPool(context: Context, private val scope: CoroutineScope) {
    private val dir = File(context.filesDir, "catalog").apply { mkdirs() }
    private val cache = ConcurrentHashMap<String, List<Track>>()
    private val loading = ConcurrentHashMap.newKeySet<String>()

    /** Fresh songs already on the phone. Returns at once; missing languages start loading for next time. */
    fun sample(languages: List<String>, count: Int, skip: Set<String>, favourite: (String) -> Boolean): List<Track> {
        languages.forEach(::ensureLoaded)
        val pool = languages.flatMap { cache[it].orEmpty() }
        if (pool.isEmpty()) return emptyList()
        val out = LinkedHashMap<String, Track>()
        pool.filter { it.id !in skip && favourite(it.artist) }.shuffled().take(count / 2).forEach { out[it.id] = it }
        var tries = 0
        while (out.size < count && tries++ < count * 4) {
            val t = pool[Random.nextInt(pool.size)]
            if (t.id !in skip) out.putIfAbsent(t.id, t)
        }
        return out.values.toList()
    }

    private fun ensureLoaded(lang: String) {
        if (cache.containsKey(lang) || !loading.add(lang)) return
        scope.launch(Dispatchers.IO) {
            try {
                val file = File(dir, "$lang.json.gz")
                val stale = !file.exists() || System.currentTimeMillis() - file.lastModified() > 24 * 3_600_000L
                if (!file.exists()) download(lang, file)
                if (file.exists()) cache[lang] = parse(file, lang)
                if (stale && file.exists()) download(lang, file) // newer list, used after the next app start
            } catch (_: Throwable) {
                // No network or a bad file: the feed simply works without the catalog.
            } finally {
                loading.remove(lang)
            }
        }
    }

    private fun download(lang: String, file: File) {
        val url = "https://github.com/${BuildConfig.UPDATE_REPO}/releases/download/catalog/$lang.json.gz"
        Http.client.newCall(Request.Builder().url(url).build()).execute().use { res ->
            if (!res.isSuccessful) return
            val tmp = File(dir, "$lang.tmp")
            res.body?.byteStream()?.use { input -> tmp.outputStream().use { input.copyTo(it) } } ?: return
            tmp.renameTo(file)
        }
    }

    /**
     * {"songs": [[id, title, artist, album, seconds, image, media, has320, year], ...], ...}
     * Keeps the first [TOP] (most popular) songs plus a random [KEEP] - [TOP] of the rest.
     */
    private fun parse(file: File, lang: String): List<Track> {
        val out = ArrayList<Track>(KEEP)
        var seen = 0
        JsonReader(InputStreamReader(GZIPInputStream(file.inputStream().buffered()), Charsets.UTF_8)).use { r ->
            r.beginObject()
            while (r.hasNext()) {
                if (r.nextName() != "songs") { r.skipValue(); continue }
                r.beginArray()
                while (r.hasNext()) {
                    val n = seen++
                    // Reservoir sampling: decide before building the Track, so skipped rows cost nothing.
                    val slot = when {
                        n < KEEP -> n
                        else -> Random.nextInt(n + 1).takeIf { it in TOP until KEEP } ?: -1
                    }
                    if (n >= KEEP && slot < 0) { r.skipValue(); continue }
                    val t = readRow(r, lang)
                    if (n < KEEP) out += t else out[slot] = t
                }
                r.endArray()
            }
            r.endObject()
        }
        return out
    }

    private fun readRow(r: JsonReader, lang: String): Track {
        r.beginArray()
        val id = r.nextString()
        val title = r.nextString()
        val artist = r.nextString()
        val album = r.nextString()
        val seconds = r.nextLong()
        val image = r.nextString()
        val media = r.nextString()
        val has320 = r.nextInt() == 1
        while (r.hasNext()) r.skipValue()
        r.endArray()
        return Track(
            id = Track.makeId(SourceType.JIOSAAVN, id),
            source = SourceType.JIOSAAVN,
            sourceId = id,
            title = title,
            artist = artist,
            album = album,
            durationMs = seconds * 1000,
            artworkUrl = image.takeIf { it.isNotBlank() }
                ?.let { if (it.startsWith("http")) it else IMG + it }
                ?.replace("150x150", "500x500"),
            // Same format JioSaavnSource uses: 96 kbps link, "#320" when 320 kbps exists.
            streamUrl = (if (media.startsWith("http")) media else "${AAC}${media}_96.mp4") + if (has320) "#320" else "",
            language = lang,
        )
    }

    private companion object {
        const val IMG = "https://c.saavncdn.com/"
        const val AAC = "https://aac.saavncdn.com/"
        /** Songs kept in memory per language, and how many of them are the most popular ones. */
        const val KEEP = 4000
        const val TOP = 2000
    }
}
