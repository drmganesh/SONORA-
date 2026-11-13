package ui.player.seekbar

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import ui.theme.LocalEditorialColors

/**
 * Style 7: Blocks Seekbar (DEFAULT)
 * Segmented rectangular progress bar with distinct blocks, crisp gaps, and rounded edges.
 */
@Composable
fun BlocksSeekbar(
    progress: Float,
    onSeekProgress: (Float) -> Unit,
    onSeekConfirmed: (Float) -> Unit,
    modifier: Modifier = Modifier,
    isPlaying: Boolean = false,
    enabled: Boolean = true,
    accentColor: Color = LocalEditorialColors.current.accentStrong,
    inactiveColor: Color = LocalEditorialColors.current.cardBorder
) {
    val colors = LocalEditorialColors.current
    val totalBlocks = 24

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(28.dp)
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
                    onDragEnd = { onSeekConfirmed(progress) },
                    onDragCancel = { onSeekConfirmed(progress) },
                    onDrag = { change, _ ->
                        change.consume()
                        val newProgress = (change.position.x / size.width).coerceIn(0f, 1f)
                        onSeekProgress(newProgress)
                    }
                )
            }
    ) {
        Canvas(modifier = Modifier.matchParentSize()) {
            val width = size.width
            val height = size.height
            val gap = 3.dp.toPx()
            val totalGaps = gap * (totalBlocks - 1)
            val blockWidth = ((width - totalGaps) / totalBlocks).coerceAtLeast(1f)
            val baseHeight = 8.dp.toPx()
            val activeIndex = (progress * totalBlocks).toInt().coerceIn(0, totalBlocks)

            for (i in 0 until totalBlocks) {
                val x = i * (blockWidth + gap)
                val isPlayed = i < activeIndex
                val isCurrent = i == (activeIndex - 1).coerceAtLeast(0) && progress > 0f

                val blockH = if (isCurrent) 11.dp.toPx() else baseHeight
                val topY = (height - blockH) / 2f

                val color = when {
                    isCurrent -> accentColor
                    isPlayed -> accentColor.copy(alpha = 0.72f)
                    else -> inactiveColor.takeIf { it.alpha > 0.3f } ?: colors.cardBorder
                }

                drawRoundRect(
                    color = color,
                    topLeft = Offset(x, topY),
                    size = Size(blockWidth, blockH),
                    cornerRadius = CornerRadius(2.5.dp.toPx(), 2.5.dp.toPx())
                )
            }
        }
    }
}
