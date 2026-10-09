package com.sangeet.player.data

import android.content.Context
import com.sangeet.player.data.remote.Http
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.longOrNull

/**
 * What the owner dashboard counts (owner, Oct 9: crashes, which features are used, how many users):
 * - feature use (screens opened) and crashes, kept on the phone and sent with the Community upload;
 * - installs and daily users of both apps on a free public hit counter (abacus.jasoncameron.dev, no key, CI-probed
 *   Oct 9): "android-users" once per install, "android-day-<yyyymmdd>" once a day; the web app does "web-…".
 *   Counted even when "Help improve suggestions" is off (it's only a number, nothing about the person).
 */
object Usage {
    private const val PREFS = "usage"
    const val COUNTER = "https://abacus.jasoncameron.dev"
    const val SPACE = "sangeet-asdf968566"

    /** The CI test phone (an emulator) is not a user. */
    fun emulator(): Boolean = android.os.Build.FINGERPRINT.startsWith("generic") ||
        android.os.Build.FINGERPRINT.contains("emulator") || android.os.Build.HARDWARE in setOf("goldfish", "ranchu") ||
        android.os.Build.PRODUCT.contains("sdk")

    fun day(offsetDays: Int = 0): String =
        SimpleDateFormat("yyyyMMdd", Locale.US).format(Date(System.currentTimeMillis() - offsetDays * 86_400_000L))

    /** A screen was opened (route without its arguments: "dj", "movies", "downloads"…). */
    fun opened(context: Context, route: String?) {
        val key = route?.substringBefore('?')?.replace(Regex("/\\{[^}]*\\}"), "")?.takeIf { it.isNotBlank() } ?: return
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        p.edit().putInt("use_$key", p.getInt("use_$key", 0) + 1).apply()
    }

    fun crashed(context: Context, firstLine: String) {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val recent = (listOf(firstLine.take(160)) + p.getString("crash_recent", "").orEmpty().split("\n")).filter { it.isNotBlank() }.take(5)
        p.edit().putInt("crashes", p.getInt("crashes", 0) + 1).putString("crash_recent", recent.joinToString("\n")).commit()
    }

    fun features(context: Context): Map<String, Int> =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).all
            .filterKeys { it.startsWith("use_") }.mapKeys { it.key.removePrefix("use_") }
            .mapNotNull { (k, v) -> (v as? Int)?.let { k to it } }.toMap()

    fun crashes(context: Context): Int = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getInt("crashes", 0)
    fun recentCrashes(context: Context): List<String> =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("crash_recent", "").orEmpty().split("\n").filter { it.isNotBlank() }

    /** Counts this phone once ever and once today (on app start, when online). */
    suspend fun countMe(context: Context, emulator: Boolean): Unit = withContext(Dispatchers.IO) {
        if (emulator) return@withContext
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        runCatching {
            if (!p.getBoolean("counted", false)) {
                Http.getText("$COUNTER/hit/$SPACE/android-users")
                p.edit().putBoolean("counted", true).apply()
            }
            val today = day()
            if (p.getString("counted_day", null) != today) {
                Http.getText("$COUNTER/hit/$SPACE/android-day-$today")
                p.edit().putString("counted_day", today).apply()
            }
        }
    }

    /** A counter's value, or null (unknown / offline). */
    suspend fun read(name: String): Long? = withContext(Dispatchers.IO) {
        runCatching {
            val body = Http.getText("$COUNTER/get/$SPACE/$name") ?: return@runCatching 0L
            (Http.json.parseToJsonElement(body).jsonObject["value"] as? JsonPrimitive)?.longOrNull
        }.getOrNull()
    }
}
