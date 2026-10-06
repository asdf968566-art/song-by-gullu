package com.sangeet.player.data.download

import com.sangeet.player.MainActivity
import android.content.Intent
import android.app.PendingIntent
import kotlinx.coroutines.flow.combine
import androidx.work.OutOfQuotaPolicy
import androidx.work.ForegroundInfo
import androidx.core.app.NotificationCompat
import android.os.Build
import android.content.pm.ServiceInfo
import android.app.NotificationManager
import android.app.NotificationChannel
import android.content.Context
import android.net.Uri
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.sangeet.player.SangeetApplication
import com.sangeet.player.data.LibraryRepository
import com.sangeet.player.data.db.DownloadDao
import com.sangeet.player.data.db.DownloadEntity
import com.sangeet.player.data.db.TrackEntity
import com.sangeet.player.data.model.AudioQuality
import com.sangeet.player.data.model.DownloadInfo
import com.sangeet.player.data.model.DownloadState
import com.sangeet.player.data.model.SourceType
import com.sangeet.player.data.model.Track
import com.sangeet.player.data.remote.Http
import com.sangeet.player.data.settings.SettingsRepository
import java.io.File
import java.io.IOException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.withContext
import okhttp3.Request
import androidx.work.BackoffPolicy
import androidx.work.WorkRequest
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.delay

/**
 * Online gaane app ke private storage mein save hote hain (Spotify offline jaisa),
 * phir bina internet ke app ke andar bajte hain.
 */
