package com.dxdepartment.screenmonitor.capture

import android.app.Activity
import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.net.wifi.WifiManager
import android.os.BatteryManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.core.content.IntentCompat
import com.dxdepartment.screenmonitor.DxApp
import com.dxdepartment.screenmonitor.MainActivity
import com.dxdepartment.screenmonitor.R
import com.dxdepartment.screenmonitor.device.DeviceIdentity
import com.dxdepartment.screenmonitor.net.NetworkMonitor
import com.dxdepartment.screenmonitor.net.PreviewServer
import com.dxdepartment.screenmonitor.recovery.Recovery
import com.dxdepartment.screenmonitor.settings.AppSettings
import com.dxdepartment.screenmonitor.settings.Quality
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Foreground service that owns the MediaProjection, the capture engine and the local viewer.
 *
 * Order matters (Android 14+ enforces it, Android 12 tolerates it): startForeground with type mediaProjection
 * → getMediaProjection → registerCallback → createVirtualDisplay.
 */
class ScreenCaptureService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val main = Handler(Looper.getMainLooper())
    private var projection: MediaProjection? = null
    private var engine: CaptureEngine? = null
    private var server: PreviewServer? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var wifiLock: WifiManager.WifiLock? = null
    private var batteryReceiver: BroadcastReceiver? = null
    private var stoppingOnPurpose = false
    @Volatile private var lowPower = false

    private val projectionCallback = object : MediaProjection.Callback() {
        override fun onStop() {
            // The user tapped "Stop sharing" in the status bar / cast tile, another app took the projection,
            // or the system stopped it. The token can't be used again.
            Log.i(TAG, "MediaProjection stopped by the system")
            main.post { stopSharing(userStopped = !stoppingOnPurpose, reason = "Sharing was stopped by Android or from the status bar") }
        }

        override fun onCapturedContentResize(width: Int, height: Int) {
            engine?.onCapturedContentResize()
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                val code = intent.getIntExtra(EXTRA_RESULT_CODE, Activity.RESULT_CANCELED)
                val data = IntentCompat.getParcelableExtra(intent, EXTRA_RESULT_DATA, Intent::class.java)
                if (code != Activity.RESULT_OK || data == null) {
                    fail("Screen capture permission was not given")
                } else {
                    startSharing(code, data)
                }
            }
            ACTION_STOP -> stopSharing(userStopped = true, reason = null)
            else -> {
                // Restarted by Android after the process was killed (START_STICKY with no intent). The screen
                // capture permission doesn't survive that, so we can't capture: offer to resume and stop.
                Log.i(TAG, "service restarted without a projection token")
                if (projection == null) {
                    Recovery.requestResume(this, "The app was stopped by Android")
                    stopSelf()
                    return START_NOT_STICKY
                }
            }
        }
        return START_STICKY
    }

    private fun startSharing(code: Int, data: Intent) {
        if (projection != null) {
            Log.i(TAG, "already sharing; ignoring a second start")
            return
        }
        CaptureState.update {
            it.copy(phase = Phase.STARTING, message = null, startedAt = System.currentTimeMillis(), framesTotal = 0)
        }
        // 1) Foreground first, with the mediaProjection type.
        if (!startForegroundCompat(buildNotification("Starting…"))) {
            fail("Android didn't allow the screen-sharing service to start")
            return
        }
        // 2) The projection.
        val mpm = getSystemService(MediaProjectionManager::class.java)
        val p = try {
            mpm.getMediaProjection(code, data)
        } catch (e: Exception) {
            Log.e(TAG, "getMediaProjection failed", e)
            null
        }
        if (p == null) {
            fail("Android refused the screen capture (open the app and press Start again)")
            return
        }
        projection = p
        p.registerCallback(projectionCallback, main)

        // 3) Capture.
        val settings = AppSettings.get(this)
        val e = CaptureEngine(this, p, effectiveQuality(settings.quality))
        engine = e
        try {
            e.start()
        } catch (ex: Exception) {
            Log.e(TAG, "capture start failed", ex)
            fail("Capture could not start: ${ex.message}")
            return
        }
        acquireLocks()
        watchBattery()
        if (settings.localViewer) startServer(settings.viewerPort)

        AppSettings.setWasSharing(this, true)
        Recovery.clearResumeNotification(this)
        CaptureState.update { it.copy(phase = Phase.SHARING) }

        // Follow quality changes made in the app while sharing, and keep the notification current.
        scope.launch {
            AppSettings.init(this@ScreenCaptureService).map { it.quality }.distinctUntilChanged().collect { q ->
                engine?.setQuality(effectiveQuality(q))
            }
        }
        scope.launch {
            AppSettings.init(this@ScreenCaptureService).map { it.localViewer }.distinctUntilChanged().collect { on ->
                if (on && server == null) startServer(AppSettings.get(this@ScreenCaptureService).viewerPort)
                if (!on) stopServer()
            }
        }
        scope.launch {
            CaptureState.status.map { it.viewers to it.phase }.distinctUntilChanged()
                .combine(NetworkMonitor.state) { s, n -> s to n }
                .collect { (s, n) ->
                    if (s.second != Phase.SHARING) return@collect
                    val viewers = if (s.first == 1) "1 viewer" else "${s.first} viewers"
                    notify(buildNotification("$viewers · ${n.label}"))
                }
        }
        Log.i(TAG, "sharing started on ${DeviceIdentity.model}")
    }

    private fun startServer(port: Int) {
        val s = PreviewServer(this, port)
        if (s.start()) {
            server = s
        }
    }

    private fun stopServer() {
        server?.stop()
        server = null
    }

    private fun effectiveQuality(q: Quality) = if (lowPower) Quality.lowPower(q) else q

    /**
     * Low battery (≤ 15% and not charging): at most 720p / 10 FPS until it's above 20% or charging.
     * Sharing itself goes on; Android's own battery saver may stop it at 5%.
     */
    private fun watchBattery() {
        val r = object : BroadcastReceiver() {
            override fun onReceive(c: Context, i: Intent) {
                val level = i.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                val scale = i.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
                if (level < 0 || scale <= 0) return
                val pct = level * 100 / scale
                val st = i.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
                val charging = st == BatteryManager.BATTERY_STATUS_CHARGING || st == BatteryManager.BATTERY_STATUS_FULL
                val low = when {
                    charging -> false
                    pct <= 15 -> true
                    pct > 20 -> false
                    else -> lowPower
                }
                CaptureState.update { it.copy(batteryPercent = pct, charging = charging, lowPower = low) }
                if (low != lowPower) {
                    lowPower = low
                    Log.i(TAG, "battery $pct% charging=$charging: low power ${if (low) "on" else "off"}")
                    engine?.setQuality(effectiveQuality(AppSettings.get(this@ScreenCaptureService).quality))
                }
            }
        }
        batteryReceiver = r
        ContextCompat.registerReceiver(this, r, IntentFilter(Intent.ACTION_BATTERY_CHANGED), ContextCompat.RECEIVER_NOT_EXPORTED)
    }

    /** With the screen off the CPU and Wi-Fi would sleep and the viewers would freeze. */
    private fun acquireLocks() {
        val pm = getSystemService(PowerManager::class.java)
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "DXScreenMonitor:sharing").apply {
            setReferenceCounted(false)
            acquire()
        }
        val wm = applicationContext.getSystemService(WifiManager::class.java)
        @Suppress("DEPRECATION")
        val mode = if (Build.VERSION.SDK_INT >= 29) WifiManager.WIFI_MODE_FULL_LOW_LATENCY else WifiManager.WIFI_MODE_FULL_HIGH_PERF
        wifiLock = wm?.createWifiLock(mode, "DXScreenMonitor:sharing")?.apply {
            setReferenceCounted(false)
            acquire()
        }
    }

    private fun releaseLocks() {
        runCatching { wakeLock?.takeIf { it.isHeld }?.release() }
        runCatching { wifiLock?.takeIf { it.isHeld }?.release() }
        wakeLock = null; wifiLock = null
    }

    private fun stopSharing(userStopped: Boolean, reason: String?) {
        stoppingOnPurpose = true
        scope.coroutineContext[kotlinx.coroutines.Job]?.children?.forEach { it.cancel() }
        releaseAll()
        if (userStopped) AppSettings.setWasSharing(this, false)
        CaptureState.update {
            it.copy(phase = Phase.STOPPED, message = reason, viewers = 0, captureFps = 0.0, encodeFps = 0.0, serverPort = null)
        }
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun fail(reason: String) {
        Log.w(TAG, "fail: $reason")
        releaseAll()
        CaptureState.update { it.copy(phase = Phase.ERROR, message = reason, serverPort = null, viewers = 0) }
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun releaseAll() {
        stopServer()
        engine?.release(); engine = null
        projection?.let {
            runCatching { it.unregisterCallback(projectionCallback) }
            runCatching { it.stop() }
        }
        projection = null
        batteryReceiver?.let { runCatching { unregisterReceiver(it) } }
        batteryReceiver = null
        releaseLocks()
    }

    override fun onDestroy() {
        stoppingOnPurpose = true
        releaseAll()
        scope.cancel()
        if (CaptureState.status.value.sharing) CaptureState.update { it.copy(phase = Phase.STOPPED) }
        super.onDestroy()
    }

    private fun startForegroundCompat(n: Notification): Boolean = try {
        ServiceCompat.startForeground(
            this, NOTIFICATION_ID, n,
            if (Build.VERSION.SDK_INT >= 29) ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION else 0,
        )
        true
    } catch (e: Exception) {
        Log.e(TAG, "startForeground failed", e)
        false
    }

    private fun notify(n: Notification) {
        runCatching { getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, n) }
    }

    private fun buildNotification(text: String): Notification {
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val stop = PendingIntent.getService(
            this, 1, Intent(this, ScreenCaptureService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return NotificationCompat.Builder(this, DxApp.CHANNEL_SHARING)
            .setSmallIcon(R.drawable.ic_stat_share)
            .setContentTitle("Screen is being shared")
            .setContentText("${DeviceIdentity.name(this)} · $text")
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setContentIntent(open)
            .addAction(0, "Stop sharing", stop)
            .build()
    }

    companion object {
        private const val TAG = "DXService"
        const val NOTIFICATION_ID = 1001
        const val ACTION_START = "com.dxdepartment.screenmonitor.START"
        const val ACTION_STOP = "com.dxdepartment.screenmonitor.STOP"
        const val EXTRA_RESULT_CODE = "result_code"
        const val EXTRA_RESULT_DATA = "result_data"

        fun start(ctx: Context, resultCode: Int, data: Intent) {
            val i = Intent(ctx, ScreenCaptureService::class.java)
                .setAction(ACTION_START)
                .putExtra(EXTRA_RESULT_CODE, resultCode)
                .putExtra(EXTRA_RESULT_DATA, data)
            ContextCompat.startForegroundService(ctx, i)
        }

        fun stop(ctx: Context) {
            ctx.startService(Intent(ctx, ScreenCaptureService::class.java).setAction(ACTION_STOP))
        }
    }
}
