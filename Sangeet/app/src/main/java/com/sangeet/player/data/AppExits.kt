package com.sangeet.player.data

import android.app.ActivityManager
import android.app.ActivityManager.RunningAppProcessInfo
import android.app.ApplicationExitInfo
import android.content.Context
import android.os.Build
import androidx.annotation.RequiresApi
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Why Android ended the app's earlier runs (report #69: "15-20 minute chalne ke baad app apne aap band ho jata hai",
 * and no crash report came). A crash in native code, a freeze (ANR), the phone's memory or battery saver end the app
 * without a Java crash, so CrashReporter never sees them; Android 11+ keeps the reason (ApplicationExitInfo).
 * Only the reason, time, memory and Android's own note go into reports: nothing about songs or the user.
 */
object AppExits {
    private const val PREFS = "app_exits"
    private const val SEEN = "seen_until"

    /** The last few exits, one line each, for every report. */
    fun text(context: Context): String? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) textR(context) else null

    @RequiresApi(Build.VERSION_CODES.R)
    private fun textR(context: Context): String? {
        val list = recent(context, 5).ifEmpty { return null }
        return list.joinToString("\n", prefix = "- Last app exits:\n") { "  - ${describe(it)}" }
    }

    /**
     * An exit since the last one reported that nobody asked for while the app was on screen or playing (or a native
     * crash / freeze at any time): the report about it goes by itself, like a crash report. [markSent] after sending.
     */
    fun unexpected(context: Context): String? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && !Usage.emulator()) unexpectedR(context) else null

    @RequiresApi(Build.VERSION_CODES.R)
    private fun unexpectedR(context: Context): String? {
        // Older than 3 days isn't news (the first run of this version looks back over what Android still keeps).
        val seen = maxOf(prefs(context).getLong(SEEN, 0L), System.currentTimeMillis() - 3 * 86_400_000L)
        val exit = recent(context, 10).firstOrNull { it.timestamp > seen && isUnexpected(it) } ?: return null
        return buildString {
            appendLine("Android stopped the app: ${describe(exit)}")
            trace(exit)?.let { appendLine(); appendLine("Main thread when it froze:"); appendLine("```"); appendLine(it); appendLine("```") }
        }
    }

    /** The newest exit has been reported (or was nothing to report): don't send it again. */
    fun markSent(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) markSentR(context)
    }

    @RequiresApi(Build.VERSION_CODES.R)
    private fun markSentR(context: Context) {
        val newest = recent(context, 1).firstOrNull()?.timestamp ?: return
        prefs(context).edit().putLong(SEEN, newest).apply()
    }

    @RequiresApi(Build.VERSION_CODES.R)
    private fun recent(context: Context, n: Int): List<ApplicationExitInfo> = runCatching {
        context.getSystemService(ActivityManager::class.java).getHistoricalProcessExitReasons(context.packageName, 0, n)
    }.getOrDefault(emptyList())

    @RequiresApi(Build.VERSION_CODES.R)
    private fun isUnexpected(e: ApplicationExitInfo): Boolean {
        val busy = e.importance <= RunningAppProcessInfo.IMPORTANCE_FOREGROUND_SERVICE // on screen or playing
        return when (e.reason) {
            ApplicationExitInfo.REASON_CRASH_NATIVE, ApplicationExitInfo.REASON_ANR,
            ApplicationExitInfo.REASON_INITIALIZATION_FAILURE -> true
            ApplicationExitInfo.REASON_LOW_MEMORY, ApplicationExitInfo.REASON_SIGNALED, ApplicationExitInfo.REASON_OTHER,
            ApplicationExitInfo.REASON_EXCESSIVE_RESOURCE_USAGE, ApplicationExitInfo.REASON_UNKNOWN -> busy
            else -> false // a Java crash (CrashReporter has it), an update, the user stopping it, a permission change…
        }
    }

    @RequiresApi(Build.VERSION_CODES.R)
    private fun describe(e: ApplicationExitInfo): String {
        val reason = when (e.reason) {
            ApplicationExitInfo.REASON_ANR -> "froze (ANR)"
            ApplicationExitInfo.REASON_CRASH -> "crash"
            ApplicationExitInfo.REASON_CRASH_NATIVE -> "crash in native code"
            ApplicationExitInfo.REASON_DEPENDENCY_DIED -> "a needed app died"
            ApplicationExitInfo.REASON_EXCESSIVE_RESOURCE_USAGE -> "used too much (battery / CPU)"
            ApplicationExitInfo.REASON_EXIT_SELF -> "closed itself"
            ApplicationExitInfo.REASON_INITIALIZATION_FAILURE -> "failed to start"
            ApplicationExitInfo.REASON_LOW_MEMORY -> "phone low on memory"
            ApplicationExitInfo.REASON_OTHER -> "stopped by the phone"
            ApplicationExitInfo.REASON_PERMISSION_CHANGE -> "a permission changed"
            ApplicationExitInfo.REASON_SIGNALED -> "killed by the phone (signal ${e.status})"
            ApplicationExitInfo.REASON_USER_REQUESTED -> "stopped (force stop / cleaner)"
            ApplicationExitInfo.REASON_USER_STOPPED -> "user stopped it"
            else -> if (Build.VERSION.SDK_INT >= 34 && e.reason == ApplicationExitInfo.REASON_PACKAGE_UPDATED) "app updated"
                else if (Build.VERSION.SDK_INT >= 33 && e.reason == ApplicationExitInfo.REASON_FREEZER) "frozen by the phone"
                else "reason ${e.reason}"
        }
        val state = when {
            e.importance <= RunningAppProcessInfo.IMPORTANCE_FOREGROUND -> "on screen"
            e.importance <= RunningAppProcessInfo.IMPORTANCE_FOREGROUND_SERVICE -> "playing in the background"
            else -> "in the background"
        }
        val time = SimpleDateFormat("MMM d HH:mm", Locale.US).format(Date(e.timestamp))
        val mem = "${e.pss / 1024} MB used"
        val note = e.description?.takeIf { it.isNotBlank() }?.take(120)?.let { ", \"$it\"" }.orEmpty()
        return "$time $reason, $state, $mem$note"
    }

    /** For a freeze: the first lines of the main thread's stack (app code only). */
    @RequiresApi(Build.VERSION_CODES.R)
    private fun trace(e: ApplicationExitInfo): String? {
        if (e.reason != ApplicationExitInfo.REASON_ANR) return null
        val text = runCatching { e.traceInputStream?.bufferedReader()?.use { it.readText() } }.getOrNull() ?: return null
        val main = text.substringAfter("\"main\"", "").ifBlank { return null }
        return ("\"main\"" + main).lineSequence().take(30).joinToString("\n")
    }

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
