package data.online

import android.content.Context
import android.util.Log
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import domain.onboarding.MusicTasteDiscoveryTrack
import domain.onboarding.MusicTastePreviewState
import domain.online.AudioQualityPreference
import domain.online.OnlineErrorKind
import domain.online.OnlineResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Lightweight, isolated Media3 ExoPlayer controller dedicated to onboarding Step 3
 * ("Yes or No: Discover Songs") audio previews.
 *
 * Plays real resolved streams via [OnlineMusicRepository] without modifying the user's
 * main playback queue, saved downloads, or listening history.
 */
class DiscoveryAudioPreviewPlayer(
    private val context: Context,
    private val onlineRepository: OnlineMusicRepository,
    private val scope: CoroutineScope,
    private val onRequestPauseMainPlayback: () -> Unit
) {
    companion object {
        private const val TAG = "DiscoveryPreviewPlayer"
        private const val PROGRESS_POLL_MS = 250L
    }

    private val _previewState = MutableStateFlow(MusicTastePreviewState())
    val previewState: StateFlow<MusicTastePreviewState> = _previewState.asStateFlow()

    private var exoPlayer: ExoPlayer? = null
    private var resolveJob: Job? = null
    private var progressJob: Job? = null

    fun togglePreview(track: MusicTasteDiscoveryTrack, isNetworkOnline: Boolean) {
        val current = _previewState.value
        val trackUrl = track.normalizedUrl
        if (trackUrl.isBlank()) return

        // Same track already active -> toggle pause/resume
        if (current.activeTrackUrl == trackUrl && exoPlayer != null && !current.isLoading) {
            scope.launch(Dispatchers.Main) {
                val player = exoPlayer ?: return@launch
                if (player.isPlaying) {
                    player.pause()
                    _previewState.value = _previewState.value.copy(isPlaying = false)
                } else {
                    onRequestPauseMainPlayback()
                    if (player.playbackState == Player.STATE_ENDED) {
                        player.seekTo(0L)
                    }
                    player.play()
                    _previewState.value = _previewState.value.copy(isPlaying = true)
                    startProgressPolling()
                }
            }
            return
        }

        if (!isNetworkOnline) {
            _previewState.value = MusicTastePreviewState(
                activeTrackUrl = trackUrl,
                isLoading = false,
                isPlaying = false,
                errorMessage = "Connect to the internet to preview this song."
            )
            return
        }

        startNewPreview(track)
    }

    private fun startNewPreview(track: MusicTasteDiscoveryTrack) {
        resolveJob?.cancel()
        progressJob?.cancel()
        val trackUrl = track.normalizedUrl
        onRequestPauseMainPlayback()
        _previewState.value = MusicTastePreviewState(
            activeTrackUrl = trackUrl,
            isLoading = true,
            isPlaying = false,
            positionMs = 0L,
            durationMs = track.durationMs.coerceAtLeast(0L),
            errorMessage = null
        )

        resolveJob = scope.launch {
            val song = track.toSong()
            var resolved = onlineRepository.resolvePlayableStream(
                song = song,
                qualityPreference = AudioQualityPreference.AUTO,
                forceRefresh = false
            )
            if (resolved is OnlineResult.Error && resolved.kind == OnlineErrorKind.STREAM_EXPIRED) {
                onlineRepository.invalidateStreamCache(song.contentUrl)
                resolved = onlineRepository.resolvePlayableStream(
                    song = song,
                    qualityPreference = AudioQualityPreference.AUTO,
                    forceRefresh = true
                )
            }

            when (resolved) {
                is OnlineResult.Success -> {
                    val streamUrl = resolved.value.selectedStream.url
                    withContext(Dispatchers.Main) {
                        if (_previewState.value.activeTrackUrl != trackUrl) return@withContext
                        runCatching {
                            val player = ensurePlayerOnMain()
                            player.setMediaItem(MediaItem.fromUri(streamUrl))
                            player.prepare()
                            player.playWhenReady = true
                            _previewState.value = _previewState.value.copy(
                                isLoading = false,
                                isPlaying = true,
                                durationMs = track.durationMs.coerceAtLeast(player.duration.coerceAtLeast(0L)),
                                errorMessage = null
                            )
                            startProgressPolling()
                        }.onFailure { err ->
                            Log.w(TAG, "Failed starting discovery preview playback", err)
                            _previewState.value = MusicTastePreviewState(
                                activeTrackUrl = trackUrl,
                                isLoading = false,
                                isPlaying = false,
                                errorMessage = "Preview unavailable for this track."
                            )
                        }
                    }
                }
                is OnlineResult.Error -> {
                    _previewState.value = MusicTastePreviewState(
                        activeTrackUrl = trackUrl,
                        isLoading = false,
                        isPlaying = false,
                        errorMessage = resolved.message.ifBlank { "Preview unavailable right now." }
                    )
                }
            }
        }
    }

    private fun ensurePlayerOnMain(): ExoPlayer {
        exoPlayer?.let { return it }
        val attrs = AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .build()
        return ExoPlayer.Builder(context.applicationContext)
            .setAudioAttributes(attrs, true)
            .setHandleAudioBecomingNoisy(true)
            .build()
            .also { player ->
                player.addListener(object : Player.Listener {
                    override fun onIsPlayingChanged(isPlaying: Boolean) {
                        _previewState.value = _previewState.value.copy(isPlaying = isPlaying)
                    }

                    override fun onPlaybackStateChanged(playbackState: Int) {
                        when (playbackState) {
                            Player.STATE_BUFFERING -> {
                                _previewState.value = _previewState.value.copy(isLoading = true)
                            }
                            Player.STATE_READY -> {
                                val dur = player.duration.coerceAtLeast(0L)
                                _previewState.value = _previewState.value.copy(
                                    isLoading = false,
                                    durationMs = if (dur > 0L) dur else _previewState.value.durationMs
                                )
                            }
                            Player.STATE_ENDED -> {
                                _previewState.value = _previewState.value.copy(
                                    isPlaying = false,
                                    isLoading = false,
                                    positionMs = _previewState.value.durationMs
                                )
                            }
                            else -> Unit
                        }
                    }

                    override fun onPlayerError(error: PlaybackException) {
                        Log.w(TAG, "Discovery preview ExoPlayer error", error)
                        _previewState.value = _previewState.value.copy(
                            isLoading = false,
                            isPlaying = false,
                            errorMessage = "Preview unavailable for this track."
                        )
                    }
                })
                exoPlayer = player
            }
    }

    private fun startProgressPolling() {
        progressJob?.cancel()
        progressJob = scope.launch(Dispatchers.Main) {
            while (isActive) {
                val player = exoPlayer ?: break
                val dur = player.duration.coerceAtLeast(0L)
                val pos = player.currentPosition.coerceAtLeast(0L)
                _previewState.value = _previewState.value.copy(
                    positionMs = pos,
                    durationMs = if (dur > 0L) dur else _previewState.value.durationMs,
                    isPlaying = player.isPlaying
                )
                if (!player.isPlaying && player.playbackState != Player.STATE_BUFFERING) {
                    break
                }
                delay(PROGRESS_POLL_MS)
            }
        }
    }

    fun stopPreview() {
        resolveJob?.cancel()
        progressJob?.cancel()
        _previewState.value = MusicTastePreviewState()
        scope.launch(Dispatchers.Main) {
            runCatching {
                exoPlayer?.stop()
                exoPlayer?.clearMediaItems()
            }
        }
    }

    fun release() {
        resolveJob?.cancel()
        progressJob?.cancel()
        _previewState.value = MusicTastePreviewState()
        scope.launch(Dispatchers.Main) {
            runCatching {
                exoPlayer?.release()
                exoPlayer = null
            }
        }
    }
}
