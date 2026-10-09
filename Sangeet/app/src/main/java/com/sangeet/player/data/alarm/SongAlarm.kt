package com.sangeet.player.data.alarm

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.sangeet.player.MainActivity
import com.sangeet.player.SangeetApplication
import com.sangeet.player.data.model.Track
import java.util.Calendar
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Wake up to your songs (owner's pick, Oct 9): every day at the set time the app plays your liked songs, a playlist or
 * a For You mix (downloaded songs when there's no internet). AlarmManager.setAlarmClock (shows in the status bar like
 * a clock alarm); set again after it rings, after a restart and after an update.
 */
object SongAlarm {
    private const val PREFS = "song_alarm"
    private const val CHANNEL = "alarm"

    data class Setup(val on: Boolean, val hour: Int, val minute: Int, val what: String)

    /** what: "liked", "foryou" or "playlist:<id>". */
    fun get(context: Context): Setup {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return Setup(p.getBoolean("on", false), p.getInt("hour", 7), p.getInt("minute", 0), p.getString("what", "liked") ?: "liked")
    }

    fun set(context: Context, s: Setup) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putBoolean("on", s.on).putInt("hour", s.hour).putInt("minute", s.minute).putString("what", s.what).apply()
        schedule(context)
    }

    /** The next time it rings, or null when it's off. */
    fun next(s: Setup): Calendar? {
        if (!s.on) return null
        val now = Calendar.getInstance()
        return (now.clone() as Calendar).apply {
            set(Calendar.HOUR_OF_DAY, s.hour); set(Calendar.MINUTE, s.minute); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
            if (!after(now)) add(Calendar.DAY_OF_YEAR, 1)
        }
    }

    private fun ring(context: Context) = PendingIntent.getBroadcast(
        context, 4100, Intent(context, Receiver::class.java).setAction("com.sangeet.player.ALARM"),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun openApp(context: Context) = PendingIntent.getActivity(
        context, 4101, Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    fun schedule(context: Context) {
        val am = context.getSystemService(AlarmManager::class.java) ?: return
        am.cancel(ring(context))
        val at = next(get(context)) ?: return
        val exact = Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms()
        runCatching {
            if (exact) am.setAlarmClock(AlarmManager.AlarmClockInfo(at.timeInMillis, openApp(context)), ring(context))
            else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at.timeInMillis, ring(context))
        }.onFailure { android.util.Log.w("Sangeet", "alarm: ${it.message}") }
    }

    /** The songs to wake up to. */
    suspend fun songs(context: Context, what: String): List<Track> {
        val c = (context.applicationContext as SangeetApplication).container
        val online = c.online.canGoOnline
        val downloaded = runCatching { c.downloads.downloadedTracks.first() }.getOrDefault(emptyList())
        val ids = downloaded.map { it.id }.toSet()
        val list = when {
            what.startsWith("playlist:") -> what.removePrefix("playlist:").toLongOrNull()?.let { c.library.playlistTracksOnce(it) }.orEmpty()
            what == "foryou" && online -> runCatching { c.recommendations.suggestions(limit = 40).map { it.track } }.getOrDefault(emptyList())
            else -> c.library.favoritesOnce()
        }.ifEmpty { c.library.favoritesOnce() }.ifEmpty { downloaded }
        // No internet: only what plays without it.
        return (if (online) list else list.filter { it.id in ids }.ifEmpty { downloaded }).shuffled().take(60)
    }

    /** Plays the alarm's songs now (also "Try it" in the alarm screen). */
    suspend fun play(context: Context): Boolean {
        val c = (context.applicationContext as SangeetApplication).container
        val tracks = songs(context, get(context).what)
        if (tracks.isEmpty()) return false
        c.player.play(tracks)
        return true
    }

    class Receiver : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED -> { schedule(context); return }
            }
            val done = goAsync()
            CoroutineScope(SupervisorJob() + Dispatchers.Main).launch {
                try {
                    val played = withTimeoutOrNull(9_000) { runCatching { play(context) }.getOrDefault(false) } == true
                    post(context, if (played) "Good morning! Your songs are playing." else "Add some liked songs or downloads for the alarm.")
                } finally {
                    schedule(context) // tomorrow
                    done.finish()
                }
            }
        }
    }

    private fun post(context: Context, text: String) = runCatching {
        val nm = context.getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(CHANNEL) == null) {
            nm.createNotificationChannel(NotificationChannel(CHANNEL, "Song alarm", NotificationManager.IMPORTANCE_HIGH))
        }
        nm.notify(4102, NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("⏰ Sangeet alarm")
            .setContentText(text)
            .setAutoCancel(true)
            .setContentIntent(openApp(context))
            .build())
    }
}
