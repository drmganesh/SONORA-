package com.example.service

import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import android.os.Binder
import android.os.IBinder
import android.support.v4.media.session.MediaSessionCompat
import com.example.audio.SonoraAudioEngine
import com.example.model.RepeatMode
import com.example.model.Song
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job

class SonoraMusicService : Service(), AudioManager.OnAudioFocusChangeListener {

    companion object {
        const val ACTION_PLAY = SonoraNotificationManager.ACTION_PLAY
        const val ACTION_PAUSE = SonoraNotificationManager.ACTION_PAUSE
        const val ACTION_NEXT = SonoraNotificationManager.ACTION_NEXT
        const val ACTION_PREV = SonoraNotificationManager.ACTION_PREV
        const val ACTION_SHUFFLE = SonoraNotificationManager.ACTION_SHUFFLE
        const val ACTION_REPEAT = SonoraNotificationManager.ACTION_REPEAT
        const val ACTION_STOP = SonoraNotificationManager.ACTION_STOP
        const val ACTION_SEEK = SonoraNotificationManager.ACTION_SEEK
        const val EXTRA_SEEK_POSITION = SonoraNotificationManager.EXTRA_SEEK_POSITION
        const val NOTIFICATION_ID = SonoraNotificationManager.NOTIFICATION_ID
    }

    inner class SonoraBinder : Binder() {
        val service: SonoraMusicService
            get() = this@SonoraMusicService
    }

    private val binder = SonoraBinder()
    lateinit var audioEngine: SonoraAudioEngine
        private set

    lateinit var notificationManager: SonoraNotificationManager
        private set

    val mediaSession: MediaSessionCompat?
        get() = notificationManager.mediaSession

    private val serviceJob = Job()
    private val serviceScope = CoroutineScope(Dispatchers.Main + serviceJob)

    private var audioManager: AudioManager? = null
    var playbackCallback: ServicePlaybackCallback? = null
        private set

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
        fun onRemoteShuffle()
        fun onRemoteRepeat()
        fun onRemoteSeek(positionMs: Long)
    }

    fun setPlaybackCallback(callback: ServicePlaybackCallback?) {
        this.playbackCallback = callback
    }

    override fun onCreate() {
        super.onCreate()
        audioEngine = SonoraAudioEngine(applicationContext)
        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        notificationManager = SonoraNotificationManager(this, serviceScope)

        val filter = IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY)
        registerReceiver(noisyReceiver, filter)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PLAY -> playbackCallback?.onRemotePlay()
            ACTION_PAUSE -> playbackCallback?.onRemotePause()
            ACTION_NEXT -> playbackCallback?.onRemoteNext()
            ACTION_PREV -> playbackCallback?.onRemotePrev()
            ACTION_SHUFFLE -> playbackCallback?.onRemoteShuffle()
            ACTION_REPEAT -> playbackCallback?.onRemoteRepeat()
            ACTION_SEEK -> {
                val pos = intent.getLongExtra(EXTRA_SEEK_POSITION, -1L)
                if (pos >= 0) playbackCallback?.onRemoteSeek(pos)
            }
            ACTION_STOP -> {
                playbackCallback?.onRemotePause()
                notificationManager.clearNotification()
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    fun updateForegroundNotification(
        song: Song?,
        isPlaying: Boolean,
        isShuffle: Boolean = false,
        repeatMode: RepeatMode = RepeatMode.OFF,
        deviceName: String = ""
    ) {
        notificationManager.updateNotification(
            song = song,
            isPlaying = isPlaying,
            isShuffle = isShuffle,
            repeatMode = repeatMode,
            deviceName = deviceName
        )
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
        notificationManager.release()
        audioEngine.release()
        serviceJob.cancel()
    }
}
