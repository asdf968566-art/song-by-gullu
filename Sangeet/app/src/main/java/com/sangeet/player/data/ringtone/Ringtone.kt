package com.sangeet.player.data.ringtone

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.provider.Settings
import com.sangeet.player.AppContainer
import com.sangeet.player.data.lyrics.LyricLine
import com.sangeet.player.data.model.AudioQuality
import com.sangeet.player.data.model.DownloadState
import com.sangeet.player.data.model.SourceType
import com.sangeet.player.data.model.Track
import com.sangeet.player.data.remote.Http
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.nio.ByteBuffer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request

/**
 * A song's best part as the phone's ringtone, alarm or notification sound (owner, Oct 11). 30 seconds are copied out
 * of the song as they are (no re-encoding: AAC → .m4a, Opus / Vorbis → .webm, MP3 → .mp3) into Ringtones/Sangeet,
 * and set with the "Modify system settings" permission. The part starts where the chorus is first sung (the lyrics
 * line sung most often), else 30% into the song; the user can move it.
 */
object Ringtone {
    const val CLIP_MS = 30_000L
    private const val CHUNK = 1L shl 20

    enum class Use(val label: String, val type: Int) {
        RINGTONE("Ringtone", RingtoneManager.TYPE_RINGTONE),
        ALARM("Alarm", RingtoneManager.TYPE_ALARM),
        NOTIFICATION("Notification", RingtoneManager.TYPE_NOTIFICATION),
    }

    /** Radio stations can't be cut; everything else can. */
    fun possible(track: Track) = !(track.source == SourceType.URL && track.sourceId.startsWith("radio-"))

    /** The song as a file: its download, the phone's own file, or fetched now (1 MB pieces) into the cache. */
    suspend fun source(c: AppContainer, track: Track, onProgress: (Int) -> Unit): Uri = withContext(Dispatchers.IO) {
        c.database.downloadDao().get(track.id)?.takeIf { it.state == DownloadState.DONE.name }
            ?.filePath?.let(::File)?.takeIf { it.exists() }?.let { return@withContext Uri.fromFile(it) }
        if (track.source == SourceType.LOCAL) return@withContext Uri.parse(track.streamUrl ?: throw IOException("This file is gone"))
        val dir = File(c.appContext.cacheDir, "ringtone").apply { mkdirs() }
        val file = File(dir, track.id.replace(Regex("[^A-Za-z0-9_-]"), "_") + ".song")
        if (file.length() > 0) return@withContext Uri.fromFile(file)
        val url = c.online.downloadUrl(track, AudioQuality.MEDIUM) ?: throw IOException("This song can't be fetched now")
        dir.listFiles()?.forEach { it.delete() } // only the last song is kept
        fetch(url, file, onProgress)
        Uri.fromFile(file)
    }

    private fun fetch(url: String, file: File, onProgress: (Int) -> Unit) {
        val tmp = File(file.path + ".part")
        var done = 0L
        var total = -1L
        FileOutputStream(tmp).use { out ->
            val buf = ByteArray(64 * 1024)
            // One piece from where we are; true when the server sent the whole file at once (or the last piece).
            fun piece(): Boolean {
                val req = Request.Builder().url(url).header("Range", "bytes=$done-${done + CHUNK - 1}").build()
                return Http.client.newCall(req).execute().use { res ->
                    if (!res.isSuccessful) throw IOException("HTTP ${res.code}")
                    val body = res.body ?: throw IOException("No data")
                    val partial = res.code == 206
                    if (!partial && done > 0) {
                        out.channel.truncate(0)
                        out.channel.position(0)
                        done = 0
                    }
                    total = if (partial) res.header("Content-Range")?.substringAfter('/')?.toLongOrNull() ?: -1L else body.contentLength()
                    var got = 0L
                    body.byteStream().use { input ->
                        while (true) {
                            val n = input.read(buf)
                            if (n < 0) break
                            out.write(buf, 0, n)
                            got += n
                            done += n
                        }
                    }
                    if (got == 0L) throw IOException("No data")
                    if (total > 0) onProgress((done * 100 / total).toInt().coerceAtMost(100))
                    !partial || (total < 0 && got < CHUNK)
                }
            }
            while (total < 0 || done < total) {
                var tries = 0
                var whole: Boolean? = null
                while (whole == null) {
                    whole = try {
                        piece()
                    } catch (e: IOException) {
                        if (++tries > 3) throw e
                        Thread.sleep(1000L * tries)
                        null
                    }
                }
                if (whole == true) break
            }
        }
        if (!tmp.renameTo(file)) throw IOException("Couldn't save the song")
    }

