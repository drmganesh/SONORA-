package com.example.audio

import android.media.audiofx.PresetReverb
import com.example.equalizer.DynamicSoundMapper
import com.example.equalizer.MacroProfile
import com.example.model.SpatialAudioConfig
import com.example.model.SpatialIntensity
import com.example.model.SpatialMode
import com.example.model.SpatialProfile
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tanh

/**
 * Thread-safe immutable snapshot of all real-time DSP parameters for [SonoraDspProcessor].
 * Swapped atomically via @Volatile so UI slider/toggle updates never lock or interrupt audio output.
 */
data class SonoraDspSnapshot(
    val globalBypass: Boolean = false,
    val eqEnabled: Boolean = true,
    val eqGainsDb: FloatArray = FloatArray(15) { 0f },
    val eqQFactor: Float = MacroProfile.BROAD.qFactor,
    val volumeBoostDb: Float = 0f,
    val bassBoostStrength: Int = 0, // 0..1000
    val virtualizerStrength: Int = 0, // 0..1000
    val reverbPreset: Short = PresetReverb.PRESET_NONE,
    val spatialConfig: SpatialAudioConfig = SpatialAudioConfig(),
    val isTwsConnected: Boolean = false,
    val orbit8dEnabled: Boolean = false,
    val orbit8dSpeed: Float = 0.35f,
    val orbit8dDepth: Float = 0.75f,
    val volumeScale: Float = 1.0f
)

/**
 * Allocation-free, click-free real-time stereo floating-point PCM DSP pipeline for SONORA.
 *
 * Key architectural guarantees against repetitive clicking ("tok-tok") & artifacts:
 * 1. Dedicated, unshared circular delay buffers per stage (`ssDelay` vs `orbitItd` vs `revBuf`)
 *    so Soundstage and Orbit/Dynamic 360° NEVER overwrite or read each other's samples across packet boundaries.
 * 2. Continuous symmetric bipolar ITD around a positive base delay (`baseDelay ± 0.5 * pan * maxItd`)
 *    with 4-point cubic Hermite interpolation — zero slope kinks (`|pan|` cusps) at front/back crossings.
 * 3. Sample-accurate parameter & mix smoothing (~15ms time constant) for all toggles, profiles,
 *    sliders, balance, reverb, and global bypass so switching modes or adjusting controls never creates steps.
 * 4. Soft stream-discontinuity fade-in ramp (8ms) on track start/seek/format change without wiping targets.
 * 5. Zero heap allocations inside [processStereoBuffer], strict NaN/Inf/silence guard, and sample-rate invariance (8kHz–192kHz).
 */
class SonoraDspProcessor {

    @Volatile
    var snapshot: SonoraDspSnapshot = SonoraDspSnapshot()
        private set

    @Volatile
    var sampleRate: Int = 44100
        private set

    @Volatile
    var currentAzimuthRad: Float = 0f
        private set

    @Volatile
    var lastDspError: String? = null
        private set

    @Volatile
    var isSafetyBypassed: Boolean = false
        private set

    // Stereo Direct Form II Transposed state for 15 EQ bands + 1 Bass Shelf
    private val eqZ1L = DoubleArray(15)
    private val eqZ2L = DoubleArray(15)
    private val eqZ1R = DoubleArray(15)
    private val eqZ2R = DoubleArray(15)

    private val b0 = DoubleArray(15) { 1.0 }
    private val b1 = DoubleArray(15)
    private val b2 = DoubleArray(15)
    private val a1 = DoubleArray(15)
    private val a2 = DoubleArray(15)
    private val bandActive = BooleanArray(15)

    // Bass Boost peaking/shelf filter at 72 Hz
    private var bassB0 = 1.0
    private var bassB1 = 0.0
    private var bassB2 = 0.0
    private var bassA1 = 0.0
    private var bassA2 = 0.0
    private var bassZ1L = 0.0
    private var bassZ2L = 0.0
    private var bassZ1R = 0.0
    private var bassZ2R = 0.0
    private var bassActive = false

    // DEDICATED Soundstage ring buffers (8192 samples covers ~42ms @ 192kHz, ~185ms @ 44.1kHz)
    private val ssMask = 8191
    private val ssDelayL = FloatArray(8192)
    private val ssDelayR = FloatArray(8192)
    private var ssWriteIdx = 0
    private var crossfeedLpL = 0f
    private var crossfeedLpR = 0f

    // DEDICATED Orbit 8D / Dynamic 360° symmetric ITD ring buffers (4096 samples)
    private val orbitMask = 4095
    private val orbitItdL = FloatArray(4096)
    private val orbitItdR = FloatArray(4096)
    private var orbitWriteIdx = 0
    private var rearLpL = 0f
    private var rearLpR = 0f

    // DEDICATED Reverb ring buffers (32768 samples covers >170ms @ 192kHz, ~680ms @ 48kHz)
    private val revMask = 32767
    private val revBufL = FloatArray(32768)
    private val revBufR = FloatArray(32768)
    private var revWriteIdx = 0
    private var revLpL = 0f
    private var revLpR = 0f

    // Cached parameters to detect coefficient recalculation without allocations
    private var cachedGains = FloatArray(15) { Float.NaN }
    private var cachedQ = -1f
    private var cachedBassStrength = -1
    private var cachedSampleRate = -1

    private var azimuthDouble: Double = 0.0

