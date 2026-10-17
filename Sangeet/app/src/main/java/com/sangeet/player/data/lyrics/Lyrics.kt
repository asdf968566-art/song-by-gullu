package com.sangeet.player.data.lyrics

import com.sangeet.player.data.OnlineRepository
import com.sangeet.player.data.SameSong
import com.sangeet.player.data.db.LyricsDao
import com.sangeet.player.data.db.LyricsEntity
import com.sangeet.player.data.model.Track
import com.sangeet.player.data.remote.Http
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import okhttp3.HttpUrl.Companion.toHttpUrl

data class LyricLine(val timeMs: Long, val text: String)

data class Lyrics(
    val lines: List<LyricLine>,
    val plain: String?,
    val source: String,
) {
    val isSynced get() = lines.isNotEmpty()
}

object LrcParser {
    private val timeTag = Regex("""\[(\d{1,3}):(\d{1,2})(?:[.:](\d{1,3}))?]""")
    private val offsetTag = Regex("""\[offset:\s*([+-]?\d+)]""", RegexOption.IGNORE_CASE)

    fun looksLikeLrc(text: String) = timeTag.containsMatchIn(text)

    fun parse(text: String): List<LyricLine> {
        val offset = offsetTag.find(text)?.groupValues?.get(1)?.toLongOrNull() ?: 0L
        val out = ArrayList<LyricLine>()
        text.lineSequence().forEach { raw ->
            val stamps = timeTag.findAll(raw).toList()
            if (stamps.isEmpty()) return@forEach
            val lyric = raw.substring(stamps.last().range.last + 1).trim()
            stamps.forEach { m ->
                val min = m.groupValues[1].toLong()
                val sec = m.groupValues[2].toLong()
                val frac = m.groupValues[3]
                val ms = when (frac.length) {
                    0 -> 0L
                    1 -> frac.toLong() * 100
                    2 -> frac.toLong() * 10
                    else -> frac.take(3).toLong()
                }
                out += LyricLine((min * 60_000 + sec * 1000 + ms - offset).coerceAtLeast(0), lyric)
            }
        }
        return out.sortedBy { it.timeMs }
    }
}

@Serializable
private data class LrcLibItem(
    val trackName: String? = null,
    val artistName: String? = null,
    val duration: Double? = null,
    val instrumental: Boolean = false,
    val plainLyrics: String? = null,
    val syncedLyrics: String? = null,
)

/**
 * Lyrics dhoondhne ka order:
 * 1. Offline cache (Room)  2. Gaane ke saath rakhi .lrc file  3. Online LRCLIB (free, synced) -> cache
 * 4. JioSaavn's own lyrics  5. lyrics.ovh (report #57: more songs with lyrics)
 */
