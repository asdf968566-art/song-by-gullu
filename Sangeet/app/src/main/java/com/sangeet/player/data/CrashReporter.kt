package com.sangeet.player.data

import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Request
import okhttp3.MediaType.Companion.toMediaType
import kotlinx.serialization.json.put
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import java.io.IOException
import com.sangeet.player.data.remote.Http
import android.content.Context
import android.content.Intent
import android.os.Build
import com.sangeet.player.BuildConfig
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Saves the reason when the app crashes, and lets the user send it (plus recent app logs) from inside the app
 * as a GitHub issue, or share it. Nothing is sent without the user tapping Send.
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

    /** Title + body of a report: what the user wrote, phone details, last crash and recent app log. */
    fun build(context: Context, what: String, includeLogs: Boolean = true): Pair<String, String> {
        val crash = pendingCrash(context)
        val body = buildString {
            appendLine("**What happened:** ${what.ifBlank { "(not described)" }}")
            appendLine()
            appendLine("App ${BuildConfig.VERSION_NAME} · Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT}) · ${Build.MANUFACTURER} ${Build.MODEL}")
            if (crash != null) {
                appendLine()
                appendLine("**Crash**")
                appendLine("```")
                appendLine(crash.take(6000))
                appendLine("```")
            }
            if (includeLogs) {
                val logs = recentLogs()
                if (logs.isNotBlank()) {
                    appendLine()
                    appendLine("**Recent app log**")
                    appendLine("```")
                    appendLine(logs.takeLast(8000))
                    appendLine("```")
                }
            }
        }
        val title = (if (crash != null) "App crashed: " else "Problem: ") +
            what.lineSequence().firstOrNull().orEmpty().take(60).ifBlank { BuildConfig.VERSION_NAME }
        return title to body
    }

    /** True when reports can be sent straight from the app (a REPORT_TOKEN was built in). */
    val canSendDirectly: Boolean get() = BuildConfig.REPORT_TOKEN.isNotBlank()

    /**
     * Sends the report as a GitHub issue from inside the app (no browser). Returns the issue number,
     * or throws with a short reason (no internet, no token...).
     */
    suspend fun submit(context: Context, what: String, includeLogs: Boolean): Int = withContext(Dispatchers.IO) {
        if (!canSendDirectly) throw IllegalStateException("Direct sending isn't set up yet")
        val (title, body) = build(context, what, includeLogs)
        val json = buildJsonObject {
            put("title", title)
            put("body", body)
        }.toString()
        val req = Request.Builder()
            .url("https://api.github.com/repos/${BuildConfig.UPDATE_REPO}/issues")
            .header("Authorization", "Bearer ${BuildConfig.REPORT_TOKEN}")
            .header("Accept", "application/vnd.github+json")
            .post(json.toRequestBody("application/json".toMediaType()))
            .build()
        Http.client.newCall(req).execute().use { res ->
            if (!res.isSuccessful) throw IOException("GitHub said ${res.code}")
            val number = Http.json.parseToJsonElement(res.body?.string().orEmpty()).jsonObject["number"]
            clear(context)
            (number as? JsonPrimitive)?.contentOrNull?.toIntOrNull() ?: 0
        }
    }

    /** Fallback without a token: share the report with WhatsApp, email or any app. */
    fun share(context: Context, what: String, includeLogs: Boolean) {
        val (title, body) = build(context, what, includeLogs)
        val send = Intent(Intent.ACTION_SEND).setType("text/plain")
            .putExtra(Intent.EXTRA_SUBJECT, "Sangeet: $title")
            .putExtra(Intent.EXTRA_TEXT, "$title\n\n$body".take(60_000))
        context.startActivity(Intent.createChooser(send, "Send report").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        clear(context)
    }

    /** This app's own recent warnings and errors (an app may read its own log). */
    private fun recentLogs(): String = runCatching {
        val p = Runtime.getRuntime().exec(arrayOf("logcat", "-d", "-t", "300", "*:W", "--pid", android.os.Process.myPid().toString()))
        p.inputStream.bufferedReader().readText()
    }.getOrDefault("")

    private fun now() = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date())
}