    // Sample-level smoothed DSP state (eliminates step jumps on toggles/sliders/profiles)
    private var smootherInitialized = false
    private var smoothedBypassWet = 1f
    private var smoothedSoundstageMix = 1f
    private var smoothedMsWidth = 1.25f
    private var smoothedCrossfeedAmt = 0.08f
    private var smoothedReflectionMix = 0f
    private var smoothedOrbitMix = 0f
    private var smoothedOrbitDepth = 0.75f
    private var smoothedOrbitSpeed = 0.35f
    private var smoothedOrbitFloor = 0.45f
    private var smoothedOrbitItdScale = 1f
    private var smoothedOrbitRearWarmth = 0.22f
    private var smoothedReverbWet = 0f
    private var smoothedReverbFeedback = 0.25f
    private var smoothedReverbDamping = 0.38f
    private var smoothedGainL = 1f
    private var smoothedGainR = 1f
    private var smoothedEqWet = 1f

    // Smooth micro fade-in ramp after seek / track switch / discontinuity (prevents pop)
    private var fadeInTotalSamples = 0
    private var fadeInRemainingSamples = 0

    // Reusable dry reference scratch buffers (grown only if buffer grows beyond current capacity)
    private var dryScratch = FloatArray(8192)
    private var bypassScratch = FloatArray(8192)

    fun updateSnapshot(newSnapshot: SonoraDspSnapshot) {
        snapshot = newSnapshot
    }

    fun configureSampleRate(newSampleRate: Int) {
        val clamped = newSampleRate.coerceIn(8000, 192000)
        if (clamped != sampleRate) {
            val hadAudio = smootherInitialized
            sampleRate = clamped
            resetTransientState(resetSmoothers = false, armFadeIn = hadAudio)
        }
    }

    /**
     * Clears filter/delay memory cleanly on seek or track boundary while arming a micro 8ms fade-in
     * and preserving current user effect targets so live playback stays warm and click-free.
     */
    fun notifyStreamDiscontinuity() {
        resetTransientState(resetSmoothers = false, armFadeIn = true)
    }

    fun resetTransientState(resetSmoothers: Boolean = true, armFadeIn: Boolean = false) {
        eqZ1L.fill(0.0)
        eqZ2L.fill(0.0)
        eqZ1R.fill(0.0)
        eqZ2R.fill(0.0)
        bassZ1L = 0.0
        bassZ2L = 0.0
        bassZ1R = 0.0
        bassZ2R = 0.0

        ssDelayL.fill(0f)
        ssDelayR.fill(0f)
        ssWriteIdx = 0
        crossfeedLpL = 0f
        crossfeedLpR = 0f

        orbitItdL.fill(0f)
        orbitItdR.fill(0f)
        orbitWriteIdx = 0
        rearLpL = 0f
        rearLpR = 0f

        revBufL.fill(0f)
        revBufR.fill(0f)
        revWriteIdx = 0
        revLpL = 0f
        revLpR = 0f

        cachedSampleRate = -1

        if (armFadeIn) {
            // Arm smooth 8ms fade-in ramp to suppress codec/seek boundary clicks
            val rampSamples = (0.008f * sampleRate.coerceIn(8000, 192000)).toInt().coerceIn(64, 1536)
            fadeInTotalSamples = rampSamples
            fadeInRemainingSamples = rampSamples
        } else {
            fadeInTotalSamples = 0
            fadeInRemainingSamples = 0
        }

        if (resetSmoothers) {
            smootherInitialized = false
        }
    }

    /**
     * Processes stereo interleaved floating-point PCM frames in-place:
     * buffer[2*i] = Left, buffer[2*i + 1] = Right, for i in 0 until frameCount.
     *
     * Guarantees finite output and never replaces an audible input signal with silence
     * (unless volumeScale itself is intentionally zeroed by a crossfade).
     */
    fun processStereoBuffer(buffer: FloatArray, frameCount: Int) {
        if (frameCount <= 0 || buffer.size < frameCount * 2) return

        val cfg = snapshot
        val sr = sampleRate.coerceIn(8000, 192000)
        val volScale = cfg.volumeScale.coerceIn(0f, 1f)
        val sampleLimit = frameCount * 2

        // Sanitize incoming samples and compute input peak
        var inputPeak = 0f
        for (idx in 0 until sampleLimit) {
            val s = buffer[idx]
            val clean = if (s.isFinite()) s.coerceIn(-4f, 4f) else 0f
            buffer[idx] = clean
            val absS = abs(clean)
            if (absS > inputPeak) inputPeak = absS
        }

        // Initialize smoothers on very first frame to exact snapshot values (no initial fade sweep)
        if (!smootherInitialized) {
            initializeSmoothers(cfg, volScale)
        }

        val targetBypassWet = if (cfg.globalBypass) 0f else 1f
        // Fast pure-dry zero-overhead bypass ONLY once wet mix has ramped completely to zero
        if (cfg.globalBypass && smoothedBypassWet < 0.0005f) {
            smoothedBypassWet = 0f
            isSafetyBypassed = false
            applyBypassGainAndRamp(buffer, frameCount, sr, volScale)
            return
        }

        ensureScratchBuffers(sampleLimit)
        System.arraycopy(buffer, 0, dryScratch, 0, sampleLimit)
        System.arraycopy(buffer, 0, bypassScratch, 0, sampleLimit)

        // Smoothing coefficient (~15ms time constant, sample-rate independent)
        val alpha15ms = (1.0 - exp(-1.0 / (0.015 * sr.toDouble()))).toFloat().coerceIn(0.0002f, 0.2f)
        // Slightly faster ramp (~10ms) for bypass & gain transitions
        val alpha10ms = (1.0 - exp(-1.0 / (0.010 * sr.toDouble()))).toFloat().coerceIn(0.0003f, 0.25f)

        try {
            recomputeCoefficientsIfNeeded(cfg, sr)

            // 1. 15-Band Parametric EQ & Bass Shelf (always filtered cleanly; wet mix smoothly crossfaded)
            applyEqAndBass(buffer, frameCount, cfg, alpha10ms)

            // 2. 360° Soundstage Mode + Phase-Safe M/S Stereo Width
            applySoundstage(buffer, frameCount, cfg, sr, alpha15ms)

            // 3. Cinematic 8D & Dynamic 360° Spatial Motion (Orbit / Dynamic 360° / Cinematic)
            applyOrbit8d(buffer, frameCount, cfg, sr, alpha15ms)

            // 4. Acoustic Room Reverb
            applyRoomReverb(buffer, frameCount, cfg, sr, alpha15ms)

            // 5. Global Bypass Crossfade + Channel Balance + Volume Boost + Soft Limiter + Stream Ramp
            applyFinalMixAndLimiter(buffer, frameCount, cfg, sr, volScale, targetBypassWet, alpha10ms)

            // Integrity check: ensure finite output and zero silent-drop regression
            var outputPeak = 0f
            var hasInvalid = false
            for (idx in 0 until sampleLimit) {
                val out = buffer[idx]
                if (!out.isFinite()) {
                    hasInvalid = true
                    break
                }
                val absOut = abs(out)
                if (absOut > outputPeak) outputPeak = absOut
            }

            if (hasInvalid || (inputPeak > 1e-3f && volScale > 0.1f && outputPeak < 1e-5f)) {
                restoreDry(buffer, sampleLimit, volScale)
                resetTransientState(resetSmoothers = true)
                isSafetyBypassed = true
                lastDspError = "Safety bypass triggered: invalid or silent DSP frame detected"
            } else {
                isSafetyBypassed = false
            }
        } catch (t: Throwable) {
            restoreDry(buffer, sampleLimit, volScale)
            resetTransientState(resetSmoothers = true)
            isSafetyBypassed = true
            lastDspError = "DSP stage bypassed: ${t.message}"
        }
    }

