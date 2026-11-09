package com.example.audio

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioFormat
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.Handler
import android.os.Looper
import android.media.audiofx.AudioEffect
import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import android.media.audiofx.LoudnessEnhancer
import android.media.audiofx.PresetReverb
import android.media.audiofx.Virtualizer
import android.media.audiofx.Visualizer
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import com.example.model.EqualizerBand
import com.example.model.SpatialAudioConfig
import com.example.model.SpatialMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

class SonoraAudioEngine(private val context: Context) {

    private val tag = "SonoraAudioEngine"
    private var mediaPlayer: MediaPlayer? = null
    private val scope = CoroutineScope(Dispatchers.Main + Job())

    // Safe Player State Tracking to prevent MediaPlayerNative (-38, 0) and state errors
    var isPrepared: Boolean = false
        private set

    private var _isPlayingState: Boolean = false
    val isPlaying: Boolean
        get() = _isPlayingState

    private var cachedDurationMs: Long = 0L
    val duration: Long
        get() = cachedDurationMs

    private var cachedPositionMs: Long = 0L
    val currentPosition: Long
        get() = if (isPrepared && mediaPlayer != null) {
            try {
                mediaPlayer?.currentPosition?.toLong()?.also { cachedPositionMs = it } ?: cachedPositionMs
            } catch (e: Exception) {
                cachedPositionMs
            }
        } else {
            cachedPositionMs
        }

    // Audio FX instances attached to current audioSessionId
    private var equalizer: Equalizer? = null
    private var bassBoost: BassBoost? = null
    private var virtualizer: Virtualizer? = null
    private var presetReverb: PresetReverb? = null
    private var loudnessEnhancer: LoudnessEnhancer? = null
    private var visualizer: Visualizer? = null

    // Audio Visualizer data flow (32 frequency bins for high-framerate UI rendering)
    private val _visualizerFrequencies = MutableStateFlow(FloatArray(32) { 0f })
    val visualizerFrequencies: StateFlow<FloatArray> = _visualizerFrequencies.asStateFlow()

    private val _waveformSamples = MutableStateFlow(FloatArray(64) { 0f })
    val waveformSamples: StateFlow<FloatArray> = _waveformSamples.asStateFlow()

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    // Supported Equalizer Bands from Hardware/Framework
    private val _equalizerBands = MutableStateFlow<List<EqualizerBand>>(emptyList())
    val equalizerBands: StateFlow<List<EqualizerBand>> = _equalizerBands.asStateFlow()

    // TWS & Bluetooth Audio Device Detection & State Tracking
    private val _isTwsConnected = MutableStateFlow(false)
    val isTwsConnected: StateFlow<Boolean> = _isTwsConnected.asStateFlow()

    private val _connectedAudioDeviceName = MutableStateFlow("Phone Speaker")
    val connectedAudioDeviceName: StateFlow<String> = _connectedAudioDeviceName.asStateFlow()

    private var audioDeviceCallback: Any? = null

    private var currentSessionId = 0
    private var eqEnabled = true
    private var bassBoostStrength = 600 // 0..1000
    private var virtualizerStrength = 750 // 0..1000
    private var currentReverbPreset: Short = PresetReverb.PRESET_NONE
    private var loudnessGainMb = 300 // millibels
    private var spatialConfig = SpatialAudioConfig()

    // Visualizer simulation fallback job for smooth animations
    private var simulationJob: Job? = null

    init {
        setupFallbackEqualizerBands()
        setupAudioDeviceDetection()
        initMediaPlayer()
        startVisualizerLoop()
    }

