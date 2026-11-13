package ui.player.seekbar

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.OutfitFontFamily
import com.example.ui.theme.SonoraCharcoalBorder
import com.example.ui.theme.SonoraDustyRose
import ui.theme.LocalEditorialColors
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/**
 * Style 10: Circular Progress Seekbar
 * Radial arc progress gauge + linear companion scrub bar so horizontal space stays compact
 * and both radial drag and horizontal tap/drag seeking work effortlessly.
 */
@Composable
fun CircularProgressSeekbar(
    progress: Float,
    onSeekProgress: (Float) -> Unit,
    onSeekConfirmed: (Float) -> Unit,
    modifier: Modifier = Modifier,
    isPlaying: Boolean = false,
    enabled: Boolean = true,
    accentColor: Color = SonoraDustyRose,
    inactiveColor: Color = SonoraCharcoalBorder
) {
    val colors = LocalEditorialColors.current
    val clamped = progress.coerceIn(0f, 1f)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(52.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .pointerInput(enabled) {
                    if (!enabled) return@pointerInput
                    detectDragGestures(
                        onDragEnd = { onSeekConfirmed(clamped) },
                        onDragCancel = { onSeekConfirmed(clamped) },
                        onDrag = { change, _ ->
                            change.consume()
                            val cx = size.width / 2f
                            val cy = size.height / 2f
                            val angle = Math.toDegrees(atan2((change.position.y - cy).toDouble(), (change.position.x - cx).toDouble()))
                            val normalized = (((angle + 90.0 + 360.0) % 360.0) / 360.0).toFloat().coerceIn(0f, 1f)
                            onSeekProgress(normalized)
                        }
                    )
                },
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.matchParentSize()) {
                val stroke = 4.dp.toPx()
                val pad = stroke / 2f + 2.dp.toPx()
                val arcSize = Size(size.width - pad * 2, size.height - pad * 2)
                val topLeft = Offset(pad, pad)

                drawArc(
                    color = inactiveColor,
                    startAngle = -90f,
                    sweepAngle = 360f,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = stroke, cap = StrokeCap.Round)
                )
                drawArc(
                    color = accentColor,
                    startAngle = -90f,
                    sweepAngle = 360f * clamped,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = stroke, cap = StrokeCap.Round)
                )

                val angleRad = ((-90f + 360f * clamped) * (PI / 180.0))
                val r = arcSize.width / 2f
                val cx = size.width / 2f + r * cos(angleRad).toFloat()
                val cy = size.height / 2f + r * sin(angleRad).toFloat()
                drawCircle(color = accentColor, radius = 4.dp.toPx(), center = Offset(cx, cy))
            }
            Text(
                text = "${(clamped * 100).toInt()}%",
                fontFamily = OutfitFontFamily,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = colors.textPrimary
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        Box(
            modifier = Modifier
                .weight(1f)
                .height(32.dp)
                .pointerInput(enabled) {
                    if (!enabled) return@pointerInput
                    detectTapGestures(
                        onPress = { offset ->
                            val frac = (offset.x / size.width).coerceIn(0f, 1f)
                            onSeekProgress(frac)
                            onSeekConfirmed(frac)
                        }
                    )
                }
                .pointerInput(enabled) {
                    if (!enabled) return@pointerInput
                    detectDragGestures(
                        onDragStart = { offset ->
                            onSeekProgress((offset.x / size.width).coerceIn(0f, 1f))
                        },
                        onDragEnd = { onSeekConfirmed(clamped) },
                        onDragCancel = { onSeekConfirmed(clamped) },
                        onDrag = { change, _ ->
                            change.consume()
                            onSeekProgress((change.position.x / size.width).coerceIn(0f, 1f))
                        }
                    )
                }
        ) {
            Canvas(modifier = Modifier.matchParentSize()) {
                val trackY = size.height / 2f
                val stroke = 5.dp.toPx()
                drawLine(
                    color = inactiveColor,
                    start = Offset(0f, trackY),
                    end = Offset(size.width, trackY),
                    strokeWidth = stroke,
                    cap = StrokeCap.Round
                )
                drawLine(
                    color = accentColor,
                    start = Offset(0f, trackY),
                    end = Offset(size.width * clamped, trackY),
                    strokeWidth = stroke,
                    cap = StrokeCap.Round
                )
                drawCircle(
                    color = accentColor,
                    radius = if (isPlaying) 6.5.dp.toPx() else 5.5.dp.toPx(),
                    center = Offset(size.width * clamped, trackY)
                )
            }
        }
    }
}
