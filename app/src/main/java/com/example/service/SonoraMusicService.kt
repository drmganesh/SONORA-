package com.example.service

import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.support.v4.media.session.MediaSessionCompat
import android.util.Log
import androidx.core.content.ContextCompat
import com.example.audio.SonoraAudioEngine
import com.example.model.RepeatMode
import com.example.model.Song
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job

class SonoraMusicService : Service(), AudioManager.OnAudioFocusChangeListener {

    companion object {
        private const val TAG = "SonoraMusicService"
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
        get() = if (::notificationManager.isInitialized) notificationManager.mediaSession else null

    private val serviceJob = Job()
    private val serviceScope = CoroutineScope(Dispatchers.Main + serviceJob)

    private var audioManager: AudioManager? = null
    private var isNoisyReceiverRegistered = false
    private var focusRequestObj: Any? = null
    private var hasAudioFocus = false
    private var resumeOnAudioFocusGain = false
    @Volatile
    private var audioFocusEnabled = true
    @Volatile
    private var headsetAutoPlayPause = true
    @Volatile
    var currentSongSnapshot: Song? = null
        private set
    @Volatile
    var currentQueueSnapshot: List<Song> = emptyList()
        private set
    @Volatile
    var currentQueueIndexSnapshot: Int = 0
        private set
    @Volatile
    var currentIsShuffleSnapshot: Boolean = false
        private set
    @Volatile
    var currentRepeatModeSnapshot: RepeatMode = RepeatMode.OFF
        private set
    @Volatile
    var currentDeviceNameSnapshot: String = ""
        private set
    var playbackCallback: ServicePlaybackCallback? = null
        private set

    // Headphone unplug listener
    private val noisyReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == AudioManager.ACTION_AUDIO_BECOMING_NOISY && headsetAutoPlayPause) {
                resumeOnAudioFocusGain = false
                handleRemotePause()
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

    fun updateQueueSnapshot(queue: List<Song>, index: Int) {
        currentQueueSnapshot = queue
        currentQueueIndexSnapshot = if (queue.isNotEmpty()) index.coerceIn(0, queue.lastIndex) else 0
    }

    fun updateAudioPolicySettings(focusEnabled: Boolean, headsetPauseEnabled: Boolean) {
        audioFocusEnabled = focusEnabled
        headsetAutoPlayPause = headsetPauseEnabled
        if (!focusEnabled) {
            abandonAudioFocus()
        }
    }

    fun requestAudioFocusIfNeeded(): Boolean {
        if (!audioFocusEnabled) return true
        val am = audioManager ?: return true
        if (hasAudioFocus) {
            if (::audioEngine.isInitialized) audioEngine.setFocusDuckScale(1.0f)
            return true
        }
        return try {
            val result = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val attrs = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
                val req = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                    .setAudioAttributes(attrs)
                    .setAcceptsDelayedFocusGain(true)
                    .setOnAudioFocusChangeListener(this)
                    .build()
                focusRequestObj = req
                am.requestAudioFocus(req)
            } else {
                @Suppress("DEPRECATION")
                am.requestAudioFocus(
                    this,
                    AudioManager.STREAM_MUSIC,
                    AudioManager.AUDIOFOCUS_GAIN
                )
            }
            hasAudioFocus = (result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED)
            if (hasAudioFocus && ::audioEngine.isInitialized) {
                audioEngine.setFocusDuckScale(1.0f)
            }
            result != AudioManager.AUDIOFOCUS_REQUEST_FAILED
        } catch (e: Throwable) {
            Log.w(TAG, "Audio focus request failed: ${e.message}")
            true
        }
    }

