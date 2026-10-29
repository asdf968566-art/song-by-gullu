package com.sangeet.player.data.recommend

import android.content.Context
import com.sangeet.player.data.LibraryRepository
import com.sangeet.player.data.LocalMusicRepository
import com.sangeet.player.data.OnlineRepository
import com.sangeet.player.data.SameSong
import com.sangeet.player.data.db.ListenDao
import com.sangeet.player.data.model.SourceType
import com.sangeet.player.data.model.Track
import com.sangeet.player.data.model.inLanguages
import com.sangeet.player.data.remote.LanguageGuess
import com.sangeet.player.data.settings.SettingsRepository
import kotlin.math.ln
import kotlin.random.Random
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

data class PlayedTrack(val track: Track, val playCount: Int, val playedAt: Long)

/** Suggest kiya gaana + kyun suggest hua. */
data class Suggestion(val track: Track, val reason: String)

/** Track record se bani auto playlist. [top]: Home's "Your top mixes" row (eras, moods, singers, languages). */
data class Mix(val id: String, val title: String, val subtitle: String, val tracks: List<Track>, val top: Boolean = false) {
    val artworkUrl: String? get() = tracks.firstNotNullOfOrNull { it.artworkUrl }
}

/**
 * Aapki history (kitni baar, kab suna) + liked songs se:
 *  - suggestions (Home + Discover feed)
 *  - mixes (Daily Mix, Artist Mix, On Repeat, ...) jo Library mein auto playlist bante hain
 *  - radio (queue khatam ho to milte-julte gaane)
 */
