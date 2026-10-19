package com.sangeet.player.data.update

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.FileProvider
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.sangeet.player.BuildConfig
import com.sangeet.player.MainActivity
import com.sangeet.player.SangeetApplication
import java.io.File
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.flow.first

/**
 * Updates on its own (owner, Oct 9: "internet mile, update check ho aur apne aap update ho jaye"): whenever there's
 * internet (every 6 h, and soon after the app opens) it checks the `latest` release and downloads a newer APK.
 * Then a notification "Sangeet update ready, tap to install" opens Android's installer, and the app shows the same
 * once ([AppUpdater.readyApk]). Settings → App update → "Update automatically" turns it off.
 *
 * It used to install silently (USER_ACTION_NOT_REQUIRED + UPDATE_PACKAGES_WITHOUT_USER_ACTION). An app that installs
 * APKs from the internet without asking looks like a harmful one to Play Protect, which warned the owner (Oct 19), so
 * the install is always the listener's tap now.
 */
object AutoUpdate {
    private const val WORK = "auto_update"
    private const val CHANNEL = "updates"
    private const val NOTIFY_ID = 7301

    fun schedule(context: Context, enabled: Boolean) {
        val wm = WorkManager.getInstance(context)
        if (!enabled || BuildConfig.PLAY_STORE) { // Google Play updates the Play version itself
            wm.cancelUniqueWork(WORK)
            wm.cancelUniqueWork("${WORK}_now")
            return
        }
        val online = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
        wm.enqueueUniquePeriodicWork(
            WORK, ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<Worker>(6, TimeUnit.HOURS).setConstraints(online).build(),
        )
        soon(context, 1)
    }

    /** One check [minutes] from now (app opened, or waiting until the app is in the background). */
    fun soon(context: Context, minutes: Long) {
        if (BuildConfig.PLAY_STORE) return
        val online = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            "${WORK}_now", ExistingWorkPolicy.REPLACE,
            OneTimeWorkRequestBuilder<Worker>().setInitialDelay(minutes, TimeUnit.MINUTES).setConstraints(online).build(),
        )
    }

    class Worker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {
        override suspend fun doWork(): Result {
            val c = (applicationContext as SangeetApplication).container
            val s = c.settings.settings.first()
            if (!s.autoUpdate || s.offlineMode) return Result.success()
            return try {
                val update = c.updater.latest() ?: return Result.success()
                if (update.build <= c.updater.currentBuild) return Result.success()
                if (s.downloadOnWifiOnly && !unmetered(applicationContext)) return Result.success()
                val file = c.updater.apkFor(update)
                if (!isNewerSangeet(applicationContext, file)) {
                    file.delete()
                    throw IllegalStateException("downloaded file is not a newer Sangeet")
                }
                android.util.Log.i("Sangeet", "auto-update: build ${update.build} downloaded, asking to install")
                notifyReady(applicationContext, file, update.build)
                Result.success()
            } catch (e: Exception) {
                android.util.Log.w("Sangeet", "auto-update: ${e.message}")
                if (runAttemptCount < 2) Result.retry() else Result.success()
            }
        }
    }

    private fun unmetered(context: Context): Boolean {
        val cm = context.getSystemService(android.net.ConnectivityManager::class.java)
        return cm?.isActiveNetworkMetered == false
    }

    /** Only our own app, and only a newer one. */
    fun isNewerSangeet(context: Context, file: File): Boolean {
        val info = context.packageManager.getPackageArchiveInfo(file.path, 0) ?: return false
        return info.packageName == context.packageName && versionOf(info) > BuildConfig.VERSION_CODE
    }

    /** "Sangeet update ready": a tap opens Android's installer (once per build, so it doesn't nag every 6 h). */
    private fun notifyReady(context: Context, file: File, build: Int) {
        val prefs = context.getSharedPreferences("updater", Context.MODE_PRIVATE)
        if (prefs.getInt("ready_notified", -1) == build) return
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
        val view = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        val tap = PendingIntent.getActivity(context, build, view, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        post(context, "Sangeet update ready", "Build $build is downloaded. Tap to install.", tap)
        prefs.edit().putInt("ready_notified", build).apply()
    }

    @Suppress("DEPRECATION")
    private fun versionOf(info: android.content.pm.PackageInfo): Long =
        if (Build.VERSION.SDK_INT >= 28) info.longVersionCode else info.versionCode.toLong()

    private fun post(context: Context, title: String, text: String, tap: PendingIntent) = runCatching {
        val nm = context.getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(CHANNEL) == null) {
            nm.createNotificationChannel(NotificationChannel(CHANNEL, "App updates", NotificationManager.IMPORTANCE_DEFAULT))
        }
        nm.notify(NOTIFY_ID, NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle(title)
            .setContentText(text)
            .setAutoCancel(true)
            .setContentIntent(tap)
            .build())
    }

    private fun openApp(context: Context) = PendingIntent.getActivity(
        context, 0,
        Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    /** After the app was updated (by itself or by hand): say so, and keep checking. */
    class UpdatedReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) return
            context.getSystemService(NotificationManager::class.java)?.cancel(NOTIFY_ID)
            post(context, "Sangeet updated", "Now on build ${BuildConfig.VERSION_CODE}. Tap to see what's new.", openApp(context))
        }
    }
}
