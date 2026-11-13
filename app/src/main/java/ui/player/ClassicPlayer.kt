package ui.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.model.PlaybackInfo
import com.example.model.PlayerAppearanceConfig
import com.example.model.PlayerGestureConfig
import com.example.model.Song
import ui.components.ArtworkContainer
import ui.components.PlaybackControls
import ui.components.TrackMetadataBar
import ui.player.seekbar.SeekbarRenderer
import ui.player.seekbar.SeekbarStyle
import ui.theme.LocalEditorialColors

/**
 * Style 1: Classic
 * Editorial classic player layout with customizable artwork framing, typography,
 * audio shortcuts, transport controls, and theme-matched live-reactive seekbar.
 */
@Composable
fun ClassicPlayer(
    song: Song?,
    playbackInfo: PlaybackInfo,
    onTogglePlayPause: () -> Unit,
    onPlayNext: () -> Unit,
    onPlayPrevious: () -> Unit,
    onSeekTo: (Long) -> Unit,
    onToggleShuffle: () -> Unit,
    onCycleRepeat: () -> Unit,
    onOpenQueue: () -> Unit,
    onOpenLyrics: () -> Unit,
    onOpenSleepTimer: () -> Unit,
    onClose: () -> Unit,
    onArtworkLongPress: () -> Unit,
    seekbarStyle: SeekbarStyle = SeekbarStyle.DEFAULT,
    onToggleFavorite: (() -> Unit)? = null,
    onOpenAudioEffects: (() -> Unit)? = null,
    onOpenEqualizer: (() -> Unit)? = null,
    onOpenOutputDevice: (() -> Unit)? = null,
    onToggleFocusMode: (() -> Unit)? = null,
    isVisualizerActive: Boolean = false,
    onToggleVisualizer: (() -> Unit)? = null,
    frequencies: FloatArray = FloatArray(32) { 0f },
    waveform: FloatArray = FloatArray(64) { 0f },
    visualizerStyle: com.example.model.VisualizerStyle = com.example.model.VisualizerStyle.SPECTRUM_BARS,
    onCycleVisualizerStyle: (() -> Unit)? = null,
    appearance: PlayerAppearanceConfig = PlayerAppearanceConfig.DEFAULT,
    gestureConfig: PlayerGestureConfig = PlayerGestureConfig.DEFAULT,
    gestureController: PlayerGestureController = rememberPlayerGestureController(),
    onOpenCustomization: (() -> Unit)? = null,
    onCyclePlayerStyle: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val colors = LocalEditorialColors.current
    val duration = (playbackInfo.durationMs).coerceAtLeast(song?.durationMs ?: 0L)
    val artworkFraction = if (isVisualizerActive) {
        (appearance.artworkSize.fraction * 0.78f).coerceIn(0.55f, 0.78f)
    } else {
        appearance.artworkSize.fraction
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .navigationBarsPadding(),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 600.dp)
                .padding(horizontal = 20.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            NowPlayingTopHeader(
                song = song,
                onClose = onClose,
                onOpenQueue = onOpenQueue,
                onOpenLyrics = onOpenLyrics,
                onOpenSleepTimer = onOpenSleepTimer,
                onOpenStyleWheel = onArtworkLongPress,
                onToggleVisualizer = { onToggleVisualizer?.invoke() },
                isVisualizerActive = isVisualizerActive,
                onOpenEqualizer = onOpenEqualizer,
                onOpenAudioStudio = onOpenAudioEffects,
                onOpenOutputDevice = onOpenOutputDevice,
                onToggleFocusMode = onToggleFocusMode,
                onOpenCustomization = onOpenCustomization
            )

            if (isVisualizerActive) {
                NowPlayingVisualizerCard(
                    frequencies = frequencies,
                    waveform = waveform,
                    currentStyle = visualizerStyle,
                    onCycleStyle = { onCycleVisualizerStyle?.invoke() }
                )
            }

            ArtworkContainer(
                song = song,
                playbackInfo = playbackInfo,
                appearance = appearance,
                gestureConfig = gestureConfig,
                gestureController = gestureController,
                onTogglePlayPause = onTogglePlayPause,
                onPlayNext = onPlayNext,
                onPlayPrevious = onPlayPrevious,
                onSeekTo = onSeekTo,
                onOpenQueue = onOpenQueue,
                onClose = onClose,
                onArtworkLongPress = onArtworkLongPress,
                onToggleFavorite = onToggleFavorite,
                onCyclePlayerStyle = onCyclePlayerStyle,
                defaultCornerRadius = 28.dp,
                modifier = Modifier
                    .fillMaxWidth(artworkFraction)
                    .aspectRatio(1f)
            )

            TrackMetadataBar(
                song = song,
                appearance = appearance,
                onToggleFavorite = onToggleFavorite,
                onOpenLyrics = onOpenLyrics,
                onOpenQueue = null,
                modifier = Modifier.padding(horizontal = 4.dp)
            )

            AnimatedVisibility(visible = gestureController.controlsVisible) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    SeekbarRenderer(
                        currentPositionMs = playbackInfo.currentPositionMs,
                        durationMs = duration,
                        onSeekTo = onSeekTo,
                        isPlaying = playbackInfo.isPlaying,
                        style = seekbarStyle,
                        liveWaveform = waveform,
                        thicknessScale = appearance.seekbarThicknessScale,
                        hapticFeedbackEnabled = gestureConfig.hapticFeedbackEnabled
                    )

                    AudioShortcutsRow(
                        onOpenAudioEffects = { onOpenAudioEffects?.invoke() },
                        onOpenEqualizer = onOpenEqualizer,
                        onOpenLyrics = onOpenLyrics,
                        onOpenSleepTimer = onOpenSleepTimer
                    )

                    PlaybackControls(
                        playbackInfo = playbackInfo,
                        onTogglePlayPause = onTogglePlayPause,
                        onPlayNext = onPlayNext,
                        onPlayPrevious = onPlayPrevious,
                        onToggleShuffle = onToggleShuffle,
                        onCycleRepeat = onCycleRepeat,
                        appearance = appearance,
                        accentColor = colors.accentStrong,
                        onAccentColor = colors.onAccentStrong,
                        primaryButtonSize = 68.dp
                    )
                }
            }
        }
    }
}

fun formatDurationMs(ms: Long): String {
    val totalSec = (ms / 1000).coerceAtLeast(0)
    val minutes = totalSec / 60
    val seconds = totalSec % 60
    return "%02d:%02d".format(minutes, seconds)
}
