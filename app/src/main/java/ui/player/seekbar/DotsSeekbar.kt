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
import com.example.ui.theme.SonoraCharcoalBorder
import com.example.ui.theme.SonoraDustyRose

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
    accentColor: Color = SonoraDustyRose,
    inactiveColor: Color = SonoraCharcoalBorder
) {
    val totalDots = 28

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(30.dp)
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
            val centerY = size.height / 2f
            val step = if (totalDots > 1) width / (totalDots - 1) else width
            val activeIndex = (progress * (totalDots - 1)).toInt().coerceIn(0, totalDots - 1)

            for (i in 0 until totalDots) {
                val cx = i * step
                val isPlayed = i <= activeIndex
                val isCurrent = i == activeIndex

                if (isCurrent) {
                    // Current dot halo
                    drawCircle(
                        color = accentColor.copy(alpha = 0.35f),
                        radius = 7.dp.toPx(),
                        center = Offset(cx, centerY)
                    )
                    drawCircle(
                        color = Color.White,
                        radius = 4.5.dp.toPx(),
                        center = Offset(cx, centerY)
                    )
                } else {
                    drawCircle(
                        color = if (isPlayed) accentColor else inactiveColor,
                        radius = if (isPlayed) 3.2.dp.toPx() else 2.5.dp.toPx(),
                        center = Offset(cx, centerY)
                    )
                }
            }
        }
    }
}
