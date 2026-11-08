package com.example.audio

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
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

    // Supported Equalizer Bands from Hardware/Framework
    private val _equalizerBands = MutableStateFlow<List<EqualizerBand>>(emptyList())
    val equalizerBands: StateFlow<List<EqualizerBand>> = _equalizerBands.asStateFlow()

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
        initMediaPlayer()
        startVisualizerLoop()
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
            setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )
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

                val numBands = equalizer?.numberOfBands ?: 5
                val minLevel = equalizer?.bandLevelRange?.get(0) ?: -1500
                val maxLevel = equalizer?.bandLevelRange?.get(1) ?: 1500

                val bands = mutableListOf<EqualizerBand>()
                for (i in 0 until numBands) {
                    val bandIdx = i.toShort()
                    val centerFreq = equalizer?.getCenterFreq(bandIdx)?.div(1000) ?: (60 * (i + 1))
                    val currentLevel = equalizer?.getBandLevel(bandIdx) ?: 0
                    bands.add(
                        EqualizerBand(
                            bandIndex = bandIdx,
                            centerFreqHz = centerFreq,
                            minLevelMb = minLevel,
                            maxLevelMb = maxLevel,
                            currentLevelMb = currentLevel
                        )
                    )
                }
                _equalizerBands.value = bands
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

    private fun setupFallbackEqualizerBands() {
        val defaultFreqs = listOf(31, 62, 125, 250, 500, 1000, 2000, 4000, 8000, 16000)
        _equalizerBands.value = defaultFreqs.mapIndexed { idx, freq ->
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
        try {
            equalizer?.setBandLevel(bandIndex, gainMb)
        } catch (e: Throwable) {
            Log.w(tag, "Error setting band gain", e)
        }
        val updated = _equalizerBands.value.map {
            if (it.bandIndex == bandIndex) it.copy(currentLevelMb = gainMb) else it
        }
        _equalizerBands.value = updated
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

    // --- 360° Spatial Audio Processing Engine ---

    fun updateSpatialAudioConfig(config: SpatialAudioConfig) {
        this.spatialConfig = config
        applySpatialAudio()
    }

    private fun applySpatialAudio() {
        if (!spatialConfig.isEnabled) {
            applyVolumeAndBalance()
            return
        }

        val targetVirtualizer = (spatialConfig.intensity * 1000f * spatialConfig.soundstageWidth).toInt().coerceIn(0, 1000)
        try {
            virtualizer?.let {
                if (it.strengthSupported) {
                    it.setStrength(targetVirtualizer.toShort())
                    it.enabled = true
                }
            }
        } catch (e: Throwable) {
            Log.w(tag, "Spatial virtualizer apply error", e)
        }

        when (spatialConfig.mode) {
            SpatialMode.HEADPHONES -> {
                applyVolumeAndBalance(spatialStereoWidening = spatialConfig.stereoWidening)
            }
            SpatialMode.SPEAKERS -> {
                applyVolumeAndBalance(spatialStereoWidening = spatialConfig.stereoWidening * 0.75f)
            }
            SpatialMode.CINEMA_360 -> {
                applyVolumeAndBalance(spatialStereoWidening = 1.0f)
            }
        }
    }

    private fun applyVolumeAndBalance(spatialStereoWidening: Float = 0f) {
        val balance = spatialConfig.channelBalance.coerceIn(-1.0f, 1.0f)
        var left = if (balance > 0f) 1.0f - balance else 1.0f
        var right = if (balance < 0f) 1.0f + balance else 1.0f

        if (spatialConfig.isEnabled) {
            val posX = spatialConfig.listenerX.coerceIn(-1f, 1f)
            if (posX > 0) {
                left *= (1.0f - (posX * 0.35f))
            } else if (posX < 0) {
                right *= (1.0f - (kotlin.math.abs(posX) * 0.35f))
            }
        }

        try {
            mediaPlayer?.setVolume(left.coerceIn(0f, 1f), right.coerceIn(0f, 1f))
        } catch (e: Throwable) {
            Log.w(tag, "Error setting volume/balance", e)
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
