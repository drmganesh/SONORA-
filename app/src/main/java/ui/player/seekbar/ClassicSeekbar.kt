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
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import ui.theme.LocalEditorialColors

/**
 * Style 3: Classic Seekbar
 * Traditional horizontal progress bar with rounded caps and circular thumb.
 */
@Composable
fun ClassicSeekbar(
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
            val trackHeight = 4.dp.toPx()
            val thumbRadius = 7.dp.toPx()
            val activeX = (width * progress).coerceIn(thumbRadius, (width - thumbRadius).coerceAtLeast(thumbRadius))

            drawLine(
                color = inactiveColor,
                start = Offset(0f, centerY),
                end = Offset(width, centerY),
                strokeWidth = trackHeight,
                cap = StrokeCap.Round
            )

            if (progress > 0f) {
                drawLine(
                    color = accentColor,
                    start = Offset(0f, centerY),
                    end = Offset(activeX, centerY),
                    strokeWidth = trackHeight,
                    cap = StrokeCap.Round
                )
            }

            drawCircle(
                color = accentColor,
                radius = thumbRadius,
                center = Offset(activeX, centerY)
            )
            drawCircle(
                color = colors.surface,
                radius = thumbRadius * 0.45f,
                center = Offset(activeX, centerY)
            )
        }
    }
}
