package com.example.equalizer

import kotlin.math.abs
import kotlin.math.sin

enum class EqualizerMode {
    DYNAMIC,
    MANUAL
}

enum class MacroProfile(val label: String, val qFactor: Float) {
    BROAD("Broad", 0.85f),
    FOCUSED("Focused", 1.65f)
}

object DynamicSoundMapper {

    val ISO_15_FREQUENCIES = intArrayOf(
        25, 40, 63, 100, 160, 250, 400, 630, 1000, 1600, 2500, 4000, 6300, 10000, 16000
    )

    val ISO_15_LABELS = listOf(
        "25 Hz", "40 Hz", "63 Hz", "100 Hz", "160 Hz", "250 Hz", "400 Hz", "630 Hz",
        "1 kHz", "1.6 kHz", "2.5 kHz", "4 kHz", "6.3 kHz", "10 kHz", "16 kHz"
    )

    // Base tilt weights for the 15 bands (-1.0 to +1.0 normalized curve)
    // Left (Warm) boosts bass indices 0..5, right (Bright) boosts treble indices 9..14
    private val TILT_WEIGHTS = floatArrayOf(
        3.5f,  4.5f,  5.0f,  4.2f,  3.0f,  1.5f,  0.5f,  0.0f,
        -0.5f, -1.2f, -1.8f, -2.5f, -3.2f, -3.8f, -4.2f
    )

    // Contour weights for the 15 bands
    // Upper (Focused) boosts vocal/presence 1kHz..4kHz (indices 7..11)
    // Lower (Broad) applies an acoustic smile lifting bass and airy highs
    private val CONTOUR_FOCUSED_WEIGHTS = floatArrayOf(
        -1.0f, -0.8f, -0.5f, -0.8f, -1.2f, -1.5f, -0.5f, 1.2f,
        2.8f,  3.6f,  3.2f,  2.2f,  0.8f,  -0.2f, -0.6f
    )

    private val CONTOUR_BROAD_WEIGHTS = floatArrayOf(
        2.2f,  2.8f,  3.0f,  2.4f,  1.2f,  0.2f, -0.4f, -0.8f,
        -0.8f, -0.6f,  0.2f,  1.2f,  2.2f,  2.8f,  2.5f
    )

    /**
     * Maps the 2D dot-matrix touch coordinates to real 15-band gain adjustments.
     * @param tilt Horizontal axis: -1.0 (Warm) to +1.0 (Bright), 0.0 is Neutral.
     * @param contour Vertical axis: -1.0 (Broad) to +1.0 (Focused), 0.0 is Balanced.
     * @param macroProfile BROAD or FOCUSED macro profile.
     * @return FloatArray of size 15 with gains in dB (-12 dB to +12 dB).
     */
    fun calculateGains(tilt: Float, contour: Float, macroProfile: MacroProfile = MacroProfile.BROAD): FloatArray {
        val clampedTilt = tilt.coerceIn(-1.0f, 1.0f)
        val clampedContour = contour.coerceIn(-1.0f, 1.0f)

        // Exact zero condition produces exactly 0 dB across all 15 bands
        if (abs(clampedTilt) < 0.001f && abs(clampedContour) < 0.001f) {
            return FloatArray(15) { 0f }
        }

        val result = FloatArray(15)

        for (i in 0 until 15) {
            // Tilt contribution: negative tilt tilts towards bass, positive tilts towards treble
            val tiltComponent = -clampedTilt * TILT_WEIGHTS[i]

            // Contour contribution:
            val contourComponent = if (clampedContour >= 0f) {
                clampedContour * CONTOUR_FOCUSED_WEIGHTS[i]
            } else {
                -clampedContour * CONTOUR_BROAD_WEIGHTS[i]
            }

            // Macro profile subtle refinement
            val macroScale = if (macroProfile == MacroProfile.FOCUSED) 1.05f else 0.95f

            val totalGain = (tiltComponent + contourComponent) * macroScale
            result[i] = (Math.round(totalGain.coerceIn(-12f, 12f) * 10f) / 10f)
        }

        return result
    }
}
