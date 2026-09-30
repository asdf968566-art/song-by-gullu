package com.sangeet.player.data

/** Home / Search ki lines: "🎵 Hindi Romantic", "🔥 Party"... Har ek ek search query hai. */
data class Category(val name: String, val emoji: String, val query: String, val language: String, val color: Long)

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
        Category("Bhojpuri", "🎺", "bhojpuri songs", "bhojpuri", 0xFF27856A),
        Category("Bhakti", "🙏", "bhakti songs hindi", "hindi", 0xFFF59B23),
        Category("Workout", "💪", "gym workout hindi songs", "hindi", 0xFF148A08),
        Category("Indie India", "🎸", "indian indie songs", "hindi", 0xFF503750),
        Category("English Pop", "🌍", "english pop hits", "english", 0xFF0D73EC),
        Category("Tamil Hits", "🌴", "tamil hits", "tamil", 0xFFE91429),
        Category("Telugu Hits", "⭐", "telugu hits", "telugu", 0xFF777777),
    )

    fun find(name: String): Category? = all.firstOrNull { it.name.equals(name, true) }

    /** Pasandida bhasha wali categories pehle. */
    fun ordered(languages: List<String>): List<Category> =
        all.sortedBy { c -> languages.indexOf(c.language).let { if (it < 0) 99 else it } }

    val languages = listOf("hindi", "punjabi", "english", "haryanvi", "bhojpuri", "tamil", "telugu", "marathi", "bengali", "gujarati")
}
