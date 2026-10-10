package com.sangeet.player.data.remote

import com.sangeet.player.data.model.SourceType
import com.sangeet.player.data.model.Track
import java.net.URLEncoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull

/**
 * Live FM radio from India (owner, Oct 10: Mirchi, Red FM, Vividh Bharati…): the free, keyless Radio Browser
 * directory (radio-browser.info, community-run; CI-probed Oct 10: ~300 working Indian stations, most over https,
 * Vividh Bharati / AIR as HLS). A station plays as a "Web link" track whose sourceId starts with "radio-".
 */
object LiveRadio {
    private val SERVERS = listOf("all.api.radio-browser.info", "de1.api.radio-browser.info", "de2.api.radio-browser.info")

    data class Station(val track: Track, val languages: List<String>, val tags: String, val clicks: Int)

    @Volatile private var cache: Pair<Long, List<Station>>? = null

    /** India's stations, most listened first (kept for an hour). */
    suspend fun stations(): List<Station> = withContext(Dispatchers.IO) {
        cache?.takeIf { System.currentTimeMillis() - it.first < 3_600_000L }?.let { return@withContext it.second }
        val query = listOf("countrycode" to "IN", "hidebroken" to "true", "order" to "clickcount", "reverse" to "true", "limit" to "300")
            .joinToString("&") { (k, v) -> "$k=${URLEncoder.encode(v, "UTF-8")}" }
        for (host in SERVERS) {
            val body = runCatching { Http.getText("https://$host/json/stations/search?$query") }.getOrNull() ?: continue
            val list = runCatching { parse(body) }.getOrNull()?.takeIf { it.isNotEmpty() } ?: continue
            cache = System.currentTimeMillis() to list
            return@withContext list
        }
        emptyList()
    }

    private fun parse(body: String): List<Station> {
        fun JsonObject.s(k: String) = (this[k] as? JsonPrimitive)?.contentOrNull.orEmpty().trim()
        val seen = HashSet<String>()
        return (Http.json.parseToJsonElement(body) as JsonArray).mapNotNull { e ->
            val o = e as? JsonObject ?: return@mapNotNull null
            val url = o.s("url_resolved").ifBlank { o.s("url") }
            val name = o.s("name").replace(Regex("\\s+"), " ")
            // https only: Android blocks plain http streams (no cleartext traffic in this app).
            if (!url.startsWith("https://") || name.isBlank() || !seen.add(name.lowercase()) || !seen.add(url)) return@mapNotNull null
            val langs = o.s("language").lowercase().split(',', ';').map { it.trim() }.filter { it.isNotBlank() }
            val icon = o.s("favicon").takeIf { it.startsWith("https://") }
            val id = "radio-" + o.s("stationuuid").ifBlank { name.hashCode().toString() }
            Station(
                Track(
                    id = Track.makeId(SourceType.URL, id),
                    source = SourceType.URL,
                    sourceId = id,
                    title = name,
                    artist = "Live radio" + (langs.firstOrNull()?.let { " · ${it.replaceFirstChar(Char::uppercase)}" } ?: ""),
                    album = "Live radio",
                    artworkUrl = icon,
                    streamUrl = url,
                    language = langs.firstOrNull { it in setOf("hindi", "punjabi", "haryanvi", "bhojpuri", "english", "tamil", "telugu", "marathi", "bengali", "gujarati", "kannada", "malayalam") }.orEmpty(),
                ),
                langs, o.s("tags"), (o["clickcount"] as? JsonPrimitive)?.intOrNull ?: 0,
            )
        }
    }
}

/** A live radio station (can't be downloaded or seeked). */
val Track.isLiveRadio: Boolean get() = source == SourceType.URL && sourceId.startsWith("radio-")
