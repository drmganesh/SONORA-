package ui.player.styles

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Lyrics
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.example.model.RepeatMode
import com.example.model.ShuffleMode
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
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Style 16: Orbit
 * Concentric planetary rings encircling circular artwork with a glowing progress planet
 * and satellite action nodes.
 */
@Composable
fun OrbitPlayer(
    state: PlayerRenderState,
    callbacks: PlayerStyleCallbacks,
    gestureController: PlayerGestureController,
    modifier: Modifier = Modifier
) {
    val colors = LocalEditorialColors.current
    val song = state.song
    val duration = state.playbackInfo.durationMs.coerceAtLeast(song?.durationMs ?: 0L)
    val accent = PlayerStyleManager.resolveAccentColor(state.appearance, colors, state.dynamicPalette)
    val progress = if (duration > 0L) (state.playbackInfo.currentPositionMs.toFloat() / duration.toFloat()).coerceIn(0f, 1f) else 0f

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
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val center = Offset(size.width / 2f, size.height / 2f)
                    val outerRadius = size.minDimension / 2f - 10.dp.toPx()
                    val innerRadius = outerRadius * 0.84f

                    drawCircle(
                        color = colors.cardBorder.copy(alpha = 0.55f),
                        radius = outerRadius,
                        center = center,
                        style = Stroke(width = 1.5.dp.toPx())
                    )
                    drawArc(
                        color = accent,
                        startAngle = -90f,
                        sweepAngle = 360f * progress,
                        useCenter = false,
                        topLeft = Offset(center.x - outerRadius, center.y - outerRadius),
                        size = Size(outerRadius * 2f, outerRadius * 2f),
                        style = Stroke(width = 3.5.dp.toPx(), cap = StrokeCap.Round)
                    )
                    drawCircle(
                        color = colors.cardBorder.copy(alpha = 0.35f),
                        radius = innerRadius,
                        center = center,
                        style = Stroke(width = 1.dp.toPx())
                    )

                    val angleRad = ((-90f + 360f * progress) * (PI / 180.0))
                    val px = center.x + outerRadius * cos(angleRad).toFloat()
                    val py = center.y + outerRadius * sin(angleRad).toFloat()
                    drawCircle(color = accent, radius = 7.dp.toPx(), center = Offset(px, py))
                    drawCircle(color = colors.onAccentStrong, radius = 2.5.dp.toPx(), center = Offset(px, py))
                }

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
                    modifier = Modifier.fillMaxSize(0.64f)
                )

                // Four Corner Satellite Nodes
                Surface(
                    onClick = callbacks.onToggleFavorite,
                    shape = CircleShape,
                    color = colors.surface,
                    shadowElevation = 4.dp,
                    modifier = Modifier.align(Alignment.TopStart).padding(12.dp).size(48.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Default.Favorite,
                            contentDescription = "Favorite",
                            tint = if (song?.isFavorite == true) accent else colors.textMuted,
                            modifier = Modifier.size(19.dp)
                        )
                    }
                }

                Surface(
                    onClick = callbacks.onOpenLyrics,
                    shape = CircleShape,
                    color = colors.surface,
                    shadowElevation = 4.dp,
                    modifier = Modifier.align(Alignment.TopEnd).padding(12.dp).size(48.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.Lyrics, contentDescription = "Lyrics", tint = accent, modifier = Modifier.size(19.dp))
                    }
                }

                Surface(
                    onClick = callbacks.onToggleShuffle,
                    shape = CircleShape,
                    color = colors.surface,
                    shadowElevation = 4.dp,
                    modifier = Modifier.align(Alignment.BottomStart).padding(12.dp).size(48.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Default.Shuffle,
                            contentDescription = "Shuffle",
                            tint = if (state.playbackInfo.shuffleMode == ShuffleMode.ON) accent else colors.textMuted,
                            modifier = Modifier.size(19.dp)
                        )
                    }
                }

                Surface(
                    onClick = callbacks.onCycleRepeat,
                    shape = CircleShape,
                    color = colors.surface,
                    shadowElevation = 4.dp,
                    modifier = Modifier.align(Alignment.BottomEnd).padding(12.dp).size(48.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        val icon = if (state.playbackInfo.repeatMode == RepeatMode.ONE) Icons.Default.RepeatOne else Icons.Default.Repeat
                        val tint = if (state.playbackInfo.repeatMode != RepeatMode.OFF) accent else colors.textMuted
                        Icon(icon, contentDescription = "Repeat", tint = tint, modifier = Modifier.size(19.dp))
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
                    onToggleFavorite = null,
                    onOpenLyrics = null,
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
                    appearance = state.appearance.copy(controlArrangement = com.example.model.ControlArrangement.MINIMAL_DECK),
                    accentColor = accent,
                    primaryButtonSize = 68.dp
                )
            }
        }
    }
}
