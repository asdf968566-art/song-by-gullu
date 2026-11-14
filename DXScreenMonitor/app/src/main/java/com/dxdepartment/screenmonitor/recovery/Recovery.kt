package com.dxdepartment.screenmonitor.recovery

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import androidx.core.app.NotificationCompat
import com.dxdepartment.screenmonitor.DxApp
import com.dxdepartment.screenmonitor.MainActivity
import com.dxdepartment.screenmonitor.R
import com.dxdepartment.screenmonitor.capture.CaptureState
import com.dxdepartment.screenmonitor.settings.AppSettings

/**
 * Getting sharing back after a reboot, a crash or Android killing the app.
 *
 * Android never lets an app keep its screen-capture permission across a process restart: a new
 * permission (the "Start now" dialog) is needed each time. So recovery = open the app and ask again:
 * - with "Display over other apps" allowed, the app opens by itself and shows the dialog at once;
 * - otherwise a notification "Tap to resume sharing" does the same with one tap.
 * On department phones the dialog itself can be pre-approved once over USB
 * (`adb shell appops set com.dxdepartment.screenmonitor PROJECT_MEDIA allow`, Android 10–13), and then resuming
 * needs no tap at all. See the README.
 */
object Recovery {
    private const val TAG = "DXRecovery"
    private const val NOTIFICATION_ID = 1002
    const val EXTRA_AUTO_RESUME = "auto_resume"

    /** Called in Application.onCreate: the process is new; if sharing was on, it was cut off. */
    fun checkAfterProcessStart(ctx: Context) {
        if (!CaptureState.status.value.sharing && AppSettings.wasSharing(ctx)) {
            requestResume(ctx, "Sharing was interrupted")
        }
    }

    /** After a reboot / app update (BootReceiver) or a process restart. */
    fun requestResume(ctx: Context, reason: String) {
        if (!AppSettings.wasSharing(ctx)) return
        val auto = AppSettings.get(ctx).autoResume
        Log.i(TAG, "resume wanted: $reason (auto=$auto)")
        if (auto && Settings.canDrawOverlays(ctx)) {
            try {
                ctx.startActivity(resumeIntent(ctx))
                return
            } catch (e: Exception) {
                Log.w(TAG, "couldn't open the app from the background: ${e.message}")
            }
        }
        showResumeNotification(ctx, reason)
    }

    fun resumeIntent(ctx: Context) = Intent(ctx, MainActivity::class.java)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        .putExtra(EXTRA_AUTO_RESUME, true)

    private fun showResumeNotification(ctx: Context, reason: String) {
        val pi = PendingIntent.getActivity(
            ctx, 2, resumeIntent(ctx), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val n = NotificationCompat.Builder(ctx, DxApp.CHANNEL_RECOVERY)
            .setSmallIcon(R.drawable.ic_stat_share)
            .setContentTitle("Tap to resume screen sharing")
            .setContentText(reason)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setContentIntent(pi)
            .build()
        runCatching { ctx.getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, n) }
    }

    fun clearResumeNotification(ctx: Context) {
        runCatching { ctx.getSystemService(NotificationManager::class.java).cancel(NOTIFICATION_ID) }
    }

    fun ignoringBatteryOptimizations(ctx: Context): Boolean =
        ctx.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(ctx.packageName)
}
