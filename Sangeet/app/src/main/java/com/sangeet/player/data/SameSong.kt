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
}
