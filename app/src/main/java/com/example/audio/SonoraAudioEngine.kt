package com.example.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.audiofx.PresetReverb
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.example.equalizer.AdaptiveEqualizerEngine
import com.example.equalizer.AdaptiveEqualizerSettings
import com.example.model.SpatialAudioConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

class SonoraAudioEngine(private val context: Context) {

    private val tag = "SonoraAudioEngine"
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager

    val adaptiveEqualizer = AdaptiveEqualizerEngine.getInstance(context)
    val dspProcessor = SonoraDspProcessor()

    private val pcmPlayer = PcmTrackPlayer(
        context = context,
        dspProcessor = dspProcessor,
        onVisualizerFrame = { fft, wave ->
            _visualizerFrequencies.value = fft
            _waveformSamples.value = wave
            lastRealTelemetryMs = System.currentTimeMillis()
        },
        onAzimuthChanged = { azimuth ->
            _orbitAzimuthRad.value = azimuth
            _dspDiagnosticMessage.value = dspProcessor.lastDspError
        },
        onAudioSessionChanged = { newSessionId ->
            if (!usingFallbackPlayer && newSessionId != 0) {
                currentSessionId = newSessionId
                adaptiveEqualizer.attachToAudioSession(newSessionId, softwareDspHandled = true)
            }
        }
    )

    // Fallback MediaPlayer used ONLY if MediaExtractor/MediaCodec cannot open an exotic source
    private var fallbackMediaPlayer: MediaPlayer? = null
    private var usingFallbackPlayer: Boolean = false

    @Volatile
    private var lastRealTelemetryMs: Long = 0L

    // Unified playback state
    @Volatile
    private var _isPreparedState: Boolean = false
    val isPrepared: Boolean
        get() = if (usingFallbackPlayer) _isPreparedState else pcmPlayer.isPrepared

    @Volatile
    private var _isPlayingState: Boolean = false
    val isPlaying: Boolean
        get() = if (usingFallbackPlayer) _isPlayingState else pcmPlayer.isPlaying

    private var cachedDurationMs: Long = 0L
    val duration: Long
        get() = if (usingFallbackPlayer) cachedDurationMs else pcmPlayer.durationMs.coerceAtLeast(cachedDurationMs)

    private var cachedPositionMs: Long = 0L
    val currentPosition: Long
        get() = if (usingFallbackPlayer) {
            if (_isPreparedState && fallbackMediaPlayer != null) {
                try {
                    fallbackMediaPlayer?.currentPosition?.toLong()?.also { cachedPositionMs = it } ?: cachedPositionMs
                } catch (_: Exception) {
                    cachedPositionMs
                }
            } else cachedPositionMs
        } else {
            pcmPlayer.currentPositionMs.also { cachedPositionMs = it }
        }

    // Real-time Visualizer Flows
    private val _visualizerFrequencies = MutableStateFlow(FloatArray(32) { 0f })
    val visualizerFrequencies: StateFlow<FloatArray> = _visualizerFrequencies.asStateFlow()

    private val _waveformSamples = MutableStateFlow(FloatArray(64) { 0f })
    val waveformSamples: StateFlow<FloatArray> = _waveformSamples.asStateFlow()

    // TWS & Bluetooth Audio Device Detection
    private val _isTwsConnected = MutableStateFlow(false)
    val isTwsConnected: StateFlow<Boolean> = _isTwsConnected.asStateFlow()

    private val _connectedAudioDeviceName = MutableStateFlow("Phone Speaker")
    val connectedAudioDeviceName: StateFlow<String> = _connectedAudioDeviceName.asStateFlow()

    private var audioDeviceCallback: Any? = null
    private var currentSessionId = 0

    // Audio Studio & DSP State
    private var bassBoostStrength = 600 // 0..1000
    private var virtualizerStrength = 750 // 0..1000
    private var currentReverbPreset: Short = PresetReverb.PRESET_NONE
    private var loudnessGainMb = 300 // millibels
    private var spatialConfig = SpatialAudioConfig()
    private var currentPlaybackSpeed = 1.0f
    val playbackSpeed: Float
        get() = currentPlaybackSpeed