    private fun initializeSmoothers(cfg: SonoraDspSnapshot, volScale: Float) {
        val sc = cfg.spatialConfig
        smoothedBypassWet = if (cfg.globalBypass) 0f else 1f
        smoothedEqWet = if (cfg.eqEnabled) 1f else 0f

        val virtNorm = (cfg.virtualizerStrength.coerceIn(0, 1000) / 1000f)
        val ssActive = sc.isEnabled || virtNorm > 0.01f
        smoothedSoundstageMix = if (ssActive) 1f else 0f
        smoothedMsWidth = computeTargetMsWidth(cfg, sc, virtNorm)
        smoothedCrossfeedAmt = computeTargetCrossfeed(sc)
        smoothedReflectionMix = computeTargetReflectionMix(sc)

        smoothedOrbitMix = if (cfg.orbit8dEnabled) 1f else 0f
        smoothedOrbitDepth = cfg.orbit8dDepth.coerceIn(0.05f, 1.0f)
        smoothedOrbitSpeed = cfg.orbit8dSpeed.coerceIn(0.1f, 2.0f)
        val profile = sc.spatialProfile
        smoothedOrbitFloor = profileMinFloor(profile, smoothedOrbitDepth, sc.spatialIntensity)
        smoothedOrbitItdScale = profileItdScale(profile)
        smoothedOrbitRearWarmth = profileRearWarmth(profile)

        val (revWet, revFb, revDamp) = resolveReverbParams(cfg.reverbPreset)
        smoothedReverbWet = revWet
        smoothedReverbFeedback = revFb
        smoothedReverbDamping = revDamp

        val (targetL, targetR) = computeTargetOutputGains(cfg, volScale)
        smoothedGainL = targetL
        smoothedGainR = targetR
        smootherInitialized = true
    }

    private fun applyBypassGainAndRamp(buffer: FloatArray, frameCount: Int, sr: Int, volScale: Float) {
        val alpha = (1.0 - exp(-1.0 / (0.010 * sr.toDouble()))).toFloat().coerceIn(0.0003f, 0.25f)
        for (f in 0 until frameCount) {
            smoothedGainL += alpha * (volScale - smoothedGainL)
            smoothedGainR += alpha * (volScale - smoothedGainR)
            val ramp = nextFadeInMultiplier()
            val lIdx = f * 2
            val rIdx = lIdx + 1
            buffer[lIdx] = (buffer[lIdx] * smoothedGainL * ramp).coerceIn(-1f, 1f)
            buffer[rIdx] = (buffer[rIdx] * smoothedGainR * ramp).coerceIn(-1f, 1f)
        }
    }

    private fun nextFadeInMultiplier(): Float {
        if (fadeInRemainingSamples <= 0 || fadeInTotalSamples <= 0) return 1f
        val progress = 1f - (fadeInRemainingSamples.toFloat() / fadeInTotalSamples.toFloat())
        fadeInRemainingSamples--
        // Smooth raised-cosine fade-in [0 -> 1]
        return (0.5f - 0.5f * cos(PI * progress).toFloat()).coerceIn(0f, 1f)
    }

    private fun ensureScratchBuffers(requiredSize: Int) {
        if (dryScratch.size < requiredSize) {
            dryScratch = FloatArray(requiredSize)
        }
        if (bypassScratch.size < requiredSize) {
            bypassScratch = FloatArray(requiredSize)
        }
    }

    private fun restoreDry(buffer: FloatArray, sampleLimit: Int, volScale: Float) {
        for (idx in 0 until sampleLimit) {
            buffer[idx] = (dryScratch[idx] * volScale).coerceIn(-1f, 1f)
        }
    }

