package com.sangeet.player.data.remote

import com.sangeet.player.data.model.AudioQuality
import com.sangeet.player.data.model.SourceType
import com.sangeet.player.data.model.Track
import com.sangeet.player.data.settings.AppSettings
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/** Har online source isko implement karta hai. */
interface OnlineSource {
    val type: SourceType
    /** true = source 128/256/320 kbps ko sach mein maanta hai. */
    val exactQuality: Boolean
    fun isEnabled(s: AppSettings): Boolean
    suspend fun trending(s: AppSettings, genre: String? = null): List<Track>
    suspend fun search(query: String, s: AppSettings): List<Track>
    fun streamUrl(track: Track, quality: AudioQuality, s: AppSettings): String
}

// ---------------------------------------------------------------- Audius

@Serializable
private data class AudiusResponse(val data: List<AudiusTrack> = emptyList())

@Serializable
private data class AudiusTrack(
    val id: String,
    val title: String = "",
    val duration: Long = 0,
    val genre: String? = null,
    val artwork: AudiusArtwork? = null,
    val user: AudiusUser? = null,
    @SerialName("is_streamable") val isStreamable: Boolean = true,
)

@Serializable
private data class AudiusArtwork(
    @SerialName("480x480") val medium: String? = null,
    @SerialName("1000x1000") val large: String? = null,
    @SerialName("150x150") val small: String? = null,
)

@Serializable
private data class AudiusUser(val name: String = "", val handle: String = "")

/** Audius: free, bina API key ke. Gaane app ke andar hi stream hote hain. */
class AudiusSource : OnlineSource {
    override val type = SourceType.AUDIUS
    override val exactQuality = false
    private val base = "https://api.audius.co/v1"
    private val app = "Sangeet"

    override fun isEnabled(s: AppSettings) = s.audiusEnabled

    override suspend fun trending(s: AppSettings, genre: String?): List<Track> {
        val url = "$base/tracks/trending".toHttpUrl().newBuilder()
            .addQueryParameter("app_name", app)
            .addQueryParameter("limit", "50")
            .apply { if (genre != null) addQueryParameter("genre", genre) }
            .build()
        return fetch(url)
    }

    override suspend fun search(query: String, s: AppSettings): List<Track> {
        val url = "$base/tracks/search".toHttpUrl().newBuilder()
            .addQueryParameter("query", query)
            .addQueryParameter("app_name", app)
            .build()
        return fetch(url)
    }

    override fun streamUrl(track: Track, quality: AudioQuality, s: AppSettings): String =
        "$base/tracks/${track.sourceId}/stream?app_name=$app"

    private suspend fun fetch(url: HttpUrl): List<Track> {
        val body = Http.getText(url.toString()) ?: return emptyList()
        return Http.json.decodeFromString(AudiusResponse.serializer(), body).data
            .filter { it.isStreamable }
            .map {
                Track(
                    id = Track.makeId(SourceType.AUDIUS, it.id),
                    source = SourceType.AUDIUS,
                    sourceId = it.id,
                    title = it.title,
                    artist = it.user?.let { u -> u.name.ifBlank { u.handle } } ?: "Unknown",
                    album = it.genre ?: "Audius",
                    durationMs = it.duration * 1000,
                    artworkUrl = it.artwork?.medium ?: it.artwork?.large ?: it.artwork?.small,
                )
            }
    }
}

// ---------------------------------------------------------------- Jamendo

@Serializable
private data class JamendoResponse(val results: List<JamendoTrack> = emptyList())

@Serializable
private data class JamendoTrack(
    val id: String,
    val name: String = "",
    val duration: Long = 0,
    @SerialName("artist_name") val artistName: String = "",
    @SerialName("album_name") val albumName: String = "",
    val image: String? = null,
    val audio: String? = null,
)

/** Jamendo: free Creative-Commons music. devportal.jamendo.com se free client_id chahiye. */
class JamendoSource : OnlineSource {
    override val type = SourceType.JAMENDO
    override val exactQuality = false

    override fun isEnabled(s: AppSettings) = s.jamendoClientId.isNotBlank()

    override suspend fun trending(s: AppSettings, genre: String?): List<Track> = fetch(s) {
        addQueryParameter("order", "popularity_week")
        if (genre != null) addQueryParameter("tags", genre.lowercase())
    }

    override suspend fun search(query: String, s: AppSettings): List<Track> = fetch(s) {
        addQueryParameter("search", query)
        addQueryParameter("order", "relevance")
    }

    /** mp31 = 96 kbps, mp32 = high VBR. Jamendo exact 128/256/320 nahi deta. */
    override fun streamUrl(track: Track, quality: AudioQuality, s: AppSettings): String {
        val fmt = if (quality == AudioQuality.LOW) "mp31" else "mp32"
        val url = track.streamUrl ?: "https://prod-1.storage.jamendo.com/?trackid=${track.sourceId}&format=mp32"
        return url.replace(Regex("format=[a-z0-9]+"), "format=$fmt")
    }

