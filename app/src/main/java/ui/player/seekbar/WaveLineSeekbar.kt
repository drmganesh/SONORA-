package ui.player.seekbar

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.example.ui.theme.SonoraCharcoalBorder
import com.example.ui.theme.SonoraDustyRose
import kotlin.math.PI
import kotlin.math.sin

/**
 * Style 2: Wave Line Seekbar
 * Smooth sinusoidal wave that undulates during playback.
 * Played segment uses primary accent with glowing thumb; remaining uses muted color.
 */
@Composable
fun WaveLineSeekbar(
    progress: Float,
    onSeekProgress: (Float) -> Unit,
    onSeekConfirmed: (Float) -> Unit,
    modifier: Modifier = Modifier,
    isPlaying: Boolean = false,
    enabled: Boolean = true,
    accentColor: Color = SonoraDustyRose,
    inactiveColor: Color = SonoraCharcoalBorder
) {
    val infiniteTransition = rememberInfiniteTransition(label = "waveLineTransition")
    val wavePhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = if (isPlaying) (2 * PI).toFloat() else 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 3500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "wavePhase"
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
            val midY = height / 2f
            val amplitude = 5.dp.toPx()
            val wavelength = 40.dp.toPx()
            val splitX = width * progress

            val playedPath = Path()
            val remainingPath = Path()

            val step = 3f
            var x = 0f

            var isFirstPlayed = true
            var isFirstRemaining = true

            while (x <= width) {
                val y = midY + amplitude * sin(((x / wavelength) * 2 * PI.toFloat()) + wavePhase)
                if (x <= splitX) {
                    if (isFirstPlayed) {
                        playedPath.moveTo(x, y)
                        isFirstPlayed = false
                    } else {
                        playedPath.lineTo(x, y)
                    }
                } else {
                    if (isFirstRemaining) {
                        remainingPath.moveTo(x, y)
                        isFirstRemaining = false
                    } else {
                        remainingPath.lineTo(x, y)
                    }
                }
                x += step
            }

            // Draw remaining unplayed wave
            drawPath(
                path = remainingPath,
                color = inactiveColor,
                style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
            )

            // Draw played active wave
            drawPath(
                path = playedPath,
                color = accentColor,
                style = Stroke(width = 3.5.dp.toPx(), cap = StrokeCap.Round)
            )

            // Draw luminous wave crest thumb at current progress point
            val thumbY = midY + amplitude * sin(((splitX / wavelength) * 2 * PI.toFloat()) + wavePhase)
            drawCircle(
                color = accentColor.copy(alpha = 0.35f),
                radius = 8.dp.toPx(),
                center = Offset(splitX, thumbY)
            )
            drawCircle(
                color = accentColor,
                radius = 4.5.dp.toPx(),
                center = Offset(splitX, thumbY)
            )
        }
    }
}
