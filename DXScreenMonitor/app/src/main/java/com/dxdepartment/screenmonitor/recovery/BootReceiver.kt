package com.dxdepartment.screenmonitor.recovery

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/** After the phone restarts (or the app is updated), offer to resume sharing if it was on. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val why = when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED -> "The phone restarted"
            Intent.ACTION_MY_PACKAGE_REPLACED -> "The app was updated"
            else -> return
        }
        Log.i("DXRecovery", "$why (${intent.action})")
        Recovery.requestResume(context, why)
    }
}
