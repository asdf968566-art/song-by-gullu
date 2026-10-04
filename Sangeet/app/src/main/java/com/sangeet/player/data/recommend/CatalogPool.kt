package com.sangeet.player.data.recommend

import android.content.Context
import android.util.JsonReader
import com.sangeet.player.BuildConfig
import com.sangeet.player.data.model.SourceType
import com.sangeet.player.data.model.Track
import com.sangeet.player.data.remote.Http
import java.io.File
import java.io.InputStreamReader
import java.util.zip.GZIPInputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.Request

/**
 * Popular songs per language from the nightly catalog (GitHub release "catalog", built from JioSaavn).
 * Gives the feed and autoplay tens of thousands of fresh songs to pick from, even when search is slow.
 */
class CatalogPool(context: Context) {
    private val dir = File(context.filesDir, "catalog").apply { mkdirs() }
    private val cache = HashMap<String, List<Track>>()
    private val lock = Mutex()

    suspend fun tracks(lang: String): List<Track> = lock.withLock {
        cache[lang] ?: load(lang).also { if (it.isNotEmpty()) cache[lang] = it }
    }

    /** Fresh songs from the given languages: favourite singers first, plenty of random ones. */
    suspend fun sample(languages: List<String>, count: Int, skip: Set<String>, favourite: (String) -> Boolean): List<Track> {
        val pool = languages.flatMap { runCatching { tracks(it) }.getOrDefault(emptyList()) }.filter { it.id !in skip }
        if (pool.isEmpty()) return emptyList()
        val fav = pool.filter { favourite(it.artist) }.shuffled().take(count / 2)
        return (fav + pool.shuffled().take(count - fav.size)).distinctBy { it.id }
    }

    private suspend fun load(lang: String): List<Track> = withContext(Dispatchers.IO) {
        val file = File(dir, "$lang.json.gz")
        val stale = !file.exists() || System.currentTimeMillis() - file.lastModified() > 24 * 3_600_000L
        if (stale) runCatching { download(lang, file) }
        if (!file.exists()) return@withContext emptyList()
        runCatching { parse(file, lang) }.getOrDefault(emptyList())
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

    /** {"songs": [[id, title, artist, album, seconds, image, media, has320, year], ...], ...} */
    private fun parse(file: File, lang: String): List<Track> {
        val out = ArrayList<Track>()
        JsonReader(InputStreamReader(GZIPInputStream(file.inputStream().buffered()), Charsets.UTF_8)).use { r ->
            r.beginObject()
            while (r.hasNext()) {
                if (r.nextName() != "songs") { r.skipValue(); continue }
                r.beginArray()
                while (r.hasNext()) {
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
                    out += Track(
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
                r.endArray()
            }
            r.endObject()
        }
        return out
    }

    private companion object {
        const val IMG = "https://c.saavncdn.com/"
        const val AAC = "https://aac.saavncdn.com/"
    }
}