    private suspend fun fetch(s: AppSettings, extra: HttpUrl.Builder.() -> Unit): List<Track> {
        val url = "https://api.jamendo.com/v3.0/tracks/".toHttpUrl().newBuilder()
            .addQueryParameter("client_id", s.jamendoClientId)
            .addQueryParameter("format", "json")
            .addQueryParameter("limit", "50")
            .addQueryParameter("imagesize", "500")
            .addQueryParameter("audioformat", "mp32")
            .apply(extra)
            .build()
        val body = Http.getText(url.toString()) ?: return emptyList()
        return Http.json.decodeFromString(JamendoResponse.serializer(), body).results
            .filter { !it.audio.isNullOrBlank() }
            .map {
                Track(
                    id = Track.makeId(SourceType.JAMENDO, it.id),
                    source = SourceType.JAMENDO,
                    sourceId = it.id,
                    title = it.name,
                    artist = it.artistName,
                    album = it.albumName,
                    durationMs = it.duration * 1000,
                    artworkUrl = it.image,
                    streamUrl = it.audio,
                )
            }
    }
}

// ---------------------------------------------------------------- Subsonic / Navidrome

@Serializable
private data class SubsonicEnvelope(@SerialName("subsonic-response") val response: SubsonicBody)

@Serializable
private data class SubsonicBody(
    val status: String = "",
    val error: SubsonicError? = null,
    val randomSongs: SubsonicSongs? = null,
    val searchResult3: SubsonicSongs? = null,
)

@Serializable
private data class SubsonicError(val code: Int = 0, val message: String = "")

@Serializable
private data class SubsonicSongs(val song: List<SubsonicSong> = emptyList())

@Serializable
private data class SubsonicSong(
    val id: String,
    val title: String = "",
    val artist: String = "",
    val album: String = "",
    val duration: Long = 0,
    val coverArt: String? = null,
)

/**
 * Apna Navidrome / Subsonic / Airsonic server. Yahi source 128/256/320 kbps
 * ko server-side transcoding se bilkul sahi deta hai.
 */
class SubsonicSource : OnlineSource {
    override val type = SourceType.SUBSONIC
    override val exactQuality = true

    override fun isEnabled(s: AppSettings) = s.subsonicConfigured

    override suspend fun trending(s: AppSettings, genre: String?): List<Track> {
        val url = endpoint(s, "getRandomSongs")?.newBuilder()
            ?.addQueryParameter("size", "50")
            ?.apply { if (genre != null) addQueryParameter("genre", genre) }
            ?.build() ?: return emptyList()
        return fetch(url, s)
    }

    override suspend fun search(query: String, s: AppSettings): List<Track> {
        val url = endpoint(s, "search3")?.newBuilder()
            ?.addQueryParameter("query", query)
            ?.addQueryParameter("songCount", "50")
            ?.addQueryParameter("artistCount", "0")
            ?.addQueryParameter("albumCount", "0")
            ?.build() ?: return emptyList()
        return fetch(url, s)
    }

    override fun streamUrl(track: Track, quality: AudioQuality, s: AppSettings): String =
        endpoint(s, "stream")!!.newBuilder()
            .addQueryParameter("id", track.sourceId)
            .addQueryParameter("maxBitRate", quality.kbps.toString())
            .addQueryParameter("format", "mp3")
            .build().toString()

    private fun endpoint(s: AppSettings, method: String): HttpUrl? {
        val base = s.subsonicUrl.toHttpUrlOrNull() ?: return null
        return base.newBuilder()
            .addPathSegment("rest")
            .addPathSegment("$method.view")
            .addQueryParameter("u", s.subsonicUser)
            .addQueryParameter("t", s.subsonicToken)
            .addQueryParameter("s", s.subsonicSalt)
            .addQueryParameter("v", "1.16.1")
            .addQueryParameter("c", "Sangeet")
            .addQueryParameter("f", "json")
            .build()
    }

    private fun coverUrl(s: AppSettings, coverId: String?): String? = coverId?.let {
        endpoint(s, "getCoverArt")?.newBuilder()
            ?.addQueryParameter("id", it)
            ?.addQueryParameter("size", "500")
            ?.build()?.toString()
    }

    private suspend fun fetch(url: HttpUrl, s: AppSettings): List<Track> {
        val body = Http.getText(url.toString()) ?: return emptyList()
        val res = Http.json.decodeFromString(SubsonicEnvelope.serializer(), body).response
        if (res.status != "ok") throw IllegalStateException(res.error?.message ?: "Server error")
        val songs = res.randomSongs?.song ?: res.searchResult3?.song ?: emptyList()
        return songs.map {
            Track(
                id = Track.makeId(SourceType.SUBSONIC, it.id),
                source = SourceType.SUBSONIC,
                sourceId = it.id,
                title = it.title,
                artist = it.artist,
                album = it.album,
                durationMs = it.duration * 1000,
                artworkUrl = coverUrl(s, it.coverArt),
            )
        }
    }
}
