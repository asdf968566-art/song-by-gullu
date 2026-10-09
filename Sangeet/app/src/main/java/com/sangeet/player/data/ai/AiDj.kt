package com.sangeet.player.data.ai

import com.anthropic.client.AnthropicClient
import com.anthropic.client.okhttp.AnthropicOkHttpClient
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

data class DjResult(
    val plan: DjPlan,
    val tracks: List<Track>,
    val usedAi: Boolean,
    /** What the built-in DJ understood (kept for the chat's next message). */
    val intent: DjIntent? = null,
    /** Things the listener can say next ("More like this", "No remix"). */
    val followUps: List<String> = emptyList(),
)

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

    /** Songs like this one (the app's radio), for "Kesariya jaise gaane". Set by the app. */
    var radio: (suspend (Track) -> List<Track>)? = null
    /** The listener's own taste (For You), for "kuch bhi" / "surprise me". Set by the app. */
    var forYou: (suspend () -> List<Track>)? = null
    /** A line about the listener's taste for Claude ("Often plays: Arijit Singh, Diljit..."). Set by the app. */
    var taste: (suspend () -> String)? = null

    /**
     * A mix for [request]. [previous] is what the chat asked before, so a follow-up ("aur", "sirf Arijit",
     * "naye wale", "remix hata do") changes that mix; [shown] are songs already given (for "more").
     * [history]: the earlier requests of this chat (for Claude).
     */
    suspend fun make(
        request: String,
        previous: DjIntent? = null,
        shown: Set<String> = emptySet(),
        history: List<String> = emptyList(),
    ): DjResult = coroutineScope {
        val intent = DjBrain.understand(request, previous, settings.current.languages)
        val followUps = DjBrain.followUps(intent)
        val key = settings.current.anthropicApiKey.trim()
        var note = ""
        if (key.isNotEmpty()) {
            val ai = runCatching { askClaude(key, request, history) }
            ai.getOrNull()?.let { plan ->
                if (plan.languages.isEmpty()) plan.languages = settings.current.languages
                val tracks = fromPlan(plan, request, usedAi = true).filter { it.id !in shown }
                if (tracks.isNotEmpty()) return@coroutineScope DjResult(plan, tracks.take(60), true, intent, followUps)
            }
            ai.exceptionOrNull()?.let { note = "${aiErrorText(it)} Using the built-in DJ instead. " }
        }
        // The built-in DJ: playlists made for the mood, the singers' songs, a song's radio, a film's album.
        val local = runCatching { buildLocal(intent, shown) }.getOrDefault(emptyList())
        val tracks = if (local.size >= 10) local else {
            val plan = localPlan(request).apply { if (intent.languages.isNotEmpty()) languages = intent.languages }
            (local + fromPlan(plan, request, usedAi = false))
                .distinctBy { it.id }
                .filter { it.id !in shown && !DjBrain.excluded(intent, it.title, it.artist) }
        }
        val title = DjBrain.describe(intent)
        val plan = DjPlan().apply {
            this.title = title.take(48)
            languages = intent.languages
            reply = note + when {
                tracks.isEmpty() -> "I couldn't find songs for that. Try other words."
                intent.page > 0 -> "Here are ${tracks.size.coerceAtMost(intent.count)} more."
                else -> "${title.replaceFirstChar(Char::uppercase)}: ${tracks.size.coerceAtMost(intent.count)} songs, starting now."
            }
        }
        DjResult(plan, tracks.take(intent.count), false, intent, followUps)
    }

    /** The built-in DJ's songs for an [i]ntent, best sources first, taking turns between them. */
    private suspend fun buildLocal(i: DjIntent, shown: Set<String>): List<Track> = coroutineScope {
        val langs = DjBrain.languagesFor(i)
        val era = when (i.era) { "old" -> "old"; "new" -> "new"; else -> i.era }
        val mood = i.mood
        val parts = ArrayList<kotlinx.coroutines.Deferred<List<Track>>>()
        // A song to start from: it, then its radio (songs like it).
        if (i.like.isNotBlank()) parts += async {
            val seed = runCatching { online.searchAll(i.like).firstOrNull() }.getOrNull()
            if (seed == null) emptyList() else listOf(seed) + runCatching { radio?.invoke(seed) }.getOrNull().orEmpty()
        }
        // A film's album ("Aashiqui 2 ke gaane"), also when the words are just a name.
        val albumName = i.movie.ifBlank {
            if (mood == null && i.artists.isEmpty() && i.like.isBlank() && i.words.isNotEmpty()) i.words.joinToString(" ") else ""
        }
        if (albumName.isNotBlank()) parts += async { runCatching { movieAlbum(albumName) }.getOrDefault(emptyList()) }
        // A mood (with its era) from playlists made for it; with singers, their songs in that mood instead.
        if (mood != null && i.artists.isEmpty()) parts += async {
            val withEra = if (era.isEmpty()) emptyList() else runCatching {
                online.topicTracks(
                    com.sangeet.player.data.Topic(langs.flatMap { l -> mood.searches.take(2).map { "$l $era $it" } }, listOf(era), mood.words, langs),
                    i.page,
                )
            }.getOrDefault(emptyList())
            withEra.ifEmpty { runCatching { online.moodTracks(mood, langs, i.page) }.getOrDefault(emptyList()) }
        }
        // Singers: their songs (with the mood / era when asked).
        i.artists.forEach { a ->
            parts += async {
                val q = listOfNotNull(a, mood?.searches?.firstOrNull(), era.ifEmpty { null }).joinToString(" ")
                val pages = listOf(q, a).distinct().map { query -> async { runCatching { online.saavn.searchPage(query, i.page + 1) }.getOrDefault(emptyList()) } }
                pages.awaitAll().flatten().filter { artistMatch(it.artist, a) }
            }
        }
        // An era or other words without a mood: playlists named so ("90s Hindi Hits", "Wedding Songs").
        if (mood == null && i.artists.isEmpty() && i.like.isBlank() && (era.isNotEmpty() || i.words.isNotEmpty())) parts += async {
            val w = (listOf(era) + i.words).filter { it.isNotBlank() }
            runCatching {
                online.topicTracks(com.sangeet.player.data.Topic(langs.map { "$it ${w.joinToString(" ")}" }, w, emptyList(), langs), i.page)
            }.getOrDefault(emptyList())
        }
        // "Kuch bhi", "surprise me", or nothing specific: the listener's own taste.
        val nothingSaid = mood == null && i.artists.isEmpty() && i.like.isBlank() && i.movie.isBlank() && i.words.isEmpty() && era.isEmpty()
        if (i.forMe || nothingSaid) parts += async { runCatching { forYou?.invoke() }.getOrNull().orEmpty() }

        val lists = parts.awaitAll()
        // A song's radio, a film or a singer: in whatever language their songs are.
        val anyLanguage = i.like.isNotBlank() || i.movie.isNotBlank() || albumName.isNotBlank() || i.artists.isNotEmpty()
        var tracks = (0 until (lists.maxOfOrNull { it.size } ?: 0)).flatMap { n -> lists.mapNotNull { it.getOrNull(n) } }
            .distinctBy { it.id }
            .filter { it.id !in shown && !DjBrain.excluded(i, it.title, it.artist) }
            .filter { anyLanguage || it.inLanguages(i.languages.ifEmpty { langs }) }
        // What other Sangeet listeners play with these songs.
        com.sangeet.player.data.community.Community.cached()?.let { shared ->
            val near = shared.near(tracks.take(20).map { it.id })
                .filter { it.id !in shown && !DjBrain.excluded(i, it.title, it.artist) && (anyLanguage || it.inLanguages(i.languages.ifEmpty { langs })) }
            tracks = (tracks + near.take(15)).distinctBy { it.id }
        }
        tracks
    }

    /** All songs of the film / album named [name], when one is named exactly so. */
    private suspend fun movieAlbum(name: String): List<Track> {
        fun words(s: String) = s.lowercase().replace(Regex("""\(.*?\)|\[.*?]"""), " ")
            .replace(Regex("[^\\p{L}\\p{N}]+"), " ").split(" ").filter { it.isNotBlank() }
        val want = words(name)
        if (want.isEmpty()) return emptyList()
        val album = online.saavn.searchAlbums(name).firstOrNull { a ->
            val w = words(a.title)
            w == want || (want.size >= 2 && w.containsAll(want))
        } ?: return emptyList()
        return online.saavn.albumTracks(album.id)
    }

    private fun artistMatch(artist: String, wanted: String): Boolean {
        val a = artist.lowercase()
        val w = wanted.lowercase()
        return a.contains(w) || (w.length >= 4 && w.split(" ").first().let { it.length >= 4 && a.contains(it) })
    }

    /** Songs for a plan of searches (Claude's, or the simple built-in one). */
    private suspend fun fromPlan(plan: DjPlan, request: String, usedAi: Boolean): List<Track> = coroutineScope {
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

        // What other Sangeet listeners play with these songs.
        com.sangeet.player.data.community.Community.cached()?.let { shared ->
            val near = shared.near(tracks.take(20).map { it.id }).filter { it.inLanguages(plan.languages) || plan.languages.isEmpty() }
            tracks = (tracks + near.take(15)).distinctBy { it.id }
        }
        // Grow the list with YouTube Music radio (and a little JioSaavn) for a longer, varied mix.
        if (tracks.size < 50) {
            val yt = tracks.filter { it.source == SourceType.YOUTUBE }.take(3)
                .map { s -> async { runCatching { online.youtube.similar(s.sourceId) }.getOrDefault(emptyList()) } }
            val saavn = tracks.filter { it.source == SourceType.JIOSAAVN }.take(1)
                .map { s -> async { runCatching { online.saavn.similar(s.sourceId) }.getOrDefault(emptyList()) } }
            val more = (yt + saavn).awaitAll().flatten().filter { it.inLanguages(plan.languages) }
            tracks = (tracks + more).distinctBy { it.id }
        }
        tracks
    }

    // ------------------------------------------------------------ Claude

    private suspend fun askClaude(key: String, request: String, history: List<String>): DjPlan = withContext(Dispatchers.IO) {
        val c = clientFor(key)
        val about = runCatching { taste?.invoke() }.getOrNull().orEmpty()
        val earlier = if (history.isEmpty()) "" else
            "Earlier in this chat the listener asked, in order: " + history.takeLast(6).joinToString(" | ") { "\"$it\"" } +
                ". Treat the new message as a change to that mix when it reads like one (\"more\", \"only Arijit\", \"no remix\").\n"
        val params = MessageCreateParams.builder()
            .model(MODEL)
            .maxTokens(16000L)
            .system(SYSTEM_PROMPT)
            .outputConfig(OutputConfig.builder().effort(OutputConfig.Effort.LOW).build())
            .addUserMessage(
                "Listener's preferred languages: ${settings.current.languages.joinToString()}.\n" +
                    (if (about.isNotBlank()) "$about\n" else "") +
                    earlier +
                    "Request: $request"
            )
            .outputConfig(DjPlan::class.java)
            .build()
        val response = c.messages().create(params)
        // No text (e.g. the request was declined): the built-in DJ takes over.
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
        // Fast and low-cost for picking songs (owner's choice, Oct 9). No server-side refusal fallback on this model.
        private const val MODEL = "claude-haiku-5-5"

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
