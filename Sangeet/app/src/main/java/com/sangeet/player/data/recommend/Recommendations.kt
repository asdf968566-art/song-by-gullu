package com.sangeet.player.data.recommend

import android.content.Context
import com.sangeet.player.data.LibraryRepository
import com.sangeet.player.data.LocalMusicRepository
import com.sangeet.player.data.OnlineRepository
import com.sangeet.player.data.model.SourceType
import com.sangeet.player.data.model.Track
import com.sangeet.player.data.model.inLanguages
import com.sangeet.player.data.settings.SettingsRepository
import kotlin.math.ln
import kotlin.random.Random
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class PlayedTrack(val track: Track, val playCount: Int, val playedAt: Long)

/** Suggest kiya gaana + kyun suggest hua. */
data class Suggestion(val track: Track, val reason: String)

/** Track record se bani auto playlist. */
data class Mix(val id: String, val title: String, val subtitle: String, val tracks: List<Track>) {
    val artworkUrl: String? get() = tracks.firstNotNullOfOrNull { it.artworkUrl }
}

/**
 * Aapki history (kitni baar, kab suna) + liked songs se:
 *  - suggestions (Home + Discover feed)
 *  - mixes (Daily Mix, Artist Mix, On Repeat, ...) jo Library mein auto playlist bante hain
 *  - radio (queue khatam ho to milte-julte gaane)
 */
