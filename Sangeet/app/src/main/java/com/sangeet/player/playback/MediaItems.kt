package com.sangeet.player.playback

import android.net.Uri
import android.os.Bundle
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.MimeTypes
import com.sangeet.player.data.model.SourceType
import com.sangeet.player.data.model.Track

/**
 * Player ke andar har gaane ka URI "sangeet://track/<id>" hota hai.
 * Asli URL (download ki hui file / quality ke hisaab se stream) play ke waqt [StreamResolver] banata hai.
 */
object MediaItems {
    const val SCHEME = "sangeet"
    private const val EXTRA_SOURCE = "source"

    fun uriFor(trackId: String): Uri = Uri.Builder().scheme(SCHEME).authority("track").appendPath(trackId).build()

    fun trackIdFrom(uri: Uri): String? = if (uri.scheme == SCHEME) uri.lastPathSegment else null

    fun fromTrack(t: Track): MediaItem = MediaItem.Builder()
        .setMediaId(t.id)
        .setUri(uriFor(t.id))
        .apply { if (t.streamUrl?.contains(".m3u8") == true) setMimeType(MimeTypes.APPLICATION_M3U8) }
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(t.title)
                .setArtist(t.artist)
                .setAlbumTitle(t.album)
                .setArtworkUri(t.artworkUrl?.let(Uri::parse))
                .setIsPlayable(true)
                .setIsBrowsable(false)
                .setExtras(Bundle().apply { putString(EXTRA_SOURCE, t.source.name) })
                .build()
        )
        .build()

    /** Controller se aaye MediaItem ko playable banata hai (URI dobara lagata hai). */
    fun withUri(item: MediaItem): MediaItem =
        if (item.localConfiguration != null) item
        else item.buildUpon().setUri(uriFor(item.mediaId)).build()

    fun sourceOf(item: MediaItem): SourceType? =
        item.mediaMetadata.extras?.getString(EXTRA_SOURCE)?.let { runCatching { SourceType.valueOf(it) }.getOrNull() }
}
