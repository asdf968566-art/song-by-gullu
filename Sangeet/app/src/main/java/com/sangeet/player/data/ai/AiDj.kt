package com.sangeet.player.data.ai

import com.anthropic.client.AnthropicClient
import com.anthropic.client.okhttp.AnthropicOkHttpClient
import com.anthropic.core.JsonValue
import com.anthropic.errors.AnthropicServiceException
import com.anthropic.errors.RateLimitException
import com.anthropic.errors.UnauthorizedException
import com.anthropic.models.messages.MessageCreateParams
import com.anthropic.models.messages.OutputConfig
import com.fasterxml.jackson.annotation.JsonPropertyDescription
import com.sangeet.player.data.Categories
import com.sangeet.player.data.Moods
import com.sangeet.player.data.OnlineRepository
import com.sangeet.player.data.model.SourceType
import com.sangeet.player.data.model.Track
import com.sangeet.player.data.model.inLanguages
import com.sangeet.player.data.remote.LanguageGuess
import com.sangeet.player.data.settings.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext

/**
 * Plan the DJ follows. Public mutable fields + no-arg constructor so the Anthropic SDK (Jackson)
 * can derive the JSON schema and fill it in (structured output).
 */
class DjPlan {
    @JvmField
    @JsonPropertyDescription("Short friendly reply to the listener (max 15 words), in the language they wrote in.")
    var reply: String = ""

    @JvmField
    @JsonPropertyDescription("Short playlist title, e.g. 'Late Night Punjabi Drive'.")
    var title: String = ""

    @JvmField
    @JsonPropertyDescription("Song languages to include, lowercase English names: hindi, punjabi, english, haryanvi, bhojpuri, tamil, telugu, marathi, bengali, gujarati.")
    var languages: List<String> = emptyList()

    @JvmField
    @JsonPropertyDescription("6-10 varied search queries for a music catalogue (song names, artists, moods, eras), e.g. 'arijit singh sad songs', 'punjabi night drive songs', 'kumar sanu 90s hits'.")
    var searchQueries: List<String> = emptyList()

    @JvmField
    @JsonPropertyDescription("Specific real song titles that fit, as 'Song - Artist' (up to 10). Empty if unsure.")
    var songs: List<String> = emptyList()
}

data class DjResult(val plan: DjPlan, val tracks: List<Track>, val usedAi: Boolean)

/**
 * In-app AI DJ: "sad punjabi songs for a night drive" -> a fresh playlist.
 * With an Anthropic API key (Settings) Claude plans the searches; without one a built-in
 * parser understands language, mood, artist and era.
 */
