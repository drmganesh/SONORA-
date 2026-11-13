package ui.player.styles

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
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
import ui.theme.LocalReduceAnimations

/**
 * Style 10: Vinyl
 * Turntable presentation featuring rotating vinyl disc with concentric grooves,
 * album label center, and pivoting tonearm linked to playback state.
 */
@Composable
fun VinylPlayer(
    state: PlayerRenderState,
    callbacks: PlayerStyleCallbacks,
    gestureController: PlayerGestureController,
    modifier: Modifier = Modifier
) {
    val colors = LocalEditorialColors.current
    val reduceAnim = LocalReduceAnimations.current
    val song = state.song
    val duration = state.playbackInfo.durationMs.coerceAtLeast(song?.durationMs ?: 0L)
    val accent = PlayerStyleManager.resolveAccentColor(state.appearance, colors, state.dynamicPalette)

    val tonearmAngle by animateFloatAsState(
        targetValue = if (state.playbackInfo.isPlaying) 24f else 4f,
        animationSpec = tween(if (reduceAnim) 0 else 450),
        label = "tonearmAngle"
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

            Box(
                modifier = Modifier
                    .fillMaxWidth(state.appearance.artworkSize.fraction.coerceIn(0.76f, 0.94f))
                    .aspectRatio(1f),
                contentAlignment = Alignment.Center
            ) {
                // Vinyl Platter & Grooves
                Box(
                    modifier = Modifier
                        .fillMaxSize(0.92f)
                        .clip(CircleShape)
                        .background(Color(0xFF14151A))
                        .border(2.dp, colors.cardBorder, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val r = size.minDimension / 2f
                        val c = Offset(size.width / 2f, size.height / 2f)
                        for (ring in 1..6) {
                            drawCircle(
                                color = Color.White.copy(alpha = if (ring % 2 == 0) 0.08f else 0.04f),
                                radius = r * (0.48f + ring * 0.08f),
                                center = c,
                                style = Stroke(width = 1.dp.toPx())
                            )
                        }
                    }

                    // Rotating Center Label Artwork
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
                        isCircular = true,
                        forceVinylSpin = true,
                        modifier = Modifier.fillMaxSize(0.62f),
                        overlayContent = {
                            Surface(
                                shape = CircleShape,
                                color = colors.background,
                                border = androidx.compose.foundation.BorderStroke(2.dp, accent),
                                modifier = Modifier.size(22.dp)
                            ) {}
                        }
                    )
                }

                // Pivoting Tonearm
                Canvas(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .size(width = 64.dp, height = 148.dp)
                        .padding(top = 8.dp, end = 6.dp)
                        .graphicsLayer {
                            transformOrigin = TransformOrigin(0.7f, 0.12f)
                            rotationZ = tonearmAngle
                        }
                ) {
                    val pivot = Offset(size.width * 0.7f, size.height * 0.12f)
                    val elbow = Offset(size.width * 0.58f, size.height * 0.68f)
                    val head = Offset(size.width * 0.25f, size.height * 0.90f)

                    drawCircle(color = colors.surfaceElevated, radius = 12.dp.toPx(), center = pivot)
                    drawCircle(color = accent, radius = 5.dp.toPx(), center = pivot)
                    drawLine(color = colors.textSecondary, start = pivot, end = elbow, strokeWidth = 5.dp.toPx(), cap = StrokeCap.Round)
                    drawLine(color = colors.textPrimary, start = elbow, end = head, strokeWidth = 6.dp.toPx(), cap = StrokeCap.Round)
                    drawCircle(color = accent, radius = 4.dp.toPx(), center = head)
                }
            }

            Column(
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
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
                    primaryButtonSize = 68.dp
                )
            }
        }
    }
}