class LyricsRepository(
    private val dao: LyricsDao,
    private val online: OnlineRepository,
) {
    suspend fun get(track: Track, allowOnline: Boolean = true): Lyrics? {
        if (track.source == com.sangeet.player.data.model.SourceType.URL && track.sourceId.startsWith("radio-")) return null // live radio
        dao.get(track.id)?.let { return it.toLyrics() }
        sidecar(track)?.let { text ->
            save(track.id, text, "Local .lrc")
            return dao.get(track.id)?.toLyrics()
        }
        if (allowOnline && online.canGoOnline) {
            val found = withTimeoutOrNull(20_000) { runCatching { fetchLrcLib(track) }.getOrNull() }
            when {
                found != null && (!found.syncedLyrics.isNullOrBlank() || !found.plainLyrics.isNullOrBlank()) ->
                    dao.upsert(LyricsEntity(track.id, found.syncedLyrics, found.plainLyrics, "LRCLIB"))
                found?.instrumental == true -> dao.upsert(LyricsEntity(track.id, null, "♪ Instrumental ♪", "LRCLIB"))
                else -> {
                    // Report #57: more songs with lyrics. JioSaavn has its own (many Hindi / Punjabi songs), then
                    // lyrics.ovh. Both are plain text, without times.
                    val saavn = withTimeoutOrNull(12_000) { runCatching { saavnLyrics(track) }.getOrNull() }
                    val text = saavn ?: withTimeoutOrNull(10_000) { runCatching { lyricsOvh(track) }.getOrNull() } ?: return null
                    dao.upsert(LyricsEntity(track.id, null, text, if (saavn != null) "JioSaavn" else "lyrics.ovh"))
                }
            }
            return dao.get(track.id)?.toLyrics()
        }
        return null
    }

    /** User ne .lrc file import ki ya lyrics paste kiye. */
    suspend fun save(trackId: String, text: String, source: String = "Manual") {
        val synced = if (LrcParser.looksLikeLrc(text)) text else null
        val plain = if (synced == null) text else LrcParser.parse(text).joinToString("\n") { it.text }
        dao.upsert(LyricsEntity(trackId, synced, plain, source))
    }

    suspend fun delete(trackId: String) = dao.delete(trackId)

    private suspend fun sidecar(track: Track): String? = withContext(Dispatchers.IO) {
        val path = track.filePath ?: return@withContext null
        val lrc = File(path.substringBeforeLast('.') + ".lrc")
        runCatching { if (lrc.canRead()) lrc.readText() else null }.getOrNull()
    }

    /**
     * LRCLIB: the song's own names first; then its clean name and main singer (YouTube titles like "Kesariya -
     * Official Video | Brahmastra" by a label's channel); then searches, where the same name and a length within 8 s win.
     */
    private suspend fun fetchLrcLib(track: Track): LrcLibItem? {
        lrcGet(track.title, track.artist, track.album, track.durationMs)?.let { return it }
        val title = SameSong.cleanTitle(track.title)
        val singer = SameSong.mainSinger(track.artist)
        if (title.isBlank()) return null
        if (singer != null && (title != track.title.lowercase() || singer != track.artist.lowercase())) {
            lrcGet(title, singer, "", track.durationMs)?.let { return it }
        }
        val searches = listOf(
            listOfNotNull("track_name" to title, singer?.let { "artist_name" to it }),
            listOf("q" to listOfNotNull(title, singer).joinToString(" ")),
        )
        for (params in searches) {
            val url = "https://lrclib.net/api/search".toHttpUrl().newBuilder()
                .apply { params.forEach { (k, v) -> addQueryParameter(k, v) } }
                .build().toString()
            val body = runCatching { Http.getText(url) }.getOrNull() ?: continue
            val list = runCatching { Http.json.decodeFromString(ListSerializer(LrcLibItem.serializer()), body) }.getOrNull() ?: continue
            pick(list, track, title)?.let { return it }
        }
        return null
    }

    private suspend fun lrcGet(title: String, artist: String, album: String, durationMs: Long): LrcLibItem? {
        val url = "https://lrclib.net/api/get".toHttpUrl().newBuilder()
            .addQueryParameter("track_name", title)
            .addQueryParameter("artist_name", artist)
            .apply {
                if (album.isNotBlank()) addQueryParameter("album_name", album)
                if (durationMs > 0) addQueryParameter("duration", (durationMs / 1000).toString())
            }
            .build().toString()
        val body = runCatching { Http.getText(url) }.getOrNull() ?: return null
        return runCatching { Http.json.decodeFromString(LrcLibItem.serializer(), body) }.getOrNull()
    }

    /** The search result with the same clean name (and a length within 8 s), synced lyrics first. */
    private fun pick(list: List<LrcLibItem>, track: Track, title: String): LrcLibItem? {
        val sec = track.durationMs / 1000.0
        val ok = list.filter { item ->
            val t = SameSong.cleanTitle(item.trackName ?: "")
            t.isNotBlank() && (t == title || title.startsWith("$t ") || t.startsWith("$title ")) &&
                (sec <= 0 || item.duration == null || kotlin.math.abs(item.duration - sec) <= 8)
        }
        return ok.firstOrNull { !it.syncedLyrics.isNullOrBlank() } ?: ok.firstOrNull { !it.plainLyrics.isNullOrBlank() }
    }

    /** JioSaavn's lyrics: the song's own, or those of the same song found there (same name, a shared singer). */
    private suspend fun saavnLyrics(track: Track): String? {
        if (!online.jiosaavnOn) return null
        val id = if (track.source == com.sangeet.player.data.model.SourceType.JIOSAAVN) track.sourceId else {
            val title = SameSong.cleanTitle(track.title).ifBlank { return null }
            val singer = SameSong.mainSinger(track.artist)
            val words = track.title.lowercase()
            online.saavn.searchPage(listOfNotNull(title, singer).joinToString(" "), 1).firstOrNull { r ->
                SameSong.cleanTitle(r.title) == title && when {
                    singer != null -> r.artist.lowercase().contains(singer) ||
                        r.artist.split(',').any { a -> a.trim().length > 2 && words.contains(a.trim().lowercase()) }
                    else -> track.durationMs <= 0 || kotlin.math.abs(r.durationMs - track.durationMs) <= 20_000
                }
            }?.sourceId ?: return null
        }
        return online.saavn.lyrics(id)
    }

    /** lyrics.ovh (free, no key; mostly English songs): by singer and song name. */
    private suspend fun lyricsOvh(track: Track): String? {
        val singer = SameSong.mainSinger(track.artist) ?: return null
        val title = SameSong.cleanTitle(track.title).ifBlank { return null }
        val url = "https://api.lyrics.ovh/v1/".toHttpUrl().newBuilder().addPathSegment(singer).addPathSegment(title).build()
        val body = Http.getText(url.toString()) ?: return null
        val text = ((Http.json.parseToJsonElement(body) as? JsonObject)?.get("lyrics") as? JsonPrimitive)?.contentOrNull
        return text?.replace("\r\n", "\n")?.trim()?.takeIf { it.length > 20 }
    }

    private fun LyricsEntity.toLyrics() = Lyrics(
        lines = synced?.let(LrcParser::parse) ?: emptyList(),
        plain = plain,
        source = source,
    )
}
