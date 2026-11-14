package com.dxdepartment.screenmonitor.settings

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.security.SecureRandom

/** Output size: the short side of the shared picture, in pixels (0 = the phone's own size). */
enum class Resolution(val label: String, val shortSide: Int) {
    P480("480p", 480),
    P720("720p", 720),
    P1080("1080p", 1080),
    NATIVE("Native", 0),
}

/** JPEG quality for the local viewer (later: the WebRTC bitrate). */
enum class ImageQuality(val label: String, val jpeg: Int, val bitrateKbps: Int) {
    LOW("Low", 45, 1500),
    MEDIUM("Medium", 65, 3000),
    HIGH("High", 80, 5000),
}

data class Quality(
    val resolution: Resolution = Resolution.P1080,
    val fps: Int = 30,
    val image: ImageQuality = ImageQuality.MEDIUM,
) {
    val summary: String get() = "${resolution.label} · $fps FPS · ${image.label}"

    companion object {
        val FPS_CHOICES = listOf(10, 15, 24, 30)

        /** Battery low: at most 720p and 10 FPS. */
        fun lowPower(q: Quality) = q.copy(
            resolution = if (q.resolution == Resolution.P480) Resolution.P480 else Resolution.P720,
            fps = minOf(q.fps, 10),
            image = if (q.image == ImageQuality.HIGH) ImageQuality.MEDIUM else q.image,
        )
    }
}

data class Settings(
    val deviceLabel: String,
    val quality: Quality,
    val viewerKey: String,
    val viewerPort: Int,
    val autoResume: Boolean,
    val localViewer: Boolean,
)

/** All settings in one SharedPreferences file, published as a StateFlow so the UI and the service follow changes. */
object AppSettings {
    private const val FILE = "dx_settings"
    private lateinit var prefs: SharedPreferences
    private lateinit var _state: MutableStateFlow<Settings>

    @Synchronized
    fun init(ctx: Context): StateFlow<Settings> {
        if (!this::prefs.isInitialized) {
            prefs = ctx.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)
            if (prefs.getString("viewer_key", null) == null) prefs.edit().putString("viewer_key", newKey()).apply()
            _state = MutableStateFlow(read())
        }
        return _state.asStateFlow()
    }

    fun get(ctx: Context): Settings = init(ctx).value

    private fun read(): Settings {
        val res = prefs.getString("resolution", null)?.let { runCatching { Resolution.valueOf(it) }.getOrNull() }
        val img = prefs.getString("image_quality", null)?.let { runCatching { ImageQuality.valueOf(it) }.getOrNull() }
        return Settings(
            deviceLabel = prefs.getString("device_label", "") ?: "",
            quality = Quality(
                resolution = res ?: Resolution.P1080,
                fps = prefs.getInt("fps", 30).takeIf { it in Quality.FPS_CHOICES } ?: 30,
                image = img ?: ImageQuality.MEDIUM,
            ),
            viewerKey = prefs.getString("viewer_key", "") ?: "",
            viewerPort = prefs.getInt("viewer_port", 8080),
            autoResume = prefs.getBoolean("auto_resume", true),
            localViewer = prefs.getBoolean("local_viewer", true),
        )
    }

    private fun edit(block: SharedPreferences.Editor.() -> Unit) {
        prefs.edit().apply(block).apply()
        _state.value = read()
    }

    fun setDeviceLabel(v: String) = edit { putString("device_label", v.trim().take(40)) }
    fun setResolution(v: Resolution) = edit { putString("resolution", v.name) }
    fun setFps(v: Int) = edit { putInt("fps", v) }
    fun setImageQuality(v: ImageQuality) = edit { putString("image_quality", v.name) }
    fun setAutoResume(v: Boolean) = edit { putBoolean("auto_resume", v) }
    fun setLocalViewer(v: Boolean) = edit { putBoolean("local_viewer", v) }
    fun newViewerKey() = edit { putString("viewer_key", newKey()) }

    // Recovery bookkeeping (not part of Settings: the UI doesn't show it).
    fun setWasSharing(ctx: Context, v: Boolean) {
        init(ctx); prefs.edit().putBoolean("was_sharing", v).apply()
    }

    fun wasSharing(ctx: Context): Boolean {
        init(ctx); return prefs.getBoolean("was_sharing", false)
    }

    /** 8 characters without look-alikes (0/O, 1/I/L), so it can be typed from the screen. */
    private fun newKey(): String {
        val abc = "ABCDEFGHJKMNPQRSTUVWXYZ23456789"
        val r = SecureRandom()
        return (1..8).map { abc[r.nextInt(abc.length)] }.joinToString("")
    }
}
