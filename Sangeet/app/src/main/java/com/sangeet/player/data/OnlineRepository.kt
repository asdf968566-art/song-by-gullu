package com.sangeet.player.data

import com.sangeet.player.data.model.AudioQuality
import com.sangeet.player.data.model.SourceType
import com.sangeet.player.data.model.Track
import com.sangeet.player.data.remote.AudiusSource
import com.sangeet.player.data.remote.JamendoSource
import com.sangeet.player.data.remote.OnlineSource
import com.sangeet.player.data.remote.SubsonicSource
import com.sangeet.player.data.settings.SettingsRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

data class SourceResult(val source: SourceType, val tracks: List<Track>, val error: String? = null)

class OnlineRepository(
    private val settings: SettingsRepository,
    private val network: NetworkMonitor,
) {
    val sources: List<OnlineSource> = listOf(AudiusSource(), JamendoSource(), SubsonicSource())

    fun source(type: SourceType): OnlineSource? = sources.firstOrNull { it.type == type }

    fun enabledSources(): List<OnlineSource> {
        val s = settings.current
        if (s.offlineMode) return emptyList()
        return sources.filter { it.isEnabled(s) }
    }

    val canGoOnline: Boolean
        get() = !settings.current.offlineMode && network.status.value.online

    suspend fun trending(genre: String? = null): List<SourceResult> = fanOut { it.trending(settings.current, genre) }

    suspend fun search(query: String): List<SourceResult> = fanOut { it.search(query, settings.current) }

    private suspend fun fanOut(block: suspend (OnlineSource) -> List<Track>): List<SourceResult> = coroutineScope {
        if (!canGoOnline) return@coroutineScope emptyList()
        enabledSources().map { src ->
            async {
                try {
                    // Duplicate id se Lazy lists crash karti hain, isliye ek hi baar rakho.
                    SourceResult(src.type, block(src).distinctBy { it.id })
                } catch (e: Exception) {
                    SourceResult(src.type, emptyList(), e.message ?: "Network error")
                }
            }
        }.awaitAll()
    }

    /** Is waqt ke network ke hisaab se kaunsi quality chahiye. */
    fun streamingQuality(): AudioQuality {
        val s = settings.current
        return if (network.status.value.unmetered) s.wifiQuality else s.mobileQuality
    }

    /** Online gaane ka asli stream url (quality ke saath). Local ke liye null. */
    fun streamUrl(track: Track, quality: AudioQuality): String? = when (track.source) {
        SourceType.LOCAL -> null
        SourceType.URL -> track.streamUrl
        else -> source(track.source)?.streamUrl(track, quality, settings.current)
    }

    fun qualityLabel(track: Track, quality: AudioQuality): String = when (track.source) {
        SourceType.LOCAL -> "Phone file"
        SourceType.URL -> "Original"
        SourceType.SUBSONIC -> quality.label
        SourceType.JAMENDO -> if (quality == AudioQuality.LOW) "96 kbps" else "High (VBR)"
        SourceType.AUDIUS -> "Source quality"
    }
}
