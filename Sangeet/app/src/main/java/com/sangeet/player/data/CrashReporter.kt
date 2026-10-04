package com.sangeet.player.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import com.sangeet.player.BuildConfig
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Saves the reason when the app crashes, and lets the user send it (plus recent app logs) as a GitHub issue
 * on the app's repo. Nothing is sent without the user tapping "Send report".
 */
object CrashReporter {
    private const val FILE = "last_crash.txt"

    fun install(context: Context) {
        val app = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching {
                val sw = StringWriter()
                error.printStackTrace(PrintWriter(sw))
                File(app.filesDir, FILE).writeText("${now()} on thread ${thread.name}\n$sw")
            }
            previous?.uncaughtException(thread, error)
        }
    }

    /** The crash from last time, if the user hasn't reported or dismissed it yet. */
    fun pendingCrash(context: Context): String? =
        File(context.filesDir, FILE).takeIf { it.exists() }?.readText()?.takeIf { it.isNotBlank() }

    fun clear(context: Context) {
        File(context.filesDir, FILE).delete()
    }

    /** Opens a pre-filled GitHub issue in the browser. [what] = what the user was doing (optional). */
    fun send(context: Context, what: String = "") {
        val crash = pendingCrash(context)
        val body = buildString {
            appendLine("**What happened:** ${what.ifBlank { "(describe here)" }}")
            appendLine()
            appendLine("App ${BuildConfig.VERSION_NAME} · Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT}) · ${Build.MANUFACTURER} ${Build.MODEL}")
            if (crash != null) {
                appendLine()
                appendLine("**Crash**")
                appendLine("```")
                appendLine(crash.take(3500))
                appendLine("```")
            }
            val logs = recentLogs()
            if (logs.isNotBlank()) {
                appendLine()
                appendLine("**Recent app log**")
                appendLine("```")
                appendLine(logs.takeLast(2500))
                appendLine("```")
            }
        }
        val title = if (crash != null) "App crashed (${BuildConfig.VERSION_NAME})" else "Problem report (${BuildConfig.VERSION_NAME})"
        val url = "https://github.com/${BuildConfig.UPDATE_REPO}/issues/new?title=${enc(title)}&body=${enc(body)}"
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        clear(context)
    }

    /** This app's own recent warnings and errors (an app may read its own log). */
    private fun recentLogs(): String = runCatching {
        val p = Runtime.getRuntime().exec(arrayOf("logcat", "-d", "-t", "300", "*:W", "--pid", android.os.Process.myPid().toString()))
        p.inputStream.bufferedReader().readText()
    }.getOrDefault("")

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")
    private fun now() = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date())
}
