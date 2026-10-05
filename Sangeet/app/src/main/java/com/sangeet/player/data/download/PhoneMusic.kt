package com.sangeet.player.data.download

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.sangeet.player.data.model.Track
import java.io.File

/**
 * Puts a copy of a downloaded song in the phone's Music/Sangeet folder (Android 10+, no permission needed).
 * That copy stays after the app is uninstalled and shows up in the file manager and other music apps.
 */
object PhoneMusic {
    const val FOLDER = "Sangeet"

    fun supported() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q

    /** Returns the new file's content Uri, or null if it already exists / isn't possible. */
    fun save(context: Context, track: Track, file: File): Uri? {
        if (!supported() || !file.exists()) return null
        val ext = file.extension.ifBlank { "m4a" }
        val name = "${track.artist} - ${track.title}".replace(Regex("""[\\/:*?"<>|]"""), " ").take(120).trim() + ".$ext"
        val resolver = context.contentResolver
        val collection = MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val relPath = "${Environment.DIRECTORY_MUSIC}/$FOLDER/"
        // Already saved before? Don't make a second copy.
        resolver.query(
            collection, arrayOf(MediaStore.Audio.Media._ID),
            "${MediaStore.Audio.Media.RELATIVE_PATH}=? AND ${MediaStore.Audio.Media.DISPLAY_NAME}=?",
            arrayOf(relPath, name), null,
        )?.use { if (it.moveToFirst()) return null }

        val values = ContentValues().apply {
            put(MediaStore.Audio.Media.DISPLAY_NAME, name)
            put(MediaStore.Audio.Media.TITLE, track.title)
            put(MediaStore.Audio.Media.ARTIST, track.artist)
            put(MediaStore.Audio.Media.ALBUM, track.album.ifBlank { "Sangeet" })
            put(MediaStore.Audio.Media.MIME_TYPE, mime(ext))
            put(MediaStore.Audio.Media.RELATIVE_PATH, relPath)
            put(MediaStore.Audio.Media.IS_MUSIC, 1)
            put(MediaStore.Audio.Media.IS_PENDING, 1)
        }
        val uri = resolver.insert(collection, values) ?: return null
        return try {
            resolver.openOutputStream(uri)?.use { out -> file.inputStream().use { it.copyTo(out) } } ?: error("no stream")
            resolver.update(uri, ContentValues().apply { put(MediaStore.Audio.Media.IS_PENDING, 0) }, null, null)
            uri
        } catch (e: Exception) {
            resolver.delete(uri, null, null)
            null
        }
    }

    private fun mime(ext: String) = when (ext.lowercase()) {
        "mp3" -> "audio/mpeg"
        "ogg" -> "audio/ogg"
        "flac" -> "audio/flac"
        "webm" -> "audio/webm"
        else -> "audio/mp4"
    }
}
