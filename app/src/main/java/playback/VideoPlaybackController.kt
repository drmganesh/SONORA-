package playback

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.media.MediaCodecList
import android.view.SurfaceView
import android.view.TextureView
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import androidx.media3.common.VideoSize
import androidx.media3.common.text.CueGroup
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.HttpDataSource
import androidx.media3.datasource.TransferListener
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.MergingMediaSource
import androidx.media3.exoplayer.trackselection.AdaptiveTrackSelection
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.exoplayer.upstream.DefaultBandwidthMeter
import data.cache.SonoraMediaCacheManager
import data.online.NewPipeDownloader
import data.online.NewPipeInitializer
import domain.online.OnlineErrorKind
import domain.video.ResolvedVideoDetails
import domain.video.VideoAudioOption
import domain.video.VideoPlaybackProgress
import domain.video.VideoStreamOption
import domain.video.VideoSubtitleOption
import okhttp3.ConnectionPool
import okhttp3.Protocol
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * Dedicated Media3 ExoPlayer controller exclusively for SONORA's Online Video mode.
 *
 * Architectural Guarantees:
 * - Maintains a completely separate playback configuration from [OnlinePlaybackHandler] and
 *   [com.example.audio.SonoraAudioEngine] so the 15-band Equalizer and Audio Studio DSP pipeline
 *   are never altered or disrupted by video playback.
 * - Single reusable [ExoPlayer] instance across video-to-video transitions and orientation changes.
 * - Supports muxed progressive streams, adaptive video-only + audio [MergingMediaSource], HLS/DASH,
 *   external VTT/TTML/SRT subtitles with live [CueGroup] emission, speed control, and clean surface
 *   attachment/detachment via [TextureView] for both fullscreen and rounded mini-player rendering.
 * - Policy compliant: pauses video playback automatically when app goes to background unless PiP is active.
 */
