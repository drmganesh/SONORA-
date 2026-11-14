package com.example.service

import android.Manifest
import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import android.graphics.RectF
import android.os.Build
import android.os.SystemClock
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.util.Log
import android.util.LruCache
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import androidx.media.app.NotificationCompat.MediaStyle
import androidx.palette.graphics.Palette
import com.example.MainActivity
import com.example.R
import com.example.SonoraApplication
import com.example.model.RepeatMode
import com.example.model.Song
import com.example.util.AlbumArtHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Flagship Android MediaStyle Notification Manager for SONORA.
 *
 * Implements real AndroidX MediaStyle notification, MediaSessionCompat state & metadata integration,
 * dynamic Palette color extraction, rounded artwork generation, and lock-screen media player support.
 */
class SonoraNotificationManager(
    private val service: SonoraMusicService,
    private val scope: CoroutineScope
) {

    companion object {
        const val NOTIFICATION_ID = 2026
        private const val DEFAULT_ACCENT_COLOR = 0xFFD4AF37.toInt() // Sonora Gold

        // Actions
        const val ACTION_PLAY = "com.example.sonora.ACTION_PLAY"
        const val ACTION_PAUSE = "com.example.sonora.ACTION_PAUSE"
        const val ACTION_NEXT = "com.example.sonora.ACTION_NEXT"
        const val ACTION_PREV = "com.example.sonora.ACTION_PREV"
        const val ACTION_SHUFFLE = "com.example.sonora.ACTION_SHUFFLE"
        const val ACTION_REPEAT = "com.example.sonora.ACTION_REPEAT"
        const val ACTION_STOP = "com.example.sonora.ACTION_STOP"
        const val ACTION_SEEK = "com.example.sonora.ACTION_SEEK"
        const val EXTRA_SEEK_POSITION = "com.example.sonora.EXTRA_SEEK_POSITION"
    }

    var mediaSession: MediaSessionCompat? = null
        private set

    // Memory cache for rounded artwork bitmaps (keyed by song ID)
    private val roundedBitmapCache = object : LruCache<Long, Bitmap>(20) {}

    // Memory cache for extracted accent colors (keyed by song ID)
    private val accentColorCache = object : LruCache<Long, Int>(50) {}

    private var currentSong: Song? = null
    private var isPlayingState: Boolean = false
    private var isShuffleState: Boolean = false
    private var repeatModeState: RepeatMode = RepeatMode.OFF
    private var currentDeviceName: String = ""
    private var isForegroundActive = false
    private var artworkLoadingJob: Job? = null
    private val prefs by lazy { com.example.data.SonoraPreferences(service) }

    init {
        setupMediaSession()
    }

    private fun setupMediaSession() {
        try {
            mediaSession = MediaSessionCompat(service, "SonoraMediaSession").apply {
                setCallback(object : MediaSessionCompat.Callback() {
                    override fun onPlay() {
                        service.playbackCallback?.onRemotePlay()
                    }

                    override fun onPause() {
                        service.playbackCallback?.onRemotePause()
                    }

                    override fun onSkipToNext() {
                        service.playbackCallback?.onRemoteNext()
                    }

                    override fun onSkipToPrevious() {
                        service.playbackCallback?.onRemotePrev()
                    }

                    override fun onSeekTo(pos: Long) {
                        service.playbackCallback?.onRemoteSeek(pos)
                    }

                    override fun onSetShuffleMode(shuffleMode: Int) {
                        service.playbackCallback?.onRemoteShuffle()
                    }

                    override fun onSetRepeatMode(repeatMode: Int) {
                        service.playbackCallback?.onRemoteRepeat()
                    }

                    override fun onCustomAction(action: String?, extras: android.os.Bundle?) {
                        when (action) {
                            ACTION_SHUFFLE -> service.playbackCallback?.onRemoteShuffle()
                            ACTION_REPEAT -> service.playbackCallback?.onRemoteRepeat()
                        }
                    }
                })
                isActive = true
            }
        } catch (e: Exception) {
            Log.e("SonoraNotifManager", "Failed initializing MediaSessionCompat", e)
            mediaSession = null
        }
    }

    /**
     * Updates notification and MediaSession state.
     */
    fun updateNotification(
        song: Song?,
        isPlaying: Boolean,
        isShuffle: Boolean = isShuffleState,
        repeatMode: RepeatMode = repeatModeState,
        deviceName: String = currentDeviceName,
        currentPositionMs: Long? = null
    ) {
        this.currentSong = song
        this.isPlayingState = isPlaying
        this.isShuffleState = isShuffle
        this.repeatModeState = repeatMode
        this.currentDeviceName = deviceName

        if (song == null) {
            clearNotification()
            return
        }

        val position = currentPositionMs ?: service.audioEngine.currentPosition
        val duration = if (song.durationMs > 0) song.durationMs else service.audioEngine.duration

        // 1. Immediately update MediaSession playback state for lock-screen seekbar & controls
        updatePlaybackState(isPlaying, position, duration, isShuffle, repeatMode)

        // 2. Check if artwork and accent color are cached
        val cachedRounded = roundedBitmapCache.get(song.id)
        val cachedColor = accentColorCache.get(song.id)

        if (cachedRounded != null && cachedColor != null) {
            updateMediaMetadata(song, cachedRounded, duration, deviceName)
            postNotification(song, isPlaying, isShuffle, repeatMode, cachedRounded, cachedColor, deviceName)
        } else {
            val fallbackArt = getFallbackArtworkBitmap()
            val fallbackColor = DEFAULT_ACCENT_COLOR
            updateMediaMetadata(song, fallbackArt, duration, deviceName)
            postNotification(song, isPlaying, isShuffle, repeatMode, fallbackArt, fallbackColor, deviceName)

            // Asynchronously decode embedded high-res art & extract dynamic palette
            artworkLoadingJob?.cancel()
            artworkLoadingJob = scope.launch {
                val rawBitmap = AlbumArtHelper.loadAlbumArt(service, song)
                val finalArt = if (rawBitmap != null) {
                    createRoundedBitmap(rawBitmap, cornerRadiusPx = 32f).also {
                        roundedBitmapCache.put(song.id, it)
                    }
                } else {
                    fallbackArt
                }

                val accentColor = if (rawBitmap != null) {
                    extractAdaptiveAccentColor(rawBitmap).also {
                        accentColorCache.put(song.id, it)
                    }
                } else {
                    DEFAULT_ACCENT_COLOR
                }

                withContext(Dispatchers.Main) {
                    if (currentSong?.id == song.id) {
                        updateMediaMetadata(song, finalArt, duration, deviceName)
                        postNotification(song, isPlayingState, isShuffleState, repeatModeState, finalArt, accentColor, deviceName)
                    }
                }
            }
        }
    }

    /**
     * Updates MediaSessionCompat's PlaybackState with exact position, speed, and actions.
     */
    fun updatePlaybackState(
        isPlaying: Boolean,
        positionMs: Long,
        durationMs: Long,
        isShuffle: Boolean,
        repeatMode: RepeatMode
    ) {
        val session = mediaSession ?: return

        val state = if (isPlaying) PlaybackStateCompat.STATE_PLAYING else PlaybackStateCompat.STATE_PAUSED
        val actions = (
            PlaybackStateCompat.ACTION_PLAY or
            PlaybackStateCompat.ACTION_PAUSE or
            PlaybackStateCompat.ACTION_PLAY_PAUSE or
            PlaybackStateCompat.ACTION_SKIP_TO_NEXT or
            PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS or
            PlaybackStateCompat.ACTION_SEEK_TO or
            PlaybackStateCompat.ACTION_SET_SHUFFLE_MODE or
            PlaybackStateCompat.ACTION_SET_REPEAT_MODE or
            PlaybackStateCompat.ACTION_STOP
        )

        val shuffleCustomAction = PlaybackStateCompat.CustomAction.Builder(
            ACTION_SHUFFLE,
            if (isShuffle) "Shuffle On" else "Shuffle Off",
            if (isShuffle) R.drawable.ic_notif_shuffle_on else R.drawable.ic_notif_shuffle_off
        ).build()

        val repeatCustomAction = PlaybackStateCompat.CustomAction.Builder(
            ACTION_REPEAT,
            when (repeatMode) {
                RepeatMode.ONE -> "Repeat Track"
                RepeatMode.ALL -> "Repeat All"
                RepeatMode.OFF -> "Repeat Off"
            },
            when (repeatMode) {
                RepeatMode.ONE -> R.drawable.ic_notif_repeat_one
                RepeatMode.ALL -> R.drawable.ic_notif_repeat_all
                RepeatMode.OFF -> R.drawable.ic_notif_repeat_off
            }
        ).build()

        val speed = if (isPlaying) service.audioEngine.playbackSpeed else 0f
        val stateBuilder = PlaybackStateCompat.Builder()
            .setActions(actions)
            .setState(state, positionMs.coerceAtLeast(0L), speed, SystemClock.elapsedRealtime())
            .addCustomAction(shuffleCustomAction)
            .addCustomAction(repeatCustomAction)

        session.setPlaybackState(stateBuilder.build())
    }

    private fun updateMediaMetadata(
        song: Song,
        artworkBitmap: Bitmap?,
        durationMs: Long,
        deviceName: String
    ) {
        val session = mediaSession ?: return
        val hifiMetadata = buildHifiSubtext(song, deviceName)

        val metadataBuilder = MediaMetadataCompat.Builder()
            .putString(MediaMetadataCompat.METADATA_KEY_TITLE, song.title)
            .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, song.artist)
            .putString(MediaMetadataCompat.METADATA_KEY_ALBUM, song.album)
            .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, durationMs.coerceAtLeast(0L))
            .putString(MediaMetadataCompat.METADATA_KEY_DISPLAY_TITLE, song.title)
            .putString(MediaMetadataCompat.METADATA_KEY_DISPLAY_SUBTITLE, "${song.artist} • ${song.album}")
            .putString(MediaMetadataCompat.METADATA_KEY_DISPLAY_DESCRIPTION, hifiMetadata)

        if (artworkBitmap != null) {
            metadataBuilder.putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, artworkBitmap)
            metadataBuilder.putBitmap(MediaMetadataCompat.METADATA_KEY_ART, artworkBitmap)
            metadataBuilder.putBitmap(MediaMetadataCompat.METADATA_KEY_DISPLAY_ICON, artworkBitmap)
        }

        session.setMetadata(metadataBuilder.build())
    }

    private fun buildNotification(
        song: Song,
        isPlaying: Boolean,
        isShuffle: Boolean,
        repeatMode: RepeatMode,
        artworkBitmap: Bitmap,
        accentColor: Int,
        deviceName: String
    ): Notification {
        val openAppIntent = Intent(service, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val contentPendingIntent = PendingIntent.getActivity(
            service, 0, openAppIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val dismissPendingIntent = PendingIntent.getService(
            service, 5, Intent(service, SonoraMusicService::class.java).apply { action = ACTION_STOP },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val shufflePendingIntent = PendingIntent.getService(
            service, 10, Intent(service, SonoraMusicService::class.java).apply { action = ACTION_SHUFFLE },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val prevPendingIntent = PendingIntent.getService(
            service, 11, Intent(service, SonoraMusicService::class.java).apply { action = ACTION_PREV },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val playPauseAction = if (isPlaying) ACTION_PAUSE else ACTION_PLAY
        val playPausePendingIntent = PendingIntent.getService(
            service, 12, Intent(service, SonoraMusicService::class.java).apply { action = playPauseAction },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val nextPendingIntent = PendingIntent.getService(
            service, 13, Intent(service, SonoraMusicService::class.java).apply { action = ACTION_NEXT },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val repeatPendingIntent = PendingIntent.getService(
            service, 14, Intent(service, SonoraMusicService::class.java).apply { action = ACTION_REPEAT },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val shuffleIcon = if (isShuffle) R.drawable.ic_notif_shuffle_on else R.drawable.ic_notif_shuffle_off
        val shuffleTitle = if (isShuffle) "Shuffle On" else "Shuffle Off"

        val playPauseIcon = if (isPlaying) R.drawable.ic_notif_pause else R.drawable.ic_notif_play
        val playPauseTitle = if (isPlaying) "Pause" else "Play"

        val repeatIcon = when (repeatMode) {
            RepeatMode.ONE -> R.drawable.ic_notif_repeat_one
            RepeatMode.ALL -> R.drawable.ic_notif_repeat_all
            RepeatMode.OFF -> R.drawable.ic_notif_repeat_off
        }
        val repeatTitle = when (repeatMode) {
            RepeatMode.ONE -> "Repeat: Track"
            RepeatMode.ALL -> "Repeat: All"
            RepeatMode.OFF -> "Repeat: Off"
        }

        val mediaStyle = MediaStyle()
        mediaSession?.let {
            mediaStyle.setMediaSession(it.sessionToken)
        }
        // Compact view: Prev (1), Play/Pause (2), Next (3)
        // Expanded view: Shuffle (0), Prev (1), Play/Pause (2), Next (3), Repeat (4)
        mediaStyle.setShowActionsInCompactView(1, 2, 3)
        mediaStyle.setShowCancelButton(true)
        mediaStyle.setCancelButtonIntent(dismissPendingIntent)

        val lockStyle = prefs.lockScreenStyle
        val hifiSubtext = if (lockStyle == com.example.model.LockScreenStyle.MINIMAL_PRIVATE) {
            null
        } else {
            buildHifiSubtext(song, deviceName)
        }
        val contentLine = if (lockStyle == com.example.model.LockScreenStyle.MINIMAL_PRIVATE) {
            song.artist
        } else {
            "${song.artist} • ${song.album}"
        }

        val builder = NotificationCompat.Builder(service, SonoraApplication.PLAYBACK_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notif_sonora_logo)
            .setContentTitle(song.title)
            .setContentText(contentLine)
            .apply { if (hifiSubtext != null) setSubText(hifiSubtext) }
            .setLargeIcon(artworkBitmap)
            .setColor(accentColor)
            .setColorized(lockStyle == com.example.model.LockScreenStyle.MEDIA_COLORED)
            .setVisibility(
                if (prefs.showLockScreenControls) NotificationCompat.VISIBILITY_PUBLIC
                else NotificationCompat.VISIBILITY_SECRET
            )
            .setOngoing(isPlaying)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setContentIntent(contentPendingIntent)
            .setDeleteIntent(dismissPendingIntent)
            .setStyle(mediaStyle)
            .setPriority(NotificationCompat.PRIORITY_LOW)

        // Add 5 Actions in order:
        builder.addAction(shuffleIcon, shuffleTitle, shufflePendingIntent)
        builder.addAction(R.drawable.ic_notif_prev, "Previous", prevPendingIntent)
        builder.addAction(playPauseIcon, playPauseTitle, playPausePendingIntent)
        builder.addAction(R.drawable.ic_notif_next, "Next", nextPendingIntent)
        builder.addAction(repeatIcon, repeatTitle, repeatPendingIntent)

        return builder.build()
    }

    private fun postNotification(
        song: Song,
        isPlaying: Boolean,
        isShuffle: Boolean,
        repeatMode: RepeatMode,
        artworkBitmap: Bitmap,
        accentColor: Int,
        deviceName: String
    ) {
        val notification = buildNotification(
            song = song,
            isPlaying = isPlaying,
            isShuffle = isShuffle,
            repeatMode = repeatMode,
            artworkBitmap = artworkBitmap,
            accentColor = accentColor,
            deviceName = deviceName
        )

        val hasNotificationPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                service,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }

        if (!isForegroundActive) {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    ServiceCompat.startForeground(
                        service,
                        NOTIFICATION_ID,
                        notification,
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
                    )
                } else {
                    service.startForeground(NOTIFICATION_ID, notification)
                }
                isForegroundActive = true
            } catch (e: Throwable) {
                Log.w("SonoraNotifManager", "startForeground failed: ${e.message}")
                if (hasNotificationPermission) {
                    try {
                        NotificationManagerCompat.from(service).notify(NOTIFICATION_ID, notification)
                    } catch (_: Throwable) {}
                }
            }
        } else if (hasNotificationPermission) {
            try {
                NotificationManagerCompat.from(service).notify(NOTIFICATION_ID, notification)
            } catch (_: Throwable) {
            }
        }
    }

    fun clearNotification() {
        artworkLoadingJob?.cancel()
        currentSong = null
        if (isForegroundActive) {
            try {
                ServiceCompat.stopForeground(service, ServiceCompat.STOP_FOREGROUND_REMOVE)
            } catch (e: Throwable) {
                Log.w("SonoraNotifManager", "stopForeground failed: ${e.message}")
            }
            isForegroundActive = false
        } else {
            try {
                NotificationManagerCompat.from(service).cancel(NOTIFICATION_ID)
            } catch (_: Throwable) {}
        }
    }

    private fun buildHifiSubtext(song: Song, deviceName: String): String {
        if (!prefs.showNotificationFormatBadge) {
            return if (deviceName.isNotBlank() && !deviceName.equals("Phone Speaker", ignoreCase = true)) {
                "SONORA • $deviceName"
            } else {
                "SONORA"
            }
        }
        val formatInfo = song.audioFormatInfo
        val devTag = if (deviceName.isNotBlank() && !deviceName.equals("Phone Speaker", ignoreCase = true)) {
            " • $deviceName"
        } else {
            ""
        }
        return "SONORA • $formatInfo$devTag"
    }

    private suspend fun extractAdaptiveAccentColor(bitmap: Bitmap): Int = withContext(Dispatchers.Default) {
        try {
            val palette = Palette.from(bitmap).generate()
            val swatch = palette.vibrantSwatch
                ?: palette.dominantSwatch
                ?: palette.darkVibrantSwatch
                ?: palette.lightVibrantSwatch
                ?: palette.mutedSwatch

            if (swatch != null) {
                val rgb = swatch.rgb
                val hsl = FloatArray(3)
                ColorUtils.colorToHSL(rgb, hsl)
                hsl[1] = hsl[1].coerceIn(0.4f, 0.9f)
                hsl[2] = hsl[2].coerceIn(0.35f, 0.65f)
                ColorUtils.HSLToColor(hsl)
            } else {
                DEFAULT_ACCENT_COLOR
            }
        } catch (e: Throwable) {
            DEFAULT_ACCENT_COLOR
        }
    }

    private fun createRoundedBitmap(source: Bitmap, cornerRadiusPx: Float): Bitmap {
        val size = 512
        val output = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        if (source.isRecycled || source.width <= 0 || source.height <= 0) {
            canvas.drawColor(0xFF18112C.toInt())
            return output
        }
        val scaled = Bitmap.createScaledBitmap(source, size, size, true)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val rect = Rect(0, 0, size, size)
        val rectF = RectF(rect)

        canvas.drawRoundRect(rectF, cornerRadiusPx, cornerRadiusPx, paint)
        paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
        canvas.drawBitmap(scaled, rect, rect, paint)
        return output
    }

    private val cachedFallbackArtwork: Bitmap by lazy {
        val size = 512
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val drawable = ContextCompat.getDrawable(service, R.drawable.ic_default_album_art)
        if (drawable != null) {
            drawable.setBounds(0, 0, size, size)
            drawable.draw(canvas)
        } else {
            canvas.drawColor(0xFF18112C.toInt())
        }
        createRoundedBitmap(bitmap, 32f)
    }

    private fun getFallbackArtworkBitmap(): Bitmap = cachedFallbackArtwork

    fun release() {
        artworkLoadingJob?.cancel()
        mediaSession?.apply {
            isActive = false
            release()
        }
        mediaSession = null
        roundedBitmapCache.evictAll()
        accentColorCache.evictAll()
    }
}