    /** The song's length, read from the file. */
    fun durationMs(context: Context, src: Uri): Long = runCatching {
        val ex = MediaExtractor()
        try {
            ex.setDataSource(context, src, null)
            (0 until ex.trackCount).map { ex.getTrackFormat(it) }
                .firstOrNull { it.getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true && it.containsKey(MediaFormat.KEY_DURATION) }
                ?.getLong(MediaFormat.KEY_DURATION)?.div(1000) ?: 0L
        } finally {
            ex.release()
        }
    }.getOrDefault(0L)

    /** Where the best part starts: a little before the chorus is first sung (the line sung most often), else 30% in. */
    fun bestStart(lines: List<LyricLine>, durationMs: Long): Long {
        val key = { s: String -> s.lowercase().replace(Regex("[^\\p{L}\\p{N}]+"), " ").trim() }
        val sung = lines.filter { key(it.text).length >= 8 }
        val chorus = sung.groupingBy { key(it.text) }.eachCount().filter { it.value >= 2 }.maxByOrNull { it.value }?.key
        val at = chorus?.let { k -> (sung.firstOrNull { key(it.text) == k && it.timeMs >= 15_000 } ?: sung.first { key(it.text) == k }).timeMs - 500 }
        return (at ?: (durationMs * 0.3).toLong()).coerceIn(0L, (durationMs - CLIP_MS).coerceAtLeast(0L))
    }