@OptIn(UnstableApi::class)
class VideoPlaybackController(
    private val context: Context,
    private val onStateSnapshot: (VideoPlaybackSnapshot) -> Unit,
    private val onVideoEnded: () -> Unit,
    private val onError: (message: String, kind: OnlineErrorKind, mayBeExpired: Boolean) -> Unit,
    private val onProgressTick: (VideoPlaybackProgress) -> Unit = {},
    private val cacheManager: SonoraMediaCacheManager = SonoraMediaCacheManager.getInstance(context)
) {
    data class VideoPlaybackSnapshot(
        val isPlaying: Boolean,
        val isBuffering: Boolean,
        val currentPositionMs: Long,
        val durationMs: Long,
        val bufferedPositionMs: Long,
        val videoWidth: Int,
        val videoHeight: Int,
        val pixelWidthHeightRatio: Float,
        val unappliedRotationDegrees: Int,
        val hasKnownDimensions: Boolean,
        val subtitleCues: List<String>,
        val estimatedBandwidthBps: Long = 0L,
        val rebufferCount: Int = 0,
        val actualVideoCodec: String = "",
        val actualVideoMimeType: String = "",
        val actualVideoBitrateKbps: Int = 0,
        val actualVideoFps: Float = 0f
    )

    private val tag = "VideoPlaybackController"
    private val mainHandler = Handler(Looper.getMainLooper())
    private var exoPlayer: ExoPlayer? = null
    private var trackSelector: DefaultTrackSelector? = null
    val diagnostics: VideoPlaybackDiagnostics = VideoPlaybackDiagnostics()
    private val bandwidthMeter: DefaultBandwidthMeter by lazy {
        DefaultBandwidthMeter.Builder(context.applicationContext)
            .setResetOnNetworkTypeChange(true)
            .build()
    }
    private var attachedSurfaceView: SurfaceView? = null
    private var attachedTextureView: TextureView? = null
    private val perStreamHeaders = ConcurrentHashMap<String, Map<String, String>>()

    @Volatile
    private var hasReachedReadyForCurrentVideo: Boolean = false

    @Volatile
    var rebufferCount: Int = 0
        private set

    fun estimatedBandwidthBps(): Long = bandwidthMeter.bitrateEstimate

    @Volatile
    private var lastVideoWidth: Int = 0

    @Volatile
    private var lastVideoHeight: Int = 0

    @Volatile
    private var lastPixelWidthHeightRatio: Float = 1f

    @Volatile
    private var lastUnappliedRotationDegrees: Int = 0

    @Volatile
    private var hasKnownDimensions: Boolean = false

    @Volatile
    private var lastMediaId: String? = null

    @Volatile
    private var lastCues: List<String> = emptyList()

    @Volatile
    private var lastVideoCodec: String = ""

    @Volatile
    private var lastVideoMimeType: String = ""

    @Volatile
    private var lastVideoBitrateKbps: Int = 0

    @Volatile
    private var lastVideoFps: Float = 0f

    private val cachedDeviceMaxVideoHeightPx: Int by lazy { computeDeviceMaxVideoHeightPx() }

    fun detectDeviceMaxVideoHeightPx(): Int = cachedDeviceMaxVideoHeightPx

    private fun computeDeviceMaxVideoHeightPx(): Int {
        return runCatching {
            val codecList = MediaCodecList(MediaCodecList.REGULAR_CODECS)
            var maxHeight = 1080
            for (info in codecList.codecInfos) {
                if (info.isEncoder) continue
                for (type in info.supportedTypes) {
                    if (type.startsWith("video/", ignoreCase = true)) {
                        val caps = runCatching { info.getCapabilitiesForType(type) }.getOrNull() ?: continue
                        val videoCaps = caps.videoCapabilities ?: continue
                        val supportedUpper = videoCaps.supportedHeights?.upper ?: 0
                        if (supportedUpper > maxHeight) {
                            maxHeight = supportedUpper
                        }
                    }
                }
            }
            maxHeight.coerceIn(1080, 4320)
        }.getOrDefault(2160)
    }

    @Volatile
    var isPlaying: Boolean = false
        private set

    private val streamingOkHttpClient by lazy {
        NewPipeInitializer.getDownloader().okHttpClient.newBuilder()
            .connectionPool(ConnectionPool(12, 5, TimeUnit.MINUTES))
            .protocols(listOf(Protocol.HTTP_2, Protocol.HTTP_1_1))
            .connectTimeout(12, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }

    private val upstreamHttpFactory: OkHttpDataSource.Factory by lazy {
        OkHttpDataSource.Factory(streamingOkHttpClient)
            .setUserAgent(NewPipeDownloader.DEFAULT_USER_AGENT)
            .setTransferListener(bandwidthMeter)
    }

    private val perStreamDataSourceFactory: DataSource.Factory by lazy {
        DataSource.Factory {
            val delegate = upstreamHttpFactory.createDataSource()
            object : DataSource {
                override fun addTransferListener(transferListener: TransferListener) {
                    delegate.addTransferListener(transferListener)
                }

                override fun open(dataSpec: DataSpec): Long {
                    val custom = perStreamHeaders[dataSpec.uri.toString()]
                    val finalSpec = if (!custom.isNullOrEmpty()) {
                        dataSpec.withAdditionalHeaders(custom)
                    } else {
                        dataSpec
                    }
                    return delegate.open(finalSpec)
                }

                override fun read(buffer: ByteArray, offset: Int, length: Int): Int =
                    delegate.read(buffer, offset, length)

                override fun getUri(): Uri? = delegate.uri
                override fun getResponseHeaders(): MutableMap<String, MutableList<String>> =
                    delegate.responseHeaders

                override fun close() = delegate.close()
            }
        }
    }

    private fun runOnMain(block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) block() else mainHandler.post(block)
    }

    private fun ensurePlayer(): ExoPlayer {
        exoPlayer?.let { return it }

        val loadControlProfile = VideoLoadControlProfile.forContext(context)
        val loadControl = loadControlProfile.buildLoadControl()

        val renderersFactory = DefaultRenderersFactory(context.applicationContext)
            .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_OFF)
            .setEnableDecoderFallback(true)

        val mediaSourceFactory = DefaultMediaSourceFactory(
            cacheManager.createVideoDataSourceFactory(perStreamDataSourceFactory, isShort = false)
        )
        val adaptiveFactory = AdaptiveTrackSelection.Factory(
            /* minDurationForQualityIncreaseMs = */ 6_000,
            /* maxDurationForQualityDecreaseMs = */ 20_000,
            /* minDurationToRetainAfterDiscardMs = */ 20_000,
            /* bandwidthFraction = */ 0.85f
        )
        val selector = DefaultTrackSelector(context.applicationContext, adaptiveFactory).apply {
            parameters = buildUponParameters()
                .setMaxVideoSize(Int.MAX_VALUE, cachedDeviceMaxVideoHeightPx)
                .setExceedVideoConstraintsIfNecessary(true)
                .setExceedRendererCapabilitiesIfNecessary(true)
                .build()
        }
        trackSelector = selector

        val player = ExoPlayer.Builder(context.applicationContext, renderersFactory)
            .setTrackSelector(selector)
            .setBandwidthMeter(bandwidthMeter)
            .setMediaSourceFactory(mediaSourceFactory)
            .setLoadControl(loadControl)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                    .build(),
                /* handleAudioFocus = */ true
            )
            .build()

        player.videoScalingMode = C.VIDEO_SCALING_MODE_SCALE_TO_FIT
        player.addAnalyticsListener(diagnostics)

        player.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_READY) {
                    hasReachedReadyForCurrentVideo = true
                } else if (playbackState == Player.STATE_BUFFERING && hasReachedReadyForCurrentVideo) {
                    rebufferCount++
                }
                emitSnapshot(player)
                if (playbackState == Player.STATE_ENDED) {
                    isPlaying = false
                    onVideoEnded()
                }
            }

            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
                emitSnapshot(player)
            }

            override fun onVideoSizeChanged(videoSize: VideoSize) {
                if (videoSize.width > 0 && videoSize.height > 0) {
                    lastVideoWidth = videoSize.width
                    lastVideoHeight = videoSize.height
                    lastPixelWidthHeightRatio = if (videoSize.pixelWidthHeightRatio > 0f) {
                        videoSize.pixelWidthHeightRatio
                    } else {
                        1f
                    }
                    lastUnappliedRotationDegrees = videoSize.unappliedRotationDegrees
                    hasKnownDimensions = true
                    captureActiveVideoFormat(player)
                    emitSnapshot(player)
                }
            }

            override fun onTracksChanged(tracks: Tracks) {
                var updated = captureActiveVideoFormat(player)
                for (group in tracks.groups) {
                    if (group.type == C.TRACK_TYPE_VIDEO && group.isSelected) {
                        for (i in 0 until group.length) {
                            if (group.isTrackSelected(i)) {
                                val fmt = group.getTrackFormat(i)
                                if (fmt.width > 0 && fmt.height > 0 && lastVideoHeight == 0) {
                                    lastVideoWidth = fmt.width
                                    lastVideoHeight = fmt.height
                                    if (fmt.pixelWidthHeightRatio > 0f) {
                                        lastPixelWidthHeightRatio = fmt.pixelWidthHeightRatio
                                    }
                                    hasKnownDimensions = true
                                }
                                if (!fmt.codecs.isNullOrBlank()) lastVideoCodec = fmt.codecs.orEmpty()
                                if (!fmt.sampleMimeType.isNullOrBlank()) lastVideoMimeType = fmt.sampleMimeType.orEmpty()
                                if (fmt.bitrate > 0) lastVideoBitrateKbps = fmt.bitrate / 1000
                                if (fmt.frameRate > 0f) lastVideoFps = fmt.frameRate
                                updated = true
                                break
                            }
                        }
                    }
                }
                if (updated) emitSnapshot(player)
            }

            override fun onCues(cueGroup: CueGroup) {
                lastCues = cueGroup.cues.mapNotNull { it.text?.toString()?.trim()?.takeIf { s -> s.isNotEmpty() } }
                emitSnapshot(player)
            }

            override fun onPlayerError(error: PlaybackException) {
                isPlaying = false
                val httpCode = extractHttpCode(error)
                val expired = httpCode == 403 || httpCode == 404 || httpCode == 410
                val kind = when {
                    httpCode == 429 -> OnlineErrorKind.RATE_LIMITED_OR_CAPTCHA
                    expired -> OnlineErrorKind.STREAM_EXPIRED
                    error.errorCode == PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED ||
                        error.errorCode == PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT ->
                        OnlineErrorKind.TIMEOUT
                    error.errorCode == PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED ||
                        error.errorCode == PlaybackException.ERROR_CODE_DECODING_FAILED ->
                        OnlineErrorKind.UNSUPPORTED
                    else -> OnlineErrorKind.EXTRACTION_FAILED
                }
                val friendly = when {
                    httpCode == 403 -> "Video stream returned HTTP 403. Refreshing or switching resolution..."
                    httpCode == 429 -> "Video provider temporarily rate-limited playback (HTTP 429)."
                    kind == OnlineErrorKind.UNSUPPORTED -> "Unsupported video format on this device. Try a lower resolution."
                    else -> error.localizedMessage?.takeIf { it.isNotBlank() } ?: "Video playback error (${error.errorCodeName})."
                }
                Log.w(tag, "Video ExoPlayer error (http=$httpCode): ${error.errorCodeName}", error)
                emitSnapshot(player)
                onError(friendly, kind, expired)
            }
        })

        attachedSurfaceView?.let { sv -> player.setVideoSurfaceView(sv) }
            ?: attachedTextureView?.let { tv -> player.setVideoTextureView(tv) }
        exoPlayer = player
        return player
    }

    private fun emitProgress(player: ExoPlayer) {
        val dur = player.duration.takeIf { it != C.TIME_UNSET && it > 0L } ?: 0L
        val pos = player.currentPosition.coerceAtLeast(0L)
        val buf = player.bufferedPosition.coerceAtLeast(0L)
        onProgressTick(
            VideoPlaybackProgress(
                currentPositionMs = pos,
                durationMs = dur,
                bufferedPositionMs = buf,
                estimatedBandwidthBps = runCatching { bandwidthMeter.bitrateEstimate }.getOrDefault(0L)
            )
        )
    }

    private fun captureActiveVideoFormat(player: ExoPlayer): Boolean {
        val fmt = player.videoFormat ?: return false
        var changed = false
        val codec = fmt.codecs.orEmpty()
        if (codec.isNotBlank() && codec != lastVideoCodec) {
            lastVideoCodec = codec
            changed = true
        }
        val mime = fmt.sampleMimeType.orEmpty()
        if (mime.isNotBlank() && mime != lastVideoMimeType) {
            lastVideoMimeType = mime
            changed = true
        }
        val bitrateKbps = if (fmt.bitrate > 0) fmt.bitrate / 1000 else 0
        if (bitrateKbps > 0 && bitrateKbps != lastVideoBitrateKbps) {
            lastVideoBitrateKbps = bitrateKbps
            changed = true
        }
        val fps = if (fmt.frameRate > 0f) fmt.frameRate else 0f
        if (fps > 0f && fps != lastVideoFps) {
            lastVideoFps = fps
            changed = true
        }
        return changed
    }

    private fun emitSnapshot(player: ExoPlayer) {
        val dur = player.duration.takeIf { it != C.TIME_UNSET && it > 0L } ?: 0L
        val pos = player.currentPosition.coerceAtLeast(0L)
        val buf = player.bufferedPosition.coerceAtLeast(0L)
        val buffering = player.playbackState == Player.STATE_BUFFERING
        val bw = runCatching { bandwidthMeter.bitrateEstimate }.getOrDefault(0L)
        onProgressTick(
            VideoPlaybackProgress(
                currentPositionMs = pos,
                durationMs = dur,
                bufferedPositionMs = buf,
                estimatedBandwidthBps = bw
            )
        )
        onStateSnapshot(
            VideoPlaybackSnapshot(
                isPlaying = player.isPlaying,
                isBuffering = buffering,
                currentPositionMs = pos,
                durationMs = dur,
                bufferedPositionMs = buf,
                videoWidth = lastVideoWidth,
                videoHeight = lastVideoHeight,
                pixelWidthHeightRatio = lastPixelWidthHeightRatio,
                unappliedRotationDegrees = lastUnappliedRotationDegrees,
                hasKnownDimensions = hasKnownDimensions,
                subtitleCues = lastCues,
                estimatedBandwidthBps = bw,
                rebufferCount = rebufferCount,
                actualVideoCodec = lastVideoCodec,
                actualVideoMimeType = lastVideoMimeType,
                actualVideoBitrateKbps = lastVideoBitrateKbps,
                actualVideoFps = lastVideoFps
            )
        )
    }

    fun currentEstimatedBandwidthBps(): Long =
        runCatching { bandwidthMeter.bitrateEstimate }.getOrDefault(0L)

    fun currentRebufferCount(): Int = rebufferCount

    fun applyAdaptiveMaxVideoHeight(maxHeightPx: Int) {
        runOnMain {
            val selector = trackSelector ?: return@runOnMain
            val capped = maxHeightPx.coerceIn(144, 4320)
            selector.parameters = selector.buildUponParameters()
                .setMaxVideoSize(Int.MAX_VALUE, capped)
                .build()
        }
    }

    fun prepareLocalVideo(
        videoId: String,
        localUri: String,
        startPositionMs: Long = 0L,
        speed: Float = 1.0f,
        playWhenReady: Boolean = true
    ) {
        runOnMain {
            val player = ensurePlayer()
            lastCues = emptyList()
            lastMediaId = videoId
            hasReachedReadyForCurrentVideo = false
            rebufferCount = 0
            diagnostics.markVideoLoadStarted(videoId, extractionDurationMs = 0L)
            lastVideoWidth = 0
            lastVideoHeight = 0
            lastPixelWidthHeightRatio = 1f
            lastUnappliedRotationDegrees = 0
            hasKnownDimensions = false

            val localSourceFactory = DefaultMediaSourceFactory(DefaultDataSource.Factory(context.applicationContext))
            cacheManager.onVideoPlayerStateChanged(isOpen = true)
            cacheManager.setActiveVideoCacheKeys(emptySet())
            val mediaItem = MediaItem.Builder()
                .setMediaId(videoId)
                .setUri(Uri.parse(localUri))
                .build()
            player.stop()
            player.clearMediaItems()
            player.setMediaSource(localSourceFactory.createMediaSource(mediaItem), startPositionMs.coerceAtLeast(0L))
            player.playbackParameters = PlaybackParameters(speed.coerceIn(0.25f, 2.0f))
            player.prepare()
            player.playWhenReady = playWhenReady
            if (playWhenReady) player.play()
            emitSnapshot(player)
        }
    }

    fun prepareVideo(
        details: ResolvedVideoDetails,
        videoStream: VideoStreamOption?,
        audioStream: VideoAudioOption?,
        subtitle: VideoSubtitleOption?,
        startPositionMs: Long = 0L,
        speed: Float = 1.0f,
        playWhenReady: Boolean = true,
        preferAdaptiveManifest: Boolean = false,
        adaptiveMaxHeightPx: Int = 4320,
        extractionDurationMs: Long = 0L,
        isShort: Boolean = details.video.isShort
    ) {
        runOnMain {
            val player = ensurePlayer()
            cacheManager.cancelBackgroundPreloadsForForegroundPlayback()
            cacheManager.onVideoPlayerStateChanged(isOpen = true)

            val isLiveStream = details.video.isLive
            val allowCacheWrite = !isLiveStream && cacheManager.shouldAllowVideoCacheWrites()
            val mediaSourceFactory = DefaultMediaSourceFactory(
                cacheManager.createVideoDataSourceFactory(
                    upstreamFactory = perStreamDataSourceFactory,
                    isShort = isShort,
                    allowCacheWrite = allowCacheWrite
                )
            )
            lastCues = emptyList()
            if (lastMediaId != details.video.videoId) {
                lastMediaId = details.video.videoId
                hasReachedReadyForCurrentVideo = false
                rebufferCount = 0
                diagnostics.markVideoLoadStarted(details.video.videoId, extractionDurationMs)
                lastVideoWidth = 0
                lastVideoHeight = 0
                lastPixelWidthHeightRatio = 1f
                lastUnappliedRotationDegrees = 0
                hasKnownDimensions = false
                lastVideoCodec = ""
                lastVideoMimeType = ""
                lastVideoBitrateKbps = 0
                lastVideoFps = 0f
            }
            applyAdaptiveMaxVideoHeight(adaptiveMaxHeightPx)

            val subtitleConfigs = subtitle?.let { sub ->
                val mime = when {
                    sub.mimeType.contains("ttml", ignoreCase = true) -> MimeTypes.APPLICATION_TTML
                    sub.mimeType.contains("srt", ignoreCase = true) || sub.mimeType.contains("subrip", ignoreCase = true) -> MimeTypes.APPLICATION_SUBRIP
                    else -> MimeTypes.TEXT_VTT
                }
                listOf(
                    MediaItem.SubtitleConfiguration.Builder(Uri.parse(sub.url))
                        .setMimeType(mime)
                        .setLanguage(sub.languageTag)
                        .setSelectionFlags(C.SELECTION_FLAG_DEFAULT)
                        .build()
                )
            } ?: emptyList()

            val chosenVideo = videoStream
            val hls = details.hlsUrl
            val dash = details.dashMpdUrl
            val activeKeys = mutableSetOf<String>()

            player.stop()
            player.clearMediaItems()

            when {
                preferAdaptiveManifest && !hls.isNullOrBlank() -> {
                    val hlsItem = MediaItem.Builder()
                        .setMediaId(details.video.videoId)
                        .setUri(hls)
                        .setMimeType(MimeTypes.APPLICATION_M3U8)
                        .setSubtitleConfigurations(subtitleConfigs)
                        .build()
                    player.setMediaSource(mediaSourceFactory.createMediaSource(hlsItem), startPositionMs.coerceAtLeast(0L))
                }
                preferAdaptiveManifest && !dash.isNullOrBlank() -> {
                    val dashItem = MediaItem.Builder()
                        .setMediaId(details.video.videoId)
                        .setUri(dash)
                        .setMimeType(MimeTypes.APPLICATION_MPD)
                        .setSubtitleConfigurations(subtitleConfigs)
                        .build()
                    player.setMediaSource(mediaSourceFactory.createMediaSource(dashItem), startPositionMs.coerceAtLeast(0L))
                }
                chosenVideo != null && chosenVideo.isVideoOnly && audioStream != null -> {
                    registerHeaders(chosenVideo.url, chosenVideo.headers)
                    registerHeaders(audioStream.url, audioStream.headers)
                    val videoKey = if (cacheManager.isCachePermitted(chosenVideo.url, isLive = isLiveStream)) {
                        cacheManager.buildVideoCacheKey(details.video.videoId, "v_${chosenVideo.id}", isShort)
                            .also { activeKeys.add(it) }
                    } else null
                    val audioKey = if (cacheManager.isCachePermitted(audioStream.url, isLive = isLiveStream)) {
                        cacheManager.buildVideoCacheKey(details.video.videoId, "a_${audioStream.id}", isShort)
                            .also { activeKeys.add(it) }
                    } else null
                    val videoItem = MediaItem.Builder()
                        .setMediaId(details.video.videoId)
                        .setUri(chosenVideo.url)
                        .setSubtitleConfigurations(subtitleConfigs)
                        .apply { if (videoKey != null) setCustomCacheKey(videoKey) }
                        .build()
                    val audioItem = MediaItem.Builder()
                        .setUri(audioStream.url)
                        .apply { if (audioKey != null) setCustomCacheKey(audioKey) }
                        .build()
                    val videoSource = mediaSourceFactory.createMediaSource(videoItem)
                    val audioSource = mediaSourceFactory.createMediaSource(audioItem)
                    player.setMediaSource(MergingMediaSource(videoSource, audioSource), startPositionMs.coerceAtLeast(0L))
                }
                chosenVideo != null -> {
                    registerHeaders(chosenVideo.url, chosenVideo.headers)
                    val videoKey = if (cacheManager.isCachePermitted(chosenVideo.url, isLive = isLiveStream)) {
                        cacheManager.buildVideoCacheKey(details.video.videoId, "m_${chosenVideo.id}", isShort)
                            .also { activeKeys.add(it) }
                    } else null
                    val videoItem = MediaItem.Builder()
                        .setMediaId(details.video.videoId)
                        .setUri(chosenVideo.url)
                        .setSubtitleConfigurations(subtitleConfigs)
                        .apply { if (videoKey != null) setCustomCacheKey(videoKey) }
                        .build()
                    player.setMediaSource(mediaSourceFactory.createMediaSource(videoItem), startPositionMs.coerceAtLeast(0L))
                }
                !hls.isNullOrBlank() -> {
                    val hlsItem = MediaItem.Builder()
                        .setMediaId(details.video.videoId)
                        .setUri(hls)
                        .setMimeType(MimeTypes.APPLICATION_M3U8)
                        .setSubtitleConfigurations(subtitleConfigs)
                        .build()
                    player.setMediaSource(mediaSourceFactory.createMediaSource(hlsItem), startPositionMs.coerceAtLeast(0L))
                }
                !dash.isNullOrBlank() -> {
                    val dashItem = MediaItem.Builder()
                        .setMediaId(details.video.videoId)
                        .setUri(dash)
                        .setMimeType(MimeTypes.APPLICATION_MPD)
                        .setSubtitleConfigurations(subtitleConfigs)
                        .build()
                    player.setMediaSource(mediaSourceFactory.createMediaSource(dashItem), startPositionMs.coerceAtLeast(0L))
                }
                else -> {
                    onError("No playable video stream available.", OnlineErrorKind.UNSUPPORTED, false)
                    return@runOnMain
                }
            }

            cacheManager.setActiveVideoCacheKeys(activeKeys)
            player.playbackParameters = PlaybackParameters(speed.coerceIn(0.25f, 2.0f))
            player.prepare()
            player.playWhenReady = playWhenReady
            if (playWhenReady) player.play()
            emitSnapshot(player)
        }
    }

    /**
     * Preloads a small bounded segment window of an upcoming Video or Short into its dedicated SimpleCache.
     */
    fun preloadUpcomingVideoStream(
        videoId: String,
        streamTag: String,
        streamUrl: String,
        headers: Map<String, String>,
        isShort: Boolean,
        isLive: Boolean = false
    ) {
        if (!cacheManager.isCachePermitted(streamUrl, isLive = isLive)) return
        registerHeaders(streamUrl, headers)
        val cacheKey = cacheManager.buildVideoCacheKey(videoId, streamTag, isShort)
        cacheManager.preloadVideoWindow(
            cacheKey = cacheKey,
            streamUrl = streamUrl,
            headers = headers,
            upstreamFactory = perStreamDataSourceFactory,
            isShort = isShort
        )
    }

    fun attachSurfaceView(surfaceView: SurfaceView) {
        runOnMain {
            if (attachedSurfaceView === surfaceView) return@runOnMain
            attachedTextureView?.let { old -> exoPlayer?.clearVideoTextureView(old) }
            attachedTextureView = null
            attachedSurfaceView?.let { old -> exoPlayer?.clearVideoSurfaceView(old) }
            attachedSurfaceView = surfaceView
            exoPlayer?.setVideoSurfaceView(surfaceView)
        }
    }

    fun detachSurfaceView(surfaceView: SurfaceView) {
        runOnMain {
            if (attachedSurfaceView === surfaceView) {
                exoPlayer?.clearVideoSurfaceView(surfaceView)
                attachedSurfaceView = null
            }
        }
    }

    fun attachTextureView(textureView: TextureView) {
        runOnMain {
            if (attachedTextureView === textureView) return@runOnMain
            attachedSurfaceView?.let { old -> exoPlayer?.clearVideoSurfaceView(old) }
            attachedSurfaceView = null
            attachedTextureView?.let { old -> exoPlayer?.clearVideoTextureView(old) }
            attachedTextureView = textureView
            exoPlayer?.setVideoTextureView(textureView)
        }
    }

    fun detachTextureView(textureView: TextureView) {
        runOnMain {
            if (attachedTextureView === textureView) {
                exoPlayer?.clearVideoTextureView(textureView)
                attachedTextureView = null
            }
        }
    }

    fun play() {
        runOnMain {
            exoPlayer?.let { p ->
                p.playWhenReady = true
                p.play()
                isPlaying = true
                emitSnapshot(p)
            }
        }
    }

    fun pause() {
        runOnMain {
            exoPlayer?.let { p ->
                p.playWhenReady = false
                p.pause()
                isPlaying = false
                emitSnapshot(p)
            }
        }
    }

    fun seekTo(positionMs: Long) {
        runOnMain {
            exoPlayer?.let { p ->
                p.seekTo(positionMs.coerceAtLeast(0L))
                emitProgress(p)
            }
        }
    }

    fun seekBy(deltaMs: Long) {
        runOnMain {
            exoPlayer?.let { p ->
                val dur = p.duration.takeIf { it != C.TIME_UNSET && it > 0L } ?: Long.MAX_VALUE
                val target = (p.currentPosition + deltaMs).coerceIn(0L, dur)
                p.seekTo(target)
                emitProgress(p)
            }
        }
    }

    fun setPlaybackSpeed(speed: Float) {
        runOnMain {
            exoPlayer?.playbackParameters = PlaybackParameters(speed.coerceIn(0.25f, 2.0f))
        }
    }

    fun pollPositionSnapshot() {
        runOnMain {
            exoPlayer?.let { p ->
                if (p.playbackState == Player.STATE_READY || p.playbackState == Player.STATE_BUFFERING) {
                    emitProgress(p)
                }
            }
        }
    }

    fun currentPositionMs(): Long = exoPlayer?.currentPosition?.coerceAtLeast(0L) ?: 0L

    fun stop() {
        cacheManager.setActiveVideoCacheKeys(emptySet())
        cacheManager.onVideoPlayerStateChanged(isOpen = false)
        runOnMain {
            isPlaying = false
            lastCues = emptyList()
            lastMediaId = null
            lastVideoWidth = 0
            lastVideoHeight = 0
            lastPixelWidthHeightRatio = 1f
            lastUnappliedRotationDegrees = 0
            hasKnownDimensions = false
            exoPlayer?.apply {
                playWhenReady = false
                stop()
                clearMediaItems()
                emitSnapshot(this)
            }
        }
    }

    fun release() {
        cacheManager.setActiveVideoCacheKeys(emptySet())
        cacheManager.onVideoPlayerStateChanged(isOpen = false)
        runOnMain {
            isPlaying = false
            attachedSurfaceView?.let { exoPlayer?.clearVideoSurfaceView(it) }
            attachedSurfaceView = null
            attachedTextureView?.let { exoPlayer?.clearVideoTextureView(it) }
            attachedTextureView = null
            exoPlayer?.removeAnalyticsListener(diagnostics)
            exoPlayer?.release()
            exoPlayer = null
            perStreamHeaders.clear()
        }
    }

    private fun registerHeaders(url: String, headers: Map<String, String>) {
        if (headers.isNotEmpty()) {
            perStreamHeaders[url] = headers
        }
        if (perStreamHeaders.size > 32) {
            val iter = perStreamHeaders.keys.iterator()
            while (perStreamHeaders.size > 20 && iter.hasNext()) {
                val k = iter.next()
                if (k != url) iter.remove()
            }
        }
    }

    private fun extractHttpCode(error: PlaybackException): Int? {
        var cause: Throwable? = error.cause
        while (cause != null) {
            if (cause is HttpDataSource.InvalidResponseCodeException) {
                return cause.responseCode
            }
            cause = cause.cause
        }
        return null
    }
}
