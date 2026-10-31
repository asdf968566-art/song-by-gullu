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
    private const val SENT_AT = "sent_at"

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
        val all = recent(context, 10).filter { it.timestamp > seen && isUnexpected(it) }
        val exit = all.firstOrNull() ?: return null
        // A crash on every start sent five reports in two minutes (#80-#84): one in 6 hours, the next one counts them.
        if (System.currentTimeMillis() - prefs(context).getLong(SENT_AT, 0L) < 6 * 3_600_000L) return null
        return buildString {
            append("Android stopped the app: ${describe(exit)}")
            appendLine(if (all.size > 1) " (${all.size} times since the last report)" else "")
            trace(exit)?.let {
                appendLine()
                appendLine(if (exit.reason == ApplicationExitInfo.REASON_ANR) "Main thread when it froze:" else "Where it crashed:")
                appendLine("```"); appendLine(it); appendLine("```")
            }
        }
    }

    /** The newest exit has been reported (or was nothing to report): don't send it again. */
    fun markSent(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) markSentR(context)
    }

    @RequiresApi(Build.VERSION_CODES.R)
    private fun markSentR(context: Context) {
        val newest = recent(context, 1).firstOrNull()?.timestamp ?: return
        prefs(context).edit().putLong(SEEN, newest).putLong(SENT_AT, System.currentTimeMillis()).apply()
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

    /** For a freeze: the first lines of the main thread's stack. For a native crash (Android 12+): its tombstone. */
    @RequiresApi(Build.VERSION_CODES.R)
    private fun trace(e: ApplicationExitInfo): String? {
        if (e.reason == ApplicationExitInfo.REASON_CRASH_NATIVE) {
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) runCatching { tombstone(e) }.getOrNull() else null
        }
        if (e.reason != ApplicationExitInfo.REASON_ANR) return null
        val text = runCatching { e.traceInputStream?.bufferedReader()?.use { it.readText() } }.getOrNull() ?: return null
        val main = text.substringAfter("\"main\"", "").ifBlank { return null }
        return ("\"main\"" + main).lineSequence().take(30).joinToString("\n")
    }

    /**
     * The crashing thread of a native crash (reports #80-#84: five on a Samsung, with no detail), from Android's
     * tombstone, a protobuf (AOSP debuggerd/proto/tombstone.proto): the signal, the cause, the abort message and the
     * first frames (library and function only). Memory, registers, other threads and the log (it has song names)
     * are left out.
     */
    @RequiresApi(Build.VERSION_CODES.R)
    private fun tombstone(e: ApplicationExitInfo): String? {
        val top = fields(e.traceInputStream?.use { it.readBytes() } ?: return null)
        fun List<Field>.text(no: Int) = firstOrNull { it.no == no }?.text?.takeIf { it.isNotBlank() }
        val tid = top.firstOrNull { it.no == 6 }?.num
        val signal = top.firstOrNull { it.no == 10 }?.bytes?.let(::fields).orEmpty()
        val threads = top.filter { it.no == 16 }.mapNotNull { entry -> fields(entry.bytes ?: return@mapNotNull null).firstOrNull { it.no == 2 }?.bytes?.let(::fields) }
        val thread = threads.firstOrNull { t -> t.firstOrNull { it.no == 1 }?.num == tid } ?: threads.firstOrNull()
        val frames = thread.orEmpty().filter { it.no == 4 }.take(16).mapIndexedNotNull { i, f ->
            val frame = fields(f.bytes ?: return@mapIndexedNotNull null)
            val file = frame.text(6)?.substringAfterLast('/') ?: "?"
            val function = frame.text(4)?.let { fn -> "$fn+${frame.firstOrNull { it.no == 5 }?.num ?: 0}" }
            "#${i.toString().padStart(2, '0')} $file" + function?.let { " ($it)" }.orEmpty()
        }
        if (frames.isEmpty() && signal.isEmpty()) return null
        return buildString {
            append("Native crash: ${signal.text(2) ?: "signal"}")
            signal.text(4)?.let { append(" ($it)") }
            thread?.text(2)?.let { append(" in thread \"$it\"") }
            appendLine()
            top.filter { it.no == 15 }.mapNotNull { c -> c.bytes?.let(::fields)?.text(1) }.forEach { appendLine("Cause: $it") }
            top.text(14)?.let { appendLine("Abort: ${it.replace('\n', ' ').take(300)}") }
            frames.forEach { appendLine(it) }
        }.trimEnd()
    }

    /** A protobuf field: its number, and a number or the bytes of a string / message. */
    private class Field(val no: Int, val num: Long, val bytes: ByteArray?) {
        val text: String? get() = bytes?.toString(Charsets.UTF_8)
    }

    private fun fields(b: ByteArray): List<Field> {
        val out = ArrayList<Field>()
        var i = 0
        fun varint(): Long {
            var r = 0L
            var shift = 0
            while (i < b.size && shift < 64) {
                val x = b[i++].toInt() and 0xff
                r = r or ((x and 0x7f).toLong() shl shift)
                if (x < 0x80) break
                shift += 7
            }
            return r
        }
        while (i < b.size) {
            val tag = varint()
            val no = (tag ushr 3).toInt()
            when ((tag and 7).toInt()) {
                0 -> out += Field(no, varint(), null)
                1 -> i += 8
                2 -> {
                    val n = varint()
                    if (n < 0 || n > b.size - i) break
                    out += Field(no, 0, b.copyOfRange(i, i + n.toInt()))
                    i += n.toInt()
                }
                5 -> i += 4
                else -> break
            }
        }
        return out
    }

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
