package com.sangeet.player.data.lyrics

import com.sangeet.player.data.OnlineRepository
import com.sangeet.player.data.db.LyricsDao
import com.sangeet.player.data.db.LyricsEntity
import com.sangeet.player.data.model.Track
import com.sangeet.player.data.remote.Http
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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
 * 1. Offline cache (Room)  2. Gaane ke saath rakhi .lrc file  3. Online LRCLIB (free) -> cache
 */
class LyricsRepository(
    private val dao: LyricsDao,
    private val online: OnlineRepository,
) {
    suspend fun get(track: Track, allowOnline: Boolean = true): Lyrics? {
        dao.get(track.id)?.let { return it.toLyrics() }
        sidecar(track)?.let { text ->
            save(track.id, text, "Local .lrc")
            return dao.get(track.id)?.toLyrics()
        }
        if (allowOnline && online.canGoOnline) {
            val found = runCatching { fetchLrcLib(track) }.getOrNull() ?: return null
            if (found.syncedLyrics.isNullOrBlank() && found.plainLyrics.isNullOrBlank()) {
                if (!found.instrumental) return null
                dao.upsert(LyricsEntity(track.id, null, "♪ Instrumental ♪", "LRCLIB"))
            } else {
                dao.upsert(LyricsEntity(track.id, found.syncedLyrics, found.plainLyrics, "LRCLIB"))
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

    private suspend fun fetchLrcLib(track: Track): LrcLibItem? {
        val getUrl = "https://lrclib.net/api/get".toHttpUrl().newBuilder()
            .addQueryParameter("track_name", track.title)
            .addQueryParameter("artist_name", track.artist)
            .apply {
                if (track.album.isNotBlank()) addQueryParameter("album_name", track.album)
                if (track.durationMs > 0) addQueryParameter("duration", (track.durationMs / 1000).toString())
            }
            .build().toString()
        Http.getText(getUrl)?.let { return Http.json.decodeFromString(LrcLibItem.serializer(), it) }

        val searchUrl = "https://lrclib.net/api/search".toHttpUrl().newBuilder()
            .addQueryParameter("track_name", track.title)
            .addQueryParameter("artist_name", track.artist)
            .build().toString()
        val body = Http.getText(searchUrl) ?: return null
        val list = Http.json.decodeFromString(ListSerializer(LrcLibItem.serializer()), body)
        return list.firstOrNull { !it.syncedLyrics.isNullOrBlank() } ?: list.firstOrNull()
    }

    private fun LyricsEntity.toLyrics() = Lyrics(
        lines = synced?.let(LrcParser::parse) ?: emptyList(),
        plain = plain,
        source = source,
    )
}
