package com.sangeet.player.data

import com.sangeet.player.data.model.Track

/**
 * Is it the same song? A playlist keeps one copy of a song (report #55), but another version (remix, lofi, slowed,
 * unplugged…) or another singer is another song. Same means: the same clean title ("Kesariya (From "Brahmastra")" =
 * "Kesariya - Official Video"), the same version words, and a shared singer. When a side has no real singer (a music
 * label's YouTube channel), the same length within 5 s decides, and with no length either it counts as the same.
 */
object SameSong {
    class Key internal constructor(
        val title: String,
        private val version: Set<String>,
        private val singers: List<String>,
        private val text: String,
        private val durationMs: Long,
    ) {
        fun sameAs(o: Key): Boolean {
            if (title.isBlank() || title != o.title || version != o.version) return false
            if (singers.isNotEmpty() && o.singers.isNotEmpty()) {
                return singers.any { it in o.text } || o.singers.any { it in text }
            }
            return durationMs <= 0 || o.durationMs <= 0 || kotlin.math.abs(durationMs - o.durationMs) <= 5_000
        }
    }

    fun key(t: Track): Key {
        val raw = norm(
            t.title.lowercase()
                .replace(Regex("lo[ -]fi"), "lofi")
                .replace(Regex("sped[ -]up"), "spedup")
                .replace(Regex("8[ -]d\\b"), "8d")
        )
        val words = raw.split(' ').toSet()
        return Key(
            title = cleanTitle(t.title),
            version = VERSIONS.filterTo(HashSet()) { it in words },
            singers = singers(t.artist),
            text = norm("${t.artist} ${t.title}"),
            durationMs = t.durationMs,
        )
    }

    fun same(a: Track, b: Track) = a.id == b.id || key(a).sameAs(key(b))

    /**
     * Remembers songs, to tell a new one from another copy of one it has. JioSaavn lists a hit on many albums, each copy
     * with its own id, so ids alone let the same song come back again and again (report #90).
     */
    class Seen(have: List<Track> = emptyList()) {
        // Same songs always have the same clean title, so only keys with that title are compared.
        private val keys = HashMap<String, MutableList<Key>>()
        private val ids = HashSet<String>()

        init { have.forEach { add(it) } }

        /** Adds the song; false when it, or a copy of it, is here already. */
        fun add(t: Track): Boolean {
            if (!ids.add(t.id)) return false
            val k = key(t)
            val same = keys[k.title]
            if (same != null && same.any { it.sameAs(k) }) return false
            (same ?: ArrayList<Key>(2).also { keys[k.title] = it }).add(k)
            return true
        }
    }

    /** [list] with one copy of each song and none that is a copy of a song in [have], at most [max]. */
    fun <T> oneCopy(list: List<T>, have: List<Track> = emptyList(), max: Int = Int.MAX_VALUE, track: (T) -> Track): List<T> {
        val seen = Seen(have)
        val out = ArrayList<T>()
        for (item in list) {
            if (out.size >= max) break
            if (seen.add(track(item))) out += item
        }
        return out
    }

    fun oneCopy(list: List<Track>, have: List<Track> = emptyList(), max: Int = Int.MAX_VALUE): List<Track> =
        oneCopy(list, have, max) { it }

    /** The song's name without "(From …)", " - Official Video", " | Film"…, lower case. */
    fun cleanTitle(t: String) = norm(
        t.lowercase()
            .replace(Regex("""\(.*?\)|\[.*?]"""), " ")
            .substringBefore(" - ")
            .substringBefore(" | ")
            .replace(Regex("\\b(official|video|audio|lyrical|lyrics|full song|song|hd|4k)\\b"), " ")
    )

    /** The first real singer of [artist] (not a music label's channel), lower case, or null. */
    fun mainSinger(artist: String): String? = singers(artist).firstOrNull()

    private fun singers(artist: String): List<String> =
        artist.replace(Regex("(?i)\\s*-\\s*topic$|vevo$|\\s+official$"), "")
            .split(',', '&', '/', '|', ';')
            .map { norm(it) }
            .filter { n -> n.length > 2 && n != "unknown" && n != "unknown artist" && LABELS.none { n.contains(it) } }

    /**
     * The first credited name that sings, as written (null for a label's channel). JioSaavn lists music directors and
     * lyricists with the singers ("Pritam, Arijit Singh"), so the best-known ones ([CREDITS]) are passed over; when
     * nobody else is named, the first name is kept.
     */
    fun singerOf(artist: String): String? {
        val names = artist.replace(Regex("(?i)\\s*-\\s*topic$|vevo$|\\s+official$"), "")
            .split(',', '&', '/', '|', ';')
            .map { it.trim() }
            .filter { n -> norm(n).let { it.length > 2 && it != "unknown" && it != "unknown artist" && LABELS.none { l -> it.contains(l) } } }
        return names.firstOrNull { norm(it) !in CREDITS } ?: names.firstOrNull()
    }

    private fun norm(s: String) = s.lowercase().replace(Regex("[^\\p{L}\\p{N}]+"), " ").trim()

    private val VERSIONS = listOf(
        "remix", "lofi", "slowed", "reverb", "spedup", "unplugged", "acoustic", "reprise", "female", "male", "sad",
        "cover", "live", "instrumental", "karaoke", "mashup", "8d", "dj", "extended", "duet", "version",
    )

    /** YouTube channels of music labels: their name is not a singer. */
    val LABELS = listOf(
        "t series", "zee music", "sony music", "saregama", "tips", "speed records", "desi music factory",
        "white hill", "yrf", "eros", "venus", "shemaroo", "times music", "geet mp3", "vyrl", "jjust music",
        "panj aab", "lokdhun", "aditya music", "lahari", "think music", "ultra", "worldwide records", "records",
        "music company", "music india",
    )

    /** Music directors, lyricists and producers JioSaavn credits next to the singers (the web app has the same list). */
    val CREDITS = setOf(
        "pritam", "r d burman", "laxmikant pyarelal", "shankar jaikishan", "kalyanji anandji", "nadeem shravan",
        "jatin lalit", "anu malik", "vishal shekhar", "shankar ehsaan loy", "salim sulaiman", "sajid wajid", "sachin jigar",
        "mithoon", "tanishk bagchi", "rochak kohli", "madan mohan", "naushad", "o p nayyar", "rajesh roshan", "roshan",
        "chitragupta", "usha khanna", "anand milind", "raamlaxman", "viju shah", "uttam singh", "amaal mallik",
        "gourov roshin", "lijo george", "dj chetas", "jam8", "ilaiyaraaja",
        "gulzar", "javed akhtar", "anand bakshi", "sameer", "sameer anjaan", "irshad kamil", "kumaar", "manoj muntashir",
        "amitabh bhattacharya", "majrooh sultanpuri", "sahir ludhianvi", "shailendra", "hasrat jaipuri", "indeevar",
        "rajendra krishan", "prasoon joshi", "kausar munir", "rashmi virag", "sayeed quadri", "neelesh misra",
        "shabbir ahmed", "kunwar juneja", "a m turaz", "rakesh kumar", "anvita dutt",
        "desi crew", "the kidd", "mxrci", "jay trak", "ikky", "snappy", "proof", "yeah proof", "intense", "mista baaz",
        "sanjoy", "gold boy", "laddi gill", "western penduz", "preet hundal", "jassi x", "mix singh", "bunty bains",
        "veet baljit", "babbu", "raj fatehpur", "jaani",
    )
}
