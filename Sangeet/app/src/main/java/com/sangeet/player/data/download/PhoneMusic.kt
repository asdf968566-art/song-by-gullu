package com.sangeet.player.data.download

import com.sangeet.player.data.Rx
import android.content.ContentResolver
import android.content.ContentUris
import android.content.ContentValues
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.sangeet.player.data.model.Track
import com.sangeet.player.data.remote.Http
import java.io.ByteArrayOutputStream
import java.io.File
import okhttp3.Request
import java.nio.ByteBuffer

/**
 * Puts a copy of a downloaded song in the phone's Music/Sangeet folder (Android 10+, no permission needed).
 * That copy stays after the app is uninstalled and shows up in the file manager and other music apps.
 */
object PhoneMusic {
    const val FOLDER = "Sangeet"

    fun supported() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q

    /** Returns the new file's content Uri, or null if it already exists / isn't possible. Never throws. */
    fun save(context: Context, track: Track, file: File): Uri? {
        if (!supported() || !file.exists()) return null
        // Android 17 refuses audio/webm in Music (report #93: the app crashed when this was turned on), so a WebM
        // (Opus) download goes in as Ogg, without re-encoding.
        val ogg = if (file.extension.equals("webm", ignoreCase = true)) toOgg(context, file) ?: return null else null
        return try {
            saveAs(context, track, ogg ?: file)
        } catch (e: Exception) {
            android.util.Log.w("Sangeet", "save to phone failed: ${e.javaClass.simpleName}: ${e.message}")
            null
        } finally {
            ogg?.delete()
        }
    }

    private fun saveAs(context: Context, track: Track, file: File): Uri? {
        val ext = file.extension.ifBlank { "m4a" }
        val name = "${track.artist} - ${track.title}".replace(Rx.BAD_FILE_CHARS, " ").take(120).trim() + ".$ext"
        val resolver = context.contentResolver
        val collection = MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val relPath = "${Environment.DIRECTORY_MUSIC}/$FOLDER/"
        val have = existing(context, collection, relPath, name, track)
        // A copy saved before Nov 5 has no name, singer or cover in it: only that one is written again.
        if (have != null && !(have.untagged && have.sameName)) return null
        // An .m4a gets the song's name, singer, album and cover inside, so the Files app and music players show
        // them (owner, Nov 5: they showed a music note and "<unknown>").
        val tagged = if (isMp4(file)) tagged(context, track, file) else null
        try {
            if (have != null) {
                if (tagged != null) {
                    resolver.openOutputStream(have.uri, "wt")?.use { out -> tagged.inputStream().use { it.copyTo(out) } }
                    android.util.Log.i("Sangeet", "phone copy tagged: $name")
                }
                return null
            }
            return insert(resolver, collection, relPath, name, ext, track, tagged ?: file)
        } finally {
            tagged?.delete()
        }
    }

