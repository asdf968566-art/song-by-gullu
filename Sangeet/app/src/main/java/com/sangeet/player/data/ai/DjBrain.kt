package com.sangeet.player.data.ai

import com.sangeet.player.data.Categories
import com.sangeet.player.data.Mood
import com.sangeet.player.data.Moods
import com.sangeet.player.data.remote.LanguageGuess

/**
 * What the listener asked the built-in DJ for, understood from English, Hindi or Hinglish
 * ("purane sad punjabi gaane, bina remix", "Kesariya jaise gaane", "Aashiqui 2 ke gaane"), and kept between
 * messages so a follow-up ("aur", "sirf Arijit", "naye wale", "lofi hata do") changes the last mix
 * instead of starting over, like talking to a person.
 */
data class DjIntent(
    val languages: List<String> = emptyList(),
    val mood: Mood? = null,
    /** "90s", "80s", "2000s", "old", "new" or "". */
    val era: String = "",
    val artists: List<String> = emptyList(),
    /** "songs like Kesariya" -> "kesariya". */
    val like: String = "",
    /** "Aashiqui 2 ke gaane" -> "aashiqui 2" (a film / album name). */
    val movie: String = "",
    /** Words a song must not have ("remix", "lofi", a singer). */
    val without: List<String> = emptyList(),
    /** "kuch bhi", "surprise me", "mere liye": the listener's own taste. */
    val forMe: Boolean = false,
    /** Anything else worth searching for ("wedding", "rain"). */
    val words: List<String> = emptyList(),
    val count: Int = 50,
    /** Asked for more of the same: the next songs. */
    val page: Int = 0,
)

object DjBrain {
    /** Hindi / Hinglish words for each mood (the moods themselves come from playlists named for them). */
    private val MOOD_WORDS = mapOf(
        "Happy" to listOf("happy", "khush", "khushi", "mast", "masti", "cheerful", "good mood", "feel good", "upbeat"),
        "Sad" to listOf("sad", "dukh", "dukhi", "udaas", "udas", "rona", "dard", "tanhai", "judaai", "breakup", "bewafa", "heartbreak", "emotional", "dil toota"),
        "Party" to listOf("party", "naach", "nachna", "dance", "dj", "club", "dhamaal", "bhangra", "celebration", "birthday"),
        "Romantic" to listOf("romantic", "romance", "love", "pyaar", "pyar", "ishq", "mohabbat", "crush", "date", "valentine"),
        "Chill" to listOf("chill", "lofi", "lo-fi", "relax", "sukoon", "calm", "study", "padhai", "focus", "acoustic", "unplugged"),
        "Sleep" to listOf("sleep", "neend", "sona", "sone", "lori", "lullaby", "soothing", "night", "raat"),
        "Workout" to listOf("workout", "gym", "exercise", "running", "run", "josh", "motivation", "pump"),
        "Devotional" to listOf("bhakti", "bhajan", "aarti", "devotional", "mandir", "bhagwan", "god", "mata", "krishna", "shiv", "ram", "hanuman", "spiritual"),
        "Road Trip" to listOf("drive", "road trip", "long drive", "safar", "travel", "trip", "journey"),
    )
    private val LANGUAGE_WORDS = mapOf(
        "bollywood" to "hindi", "hindi" to "hindi", "punjabi" to "punjabi", "haryanvi" to "haryanvi", "bhojpuri" to "bhojpuri",
        "english" to "english", "angrezi" to "english", "tamil" to "tamil", "telugu" to "telugu", "marathi" to "marathi",
        "bengali" to "bengali", "bangla" to "bengali", "gujarati" to "gujarati", "pahadi" to "pahadi", "pahari" to "pahadi",
        "garhwali" to "pahadi", "kumaoni" to "pahadi", "himachali" to "pahadi",
    )
    private val KNOWN_ARTISTS = listOf(
        "arijit singh", "arijit", "shreya ghoshal", "atif aslam", "jubin nautiyal", "neha kakkar", "sonu nigam",
        "kishore kumar", "lata mangeshkar", "mohammed rafi", "kumar sanu", "udit narayan", "alka yagnik",
        "honey singh", "badshah", "diljit dosanjh", "diljit", "karan aujla", "sidhu moose wala", "sidhu", "ap dhillon",
        "shubh", "b praak", "darshan raval", "armaan malik", "vishal mishra", "pritam", "a r rahman", "ar rahman",
        "sachet tandon", "mohit chauhan", "sunidhi chauhan", "kk", "anuv jain", "king", "guru randhawa",
        "pawan singh", "khesari lal", "masoom sharma", "jasleen royal", "papon", "ankit tiwari", "sapna choudhary",
        "amit saini rohtakiya", "raj mawar", "ndee kundu", "narender bhagana", "jassi gill", "ammy virk", "gurnam bhullar",
        "satinder sartaaj", "nusrat fateh ali khan", "rahat fateh ali khan", "jagjit singh", "mukesh", "asha bhosle",
    )
    private val FOLLOW_UP = Regex(
        "^(aur|or|more|isme|is me|add|also|bhi|only|sirf|bas|bina|without|no |hata|remove|zyada|jyada|kam|less|purane|naye|new|old|same|aise|aisa|similar|thoda|ab |and |make|change|badal)",
        RegexOption.IGNORE_CASE,
    )
    private val STOP = setOf(
        "songs", "song", "gaane", "gane", "gana", "gaana", "geet", "music", "play", "chalao", "chala", "bajao", "baja", "sunao",
        "suna", "do", "de", "dena", "please", "plz", "mujhe", "mere", "liye", "for", "me", "the", "a", "an", "and", "with", "of",
        "some", "kuch", "wale", "wala", "waale", "vale", "ke", "ki", "ka", "ko", "se", "me", "mein", "hits", "best", "top",
        "playlist", "mix", "chahiye", "sunna", "hai", "hain", "h", "bhai", "yaar", "ek", "koi", "aur", "or", "bhi", "isme",
        "only", "sirf", "bas", "jaise", "like", "type",
    )

