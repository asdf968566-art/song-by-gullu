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
        if (alreadySaved(context, collection, relPath, name, track)) return null

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

    /**
     * The same song must not be saved twice, even when it came once from YouTube and once from JioSaavn
     * with a different artist line: same file name, or same clean title plus same singer / same length.
     */
    private fun alreadySaved(context: Context, collection: Uri, relPath: String, name: String, track: Track): Boolean {
        val want = clean(track.title)
        val singers = track.artist.lowercase().split(',', '&').map { it.trim() }.filter { it.length > 2 }
        context.contentResolver.query(
            collection,
            arrayOf(MediaStore.Audio.Media.DISPLAY_NAME, MediaStore.Audio.Media.TITLE, MediaStore.Audio.Media.ARTIST, MediaStore.Audio.Media.DURATION),
            "${MediaStore.Audio.Media.RELATIVE_PATH}=?", arrayOf(relPath), null,
        )?.use { c ->
            while (c.moveToNext()) {
                if (c.getString(0) == name) return true
                if (clean(c.getString(1).orEmpty()) != want || want.isBlank()) continue
                val artist = c.getString(2).orEmpty().lowercase()
                val sameSinger = singers.any { artist.contains(it) }
                val seconds = c.getLong(3) / 1000
                val sameLength = track.durationMs > 0 && seconds > 0 && kotlin.math.abs(seconds - track.durationMs / 1000) <= 5
                if (sameSinger || sameLength) return true
            }
        }
        return false
    }

    /** "Kesariya (From \"Brahmastra\")" and "Kesariya - Official Video" both become "kesariya". */
    private fun clean(title: String) = title.lowercase()
        .replace(Regex("""\(.*?\)|\[.*?]"""), " ")
        .substringBefore(" - ")
        .replace(Regex("[^\\p{L}\\p{N}]+"), " ")
        .trim()

    private fun mime(ext: String) = when (ext.lowercase()) {
        "mp3" -> "audio/mpeg"
        "ogg" -> "audio/ogg"
        "flac" -> "audio/flac"
        "webm" -> "audio/webm"
        else -> "audio/mp4"
    }
}