class RecommendationRepository(
    context: Context,
    private val library: LibraryRepository,
    private val local: LocalMusicRepository,
    private val online: OnlineRepository,
    private val settings: SettingsRepository,
) {
    private val prefs = context.getSharedPreferences("auto_playlists", Context.MODE_PRIVATE)
    private val mixLock = Mutex()
    private val syncLock = Mutex()
    private var mixesAt = 0L

    private val _mixes = MutableStateFlow<List<Mix>>(emptyList())
    val mixes: StateFlow<List<Mix>> = _mixes.asStateFlow()

    fun mix(id: String): Mix? = _mixes.value.firstOrNull { it.id == id }

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

    private fun dislikedIds(): Set<String> = taste.getStringSet("disliked_ids", emptySet()).orEmpty()
    private fun artistDislikes(artist: String): Int = taste.getInt("artist_" + norm(artist), 0)

    private class Profile(
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

    suspend fun suggestions(limit: Int = 30, exclude: Set<String> = emptySet()): List<Suggestion> {
        val p = profile()
        return rank(p, candidates(p, null), null, exclude + p.recentIds, preferNew = true).take(limit)
    }

    /** Autoplay / radio: haal hi mein (12 ghante) suna gaana apne aap dobara nahi aata. */
    suspend fun radio(seed: Track, exclude: Set<String>, limit: Int = 15): List<Track> {
        val p = profile()
        return rank(p, candidates(p, seed), seed, exclude + p.recentIds, preferNew = false).take(limit).map { it.track }
    }

    suspend fun refreshMixes(force: Boolean = false): List<Mix> = mixLock.withLock {
        val fresh = System.currentTimeMillis() - mixesAt < 30 * 60_000L
        if (!force && fresh && _mixes.value.isNotEmpty()) return@withLock _mixes.value
        val built = buildMixes()
        _mixes.value = built
        mixesAt = System.currentTimeMillis()
        built
    }

    /** Mixes ko Library ki asli playlists mein likho ("✨ Daily Mix" wagairah). */
    suspend fun syncAutoPlaylists(force: Boolean = false) = syncLock.withLock {
        val last = prefs.getLong(KEY_LAST_SYNC, 0L)
        if (!force && System.currentTimeMillis() - last < 12 * 3_600_000L) return@withLock
        val mixes = refreshMixes(force = true)
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

    private suspend fun profile(): Profile {
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

    private suspend fun candidates(p: Profile, seed: Track?): List<Suggestion> = coroutineScope {
        val out = ArrayList<Suggestion>()
        val songs = local.songs.value
        val artists = buildList {
            seed?.artist?.takeIf { it.isNotBlank() && !it.equals("Unknown artist", true) }?.let(::add)
            addAll(p.topArtists.take(5))
        }.distinctBy(::norm)

        val canOnline = online.canGoOnline
        val byArtist = if (canOnline) artists.take(4).map { a ->
            async {
                a to runCatching { online.search(a) }.getOrDefault(emptyList())
                    .flatMap { it.tracks }
                    .filter { artistMatch(it.artist, a) }
            }
        } else emptyList()
        val byGenre = if (canOnline) p.genres.take(2).map { g ->
            async { g to runCatching { online.trending(g) }.getOrDefault(emptyList()).flatMap { it.tracks } }
        } else emptyList()
        val byLanguage = if (canOnline) p.languages.take(2).map { lang ->
            async { lang to runCatching { online.byLanguage(lang) }.getOrDefault(emptyList()) }
        } else emptyList()
        val trending = if (canOnline && artists.size < 3) {
            async { runCatching { online.trending() }.getOrDefault(emptyList()).flatMap { it.tracks } }
        } else null

        // Phone ke gaane: pasandida artists ke
        artists.forEach { a ->
            songs.filter { artistMatch(it.artist, a) }.forEach { out += Suggestion(it, reasonFor(a, seed)) }
        }
        byArtist.awaitAll().forEach { (a, list) -> list.forEach { out += Suggestion(it, reasonFor(a, seed)) } }
        byGenre.awaitAll().forEach { (g, list) -> list.take(25).forEach { out += Suggestion(it, "Aapko $g pasand hai") } }
        byLanguage.awaitAll().forEach { (lang, list) ->
            list.take(40).forEach { out += Suggestion(it, "Naya ${lang.replaceFirstChar(Char::uppercase)} gaana") }
        }
        trending?.await()?.forEach { out += Suggestion(it, "Abhi trending") }
        // Thoda naya-pan: phone ke kuch random gaane
        songs.shuffled().take(20).forEach { out += Suggestion(it, "Phone se ek pick") }
        // Pehle se liked gaane bhi (radio / mix mein kaam aate hain)
        p.liked.take(30).forEach { out += Suggestion(it, "Aapka liked gaana") }

        out.distinctBy { it.track.id }
    }

    private fun rank(
        p: Profile,
        list: List<Suggestion>,
        seed: Track?,
        exclude: Set<String>,
        preferNew: Boolean,
    ): List<Suggestion> {
        // Har ghante thoda alag order, par ek ghante ke andar stable.
        val rnd = Random(System.currentTimeMillis() / 3_600_000L)
        val seedArtist = seed?.artist?.let(::norm)
        val topLangs = p.languages.take(2).toSet()
        val disliked = dislikedIds()
        val langs = settings.current.languages
        return list
            .filter { it.track.id !in exclude && it.track.id != seed?.id && it.track.id !in disliked }
            // Strict bhasha: Hindi chuna hai to sirf Hindi (phone ke gaane chhod ke)
            .filter { it.track.inLanguages(langs) }
            .map { s ->
                val a = norm(s.track.artist)
                var score = ln(1.0 + (p.artistScore[a] ?: 0.0))
                if (seedArtist != null && a == seedArtist) score += 2.0
                if (s.track.id in p.playedIds) score += if (preferNew) -1.5 else 0.4
                score -= minOf(artistDislikes(s.track.artist), 4) * 0.8
                // Pasandida bhasha = bonus, doosri bhasha = thoda kam
                val lang = s.track.language
                if (lang.isNotBlank()) score += if (lang in topLangs) 1.2 else -0.6
                if (seed != null && lang.isNotBlank() && lang == seed.language) score += 0.8
                score += rnd.nextDouble() * 1.5
                s to score
            }
            .sortedByDescending { it.second }
            .map { it.first }
            .let(::spreadArtists)
    }

    /** Ek hi artist ke gaane lagatar na aayein. */
    private fun spreadArtists(list: List<Suggestion>): List<Suggestion> {
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

    private suspend fun buildMixes(): List<Mix> {
        val p = profile()
        val ranked = rank(p, candidates(p, null), null, emptySet(), preferNew = true).map { it.track }
        val out = ArrayList<Mix>()

        if (p.isEmpty) {
            val starter = ranked.take(30)
            if (starter.size >= 5) {
                out += Mix("starter", "Shuruaat Mix", "Trending + phone ke gaane. Jitna sunoge, utna behtar banega", starter)
            }
            return out
        }

        val newOnes = ranked.filter { it.id !in p.playedIds }
        val favs = (p.played.sortedByDescending { it.playCount }.map { it.track }.take(15) + p.liked.take(10))
            .distinctBy { it.id }

        val daily = interleave(favs.shuffled().take(12), newOnes.take(18))
        if (daily.size >= 5) out += Mix("daily", "Daily Mix", "Aapke favourite + naye gaane, roz badalta hai", daily)

        p.topArtists.take(3).forEachIndexed { i, artist ->
            val mine = (p.played.map { it.track } + p.liked + local.songs.value).filter { artistMatch(it.artist, artist) }
            val more = ranked.filter { artistMatch(it.artist, artist) }
            val tracks = (mine.shuffled() + more).distinctBy { it.id }.take(30)
            if (tracks.size >= 5) out += Mix("artist${i + 1}", "$artist Mix", "$artist aur unke jaise gaane", tracks)
        }

        val discover = newOnes.take(30)
        if (discover.size >= 5) out += Mix("fresh", "Naye gaane aapke liye", "Jo aapne abhi tak nahi sune", discover)

        val onRepeat = p.played.filter { it.playCount >= 2 }.sortedByDescending { it.playCount }.map { it.track }.take(25)
        if (onRepeat.size >= 5) out += Mix("repeat", "On Repeat", "Jo aap baar-baar sunte ho", onRepeat)

        val cutoff = System.currentTimeMillis() - 14 * 86_400_000L
        val forgotten = p.played.filter { it.playCount >= 2 && it.playedAt < cutoff }
            .sortedByDescending { it.playCount }.map { it.track }.take(25)
        if (forgotten.size >= 5) out += Mix("forgotten", "Bhoole-bisre gaane", "Pehle bahut sune, ab yaad dilate hain", forgotten)

        return out
    }

    private fun interleave(a: List<Track>, b: List<Track>): List<Track> {
        val out = ArrayList<Track>(a.size + b.size)
        val ia = a.iterator()
        val ib = b.iterator()
        while (ia.hasNext() || ib.hasNext()) {
            if (ia.hasNext()) out += ia.next()
            if (ib.hasNext()) out += ib.next()
            if (ib.hasNext()) out += ib.next()
        }
        return out.distinctBy { it.id }
    }

    // ------------------------------------------------------------ helpers

    private fun reasonFor(artist: String, seed: Track?): String =
        if (seed != null && norm(artist) == norm(seed.artist)) "\"${seed.title}\" jaisa"
        else "Kyunki aap $artist sunte ho"

    private fun artistMatch(candidate: String, artist: String): Boolean {
        val c = norm(candidate)
        val a = norm(artist)
        if (c.isBlank() || a.isBlank()) return false
        return c == a || c.contains(a) || a.contains(c)
    }

    private fun norm(s: String) = s.lowercase()
        .replace(Regex("""\(.*?\)|\[.*?]"""), "")
        .replace(Regex("[^\\p{L}\\p{N}]+"), " ")
        .trim()

    companion object {
        private const val KEY_LAST_SYNC = "last_sync"
    }
}
