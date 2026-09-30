package com.sangeet.player.data.download

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.sangeet.player.SangeetApplication
import com.sangeet.player.data.model.DownloadState
import java.util.concurrent.TimeUnit

/**
 * Smart downloads: din mein ek baar (Wi-Fi + charging pe) liked gaane aur Daily Mix
 * apne aap download — taaki bina internet ke bhi feed / mix chale.
 */
class SmartDownloadWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val c = (applicationContext as SangeetApplication).container
        if (!c.settings.current.smartDownloads || c.settings.current.offlineMode) return Result.success()
        val liked = c.library.favoritesOnce().filter { it.source.isOnline }.take(LIKED_LIMIT)
        val daily = runCatching { c.recommendations.refreshMixes().firstOrNull()?.tracks.orEmpty() }
            .getOrDefault(emptyList()).filter { it.source.isOnline }.take(MIX_LIMIT)
        val have = c.downloads.downloads.value
        (liked + daily).distinctBy { it.id }
            .filter { have[it.id]?.state != DownloadState.DONE }
            .forEach { runCatching { c.downloads.download(it) } }
        return Result.success()
    }

    companion object {
        private const val NAME = "smart_downloads"
        private const val LIKED_LIMIT = 50
        private const val MIX_LIMIT = 30

        fun schedule(context: Context, enabled: Boolean) {
            val wm = WorkManager.getInstance(context)
            if (!enabled) {
                wm.cancelUniqueWork(NAME)
                return
            }
            val req = PeriodicWorkRequestBuilder<SmartDownloadWorker>(24, TimeUnit.HOURS)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.UNMETERED)
                        .setRequiresCharging(true)
                        .setRequiresBatteryNotLow(true)
                        .build()
                )
                .build()
            wm.enqueueUniquePeriodicWork(NAME, ExistingPeriodicWorkPolicy.KEEP, req)
        }
    }
}