    private fun insert(resolver: ContentResolver, collection: Uri, relPath: String, name: String, ext: String, track: Track, file: File): Uri? {
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

    /** The Opus track of a WebM file, put in an Ogg file (MediaMuxer, Android 10+); null if it isn't Opus. */
    private fun toOgg(context: Context, file: File): File? = runCatching {
        val out = File(context.cacheDir, "phone-${file.nameWithoutExtension}.ogg")
        val ex = MediaExtractor()
        try {
            ex.setDataSource(file.path)
            val track = (0 until ex.trackCount)
                .firstOrNull { ex.getTrackFormat(it).getString(MediaFormat.KEY_MIME) == MediaFormat.MIMETYPE_AUDIO_OPUS }
                ?: return@runCatching null
            ex.selectTrack(track)
            val mux = MediaMuxer(out.path, MediaMuxer.OutputFormat.MUXER_OUTPUT_OGG)
            try {
                val t = mux.addTrack(ex.getTrackFormat(track))
                mux.start()
                val buf = ByteBuffer.allocate(1 shl 18)
                val info = MediaCodec.BufferInfo()
                while (true) {
                    val n = ex.readSampleData(buf, 0)
                    if (n < 0) break
                    val sync = ex.sampleFlags and MediaExtractor.SAMPLE_FLAG_SYNC != 0
                    info.set(0, n, ex.sampleTime, if (sync) MediaCodec.BUFFER_FLAG_KEY_FRAME else 0)
                    mux.writeSampleData(t, buf, info)
                    ex.advance()
                }
                mux.stop()
            } finally {
                mux.release()
            }
        } finally {
            ex.release()
        }
        out.takeIf { it.length() > 0 }
    }.onFailure { android.util.Log.w("Sangeet", "save to phone: WebM to Ogg failed: ${it.message}") }.getOrNull()

    /** The song as an .m4a with its tags, in the cache; null if that didn't work (the plain file is saved then). */
    private fun tagged(context: Context, track: Track, file: File): File? {
        val out = File(context.cacheDir, "phone-tags-${file.nameWithoutExtension}.m4a")
        if (Mp4Tags.write(file, out, track.title, track.artist, track.album, cover(track))) return out
        out.delete()
        return null
    }

    /** The song's cover as a JPEG of at most 600 px: the downloaded picture, else from the internet. */
    private fun cover(track: Track): ByteArray? = runCatching {
        val url = track.artworkUrl ?: return@runCatching null
        val raw = when {
            url.startsWith("file:") -> Uri.parse(url).path?.let(::File)?.takeIf { it.exists() }?.readBytes()
            url.startsWith("/") -> File(url).takeIf { it.exists() }?.readBytes()
            url.startsWith("http") -> Http.client.newCall(Request.Builder().url(url.replace("150x150", "500x500")).build())
                .execute().use { res -> if (res.isSuccessful) res.body?.bytes() else null }
            else -> null
        } ?: return@runCatching null
        val bmp = BitmapFactory.decodeByteArray(raw, 0, raw.size) ?: return@runCatching null
        val k = minOf(1f, 600f / maxOf(bmp.width, bmp.height))
        val pic = if (k < 1f) {
            Bitmap.createScaledBitmap(bmp, (bmp.width * k).toInt().coerceAtLeast(1), (bmp.height * k).toInt().coerceAtLeast(1), true)
        } else bmp
        ByteArrayOutputStream().also { pic.compress(Bitmap.CompressFormat.JPEG, 90, it) }.toByteArray()
    }.getOrNull()

    /** A copy already in Music/Sangeet; [untagged]: saved before Nov 5, with no singer in the file. */
    private class Saved(val uri: Uri, val sameName: Boolean, val untagged: Boolean)

    /**
     * The same song must not be saved twice, even when it came once from YouTube and once from JioSaavn
     * with a different artist line: same file name, or same clean title plus same singer / same length.
     */
    private fun existing(context: Context, collection: Uri, relPath: String, name: String, track: Track): Saved? {
        val want = clean(track.title)
        val singers = track.artist.lowercase().split(',', '&').map { it.trim() }.filter { it.length > 2 }
        context.contentResolver.query(
            collection,
            arrayOf(
                MediaStore.Audio.Media._ID, MediaStore.Audio.Media.DISPLAY_NAME, MediaStore.Audio.Media.TITLE,
                MediaStore.Audio.Media.ARTIST, MediaStore.Audio.Media.DURATION,
            ),
            "${MediaStore.Audio.Media.RELATIVE_PATH}=?", arrayOf(relPath), null,
        )?.use { c ->
            while (c.moveToNext()) {
                val artist = c.getString(3).orEmpty()
                fun found(sameName: Boolean) = Saved(
                    ContentUris.withAppendedId(collection, c.getLong(0)),
                    sameName,
                    untagged = artist.isBlank() || artist == MediaStore.UNKNOWN_STRING,
                )
                if (c.getString(1) == name) return found(sameName = true)
                if (clean(c.getString(2).orEmpty()) != want || want.isBlank()) continue
                val sameSinger = singers.any { artist.lowercase().contains(it) }
                val seconds = c.getLong(4) / 1000
                val sameLength = track.durationMs > 0 && seconds > 0 && kotlin.math.abs(seconds - track.durationMs / 1000) <= 5
                if (sameSinger || sameLength) return found(sameName = false)
            }
        }
        return null
    }

    /** "Kesariya (From \"Brahmastra\")" and "Kesariya - Official Video" both become "kesariya". */
    private fun clean(title: String) = title.lowercase()
        .replace(Rx.BRACKETS, " ")
        .substringBefore(" - ")
        .replace(Rx.NON_WORD, " ")
        .trim()

    /** An MP4 / M4A file starts with its "ftyp" box, whatever its name says. */
    private fun isMp4(file: File): Boolean = runCatching {
        file.inputStream().use { val head = ByteArray(8); it.read(head) == 8 && String(head, 4, 4, Charsets.ISO_8859_1) == "ftyp" }
    }.getOrDefault(false)

    private fun mime(ext: String) = when (ext.lowercase()) {
        "mp3" -> "audio/mpeg"
        "ogg" -> "audio/ogg"
        "flac" -> "audio/flac"
        "webm" -> "audio/webm"
        else -> "audio/mp4"
    }
}
