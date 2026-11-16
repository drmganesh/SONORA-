package playback

import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import com.example.audio.SonoraDspProcessor
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Custom Media3 [AudioProcessor] bridging ExoPlayer's decoded PCM stream directly through
 * SONORA's real-time [SonoraDspProcessor] (15-band EQ, bass boost, virtualizer, 8D orbit,
 * spatial soundstage, reverb, soft limiter) and feeding real-time FFT/waveform visualizer telemetry.
 */
@OptIn(UnstableApi::class)
class SonoraMedia3DspProcessor(
    private val dspProcessor: SonoraDspProcessor,
    private val onVisualizerFrame: (fftBins: FloatArray, waveformSamples: FloatArray) -> Unit,
    private val onAzimuthChanged: (Float) -> Unit
) : BaseAudioProcessor() {

    private var stereoFloats = FloatArray(16384)
    private val waveBufA = FloatArray(64)
    private val waveBufB = FloatArray(64)
    private val binsBufA = FloatArray(32)
    private val binsBufB = FloatArray(32)
    private var telemetrySlot = 0
    private var lastUiPushMs = 0L

    private var cachedOscSampleRate = -1
    private val oscCosW = DoubleArray(32)
    private val oscSinW = DoubleArray(32)

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        val encoding = inputAudioFormat.encoding
        if (encoding != C.ENCODING_PCM_16BIT && encoding != C.ENCODING_PCM_FLOAT) {
            throw AudioProcessor.UnhandledAudioFormatException(inputAudioFormat)
        }
        val channels = inputAudioFormat.channelCount
        if (channels !in 1..2) {
            throw AudioProcessor.UnhandledAudioFormatException(inputAudioFormat)
        }
        dspProcessor.configureSampleRate(inputAudioFormat.sampleRate)
        return AudioProcessor.AudioFormat(
            inputAudioFormat.sampleRate,
            2,
            encoding
        )
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val remaining = inputBuffer.remaining()
        if (remaining <= 0) return

        val inChannels = inputAudioFormat.channelCount.coerceIn(1, 2)
        val encoding = inputAudioFormat.encoding
        val bytesPerSample = if (encoding == C.ENCODING_PCM_FLOAT) 4 else 2
        val bytesPerFrame = bytesPerSample * inChannels
        if (bytesPerFrame <= 0) return

        val frameCount = remaining / bytesPerFrame
        if (frameCount <= 0) {
            inputBuffer.position(inputBuffer.limit())
            return
        }

        val neededFloats = frameCount * 2
        if (stereoFloats.size < neededFloats) {
            stereoFloats = FloatArray(neededFloats)
        }

        inputBuffer.order(ByteOrder.LITTLE_ENDIAN)
        if (encoding == C.ENCODING_PCM_FLOAT) {
            if (inChannels == 2) {
                for (i in 0 until neededFloats) {
                    stereoFloats[i] = inputBuffer.float
                }
            } else {
                for (f in 0 until frameCount) {
                    val m = inputBuffer.float
                    val base = f * 2
                    stereoFloats[base] = m
                    stereoFloats[base + 1] = m
                }
            }
        } else {
            val invScale = 1f / 32768f
            if (inChannels == 2) {
                for (i in 0 until neededFloats) {
                    stereoFloats[i] = inputBuffer.short * invScale
                }
            } else {
                for (f in 0 until frameCount) {
                    val m = inputBuffer.short * invScale
                    val base = f * 2
                    stereoFloats[base] = m
                    stereoFloats[base + 1] = m
                }
            }
        }

        // Discard any trailing partial frame bytes cleanly
        val consumedBytes = frameCount * bytesPerFrame
        if (consumedBytes < remaining) {
            inputBuffer.position(inputBuffer.position() + (remaining - consumedBytes))
        }

        dspProcessor.processStereoBuffer(stereoFloats, frameCount)
        maybeEmitTelemetry(stereoFloats, frameCount, inputAudioFormat.sampleRate)

        val outBytes = frameCount * 2 * bytesPerSample
        val outBuf = replaceOutputBuffer(outBytes)
        outBuf.order(ByteOrder.LITTLE_ENDIAN)

        if (encoding == C.ENCODING_PCM_FLOAT) {
            for (i in 0 until neededFloats) {
                outBuf.putFloat(stereoFloats[i].coerceIn(-1f, 1f))
            }
        } else {
            for (i in 0 until neededFloats) {
                val s = (stereoFloats[i].coerceIn(-1f, 1f) * 32767f).toInt().coerceIn(-32768, 32767)
                outBuf.putShort(s.toShort())
            }
        }
        outBuf.flip()
    }

    override fun onFlush() {
        dspProcessor.notifyStreamDiscontinuity()
    }

    override fun onReset() {
        dspProcessor.notifyStreamDiscontinuity()
    }

    private fun ensureOscillatorTable(sampleRate: Int) {
        if (cachedOscSampleRate == sampleRate) return
        cachedOscSampleRate = sampleRate.coerceIn(8000, 192000)
        for (b in 0 until 32) {
            val norm = b / 31.0
            val freq = 40.0 * Math.pow(350.0, norm)
            val omega = (2.0 * PI * freq / cachedOscSampleRate).coerceAtMost(PI * 0.9)
            oscCosW[b] = cos(omega)
            oscSinW[b] = sin(omega)
        }
    }

    private fun maybeEmitTelemetry(stereoFloats: FloatArray, frameCount: Int, sampleRate: Int) {
        val now = System.currentTimeMillis()
        if (now - lastUiPushMs < 33L) return
        lastUiPushMs = now

        val wave = if (telemetrySlot == 0) waveBufA else waveBufB
        val bins = if (telemetrySlot == 0) binsBufA else binsBufB
        telemetrySlot = telemetrySlot xor 1

        val step = (frameCount / 64).coerceAtLeast(1)
        for (i in 0 until 64) {
            val f = (i * step).coerceAtMost(frameCount - 1)
            val l = stereoFloats[f * 2]
            val r = stereoFloats[f * 2 + 1]
            wave[i] = ((l + r) * 0.5f).coerceIn(-1f, 1f)
        }

        ensureOscillatorTable(sampleRate)
        val n = frameCount.coerceAtMost(128)
        if (n >= 32) {
            val invNorm = 1.0 / (n * 0.35)
            for (b in 0 until 32) {
                val cosW = oscCosW[b]
                val sinW = oscSinW[b]
                var c = 1.0
                var s = 0.0
                var sumRe = 0.0
                var sumIm = 0.0
                for (i in 0 until n) {
                    val sample = (stereoFloats[i * 2] + stereoFloats[i * 2 + 1]) * 0.5
                    sumRe += sample * c
                    sumIm += sample * s
                    val nextC = c * cosW - s * sinW
                    s = s * cosW + c * sinW
                    c = nextC
                }
                val mag = (sqrt(sumRe * sumRe + sumIm * sumIm) * invNorm).toFloat()
                val boosted = if (b < 8) mag * 1.1f else mag * (1.25f + b * 0.03f)
                bins[b] = boosted.coerceIn(0f, 1f)
            }
        }

        onVisualizerFrame(bins, wave)
        onAzimuthChanged(dspProcessor.currentAzimuthRad)
    }
}
