package com.dxdepartment.screenmonitor.recovery

import android.content.Context
import android.content.Intent
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import com.dxdepartment.screenmonitor.MainActivity
import com.dxdepartment.screenmonitor.capture.CaptureState
import com.dxdepartment.screenmonitor.settings.AppSettings

/**
 * Getting sharing back by itself after a reboot, a crash or Android killing the app — with no taps and
 * no extra notification (owner: "koi faltu notification nhi, one time setup, 24x7 chale").
 *
 * Android never lets an app keep its screen-capture permission across a process restart. So resuming means
 * opening the app and asking for the screen again. For that to be silent (no dialog, no tap), the one-time
 * setup grants two things over USB, once per phone:
 *   adb shell appops set com.dxdepartment.screenmonitor PROJECT_MEDIA allow      (Android 10–13: auto-approves the dialog)
 *   adb shell appops set com.dxdepartment.screenmonitor SYSTEM_ALERT_WINDOW allow (lets the app open itself in the background)
 * Then: boot / kill → the app opens itself → the capture request returns instantly → sharing is back.
 * Without the overlay grant the app can't open itself from the background on Android 12+, so it resumes the
 * next time someone opens it; nothing is shown in the meantime (no notification is posted).
 */
object Recovery {
    private const val TAG = "DXRecovery"
    const val EXTRA_AUTO_RESUME = "auto_resume"

    /** Called in Application.onCreate: the process is new; if sharing should be on, it was cut off. */
    fun checkAfterProcessStart(ctx: Context) {
        if (!CaptureState.status.value.sharing && shouldRun(ctx)) {
            requestResume(ctx, "process started")
        }
    }

    /** After a reboot / app update (BootReceiver) or a process restart: open the app so it shares again. */
    fun requestResume(ctx: Context, reason: String) {
        if (!shouldRun(ctx)) return
        Log.i(TAG, "resume wanted: $reason")
        if (Settings.canDrawOverlays(ctx)) {
            try {
                ctx.startActivity(resumeIntent(ctx))
            } catch (e: Exception) {
                // Android 12+ blocks a background activity start without the overlay grant: resume on next open.
                Log.w(TAG, "couldn't open the app from the background: ${e.message}")
            }
        } else {
            Log.i(TAG, "overlay not allowed: will resume the next time the app is opened")
        }
    }

    /** Sharing should be running: the 24×7 switch is on and it was started at least once (not stopped by hand). */
    fun shouldRun(ctx: Context): Boolean = AppSettings.get(ctx).autoResume && AppSettings.wasSharing(ctx)

    fun resumeIntent(ctx: Context) = Intent(ctx, MainActivity::class.java)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        .putExtra(EXTRA_AUTO_RESUME, true)

    fun ignoringBatteryOptimizations(ctx: Context): Boolean =
        ctx.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(ctx.packageName)
}
