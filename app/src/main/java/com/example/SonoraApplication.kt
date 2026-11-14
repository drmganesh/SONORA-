package com.example

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import android.util.Log

class SonoraApplication : Application() {

    companion object {
        private const val TAG = "SonoraApplication"
        const val PLAYBACK_CHANNEL_ID = "sonora_playback_channel"
        lateinit var instance: SonoraApplication
            private set
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        Log.i(TAG, "SONORA startup initialized (SDK=${Build.VERSION.SDK_INT})")
        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                val name = "SONORA Playback Controls"
                val descriptionText = "Displays playback status, album artwork, and controls for SONORA music player"
                val importance = NotificationManager.IMPORTANCE_LOW
                val channel = NotificationChannel(PLAYBACK_CHANNEL_ID, name, importance).apply {
                    description = descriptionText
                    setShowBadge(false)
                    lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
                    enableVibration(false)
                    setSound(null, null)
                }
                val notificationManager = getSystemService(NotificationManager::class.java)
                if (notificationManager == null) {
                    Log.w(TAG, "NotificationManager service unavailable during startup")
                    return
                }
                notificationManager.createNotificationChannel(channel)
            } catch (t: Throwable) {
                Log.e(TAG, "Failed creating playback notification channel", t)
            }
        }
    }
}