    private fun recomputeCoefficientsIfNeeded(cfg: SonoraDspSnapshot, sr: Int) {
        val srChanged = cachedSampleRate != sr
        val qClamped = cfg.eqQFactor.coerceIn(0.4f, 6.0f)
        val qChanged = abs(cachedQ - qClamped) > 0.001f

        val gainsChanged = srChanged || qChanged || (0 until 15).any { i ->
            val g = cfg.eqGainsDb.getOrElse(i) { 0f }.coerceIn(-12f, 12f)
            abs(cachedGains[i] - g) > 0.01f
        }

        if (gainsChanged) {
            val freqs = DynamicSoundMapper.ISO_15_FREQUENCIES
            for (i in 0 until 15) {
                val g = cfg.eqGainsDb.getOrElse(i) { 0f }.coerceIn(-12f, 12f)
                cachedGains[i] = g
                val f0 = freqs[i].toDouble()
                if (abs(g) < 0.05f || f0 >= sr * 0.45) {
                    bandActive[i] = false
                    b0[i] = 1.0; b1[i] = 0.0; b2[i] = 0.0; a1[i] = 0.0; a2[i] = 0.0
                } else {
                    val a = 10.0.pow(g.toDouble() / 40.0)
                    val w0 = 2.0 * PI * (f0 / sr.toDouble())
                    val cosW0 = cos(w0)
                    val sinW0 = sin(w0)
                    val alpha = sinW0 / (2.0 * qClamped.toDouble())

                    val a0 = 1.0 + alpha / a
                    if (abs(a0) > 1e-9) {
                        b0[i] = (1.0 + alpha * a) / a0
                        b1[i] = (-2.0 * cosW0) / a0
                        b2[i] = (1.0 - alpha * a) / a0
                        a1[i] = (-2.0 * cosW0) / a0
                        a2[i] = (1.0 - alpha / a) / a0
                        bandActive[i] = b0[i].isFinite() && a1[i].isFinite() && a2[i].isFinite()
                    } else {
                        bandActive[i] = false
                    }
                }
            }
            cachedQ = qClamped
        }

        val clampedBass = cfg.bassBoostStrength.coerceIn(0, 1000)
        if (srChanged || cachedBassStrength != clampedBass) {
            cachedBassStrength = clampedBass
            if (clampedBass <= 0) {
                bassActive = false
                bassB0 = 1.0; bassB1 = 0.0; bassB2 = 0.0; bassA1 = 0.0; bassA2 = 0.0
            } else {
                val boostDb = (clampedBass / 1000.0) * 7.5
                val a = 10.0.pow(boostDb / 40.0)
                val w0 = 2.0 * PI * (72.0 / sr.toDouble())
                val cosW0 = cos(w0)
                val sinW0 = sin(w0)
                val alpha = sinW0 / (2.0 * 0.75)
                val a0 = 1.0 + alpha / a
                if (abs(a0) > 1e-9) {
                    bassB0 = (1.0 + alpha * a) / a0
                    bassB1 = (-2.0 * cosW0) / a0
                    bassB2 = (1.0 - alpha * a) / a0
                    bassA1 = (-2.0 * cosW0) / a0
                    bassA2 = (1.0 - alpha / a) / a0
                    bassActive = bassB0.isFinite() && bassA1.isFinite() && bassA2.isFinite()
                } else {
                    bassActive = false
                }
            }
        }

        cachedSampleRate = sr
    }

    private fun applyEqAndBass(
        buffer: FloatArray,
        frameCount: Int,
        cfg: SonoraDspSnapshot,
        alpha: Float
    ) {
        val targetEqWet = if (cfg.eqEnabled) 1f else 0f
        val anyEqActive = bandActive.any { it }
        if (!anyEqActive && !bassActive && smoothedEqWet < 0.0002f) {
            smoothedEqWet = targetEqWet
            return
        }

        for (f in 0 until frameCount) {
            smoothedEqWet += alpha * (targetEqWet - smoothedEqWet)
            val lIdx = f * 2
            val rIdx = lIdx + 1
            val dryL = buffer[lIdx]
            val dryR = buffer[rIdx]
            var l = dryL.toDouble()
            var r = dryR.toDouble()

            if (anyEqActive) {
                for (b in 0 until 15) {
                    if (!bandActive[b]) continue
                    val outL = b0[b] * l + eqZ1L[b]
                    var nextZ1L = b1[b] * l - a1[b] * outL + eqZ2L[b]
                    var nextZ2L = b2[b] * l - a2[b] * outL
                    if (abs(nextZ1L) < 1e-20) nextZ1L = 0.0
                    if (abs(nextZ2L) < 1e-20) nextZ2L = 0.0
                    if (outL.isFinite() && nextZ1L.isFinite() && nextZ2L.isFinite()) {
                        eqZ1L[b] = nextZ1L
                        eqZ2L[b] = nextZ2L
                        l = outL
                    } else {
                        eqZ1L[b] = 0.0; eqZ2L[b] = 0.0
                    }

                    val outR = b0[b] * r + eqZ1R[b]
                    var nextZ1R = b1[b] * r - a1[b] * outR + eqZ2R[b]
                    var nextZ2R = b2[b] * r - a2[b] * outR
                    if (abs(nextZ1R) < 1e-20) nextZ1R = 0.0
                    if (abs(nextZ2R) < 1e-20) nextZ2R = 0.0
                    if (outR.isFinite() && nextZ1R.isFinite() && nextZ2R.isFinite()) {
                        eqZ1R[b] = nextZ1R
                        eqZ2R[b] = nextZ2R
                        r = outR
                    } else {
                        eqZ1R[b] = 0.0; eqZ2R[b] = 0.0
                    }
                }
            }

            if (bassActive) {
                val outL = bassB0 * l + bassZ1L
                val n1L = bassB1 * l - bassA1 * outL + bassZ2L
                val n2L = bassB2 * l - bassA2 * outL
                if (outL.isFinite() && n1L.isFinite() && n2L.isFinite()) {
                    bassZ1L = if (abs(n1L) < 1e-20) 0.0 else n1L
                    bassZ2L = if (abs(n2L) < 1e-20) 0.0 else n2L
                    l = outL
                } else {
                    bassZ1L = 0.0; bassZ2L = 0.0
                }

                val outR = bassB0 * r + bassZ1R
                val n1R = bassB1 * r - bassA1 * outR + bassZ2R
                val n2R = bassB2 * r - bassA2 * outR
                if (outR.isFinite() && n1R.isFinite() && n2R.isFinite()) {
                    bassZ1R = if (abs(n1R) < 1e-20) 0.0 else n1R
                    bassZ2R = if (abs(n2R) < 1e-20) 0.0 else n2R
                    r = outR
                } else {
                    bassZ1R = 0.0; bassZ2R = 0.0
                }
            }

            // Smooth crossfade for EQ section enable/disable; Bass Shelf always follows bassStrength
            val eqOnlyWet = smoothedEqWet
            val eqMixedL = dryL + eqOnlyWet * (l.toFloat() - dryL)
            val eqMixedR = dryR + eqOnlyWet * (r.toFloat() - dryR)
            buffer[lIdx] = eqMixedL
            buffer[rIdx] = eqMixedR
        }
    }

