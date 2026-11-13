package ui.player.styles

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.model.RepeatMode
import com.example.model.ShuffleMode
import ui.components.AdaptiveSeekbar
import ui.components.ArtworkContainer
import ui.components.TrackMetadataBar
import ui.player.NowPlayingTopHeader
import ui.player.PlayerGestureController
import ui.player.PlayerRenderState
import ui.player.PlayerStyleCallbacks
import ui.player.PlayerStyleManager
import ui.theme.LocalEditorialColors
import ui.theme.LocalReduceAnimations

/**
 * Style 3: Material You
 * Expressive Material 3 tonal container architecture with morphing play/pause shape
 * and cohesive dynamic surface elevation.
 */
@Composable
fun MaterialYouPlayer(
    state: PlayerRenderState,
    callbacks: PlayerStyleCallbacks,
    gestureController: PlayerGestureController,
    modifier: Modifier = Modifier
) {
    val colors = LocalEditorialColors.current
    val m3 = MaterialTheme.colorScheme
    val reduceAnim = LocalReduceAnimations.current
    val song = state.song
    val duration = state.playbackInfo.durationMs.coerceAtLeast(song?.durationMs ?: 0L)
    val accent = PlayerStyleManager.resolveAccentColor(state.appearance, colors, state.dynamicPalette)

    val playCorner by animateDpAsState(
        targetValue = if (state.playbackInfo.isPlaying) 22.dp else 38.dp,
        animationSpec = tween(if (reduceAnim) 0 else 280),
        label = "m3PlayCorner"
    )

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

            Surface(
                modifier = Modifier
                    .fillMaxWidth(state.appearance.artworkSize.fraction.coerceIn(0.72f, 0.96f))
                    .aspectRatio(1f),
                shape = RoundedCornerShape(36.dp),
                color = m3.surfaceContainerHigh,
                tonalElevation = 6.dp
            ) {
                Box(modifier = Modifier.fillMaxSize().padding(10.dp)) {
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
                        defaultCornerRadius = 28.dp,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }

            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(28.dp),
                color = colors.surfaceTinted,
                tonalElevation = 3.dp
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 18.dp, vertical = 16.dp)
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
                        accentColor = accent
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        FilledTonalIconButton(
                            onClick = callbacks.onToggleShuffle,
                            colors = IconButtonDefaults.filledTonalIconButtonColors(
                                containerColor = if (state.playbackInfo.shuffleMode == ShuffleMode.ON) accent else colors.surface,
                                contentColor = if (state.playbackInfo.shuffleMode == ShuffleMode.ON) colors.onAccentStrong else colors.textSecondary
                            ),
                            modifier = Modifier.size(48.dp)
                        ) {
                            Icon(Icons.Default.Shuffle, contentDescription = "Shuffle")
                        }

                        FilledTonalIconButton(
                            onClick = callbacks.onPlayPrevious,
                            colors = IconButtonDefaults.filledTonalIconButtonColors(
                                containerColor = colors.surface,
                                contentColor = colors.textPrimary
                            ),
                            modifier = Modifier.size(52.dp)
                        ) {
                            Icon(Icons.Default.SkipPrevious, contentDescription = "Previous", modifier = Modifier.size(28.dp))
                        }

                        Surface(
                            onClick = callbacks.onTogglePlayPause,
                            shape = RoundedCornerShape(playCorner),
                            color = accent,
                            shadowElevation = 6.dp,
                            modifier = Modifier
                                .size(width = 84.dp, height = 68.dp)
                                .testTag("play_pause_button")
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = if (state.playbackInfo.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                    contentDescription = if (state.playbackInfo.isPlaying) "Pause" else "Play",
                                    tint = colors.onAccentStrong,
                                    modifier = Modifier.size(34.dp)
                                )
                            }
                        }

                        FilledTonalIconButton(
                            onClick = callbacks.onPlayNext,
                            colors = IconButtonDefaults.filledTonalIconButtonColors(
                                containerColor = colors.surface,
                                contentColor = colors.textPrimary
                            ),
                            modifier = Modifier.size(52.dp)
                        ) {
                            Icon(Icons.Default.SkipNext, contentDescription = "Next", modifier = Modifier.size(28.dp))
                        }

                        FilledTonalIconButton(
                            onClick = callbacks.onCycleRepeat,
                            colors = IconButtonDefaults.filledTonalIconButtonColors(
                                containerColor = if (state.playbackInfo.repeatMode != RepeatMode.OFF) accent else colors.surface,
                                contentColor = if (state.playbackInfo.repeatMode != RepeatMode.OFF) colors.onAccentStrong else colors.textSecondary
                            ),
                            modifier = Modifier.size(48.dp)
                        ) {
                            val icon = if (state.playbackInfo.repeatMode == RepeatMode.ONE) Icons.Default.RepeatOne else Icons.Default.Repeat
                            Icon(icon, contentDescription = "Repeat")
                        }
                    }
                }
            }
        }
    }
}
