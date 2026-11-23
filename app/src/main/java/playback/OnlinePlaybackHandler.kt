package playback

import android.app.ActivityManager
import android.content.Context
import android.net.Uri
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
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.HttpDataSource
import androidx.media3.datasource.TransferListener
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import com.example.audio.SonoraDspProcessor
import com.example.model.OnlinePlaybackStage
import data.cache.SonoraMediaCacheManager
import data.online.NewPipeDownloader
import data.online.NewPipeInitializer
import domain.online.ResolvedOnlineStreamResult
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * Manages the single Media3 [ExoPlayer] instance for online audio streaming, next-track playlist staging,
 * adaptive buffering, seeking, speed/pitch, HTTP error detection, and unified PCM DSP processing
 * through [SonoraMedia3DspProcessor].
 */
@OptIn(UnstableApi::class)
class OnlinePlaybackHandler(
    private val context: Context,
    private val dspProcessor: SonoraDspProcessor,
    private val onVisualizerFrame: (fftBins: FloatArray, waveformSamples: FloatArray) -> Unit,
    private val onAzimuthChanged: (Float) -> Unit,
    private val onBufferingChanged: (Boolean) -> Unit,
    private val onStageChanged: (OnlinePlaybackStage, String) -> Unit = { _, _ -> },
    private val cacheManager: SonoraMediaCacheManager = SonoraMediaCacheManager.getInstance(context)
) {

    private val tag = "OnlinePlaybackHandler"
    private val mainHandler = Handler(Looper.getMainLooper())

    private var exoPlayer: ExoPlayer? = null
    private val streamTokenGenerator = AtomicLong(0L)

    @Volatile
    private var activeStreamToken: Long = 0L

    private var preparedNotifiedForCurrentItem = false

    @Volatile
    private var activeContentUrl: String? = null

    @Volatile
    private var stagedNextContentUrl: String? = null

    @Volatile
    private var stagedNextStreamUrl: String? = null

    @Volatile
    private var onPreparedCallback: ((durationMs: Long) -> Unit)? = null

    @Volatile
    private var onCompletionCallback: (() -> Unit)? = null

    @Volatile
    private var onErrorCallback: ((message: String, mayBeExpiredStream: Boolean) -> Unit)? = null

    @Volatile
    private var onAutoTransitionCallback: ((mediaId: String) -> Unit)? = null

    private val perStreamHeaders = ConcurrentHashMap<String, Map<String, String>>()

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
    var currentStage: OnlinePlaybackStage = OnlinePlaybackStage.IDLE
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
    var isCurrentItemSeekable: Boolean = true
        private set

    @Volatile
    var isCurrentItemLive: Boolean = false
        private set

    @Volatile
    var audioSessionId: Int = 0
        private set

    private val upstreamHttpFactory: OkHttpDataSource.Factory by lazy {
        OkHttpDataSource.Factory(NewPipeInitializer.getDownloader().okHttpClient)
            .setUserAgent(NewPipeDownloader.DEFAULT_USER_AGENT)
    }

    /**
     * Stateless per-request wrapper around [upstreamHttpFactory] so Track A and a pre-buffered Track B
     * can maintain independent HTTP headers without mutating global default request properties.
     */
    private val perStreamDataSourceFactory: DataSource.Factory by lazy {
        DataSource.Factory {
            val delegate = upstreamHttpFactory.createDataSource()
            object : DataSource {
                override fun addTransferListener(transferListener: TransferListener) {
                    delegate.addTransferListener(transferListener)
                }

                override fun open(dataSpec: DataSpec): Long {
                    val key = dataSpec.uri.toString()
                    val customHeaders = perStreamHeaders[key]
                    val finalSpec = if (!customHeaders.isNullOrEmpty()) {
                        dataSpec.withAdditionalHeaders(customHeaders)
                    } else {
                        dataSpec
                    }
                    return delegate.open(finalSpec)
                }

                override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
                    return delegate.read(buffer, offset, length)
                }

                override fun getUri(): Uri? = delegate.uri

                override fun getResponseHeaders(): MutableMap<String, MutableList<String>> {
                    return delegate.responseHeaders
                }

                override fun close() {
                    delegate.close()
                }
            }
        }
    }

    private fun runOnMain(block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            block()
        } else {
            mainHandler.post(block)
        }
    }

    private fun updateStage(stage: OnlinePlaybackStage, message: String = stage.defaultMessage) {
        currentStage = stage
        val buffering = stage == OnlinePlaybackStage.RESOLVING_STREAM ||
            stage == OnlinePlaybackStage.PREPARING_PLAYER ||
            stage == OnlinePlaybackStage.BUFFERING_AUDIO
        if (isBuffering != buffering) {
            isBuffering = buffering
            onBufferingChanged(buffering)
        }
        onStageChanged(stage, message)
    }

    private fun buildAdaptiveLoadControl(appContext: Context): DefaultLoadControl {
        val am = appContext.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        val isLowRam = am?.isLowRamDevice == true
        val maxHeapMb = (Runtime.getRuntime().maxMemory() / (1024L * 1024L)).coerceAtLeast(64L)
        val constrainedMemory = isLowRam || maxHeapMb <= 192L

        return if (constrainedMemory) {
            DefaultLoadControl.Builder()
                .setBufferDurationsMs(
                    /* minBufferMs = */ 10_000,
                    /* maxBufferMs = */ 18_000,
                    /* bufferForPlaybackMs = */ 650,
                    /* bufferForPlaybackAfterRebufferMs = */ 1_600
                )
                .setTargetBufferBytes(6 * 1024 * 1024)
                .setBackBuffer(2_000, false)
                .setPrioritizeTimeOverSizeThresholds(true)
                .build()
        } else {
            DefaultLoadControl.Builder()
                .setBufferDurationsMs(
                    /* minBufferMs = */ 14_000,
                    /* maxBufferMs = */ 32_000,
                    /* bufferForPlaybackMs = */ 550,
                    /* bufferForPlaybackAfterRebufferMs = */ 1_400
                )
                .setTargetBufferBytes(16 * 1024 * 1024)
                .setBackBuffer(4_000, false)
                .setPrioritizeTimeOverSizeThresholds(true)
                .build()
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

        val loadControl = buildAdaptiveLoadControl(context)
        val mediaSourceFactory = DefaultMediaSourceFactory(
            cacheManager.createMusicDataSourceFactory(perStreamDataSourceFactory)
        )

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
                val tokenAtEvent = activeStreamToken
                if (tokenAtEvent == 0L || onErrorCallback == null && playbackState == Player.STATE_IDLE) {
                    if (playbackState == Player.STATE_IDLE) {
                        isPlaying = false
                        isPrepared = false
                        if (isBuffering) {
                            isBuffering = false
                            onBufferingChanged(false)
                        }
                    }
                    return
                }
                when (playbackState) {
                    Player.STATE_BUFFERING -> {
                        if (tokenAtEvent != activeStreamToken) return
                        updateStage(OnlinePlaybackStage.BUFFERING_AUDIO)
                    }

                    Player.STATE_READY -> {
                        if (tokenAtEvent != activeStreamToken) return
                        handleReadyState(player, tokenAtEvent)
                    }

                    Player.STATE_ENDED -> {
                        if (tokenAtEvent != activeStreamToken) return
                        isPlaying = false
                        isPrepared = false
                        stagedNextContentUrl = null
                        stagedNextStreamUrl = null
                        updateStage(OnlinePlaybackStage.IDLE, "")
                        onCompletionCallback?.invoke()
                    }

                    Player.STATE_IDLE -> {
                        isPlaying = false
                        isPrepared = false
                        if (isBuffering) {
                            isBuffering = false
                            onBufferingChanged(false)
                        }
                    }
                }
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                if (reason != Player.MEDIA_ITEM_TRANSITION_REASON_AUTO || mediaItem == null) return
                val expectedStagedId = stagedNextContentUrl ?: return
                if (mediaItem.mediaId != expectedStagedId) return

                val autoCb = onAutoTransitionCallback ?: return
                val curIdx = player.currentMediaItemIndex
                if (curIdx > 0) {
                    runCatching { player.removeMediaItems(0, curIdx) }
                }
                stagedNextContentUrl = null
                stagedNextStreamUrl = null
                activeContentUrl = expectedStagedId
                activeStreamToken = streamTokenGenerator.incrementAndGet()
                cacheManager.setActiveMusicCacheKey(mediaItem.localConfiguration?.customCacheKey)
                preparedNotifiedForCurrentItem = true
                dspProcessor.notifyStreamDiscontinuity()

                val dur = player.duration.takeIf { it != C.TIME_UNSET && it > 0L } ?: 0L
                cachedDurationMs = dur
                cachedPositionMs = player.currentPosition.coerceAtLeast(0L)
                cachedBufferedPositionMs = player.bufferedPosition.coerceAtLeast(0L)
                isCurrentItemLive = player.isCurrentMediaItemLive
                isCurrentItemSeekable = player.isCurrentMediaItemSeekable && !player.isCurrentMediaItemLive && dur > 0L
                audioSessionId = player.audioSessionId

                if (player.playbackState == Player.STATE_READY) {
                    isPrepared = true
                    isPlaying = player.isPlaying
                    updateStage(OnlinePlaybackStage.PLAYING)
                } else {
                    isPrepared = false
                    updateStage(OnlinePlaybackStage.BUFFERING_AUDIO)
                }
                autoCb.invoke(expectedStagedId)
            }

            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
                if (playing && currentStage == OnlinePlaybackStage.BUFFERING_AUDIO && player.playbackState == Player.STATE_READY) {
                    updateStage(OnlinePlaybackStage.PLAYING)
                }
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
                val tokenAtEvent = activeStreamToken
                if (tokenAtEvent == 0L) return
                isPlaying = false
                isPrepared = false
                val errorCb = onErrorCallback ?: return
                val expired = isLikelyExpiredOrForbiddenHttpError(error)
                val userMessage = buildFriendlyPlaybackErrorMessage(error, expired)
                Log.w(tag, "ExoPlayer playback error (expired=$expired): ${error.errorCodeName}", error)
                updateStage(OnlinePlaybackStage.FAILED, userMessage)
                errorCb.invoke(userMessage, expired)
            }
        })

        exoPlayer = player
        return player
    }

    private fun handleReadyState(player: ExoPlayer, tokenAtEvent: Long) {
        if (tokenAtEvent != activeStreamToken) return
        isPrepared = true
        val dur = player.duration.takeIf { it != C.TIME_UNSET && it > 0L } ?: 0L
        if (dur > 0L) cachedDurationMs = dur
        cachedPositionMs = player.currentPosition.coerceAtLeast(0L)
        cachedBufferedPositionMs = player.bufferedPosition.coerceAtLeast(0L)
        isCurrentItemLive = player.isCurrentMediaItemLive
        isCurrentItemSeekable = player.isCurrentMediaItemSeekable && !player.isCurrentMediaItemLive && cachedDurationMs > 0L
        audioSessionId = player.audioSessionId
        isPlaying = player.isPlaying || player.playWhenReady
        updateStage(OnlinePlaybackStage.PLAYING)
        if (!preparedNotifiedForCurrentItem) {
            preparedNotifiedForCurrentItem = true
            onPreparedCallback?.invoke(cachedDurationMs)
        }
    }

    private fun registerHeaders(streamUrl: String, rawHeaders: Map<String, String>) {
        val headers = HashMap(rawHeaders)
        if (headers.keys.none { it.equals("User-Agent", ignoreCase = true) }) {
            headers["User-Agent"] = NewPipeDownloader.DEFAULT_USER_AGENT
        }
        perStreamHeaders[streamUrl] = headers
        if (perStreamHeaders.size > 24) {
            val activeUrl = stagedNextStreamUrl
            val iter = perStreamHeaders.keys.iterator()
            while (perStreamHeaders.size > 16 && iter.hasNext()) {
                val k = iter.next()
                if (k != streamUrl && k != activeUrl) {
                    iter.remove()
                }
            }
        }
    }

    private fun buildMediaItem(resolved: ResolvedOnlineStreamResult): MediaItem {
        registerHeaders(resolved.selectedStream.url, resolved.selectedStream.headers)
        val builder = MediaItem.Builder()
            .setMediaId(resolved.contentUrl)
            .setUri(resolved.selectedStream.url)
        val mime = resolved.selectedStream.mimeType
        if (mime.isNotBlank() && mime != "audio/*") {
            builder.setMimeType(mime)
        }
        val isCacheable = cacheManager.isCachePermitted(
            url = resolved.selectedStream.url,
            isLive = resolved.durationMs <= 0L,
            hasDrm = false
        )
        if (isCacheable) {
            builder.setCustomCacheKey(
                cacheManager.buildMusicCacheKey(resolved.contentUrl, resolved.selectedStream.id)
            )
        }
        return builder.build()
    }

    fun prepareAndPlayStream(
        resolved: ResolvedOnlineStreamResult,
        startPositionMs: Long = 0L,
        speed: Float = 1.0f,
        pitch: Float = 1.0f,
        onPrepared: (durationMs: Long) -> Unit,
        onCompletion: () -> Unit,
        onError: (message: String, mayBeExpiredStream: Boolean) -> Unit,
        onAutoTransitionToNext: ((mediaId: String) -> Unit)? = null
    ) {
        runOnMain {
            val player = ensurePlayerCreated()
            val reqToken = streamTokenGenerator.incrementAndGet()
            activeStreamToken = reqToken

            // Clear old callbacks BEFORE player.stop() so synchronous STATE_IDLE cannot emit stale failures
            onPreparedCallback = null
            onCompletionCallback = null
            onErrorCallback = null
            onAutoTransitionCallback = onAutoTransitionToNext

            preparedNotifiedForCurrentItem = false
            isPrepared = false
            isPlaying = false
            activeContentUrl = resolved.contentUrl
            cachedDurationMs = resolved.durationMs
            cachedPositionMs = startPositionMs.coerceAtLeast(0L)
            cachedBufferedPositionMs = 0L

            updateStage(OnlinePlaybackStage.PREPARING_PLAYER)

            val safeStartPos = startPositionMs.coerceAtLeast(0L)
            val curIdx = player.currentMediaItemIndex
            val nextIdx = curIdx + 1
            val canSeekToStagedNext = safeStartPos == 0L &&
                stagedNextContentUrl == resolved.contentUrl &&
                stagedNextStreamUrl == resolved.selectedStream.url &&
                player.playbackState != Player.STATE_IDLE &&
                nextIdx < player.mediaItemCount &&
                player.getMediaItemAt(nextIdx).mediaId == resolved.contentUrl

            onPreparedCallback = onPrepared
            onCompletionCallback = onCompletion
            onErrorCallback = onError

            // Foreground playback always takes priority over speculative preloading
            cacheManager.cancelBackgroundPreloadsForForegroundPlayback()
            cacheManager.onMusicPlayerStateChanged(isOpen = true)

            if (canSeekToStagedNext) {
                stagedNextContentUrl = null
                stagedNextStreamUrl = null
                val nextKey = runCatching { player.getMediaItemAt(nextIdx).localConfiguration?.customCacheKey }.getOrNull()
                cacheManager.setActiveMusicCacheKey(nextKey)
                dspProcessor.notifyStreamDiscontinuity()
                player.seekToNextMediaItem()
                val newCurIdx = player.currentMediaItemIndex
                if (newCurIdx > 0) {
                    runCatching { player.removeMediaItems(0, newCurIdx) }
                }
                player.playbackParameters = PlaybackParameters(
                    speed.coerceIn(0.5f, 2.0f),
                    pitch.coerceIn(0.5f, 2.0f)
                )
                player.playWhenReady = true
                player.play()

                if (player.playbackState == Player.STATE_READY) {
                    handleReadyState(player, reqToken)
                } else {
                    updateStage(OnlinePlaybackStage.BUFFERING_AUDIO)
                }
                Log.d(tag, "Instant staged handoff for ${resolved.title}")
                return@runOnMain
            }

            stagedNextContentUrl = null
            stagedNextStreamUrl = null
            val mediaItem = buildMediaItem(resolved)
            val cacheKey = mediaItem.localConfiguration?.customCacheKey
            cacheManager.setActiveMusicCacheKey(cacheKey)

            // Evaluate Cache Music policy freshly on every new track load so toggling OFF stops new writes immediately
            val allowCacheWrite = cacheKey != null && cacheManager.shouldAllowMusicCacheWrites()
            val mediaSourceFactory = DefaultMediaSourceFactory(
                cacheManager.createMusicDataSourceFactory(perStreamDataSourceFactory, allowCacheWrite = allowCacheWrite)
            )

            dspProcessor.notifyStreamDiscontinuity()
            player.stop()
            player.clearMediaItems()
            player.setMediaSource(mediaSourceFactory.createMediaSource(mediaItem), safeStartPos)
            player.playbackParameters = PlaybackParameters(
                speed.coerceIn(0.5f, 2.0f),
                pitch.coerceIn(0.5f, 2.0f)
            )
            player.prepare()
            player.playWhenReady = true
            player.play()
        }
    }

    /**
     * Stages a pre-resolved next track into the single [ExoPlayer] playlist right after the currently
     * playing track without interrupting active playback, letting Media3 pre-buffer it ahead of time.
     */
    fun stageNextStream(resolvedNext: ResolvedOnlineStreamResult) {
        runOnMain {
            val player = exoPlayer ?: return@runOnMain
            if (activeContentUrl == null || activeContentUrl == resolvedNext.contentUrl) return@runOnMain
            if (player.playbackState == Player.STATE_IDLE || player.mediaItemCount == 0) return@runOnMain

            val curIdx = player.currentMediaItemIndex.coerceAtLeast(0)
            val existingNextIdx = curIdx + 1
            if (stagedNextContentUrl == resolvedNext.contentUrl &&
                stagedNextStreamUrl == resolvedNext.selectedStream.url &&
                existingNextIdx < player.mediaItemCount &&
                player.getMediaItemAt(existingNextIdx).mediaId == resolvedNext.contentUrl
            ) {
                return@runOnMain
            }

            val nextItem = buildMediaItem(resolvedNext)
            val allowCacheWrite = nextItem.localConfiguration?.customCacheKey != null &&
                cacheManager.shouldAllowMusicCacheWrites()
            val mediaSourceFactory = DefaultMediaSourceFactory(
                cacheManager.createMusicDataSourceFactory(perStreamDataSourceFactory, allowCacheWrite = allowCacheWrite)
            )
            if (existingNextIdx < player.mediaItemCount) {
                player.removeMediaItems(existingNextIdx, player.mediaItemCount)
            }
            player.addMediaSource(mediaSourceFactory.createMediaSource(nextItem))
            stagedNextContentUrl = resolvedNext.contentUrl
            stagedNextStreamUrl = resolvedNext.selectedStream.url
            Log.d(tag, "Staged next stream into single ExoPlayer queue: ${resolvedNext.title}")
        }
    }

    /**
     * Preloads a small bounded audio window of an upcoming resolved track into Media3 Music SimpleCache,
     * honoring network conditions, Data Saver, low storage, and the Preload Upcoming Media toggle.
     */
    fun preloadNextTrackWindow(resolvedNext: ResolvedOnlineStreamResult) {
        if (!cacheManager.isCachePermitted(resolvedNext.selectedStream.url, isLive = resolvedNext.durationMs <= 0L)) {
            return
        }
        registerHeaders(resolvedNext.selectedStream.url, resolvedNext.selectedStream.headers)
        val key = cacheManager.buildMusicCacheKey(resolvedNext.contentUrl, resolvedNext.selectedStream.id)
        cacheManager.preloadMusicWindow(
            cacheKey = key,
            streamUrl = resolvedNext.selectedStream.url,
            headers = resolvedNext.selectedStream.headers,
            upstreamFactory = perStreamDataSourceFactory
        )
    }

    fun clearStagedNextStream() {
        runOnMain {
            val player = exoPlayer ?: return@runOnMain
            stagedNextContentUrl = null
            stagedNextStreamUrl = null
            val nextIdx = player.currentMediaItemIndex.coerceAtLeast(0) + 1
            if (nextIdx < player.mediaItemCount) {
                runCatching { player.removeMediaItems(nextIdx, player.mediaItemCount) }
            }
        }
    }

    fun isNextStreamStaged(contentUrl: String): Boolean {
        return contentUrl.isNotBlank() && stagedNextContentUrl == contentUrl
    }

    fun play() {
        runOnMain {
            val player = exoPlayer ?: return@runOnMain
            if (!isPrepared) return@runOnMain
            player.playWhenReady = true
            player.play()
            isPlaying = true
            if (currentStage != OnlinePlaybackStage.BUFFERING_AUDIO) {
                updateStage(OnlinePlaybackStage.PLAYING)
            }
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
        if (!isCurrentItemSeekable || isCurrentItemLive) return
        val maxDur = cachedDurationMs
        val safe = if (maxDur > 0L) positionMs.coerceIn(0L, maxDur) else positionMs.coerceAtLeast(0L)
        cachedPositionMs = safe
        runOnMain {
            val player = exoPlayer ?: return@runOnMain
            if (isPrepared && player.isCurrentMediaItemSeekable && !player.isCurrentMediaItemLive) {
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
                    isCurrentItemLive = p.isCurrentMediaItemLive
                    isCurrentItemSeekable = p.isCurrentMediaItemSeekable && !p.isCurrentMediaItemLive && cachedDurationMs > 0L
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
                        isCurrentItemLive = p.isCurrentMediaItemLive
                        isCurrentItemSeekable = p.isCurrentMediaItemSeekable && !p.isCurrentMediaItemLive && cachedDurationMs > 0L
                    }
                }
            }
        }
        return cachedPositionMs
    }

    fun stop() {
        activeStreamToken = 0L
        activeContentUrl = null
        stagedNextContentUrl = null
        stagedNextStreamUrl = null
        isPlaying = false
        isPrepared = false
        preparedNotifiedForCurrentItem = false
        onPreparedCallback = null
        onCompletionCallback = null
        onErrorCallback = null
        onAutoTransitionCallback = null
        cacheManager.setActiveMusicCacheKey(null)
        cacheManager.onMusicPlayerStateChanged(isOpen = false)
        updateStage(OnlinePlaybackStage.IDLE, "")
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
        perStreamHeaders.clear()
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
            return "Stream link expired. Switching format or refreshing..."
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