class DownloadRepository(
    private val context: Context,
    private val dao: DownloadDao,
    private val library: LibraryRepository,
    private val settings: SettingsRepository,
    scope: CoroutineScope,
) {
    val downloads: StateFlow<Map<String, DownloadInfo>> = dao.observeAll().map { list ->
        list.associate { e ->
            e.trackId to DownloadInfo(
                trackId = e.trackId,
                state = runCatching { DownloadState.valueOf(e.state) }.getOrDefault(DownloadState.FAILED),
                progress = e.progress,
                filePath = e.filePath,
                quality = runCatching { AudioQuality.valueOf(e.quality) }.getOrDefault(AudioQuality.HIGH),
            )
        }
    }.stateIn(scope, SharingStarted.Eagerly, emptyMap())

    val downloadedTracks: Flow<List<Track>> = dao.observeDownloadedTracks().map { it.map(TrackEntity::toTrack) }

    /** Every download with its song and status (downloading, waiting, failed, done), newest first. */
    val items: Flow<List<Pair<Track, DownloadInfo>>> = combine(dao.observeAllDownloadTracks(), downloads) { tracks, map ->
        tracks.mapNotNull { e -> map[e.id]?.let { e.toTrack() to it } }
    }

    /** How many songs are downloading or waiting right now. */
    val activeCount: Flow<Int> = downloads.map { m -> m.values.count { it.state == DownloadState.QUEUED || it.state == DownloadState.DOWNLOADING } }

    /** Download ho chuka ho to local file path. */
    fun localPath(trackId: String): String? {
        val info = downloads.value[trackId] ?: return null
        if (info.state != DownloadState.DONE) return null
        return info.filePath?.takeIf { File(it).exists() }
    }

    /** Starts a download. Returns false when this song (or the same song from another source) is already downloaded. */
    suspend fun download(track: Track): Boolean {
        if (track.source == SourceType.LOCAL) return false
        if (downloads.value[track.id]?.state == DownloadState.DONE) return false
        if (sameSongDownloaded(track)) {
            android.util.Log.i("Sangeet", "download skipped, same song already downloaded: ${track.title} (${track.artist})")
            return false
        }
        library.remember(listOf(track))
        val quality = settings.current.downloadQuality
        dao.upsert(DownloadEntity(track.id, DownloadState.QUEUED.name, 0, null, quality.name))
        val request = OneTimeWorkRequestBuilder<DownloadWorker>()
            .setInputData(workDataOf(DownloadWorker.KEY_TRACK_ID to track.id))
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(
                        if (settings.current.downloadOnWifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED
                    )
                    .build()
            )
            .addTag(TAG)
            // Runs right away even when the phone limits background work (Realme, Oppo, Xiaomi...).
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            // A failed try is retried after 10 s, not WorkManager's default 30 s, 60 s...
            .setBackoffCriteria(BackoffPolicy.LINEAR, WorkRequest.MIN_BACKOFF_MILLIS, TimeUnit.MILLISECONDS)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(workName(track.id), ExistingWorkPolicy.REPLACE, request)
        return true
    }

    /** The same song already downloaded from another source (e.g. YouTube vs JioSaavn): same clean title and singer or length. */
    private suspend fun sameSongDownloaded(track: Track): Boolean {
        val want = cleanTitle(track.title)
        if (want.isBlank()) return false
        val singers = track.artist.lowercase().split(',', '&').map { it.trim() }.filter { it.length > 2 }
        return downloads.value.values.filter { it.state == DownloadState.DONE && it.trackId != track.id }.any { info ->
            val other = library.find(info.trackId) ?: return@any false
            if (cleanTitle(other.title) != want) return@any false
            android.util.Log.i("Sangeet", "same title as download ${other.id}: '${other.title}' by ${other.artist}")
            val sameSinger = singers.any { other.artist.lowercase().contains(it) }
            val sameLength = track.durationMs > 0 && other.durationMs > 0 && kotlin.math.abs(track.durationMs - other.durationMs) <= 5_000
            sameSinger || sameLength
        }
    }

    private fun cleanTitle(t: String) = t.lowercase()
        .replace(Regex("""\(.*?\)|\[.*?]"""), " ")
        .substringBefore(" - ")
        .replace(Regex("[^\\p{L}\\p{N}]+"), " ")
        .trim()

    suspend fun downloadAll(tracks: List<Track>) = tracks.forEach { download(it) }

    /** Copies every finished download to Music/Sangeet (when "Save downloads to phone storage" is turned on). */
    suspend fun copyAllToPhone(): Int = withContext(Dispatchers.IO) {
        var n = 0
        for (info in downloads.value.values) {
            if (info.state != DownloadState.DONE) continue
            val file = info.filePath?.let(::File)?.takeIf { it.exists() } ?: continue
            val track = library.find(info.trackId) ?: continue
            if (PhoneMusic.save(context, track, file) != null) n++
        }
        n
    }

    suspend fun remove(trackId: String) {
        WorkManager.getInstance(context).cancelUniqueWork(workName(trackId))
        withContext(Dispatchers.IO) {
            dao.get(trackId)?.filePath?.let { File(it).delete() }
            File(dir(context), "${safe(trackId)}.jpg").delete()
        }
        dao.delete(trackId)
    }

    companion object {
        const val TAG = "downloads"
        fun workName(trackId: String) = "download_$trackId"
        fun dir(context: Context) = File(context.filesDir, "downloads").apply { mkdirs() }
        fun safe(id: String) = id.replace(Regex("[^A-Za-z0-9_-]"), "_")
    }
}

class DownloadWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val container = (applicationContext as SangeetApplication).container
        val trackId = inputData.getString(KEY_TRACK_ID) ?: return Result.failure()
        val dao = container.database.downloadDao()
        val entry = dao.get(trackId) ?: return Result.failure()
        val track = container.library.find(trackId) ?: return fail(dao, trackId)
        val quality = runCatching { AudioQuality.valueOf(entry.quality) }.getOrDefault(AudioQuality.HIGH)
        val url = withContext(Dispatchers.IO) {
            runCatching { container.online.downloadUrl(track, quality) }
                .onFailure { android.util.Log.w("Sangeet", "download: no stream for ${track.title}", it) }
                .getOrNull()
        } ?: return fail(dao, trackId)

        return try {
            dao.updateState(trackId, DownloadState.DOWNLOADING.name, 0)
            notifyProgress(track.title, 0)
            val file = fetch(url, trackId) { p ->
                dao.updateState(trackId, DownloadState.DOWNLOADING.name, p)
                notifyProgress(track.title, p)
            }
            notifyDone(track.title)
            dao.upsert(entry.copy(state = DownloadState.DONE.name, progress = 100, filePath = file.absolutePath))
            android.util.Log.i("Sangeet", "download done: ${track.title} (${file.length() / 1024} KB from ${Uri.parse(url).host})")
            if (container.settings.current.saveToPhone) {
                PhoneMusic.save(applicationContext, track, file)?.let { android.util.Log.i("Sangeet", "saved to phone: $it") }
            }

            // Offline ke liye cover art aur lyrics bhi save kar lo.
            saveArtwork(track)?.let { art -> container.library.remember(listOf(track.copy(artworkUrl = art))) }
            runCatching { container.lyrics.get(track) }
            Result.success()
        } catch (e: Exception) {
            android.util.Log.w("Sangeet", "download failed (try ${runAttemptCount + 1}): ${track.title}: ${e.message}")
            cancelNotification()
            if (runAttemptCount < 2) {
                dao.updateState(trackId, DownloadState.QUEUED.name, 0)
                Result.retry()
            } else {
                fail(dao, trackId)
            }
        }
    }

    /** Shown while downloading on older Android versions (where an expedited job needs a notification). */
    override suspend fun getForegroundInfo(): ForegroundInfo {
        val nm = applicationContext.getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(CHANNEL) == null) {
            nm.createNotificationChannel(NotificationChannel(CHANNEL, "Downloads", NotificationManager.IMPORTANCE_LOW))
        }
        val n = NotificationCompat.Builder(applicationContext, CHANNEL)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle("Downloading song")
            .setOngoing(true)
            .setSilent(true)
            .build()
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) ForegroundInfo(NOTIFICATION_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        else ForegroundInfo(NOTIFICATION_ID, n)
    }

    // ------------------------------------------------------------ progress in the notification shade

    private val nm get() = applicationContext.getSystemService(NotificationManager::class.java)
    private val notifyId get() = NOTIFICATION_ID + (inputData.getString(KEY_TRACK_ID)?.hashCode() ?: 0) % 1000

    private fun channel() {
        if (nm.getNotificationChannel(CHANNEL) == null) {
            nm.createNotificationChannel(NotificationChannel(CHANNEL, "Downloads", NotificationManager.IMPORTANCE_LOW))
        }
    }

    private fun openDownloads() = PendingIntent.getActivity(
        applicationContext, 0,
        Intent(applicationContext, MainActivity::class.java).setAction(MainActivity.ACTION_DOWNLOADS)
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun notifyProgress(title: String, pct: Int) = runCatching {
        channel()
        nm.notify(notifyId, NotificationCompat.Builder(applicationContext, CHANNEL)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle(title)
            .setContentText("Downloading… $pct%")
            .setProgress(100, pct, pct == 0)
            .setOngoing(true)
            .setSilent(true)
            .setContentIntent(openDownloads())
            .build())
    }

    private fun notifyDone(title: String) = runCatching {
        channel()
        nm.notify(notifyId, NotificationCompat.Builder(applicationContext, CHANNEL)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle(title)
            .setContentText("Downloaded — plays without internet")
            .setAutoCancel(true)
            .setSilent(true)
            .setContentIntent(openDownloads())
            .build())
    }

    private fun cancelNotification() = runCatching { nm.cancel(notifyId) }

    private suspend fun fail(dao: DownloadDao, trackId: String): Result {
        cancelNotification()
        dao.updateState(trackId, DownloadState.FAILED.name, 0)
        return Result.failure()
    }

    /**
     * Downloads in 1 MB pieces (HTTP Range). YouTube throttles one big request to a crawl; pieces stay fast.
     * Servers that ignore Range just send the whole file in the first answer, which works too.
     */
    private suspend fun fetch(url: String, trackId: String, onProgress: suspend (Int) -> Unit): File =
        withContext(Dispatchers.IO) {
            val dir = DownloadRepository.dir(applicationContext)
            var ext = "mp3"
            val tmp = File(dir, "${DownloadRepository.safe(trackId)}.part")
            var done = 0L
            var total = -1L
            var lastPct = -1
            tmp.outputStream().use { output ->
                val buf = ByteArray(64 * 1024)
                var resumable = true
                // One piece from where we are. A piece that breaks off is fetched again from the same byte
                // (a few quick tries), instead of failing the whole download and starting over later.
                suspend fun piece(): Pair<Boolean, Long> {
                    val req = Request.Builder().url(url).header("Range", "bytes=$done-${done + CHUNK - 1}").build()
                    return Http.client.newCall(req).execute().use { res ->
                        if (!res.isSuccessful) throw IOException("HTTP ${res.code}")
                        val body = res.body ?: throw IOException("Empty body")
                        body.contentType()?.subtype?.let { sub ->
                            ext = when {
                                "ogg" in sub -> "ogg"
                                "flac" in sub -> "flac"
                                "mp4" in sub || "m4a" in sub || "aac" in sub -> "m4a"
                                "webm" in sub -> "webm"
                                else -> ext
                            }
                        }
                        val partial = res.code == 206
                        resumable = partial || done == 0L
                        total = if (partial) res.header("Content-Range")?.substringAfter('/')?.toLongOrNull() ?: -1L
                        else body.contentLength()
                        var got = 0L
                        body.byteStream().use { input ->
                            while (true) {
                                if (isStopped) throw IOException("Cancelled")
                                val n = input.read(buf)
                                if (n < 0) break
                                output.write(buf, 0, n)
                                got += n
                                done += n
                                if (total > 0) {
                                    val pct = (done * 100 / total).toInt()
                                    if (pct >= lastPct + 2) { lastPct = pct; onProgress(pct) }
                                }
                            }
                        }
                        if (got == 0L) throw IOException("No data")
                        !partial to got // !partial: the whole file came in one go
                    }
                }
                while (total < 0 || done < total) {
                    if (isStopped) throw IOException("Cancelled")
                    var tries = 0
                    var result: Pair<Boolean, Long>? = null
                    while (result == null) {
                        result = try {
                            piece()
                        } catch (e: IOException) {
                            if (isStopped || !resumable || ++tries > 3) throw e
                            android.util.Log.w("Sangeet", "download piece broke at ${done / 1024} KB (${e.message}), again ($tries)")
                            delay(1000L * tries)
                            null
                        }
                    }
                    val (whole, got) = result!!
                    if (whole || (total < 0 && got < CHUNK)) break
                }
            }
            val out = File(dir, "${DownloadRepository.safe(trackId)}.$ext")
            if (!tmp.renameTo(out)) throw IOException("Could not save file")
            out
        }

    private suspend fun saveArtwork(track: Track): String? = withContext(Dispatchers.IO) {
        val url = track.artworkUrl?.takeIf { it.startsWith("http") } ?: return@withContext null
        runCatching {
            val file = File(DownloadRepository.dir(applicationContext), "${DownloadRepository.safe(track.id)}.jpg")
            Http.client.newCall(Request.Builder().url(url).build()).execute().use { res ->
                if (!res.isSuccessful) return@runCatching null
                res.body?.byteStream()?.use { input -> file.outputStream().use { input.copyTo(it) } }
            }
            Uri.fromFile(file).toString()
        }.getOrNull()
    }

    companion object {
        const val KEY_TRACK_ID = "track_id"
        private const val CHUNK = 1L shl 20
        private const val CHANNEL = "downloads"
        private const val NOTIFICATION_ID = 4711
    }
}
