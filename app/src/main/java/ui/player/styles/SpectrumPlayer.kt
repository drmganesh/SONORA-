package ui.player.styles

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.dp
import ui.components.AdaptiveSeekbar
import ui.components.ArtworkContainer
import ui.components.PlaybackControls
import ui.components.TrackMetadataBar
import ui.player.NowPlayingTopHeader
import ui.player.PlayerGestureController
import ui.player.PlayerRenderState
import ui.player.PlayerStyleCallbacks
import ui.player.PlayerStyleManager
import ui.theme.LocalEditorialColors

/**
 * Style 15: Spectrum
 * Live 32-band FFT spectrum analyzer canvas synced directly with SonoraAudioEngine
 * FFT telemetry when actively playing, resting cleanly at baseline when paused.
 */
@Composable
fun SpectrumPlayer(
    state: PlayerRenderState,
    callbacks: PlayerStyleCallbacks,
    gestureController: PlayerGestureController,
    modifier: Modifier = Modifier
) {
    val colors = LocalEditorialColors.current
    val palette = state.dynamicPalette
    val song = state.song
    val duration = state.playbackInfo.durationMs.coerceAtLeast(song?.durationMs ?: 0L)
    val accent = PlayerStyleManager.resolveAccentColor(state.appearance, colors, palette)

    val isActiveAudio = state.isNowPlayingActive && state.playbackInfo.isPlaying

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
                .padding(horizontal = 20.dp, vertical = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            NowPlayingTopHeader(
                song = song,
                onClose = callbacks.onClose,
                onOpenQueue = callbacks.onOpenQueue,
                onOpenLyrics = callbacks.onOpenLyrics,
                onOpenSleepTimer = callbacks.onOpenSleepTimer,
                onOpenStyleWheel = callbacks.onArtworkLongPress,
                onToggleVisualizer = callbacks.onToggleVisualizer,
                isVisualizerActive = state.isVisualizerActive,
                onOpenEqualizer = callbacks.onOpenEqualizer,
                onOpenAudioStudio = callbacks.onOpenAudioEffects,
                onOpenOutputDevice = callbacks.onOpenOutputDevice,
                onToggleFocusMode = callbacks.onToggleFocusMode,
                onOpenCustomization = callbacks.onOpenCustomization
            )

            ArtworkContainer(
                song = song,
                playbackInfo = state.playbackInfo,
                appearance = state.appearance,
                gestureConfig = state.gestureConfig,
                gestureController = gestureController,
                onTogglePlayPause = callbacks.onTogglePlayPause,
                onPlayNext = callbacks.onPlayNext,
                onPlayPrevious = callbacks.onPlayPrevious,
                onSeekTo = callbacks.onSeekTo,
                onOpenQueue = callbacks.onOpenQueue,
                onClose = callbacks.onClose,
                onArtworkLongPress = callbacks.onArtworkLongPress,
                onToggleFavorite = callbacks.onToggleFavorite,
                onCyclePlayerStyle = callbacks.onCyclePlayerStyle,
                defaultCornerRadius = 26.dp,
                modifier = Modifier
                    .fillMaxWidth(0.56f)
                    .aspectRatio(1f)
            )

            Surface(
                shape = RoundedCornerShape(24.dp),
                color = colors.surface,
                border = BorderStroke(1.dp, colors.cardBorder),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(126.dp)
            ) {
                Canvas(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp, vertical = 14.dp)
                ) {
                    val bands = state.frequencies
                    val count = bands.size.coerceAtLeast(24)
                    val gap = 3.dp.toPx()
                    val barW = ((size.width - gap * (count - 1)) / count).coerceAtLeast(2f)

                    for (i in 0 until count) {
                        val mag = if (isActiveAudio && i < bands.size) {
                            bands[i].coerceIn(0.06f, 1.0f)
                        } else {
                            0.08f
                        }
                        val barH = (size.height * mag).coerceAtLeast(4.dp.toPx())
                        val x = i * (barW + gap)
                        val y = size.height - barH

                        drawRoundRect(
                            brush = Brush.verticalGradient(
                                colors = listOf(accent, palette.secondary.copy(alpha = 0.7f))
                            ),
                            topLeft = Offset(x, y),
                            size = Size(barW, barH),
                            cornerRadius = CornerRadius(barW / 2f, barW / 2f)
                        )
                        // Peak cap indicator
                        drawRoundRect(
                            color = colors.textPrimary.copy(alpha = if (isActiveAudio) 0.75f else 0.25f),
                            topLeft = Offset(x, (y - 4.dp.toPx()).coerceAtLeast(0f)),
                            size = Size(barW, 2.dp.toPx()),
                            cornerRadius = CornerRadius(1.dp.toPx(), 1.dp.toPx())
                        )
                    }
                }
            }

            Column(
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                TrackMetadataBar(
                    song = song,
                    appearance = state.appearance,
                    onToggleFavorite = callbacks.onToggleFavorite,
                    onOpenLyrics = callbacks.onOpenLyrics,
                    onOpenQueue = callbacks.onOpenQueue,
                    accentColor = accent
                )

                AdaptiveSeekbar(
                    currentPositionMs = state.playbackInfo.currentPositionMs,
                    durationMs = duration,
                    onSeekTo = callbacks.onSeekTo,
                    isPlaying = state.playbackInfo.isPlaying,
                    style = state.seekbarStyle,
                    appearance = state.appearance,
                    liveWaveform = state.waveformSamples,
                    hapticFeedbackEnabled = state.gestureConfig.hapticFeedbackEnabled,
                    accentColor = accent
                )

                PlaybackControls(
                    playbackInfo = state.playbackInfo,
                    onTogglePlayPause = callbacks.onTogglePlayPause,
                    onPlayNext = callbacks.onPlayNext,
                    onPlayPrevious = callbacks.onPlayPrevious,
                    onToggleShuffle = callbacks.onToggleShuffle,
                    onCycleRepeat = callbacks.onCycleRepeat,
                    appearance = state.appearance,
                    accentColor = accent,
                    primaryButtonSize = 66.dp
                )
            }
        }
    }
}
