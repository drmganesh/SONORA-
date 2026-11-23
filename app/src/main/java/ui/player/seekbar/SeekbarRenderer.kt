package ui.player.seekbar

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.PlusJakartaFontFamily
import ui.player.formatDurationMs
import ui.theme.LocalEditorialColors
import kotlin.math.roundToInt

/**
 * Universal Seekbar Renderer
 * Dispatches across all 10 SeekbarStyles, smoothly animates live playback progress,
 * supports responsive drag-to-seek with a floating preview timestamp pill, live elapsed
 * & remaining time readouts, single final seekTo commit on release, and graceful
 * live-stream / non-seekable handling across Offline & Online Music.
 */
@Composable
fun SeekbarRenderer(
    currentPositionMs: Long,
    durationMs: Long,
    onSeekTo: (Long) -> Unit,
    isPlaying: Boolean,
    style: SeekbarStyle,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    showTimestamps: Boolean = true,
    accentColor: Color = LocalEditorialColors.current.accentStrong,
    inactiveColor: Color = LocalEditorialColors.current.cardBorder,
    liveWaveform: FloatArray? = null,
    thicknessScale: Float = 1.0f,
    hapticFeedbackEnabled: Boolean = true,
    isSeekable: Boolean = LocalPlaybackSeekCapability.current.isSeekable,
    isLiveStream: Boolean = LocalPlaybackSeekCapability.current.isLiveStream
) {
    val colors = LocalEditorialColors.current
    val haptic = LocalHapticFeedback.current
    val canSeek = enabled && isSeekable && !isLiveStream && durationMs > 0L

    var isDragging by remember(durationMs, isLiveStream, isSeekable) { mutableStateOf(false) }
    var dragProgress by remember(durationMs, isLiveStream, isSeekable) { mutableFloatStateOf(0f) }
    var showRemainingByDefault by remember { mutableStateOf(false) }

    val actualProgress = if (durationMs > 0L && !isLiveStream) {
        (currentPositionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
    } else if (isLiveStream) {
        1f
    } else {
        0f
    }

    val smoothPlaybackProgress by animateFloatAsState(
        targetValue = actualProgress,
        animationSpec = tween(durationMillis = if (isPlaying) 220 else 120, easing = LinearEasing),
        label = "seekbar_smooth_playback_progress"
    )

    val displayProgress = if (isDragging && canSeek) dragProgress else smoothPlaybackProgress
    val displayPositionMs = if (isDragging && canSeek) {
        (dragProgress * durationMs).toLong().coerceIn(0L, durationMs.coerceAtLeast(0L))
    } else {
        currentPositionMs.coerceAtLeast(0L)
    }
    val remainingMs = (durationMs - displayPositionMs).coerceAtLeast(0L)

    val handleProgress: (Float) -> Unit = { frac ->
        if (canSeek) {
            if (!isDragging && hapticFeedbackEnabled) {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            }
            isDragging = true
            dragProgress = frac.coerceIn(0f, 1f)
        }
    }
    val handleConfirmed: (Float) -> Unit = { frac ->
        if (canSeek) {
            if (hapticFeedbackEnabled) {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            }
            val finalFraction = frac.coerceIn(0f, 1f)
            isDragging = false
            onSeekTo((finalFraction * durationMs).toLong().coerceIn(0L, durationMs))
        } else {
            isDragging = false
        }
    }
    val handleCancel: () -> Unit = {
        isDragging = false
    }

    Column(modifier = modifier.fillMaxWidth()) {
        // Floating live preview timestamp bubble while dragging
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .height(if (isDragging && canSeek) 28.dp else 0.dp)
        ) {
            val maxW = constraints.maxWidth.toFloat().coerceAtLeast(1f)
            val pillHalfWidthPx = 56f
            val targetX = ((displayProgress * maxW) - pillHalfWidthPx)
                .coerceIn(0f, (maxW - pillHalfWidthPx * 2f).coerceAtLeast(0f))

            androidx.compose.animation.AnimatedVisibility(
                visible = isDragging && canSeek,
                enter = fadeIn(tween(110)) + scaleIn(tween(110), initialScale = 0.88f),
                exit = fadeOut(tween(120)) + scaleOut(tween(120), targetScale = 0.90f),
                modifier = Modifier.offset { IntOffset(targetX.roundToInt(), 0) }
            ) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = accentColor,
                    shadowElevation = 4.dp,
                    modifier = Modifier.testTag("seekbar_preview_timestamp")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Text(
                            text = formatDurationMs(displayPositionMs),
                            fontFamily = PlusJakartaFontFamily,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = colors.onAccentStrong
                        )
                        Text(
                            text = "• -${formatDurationMs(remainingMs)}",
                            fontFamily = PlusJakartaFontFamily,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Medium,
                            color = colors.onAccentStrong.copy(alpha = 0.85f)
                        )
                    }
                }
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("music_seekbar_track")
        ) {
            when (style) {
                SeekbarStyle.BLOCKS -> BlocksSeekbar(
                    progress = displayProgress,
                    onSeekProgress = handleProgress,
                    onSeekConfirmed = handleConfirmed,
                    onSeekCancelled = handleCancel,
                    isPlaying = isPlaying,
                    enabled = canSeek,
                    accentColor = accentColor,
                    inactiveColor = inactiveColor
                )

                SeekbarStyle.WAVEFORM -> WaveformSeekbar(
                    progress = displayProgress,
                    onSeekProgress = handleProgress,
                    onSeekConfirmed = handleConfirmed,
                    onSeekCancelled = handleCancel,
                    isPlaying = isPlaying,
                    enabled = canSeek,
                    accentColor = accentColor,
                    inactiveColor = inactiveColor,
                    liveWaveform = liveWaveform,
                    heightScale = thicknessScale
                )

                SeekbarStyle.WAVE_LINE -> WaveLineSeekbar(
                    progress = displayProgress,
                    onSeekProgress = handleProgress,
                    onSeekConfirmed = handleConfirmed,
                    onSeekCancelled = handleCancel,
                    isPlaying = isPlaying,
                    enabled = canSeek,
                    accentColor = accentColor,
                    inactiveColor = inactiveColor
                )

                SeekbarStyle.CLASSIC -> ClassicSeekbar(
                    progress = displayProgress,
                    onSeekProgress = handleProgress,
                    onSeekConfirmed = handleConfirmed,
                    onSeekCancelled = handleCancel,
                    isPlaying = isPlaying,
                    enabled = canSeek,
                    accentColor = accentColor,
                    inactiveColor = inactiveColor
                )

                SeekbarStyle.DOTS -> DotsSeekbar(
                    progress = displayProgress,
                    onSeekProgress = handleProgress,
                    onSeekConfirmed = handleConfirmed,
                    onSeekCancelled = handleCancel,
                    isPlaying = isPlaying,
                    enabled = canSeek,
                    accentColor = accentColor,
                    inactiveColor = inactiveColor
                )

                SeekbarStyle.GRADIENT -> GradientSeekbar(
                    progress = displayProgress,
                    onSeekProgress = handleProgress,
                    onSeekConfirmed = handleConfirmed,
                    onSeekCancelled = handleCancel,
                    isPlaying = isPlaying,
                    enabled = canSeek,
                    accentColor = accentColor,
                    inactiveColor = inactiveColor
                )

                SeekbarStyle.NEON_GLOW -> NeonGlowSeekbar(
                    progress = displayProgress,
                    onSeekProgress = handleProgress,
                    onSeekConfirmed = handleConfirmed,
                    onSeekCancelled = handleCancel,
                    isPlaying = isPlaying,
                    enabled = canSeek,
                    accentColor = accentColor,
                    inactiveColor = inactiveColor
                )

                SeekbarStyle.MATERIAL3 -> Material3Seekbar(
                    progress = displayProgress,
                    onSeekProgress = handleProgress,
                    onSeekConfirmed = handleConfirmed,
                    isPlaying = isPlaying,
                    enabled = canSeek,
                    accentColor = accentColor,
                    inactiveColor = inactiveColor
                )

                SeekbarStyle.M3_EXPRESSIVE -> M3ExpressiveSeekbar(
                    progress = displayProgress,
                    onSeekProgress = handleProgress,
                    onSeekConfirmed = handleConfirmed,
                    onSeekCancelled = handleCancel,
                    isPlaying = isPlaying,
                    enabled = canSeek,
                    accentColor = accentColor,
                    inactiveColor = inactiveColor
                )

                SeekbarStyle.CIRCULAR_PROGRESS -> CircularProgressSeekbar(
                    progress = displayProgress,
                    onSeekProgress = handleProgress,
                    onSeekConfirmed = handleConfirmed,
                    onSeekCancelled = handleCancel,
                    isPlaying = isPlaying,
                    enabled = canSeek,
                    accentColor = accentColor,
                    inactiveColor = inactiveColor
                )
            }
        }

        if (showTimestamps) {
            Spacer(modifier = Modifier.height(2.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (isLiveStream) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color(0xFFD32F2F).copy(alpha = 0.16f),
                        border = BorderStroke(0.75.dp, Color(0xFFD32F2F).copy(alpha = 0.55f)),
                        modifier = Modifier.testTag("seekbar_live_badge")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFFEF4444))
                            )
                            Spacer(modifier = Modifier.width(5.dp))
                            Text(
                                text = "LIVE",
                                fontFamily = PlusJakartaFontFamily,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 10.sp,
                                color = Color(0xFFEF4444)
                            )
                        }
                    }
                    Text(
                        text = "Live stream",
                        fontFamily = PlusJakartaFontFamily,
                        fontWeight = FontWeight.Medium,
                        color = colors.textMuted,
                        fontSize = 11.sp
                    )
                } else {
                    Text(
                        text = formatDurationMs(displayPositionMs),
                        fontFamily = PlusJakartaFontFamily,
                        fontWeight = if (isDragging) FontWeight.Bold else FontWeight.Medium,
                        color = if (isDragging) accentColor else colors.textSecondary,
                        fontSize = 11.sp,
                        modifier = Modifier.testTag("seekbar_elapsed_time")
                    )

                    if (!isSeekable && durationMs > 0L) {
                        Text(
                            text = "Non-seekable",
                            fontFamily = PlusJakartaFontFamily,
                            fontWeight = FontWeight.Medium,
                            color = colors.textMuted,
                            fontSize = 10.sp,
                            modifier = Modifier.testTag("seekbar_non_seekable_label")
                        )
                    }

                    val rightLabel = if (isDragging || showRemainingByDefault) {
                        "-${formatDurationMs(remainingMs)}"
                    } else {
                        formatDurationMs(durationMs)
                    }
                    Text(
                        text = rightLabel,
                        fontFamily = PlusJakartaFontFamily,
                        fontWeight = if (isDragging) FontWeight.Bold else FontWeight.Medium,
                        color = if (isDragging) accentColor else colors.textSecondary,
                        fontSize = 11.sp,
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .clickable(enabled = durationMs > 0L) {
                                showRemainingByDefault = !showRemainingByDefault
                            }
                            .padding(horizontal = 2.dp)
                            .testTag("seekbar_remaining_or_total_time")
                    )
                }
            }
        }
    }
}
