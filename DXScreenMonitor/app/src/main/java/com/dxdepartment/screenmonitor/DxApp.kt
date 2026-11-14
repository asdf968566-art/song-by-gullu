package com.dxdepartment.screenmonitor

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import com.dxdepartment.screenmonitor.net.NetworkMonitor
import com.dxdepartment.screenmonitor.recovery.Recovery

class DxApp : Application() {
    override fun onCreate() {
        super.onCreate()
        createChannels()
        NetworkMonitor.start(this)
        // The process was started again (after a crash, being killed, or by the system) while sharing was on:
        // the screen-capture permission died with the old process, so offer to resume.
        Recovery.checkAfterProcessStart(this)
    }

    private fun createChannels() {
        // The only notification: the one Android forces while a screen is being captured. Kept silent and low,
        // with no badge, so it never interrupts. There is no "resume" or any other notification.
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_SHARING, "Screen sharing", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Shown by Android while this phone's screen is being shared (required, cannot be removed)"
                setShowBadge(false)
                setSound(null, null)
                enableVibration(false)
            },
        )
    }

    companion object {
        const val CHANNEL_SHARING = "sharing"
    }
}
