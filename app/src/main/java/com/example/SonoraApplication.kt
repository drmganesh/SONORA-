package com.example

import android.app.ActivityManager
import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.graphics.Bitmap
import android.os.Build
import android.util.Log
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache

class SonoraApplication : Application(), ImageLoaderFactory {

    companion object {
        private const val TAG = "SonoraApplication"
        const val PLAYBACK_CHANNEL_ID = "sonora_playback_channel"
        const val DOWNLOADS_CHANNEL_ID = "sonora_downloads_channel"
        const val CHANNEL_UPLOADS_CHANNEL_ID = "sonora_channel_uploads"
        lateinit var instance: SonoraApplication
            private set

        @Volatile
        var hadPreExistingInstallationDataOnProcessStart: Boolean = false
            private set

        /**
         * Detects whether this device already had SONORA user data from a prior installation
         * before the current process created databases or preference stores.
         */
        fun detectExistingInstallationData(context: Context): Boolean {
            return runCatching {
                val dbFile = context.getDatabasePath("sonora_database")
                if (dbFile != null && dbFile.exists() && dbFile.length() > 0L) {
                    return true
                }
                val appPrefs = context.getSharedPreferences("sonora_app_settings", Context.MODE_PRIVATE)
                if (appPrefs.all.isNotEmpty()) {
                    return true
                }
                val datastoreDir = java.io.File(context.filesDir, "datastore")
                if (datastoreDir.exists() && datastoreDir.isDirectory) {
                    val existingFiles = datastoreDir.listFiles().orEmpty()
                    val hasOtherPriorStore = existingFiles.any { file ->
                        file.isFile &&
                            file.length() > 0L &&
                            file.name.endsWith(".preferences_pb") &&
                            file.name != "sonora_user_profile.preferences_pb"
                    }
                    if (hasOtherPriorStore) return true
                }
                false
            }.getOrDefault(false)
        }
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        util.SonoraCrashDiagnostics.installIfDebug(this)
        hadPreExistingInstallationDataOnProcessStart = detectExistingInstallationData(this)
        data.online.NewPipeInitializer.ensureInitialized(this)
        Log.i(TAG, "SONORA startup initialized (SDK=${Build.VERSION.SDK_INT}, existingInstall=$hadPreExistingInstallationDataOnProcessStart)")
        createNotificationChannel()
    }

    override fun newImageLoader(): ImageLoader {
        val am = getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        val isLowRam = runCatching { am?.isLowRamDevice == true }.getOrDefault(false)
        return ImageLoader.Builder(this)
            .memoryCache {
                MemoryCache.Builder(this)
                    .maxSizePercent(if (isLowRam) 0.12 else 0.20)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("sonora_image_cache"))
                    .maxSizeBytes(if (isLowRam) 30L * 1024 * 1024 else 60L * 1024 * 1024)
                    .build()
            }
            .bitmapConfig(Bitmap.Config.RGB_565)
            .respectCacheHeaders(false)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                val notificationManager = getSystemService(NotificationManager::class.java)
                if (notificationManager == null) {
                    Log.w(TAG, "NotificationManager service unavailable during startup")
                    return
                }
                val playbackChannel = NotificationChannel(
                    PLAYBACK_CHANNEL_ID,
                    "SONORA Playback Controls",
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = "Displays playback status, album artwork, and controls for SONORA music player"
                    setShowBadge(false)
                    lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
                    enableVibration(false)
                    setSound(null, null)
                }
                val downloadsChannel = NotificationChannel(
                    DOWNLOADS_CHANNEL_ID,
                    "SONORA Music Downloads",
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = "Shows background audio download progress and completion updates"
                    setShowBadge(true)
                    enableVibration(false)
                    setSound(null, null)
                }
                val channelUploadsChannel = NotificationChannel(
                    CHANNEL_UPLOADS_CHANNEL_ID,
                    "SONORA Followed Channel Uploads",
                    NotificationManager.IMPORTANCE_DEFAULT
                ).apply {
                    description = "Alerts when channels you follow in SONORA publish new videos"
                    setShowBadge(true)
                }
                notificationManager.createNotificationChannel(playbackChannel)
                notificationManager.createNotificationChannel(downloadsChannel)
                notificationManager.createNotificationChannel(channelUploadsChannel)
            } catch (t: Throwable) {
                Log.e(TAG, "Failed creating notification channels", t)
            }
        }
    }
}
