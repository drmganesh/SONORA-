package ui.player.seekbar

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.example.ui.theme.SonoraCharcoalBorder
import com.example.ui.theme.SonoraDustyRose
import kotlin.math.sin

/**
 * Style 1: Waveform Seekbar
 * Displays multi-height realistic audio waveform bars.
 * Played waveform is filled with accent color, remaining is muted.
 * Supports tap and drag to seek.
 */
@Composable
fun WaveformSeekbar(
    progress: Float,
    onSeekProgress: (Float) -> Unit,
    onSeekConfirmed: (Float) -> Unit,
    modifier: Modifier = Modifier,
    isPlaying: Boolean = false,
    enabled: Boolean = true,
    accentColor: Color = SonoraDustyRose,
    inactiveColor: Color = SonoraCharcoalBorder
) {
    // 36 bars with realistic normalized audio waveform profile
    val waveformProfile = remember {
        floatArrayOf(
            0.25f, 0.40f, 0.65f, 0.35f, 0.85f, 0.95f, 0.50f, 0.70f,
            0.45f, 0.80f, 1.00f, 0.60f, 0.30f, 0.55f, 0.90f, 0.75f,
            0.40f, 0.85f, 0.65f, 0.50f, 0.95f, 0.80f, 0.35f, 0.60f,
            0.75f, 0.90f, 0.45f, 0.70f, 0.85f, 0.55f, 0.40f, 0.65f,
            0.50f, 0.75f, 0.40f, 0.30f
        )
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(36.dp)
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                detectTapGestures(
                    onPress = { offset ->
                        val newProgress = (offset.x / size.width).coerceIn(0f, 1f)
                        onSeekProgress(newProgress)
                        onSeekConfirmed(newProgress)
                    }
                )
            }
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                detectDragGestures(
                    onDragStart = { offset ->
                        val newProgress = (offset.x / size.width).coerceIn(0f, 1f)
                        onSeekProgress(newProgress)
                    },
                    onDragEnd = {
                        onSeekConfirmed(progress)
                    },
                    onDragCancel = {
                        onSeekConfirmed(progress)
                    },
                    onDrag = { change, _ ->
                        change.consume()
                        val newProgress = (change.position.x / size.width).coerceIn(0f, 1f)
                        onSeekProgress(newProgress)
                    }
                )
            }
    ) {
        Canvas(modifier = Modifier.matchParentSize()) {
            val totalBars = waveformProfile.size
            val barGap = 2.5.dp.toPx()
            val availableWidth = size.width - (barGap * (totalBars - 1))
            val barWidth = (availableWidth / totalBars).coerceAtLeast(1f)
            val centerY = size.height / 2f
            val maxBarHeight = size.height * 0.9f

            for (i in 0 until totalBars) {
                val x = i * (barWidth + barGap)
                val barProgressFraction = (i.toFloat() + 0.5f) / totalBars
                val isPlayed = barProgressFraction <= progress

                val normalizedHeight = waveformProfile[i]
                val barHeight = (maxBarHeight * normalizedHeight).coerceAtLeast(4.dp.toPx())
                val topY = centerY - (barHeight / 2f)

                drawRoundRect(
                    color = if (isPlayed) accentColor else inactiveColor,
                    topLeft = Offset(x, topY),
                    size = Size(barWidth, barHeight),
                    cornerRadius = CornerRadius(barWidth / 2f, barWidth / 2f)
                )
            }
        }
    }
}
