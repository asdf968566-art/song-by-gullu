package com.sangeet.player.data.recommend

import com.sangeet.player.data.LibraryRepository
import com.sangeet.player.data.Moods
import com.sangeet.player.data.OnlineRepository
import com.sangeet.player.data.SameSong
import com.sangeet.player.data.db.ListenDao
import com.sangeet.player.data.db.ListenStat
import com.sangeet.player.data.model.SourceType
import com.sangeet.player.data.model.Track
import com.sangeet.player.data.remote.LanguageGuess
import com.sangeet.player.data.settings.SettingsRepository
import java.io.File
import java.util.Calendar
import java.util.concurrent.ConcurrentHashMap
import kotlin.random.Random
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * "Made for you" like Spotify's and YouTube Music's (owner, Oct 27: "playlist banane me kaphi kachha h, user ke use
 * ke hisab se trend bna jaise Spotify banata h … ya yt music ki tarah"). Every mix comes from what the listener plays,
 * likes, skips and when, and keeps its songs for its period, so a refresh doesn't reshuffle Discover Weekly:
 *  - My Supermix (YouTube Music): the Daily Mixes and new finds in one long mix. Every day.
 *  - Daily Mix 1–6 (Spotify): your singers, 3 to a mix, per kind (Hindi, Punjabi, Pahadi…); your songs of theirs take
 *    turns with their other songs and JioSaavn's "similar" ones. Every day.
 *  - Discover Weekly: 30 songs you've never played, mostly by singers you don't play yet. Every Monday.
 *  - Release Radar: this year's songs by your singers. Every Friday.
 *  - Daylist ("Your evening mix"): what you play at this time of day, and songs like it. Each part of the day.
 *  - On Repeat (30 days), Repeat Rewind (old favourites), Your Top Songs (this year).
 *  - Your top mixes: your decades (90s Mix…), moods (Romantic Mix…) from your singers and languages, singer mixes
 *    with similar singers, and language mixes.
 * Songs skipped again and again (played < 30 s) stay out.
 */
internal class MadeForYou(
    private val r: RecommendationRepository,
    private val library: LibraryRepository,
    private val online: OnlineRepository,
    private val settings: SettingsRepository,
    private val catalog: CatalogPool,
    private val listens: ListenDao,
    private val file: File,
) {
    private enum class Period { DAY, MONDAY, FRIDAY, PART }

    private enum class Part(val label: String, val from: Int, val to: Int) {
        MORNING("morning", 5, 11), AFTERNOON("afternoon", 12, 16), EVENING("evening", 17, 20), NIGHT("night", 21, 4);

        companion object {
            fun of(hour: Int) = entries.firstOrNull { p -> if (p.from <= p.to) hour in p.from..p.to else hour >= p.from || hour <= p.to } ?: NIGHT
        }
    }

    @Serializable
    private data class SavedMix(val id: String, val title: String, val subtitle: String, val top: Boolean = false, val tracks: List<Track>)

    @Serializable
    private data class SavedGroup(val key: String, val mixes: List<SavedMix>, val at: Long = System.currentTimeMillis())

    @Serializable
    private data class Saved(val groups: Map<String, SavedGroup> = emptyMap())

    /** What a build knows about the listener. */
    private class Taste(
        val p: RecommendationRepository.Profile,
        /** Songs played in the last 30 days. */
        val month: List<ListenStat>,
        /** Songs mostly skipped (played < 30 s) lately: kept out of every mix. */
        val skipped: Set<String>,
        /** Your songs, the ones you play most (and lately, and like) first. */
        val favs: List<Track>,
        /** Your singers with how much you play them, most first (picked singers too). */
        val singers: List<Pair<String, Double>>,
        val pahadi: Set<String>,
        val known: Set<String>,
        val disliked: Set<String>,
        val seed: Int,
    ) {
        val weight: Map<String, Double> = singers.associate { it.first.lowercase() to it.second }
    }

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = false }

    /** One build's JioSaavn searches and "similar" lists, shared between the mixes that need the same one. */
    private val searches = ConcurrentHashMap<String, Deferred<List<Track>>>()
    private val bg = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** The mixes for now: kept ones whose period hasn't ended, the rest built again. [force]: Home's refresh. */
    suspend fun build(force: Boolean): List<Mix> = withContext(Dispatchers.Default) {
        val started = System.currentTimeMillis()
        searches.clear()
        val saved = load()
        val now = Calendar.getInstance()
        val keys = mapOf(
            Period.DAY to dayKey(now),
            Period.MONDAY to weekKey(now, Calendar.MONDAY),
            Period.FRIDAY to weekKey(now, Calendar.FRIDAY),
            Period.PART to dayKey(now) + "-" + Part.of(now.get(Calendar.HOUR_OF_DAY)).name,
        )
        val t = taste(now)
        fun every(period: Period, make: suspend () -> List<Mix>) = period to make
        val groups = linkedMapOf(
            "daily" to every(Period.DAY) { dailyMixes(t) },
            "discover" to every(Period.MONDAY) { listOfNotNull(discoverWeekly(t)) },
            "radar" to every(Period.FRIDAY) { listOfNotNull(releaseRadar(t)) },
            "daylist" to every(Period.PART) { listOfNotNull(daylist(t, Part.of(now.get(Calendar.HOUR_OF_DAY)))) },
            "repeat" to every(Period.DAY) { listOfNotNull(onRepeat(t)) },
            "rewind" to every(Period.MONDAY) { listOfNotNull(repeatRewind(t)) },
            "topyear" to every(Period.DAY) { listOfNotNull(topSongs(t, now)) },
            "era" to every(Period.MONDAY) { eraMixes(t, now) },
            "mood" to every(Period.MONDAY) { moodMixes(t, now) },
            "singers" to every(Period.DAY) { singerMixes(t) },
            "languages" to every(Period.DAY) { languageMixes(t) },
        )
        val built = HashMap<String, SavedGroup>()
        val rebuilt = ArrayList<String>()
        coroutineScope {
            groups.map { (name, def) ->
                val (period, make) = def
                val key = keys.getValue(period)
                val old = saved.groups[name]
                // A mix stays for its period; a week's mix even on refresh (like Spotify's). One that came out
                // empty (too little listening yet) is tried again on refresh, or after 6 hours.
                val keep = old != null && old.key == key &&
                    (!force || (old.mixes.isNotEmpty() && period != Period.DAY && period != Period.PART)) &&
                    (old.mixes.isNotEmpty() || System.currentTimeMillis() - old.at < 6 * 3_600_000L)
                async {
                    if (keep && old != null) {
                        name to old
                    } else {
                        val mixes = withTimeoutOrNull(30_000) { runCatching { make() }.getOrNull() }
                        if (mixes == null) {
                            name to (old ?: SavedGroup(key, emptyList())) // slow network: keep what there was
                        } else {
                            synchronized(rebuilt) { rebuilt += name }
                            name to SavedGroup(key, mixes.map { SavedMix(it.id, it.title, it.subtitle, it.top, it.tracks) })
                        }
                    }
                }
            }.awaitAll().forEach { (name, group) -> built[name] = group }
        }
        // My Supermix (YouTube Music): the Daily Mixes taking turns, with Discover Weekly's new songs between them.
        val superKey = keys.getValue(Period.DAY)
        val superOld = saved.groups["supermix"]
        built["supermix"] = if (superOld != null && superOld.key == superKey && !force && superOld.mixes.isNotEmpty()) superOld
        else SavedGroup(superKey, listOfNotNull(supermix(t, built)).map { SavedMix(it.id, it.title, it.subtitle, it.top, it.tracks) })
        if (t.p.isEmpty) {
            built["starter"] = SavedGroup(superKey, listOfNotNull(starter(t)).map { SavedMix(it.id, it.title, it.subtitle, it.top, it.tracks) })
        }
        save(Saved(built))
        val order = listOf("starter", "supermix", "daily", "discover", "radar", "daylist", "repeat", "rewind", "topyear", "era", "mood", "singers", "languages")
        val mixes = order.flatMap { built[it]?.mixes.orEmpty() }
            .map { Mix(it.id, it.title, it.subtitle, it.tracks, it.top) }
            .filter { it.tracks.isNotEmpty() }
        android.util.Log.i(
            "Sangeet",
            "mixes: ${mixes.size} (${mixes.joinToString(" ") { "${it.id}:${it.tracks.size}" }}); built ${rebuilt.sorted()} in ${System.currentTimeMillis() - started} ms",
        )
        mixes
    }

    // ------------------------------------------------------------ the listener

    private suspend fun taste(now: Calendar): Taste {
        val p = r.profile()
        val nowMs = now.timeInMillis
        val month = runCatching { listens.stats(nowMs - 30 * DAY_MS, 300) }.getOrDefault(emptyList())
        val lately = runCatching { listens.stats(nowMs - 90 * DAY_MS, 500) }.getOrDefault(emptyList())
        val skipped = lately.filter { it.skips >= 2 && it.skips * 3 >= it.listens * 2 }.mapTo(HashSet()) { it.track.id }
        val disliked = r.dislikedIds()
        val monthPlays = month.associate { it.track.id to it.listens }
        val likedIds = p.liked.mapTo(HashSet()) { it.id }

        // Your songs: how often (more weight when lately), liked ones up, skipped ones out.
        val score = HashMap<String, Double>()
        val tracks = HashMap<String, Track>()
        p.played.forEach { pt ->
            val ageDays = (nowMs - pt.playedAt) / DAY_MS.toDouble()
            score[pt.track.id] = pt.playCount * (0.5 + 1.0 / (1.0 + ageDays / 14.0)) + (monthPlays[pt.track.id] ?: 0) * 0.5
            tracks[pt.track.id] = pt.track
        }
        p.liked.forEach { t ->
            score[t.id] = (score[t.id] ?: 0.0) + 3.0
            tracks[t.id] = t
        }
        val favs = score.entries
            .filter { it.key !in skipped && it.key !in disliked && tracks[it.key]?.let(::isRadio) == false }
            .sortedByDescending { it.value }
            .mapNotNull { tracks[it.key] }

        // Your singers: from your songs (their first real singer, not the music director), and the ones picked in Settings.
        val singer = LinkedHashMap<String, Pair<String, Double>>()
        favs.forEachIndexed { i, t ->
            val name = SameSong.singerOf(t.artist) ?: return@forEachIndexed
            val w = (score[t.id] ?: 0.0) + if (t.id in likedIds) 1.0 else 0.0
            val k = name.lowercase()
            singer[k] = name to ((singer[k]?.second ?: 0.0) + w + 0.01 * (favs.size - i) / favs.size.coerceAtLeast(1))
        }
        settings.current.favoriteSingers.forEach { name ->
            val k = name.lowercase()
            singer[k] = (singer[k]?.first ?: name) to ((singer[k]?.second ?: 0.0) + 8.0)
        }
        val singers = singer.values.sortedByDescending { it.second }.take(40)
        return Taste(
            p = p,
            month = month,
            skipped = skipped,
            favs = favs,
            singers = singers,
            pahadi = r.pahadiIds(),
            known = p.playedIds + likedIds,
            disliked = disliked,
            seed = dayKey(now).hashCode(),
        )
    }

    private fun isRadio(t: Track) = t.source == SourceType.URL && t.sourceId.startsWith("radio-")

    private fun kindOf(t: Track, taste: Taste) = r.kindOf(t, taste.pahadi)

    /** A singer's kind: what most of your songs of theirs are, else a guess from the name, else your first language. */
    private fun singerKind(name: String, t: Taste): String {
        val mine = t.favs.filter { r.artistMatch(it.artist, name) }.map { kindOf(it, t) }.filter { it.isNotBlank() }
        return mine.groupingBy { it }.eachCount().maxByOrNull { it.value }?.key
            ?: LanguageGuess.guess("", name).ifBlank { t.p.languages.firstOrNull().orEmpty() }
    }

    /** The kinds you listen to (a tenth of your songs or more). */
    private fun yourKinds(t: Taste): Set<String> {
        val kinds = t.favs.take(150).map { kindOf(it, t) }.filter { it.isNotBlank() }
        if (kinds.isEmpty()) return t.p.languages.take(2).toSet()
        return kinds.groupingBy { it }.eachCount().filter { it.value * 10 >= kinds.size }.keys + t.p.languages.take(1)
    }

    // ------------------------------------------------------------ Spotify's "Made for you"

    /** Is most of what you play by this singer from before 2000? (Kishore Kumar and Arijit Singh get their own mixes.) */
    private fun isOld(name: String, t: Taste): Boolean {
        val years = t.favs.filter { it.year > 0 && r.artistMatch(it.artist, name) }.map { it.year }.sorted()
        return years.isNotEmpty() && years[years.size / 2] < 2000
    }

    /** Daily Mix 1–6: your singers, 3 to a mix, by kind and old / new, strongest first (at most 2 mixes per group). */
    private suspend fun dailyMixes(t: Taste): List<Mix> = coroutineScope {
        if (t.favs.size < 3) return@coroutineScope emptyList()
        val byKind = LinkedHashMap<Pair<String, Boolean>, MutableList<String>>()
        t.singers.forEach { (name, _) -> byKind.getOrPut(singerKind(name, t) to isOld(name, t)) { ArrayList() } += name }
        val kindWeight = byKind.mapValues { (_, names) -> names.sumOf { t.weight[it.lowercase()] ?: 0.0 } }
        val groups = byKind.entries.sortedByDescending { kindWeight[it.key] ?: 0.0 }
            .flatMap { (key, names) -> names.take(6).chunked(3).take(2).map { key to it } }
            .filter { (_, names) -> t.favs.count { f -> names.any { r.artistMatch(f.artist, it) } } >= 2 }
            .take(6)
        groups.mapIndexed { i, (key, names) -> async { dailyMix(i + 1, key.first, key.second, names, t) } }.awaitAll()
            .filterNotNull()
            .mapIndexed { i, m -> m.copy(id = "daily${i + 1}", title = "Daily Mix ${i + 1}") }
    }

    private suspend fun dailyMix(n: Int, kind: String, old: Boolean, names: List<String>, t: Taste): Mix? = coroutineScope {
        val rnd = Random(t.seed + n)
        val mine = t.favs.filter { f -> names.any { r.artistMatch(f.artist, it) } }.take(16).shuffled(rnd)
        val theirs = names.map { a -> async { singerSongs(a) } }
        val similar = mine.filter { it.source == SourceType.JIOSAAVN }.take(2).map { s -> async { similarTo(s) } }
        val pool = (theirs.awaitAll().flatten() + similar.awaitAll().flatten() +
            catalogOf(kind).filter { c -> names.any { r.artistMatch(c.artist, it) } })
            .filter { fresh(it, t) && (kind.isBlank() || kindOf(it, t) == kind) && (it.year == 0 || (it.year < 2000) == old) }
        val newSongs = ranked(pool, t, kind, rnd, boost = names)
        val tracks = mix(mine, newSongs, 30)
        if (tracks.size < 10) return@coroutineScope null
        Mix("daily$n", "Daily Mix $n", names.joinToString(", ") + " and more", tracks)
    }

    /** Discover Weekly: 30 songs you've never played, mostly by singers new to you (at most 1 each of yours). */
    private suspend fun discoverWeekly(t: Taste): Mix? = coroutineScope {
        if (t.p.isEmpty) return@coroutineScope null
        val reco = t.favs.filter { it.source == SourceType.JIOSAAVN }.take(6).map { s -> async { similarTo(s) } }
        val pool = r.candidates(t.p, null) + reco.awaitAll().flatten().map { Suggestion(it, "") }
        val ranked = r.rank(t.p, pool, null, t.known + t.skipped + t.disliked, preferNew = true).map { it.track }
            .filter { fresh(it, t) }
        val picked = ArrayList<Track>()
        val perSinger = HashMap<String, Int>()
        // New singers first, then one song each from singers you already play.
        for (newOnly in listOf(true, false)) {
            for (s in ranked) {
                if (picked.size >= 30) break
                val singer = SameSong.singerOf(s.artist)?.lowercase() ?: s.artist.lowercase()
                val yours = singer in t.weight
                if (newOnly == yours) continue
                val cap = if (yours) 1 else 2
                if ((perSinger[singer] ?: 0) >= cap || picked.any { SameSong.same(it, s) }) continue
                perSinger[singer] = (perSinger[singer] ?: 0) + 1
                picked += s
            }
        }
        if (picked.size < 10) return@coroutineScope null
        Mix("discover", "Discover Weekly", "New songs for you, picked every Monday", r.spreadTracks(picked))
    }

    /** Release Radar: this year's songs by your singers (picked singers first), newest first, every Friday. */
    private suspend fun releaseRadar(t: Taste): Mix? = coroutineScope {
        if (!online.canGoOnline || !settings.current.jiosaavnEnabled) return@coroutineScope null
        // A new song by a singer you play from before 2000 is a re-release ("Kishore Kumar Hits"), not new.
        val names = (settings.current.favoriteSingers + t.singers.map { it.first }).distinctBy { it.lowercase() }
            .filterNot { isOld(it, t) }.take(12)
        if (names.isEmpty()) return@coroutineScope null
        val now = Calendar.getInstance()
        val since = now.get(Calendar.YEAR) - if (now.get(Calendar.MONTH) < 3) 1 else 0
        val lists = names.map { a ->
            async { withTimeoutOrNull(15_000) { runCatching { online.saavn.newSongsBy(a, since, 6) }.getOrNull() }.orEmpty() }
        }.awaitAll()
        val out = ArrayList<Track>()
        // Take turns between singers, newest first for each.
        for (i in 0 until (lists.maxOfOrNull { it.size } ?: 0)) {
            lists.mapNotNull { it.getOrNull(i) }.forEach { s ->
                if (s.id !in t.skipped && s.id !in t.disliked && out.none { SameSong.same(it, s) }) out += s
            }
        }
        if (out.size < 5) return@coroutineScope null
        Mix("radar", "Release Radar", "New songs from your singers, every Friday", out.take(30))
    }

    /** Daylist: what you play at this time of day, and songs like it; else a mood for the time of day. */
    private suspend fun daylist(t: Taste, part: Part): Mix? {
        val since = System.currentTimeMillis() - 60 * DAY_MS
        val at = runCatching { listens.statsAt(since, part.from, part.to, 60) }.getOrDefault(emptyList())
            .filter { it.ms >= 60_000 && it.track.id !in t.skipped && it.track.id !in t.disliked }
            .map { it.track.toTrack() }
            .filterNot(::isRadio)
        val name = part.label.replaceFirstChar(Char::uppercase)
        if (at.size >= 4) {
            val rnd = Random(t.seed + part.ordinal)
            val like = runCatching { r.radio(at.first(), at.mapTo(HashSet()) { it.id }, 20) }.getOrDefault(emptyList())
            val tracks = mix(at.take(15).shuffled(rnd), like.filter { fresh(it, t) }, 30)
            if (tracks.size >= 8) return Mix("daylist", "Your $name Mix", "What you play in the ${part.label}, and more like it", tracks)
        }
        val mood = Moods.all.first {
            it.name == when (part) {
                Part.MORNING -> "Happy"; Part.AFTERNOON -> "Road Trip"; Part.EVENING -> "Romantic"; Part.NIGHT -> "Chill"
            }
        }
        val tracks = moodSongs(mood, t, Random(t.seed + part.ordinal))
        if (tracks.size < 10) return null
        return Mix("daylist", "Your $name Mix", "${mood.name} songs for your ${part.label}", tracks)
    }

    /** On Repeat: the songs you've played most in the last 30 days. */
    private fun onRepeat(t: Taste): Mix? {
        val month = t.month.filter { it.listens >= 2 && it.skips * 2 < it.listens }
            .sortedByDescending { it.listens * 1_000_000L + it.ms / 1000 }
            .map { it.track.toTrack() }
        val tracks = month.ifEmpty {
            t.p.played.filter { it.playCount >= 2 && it.track.id !in t.skipped }.sortedByDescending { it.playCount }.map { it.track }
        }.filterNot(::isRadio).take(30)
        if (tracks.size < 5) return null
        return Mix("repeat", "On Repeat", "Songs you can't stop playing this month", tracks)
    }

    /** Repeat Rewind: songs you played a lot before, but not in the last 30 days. */
    private fun repeatRewind(t: Taste): Mix? {
        val cutoff = System.currentTimeMillis() - 30 * DAY_MS
        val tracks = t.p.played.filter { it.playCount >= 3 && it.playedAt < cutoff && it.track.id !in t.skipped }
            .sortedByDescending { it.playCount }.map { it.track }.filterNot(::isRadio).take(30)
        if (tracks.size < 5) return null
        return Mix("rewind", "Repeat Rewind", "Old favourites you haven't played lately", tracks)
    }

    /** Your Top Songs of this year (from Jan 1). */
    private suspend fun topSongs(t: Taste, now: Calendar): Mix? {
        val start = (now.clone() as Calendar).apply {
            set(Calendar.DAY_OF_YEAR, 1); set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0)
        }.timeInMillis
        val year = now.get(Calendar.YEAR)
        val tracks = runCatching { listens.stats(start, 60) }.getOrDefault(emptyList())
            .filter { it.listens >= 2 && it.track.id !in t.skipped }.map { it.track.toTrack() }.filterNot(::isRadio).take(50)
        if (tracks.size < 15) return null
        return Mix("topyear", "Your Top Songs $year", "Your most played songs this year", tracks)
    }

    /** My Supermix: the Daily Mixes taking turns, with Discover Weekly's new songs in between. */
    private fun supermix(t: Taste, built: Map<String, SavedGroup>): Mix? {
        val daily = built["daily"]?.mixes.orEmpty().map { it.tracks }
        val discover = built["discover"]?.mixes?.firstOrNull()?.tracks.orEmpty()
        val lists = daily + listOf(discover)
        val out = ArrayList<Track>()
        for (i in 0 until (lists.maxOfOrNull { it.size } ?: 0)) {
            lists.mapNotNull { it.getOrNull(i) }.forEach { s -> if (out.none { SameSong.same(it, s) }) out += s }
        }
        val tracks = out.ifEmpty { t.favs.take(30) }.take(60)
        if (tracks.size < 10) return null
        return Mix("supermix", "My Supermix", "All your favourites and new finds in one mix", tracks)
    }

    /** A new listener: popular songs in their languages and songs on the phone. */
    private suspend fun starter(t: Taste): Mix? {
        val ranked = r.rank(t.p, r.candidates(t.p, null), null, emptySet(), preferNew = true).map { it.track }.take(30)
        if (ranked.size < 5) return null
        return Mix("starter", "Starter Mix", "Trending songs and music on your phone. Gets better the more you listen", ranked)
    }

    // ------------------------------------------------------------ "Your top mixes" (YouTube Music style)

    /** Your decades: "Your 90s Mix" for the decades of your songs (4 or more), from the catalog's songs of those years. */
    private fun eraMixes(t: Taste, now: Calendar): List<Mix> {
        val langs = t.p.languages.take(2)
        val pool = langs.flatMap { catalog.songs(it) }
        val years = HashMap<String, Int>()
        pool.forEach { if (it.year > 0) years[it.id] = it.year }
        fun yearOf(s: Track) = s.year.takeIf { it > 0 } ?: years[s.id] ?: 0
        val thisDecade = now.get(Calendar.YEAR) / 10 * 10
        val decades = t.favs.take(200).mapNotNull { s -> yearOf(s).takeIf { it in 1950 until thisDecade }?.let { it / 10 * 10 } }
            .groupingBy { it }.eachCount().filter { it.value >= 4 }.entries.sortedByDescending { it.value }.take(2)
        return decades.mapNotNull { (decade, _) ->
            val rnd = Random(t.seed + decade)
            val mine = t.favs.filter { yearOf(it) / 10 * 10 == decade }.take(12).shuffled(rnd)
            val theirs = pool.filter { it.year / 10 * 10 == decade && fresh(it, t) }
            val tracks = mix(mine, ranked(theirs, t, "", rnd), 30)
            val label = if (decade >= 2000) "${decade}s" else "${decade % 100}s"
            if (tracks.size < 10) null
            else Mix("era$decade", "Your $label Mix", "Songs from the $label you love, and more from those years", tracks, top = true)
        }
    }

    /** Mood mixes from your singers and languages: moods your playlists' names talk about first, then a weekly turn. */
    private suspend fun moodMixes(t: Taste, now: Calendar): List<Mix> = coroutineScope {
        val names = runCatching { library.playlistNames() }.getOrDefault(emptyList()).joinToString(" ")
        val yours = Moods.all.filter { m -> m.words.any { w -> Regex("\\b${Regex.escape(w)}\\b", RegexOption.IGNORE_CASE).containsMatchIn(names) } }
        val turn = listOf("Romantic", "Party", "Sad", "Chill", "Happy", "Workout", "Road Trip", "Devotional")
        val week = now.get(Calendar.WEEK_OF_YEAR)
        val rotation = (turn.drop(week % turn.size) + turn.take(week % turn.size)).mapNotNull { n -> Moods.all.firstOrNull { it.name == n } }
        val moods = (yours + rotation).distinctBy { it.name }.take(3)
        moods.map { mood -> async { mood to moodSongs(mood, t, Random(t.seed + mood.name.hashCode())) } }.awaitAll()
            .filter { it.second.size >= 10 }
            .map { (mood, tracks) -> Mix("mood_${mood.name.lowercase().replace(' ', '_')}", "${mood.name} Mix", "${mood.name} songs from your languages and singers", tracks, top = true) }
    }

    /** "Arijit Singh Mix": the singer's songs taking turns with similar singers' songs, for your top 3 singers. */
    private suspend fun singerMixes(t: Taste): List<Mix> = coroutineScope {
        t.singers.take(3).mapIndexed { i, (name, _) ->
            async {
                val rnd = Random(t.seed + name.hashCode())
                val kind = singerKind(name, t)
                val mine = t.favs.filter { r.artistMatch(it.artist, name) }.take(10)
                val theirs = singerSongs(name).filter { fresh(it, t) }
                val seeds = (mine + theirs).filter { it.source == SourceType.JIOSAAVN }.take(2)
                val similar = seeds.map { s -> async { similarTo(s) } }.awaitAll().flatten()
                    .filter { fresh(it, t) && !r.artistMatch(it.artist, name) && (kind.isBlank() || kindOf(it, t) == kind) }
                val own = (mine.shuffled(rnd) + ranked(theirs, t, kind, rnd)).distinctBy { it.id }
                // Mostly the singer, like YouTube Music's: 2 of theirs, then 1 by a similar singer.
                val tracks = mix(own, ranked(similar, t, kind, rnd), 30, mineEach = 2, each = 1)
                if (tracks.size < 10) null
                else Mix("singer${i + 1}", "$name Mix", "$name and similar singers", tracks, top = true)
            }
        }.awaitAll().filterNotNull()
    }

    /** "Hindi Mix": the catalog's popular songs of each of your languages, your singers' songs first. */
    private fun languageMixes(t: Taste): List<Mix> {
        val favs = t.singers.take(10).map { r.norm(it.first) }
        return t.p.languages.take(3).mapNotNull { lang ->
            val picks = catalog.sample(listOf(lang), 30, t.known + t.skipped) { a -> favs.any { f -> r.norm(a).contains(f) } }
            val name = lang.replaceFirstChar(Char::uppercase)
            if (picks.size < 10) null else Mix("lang_$lang", "$name Mix", "Popular $name songs picked for you", picks, top = true)
        }
    }

    // ------------------------------------------------------------ helpers

    /** Mood songs from JioSaavn's mood playlists, your singers and kinds first, then the most played. */
    private suspend fun moodSongs(mood: com.sangeet.player.data.Mood, t: Taste, rnd: Random): List<Track> {
        val langs = t.p.languages.take(2).ifEmpty { settings.current.languages.take(2) }
        val found = withTimeoutOrNull(20_000) { runCatching { online.moodTracks(mood, langs, limit = 90) }.getOrNull() }.orEmpty()
        val kinds = yourKinds(t)
        val ranked = found.filter { it.id !in t.skipped && it.id !in t.disliked }
            .map { s ->
                var score = weightOf(s, t).coerceAtMost(10.0) / 3.0
                if (kindOf(s, t) in kinds) score += 1.0
                score += r.popularity(s, emptyMap()) + rnd.nextDouble() * 1.5
                s to score
            }
            .sortedByDescending { it.second }.map { it.first }
        return r.spreadTracks(ranked.distinctBy { SameSong.cleanTitle(it.title) }.take(30))
    }

    /** New songs ranked for you: your singers, your kind, popular and recent, with a little chance. */
    private fun ranked(list: List<Track>, t: Taste, kind: String, rnd: Random, boost: List<String> = emptyList()): List<Track> {
        val ranks = r.catalogRanks(kind)
        return list.distinctBy { it.id }
            .filterNot { JUNK.containsMatchIn(it.title) }
            .map { s ->
                var score = r.popularity(s, ranks) + r.era(0, s) + rnd.nextDouble() * 1.5
                score += weightOf(s, t).coerceAtMost(10.0) / 5.0
                if (boost.any { r.artistMatch(s.artist, it) }) score += 0.8
                s to score
            }
            .sortedByDescending { it.second }
            .map { it.first }
            .let { songs ->
                val kept = ArrayList<SameSong.Key>()
                songs.filter { s -> SameSong.key(s).let { k -> kept.none { it.sameAs(k) }.also { if (it) kept += k } } }
            }
    }

    /** Your songs taking turns with new ones (1 of yours, 2 new), no song twice. */
    private fun mix(mine: List<Track>, fresh: List<Track>, size: Int, mineEach: Int = 1, each: Int = 2): List<Track> {
        val out = ArrayList<Track>()
        val a = mine.iterator()
        val b = fresh.iterator()
        fun add(s: Track) { if (out.none { SameSong.same(it, s) }) out += s }
        while (out.size < size && (a.hasNext() || b.hasNext())) {
            repeat(mineEach) { if (a.hasNext()) add(a.next()) }
            repeat(each) { if (b.hasNext()) add(b.next()) }
        }
        return r.spreadTracks(out.take(size))
    }

    /** How much you play the song's singer. */
    private fun weightOf(s: Track, t: Taste): Double = SameSong.singerOf(s.artist)?.lowercase()?.let { t.weight[it] } ?: 0.0

    /** Not played before, not skipped, not disliked, not live radio. */
    private fun fresh(s: Track, t: Taste) = s.id !in t.known && s.id !in t.skipped && s.id !in t.disliked && !isRadio(s)

    private fun catalogOf(kind: String): List<Track> = if (catalog.has(kind)) catalog.songs(kind) else emptyList()

    /** A singer's songs from a JioSaavn search (shared within a build). */
    private suspend fun singerSongs(name: String): List<Track> {
        if (!online.canGoOnline || !settings.current.jiosaavnEnabled) return emptyList()
        val job = searches.getOrPut("s:" + name.lowercase()) {
            bg.async { runCatching { online.saavn.searchPage(name, 1) }.getOrDefault(emptyList()).filter { r.artistMatch(it.artist, name) } }
        }
        return withTimeoutOrNull(15_000) { job.await() }.orEmpty()
    }

    /** JioSaavn's "listeners also play" for a song (shared within a build). */
    private suspend fun similarTo(s: Track): List<Track> {
        if (!online.canGoOnline || !settings.current.jiosaavnEnabled || s.source != SourceType.JIOSAAVN) return emptyList()
        val job = searches.getOrPut("r:" + s.sourceId) { bg.async { runCatching { online.saavn.similar(s.sourceId) }.getOrDefault(emptyList()) } }
        return withTimeoutOrNull(15_000) { job.await() }.orEmpty()
    }

    private fun load(): Saved = runCatching { json.decodeFromString(Saved.serializer(), file.readText()) }.getOrDefault(Saved())

    private fun save(saved: Saved) {
        runCatching {
            val tmp = File(file.parentFile, file.name + ".tmp")
            tmp.writeText(json.encodeToString(Saved.serializer(), saved))
            tmp.renameTo(file)
        }
    }

    private fun dayKey(c: Calendar) = "%04d-%03d".format(c.get(Calendar.YEAR), c.get(Calendar.DAY_OF_YEAR))

    /** The day this week's mix began: the last [weekday] (today if it is that day). */
    private fun weekKey(c: Calendar, weekday: Int): String {
        val d = c.clone() as Calendar
        while (d.get(Calendar.DAY_OF_WEEK) != weekday) d.add(Calendar.DAY_OF_YEAR, -1)
        return dayKey(d)
    }

    private companion object {
        const val DAY_MS = 86_400_000L

        /** Remixes, mashups, lofi… stay out of the new songs a mix adds (your own ones stay). Same as the web's JUNK. */
        val JUNK = Regex("(?i)mash ?up|lo-?fi|remix|slowed|reverb|mixtape|karaoke|instrumental|jukebox|non ?stop|\\b8d\\b")
    }
}
