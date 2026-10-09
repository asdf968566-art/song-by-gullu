package com.sangeet.player.data.update

import android.app.ActivityManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.media.AudioManager
import android.os.Build
import androidx.core.app.NotificationCompat
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
 * internet (every 6 h, and soon after the app opens) it checks the `latest` release, downloads a newer APK and
 * installs it with PackageInstaller once the app isn't on screen and nothing is playing.
 *
 * Android 12+ lets an app update itself without a tap (USER_ACTION_NOT_REQUIRED + UPDATE_PACKAGES_WITHOUT_USER_ACTION),
 * once "Install unknown apps" is allowed for Sangeet. Older Android, or when Android still wants a tap, gets a
 * notification "Update ready: tap to install". Settings → App update → "Update automatically" turns it off.
 */
object AutoUpdate {
    private const val WORK = "auto_update"
    private const val CHANNEL = "updates"
    private const val NOTIFY_ID = 7301
    const val ACTION_STATUS = "com.sangeet.player.UPDATE_STATUS"

    fun schedule(context: Context, enabled: Boolean) {
        val wm = WorkManager.getInstance(context)
        if (!enabled) {
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
                if (busy(applicationContext)) {
                    // Don't close the app under the listener: try again in a while.
                    android.util.Log.i("Sangeet", "auto-update: build ${update.build} ready, waiting until the app isn't in use")
                    soon(applicationContext, 20)
                    return Result.success()
                }
                android.util.Log.i("Sangeet", "auto-update: installing build ${update.build}")
                install(applicationContext, file, update.build)
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

    /** On screen, or music playing: installing now would close the app. */
    private fun busy(context: Context): Boolean {
        val me = ActivityManager.RunningAppProcessInfo()
        ActivityManager.getMyMemoryState(me)
        val onScreen = me.importance <= ActivityManager.RunningAppProcessInfo.IMPORTANCE_VISIBLE
        val playing = context.getSystemService(AudioManager::class.java)?.isMusicActive == true
        return onScreen || playing
    }

    /** Hands the APK to Android's PackageInstaller; the result comes to [StatusReceiver]. */
    fun install(context: Context, file: File, build: Int) {
        val pm = context.packageManager
        // Only our own app, and only a newer one.
        val info = pm.getPackageArchiveInfo(file.path, 0)
        if (info == null || info.packageName != context.packageName || versionOf(info) <= BuildConfig.VERSION_CODE) {
            file.delete()
            throw IllegalStateException("downloaded file is not a newer Sangeet")
        }
        val installer = pm.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            setAppPackageName(context.packageName)
            setSize(file.length())
            if (Build.VERSION.SDK_INT >= 31) setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
        }
        val id = installer.createSession(params)
        installer.openSession(id).use { session ->
            session.openWrite("sangeet.apk", 0, file.length()).use { out ->
                file.inputStream().use { it.copyTo(out) }
                session.fsync(out)
            }
            val status = Intent(context, StatusReceiver::class.java).setAction(ACTION_STATUS).putExtra("build", build)
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0)
            session.commit(PendingIntent.getBroadcast(context, id, status, flags).intentSender)
        }
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

    /** What Android did with the update. */
    class StatusReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val build = intent.getIntExtra("build", 0)
            when (val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)) {
                PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                    // Android wants one tap (older Android, or "Install unknown apps" not allowed yet).
                    @Suppress("DEPRECATION")
                    val confirm = (if (Build.VERSION.SDK_INT >= 33) intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
                        else intent.getParcelableExtra(Intent.EXTRA_INTENT)) ?: return
                    confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    val tap = PendingIntent.getActivity(context, build, confirm, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
                    post(context, "Sangeet update ready", "Build $build is downloaded. Tap to install.", tap)
                }
                PackageInstaller.STATUS_SUCCESS -> android.util.Log.i("Sangeet", "auto-update: installed build $build")
                else -> android.util.Log.w("Sangeet", "auto-update: install failed ($status) ${intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)}")
            }
        }
    }

    /** After the app was updated (by itself or by hand): say so, and keep checking. */
    class UpdatedReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) return
            context.getSystemService(NotificationManager::class.java)?.cancel(NOTIFY_ID)
            post(context, "Sangeet updated", "Now on build ${BuildConfig.VERSION_CODE}. Tap to see what's new.", openApp(context))
        }
    }
}
