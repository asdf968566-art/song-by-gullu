package com.dxdepartment.screenmonitor.webrtc

import android.content.Context
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.projection.MediaProjection
import android.util.Log
import android.view.Surface
import org.webrtc.CapturerObserver
import org.webrtc.SurfaceTextureHelper
import org.webrtc.VideoCapturer
import org.webrtc.VideoSink

/**
 * A WebRTC [VideoCapturer] that mirrors the screen using a MediaProjection the service already owns.
 *
 * This is like libwebrtc's own ScreenCapturerAndroid, but it takes an existing [MediaProjection] instead of
 * the consent Intent — so the single projection the foreground service holds backs both this (for WebRTC, a
 * GPU texture feed into the hardware encoder) and the ImageReader used by the local LAN viewer. It never stops
 * the projection; the service owns its lifecycle.
 */
class ProjectionVideoCapturer(
    private val projection: MediaProjection,
    private val densityDpi: Int,
) : VideoCapturer {

    private var helper: SurfaceTextureHelper? = null
    private var observer: CapturerObserver? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var surface: Surface? = null
    private var width = 0
    private var height = 0

    override fun initialize(surfaceTextureHelper: SurfaceTextureHelper, context: Context, capturerObserver: CapturerObserver) {
        this.helper = surfaceTextureHelper
        this.observer = capturerObserver
    }

    override fun startCapture(width: Int, height: Int, framerate: Int) {
        this.width = width
        this.height = height
        val h = helper ?: return
        h.setTextureSize(width, height)
        surface = Surface(h.surfaceTexture)
        virtualDisplay = projection.createVirtualDisplay(
            "DXWebRtc", width, height, densityDpi,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            surface, null, null,
        )
        h.startListening(VideoSink { frame -> observer?.onFrameCaptured(frame) })
        observer?.onCapturerStarted(true)
        Log.i(TAG, "WebRTC capture ${width}x$height @ ${framerate}fps")
    }

    override fun changeCaptureFormat(width: Int, height: Int, framerate: Int) {
        if (width == this.width && height == this.height) return
        this.width = width
        this.height = height
        helper?.setTextureSize(width, height)
        virtualDisplay?.resize(width, height, densityDpi)
    }

    override fun stopCapture() {
        helper?.stopListening()
        virtualDisplay?.release(); virtualDisplay = null
        surface?.release(); surface = null
        observer?.onCapturerStopped()
    }

    override fun dispose() {
        stopCapture()
    }

    override fun isScreencast(): Boolean = true

    companion object {
        private const val TAG = "DXWebRtcCapturer"
    }
}