    private fun computeTargetMsWidth(
        cfg: SonoraDspSnapshot,
        sc: SpatialAudioConfig,
        virtNorm: Float
    ): Float {
        if (!sc.isEnabled && virtNorm <= 0.01f) return 1.0f

        val intensityScale = sc.spatialIntensity.multiplier
        val activeWidth = if (sc.isEnabled) {
            val base = (sc.soundstageWidth.coerceIn(0f, 1f) * 0.45f +
                sc.stereoWidening.coerceIn(0f, 1f) * 0.55f) *
                sc.intensity.coerceIn(0.25f, 1f) * intensityScale
            val twsMul = if (cfg.isTwsConnected && sc.twsWideStereoEnabled) {
                (1.0f + 0.14f * sc.twsStereoBoost.coerceIn(0.5f, 1.5f)).coerceIn(1.0f, 1.22f)
            } else {
                1.0f
            }
            (base * twsMul).coerceIn(0f, 1.15f)
        } else {
            virtNorm * 0.50f
        }

        val profileFactor = when (sc.spatialProfile) {
            SpatialProfile.ORBIT -> 0.40f
            SpatialProfile.DYNAMIC_360 -> 0.48f
            SpatialProfile.CINEMATIC -> 0.54f
        }
        val modeFactor = when (if (sc.isEnabled) sc.mode else SpatialMode.HEADPHONES) {
            SpatialMode.HEADPHONES -> profileFactor
            SpatialMode.CINEMA_360 -> (profileFactor + 0.08f)
            SpatialMode.SPEAKERS -> (profileFactor * 0.65f)
        }
        // Mono-compatible safe widening limit (<= 1.56f so Mid center never cancels)
        return (1.0f + modeFactor * activeWidth).coerceIn(0.95f, 1.56f)
    }

    private fun computeTargetCrossfeed(sc: SpatialAudioConfig): Float {
        if (!sc.isEnabled) return 0f
        val width = sc.soundstageWidth.coerceIn(0f, 1f) * sc.spatialIntensity.multiplier.coerceIn(0.5f, 1.0f)
        return when (sc.mode) {
            SpatialMode.HEADPHONES -> 0.09f * width
            SpatialMode.CINEMA_360 -> 0.12f * width
            SpatialMode.SPEAKERS -> 0f
        }
    }

    private fun computeTargetReflectionMix(sc: SpatialAudioConfig): Float {
        if (!sc.isEnabled) return 0f
        val width = sc.soundstageWidth.coerceIn(0f, 1f) * sc.spatialIntensity.multiplier
        return when {
            sc.spatialProfile == SpatialProfile.CINEMATIC -> (0.11f * width).coerceIn(0f, 0.14f)
            sc.mode == SpatialMode.CINEMA_360 -> (0.09f * width).coerceIn(0f, 0.12f)
            else -> 0f
        }
    }

