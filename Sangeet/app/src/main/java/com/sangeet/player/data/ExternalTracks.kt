package com.sangeet.player.data

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.sangeet.player.data.model.SourceType
import com.sangeet.player.data.model.Track
import java.security.MessageDigest

/** File manager / browser se "Open with Sangeet" kiya gaana. */
object ExternalTracks {
    fun fromUri(context: Context, uri: Uri): Track {
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

    private fun displayName(context: Context, uri: Uri): String? {
        if (uri.scheme != "content") return null
        return runCatching {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) c.getString(0) else null
            }
        }.getOrNull()
    }
}