class AiDj(
    private val online: OnlineRepository,
    private val settings: SettingsRepository,
) {
    @Volatile private var client: AnthropicClient? = null
    @Volatile private var clientKey: String? = null

    suspend fun make(request: String): DjResult = coroutineScope {
        val key = settings.current.anthropicApiKey.trim()
        val (plan, usedAi) = if (key.isNotEmpty()) {
            runCatching { askClaude(key, request) to true }.getOrElse { e ->
                localPlan(request).apply { reply = "${aiErrorText(e)} Using the built-in DJ instead." } to false
            }
        } else {
            localPlan(request) to false
        }
        if (plan.languages.isEmpty()) plan.languages = settings.current.languages

        val queries = (plan.songs + plan.searchQueries).map { it.trim() }.filter { it.isNotEmpty() }.distinct().take(16)
        // A mood ("happy punjabi songs"): songs from playlists made for it come first.
        val mood = Moods.find(request)
        val moodSongs = mood?.let { m -> async { runCatching { online.moodTracks(m, plan.languages, limit = 30) }.getOrDefault(emptyList()) } }
        val found = queries.map { q -> async { runCatching { online.searchAll(q).take(12) }.getOrDefault(emptyList()) } }
            .awaitAll()
        // Exact song picks first, then the rest interleaved for variety.
        val merged = ArrayList<Track>()
        val maxLen = found.maxOfOrNull { it.size } ?: 0
        for (i in 0 until maxLen) found.forEach { list -> list.getOrNull(i)?.let(merged::add) }
        // The built-in DJ searches "hindi happy songs", which also finds songs that only have the word in their
        // name or singer (Happy Raikoti): leave those out when there is a mood.
        val noisy = { t: Track ->
            !usedAi && mood != null && mood.words.any { w -> Regex("\\b${Regex.escape(w)}\\b", RegexOption.IGNORE_CASE).containsMatchIn("${t.title} ${t.artist}") }
        }
        var tracks = (moodSongs?.await().orEmpty() + merged.filterNot(noisy)).distinctBy { it.id }
            .filter { it.inLanguages(plan.languages) || plan.languages.isEmpty() }

        // Grow the list with YouTube Music radio (and a little JioSaavn) for a longer, varied mix.
        if (tracks.size < 50) {
            val yt = tracks.filter { it.source == SourceType.YOUTUBE }.take(3)
                .map { s -> async { runCatching { online.youtube.similar(s.sourceId) }.getOrDefault(emptyList()) } }
            val saavn = tracks.filter { it.source == SourceType.JIOSAAVN }.take(1)
                .map { s -> async { runCatching { online.saavn.similar(s.sourceId) }.getOrDefault(emptyList()) } }
            val more = (yt + saavn).awaitAll().flatten().filter { it.inLanguages(plan.languages) }
            tracks = (tracks + more).distinctBy { it.id }
        }
        DjResult(plan, tracks.take(60), usedAi)
    }

    // ------------------------------------------------------------ Claude

    private suspend fun askClaude(key: String, request: String): DjPlan = withContext(Dispatchers.IO) {
        val c = clientFor(key)
        val params = MessageCreateParams.builder()
            .model(MODEL)
            .maxTokens(16000L)
            .system(SYSTEM_PROMPT)
            .outputConfig(OutputConfig.builder().effort(OutputConfig.Effort.LOW).build())
            // Server-side fallback if the request is refused by a safety classifier.
            .putAdditionalHeader("anthropic-beta", "server-side-fallback-2026-07-01")
            .putAdditionalBodyProperty("fallbacks", JsonValue.from("default"))
            .addUserMessage(
                "Listener's preferred languages: ${settings.current.languages.joinToString()}.\n" +
                    "Request: $request"
            )
            .outputConfig(DjPlan::class.java)
            .build()
        val response = c.messages().create(params)
        response.content().stream()
            .flatMap { it.text().stream() }
            .map { it.text() }
            .findFirst()
            .orElseThrow { IllegalStateException("The AI DJ could not make a plan for that request.") }
    }

    private fun clientFor(key: String): AnthropicClient {
        val existing = client
        if (existing != null && clientKey == key) return existing
        return AnthropicOkHttpClient.builder().apiKey(key).build().also {
            client = it
            clientKey = key
        }
    }

    private fun aiErrorText(e: Throwable): String = when (e) {
        is UnauthorizedException -> "Your Anthropic API key was not accepted."
        is RateLimitException -> "The AI is busy right now."
        is AnthropicServiceException -> "The AI service returned an error (${e.statusCode()})."
        else -> "Couldn't reach the AI."
    }

    // ------------------------------------------------------------ built-in DJ (no key)

    fun localPlan(request: String): DjPlan {
        val t = request.lowercase()
        val langs = Categories.languages.filter { it in t }.ifEmpty {
            LanguageGuess.guess(request, "").takeIf { it.isNotBlank() }?.let { listOf(it) }.orEmpty()
        }.ifEmpty { settings.current.languages }
        val mood = MOODS.entries.firstOrNull { (words, _) -> words.any { it in t } }?.value
        val era = when {
            listOf("90s", "90's", "nineties", "purane", "old", "retro").any { it in t } -> "90s"
            listOf("80s", "70s", "classic").any { it in t } -> "old classic"
            listOf("new", "naye", "latest", "2025", "2026").any { it in t } -> "latest"
            else -> ""
        }
        val artists = KNOWN_ARTISTS.filter { it in t }
        val queries = buildList {
            artists.forEach { a -> add("$a ${mood ?: "hits"}"); add("$a $era songs".trim()) }
            langs.take(2).forEach { l ->
                add("$l ${mood ?: "hit"} songs $era".replace(Regex("\\s+"), " ").trim())
                add("$l ${mood ?: "popular"} playlist")
            }
            if (isEmpty()) add(request)
            add(request)
        }.distinct()
        return DjPlan().apply {
            reply = "Here's a mix for you."
            title = request.replaceFirstChar(Char::uppercase).take(40)
            languages = langs
            searchQueries = queries
        }
    }

    companion object {
        private const val MODEL = "claude-opus-5-5"

        private val SYSTEM_PROMPT = """
            You are the DJ inside Sangeet, an Indian music app with JioSaavn and YouTube catalogues.
            Turn the listener's request (English, Hindi or Hinglish) into a playlist plan.
            Prefer songs in the listener's languages unless they ask for another language.
            Give varied search queries: mix well-known hits with deeper cuts and different artists,
            so the playlist does not feel repetitive.
        """.trimIndent()

        private val MOODS = mapOf(
            listOf("sad", "dukh", "udaas", "breakup", "heartbreak", "dard") to "sad",
            listOf("happy", "khush", "cheerful") to "happy",
            listOf("party", "dance", "club", "naach") to "party",
            listOf("romantic", "love", "pyaar", "ishq", "date") to "romantic",
            listOf("chill", "lofi", "relax", "calm", "sukoon") to "lofi chill",
            listOf("sleep", "neend", "night", "raat") to "soft night",
            listOf("gym", "workout", "running", "exercise") to "workout",
            listOf("bhakti", "bhajan", "devotional", "god", "mandir") to "bhakti",
            listOf("drive", "road trip", "travel", "safar") to "road trip",
            listOf("rain", "barish", "baarish", "monsoon") to "barish",
            listOf("wedding", "shaadi", "sangeet", "mehendi") to "wedding",
        )

        private val KNOWN_ARTISTS = listOf(
            "arijit singh", "shreya ghoshal", "atif aslam", "jubin nautiyal", "neha kakkar", "sonu nigam",
            "kishore kumar", "lata mangeshkar", "mohammed rafi", "kumar sanu", "udit narayan", "alka yagnik",
            "honey singh", "badshah", "diljit dosanjh", "karan aujla", "sidhu moose wala", "ap dhillon",
            "shubh", "b praak", "darshan raval", "armaan malik", "vishal mishra", "pritam", "a r rahman",
            "sachet tandon", "mohit chauhan", "sunidhi chauhan", "kk", "anuv jain", "king", "guru randhawa",
            "pawan singh", "khesari lal", "masoom sharma", "jasleen royal", "papon", "ankit tiwari",
        )
    }
}
