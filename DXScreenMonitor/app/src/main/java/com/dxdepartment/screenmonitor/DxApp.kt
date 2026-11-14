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
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_SHARING, "Screen sharing", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Shown while this phone's screen is being shared"
                setShowBadge(false)
            },
        )
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_RECOVERY, "Resume sharing", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Asks to resume sharing after a reboot or after the app was stopped"
            },
        )
    }

    companion object {
        const val CHANNEL_SHARING = "sharing"
        const val CHANNEL_RECOVERY = "recovery"
    }
}
