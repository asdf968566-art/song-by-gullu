package com.sangeet.player.playback

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import com.sangeet.player.BuildConfig
import com.sangeet.player.data.Usage

/**
 * Report #69 ("15-20 minute chalne ke baad app apne aap band ho jata hai songs bhi ruk jate hain"): on a 28-minute CI
 * run the app kept playing with flat memory, so it is the phone that stops it. Some phones end apps in the
 * background to save battery, even while they play music. Android lets the user allow an app to keep running; the
 * app asks once, when music first plays, and Settings → Playback has the same switch. Not in the Play version (Play
 * allows this only for a few kinds of apps).
 */
object KeepPlaying {
    private const val PREFS = "keep_playing"

    /** Is the battery saver kept away from the app? (Also true where Android can't say.) */
    fun allowed(context: Context): Boolean =
        context.getSystemService(PowerManager::class.java)?.isIgnoringBatteryOptimizations(context.packageName) ?: true

    val offered: Boolean get() = !BuildConfig.PLAY_STORE

    /** Ask once: not on the CI emulator, not when already allowed. */
    fun shouldAsk(context: Context): Boolean =
        offered && !Usage.emulator() && !allowed(context) &&
            !context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean("asked", false)

    fun markAsked(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean("asked", true).apply()

    /** Android's own "Let the app always run in the background?" dialog, else its battery settings list. */
    fun ask(context: Context) {
        val tries = listOf(
            Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${context.packageName}")),
            Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS),
        )
        for (intent in tries) {
            if (runCatching { context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }.isSuccess) return
        }
    }
}
