package com.sangeet.player.data.download

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

    /** Download ho chuka ho to local file path. */
    fun localPath(trackId: String): String? {
        val info = downloads.value[trackId] ?: return null
        if (info.state != DownloadState.DONE) return null
        return info.filePath?.takeIf { File(it).exists() }
    }

    suspend fun download(track: Track) {
        if (track.source == SourceType.LOCAL) return
        if (downloads.value[track.id]?.state == DownloadState.DONE) return
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
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(workName(track.id), ExistingWorkPolicy.REPLACE, request)
    }

    suspend fun downloadAll(tracks: List<Track>) = tracks.forEach { download(it) }

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
        val url = container.online.streamUrl(track, quality) ?: return fail(dao, trackId)

        return try {
            dao.updateState(trackId, DownloadState.DOWNLOADING.name, 0)
            val file = fetch(url, trackId) { p -> dao.updateState(trackId, DownloadState.DOWNLOADING.name, p) }
            dao.upsert(entry.copy(state = DownloadState.DONE.name, progress = 100, filePath = file.absolutePath))

            // Offline ke liye cover art aur lyrics bhi save kar lo.
            saveArtwork(track)?.let { art -> container.library.remember(listOf(track.copy(artworkUrl = art))) }
            runCatching { container.lyrics.get(track) }
            Result.success()
        } catch (e: Exception) {
            if (runAttemptCount < 2) {
                dao.updateState(trackId, DownloadState.QUEUED.name, 0)
                Result.retry()
            } else {
                fail(dao, trackId)
            }
        }
    }

    private suspend fun fail(dao: DownloadDao, trackId: String): Result {
        dao.updateState(trackId, DownloadState.FAILED.name, 0)
        return Result.failure()
    }

    private suspend fun fetch(url: String, trackId: String, onProgress: suspend (Int) -> Unit): File =
        withContext(Dispatchers.IO) {
            val dir = DownloadRepository.dir(applicationContext)
            Http.client.newCall(Request.Builder().url(url).build()).execute().use { res ->
                if (!res.isSuccessful) throw IOException("HTTP ${res.code}")
                val body = res.body ?: throw IOException("Empty body")
                val ext = when {
                    body.contentType()?.subtype?.contains("ogg") == true -> "ogg"
                    body.contentType()?.subtype?.contains("flac") == true -> "flac"
                    body.contentType()?.subtype?.contains("mp4") == true -> "m4a"
                    else -> "mp3"
                }
                val out = File(dir, "${DownloadRepository.safe(trackId)}.$ext")
                val tmp = File(dir, out.name + ".part")
                val total = body.contentLength()
                var done = 0L
                var lastPct = -1
                body.byteStream().use { input ->
                    tmp.outputStream().use { output ->
                        val buf = ByteArray(64 * 1024)
                        while (true) {
                            if (isStopped) throw IOException("Cancelled")
                            val n = input.read(buf)
                            if (n < 0) break
                            output.write(buf, 0, n)
                            done += n
                            if (total > 0) {
                                val pct = (done * 100 / total).toInt()
                                if (pct >= lastPct + 5) {
                                    lastPct = pct
                                    onProgress(pct)
                                }
                            }
                        }
                    }
                }
                if (!tmp.renameTo(out)) throw IOException("Could not save file")
                out
            }
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
    }
}
