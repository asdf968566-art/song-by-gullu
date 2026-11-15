package com.dxdepartment.screenmonitor.webrtc

import android.content.Context
import android.media.projection.MediaProjection
import android.util.Log
import com.dxdepartment.screenmonitor.capture.CaptureEngine
import com.dxdepartment.screenmonitor.settings.Quality
import org.json.JSONObject
import org.webrtc.DefaultVideoDecoderFactory
import org.webrtc.DefaultVideoEncoderFactory
import org.webrtc.EglBase
import org.webrtc.IceCandidate
import org.webrtc.MediaConstraints
import org.webrtc.MediaStreamTrack
import org.webrtc.PeerConnection
import org.webrtc.PeerConnectionFactory
import org.webrtc.RtpParameters
import org.webrtc.RtpTransceiver
import org.webrtc.SdpObserver
import org.webrtc.SessionDescription
import org.webrtc.SurfaceTextureHelper
import org.webrtc.VideoSource
import org.webrtc.VideoTrack
import java.util.concurrent.Executors

/**
 * Publishes the screen to any number of viewers over WebRTC, using one shared video track (hardware H264/VP8
 * from the screen texture) and one PeerConnection per viewer. The phone is always the offerer.
 *
 * All WebRTC calls run on a single executor thread to keep the native library happy. Network changes trigger an
 * ICE restart so a Wi-Fi ↔ mobile-data switch recovers without a reconnect.
 */