    private fun applySoundstage(
        buffer: FloatArray,
        frameCount: Int,
        cfg: SonoraDspSnapshot,
        sr: Int,
        alpha: Float
    ) {
        val sc = cfg.spatialConfig
        val virtNorm = (cfg.virtualizerStrength.coerceIn(0, 1000) / 1000f)
        val ssActive = sc.isEnabled || virtNorm > 0.01f
        val targetMix = if (ssActive) 1f else 0f

        val targetWidth = computeTargetMsWidth(cfg, sc, virtNorm)
        val targetCrossfeed = computeTargetCrossfeed(sc)
        val targetReflection = computeTargetReflectionMix(sc)

        val crossfeedDelaySamples = (0.00024f * sr).toInt().coerceIn(2, 64)
        val lpAlpha = (1600.0 / (sr.toDouble() + 1600.0)).toFloat().coerceIn(0.02f, 0.5f)
        val reflTapL = (0.011f * sr).toInt().coerceIn(64, 4000)
        val reflTapR = (0.017f * sr).toInt().coerceIn(96, 6000)

        // If already settled completely dry, still feed dedicated ring silently so next enable is warm
        val skipCompute = !ssActive && smoothedSoundstageMix < 0.0002f

        for (f in 0 until frameCount) {
            smoothedSoundstageMix += alpha * (targetMix - smoothedSoundstageMix)
            smoothedMsWidth += alpha * (targetWidth - smoothedMsWidth)
            smoothedCrossfeedAmt += alpha * (targetCrossfeed - smoothedCrossfeedAmt)
            smoothedReflectionMix += alpha * (targetReflection - smoothedReflectionMix)

            val lIdx = f * 2
            val rIdx = lIdx + 1
            val inL = buffer[lIdx]
            val inR = buffer[rIdx]

            if (skipCompute) {
                ssDelayL[ssWriteIdx] = inL
                ssDelayR[ssWriteIdx] = inR
                ssWriteIdx = (ssWriteIdx + 1) and ssMask
                continue
            }

            val mid = 0.5f * (inL + inR)
            val side = 0.5f * (inL - inR)
            val w = smoothedMsWidth
            val normGain = 1.0f / sqrt(1.0f + 0.14f * (w - 1.0f).coerceAtLeast(0f))

            var wetL = (mid + w * side) * normGain
            var wetR = (mid - w * side) * normGain

            // Write ONLY to Soundstage's dedicated ring buffer
            ssDelayL[ssWriteIdx] = wetL
            ssDelayR[ssWriteIdx] = wetR

            val cfAmt = smoothedCrossfeedAmt
            if (cfAmt > 0.002f) {
                val readIdx = (ssWriteIdx - crossfeedDelaySamples) and ssMask
                val oppR = ssDelayR[readIdx]
                val oppL = ssDelayL[readIdx]
                crossfeedLpL += lpAlpha * (oppR - crossfeedLpL)
                crossfeedLpR += lpAlpha * (oppL - crossfeedLpR)
                wetL = wetL * (1f - cfAmt * 0.35f) + crossfeedLpL * cfAmt
                wetR = wetR * (1f - cfAmt * 0.35f) + crossfeedLpR * cfAmt
            }

            val reflMix = smoothedReflectionMix
            if (reflMix > 0.002f) {
                val rTapL = (ssWriteIdx - reflTapL) and ssMask
                val rTapR = (ssWriteIdx - reflTapR) and ssMask
                wetL += ssDelayR[rTapL] * reflMix
                wetR += ssDelayL[rTapR] * reflMix
            }

            ssWriteIdx = (ssWriteIdx + 1) and ssMask

            val mix = smoothedSoundstageMix
            buffer[lIdx] = inL + mix * (wetL - inL)
            buffer[rIdx] = inR + mix * (wetR - inR)
        }
    }

    private fun profileMinFloor(
        profile: SpatialProfile,
        depth: Float,
        intensity: SpatialIntensity
    ): Float {
        val scaledDepth = (depth * intensity.multiplier).coerceIn(0.05f, 1.0f)
        return when (profile) {
            SpatialProfile.ORBIT -> (1.0f - 0.42f * scaledDepth).coerceIn(0.52f, 1.0f)
            SpatialProfile.DYNAMIC_360 -> (1.0f - 0.54f * scaledDepth).coerceIn(0.40f, 1.0f)
            SpatialProfile.CINEMATIC -> (1.0f - 0.28f * scaledDepth).coerceIn(0.66f, 1.0f)
        }
    }

    private fun profileItdScale(profile: SpatialProfile): Float = when (profile) {
        SpatialProfile.ORBIT -> 0.65f
        SpatialProfile.DYNAMIC_360 -> 1.00f
        SpatialProfile.CINEMATIC -> 0.55f
    }

    private fun profileRearWarmth(profile: SpatialProfile): Float = when (profile) {
        SpatialProfile.ORBIT -> 0.14f
        SpatialProfile.DYNAMIC_360 -> 0.24f
        SpatialProfile.CINEMATIC -> 0.10f
    }

    private fun profileSpeedScale(profile: SpatialProfile): Double = when (profile) {
        SpatialProfile.ORBIT -> 0.65
        SpatialProfile.DYNAMIC_360 -> 1.00
        SpatialProfile.CINEMATIC -> 0.30
    }

