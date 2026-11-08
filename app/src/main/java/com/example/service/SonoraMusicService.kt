package com.example.service

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import android.media.session.MediaSession
import android.os.Binder
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.R
import com.example.SonoraApplication
import com.example.audio.SonoraAudioEngine
import com.example.model.Song

class SonoraMusicService : Service(), AudioManager.OnAudioFocusChangeListener {

    companion object {
        const val ACTION_PLAY = "com.example.sonora.ACTION_PLAY"
        const val ACTION_PAUSE = "com.example.sonora.ACTION_PAUSE"
        const val ACTION_NEXT = "com.example.sonora.ACTION_NEXT"
        const val ACTION_PREV = "com.example.sonora.ACTION_PREV"
        const val ACTION_STOP = "com.example.sonora.ACTION_STOP"
        const val NOTIFICATION_ID = 2026
    }

    inner class SonoraBinder : Binder() {
        val service: SonoraMusicService
            get() = this@SonoraMusicService
    }

    private val binder = SonoraBinder()
    lateinit var audioEngine: SonoraAudioEngine
        private set

    private var mediaSession: MediaSession? = null
    private var audioManager: AudioManager? = null
    private var playbackCallback: ServicePlaybackCallback? = null
    private var currentSong: Song? = null
    private var isServiceRunningInForeground = false

    // Headphone unplug listener
    private val noisyReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == AudioManager.ACTION_AUDIO_BECOMING_NOISY) {
                playbackCallback?.onRemotePause()
            }
        }
    }

    interface ServicePlaybackCallback {
        fun onRemotePlay()
        fun onRemotePause()
        fun onRemoteNext()
        fun onRemotePrev()
    }

    fun setPlaybackCallback(callback: ServicePlaybackCallback?) {
        this.playbackCallback = callback
    }

    override fun onCreate() {
        super.onCreate()
        audioEngine = SonoraAudioEngine(applicationContext)
        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager

        setupMediaSession()

        val filter = IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY)
        registerReceiver(noisyReceiver, filter)
    }

    private fun setupMediaSession() {
        mediaSession = MediaSession(this, "SonoraMediaSession").apply {
            setCallback(object : MediaSession.Callback() {
                override fun onPlay() {
                    playbackCallback?.onRemotePlay()
                }

                override fun onPause() {
                    playbackCallback?.onRemotePause()
                }

                override fun onSkipToNext() {
                    playbackCallback?.onRemoteNext()
                }

                override fun onSkipToPrevious() {
                    playbackCallback?.onRemotePrev()
                }

                override fun onSeekTo(pos: Long) {
                    audioEngine.seekTo(pos)
                }
            })
            isActive = true
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PLAY -> playbackCallback?.onRemotePlay()
            ACTION_PAUSE -> playbackCallback?.onRemotePause()
            ACTION_NEXT -> playbackCallback?.onRemoteNext()
            ACTION_PREV -> playbackCallback?.onRemotePrev()
            ACTION_STOP -> {
                playbackCallback?.onRemotePause()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    fun updateForegroundNotification(song: Song?, isPlaying: Boolean) {
        this.currentSong = song
        if (song == null) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            isServiceRunningInForeground = false
            return
        }

        val notification = buildNotification(song, isPlaying)
        if (!isServiceRunningInForeground) {
            startForeground(NOTIFICATION_ID, notification)
            isServiceRunningInForeground = true
        } else {
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
            notificationManager.notify(NOTIFICATION_ID, notification)
        }
    }

    private fun buildNotification(song: Song, isPlaying: Boolean): Notification {
        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val contentPendingIntent = PendingIntent.getActivity(
            this, 0, openAppIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val prevPendingIntent = PendingIntent.getService(
            this, 1, Intent(this, SonoraMusicService::class.java).apply { action = ACTION_PREV },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val playPauseAction = if (isPlaying) ACTION_PAUSE else ACTION_PLAY
        val playPausePendingIntent = PendingIntent.getService(
            this, 2, Intent(this, SonoraMusicService::class.java).apply { action = playPauseAction },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val nextPendingIntent = PendingIntent.getService(
            this, 3, Intent(this, SonoraMusicService::class.java).apply { action = ACTION_NEXT },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val playPauseIcon = if (isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play

        val builder = NotificationCompat.Builder(this, SonoraApplication.PLAYBACK_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(song.title)
            .setContentText("${song.artist} • ${song.album}")
            .setSubText(song.audioFormatInfo)
            .setContentIntent(contentPendingIntent)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(isPlaying)
            .addAction(android.R.drawable.ic_media_previous, "Previous", prevPendingIntent)
            .addAction(playPauseIcon, if (isPlaying) "Pause" else "Play", playPausePendingIntent)
            .addAction(android.R.drawable.ic_media_next, "Next", nextPendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)

        return builder.build()
    }

    override fun onAudioFocusChange(focusChange: Int) {
        when (focusChange) {
            AudioManager.AUDIOFOCUS_LOSS -> {
                playbackCallback?.onRemotePause()
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                playbackCallback?.onRemotePause()
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                // Duck volume if desired
            }
            AudioManager.AUDIOFOCUS_GAIN -> {
                playbackCallback?.onRemotePlay()
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onDestroy() {
        super.onDestroy()
        try {
            unregisterReceiver(noisyReceiver)
        } catch (e: Exception) {
            // Ignored
        }
        audioEngine.release()
        mediaSession?.release()
        mediaSession = null
    }
}
