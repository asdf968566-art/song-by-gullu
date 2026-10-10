package com.sangeet.player.data.notify

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.app.NotificationCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.sangeet.player.MainActivity
import com.sangeet.player.SangeetApplication
import com.sangeet.player.data.LibrarySync
import java.util.Calendar
import java.util.concurrent.TimeUnit
import kotlin.random.Random
import kotlinx.coroutines.flow.first

/**
 * "Song of the day" (owner, Oct 10): every morning around 9 a notification with one song picked for you from For You
 * that you haven't been shown before. Tapping it plays the song (sangeet://play?song=…). Settings can turn it off.
 */
object SongOfTheDay {
    private const val WORK = "song_of_the_day"
    private const val CHANNEL = "song_of_the_day"
    private const val PREFS = "song_of_the_day"

    fun schedule(context: Context, enabled: Boolean) {
        val wm = WorkManager.getInstance(context)
        if (!enabled) { wm.cancelUniqueWork(WORK); return }
        val now = Calendar.getInstance()
        val next = (now.clone() as Calendar).apply {
            set(Calendar.HOUR_OF_DAY, 9); set(Calendar.MINUTE, Random.nextInt(0, 40)); set(Calendar.SECOND, 0)
            if (!after(now)) add(Calendar.DAY_OF_YEAR, 1)
        }
        wm.enqueueUniquePeriodicWork(
            WORK, ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<Worker>(24, TimeUnit.HOURS)
                .setInitialDelay(next.timeInMillis - now.timeInMillis, TimeUnit.MILLISECONDS)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build(),
        )
    }

    class Worker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {
        override suspend fun doWork(): Result {
            val c = (applicationContext as SangeetApplication).container
            val s = c.settings.settings.first()
            if (!s.songOfTheDay || s.offlineMode) return Result.success()
            val prefs = applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val shown = prefs.getString("shown", "").orEmpty().split(",").filter { it.isNotBlank() }
            val pick = runCatching { c.recommendations.suggestions(limit = 30) }.getOrDefault(emptyList())
                .map { it.track }
                .firstOrNull { it.id !in shown && LibrarySync.encode(it) != null } ?: return Result.success()
            val code = LibrarySync.pack(LibrarySync.encode(pick)!!.toString())
            val play = Uri.parse("sangeet://play?song=$code")
            val tap = PendingIntent.getActivity(
                applicationContext, 9200,
                Intent(Intent.ACTION_VIEW, play, applicationContext, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            runCatching {
                val nm = applicationContext.getSystemService(NotificationManager::class.java)
                if (nm.getNotificationChannel(CHANNEL) == null) {
                    nm.createNotificationChannel(NotificationChannel(CHANNEL, "Song of the day", NotificationManager.IMPORTANCE_DEFAULT))
                }
                nm.notify(9201, NotificationCompat.Builder(applicationContext, CHANNEL)
                    .setSmallIcon(android.R.drawable.ic_media_play)
                    .setContentTitle("Song of the day")
                    .setContentText("${pick.title} · ${pick.artist.substringBefore(",")}")
                    .setAutoCancel(true)
                    .setContentIntent(tap)
                    .build())
            }
            prefs.edit().putString("shown", (shown + pick.id).takeLast(400).joinToString(",")).apply()
            return Result.success()
        }
    }
}
