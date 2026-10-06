package com.sangeet.player.data.lyrics

import com.sangeet.player.data.remote.Http
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.HttpUrl.Companion.toHttpUrl

/**
 * Finds songs from a line of their lyrics ("tere bina zindagi se koi shikwa to nahi") with Genius' public
 * search, which also looks inside lyrics. Gives song names + singers; the app then finds them to play.
 */
object LyricsSearch {
    data class Hit(val title: String, val artist: String)

    private val devanagari = Regex("[\\u0900-\\u097F]")
    private val brackets = Regex("\\s*[(\\[].*?[)\\]]")

    /** A few words typed (3+) can be a line from the middle of a song, not just its name. */
    fun looksLikeLine(q: String): Boolean = q.trim().split(Regex("\\s+")).size >= 3

    suspend fun find(line: String, limit: Int = 6): List<Hit> {
        val url = "https://genius.com/api/search/multi".toHttpUrl().newBuilder()
            .addQueryParameter("per_page", "5")
            .addQueryParameter("q", line.trim())
            .build().toString()
        val body = runCatching { Http.getText(url) }.getOrNull() ?: return emptyList()
        val sections = runCatching {
            Http.json.parseToJsonElement(body).jsonObject["response"]?.jsonObject?.get("sections")?.jsonArray
        }.getOrNull() ?: return emptyList()
        // Matches inside the lyrics first, then the best hit, then songs by name.
        val hits = listOf("lyric", "top_hit", "song").flatMap { type ->
            sections.filter { it.jsonObject["type"]?.jsonPrimitive?.contentOrNull == type }
                .flatMap { it.jsonObject["hits"]?.jsonArray.orEmpty() }
        }.mapNotNull { h ->
            runCatching {
                val o = h.jsonObject
                if (o["type"]?.jsonPrimitive?.contentOrNull != "song") return@runCatching null
                val r = o["result"]?.jsonObject ?: return@runCatching null
                val title = r["title"]?.jsonPrimitive?.contentOrNull ?: return@runCatching null
                val artist = r["primary_artist"]?.jsonObject?.get("name")?.jsonPrimitive?.contentOrNull.orEmpty()
                // Translations ("Genius English Translations") aren't the song itself.
                if (artist.contains("Genius", ignoreCase = true)) null else Hit(cleanTitle(title), artist)
            }.getOrNull()
        }
        return hits.filter { it.title.isNotBlank() }.distinctBy { it.title.lowercase() }.take(limit)
    }

    /** "तुम ही हो (Tum Hi Ho)" -> "Tum Hi Ho"; "Kesariya (From "Brahmastra")" -> "Kesariya". */
    private fun cleanTitle(t: String): String {
        if (devanagari.containsMatchIn(t)) {
            Regex("\\(([^)]*[A-Za-z][^)]*)\\)").find(t)?.groupValues?.get(1)?.let { return it.trim() }
        }
        return t.replace(brackets, "").trim()
    }
}