    /** [lengthMs] of the song from [startMs], copied as it is into [dir]/part.<ext>. */
    fun cut(context: Context, src: Uri, startMs: Long, lengthMs: Long, dir: File): File {
        dir.mkdirs()
        val ex = MediaExtractor()
        try {
            ex.setDataSource(context, src, null)
            val track = (0 until ex.trackCount).firstOrNull { ex.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true }
                ?: throw IOException("There's no sound in this song's file")
            val format = ex.getTrackFormat(track)
            val mime = format.getString(MediaFormat.KEY_MIME).orEmpty()
            ex.selectTrack(track)
            val startUs = startMs * 1000
            val endUs = (startMs + lengthMs) * 1000
            ex.seekTo(startUs, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)
            val size = if (format.containsKey(MediaFormat.KEY_MAX_INPUT_SIZE)) format.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE) else 0
            val buf = ByteBuffer.allocate(size.coerceAtLeast(512 * 1024))
            return when (mime) {
                MediaFormat.MIMETYPE_AUDIO_MPEG -> {
                    // MP3 is a row of frames: the frames of the part, one after another, are a playable file.
                    val out = File(dir, "part.mp3")
                    FileOutputStream(out).use { o ->
                        val bytes = ByteArray(buf.capacity())
                        while (true) {
                            val n = ex.readSampleData(buf, 0)
                            if (n < 0 || ex.sampleTime > endUs) break
                            buf.position(0)
                            buf.get(bytes, 0, n)
                            o.write(bytes, 0, n)
                            ex.advance()
                        }
                    }
                    out
                }
                MediaFormat.MIMETYPE_AUDIO_AAC, MediaFormat.MIMETYPE_AUDIO_OPUS, MediaFormat.MIMETYPE_AUDIO_VORBIS -> {
                    val aac = mime == MediaFormat.MIMETYPE_AUDIO_AAC
                    val out = File(dir, if (aac) "part.m4a" else "part.webm")
                    val muxer = MediaMuxer(out.path, if (aac) MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4 else MediaMuxer.OutputFormat.MUXER_OUTPUT_WEBM)
                    try {
                        val t = muxer.addTrack(format)
                        muxer.start()
                        val info = MediaCodec.BufferInfo()
                        var first = -1L
                        var written = 0
                        while (true) {
                            val n = ex.readSampleData(buf, 0)
                            val time = ex.sampleTime
                            if (n < 0 || time > endUs) break
                            if (first < 0) first = time
                            val key = (ex.sampleFlags and MediaExtractor.SAMPLE_FLAG_SYNC) != 0
                            info.set(0, n, time - first, if (key) MediaCodec.BUFFER_FLAG_KEY_FRAME else 0)
                            muxer.writeSampleData(t, buf, info)
                            written++
                            ex.advance()
                        }
                        if (written == 0) throw IOException("That part of the song is empty")
                        muxer.stop()
                    } finally {
                        runCatching { muxer.release() }
                    }
                    out
                }
                else -> throw IOException("This song's format can't be made a ringtone")
            }
        } finally {
            ex.release()
        }
    }

    /** Puts the part in Ringtones/Sangeet (shown in the phone's sound pickers too). Returns its media Uri. */
    fun save(context: Context, part: File, track: Track): Uri {
        val ext = part.extension
        val name = "${track.title} (Sangeet)".replace(Regex("""[\\/:*?"<>|]"""), " ").take(100).trim()
        val values = ContentValues().apply {
            put(MediaStore.Audio.Media.DISPLAY_NAME, "$name.$ext")
            put(MediaStore.Audio.Media.TITLE, track.title)
            put(MediaStore.Audio.Media.ARTIST, track.artist)
            put(MediaStore.Audio.Media.MIME_TYPE, when (ext) { "mp3" -> "audio/mpeg"; "webm" -> "audio/webm"; else -> "audio/mp4" })
            put(MediaStore.Audio.Media.IS_RINGTONE, 1)
            put(MediaStore.Audio.Media.IS_ALARM, 1)
            put(MediaStore.Audio.Media.IS_NOTIFICATION, 1)
            put(MediaStore.Audio.Media.IS_MUSIC, 0)
        }
        val resolver = context.contentResolver
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val collection = MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            val folder = "${Environment.DIRECTORY_RINGTONES}/Sangeet/"
            // The same song made again replaces the old one (our own files only).
            runCatching {
                resolver.delete(collection, "${MediaStore.Audio.Media.RELATIVE_PATH}=? AND ${MediaStore.Audio.Media.DISPLAY_NAME}=?", arrayOf(folder, "$name.$ext"))
            }
            values.put(MediaStore.Audio.Media.RELATIVE_PATH, folder)
            values.put(MediaStore.Audio.Media.IS_PENDING, 1)
            val uri = resolver.insert(collection, values) ?: throw IOException("Couldn't save the ringtone")
            try {
                resolver.openOutputStream(uri)?.use { out -> part.inputStream().use { it.copyTo(out) } } ?: throw IOException("Couldn't save the ringtone")
                resolver.update(uri, ContentValues().apply { put(MediaStore.Audio.Media.IS_PENDING, 0) }, null, null)
            } catch (e: Exception) {
                resolver.delete(uri, null, null)
                throw e
            }
            return uri
        }
        // Android 8 and 9: the file goes in the Ringtones folder (storage permission), then into the phone's media list.
        @Suppress("DEPRECATION")
        val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_RINGTONES), "Sangeet").apply { mkdirs() }
        val file = File(dir, "$name.$ext")
        part.copyTo(file, overwrite = true)
        @Suppress("DEPRECATION")
        val data = MediaStore.MediaColumns.DATA
        values.put(data, file.absolutePath)
        resolver.delete(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, "$data=?", arrayOf(file.absolutePath))
        return resolver.insert(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, values) ?: throw IOException("Couldn't save the ringtone")
    }

    /** Android needs "Modify system settings" for an app to change the ringtone. */
    fun canSet(context: Context) = Settings.System.canWrite(context)

    fun askToSet(context: Context) {
        context.startActivity(
            Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, Uri.parse("package:${context.packageName}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }

    fun set(context: Context, uri: Uri, use: Use) = RingtoneManager.setActualDefaultRingtoneUri(context, use.type, uri)
}