class WebRtcStreamer(
    private val context: Context,
    private val projection: MediaProjection,
    private val densityDpi: Int,
    private val deviceId: String,
    private val deviceName: String,
    private val serverUrl: String,
    private val deviceToken: String,
    private val onViewerCount: (Int) -> Unit,
    private val onState: (connected: Boolean) -> Unit,
) {
    private val exec = Executors.newSingleThreadExecutor()
    private lateinit var eglBase: EglBase
    private lateinit var factory: PeerConnectionFactory
    private lateinit var surfaceHelper: SurfaceTextureHelper
    private lateinit var capturer: ProjectionVideoCapturer
    private lateinit var videoSource: VideoSource
    private lateinit var videoTrack: VideoTrack

    private var iceServers: List<PeerConnection.IceServer> = emptyList()
    private val peers = HashMap<String, PeerConnection>()
    private var quality = Quality()
    private var started = false
    private var signaling: Signaling? = null

    fun start(quality: Quality) {
        this.quality = quality
        exec.execute {
            try {
                initFactory()
                startCapture(quality)
                started = true
                signaling = Signaling(wsUrl(serverUrl), deviceId, deviceName, deviceToken, Relay()).also { it.connect() }
                Log.i(TAG, "WebRTC streamer started for $deviceId -> $serverUrl")
            } catch (e: Throwable) {
                Log.e(TAG, "start failed", e)
            }
        }
    }

    private fun initFactory() {
        synchronized(initLock) {
            if (!factoryInited) {
                PeerConnectionFactory.initialize(
                    PeerConnectionFactory.InitializationOptions.builder(context.applicationContext).createInitializationOptions(),
                )
                factoryInited = true
            }
        }
        eglBase = EglBase.create()
        val encoder = DefaultVideoEncoderFactory(eglBase.eglBaseContext, true, true)
        val decoder = DefaultVideoDecoderFactory(eglBase.eglBaseContext)
        factory = PeerConnectionFactory.builder()
            .setVideoEncoderFactory(encoder)
            .setVideoDecoderFactory(decoder)
            .createPeerConnectionFactory()
    }

    private fun startCapture(q: Quality) {
        val (w, h) = CaptureEngine.outputSize(screenW(), screenH(), q)
        surfaceHelper = SurfaceTextureHelper.create("DXCaptureThread", eglBase.eglBaseContext)
        capturer = ProjectionVideoCapturer(projection, densityDpi)
        videoSource = factory.createVideoSource(true) // isScreencast
        capturer.initialize(surfaceHelper, context, videoSource.capturerObserver)
        capturer.startCapture(w, h, q.fps)
        videoTrack = factory.createVideoTrack("dxvideo", videoSource)
    }

    fun setQuality(q: Quality) {
        exec.execute {
            if (!started) { quality = q; return@execute }
            quality = q
            val (w, h) = CaptureEngine.outputSize(screenW(), screenH(), q)
            capturer.changeCaptureFormat(w, h, q.fps)
            for (pc in peers.values) applyBitrate(pc, q)
        }
    }

    /** Wi-Fi ↔ mobile data changed: restart ICE on every active connection. */
    fun onNetworkChanged() {
        exec.execute {
            for ((viewerId, pc) in peers) createOffer(viewerId, pc, iceRestart = true)
        }
    }

    fun stop() {
        exec.execute {
            signaling?.close(); signaling = null
            for (pc in peers.values) runCatching { pc.dispose() }
            peers.clear()
            if (started) {
                runCatching { capturer.dispose() }
                runCatching { videoTrack.dispose() }
                runCatching { videoSource.dispose() }
                runCatching { surfaceHelper.dispose() }
                runCatching { factory.dispose() }
                runCatching { eglBase.release() }
                started = false
            }
            onViewerCount(0)
        }
        exec.shutdown()
    }

    // ---- signaling relay ----
    private inner class Relay : Signaling.Listener {
        override fun onReady(servers: List<PeerConnection.IceServer>) {
            exec.execute { iceServers = servers }
        }
        override fun onConnectionChanged(connected: Boolean) = onState(connected)
        override fun onViewerJoin(viewerId: String) = exec.execute { addViewer(viewerId) }
        override fun onViewerLeft(viewerId: String) = exec.execute { removeViewer(viewerId) }
        override fun onSignal(from: String, kind: String, data: JSONObject) = exec.execute { onSignal(from, kind, data) }
    }

    private fun addViewer(viewerId: String) {
        if (peers.containsKey(viewerId)) return
        val config = PeerConnection.RTCConfiguration(iceServers).apply {
            sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
            continualGatheringPolicy = PeerConnection.ContinualGatheringPolicy.GATHER_CONTINUALLY
            bundlePolicy = PeerConnection.BundlePolicy.MAXBUNDLE
            rtcpMuxPolicy = PeerConnection.RtcpMuxPolicy.REQUIRE
            keyType = PeerConnection.KeyType.ECDSA
        }
        val pc = factory.createPeerConnection(config, PcObserver(viewerId)) ?: run {
            Log.e(TAG, "could not create PeerConnection for $viewerId"); return
        }
        pc.addTransceiver(videoTrack, RtpTransceiver.RtpTransceiverInit(RtpTransceiver.RtpTransceiverDirection.SEND_ONLY, listOf("dxstream")))
        applyBitrate(pc, quality)
        peers[viewerId] = pc
        onViewerCount(peers.size)
        Log.i(TAG, "viewer $viewerId joined (${peers.size} total)")
        createOffer(viewerId, pc, iceRestart = false)
    }

    private fun removeViewer(viewerId: String) {
        peers.remove(viewerId)?.let { runCatching { it.dispose() } }
        onViewerCount(peers.size)
        Log.i(TAG, "viewer $viewerId left (${peers.size} total)")
    }

    private fun createOffer(viewerId: String, pc: PeerConnection, iceRestart: Boolean) {
        val constraints = MediaConstraints().apply {
            if (iceRestart) mandatory.add(MediaConstraints.KeyValuePair("IceRestart", "true"))
        }
        pc.createOffer(object : SimpleSdp() {
            override fun onCreateSuccess(sdp: SessionDescription) {
                pc.setLocalDescription(SimpleSdp(), sdp)
                val data = JSONObject().put("sdp", sdp.description).put("type", sdp.type.canonicalForm())
                signaling?.sendSignal(viewerId, "offer", data)
            }
        }, constraints)
    }

    private fun onSignal(from: String, kind: String, data: JSONObject) {
        val pc = peers[from] ?: return
        when (kind) {
            "answer" -> {
                val sdp = SessionDescription(SessionDescription.Type.ANSWER, data.optString("sdp"))
                pc.setRemoteDescription(SimpleSdp(), sdp)
            }
            "ice" -> {
                val c = IceCandidate(
                    data.optString("sdpMid"),
                    data.optInt("sdpMLineIndex"),
                    data.optString("candidate"),
                )
                pc.addIceCandidate(c)
            }
        }
    }

    private fun applyBitrate(pc: PeerConnection, q: Quality) {
        for (sender in pc.senders) {
            if (sender.track()?.kind() != MediaStreamTrack.VIDEO_TRACK_KIND) continue
            val p = sender.parameters ?: continue
            for (enc in p.encodings) {
                enc.maxBitrateBps = q.image.bitrateKbps * 1000
            }
            p.degradationPreference = RtpParameters.DegradationPreference.MAINTAIN_FRAMERATE
            sender.parameters = p
        }
    }

    private inner class PcObserver(private val viewerId: String) : SimplePcObserver() {
        override fun onIceCandidate(c: IceCandidate) {
            val data = JSONObject()
                .put("candidate", c.sdp).put("sdpMid", c.sdpMid).put("sdpMLineIndex", c.sdpMLineIndex)
            signaling?.sendSignal(viewerId, "ice", data)
        }
        override fun onConnectionChange(state: PeerConnection.PeerConnectionState) {
            Log.i(TAG, "viewer $viewerId connection: $state")
            if (state == PeerConnection.PeerConnectionState.FAILED || state == PeerConnection.PeerConnectionState.CLOSED) {
                exec.execute { removeViewer(viewerId) }
            }
        }
    }

    private fun screenW() = com.dxdepartment.screenmonitor.capture.CaptureState.status.value.screenWidth.takeIf { it > 0 } ?: 1080
    private fun screenH() = com.dxdepartment.screenmonitor.capture.CaptureState.status.value.screenHeight.takeIf { it > 0 } ?: 1920

    companion object {
        private const val TAG = "DXWebRtc"
        private val initLock = Any()
        @Volatile private var factoryInited = false

        /** http(s):// → ws(s)://, append /ws if no path. */
        fun wsUrl(server: String): String {
            var s = server.trim()
            s = when {
                s.startsWith("https://") -> "wss://" + s.substring(8)
                s.startsWith("http://") -> "ws://" + s.substring(7)
                s.startsWith("wss://") || s.startsWith("ws://") -> s
                else -> "wss://$s"
            }
            val afterScheme = s.substringAfter("://")
            if (!afterScheme.contains("/")) s = "$s/ws"
            return s
        }
    }
}
