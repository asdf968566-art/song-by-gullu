package com.sangeet.player.data

import android.os.SystemClock
import com.sangeet.player.data.remote.Http
import com.sangeet.player.playback.PlayerConnection
import java.io.IOException
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlin.coroutines.cancellation.CancellationException
import kotlin.math.abs
import kotlin.random.Random
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * Listen together (owner, Oct 10): phones play the same song at the same moment (a party, or friends far away).
 * One phone starts a room (a 6-letter code or link), the others join and follow its song, position and play/pause.
 * The messages go through ntfy.sh (free, keyless public push service; CI-probed Oct 10: publish + live stream work,
 * CORS *, ~250 messages a day per phone) on the topic "sangeet-tg-<code>". The web app (iPhone) sends the same:
 *   {"v":1,"k":"s","from":id,"o":<song, same JSON as #play links>,"n":title,"p":seconds,"on":true}  the host's state
 *   {"v":1,"k":"h","from":id}  a guest joined: the host answers with its state at once
 * The host sends only on a change (song, play/pause, a seek) and every 5 minutes, to stay inside that limit.
 */
class Together(private val player: PlayerConnection, private val scope: CoroutineScope) {
    data class Room(val code: String, val host: Boolean, val note: String = "")

    private val _room = MutableStateFlow<Room?>(null)
    val room: StateFlow<Room?> = _room.asStateFlow()
    private val me = UUID.randomUUID().toString().take(8)
    private var job: Job? = null
    @Volatile private var lastSent = 0L

    fun link(code: String) = LibrarySync.site + "#together=" + code

    fun start() = open(Room(newCode(), host = true, note = "Send the code or link to your friends"))

    /** Joins a room by its code or link; false when it isn't one. */
    fun join(text: String): Boolean {
        val code = clean(text) ?: return false
        if (_room.value?.code == code) return true
        open(Room(code, host = false, note = "Waiting for the host…"))
        return true
    }

    fun leave() {
        job?.cancel()
        job = null
        _room.value = null
    }

    private fun open(r: Room) {
        leave()
        _room.value = r
        job = scope.launch {
            if (r.host) launch { hostLoop(r.code) } else launch(Dispatchers.IO) { send(r.code, hello()) }
            listen(r.code)
        }
    }

    private fun note(text: String) {
        _room.value = _room.value?.copy(note = text)
    }

    // ------------------------------------------------------------ host

    private suspend fun hostLoop(code: String) {
        var lastId: String? = null
        var lastOn = false
        var lastPos = 0L
        var lastAt = 0L
        while (currentCoroutineContext().isActive) {
            val s = player.state.value
            val on = s.isPlaying || s.isBuffering
            val pos = player.position.value.positionMs
            val now = SystemClock.elapsedRealtime()
            val expected = if (lastOn) lastPos + (now - lastAt) else lastPos
            if (s.current != null && (s.current.id != lastId || on != lastOn || abs(pos - expected) > 3_000 || now - lastSent > 300_000)) {
                val msg = state()
                withContext(Dispatchers.IO) { send(code, msg) }
                lastId = s.current.id; lastOn = on; lastPos = pos; lastAt = now
            }
            delay(1_500)
        }
    }

    private fun state(): String {
        val s = player.state.value
        val t = s.current
        return buildJsonObject {
            put("v", 1); put("k", "s"); put("from", me)
            t?.let(LibrarySync::encode)?.let { put("o", it) }
            put("n", t?.title.orEmpty())
            put("p", player.position.value.positionMs / 1000.0)
            put("on", s.isPlaying || s.isBuffering)
        }.toString()
    }

    private fun hello() = buildJsonObject { put("v", 1); put("k", "h"); put("from", me) }.toString()

    // ------------------------------------------------------------ guest

    private fun follow(msg: JsonObject) {
        val t = (msg["o"] as? JsonObject)?.let(LibrarySync::decode)
        if (t == null) {
            note("The host is playing ${msg.s("n").ifBlank { "a song" }}, which can't be shared (a phone file or radio)")
            return
        }
        val on = (msg["on"] as? JsonPrimitive)?.booleanOrNull ?: true
        // The message is a moment old when it gets here.
        val target = (((msg["p"] as? JsonPrimitive)?.doubleOrNull ?: 0.0) * 1000).toLong() + if (on) 400 else 0
        note("Playing with the host")
        val s = player.state.value
        if (s.current?.id != t.id) {
            player.play(listOf(t), startPositionMs = target)
            if (!on) scope.launch { delay(1_500); player.setPlaying(false) }
            return
        }
        if (abs(player.position.value.positionMs - target) > 2_500) player.seekTo(target)
        if (on != (s.isPlaying || s.isBuffering)) player.setPlaying(on)
    }

    // ------------------------------------------------------------ ntfy.sh

    /** Reads the room's live stream (one JSON line per event) until left; reconnects when it drops. */
    private suspend fun listen(code: String) {
        val client = Http.client.newBuilder().readTimeout(100, TimeUnit.SECONDS).build() // ntfy keepalive: every 45 s
        while (currentCoroutineContext().isActive) {
            try {
                withContext(Dispatchers.IO) {
                    val call = client.newCall(Request.Builder().url("$NTFY/${topic(code)}/json").build())
                    val stop = currentCoroutineContext()[Job]?.invokeOnCompletion { call.cancel() }
                    try {
                        call.execute().use { res ->
                            if (!res.isSuccessful) throw IOException("ntfy ${res.code}")
                            val src = res.body?.source() ?: throw IOException("no body")
                            while (currentCoroutineContext().isActive) {
                                val line = src.readUtf8Line() ?: break
                                val ev = runCatching { Http.json.parseToJsonElement(line).jsonObject }.getOrNull() ?: continue
                                if (ev.s("event") != "message") continue
                                val msg = runCatching { Http.json.parseToJsonElement(ev.s("message")).jsonObject }.getOrNull() ?: continue
                                if (msg.s("from") == me) continue
                                withContext(Dispatchers.Main) { onMessage(code, msg) }
                            }
                        }
                    } finally {
                        stop?.dispose()
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                android.util.Log.w("Sangeet", "together: ${e.message}")
            }
            delay(3_000)
        }
    }

    private fun onMessage(code: String, msg: JsonObject) {
        val r = _room.value ?: return
        if (r.code != code) return
        when (msg.s("k")) {
            "h" -> if (r.host) scope.launch { val m = state(); withContext(Dispatchers.IO) { send(code, m) } }
            "s" -> if (!r.host) follow(msg)
        }
    }

    private fun send(code: String, body: String) {
        runCatching {
            val req = Request.Builder().url("$NTFY/${topic(code)}")
                .post(body.toRequestBody("text/plain".toMediaType())).build()
            Http.client.newCall(req).execute().use { if (!it.isSuccessful) throw IOException("ntfy ${it.code}") }
            lastSent = SystemClock.elapsedRealtime()
        }.onFailure { android.util.Log.w("Sangeet", "together send: ${it.message}") }
    }

    private fun JsonObject.s(k: String) = (this[k] as? JsonPrimitive)?.contentOrNull.orEmpty()

    companion object {
        private const val NTFY = "https://ntfy.sh"
        private const val LETTERS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789" // no 0/O, 1/I

        fun topic(code: String) = "sangeet-tg-" + code.lowercase()

        private fun newCode() = (1..6).map { LETTERS[Random.nextInt(LETTERS.length)] }.joinToString("")

        /** "ABC234", "abc 234" or a link with #together=ABC234 → "ABC234"; null when it isn't a code. */
        fun clean(text: String): String? =
            (if ("together=" in text) text.substringAfter("together=").takeWhile { it.isLetterOrDigit() } else text.filter { it.isLetterOrDigit() })
                .uppercase().takeIf { it.length == 6 }
    }
}
