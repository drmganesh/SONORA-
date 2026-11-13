package ui.player.seekbar

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.PlusJakartaFontFamily
import ui.player.formatDurationMs
import ui.theme.LocalEditorialColors

/**
 * Universal Seekbar Renderer
 * Dispatches across all 10 SeekbarStyles, coordinates drag seeking vs live playback updates,
 * supports long-press 0.25x fine scrubbing & haptic feedback, and displays duration timestamps.
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
    hapticFeedbackEnabled: Boolean = true
) {
    val colors = LocalEditorialColors.current
    val haptic = LocalHapticFeedback.current
    var isDragging by remember { mutableStateOf(false) }
    var isFineScrubbing by remember { mutableStateOf(false) }
    var dragProgress by remember { mutableFloatStateOf(0f) }

    val actualProgress = if (durationMs > 0) {
        (currentPositionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
    } else 0f

    val displayProgress = if (isDragging) dragProgress else actualProgress
    val displayPositionMs = if (isDragging) {
        (dragProgress * durationMs).toLong().coerceIn(0L, durationMs.coerceAtLeast(0L))
    } else currentPositionMs

    val handleProgress: (Float) -> Unit = { frac ->
        if (!isDragging && hapticFeedbackEnabled) {
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        }
        isDragging = true
        dragProgress = frac.coerceIn(0f, 1f)
    }
    val handleConfirmed: (Float) -> Unit = { frac ->
        if (hapticFeedbackEnabled) {
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        }
        isDragging = false
        isFineScrubbing = false
        onSeekTo((frac.coerceIn(0f, 1f) * durationMs).toLong())
    }

    Column(modifier = modifier.fillMaxWidth()) {
        AnimatedVisibility(
            visible = isFineScrubbing,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 4.dp),
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = colors.accentPill
                ) {
                    Text(
                        text = "Fine Scrubbing • ${formatDurationMs(displayPositionMs)}",
                        fontFamily = PlusJakartaFontFamily,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = colors.onAccentPill,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp)
                    )
                }
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .pointerInput(enabled, durationMs) {
                    if (!enabled || durationMs <= 0L) return@pointerInput
                    detectDragGesturesAfterLongPress(
                        onDragStart = {
                            if (hapticFeedbackEnabled) {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            }
                            isFineScrubbing = true
                            isDragging = true
                            dragProgress = actualProgress
                        },
                        onDragEnd = {
                            if (isFineScrubbing) {
                                handleConfirmed(dragProgress)
                            }
                        },
                        onDragCancel = {
                            isFineScrubbing = false
                            isDragging = false
                        },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            val deltaFraction = (dragAmount.x / size.width.coerceAtLeast(1)) * 0.25f
                            dragProgress = (dragProgress + deltaFraction).coerceIn(0f, 1f)
                        }
                    )
                }
        ) {
            when (style) {
                SeekbarStyle.BLOCKS -> BlocksSeekbar(
                    progress = displayProgress,
                    onSeekProgress = handleProgress,
                    onSeekConfirmed = handleConfirmed,
                    isPlaying = isPlaying,
                    enabled = enabled,
                    accentColor = accentColor,
                    inactiveColor = inactiveColor
                )

                SeekbarStyle.WAVEFORM -> WaveformSeekbar(
                    progress = displayProgress,
                    onSeekProgress = handleProgress,
                    onSeekConfirmed = handleConfirmed,
                    isPlaying = isPlaying,
                    enabled = enabled,
                    accentColor = accentColor,
                    inactiveColor = inactiveColor,
                    liveWaveform = liveWaveform,
                    heightScale = thicknessScale
                )

                SeekbarStyle.WAVE_LINE -> WaveLineSeekbar(
                    progress = displayProgress,
                    onSeekProgress = handleProgress,
                    onSeekConfirmed = handleConfirmed,
                    isPlaying = isPlaying,
                    enabled = enabled,
                    accentColor = accentColor,
                    inactiveColor = inactiveColor
                )

                SeekbarStyle.CLASSIC -> ClassicSeekbar(
                    progress = displayProgress,
                    onSeekProgress = handleProgress,
                    onSeekConfirmed = handleConfirmed,
                    isPlaying = isPlaying,
                    enabled = enabled,
                    accentColor = accentColor,
                    inactiveColor = inactiveColor
                )

                SeekbarStyle.DOTS -> DotsSeekbar(
                    progress = displayProgress,
                    onSeekProgress = handleProgress,
                    onSeekConfirmed = handleConfirmed,
                    isPlaying = isPlaying,
                    enabled = enabled,
                    accentColor = accentColor,
                    inactiveColor = inactiveColor
                )

                SeekbarStyle.GRADIENT -> GradientSeekbar(
                    progress = displayProgress,
                    onSeekProgress = handleProgress,
                    onSeekConfirmed = handleConfirmed,
                    isPlaying = isPlaying,
                    enabled = enabled,
                    accentColor = accentColor,
                    inactiveColor = inactiveColor
                )

                SeekbarStyle.NEON_GLOW -> NeonGlowSeekbar(
                    progress = displayProgress,
                    onSeekProgress = handleProgress,
                    onSeekConfirmed = handleConfirmed,
                    isPlaying = isPlaying,
                    enabled = enabled,
                    accentColor = accentColor,
                    inactiveColor = inactiveColor
                )

                SeekbarStyle.MATERIAL3 -> Material3Seekbar(
                    progress = displayProgress,
                    onSeekProgress = handleProgress,
                    onSeekConfirmed = handleConfirmed,
                    isPlaying = isPlaying,
                    enabled = enabled,
                    accentColor = accentColor,
                    inactiveColor = inactiveColor
                )

                SeekbarStyle.M3_EXPRESSIVE -> M3ExpressiveSeekbar(
                    progress = displayProgress,
                    onSeekProgress = handleProgress,
                    onSeekConfirmed = handleConfirmed,
                    isPlaying = isPlaying,
                    enabled = enabled,
                    accentColor = accentColor,
                    inactiveColor = inactiveColor
                )

                SeekbarStyle.CIRCULAR_PROGRESS -> CircularProgressSeekbar(
                    progress = displayProgress,
                    onSeekProgress = handleProgress,
                    onSeekConfirmed = handleConfirmed,
                    isPlaying = isPlaying,
                    enabled = enabled,
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
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = formatDurationMs(displayPositionMs),
                    fontFamily = PlusJakartaFontFamily,
                    fontWeight = FontWeight.Medium,
                    color = colors.textSecondary,
                    fontSize = 11.sp
                )
                Text(
                    text = formatDurationMs(durationMs),
                    fontFamily = PlusJakartaFontFamily,
                    fontWeight = FontWeight.Medium,
                    color = colors.textSecondary,
                    fontSize = 11.sp
                )
            }
        }
    }
}