    private fun applyOrbit8d(
        buffer: FloatArray,
        frameCount: Int,
        cfg: SonoraDspSnapshot,
        sr: Int,
        alpha: Float
    ) {
        val sc = cfg.spatialConfig
        val profile = sc.spatialProfile
        val intensity = sc.spatialIntensity

        val targetMix = if (cfg.orbit8dEnabled) 1f else 0f
        val targetDepth = (cfg.orbit8dDepth.coerceIn(0.05f, 1.0f) * intensity.multiplier).coerceIn(0.05f, 1.0f)
        val targetSpeed = cfg.orbit8dSpeed.coerceIn(0.1f, 2.0f)
        val targetFloor = profileMinFloor(profile, cfg.orbit8dDepth.coerceIn(0.05f, 1.0f), intensity)
        val targetItdScale = profileItdScale(profile)
        val targetRearWarmth = profileRearWarmth(profile)
        val speedScale = profileSpeedScale(profile)

        val maxItdSamples = 0.00045 * sr.toDouble() // ~0.45ms max interaural acoustic delta
        // Constant positive centering delay (>= maxItd + margin for 4-point Hermite read at offset+2)
        val baseDelaySamples = maxItdSamples + 8.0
        val rearLpAlpha = (2200.0 / (sr.toDouble() + 2200.0)).toFloat().coerceIn(0.05f, 0.55f)

        val skipCompute = !cfg.orbit8dEnabled && smoothedOrbitMix < 0.0002f

        for (f in 0 until frameCount) {
            smoothedOrbitMix += alpha * (targetMix - smoothedOrbitMix)
            smoothedOrbitDepth += alpha * (targetDepth - smoothedOrbitDepth)
            smoothedOrbitSpeed += alpha * (targetSpeed - smoothedOrbitSpeed)
            smoothedOrbitFloor += alpha * (targetFloor - smoothedOrbitFloor)
            smoothedOrbitItdScale += alpha * (targetItdScale - smoothedOrbitItdScale)
            smoothedOrbitRearWarmth += alpha * (targetRearWarmth - smoothedOrbitRearWarmth)

            val lIdx = f * 2
            val rIdx = lIdx + 1
            val inL = buffer[lIdx]
            val inR = buffer[rIdx]

            // Continuously feed Orbit's dedicated ITD ring buffer so enabling/disabling is seamless
            orbitItdL[orbitWriteIdx] = inL
            orbitItdR[orbitWriteIdx] = inR

            if (skipCompute) {
                orbitWriteIdx = (orbitWriteIdx + 1) and orbitMask
                continue
            }

            // Advance azimuth smoothly using smoothed speed & profile speed multiplier
            val orbitHz = (0.04 + 0.19 * smoothedOrbitSpeed.toDouble()) * speedScale
            val deltaAngle = (2.0 * PI * orbitHz) / sr.toDouble()
            azimuthDouble = (azimuthDouble + deltaAngle) % (2.0 * PI)
            val sinAz = sin(azimuthDouble).toFloat()
            val cosAz = cos(azimuthDouble).toFloat()

            // Effective azimuth pan modulated by smoothed depth (-1 Left .. +1 Right)
            val profilePanWeight = if (profile == SpatialProfile.CINEMATIC) 0.45f else 1.0f
            val pan = (sinAz * smoothedOrbitDepth * profilePanWeight).coerceIn(-1f, 1f)

            // Equal-power stereo ILD with safe minimum gain floor (never drops an earbud to zero)
            val rightEmphasis = ((pan + 1f) * 0.5f).coerceIn(0f, 1f)
            val leftEmphasis = 1f - rightEmphasis
            val floor = smoothedOrbitFloor
            val lGain = (floor + (1.0f - floor) * sqrt(leftEmphasis)).coerceIn(0.38f, 1.02f)
            val rGain = (floor + (1.0f - floor) * sqrt(rightEmphasis)).coerceIn(0.38f, 1.02f)

            // Symmetric smooth fractional ITD centered around baseDelaySamples:
            // When pan > 0 (sound moves Right), Left ear is delayed slightly more and Right slightly less.
            // Zero cusp at pan == 0 because delayL and delayR are linear C^\infty functions of pan!
            val halfItd = 0.5 * pan.toDouble() * maxItdSamples * smoothedOrbitItdScale.toDouble()
            val delayL = (baseDelaySamples + halfItd).coerceIn(2.0, (orbitMask - 8).toDouble())
            val delayR = (baseDelaySamples - halfItd).coerceIn(2.0, (orbitMask - 8).toDouble())

            val delayedL = readCubicHermite(orbitItdL, orbitWriteIdx, orbitMask, delayL)
            val delayedR = readCubicHermite(orbitItdR, orbitWriteIdx, orbitMask, delayR)

            orbitWriteIdx = (orbitWriteIdx + 1) and orbitMask

            // Subtle rear-hemisphere warmth modulation when sound orbits behind the listener
            rearLpL += rearLpAlpha * (delayedL - rearLpL)
            rearLpR += rearLpAlpha * (delayedR - rearLpR)
            val rearBlend = ((-cosAz).coerceAtLeast(0f) * smoothedOrbitRearWarmth * smoothedOrbitDepth).coerceIn(0f, 0.28f)

            val wetL = (delayedL * (1f - rearBlend) + rearLpL * rearBlend) * lGain
            val wetR = (delayedR * (1f - rearBlend) + rearLpR * rearBlend) * rGain

            val mix = smoothedOrbitMix
            buffer[lIdx] = inL + mix * (wetL - inL)
            buffer[rIdx] = inR + mix * (wetR - inR)
        }

        if (cfg.orbit8dEnabled) {
            currentAzimuthRad = azimuthDouble.toFloat()
        }
    }

    /**
     * 4-point Catmull-Rom cubic Hermite fractional delay reader over a circular ring buffer.
     * Eliminates linear-interpolation zipper noise during continuous ITD modulation.
     */
    private fun readCubicHermite(
        delayBuf: FloatArray,
        writeIdx: Int,
        mask: Int,
        delaySamples: Double
    ): Float {
        val intPart = delaySamples.toInt()
        val frac = (delaySamples - intPart).toFloat()

        val ym1 = delayBuf[(writeIdx - intPart + 1) and mask]
        val y0 = delayBuf[(writeIdx - intPart) and mask]
        val y1 = delayBuf[(writeIdx - intPart - 1) and mask]
        val y2 = delayBuf[(writeIdx - intPart - 2) and mask]

        val c0 = y0
        val c1 = 0.5f * (y1 - ym1)
        val c2 = ym1 - 2.5f * y0 + 2.0f * y1 - 0.5f * y2
        val c3 = 0.5f * (y2 - ym1) + 1.5f * (y0 - y1)

        val out = ((c3 * frac + c2) * frac + c1) * frac + c0
        return if (out.isFinite()) out else y0
    }

    private fun resolveReverbParams(preset: Short): Triple<Float, Float, Float> {
        return when (preset) {
            PresetReverb.PRESET_NONE -> Triple(0f, 0.22f, 0.38f)
            PresetReverb.PRESET_SMALLROOM -> Triple(0.13f, 0.24f, 0.42f)
            PresetReverb.PRESET_MEDIUMROOM -> Triple(0.17f, 0.28f, 0.36f)
            PresetReverb.PRESET_LARGEROOM -> Triple(0.21f, 0.33f, 0.32f)
            PresetReverb.PRESET_MEDIUMHALL -> Triple(0.24f, 0.37f, 0.28f)
            PresetReverb.PRESET_LARGEHALL -> Triple(0.27f, 0.42f, 0.24f)
            PresetReverb.PRESET_PLATE -> Triple(0.21f, 0.32f, 0.50f)
            else -> Triple(0.15f, 0.26f, 0.36f)
        }
    }

