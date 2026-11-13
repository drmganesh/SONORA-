package ui.player.seekbar

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.sin
import ui.theme.LocalEditorialColors
import ui.theme.LocalReduceAnimations

/**
 * Style 9: M3 Expressive Seekbar
 * Modern expressive Material 3-inspired squiggly seekbar with dynamic wavy motion
 * and an expressive capsule thumb.
 */
@Composable
fun M3ExpressiveSeekbar(
    progress: Float,
    onSeekProgress: (Float) -> Unit,
    onSeekConfirmed: (Float) -> Unit,
    modifier: Modifier = Modifier,
    isPlaying: Boolean = false,
    enabled: Boolean = true,
    accentColor: Color = LocalEditorialColors.current.accentStrong,
    inactiveColor: Color = LocalEditorialColors.current.cardBorder
) {
    val reduceAnimations = LocalReduceAnimations.current
    val infiniteTransition = rememberInfiniteTransition(label = "expressiveSquiggle")
    val animatedPhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = if (isPlaying && !reduceAnimations) (2 * PI).toFloat() else 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(2400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "phase"
    )
    val phase = if (reduceAnimations) 0f else animatedPhase

    val waveAmplitude by animateFloatAsState(
        targetValue = if (isPlaying && !reduceAnimations) 4f else 0.5f,
        label = "amplitude"
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(34.dp)
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
            val centerY = height / 2f
            val activeX = width * progress
            val wavelength = 28.dp.toPx()
            val amp = waveAmplitude.dp.toPx()

            if (activeX < width) {
                drawLine(
                    color = inactiveColor,
                    start = Offset(activeX, centerY),
                    end = Offset(width, centerY),
                    strokeWidth = 4.dp.toPx(),
                    cap = StrokeCap.Round
                )
            }

            if (activeX > 0f) {
                val path = Path()
                path.moveTo(0f, centerY)
                var x = 0f
                val step = 2f

                while (x <= activeX) {
                    val y = centerY + amp * sin(((x / wavelength) * 2 * PI.toFloat()) - phase)
                    path.lineTo(x, y)
                    x += step
                }

                drawPath(
                    path = path,
                    color = accentColor,
                    style = Stroke(width = 4.5.dp.toPx(), cap = StrokeCap.Round)
                )
            }

            val thumbWidth = 5.dp.toPx()
            val thumbHeight = 16.dp.toPx()
            val thumbLeft = (activeX - (thumbWidth / 2f)).coerceIn(0f, width - thumbWidth)
            val thumbTop = centerY - (thumbHeight / 2f)

            drawRoundRect(
                color = accentColor,
                topLeft = Offset(thumbLeft, thumbTop),
                size = Size(thumbWidth, thumbHeight),
                cornerRadius = CornerRadius(2.5.dp.toPx(), 2.5.dp.toPx())
            )
        }
    }
}
