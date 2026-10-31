package com.sangeet.player.data

import android.content.Context
import com.sangeet.player.data.db.SangeetDatabase
import com.sangeet.player.data.model.Track
import com.sangeet.player.data.remote.LanguageGuess
import com.sangeet.player.data.settings.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn

/**
 * The listener's singers (report #57): the ones picked in Settings → Favourite singers, then the ones they play most.
 * Home shows them as round tiles; a tap plays all of a singer's songs in the listener's languages.
 */
class Singers(
    context: Context,
    private val settings: SettingsRepository,
    private val online: OnlineRepository,
    private val database: SangeetDatabase,
) {
    private val photos = context.getSharedPreferences("singer_photos", Context.MODE_PRIVATE)

    /** Picked singers first, then the most played (music labels' channels aren't singers). */
    suspend fun yours(limit: Int = 12): List<String> {
        val picked = settings.current.favoriteSingers
        val played = runCatching { database.listenDao().topArtists(0L, 30) }.getOrDefault(emptyList())
            .mapNotNull { a -> a.artist.split(',', '&').map { it.trim() }.firstOrNull { SameSong.mainSinger(it) != null } }
        return (picked + played).distinctBy { it.lowercase() }.take(limit)
    }

    /** Popular singers of the listener's languages, to pick from. */
    fun popular(): List<String> =
        settings.current.languages.flatMap { POPULAR[it].orEmpty() }.distinct()

    /** Singers for a search: (name, photo). */
    suspend fun search(query: String): List<Pair<String, String?>> =
        runCatching { online.saavn.searchArtists(query) }.getOrDefault(emptyList())
            .onEach { (name, photo) -> if (photo != null) remember(name, photo) }

    fun remember(name: String, photo: String) = photos.edit().putString(name.lowercase(), photo).apply()

    /** The singer's photo (JioSaavn), kept on the phone once found. */
    suspend fun photo(name: String): String? {
        photos.getString(name.lowercase(), null)?.let { return it.ifBlank { null } }
        if (!online.canGoOnline) return null
        val found = runCatching { online.saavn.searchArtists(name, 3) }.getOrNull() ?: return null
        val photo = (found.firstOrNull { it.first.equals(name, ignoreCase = true) } ?: found.firstOrNull())?.second
        photos.edit().putString(name.lowercase(), photo ?: "").apply()
        return photo
    }

    /**
     * All the singer's songs, as they come (report #89: "sab singers ke saare songs … only hindi … only original song
     * hi, remix wale mat karna"): JioSaavn's (their page, its "more songs" pages and a search, page after page; another
     * search when that is short) in the listener's languages, originals only, one copy of each song. Pahadi songs are
     * listed as Hindi on JioSaavn, so they count for Pahadi too.
     */
    fun songs(name: String): Flow<List<Track>> = flow {
        val langs = settings.current.languages.toSet()
        fun inLanguages(t: Track): Boolean {
            val lang = t.language.ifBlank { LanguageGuess.guess(t.title, t.artist) }
            return lang.isBlank() || lang in langs || ("pahadi" in langs && LanguageGuess.isPahadi(t.title, "${t.artist} ${t.language}"))
        }
        // One copy of each song (JioSaavn lists a song again on compilations); other versions of it stay. Every list
        // starts with the one before, so only the new songs are looked at.
        val seen = SameSong.Seen()
        val kept = ArrayList<Track>()
        var done = 0
        fun keep(all: List<Track>): List<Track> {
            for (t in all.subList(done.coerceAtMost(all.size), all.size)) if (original(t) && inLanguages(t) && seen.add(t)) kept += t
            done = all.size
            return kept.toList()
        }
        var all = emptyList<Track>()
        if (online.jiosaavnOn) online.saavn.artistSongs(name).catch { }.collect { all = it; emit(keep(it)) }
        if (all.size < 20) {
            val me = name.lowercase()
            val more = runCatching { online.searchAll(name) }.getOrDefault(emptyList()).filter { me in it.artist.lowercase() }
            emit(keep((all + more).distinctBy { it.id }))
        }
    }.flowOn(Dispatchers.IO)

    companion object {
        /** Not the song as sung: remixes, lofi, slowed, sped up, mashups, karaoke, instrumentals, DJ / cover versions. */
        private val NOT_ORIGINAL = Regex(
            "(?i)re-?mix|mash ?-?up|lo-?fi|slowed|reverb|sped ?up|\\b8d\\b|karaoke|instrumental|jukebox|non ?-?stop|mixtape|" +
                "bass ?boost|[(\\[][^)\\]]*\\b(dj|cover)\\b",
        )

        fun original(t: Track) = !NOT_ORIGINAL.containsMatchIn(t.title) && !NOT_ORIGINAL.containsMatchIn(t.album)

        /** Well-known singers per language, offered in the picker. */
        private val POPULAR = mapOf(
            "hindi" to listOf(
                "Arijit Singh", "Shreya Ghoshal", "Jubin Nautiyal", "Atif Aslam", "Neha Kakkar", "Sonu Nigam", "Vishal Mishra",
                "Armaan Malik", "B Praak", "Darshan Raval", "Kishore Kumar", "Lata Mangeshkar", "Mohammed Rafi", "Udit Narayan",
                "Kumar Sanu", "Alka Yagnik", "KK", "Badshah", "Sachet Tandon", "Shilpa Rao",
            ),
            "punjabi" to listOf(
                "Diljit Dosanjh", "Sidhu Moose Wala", "Karan Aujla", "AP Dhillon", "Shubh", "Guru Randhawa", "Babbu Maan",
                "Gurdas Maan", "Ammy Virk", "Jass Manak", "Amrit Maan", "Satinder Sartaaj", "Harrdy Sandhu", "Gippy Grewal",
            ),
            "haryanvi" to listOf(
                "Masoom Sharma", "Sapna Choudhary", "Raju Punjabi", "Gulzaar Chhaniwala", "Amanraj Gill", "Renuka Panwar",
                "Ajay Hooda", "KD", "Narender Bhagana", "Pranjal Dahiya", "Khasa Aala Chahar", "Diler Kharkiya",
            ),
            "bhojpuri" to listOf("Pawan Singh", "Khesari Lal Yadav", "Arvind Akela Kallu", "Shilpi Raj", "Neelkamal Singh", "Ritesh Pandey"),
            "pahadi" to listOf("Narendra Singh Negi", "Pritam Bhartwan", "Inder Arya", "Kishan Mahipal", "Gajendra Rana", "Meena Rana"),
            "tamil" to listOf("Anirudh Ravichander", "A.R. Rahman", "Sid Sriram", "Yuvan Shankar Raja", "Dhanush"),
            "telugu" to listOf("Sid Sriram", "Devi Sri Prasad", "Thaman S", "Anurag Kulkarni", "Mangli"),
            "marathi" to listOf("Ajay-Atul", "Avadhoot Gupte", "Adarsh Shinde", "Shankar Mahadevan"),
            "bengali" to listOf("Anupam Roy", "Arijit Singh", "Shreya Ghoshal", "Nachiketa Chakraborty"),
            "gujarati" to listOf("Kinjal Dave", "Geeta Rabari", "Jignesh Kaviraj", "Kirtidan Gadhvi"),
        )
    }
}
