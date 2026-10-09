package com.sangeet.player.data

import android.content.Context
import com.sangeet.player.data.remote.Http
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer

/**
 * "What's new": the update log in assets/whats-new.json (newest first; the web app shows the same file).
 * After an update the new entries show once; Settings → What's new shows them all.
 */
object WhatsNew {
    @Serializable
    data class Item(val on: String = "both", val text: String)

    @Serializable
    data class Entry(val date: String, val title: String = "", val items: List<Item> = emptyList())

    private const val PREFS = "whats_new"
    private const val SEEN = "seen_date"

    /** All entries, only what this app has (no iPhone-only items). */
    fun all(context: Context): List<Entry> = runCatching {
        val text = context.assets.open("whats-new.json").bufferedReader().use { it.readText() }
        Http.json.decodeFromString(ListSerializer(Entry.serializer()), text)
            .map { e -> e.copy(items = e.items.filter { it.on != "iphone" }) }
            .filter { it.items.isNotEmpty() }
    }.getOrDefault(emptyList())

    /**
     * Entries not seen yet. A fresh install shows nothing (it's all new anyway); an update from a version without
     * this list shows the newest entry.
     */
    fun unseen(context: Context): List<Entry> {
        val list = all(context)
        val latest = list.firstOrNull() ?: return emptyList()
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val seen = prefs.getString(SEEN, null)
        if (seen == null) {
            val info = runCatching { context.packageManager.getPackageInfo(context.packageName, 0) }.getOrNull()
            val updated = info != null && info.lastUpdateTime - info.firstInstallTime > 60_000L
            if (!updated) { markSeen(context); return emptyList() }
            return listOf(latest)
        }
        return list.filter { it.date > seen }
    }

    fun markSeen(context: Context) {
        val latest = all(context).firstOrNull()?.date ?: return
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(SEEN, latest).apply()
    }
}
