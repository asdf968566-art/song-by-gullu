package com.sangeet.player.data

import com.sangeet.player.data.model.AudioQuality
import com.sangeet.player.data.model.OnlinePlaylist
import com.sangeet.player.data.model.inLanguages
import com.sangeet.player.data.model.SourceType
import com.sangeet.player.data.model.Track
import com.sangeet.player.data.remote.AudiusSource
import com.sangeet.player.data.remote.JamendoSource
import com.sangeet.player.data.remote.JioSaavnSource
import com.sangeet.player.data.remote.YouTubeSource
import com.sangeet.player.data.remote.OnlineSource
import com.sangeet.player.data.remote.SubsonicSource
import com.sangeet.player.data.settings.SettingsRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

data class SourceResult(val source: SourceType, val tracks: List<Track>, val error: String? = null)

class OnlineRepository(
    private val settings: SettingsRepository,
    private val network: NetworkMonitor,
) {
    // Indian sources pehle, taaki Hindi / Punjabi gaane upar aayein.
    val sources: List<OnlineSource> = listOf(YouTubeSource(), JioSaavnSource(), AudiusSource(), JamendoSource(), SubsonicSource())

    fun source(type: SourceType): OnlineSource? = sources.firstOrNull { it.type == type }

    /** Default online library (charts + playlists) JioSaavn se. */
    val saavn: JioSaavnSource = sources.filterIsInstance<JioSaavnSource>().first()
    val youtube: YouTubeSource = sources.filterIsInstance<YouTubeSource>().first()

    fun enabledSources(): List<OnlineSource> {
        val s = settings.current
        if (s.offlineMode) return emptyList()
        return sources.filter { it.isEnabled(s) }
    }

    /** The YouTube Data API key (Settings, or the built-in one). */
    val youtubeKey: String get() = settings.current.youtubeApiKey.trim()

    val canGoOnline: Boolean
        get() = !settings.current.offlineMode && network.status.value.online

    suspend fun trending(genre: String? = null): List<SourceResult> = fanOut { it.trending(settings.current, genre) }

    suspend fun search(query: String): List<SourceResult> = fanOut { it.search(query, settings.current) }

    private suspend fun fanOut(block: suspend (OnlineSource) -> List<Track>): List<SourceResult> = coroutineScope {
        if (!canGoOnline) return@coroutineScope emptyList()
        enabledSources().map { src ->
            async {
                try {
                    // Duplicate id se Lazy lists crash karti hain, isliye ek hi baar rakho.
                    SourceResult(src.type, block(src).distinctBy { it.id })
                } catch (e: Exception) {
                    SourceResult(src.type, emptyList(), e.message ?: "Network error")
                }
            }
        }.awaitAll()
    }

    /** Kisi bhasha ke trending / naye gaane, jo sources bhasha samajhte hain unse. */
    suspend fun byLanguage(language: String): List<Track> = coroutineScope {
        if (!canGoOnline) return@coroutineScope emptyList()
        enabledSources().map { src -> async { runCatching { src.byLanguage(language, settings.current) }.getOrDefault(emptyList()) } }
            .awaitAll().flatten().distinctBy { it.id }
    }

    /** Ek query ke saare sources ke gaane ek list mein (JioSaavn pehle). */
    suspend fun searchAll(query: String): List<Track> = search(query).flatMap { it.tracks }.distinctBy { it.id }

    /**
     * A category built from several JioSaavn searches (see [Category.more]): only songs in its language,
     * and from the singer searches only songs whose title fits [Category.match]. Same song once.
     */
    suspend fun categoryTracks(cat: Category): List<Track> = coroutineScope {
        val match = cat.match
        val found = (listOf(cat.query) + cat.more).map { q ->
            async {
                val trusted = match == null || match.containsMatchIn(q)
                (1..2).flatMap { page -> runCatching { saavn.searchPage(q, page) }.getOrDefault(emptyList()) }
                    .filter { it.inLanguages(listOf(cat.language)) && (trusted || match!!.containsMatchIn(it.title)) }
            }
        }.awaitAll()
        // Take turns from each search so the list isn't one singer after another.
        val mixed = (0 until (found.maxOfOrNull { it.size } ?: 0)).flatMap { i -> found.mapNotNull { it.getOrNull(i) } }
        val seen = HashSet<String>()
        mixed.filter { seen.add(it.title.lowercase().substringBefore(" (").trim()) }
            .ifEmpty { searchAll(cat.query) }
    }

    private val playlistSearches = java.util.concurrent.ConcurrentHashMap<String, List<OnlinePlaylist>>()
    private val playlistSongs = java.util.concurrent.ConcurrentHashMap<String, List<Track>>()

    /**
     * Songs for a mood, category or festival, taken from JioSaavn playlists made for it ("Feel Good Hindi",
     * "Bollywood Party Hits"). A song search for "hindi happy songs" only finds songs with the word in their name or
     * singer (Happy Raikoti, "Happy Birthday"), so it is used only when no fitting playlist is found, and then
     * without such songs. [page] picks other playlists for an endless feed.
     */
    suspend fun topicTracks(topic: Topic, page: Int = 0, limit: Int = 60): List<Track> = coroutineScope {
        if (!canGoOnline || !settings.current.jiosaavnEnabled) return@coroutineScope emptyList()
        val generic = Categories.languages.toSet() + setOf("hits", "hit", "top", "best", "new", "latest", "bollywood", "old", "playlist")
        fun has(text: String, w: String) = Regex("\\b${Regex.escape(w)}\\b", RegexOption.IGNORE_CASE).containsMatchIn(text)
        val words = topic.words.map { it.lowercase() }.distinct()
        val needed = words.filter { it !in generic }.ifEmpty { words }
        val found = topic.searches.map { q ->
            async {
                playlistSearches[q] ?: runCatching { saavn.searchPlaylists(q) }.getOrDefault(emptyList())
                    .also { if (it.isNotEmpty()) playlistSearches[q] = it }
            }
        }.awaitAll()
        // Playlists whose name fits, the best fits first (taking turns between the searches).
        val lists = (0 until (found.maxOfOrNull { it.size } ?: 0)).flatMap { i -> found.mapNotNull { it.getOrNull(i) } }
            .distinctBy { it.id }
            .filter { p -> needed.any { has(p.title, it) } }
            .sortedByDescending { p -> words.count { has(p.title, it) } }
        val picked = if (lists.isEmpty()) emptyList() else List(minOf(4, lists.size)) { lists[(page * 4 + it) % lists.size] }.distinctBy { it.id }
        val noisy = { t: Track -> topic.noise.any { has(t.artist, it) } }
        val songs = picked.map { p ->
            async {
                (playlistSongs[p.id] ?: runCatching { saavn.playlistTracks(p.id) }.getOrDefault(emptyList())
                    .also { if (it.isNotEmpty()) playlistSongs[p.id] = it })
                    .filter { it.inLanguages(topic.languages) && !noisy(it) }
                    .shuffled()
            }
        }.awaitAll()
        val mixed = (0 until (songs.maxOfOrNull { it.size } ?: 0)).flatMap { i -> songs.mapNotNull { it.getOrNull(i) } }
            .distinctBy { it.id }
        if (mixed.size >= 15) return@coroutineScope mixed.take(limit)
        // Hardly any playlists: a song search, minus songs that only match by their name or singer.
        val searched = topic.searches.map { q -> async { runCatching { saavn.searchPage("$q songs", page + 1) }.getOrDefault(emptyList()) } }
            .awaitAll().flatten()
            .filter { t -> t.inLanguages(topic.languages) && !noisy(t) && topic.noise.none { has(t.title, it) } }
        (mixed + searched).distinctBy { it.id }.take(limit)
    }

    /**
     * A category whose songs are mostly on YouTube (Pahadi): YouTube and JioSaavn searches, taking turns,
     * only songs that really are in its language, same song once.
     */
    suspend fun youtubeCategory(cat: Category): List<Track> = coroutineScope {
        if (!canGoOnline) return@coroutineScope emptyList()
        val s = settings.current
        val found = cat.youtube.flatMap { q ->
            listOf(
                async { if (youtube.isEnabled(s)) runCatching { youtube.search(q, s) }.getOrDefault(emptyList()) else emptyList() },
                async { if (saavn.isEnabled(s)) runCatching { saavn.searchPage(q, 1) }.getOrDefault(emptyList()) else emptyList() },
            )
        }.awaitAll().map { list -> list.filter { it.inLanguages(listOf(cat.language)) }.map { it.copy(language = cat.language) } }
        val seen = HashSet<String>()
        (0 until (found.maxOfOrNull { it.size } ?: 0)).flatMap { i -> found.mapNotNull { it.getOrNull(i) } }
            .filter { seen.add(it.title.lowercase().substringBefore(" (").substringBefore(" |").trim()) }
    }

    /**
     * A movie's songs in the film's order: its JioSaavn album ([albumId] when known, else the album with the film's
     * name and year), else songs from that album found by a search, else YouTube.
     */
    suspend fun movieSongs(title: String, year: Int, albumId: String = ""): List<Track> = coroutineScope {
        if (!canGoOnline) return@coroutineScope emptyList()
        if (albumId.isNotBlank()) runCatching { saavn.albumTracks(albumId) }.getOrNull()?.takeIf { it.isNotEmpty() }?.let { return@coroutineScope it }
        fun words(s: String) = s.lowercase().replace(Regex("""\(.*?\)|\[.*?]"""), " ")
            .replace(Regex("[^\\p{L}\\p{N}]+"), " ").split(" ").filter { it.isNotBlank() }
        val want = words(title)
        val album = runCatching { saavn.searchAlbums(title) }.getOrDefault(emptyList())
            .filter { words(it.title).containsAll(want) && (year == 0 || it.year == 0 || kotlin.math.abs(it.year - year) <= 1) }
            .sortedWith(compareBy({ if (words(it.title) == want) 0 else 1 }, { if (year > 0 && it.year > 0) kotlin.math.abs(it.year - year) else 2 }))
            .firstOrNull()
        album?.let { a -> runCatching { saavn.albumTracks(a.id) }.getOrNull()?.takeIf { it.isNotEmpty() }?.let { return@coroutineScope it } }
        val searched = runCatching { saavn.searchPage("$title ${if (year > 0) year else ""} songs".trim(), 1) }.getOrDefault(emptyList())
            .filter { words(it.album).containsAll(want) }
        if (searched.size >= 3) return@coroutineScope searched
        val s = settings.current
        if (youtube.isEnabled(s)) runCatching { youtube.search("$title ${if (year > 0) year else ""} movie songs".trim(), s) }.getOrDefault(emptyList())
        else searched
    }

    /** A feed mood's songs in these languages. */
    suspend fun moodTracks(mood: Mood, languages: List<String>, page: Int = 0, limit: Int = 60): List<Track> {
        val own = topicTracks(Topic(Moods.searches(mood, languages), mood.words, mood.words, languages), page, limit)
        if (own.size >= 15 || mood.close.isEmpty()) return own
        val closeMood = Mood(mood.emoji, mood.name, mood.close, mood.close)
        val near = topicTracks(Topic(Moods.searches(closeMood, languages), mood.close, mood.words, languages), page, limit)
        return (own + near).distinctBy { it.id }.take(limit)
    }

    /** Is waqt ke network ke hisaab se kaunsi quality chahiye. */
    fun streamingQuality(): AudioQuality {
        val s = settings.current
        return if (network.status.value.unmetered) s.wifiQuality else s.mobileQuality
    }

    /** Online gaane ka asli stream url (quality ke saath). Local ke liye null. */
    fun streamUrl(track: Track, quality: AudioQuality): String? = when (track.source) {
        SourceType.LOCAL -> null
        SourceType.URL -> track.streamUrl
        SourceType.YOUTUBE -> try {
            saavnOrYouTube(track, quality)
        } catch (e: Exception) {
            // YouTube ne roka (bot check / band video) -> wahi gaana JioSaavn pe dhoondh ke bajao.
            jioSaavnFallback(track, quality) ?: throw e
        }
        else -> source(track.source)?.streamUrl(track, quality, settings.current)
    }

    /**
     * Link for downloading. A YouTube song is taken from JioSaavn when the same song is there:
     * its CDN is fast and steady, while YouTube slows down full-file downloads.
     */
    fun downloadUrl(track: Track, quality: AudioQuality): String? =
        if (track.source == SourceType.YOUTUBE) jioSaavnFallback(track, quality) ?: streamUrl(track, quality)
        else streamUrl(track, quality)

    private fun saavnOrYouTube(track: Track, quality: AudioQuality): String =
        source(SourceType.YOUTUBE)!!.streamUrl(track, quality, settings.current)

    /** Player ke loader thread se chalta hai, isliye runBlocking theek hai. */
    private fun jioSaavnFallback(track: Track, quality: AudioQuality): String? {
        if (!settings.current.jiosaavnEnabled) return null
        val want = norm(track.title)
        if (want.isBlank()) return null
        val results = runCatching {
            kotlinx.coroutines.runBlocking { saavn.search("${track.title} ${track.artist}".trim(), settings.current) }
        }.getOrDefault(emptyList())
        val match = results.firstOrNull { norm(it.title) == want }
            ?: results.firstOrNull { norm(it.title).contains(want) || want.contains(norm(it.title)) }
            ?: return null
        android.util.Log.i("Sangeet", "YouTube fallback -> JioSaavn: '${track.title}' = '${match.title}' (${match.artist})")
        return saavn.streamUrl(match, quality, settings.current)
    }

    private fun norm(s: String) = s.lowercase()
        .replace(Regex("""\(.*?\)|\[.*?]"""), "")
        .replace(Regex("[^\\p{L}\\p{N}]+"), " ")
        .trim()

    fun qualityLabel(track: Track, quality: AudioQuality): String = when (track.source) {
        SourceType.LOCAL -> "Phone file"
        SourceType.URL -> "Original"
        SourceType.SUBSONIC -> quality.label
        SourceType.JIOSAAVN -> if (quality == AudioQuality.LOW) "96 kbps" else if (quality == AudioQuality.MEDIUM) "160 kbps" else "320 kbps (when available)"
        SourceType.YOUTUBE -> "YouTube audio"
        SourceType.JAMENDO -> if (quality == AudioQuality.LOW) "96 kbps" else "High (VBR)"
        SourceType.AUDIUS -> "Source quality"
    }
}