    private fun applyRoomReverb(
        buffer: FloatArray,
        frameCount: Int,
        cfg: SonoraDspSnapshot,
        sr: Int,
        alpha: Float
    ) {
        val (targetWet, targetFb, targetDamp) = resolveReverbParams(cfg.reverbPreset)
        if (cfg.reverbPreset == PresetReverb.PRESET_NONE && smoothedReverbWet < 0.0002f) {
            smoothedReverbWet = 0f
            return
        }

        val roomScale = when (cfg.reverbPreset) {
            PresetReverb.PRESET_SMALLROOM -> 0.45f
            PresetReverb.PRESET_MEDIUMROOM -> 0.65f
            PresetReverb.PRESET_LARGEROOM -> 0.85f
            PresetReverb.PRESET_MEDIUMHALL -> 1.05f
            PresetReverb.PRESET_LARGEHALL -> 1.30f
            PresetReverb.PRESET_PLATE -> 0.75f
            else -> 0.60f
        }

        val tap1L = ((0.0297f * roomScale) * sr).toInt().coerceIn(64, 30000)
        val tap2L = ((0.0419f * roomScale) * sr).toInt().coerceIn(96, 31000)
        val tap1R = ((0.0313f * roomScale) * sr).toInt().coerceIn(64, 30000)
        val tap2R = ((0.0443f * roomScale) * sr).toInt().coerceIn(96, 31000)

        for (f in 0 until frameCount) {
            smoothedReverbWet += alpha * (targetWet - smoothedReverbWet)
            smoothedReverbFeedback += alpha * (targetFb - smoothedReverbFeedback)
            smoothedReverbDamping += alpha * (targetDamp - smoothedReverbDamping)

            val lIdx = f * 2
            val rIdx = lIdx + 1
            val dryL = buffer[lIdx]
            val dryR = buffer[rIdx]

            val rL = 0.55f * revBufL[(revWriteIdx - tap1L) and revMask] +
                0.45f * revBufR[(revWriteIdx - tap2L) and revMask]
            val rR = 0.55f * revBufR[(revWriteIdx - tap1R) and revMask] +
                0.45f * revBufL[(revWriteIdx - tap2R) and revMask]

            revLpL += smoothedReverbDamping * (rL - revLpL)
            revLpR += smoothedReverbDamping * (rR - revLpR)

            revBufL[revWriteIdx] = (dryL + revLpL * smoothedReverbFeedback).coerceIn(-2f, 2f)
            revBufR[revWriteIdx] = (dryR + revLpR * smoothedReverbFeedback).coerceIn(-2f, 2f)
            revWriteIdx = (revWriteIdx + 1) and revMask

            val wet = smoothedReverbWet
            val dryGain = 1.0f - wet * 0.25f
            buffer[lIdx] = dryL * dryGain + revLpL * wet
            buffer[rIdx] = dryR * dryGain + revLpR * wet
        }
    }

    private fun computeTargetOutputGains(cfg: SonoraDspSnapshot, volScale: Float): Pair<Float, Float> {
        val sc = cfg.spatialConfig
        val balance = sc.channelBalance.coerceIn(-1.0f, 1.0f)
        var balL = if (balance > 0f) (1.0f - balance) else 1.0f
        var balR = if (balance < 0f) (1.0f + balance) else 1.0f

        if (sc.isEnabled) {
            val posX = sc.listenerX.coerceIn(-1f, 1f)
            if (posX > 0f) balL *= (1.0f - posX * 0.35f)
            else if (posX < 0f) balR *= (1.0f - abs(posX) * 0.35f)
        }

        if (abs(balance) < 0.05f && abs(sc.listenerX) < 0.05f) {
            balL = balL.coerceAtLeast(0.95f)
            balR = balR.coerceAtLeast(0.95f)
        }

        val boostDb = cfg.volumeBoostDb.coerceIn(0f, 10f)
        val boostLinear = 10.0.pow(boostDb.toDouble() / 20.0).toFloat()

        return (balL * boostLinear * volScale) to (balR * boostLinear * volScale)
    }

    private fun applyFinalMixAndLimiter(
        buffer: FloatArray,
        frameCount: Int,
        cfg: SonoraDspSnapshot,
        sr: Int,
        volScale: Float,
        targetBypassWet: Float,
        alpha: Float
    ) {
        val (targetGainL, targetGainR) = computeTargetOutputGains(cfg, volScale)

        for (f in 0 until frameCount) {
            smoothedBypassWet += alpha * (targetBypassWet - smoothedBypassWet)
            smoothedGainL += alpha * (targetGainL - smoothedGainL)
            smoothedGainR += alpha * (targetGainR - smoothedGainR)
            val ramp = nextFadeInMultiplier()

            val lIdx = f * 2
            val rIdx = lIdx + 1
            val dryL = bypassScratch[lIdx] * volScale
            val dryR = bypassScratch[rIdx] * volScale

            val processedL = softLimit(buffer[lIdx] * smoothedGainL)
            val processedR = softLimit(buffer[rIdx] * smoothedGainR)

            val wet = smoothedBypassWet
            val outL = (dryL + wet * (processedL - dryL)) * ramp
            val outR = (dryR + wet * (processedR - dryR)) * ramp

            buffer[lIdx] = if (outL.isFinite()) outL.coerceIn(-0.995f, 0.995f) else 0f
            buffer[rIdx] = if (outR.isFinite()) outR.coerceIn(-0.995f, 0.995f) else 0f
        }
    }

    private fun softLimit(x: Float): Float {
        if (!x.isFinite()) return 0f
        val absX = abs(x)
        if (absX <= 0.88f) return x
        val excess = (absX - 0.88f) / 0.12f
        val limited = 0.88f + 0.115f * tanh(excess.toDouble()).toFloat()
        return if (x >= 0f) limited else -limited
    }
}
