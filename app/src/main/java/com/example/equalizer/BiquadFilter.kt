package com.example.equalizer

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * High-precision Second-Order IIR Biquad Filter based on the Audio EQ Cookbook by Robert Bristow-Johnson.
 * Implements Peaking EQ with accurate coefficient calculations and real-time frequency response computation.
 */
class BiquadFilter(
    val centerFrequency: Float,
    var gainDb: Float = 0f,
    var qFactor: Float = 1.0f,
    var sampleRate: Float = 44100f
) {
    // Direct Form I / II coefficients:
    // H(z) = (b0 + b1*z^-1 + b2*z^-2) / (a0 + a1*z^-1 + a2*z^-2)
    var b0: Double = 1.0
        private set
    var b1: Double = 0.0
        private set
    var b2: Double = 0.0
        private set
    var a0: Double = 1.0
        private set
    var a1: Double = 0.0
        private set
    var a2: Double = 0.0
        private set

    init {
        recalculateCoefficients()
    }

    fun update(gainDb: Float, qFactor: Float = this.qFactor, sampleRate: Float = this.sampleRate) {
        this.gainDb = gainDb
        this.qFactor = qFactor.coerceIn(0.2f, 10.0f)
        this.sampleRate = sampleRate.coerceAtLeast(8000f)
        recalculateCoefficients()
    }

    /**
     * Recalculates peaking EQ biquad coefficients:
     * A  = 10^(gainDb / 40)
     * w0 = 2 * pi * f0 / Fs
     * alpha = sin(w0) / (2 * Q)
     */
    private fun recalculateCoefficients() {
        if (gainDb == 0f) {
            b0 = 1.0
            b1 = 0.0
            b2 = 0.0
            a0 = 1.0
            a1 = 0.0
            a2 = 0.0
            return
        }

        val a = 10.0.pow(gainDb.toDouble() / 40.0)
        val w0 = 2.0 * PI * (centerFrequency.toDouble() / sampleRate.toDouble())
        val cosW0 = cos(w0)
        val sinW0 = sin(w0)
        val alpha = sinW0 / (2.0 * qFactor.toDouble())

        val unnormB0 = 1.0 + alpha * a
        val unnormB1 = -2.0 * cosW0
        val unnormB2 = 1.0 - alpha * a
        val unnormA0 = 1.0 + alpha / a
        val unnormA1 = -2.0 * cosW0
        val unnormA2 = 1.0 - alpha / a

        // Normalize by a0
        b0 = unnormB0 / unnormA0
        b1 = unnormB1 / unnormA0
        b2 = unnormB2 / unnormA0
        a0 = 1.0
        a1 = unnormA1 / unnormA0
        a2 = unnormA2 / unnormA0
    }

    /**
     * Calculates the magnitude response in decibels |H(e^jω)| at an arbitrary frequency [f].
     */
    fun magnitudeResponseDbAt(f: Float): Float {
        if (gainDb == 0f) return 0f

        val w = 2.0 * PI * (f.toDouble() / sampleRate.toDouble())
        val cosW = cos(w)
        val cos2W = cos(2.0 * w)
        val sinW = sin(w)
        val sin2W = sin(2.0 * w)

        // Numerator = b0 + b1*e^-jw + b2*e^-j2w
        val numReal = b0 + b1 * cosW + b2 * cos2W
        val numImag = -b1 * sinW - b2 * sin2W
        val numMagSq = numReal * numReal + numImag * numImag

        // Denominator = 1 + a1*e^-jw + a2*e^-j2w
        val denReal = 1.0 + a1 * cosW + a2 * cos2W
        val denImag = -a1 * sinW - a2 * sin2W
        val denMagSq = denReal * denReal + denImag * denImag

        if (denMagSq <= 1e-12) return 0f

        val magLinear = sqrt(numMagSq / denMagSq)
        val magDb = (20.0 * log10(magLinear)).toFloat()
        return magDb.coerceIn(-24f, 24f)
    }
}
