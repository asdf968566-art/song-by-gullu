package com.dxdepartment.screenmonitor.webrtc

import android.util.Log
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONArray
import org.json.JSONObject
import org.webrtc.PeerConnection
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * The device side of the signaling protocol (see server/src/signaling.js), over a WebSocket.
 * It registers this phone as a "device", then forwards viewer-join / signal events to the streamer and
 * sends the streamer's offers / ICE candidates back. It reconnects by itself with backoff.
 */
class Signaling(
    private val url: String,
    private val deviceId: String,
    private val name: String,
    private val token: String,
    private val listener: Listener,
) {
    interface Listener {
        fun onReady(iceServers: List<PeerConnection.IceServer>)
        fun onViewerJoin(viewerId: String)
        fun onViewerLeft(viewerId: String)
        fun onSignal(from: String, kind: String, data: JSONObject)
        fun onConnectionChanged(connected: Boolean)
    }

    private val client = OkHttpClient.Builder()
        .pingInterval(20, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    @Volatile private var ws: WebSocket? = null
    private val closed = AtomicBoolean(false)
    private var backoffMs = 1000L

    fun connect() {
        if (closed.get()) return
        Log.i(TAG, "connecting to $url")
        val req = Request.Builder().url(url).build()
        ws = client.newWebSocket(req, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                backoffMs = 1000L
                val hello = JSONObject()
                    .put("t", "hello").put("role", "device")
                    .put("deviceId", deviceId).put("name", name).put("token", token)
                webSocket.send(hello.toString())
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                handle(text)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                Log.i(TAG, "signaling closed: $code $reason")
                listener.onConnectionChanged(false)
                if (code == 4001 || code == 4002) {
                    // unauthorized / replaced — don't keep retrying a losing fight.
                    if (code == 4001) Log.w(TAG, "signaling rejected: wrong device token")
                    return
                }
                reconnect()
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.w(TAG, "signaling failure: ${t.message}")
                listener.onConnectionChanged(false)
                reconnect()
            }
        })
    }

    private fun handle(text: String) {
        val m = runCatching { JSONObject(text) }.getOrNull() ?: return
        when (m.optString("t")) {
            "welcome" -> {
                val servers = parseIceServers(m.optJSONArray("iceServers"))
                Log.i(TAG, "registered as $deviceId; ${servers.size} ICE servers")
                listener.onConnectionChanged(true)
                listener.onReady(servers)
            }
            "viewer-join" -> listener.onViewerJoin(m.optString("viewerId"))
            "viewer-left" -> listener.onViewerLeft(m.optString("viewerId"))
            "signal" -> listener.onSignal(m.optString("from"), m.optString("kind"), m.optJSONObject("data") ?: JSONObject())
            "replaced" -> Log.w(TAG, "another device took this id")
            "error" -> Log.w(TAG, "server error: ${m.optString("code")} ${m.optString("message")}")
        }
    }

    fun sendSignal(to: String, kind: String, data: JSONObject) {
        val msg = JSONObject().put("t", "signal").put("to", to).put("kind", kind).put("data", data)
        ws?.send(msg.toString())
    }

    fun viewerGone(viewerId: String) {
        ws?.send(JSONObject().put("t", "stop").put("viewerId", viewerId).toString())
    }

    private fun reconnect() {
        if (closed.get()) return
        val delay = backoffMs
        backoffMs = (backoffMs * 2).coerceAtMost(15000L)
        Thread {
            Thread.sleep(delay)
            if (!closed.get()) connect()
        }.apply { isDaemon = true }.start()
    }

    fun close() {
        closed.set(true)
        runCatching { ws?.close(1000, "bye") }
        ws = null
    }

    private fun parseIceServers(arr: JSONArray?): List<PeerConnection.IceServer> {
        if (arr == null) return emptyList()
        val out = ArrayList<PeerConnection.IceServer>()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val urls = ArrayList<String>()
            val u = o.opt("urls")
            if (u is JSONArray) for (j in 0 until u.length()) urls.add(u.getString(j))
            else if (u is String) urls.add(u)
            if (urls.isEmpty()) continue
            val b = PeerConnection.IceServer.builder(urls)
            o.optString("username").takeIf { it.isNotEmpty() }?.let { b.setUsername(it) }
            o.optString("credential").takeIf { it.isNotEmpty() }?.let { b.setPassword(it) }
            out.add(b.createIceServer())
        }
        return out
    }

    companion object {
        private const val TAG = "DXSignaling"
    }
}
