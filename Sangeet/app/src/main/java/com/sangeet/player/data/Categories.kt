package com.sangeet.player.data

import java.util.Calendar

/**
 * Home / Search ki lines: "🎵 Hindi Romantic", "🔥 Party"... Har ek ek search query hai.
 * `more` + `match`: a category made of several searches whose songs must fit it (e.g. Haryanvi Badmashi):
 * results of a search that names the category itself are kept, results of the other searches (singers)
 * only when the song title fits `match`.
 */
data class Category(
    val name: String,
    val emoji: String,
    val query: String,
    val language: String,
    val color: Long,
    val more: List<String> = emptyList(),
    val match: Regex? = null,
)

/** Haryanvi badmashi songs: words from their titles (gunda, bandook, jail, dushman...). */
val BADMASHI = Regex(
    "badma?sh?|bdmash|\\bgund[aeiy]|gundagardi|\\bgoli|band[ou]+k|raf+al|rifle|pistol|\\bkatt[ae]\\b|\\basla\\b|licen[cs]e|" +
        "\\bjail\\b|khoon|dushman|gangster|\\bgang\\b|ak ?47|\\bbore\\b|encounter|rangdari|dabdaba|hathyar|qatal|\\bkatl\\b|" +
        "\\bmaut\\b|\\bbadla\\b|warrant|hawalat|\\bdaku\\b|bahubali|khatarnak|\\bthar\\b|bawal|rangbaaz|shooter|firing|" +
        "\\bfire\\b|\\bbullet\\b|danger|\\bdon\\b",
    RegexOption.IGNORE_CASE,
)

object Categories {
    val all = listOf(
        Category("Bollywood Hits", "🎬", "bollywood hits", "hindi", 0xFFE13300),
        Category("Hindi Romantic", "🎵", "hindi romantic songs", "hindi", 0xFFDC148C),
        Category("Punjabi Hits", "🥁", "punjabi hits", "punjabi", 0xFFE8115B),
        Category("Party", "🔥", "bollywood party songs", "hindi", 0xFF7358FF),
        Category("Lofi Chill", "🎧", "hindi lofi", "hindi", 0xFF477D95),
        Category("Arijit Singh", "🎤", "arijit singh", "hindi", 0xFF8D67AB),
        Category("Sad Songs", "💔", "hindi sad songs", "hindi", 0xFF1E3264),
        Category("Old is Gold", "📻", "old hindi songs 90s", "hindi", 0xFFBA5D07),
        Category("Punjabi Romantic", "💕", "punjabi romantic songs", "punjabi", 0xFFB06239),
        Category("Haryanvi", "🌾", "haryanvi songs", "haryanvi", 0xFF608108),
        Category(
            "Haryanvi Badmashi", "😎", "haryanvi badmashi songs", "haryanvi", 0xFF8B1E1E,
            more = listOf(
                "badmashi haryanvi", "badmash haryanvi song", "haryanvi gangster songs", "haryanvi bandook song",
                "masoom sharma", "amit saini rohtakiya", "narender bhagana", "raj mawar", "khasa aala chahar", "ndee kundu",
            ),
            match = BADMASHI,
        ),
        Category("Bhojpuri", "🎺", "bhojpuri songs", "bhojpuri", 0xFF27856A),
        Category("Devotional", "🙏", "bhakti songs hindi", "hindi", 0xFFF59B23),
        Category("Workout", "💪", "gym workout hindi songs", "hindi", 0xFF148A08),
        Category("Indie India", "🎸", "indian indie songs", "hindi", 0xFF503750),
        Category("English Pop", "🌍", "english pop hits", "english", 0xFF0D73EC),
        Category("Tamil Hits", "🌴", "tamil hits", "tamil", 0xFFE91429),
        Category("Telugu Hits", "⭐", "telugu hits", "telugu", 0xFF777777),
    )

    fun find(name: String): Category? = all.firstOrNull { it.name.equals(name, true) }

    /** Category ya tyohaar ke naam se search query (list screen ke liye). */
    fun queryFor(name: String): String? =
        find(name)?.query ?: Festivals.all.firstOrNull { it.name.equals(name, true) }?.query

