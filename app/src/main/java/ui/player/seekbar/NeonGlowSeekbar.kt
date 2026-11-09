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
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.example.ui.theme.SonoraCharcoalBorder
import com.example.ui.theme.SonoraDustyRose

/**
 * Style 6: Neon Glow Seekbar
 * Minimal horizontal track with soft luminous neon glow around the played section and thumb.
 */
@Composable
fun NeonGlowSeekbar(
    progress: Float,
    onSeekProgress: (Float) -> Unit,
    onSeekConfirmed: (Float) -> Unit,
    modifier: Modifier = Modifier,
    isPlaying: Boolean = false,
    enabled: Boolean = true,
    accentColor: Color = SonoraDustyRose,
    inactiveColor: Color = SonoraCharcoalBorder
) {
    val infiniteTransition = rememberInfiniteTransition(label = "neonPulse")
    val neonAlpha by infiniteTransition.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.70f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "neonAlpha"
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(32.dp)
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
            val activeX = width * progress

            // Inactive track
            drawLine(
                color = inactiveColor,
                start = Offset(0f, centerY),
                end = Offset(width, centerY),
                strokeWidth = 3.dp.toPx(),
                cap = StrokeCap.Round
            )

            if (activeX > 0f) {
                // Neon glow outer aura
                drawLine(
                    color = accentColor.copy(alpha = if (isPlaying) neonAlpha * 0.4f else 0.25f),
                    start = Offset(0f, centerY),
                    end = Offset(activeX, centerY),
                    strokeWidth = 10.dp.toPx(),
                    cap = StrokeCap.Round
                )
                // Neon glow mid aura
                drawLine(
                    color = accentColor.copy(alpha = if (isPlaying) neonAlpha * 0.7f else 0.5f),
                    start = Offset(0f, centerY),
                    end = Offset(activeX, centerY),
                    strokeWidth = 6.dp.toPx(),
                    cap = StrokeCap.Round
                )
                // Active crisp core line
                drawLine(
                    color = Color.White,
                    start = Offset(0f, centerY),
                    end = Offset(activeX, centerY),
                    strokeWidth = 2.5.dp.toPx(),
                    cap = StrokeCap.Round
                )
            }

            // Glowing neon thumb
            drawCircle(
                color = accentColor.copy(alpha = if (isPlaying) neonAlpha else 0.4f),
                radius = 10.dp.toPx(),
                center = Offset(activeX, centerY)
            )
            drawCircle(
                color = accentColor,
                radius = 6.dp.toPx(),
                center = Offset(activeX, centerY)
            )
            drawCircle(
                color = Color.White,
                radius = 3.dp.toPx(),
                center = Offset(activeX, centerY)
            )
        }
    }
}