    private var globalEffectsBypass = false
    private var orbit8dEnabled = false
    private var orbit8dSpeed = 0.35f
    private var orbit8dDepth = 0.75f
    private val _orbitAzimuthRad = MutableStateFlow(0f)
    val orbitAzimuthRad: StateFlow<Float> = _orbitAzimuthRad.asStateFlow()

    private val _dspDiagnosticMessage = MutableStateFlow<String?>(null)
    val dspDiagnosticMessage: StateFlow<String?> = _dspDiagnosticMessage.asStateFlow()

    // Native PCM DSP pipeline supports Bass, Virtualizer, Reverb, and Loudness cleanly on all devices
    private val _isBassBoostSupported = MutableStateFlow(true)
    val isBassBoostSupported: StateFlow<Boolean> = _isBassBoostSupported.asStateFlow()

    private val _isVirtualizerSupported = MutableStateFlow(true)
    val isVirtualizerSupported: StateFlow<Boolean> = _isVirtualizerSupported.asStateFlow()

    private val _isReverbSupported = MutableStateFlow(true)
    val isReverbSupported: StateFlow<Boolean> = _isReverbSupported.asStateFlow()

    private val _isLoudnessSupported = MutableStateFlow(true)
    val isLoudnessSupported: StateFlow<Boolean> = _isLoudnessSupported.asStateFlow()

    private var crossfadeJob: Job? = null
    private var currentVolumeScale = 1.0f
    @Volatile
    internal var focusDuckScale = 1.0f
        private set
    private var fallbackOrbitJob: Job? = null
    private var visualizerDecayJob: Job? = null
    private var eqObserverJob: Job? = null

    init {
        setupAudioDeviceDetection()
        observeEqualizerChanges()
        pushDspSnapshot()
        startVisualizerDecayLoop()
    }

    private fun observeEqualizerChanges() {
        eqObserverJob?.cancel()
        eqObserverJob = scope.launch {
            adaptiveEqualizer.settings.collect { eqSettings ->
                pushDspSnapshot(eqSettings)
            }
        }
    }

    private fun pushDspSnapshot(eqSettings: AdaptiveEqualizerSettings = adaptiveEqualizer.settings.value) {
        val totalBoostDb = (eqSettings.volumeBoostDb + (loudnessGainMb.coerceAtLeast(0) / 100f * 0.35f))
            .coerceIn(0f, 10f)

        dspProcessor.updateSnapshot(
            SonoraDspSnapshot(
                globalBypass = globalEffectsBypass,
                eqEnabled = eqSettings.isEnabled,
                eqGainsDb = eqSettings.getEffectiveGains(),
                eqQFactor = eqSettings.macroProfile.qFactor,
                volumeBoostDb = totalBoostDb,
                bassBoostStrength = bassBoostStrength,
                virtualizerStrength = virtualizerStrength,
                reverbPreset = currentReverbPreset,
                spatialConfig = spatialConfig,
                isTwsConnected = _isTwsConnected.value,
                orbit8dEnabled = orbit8dEnabled,
                orbit8dSpeed = orbit8dSpeed,
                orbit8dDepth = orbit8dDepth,
                volumeScale = (currentVolumeScale * focusDuckScale).coerceIn(0f, 1f)
            )
        )
    }

    fun setFocusDuckScale(scale: Float) {
        val target = scale.coerceIn(0.15f, 1.0f)
        if (abs(focusDuckScale - target) < 0.01f) return
        focusDuckScale = target
        pushDspSnapshot()
        if (usingFallbackPlayer) {
            applyFallbackVolumeBalance()
        }
    }

