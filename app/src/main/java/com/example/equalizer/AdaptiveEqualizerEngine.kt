package com.example.equalizer

import android.content.Context
import android.media.audiofx.Equalizer
import android.media.audiofx.LoudnessEnhancer
import android.os.Build
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.math.log10
import kotlin.math.pow

class AdaptiveEqualizerEngine private constructor(
    private val context: Context
) {
    companion object {
        @Volatile
        private var instance: AdaptiveEqualizerEngine? = null

        fun getInstance(context: Context): AdaptiveEqualizerEngine {
            return instance ?: synchronized(this) {
                instance ?: AdaptiveEqualizerEngine(context.applicationContext).also { instance = it }
            }
        }
    }
    private val tag = "AdaptiveEqualizerEngine"
    private val prefs = EqualizerPreferences(context)
    private val scope = CoroutineScope(Dispatchers.Default + Job())

    private val _settings = MutableStateFlow(prefs.loadSettings())
    val settings: StateFlow<AdaptiveEqualizerSettings> = _settings.asStateFlow()

    // 15 Biquad filters for high-precision parametric response modeling
    private val filters: Array<BiquadFilter> = Array(15) { i ->
        val freq = DynamicSoundMapper.ISO_15_FREQUENCIES[i].toFloat()
        val q = _settings.value.macroProfile.qFactor
        BiquadFilter(centerFrequency = freq, gainDb = 0f, qFactor = q)
    }

    // Pre-calculated logarithmic evaluation frequencies for the response graph (20 Hz to 20,000 Hz)
    val graphFrequencies: FloatArray = run {
        val points = 80
        val minLog = log10(20.0)
        val maxLog = log10(20000.0)
        FloatArray(points) { i ->
            val fraction = i.toDouble() / (points - 1)
            10.0.pow(minLog + fraction * (maxLog - minLog)).toFloat()
        }
    }

    // Live frequency response curve in dB across the graph frequencies
    private val _frequencyResponseCurve = MutableStateFlow(FloatArray(graphFrequencies.size) { 0f })
    val frequencyResponseCurve: StateFlow<FloatArray> = _frequencyResponseCurve.asStateFlow()

    // Hardware Audio Effects attached to current audioSessionId
    private var hardwareEqualizer: Equalizer? = null
    private var loudnessEnhancer: LoudnessEnhancer? = null
    private var currentSessionId: Int = 0
    private var externalBypass: Boolean = false
    private var useSoftwareDsp: Boolean = true

    private val _isHardwareEqSupported = MutableStateFlow(true)
    val isHardwareEqSupported: StateFlow<Boolean> = _isHardwareEqSupported.asStateFlow()

    init {
        applySettings(_settings.value, persist = false)
    }

    fun attachToAudioSession(sessionId: Int, softwareDspHandled: Boolean = false) {
        useSoftwareDsp = softwareDspHandled
        if (softwareDspHandled) {
            releaseHardwareEffects()
            this.currentSessionId = sessionId
            _isHardwareEqSupported.value = true
            return
        }
        if (sessionId == 0) return
        this.currentSessionId = sessionId
        try {
            releaseHardwareEffects()

            hardwareEqualizer = Equalizer(0, sessionId).apply {
                enabled = _settings.value.isEnabled && !externalBypass
            }
            _isHardwareEqSupported.value = true

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                loudnessEnhancer = LoudnessEnhancer(sessionId).apply {
                    val boostMb = (_settings.value.volumeBoostDb * 100f).toInt()
                    setTargetGain(boostMb)
                    enabled = _settings.value.isEnabled && !externalBypass && boostMb > 0
                }
            }

            syncToHardware()
        } catch (e: Throwable) {
            _isHardwareEqSupported.value = false
            Log.w(tag, "Failed to initialize hardware equalizer: ${e.message}")
        }
    }

    fun setExternalBypass(bypass: Boolean) {
        if (externalBypass == bypass) return
        externalBypass = bypass
        applySettings(_settings.value, persist = false)
    }

    fun releaseHardwareEffects() {
        try {
            hardwareEqualizer?.release()
            hardwareEqualizer = null
            loudnessEnhancer?.release()
            loudnessEnhancer = null
        } catch (e: Throwable) {
            Log.w(tag, "Error releasing hardware effects: ${e.message}")
        }
    }

    fun setMasterEnabled(enabled: Boolean) {
        val current = _settings.value
        if (current.isEnabled == enabled) return
        val updated = current.copy(isEnabled = enabled)
        applySettings(updated)
    }

    fun setMode(mode: EqualizerMode) {
        val current = _settings.value
        if (current.mode == mode) return
        val updated = current.copy(mode = mode)
        applySettings(updated)
    }

    fun setDynamicPad(tilt: Float, contour: Float) {
        val current = _settings.value
        val clampedTilt = (Math.round(tilt.coerceIn(-1.0f, 1.0f) * 100f) / 100f)
        val clampedContour = (Math.round(contour.coerceIn(-1.0f, 1.0f) * 100f) / 100f)
        val updated = current.copy(
            tilt = clampedTilt,
            contour = clampedContour,
            selectedProfileId = "custom"
        )
        applySettings(updated)
    }

    fun setMacroProfile(macroProfile: MacroProfile) {
        val current = _settings.value
        if (current.macroProfile == macroProfile) return
        val updated = current.copy(macroProfile = macroProfile)
        applySettings(updated)
    }

    fun setManualBandGain(index: Int, gainDb: Float) {
        if (index !in 0 until 15) return
        val current = _settings.value
        val clampedGain = (Math.round(gainDb.coerceIn(-12f, 12f) * 10f) / 10f)
        val updatedBands = current.manualBands.copyOf()
        updatedBands[index] = clampedGain
        val updated = current.copy(
            manualBands = updatedBands,
            selectedProfileId = "custom"
        )
        applySettings(updated)
    }

    fun applyCustomPresetGains(gains: FloatArray, profileId: String, volumeBoostDb: Float? = null) {
        val current = _settings.value
        val sized = FloatArray(15) { i -> gains.getOrNull(i)?.coerceIn(-12f, 12f) ?: 0f }
        val updated = current.copy(
            mode = EqualizerMode.MANUAL,
            manualBands = sized,
            selectedProfileId = profileId,
            volumeBoostDb = volumeBoostDb?.coerceIn(0f, 10f) ?: current.volumeBoostDb
        )
        applySettings(updated)
    }

    /**
     * Studio Bass/Treble shelf macro adjusting sub/bass (25-100Hz) or presence/air (6.3k-16kHz).
     */
    fun applyStudioShelf(isBass: Boolean, levelDb: Float) {
        val current = _settings.value
        val base = current.getEffectiveGains().copyOf()
        val clamped = levelDb.coerceIn(-12f, 12f)
        if (isBass) {
            base[0] = clamped
            base[1] = clamped * 0.9f
            base[2] = clamped * 0.72f
            base[3] = clamped * 0.45f
        } else {
            base[11] = clamped * 0.45f
            base[12] = clamped * 0.72f
            base[13] = clamped * 0.9f
            base[14] = clamped
        }
        val updated = current.copy(
            mode = EqualizerMode.MANUAL,
            manualBands = base,
            selectedProfileId = "custom"
        )
        applySettings(updated)
    }

    fun setVolumeBoost(boostDb: Float) {
        val current = _settings.value
        val clampedBoost = (Math.round(boostDb.coerceIn(0f, 10f) * 10f) / 10f)
        if (current.volumeBoostDb == clampedBoost) return
        val updated = current.copy(volumeBoostDb = clampedBoost)
        applySettings(updated)
    }

    fun selectSoundProfile(profileId: String) {
        val profile = EqualizerPresets.getById(profileId)
        val current = _settings.value
        val effectiveGains = profile.manualGains ?: DynamicSoundMapper.calculateGains(profile.tilt, profile.contour, profile.macroProfile)

        val updated = current.copy(
            tilt = profile.tilt,
            contour = profile.contour,
            macroProfile = profile.macroProfile,
            selectedProfileId = profile.id,
            manualBands = effectiveGains
        )
        applySettings(updated)
    }

    fun resetToNeutral() {
        val updated = AdaptiveEqualizerSettings(
            isEnabled = _settings.value.isEnabled,
            mode = _settings.value.mode,
            tilt = 0f,
            contour = 0f,
            macroProfile = MacroProfile.BROAD,
            selectedProfileId = "balanced",
            manualBands = FloatArray(15) { 0f },
            volumeBoostDb = 0f
        )
        applySettings(updated)
    }

    private fun applySettings(newSettings: AdaptiveEqualizerSettings, persist: Boolean = true) {
        _settings.value = newSettings

        val active = newSettings.isEnabled && !externalBypass
        val effectiveGains = newSettings.getEffectiveGains()
        val q = newSettings.macroProfile.qFactor

        // Update 15 biquad filters
        for (i in 0 until 15) {
            val gain = if (active) effectiveGains[i] else 0f
            filters[i].update(gainDb = gain, qFactor = q)
        }

        // Recalculate combined frequency response curve for live graph rendering
        val response = FloatArray(graphFrequencies.size)
        if (active) {
            for (pt in graphFrequencies.indices) {
                val f = graphFrequencies[pt]
                var totalDb = 0f
                for (b in 0 until 15) {
                    totalDb += filters[b].magnitudeResponseDbAt(f)
                }
                response[pt] = totalDb.coerceIn(-18f, 18f)
            }
        }
        _frequencyResponseCurve.value = response

        // Synchronize to platform hardware audio effects
        syncToHardware()

        if (persist) {
            scope.launch {
                prefs.saveSettings(newSettings)
            }
        }
    }

    private fun syncToHardware() {
        if (useSoftwareDsp) return
        val settings = _settings.value
        val active = settings.isEnabled && !externalBypass

        // 1. Hardware Equalizer
        hardwareEqualizer?.let { eq ->
            try {
                eq.enabled = active
                if (active) {
                    val numBands = eq.numberOfBands.toInt()
                    val bandRange = eq.bandLevelRange // e.g. [-1500, 1500] in millibels
                    val minMb = bandRange.getOrNull(0)?.toFloat() ?: -1200f
                    val maxMb = bandRange.getOrNull(1)?.toFloat() ?: 1200f

                    val effectiveGains = settings.getEffectiveGains()

                    for (bandIdx in 0 until numBands) {
                        val centerFreqHz = eq.getCenterFreq(bandIdx.toShort()) / 1000f // in Hz
                        val targetGainDb = interpolateGainAt(centerFreqHz, effectiveGains)
                        val targetGainMb = (targetGainDb * 100f).coerceIn(minMb, maxMb).toInt().toShort()
                        eq.setBandLevel(bandIdx.toShort(), targetGainMb)
                    }
                }
            } catch (e: Throwable) {
                Log.w(tag, "Error setting hardware EQ levels: ${e.message}")
            }
        }

        // 2. Hardware LoudnessEnhancer for Volume Boost (0 dB to +10 dB)
        loudnessEnhancer?.let { le ->
            try {
                val boostMb = (settings.volumeBoostDb * 100f).toInt()
                le.setTargetGain(boostMb)
                le.enabled = active && boostMb > 0
            } catch (e: Throwable) {
                Log.w(tag, "Error setting loudness enhancer boost: ${e.message}")
            }
        }
    }

    /**
     * Logarithmically interpolates gain between the 15 ISO frequencies at an arbitrary frequency [f].
     */
    private fun interpolateGainAt(f: Float, gains: FloatArray): Float {
        val freqs = DynamicSoundMapper.ISO_15_FREQUENCIES
        if (f <= freqs[0]) return gains[0]
        if (f >= freqs.last()) return gains.last()

        for (i in 0 until freqs.size - 1) {
            val f1 = freqs[i].toFloat()
            val f2 = freqs[i + 1].toFloat()
            if (f in f1..f2) {
                val logF = log10(f.toDouble())
                val logF1 = log10(f1.toDouble())
                val logF2 = log10(f2.toDouble())
                val fraction = ((logF - logF1) / (logF2 - logF1)).toFloat().coerceIn(0f, 1f)
                return gains[i] + fraction * (gains[i + 1] - gains[i])
            }
        }
        return 0f
    }
}
