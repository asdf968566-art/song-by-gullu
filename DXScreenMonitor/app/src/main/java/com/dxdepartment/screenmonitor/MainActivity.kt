package com.dxdepartment.screenmonitor

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionConfig
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.dxdepartment.screenmonitor.capture.CaptureState
import com.dxdepartment.screenmonitor.capture.Phase
import com.dxdepartment.screenmonitor.capture.ScreenCaptureService
import com.dxdepartment.screenmonitor.recovery.Recovery
import com.dxdepartment.screenmonitor.settings.AppSettings
import com.dxdepartment.screenmonitor.ui.DxTheme
import com.dxdepartment.screenmonitor.ui.MainScreen
import com.dxdepartment.screenmonitor.ui.ScreenActions

class MainActivity : ComponentActivity() {

    /** The "Start now" dialog is open (a second request would waste a token). */
    private var asking = false

    private val consent = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        asking = false
        val data = r.data
        if (r.resultCode == RESULT_OK && data != null) {
            Log.i(TAG, "screen capture allowed")
            ScreenCaptureService.start(this, r.resultCode, data)
        } else {
            Log.i(TAG, "screen capture not allowed")
            AppSettings.setWasSharing(this, false)
            CaptureState.update { it.copy(phase = Phase.IDLE, message = "Screen capture was not allowed") }
        }
    }

    private val notificationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) {}

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AppSettings.init(this)
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        val actions = ScreenActions(
            start = ::requestCapture,
            stop = { ScreenCaptureService.stop(this) },
            openOverlaySettings = {
                open(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
            },
            openBatterySettings = {
                @Suppress("BatteryLife")
                val ask = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:$packageName"))
                if (!open(ask)) open(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
            },
        )
        setContent {
            DxTheme {
                MainScreen(actions)
            }
        }
        if (savedInstanceState == null) handleAutoResume(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleAutoResume(intent)
    }

    /** Opened by Recovery (after a reboot / process restart): ask for the screen again at once. */
    private fun handleAutoResume(i: Intent?) {
        if (i == null || !i.getBooleanExtra(Recovery.EXTRA_AUTO_RESUME, false)) return
        i.removeExtra(Recovery.EXTRA_AUTO_RESUME)
        Recovery.clearResumeNotification(this)
        if (!CaptureState.status.value.sharing) {
            Log.i(TAG, "auto resume")
            requestCapture()
        }
    }

    private fun requestCapture() {
        if (CaptureState.status.value.sharing || asking) return
        val mpm = getSystemService(MediaProjectionManager::class.java)
        val ask = if (Build.VERSION.SDK_INT >= 34) {
            // Android 14+ also offers "a single app"; the monitor needs the whole screen.
            mpm.createScreenCaptureIntent(MediaProjectionConfig.createConfigForDefaultDisplay())
        } else {
            mpm.createScreenCaptureIntent()
        }
        CaptureState.update { it.copy(message = null) }
        try {
            asking = true
            consent.launch(ask)
        } catch (e: Exception) {
            asking = false
            CaptureState.update { it.copy(phase = Phase.ERROR, message = "This phone can't share its screen: ${e.message}") }
        }
    }

    private fun open(i: Intent): Boolean = try {
        startActivity(i); true
    } catch (e: Exception) {
        false
    }

    companion object {
        private const val TAG = "DXMain"
    }
}
