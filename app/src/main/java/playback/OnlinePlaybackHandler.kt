package playback

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.HttpDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import com.example.audio.SonoraDspProcessor
import data.online.NewPipeDownloader
import data.online.NewPipeInitializer
import domain.online.ResolvedOnlineStreamResult

/**
 * Manages Media3 [ExoPlayer] for online audio streaming, buffering, seeking, speed/pitch,
 * HTTP error detection, and unified PCM DSP processing through [SonoraMedia3DspProcessor].
 */
@OptIn(UnstableApi::class)
class OnlinePlaybackHandler(
    private val context: Context,
    private val dspProcessor: SonoraDspProcessor,
    private val onVisualizerFrame: (fftBins: FloatArray, waveformSamples: FloatArray) -> Unit,
    private val onAzimuthChanged: (Float) -> Unit,
    private val onBufferingChanged: (Boolean) -> Unit
) {

    private val tag = "OnlinePlaybackHandler"
    private val mainHandler = Handler(Looper.getMainLooper())

    private var exoPlayer: ExoPlayer? = null
    private var preparedNotifiedForCurrentItem = false
    private var activeContentUrl: String? = null
    private var activeHeaders: Map<String, String> = emptyMap()

    private var onPreparedCallback: ((durationMs: Long) -> Unit)? = null
    private var onCompletionCallback: (() -> Unit)? = null
    private var onErrorCallback: ((message: String, mayBeExpiredStream: Boolean) -> Unit)? = null

    @Volatile
    var isPrepared: Boolean = false
        private set

    @Volatile
    var isPlaying: Boolean = false
        private set

    @Volatile
    var isBuffering: Boolean = false
        private set

    @Volatile
    var cachedDurationMs: Long = 0L
        private set

    @Volatile
    var cachedPositionMs: Long = 0L
        private set

    @Volatile
    var cachedBufferedPositionMs: Long = 0L
        private set

    @Volatile
    var audioSessionId: Int = 0
        private set

    private val httpDataSourceFactory: OkHttpDataSource.Factory by lazy {
        OkHttpDataSource.Factory(NewPipeInitializer.getDownloader().okHttpClient)
            .setUserAgent(NewPipeDownloader.DEFAULT_USER_AGENT)
    }

    private fun runOnMain(block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            block()
        } else {
            mainHandler.post(block)
        }
    }

    private fun ensurePlayerCreated(): ExoPlayer {
        exoPlayer?.let { return it }

        val dspBridge = SonoraMedia3DspProcessor(
            dspProcessor = dspProcessor,
            onVisualizerFrame = onVisualizerFrame,
            onAzimuthChanged = onAzimuthChanged
        )

        val renderersFactory = object : DefaultRenderersFactory(context) {
            override fun buildAudioSink(
                context: Context,
                enableFloatOutput: Boolean,
                enableAudioOutputPlaybackParameters: Boolean
            ): AudioSink {
                return DefaultAudioSink.Builder(context)
                    .setEnableFloatOutput(enableFloatOutput)
                    .setEnableAudioOutputPlaybackParameters(enableAudioOutputPlaybackParameters)
                    .setAudioProcessors(arrayOf(dspBridge))
                    .build()
            }
        }

        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                /* minBufferMs = */ 15_000,
                /* maxBufferMs = */ 45_000,
                /* bufferForPlaybackMs = */ 1_500,
                /* bufferForPlaybackAfterRebufferMs = */ 3_000
            )
            .setPrioritizeTimeOverSizeThresholds(true)
            .build()

        val mediaSourceFactory = DefaultMediaSourceFactory(httpDataSourceFactory)

        val player = ExoPlayer.Builder(context, renderersFactory)
            .setMediaSourceFactory(mediaSourceFactory)
            .setLoadControl(loadControl)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
                /* handleAudioFocus = */ false
            )
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .build()

        player.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                when (playbackState) {
                    Player.STATE_BUFFERING -> {
                        isBuffering = true
                        onBufferingChanged(true)
                    }
                    Player.STATE_READY -> {
                        isBuffering = false
                        onBufferingChanged(false)
                        isPrepared = true
                        val dur = player.duration.takeIf { it != C.TIME_UNSET && it > 0L } ?: 0L
                        if (dur > 0L) cachedDurationMs = dur
                        cachedPositionMs = player.currentPosition.coerceAtLeast(0L)
                        cachedBufferedPositionMs = player.bufferedPosition.coerceAtLeast(0L)
                        audioSessionId = player.audioSessionId
                        isPlaying = player.isPlaying
                        if (!preparedNotifiedForCurrentItem) {
                            preparedNotifiedForCurrentItem = true
                            onPreparedCallback?.invoke(cachedDurationMs)
                        }
                    }
                    Player.STATE_ENDED -> {
                        isBuffering = false
                        onBufferingChanged(false)
                        isPlaying = false
                        isPrepared = false
                        onCompletionCallback?.invoke()
                    }
                    Player.STATE_IDLE -> {
                        isBuffering = false
                        onBufferingChanged(false)
                        isPlaying = false
                    }
                }
            }

            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
                player.currentPosition.let { pos ->
                    if (pos >= 0L) cachedPositionMs = pos
                }
                player.bufferedPosition.let { buf ->
                    if (buf >= 0L) cachedBufferedPositionMs = buf
                }
            }

            override fun onAudioSessionIdChanged(newSessionId: Int) {
                audioSessionId = newSessionId
            }

            override fun onPlayerError(error: PlaybackException) {
                isBuffering = false
                onBufferingChanged(false)
                isPlaying = false
                isPrepared = false
                val expired = isLikelyExpiredOrForbiddenHttpError(error)
                val userMessage = buildFriendlyPlaybackErrorMessage(error, expired)
                Log.w(tag, "ExoPlayer playback error (expired=$expired): ${error.errorCodeName}", error)
                onErrorCallback?.invoke(userMessage, expired)
            }
        })

        exoPlayer = player
        return player
    }

    fun prepareAndPlayStream(
        resolved: ResolvedOnlineStreamResult,
        startPositionMs: Long = 0L,
        speed: Float = 1.0f,
        pitch: Float = 1.0f,
        onPrepared: (durationMs: Long) -> Unit,
        onCompletion: () -> Unit,
        onError: (message: String, mayBeExpiredStream: Boolean) -> Unit
    ) {
        runOnMain {
            val player = ensurePlayerCreated()
            preparedNotifiedForCurrentItem = false
            isPrepared = false
            isPlaying = false
            isBuffering = true
            onBufferingChanged(true)

            activeContentUrl = resolved.contentUrl
            cachedDurationMs = resolved.durationMs
            cachedPositionMs = startPositionMs.coerceAtLeast(0L)
            cachedBufferedPositionMs = 0L

            onPreparedCallback = onPrepared
            onCompletionCallback = onCompletion
            onErrorCallback = onError

            val headers = HashMap(resolved.selectedStream.headers)
            if (headers.keys.none { it.equals("User-Agent", ignoreCase = true) }) {
                headers["User-Agent"] = NewPipeDownloader.DEFAULT_USER_AGENT
            }
            activeHeaders = headers
            httpDataSourceFactory.setDefaultRequestProperties(headers)

            val mediaItemBuilder = MediaItem.Builder()
                .setUri(resolved.selectedStream.url)

            // Provide explicit MIME hint when known for HLS/DASH/WebM/MP4
            val mime = resolved.selectedStream.mimeType
            if (mime.isNotBlank() && mime != "audio/*") {
                mediaItemBuilder.setMimeType(mime)
            }

            dspProcessor.notifyStreamDiscontinuity()
            player.stop()
            player.clearMediaItems()
            player.setMediaItem(mediaItemBuilder.build(), startPositionMs.coerceAtLeast(0L))
            player.playbackParameters = PlaybackParameters(
                speed.coerceIn(0.5f, 2.0f),
                pitch.coerceIn(0.5f, 2.0f)
            )
            player.prepare()
            player.playWhenReady = true
            player.play()
        }
    }

    fun play() {
        runOnMain {
            val player = exoPlayer ?: return@runOnMain
            if (!isPrepared) return@runOnMain
            player.playWhenReady = true
            player.play()
            isPlaying = true
        }
    }

    fun pause() {
        runOnMain {
            val player = exoPlayer ?: return@runOnMain
            player.playWhenReady = false
            player.pause()
            isPlaying = false
        }
    }

    fun seekTo(positionMs: Long) {
        val safe = positionMs.coerceAtLeast(0L)
        cachedPositionMs = safe
        runOnMain {
            val player = exoPlayer ?: return@runOnMain
            if (isPrepared) {
                dspProcessor.notifyStreamDiscontinuity()
                player.seekTo(safe)
            }
        }
    }

    fun setPlaybackSpeed(speed: Float, pitch: Float = 1.0f) {
        val s = speed.coerceIn(0.5f, 2.0f)
        val p = pitch.coerceIn(0.5f, 2.0f)
        runOnMain {
            exoPlayer?.playbackParameters = PlaybackParameters(s, p)
        }
    }

    fun refreshPositionSnapshot(): Long {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            exoPlayer?.let { p ->
                if (isPrepared) {
                    cachedPositionMs = p.currentPosition.coerceAtLeast(0L)
                    cachedBufferedPositionMs = p.bufferedPosition.coerceAtLeast(0L)
                    val dur = p.duration.takeIf { it != C.TIME_UNSET && it > 0L } ?: 0L
                    if (dur > 0L) cachedDurationMs = dur
                }
            }
        } else {
            mainHandler.post {
                exoPlayer?.let { p ->
                    if (isPrepared) {
                        cachedPositionMs = p.currentPosition.coerceAtLeast(0L)
                        cachedBufferedPositionMs = p.bufferedPosition.coerceAtLeast(0L)
                        val dur = p.duration.takeIf { it != C.TIME_UNSET && it > 0L } ?: 0L
                        if (dur > 0L) cachedDurationMs = dur
                    }
                }
            }
        }
        return cachedPositionMs
    }

    fun stop() {
        isPlaying = false
        isPrepared = false
        isBuffering = false
        onBufferingChanged(false)
        preparedNotifiedForCurrentItem = false
        onPreparedCallback = null
        onCompletionCallback = null
        onErrorCallback = null
        runOnMain {
            exoPlayer?.apply {
                playWhenReady = false
                stop()
                clearMediaItems()
            }
        }
    }

    fun release() {
        stop()
        runOnMain {
            exoPlayer?.release()
            exoPlayer = null
        }
    }

    private fun isLikelyExpiredOrForbiddenHttpError(error: PlaybackException): Boolean {
        var cause: Throwable? = error.cause
        while (cause != null) {
            if (cause is HttpDataSource.InvalidResponseCodeException) {
                val code = cause.responseCode
                if (code == 403 || code == 404 || code == 410 || code == 416) {
                    return true
                }
            }
            cause = cause.cause
        }
        return error.errorCode == PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS
    }

    private fun buildFriendlyPlaybackErrorMessage(error: PlaybackException, expired: Boolean): String {
        if (expired) {
            return "Stream link expired. Retrying extraction..."
        }
        return when (error.errorCode) {
            PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
            PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT ->
                "Network connection interrupted while streaming."
            PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED,
            PlaybackException.ERROR_CODE_DECODING_FAILED ->
                "Audio stream format is unsupported on this device."
            else -> error.localizedMessage?.takeIf { it.isNotBlank() } ?: "Online playback failed (${error.errorCodeName})."
        }
    }
}
