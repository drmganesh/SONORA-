package ui.player.styles

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
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
 * Style 4: Glass
 * Translucent frosted-glass aesthetic layered over album-art ambient colors
 * with subtle hairline specular borders.
 */
@Composable
fun GlassPlayer(
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

    val glassSurface = if (colors.isDark) {
        Color.White.copy(alpha = 0.09f)
    } else {
        Color.White.copy(alpha = 0.62f)
    }
    val glassBorder = if (colors.isDark) {
        Color.White.copy(alpha = 0.22f)
    } else {
        Color.White.copy(alpha = 0.85f)
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.linearGradient(
                    colors = listOf(
                        palette.primary.copy(alpha = if (colors.isDark) 0.30f else 0.22f),
                        colors.background,
                        palette.secondary.copy(alpha = if (colors.isDark) 0.24f else 0.18f)
                    )
                )
            )
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

            Surface(
                shape = RoundedCornerShape(34.dp),
                color = glassSurface,
                border = BorderStroke(1.2.dp, glassBorder),
                modifier = Modifier
                    .fillMaxWidth(state.appearance.artworkSize.fraction.coerceIn(0.70f, 0.92f))
                    .aspectRatio(1f)
            ) {
                Box(modifier = Modifier.fillMaxSize().padding(12.dp)) {
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
                        defaultCornerRadius = 24.dp,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }

            Surface(
                shape = RoundedCornerShape(30.dp),
                color = glassSurface,
                border = BorderStroke(1.dp, glassBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 16.dp)
                ) {
                    TrackMetadataBar(
                        song = song,
                        appearance = state.appearance,
                        onToggleFavorite = callbacks.onToggleFavorite,
                        onOpenLyrics = callbacks.onOpenLyrics,
                        onOpenQueue = callbacks.onOpenQueue,
                        accentColor = accent
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    AdaptiveSeekbar(
                        currentPositionMs = state.playbackInfo.currentPositionMs,
                        durationMs = duration,
                        onSeekTo = callbacks.onSeekTo,
                        isPlaying = state.playbackInfo.isPlaying,
                        style = state.seekbarStyle,
                        appearance = state.appearance,
                        liveWaveform = state.waveformSamples,
                        hapticFeedbackEnabled = state.gestureConfig.hapticFeedbackEnabled,
                        accentColor = accent,
                        inactiveColor = glassBorder.copy(alpha = 0.55f)
                    )

                    Spacer(modifier = Modifier.height(12.dp))

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
}
