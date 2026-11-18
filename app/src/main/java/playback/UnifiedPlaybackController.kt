package playback

import com.example.audio.SonoraAudioEngine
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

/**
 * Coordinates unified playback between SONORA's local [SonoraAudioEngine] (PCM DSP / MediaPlayer)
 * and online streaming playback ([OnlinePlaybackHandler] via Media3 ExoPlayer + NewPipe Extractor).
 * Guarantees only one active playback backend runs at a time and handles automatic URL re-extraction once
 * if an online stream URL expires mid-session.
 */
class UnifiedPlaybackController(
    private val audioEngine: SonoraAudioEngine,
    private val onlineRepository: OnlineMusicRepository
) {

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var resolveJob: Job? = null
    @Volatile
    private var resolvingContentUrl: String? = null
    @Volatile
    private var resolvingManualStreamId: String? = null

    private val _currentResolvedStream = MutableStateFlow<ResolvedOnlineStreamResult?>(null)
    val currentResolvedStream: StateFlow<ResolvedOnlineStreamResult?> = _currentResolvedStream.asStateFlow()

    @Volatile
    private var activePlaybackGeneration = 0L

    fun isCurrentlyResolving(song: Song, manualStreamId: String? = null): Boolean {
        if (!song.isOnline) return false
        val job = resolveJob ?: return false
        if (!job.isActive) return false
        val targetUrl = song.contentUrl.ifBlank { song.uri.toString() }
        return targetUrl.isNotBlank() &&
            targetUrl == resolvingContentUrl &&
            manualStreamId == resolvingManualStreamId
    }

    fun playSong(
        song: Song,
        qualityPreference: AudioQualityPreference,
        manualStreamId: String? = null,
        resumePositionMs: Long = 0L,
        onResolvingStarted: () -> Unit,
        onPrepared: (updatedSong: Song, resolved: ResolvedOnlineStreamResult?, durationMs: Long) -> Unit,
        onCompletion: () -> Unit,
        onError: (String, OnlineErrorKind?) -> Unit
    ) {
        val generation = ++activePlaybackGeneration
        resolveJob?.cancel()

        val source: PlaybackSource = if (song.isOnline) {
            PlaybackSource.OnlineStream(
                song = song,
                serviceId = song.serviceId,
                providerKey = song.providerKey,
                contentUrl = song.contentUrl.ifBlank { song.uri.toString() }
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
                _currentResolvedStream.value = null
                audioEngine.prepareAndPlay(
                    uri = source.uri,
                    fallbackPath = source.fallbackPath,
                    onPrepared = {
                        if (generation != activePlaybackGeneration) return@prepareAndPlay
                        onPrepared(song, null, audioEngine.duration)
                    },
                    onCompletion = {
                        if (generation != activePlaybackGeneration) return@prepareAndPlay
                        onCompletion()
                    },
                    onError = { msg ->
                        if (generation != activePlaybackGeneration) return@prepareAndPlay
                        onError(msg, null)
                    }
                )
            }

            is PlaybackSource.OnlineStream -> {
                onResolvingStarted()
                resolveAndStartOnlineStream(
                    generation = generation,
                    song = source.song,
                    qualityPreference = qualityPreference,
                    manualStreamId = manualStreamId,
                    resumePositionMs = resumePositionMs,
                    allowReExtractRetry = true,
                    onPrepared = onPrepared,
                    onCompletion = onCompletion,
                    onError = onError
                )
            }
        }
    }

    private fun resolveAndStartOnlineStream(
        generation: Long,
        song: Song,
        qualityPreference: AudioQualityPreference,
        manualStreamId: String?,
        resumePositionMs: Long,
        allowReExtractRetry: Boolean,
        onPrepared: (updatedSong: Song, resolved: ResolvedOnlineStreamResult?, durationMs: Long) -> Unit,
        onCompletion: () -> Unit,
        onError: (String, OnlineErrorKind?) -> Unit
    ) {
        resolvingContentUrl = song.contentUrl.ifBlank { song.uri.toString() }
        resolvingManualStreamId = manualStreamId
        resolveJob = scope.launch {
            try {
                val forceRefresh = !allowReExtractRetry
                when (
                    val result = onlineRepository.resolvePlayableStream(
                        song = song,
                        qualityPreference = qualityPreference,
                        manualStreamId = manualStreamId,
                        forceRefresh = forceRefresh
                    )
                ) {
                    is OnlineResult.Success -> {
                        if (generation != activePlaybackGeneration) return@launch
                        val resolved = result.value
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
                                if (generation != activePlaybackGeneration) return@prepareAndPlayOnlineStream
                                onPrepared(enrichedSong, resolved, dur.coerceAtLeast(enrichedSong.durationMs))
                            },
                            onCompletion = {
                                if (generation != activePlaybackGeneration) return@prepareAndPlayOnlineStream
                                onCompletion()
                            },
                            onError = { errMsg, mayBeExpiredStream ->
                                if (generation != activePlaybackGeneration) return@prepareAndPlayOnlineStream
                                if (mayBeExpiredStream && allowReExtractRetry) {
                                    onlineRepository.invalidateStreamCache(resolved.contentUrl)
                                    val lastPos = audioEngine.currentPosition.coerceAtLeast(resumePositionMs)
                                    resolveAndStartOnlineStream(
                                        generation = generation,
                                        song = enrichedSong,
                                        qualityPreference = qualityPreference,
                                        manualStreamId = manualStreamId,
                                        resumePositionMs = lastPos,
                                        allowReExtractRetry = false,
                                        onPrepared = onPrepared,
                                        onCompletion = onCompletion,
                                        onError = onError
                                    )
                                } else {
                                    onError(
                                        errMsg,
                                        if (mayBeExpiredStream) OnlineErrorKind.STREAM_EXPIRED else OnlineErrorKind.EXTRACTION_FAILED
                                    )
                                }
                            }
                        )
                    }

                    is OnlineResult.Error -> {
                        if (generation != activePlaybackGeneration) return@launch
                        onError(result.message, result.kind)
                    }
                }
            } catch (ce: CancellationException) {
                throw ce
            } catch (t: Throwable) {
                if (generation == activePlaybackGeneration) {
                    onError(t.message ?: "Failed resolving online track.", OnlineErrorKind.EXTRACTION_FAILED)
                }
            } finally {
                if (generation == activePlaybackGeneration) {
                    resolvingContentUrl = null
                    resolvingManualStreamId = null
                }
            }
        }
    }

    fun cancelPendingResolution() {
        activePlaybackGeneration++
        resolvingContentUrl = null
        resolvingManualStreamId = null
        resolveJob?.cancel()
        resolveJob = null
    }
}
