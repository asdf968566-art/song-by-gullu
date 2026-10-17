package com.sangeet.player.data

import android.content.Context
import com.sangeet.player.data.db.SangeetDatabase
import com.sangeet.player.data.model.Track
import com.sangeet.player.data.remote.LanguageGuess
import com.sangeet.player.data.settings.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

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
     * All the singer's songs (their JioSaavn page, then a search) in the listener's languages. Pahadi songs are
     * listed as Hindi on JioSaavn, so they count for Pahadi too.
     */
    suspend fun songs(name: String): List<Track> = withContext(Dispatchers.IO) {
        val langs = settings.current.languages.toSet()
        val me = name.lowercase()
        val page = if (online.jiosaavnOn) runCatching { online.saavn.artistSongs(name, 300) }.getOrDefault(emptyList()) else emptyList()
        val more = if (page.size < 20) runCatching { online.searchAll(name) }.getOrDefault(emptyList()) else emptyList()
        (page + more.filter { me in it.artist.lowercase() })
            .distinctBy { it.id }
            .filter { t ->
                val lang = t.language.ifBlank { LanguageGuess.guess(t.title, t.artist) }
                lang.isBlank() || lang in langs || ("pahadi" in langs && LanguageGuess.isPahadi(t.title, t.artist))
            }
            .let { list ->
                // One copy of each song (JioSaavn lists a song again on compilations); other versions stay.
                val kept = ArrayList<SameSong.Key>()
                list.filter { t ->
                    val k = SameSong.key(t)
                    (kept.none { it.sameAs(k) }).also { if (it) kept += k }
                }
            }
    }

    companion object {
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
