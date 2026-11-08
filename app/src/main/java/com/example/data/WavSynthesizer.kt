package com.example.data

import android.content.Context
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.sin

/**
 * Synthesizes pristine 44.1kHz 16-bit stereo PCM WAV audio files
 * for out-of-the-box offline playback, testing 10-band EQ, 360° spatial audio,
 * and real-time audio visualizers.
 */
object WavSynthesizer {

    fun getOrGenerateDemoTrack(
        context: Context,
        filename: String,
        durationSeconds: Int,
        baseFrequencies: DoubleArray,
        patternType: Int
    ): File {
        val targetFile = File(context.filesDir, filename)
        if (targetFile.exists() && targetFile.length() > 44) {
            return targetFile
        }

        val sampleRate = 44100
        val numChannels = 2
        val bitsPerSample = 16
        val totalSamples = durationSeconds * sampleRate
        val dataSize = totalSamples * numChannels * (bitsPerSample / 8)

        FileOutputStream(targetFile).use { fos ->
            // Write 44-byte WAV Header
            val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
            header.put("RIFF".toByteArray())
            header.putInt(36 + dataSize)
            header.put("WAVE".toByteArray())
            header.put("fmt ".toByteArray())
            header.putInt(16) // Subchunk1Size (16 for PCM)
            header.putShort(1) // AudioFormat (1 for PCM)
            header.putShort(numChannels.toShort())
            header.putInt(sampleRate)
            header.putInt(sampleRate * numChannels * bitsPerSample / 8) // ByteRate
            header.putShort((numChannels * bitsPerSample / 8).toShort()) // BlockAlign
            header.putShort(bitsPerSample.toShort())
            header.put("data".toByteArray())
            header.putInt(dataSize)

            fos.write(header.array())

            // Generate musical audio samples
            val buffer = ByteArray(4096)
            var bufferIdx = 0
            val twoPi = 2.0 * Math.PI

            for (i in 0 until totalSamples) {
                val t = i.toDouble() / sampleRate
                val beatPhase = (t * 2.0) % 1.0 // 120 bpm rhythm
                val envelope = when {
                    beatPhase < 0.05 -> beatPhase / 0.05
                    beatPhase < 0.6 -> 1.0 - (beatPhase - 0.05) * 0.7
                    else -> 0.3
                }

                // Chords and melody
                val f1 = baseFrequencies[0]
                val f2 = baseFrequencies.getOrElse(1) { f1 * 1.25 }
                val f3 = baseFrequencies.getOrElse(2) { f1 * 1.5 }
                val bass = f1 / 2.0

                val sampleBase = (
                    0.35 * sin(twoPi * f1 * t) +
                    0.25 * sin(twoPi * f2 * t) +
                    0.20 * sin(twoPi * f3 * t) +
                    0.40 * sin(twoPi * bass * t)
                ) * envelope

                // Spatial panning effect (stereo difference)
                val panL = when (patternType) {
                    0 -> 0.7 + 0.3 * sin(twoPi * 0.2 * t) // slow orbital pan
                    1 -> 0.5 + 0.4 * sin(twoPi * 0.4 * t)
                    else -> 0.8
                }
                val panR = when (patternType) {
                    0 -> 0.7 + 0.3 * sin(twoPi * 0.2 * t + Math.PI)
                    1 -> 0.5 + 0.4 * sin(twoPi * 0.4 * t + Math.PI)
                    else -> 0.8
                }

                val leftSample = (sampleBase * panL * 18000.0).toInt().coerceIn(-32767, 32767).toShort()
                val rightSample = (sampleBase * panR * 18000.0).toInt().coerceIn(-32767, 32767).toShort()

                // Left channel (16-bit little endian)
                buffer[bufferIdx++] = (leftSample.toInt() and 0xFF).toByte()
                buffer[bufferIdx++] = ((leftSample.toInt() shr 8) and 0xFF).toByte()

                // Right channel (16-bit little endian)
                buffer[bufferIdx++] = (rightSample.toInt() and 0xFF).toByte()
                buffer[bufferIdx++] = ((rightSample.toInt() shr 8) and 0xFF).toByte()

                if (bufferIdx >= buffer.size) {
                    fos.write(buffer, 0, bufferIdx)
                    bufferIdx = 0
                }
            }

            if (bufferIdx > 0) {
                fos.write(buffer, 0, bufferIdx)
            }
        }

        return targetFile
    }
}
