package com.sangeet.player.data.playlist

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.sangeet.player.data.LocalMusicRepository
import com.sangeet.player.data.OnlineRepository
import com.sangeet.player.data.model.SourceType
import com.sangeet.player.data.model.Track
import com.sangeet.player.data.remote.Http
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class ImportEntry(
    val title: String,
    val artist: String = "",
    val album: String = "",
    val durationMs: Long = 0,
    val location: String? = null,
)

/** A playlist link: either ready songs (YouTube, JioSaavn) or names to match (Spotify). */
data class LinkImport(val name: String, val tracks: List<Track>, val entries: List<ImportEntry>)

data class ImportResult(
    val name: String,
    val matched: List<Track>,
    val missing: List<ImportEntry>,
)

/**
 * Playlist import: M3U / M3U8 / PLS / CSV (Spotify ki Exportify, TuneMyMusic, Soundiiz export) / TXT.
 * Har entry pehle phone ke gaanon se milayi jati hai, na mile to online search.
 */
class PlaylistImporter(
    private val context: Context,
    private val local: LocalMusicRepository,
    private val online: OnlineRepository,
) {
    suspend fun read(uri: Uri): Pair<String, List<ImportEntry>> = withContext(Dispatchers.IO) {
        val fileName = displayName(uri) ?: "Imported playlist"
        val text = context.contentResolver.openInputStream(uri)?.use { it.readBytes().decodeToString() }
            ?: throw IllegalArgumentException("Couldn't open the file")
        val name = fileName.substringBeforeLast('.').ifBlank { "Imported playlist" }
        val ext = fileName.substringAfterLast('.', "").lowercase()
        val entries = when {
            ext == "pls" || text.trimStart().startsWith("[playlist]", ignoreCase = true) -> parsePls(text)
            ext == "m3u" || ext == "m3u8" || text.trimStart().startsWith("#EXTM3U") -> parseM3u(text)
            ext == "csv" -> parseCsv(text)
            else -> parseTxt(text)
        }
        name to entries
    }

    /** Spotify, YouTube / YouTube Music or JioSaavn playlist (or album) link. */
    suspend fun readLink(link: String): LinkImport {
        val l = link.trim()
        return when {
            "spotify" in l -> spotify(l)
            "list=" in l && ("youtube" in l || "youtu.be" in l) ->
                online.youtube.playlist(l, online.youtubeKey)?.let { (n, t) -> LinkImport(n, t, emptyList()) }
                    ?: throw IllegalArgumentException("Couldn't open that YouTube playlist. Is it public?")
            "saavn" in l ->
                online.saavn.fromLink(l)?.let { (n, t) -> LinkImport(n, t, emptyList()) }
                    ?: throw IllegalArgumentException("Couldn't open that JioSaavn link.")
            else -> throw IllegalArgumentException("Paste a Spotify, YouTube Music, YouTube or JioSaavn playlist link.")
        }
    }

    /** Spotify's public embed page lists the playlist's songs (no account or key needed). */
    private suspend fun spotify(link: String): LinkImport = withContext(Dispatchers.IO) {
        val m = Regex("(playlist|album|track)[/:]([A-Za-z0-9]{10,})").find(link)
            ?: throw IllegalArgumentException("That doesn't look like a Spotify playlist link.")
        val (kind, id) = m.destructured
        val html = Http.getText("https://open.spotify.com/embed/$kind/$id")
            ?: throw IllegalArgumentException("Spotify playlist not found. Is it public?")
        val raw = Regex("<script id=\"__NEXT_DATA__\" type=\"application/json\">(.*?)</script>", RegexOption.DOT_MATCHES_ALL)
            .find(html)?.groupValues?.get(1) ?: throw IllegalArgumentException("Couldn't read the Spotify playlist.")
        val entity = Http.json.parseToJsonElement(raw).jsonObject["props"]?.jsonObject?.get("pageProps")?.jsonObject
            ?.get("state")?.jsonObject?.get("data")?.jsonObject?.get("entity")?.jsonObject
            ?: throw IllegalArgumentException("Couldn't read the Spotify playlist.")
        fun JsonObject.s(k: String) = (this[k] as? JsonPrimitive)?.contentOrNull.orEmpty()
        val name = entity.s("name").ifBlank { entity.s("title") }.ifBlank { "Spotify playlist" }
        val list = (entity["trackList"] as? JsonArray).orEmpty().mapNotNull { it as? JsonObject }
        val entries = if (list.isNotEmpty()) list.map {
            ImportEntry(title = it.s("title"), artist = it.s("subtitle").replace('\u00a0', ' ').split(',').first().trim(),
                durationMs = it.s("duration").toLongOrNull() ?: 0)
        } else listOf(ImportEntry(title = name, artist = entity.s("subtitle")))
        LinkImport(name, emptyList(), entries.filter { it.title.isNotBlank() })
    }

    suspend fun match(
        name: String,
        entries: List<ImportEntry>,
        searchOnline: Boolean,
        onProgress: (done: Int, total: Int) -> Unit,
    ): ImportResult {
        val songs = local.songs.value.ifEmpty { local.scan() }
        val byKey = songs.associateBy { key(it.title, it.artist) }
        val byTitle = songs.groupBy { norm(it.title) }
        val matched = ArrayList<Track>()
        val missing = ArrayList<ImportEntry>()
        entries.forEachIndexed { i, e ->
            val loc = e.location
            val track: Track? = when {
                loc != null && (loc.startsWith("http://") || loc.startsWith("https://")) -> urlTrack(e, loc)
                loc != null && loc.isNotBlank() -> {
                    val file = loc.replace('\\', '/').substringAfterLast('/')
                    songs.firstOrNull { it.filePath?.endsWith(loc.replace('\\', '/')) == true }
                        ?: songs.firstOrNull { it.filePath?.substringAfterLast('/')?.equals(file, true) == true }
                        ?: findByName(e, byKey, byTitle)
                }
                else -> findByName(e, byKey, byTitle)
            } ?: if (searchOnline && online.canGoOnline && e.title.isNotBlank()) searchOnline(e) else null

            if (track != null) matched += track else missing += e
            onProgress(i + 1, entries.size)
        }
        return ImportResult(name, matched.distinctBy { it.id }, missing)
    }

    private fun findByName(e: ImportEntry, byKey: Map<String, Track>, byTitle: Map<String, List<Track>>): Track? {
        if (e.title.isBlank()) return null
        byKey[key(e.title, e.artist)]?.let { return it }
        val candidates = byTitle[norm(e.title)] ?: return null
        if (e.artist.isBlank()) return candidates.first()
        val a = norm(e.artist)
        return candidates.firstOrNull { norm(it.artist).contains(a) || a.contains(norm(it.artist)) }
    }

    private suspend fun searchOnline(e: ImportEntry): Track? {
        // JioSaavn first (plays reliably, 320 kbps), then every source incl. YouTube.
        val saavn = runCatching { online.saavn.searchPage("${e.title} ${e.artist}".trim(), 1) }.getOrDefault(emptyList())
        val results = runCatching { online.search("${e.artist} ${e.title}".trim()) }.getOrNull().orEmpty()
        val all = saavn + results.flatMap { it.tracks }
        val t = norm(e.title)
        val a = norm(e.artist)
        return all.firstOrNull { norm(it.title) == t && (a.isEmpty() || norm(it.artist).contains(a)) }
            ?: all.firstOrNull { norm(it.title).contains(t) || t.contains(norm(it.title)) }
    }

    private fun urlTrack(e: ImportEntry, url: String): Track {
        val id = sha1(url).take(20)
        val fallback = url.substringAfterLast('/').substringBefore('?').ifBlank { "Web stream" }
        return Track(
            id = Track.makeId(SourceType.URL, id),
            source = SourceType.URL,
            sourceId = id,
            title = e.title.ifBlank { fallback },
            artist = e.artist.ifBlank { "Web" },
            album = e.album,
            durationMs = e.durationMs,
            streamUrl = url,
        )
    }

    // ------------------------------------------------------------ parsers

    fun parseM3u(text: String): List<ImportEntry> {
        val out = ArrayList<ImportEntry>()
        var pendingTitle = ""
        var pendingArtist = ""
        var pendingDuration = 0L
        text.lineSequence().map { it.trim() }.forEach { line ->
            when {
                line.isEmpty() -> Unit
                line.startsWith("#EXTINF", ignoreCase = true) -> {
                    val info = line.substringAfter(':')
                    pendingDuration = (info.substringBefore(',').trim().substringBefore(' ').toLongOrNull() ?: 0L) * 1000
                    val label = info.substringAfter(',', "").trim()
                    val (artist, title) = splitArtistTitle(label)
                    pendingArtist = artist
                    pendingTitle = title
                }
                line.startsWith("#") -> Unit
                else -> {
                    val fromFile = line.replace('\\', '/').substringAfterLast('/').substringBeforeLast('.')
                    val title = pendingTitle.ifBlank { splitArtistTitle(fromFile).second }
                    val artist = pendingArtist.ifBlank { splitArtistTitle(fromFile).first }
                    out += ImportEntry(title = title, artist = artist, durationMs = pendingDuration.coerceAtLeast(0), location = line)
                    pendingTitle = ""; pendingArtist = ""; pendingDuration = 0
                }
            }
        }
        return out
    }

    fun parsePls(text: String): List<ImportEntry> {
        val files = HashMap<Int, String>()
        val titles = HashMap<Int, String>()
        val lengths = HashMap<Int, Long>()
        val re = Regex("""^(File|Title|Length)(\d+)=(.*)$""", RegexOption.IGNORE_CASE)
        text.lineSequence().forEach { raw ->
            val m = re.find(raw.trim()) ?: return@forEach
            val idx = m.groupValues[2].toInt()
            val value = m.groupValues[3].trim()
            when (m.groupValues[1].lowercase()) {
                "file" -> files[idx] = value
                "title" -> titles[idx] = value
                "length" -> lengths[idx] = (value.toLongOrNull() ?: 0L) * 1000
            }
        }
        return files.keys.sorted().map { i ->
            val (artist, title) = splitArtistTitle(titles[i] ?: files.getValue(i).substringAfterLast('/'))
            ImportEntry(title, artist, "", lengths[i]?.coerceAtLeast(0) ?: 0, files.getValue(i))
        }
    }

    fun parseCsv(text: String): List<ImportEntry> {
        val rows = text.lineSequence().filter { it.isNotBlank() }.map(::csvRow).toList()
        if (rows.isEmpty()) return emptyList()
        val header = rows.first().map { it.trim().lowercase() }
        fun col(vararg names: String) = names.firstNotNullOfOrNull { n -> header.indexOf(n).takeIf { it >= 0 } }
        val titleCol = col("track name", "title", "song", "song name", "name", "track")
        val artistCol = col("artist name(s)", "artist name", "artist", "artists", "artist(s)")
        val albumCol = col("album name", "album")
        val durCol = col("duration (ms)", "track duration (ms)", "duration_ms")
        val urlCol = col("url", "link", "location", "file")
        val hasHeader = titleCol != null
        val body = if (hasHeader) rows.drop(1) else rows
        return body.mapNotNull { r ->
            val title = r.getOrNull(titleCol ?: 0)?.trim().orEmpty()
            if (title.isBlank()) return@mapNotNull null
            ImportEntry(
                title = title,
                artist = r.getOrNull(artistCol ?: 1)?.trim().orEmpty().split(';', ',').first().trim(),
                album = albumCol?.let { r.getOrNull(it)?.trim() }.orEmpty(),
                durationMs = durCol?.let { r.getOrNull(it)?.trim()?.toLongOrNull() } ?: 0L,
                location = urlCol?.let { r.getOrNull(it)?.trim() }?.takeIf { it.isNotBlank() },
            )
        }
    }

    fun parseTxt(text: String): List<ImportEntry> = text.lineSequence()
        .map { it.trim() }
        .filter { it.isNotEmpty() && !it.startsWith("#") }
        .map { line ->
            if (line.startsWith("http")) {
                ImportEntry(title = "", location = line)
            } else {
                val (artist, title) = splitArtistTitle(line)
                ImportEntry(title = title, artist = artist)
            }
        }.toList()

    private fun csvRow(line: String): List<String> {
        val out = ArrayList<String>()
        val sb = StringBuilder()
        var quoted = false
        var i = 0
        while (i < line.length) {
            val c = line[i]
            when {
                c == '"' && quoted && i + 1 < line.length && line[i + 1] == '"' -> { sb.append('"'); i++ }
                c == '"' -> quoted = !quoted
                c == ',' && !quoted -> { out += sb.toString(); sb.clear() }
                else -> sb.append(c)
            }
            i++
        }
        out += sb.toString()
        return out
    }

    /** "Artist - Title" ko todta hai. */
    private fun splitArtistTitle(label: String): Pair<String, String> {
        val idx = label.indexOf(" - ")
        return if (idx > 0) label.substring(0, idx).trim() to label.substring(idx + 3).trim() else "" to label.trim()
    }

    private fun displayName(uri: Uri): String? = runCatching {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        }
    }.getOrNull() ?: uri.lastPathSegment

    private fun norm(s: String) = s.lowercase()
        .replace(Regex("""\(.*?\)|\[.*?]"""), "")
        .replace(Regex("[^\\p{L}\\p{N}]+"), " ")
        .trim()

    private fun key(title: String, artist: String) = norm(title) + "|" + norm(artist)

    private fun sha1(s: String): String =
        MessageDigest.getInstance("SHA-1").digest(s.toByteArray()).joinToString("") { "%02x".format(it) }

    companion object {
        /** Playlist ko M3U8 text mein badalta hai (export ke liye). */
        fun toM3u(name: String, tracks: List<Track>): String = buildString {
            appendLine("#EXTM3U")
            appendLine("#PLAYLIST:$name")
            tracks.forEach { t ->
                appendLine("#EXTINF:${t.durationMs / 1000},${t.artist} - ${t.title}")
                appendLine(t.filePath ?: t.streamUrl ?: "${t.artist} - ${t.title}")
            }
        }
    }
}
