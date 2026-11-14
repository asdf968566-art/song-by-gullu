package com.dxdepartment.screenmonitor.net

import android.content.Context
import android.util.Log
import com.dxdepartment.screenmonitor.capture.CaptureState
import com.dxdepartment.screenmonitor.capture.FrameHub
import com.dxdepartment.screenmonitor.device.DeviceIdentity
import com.dxdepartment.screenmonitor.settings.AppSettings
import java.io.BufferedOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketException
import java.net.URLDecoder
import java.security.MessageDigest
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * Local test viewer (Milestone 4): a small HTTP server on the phone so a PC or another phone on the same Wi-Fi
 * (or on this phone's hotspot) can watch the screen in a browser, without any cloud server.
 *
 * - `/?key=…` viewer page, `/stream.mjpg?key=…` live picture (MJPEG), `/snapshot.jpg?key=…`, `/status.json?key=…`.
 * - Every request needs the 8-character viewer key shown in the app (compared in constant time). Ten wrong keys
 *   from one address block it for a minute.
 * - Plain HTTP: meant for a trusted local network during testing. Internet viewing (HTTPS / WSS / WebRTC,
 *   with login) is Phase 2.
 */
class PreviewServer(private val context: Context, private val preferredPort: Int) {
    private var server: ServerSocket? = null
    private var acceptThread: Thread? = null
    @Volatile private var running = false
    private val clients = Collections.newSetFromMap(ConcurrentHashMap<Socket, Boolean>())
    private val streams = AtomicInteger(0)
    private val failures = ConcurrentHashMap<String, Pair<Int, Long>>()

    fun start(): Boolean {
        for (port in preferredPort..preferredPort + 10) {
            try {
                val s = ServerSocket()
                s.reuseAddress = true
                s.bind(InetSocketAddress(port), 16)
                server = s
                running = true
                acceptThread = Thread({ acceptLoop(s) }, "dx-http-accept").apply { isDaemon = true; start() }
                Log.i(TAG, "local viewer on port $port")
                CaptureState.update { it.copy(serverPort = port, serverError = null) }
                return true
            } catch (e: Exception) {
                Log.w(TAG, "port $port busy: ${e.message}")
            }
        }
        CaptureState.update { it.copy(serverPort = null, serverError = "No free port for the local viewer") }
        return false
    }

    fun stop() {
        running = false
        runCatching { server?.close() }
        clients.toList().forEach { runCatching { it.close() } }
        clients.clear()
        server = null
        CaptureState.update { it.copy(serverPort = null, viewers = 0) }
        FrameHub.viewers.set(0)
    }

    private fun acceptLoop(s: ServerSocket) {
        while (running) {
            val sock = try {
                s.accept()
            } catch (e: Exception) {
                if (running) Log.w(TAG, "accept failed: ${e.message}")
                break
            }
            if (clients.size >= MAX_CLIENTS) {
                runCatching { sock.close() }
                continue
            }
            clients += sock
            Thread({ handle(sock) }, "dx-http").apply { isDaemon = true; start() }
        }
    }

    private fun handle(sock: Socket) {
        val remote = sock.inetAddress?.hostAddress ?: "?"
        try {
            sock.soTimeout = 10_000
            sock.tcpNoDelay = true
            val req = readRequest(sock.getInputStream()) ?: return
            val out = BufferedOutputStream(sock.getOutputStream(), 64 * 1024)
            if (req.method != "GET" && req.method != "HEAD") {
                respond(out, 405, "text/plain", "Method not allowed".toByteArray()); return
            }
            if (req.path == "/favicon.ico") { respond(out, 404, "text/plain", ByteArray(0)); return }
            if (blocked(remote)) { respond(out, 429, "text/plain", "Too many wrong keys. Wait a minute.".toByteArray()); return }
            val key = req.query["key"] ?: req.headers["x-dx-key"]
            if (!keyOk(key)) {
                fail(remote)
                if (req.path == "/" ) respond(out, 401, "text/html; charset=utf-8", Html.keyPage(key != null).toByteArray())
                else respond(out, 401, "text/plain", "Wrong or missing viewer key".toByteArray())
                return
            }
            failures.remove(remote)
            when (req.path) {
                "/" -> respond(out, 200, "text/html; charset=utf-8", Html.viewer(DeviceIdentity.name(context)).toByteArray())
                "/status.json" -> respond(out, 200, "application/json", statusJson().toByteArray())
                "/snapshot.jpg" -> {
                    FrameHub.previewWanted = true
                    val f = FrameHub.latest() ?: FrameHub.awaitNewer(0, 3000)
                    if (f == null) respond(out, 503, "text/plain", "No picture yet: is sharing on?".toByteArray())
                    else respond(out, 200, "image/jpeg", f.jpeg)
                }
                "/stream.mjpg" -> stream(sock, out, remote)
                else -> respond(out, 404, "text/plain", "Not found".toByteArray())
            }
        } catch (e: SocketException) {
            // Viewer closed the page or the network changed.
        } catch (e: Exception) {
            Log.w(TAG, "request from $remote failed: ${e.message}")
        } finally {
            clients -= sock
            runCatching { sock.close() }
        }
    }

    private fun stream(sock: Socket, out: OutputStream, remote: String) {
        if (streams.get() >= MAX_STREAMS) {
            respond(out, 503, "text/plain", "Too many viewers ($MAX_STREAMS max)".toByteArray()); return
        }
        sock.soTimeout = 0
        val n = streams.incrementAndGet()
        FrameHub.viewers.set(n)
        CaptureState.update { it.copy(viewers = n, lastViewerAt = System.currentTimeMillis(), lastViewerAddress = remote) }
        Log.i(TAG, "viewer connected from $remote ($n watching)")
        try {
            out.write(
                ("HTTP/1.1 200 OK\r\nContent-Type: multipart/x-mixed-replace; boundary=$BOUNDARY\r\n" +
                    "Cache-Control: no-cache, no-store, must-revalidate\r\nPragma: no-cache\r\nConnection: close\r\n" +
                    "X-Content-Type-Options: nosniff\r\n\r\n").toByteArray(),
            )
            out.flush()
            var lastVersion = -1L
            var lastSent = 0L
            while (running && !sock.isClosed) {
                val f = FrameHub.awaitNewer(lastVersion, 1000) ?: continue
                val now = System.currentTimeMillis()
                // A still screen gives no new frames: send the same picture again every 2 s so the viewer
                // knows the phone is still there (and proxies don't close the connection).
                if (f.version == lastVersion && now - lastSent < 2000) continue
                out.write("--$BOUNDARY\r\nContent-Type: image/jpeg\r\nContent-Length: ${f.jpeg.size}\r\n\r\n".toByteArray())
                out.write(f.jpeg)
                out.write("\r\n".toByteArray())
                out.flush()
                lastVersion = f.version
                lastSent = now
            }
        } finally {
            val left = streams.decrementAndGet().coerceAtLeast(0)
            FrameHub.viewers.set(left)
            CaptureState.update { it.copy(viewers = left, lastViewerAt = System.currentTimeMillis()) }
            Log.i(TAG, "viewer $remote left ($left watching)")
        }
    }

    private fun statusJson(): String {
        val s = CaptureState.status.value
        val n = NetworkMonitor.state.value
        fun q(v: String?) = if (v == null) "null" else "\"" + v.replace("\\", "\\\\").replace("\"", "\\\"") + "\""
        val settings = AppSettings.get(context)
        return buildString {
            append('{')
            append("\"device\":").append(q(DeviceIdentity.name(context))).append(',')
            append("\"deviceId\":").append(q(DeviceIdentity.id(context))).append(',')
            append("\"model\":").append(q(DeviceIdentity.model)).append(',')
            append("\"phase\":").append(q(s.phase.name)).append(',')
            append("\"width\":").append(s.width).append(",\"height\":").append(s.height).append(',')
            append("\"screenWidth\":").append(s.screenWidth).append(",\"screenHeight\":").append(s.screenHeight).append(',')
            append("\"rotation\":").append(s.rotation).append(',')
            append("\"captureFps\":").append("%.1f".format(java.util.Locale.US, s.captureFps)).append(',')
            append("\"encodeFps\":").append("%.1f".format(java.util.Locale.US, s.encodeFps)).append(',')
            append("\"frameKb\":").append(s.lastFrameKb).append(',')
            append("\"framesTotal\":").append(s.framesTotal).append(',')
            append("\"viewers\":").append(s.viewers).append(',')
            append("\"quality\":").append(q(settings.quality.summary)).append(',')
            append("\"lowPower\":").append(s.lowPower).append(',')
            append("\"battery\":").append(s.batteryPercent ?: "null").append(',')
            append("\"charging\":").append(s.charging).append(',')
            append("\"network\":").append(q(n.label)).append(',')
            append("\"startedAt\":").append(s.startedAt ?: "null")
            append('}')
        }
    }

    private fun keyOk(key: String?): Boolean {
        val want = AppSettings.get(context).viewerKey
        if (key == null || want.isEmpty()) return false
        return MessageDigest.isEqual(key.trim().uppercase().toByteArray(), want.toByteArray())
    }

    private fun blocked(remote: String): Boolean {
        val (count, at) = failures[remote] ?: return false
        if (System.currentTimeMillis() - at > 60_000) { failures.remove(remote); return false }
        return count >= 10
    }

    private fun fail(remote: String) {
        val now = System.currentTimeMillis()
        failures.compute(remote) { _, old -> if (old == null || now - old.second > 60_000) 1 to now else (old.first + 1) to old.second }
    }

    private fun respond(out: OutputStream, code: Int, type: String, body: ByteArray) {
        val reason = when (code) {
            200 -> "OK"; 401 -> "Unauthorized"; 404 -> "Not Found"; 405 -> "Method Not Allowed"
            429 -> "Too Many Requests"; 503 -> "Service Unavailable"; else -> "Error"
        }
        out.write(
            ("HTTP/1.1 $code $reason\r\nContent-Type: $type\r\nContent-Length: ${body.size}\r\n" +
                "Cache-Control: no-store\r\nConnection: close\r\nX-Content-Type-Options: nosniff\r\n" +
                "Referrer-Policy: no-referrer\r\n\r\n").toByteArray(),
        )
        out.write(body)
        out.flush()
    }

    private class Request(val method: String, val path: String, val query: Map<String, String>, val headers: Map<String, String>)

    /** Reads the request line and headers (at most 8 KB); the body is never needed. */
    private fun readRequest(input: InputStream): Request? {
        val lines = mutableListOf<String>()
        val sb = StringBuilder()
        var total = 0
        while (true) {
            val b = input.read()
            if (b < 0) return null
            if (++total > 8192) return null
            if (b == '\n'.code) {
                val line = sb.toString().trimEnd('\r')
                sb.setLength(0)
                if (line.isEmpty()) break
                lines += line
            } else {
                sb.append(b.toChar())
            }
        }
        val first = lines.firstOrNull()?.split(' ') ?: return null
        if (first.size < 2) return null
        val target = first[1]
        val path = target.substringBefore('?')
        val query = target.substringAfter('?', "").split('&').filter { it.contains('=') }.associate {
            val k = URLDecoder.decode(it.substringBefore('='), "UTF-8")
            val v = URLDecoder.decode(it.substringAfter('='), "UTF-8")
            k to v
        }
        val headers = lines.drop(1).filter { it.contains(':') }.associate {
            it.substringBefore(':').trim().lowercase() to it.substringAfter(':').trim()
        }
        return Request(first[0], path, query, headers)
    }

    companion object {
        private const val TAG = "DXViewer"
        private const val BOUNDARY = "dxframe"
        private const val MAX_CLIENTS = 16
        const val MAX_STREAMS = 4
    }
}
