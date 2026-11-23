package playback

import android.app.ActivityManager
import android.content.Context
import android.os.SystemClock
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.Format
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DecoderCounters
import androidx.media3.exoplayer.DecoderReuseEvaluation
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.analytics.AnalyticsListener

/**
 * Memory- and network-aware Media3 LoadControl tuning profile for SONORA Online Video.
 *
 * Avoids blindly maximizing buffer sizes (which causes GC stalls and OOM on low-RAM devices)
 * while maintaining fast startup and resilient rebuffer recovery on 4G / weak mobile networks.
 */
data class VideoLoadControlProfile(
    val minBufferMs: Int,
    val maxBufferMs: Int,
    val bufferForPlaybackMs: Int,
    val bufferForPlaybackAfterRebufferMs: Int,
    val backBufferDurationMs: Int,
    val targetBufferBytes: Int,
    val prioritizeTimeOverSizeThresholds: Boolean,
    val isLowMemoryProfile: Boolean
) {
    @OptIn(UnstableApi::class)
    fun buildLoadControl(): DefaultLoadControl {
        val builder = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                minBufferMs,
                maxBufferMs,
                bufferForPlaybackMs,
                bufferForPlaybackAfterRebufferMs
            )
            .setBackBuffer(backBufferDurationMs, /* retainBackBufferFromKeyframe = */ true)
            .setPrioritizeTimeOverSizeThresholds(prioritizeTimeOverSizeThresholds)
        if (targetBufferBytes > 0) {
            builder.setTargetBufferBytes(targetBufferBytes)
        }
        return builder.build()
    }

    companion object {
        private const val LOW_RAM_THRESHOLD_BYTES = 3L * 1024L * 1024L * 1024L

        fun forDeviceMemory(isLowRamDevice: Boolean, totalMemoryBytes: Long): VideoLoadControlProfile {
            val useLowMem = isLowRamDevice || (totalMemoryBytes in 1L until LOW_RAM_THRESHOLD_BYTES)
            return if (useLowMem) {
                VideoLoadControlProfile(
                    minBufferMs = 10_000,
                    maxBufferMs = 25_000,
                    bufferForPlaybackMs = 1_500,
                    bufferForPlaybackAfterRebufferMs = 3_000,
                    backBufferDurationMs = 2_000,
                    targetBufferBytes = 16 * 1024 * 1024,
                    prioritizeTimeOverSizeThresholds = true,
                    isLowMemoryProfile = true
                )
            } else {
                VideoLoadControlProfile(
                    minBufferMs = 15_000,
                    maxBufferMs = 45_000,
                    bufferForPlaybackMs = 1_500,
                    bufferForPlaybackAfterRebufferMs = 3_000,
                    backBufferDurationMs = 5_000,
                    targetBufferBytes = DefaultLoadControl.DEFAULT_TARGET_BUFFER_BYTES,
                    prioritizeTimeOverSizeThresholds = true,
                    isLowMemoryProfile = false
                )
            }
        }

        fun forContext(context: Context): VideoLoadControlProfile {
            val am = context.applicationContext.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
            val memInfo = ActivityManager.MemoryInfo()
            val totalMem = runCatching {
                am?.getMemoryInfo(memInfo)
                memInfo.totalMem
            }.getOrDefault(0L)
            val isLowRam = runCatching { am?.isLowRamDevice == true }.getOrDefault(false)
            return forDeviceMemory(isLowRamDevice = isLowRam, totalMemoryBytes = totalMem)
        }
    }
}

/**
 * Snapshot of internal video playback performance diagnostics.
 * Logged internally via Logcat for testing and tuning; never exposed to normal users.
 */
data class VideoPlaybackMetricsSnapshot(
    val videoId: String? = null,
    val extractionDurationMs: Long = 0L,
    val timeToFirstFrameMs: Long = -1L,
    val initialBufferingDurationMs: Long = -1L,
    val rebufferCount: Int = 0,
    val totalRebufferDurationMs: Long = 0L,
    val selectedResolution: String = "",
    val selectedCodec: String = "",
    val selectedMimeType: String = "",
    val selectedBitrateBps: Int = 0,
    val videoDecoderName: String = "",
    val audioDecoderName: String = "",
    val decoderInitFailureCount: Int = 0,
    val playbackErrorCount: Int = 0,
    val lastErrorCodeName: String? = null,
    val droppedFramesTotal: Long = 0L
)

