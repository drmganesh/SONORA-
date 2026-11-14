package com.example.audio

import android.media.audiofx.PresetReverb
import com.example.model.SpatialAudioConfig
import com.example.model.SpatialMode
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin
import kotlin.math.sqrt
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SonoraDspProcessorTest {

    private fun generateStereoSineBuffer(
        frames: Int,
        sampleRate: Int,
        leftFreq: Double = 440.0,
        rightFreq: Double = 554.37,
        amplitude: Float = 0.5f
    ): FloatArray {
        val buf = FloatArray(frames * 2)
        for (i in 0 until frames) {
            buf[i * 2] = (sin(2.0 * PI * leftFreq * i / sampleRate) * amplitude).toFloat()
            buf[i * 2 + 1] = (sin(2.0 * PI * rightFreq * i / sampleRate) * amplitude).toFloat()
        }
        return buf
    }

    private fun computeChannelRms(buf: FloatArray, frames: Int): Pair<Float, Float> {
        var sumSqL = 0.0
        var sumSqR = 0.0
        for (i in 0 until frames) {
            val l = buf[i * 2].toDouble()
            val r = buf[i * 2 + 1].toDouble()
            sumSqL += l * l
            sumSqR += r * r
        }
        return Pair(sqrt(sumSqL / frames).toFloat(), sqrt(sumSqR / frames).toFloat())
    }

    private fun assertValidAudibleStereo(buf: FloatArray, frames: Int, minRms: Float = 0.05f) {
        var peak = 0f
        for (i in 0 until frames * 2) {
            val s = buf[i]
            assertTrue("Sample $i must be finite (was $s)", s.isFinite())
            assertTrue("Sample $i exceeds [-1, 1] safe bounds (was $s)", abs(s) <= 1.001f)
            if (abs(s) > peak) peak = abs(s)
        }
        val (rmsL, rmsR) = computeChannelRms(buf, frames)
        assertTrue("Left channel went silent (rmsL=$rmsL)", rmsL >= minRms)
        assertTrue("Right channel went silent (rmsR=$rmsR)", rmsR >= minRms)
        assertTrue("Peak dropped unexpectedly ($peak)", peak >= minRms)
    }

    @Test
    fun `test bypass ON plays dry audio and bypass OFF applies active effects without silence`() {
        val dsp = SonoraDspProcessor()
        dsp.configureSampleRate(48000)

        val frames = 2048
        val dry = generateStereoSineBuffer(frames, 48000)
        val processed = dry.copyOf()

        // 1. All Effects Bypassed = ON -> bit-exact unity pass-through
        dsp.updateSnapshot(
            SonoraDspSnapshot(
                globalBypass = true,
                orbit8dEnabled = true,
                spatialConfig = SpatialAudioConfig(isEnabled = true, mode = SpatialMode.CINEMA_360),
                bassBoostStrength = 900,
                reverbPreset = PresetReverb.PRESET_LARGEHALL
            )
        )
        dsp.processStereoBuffer(processed, frames)
        assertValidAudibleStereo(processed, frames)
        for (i in processed.indices) {
            assertTrue("Bypass must preserve dry audio", abs(processed[i] - dry[i]) < 1e-6f)
        }

        // 2. Toggle All Effects Bypassed = OFF -> effects become active while audio stays audible
        val activeBuf = dry.copyOf()
        dsp.updateSnapshot(
            SonoraDspSnapshot(
                globalBypass = false,
                orbit8dEnabled = true,
                orbit8dSpeed = 1.2f,
                orbit8dDepth = 1.0f,
                spatialConfig = SpatialAudioConfig(isEnabled = true, mode = SpatialMode.CINEMA_360, soundstageWidth = 1.0f, stereoWidening = 1.0f),
                bassBoostStrength = 800,
                reverbPreset = PresetReverb.PRESET_MEDIUMHALL
            )
        )
        dsp.processStereoBuffer(activeBuf, frames)
        assertValidAudibleStereo(activeBuf, frames)
        assertFalse("Safety bypass should not trigger on valid DSP chain", dsp.isSafetyBypassed)
    }

    @Test
    fun `test Cinematic 8D orbit rotates continuously with safe stereo floor across full cycle`() {
        val dsp = SonoraDspProcessor()
        dsp.configureSampleRate(44100)
        dsp.updateSnapshot(
            SonoraDspSnapshot(
                globalBypass = false,
                orbit8dEnabled = true,
                orbit8dSpeed = 2.0f,
                orbit8dDepth = 1.0f,
                spatialConfig = SpatialAudioConfig(isEnabled = true, mode = SpatialMode.HEADPHONES)
            )
        )

        val frames = 2048
        var minRmsL = Float.MAX_VALUE
        var minRmsR = Float.MAX_VALUE
        val initialAz = dsp.currentAzimuthRad

        // Stream ~3 seconds of audio frames continuously
        repeat(65) {
            val buf = generateStereoSineBuffer(frames, 44100)
            dsp.processStereoBuffer(buf, frames)
            assertValidAudibleStereo(buf, frames, minRms = 0.10f)
            val (rmsL, rmsR) = computeChannelRms(buf, frames)
            if (rmsL < minRmsL) minRmsL = rmsL
            if (rmsR < minRmsR) minRmsR = rmsR
        }

        assertNotEquals("Azimuth must advance during 8D processing", initialAz, dsp.currentAzimuthRad)
        assertTrue("Left ear minimum RMS too low across 360 orbit ($minRmsL)", minRmsL >= 0.10f)
        assertTrue("Right ear minimum RMS too low across 360 orbit ($minRmsR)", minRmsR >= 0.10f)

        // Turn 8D OFF seamlessly mid-stream -> smooth 15ms transition then full centered stereo
        dsp.updateSnapshot(dsp.snapshot.copy(orbit8dEnabled = false))
        val transitionOff = generateStereoSineBuffer(frames, 44100, leftFreq = 440.0, rightFreq = 440.0)
        dsp.processStereoBuffer(transitionOff, frames)
        assertValidAudibleStereo(transitionOff, frames, minRms = 0.20f)
        val settledOff = generateStereoSineBuffer(frames, 44100, leftFreq = 440.0, rightFreq = 440.0)
        dsp.processStereoBuffer(settledOff, frames)
        assertValidAudibleStereo(settledOff, frames, minRms = 0.30f)
    }

    @Test
    fun `test 360 soundstage mode switching Binaural to Dynamic to Speakers and mono anti-cancellation`() {
        val dsp = SonoraDspProcessor()
        dsp.configureSampleRate(48000)
        val frames = 2048

        val modes = listOf(SpatialMode.HEADPHONES, SpatialMode.CINEMA_360, SpatialMode.SPEAKERS)
        for (mode in modes) {
            for (width in listOf(0.0f, 0.5f, 1.0f)) {
                dsp.updateSnapshot(
                    SonoraDspSnapshot(
                        globalBypass = false,
                        spatialConfig = SpatialAudioConfig(
                            isEnabled = true,
                            mode = mode,
                            intensity = 1.0f,
                            soundstageWidth = width,
                            stereoWidening = width,
                            twsWideStereoEnabled = true,
                            twsStereoBoost = 1.5f
                        ),
                        isTwsConnected = true
                    )
                )
                // Mono-identical input (L == R) is the classic trigger for phase-cancellation silence bugs
                val monoInput = generateStereoSineBuffer(frames, 48000, leftFreq = 440.0, rightFreq = 440.0)
                dsp.processStereoBuffer(monoInput, frames)
                assertValidAudibleStereo(monoInput, frames, minRms = 0.20f)
            }
        }
    }

    @Test
    fun `test multiple sample rates 32kHz through 192kHz and NaN input resilience`() {
        val rates = listOf(32000, 44100, 48000, 96000, 192000)
        val dsp = SonoraDspProcessor()

        for (sr in rates) {
            dsp.configureSampleRate(sr)
            dsp.updateSnapshot(
                SonoraDspSnapshot(
                    globalBypass = false,
                    eqEnabled = true,
                    eqGainsDb = FloatArray(15) { 9.0f }, // Heavy boost across all bands including 16kHz
                    volumeBoostDb = 8.0f,
                    bassBoostStrength = 1000,
                    virtualizerStrength = 1000,
                    reverbPreset = PresetReverb.PRESET_LARGEHALL,
                    orbit8dEnabled = true,
                    orbit8dSpeed = 1.5f,
                    orbit8dDepth = 0.9f,
                    spatialConfig = SpatialAudioConfig(isEnabled = true, mode = SpatialMode.CINEMA_360)
                )
            )
            val buf = generateStereoSineBuffer(1024, sr)
            // Inject poison NaN & Infinity samples in first frame
            buf[0] = Float.NaN
            buf[1] = Float.POSITIVE_INFINITY
            dsp.processStereoBuffer(buf, 1024)
            assertValidAudibleStereo(buf, 1024, minRms = 0.08f)
        }
    }

    @Test
    fun `test zero tok-tok packet boundary discontinuities with Soundstage and Dynamic 360 both ON`() {
        val dsp = SonoraDspProcessor()
        val sr = 48000
        val frames = 1024
        dsp.configureSampleRate(sr)
        dsp.updateSnapshot(
            SonoraDspSnapshot(
                globalBypass = false,
                orbit8dEnabled = true,
                orbit8dSpeed = 1.6f,
                orbit8dDepth = 0.95f,
                spatialConfig = SpatialAudioConfig(
                    isEnabled = true,
                    mode = SpatialMode.CINEMA_360,
                    soundstageWidth = 0.95f,
                    stereoWidening = 0.9f,
                    spatialProfile = com.example.model.SpatialProfile.DYNAMIC_360,
                    spatialIntensity = com.example.model.SpatialIntensity.IMMERSIVE
                ),
                reverbPreset = PresetReverb.PRESET_SMALLROOM
            )
        )

        var globalSampleIdx = 0L
        var prevL = 0f
        var prevR = 0f
        var maxStepAfterWarmup = 0f

        // Stream 80 consecutive packets of continuous-phase 440Hz/523Hz stereo sine
        for (packet in 0 until 80) {
            val buf = FloatArray(frames * 2)
            for (i in 0 until frames) {
                val n = globalSampleIdx++
                buf[i * 2] = (sin(2.0 * PI * 440.0 * n / sr) * 0.45).toFloat()
                buf[i * 2 + 1] = (sin(2.0 * PI * 523.25 * n / sr) * 0.45).toFloat()
            }
            dsp.processStereoBuffer(buf, frames)
            assertValidAudibleStereo(buf, frames, minRms = 0.08f)

            // After 3-packet delay-line fill warmup (~64ms), every sample transition including packet boundaries
            // must be smooth with zero periodic "tok-tok" spikes.
            if (packet >= 3) {
                for (i in 0 until frames) {
                    val curL = buf[i * 2]
                    val curR = buf[i * 2 + 1]
                    val dL = abs(curL - prevL)
                    val dR = abs(curR - prevR)
                    if (dL > maxStepAfterWarmup) maxStepAfterWarmup = dL
                    if (dR > maxStepAfterWarmup) maxStepAfterWarmup = dR
                    prevL = curL
                    prevR = curR
                }
            } else {
                prevL = buf[(frames - 1) * 2]
                prevR = buf[(frames - 1) * 2 + 1]
            }
        }

        // Theoretical single-sample step for 523Hz@48kHz amp 0.45 is ~0.031; allow generous margin (< 0.14)
        // A buffer collision or ITD jump click easily exceeds 0.35 - 0.80.
        assertTrue(
            "Detected click/discontinuity spike across packet boundaries (maxDelta=$maxStepAfterWarmup)",
            maxStepAfterWarmup < 0.14f
        )
    }

    @Test
    fun `test live profile switching and orbit toggle without click spikes`() {
        val dsp = SonoraDspProcessor()
        val sr = 44100
        val frames = 1024
        dsp.configureSampleRate(sr)

        var globalSampleIdx = 0L
        var prevL = 0f
        var prevR = 0f
        var maxDelta = 0f

        val scenarios = listOf(
            // Start with effects ON, Orbit profile
            SonoraDspSnapshot(
                orbit8dEnabled = true,
                orbit8dSpeed = 0.6f,
                orbit8dDepth = 0.8f,
                spatialConfig = SpatialAudioConfig(
                    isEnabled = true,
                    spatialProfile = com.example.model.SpatialProfile.ORBIT,
                    spatialIntensity = com.example.model.SpatialIntensity.SUBTLE
                )
            ),
            // Switch live to Dynamic 360 Immersive
            SonoraDspSnapshot(
                orbit8dEnabled = true,
                orbit8dSpeed = 1.4f,
                orbit8dDepth = 1.0f,
                spatialConfig = SpatialAudioConfig(
                    isEnabled = true,
                    mode = SpatialMode.CINEMA_360,
                    spatialProfile = com.example.model.SpatialProfile.DYNAMIC_360,
                    spatialIntensity = com.example.model.SpatialIntensity.IMMERSIVE
                )
            ),
            // Switch live to Cinematic
            SonoraDspSnapshot(
                orbit8dEnabled = true,
                orbit8dSpeed = 0.8f,
                orbit8dDepth = 0.85f,
                spatialConfig = SpatialAudioConfig(
                    isEnabled = true,
                    mode = SpatialMode.HEADPHONES,
                    spatialProfile = com.example.model.SpatialProfile.CINEMATIC,
                    spatialIntensity = com.example.model.SpatialIntensity.BALANCED
                ),
                reverbPreset = PresetReverb.PRESET_MEDIUMROOM
            ),
            // Toggle 8D OFF live
            SonoraDspSnapshot(
                orbit8dEnabled = false,
                spatialConfig = SpatialAudioConfig(isEnabled = true)
            ),
            // Toggle Global Bypass ON live
            SonoraDspSnapshot(
                globalBypass = true,
                orbit8dEnabled = false
            ),
            // Re-enable 8D + Cinematic live
            SonoraDspSnapshot(
                globalBypass = false,
                orbit8dEnabled = true,
                orbit8dSpeed = 1.0f,
                orbit8dDepth = 0.9f,
                spatialConfig = SpatialAudioConfig(
                    isEnabled = true,
                    spatialProfile = com.example.model.SpatialProfile.CINEMATIC
                )
            )
        )

        scenarios.forEachIndexed { idx, snap ->
            dsp.updateSnapshot(snap)
            repeat(8) { pkt ->
                val buf = FloatArray(frames * 2)
                for (i in 0 until frames) {
                    val n = globalSampleIdx++
                    val s = (sin(2.0 * PI * 330.0 * n / sr) * 0.45).toFloat()
                    buf[i * 2] = s
                    buf[i * 2 + 1] = s
                }
                dsp.processStereoBuffer(buf, frames)
                assertValidAudibleStereo(buf, frames, minRms = 0.08f)

                if (!(idx == 0 && pkt == 0)) {
                    for (i in 0 until frames) {
                        val dL = abs(buf[i * 2] - prevL)
                        val dR = abs(buf[i * 2 + 1] - prevR)
                        if (dL > maxDelta) maxDelta = dL
                        if (dR > maxDelta) maxDelta = dR
                        prevL = buf[i * 2]
                        prevR = buf[i * 2 + 1]
                    }
                } else {
                    prevL = buf[(frames - 1) * 2]
                    prevR = buf[(frames - 1) * 2 + 1]
                }
            }
        }

        assertTrue(
            "Live switching caused discontinuity click (maxDelta=$maxDelta)",
            maxDelta < 0.16f
        )
    }

    @Test
    fun `test stream discontinuity fade-in ramp prevents seek pop`() {
        val dsp = SonoraDspProcessor()
        dsp.configureSampleRate(48000)
        dsp.updateSnapshot(
            SonoraDspSnapshot(
                orbit8dEnabled = true,
                spatialConfig = SpatialAudioConfig(
                    isEnabled = true,
                    spatialProfile = com.example.model.SpatialProfile.DYNAMIC_360
                )
            )
        )

        dsp.notifyStreamDiscontinuity()
        // Step input (constant +0.6) right after seek: first sample must start near 0 and ramp up smoothly
        val buf = FloatArray(1024 * 2) { 0.6f }
        dsp.processStereoBuffer(buf, 1024)
        assertTrue("First sample after seek must ramp smoothly from ~0 (was ${buf[0]})", abs(buf[0]) < 0.05f)
        assertValidAudibleStereo(buf, 1024, minRms = 0.15f)
    }
}