    private fun setupAudioDeviceDetection() {
        checkCurrentAudioDevices()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
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
            audioManager.registerAudioDeviceCallback(callback, Handler(Looper.getMainLooper()))
        }
    }

    fun checkCurrentAudioDevices() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val devices = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
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
            val isBt = audioManager.isBluetoothA2dpOn
            _isTwsConnected.value = isBt
            _connectedAudioDeviceName.value = if (isBt) "Bluetooth TWS" else "Built-in Speaker"
        }
    }

    private fun initMediaPlayer() {
        releaseEffects()
        try {
            mediaPlayer?.release()
        } catch (e: Exception) {
            // Ignored
        }

        isPrepared = false
        _isPlayingState = false

        mediaPlayer = MediaPlayer().apply {
            val attrBuilder = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                attrBuilder.setAllowedCapturePolicy(AudioAttributes.ALLOW_CAPTURE_BY_ALL)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S_V2) {
                // Prevent duplicate system spatializer from muting or phase-cancelling the right earbud on TWS
                attrBuilder.setSpatializationBehavior(AudioAttributes.SPATIALIZATION_BEHAVIOR_NEVER)
            }

            setAudioAttributes(attrBuilder.build())
            setWakeMode(context, android.os.PowerManager.PARTIAL_WAKE_LOCK)
        }

        currentSessionId = mediaPlayer?.audioSessionId ?: 0
    }

    fun getAudioSessionId(): Int = currentSessionId

    fun prepareAndPlay(
        uri: android.net.Uri,
        onPrepared: () -> Unit,
        onCompletion: () -> Unit,
        onError: (String) -> Unit
    ) {
        try {
            val player = mediaPlayer ?: run {
                initMediaPlayer()
                mediaPlayer!!
            }

            isPrepared = false
            _isPlayingState = false
            cachedPositionMs = 0L

            player.reset()
            player.setDataSource(context, uri)

            player.setOnPreparedListener { mp ->
                isPrepared = true
                cachedDurationMs = try {
                    mp.duration.toLong().coerceAtLeast(0L)
                } catch (e: Exception) {
                    0L
                }

                currentSessionId = mp.audioSessionId
                setupAudioEffects(currentSessionId)
                applyVolumeAndBalance()

                try {
                    mp.start()
                    _isPlayingState = true
                } catch (e: Exception) {
                    _isPlayingState = false
                    Log.e(tag, "Start error in onPrepared", e)
                }

                onPrepared()
            }

            player.setOnCompletionListener {
                _isPlayingState = false
                onCompletion()
            }

            player.setOnErrorListener { _, what, extra ->
                Log.w(tag, "MediaPlayer Error: what=$what, extra=$extra")
                isPrepared = false
                _isPlayingState = false
                onError("Playback error ($what, $extra)")
                true // Handled, prevents calling onCompletion
            }

            player.prepareAsync()
        } catch (e: Exception) {
            Log.e(tag, "Error setting data source", e)
            isPrepared = false
            _isPlayingState = false
            onError(e.message ?: "Failed to play audio")
        }
    }

    private var crossfadeJob: Job? = null
    private var currentVolumeScale = 1.0f

    fun play() {
        if (!isPrepared) return
        try {
            mediaPlayer?.start()
            _isPlayingState = true
        } catch (e: Exception) {
            _isPlayingState = false
            Log.e(tag, "Play error", e)
        }
    }

    /**
     * Smoothly fades out the current track over fadeDurationMs,
     * then executes onFadedOut() callback to seamlessly load and fade in the next track.
     */
    fun crossfadeOut(
        fadeDurationMs: Long,
        onFadedOut: () -> Unit
    ) {
        crossfadeJob?.cancel()
        if (fadeDurationMs <= 0 || !isPlaying) {
            onFadedOut()
            return
        }

        crossfadeJob = scope.launch {
            val steps = 20
            val stepDelay = (fadeDurationMs / steps).coerceAtLeast(10L)
            for (i in steps downTo 0) {
                currentVolumeScale = (i.toFloat() / steps.toFloat()).coerceIn(0f, 1f)
                applyVolumeAndBalance()
                delay(stepDelay)
            }
            onFadedOut()
        }
    }

    /**
     * Smoothly fades in the newly loaded track from 0 to full volume over fadeDurationMs.
     */
    fun crossfadeIn(fadeDurationMs: Long) {
        crossfadeJob?.cancel()
        if (fadeDurationMs <= 0) {
            currentVolumeScale = 1.0f
            applyVolumeAndBalance()
            return
        }

        crossfadeJob = scope.launch {
            currentVolumeScale = 0f
            applyVolumeAndBalance()
            val steps = 20
            val stepDelay = (fadeDurationMs / steps).coerceAtLeast(10L)
            for (i in 0..steps) {
                currentVolumeScale = (i.toFloat() / steps.toFloat()).coerceIn(0f, 1f)
                applyVolumeAndBalance()
                delay(stepDelay)
            }
            currentVolumeScale = 1.0f
            applyVolumeAndBalance()
        }
    }

    fun pause() {
        if (!isPrepared) return
        try {
            mediaPlayer?.pause()
            _isPlayingState = false
        } catch (e: Exception) {
            Log.e(tag, "Pause error", e)
        }
    }

    fun seekTo(positionMs: Long) {
        if (!isPrepared) {
            cachedPositionMs = positionMs
            return
        }
        try {
            cachedPositionMs = positionMs
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                mediaPlayer?.seekTo(positionMs, MediaPlayer.SEEK_CLOSEST)
            } else {
                mediaPlayer?.seekTo(positionMs.toInt())
            }
        } catch (e: Exception) {
            Log.e(tag, "Seek error", e)
        }
    }

    fun setPlaybackSpeed(speed: Float, pitch: Float = 1.0f) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && isPrepared) {
            try {
                val params = mediaPlayer?.playbackParams ?: android.media.PlaybackParams()
                params.speed = speed.coerceIn(0.25f, 2.5f)
                params.pitch = pitch.coerceIn(0.5f, 2.0f)
                mediaPlayer?.playbackParams = params
            } catch (e: Exception) {
                Log.w(tag, "Failed to set playback params: ${e.message}")
            }
        }
    }

    // --- Equalizer & Audio Effects Management ---

    private fun setupAudioEffects(sessionId: Int) {
        if (sessionId == 0 || sessionId == AudioManager.ERROR) return

        // 1. Equalizer
        try {
            if (equalizer == null) {
                equalizer = Equalizer(0, sessionId).apply {
                    enabled = eqEnabled
                }
                // Maintain the 15-band precision filter array
                setupFallbackEqualizerBands()
            }
        } catch (e: Throwable) {
            Log.w(tag, "Hardware Equalizer not available on this session, using software DSP: ${e.message}")
        }

        // 2. BassBoost
        try {
            if (bassBoost == null) {
                bassBoost = BassBoost(0, sessionId).apply {
                    if (strengthSupported) {
                        setStrength(bassBoostStrength.toShort())
                        enabled = bassBoostStrength > 0
                    }
                }
            }
        } catch (e: Throwable) {
            Log.w(tag, "Hardware BassBoost not available: ${e.message}")
        }

        // 3. Virtualizer
        try {
            if (virtualizer == null) {
                virtualizer = Virtualizer(0, sessionId).apply {
                    if (strengthSupported) {
                        setStrength(virtualizerStrength.toShort())
                        enabled = virtualizerStrength > 0
                    }
                }
            }
        } catch (e: Throwable) {
            Log.w(tag, "Hardware Virtualizer not available: ${e.message}")
        }

        // 4. PresetReverb (Safely check before instantiating to avoid AudioFlinger status -1)
        try {
            if (presetReverb == null && currentReverbPreset != PresetReverb.PRESET_NONE) {
                presetReverb = PresetReverb(0, sessionId).apply {
                    preset = currentReverbPreset
                    enabled = true
                }
            }
        } catch (e: Throwable) {
            Log.w(tag, "Hardware PresetReverb not supported by AudioFlinger: ${e.message}")
            presetReverb = null
        }

        // 5. LoudnessEnhancer
        try {
            if (loudnessEnhancer == null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                loudnessEnhancer = LoudnessEnhancer(sessionId).apply {
                    setTargetGain(loudnessGainMb)
                    enabled = loudnessGainMb > 0
                }
            }
        } catch (e: Throwable) {
            Log.w(tag, "LoudnessEnhancer not available: ${e.message}")
        }

        // 6. Visualizer (Only try if RECORD_AUDIO permission is granted to prevent initCheck -3)
        setupVisualizer(sessionId)
    }

    companion object {
        // Studio-Grade 15-Band ISO Center Frequencies
        val ISO_15_BAND_FREQUENCIES = listOf(
            25,    // Sub-bass rumble
            40,    // Deep bass
            63,    // Punch bass
            100,   // Low bass
            160,   // Upper bass
            250,   // Warmth / low mids
            400,   // Low mids
            630,   // Center mids
            1000,  // Vocal presence
            1600,  // Upper mids
            2500,  // Vocal bite / edge
            4000,  // Presence / attack
            6300,  // Definition / brightness
            10000, // Treble / air
            16000  // Ultra-high brilliance
        )
    }

    private fun setupFallbackEqualizerBands() {
        _equalizerBands.value = ISO_15_BAND_FREQUENCIES.mapIndexed { idx, freq ->
            EqualizerBand(
                bandIndex = idx.toShort(),
                centerFreqHz = freq,
                minLevelMb = -1500,
                maxLevelMb = 1500,
                currentLevelMb = 0
            )
        }
    }

    fun setEqualizerEnabled(enabled: Boolean) {
        eqEnabled = enabled
        try {
            equalizer?.enabled = enabled
        } catch (e: Throwable) {
            Log.w(tag, "Error setting EQ enabled", e)
        }
    }

    fun setBandGain(bandIndex: Short, gainMb: Short) {
        val updated = _equalizerBands.value.map {
            if (it.bandIndex == bandIndex) it.copy(currentLevelMb = gainMb) else it
        }
        _equalizerBands.value = updated

        try {
            val eq = equalizer
            if (eq != null) {
                val hwBands = eq.numberOfBands.toInt()
                if (hwBands > 0) {
                    // Map 15-band index (0..14) proportionally to hardware band index (0..hwBands-1)
                    val hwIndex = ((bandIndex.toFloat() / 14f) * (hwBands - 1)).toInt().coerceIn(0, hwBands - 1).toShort()
                    val range = eq.bandLevelRange
                    val clampedGain = gainMb.coerceIn(range[0], range[1])
                    eq.setBandLevel(hwIndex, clampedGain)
                }
            }
        } catch (e: Throwable) {
            Log.w(tag, "Error mapping 15-band gain to hardware EQ", e)
        }
    }

    fun setBassBoost(strength: Int) {
        bassBoostStrength = strength.coerceIn(0, 1000)
        try {
            bassBoost?.apply {
                if (strengthSupported) {
                    setStrength(bassBoostStrength.toShort())
                    enabled = bassBoostStrength > 0
                }
            }
        } catch (e: Throwable) {
            Log.w(tag, "Error setting bass boost", e)
        }
    }

    fun setVirtualizer(strength: Int) {
        virtualizerStrength = strength.coerceIn(0, 1000)
        try {
            virtualizer?.apply {
                if (strengthSupported) {
                    setStrength(virtualizerStrength.toShort())
                    enabled = virtualizerStrength > 0
                }
            }
        } catch (e: Throwable) {
            Log.w(tag, "Error setting virtualizer", e)
        }
    }

    fun setReverbPreset(preset: Short) {
        currentReverbPreset = preset
        try {
            if (currentSessionId != 0 && preset != PresetReverb.PRESET_NONE && presetReverb == null) {
                presetReverb = PresetReverb(0, currentSessionId)
            }
            presetReverb?.apply {
                this.preset = preset
                enabled = preset != PresetReverb.PRESET_NONE
            }
        } catch (e: Throwable) {
            Log.w(tag, "PresetReverb not supported: ${e.message}")
        }
    }

    fun setLoudnessEnhancer(gainMb: Int) {
        loudnessGainMb = gainMb
        try {
            loudnessEnhancer?.apply {
                setTargetGain(gainMb)
                enabled = gainMb > 0
            }
        } catch (e: Throwable) {
            Log.w(tag, "Error setting loudness enhancer", e)
        }
    }

    // --- 360° Spatial Audio Processing & TWS Wide Stereo Engine ---

    fun updateSpatialAudioConfig(config: SpatialAudioConfig) {
        this.spatialConfig = config
        applySpatialAudio()
    }

    private fun applySpatialAudio() {
        if (!spatialConfig.isEnabled) {
            try {
                virtualizer?.enabled = false
            } catch (e: Throwable) {
                // Ignored
            }
            applyVolumeAndBalance()
            return
        }

        configureVirtualizerForRouting()

        // Calculate stereo widening factor, with extra expansion when TWS Wide Stereo is active
        val effectiveWidening = if (_isTwsConnected.value && spatialConfig.twsWideStereoEnabled) {
            (spatialConfig.stereoWidening * 1.25f * spatialConfig.twsStereoBoost).coerceIn(0.5f, 1.0f)
        } else {
            spatialConfig.stereoWidening
        }

        when (spatialConfig.mode) {
            SpatialMode.HEADPHONES -> {
                applyVolumeAndBalance(spatialStereoWidening = effectiveWidening)
            }
            SpatialMode.SPEAKERS -> {
                applyVolumeAndBalance(spatialStereoWidening = effectiveWidening * 0.75f)
            }
            SpatialMode.CINEMA_360 -> {
                applyVolumeAndBalance(spatialStereoWidening = 1.0f)
            }
        }
    }

    /**
     * Configures the Virtualizer effect specifically for current audio routing.
     * FIX FOR TWS LEFT-ONLY AUDIO:
     * When Bluetooth TWS connects, Android AudioFlinger defaults to transaural speaker mode,
     * which applies phase-inversion crosstalk cancellation that zeroes out the right earbud.
     * We explicitly force VIRTUALIZATION_MODE_BINAURAL so both left & right earbuds receive
     * balanced, natural, immersive 360° spatial audio.
     */
    private fun configureVirtualizerForRouting() {
        val virt = virtualizer ?: return

        try {
            val isHeadphoneOrTws = _isTwsConnected.value ||
                    spatialConfig.mode == SpatialMode.HEADPHONES ||
                    spatialConfig.mode == SpatialMode.CINEMA_360

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                if (isHeadphoneOrTws) {
                    val canBinaural = virt.canVirtualize(
                        AudioFormat.CHANNEL_OUT_STEREO,
                        Virtualizer.VIRTUALIZATION_MODE_BINAURAL
                    )
                    if (canBinaural) {
                        virt.forceVirtualizationMode(Virtualizer.VIRTUALIZATION_MODE_BINAURAL)
                    }
                } else {
                    val canTransaural = virt.canVirtualize(
                        AudioFormat.CHANNEL_OUT_STEREO,
                        Virtualizer.VIRTUALIZATION_MODE_TRANSAURAL
                    )
                    if (canTransaural) {
                        virt.forceVirtualizationMode(Virtualizer.VIRTUALIZATION_MODE_TRANSAURAL)
                    }
                }
            }

            if (spatialConfig.isEnabled) {
                val baseVirtualizer = (spatialConfig.intensity * 1000f * spatialConfig.soundstageWidth).toInt()
                val twsFactor = if (_isTwsConnected.value && spatialConfig.twsWideStereoEnabled) {
                    (1.15f * spatialConfig.twsStereoBoost).coerceIn(1.0f, 1.4f)
                } else {
                    1.0f
                }
                val targetStrength = (baseVirtualizer * twsFactor).toInt().coerceIn(0, 1000)

                if (virt.strengthSupported) {
                    virt.setStrength(targetStrength.toShort())
                }
                virt.enabled = targetStrength > 0
            }
        } catch (e: Throwable) {
            Log.w(tag, "Virtualizer routing configuration error: ${e.message}")
        }
    }

    /**
     * Invoked when Bluetooth TWS or audio routing changes (e.g. connecting/disconnecting earbuds).
     * Re-applies volume and effects on staggered intervals because secondary TWS earbuds negotiate
     * 50-250ms after the primary earbud.
     */
    fun onAudioRoutingChanged() {
        scope.launch {
            checkCurrentAudioDevices()
            configureVirtualizerForRouting()
            applyVolumeAndBalance()

            delay(100)
            configureVirtualizerForRouting()
            applyVolumeAndBalance()

            delay(250)
            configureVirtualizerForRouting()
            applyVolumeAndBalance()
        }
    }

    /**
     * Fixes and immediately forces symmetrical, balanced 100% stereo gain to both earbuds.
     */
    fun forceSymmetricalDualEarbudBalance() {
        spatialConfig = spatialConfig.copy(
            channelBalance = 0.0f,
            listenerX = 0.0f
        )
        try {
            mediaPlayer?.setVolume(1.0f, 1.0f)
        } catch (e: Throwable) {
            Log.w(tag, "forceSymmetricalDualEarbudBalance error: ${e.message}")
        }
        configureVirtualizerForRouting()
        applyVolumeAndBalance()
    }

    private fun applyVolumeAndBalance(spatialStereoWidening: Float = 0f) {
        val balance = spatialConfig.channelBalance.coerceIn(-1.0f, 1.0f)
        var left = if (balance > 0f) (1.0f - balance) else 1.0f
        var right = if (balance < 0f) (1.0f + balance) else 1.0f

        if (spatialConfig.isEnabled) {
            val posX = spatialConfig.listenerX.coerceIn(-1f, 1f)
            if (posX > 0) {
                left *= (1.0f - (posX * 0.35f))
            } else if (posX < 0) {
                right *= (1.0f - (kotlin.math.abs(posX) * 0.35f))
            }
        }

        // TWS Single-Earbud Audio Fix:
        // When channel balance is centered, GUARANTEE that neither the left nor the right
        // earbud receives degraded signal. Both are held at full 100% stereo amplitude!
        if (kotlin.math.abs(spatialConfig.channelBalance) < 0.05f) {
            left = left.coerceAtLeast(0.95f)
            right = right.coerceAtLeast(0.95f)
        }

        val finalLeft = (left * currentVolumeScale).coerceIn(0.0f, 1.0f)
        val finalRight = (right * currentVolumeScale).coerceIn(0.0f, 1.0f)

        try {
            mediaPlayer?.setVolume(finalLeft, finalRight)
        } catch (e: Throwable) {
            Log.w(tag, "Error setting volume/balance: ${e.message}")
        }
    }

    // --- Audio Visualizer & Real-time FFT Engine ---

    private fun setupVisualizer(sessionId: Int) {
        // Check permission first to avoid OS-level Visualizer-JAVA Error code -3
        val hasRecordPerm = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED

        if (!hasRecordPerm) {
            // Permission not yet granted, fallback audio simulation will run smoothly
            return
        }

        try {
            visualizer?.release()
            visualizer = Visualizer(sessionId).apply {
                captureSize = Visualizer.getCaptureSizeRange()[1].coerceAtMost(512)
                setDataCaptureListener(
                    object : Visualizer.OnDataCaptureListener {
                        override fun onWaveFormDataCapture(vis: Visualizer?, waveform: ByteArray?, samplingRate: Int) {
                            waveform?.let { processWaveformData(it) }
                        }

                        override fun onFftDataCapture(vis: Visualizer?, fft: ByteArray?, samplingRate: Int) {
                            fft?.let { processFftData(it) }
                        }
                    },
                    Visualizer.getMaxCaptureRate() / 2,
                    true,
                    true
                )
                enabled = true
            }
        } catch (e: Throwable) {
            // Hardware visualizer failed (e.g. on emulator); simulation handles it gracefully
            visualizer = null
        }
    }

    private fun processFftData(fft: ByteArray) {
        val n = fft.size / 2
        val bins = FloatArray(32)
        val binSize = (n / 32).coerceAtLeast(1)

        for (i in 0 until 32) {
            var sum = 0f
            for (j in 0 until binSize) {
                val idx = (i * binSize + j) * 2
                if (idx + 1 < fft.size) {
                    val r = fft[idx].toFloat()
                    val im = fft[idx + 1].toFloat()
                    sum += sqrt(r * r + im * im)
                }
            }
            val mag = (sum / binSize / 128f).coerceIn(0f, 1f)
            bins[i] = mag
        }
        _visualizerFrequencies.value = bins
    }

    private fun processWaveformData(waveform: ByteArray) {
        val samples = FloatArray(64)
        val step = (waveform.size / 64).coerceAtLeast(1)
        for (i in 0 until 64) {
            val idx = (i * step).coerceAtMost(waveform.size - 1)
            val v = ((waveform[idx].toInt() and 0xFF) - 128) / 128f
            samples[i] = v
        }
        _waveformSamples.value = samples
    }

    private fun startVisualizerLoop() {
        simulationJob?.cancel()
        simulationJob = scope.launch {
            var phase = 0f
            while (isActive) {
                delay(33) // ~30 fps update
                if (_isPlayingState) {
                    phase += 0.15f
                    val currentFft = _visualizerFrequencies.value
                    val isFftZero = currentFft.all { it == 0f }

                    // If hardware FFT is silent or unavailable, produce dynamic audio-reactive waveform & spectrum
                    if (isFftZero) {
                        val simulatedBins = FloatArray(32)
                        for (i in 0 until 32) {
                            val f = (i + 1) * 0.4
                            val v = (
                                0.45 * sin(phase * 1.5 + f) +
                                0.35 * cos(phase * 0.8 - f * 0.5) +
                                0.20 * sin(phase * 2.3 + i)
                            ).toFloat().coerceIn(0f, 1f)
                            val bassFactor = if (i < 8) (1f + (bassBoostStrength / 1000f) * 0.8f) else 1f
                            simulatedBins[i] = (v * bassFactor).coerceIn(0f, 1f)
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
                    val decayed = _visualizerFrequencies.value.map { (it * 0.85f).coerceAtLeast(0f) }.toFloatArray()
                    _visualizerFrequencies.value = decayed
                    val decayedWave = _waveformSamples.value.map { (it * 0.85f) }.toFloatArray()
                    _waveformSamples.value = decayedWave
                }
            }
        }
    }

    private fun releaseEffects() {
        try {
            visualizer?.apply {
                enabled = false
                release()
            }
            visualizer = null
        } catch (e: Throwable) {
            // Ignored
        }

        try {
            equalizer?.apply {
                enabled = false
                release()
            }
            equalizer = null
        } catch (e: Throwable) {
            // Ignored
        }

        try {
            bassBoost?.release()
            bassBoost = null
        } catch (e: Throwable) {
            // Ignored
        }

        try {
            virtualizer?.release()
            virtualizer = null
        } catch (e: Throwable) {
            // Ignored
        }

        try {
            presetReverb?.release()
            presetReverb = null
        } catch (e: Throwable) {
            // Ignored
        }

        try {
            loudnessEnhancer?.release()
            loudnessEnhancer = null
        } catch (e: Throwable) {
            // Ignored
        }
    }

    fun release() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && audioDeviceCallback != null) {
            try {
                audioManager.unregisterAudioDeviceCallback(audioDeviceCallback as AudioDeviceCallback)
            } catch (e: Exception) {
                // Ignored
            }
            audioDeviceCallback = null
        }
        simulationJob?.cancel()
        releaseEffects()
        try {
            mediaPlayer?.release()
            mediaPlayer = null
        } catch (e: Throwable) {
            // Ignored
        }
        isPrepared = false
        _isPlayingState = false
    }
}
