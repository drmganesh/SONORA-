package com.example.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.PlaybackParams
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.util.Log
import java.io.File
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Single-pipeline real-time PCM decoder + AudioTrack renderer wired through [SonoraDspProcessor].
 *
 * Uses MediaExtractor + MediaCodec -> Stereo Float PCM -> SonoraDspProcessor -> AudioTrack (MODE_STREAM),
 * avoiding Android AudioFlinger offload/Virtualizer/PresetReverb HAL muting bugs while keeping
 * genuine real-time 8D orbit, 360° soundstage, 15-band EQ, reverb, and soft-limiting active.
 *
 * Only transitions to [isPrepared] = true once an initialized [AudioTrack] actually writes the
 * first decoded PCM frame, guaranteeing automatic seamless fallback on any codec/extractor/HAL issue
 * both at track start and mid-stream.
 */
class PcmTrackPlayer(
    private val context: Context,
    private val dspProcessor: SonoraDspProcessor,
    private val onVisualizerFrame: (fftBins: FloatArray, waveformSamples: FloatArray) -> Unit,
    private val onAzimuthChanged: (Float) -> Unit
) {
    private val tag = "PcmTrackPlayer"
    private val mainHandler = Handler(Looper.getMainLooper())

    private val activeGeneration = AtomicLong(0L)
    private val stopRequested = AtomicBoolean(false)
    private val seekRequested = AtomicBoolean(false)
    private val pendingSeekMs = AtomicLong(0L)
    private val currentPositionAtomicMs = AtomicLong(0L)
    private val durationAtomicMs = AtomicLong(0L)

    private val pauseLock = Object()

    @Volatile
    private var isPausedState: Boolean = false

    @Volatile
    var isPrepared: Boolean = false
        private set

    @Volatile
    var isPlaying: Boolean = false
        private set

    @Volatile
    var audioSessionId: Int = 0
        private set

    @Volatile
    private var playbackSpeed: Float = 1.0f

    @Volatile
    private var playbackPitch: Float = 1.0f

    private var workerThread: Thread? = null

    @Volatile
    private var activeAudioTrack: AudioTrack? = null

    // Pre-allocated telemetry double-buffers (eliminates GC allocations on the real-time audio thread)
    private val waveBufA = FloatArray(64)
    private val waveBufB = FloatArray(64)
    private val binsBufA = FloatArray(32)
    private val binsBufB = FloatArray(32)
    private var telemetrySlot = 0
    private var cachedOscSampleRate = -1
    private val oscCosW = DoubleArray(32)
    private val oscSinW = DoubleArray(32)
    private var lastUnderrunCount = 0

    val durationMs: Long
        get() = durationAtomicMs.get()

    val currentPositionMs: Long
        get() = if (seekRequested.get()) pendingSeekMs.get() else currentPositionAtomicMs.get()

    fun prepareAndPlay(
        uri: Uri,
        fallbackPath: String? = null,
        initialSpeed: Float,
        onPrepared: (sessionId: Int, durationMs: Long) -> Unit,
        onCompletion: () -> Unit,
        onFallbackRequired: (resumePositionMs: Long, reason: String) -> Unit
    ) {
        val gen = activeGeneration.incrementAndGet()
        playbackSpeed = initialSpeed.coerceIn(0.5f, 2.0f)

        stopInternal()

        stopRequested.set(false)
        seekRequested.set(false)
        pendingSeekMs.set(0L)
        currentPositionAtomicMs.set(0L)
        durationAtomicMs.set(0L)
        isPausedState = false
        isPrepared = false
        isPlaying = false

        val thread = Thread({
            runAudioPipeline(
                generation = gen,
                uri = uri,
                fallbackPath = fallbackPath,
                onPrepared = onPrepared,
                onCompletion = onCompletion,
                onFallbackRequired = onFallbackRequired
            )
        }, "SonoraPcmAudio-$gen").apply {
            priority = Thread.MAX_PRIORITY
        }
        workerThread = thread
        thread.start()
    }

    private fun runAudioPipeline(
        generation: Long,
        uri: Uri,
        fallbackPath: String?,
        onPrepared: (sessionId: Int, durationMs: Long) -> Unit,
        onCompletion: () -> Unit,
        onFallbackRequired: (resumePositionMs: Long, reason: String) -> Unit
    ) {
        try {
            Process.setThreadPriority(Process.THREAD_PRIORITY_AUDIO)
        } catch (_: Throwable) {
        }

        var extractor: MediaExtractor? = null
        var codec: MediaCodec? = null
        var audioTrack: AudioTrack? = null
        var firstFrameCommitted = false

        try {
            extractor = MediaExtractor()
            openExtractorDataSource(extractor, uri, fallbackPath)

            var audioTrackIndex = -1
            var trackFormat: MediaFormat? = null
            var mime = ""
            for (i in 0 until extractor.trackCount) {
                val fmt = extractor.getTrackFormat(i)
                val m = fmt.getString(MediaFormat.KEY_MIME) ?: ""
                if (m.startsWith("audio/")) {
                    audioTrackIndex = i
                    trackFormat = fmt
                    mime = m
                    break
                }
            }

            if (audioTrackIndex < 0 || trackFormat == null) {
                throw IllegalStateException("No audio track found in URI")
            }

            extractor.selectTrack(audioTrackIndex)

            val initialSampleRate = readIntSafe(trackFormat, MediaFormat.KEY_SAMPLE_RATE, 44100).coerceIn(8000, 192000)
            val initialChannels = readIntSafe(trackFormat, MediaFormat.KEY_CHANNEL_COUNT, 2).coerceAtLeast(1)
            val initialEncoding = readPcmEncodingSafe(trackFormat)
            val durMs = if (trackFormat.containsKey(MediaFormat.KEY_DURATION)) {
                (trackFormat.getLong(MediaFormat.KEY_DURATION) / 1000L).coerceAtLeast(0L)
            } else {
                0L
            }
            durationAtomicMs.set(durMs)

            val isRawPcm = mime.equals("audio/raw", ignoreCase = true)
            if (!isRawPcm) {
                // Ensure ample input buffer size for high-bitrate FLAC/MP3/AAC packets
                try {
                    val existingMax = if (trackFormat.containsKey(MediaFormat.KEY_MAX_INPUT_SIZE)) {
                        trackFormat.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE)
                    } else {
                        0
                    }
                    if (existingMax < 262144) {
                        trackFormat.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 262144)
                    }
                } catch (_: Throwable) {
                }

                codec = MediaCodec.createDecoderByType(mime)
                codec.configure(trackFormat, null, null, 0)
                codec.start()
            }

            if (stopRequested.get() || activeGeneration.get() != generation) {
                return
            }

            val commitPreparedIfFirstFrame: (AudioTrack) -> Unit = { track ->
                if (!firstFrameCommitted && activeGeneration.get() == generation && !stopRequested.get()) {
                    firstFrameCommitted = true
                    isPrepared = true
                    isPlaying = !isPausedState
                    val sid = track.audioSessionId
                    audioSessionId = sid
                    mainHandler.post {
                        if (activeGeneration.get() == generation && !stopRequested.get()) {
                            onPrepared(sid, durMs)
                        }
                    }
                }
            }

            if (isRawPcm) {
                val track = createAndStartStereoTrack(initialSampleRate, generation)
                audioTrack = track
                dspProcessor.configureSampleRate(initialSampleRate)
                dspProcessor.resetTransientState()

                streamRawPcmLoop(
                    generation = generation,
                    extractor = extractor,
                    initialTrack = track,
                    sampleRate = initialSampleRate,
                    channelCount = initialChannels,
                    pcmEncoding = initialEncoding,
                    onFirstFrameWritten = commitPreparedIfFirstFrame
                ) { rebuilt -> audioTrack = rebuilt }
            } else {
                streamCodecLoop(
                    generation = generation,
                    extractor = extractor,
                    codec = codec!!,
                    initialSampleRate = initialSampleRate,
                    initialChannels = initialChannels,
                    initialPcmEncoding = initialEncoding,
                    onTrackAllocated = { allocated -> audioTrack = allocated },
                    onFirstFrameWritten = commitPreparedIfFirstFrame
                )
            }

            if (!stopRequested.get() && activeGeneration.get() == generation) {
                if (!firstFrameCommitted) {
                    throw IllegalStateException("Decoder produced no playable PCM frames")
                }
                isPlaying = false
                isPrepared = false
                try {
                    audioTrack?.stop()
                } catch (_: Throwable) {
                }
                mainHandler.post {
                    if (activeGeneration.get() == generation && !stopRequested.get()) {
                        onCompletion()
                    }
                }
            }
        } catch (t: Throwable) {
            Log.w(tag, "PCM pipeline fallback triggered (${t.message})")
            if (!stopRequested.get() && activeGeneration.get() == generation) {
                val resumeMs = if (firstFrameCommitted) currentPositionAtomicMs.get().coerceAtLeast(0L) else 0L
                mainHandler.post {
                    if (activeGeneration.get() == generation && !stopRequested.get()) {
                        onFallbackRequired(resumeMs, t.message ?: "Unsupported PCM stream")
                    }
                }
            }
        } finally {
            if (activeGeneration.get() == generation) {
                isPlaying = false
                isPrepared = false
                activeAudioTrack = null
            }
            safeReleaseTrack(audioTrack)
            safeReleaseCodec(codec)
            safeReleaseExtractor(extractor)
        }
    }

    private fun openExtractorDataSource(
        extractor: MediaExtractor,
        uri: Uri,
        fallbackPath: String?
    ) {
        try {
            extractor.setDataSource(context, uri, null)
            return
        } catch (primaryErr: Throwable) {
            Log.w(tag, "MediaExtractor uri open failed, trying AFD/path fallback: ${primaryErr.message}")
            // Fallback 1: ParcelFileDescriptor via ContentResolver (scoped storage friendly)
            if (uri.scheme.equals("content", ignoreCase = true)) {
                try {
                    context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { afd ->
                        if (afd.declaredLength >= 0) {
                            extractor.setDataSource(afd.fileDescriptor, afd.startOffset, afd.declaredLength)
                        } else {
                            extractor.setDataSource(afd.fileDescriptor)
                        }
                        return
                    }
                } catch (_: Throwable) {
                }
            }

            // Fallback 2: Direct file path descriptor for file:// URIs or known dataPath
            val pathCandidates = listOfNotNull(
                if (uri.scheme.equals("file", ignoreCase = true)) uri.path else null,
                fallbackPath?.takeIf { it.isNotBlank() }
            )
            for (path in pathCandidates) {
                try {
                    val f = File(path)
                    if (f.exists() && f.canRead()) {
                        FileInputStream(f).use { fis ->
                            extractor.setDataSource(fis.fd)
                            return
                        }
                    }
                } catch (_: Throwable) {
                }
            }
            throw primaryErr
        }
    }

    private fun streamRawPcmLoop(
        generation: Long,
        extractor: MediaExtractor,
        initialTrack: AudioTrack,
        sampleRate: Int,
        channelCount: Int,
        pcmEncoding: Int,
        onFirstFrameWritten: (AudioTrack) -> Unit,
        onTrackRebuilt: (AudioTrack) -> Unit
    ) {
        var track = initialTrack
        var rawBuf = ByteBuffer.allocateDirect(131072).order(ByteOrder.LITTLE_ENDIAN)
        var stereoFloats = FloatArray(16384)
        var outShorts = ShortArray(16384)
        var lastUiPushMs = 0L

        while (!stopRequested.get() && activeGeneration.get() == generation) {
            if (seekRequested.compareAndSet(true, false)) {
                val targetMs = pendingSeekMs.get().coerceAtLeast(0L)
                extractor.seekTo(targetMs * 1000L, MediaExtractor.SEEK_TO_CLOSEST_SYNC)
                flushAudioTrack(track)
                dspProcessor.notifyStreamDiscontinuity()
                currentPositionAtomicMs.set(targetMs)
            }

            waitWhilePaused(generation)
            if (stopRequested.get() || activeGeneration.get() != generation) break

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val nextSize = extractor.sampleSize
                if (nextSize > rawBuf.capacity()) {
                    rawBuf = ByteBuffer.allocateDirect((nextSize.toInt() + 4096).coerceAtMost(2 * 1024 * 1024))
                        .order(ByteOrder.LITTLE_ENDIAN)
                }
            }

            rawBuf.clear()
            val sampleSize = extractor.readSampleData(rawBuf, 0)
            if (sampleSize < 0) break

            val sampleTimeMs = (extractor.sampleTime / 1000L).coerceAtLeast(0L)
            rawBuf.limit(sampleSize)

            val bytesPerFrame = bytesPerSampleForEncoding(pcmEncoding) * channelCount
            val frameCount = if (bytesPerFrame > 0) sampleSize / bytesPerFrame else 0
            if (frameCount > 0) {
                val neededFloats = frameCount * 2
                if (stereoFloats.size < neededFloats) stereoFloats = FloatArray(neededFloats)
                if (outShorts.size < neededFloats) outShorts = ShortArray(neededFloats)

                decodePcmBytesToStereoFloat(rawBuf, pcmEncoding, channelCount, frameCount, stereoFloats)
                dspProcessor.processStereoBuffer(stereoFloats, frameCount)
                stereoFloatToShorts(stereoFloats, outShorts, frameCount)

                track = writeToTrackWithRecovery(
                    generation = generation,
                    track = track,
                    shorts = outShorts,
                    sampleCount = neededFloats,
                    sampleRate = sampleRate,
                    onTrackRebuilt = onTrackRebuilt
                )
                onFirstFrameWritten(track)
                currentPositionAtomicMs.set(sampleTimeMs)
                lastUiPushMs = maybePushUiTelemetry(stereoFloats, frameCount, sampleRate, lastUiPushMs)
            }

            if (!extractor.advance()) break
        }
    }

    private fun streamCodecLoop(
        generation: Long,
        extractor: MediaExtractor,
        codec: MediaCodec,
        initialSampleRate: Int,
        initialChannels: Int,
        initialPcmEncoding: Int,
        onTrackAllocated: (AudioTrack) -> Unit,
        onFirstFrameWritten: (AudioTrack) -> Unit
    ) {
        var track: AudioTrack? = null
        var currentSampleRate = initialSampleRate
        var currentChannels = initialChannels
        var currentEncoding = initialPcmEncoding

        val bufferInfo = MediaCodec.BufferInfo()
        var sawInputEos = false
        var sawOutputEos = false
        var stereoFloats = FloatArray(16384)
        var outShorts = ShortArray(16384)
        var lastUiPushMs = 0L

        while (!stopRequested.get() && activeGeneration.get() == generation && !sawOutputEos) {
            if (seekRequested.compareAndSet(true, false)) {
                val targetMs = pendingSeekMs.get().coerceAtLeast(0L)
                extractor.seekTo(targetMs * 1000L, MediaExtractor.SEEK_TO_CLOSEST_SYNC)
                try {
                    codec.flush()
                } catch (_: Throwable) {
                }
                track?.let { flushAudioTrack(it) }
                dspProcessor.notifyStreamDiscontinuity()
                sawInputEos = false
                sawOutputEos = false
                currentPositionAtomicMs.set(targetMs)
            }

            waitWhilePaused(generation)
            if (stopRequested.get() || activeGeneration.get() != generation) break

            var fedInput = false
            if (!sawInputEos) {
                val inIdx = codec.dequeueInputBuffer(10_000L)
                if (inIdx >= 0) {
                    val inputBuf = codec.getInputBuffer(inIdx)
                    if (inputBuf != null) {
                        inputBuf.clear()
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                            val needed = extractor.sampleSize
                            if (needed > inputBuf.capacity()) {
                                throw IllegalStateException("Packet size ($needed) exceeds codec input capacity (${inputBuf.capacity()})")
                            }
                        }
                        val sampleSize = extractor.readSampleData(inputBuf, 0)
                        if (sampleSize < 0) {
                            codec.queueInputBuffer(inIdx, 0, 0, 0L, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            sawInputEos = true
                        } else if (sampleSize == 0) {
                            extractor.advance()
                            codec.queueInputBuffer(inIdx, 0, 0, 0L, 0)
                        } else {
                            val pts = extractor.sampleTime.coerceAtLeast(0L)
                            codec.queueInputBuffer(inIdx, 0, sampleSize, pts, 0)
                            extractor.advance()
                            fedInput = true
                        }
                    }
                }
            }

            // Drain all currently decoded output buffers before looping back to input
            var drainTimeout = if (fedInput && !sawInputEos) 2_000L else 10_000L
            drainLoop@ while (!stopRequested.get() && activeGeneration.get() == generation && !sawOutputEos) {
                val outIdx = codec.dequeueOutputBuffer(bufferInfo, drainTimeout)
                drainTimeout = 0L

                when {
                    outIdx >= 0 -> {
                        val outBuf = codec.getOutputBuffer(outIdx)
                        if (outBuf != null && bufferInfo.size > 0) {
                            outBuf.position(bufferInfo.offset)
                            outBuf.limit(bufferInfo.offset + bufferInfo.size)
                            outBuf.order(ByteOrder.LITTLE_ENDIAN)

                            val bytesPerFrame = bytesPerSampleForEncoding(currentEncoding) * currentChannels
                            val frameCount = if (bytesPerFrame > 0) bufferInfo.size / bytesPerFrame else 0

                            if (frameCount > 0) {
                                val readyTrack = track ?: createAndStartStereoTrack(currentSampleRate, generation).also {
                                    track = it
                                    onTrackAllocated(it)
                                    dspProcessor.configureSampleRate(currentSampleRate)
                                    dspProcessor.resetTransientState()
                                }

                                val neededFloats = frameCount * 2
                                if (stereoFloats.size < neededFloats) stereoFloats = FloatArray(neededFloats)
                                if (outShorts.size < neededFloats) outShorts = ShortArray(neededFloats)

                                decodePcmBytesToStereoFloat(outBuf, currentEncoding, currentChannels, frameCount, stereoFloats)
                                dspProcessor.processStereoBuffer(stereoFloats, frameCount)
                                stereoFloatToShorts(stereoFloats, outShorts, frameCount)

                                val activeTrack = writeToTrackWithRecovery(
                                    generation = generation,
                                    track = readyTrack,
                                    shorts = outShorts,
                                    sampleCount = neededFloats,
                                    sampleRate = currentSampleRate
                                ) { rebuilt ->
                                    track = rebuilt
                                    onTrackAllocated(rebuilt)
                                }
                                track = activeTrack
                                onFirstFrameWritten(activeTrack)

                                val ptsMs = (bufferInfo.presentationTimeUs / 1000L).coerceAtLeast(0L)
                                if (!seekRequested.get()) {
                                    currentPositionAtomicMs.set(ptsMs)
                                }
                                lastUiPushMs = maybePushUiTelemetry(stereoFloats, frameCount, currentSampleRate, lastUiPushMs)
                            }
                        }

                        codec.releaseOutputBuffer(outIdx, false)
                        if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                            sawOutputEos = true
                            break@drainLoop
                        }
                    }
                    outIdx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        val outFmt = codec.outputFormat
                        val newSr = readIntSafe(outFmt, MediaFormat.KEY_SAMPLE_RATE, currentSampleRate).coerceIn(8000, 192000)
                        currentChannels = readIntSafe(outFmt, MediaFormat.KEY_CHANNEL_COUNT, currentChannels).coerceAtLeast(1)
                        currentEncoding = readPcmEncodingSafe(outFmt, currentEncoding)

                        if (track != null && newSr != currentSampleRate) {
                            currentSampleRate = newSr
                            dspProcessor.configureSampleRate(newSr)
                            val rebuilt = rebuildAudioTrack(track!!, newSr)
                            track = rebuilt
                            onTrackAllocated(rebuilt)
                        } else {
                            currentSampleRate = newSr
                        }
                    }
                    outIdx == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED -> {
                        // Deprecated on API 21+, safe to continue draining
                    }
                    else -> {
                        // INFO_TRY_AGAIN_LATER
                        break@drainLoop
                    }
                }
            }
        }
    }

    private fun writeToTrackWithRecovery(
        generation: Long,
        track: AudioTrack,
        shorts: ShortArray,
        sampleCount: Int,
        sampleRate: Int,
        onTrackRebuilt: (AudioTrack) -> Unit
    ): AudioTrack {
        var currentTrack = track
        var written = 0
        var trackRetries = 0
        var zeroRetries = 0

        while (written < sampleCount &&
            !stopRequested.get() &&
            activeGeneration.get() == generation &&
            !isPausedState &&
            !seekRequested.get()
        ) {
            val remaining = sampleCount - written
            val result = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                currentTrack.write(shorts, written, remaining, AudioTrack.WRITE_BLOCKING)
            } else {
                currentTrack.write(shorts, written, remaining)
            }

            if (result > 0) {
                written += result
                zeroRetries = 0
            } else if (result == 0) {
                zeroRetries++
                if (zeroRetries > 60) {
                    throw IllegalStateException("AudioTrack write stalled (0 bytes written)")
                }
                try {
                    Thread.sleep(4L)
                } catch (_: InterruptedException) {
                    break
                }
            } else if ((result == AudioTrack.ERROR_DEAD_OBJECT || result == AudioTrack.ERROR_INVALID_OPERATION) && trackRetries < 2) {
                trackRetries++
                Log.w(tag, "AudioTrack error ($result) — recovering AudioTrack cleanly")
                currentTrack = rebuildAudioTrack(currentTrack, sampleRate)
                onTrackRebuilt(currentTrack)
            } else {
                throw IllegalStateException("AudioTrack write failed (code=$result)")
            }
        }
        return currentTrack
    }

    private fun waitWhilePaused(generation: Long) {
        synchronized(pauseLock) {
            while (isPausedState && !stopRequested.get() && activeGeneration.get() == generation && !seekRequested.get()) {
                try {
                    pauseLock.wait(60L)
                } catch (_: InterruptedException) {
                    break
                }
            }
        }
    }

    private fun flushAudioTrack(track: AudioTrack) {
        try {
            track.pause()
            track.flush()
            if (!isPausedState) {
                track.play()
            }
        } catch (t: Throwable) {
            Log.w(tag, "flushAudioTrack warning: ${t.message}")
        }
    }

    private fun createAndStartStereoTrack(sampleRate: Int, generation: Long): AudioTrack {
        val newTrack = createStereoAudioTrack(sampleRate)
        activeAudioTrack = newTrack
        audioSessionId = newTrack.audioSessionId
        applyPlaybackParams(newTrack, playbackSpeed, playbackPitch)
        if (!isPausedState && activeGeneration.get() == generation && !stopRequested.get()) {
            newTrack.play()
        }
        return newTrack
    }

    private fun createStereoAudioTrack(sampleRate: Int): AudioTrack {
        val minBuf = AudioTrack.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_OUT_STEREO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        if (minBuf <= 0) {
            throw IllegalStateException("AudioTrack rejected sampleRate=$sampleRate (minBuf=$minBuf)")
        }
        val rawBuf = maxOf(minBuf * 3, 24576)
        // Align to stereo 16-bit frame boundary (4 bytes per frame)
        val bufferSize = ((rawBuf + 3) / 4) * 4

        val format = AudioFormat.Builder()
            .setSampleRate(sampleRate)
            .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
            .build()

        // First attempt with spatialization bypass; fallback to plain media attributes if OEM HAL rejects flags
        val extendedAttrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
            .apply {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    setAllowedCapturePolicy(AudioAttributes.ALLOW_CAPTURE_BY_ALL)
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S_V2) {
                    setSpatializationBehavior(AudioAttributes.SPATIALIZATION_BEHAVIOR_NEVER)
                }
            }
            .build()

        val firstAttempt = buildAudioTrackCompat(extendedAttrs, format, bufferSize)
        if (firstAttempt != null && firstAttempt.state == AudioTrack.STATE_INITIALIZED) {
            return firstAttempt
        }
        safeReleaseTrack(firstAttempt)

        val basicAttrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
            .build()
        val secondAttempt = buildAudioTrackCompat(basicAttrs, format, bufferSize)
        if (secondAttempt != null && secondAttempt.state == AudioTrack.STATE_INITIALIZED) {
            return secondAttempt
        }
        safeReleaseTrack(secondAttempt)
        throw IllegalStateException("AudioTrack initialization failed for sampleRate=$sampleRate")
    }

    private fun buildAudioTrackCompat(
        attrs: AudioAttributes,
        format: AudioFormat,
        bufferSize: Int
    ): AudioTrack? {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                AudioTrack.Builder()
                    .setAudioAttributes(attrs)
                    .setAudioFormat(format)
                    .setBufferSizeInBytes(bufferSize)
                    .setTransferMode(AudioTrack.MODE_STREAM)
                    .setSessionId(AudioManager.AUDIO_SESSION_ID_GENERATE)
                    .build()
            } else {
                AudioTrack(
                    attrs,
                    format,
                    bufferSize,
                    AudioTrack.MODE_STREAM,
                    AudioManager.AUDIO_SESSION_ID_GENERATE
                )
            }
        } catch (t: Throwable) {
            Log.w(tag, "AudioTrack build failed: ${t.message}")
            null
        }
    }

    private fun rebuildAudioTrack(oldTrack: AudioTrack, sampleRate: Int): AudioTrack {
        safeReleaseTrack(oldTrack)
        val newTrack = createStereoAudioTrack(sampleRate)
        activeAudioTrack = newTrack
        audioSessionId = newTrack.audioSessionId
        applyPlaybackParams(newTrack, playbackSpeed, playbackPitch)
        if (!isPausedState && !stopRequested.get()) {
            newTrack.play()
        }
        return newTrack
    }

    fun play() {
        synchronized(pauseLock) {
            isPausedState = false
            val track = activeAudioTrack
            if (track != null && isPrepared) {
                isPlaying = true
                try {
                    track.play()
                } catch (t: Throwable) {
                    Log.w(tag, "play() warning: ${t.message}")
                }
            }
            pauseLock.notifyAll()
        }
    }

    fun pause() {
        synchronized(pauseLock) {
            isPausedState = true
            isPlaying = false
            try {
                activeAudioTrack?.pause()
            } catch (t: Throwable) {
                Log.w(tag, "pause() warning: ${t.message}")
            }
            pauseLock.notifyAll()
        }
    }

    fun seekTo(positionMs: Long) {
        val clamped = positionMs.coerceIn(0L, durationAtomicMs.get().coerceAtLeast(positionMs.coerceAtLeast(0L)))
        pendingSeekMs.set(clamped)
        seekRequested.set(true)
        synchronized(pauseLock) {
            pauseLock.notifyAll()
        }
    }

    fun setPlaybackSpeed(speed: Float, pitch: Float = 1.0f) {
        playbackSpeed = speed.coerceIn(0.5f, 2.0f)
        playbackPitch = pitch.coerceIn(0.5f, 2.0f)
        activeAudioTrack?.let { applyPlaybackParams(it, playbackSpeed, playbackPitch) }
    }

    private fun applyPlaybackParams(track: AudioTrack, speed: Float, pitch: Float) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try {
                if (speed == 1.0f && pitch == 1.0f) return
                track.playbackParams = PlaybackParams()
                    .setSpeed(speed.coerceIn(0.5f, 2.0f))
                    .setPitch(pitch.coerceIn(0.5f, 2.0f))
            } catch (t: Throwable) {
                Log.w(tag, "PlaybackParams unsupported: ${t.message}")
            }
        }
    }

    fun stop() {
        activeGeneration.incrementAndGet()
        stopInternal()
    }

    private fun stopInternal() {
        stopRequested.set(true)
        isPlaying = false
        isPrepared = false

        val trackToStop = activeAudioTrack
        activeAudioTrack = null
        if (trackToStop != null) {
            try {
                trackToStop.pause()
                trackToStop.flush()
            } catch (_: Throwable) {
            }
        }

        synchronized(pauseLock) {
            isPausedState = false
            pauseLock.notifyAll()
        }

        val oldThread = workerThread
        workerThread = null
        if (oldThread != null && oldThread !== Thread.currentThread()) {
            try {
                oldThread.join(250L)
            } catch (_: InterruptedException) {
            }
        }
    }

    private fun ensureOscillatorTable(sampleRate: Int) {
        if (cachedOscSampleRate == sampleRate) return
        cachedOscSampleRate = sampleRate
        for (b in 0 until 32) {
            val norm = b / 31.0
            val freq = 40.0 * Math.pow(350.0, norm)
            val omega = (2.0 * PI * freq / sampleRate).coerceAtMost(PI * 0.9)
            oscCosW[b] = cos(omega)
            oscSinW[b] = sin(omega)
        }
    }

    private fun maybePushUiTelemetry(
        stereoFloats: FloatArray,
        frameCount: Int,
        sampleRate: Int,
        lastPushMs: Long
    ): Long {
        val now = System.currentTimeMillis()
        if (now - lastPushMs < 33L) return lastPushMs

        // Periodic underrun health check on API 24+ (pure integer read, zero allocation)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            activeAudioTrack?.let { tr ->
                try {
                    val ur = tr.underrunCount
                    if (ur > lastUnderrunCount) {
                        lastUnderrunCount = ur
                        Log.w(tag, "AudioTrack underrun detected (count=$ur)")
                    }
                } catch (_: Throwable) {
                }
            }
        }

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
                var cosSum = 0.0
                var sinSum = 0.0
                for (i in 0 until n) {
                    val mono = (stereoFloats[i * 2] + stereoFloats[i * 2 + 1]) * 0.5
                    cosSum += mono * c
                    sinSum += mono * s
                    val nextC = c * cosW - s * sinW
                    s = s * cosW + c * sinW
                    c = nextC
                }
                bins[b] = (sqrt(cosSum * cosSum + sinSum * sinSum) * invNorm).toFloat().coerceIn(0f, 1f)
            }
        } else {
            bins.fill(0f)
        }

        onVisualizerFrame(bins, wave)
        onAzimuthChanged(dspProcessor.currentAzimuthRad)
        return now
    }

    private fun decodePcmBytesToStereoFloat(
        buf: ByteBuffer,
        encoding: Int,
        channels: Int,
        frameCount: Int,
        outStereo: FloatArray
    ) {
        for (f in 0 until frameCount) {
            var left = 0f
            var right = 0f
            when (channels) {
                1 -> {
                    val m = readNormalizedSample(buf, encoding)
                    left = m
                    right = m
                }
                2 -> {
                    left = readNormalizedSample(buf, encoding)
                    right = readNormalizedSample(buf, encoding)
                }
                else -> {
                    left = readNormalizedSample(buf, encoding)
                    right = readNormalizedSample(buf, encoding)
                    val center = if (channels >= 3) readNormalizedSample(buf, encoding) * 0.707f else 0f
                    for (c in 3 until channels) {
                        val s = readNormalizedSample(buf, encoding) * 0.35f
                        if (c % 2 == 1) left += s else right += s
                    }
                    left = (left + center).coerceIn(-1.5f, 1.5f)
                    right = (right + center).coerceIn(-1.5f, 1.5f)
                }
            }
            outStereo[f * 2] = if (left.isFinite()) left else 0f
            outStereo[f * 2 + 1] = if (right.isFinite()) right else 0f
        }
    }

    private fun readNormalizedSample(buf: ByteBuffer, encoding: Int): Float {
        return when (encoding) {
            AudioFormat.ENCODING_PCM_FLOAT -> {
                if (buf.remaining() >= 4) buf.float else 0f
            }
            AudioFormat.ENCODING_PCM_8BIT -> {
                if (buf.remaining() >= 1) ((buf.get().toInt() and 0xFF) - 128) / 128f else 0f
            }
            AudioFormat.ENCODING_PCM_24BIT_PACKED -> {
                if (buf.remaining() >= 3) {
                    val b0 = buf.get().toInt() and 0xFF
                    val b1 = buf.get().toInt() and 0xFF
                    val b2 = buf.get().toInt()
                    val sample24 = (b2 shl 16) or (b1 shl 8) or b0
                    sample24 / 8388608f
                } else 0f
            }
            AudioFormat.ENCODING_PCM_32BIT -> {
                if (buf.remaining() >= 4) buf.int / 2147483648f else 0f
            }
            else -> {
                if (buf.remaining() >= 2) buf.short / 32768f else 0f
            }
        }
    }

    private fun stereoFloatToShorts(stereo: FloatArray, outShorts: ShortArray, frameCount: Int) {
        val count = frameCount * 2
        for (i in 0 until count) {
            val s = stereo[i]
            val clamped = if (s.isFinite()) s.coerceIn(-1f, 1f) else 0f
            outShorts[i] = (clamped * 32767f).toInt().coerceIn(-32767, 32767).toShort()
        }
    }

    private fun bytesPerSampleForEncoding(encoding: Int): Int = when (encoding) {
        AudioFormat.ENCODING_PCM_8BIT -> 1
        AudioFormat.ENCODING_PCM_24BIT_PACKED -> 3
        AudioFormat.ENCODING_PCM_32BIT, AudioFormat.ENCODING_PCM_FLOAT -> 4
        else -> 2
    }

    private fun readIntSafe(fmt: MediaFormat, key: String, fallback: Int): Int {
        return try {
            if (fmt.containsKey(key)) fmt.getInteger(key) else fallback
        } catch (_: Throwable) {
            fallback
        }
    }

    private fun readPcmEncodingSafe(fmt: MediaFormat, fallback: Int = AudioFormat.ENCODING_PCM_16BIT): Int {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            readIntSafe(fmt, MediaFormat.KEY_PCM_ENCODING, fallback)
        } else {
            fallback
        }
    }

    private fun safeReleaseTrack(track: AudioTrack?) {
        try {
            track?.pause()
            track?.flush()
            track?.release()
        } catch (_: Throwable) {
        }
    }

    private fun safeReleaseCodec(codec: MediaCodec?) {
        try {
            codec?.stop()
        } catch (_: Throwable) {
        }
        try {
            codec?.release()
        } catch (_: Throwable) {
        }
    }

    private fun safeReleaseExtractor(extractor: MediaExtractor?) {
        try {
            extractor?.release()
        } catch (_: Throwable) {
        }
    }
}