    fun understand(request: String, previous: DjIntent?, myLanguages: List<String>): DjIntent {
        val t = " " + request.lowercase().replace(Regex("[^\\p{L}\\p{N}' ]+"), " ").replace(Regex("\\s+"), " ").trim() + " "
        fun has(w: String) = t.contains(" $w ")

        val langs = LANGUAGE_WORDS.filterKeys { has(it) }.values.distinct()
            .ifEmpty { if (LanguageGuess.isPahadi(request, "")) listOf("pahadi") else emptyList() }
        val mood = MOOD_WORDS.entries.firstOrNull { (_, ws) -> ws.any { has(it) } }
            ?.let { (name, _) -> Moods.all.firstOrNull { it.name == name } }
            ?: Moods.find(request)
        val era = when {
            has("90s") || has("90's") || has("nineties") -> "90s"
            has("80s") || has("80's") -> "80s"
            has("70s") || has("70's") || has("60s") -> "70s"
            has("2000s") || has("2000") -> "2000s"
            listOf("purane", "purana", "purani", "old", "older", "retro", "classic", "evergreen", "sadabahar").any { has(it) } -> "old"
            listOf("naye", "naya", "nayi", "new", "newer", "latest", "2025", "2026", "trending", "abhi ke").any { has(it) } -> "new"
            else -> ""
        }
        val count = Regex("\\b(\\d{1,3})\\s*(songs|song|gaane|gane|gaana)\\b").find(t)?.groupValues?.get(1)?.toIntOrNull()?.coerceIn(5, 100)
        // "Kesariya jaise gaane", "songs like Kesariya"
        val like = Regex("(?:songs like|like|similar to)\\s+(.+?)\\s*$").find(t.trim())?.groupValues?.get(1)
            ?: Regex("^(.+?)\\s+(?:jaise|jaisa|jaisi|type ke|type)\\b").find(t.trim())?.groupValues?.get(1)
        // "Aashiqui 2 ke gaane", "Animal movie songs"
        val movie = Regex("^(.+?)\\s+(?:movie|film|album)\\b").find(t.trim())?.groupValues?.get(1)
        // "bina remix", "without lofi", "Arijit ke alawa", "lofi hata do"
        val without = buildList {
            Regex("(?:bina|without|no|except)\\s+([\\p{L}\\p{N}]+(?: [\\p{L}\\p{N}]+)?)").findAll(t).forEach { add(it.groupValues[1]) }
            Regex("([\\p{L}\\p{N}]+(?: [\\p{L}\\p{N}]+)?)\\s+(?:ke alawa|ke alava|ke bina|hata|hatao|remove)").findAll(t).forEach { add(it.groupValues[1]) }
        }.map { it.removePrefix("wale ").trim() }.filter { it.isNotBlank() && it !in STOP }.distinct()
        val artists = KNOWN_ARTISTS.filter { has(it) && without.none { w -> w.contains(it) } }
            .let { list -> list.filterNot { a -> list.any { b -> b != a && b.contains(a) } } }
        val forMe = listOf("kuch bhi", "surprise", "mere liye", "for me", "my taste", "mera mood", "mujhe pasand", "jo mujhe").any { t.contains(it) }

        val known = (LANGUAGE_WORDS.keys + MOOD_WORDS.values.flatten() + artists.flatMap { it.split(" ") } +
            listOf("90s", "80s", "70s", "2000s", "purane", "old", "older", "new", "newer", "naye", "latest", "retro", "classic",
                "make", "change", "more", "this", "too", "it", "remix")).toSet()
        val words = t.trim().split(" ").filter { it.length > 2 && it !in STOP && it !in known && without.none { w -> w.split(" ").contains(it) } }
            .filter { it.toIntOrNull() == null }

        val now = DjIntent(
            languages = langs, mood = mood, era = era, artists = artists,
            like = like?.trim().orEmpty().takeIf { it.isNotBlank() && it !in STOP && it !in setOf("this", "these", "that", "ye", "yeh", "isse", "is") }.orEmpty(),
            movie = movie?.trim().orEmpty().takeIf { it.isNotBlank() && it !in STOP }.orEmpty(),
            without = without, forMe = forMe, words = words, count = count ?: 50,
        )
        val followUp = previous != null && (FOLLOW_UP.containsMatchIn(request.trim()) ||
            (now.languages.isEmpty() && now.mood == null && now.artists.isEmpty() && now.like.isEmpty() && now.movie.isEmpty() &&
                request.trim().split(Regex("\\s+")).size <= 3))
        if (!followUp || previous == null) return now.copy(languages = now.languages.ifEmpty { myLanguages })
        // A follow-up: change only what was said, keep the rest. "Punjabi too" / "punjabi bhi" adds a language.
        val addLanguage = t.contains(" too ") || t.contains(" bhi ") || t.contains(" also ")
        val onlyArtists = Regex("^(only|sirf|bas)\\b", RegexOption.IGNORE_CASE).containsMatchIn(request.trim())
        val sameAgain = now.languages.isEmpty() && now.mood == null && now.era.isEmpty() && now.artists.isEmpty() &&
            now.like.isEmpty() && now.movie.isEmpty() && now.without.isEmpty()
        return previous.copy(
            languages = when {
                now.languages.isEmpty() -> previous.languages
                addLanguage -> (previous.languages + now.languages).distinct()
                else -> now.languages
            },
            mood = now.mood ?: previous.mood,
            era = now.era.ifEmpty { previous.era },
            artists = when {
                now.artists.isEmpty() -> previous.artists.filter { a -> now.without.none { it.contains(a) || a.contains(it) } }
                onlyArtists -> now.artists
                else -> (previous.artists + now.artists).distinct()
            },
            like = now.like.ifEmpty { previous.like },
            movie = now.movie.ifEmpty { previous.movie },
            without = (previous.without + now.without).distinct(),
            words = if (sameAgain) previous.words else (previous.words + now.words).distinct(),
            count = count ?: previous.count,
            page = if (sameAgain) previous.page + 1 else 0,
        )
    }

