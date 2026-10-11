package com.sangeet.player.data.community

import com.sangeet.player.AppContainer
import com.sangeet.player.BuildConfig
import com.sangeet.player.data.model.SourceType
import com.sangeet.player.data.model.Track
import com.sangeet.player.data.model.inLanguages

/**
 * Sangeet Top 50 (owner, Oct 11): what the app's listeners played most this week (both apps, two or more listeners
 * per song: Community's "chart"), in the chosen languages. While there are few listeners, JioSaavn's trending songs
 * of those languages fill it up to 50. The website shows the same list.
 */
object Top50 {
    suspend fun tracks(c: AppContainer, limit: Int = 50): List<Track> {
        val s = c.settings.current
        val youtube = s.youtubeEnabled && !BuildConfig.PLAY_STORE
        val ours = Community.await(c.appContext)?.charts().orEmpty()
            .filter { it.inLanguages(s.languages) && (youtube || it.source != SourceType.YOUTUBE) }
        val fill = if (ours.size >= limit) emptyList()
        else runCatching { c.online.saavn.trending(s, null) }.getOrDefault(emptyList()).filter { it.inLanguages(s.languages) }
        // The same song once, even when it came from both sources.
        val seen = HashSet<String>()
        return (ours + fill).filter { t ->
            val name = t.title.lowercase().replace(Regex("""\(.*?\)|\[.*?]"""), "").replace(Regex("[^\\p{L}\\p{N}]+"), " ").trim()
            seen.add(t.id) and seen.add("$name|${t.artist.substringBefore(",").trim().lowercase()}")
        }.take(limit)
    }
}
