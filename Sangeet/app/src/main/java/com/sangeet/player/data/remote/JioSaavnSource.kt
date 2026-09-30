package com.sangeet.player.data.remote

import android.util.Base64
import com.sangeet.player.data.model.AudioQuality
import com.sangeet.player.data.model.OnlinePlaylist
import com.sangeet.player.data.model.SourceType
import com.sangeet.player.data.model.Track
import com.sangeet.player.data.settings.AppSettings
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import okhttp3.HttpUrl.Companion.toHttpUrl

/**
 * JioSaavn: Hindi, Punjabi, Bollywood aur baaki Indian gaane, 320 kbps tak.
 * (jiosaavnpy jaisa hi — JioSaavn ki web API, koi key nahi.) Sirf personal use ke liye.
 */
class JioSaavnSource : OnlineSource {
    override val type = SourceType.JIOSAAVN
    override val exactQuality = true

    override fun isEnabled(s: AppSettings) = s.jiosaavnEnabled

    override suspend fun trending(s: AppSettings, genre: String?): List<Track> {
        val langs = genre?.let { listOf(it.lowercase()) } ?: s.languages.take(3)
        val lists = langs.map { runCatching { byLanguage(it, s) }.getOrDefault(emptyList()) }
        return interleave(lists)
    }

    override suspend fun byLanguage(language: String, s: AppSettings): List<Track> {
        val lang = language.lowercase()
        val trending = runCatching { songsFrom(call("content.getTrending", "entity_type" to "song", "entity_language" to lang)) }
            .getOrDefault(emptyList())
        if (trending.size >= 10) return trending.map { if (it.language.isBlank()) it.copy(language = lang) else it }
        // Trending khaali mila to search se naye gaane
        return (trending + search("$lang new songs", s)).distinctBy { it.id }
    }

    override suspend fun search(query: String, s: AppSettings): List<Track> = searchPage(query, 1)

    /** Search ke aage ke pages (variety ke liye). */
    suspend fun searchPage(query: String, page: Int): List<Track> =
        songsFrom(call("search.getResults", "q" to query, "n" to "50", "p" to page.toString()))

    /** JioSaavn ki apni recommendations: is gaane ko sunne wale ye bhi sunte hain (users ke data se). */
    suspend fun similar(songId: String): List<Track> =
        songsFrom(call("reco.getreco", "pid" to songId))

    // ------------------------------------------------------------ online library (charts + playlists)

    /** Top charts: "Trending Today", "Bollywood Top 50", "Punjabi Top 50"... */
    suspend fun charts(s: AppSettings): List<OnlinePlaylist> =
        playlistsFrom(call("content.getCharts", langs = s.languages))

    /** Hazaron featured playlists, page-by-page (har page ~50). */
    suspend fun featuredPlaylists(s: AppSettings, page: Int): List<OnlinePlaylist> =
        playlistsFrom(call("content.getFeaturedPlaylists", "fetch_from_serialized_files" to "true", "p" to page.toString(), "n" to "50", langs = s.languages))

    suspend fun playlistTracks(id: String): List<Track> =
        songsFrom(call("playlist.getDetails", "listid" to id, "n" to "300", "p" to "1"))

    private fun playlistsFrom(root: JsonElement?): List<OnlinePlaylist> {
        val items: List<JsonElement> = when (root) {
            is JsonArray -> root
            is JsonObject -> (root["data"] as? JsonArray) ?: (root["results"] as? JsonArray) ?: emptyList()
            else -> emptyList()
        }
        return items.mapNotNull { el ->
            val o = el as? JsonObject ?: return@mapNotNull null
            if (o.str("type").let { it != null && it != "playlist" }) return@mapNotNull null
            val id = o.str("id") ?: o.str("listid") ?: return@mapNotNull null
            val info = o["more_info"] as? JsonObject
            OnlinePlaylist(
                id = id,
                title = unescape(o.str("title") ?: o.str("listname") ?: return@mapNotNull null),
                subtitle = unescape(o.str("subtitle") ?: ""),
                artworkUrl = o.str("image")?.replace("150x150", "500x500"),
                songCount = (info?.str("song_count") ?: o.str("count"))?.toIntOrNull() ?: 0,
            )
        }.distinctBy { it.id }
    }