    fun abandonAudioFocus() {
        resumeOnAudioFocusGain = false
        hasAudioFocus = false
        if (::audioEngine.isInitialized) {
            audioEngine.setFocusDuckScale(1.0f)
        }
        val am = audioManager ?: return
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                (focusRequestObj as? AudioFocusRequest)?.let { req ->
                    am.abandonAudioFocusRequest(req)
                }
                focusRequestObj = null
            } else {
                @Suppress("DEPRECATION")
                am.abandonAudioFocus(this)
            }
        } catch (e: Throwable) {
            Log.w(TAG, "Audio focus abandon failed: ${e.message}")
        }
    }

    override fun onCreate() {
        super.onCreate()
        audioEngine = SonoraAudioEngine(applicationContext)
        audioManager = getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        notificationManager = SonoraNotificationManager(this, serviceScope)

        val filter = IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY)
        try {
            ContextCompat.registerReceiver(
                this,
                noisyReceiver,
                filter,
                ContextCompat.RECEIVER_NOT_EXPORTED
            )
            isNoisyReceiverRegistered = true
        } catch (e: Exception) {
            Log.e(TAG, "Failed registering noisy audio receiver", e)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Satisfy Android 8.0+ foreground service promotion synchronously before handling actions or stopping
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !notificationManager.isForegroundNotificationActive) {
            val likelyPlaying = (::audioEngine.isInitialized && audioEngine.isPlaying) || intent?.action == ACTION_PLAY
            notificationManager.ensureForegroundForServiceStart(currentSongSnapshot, likelyPlaying)
        }

        when (intent?.action) {
            ACTION_PLAY -> handleRemotePlay()
            ACTION_PAUSE -> handleRemotePause()
            ACTION_NEXT -> handleRemoteNext()
            ACTION_PREV -> handleRemotePrev()
            ACTION_SHUFFLE -> handleRemoteShuffle()
            ACTION_REPEAT -> handleRemoteRepeat()
            ACTION_SEEK -> {
                val pos = intent.getLongExtra(EXTRA_SEEK_POSITION, -1L)
                if (pos >= 0) handleRemoteSeek(pos)
            }
            ACTION_STOP -> {
                handleRemotePause()
                notificationManager.clearNotification()
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    fun handleRemotePlay() {
        playbackCallback?.let {
            it.onRemotePlay()
            return
        }
        if (!::audioEngine.isInitialized) return
        if (audioEngine.isPlaying) return
        val song = currentSongSnapshot ?: return
        if (!requestAudioFocusIfNeeded()) return
        if (audioEngine.isPrepared) {
            audioEngine.play()
            updateForegroundNotification(
                song = song,
                isPlaying = true,
                isShuffle = currentIsShuffleSnapshot,
                repeatMode = currentRepeatModeSnapshot,
                deviceName = currentDeviceNameSnapshot
            )
        } else {
            playSongAutonomously(song)
        }
    }

    fun handleRemotePause() {
        playbackCallback?.let {
            it.onRemotePause()
            return
        }
        if (::audioEngine.isInitialized && audioEngine.isPlaying) {
            audioEngine.pause()
        }
        abandonAudioFocus()
        updateForegroundNotification(
            song = currentSongSnapshot,
            isPlaying = false,
            isShuffle = currentIsShuffleSnapshot,
            repeatMode = currentRepeatModeSnapshot,
            deviceName = currentDeviceNameSnapshot
        )
    }

    fun handleRemoteNext() {
        playbackCallback?.let {
            it.onRemoteNext()
            return
        }
        val queue = currentQueueSnapshot
        if (queue.isEmpty() || !::audioEngine.isInitialized) return
        var nextIdx = currentQueueIndexSnapshot + 1
        if (nextIdx >= queue.size) {
            if (currentRepeatModeSnapshot == RepeatMode.ALL) {
                nextIdx = 0
            } else {
                return
            }
        }
        currentQueueIndexSnapshot = nextIdx
        playSongAutonomously(queue[nextIdx])
    }

    fun handleRemotePrev() {
        playbackCallback?.let {
            it.onRemotePrev()
            return
        }
        if (!::audioEngine.isInitialized) return
        if (audioEngine.currentPosition > 3000L) {
            handleRemoteSeek(0L)
            return
        }
        val queue = currentQueueSnapshot
        if (queue.isEmpty()) return
        var prevIdx = currentQueueIndexSnapshot - 1
        if (prevIdx < 0) {
            prevIdx = if (currentRepeatModeSnapshot == RepeatMode.ALL) queue.size - 1 else 0
        }
        currentQueueIndexSnapshot = prevIdx
        playSongAutonomously(queue[prevIdx])
    }

    fun handleRemoteShuffle() {
        playbackCallback?.let {
            it.onRemoteShuffle()
            return
        }
        currentIsShuffleSnapshot = !currentIsShuffleSnapshot
        updateForegroundNotification(
            song = currentSongSnapshot,
            isPlaying = ::audioEngine.isInitialized && audioEngine.isPlaying,
            isShuffle = currentIsShuffleSnapshot,
            repeatMode = currentRepeatModeSnapshot,
            deviceName = currentDeviceNameSnapshot
        )
    }

    fun handleRemoteRepeat() {
        playbackCallback?.let {
            it.onRemoteRepeat()
            return
        }
        currentRepeatModeSnapshot = when (currentRepeatModeSnapshot) {
            RepeatMode.OFF -> RepeatMode.ALL
            RepeatMode.ALL -> RepeatMode.ONE
            RepeatMode.ONE -> RepeatMode.OFF
        }
        updateForegroundNotification(
            song = currentSongSnapshot,
            isPlaying = ::audioEngine.isInitialized && audioEngine.isPlaying,
            isShuffle = currentIsShuffleSnapshot,
            repeatMode = currentRepeatModeSnapshot,
            deviceName = currentDeviceNameSnapshot
        )
    }

    fun handleRemoteSeek(positionMs: Long) {
        playbackCallback?.let {
            it.onRemoteSeek(positionMs)
            return
        }
        if (!::audioEngine.isInitialized) return
        audioEngine.seekTo(positionMs)
        val song = currentSongSnapshot
        notificationManager.updatePlaybackState(
            isPlaying = audioEngine.isPlaying,
            positionMs = positionMs,
            durationMs = audioEngine.duration.coerceAtLeast(song?.durationMs ?: 0L),
            isShuffle = currentIsShuffleSnapshot,
            repeatMode = currentRepeatModeSnapshot
        )
    }

    private fun playSongAutonomously(song: Song) {
        if (!::audioEngine.isInitialized) return
        if (!requestAudioFocusIfNeeded()) return
        currentSongSnapshot = song
        updateForegroundNotification(
            song = song,
            isPlaying = true,
            isShuffle = currentIsShuffleSnapshot,
            repeatMode = currentRepeatModeSnapshot,
            deviceName = currentDeviceNameSnapshot
        )
        audioEngine.prepareAndPlay(
            uri = song.uri,
            fallbackPath = song.dataPath,
            onPrepared = {
                updateForegroundNotification(
                    song = song,
                    isPlaying = true,
                    isShuffle = currentIsShuffleSnapshot,
                    repeatMode = currentRepeatModeSnapshot,
                    deviceName = currentDeviceNameSnapshot
                )
            },
            onCompletion = {
                when (currentRepeatModeSnapshot) {
                    RepeatMode.ONE -> playSongAutonomously(song)
                    RepeatMode.ALL -> handleRemoteNext()
                    RepeatMode.OFF -> {
                        if (currentQueueIndexSnapshot < currentQueueSnapshot.lastIndex) {
                            handleRemoteNext()
                        } else {
                            updateForegroundNotification(
                                song = song,
                                isPlaying = false,
                                isShuffle = currentIsShuffleSnapshot,
                                repeatMode = currentRepeatModeSnapshot,
                                deviceName = currentDeviceNameSnapshot
                            )
                        }
                    }
                }
            },
            onError = {
                updateForegroundNotification(
                    song = song,
                    isPlaying = false,
                    isShuffle = currentIsShuffleSnapshot,
                    repeatMode = currentRepeatModeSnapshot,
                    deviceName = currentDeviceNameSnapshot
                )
            }
        )
    }

    fun updateForegroundNotification(
        song: Song?,
        isPlaying: Boolean,
        isShuffle: Boolean = false,
        repeatMode: RepeatMode = RepeatMode.OFF,
        deviceName: String = ""
    ) {
        currentSongSnapshot = song
        currentIsShuffleSnapshot = isShuffle
        currentRepeatModeSnapshot = repeatMode
        currentDeviceNameSnapshot = deviceName
        notificationManager.updateNotification(
            song = song,
            isPlaying = isPlaying,
            isShuffle = isShuffle,
            repeatMode = repeatMode,
            deviceName = deviceName
        )
    }

    override fun onAudioFocusChange(focusChange: Int) {
        if (!audioFocusEnabled) return
        when (focusChange) {
            AudioManager.AUDIOFOCUS_LOSS -> {
                hasAudioFocus = false
                resumeOnAudioFocusGain = false
                if (::audioEngine.isInitialized) audioEngine.setFocusDuckScale(1.0f)
                handleRemotePause()
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                resumeOnAudioFocusGain = ::audioEngine.isInitialized && audioEngine.isPlaying
                if (::audioEngine.isInitialized) audioEngine.setFocusDuckScale(1.0f)
                handleRemotePause()
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                if (::audioEngine.isInitialized) audioEngine.setFocusDuckScale(0.25f)
            }
            AudioManager.AUDIOFOCUS_GAIN -> {
                hasAudioFocus = true
                if (::audioEngine.isInitialized) audioEngine.setFocusDuckScale(1.0f)
                if (resumeOnAudioFocusGain) {
                    resumeOnAudioFocusGain = false
                    handleRemotePlay()
                }
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onDestroy() {
        super.onDestroy()
        abandonAudioFocus()
        if (isNoisyReceiverRegistered) {
            try {
                unregisterReceiver(noisyReceiver)
            } catch (e: Exception) {
                Log.w(TAG, "Failed unregistering noisy receiver cleanly", e)
            } finally {
                isNoisyReceiverRegistered = false
            }
        }
        if (::notificationManager.isInitialized) {
            notificationManager.release()
        }
        if (::audioEngine.isInitialized) {
            audioEngine.release()
        }
        serviceJob.cancel()
    }
}