/**
 * Internal Media3 [AnalyticsListener] tracking video startup latency, buffering stalls,
 * format/codec selection, decoder failures, and dropped frames.
 */
@OptIn(UnstableApi::class)
class VideoPlaybackDiagnostics(
    private val elapsedRealtimeProvider: () -> Long = { SystemClock.elapsedRealtime() }
) : AnalyticsListener {

    companion object {
        private const val TAG = "SonoraVideoPerf"
    }

    @Volatile
    private var snapshot = VideoPlaybackMetricsSnapshot()

    @Volatile
    private var loadStartMs: Long = 0L

    @Volatile
    private var rebufferStartMs: Long = 0L

    @Volatile
    private var hasRenderedFirstFrame: Boolean = false

    @Volatile
    private var hasReachedInitialReady: Boolean = false

    fun currentSnapshot(): VideoPlaybackMetricsSnapshot = snapshot

    fun markVideoLoadStarted(videoId: String, extractionDurationMs: Long = 0L) {
        val now = elapsedRealtimeProvider()
        loadStartMs = now
        rebufferStartMs = 0L
        hasRenderedFirstFrame = false
        hasReachedInitialReady = false
        snapshot = VideoPlaybackMetricsSnapshot(
            videoId = videoId,
            extractionDurationMs = extractionDurationMs.coerceAtLeast(0L)
        )
        Log.d(TAG, "load_start videoId=$videoId extractMs=$extractionDurationMs")
    }

    fun recordStateTransition(playbackState: Int) {
        val now = elapsedRealtimeProvider()
        when (playbackState) {
            Player.STATE_READY -> {
                if (!hasReachedInitialReady && loadStartMs > 0L) {
                    hasReachedInitialReady = true
                    val initialBuf = (now - loadStartMs).coerceAtLeast(0L)
                    snapshot = snapshot.copy(initialBufferingDurationMs = initialBuf)
                    Log.d(TAG, "initial_ready videoId=${snapshot.videoId} initialBufferMs=$initialBuf")
                }
                if (rebufferStartMs > 0L) {
                    val stallMs = (now - rebufferStartMs).coerceAtLeast(0L)
                    rebufferStartMs = 0L
                    snapshot = snapshot.copy(
                        totalRebufferDurationMs = snapshot.totalRebufferDurationMs + stallMs
                    )
                    Log.d(
                        TAG,
                        "rebuffer_end videoId=${snapshot.videoId} stallMs=$stallMs totalRebufferMs=${snapshot.totalRebufferDurationMs} count=${snapshot.rebufferCount}"
                    )
                }
            }
            Player.STATE_BUFFERING -> {
                if (hasReachedInitialReady && rebufferStartMs == 0L) {
                    rebufferStartMs = now
                    snapshot = snapshot.copy(rebufferCount = snapshot.rebufferCount + 1)
                    Log.d(TAG, "rebuffer_start videoId=${snapshot.videoId} count=${snapshot.rebufferCount}")
                }
            }
            Player.STATE_IDLE, Player.STATE_ENDED -> {
                if (rebufferStartMs > 0L) {
                    val stallMs = (now - rebufferStartMs).coerceAtLeast(0L)
                    rebufferStartMs = 0L
                    snapshot = snapshot.copy(
                        totalRebufferDurationMs = snapshot.totalRebufferDurationMs + stallMs
                    )
                }
            }
        }
    }

    fun recordFirstFrameRendered() {
        if (!hasRenderedFirstFrame && loadStartMs > 0L) {
            hasRenderedFirstFrame = true
            val ttff = (elapsedRealtimeProvider() - loadStartMs).coerceAtLeast(0L)
            snapshot = snapshot.copy(timeToFirstFrameMs = ttff)
            Log.d(
                TAG,
                "first_frame videoId=${snapshot.videoId} ttffMs=$ttff extractMs=${snapshot.extractionDurationMs} res=${snapshot.selectedResolution} codec=${snapshot.selectedCodec}"
            )
        }
    }

    fun recordDroppedFrames(count: Int) {
        if (count <= 0) return
        val updated = snapshot.droppedFramesTotal + count.toLong()
        snapshot = snapshot.copy(droppedFramesTotal = updated)
        if (count >= 15) {
            Log.w(
                TAG,
                "dropped_frames videoId=${snapshot.videoId} burst=$count total=$updated res=${snapshot.selectedResolution} decoder=${snapshot.videoDecoderName}"
            )
        }
    }

    fun recordVideoFormat(width: Int, height: Int, codecs: String?, mimeType: String?, bitrate: Int) {
        val resLabel = if (width > 0 && height > 0) "${width}x${height}" else snapshot.selectedResolution
        snapshot = snapshot.copy(
            selectedResolution = resLabel,
            selectedCodec = codecs.orEmpty(),
            selectedMimeType = mimeType.orEmpty(),
            selectedBitrateBps = bitrate.coerceAtLeast(0)
        )
        Log.d(
            TAG,
            "video_format videoId=${snapshot.videoId} res=$resLabel codec=${codecs.orEmpty()} mime=${mimeType.orEmpty()} bps=$bitrate"
        )
    }

    fun recordPlaybackError(errorCodeName: String, isDecoderFailure: Boolean) {
        snapshot = snapshot.copy(
            playbackErrorCount = snapshot.playbackErrorCount + 1,
            decoderInitFailureCount = snapshot.decoderInitFailureCount + if (isDecoderFailure) 1 else 0,
            lastErrorCodeName = errorCodeName
        )
        Log.w(
            TAG,
            "playback_error videoId=${snapshot.videoId} code=$errorCodeName decoderFailure=$isDecoderFailure errors=${snapshot.playbackErrorCount}"
        )
    }

    override fun onPlaybackStateChanged(eventTime: AnalyticsListener.EventTime, state: Int) {
        recordStateTransition(state)
    }

    override fun onRenderedFirstFrame(
        eventTime: AnalyticsListener.EventTime,
        output: Any,
        renderTimeMs: Long
    ) {
        recordFirstFrameRendered()
    }

    override fun onDroppedVideoFrames(
        eventTime: AnalyticsListener.EventTime,
        droppedFrames: Int,
        elapsedMs: Long
    ) {
        recordDroppedFrames(droppedFrames)
    }

    override fun onVideoInputFormatChanged(
        eventTime: AnalyticsListener.EventTime,
        format: Format,
        decoderReuseEvaluation: DecoderReuseEvaluation?
    ) {
        recordVideoFormat(
            width = format.width,
            height = format.height,
            codecs = format.codecs,
            mimeType = format.sampleMimeType ?: format.containerMimeType,
            bitrate = format.bitrate
        )
    }

    override fun onVideoDecoderInitialized(
        eventTime: AnalyticsListener.EventTime,
        decoderName: String,
        initializedTimestampMs: Long,
        initializationDurationMs: Long
    ) {
        snapshot = snapshot.copy(videoDecoderName = decoderName)
        Log.d(TAG, "video_decoder name=$decoderName initMs=$initializationDurationMs")
    }

    override fun onAudioDecoderInitialized(
        eventTime: AnalyticsListener.EventTime,
        decoderName: String,
        initializedTimestampMs: Long,
        initializationDurationMs: Long
    ) {
        snapshot = snapshot.copy(audioDecoderName = decoderName)
    }

    override fun onVideoDisabled(eventTime: AnalyticsListener.EventTime, decoderCounters: DecoderCounters) {
        val s = snapshot
        if (s.videoId != null) {
            Log.d(
                TAG,
                "session_summary videoId=${s.videoId} ttffMs=${s.timeToFirstFrameMs} initBufMs=${s.initialBufferingDurationMs} rebuffers=${s.rebufferCount} rebufMs=${s.totalRebufferDurationMs} dropped=${s.droppedFramesTotal} res=${s.selectedResolution} decoder=${s.videoDecoderName}"
            )
        }
    }

    override fun onPlayerError(eventTime: AnalyticsListener.EventTime, error: PlaybackException) {
        val isDecoderErr = error.errorCode == PlaybackException.ERROR_CODE_DECODER_INIT_FAILED ||
            error.errorCode == PlaybackException.ERROR_CODE_DECODING_FAILED ||
            error.errorCode == PlaybackException.ERROR_CODE_DECODER_QUERY_FAILED
        recordPlaybackError(error.errorCodeName, isDecoderErr)
    }
}
