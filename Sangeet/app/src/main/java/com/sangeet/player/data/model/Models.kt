package com.sangeet.player.data.model

import kotlinx.serialization.Serializable

/** Gaana kahan se aa raha hai. */
@Serializable
enum class SourceType(val label: String) {
    LOCAL("Phone"),
    JIOSAAVN("JioSaavn"),
    YOUTUBE("YouTube"),
    AUDIUS("Audius"),
    JAMENDO("Jamendo"),
    SUBSONIC("My Server"),
    URL("Web link");

    val isOnline: Boolean get() = this != LOCAL
}

/** Streaming / download quality. */
@Serializable
enum class AudioQuality(val kbps: Int, val label: String) {
    LOW(128, "128 kbps"),
    MEDIUM(256, "256 kbps"),
    HIGH(320, "320 kbps");
}

/**
 * Ek gaana. [id] poore app mein unique hai: "<source>:<sourceId>".
 * [streamUrl] source ke hisaab se: local ke liye content:// uri, Jamendo ke liye audio url,
 * URL source ke liye direct link. Audius / Subsonic ka url play ke time banta hai.
 */
@Serializable
data class Track(
    val id: String,
    val source: SourceType,
    val sourceId: String,
    val title: String,
    val artist: String,
    val album: String = "",
    val durationMs: Long = 0L,
    val artworkUrl: String? = null,
    val streamUrl: String? = null,
    val filePath: String? = null,
    val albumId: Long = 0L,
    /** "hindi", "punjabi", "english"... (pata ho to). Suggestions mein kaam aata hai. */
    val language: String = "",
) {
    companion object {
        fun makeId(source: SourceType, sourceId: String) = "${source.name.lowercase()}:$sourceId"
    }
}

data class Album(
    val id: Long,
    val title: String,
    val artist: String,
    val artworkUrl: String?,
    val tracks: List<Track>,
)

data class Artist(
    val name: String,
    val tracks: List<Track>,
    val artworkUrl: String?,
)

data class Playlist(
    val id: Long,
    val name: String,
    val trackCount: Int,
    val coverUrl: String?,
)

/** Online playlist / chart (JioSaavn) — library mein browse karne ke liye. */
data class OnlinePlaylist(
    val id: String,
    val title: String,
    val subtitle: String,
    val artworkUrl: String?,
    val songCount: Int = 0,
)

/** Download ki halat. */
enum class DownloadState { QUEUED, DOWNLOADING, DONE, FAILED }

data class DownloadInfo(
    val trackId: String,
    val state: DownloadState,
    val progress: Int,
    val filePath: String?,
    val quality: AudioQuality,
)

/**
 * Kya ye gaana pasandida bhashaon mein hai? Phone ke apne gaane hamesha haan.
 * Bhasha pata na ho to title/artist se andaza; phir bhi na pata chale to nahi (strict).
 */
fun Track.inLanguages(languages: List<String>): Boolean {
    if (source == SourceType.LOCAL || languages.isEmpty()) return true
    val lang = language.ifBlank { com.sangeet.player.data.remote.LanguageGuess.guess(title, artist) }
    return lang.isNotBlank() && lang in languages
}
