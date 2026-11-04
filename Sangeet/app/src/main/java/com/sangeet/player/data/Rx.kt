package com.sangeet.player.data

import java.util.concurrent.ConcurrentHashMap

/**
 * Regexes built once (report #92). On Android each Regex is compiled by ICU in native memory, and building the same
 * ones again for every song (thousands for a radio, the feed or a singer page) used up a phone's native memory:
 * Android ended the app with "Scudo ERROR: internal map failure (Out of memory)" inside RegexCompile.
 */
object Rx {
    /** Anything that isn't a letter or a digit. */
    val NON_WORD = Regex("[^\\p{L}\\p{N}]+")

    /** "(From …)", "[Official Video]". */
    val BRACKETS = Regex("""\(.*?\)|\[.*?]""")

    val SPACES = Regex("\\s+")

    /** " - Topic", "VEVO", " Official" at the end of a YouTube channel's name. */
    val CHANNEL_SUFFIX = Regex("(?i)\\s*-\\s*topic$|vevo$|\\s+official$")

    /** Characters a file name can't have. */
    val BAD_FILE_CHARS = Regex("""[\\/:*?"<>|]""")

    /** Characters that can't be in a file name made from an id. */
    val NOT_ID = Regex("[^A-Za-z0-9_-]")

    private val words = ConcurrentHashMap<String, Regex>()

    /** [w] as a whole word, in any case; each word is compiled once. */
    fun word(w: String): Regex {
        if (words.size > 2000) words.clear()
        return words.getOrPut(w.lowercase()) { Regex("\\b${Regex.escape(w)}\\b", RegexOption.IGNORE_CASE) }
    }
}