    private fun setupAudioDeviceDetection() {
        checkCurrentAudioDevices()
        val manager = audioManager ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try {
                val callback = object : AudioDeviceCallback() {
                    override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>?) {
                        checkCurrentAudioDevices()
                        onAudioRoutingChanged()
                    }

                    override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>?) {
                        checkCurrentAudioDevices()
                        onAudioRoutingChanged()
                    }
                }
                audioDeviceCallback = callback
                manager.registerAudioDeviceCallback(callback, Handler(Looper.getMainLooper()))
            } catch (e: Throwable) {
                Log.w(tag, "AudioDeviceCallback registration failed: ${e.message}")
            }
        }
    }

    fun checkCurrentAudioDevices() {
        val manager = audioManager
        if (manager == null) {
            _isTwsConnected.value = false
            _connectedAudioDeviceName.value = "Phone Speaker"
            pushDspSnapshot()
            return
        }
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val devices = manager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
                var isBluetooth = false
                var devName = "Phone Speaker"

                for (dev in devices) {
                    when (dev.type) {
                        AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
                        AudioDeviceInfo.TYPE_BLUETOOTH_SCO -> {
                            isBluetooth = true
                            devName = dev.productName?.toString()?.takeIf { it.isNotBlank() } ?: "Bluetooth TWS"
                            break
                        }
                        else -> {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                if (dev.type == AudioDeviceInfo.TYPE_BLE_HEADSET ||
                                    dev.type == AudioDeviceInfo.TYPE_BLE_SPEAKER ||
                                    dev.type == AudioDeviceInfo.TYPE_BLE_BROADCAST) {
                                    isBluetooth = true
                                    devName = dev.productName?.toString()?.takeIf { it.isNotBlank() } ?: "BLE TWS Earbuds"
                                    break
                                }
                            }
                            if (dev.type == AudioDeviceInfo.TYPE_WIRED_HEADSET ||
                                dev.type == AudioDeviceInfo.TYPE_WIRED_HEADPHONES) {
                                devName = "Wired Headphones"
                            }
                        }
                    }
                }

                _isTwsConnected.value = isBluetooth
                _connectedAudioDeviceName.value = devName
            } else {
                @Suppress("DEPRECATION")
                val isBt = manager.isBluetoothA2dpOn
                _isTwsConnected.value = isBt
                _connectedAudioDeviceName.value = if (isBt) "Bluetooth TWS" else "Built-in Speaker"
            }
        } catch (e: Throwable) {
            Log.w(tag, "Audio device query failed: ${e.message}")
        }
        pushDspSnapshot()
    }

    fun getAudioSessionId(): Int = currentSessionId

    private var playbackRequestGeneration = 0L

    fun prepareAndPlay(
        uri: Uri,
        fallbackPath: String? = null,
        onPrepared: () -> Unit,
        onCompletion: () -> Unit,
        onError: (String) -> Unit
    ) {
        val reqGen = ++playbackRequestGeneration

        // Always cancel any pending crossfade and restore unity volume before preparing a track
        crossfadeJob?.cancel()
        crossfadeJob = null
        currentVolumeScale = 1.0f
        focusDuckScale = 1.0f
        pushDspSnapshot()

        stopFallbackMediaPlayer()
        usingFallbackPlayer = false
        _isPreparedState = false
        _isPlayingState = false
        cachedPositionMs = 0L
        _dspDiagnosticMessage.value = null

        pcmPlayer.prepareAndPlay(
            uri = uri,
            fallbackPath = fallbackPath,
            initialSpeed = currentPlaybackSpeed,
            onPrepared = { sessionId, durMs ->
                if (reqGen != playbackRequestGeneration) return@prepareAndPlay
                usingFallbackPlayer = false
                currentSessionId = sessionId
                cachedDurationMs = durMs
                _isPreparedState = true
                _isPlayingState = true
                _isBassBoostSupported.value = true
                _isVirtualizerSupported.value = true
                _isReverbSupported.value = true
                _isLoudnessSupported.value = true

                // Software DSP pipeline already handles EQ & loudness without AudioEffect conflicts
                adaptiveEqualizer.attachToAudioSession(sessionId, softwareDspHandled = true)
                onPrepared()
            },
            onCompletion = {
                if (reqGen != playbackRequestGeneration) return@prepareAndPlay
                _isPreparedState = false
                _isPlayingState = false
                onCompletion()
            },
            onFallbackRequired = { resumePositionMs, reason ->
                if (reqGen != playbackRequestGeneration) return@prepareAndPlay
                Log.w(tag, "Switching to direct MediaPlayer fallback at ${resumePositionMs}ms: $reason")
                prepareFallbackMediaPlayer(
                    requestGeneration = reqGen,
                    uri = uri,
                    fallbackPath = fallbackPath,
                    startPositionMs = resumePositionMs,
                    onPrepared = onPrepared,
                    onCompletion = onCompletion,
                    onError = onError
                )
            }
        )
    }

    private fun prepareFallbackMediaPlayer(
        requestGeneration: Long,
        uri: Uri,
        fallbackPath: String?,
        startPositionMs: Long,
        onPrepared: () -> Unit,
        onCompletion: () -> Unit,
        onError: (String) -> Unit
    ) {
        if (requestGeneration != playbackRequestGeneration) return
        usingFallbackPlayer = true
        stopFallbackMediaPlayer()
        fallbackOrbitJob?.cancel()
        fallbackOrbitJob = null

        try {
            val mp = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                setWakeMode(context, android.os.PowerManager.PARTIAL_WAKE_LOCK)

                try {
                    setDataSource(context, uri)
                } catch (uriErr: Throwable) {
                    val altPath = fallbackPath?.takeIf { it.isNotBlank() }
                        ?: if (uri.scheme.equals("file", ignoreCase = true)) uri.path else null
                    if (altPath != null) {
                        setDataSource(altPath)
                    } else {
                        throw uriErr
                    }
                }

                setOnPreparedListener { preparedMp ->
                    if (requestGeneration != playbackRequestGeneration) {
                        try { preparedMp.release() } catch (_: Throwable) {}
                        return@setOnPreparedListener
                    }
                    _isPreparedState = true
                    cachedDurationMs = try {
                        preparedMp.duration.toLong().coerceAtLeast(0L)
                    } catch (_: Exception) {
                        0L
                    }
                    currentSessionId = preparedMp.audioSessionId

                    // Attach ONLY safe Equalizer cleanly; omit Virtualizer/PresetReverb that mute AudioFlinger
                    adaptiveEqualizer.attachToAudioSession(currentSessionId, softwareDspHandled = false)
                    applyFallbackVolumeBalance()

                    if (startPositionMs > 0L) {
                        try {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                preparedMp.seekTo(startPositionMs, MediaPlayer.SEEK_CLOSEST)
                            } else {
                                preparedMp.seekTo(startPositionMs.toInt())
                            }
                        } catch (_: Throwable) {
                        }
                    }

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && currentPlaybackSpeed != 1.0f) {
                        try {
                            val params = preparedMp.playbackParams ?: android.media.PlaybackParams()
                            params.speed = currentPlaybackSpeed.coerceIn(0.5f, 2.0f)
                            preparedMp.playbackParams = params
                        } catch (e: Throwable) {
                            Log.w(tag, "Fallback playback speed warning: ${e.message}")
                        }
                    }

                    try {
                        preparedMp.start()
                        _isPlayingState = true
                    } catch (e: Exception) {
                        _isPlayingState = false
                        Log.e(tag, "Fallback start error", e)
                    }

                    if (orbit8dEnabled && !globalEffectsBypass) {
                        startFallbackOrbitLoop()
                    }
                    onPrepared()
                }

                setOnCompletionListener {
                    if (requestGeneration != playbackRequestGeneration) return@setOnCompletionListener
                    _isPreparedState = false
                    _isPlayingState = false
                    fallbackOrbitJob?.cancel()
                    onCompletion()
                }

                setOnErrorListener { _, what, extra ->
                    if (requestGeneration != playbackRequestGeneration) return@setOnErrorListener true
                    Log.w(tag, "Fallback MediaPlayer error: what=$what, extra=$extra")
                    _isPreparedState = false
                    _isPlayingState = false
                    onError("Playback error ($what, $extra)")
                    true
                }

                prepareAsync()
            }
            fallbackMediaPlayer = mp
        } catch (e: Exception) {
            Log.e(tag, "Fallback MediaPlayer setup failed", e)
            _isPreparedState = false
            _isPlayingState = false
            onError(e.message ?: "Failed to play audio")
        }
    }

    private fun stopFallbackMediaPlayer() {
        fallbackOrbitJob?.cancel()
        fallbackOrbitJob = null
        try {
            fallbackMediaPlayer?.release()
        } catch (_: Throwable) {
        }
        fallbackMediaPlayer = null
    }

    fun play() {
        if (usingFallbackPlayer) {
            if (!_isPreparedState) return
            try {
                fallbackMediaPlayer?.start()
                _isPlayingState = true
                if (orbit8dEnabled && !globalEffectsBypass) startFallbackOrbitLoop()
            } catch (e: Exception) {
                _isPlayingState = false
                Log.e(tag, "Play error", e)
            }
        } else {
            if (!pcmPlayer.isPrepared) return
            pcmPlayer.play()
            _isPlayingState = true
        }
    }

    fun pause() {
        if (usingFallbackPlayer) {
            if (!_isPreparedState) return
            try {
                fallbackMediaPlayer?.pause()
                _isPlayingState = false
                fallbackOrbitJob?.cancel()
            } catch (e: Exception) {
                Log.e(tag, "Pause error", e)
            }
        } else {
            if (!pcmPlayer.isPrepared) return
            pcmPlayer.pause()
            _isPlayingState = false
        }
    }

    fun seekTo(positionMs: Long) {
        cachedPositionMs = positionMs.coerceAtLeast(0L)
        if (usingFallbackPlayer) {
            if (!_isPreparedState) return
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    fallbackMediaPlayer?.seekTo(positionMs, MediaPlayer.SEEK_CLOSEST)
                } else {
                    fallbackMediaPlayer?.seekTo(positionMs.toInt())
                }
            } catch (e: Exception) {
                Log.e(tag, "Seek error", e)
            }
        } else {
            pcmPlayer.seekTo(positionMs)
        }
    }

    fun crossfadeOut(
        fadeDurationMs: Long,
        onFadedOut: () -> Unit
    ) {
        crossfadeJob?.cancel()
        if (fadeDurationMs <= 0 || !isPlaying) {
            currentVolumeScale = 1.0f
            pushDspSnapshot()
            onFadedOut()
            return
        }

        crossfadeJob = scope.launch {
            val steps = 20
            val stepDelay = (fadeDurationMs / steps).coerceAtLeast(10L)
            for (i in steps downTo 0) {
                currentVolumeScale = (i.toFloat() / steps.toFloat()).coerceIn(0f, 1f)
                pushDspSnapshot()
                if (usingFallbackPlayer) applyFallbackVolumeBalance()
                delay(stepDelay)
            }
            onFadedOut()
        }
    }

    fun crossfadeIn(fadeDurationMs: Long) {
        crossfadeJob?.cancel()
        if (fadeDurationMs <= 0) {
            currentVolumeScale = 1.0f
            pushDspSnapshot()
            if (usingFallbackPlayer) applyFallbackVolumeBalance()
            return
        }

        crossfadeJob = scope.launch {
            currentVolumeScale = 0f
            pushDspSnapshot()
            if (usingFallbackPlayer) applyFallbackVolumeBalance()
            val steps = 20
            val stepDelay = (fadeDurationMs / steps).coerceAtLeast(10L)
            for (i in 0..steps) {
                currentVolumeScale = (i.toFloat() / steps.toFloat()).coerceIn(0f, 1f)
                pushDspSnapshot()
                if (usingFallbackPlayer) applyFallbackVolumeBalance()
                delay(stepDelay)
            }
            currentVolumeScale = 1.0f
            pushDspSnapshot()
            if (usingFallbackPlayer) applyFallbackVolumeBalance()
        }
    }

    fun setPlaybackSpeed(speed: Float, pitch: Float = 1.0f) {
        currentPlaybackSpeed = speed.coerceIn(0.5f, 2.0f)
        if (usingFallbackPlayer) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && _isPreparedState) {
                try {
                    val params = fallbackMediaPlayer?.playbackParams ?: android.media.PlaybackParams()
                    params.speed = currentPlaybackSpeed
                    params.pitch = pitch.coerceIn(0.5f, 2.0f)
                    fallbackMediaPlayer?.playbackParams = params
                } catch (e: Exception) {
                    Log.w(tag, "Failed to set playback params: ${e.message}")
                }
            }
        } else {
            pcmPlayer.setPlaybackSpeed(currentPlaybackSpeed, pitch)
        }
    }

    fun setPlaybackSpeed(speed: Float) = setPlaybackSpeed(speed, 1.0f)

    // --- Safe Lock-Free DSP Toggle & Slider Controls ---

    fun setGlobalEffectsBypass(bypass: Boolean) {
        globalEffectsBypass = bypass
        adaptiveEqualizer.setExternalBypass(bypass)
        pushDspSnapshot()
        if (usingFallbackPlayer) {
            if (bypass) {
                fallbackOrbitJob?.cancel()
                fallbackOrbitJob = null
                applyFallbackVolumeBalance()
            } else if (orbit8dEnabled) {
                startFallbackOrbitLoop()
            }
        }
    }

    fun update8dOrbitConfig(enabled: Boolean, speed: Float, depth: Float) {
        orbit8dEnabled = enabled
        orbit8dSpeed = speed.coerceIn(0.1f, 2.0f)
        orbit8dDepth = depth.coerceIn(0.1f, 1.0f)
        pushDspSnapshot()
        if (usingFallbackPlayer) {
            if (enabled && !globalEffectsBypass) {
                startFallbackOrbitLoop()
            } else {
                fallbackOrbitJob?.cancel()
                fallbackOrbitJob = null
                applyFallbackVolumeBalance()
            }
        }
    }

    fun setBassBoost(strength: Int) {
        bassBoostStrength = strength.coerceIn(0, 1000)
        pushDspSnapshot()
    }

    fun setVirtualizer(strength: Int) {
        virtualizerStrength = strength.coerceIn(0, 1000)
        pushDspSnapshot()
    }

    fun setReverbPreset(preset: Short) {
        currentReverbPreset = preset
        pushDspSnapshot()
    }

    fun setLoudnessEnhancer(gainMb: Int) {
        loudnessGainMb = gainMb.coerceIn(0, 1000)
        pushDspSnapshot()
    }

    fun updateSpatialAudioConfig(config: SpatialAudioConfig) {
        spatialConfig = config.copy(
            intensity = config.intensity.coerceIn(0f, 1f),
            soundstageWidth = config.soundstageWidth.coerceIn(0f, 1f),
            stereoWidening = config.stereoWidening.coerceIn(0f, 1f),
            channelBalance = config.channelBalance.coerceIn(-1f, 1f),
            listenerX = config.listenerX.coerceIn(-1f, 1f),
            listenerY = config.listenerY.coerceIn(-1f, 1f)
        )
        pushDspSnapshot()
        if (usingFallbackPlayer) {
            applyFallbackVolumeBalance()
        }
    }

    fun onAudioRoutingChanged() {
        scope.launch {
            checkCurrentAudioDevices()
            pushDspSnapshot()
            if (usingFallbackPlayer) applyFallbackVolumeBalance()
            delay(150)
            checkCurrentAudioDevices()
            pushDspSnapshot()
        }
    }

    fun forceSymmetricalDualEarbudBalance() {
        spatialConfig = spatialConfig.copy(
            channelBalance = 0.0f,
            listenerX = 0.0f
        )
        pushDspSnapshot()
        if (usingFallbackPlayer) {
            applyFallbackVolumeBalance()
        }
    }

    private fun startFallbackOrbitLoop() {
        fallbackOrbitJob?.cancel()
        fallbackOrbitJob = scope.launch {
            var angle = _orbitAzimuthRad.value
            while (isActive && usingFallbackPlayer && orbit8dEnabled && !globalEffectsBypass) {
                if (_isPlayingState) {
                    val profile = spatialConfig.spatialProfile
                    val speedMul = when (profile) {
                        com.example.model.SpatialProfile.ORBIT -> 0.65f
                        com.example.model.SpatialProfile.DYNAMIC_360 -> 1.0f
                        com.example.model.SpatialProfile.CINEMATIC -> 0.30f
                    }
                    val depthWeight = if (profile == com.example.model.SpatialProfile.CINEMATIC) 0.45f else 1.0f
                    angle = (angle + 0.03f * orbit8dSpeed * speedMul) % (2f * Math.PI.toFloat())
                    _orbitAzimuthRad.value = angle
                    val pan = sin(angle.toDouble()).toFloat() * orbit8dDepth * depthWeight *
                        spatialConfig.spatialIntensity.multiplier.coerceIn(0.5f, 1.05f)
                    val leftGain = (1f - (pan.coerceAtLeast(0f) * 0.45f)).coerceIn(0.45f, 1f)
                    val rightGain = (1f - ((-pan).coerceAtLeast(0f) * 0.45f)).coerceIn(0.45f, 1f)
                    val floor = if (currentVolumeScale > 0.05f) 0.42f * currentVolumeScale else 0f
                    try {
                        fallbackMediaPlayer?.setVolume(
                            (leftGain * currentVolumeScale).coerceIn(floor, 1f),
                            (rightGain * currentVolumeScale).coerceIn(floor, 1f)
                        )
                    } catch (_: Throwable) {
                    }
                }
                delay(33L)
            }
        }
    }

    private fun applyFallbackVolumeBalance() {
        val balance = spatialConfig.channelBalance.coerceIn(-1.0f, 1.0f)
        var left = if (balance > 0f) (1.0f - balance) else 1.0f
        var right = if (balance < 0f) (1.0f + balance) else 1.0f
        if (abs(balance) < 0.05f) {
            left = left.coerceAtLeast(0.95f)
            right = right.coerceAtLeast(0.95f)
        }
        val floor = if (currentVolumeScale > 0.05f) 0.40f * currentVolumeScale else 0f
        try {
            fallbackMediaPlayer?.setVolume(
                (left * currentVolumeScale).coerceIn(floor, 1f),
                (right * currentVolumeScale).coerceIn(floor, 1f)
            )
        } catch (e: Throwable) {
            Log.w(tag, "Fallback balance error: ${e.message}")
        }
    }

    private fun startVisualizerDecayLoop() {
        visualizerDecayJob?.cancel()
        visualizerDecayJob = scope.launch {
            var phase = 0f
            while (isActive) {
                delay(33L)
                val now = System.currentTimeMillis()
                if (isPlaying) {
                    // Only synthesize fallback animation if no live PCM telemetry arrived recently (e.g. MediaPlayer fallback)
                    if (now - lastRealTelemetryMs > 180L) {
                        phase += 0.15f
                        val simulatedBins = FloatArray(32)
                        for (i in 0 until 32) {
                            val f = (i + 1) * 0.4
                            val v = (0.45 * sin(phase * 1.5 + f) + 0.35 * cos(phase * 0.8 - f * 0.5) + 0.20 * sin(phase * 2.3 + i))
                                .toFloat()
                                .coerceIn(0f, 1f)
                            simulatedBins[i] = v
                        }
                        _visualizerFrequencies.value = simulatedBins

                        val simulatedWave = FloatArray(64)
                        for (i in 0 until 64) {
                            val p = i * 0.15f + phase
                            simulatedWave[i] = (0.7f * sin(p) + 0.3f * cos(p * 2.1f)).coerceIn(-1f, 1f)
                        }
                        _waveformSamples.value = simulatedWave
                    }
                } else {
                    val curFreq = _visualizerFrequencies.value
                    val curWave = _waveformSamples.value
                    val freqActive = curFreq.any { abs(it) > 0.002f }
                    val waveActive = curWave.any { abs(it) > 0.002f }
                    if (freqActive) {
                        val decayedFreq = FloatArray(curFreq.size) { i ->
                            val v = curFreq[i] * 0.82f
                            if (v > 0.002f) v else 0f
                        }
                        _visualizerFrequencies.value = decayedFreq
                    }
                    if (waveActive) {
                        val decayedWave = FloatArray(curWave.size) { i ->
                            val v = curWave[i] * 0.82f
                            if (abs(v) > 0.002f) v else 0f
                        }
                        _waveformSamples.value = decayedWave
                    }
                    if (!freqActive && !waveActive) {
                        delay(180L)
                    }
                }
            }
        }
    }

    fun release() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && audioDeviceCallback != null) {
            try {
                (audioDeviceCallback as? AudioDeviceCallback)?.let { cb ->
                    audioManager?.unregisterAudioDeviceCallback(cb)
                }
            } catch (_: Exception) {
            }
            audioDeviceCallback = null
        }
        crossfadeJob?.cancel()
        fallbackOrbitJob?.cancel()
        visualizerDecayJob?.cancel()
        eqObserverJob?.cancel()
        pcmPlayer.stop()
        stopFallbackMediaPlayer()
        adaptiveEqualizer.releaseHardwareEffects()
        scope.cancel()
        _isPreparedState = false
        _isPlayingState = false
    }
}
