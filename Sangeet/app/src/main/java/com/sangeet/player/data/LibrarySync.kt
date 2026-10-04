package com.sangeet.player.data

import android.util.Base64
import com.sangeet.player.BuildConfig
import com.sangeet.player.data.model.SourceType
import com.sangeet.player.data.model.Track
import com.sangeet.player.data.remote.Http
import java.io.ByteArrayOutputStream
import java.util.zip.Deflater
import java.util.zip.Inflater
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put

/**
 * Move liked songs and playlists between phones (Android <-> Android, Android <-> iPhone web app) without any
 * account: everything is packed into one link (#sync=...). Opening the link on the iPhone, or pasting it into
 * "Import playlist" on Android, adds the songs. Songs that only exist on this phone (local files) can't travel.
 */
object LibrarySync {
    private val site = BuildConfig.UPDATE_REPO.split('/').let { (owner, repo) -> "https://$owner.github.io/$repo/" }

    fun isSyncLink(text: String) = "sync=" in text

    suspend fun exportLink(lib: LibraryRepository): String {
        val liked = lib.favoritesOnce().mapNotNull(::encode)
        val playlists = lib.playlists.first()
            .filter { !it.name.startsWith("✨") } // auto playlists are rebuilt on every phone anyway
            .map { p ->
                buildJsonObject {
                    put("n", p.name)
                    put("t", JsonArray(lib.playlistTracksOnce(p.id).mapNotNull(::encode)))
                }
            }
        val json = buildJsonObject {
            put("v", 1)
            put("l", JsonArray(liked))
            put("p", JsonArray(playlists))
        }.toString()
        return site + "#sync=" + pack(json)
    }

    /** Returns (liked songs added, playlists added). */
    suspend fun import(lib: LibraryRepository, text: String): Pair<Int, Int> {
        val data = text.substringAfter("sync=").trim().takeWhile { !it.isWhitespace() }
        val root = Http.json.parseToJsonElement(unpack(data)).jsonObject
        val have = lib.favoriteIds.value
        var liked = 0
        (root["l"] as? JsonArray).orEmpty().mapNotNull { (it as? JsonObject)?.let(::decode) }.forEach { t ->
            if (t.id !in have) { lib.toggleFavorite(t); liked++ }
        }
        var lists = 0
        (root["p"] as? JsonArray).orEmpty().mapNotNull { it as? JsonObject }.forEach { p ->
            val name = (p["n"] as? JsonPrimitive)?.contentOrNull ?: "Imported playlist"
            val tracks = (p["t"] as? JsonArray).orEmpty().mapNotNull { (it as? JsonObject)?.let(::decode) }
            if (tracks.isNotEmpty()) { lib.createPlaylist(name, tracks); lists++ }
        }
        return liked to lists
    }

    // ------------------------------------------------------------ one song <-> compact JSON (same format as the web app)

    private fun encode(t: Track): JsonObject? = when (t.source) {
        SourceType.JIOSAAVN -> buildJsonObject {
            put("s", "js"); put("i", t.sourceId); put("t", t.title); put("a", t.artist); put("al", t.album)
            put("d", t.durationMs / 1000); t.artworkUrl?.let { put("img", it) }
            t.streamUrl?.let { put("m", it.removeSuffix("#320")); put("h", if (it.endsWith("#320")) 1 else 0) }
        }
        SourceType.YOUTUBE -> buildJsonObject {
            put("s", "yt"); put("i", t.sourceId); put("t", t.title); put("a", t.artist)
            put("d", t.durationMs / 1000); t.artworkUrl?.let { put("img", it) }
        }
        else -> null
    }

    private fun decode(o: JsonObject): Track? {
        fun s(k: String) = (o[k] as? JsonPrimitive)?.contentOrNull
        val id = s("i") ?: return null
        val title = s("t") ?: return null
        val seconds = s("d")?.toLongOrNull() ?: 0
        return when (s("s")) {
            "js" -> Track(
                id = Track.makeId(SourceType.JIOSAAVN, id), source = SourceType.JIOSAAVN, sourceId = id,
                title = title, artist = s("a").orEmpty(), album = s("al").orEmpty(), durationMs = seconds * 1000,
                artworkUrl = s("img")?.replace("150x150", "500x500"),
                streamUrl = s("m")?.let { it + if (s("h") == "1") "#320" else "" },
            )
            "yt" -> Track(
                id = Track.makeId(SourceType.YOUTUBE, id), source = SourceType.YOUTUBE, sourceId = id,
                title = title, artist = s("a").orEmpty(), album = "YouTube", durationMs = seconds * 1000, artworkUrl = s("img"),
            )
            else -> null
        }
    }

    // ------------------------------------------------------------ deflate + base64url (the web app uses deflate-raw too)

    private fun pack(text: String): String {
        val d = Deflater(9, true)
        d.setInput(text.toByteArray(Charsets.UTF_8))
        d.finish()
        val out = ByteArrayOutputStream()
        val buf = ByteArray(8192)
        while (!d.finished()) out.write(buf, 0, d.deflate(buf))
        d.end()
        return Base64.encodeToString(out.toByteArray(), Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
    }

    private fun unpack(data: String): String {
        val bytes = Base64.decode(data, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
        val inf = Inflater(true)
        inf.setInput(bytes)
        val out = ByteArrayOutputStream()
        val buf = ByteArray(8192)
        while (!inf.finished()) {
            val n = inf.inflate(buf)
            if (n == 0 && (inf.needsInput() || inf.needsDictionary())) break
            out.write(buf, 0, n)
        }
        inf.end()
        return out.toString(Charsets.UTF_8.name())
    }
}
