package ui.player.seekbar

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.SonoraCharcoalBorder
import com.example.ui.theme.SonoraDustyRose
import com.example.ui.theme.SonoraTextMuted
import ui.player.formatDurationMs

/**
 * Universal Seekbar Renderer
 * Dispatches to the active SeekbarStyle, coordinates drag seeking vs live playback updates,
 * and displays accurate duration timestamps.
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
    accentColor: Color = SonoraDustyRose,
    inactiveColor: Color = SonoraCharcoalBorder
) {
    var isDragging by remember { mutableStateOf(false) }
    var dragProgress by remember { mutableFloatStateOf(0f) }

    val actualProgress = if (durationMs > 0) {
        (currentPositionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
    } else 0f

    val displayProgress = if (isDragging) dragProgress else actualProgress
    val displayPositionMs = if (isDragging) {
        (dragProgress * durationMs).toLong().coerceIn(0L, durationMs.coerceAtLeast(0L))
    } else currentPositionMs

    Column(modifier = modifier.fillMaxWidth()) {
        when (style) {
            SeekbarStyle.BLOCKS -> BlocksSeekbar(
                progress = displayProgress,
                onSeekProgress = { frac ->
                    isDragging = true
                    dragProgress = frac
                },
                onSeekConfirmed = { frac ->
                    isDragging = false
                    onSeekTo((frac * durationMs).toLong())
                },
                isPlaying = isPlaying,
                enabled = enabled,
                accentColor = accentColor,
                inactiveColor = inactiveColor
            )

            SeekbarStyle.WAVEFORM -> WaveformSeekbar(
                progress = displayProgress,
                onSeekProgress = { frac ->
                    isDragging = true
                    dragProgress = frac
                },
                onSeekConfirmed = { frac ->
                    isDragging = false
                    onSeekTo((frac * durationMs).toLong())
                },
                isPlaying = isPlaying,
                enabled = enabled,
                accentColor = accentColor,
                inactiveColor = inactiveColor
            )

            SeekbarStyle.WAVE_LINE -> WaveLineSeekbar(
                progress = displayProgress,
                onSeekProgress = { frac ->
                    isDragging = true
                    dragProgress = frac
                },
                onSeekConfirmed = { frac ->
                    isDragging = false
                    onSeekTo((frac * durationMs).toLong())
                },
                isPlaying = isPlaying,
                enabled = enabled,
                accentColor = accentColor,
                inactiveColor = inactiveColor
            )

            SeekbarStyle.CLASSIC -> ClassicSeekbar(
                progress = displayProgress,
                onSeekProgress = { frac ->
                    isDragging = true
                    dragProgress = frac
                },
                onSeekConfirmed = { frac ->
                    isDragging = false
                    onSeekTo((frac * durationMs).toLong())
                },
                isPlaying = isPlaying,
                enabled = enabled,
                accentColor = accentColor,
                inactiveColor = inactiveColor
            )

            SeekbarStyle.DOTS -> DotsSeekbar(
                progress = displayProgress,
                onSeekProgress = { frac ->
                    isDragging = true
                    dragProgress = frac
                },
                onSeekConfirmed = { frac ->
                    isDragging = false
                    onSeekTo((frac * durationMs).toLong())
                },
                isPlaying = isPlaying,
                enabled = enabled,
                accentColor = accentColor,
                inactiveColor = inactiveColor
            )

            SeekbarStyle.GRADIENT -> GradientSeekbar(
                progress = displayProgress,
                onSeekProgress = { frac ->
                    isDragging = true
                    dragProgress = frac
                },
                onSeekConfirmed = { frac ->
                    isDragging = false
                    onSeekTo((frac * durationMs).toLong())
                },
                isPlaying = isPlaying,
                enabled = enabled,
                accentColor = accentColor,
                inactiveColor = inactiveColor
            )

            SeekbarStyle.NEON_GLOW -> NeonGlowSeekbar(
                progress = displayProgress,
                onSeekProgress = { frac ->
                    isDragging = true
                    dragProgress = frac
                },
                onSeekConfirmed = { frac ->
                    isDragging = false
                    onSeekTo((frac * durationMs).toLong())
                },
                isPlaying = isPlaying,
                enabled = enabled,
                accentColor = accentColor,
                inactiveColor = inactiveColor
            )

            SeekbarStyle.MATERIAL3 -> Material3Seekbar(
                progress = displayProgress,
                onSeekProgress = { frac ->
                    isDragging = true
                    dragProgress = frac
                },
                onSeekConfirmed = { frac ->
                    isDragging = false
                    onSeekTo((frac * durationMs).toLong())
                },
                isPlaying = isPlaying,
                enabled = enabled,
                accentColor = accentColor,
                inactiveColor = inactiveColor
            )

            SeekbarStyle.M3_EXPRESSIVE -> M3ExpressiveSeekbar(
                progress = displayProgress,
                onSeekProgress = { frac ->
                    isDragging = true
                    dragProgress = frac
                },
                onSeekConfirmed = { frac ->
                    isDragging = false
                    onSeekTo((frac * durationMs).toLong())
                },
                isPlaying = isPlaying,
                enabled = enabled,
                accentColor = accentColor,
                inactiveColor = inactiveColor
            )
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
                    color = SonoraTextMuted,
                    fontSize = 11.sp
                )
                Text(
                    text = formatDurationMs(durationMs),
                    color = SonoraTextMuted,
                    fontSize = 11.sp
                )
            }
        }
    }
}
