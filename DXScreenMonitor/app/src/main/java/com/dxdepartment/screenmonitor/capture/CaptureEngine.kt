package com.dxdepartment.screenmonitor.capture

import android.content.Context
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.Image
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.os.Handler
import android.os.HandlerThread
import android.os.SystemClock
import android.util.DisplayMetrics
import android.util.Log
import android.view.Display
import com.dxdepartment.screenmonitor.settings.Quality
import java.io.ByteArrayOutputStream
import kotlin.math.roundToInt

/**
 * Mirrors the phone's screen into an [ImageReader] through a [VirtualDisplay] and turns frames into JPEGs
 * for [FrameHub].
 *
 * CPU use is kept low:
 * - the virtual display is created at the output size (1080p etc.), so the GPU scales, not the CPU;
 * - frames come on their own thread, and only the newest one is taken (`acquireLatestImage`);
 * - frames faster than the FPS target are dropped before any pixel is copied;
 * - with no viewer connected, at most one picture a second is encoded (for the app's own preview).
 *
 * Rotation / size changes: a [DisplayManager.DisplayListener] resizes the virtual display and swaps in an
 * ImageReader of the new size (on the capture thread, so no frame is read from a closed reader).
 */
class CaptureEngine(
    private val context: Context,
    private val projection: MediaProjection,
    initialQuality: Quality,
) {
    private val thread = HandlerThread("dx-capture").apply { start() }
    private val handler = Handler(thread.looper)
    private val displayManager = context.getSystemService(DisplayManager::class.java)

    @Volatile private var quality = initialQuality
    private var virtualDisplay: VirtualDisplay? = null
    private var reader: ImageReader? = null
    private var outW = 0
    private var outH = 0
    private var released = false

    // Reused buffers: one bitmap of the reader's padded size, one output stream.
    private var bitmap: Bitmap? = null
    private val jpegOut = ByteArrayOutputStream(256 * 1024)

    private var lastEncodeAt = 0L
    private var framesThisSecond = 0
    private var encodedThisSecond = 0
    private var secondStartedAt = SystemClock.elapsedRealtime()
    private var framesTotal = 0L

    private val displayListener = object : DisplayManager.DisplayListener {
        override fun onDisplayAdded(displayId: Int) {}
        override fun onDisplayRemoved(displayId: Int) {}
        override fun onDisplayChanged(displayId: Int) {
            if (displayId == Display.DEFAULT_DISPLAY) refreshSize("display changed")
        }
    }

    /** Must be called after MediaProjection.registerCallback (Android 14 requires that order). */
    fun start() {
        handler.post {
            val (sw, sh, dpi) = screenSize()
            val (w, h) = outputSize(sw, sh, quality)
            outW = w; outH = h
            val r = newReader(w, h)
            reader = r
            virtualDisplay = projection.createVirtualDisplay(
                "DXScreenMonitor", w, h, dpi,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                r.surface, null, handler,
            )
            Log.i(TAG, "capture started: screen ${sw}x$sh -> ${w}x$h @ ${dpi}dpi, ${quality.summary}")
            publishSize(sw, sh)
        }
        displayManager.registerDisplayListener(displayListener, handler)
    }

    /** New quality (from the UI or the battery monitor): a new size takes effect at once. */
    fun setQuality(q: Quality) {
        val old = quality
        quality = q
        if (old.resolution != q.resolution) refreshSize("quality changed")
    }

    /** Android 14+ tells the app when the captured content changes size (MediaProjection.Callback). */
    fun onCapturedContentResize() = refreshSize("captured content resized")

    private fun refreshSize(why: String) {
        handler.post {
            if (released) return@post
            val vd = virtualDisplay ?: return@post
            val (sw, sh, dpi) = screenSize()
            val (w, h) = outputSize(sw, sh, quality)
            publishSize(sw, sh)
            if (w == outW && h == outH) return@post
            Log.i(TAG, "$why: ${outW}x$outH -> ${w}x$h")
            val old = reader
            val r = newReader(w, h)
            vd.resize(w, h, dpi)
            vd.surface = r.surface
            reader = r
            outW = w; outH = h
            bitmap?.recycle(); bitmap = null
            old?.setOnImageAvailableListener(null, null)
            old?.close()
        }
    }

    private fun newReader(w: Int, h: Int): ImageReader =
        ImageReader.newInstance(w, h, PixelFormat.RGBA_8888, 2).apply {
            setOnImageAvailableListener({ onImage(it) }, handler)
        }

    private fun onImage(r: ImageReader) {
        val image = try {
            r.acquireLatestImage()
        } catch (e: IllegalStateException) {
            null
        } ?: return
        try {
            framesThisSecond++
            framesTotal++
            val now = SystemClock.elapsedRealtime()
            val q = quality
            val busy = FrameHub.viewers.get() > 0
            val interval = if (busy) 1000L / q.fps else if (FrameHub.previewWanted) 500L else 1000L
            if (now - lastEncodeAt >= interval - 2) {
                lastEncodeAt = now
                encode(image, q)
                encodedThisSecond++
            }
            if (now - secondStartedAt >= 1000) {
                val secs = (now - secondStartedAt) / 1000.0
                val cap = framesThisSecond / secs
                val enc = encodedThisSecond / secs
                framesThisSecond = 0; encodedThisSecond = 0; secondStartedAt = now
                val total = framesTotal
                CaptureState.update { it.copy(captureFps = cap, encodeFps = enc, framesTotal = total) }
            }
        } catch (e: Exception) {
            Log.w(TAG, "frame failed", e)
        } finally {
            image.close()
        }
    }

    private fun encode(image: Image, q: Quality) {
        val plane = image.planes[0]
        val pixelStride = plane.pixelStride
        val rowStride = plane.rowStride
        val w = image.width
        val h = image.height
        // Rows can be padded: the bitmap is as wide as a padded row, and the picture is cut out of it.
        val paddedW = rowStride / pixelStride
        var bmp = bitmap
        if (bmp == null || bmp.width != paddedW || bmp.height != h) {
            bmp?.recycle()
            bmp = Bitmap.createBitmap(paddedW, h, Bitmap.Config.ARGB_8888)
            bitmap = bmp
        }
        plane.buffer.rewind()
        bmp!!.copyPixelsFromBuffer(plane.buffer)
        val out = if (paddedW != w) Bitmap.createBitmap(bmp, 0, 0, w, h) else bmp
        jpegOut.reset()
        out.compress(Bitmap.CompressFormat.JPEG, q.image.jpeg, jpegOut)
        if (out !== bmp) out.recycle()
        val bytes = jpegOut.toByteArray()
        FrameHub.publish(bytes, w, h)
        CaptureState.update { it.copy(lastFrameKb = bytes.size / 1024) }
    }

    private fun publishSize(sw: Int, sh: Int) {
        val rot = displayManager.getDisplay(Display.DEFAULT_DISPLAY)?.rotation ?: 0
        val (w, h) = outputSize(sw, sh, quality)
        CaptureState.update { it.copy(width = w, height = h, screenWidth = sw, screenHeight = sh, rotation = rot * 90) }
    }

    @Suppress("DEPRECATION")
    private fun screenSize(): Triple<Int, Int, Int> {
        val m = DisplayMetrics()
        displayManager.getDisplay(Display.DEFAULT_DISPLAY).getRealMetrics(m)
        return Triple(m.widthPixels, m.heightPixels, m.densityDpi)
    }

    fun release() {
        displayManager.unregisterDisplayListener(displayListener)
        handler.post {
            released = true
            virtualDisplay?.release(); virtualDisplay = null
            reader?.setOnImageAvailableListener(null, null)
            reader?.close(); reader = null
            bitmap?.recycle(); bitmap = null
            FrameHub.clear()
            Log.i(TAG, "capture released")
        }
        thread.quitSafely()
    }

    companion object {
        private const val TAG = "DXCapture"

        /**
         * Scales the screen so its short side is the chosen resolution (never upscales), keeping the aspect ratio.
         * Sizes are even numbers (video encoders need that later for WebRTC).
         */
        fun outputSize(sw: Int, sh: Int, q: Quality): Pair<Int, Int> {
            val target = q.resolution.shortSide
            val short = minOf(sw, sh)
            if (target <= 0 || short <= target) return even(sw) to even(sh)
            val scale = target.toDouble() / short
            return even((sw * scale).roundToInt()) to even((sh * scale).roundToInt())
        }

        private fun even(v: Int) = maxOf(2, v - v % 2)
    }
}