    /** 96 kbps wale link se chahiye wali quality ka link banao. */
    override fun streamUrl(track: Track, quality: AudioQuality, s: AppSettings): String {
        val raw = track.streamUrl ?: throw IllegalStateException("JioSaavn link missing")
        val has320 = raw.endsWith(HAS_320)
        val base = raw.removeSuffix(HAS_320)
        val kbps = when (quality) {
            AudioQuality.LOW -> "96"
            AudioQuality.MEDIUM -> "160"
            AudioQuality.HIGH -> if (has320) "320" else "160"
        }
        return base.replace(Regex("_(96|160|320)\\.(mp4|m4a|mp3)"), "_$kbps.$2")
    }

    // ------------------------------------------------------------ http + parsing

    private suspend fun call(method: String, vararg params: Pair<String, String>, langs: List<String> = emptyList()): JsonElement? {
        val url = "https://www.jiosaavn.com/api.php".toHttpUrl().newBuilder()
            .addQueryParameter("__call", method)
            .addQueryParameter("_format", "json")
            .addQueryParameter("_marker", "0")
            .addQueryParameter("api_version", "4")
            .addQueryParameter("ctx", "web6dot0")
            .apply { params.forEach { (k, v) -> addQueryParameter(k, v) } }
            .build()
        // JioSaavn bhasha cookie se samajhta hai (L=hindi,punjabi)
        val headers = if (langs.isEmpty()) emptyMap() else mapOf("Cookie" to "L=${langs.joinToString("%2C")}")
        val body = Http.getText(url.toString(), headers) ?: return null
        return Http.json.parseToJsonElement(body)
    }

    private fun songsFrom(root: JsonElement?): List<Track> {
        val items: List<JsonElement> = when (root) {
            is JsonArray -> root
            is JsonObject -> (root["results"] as? JsonArray) ?: (root["list"] as? JsonArray)
                ?: (root["songs"] as? JsonArray) ?: (root["data"] as? JsonArray) ?: emptyList()
            else -> emptyList()
        }
        return items.mapNotNull { (it as? JsonObject)?.let(::toTrack) }.distinctBy { it.id }
    }

    private fun toTrack(o: JsonObject): Track? {
        if (o.str("type").let { it != null && it != "song" }) return null
        val id = o.str("id") ?: return null
        val info = o["more_info"] as? JsonObject ?: JsonObject(emptyMap())
        val encrypted = info.str("encrypted_media_url") ?: o.str("encrypted_media_url") ?: return null
        val media = decrypt(encrypted) ?: return null
        val has320 = (info.str("320kbps") ?: o.str("320kbps")) == "true"
        val artists = ((info["artistMap"] as? JsonObject)?.get("primary_artists") as? JsonArray)
            ?.mapNotNull { (it as? JsonObject)?.str("name") }
            ?.joinToString(", ")
            ?.takeIf { it.isNotBlank() }
            ?: info.str("music") ?: o.str("subtitle")?.substringBefore(" - ") ?: "Unknown"
        return Track(
            id = Track.makeId(SourceType.JIOSAAVN, id),
            source = SourceType.JIOSAAVN,
            sourceId = id,
            title = unescape(o.str("title") ?: o.str("song") ?: return null),
            artist = unescape(artists),
            album = unescape(info.str("album") ?: o.str("album") ?: ""),
            durationMs = ((info.str("duration") ?: o.str("duration"))?.toLongOrNull() ?: 0L) * 1000,
            artworkUrl = o.str("image")?.replace("150x150", "500x500")?.replace("50x50", "500x500"),
            streamUrl = media + if (has320) HAS_320 else "",
            language = (o.str("language") ?: info.str("language") ?: "").lowercase(),
        )
    }

    private fun JsonObject.str(key: String): String? =
        (this[key] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }

    private fun interleave(lists: List<List<Track>>): List<Track> {
        val out = ArrayList<Track>()
        val max = lists.maxOfOrNull { it.size } ?: 0
        for (i in 0 until max) lists.forEach { l -> l.getOrNull(i)?.let(out::add) }
        return out.distinctBy { it.id }
    }

    companion object {
        /** streamUrl ke aakhir mein ye marker = 320 kbps bhi milta hai. */
        private const val HAS_320 = "#320"

        /** JioSaavn media url DES-ECB (key "38346591") se encrypted hota hai. */
        fun decrypt(encrypted: String): String? = runCatching {
            val cipher = Cipher.getInstance("DES/ECB/PKCS5Padding")
            cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec("38346591".toByteArray(), "DES"))
            val bytes = cipher.doFinal(Base64.decode(encrypted.trim(), Base64.DEFAULT))
            String(bytes).trim().replace("http://", "https://")
        }.getOrNull()

        fun unescape(s: String): String = s
            .replace("&quot;", "\"").replace("&#039;", "'").replace("&apos;", "'")
            .replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">")
    }
}