class RecommendationRepository(
    private val context: Context,
    private val library: LibraryRepository,
    private val local: LocalMusicRepository,
    private val online: OnlineRepository,
    private val settings: SettingsRepository,
    private val catalog: CatalogPool,
    listens: ListenDao,
) {
    private val prefs = context.getSharedPreferences("auto_playlists", Context.MODE_PRIVATE)
    private val mixLock = Mutex()
    private val syncLock = Mutex()
    private var mixesAt = 0L

    private val _mixes = MutableStateFlow<List<Mix>>(emptyList())
    val mixes: StateFlow<List<Mix>> = _mixes.asStateFlow()

    fun mix(id: String): Mix? = _mixes.value.firstOrNull { it.id == id }

    /** Spotify / YouTube Music style mixes, kept on the phone for their day or week (MadeForYou). */
    private val madeForYou = MadeForYou(this, library, online, settings, catalog, listens, java.io.File(context.filesDir, "mixes.json"))

    // ------------------------------------------------------------ dislike ("aisa gaana mat dikhao")

    private val taste = context.getSharedPreferences("taste", Context.MODE_PRIVATE)

    /** Feed mein left swipe: ye gaana dobara nahi, aur is artist ke gaane kam. */
    fun dislike(track: Track) {
        val ids = taste.getStringSet("disliked_ids", emptySet()).orEmpty() + track.id
        val key = "artist_" + norm(track.artist)
        taste.edit()
            .putStringSet("disliked_ids", ids.toList().takeLast(2000).toSet())
            .putInt(key, taste.getInt(key, 0) + 1)
            .apply()
    }

    // ------------------------------------------------------------ seen: ek baar aaya gaana auto mein dobara nahi

    private val seenPrefs = context.getSharedPreferences("seen", Context.MODE_PRIVATE)
    private val seen: LinkedHashSet<String> by lazy {
        LinkedHashSet(seenPrefs.getString("ids", "").orEmpty().split('\n').filter { it.isNotBlank() })
    }

    /** Feed mein dikha / baja gaana yaad rakho (max 20,000). */
    fun markSeen(ids: Collection<String>) {
        if (ids.isEmpty()) return
        val snapshot = synchronized(seen) {
            ids.forEach { seen.remove(it); seen.add(it) }
            while (seen.size > MAX_SEEN) seen.remove(seen.first())
            seen.joinToString("\n")
        }
        seenPrefs.edit().putString("ids", snapshot).apply()
    }

    fun clearSeen() {
        synchronized(seen) { seen.clear() }
        seenPrefs.edit().remove("ids").apply()
    }

    val seenCount: Int get() = synchronized(seen) { seen.size }

    internal fun seenIds(): Set<String> = synchronized(seen) { HashSet(seen) }

    internal fun dislikedIds(): Set<String> = taste.getStringSet("disliked_ids", emptySet()).orEmpty()
    private fun artistDislikes(artist: String): Int = taste.getInt("artist_" + norm(artist), 0)

    internal class Profile(
        val played: List<PlayedTrack>,
        val liked: List<Track>,
        val artistScore: Map<String, Double>,
        private val artistNames: Map<String, String>,
        val genres: List<String>,
        /** Sabse zyada suni bhashayein (history + settings). */
        val languages: List<String>,
    ) {
        val playedIds: Set<String> = played.mapTo(HashSet()) { it.track.id }
        /** Pichhle 12 ghante mein suna — auto mode mein dobara nahi. */
        val recentIds: Set<String> = played
            .filter { System.currentTimeMillis() - it.playedAt < 12 * 3_600_000L }
            .mapTo(HashSet()) { it.track.id }
        val topArtists: List<String> =
            artistScore.entries.sortedByDescending { it.value }.map { artistNames.getValue(it.key) }
        val isEmpty: Boolean get() = played.isEmpty() && liked.isEmpty()
    }

    // ------------------------------------------------------------ public

    // All the heavy work (thousands of songs scored) runs off the main thread, so the UI never stutters.
    suspend fun suggestions(limit: Int = 30, exclude: Set<String> = emptySet()): List<Suggestion> = withContext(Dispatchers.Default) {
        val p = profile()
        val pool = candidates(p, null)
        val fresh = rank(p, pool, null, exclude + p.recentIds + seenIds(), preferNew = true)
        // Sab dekh liya ho (bahut kam hota hai) tabhi purane mein se
        (fresh.ifEmpty { rank(p, pool, null, exclude + p.recentIds, preferNew = true) }).take(limit)
    }

    /**
     * Autoplay / radio: haal hi mein (12 ghante) suna gaana apne aap dobara nahi aata. Songs of the seed's own kind come
     * first (report #63: "bollywood ya pahadi to uske related hi … latest and popular wale"): Pahadi after Pahadi,
     * Hindi after Hindi; other songs only when those run out.
     */
    suspend fun radio(seed: Track, exclude: Set<String>, limit: Int = 15): List<Track> = withContext(Dispatchers.Default) {
        // A YouTube song's JioSaavn copy tells its year and language, and has JioSaavn's "similar" list.
        val twinJob: Deferred<Track?>? = if (seed.source != SourceType.JIOSAAVN && online.canGoOnline && settings.current.jiosaavnEnabled) {
            CoroutineScope(SupervisorJob() + Dispatchers.IO).async { saavnTwin(seed, sameName = true) }
        } else null
        val p = profile()
        val twin = twinJob?.let { job -> withTimeoutOrNull(8_000) { job.await() } ?: run { job.cancel(); null } }
        val known = if (twin == null) seed else seed.copy(
            year = seed.year.takeIf { it > 0 } ?: twin.year,
            language = seed.language.ifBlank { twin.language },
        )
        val pool = candidates(p, known, twin)
        val fresh = rank(p, pool, known, exclude + p.recentIds + seenIds(), preferNew = false)
        val ranked = fresh.ifEmpty { rank(p, pool, known, exclude + p.recentIds, preferNew = false) }
        val pahadi = pahadiIds()
        val kind = kindOf(known, pahadi)
        val (same, other) = if (kind.isBlank()) ranked to emptyList() else ranked.partition { kindOf(it.track, pahadi) == kind }
        val out = (spreadArtists(same) + spreadArtists(other)).take(limit).map { it.track }
        android.util.Log.i("Sangeet", "radio: ${out.size} songs after '${seed.title}' by ${seed.artist} (${kind.ifBlank { "?" }}): ${minOf(same.size, out.size)} the same kind")
        out
    }

    /**
     * "More like this" for the For You feed (report #51: "jo song play ho uske jaise song chalne chahiye … 90s ka koi
     * song chalaya toh 90s ke usi genre ke"): the same singer's songs, and songs like it from JioSaavn's and YouTube
     * Music's own lists of what its listeners play next, from the same years and in its language. Every other song
     * is the singer's own, so the singer keeps coming back.
     */
    suspend fun likeThis(seed: Track, exclude: Set<String>, limit: Int = 6): List<Track> = withContext(Dispatchers.Default) {
        val singer = mainSinger(seed.artist)
        val canOnline = online.canGoOnline
        val saavnOn = canOnline && settings.current.jiosaavnEnabled
        val ytOn = canOnline && settings.current.youtubeEnabled
        val bg = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        // A YouTube song's JioSaavn twin gives its year and JioSaavn's "similar" list.
        val twin: Deferred<Track?>? = if (seed.source != SourceType.JIOSAAVN && saavnOn) bg.async { saavnTwin(seed) } else null
        val bySinger: Deferred<List<Track>>? = if (singer != null && canOnline) bg.async {
            runCatching { online.search(singer) }.getOrDefault(emptyList()).flatMap { it.tracks }.filter { artistMatch(it.artist, singer) }
        } else null
        val bySingerDeep: Deferred<List<Track>>? = if (singer != null && saavnOn) bg.async {
            runCatching { online.saavn.searchPage(singer, Random.nextInt(2, 4)) }.getOrDefault(emptyList()).filter { artistMatch(it.artist, singer) }
        } else null
        val ytSimilar: Deferred<List<Track>>? = if (ytOn) bg.async {
            runCatching {
                val vid = if (seed.source == SourceType.YOUTUBE) seed.sourceId else online.youtube.find(seed.title, seed.artist)?.sourceId
                if (vid == null) emptyList<Track>() else online.youtube.similar(vid)
            }.getOrDefault(emptyList())
        } else null

        val deadline = System.currentTimeMillis() + 9_000
        suspend fun <T> Deferred<T>.orSkip(): T? =
            withTimeoutOrNull((deadline - System.currentTimeMillis()).coerceAtLeast(1)) { await() } ?: run { cancel(); null }

        val saavnSeed = if (seed.source == SourceType.JIOSAAVN) seed else twin?.orSkip()
        val saavnSimilar: Deferred<List<Track>>? = if (saavnOn && saavnSeed != null) bg.async {
            runCatching { online.saavn.similar(saavnSeed.sourceId) }.getOrDefault(emptyList())
        } else null
        val year = seed.year.takeIf { it > 0 } ?: saavnSeed?.year ?: 0
        val seedLang = seed.language.ifBlank { saavnSeed?.language.orEmpty() }
            .ifBlank { LanguageGuess.guess(seed.title, seed.artist) }
        val pahadi = pahadiIds()
        val seedKind = kindOf(seed, pahadi).ifBlank { seedLang }
        val langs = (settings.current.languages + listOf(seedLang, seedKind).filter { it.isNotBlank() }).distinct()

        val found = buildList {
            addAll(saavnSimilar?.orSkip().orEmpty())
            addAll(ytSimilar?.orSkip().orEmpty())
            addAll(bySinger?.orSkip().orEmpty())
            addAll(bySingerDeep?.orSkip().orEmpty())
            if (singer != null) {
                addAll(local.songs.value.filter { artistMatch(it.artist, singer) })
                if (catalog.has(seedKind)) addAll(catalog.songs(seedKind).filter { artistMatch(it.artist, singer) })
            }
        }
        val skip = exclude + seenIds() + dislikedIds() + seed.id
        val rnd = Random(System.nanoTime())
        // Same years count most: a 90s song brings 90s songs, a new one new songs. Its kind (Pahadi, Hindi…) and
        // popular songs count too (report #63).
        val popular = catalogRanks(seedKind)
        val ranked = found
            .filter { it.id !in skip && it.inLanguages(langs) && norm(it.title) != norm(seed.title) }
            .distinctBy { norm(it.title) }
            .map { t ->
                var score = era(year, t) + popularity(t, popular) + kindScore(seedKind, kindOf(t, pahadi)) + rnd.nextDouble() * 1.2
                score -= minOf(artistDislikes(t.artist), 4) * 0.8
                t to score
            }
            .sortedByDescending { it.second }
            .map { it.first }
        val mine = if (singer == null) emptyList() else ranked.filter { artistMatch(it.artist, singer) }
        val like = ranked.filter { singer == null || !artistMatch(it.artist, singer) }
        buildList {
            val a = mine.iterator()
            val b = like.iterator()
            while (size < limit && (a.hasNext() || b.hasNext())) {
                if (a.hasNext()) add(a.next())
                if (size < limit && b.hasNext()) add(b.next())
            }
        }
    }

    /** The singer to follow: the first name of the song's singers, not a music label's channel. */
    internal fun mainSinger(artist: String): String? {
        val first = artist.replace(Regex("(?i)\\s*-\\s*topic$|vevo$|\\s+official$"), "")
            .split(',', '&', '/', '|').map { it.trim() }.firstOrNull { it.isNotBlank() } ?: return null
        val n = norm(first)
        if (n.isBlank() || n == "unknown" || n == "unknown artist" || LABELS.any { n.contains(it) }) return null
        return first
    }

    suspend fun refreshMixes(force: Boolean = false): List<Mix> = mixLock.withLock {
        val fresh = System.currentTimeMillis() - mixesAt < 30 * 60_000L
        if (!force && fresh && _mixes.value.isNotEmpty()) return@withLock _mixes.value
        val built = withContext(Dispatchers.Default) { withDistinctCovers(madeForYou.build(force)) }
        _mixes.value = built
        mixesAt = System.currentTimeMillis()
        built
    }

    /** Mixes ko Library ki asli playlists mein likho ("✨ Daily Mix" wagairah). */
    suspend fun syncAutoPlaylists(force: Boolean = false) = syncLock.withLock {
        val last = prefs.getLong(KEY_LAST_SYNC, 0L)
        if (!force && System.currentTimeMillis() - last < 12 * 3_600_000L) return@withLock
        // Not forced: the mixes just built (Home's refresh) are used, else only those whose day / week ended are built.
        val mixes = refreshMixes().filter { it.id in SYNCED }
        // Mixes that aren't kept in the Library any more (Fresh Finds, Rediscover, the old single Daily Mix…): their
        // playlist goes, unless the listener renamed it (then it's theirs).
        prefs.all.keys.filter { it.startsWith("pl_") && it.removePrefix("pl_") !in SYNCED }.forEach { key ->
            val id = prefs.getLong(key, -1L)
            if (id > 0 && library.playlistExists(id) && library.playlistName(id)?.startsWith("✨") == true) library.deletePlaylist(id)
            prefs.edit().remove(key).apply()
        }
        for (m in mixes) {
            val key = "pl_${m.id}"
            val name = "✨ ${m.title}"
            val existing = prefs.getLong(key, -1L).takeIf { it > 0 && library.playlistExists(it) }
            if (existing != null) {
                library.renamePlaylist(existing, name)
                library.replacePlaylistTracks(existing, m.tracks)
            } else {
                prefs.edit().putLong(key, library.createPlaylist(name, m.tracks)).apply()
            }
        }
        prefs.edit().putLong(KEY_LAST_SYNC, System.currentTimeMillis()).apply()
    }

    // ------------------------------------------------------------ profile

    internal suspend fun profile(): Profile {
        val played = library.playedHistory(500)
        val liked = library.favoritesOnce()
        val now = System.currentTimeMillis()
        val score = HashMap<String, Double>()
        val names = HashMap<String, String>()

        fun add(t: Track, weight: Double) {
            val a = t.artist.trim()
            if (a.isBlank() || a.equals("Unknown artist", true) || a.equals("Unknown", true)) return
            val k = norm(a)
            if (k.isBlank()) return
            names.putIfAbsent(k, a)
            score[k] = (score[k] ?: 0.0) + weight
        }

        played.forEach { p ->
            // Haal hi mein suna = zyada wazan, purana = kam.
            val ageDays = (now - p.playedAt) / 86_400_000.0
            add(p.track, p.playCount * (0.5 + 1.0 / (1.0 + ageDays / 7.0)))
        }
        liked.forEach { add(it, 3.0) }

        // Audius gaanon mein album ki jagah genre hota hai.
        val genres = (played.map { it.track } + liked)
            .filter { it.source == SourceType.AUDIUS && it.album.isNotBlank() && it.album != "Audius" }
            .groupingBy { it.album }.eachCount()
            .entries.sortedByDescending { it.value }.map { it.key }

        // Bhasha ka wazan (tumhari record_play jaisa): jitna suna, utna wazan; phir settings wali.
        val langScore = HashMap<String, Double>()
        played.forEach { p -> if (p.track.language.isNotBlank()) langScore[p.track.language] = (langScore[p.track.language] ?: 0.0) + p.playCount }
        liked.forEach { t -> if (t.language.isNotBlank()) langScore[t.language] = (langScore[t.language] ?: 0.0) + 3.0 }
        val languages = (langScore.entries.sortedByDescending { it.value }.map { it.key } + settings.current.languages).distinct()

        return Profile(played, liked, score, names, genres, languages)
    }

    // ------------------------------------------------------------ candidates + ranking

    /** [twin]: a YouTube seed's JioSaavn copy, for JioSaavn's "similar" list. */
    internal suspend fun candidates(p: Profile, seed: Track?, twin: Track? = null): List<Suggestion> = coroutineScope {
        val out = ArrayList<Suggestion>()
        // Sources run outside this scope, so one that hangs (blocking network call) can't hold the feed.
        val bg = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val songs = local.songs.value
        val artists = buildList {
            seed?.artist?.takeIf { it.isNotBlank() && !it.equals("Unknown artist", true) }?.let(::add)
            addAll(p.topArtists.take(5))
        }.distinctBy(::norm)

        val canOnline = online.canGoOnline
        val byArtist = if (canOnline) artists.take(4).map { a ->
            bg.async {
                a to runCatching { online.search(a) }.getOrDefault(emptyList())
                    .flatMap { it.tracks }
                    .filter { artistMatch(it.artist, a) }
            }
        } else emptyList()
        val byGenre = if (canOnline) p.genres.take(2).map { g ->
            bg.async { g to runCatching { online.trending(g) }.getOrDefault(emptyList()).flatMap { it.tracks } }
        } else emptyList()
        val byLanguage = if (canOnline) p.languages.take(2).map { lang ->
            bg.async { lang to runCatching { online.byLanguage(lang) }.getOrDefault(emptyList()) }
        } else emptyList()
        // Lakhon gaano se variety: JioSaavn ki "similar" (users ke data se), random playlists, search ke aage ke pages
        val saavnOn = canOnline && settings.current.jiosaavnEnabled
        // A radio asks about its own song first; the feed about songs from the listener's history.
        val recoSeeds = buildList {
            seed?.takeIf { it.source == SourceType.JIOSAAVN }?.let(::add)
            twin?.let(::add)
            addAll((p.liked + p.played.map { it.track }).filter { it.source == SourceType.JIOSAAVN }.shuffled().take(if (seed != null) 1 else 2))
        }.distinctBy { it.id }.take(2)
        val byReco = if (saavnOn) recoSeeds.map { s ->
            bg.async { s to runCatching { online.saavn.similar(s.sourceId) }.getOrDefault(emptyList()) }
        } else emptyList()
        // YouTube Music radio (main source): YouTube ke listeners aage kya sunte hain
        val ytOn = canOnline && settings.current.youtubeEnabled
        val ytSeeds = buildList {
            seed?.let(::add)
            addAll((p.liked + p.played.map { it.track }).shuffled().take(if (seed != null) 2 else 5))
        }.distinctBy { it.id }.take(5)
        val byYtMix = if (ytOn) ytSeeds.map { s ->
            bg.async {
                s to runCatching {
                    val vid = if (s.source == SourceType.YOUTUBE) s.sourceId else online.youtube.find(s.title, s.artist)?.sourceId
                    if (vid == null) emptyList() else online.youtube.similar(vid)
                }.getOrDefault(emptyList())
            }
        } else emptyList()
        val byPlaylists: Deferred<List<Pair<String, Track>>>? =
            if (saavnOn && seed == null) bg.async { runCatching { randomPlaylistTracks() }.getOrDefault(emptyList()) } else null
        // A radio stays with its song's kind (report #63): what's trending in its language now (new and most played),
        // or after a Pahadi song (JioSaavn has no such language) Pahadi searches, kept when names or singers say Pahadi.
        val seedKind = seed?.let { kindOf(it, pahadiIds()) }.orEmpty()
        val byKind: Deferred<List<Track>>? = when {
            seedKind.isBlank() || !canOnline -> null
            seedKind == "pahadi" -> if (saavnOn) bg.async {
                PAHADI_SEARCHES.map { q -> async { runCatching { online.saavn.searchPage(q, 1) }.getOrDefault(emptyList()) } }
                    .awaitAll().flatten()
                    .filter { LanguageGuess.isPahadi(it.title, "${it.artist} ${it.album}") }
            } else null
            seedKind in p.languages.take(2) -> null // byLanguage has it
            else -> bg.async { runCatching { online.byLanguage(seedKind) }.getOrDefault(emptyList()) }
        }
        // The catalog's most played songs of that kind, and some more of them at random.
        val kindCatalog = if (!catalog.has(seedKind)) emptyList() else catalog.songs(seedKind).let { all ->
            val seen = seenIds()
            all.take(150) + all.drop(150).filter { it.id !in seen }.shuffled().take(100)
        }
        val byArtistDeep = if (saavnOn) artists.take(3).map { a ->
            bg.async { a to runCatching { online.saavn.searchPage(a, Random.nextInt(2, 5)) }.getOrDefault(emptyList()).filter { artistMatch(it.artist, a) } }
        } else emptyList()
        // Songs from the nightly catalog that are already on the phone (never waits for a download).
        val favs = artists.map(::norm)
        val fromCatalog = runCatching {
            catalog.sample(p.languages.take(3), 60, seenIds()) { a -> favs.any { f -> norm(a).contains(f) } }
        }.getOrDefault(emptyList())
        val trending = if (canOnline && artists.size < 3) {
            bg.async { runCatching { online.trending() }.getOrDefault(emptyList()).flatMap { it.tracks } }
        } else null

        // A slow source (e.g. YouTube on a weak network) must not keep the feed loading: 10 s for all of them.
        val deadline = System.currentTimeMillis() + 10_000
        suspend fun <T> Deferred<T>.orSkip(): T? =
            withTimeoutOrNull((deadline - System.currentTimeMillis()).coerceAtLeast(1)) { await() } ?: run { cancel(); null }
        suspend fun <T> List<Deferred<T>>.ready(): List<T> = mapNotNull { it.orSkip() }

        // Phone ke gaane: pasandida artists ke
        artists.forEach { a ->
            songs.filter { artistMatch(it.artist, a) }.forEach { out += Suggestion(it, reasonFor(a, seed)) }
        }
        byArtist.ready().forEach { (a, list) -> list.forEach { out += Suggestion(it, reasonFor(a, seed)) } }
        byGenre.ready().forEach { (g, list) -> list.take(25).forEach { out += Suggestion(it, "Because you like $g") } }
        byLanguage.ready().forEach { (lang, list) ->
            list.take(40).forEach { out += Suggestion(it, "New in ${lang.replaceFirstChar(Char::uppercase)}") }
        }
        byYtMix.ready().forEach { (s, list) -> list.forEach { out += Suggestion(it, "Similar to \"${s.title}\"") } }
        byReco.ready().forEach { (s, list) -> list.forEach { out += Suggestion(it, "Similar to \"${s.title}\"") } }
        byArtistDeep.ready().forEach { (a, list) -> list.forEach { out += Suggestion(it, reasonFor(a, seed)) } }
        byPlaylists?.orSkip()?.forEach { (name, t) -> out += Suggestion(t, "From $name") }
        trending?.orSkip()?.forEach { out += Suggestion(it, "Trending now") }
        val kindName = seedKind.replaceFirstChar(Char::uppercase)
        byKind?.orSkip()?.forEach { out += Suggestion(it, "More $kindName songs") }
        kindCatalog.forEach { out += Suggestion(it, "Popular $kindName songs") }
        fromCatalog.forEach { out += Suggestion(it, "New for you") }
        // Other Sangeet listeners: songs played and liked together with yours, and what most of them play.
        com.sangeet.player.data.community.Community.current(context)?.let { shared ->
            val mine = buildList {
                seed?.let { add(it.id) }
                addAll(p.liked.take(30).map { it.id })
                addAll(p.played.take(30).map { it.track.id })
            }.distinct()
            shared.near(mine).take(50).forEach { out += Suggestion(it, "Listeners like you play this") }
            shared.popular().take(30).forEach { out += Suggestion(it, "Popular with Sangeet listeners") }
        }
        // Thoda naya-pan: phone ke kuch random gaane
        songs.shuffled().take(20).forEach { out += Suggestion(it, "From your phone") }
        // Pehle se liked gaane bhi (radio / mix mein kaam aate hain)
        p.liked.take(30).forEach { out += Suggestion(it, "From your likes") }

        out.distinctBy { it.track.id }
    }

    /** 2-3 random online playlists (hazaron mein se) ke gaane. */
    private suspend fun randomPlaylistTracks(): List<Pair<String, Track>> = coroutineScope {
        val page = Random.nextInt(1, 30)
        val lists = online.saavn.featuredPlaylists(settings.current, page).shuffled().take(1)
        lists.map { pl -> async { runCatching { online.saavn.playlistTracks(pl.id) }.getOrDefault(emptyList()).map { pl.title to it } } }
            .awaitAll().flatten()
    }

    internal fun rank(
        p: Profile,
        list: List<Suggestion>,
        seed: Track?,
        exclude: Set<String>,
        preferNew: Boolean,
    ): List<Suggestion> {
        // Har ghante thoda alag order, par ek ghante ke andar stable.
        val rnd = Random(System.nanoTime())
        val seedArtist = seed?.artist?.let(::norm)
        val topLangs = p.languages.take(2).toSet()
        val disliked = dislikedIds()
        // Strict bhasha: Hindi chuna hai to sirf Hindi (phone ke gaane chhod ke). A radio also keeps its song's own
        // language: a searched Pahadi or Tamil song is followed by more like it, chosen or not.
        val seedLang = seed?.let { it.language.ifBlank { LanguageGuess.guess(it.title, it.artist) } }.orEmpty()
        // A radio keeps its song's kind (Pahadi, Hindi…), its time, and plays popular songs first (reports #51, #63).
        val pahadi = if (seed != null) pahadiIds() else emptySet()
        val seedKind = seed?.let { kindOf(it, pahadi) }.orEmpty()
        val langs = (settings.current.languages + listOf(seedLang, seedKind).filter { it.isNotBlank() }).distinct()
        val popular = catalogRanks(seedKind)
        val seedKey = seed?.let(SameSong::key)
        return list
            .filter { it.track.id !in exclude && it.track.id != seed?.id && it.track.id !in disliked }
            .filter { it.track.inLanguages(langs) }
            .filter { seedKey == null || !SameSong.key(it.track).sameAs(seedKey) }
            .map { s ->
                val a = norm(s.track.artist)
                var score = ln(1.0 + (p.artistScore[a] ?: 0.0))
                if (seedArtist != null && a == seedArtist) score += 2.0
                if (s.track.id in p.playedIds) score += if (preferNew) -1.5 else 0.4
                score -= minOf(artistDislikes(s.track.artist), 4) * 0.8
                // Pasandida bhasha = bonus, doosri bhasha = thoda kam
                val lang = s.track.language
                if (lang.isNotBlank()) score += if (lang in topLangs) 1.2 else -0.6
                if (seed != null) {
                    score += kindScore(seedKind, kindOf(s.track, pahadi)) + popularity(s.track, popular) + era(seed.year, s.track)
                    score += rnd.nextDouble() * 1.5
                } else {
                    score += rnd.nextDouble() * 2.5
                }
                s to score
            }
            .sortedByDescending { it.second }
            .map { it.first }
            .let(::spreadArtists)
    }

    /** The same for plain songs (mixes). */
    internal fun spreadTracks(list: List<Track>): List<Track> = spreadArtists(list.map { Suggestion(it, "") }).map { it.track }

    /** Ek hi artist ke gaane lagatar na aayein. */
    internal fun spreadArtists(list: List<Suggestion>): List<Suggestion> {
        val pending = list.toMutableList()
        val out = ArrayList<Suggestion>(list.size)
        while (pending.isNotEmpty()) {
            val last = out.lastOrNull()?.track?.artist?.let(::norm)
            val idx = pending.indexOfFirst { norm(it.track.artist) != last }.takeIf { it >= 0 } ?: 0
            out += pending.removeAt(idx)
        }
        return out
    }

    // ------------------------------------------------------------ mixes

    /** Each mix shows a different cover: move a song with an unused cover to the front. */
    private fun withDistinctCovers(mixes: List<Mix>): List<Mix> {
        val used = HashSet<String>()
        return mixes.map { m ->
            val i = m.tracks.indexOfFirst { it.artworkUrl != null && it.artworkUrl !in used }
            val tracks = if (i > 0) listOf(m.tracks[i]) + m.tracks.filterIndexed { k, _ -> k != i } else m.tracks
            tracks.firstOrNull()?.artworkUrl?.let(used::add)
            m.copy(tracks = tracks)
        }
    }

    // ------------------------------------------------------------ helpers

    /**
     * What kind of song it is, so a radio stays with it (report #63): Pahadi (JioSaavn lists those songs as Hindi;
     * their names, singers or film tell, or the catalog's Pahadi list has them), else its language.
     */
    internal fun kindOf(t: Track, pahadi: Set<String>): String = when {
        // Also a song JioSaavn lists as Garhwali, Kumaoni, Himachali… (CI walk, Oct 29: "garhwali").
        t.id in pahadi || LanguageGuess.isPahadi(t.title, "${t.artist} ${t.album} ${t.language}") -> "pahadi"
        else -> t.language.ifBlank { LanguageGuess.guess(t.title, t.artist) }
    }

    /** The catalog's Pahadi songs (downloaded only for Pahadi listeners; otherwise used once it is on the phone). */
    internal fun pahadiIds(): Set<String> =
        (if ("pahadi" in settings.current.languages) catalog.songs("pahadi") else catalog.loaded("pahadi")).mapTo(HashSet()) { it.id }

    /** The catalog's most played songs of a kind: id -> place (the file lists the most played first). */
    internal fun catalogRanks(kind: String): Map<String, Int> =
        if (kind.isBlank()) emptyMap()
        else catalog.loaded(kind).take(2_000).withIndex().associate { (i, t) -> t.id to i }

    /** Popular songs first (report #63): JioSaavn's play count, else the song's place among the catalog's most played. */
    internal fun popularity(t: Track, ranks: Map<String, Int>): Double {
        val n = t.plays
        if (n > 0) return when { n >= 50_000_000 -> 1.5; n >= 10_000_000 -> 1.1; n >= 1_000_000 -> 0.7; n >= 100_000 -> 0.3; else -> 0.0 }
        val r = ranks[t.id] ?: return 0.0
        return if (r < 200) 1.2 else if (r < 1_000) 0.6 else 0.2
    }

    /** Same kind as the seed: well ahead; another kind: behind (far behind after a Pahadi song). */
    private fun kindScore(seedKind: String, kind: String): Double = when {
        seedKind.isBlank() || kind.isBlank() -> 0.0
        kind == seedKind -> 2.5
        seedKind == "pahadi" -> -3.0
        else -> -1.2
    }

    /**
     * A song's time: an old song brings songs of its years (report #51); a new one, or one whose year isn't known,
     * brings the newest songs first (report #63).
     */
    internal fun era(seedYear: Int, t: Track): Double {
        if (t.year == 0) return 0.0
        val now = (1970 + System.currentTimeMillis() / 31_556_952_000L).toInt()
        val age = now - t.year
        val fresh = when { age <= 1 -> 1.0; age <= 3 -> 0.6; age <= 6 -> 0.2; else -> 0.0 }
        if (seedYear == 0) return fresh - if (age > 15) 0.5 else 0.0
        val d = kotlin.math.abs(t.year - seedYear)
        val near = when { d <= 4 -> 2.0; d <= 8 -> 1.0; d <= 14 -> 0.0; else -> -2.5 }
        return near + if (seedYear >= now - 6) fresh else 0.0
    }

    /**
     * The same song on JioSaavn (for a YouTube song): its year, play count and JioSaavn's "similar" list. With
     * [sameName], a song of the same name by others will do when there is no copy by the same singer (a cover on
     * someone's YouTube channel): it tells the language, but its year (0) and plays aren't the seed's.
     */
    private suspend fun saavnTwin(seed: Track, sameName: Boolean = false): Track? {
        val singer = mainSinger(seed.artist)
        val title = SameSong.cleanTitle(seed.title).ifBlank { return null }
        val found = runCatching { online.saavn.search("$title ${singer.orEmpty()}".trim(), settings.current) }.getOrDefault(emptyList())
        found.firstOrNull { SameSong.cleanTitle(it.title) == title && (singer == null || artistMatch(it.artist, singer)) }?.let { return it }
        if (!sameName) return null
        val byName = if (singer == null) found else runCatching { online.saavn.search(title, settings.current) }.getOrDefault(emptyList())
        return byName.firstOrNull { SameSong.cleanTitle(it.title) == title }?.copy(year = 0, plays = 0)
    }

    private fun reasonFor(artist: String, seed: Track?): String =
        if (seed != null && norm(artist) == norm(seed.artist)) "Similar to \"${seed.title}\""
        else "Because you listen to $artist"

    internal fun artistMatch(candidate: String, artist: String): Boolean {
        val c = norm(candidate)
        val a = norm(artist)
        if (c.isBlank() || a.isBlank()) return false
        return c == a || c.contains(a) || a.contains(c)
    }

    internal fun norm(s: String) = s.lowercase()
        .replace(Regex("""\(.*?\)|\[.*?]"""), "")
        .replace(Regex("[^\\p{L}\\p{N}]+"), " ")
        .trim()

    companion object {
        private const val KEY_LAST_SYNC = "last_sync"
        private const val MAX_SEEN = 20_000
        /** Mixes also kept as Library playlists ("✨ Daily Mix 1"…); the others are only on Home. */
        private val SYNCED = setOf("supermix", "daily1", "daily2", "daily3", "discover", "radar", "repeat")
        /** YouTube channels of music labels: their name is not a singer. */
        private val LABELS = SameSong.LABELS
        /** JioSaavn searches for a Pahadi radio. */
        private val PAHADI_SEARCHES = listOf("pahadi songs", "garhwali songs", "kumaoni songs", "himachali songs")
    }
}
