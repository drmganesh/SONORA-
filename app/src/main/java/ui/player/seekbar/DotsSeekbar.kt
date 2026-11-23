package ui.player.seekbar

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import ui.theme.LocalEditorialColors

/**
 * Style 4: Dots Seekbar
 * Sequence of circular progress dots with active tracking and current-node glow.
 */
@Composable
fun DotsSeekbar(
    progress: Float,
    onSeekProgress: (Float) -> Unit,
    onSeekConfirmed: (Float) -> Unit,
    modifier: Modifier = Modifier,
    isPlaying: Boolean = false,
    enabled: Boolean = true,
    accentColor: Color = LocalEditorialColors.current.accentStrong,
    inactiveColor: Color = LocalEditorialColors.current.cardBorder,
    onSeekCancelled: () -> Unit = {}
) {
    val colors = LocalEditorialColors.current
    val totalDots = 28

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(36.dp)
            .scrubSeekGestures(
                enabled = enabled,
                progress = progress,
                onScrubStart = onSeekProgress,
                onScrubUpdate = onSeekProgress,
                onScrubEnd = onSeekConfirmed,
                onScrubCancel = onSeekCancelled
            )
    ) {
        Canvas(modifier = Modifier.matchParentSize()) {
            val width = size.width
            val centerY = size.height / 2f
            val pad = 6.dp.toPx()
            val usableW = (width - pad * 2).coerceAtLeast(1f)
            val step = if (totalDots > 1) usableW / (totalDots - 1) else usableW
            val activeIndex = (progress * (totalDots - 1)).toInt().coerceIn(0, totalDots - 1)

            for (i in 0 until totalDots) {
                val cx = pad + i * step
                val isPlayed = i <= activeIndex
                val isCurrent = i == activeIndex

                if (isCurrent) {
                    drawCircle(
                        color = accentColor.copy(alpha = 0.28f),
                        radius = 7.5.dp.toPx(),
                        center = Offset(cx, centerY)
                    )
                    drawCircle(
                        color = accentColor,
                        radius = 4.8.dp.toPx(),
                        center = Offset(cx, centerY)
                    )
                    drawCircle(
                        color = colors.surface,
                        radius = 2.dp.toPx(),
                        center = Offset(cx, centerY)
                    )
                } else {
                    drawCircle(
                        color = if (isPlayed) accentColor else inactiveColor,
                        radius = if (isPlayed) 3.2.dp.toPx() else 2.4.dp.toPx(),
                        center = Offset(cx, centerY)
                    )
                }
            }
        }
    }
}
