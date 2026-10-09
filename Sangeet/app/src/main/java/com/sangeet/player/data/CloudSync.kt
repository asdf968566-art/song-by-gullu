package com.sangeet.player.data

import android.content.Context
import com.sangeet.player.data.model.Track
import com.sangeet.player.data.remote.Http
import java.io.IOException
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * Liked songs and playlists the same on two phones, Android and iPhone (owner, Oct 9), with no account: one phone
 * makes a sync code, the other joins with it. The library is kept (packed like a #sync link) on restful-api.dev, a
 * free keyless JSON store (CI-probed Oct 9; anyone with the 32-letter code could read it, nobody can guess it).
 * Each sync merges three ways against what both had last time, so a song un-liked on one phone goes on the other
 * too. The web app's CloudSync does the same with the same data.
 */
object CloudSync {
    private const val API = "https://api.restful-api.dev/objects"
    private const val PREFS = "cloud_sync"
    private val lock = Mutex()

    fun code(context: Context): String? = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("code", null)
    fun link(code: String) = LibrarySync.site + "#joinsync=" + code
    fun lastSync(context: Context): Long = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getLong("at", 0L)

    fun stop(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear().apply()
    }

    private fun me(context: Context): String {
        val p = context.getSharedPreferences(PREFS + "_id", Context.MODE_PRIVATE)
        return p.getString("id", null) ?: UUID.randomUUID().toString().take(8).also { p.edit().putString("id", it).apply() }
    }

    /** A library: liked ids in order, playlists by name, and the songs. */
    private class Lib(val liked: List<String>, val lists: Map<String, List<String>>, val tracks: Map<String, Track>)

    private suspend fun local(lib: LibraryRepository): Lib {
        val liked = lib.favoritesOnce().filter { LibrarySync.encode(it) != null }
        val lists = lib.playlists.first().filter { !it.name.startsWith("✨") }.associate { p ->
            p.name to lib.playlistTracksOnce(p.id).filter { LibrarySync.encode(it) != null }
        }
        val tracks = (liked + lists.values.flatten()).associateBy { it.id }
        return Lib(liked.map { it.id }, lists.mapValues { (_, t) -> t.map { it.id } }, tracks)
    }

    private fun toJson(l: Lib) = buildJsonObject {
        put("l", JsonArray(l.liked.mapNotNull { l.tracks[it]?.let(LibrarySync::encode) }))
        put("p", JsonArray(l.lists.map { (n, ids) -> buildJsonObject { put("n", n); put("t", JsonArray(ids.mapNotNull { l.tracks[it]?.let(LibrarySync::encode) })) } }))
    }

    private fun fromJson(o: JsonObject): Lib {
        val tracks = HashMap<String, Track>()
        fun list(a: Any?) = (a as? JsonArray).orEmpty().mapNotNull { (it as? JsonObject)?.let(LibrarySync::decode) }.onEach { tracks[it.id] = it }.map { it.id }
        val liked = list(o["l"])
        val lists = (o["p"] as? JsonArray).orEmpty().mapNotNull { it as? JsonObject }.associate { p ->
            ((p["n"] as? JsonPrimitive)?.contentOrNull ?: "Playlist") to list(p["t"])
        }
        return Lib(liked, lists, tracks)
    }

    // ------------------------------------------------------------ the store

    private suspend fun call(method: String, url: String, body: JsonObject? = null): JsonObject? = withContext(Dispatchers.IO) {
        val req = Request.Builder().url(url)
            .method(method, body?.toString()?.toRequestBody("application/json".toMediaType()))
            .build()
        Http.client.newCall(req).execute().use { res ->
            if (res.code == 404) return@withContext null
            if (!res.isSuccessful) throw IOException("sync HTTP ${res.code}")
            Http.json.parseToJsonElement(res.body?.string().orEmpty()).jsonObject
        }
    }

    private fun wrap(context: Context, l: Lib) = buildJsonObject {
        put("name", "sangeet-sync")
        put("data", buildJsonObject {
            put("v", 1); put("u", System.currentTimeMillis()); put("by", me(context))
            put("z", LibrarySync.pack(toJson(l).toString()))
        })
    }

    private fun unwrap(o: JsonObject?): Lib? {
        val z = ((o?.get("data") as? JsonObject)?.get("z") as? JsonPrimitive)?.contentOrNull ?: return null
        return fromJson(Http.json.parseToJsonElement(LibrarySync.unpack(z)).jsonObject)
    }

    // ------------------------------------------------------------ start, join, sync

    /** Makes a sync code for this phone's library. */
    suspend fun start(context: Context, lib: LibraryRepository): String = lock.withLock {
        val mine = local(lib)
        val made = call("POST", API, wrap(context, mine)) ?: throw IOException("sync store not reachable")
        val code = (made["id"] as? JsonPrimitive)?.contentOrNull ?: throw IOException("no sync code")
        saveBase(context, code, mine)
        code
    }

    /** Joins another phone's sync code: both libraries are put together. */
    suspend fun join(context: Context, lib: LibraryRepository, code: String): Pair<Int, Int> {
        val clean = code.substringAfter("joinsync=").trim().takeWhile { it.isLetterOrDigit() }
        require(clean.length >= 8) { "That code is too short" }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString("code", clean).remove("base").apply()
        return sync(context, lib) ?: throw IOException("That sync code wasn't found")
    }

    /** Merges with the other phone. Returns (liked songs, playlists) after the merge, or null when the code is gone. */
    suspend fun sync(context: Context, lib: LibraryRepository): Pair<Int, Int>? = lock.withLock {
        val code = code(context) ?: return@withLock null
        val remote = unwrap(call("GET", "$API/$code")) ?: return@withLock null
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val base = prefs.getString("base", null)?.let { runCatching { fromJson(Http.json.parseToJsonElement(it).jsonObject) }.getOrNull() }
        val mine = local(lib)
        val tracks = mine.tracks + remote.tracks
        fun merge(b: List<String>?, a: List<String>, r: List<String>): List<String> {
            val gone = b.orEmpty().toSet().let { (it - a.toSet()) + (it - r.toSet()) }
            return (a + r).distinct().filter { it !in gone }
        }
        val liked = merge(base?.liked, mine.liked, remote.liked)
        val names = (mine.lists.keys + remote.lists.keys).filter { n ->
            val inBase = base?.lists?.containsKey(n) == true
            // Deleted on one phone since last time: deleted on both.
            !(inBase && (n !in mine.lists || n !in remote.lists))
        }
        val lists = names.associateWith { n -> merge(base?.lists?.get(n), mine.lists[n].orEmpty(), remote.lists[n].orEmpty()) }
        val merged = Lib(liked, lists, tracks.filterKeys { it in liked.toSet() || lists.values.any { l -> it in l } })
        apply(lib, mine, merged)
        if (toJson(merged) != toJson(remote)) call("PUT", "$API/$code", wrap(context, merged))
        saveBase(context, code, merged)
        liked.size to lists.size
    }

    private suspend fun apply(lib: LibraryRepository, mine: Lib, to: Lib) {
        val have = mine.liked.toSet()
        val want = to.liked.toSet()
        to.liked.filter { it !in have }.mapNotNull { to.tracks[it] }.forEach { lib.toggleFavorite(it) }
        mine.liked.filter { it !in want }.mapNotNull { mine.tracks[it] }.forEach { lib.toggleFavorite(it) }
        val existing = lib.playlists.first().filter { !it.name.startsWith("✨") }
        existing.filter { it.name !in to.lists }.forEach { lib.deletePlaylist(it.id) }
        to.lists.forEach { (name, ids) ->
            val songs = ids.mapNotNull { to.tracks[it] }
            val p = existing.firstOrNull { it.name == name }
            when {
                p == null -> lib.createPlaylist(name, songs)
                mine.lists[name] != ids -> lib.replacePlaylistTracks(p.id, songs)
            }
        }
    }

    private fun saveBase(context: Context, code: String, l: Lib) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString("code", code).putString("base", toJson(l).toString()).putLong("at", System.currentTimeMillis()).apply()
    }
}