    /** "Sad Punjabi songs from the 90s with Arijit Singh, no remix". */
    fun describe(i: DjIntent): String {
        if (i.movie.isNotBlank()) return "Songs from ${i.movie.titleCase()}"
        if (i.like.isNotBlank()) return "Songs like ${i.like.titleCase()}"
        val parts = buildString {
            i.mood?.let { append(it.name).append(' ') }
            if (i.era == "new") append("New ")
            if (i.era == "old") append("Old ")
            if (i.languages.isNotEmpty() && i.languages.size <= 2) append(i.languages.joinToString(" & ") { it.titleCase() }).append(' ')
            append("songs")
            if (i.era in setOf("90s", "80s", "70s", "2000s")) append(" from the ${i.era}")
            if (i.artists.isNotEmpty()) append(" with ${i.artists.joinToString(", ") { it.titleCase() }}")
            if (i.words.isNotEmpty() && i.mood == null) append(" · ${i.words.take(3).joinToString(" ")}")
            if (i.without.isNotEmpty()) append(", no ${i.without.joinToString(", ")}")
        }
        return if (i.forMe && i.mood == null && i.artists.isEmpty()) "Picked for you" else parts.trim().replaceFirstChar(Char::uppercase)
    }

    /** What the listener can say next. */
    fun followUps(i: DjIntent): List<String> = buildList {
        add("More like this")
        if (i.era != "new") add("Newer songs") else add("Older songs")
        if (i.mood?.name != "Sad") add("Make it sad") else add("Make it happy")
        if (i.artists.isEmpty()) add("Only Arijit Singh")
        if ("remix" !in i.without) add("No remix")
        if ("punjabi" !in i.languages) add("Punjabi too")
    }

    private fun String.titleCase() = split(" ").joinToString(" ") { w -> w.replaceFirstChar(Char::uppercase) }

    /** Words that mean the song doesn't fit (for [DjIntent.without]). */
    fun excluded(i: DjIntent, title: String, artist: String): Boolean {
        val s = " ${title.lowercase()} ${artist.lowercase()} "
        return i.without.any { w -> s.contains(w) }
    }

    /** Which languages a request may use, at most the first two (searches get slow with more). */
    fun languagesFor(i: DjIntent): List<String> = i.languages.ifEmpty { Categories.languages.take(1) }.take(2)
}
