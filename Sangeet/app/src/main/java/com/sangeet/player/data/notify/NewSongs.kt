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
import java.util.Calendar
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.flow.first

/**
 * "🆕 New from Arijit Singh": a notification when a singer you play most has a new song on JioSaavn (twice a day,
 * when online). The first check only remembers what's there, so old songs never ring. Tapping it plays the song
 * (sangeet://play deep link). Settings → "New song alerts" turns it off.
 */
object NewSongs {
    private const val WORK = "new_songs"
    private const val CHANNEL = "new_songs"
    private const val PREFS = "new_songs"

    fun schedule(context: Context, enabled: Boolean) {
        val wm = WorkManager.getInstance(context)
        if (!enabled) { wm.cancelUniqueWork(WORK); return }
        wm.enqueueUniquePeriodicWork(
            WORK, ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<Worker>(12, TimeUnit.HOURS)
                .setInitialDelay(20, TimeUnit.MINUTES)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build(),
        )
    }

    class Worker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {
        override suspend fun doWork(): Result {
            val c = (applicationContext as SangeetApplication).container
            val s = c.settings.settings.first()
            if (!s.newSongAlerts || s.offlineMode || !s.jiosaavnEnabled) return Result.success()
            val prefs = applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val known = prefs.getString("known", "").orEmpty().split(",").filter { it.isNotBlank() }.toMutableList()
            val firstRun = !prefs.getBoolean("started", false)
            val now = Calendar.getInstance()
            val since = now.get(Calendar.YEAR) - if (now.get(Calendar.MONTH) < 2) 1 else 0
            val singers = runCatching { c.database.listenDao().topArtists(0L, 8) }.getOrDefault(emptyList())
                .map { it.artist.substringBefore(",").trim() }.filter { it.isNotBlank() && !it.equals("Unknown", true) }.distinct().take(5)
            var shown = 0
            for (singer in singers) {
                val songs = runCatching { c.online.saavn.newSongsBy(singer, since, 6) }.getOrDefault(emptyList())
                val fresh = songs.filter { it.id !in known }
                known += fresh.map { it.id }
                if (!firstRun && fresh.isNotEmpty() && shown < 3) {
                    notify(applicationContext, singer, fresh.first().title, fresh.first().artist, fresh.size)
                    shown++
                }
            }
            prefs.edit().putBoolean("started", true).putString("known", known.takeLast(600).joinToString(",")).apply()
            return Result.success()
        }
    }

    private fun notify(context: Context, singer: String, title: String, artist: String, count: Int) = runCatching {
        val nm = context.getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(CHANNEL) == null) {
            nm.createNotificationChannel(NotificationChannel(CHANNEL, "New songs from your singers", NotificationManager.IMPORTANCE_DEFAULT))
        }
        val play = Uri.Builder().scheme("sangeet").authority("play")
            .appendQueryParameter("q", "$title $artist").appendQueryParameter("source", "jiosaavn").build()
        val tap = PendingIntent.getActivity(
            context, singer.hashCode(),
            Intent(Intent.ACTION_VIEW, play, context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        nm.notify(8100 + (singer.hashCode() and 0xff), NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle("🆕 New from $singer")
            .setContentText(if (count > 1) "$title and ${count - 1} more. Tap to play." else "$title. Tap to play.")
            .setAutoCancel(true)
            .setContentIntent(tap)
            .build())
    }
}
