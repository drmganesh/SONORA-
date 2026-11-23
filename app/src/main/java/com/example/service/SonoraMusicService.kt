package com.example.service

import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioAttributes
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Binder
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.support.v4.media.session.MediaSessionCompat
import android.util.Log
import androidx.core.content.ContextCompat
import com.example.audio.SonoraAudioEngine
import com.example.data.SonoraDatabase
import com.example.model.RepeatMode
import com.example.model.Song
import data.analytics.PlaybackSessionSnapshotEntity
import data.preferences.PlaybackLifecyclePreferences
import domain.player.PlaybackAudioFocusController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch

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

    val focusController: PlaybackAudioFocusController = PlaybackAudioFocusController()

    private val serviceJob = Job()
    private val serviceScope = CoroutineScope(Dispatchers.Main + serviceJob)
    private var autonomousController: playback.UnifiedPlaybackController? = null
    private var connectivityMonitor: data.online.NetworkConnectivityMonitor? = null
    private var lifecyclePrefs: PlaybackLifecyclePreferences? = null

    private var audioManager: AudioManager? = null
    private var audioDeviceCallback: AudioDeviceCallback? = null
    private var isNoisyReceiverRegistered = false
    private var focusRequestObj: Any? = null
    private var hasAudioFocus = false
    @Volatile
    private var audioFocusEnabled = true
    @Volatile
    private var headsetAutoPlayPause = true
    @Volatile
    var backgroundPlaybackEnabled: Boolean = true
        private set
    @Volatile
    var resumeAfterPhoneCallEnabled: Boolean = true
        private set
    @Volatile
    var stopPlaybackOnTaskRemovedEnabled: Boolean = true
        private set

    @Volatile
    var lastSavedTransientPositionMs: Long = 0L
        private set

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

    // Headphone unplug / Bluetooth becoming noisy listener
    private val noisyReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == AudioManager.ACTION_AUDIO_BECOMING_NOISY) {
                val action = focusController.onAudioBecomingNoisy(headsetAutoPlayPause)
                if (action is PlaybackAudioFocusController.Action.PauseForBecomingNoisy) {
                    handleRemotePause(userInitiated = true)
                }
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
        fun onRemoteStop() {}
        fun onTransientFocusPause(positionMs: Long) {}
        fun onTaskRemovedStop(positionMs: Long, wasPlaying: Boolean) {}
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

    fun updateLifecycleSettings(
        backgroundPlayback: Boolean,
        resumeAfterPhoneCall: Boolean,
        stopOnTaskRemoved: Boolean
    ) {
        backgroundPlaybackEnabled = backgroundPlayback
        resumeAfterPhoneCallEnabled = resumeAfterPhoneCall
        stopPlaybackOnTaskRemovedEnabled = stopOnTaskRemoved
    }

    fun notifyUserStartedPlayback() {
        focusController.onUserStartedOrResumedPlayback()
    }

    fun notifyUserManualPause() {
        focusController.onUserManualPause()
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

        lifecyclePrefs = PlaybackLifecyclePreferences(applicationContext).also { prefs ->
            serviceScope.launch {
                prefs.backgroundSettingsFlow
                    .catch { e -> Log.w(TAG, "Background settings stream error: ${e.message}") }
                    .collect { settings ->
                        backgroundPlaybackEnabled = settings.backgroundPlaybackEnabled
                        resumeAfterPhoneCallEnabled = settings.resumeAfterPhoneCall
                    }
            }
            serviceScope.launch {
                prefs.sessionSettingsFlow
                    .catch { e -> Log.w(TAG, "Session settings stream error: ${e.message}") }
                    .collect { settings ->
                        stopPlaybackOnTaskRemovedEnabled = settings.stopPlaybackOnTaskRemoved
                    }
            }
        }

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

        registerBluetoothAndHeadsetRouteCallback()
    }

    private fun registerBluetoothAndHeadsetRouteCallback() {
        val am = audioManager ?: return
        val callback = object : AudioDeviceCallback() {
            override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>?) {
                if (!headsetAutoPlayPause || removedDevices.isNullOrEmpty()) return
                val disconnectedExternalOutput = removedDevices.any { dev ->
                    dev.isSink && (
                        dev.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP ||
                            dev.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO ||
                            dev.type == AudioDeviceInfo.TYPE_BLE_HEADSET ||
                            dev.type == AudioDeviceInfo.TYPE_WIRED_HEADSET ||
                            dev.type == AudioDeviceInfo.TYPE_WIRED_HEADPHONES ||
                            dev.type == AudioDeviceInfo.TYPE_USB_HEADSET
                        )
                }
                if (disconnectedExternalOutput && ::audioEngine.isInitialized && audioEngine.isPlaying) {
                    val action = focusController.onAudioBecomingNoisy(headsetAutoPlayPause)
                    if (action is PlaybackAudioFocusController.Action.PauseForBecomingNoisy) {
                        handleRemotePause(userInitiated = true)
                    }
                }
            }
        }
        try {
            am.registerAudioDeviceCallback(callback, Handler(Looper.getMainLooper()))
            audioDeviceCallback = callback
        } catch (e: Throwable) {
            Log.w(TAG, "AudioDeviceCallback registration skipped: ${e.message}")
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
            ACTION_PAUSE -> handleRemotePause(userInitiated = true)
            ACTION_NEXT -> handleRemoteNext()
            ACTION_PREV -> handleRemotePrev()
            ACTION_SHUFFLE -> handleRemoteShuffle()
            ACTION_REPEAT -> handleRemoteRepeat()
            ACTION_SEEK -> {
                val pos = intent.getLongExtra(EXTRA_SEEK_POSITION, -1L)
                if (pos >= 0) handleRemoteSeek(pos)
            }
            ACTION_STOP -> handleExplicitStop()
        }
        return START_NOT_STICKY
    }

    fun handleRemotePlay() {
        focusController.onUserStartedOrResumedPlayback()
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

    fun handleRemotePause(userInitiated: Boolean = true) {
        if (userInitiated) {
            focusController.onUserManualPause()
        }
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

    /**
     * Pauses playback during an incoming/outgoing phone call or temporary audio focus loss
     * WITHOUT abandoning the AudioFocusRequest, so Android delivers AUDIOFOCUS_GAIN when the call ends.
     */
    private fun pauseForTransientFocusLoss() {
        val exactPos = if (::audioEngine.isInitialized) {
            audioEngine.setFocusDuckScale(1.0f)
            val pos = audioEngine.currentPosition
            if (audioEngine.isPlaying) {
                audioEngine.pause()
            }
            pos
        } else {
            lastSavedTransientPositionMs
        }
        lastSavedTransientPositionMs = exactPos
        hasAudioFocus = false

        val cb = playbackCallback
        if (cb != null) {
            cb.onTransientFocusPause(exactPos)
        } else {
            persistFallbackSessionSnapshot(
                positionMs = exactPos,
                wasPlaying = true,
                explicitlyStopped = false
            )
            updateForegroundNotification(
                song = currentSongSnapshot,
                isPlaying = false,
                isShuffle = currentIsShuffleSnapshot,
                repeatMode = currentRepeatModeSnapshot,
                deviceName = currentDeviceNameSnapshot
            )
        }
    }

    /**
     * Explicit Stop Playback action (from notification Stop button, MediaSession.onStop(), or Now Playing menu).
     * Stops playback, marks session explicitly stopped, clears notification & MediaSession, preserves queue,
     * and stops the foreground service cleanly.
     */
    fun handleExplicitStop() {
        focusController.onUserExplicitStop()
        val exactPos = if (::audioEngine.isInitialized) {
            val pos = audioEngine.currentPosition
            if (audioEngine.isPlaying) {
                audioEngine.pause()
            }
            pos
        } else {
            0L
        }
        abandonAudioFocus()
        val cb = playbackCallback
        if (cb != null) {
            cb.onRemoteStop()
        } else {
            persistFallbackSessionSnapshot(
                positionMs = exactPos,
                wasPlaying = false,
                explicitlyStopped = true
            )
        }
        if (::notificationManager.isInitialized) {
            notificationManager.clearNotification(deactivateSession = true)
        }
        stopSelf()
    }

    /**
     * Triggered by Android ONLY when the user explicitly removes SONORA from Recents / App Switcher.
     * Never triggered by pressing Home, locking the screen, or switching to another app.
     */
    override fun onTaskRemoved(rootIntent: Intent?) {
        val wasPlaying = ::audioEngine.isInitialized && audioEngine.isPlaying
        val exactPos = if (::audioEngine.isInitialized) audioEngine.currentPosition else 0L

        if (!stopPlaybackOnTaskRemovedEnabled) {
            // Persist current position safely while letting background music continue if configured off.
            persistFallbackSessionSnapshot(
                positionMs = exactPos,
                wasPlaying = wasPlaying,
                explicitlyStopped = false
            )
            super.onTaskRemoved(rootIntent)
            return
        }

        // 1. Save current playback session & queue pointer BEFORE stopping playback.
        val cb = playbackCallback
        if (cb != null) {
            cb.onTaskRemovedStop(exactPos, wasPlaying)
        } else {
            persistFallbackSessionSnapshot(
                positionMs = exactPos,
                wasPlaying = wasPlaying,
                explicitlyStopped = false
            )
        }

        // 2. Stop playback, abandon audio focus, clear notification + MediaSession, and stop service.
        focusController.onUserExplicitStop()
        if (::audioEngine.isInitialized) {
            if (audioEngine.isPlaying) {
                audioEngine.pause()
            }
            audioEngine.stopPlayback()
        }
        abandonAudioFocus()
        if (::notificationManager.isInitialized) {
            notificationManager.clearNotification(deactivateSession = true)
        }
        stopSelf()
        super.onTaskRemoved(rootIntent)
    }

    private fun persistFallbackSessionSnapshot(
        positionMs: Long,
        wasPlaying: Boolean,
        explicitlyStopped: Boolean
    ) {
        val song = currentSongSnapshot ?: return
        val speed = if (::audioEngine.isInitialized) audioEngine.playbackSpeed else 1.0f
        val idx = currentQueueIndexSnapshot
        val repeatName = currentRepeatModeSnapshot.name
        val shuffle = currentIsShuffleSnapshot
        serviceScope.launch(Dispatchers.IO) {
            runCatching {
                val dao = SonoraDatabase.getDatabase(applicationContext).dao()
                val existing = dao.getPlaybackSessionSnapshot()
                dao.upsertPlaybackSessionSnapshot(
                    PlaybackSessionSnapshotEntity(
                        id = 1,
                        lastTrackId = song.id,
                        isOnline = song.isOnline,
                        positionMs = positionMs.coerceAtLeast(0L),
                        queueIndex = idx,
                        repeatMode = repeatName,
                        isShuffle = shuffle,
                        playbackSpeed = speed,
                        volume = existing?.volume ?: 1.0f,
                        contextType = existing?.contextType ?: "QUEUE",
                        contextLabel = existing?.contextLabel ?: "",
                        lastPlayerStyle = existing?.lastPlayerStyle ?: "",
                        wasPlayingWhenSaved = wasPlaying,
                        explicitlyStopped = explicitlyStopped,
                        savedAtMs = System.currentTimeMillis()
                    )
                )
            }.onFailure { e ->
                Log.w(TAG, "Fallback session snapshot persistence skipped: ${e.message}")
            }
        }
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

    private fun ensureAutonomousController(): playback.UnifiedPlaybackController {
        autonomousController?.let { return it }
        val monitor = connectivityMonitor ?: data.online.NetworkConnectivityMonitor(applicationContext).also {
            connectivityMonitor = it
        }
        val dao = SonoraDatabase.getDatabase(applicationContext).dao()
        val repo = data.online.OnlineMusicRepository(dao = dao, connectivityMonitor = monitor)
        return playback.UnifiedPlaybackController(audioEngine, repo).also {
            autonomousController = it
        }
    }

    private fun playSongAutonomously(song: Song) {
        if (!::audioEngine.isInitialized) return
        if (!requestAudioFocusIfNeeded()) return
        focusController.onUserStartedOrResumedPlayback()
        currentSongSnapshot = song
        updateForegroundNotification(
            song = song,
            isPlaying = true,
            isShuffle = currentIsShuffleSnapshot,
            repeatMode = currentRepeatModeSnapshot,
            deviceName = currentDeviceNameSnapshot
        )
        val controller = ensureAutonomousController()
        controller.playSong(
            song = song,
            qualityPreference = domain.online.AudioQualityPreference.AUTO,
            onResolvingStarted = {},
            onPrepared = { updatedSong, _, _ ->
                currentSongSnapshot = updatedSong
                updateForegroundNotification(
                    song = updatedSong,
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
                                song = currentSongSnapshot ?: song,
                                isPlaying = false,
                                isShuffle = currentIsShuffleSnapshot,
                                repeatMode = currentRepeatModeSnapshot,
                                deviceName = currentDeviceNameSnapshot
                            )
                        }
                    }
                }
            },
            onError = { _, _ ->
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
        val currentlyPlaying = ::audioEngine.isInitialized && audioEngine.isPlaying
        when (val action = focusController.onAudioFocusChange(
            focusChange = focusChange,
            isCurrentlyPlaying = currentlyPlaying,
            resumeAfterPhoneCallEnabled = resumeAfterPhoneCallEnabled
        )) {
            is PlaybackAudioFocusController.Action.PauseForTransientLoss -> {
                pauseForTransientFocusLoss()
            }
            is PlaybackAudioFocusController.Action.DuckVolume -> {
                if (::audioEngine.isInitialized) {
                    audioEngine.setFocusDuckScale(action.targetVolume)
                }
            }
            is PlaybackAudioFocusController.Action.RestoreVolumeOnly -> {
                hasAudioFocus = true
                if (::audioEngine.isInitialized) {
                    audioEngine.setFocusDuckScale(action.targetVolume)
                }
            }
            is PlaybackAudioFocusController.Action.ResumeAfterFocusGain -> {
                hasAudioFocus = true
                if (::audioEngine.isInitialized) {
                    audioEngine.setFocusDuckScale(action.targetVolume)
                }
                handleRemotePlay()
            }
            is PlaybackAudioFocusController.Action.PauseForPermanentLoss -> {
                hasAudioFocus = false
                if (::audioEngine.isInitialized) {
                    audioEngine.setFocusDuckScale(1.0f)
                }
                handleRemotePause(userInitiated = false)
            }
            is PlaybackAudioFocusController.Action.PauseForBecomingNoisy -> {
                handleRemotePause(userInitiated = true)
            }
            PlaybackAudioFocusController.Action.None -> Unit
        }
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onDestroy() {
        super.onDestroy()
        abandonAudioFocus()
        audioDeviceCallback?.let { cb ->
            try {
                audioManager?.unregisterAudioDeviceCallback(cb)
            } catch (_: Throwable) {}
            audioDeviceCallback = null
        }
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
        autonomousController?.cancelPendingResolution()
        connectivityMonitor?.unregister()
        if (::audioEngine.isInitialized) {
            audioEngine.release()
        }
        serviceJob.cancel()
    }
}
