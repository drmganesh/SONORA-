package com.example.audio

import android.media.audiofx.PresetReverb
import com.example.equalizer.DynamicSoundMapper
import com.example.equalizer.MacroProfile
import com.example.model.SpatialAudioConfig
import com.example.model.SpatialMode
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
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
 * Allocation-free real-time stereo floating-point PCM DSP pipeline for SONORA.
 *
 * Processing order (each stage independently guarded with NaN/Inf/silence protection):
 * 1. Adaptive 15-Band Parametric Biquad EQ + Dynamic Bass Shelf
 * 2. 360° Soundstage Mode (Binaural / Dynamic 360° / Speakers) + Phase-Safe M/S Widening
 * 3. Cinematic 8D Slow-Orbit (Equal-power ILD floor + Fractional ITD delay + Depth shimmer)
 * 4. Acoustic Room Reverb (Pre-allocated multi-tap stereo diffuser)
 * 5. Channel Balance + Volume Boost + Soft-knee Limiter + Output Sanity Guard
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

    // ITD & Binaural crossfeed delay ring buffers (4096 samples covers >85ms @ 48kHz, >21ms @ 192kHz)
    private val delayMask = 4095
    private val itdDelayL = FloatArray(4096)
    private val itdDelayR = FloatArray(4096)
    private var itdWriteIdx = 0

    // Low-pass filter state for binaural crossfeed & 8D rear-hemisphere warmth
    private var crossfeedLpL = 0f
    private var crossfeedLpR = 0f
    private var rearLpL = 0f
    private var rearLpR = 0f

    // Reverb / Early reflection ring buffers (32768 samples covers >170ms @ 192kHz, ~680ms @ 48kHz)
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

    fun updateSnapshot(newSnapshot: SonoraDspSnapshot) {
        snapshot = newSnapshot
    }

    fun configureSampleRate(newSampleRate: Int) {
        val clamped = newSampleRate.coerceIn(8000, 192000)
        if (clamped != sampleRate) {
            sampleRate = clamped
            resetTransientState()
        }
    }

    fun resetTransientState() {
        eqZ1L.fill(0.0)
        eqZ2L.fill(0.0)
        eqZ1R.fill(0.0)
        eqZ2R.fill(0.0)
        bassZ1L = 0.0
        bassZ2L = 0.0
        bassZ1R = 0.0
        bassZ2R = 0.0
        itdDelayL.fill(0f)
        itdDelayR.fill(0f)
        itdWriteIdx = 0
        crossfeedLpL = 0f
        crossfeedLpR = 0f
        rearLpL = 0f
        rearLpR = 0f
        revBufL.fill(0f)
        revBufR.fill(0f)
        revWriteIdx = 0
        revLpL = 0f
        revLpR = 0f
        cachedSampleRate = -1
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

        // Ensure clean finite input and capture peak amplitude to verify non-silent output
        var inputPeak = 0f
        val sampleLimit = frameCount * 2
        for (idx in 0 until sampleLimit) {
            val s = buffer[idx]
            val clean = if (s.isFinite()) s.coerceIn(-4f, 4f) else 0f
            buffer[idx] = clean
            val absS = abs(clean)
            if (absS > inputPeak) inputPeak = absS
        }

        // Fast bypass path when "All Effects Bypassed" is ON
        if (cfg.globalBypass) {
            isSafetyBypassed = false
            if (volScale != 1.0f) {
                for (idx in 0 until sampleLimit) {
                    buffer[idx] = (buffer[idx] * volScale).coerceIn(-1f, 1f)
                }
            }
            return
        }

        // Keep temporary copy in case an unexpected DSP failure requires clean fallback
        // To avoid heap allocation on the hot audio thread, we keep a reusable scratch array.
        ensureDryScratch(sampleLimit)
        System.arraycopy(buffer, 0, dryScratch, 0, sampleLimit)

        try {
            recomputeCoefficientsIfNeeded(cfg, sr)

            // 1. 15-Band Parametric EQ & Bass Shelf
            applyEqAndBass(buffer, frameCount)

            // 2. 360° Soundstage Mode (Binaural / Dynamic 360° / Speakers)
            applySoundstage(buffer, frameCount, cfg, sr)

            // 3. Cinematic 8D & Dynamic 360° Real-Time Spatial Orbit
            applyOrbit8d(buffer, frameCount, cfg, sr)

            // 4. Acoustic Room Reverb
            applyRoomReverb(buffer, frameCount, cfg, sr)

            // 5. Channel Balance, Volume Boost, Volume Scale & Soft Limiter
            applyOutputGainAndLimiter(buffer, frameCount, cfg, volScale)

            // Final output verification: ensure no NaN/Inf and no accidental silence drop
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
                resetTransientState()
                isSafetyBypassed = true
                lastDspError = "Safety bypass triggered: invalid or silent DSP frame detected"
            } else {
                isSafetyBypassed = false
            }
        } catch (t: Throwable) {
            restoreDry(buffer, sampleLimit, volScale)
            resetTransientState()
            isSafetyBypassed = true
            lastDspError = "DSP stage bypassed: ${t.message}"
        }
    }

    private var dryScratch = FloatArray(4096)

    private fun ensureDryScratch(requiredSize: Int) {
        if (dryScratch.size < requiredSize) {
            dryScratch = FloatArray(requiredSize)
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
            val g = if (cfg.eqEnabled) cfg.eqGainsDb.getOrElse(i) { 0f }.coerceIn(-12f, 12f) else 0f
            abs(cachedGains[i] - g) > 0.01f
        }

        if (gainsChanged) {
            val freqs = DynamicSoundMapper.ISO_15_FREQUENCIES
            for (i in 0 until 15) {
                val g = if (cfg.eqEnabled) cfg.eqGainsDb.getOrElse(i) { 0f }.coerceIn(-12f, 12f) else 0f
                cachedGains[i] = g
                val f0 = freqs[i].toDouble()
                // Guard against Nyquist instability for high bands at lower sample rates
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
                val boostDb = (clampedBass / 1000.0) * 7.5 // Smooth 0..+7.5 dB low shelf peak
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

    private fun applyEqAndBass(buffer: FloatArray, frameCount: Int) {
        val anyEqActive = bandActive.any { it }
        if (!anyEqActive && !bassActive) return

        for (f in 0 until frameCount) {
            val lIdx = f * 2
            val rIdx = lIdx + 1
            var l = buffer[lIdx].toDouble()
            var r = buffer[rIdx].toDouble()

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
                        eqZ1L[b] = 0.0
                        eqZ2L[b] = 0.0
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
                        eqZ1R[b] = 0.0
                        eqZ2R[b] = 0.0
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

            buffer[lIdx] = l.toFloat()
            buffer[rIdx] = r.toFloat()
        }
    }

    private fun applySoundstage(
        buffer: FloatArray,
        frameCount: Int,
        cfg: SonoraDspSnapshot,
        sr: Int
    ) {
        val sc = cfg.spatialConfig
        val virtNorm = (cfg.virtualizerStrength.coerceIn(0, 1000) / 1000f)
        if (!sc.isEnabled && virtNorm <= 0.01f) return

        val activeWidth = if (sc.isEnabled) {
            val base = (sc.soundstageWidth.coerceIn(0f, 1f) * 0.5f +
                sc.stereoWidening.coerceIn(0f, 1f) * 0.5f) *
                sc.intensity.coerceIn(0.25f, 1f)
            val twsMul = if (cfg.isTwsConnected && sc.twsWideStereoEnabled) {
                (1.0f + 0.18f * sc.twsStereoBoost.coerceIn(0.5f, 1.5f)).coerceIn(1.0f, 1.25f)
            } else {
                1.0f
            }
            (base * twsMul).coerceIn(0f, 1.15f)
        } else {
            virtNorm * 0.55f
        }

        // Mode-specific safe M/S widening factor (never phase-cancels Mid energy)
        val msWidth = when (if (sc.isEnabled) sc.mode else SpatialMode.HEADPHONES) {
            SpatialMode.HEADPHONES -> (1.0f + 0.44f * activeWidth).coerceIn(0.90f, 1.50f)
            SpatialMode.CINEMA_360 -> (1.0f + 0.58f * activeWidth).coerceIn(0.95f, 1.65f)
            SpatialMode.SPEAKERS -> (1.0f + 0.28f * activeWidth).coerceIn(0.90f, 1.32f)
        }

        // Loudness compensation so widening does not over-boost side energy
        val normGain = 1.0f / sqrt(1.0f + 0.14f * (msWidth - 1.0f).coerceAtLeast(0f))
        val crossfeedAmount = when (if (sc.isEnabled) sc.mode else SpatialMode.HEADPHONES) {
            SpatialMode.HEADPHONES -> (0.10f * sc.soundstageWidth.coerceIn(0f, 1f))
            SpatialMode.CINEMA_360 -> (0.14f * sc.soundstageWidth.coerceIn(0f, 1f))
            SpatialMode.SPEAKERS -> 0.0f
        }
        val crossfeedDelaySamples = (0.00024f * sr).toInt().coerceIn(2, 64)
        val lpAlpha = (1600.0 / (sr.toDouble() + 1600.0)).toFloat().coerceIn(0.02f, 0.5f)

        val reflTapL = (0.011f * sr).toInt().coerceIn(64, 4000)
        val reflTapR = (0.017f * sr).toInt().coerceIn(96, 6000)
        val cinemaReflectionMix = if (sc.isEnabled && sc.mode == SpatialMode.CINEMA_360) {
            0.12f * sc.soundstageWidth.coerceIn(0f, 1f)
        } else {
            0f
        }

        for (f in 0 until frameCount) {
            val lIdx = f * 2
            val rIdx = lIdx + 1
            val inL = buffer[lIdx]
            val inR = buffer[rIdx]

            val mid = 0.5f * (inL + inR)
            val side = 0.5f * (inL - inR)

            var outL = (mid + msWidth * side) * normGain
            var outR = (mid - msWidth * side) * normGain

            // Write dry stereo to delay buffers cleanly for crossfeed / reflections
            itdDelayL[itdWriteIdx] = outL
            itdDelayR[itdWriteIdx] = outR

            if (crossfeedAmount > 0.005f) {
                val readIdx = (itdWriteIdx - crossfeedDelaySamples) and delayMask
                val oppR = itdDelayR[readIdx]
                val oppL = itdDelayL[readIdx]
                crossfeedLpL += lpAlpha * (oppR - crossfeedLpL)
                crossfeedLpR += lpAlpha * (oppL - crossfeedLpR)
                outL = outL * (1f - crossfeedAmount * 0.35f) + crossfeedLpL * crossfeedAmount
                outR = outR * (1f - crossfeedAmount * 0.35f) + crossfeedLpR * crossfeedAmount
            }

            if (cinemaReflectionMix > 0.005f) {
                val rTapL = (itdWriteIdx - reflTapL) and delayMask
                val rTapR = (itdWriteIdx - reflTapR) and delayMask
                outL += itdDelayR[rTapL] * cinemaReflectionMix
                outR += itdDelayL[rTapR] * cinemaReflectionMix
            }

            itdWriteIdx = (itdWriteIdx + 1) and delayMask
            buffer[lIdx] = outL
            buffer[rIdx] = outR
        }
    }

    private fun applyOrbit8d(
        buffer: FloatArray,
        frameCount: Int,
        cfg: SonoraDspSnapshot,
        sr: Int
    ) {
        if (!cfg.orbit8dEnabled) return

        val speed = cfg.orbit8dSpeed.coerceIn(0.1f, 2.0f)
        val depth = cfg.orbit8dDepth.coerceIn(0.1f, 1.0f)

        // Smooth rotation frequency: 0.06 Hz .. 0.44 Hz (~2.3s to ~16.6s per full 360° orbit)
        val orbitHz = 0.04 + 0.20 * speed.toDouble()
        val deltaAngle = (2.0 * PI * orbitHz) / sr.toDouble()
        val maxItdSamples = 0.00046 * sr.toDouble() // ~0.46ms human ear acoustic ITD
        val minGainFloor = (1.0f - 0.56f * depth).coerceIn(0.38f, 1.0f)
        val rearLpAlpha = (2200.0 / (sr.toDouble() + 2200.0)).toFloat().coerceIn(0.05f, 0.55f)

        for (f in 0 until frameCount) {
            val lIdx = f * 2
            val rIdx = lIdx + 1
            val inL = buffer[lIdx]
            val inR = buffer[rIdx]

            azimuthDouble = (azimuthDouble + deltaAngle) % (2.0 * PI)
            val sinAz = sin(azimuthDouble).toFloat()
            val cosAz = cos(azimuthDouble).toFloat()

            val pan = sinAz * depth // -1 (Left) .. +1 (Right)

            // Controlled equal-power ILD with guaranteed >= 0.38f floor so neither ear ever goes silent
            val rightEmphasis = ((pan + 1f) * 0.5f).coerceIn(0f, 1f)
            val leftEmphasis = 1f - rightEmphasis
            val lGain = (minGainFloor + (1.0f - minGainFloor) * sqrt(leftEmphasis)).coerceIn(0.38f, 1.05f)
            val rGain = (minGainFloor + (1.0f - minGainFloor) * sqrt(rightEmphasis)).coerceIn(0.38f, 1.05f)

            // Write current stereo to ITD ring buffer
            itdDelayL[itdWriteIdx] = inL
            itdDelayR[itdWriteIdx] = inR

            // Trailing ear receives smooth fractional ITD delay
            val itdSamples = abs(pan).toDouble() * maxItdSamples
            val delayedL = if (pan > 0f) readFractional(itdDelayL, itdSamples) else inL
            val delayedR = if (pan < 0f) readFractional(itdDelayR, itdSamples) else inR

            itdWriteIdx = (itdWriteIdx + 1) and delayMask

            // Subtle rear-hemisphere warmth modulation when sound orbits behind the listener (cosAz < 0)
            rearLpL += rearLpAlpha * (delayedL - rearLpL)
            rearLpR += rearLpAlpha * (delayedR - rearLpR)
            val rearBlend = ((-cosAz).coerceAtLeast(0f) * 0.24f * depth).coerceIn(0f, 0.30f)

            val shapedL = delayedL * (1f - rearBlend) + rearLpL * rearBlend
            val shapedR = delayedR * (1f - rearBlend) + rearLpR * rearBlend

            buffer[lIdx] = shapedL * lGain
            buffer[rIdx] = shapedR * rGain
        }

        currentAzimuthRad = azimuthDouble.toFloat()
    }

    private fun readFractional(delayBuf: FloatArray, delaySamples: Double): Float {
        if (delaySamples <= 0.001) return delayBuf[itdWriteIdx]
        val clampedDelay = delaySamples.coerceIn(0.0, 128.0)
        val intPart = clampedDelay.toInt()
        val frac = (clampedDelay - intPart).toFloat()
        val idx0 = (itdWriteIdx - intPart) and delayMask
        val idx1 = (itdWriteIdx - intPart - 1) and delayMask
        val s0 = delayBuf[idx0]
        val s1 = delayBuf[idx1]
        return s0 + frac * (s1 - s0)
    }

    private fun applyRoomReverb(
        buffer: FloatArray,
        frameCount: Int,
        cfg: SonoraDspSnapshot,
        sr: Int
    ) {
        val preset = cfg.reverbPreset
        if (preset == PresetReverb.PRESET_NONE) return

        val (roomScale, wetMix, damping) = when (preset) {
            PresetReverb.PRESET_SMALLROOM -> Triple(0.45f, 0.14f, 0.42f)
            PresetReverb.PRESET_MEDIUMROOM -> Triple(0.65f, 0.18f, 0.36f)
            PresetReverb.PRESET_LARGEROOM -> Triple(0.85f, 0.22f, 0.32f)
            PresetReverb.PRESET_MEDIUMHALL -> Triple(1.05f, 0.25f, 0.28f)
            PresetReverb.PRESET_LARGEHALL -> Triple(1.30f, 0.28f, 0.24f)
            PresetReverb.PRESET_PLATE -> Triple(0.75f, 0.22f, 0.50f)
            else -> Triple(0.60f, 0.16f, 0.35f)
        }

        val tap1L = ((0.0297f * roomScale) * sr).toInt().coerceIn(64, 30000)
        val tap2L = ((0.0419f * roomScale) * sr).toInt().coerceIn(96, 31000)
        val tap1R = ((0.0313f * roomScale) * sr).toInt().coerceIn(64, 30000)
        val tap2R = ((0.0443f * roomScale) * sr).toInt().coerceIn(96, 31000)
        val feedback = (0.34f * roomScale).coerceIn(0.15f, 0.48f)

        for (f in 0 until frameCount) {
            val lIdx = f * 2
            val rIdx = lIdx + 1
            val dryL = buffer[lIdx]
            val dryR = buffer[rIdx]

            val rL = 0.55f * revBufL[(revWriteIdx - tap1L) and revMask] +
                0.45f * revBufR[(revWriteIdx - tap2L) and revMask]
            val rR = 0.55f * revBufR[(revWriteIdx - tap1R) and revMask] +
                0.45f * revBufL[(revWriteIdx - tap2R) and revMask]

            revLpL += damping * (rL - revLpL)
            revLpR += damping * (rR - revLpR)

            revBufL[revWriteIdx] = (dryL + revLpL * feedback).coerceIn(-2f, 2f)
            revBufR[revWriteIdx] = (dryR + revLpR * feedback).coerceIn(-2f, 2f)
            revWriteIdx = (revWriteIdx + 1) and revMask

            // Dry always stays >= 0.88f so reverb never buries or silences music
            buffer[lIdx] = dryL * 0.92f + revLpL * wetMix
            buffer[rIdx] = dryR * 0.92f + revLpR * wetMix
        }
    }

    private fun applyOutputGainAndLimiter(
        buffer: FloatArray,
        frameCount: Int,
        cfg: SonoraDspSnapshot,
        volScale: Float
    ) {
        val sc = cfg.spatialConfig
        val balance = sc.channelBalance.coerceIn(-1.0f, 1.0f)
        var balL = if (balance > 0f) (1.0f - balance) else 1.0f
        var balR = if (balance < 0f) (1.0f + balance) else 1.0f

        if (sc.isEnabled) {
            val posX = sc.listenerX.coerceIn(-1f, 1f)
            if (posX > 0f) balL *= (1.0f - posX * 0.35f)
            else if (posX < 0f) balR *= (1.0f - abs(posX) * 0.35f)
        }

        // Preserve full symmetrical amplitude when centered
        if (abs(balance) < 0.05f && abs(sc.listenerX) < 0.05f) {
            balL = balL.coerceAtLeast(0.95f)
            balR = balR.coerceAtLeast(0.95f)
        }

        val boostDb = cfg.volumeBoostDb.coerceIn(0f, 10f)
        val boostLinear = 10.0.pow(boostDb.toDouble() / 20.0).toFloat()

        val finalGainL = balL * boostLinear * volScale
        val finalGainR = balR * boostLinear * volScale

        for (f in 0 until frameCount) {
            val lIdx = f * 2
            val rIdx = lIdx + 1
            buffer[lIdx] = softLimit(buffer[lIdx] * finalGainL)
            buffer[rIdx] = softLimit(buffer[rIdx] * finalGainR)
        }
    }

    private fun softLimit(x: Float): Float {
        if (!x.isFinite()) return 0f
        val absX = abs(x)
        if (absX <= 0.88f) return x
        // Smooth tanh saturation above 0.88f -> strictly bounded in [-0.995f, +0.995f]
        val excess = (absX - 0.88f) / 0.12f
        val limited = 0.88f + 0.115f * tanh(excess.toDouble()).toFloat()
        return if (x >= 0f) limited else -limited
    }
}