    fun titleFor(name: String): String? =
        find(name)?.let { "${it.emoji} ${it.name}" }
            ?: Festivals.all.firstOrNull { it.name.equals(name, true) }?.let { "${it.emoji} ${it.name}" }

    /** Pasandida bhasha wali categories pehle. */
    fun ordered(languages: List<String>): List<Category> =
        all.sortedBy { c -> languages.indexOf(c.language).let { if (it < 0) 99 else it } }

    val languages = listOf("hindi", "punjabi", "english", "haryanvi", "bhojpuri", "tamil", "telugu", "marathi", "bengali", "gujarati")
}

/** Feed ke mood buttons: 😊 Happy, 💔 Sad, 🔥 Party... */
data class Mood(val emoji: String, val name: String, val keyword: String)

object Moods {
    val all = listOf(
        Mood("😊", "Happy", "happy"),
        Mood("💔", "Sad", "sad"),
        Mood("🔥", "Party", "party dance"),
        Mood("💕", "Romantic", "romantic love"),
        Mood("😌", "Chill", "chill lofi"),
        Mood("😴", "Sleep", "soft sleep calm"),
        Mood("💪", "Workout", "workout gym"),
        Mood("🙏", "Devotional", "bhakti bhajan"),
        Mood("🚗", "Road Trip", "road trip travel"),
    )

    /** Mood + pasandida bhasha se search queries, jaise "hindi romantic love songs". */
    fun queries(mood: Mood, languages: List<String>): List<String> =
        languages.take(2).ifEmpty { listOf("hindi") }.map { "$it ${mood.keyword} songs" }
}

/** Tyohaar / mausam ki playlists — sahi waqt pe apne aap Home pe aati hain. */
data class Festival(
    val name: String,
    val emoji: String,
    val query: String,
    /** (month 1-12, day) se (month, day) tak — saal paar kar sakta hai (Dec -> Jan). */
    val from: Pair<Int, Int>,
    val to: Pair<Int, Int>,
)

object Festivals {
    val all = listOf(
        Festival("Lohri & Makar Sankranti", "🪁", "lohri punjabi songs", 1 to 8, 1 to 16),
        Festival("Republic Day", "🇮🇳", "desh bhakti songs", 1 to 20, 1 to 27),
        Festival("Valentine Week", "💝", "romantic love songs hindi", 2 to 6, 2 to 15),
        Festival("Holi", "🎨", "holi songs", 2 to 25, 3 to 25),
        Festival("Baisakhi", "🌾", "baisakhi punjabi bhangra", 4 to 5, 4 to 16),
        Festival("Monsoon", "🌧️", "barish monsoon songs hindi", 6 to 25, 9 to 10),
        Festival("Independence Day", "🇮🇳", "desh bhakti songs", 8 to 8, 8 to 16),
        Festival("Raksha Bandhan", "🎀", "raksha bandhan songs", 8 to 1, 8 to 31),
        Festival("Janmashtami", "🦚", "krishna bhajan", 8 to 10, 9 to 10),
        Festival("Ganesh Chaturthi", "🐘", "ganpati songs", 8 to 20, 9 to 25),
        Festival("Navratri & Garba", "🪔", "navratri garba dandiya songs", 9 to 15, 10 to 25),
        Festival("Durga Puja", "🔱", "durga puja songs", 9 to 20, 10 to 20),
        Festival("Diwali", "🪔", "diwali songs", 10 to 12, 11 to 15),
        Festival("Chhath Puja", "🌅", "chhath puja geet", 10 to 25, 11 to 20),
        Festival("Wedding Season", "💍", "wedding songs bollywood", 11 to 10, 2 to 28),
        Festival("Christmas", "🎄", "christmas songs", 12 to 15, 12 to 26),
        Festival("New Year Party", "🎉", "new year party songs", 12 to 26, 1 to 3),
    )

    /** Aaj ke din chal rahe tyohaar. */
    fun active(now: Calendar = Calendar.getInstance()): List<Festival> {
        val today = (now.get(Calendar.MONTH) + 1) * 100 + now.get(Calendar.DAY_OF_MONTH)
        return all.filter { f ->
            val a = f.from.first * 100 + f.from.second
            val b = f.to.first * 100 + f.to.second
            if (a <= b) today in a..b else today >= a || today <= b
        }
    }
}
