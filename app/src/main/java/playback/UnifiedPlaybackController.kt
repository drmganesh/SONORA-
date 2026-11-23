package playback

import com.example.audio.SonoraAudioEngine
import com.example.model.OnlinePlaybackStage
import com.example.model.Song
import data.online.OnlineMusicRepository
import domain.online.AudioQualityPreference
import domain.online.OnlineErrorKind
import domain.online.OnlineResult
import domain.online.PlaybackSource
import domain.online.ResolvedOnlineStreamResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicLong

/**
 * Coordinates unified playback between SONORA's local [SonoraAudioEngine] (PCM DSP / MediaPlayer)
 * and online streaming playback ([OnlinePlaybackHandler] via Media3 ExoPlayer + NewPipe Extractor).
 * Guarantees a single active playback backend, thread-safe generation/track guards against async races,
 * background next-track preloading + ExoPlayer staging, and bounded fallback recovery on stream errors.
 */
class UnifiedPlaybackController(
    private val audioEngine: SonoraAudioEngine,
    private val onlineRepository: OnlineMusicRepository
) {

    data class PreloadedNextTrack(
        val songId: Long,
        val contentUrl: String,
        val resolved: ResolvedOnlineStreamResult
    )

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var resolveJob: Job? = null
    private var preloadJob: Job? = null

    @Volatile
    private var resolvingContentUrl: String? = null

    @Volatile
    private var resolvingManualStreamId: String? = null

    @Volatile
    private var activeTargetSongId: Long? = null

    @Volatile
    private var activeTargetContentUrl: String? = null

    @Volatile
    private var preloadedNextTrack: PreloadedNextTrack? = null

    @Volatile
    private var stagedNextResolved: ResolvedOnlineStreamResult? = null

    private val _currentResolvedStream = MutableStateFlow<ResolvedOnlineStreamResult?>(null)
    val currentResolvedStream: StateFlow<ResolvedOnlineStreamResult?> = _currentResolvedStream.asStateFlow()

    private val activePlaybackGeneration = AtomicLong(0L)
    private val preloadGeneration = AtomicLong(0L)

    fun isCurrentlyResolving(song: Song, manualStreamId: String? = null): Boolean {
        if (!song.isOnline) return false
        val job = resolveJob ?: return false
        if (!job.isActive) return false
        val targetUrl = song.contentUrl.ifBlank { song.uri.toString() }
        return targetUrl.isNotBlank() &&
            targetUrl == resolvingContentUrl &&
            manualStreamId == resolvingManualStreamId
    }

    fun getPreloadedStream(contentUrl: String): ResolvedOnlineStreamResult? {
        val candidate = preloadedNextTrack ?: return null
        if (candidate.contentUrl != contentUrl) return null
        if (candidate.resolved.isExpired()) return null
        return candidate.resolved
    }

    /**
     * Resolves [song] in the background without touching current playback, caches the extracted formats,
     * and optionally stages the resolved track into the single ExoPlayer instance for near-instant Next / auto-advance.
     */
    fun preloadNextTrack(
        song: Song?,
        qualityPreference: AudioQualityPreference,
        stageIntoPlayer: Boolean = true,
        preloadAudioWindow: Boolean = true,
        onPreloadReady: ((ResolvedOnlineStreamResult) -> Unit)? = null
    ) {
        val myPreloadGen = preloadGeneration.incrementAndGet()
        preloadJob?.cancel()
        preloadJob = null

        if (song == null || !song.isOnline) {
            preloadedNextTrack = null
            stagedNextResolved = null
            audioEngine.clearStagedNextOnlineStream()
            return
        }

        val targetContentUrl = song.contentUrl.ifBlank { song.uri.toString() }
        if (targetContentUrl.isBlank() || targetContentUrl == activeTargetContentUrl) {
            preloadedNextTrack = null
            stagedNextResolved = null
            audioEngine.clearStagedNextOnlineStream()
            return
        }

        val existing = preloadedNextTrack
        if (existing != null && existing.contentUrl == targetContentUrl && !existing.resolved.isExpired()) {
            if (stageIntoPlayer) {
                stagedNextResolved = existing.resolved
                audioEngine.stageNextOnlineStream(existing.resolved)
            }
            if (preloadAudioWindow) {
                audioEngine.preloadNextOnlineTrackWindow(existing.resolved)
            }
            onPreloadReady?.invoke(existing.resolved)
            return
        }

        preloadJob = scope.launch {
            try {
                val result = onlineRepository.preloadNextTrackStream(
                    song = song,
                    qualityPreference = qualityPreference
                )
                if (myPreloadGen != preloadGeneration.get()) return@launch
                if (targetContentUrl == activeTargetContentUrl) return@launch

                if (result is OnlineResult.Success) {
                    val resolved = result.value
                    preloadedNextTrack = PreloadedNextTrack(
                        songId = song.id,
                        contentUrl = targetContentUrl,
                        resolved = resolved
                    )
                    if (stageIntoPlayer) {
                        stagedNextResolved = resolved
                        audioEngine.stageNextOnlineStream(resolved)
                    }
                    if (preloadAudioWindow) {
                        audioEngine.preloadNextOnlineTrackWindow(resolved)
                    }
                    onPreloadReady?.invoke(resolved)
                }
            } catch (ce: CancellationException) {
                throw ce
            } catch (_: Throwable) {
                // Preloading is best-effort; foreground playback falls back cleanly if unavailable
            }
        }
    }

    fun clearPreloadedNextTrack() {
        preloadGeneration.incrementAndGet()
        preloadJob?.cancel()
        preloadJob = null
        preloadedNextTrack = null
        stagedNextResolved = null
        audioEngine.clearStagedNextOnlineStream()
    }

    fun playSong(
        song: Song,
        qualityPreference: AudioQualityPreference,
        manualStreamId: String? = null,
        resumePositionMs: Long = 0L,
        onResolvingStarted: () -> Unit,
        onStageChanged: (OnlinePlaybackStage, String) -> Unit = { _, _ -> },
        onPrepared: (updatedSong: Song, resolved: ResolvedOnlineStreamResult?, durationMs: Long) -> Unit,
        onCompletion: () -> Unit,
        onError: (String, OnlineErrorKind?) -> Unit,
        onAutoTransitionToNext: ((resolvedNext: ResolvedOnlineStreamResult) -> Unit)? = null
    ) {
        val generation = activePlaybackGeneration.incrementAndGet()
        resolveJob?.cancel()
        resolveJob = null
        preloadGeneration.incrementAndGet()
        preloadJob?.cancel()
        preloadJob = null

        val targetContentUrl = if (song.isOnline) song.contentUrl.ifBlank { song.uri.toString() } else null
        activeTargetSongId = song.id
        activeTargetContentUrl = targetContentUrl
        resolvingContentUrl = null
        resolvingManualStreamId = null

        val source: PlaybackSource = if (song.isOnline) {
            PlaybackSource.OnlineStream(
                song = song,
                serviceId = song.serviceId,
                providerKey = song.providerKey,
                contentUrl = targetContentUrl.orEmpty()
            )
        } else {
            PlaybackSource.LocalFile(
                songId = song.id,
                uri = song.uri,
                fallbackPath = song.dataPath.takeIf { it.isNotBlank() }
            )
        }

        when (source) {
            is PlaybackSource.LocalFile -> {
                preloadedNextTrack = null
                stagedNextResolved = null
                _currentResolvedStream.value = null
                onStageChanged(OnlinePlaybackStage.IDLE, "")
                audioEngine.prepareAndPlay(
                    uri = source.uri,
                    fallbackPath = source.fallbackPath,
                    onPrepared = {
                        if (generation != activePlaybackGeneration.get() || activeTargetSongId != song.id) {
                            return@prepareAndPlay
                        }
                        onPrepared(song, null, audioEngine.duration)
                    },
                    onCompletion = {
                        if (generation != activePlaybackGeneration.get()) return@prepareAndPlay
                        onCompletion()
                    },
                    onError = { msg ->
                        if (generation != activePlaybackGeneration.get() || activeTargetSongId != song.id) {
                            return@prepareAndPlay
                        }
                        onError(msg, null)
                    }
                )
            }

            is PlaybackSource.OnlineStream -> {
                val preloaded = if (manualStreamId == null) getPreloadedStream(source.contentUrl) else null
                if (preloaded != null) {
                    preloadedNextTrack = null
                    stagedNextResolved = null
                    onResolvingStarted()
                    onStageChanged(
                        OnlinePlaybackStage.PREPARING_PLAYER,
                        OnlinePlaybackStage.PREPARING_PLAYER.defaultMessage
                    )
                    startResolvedOnlineStream(
                        generation = generation,
                        song = source.song,
                        resolved = preloaded,
                        qualityPreference = qualityPreference,
                        manualStreamId = manualStreamId,
                        resumePositionMs = resumePositionMs,
                        retriesRemaining = 2,
                        failedStreamIds = mutableSetOf(),
                        onStageChanged = onStageChanged,
                        onPrepared = onPrepared,
                        onCompletion = onCompletion,
                        onError = onError,
                        onAutoTransitionToNext = onAutoTransitionToNext
                    )
                } else {
                    preloadedNextTrack = null
                    stagedNextResolved = null
                    onResolvingStarted()
                    onStageChanged(
                        OnlinePlaybackStage.RESOLVING_STREAM,
                        OnlinePlaybackStage.RESOLVING_STREAM.defaultMessage
                    )
                    resolveAndStartOnlineStream(
                        generation = generation,
                        song = source.song,
                        qualityPreference = qualityPreference,
                        manualStreamId = manualStreamId,
                        resumePositionMs = resumePositionMs,
                        retriesRemaining = 2,
                        forceRefresh = false,
                        failedStreamIds = mutableSetOf(),
                        onStageChanged = onStageChanged,
                        onPrepared = onPrepared,
                        onCompletion = onCompletion,
                        onError = onError,
                        onAutoTransitionToNext = onAutoTransitionToNext
                    )
                }
            }
        }
    }

    private fun resolveAndStartOnlineStream(
        generation: Long,
        song: Song,
        qualityPreference: AudioQualityPreference,
        manualStreamId: String?,
        resumePositionMs: Long,
        retriesRemaining: Int,
        forceRefresh: Boolean,
        failedStreamIds: MutableSet<String>,
        onStageChanged: (OnlinePlaybackStage, String) -> Unit,
        onPrepared: (updatedSong: Song, resolved: ResolvedOnlineStreamResult?, durationMs: Long) -> Unit,
        onCompletion: () -> Unit,
        onError: (String, OnlineErrorKind?) -> Unit,
        onAutoTransitionToNext: ((resolvedNext: ResolvedOnlineStreamResult) -> Unit)?
    ) {
        val targetUrl = song.contentUrl.ifBlank { song.uri.toString() }
        resolvingContentUrl = targetUrl
        resolvingManualStreamId = manualStreamId
        resolveJob = scope.launch {
            try {
                when (
                    val result = onlineRepository.resolvePlayableStream(
                        song = song,
                        qualityPreference = qualityPreference,
                        manualStreamId = manualStreamId,
                        forceRefresh = forceRefresh,
                        excludedStreamIds = failedStreamIds
                    )
                ) {
                    is OnlineResult.Success -> {
                        if (generation != activePlaybackGeneration.get() || activeTargetContentUrl != targetUrl) {
                            return@launch
                        }
                        val resolved = result.value
                        startResolvedOnlineStream(
                            generation = generation,
                            song = song,
                            resolved = resolved,
                            qualityPreference = qualityPreference,
                            manualStreamId = manualStreamId,
                            resumePositionMs = resumePositionMs,
                            retriesRemaining = retriesRemaining,
                            failedStreamIds = failedStreamIds,
                            onStageChanged = onStageChanged,
                            onPrepared = onPrepared,
                            onCompletion = onCompletion,
                            onError = onError,
                            onAutoTransitionToNext = onAutoTransitionToNext
                        )
                    }

                    is OnlineResult.Error -> {
                        if (generation != activePlaybackGeneration.get() || activeTargetContentUrl != targetUrl) {
                            return@launch
                        }
                        onStageChanged(OnlinePlaybackStage.FAILED, OnlinePlaybackStage.FAILED.defaultMessage)
                        onError(result.message, result.kind)
                    }
                }
            } catch (ce: CancellationException) {
                throw ce
            } catch (t: Throwable) {
                if (generation == activePlaybackGeneration.get() && activeTargetContentUrl == targetUrl) {
                    onStageChanged(OnlinePlaybackStage.FAILED, OnlinePlaybackStage.FAILED.defaultMessage)
                    onError(t.message ?: "Failed resolving online track.", OnlineErrorKind.EXTRACTION_FAILED)
                }
            } finally {
                if (generation == activePlaybackGeneration.get()) {
                    resolvingContentUrl = null
                    resolvingManualStreamId = null
                }
            }
        }
    }

    private fun startResolvedOnlineStream(
        generation: Long,
        song: Song,
        resolved: ResolvedOnlineStreamResult,
        qualityPreference: AudioQualityPreference,
        manualStreamId: String?,
        resumePositionMs: Long,
        retriesRemaining: Int,
        failedStreamIds: MutableSet<String>,
        onStageChanged: (OnlinePlaybackStage, String) -> Unit,
        onPrepared: (updatedSong: Song, resolved: ResolvedOnlineStreamResult?, durationMs: Long) -> Unit,
        onCompletion: () -> Unit,
        onError: (String, OnlineErrorKind?) -> Unit,
        onAutoTransitionToNext: ((resolvedNext: ResolvedOnlineStreamResult) -> Unit)?
    ) {
        if (generation != activePlaybackGeneration.get() || activeTargetContentUrl != resolved.contentUrl) return

        _currentResolvedStream.value = resolved
        val enrichedSong = song.copy(
            title = resolved.title.ifBlank { song.title },
            artist = resolved.artist.ifBlank { song.artist },
            durationMs = if (resolved.durationMs > 0L) resolved.durationMs else song.durationMs,
            artworkUrl = resolved.artworkUrl ?: song.artworkUrl,
            uploaderUrl = resolved.artistUrl ?: song.uploaderUrl,
            mimeType = resolved.selectedStream.mimeType,
            bitrateKbps = resolved.selectedStream.averageBitrateKbps,
            streamCodec = resolved.selectedStream.codec,
            streamQualityLabel = resolved.selectedStream.qualityLabel,
            isBitrateKnown = resolved.selectedStream.averageBitrateKbps > 0,
            isSampleRateKnown = false
        )

        audioEngine.prepareAndPlayOnlineStream(
            resolved = resolved,
            startPositionMs = resumePositionMs,
            onPrepared = { dur ->
                if (generation != activePlaybackGeneration.get() || activeTargetContentUrl != resolved.contentUrl) {
                    return@prepareAndPlayOnlineStream
                }
                onStageChanged(OnlinePlaybackStage.PLAYING, OnlinePlaybackStage.PLAYING.defaultMessage)
                onPrepared(enrichedSong, resolved, dur.coerceAtLeast(enrichedSong.durationMs))
            },
            onCompletion = {
                if (generation != activePlaybackGeneration.get()) return@prepareAndPlayOnlineStream
                onCompletion()
            },
            onError = { errMsg, mayBeExpiredStream ->
                if (generation != activePlaybackGeneration.get() || activeTargetContentUrl != resolved.contentUrl) {
                    return@prepareAndPlayOnlineStream
                }
                failedStreamIds.add(resolved.selectedStream.id)
                val hasAlternateFormat = resolved.availableStreams.any { it.id !in failedStreamIds }
                val shouldTryAlternateCachedFormat = !mayBeExpiredStream &&
                    hasAlternateFormat &&
                    retriesRemaining > 0 &&
                    manualStreamId == null
                val shouldReExtract = mayBeExpiredStream && retriesRemaining > 0

                if (shouldTryAlternateCachedFormat || shouldReExtract) {
                    if (shouldReExtract) {
                        onlineRepository.invalidateStreamCache(resolved.contentUrl)
                        onStageChanged(
                            OnlinePlaybackStage.RESOLVING_STREAM,
                            OnlinePlaybackStage.RESOLVING_STREAM.defaultMessage
                        )
                    } else {
                        onStageChanged(
                            OnlinePlaybackStage.PREPARING_PLAYER,
                            OnlinePlaybackStage.PREPARING_PLAYER.defaultMessage
                        )
                    }
                    val lastPos = audioEngine.currentPosition.coerceAtLeast(resumePositionMs)
                    resolveAndStartOnlineStream(
                        generation = generation,
                        song = enrichedSong,
                        qualityPreference = qualityPreference,
                        manualStreamId = if (shouldTryAlternateCachedFormat) null else manualStreamId,
                        resumePositionMs = lastPos,
                        retriesRemaining = retriesRemaining - 1,
                        forceRefresh = shouldReExtract,
                        failedStreamIds = failedStreamIds,
                        onStageChanged = onStageChanged,
                        onPrepared = onPrepared,
                        onCompletion = onCompletion,
                        onError = onError,
                        onAutoTransitionToNext = onAutoTransitionToNext
                    )
                } else {
                    onStageChanged(OnlinePlaybackStage.FAILED, OnlinePlaybackStage.FAILED.defaultMessage)
                    onError(
                        errMsg,
                        if (mayBeExpiredStream) OnlineErrorKind.STREAM_EXPIRED else OnlineErrorKind.EXTRACTION_FAILED
                    )
                }
            },
            onAutoTransitionToNext = { mediaId ->
                if (generation != activePlaybackGeneration.get()) return@prepareAndPlayOnlineStream
                val promoted = stagedNextResolved?.takeIf { it.contentUrl == mediaId }
                    ?: preloadedNextTrack?.takeIf { it.contentUrl == mediaId }?.resolved
                    ?: return@prepareAndPlayOnlineStream

                activeTargetContentUrl = promoted.contentUrl
                activeTargetSongId = preloadedNextTrack?.takeIf { it.contentUrl == mediaId }?.songId
                preloadedNextTrack = null
                stagedNextResolved = null
                _currentResolvedStream.value = promoted
                onStageChanged(OnlinePlaybackStage.PLAYING, OnlinePlaybackStage.PLAYING.defaultMessage)
                onAutoTransitionToNext?.invoke(promoted)
            }
        )
    }

    fun cancelPendingResolution() {
        activePlaybackGeneration.incrementAndGet()
        preloadGeneration.incrementAndGet()
        resolvingContentUrl = null
        resolvingManualStreamId = null
        activeTargetSongId = null
        activeTargetContentUrl = null
        preloadedNextTrack = null
        stagedNextResolved = null
        resolveJob?.cancel()
        resolveJob = null
        preloadJob?.cancel()
        preloadJob = null
        audioEngine.clearStagedNextOnlineStream()
    }
}
