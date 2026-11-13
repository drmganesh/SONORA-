package ui.player.styles

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.OutfitFontFamily
import com.example.ui.theme.PlusJakartaFontFamily
import ui.components.ArtworkContainer
import ui.components.PlaybackControls
import ui.components.TrackMetadataBar
import ui.player.NowPlayingTopHeader
import ui.player.PlayerGestureController
import ui.player.PlayerRenderState
import ui.player.PlayerStyleCallbacks
import ui.player.PlayerStyleManager
import ui.player.formatDurationMs
import ui.theme.LocalEditorialColors
import kotlin.math.abs

/**
 * Style 14: Waveform
 * Studio waveform centerpiece reflecting real PCM samples during playback
 * with direct tap & scrub seeking across a tall expressive waveform deck.
 */
@Composable
fun WaveformPlayer(
    state: PlayerRenderState,
    callbacks: PlayerStyleCallbacks,
    gestureController: PlayerGestureController,
    modifier: Modifier = Modifier
) {
    val colors = LocalEditorialColors.current
    val song = state.song
    val duration = state.playbackInfo.durationMs.coerceAtLeast(song?.durationMs ?: 0L)
    val accent = PlayerStyleManager.resolveAccentColor(state.appearance, colors, state.dynamicPalette)

    var isScrubbing by remember { mutableStateOf(false) }
    var scrubProgress by remember { mutableFloatStateOf(0f) }
    val actualProgress = if (duration > 0L) {
        (state.playbackInfo.currentPositionMs.toFloat() / duration.toFloat()).coerceIn(0f, 1f)
    } else 0f
    val displayProgress = if (isScrubbing) scrubProgress else actualProgress
    val displayPosMs = if (isScrubbing) (scrubProgress * duration).toLong() else state.playbackInfo.currentPositionMs

    val hasRealAudio = state.isNowPlayingActive && state.playbackInfo.isPlaying &&
        state.waveformSamples.any { abs(it) > 0.01f }

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

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
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
                    defaultCornerRadius = 22.dp,
                    modifier = Modifier.size(96.dp)
                )

                Spacer(modifier = Modifier.width(16.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (hasRealAudio) "LIVE PCM WAVEFORM" else "STUDIO WAVEFORM",
                        fontFamily = OutfitFontFamily,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.4.sp,
                        color = accent
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = song?.title ?: "No Track Selected",
                        fontFamily = OutfitFontFamily,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = colors.textPrimary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = song?.artist ?: "Unknown Artist",
                        fontFamily = PlusJakartaFontFamily,
                        fontSize = 13.sp,
                        color = colors.textSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Surface(
                shape = RoundedCornerShape(28.dp),
                color = colors.surface,
                border = BorderStroke(1.dp, colors.cardBorder),
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(vertical = 12.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(18.dp),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = formatDurationMs(displayPosMs),
                            fontFamily = OutfitFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = accent
                        )
                        Text(
                            text = formatDurationMs(duration),
                            fontFamily = OutfitFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = colors.textMuted
                        )
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .padding(vertical = 12.dp)
                            .pointerInput(duration) {
                                detectTapGestures { offset ->
                                    val frac = (offset.x / size.width).coerceIn(0f, 1f)
                                    callbacks.onSeekTo((frac * duration).toLong())
                                }
                            }
                            .pointerInput(duration) {
                                detectDragGestures(
                                    onDragStart = { offset ->
                                        isScrubbing = true
                                        scrubProgress = (offset.x / size.width).coerceIn(0f, 1f)
                                    },
                                    onDragEnd = {
                                        isScrubbing = false
                                        callbacks.onSeekTo((scrubProgress * duration).toLong())
                                    },
                                    onDragCancel = { isScrubbing = false },
                                    onDrag = { change, _ ->
                                        change.consume()
                                        scrubProgress = (change.position.x / size.width).coerceIn(0f, 1f)
                                    }
                                )
                            }
                    ) {
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            val barCount = 48
                            val gap = 3.dp.toPx()
                            val barW = ((size.width - gap * (barCount - 1)) / barCount).coerceAtLeast(2f)
                            val centerY = size.height / 2f
                            val maxH = size.height * 0.92f
                            val samples = state.waveformSamples

                            for (i in 0 until barCount) {
                                val x = i * (barW + gap)
                                val frac = (i + 0.5f) / barCount
                                val played = frac <= displayProgress
                                val amp = if (hasRealAudio && samples.isNotEmpty()) {
                                    val idx = (i * samples.size / barCount).coerceIn(0, samples.lastIndex)
                                    (abs(samples[idx]) * 2.4f).coerceIn(0.14f, 1.0f)
                                } else {
                                    // Deterministic calm envelope when paused/idle
                                    val env = 0.25f + 0.20f * abs(kotlin.math.sin(i * 0.45f))
                                    env.coerceIn(0.15f, 0.45f)
                                }
                                val h = (maxH * amp).coerceAtLeast(8.dp.toPx())
                                drawRoundRect(
                                    color = if (played) accent else colors.cardBorder,
                                    topLeft = Offset(x, centerY - h / 2f),
                                    size = Size(barW, h),
                                    cornerRadius = CornerRadius(barW / 2f, barW / 2f)
                                )
                            }
                        }
                    }

                    Text(
                        text = if (state.appearance.showAudioFormatMetadata && song != null) {
                            PlayerStyleManager.formatTrackQualityBadge(song)
                        } else {
                            "Drag waveform to seek precision position"
                        },
                        fontFamily = PlusJakartaFontFamily,
                        fontSize = 11.sp,
                        color = colors.textSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Column(
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                TrackMetadataBar(
                    song = song,
                    appearance = state.appearance.copy(showAudioFormatMetadata = false),
                    onToggleFavorite = callbacks.onToggleFavorite,
                    onOpenLyrics = callbacks.onOpenLyrics,
                    onOpenQueue = callbacks.onOpenQueue,
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
