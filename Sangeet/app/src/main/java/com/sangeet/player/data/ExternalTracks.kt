package com.sangeet.player.data

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.sangeet.player.data.model.SourceType
import com.sangeet.player.data.model.Track
import com.sangeet.player.data.remote.YouTubeSource
import java.security.MessageDigest

/** File manager / browser / YouTube share se aaya gaana. */
object ExternalTracks {
    fun fromUri(context: Context, uri: Uri): Track {
        YouTubeSource.videoId(uri.toString())?.let { return youtube(it) }
        val id = MessageDigest.getInstance("SHA-1").digest(uri.toString().toByteArray())
            .joinToString("") { "%02x".format(it) }.take(20)
        val name = displayName(context, uri)
            ?: uri.lastPathSegment?.substringAfterLast('/')
            ?: "Audio"
        val title = name.substringBeforeLast('.').ifBlank { name }
        val idx = title.indexOf(" - ")
        return Track(
            id = Track.makeId(SourceType.URL, id),
            source = SourceType.URL,
            sourceId = id,
            title = if (idx > 0) title.substring(idx + 3).trim() else title,
            artist = if (idx > 0) title.substring(0, idx).trim() else "Unknown artist",
            streamUrl = uri.toString(),
        )
    }

    /** Share kiye text mein YouTube link ho to wo gaana. */
    fun fromSharedText(text: String): Track? =
        Regex("""https?://\S+""").findAll(text).firstNotNullOfOrNull { YouTubeSource.videoId(it.value) }?.let(::youtube)

    private fun youtube(id: String) = Track(
        id = Track.makeId(SourceType.YOUTUBE, id),
        source = SourceType.YOUTUBE,
        sourceId = id,
        title = "YouTube song",
        artist = "YouTube",
        artworkUrl = "https://i.ytimg.com/vi/$id/hqdefault.jpg",
    )

    private fun displayName(context: Context, uri: Uri): String? {
        if (uri.scheme != "content") return null
        return runCatching {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) c.getString(0) else null
            }
        }.getOrNull()
    }
}
