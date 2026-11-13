package com.example.ui.equalizer

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.PlusJakartaFontFamily
import ui.theme.LocalEditorialColors

/**
 * Frequency Response Graph — SONORA AESTHETIC UI.
 * Renders the combined live 15-band biquad filter magnitude response from 20 Hz to 20 kHz.
 */
@Composable
fun FrequencyResponseGraph(
    responseDb: FloatArray,
    isEnabled: Boolean,
    modifier: Modifier = Modifier
) {
    val colors = LocalEditorialColors.current
    val strokeColor = if (isEnabled) colors.accentStrong else colors.textMuted.copy(alpha = 0.5f)
    val glowColor = if (isEnabled) colors.accentStrong else colors.textMuted
    val gridColor = colors.textMuted

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(colors.surface)
            .border(0.75.dp, colors.cardBorder, RoundedCornerShape(22.dp))
            .padding(14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "FREQUENCY RESPONSE",
                fontFamily = PlusJakartaFontFamily,
                color = colors.textMuted,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )
            Spacer(modifier = Modifier.weight(1f))
            Text(
                text = "20 Hz – 20 kHz • ±12 dB",
                color = colors.textSecondary,
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(98.dp)
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val widthPx = size.width
                val heightPx = size.height

                val minDb = -12f
                val maxDb = 12f
                val dbSpan = maxDb - minDb

                fun dbToY(db: Float): Float {
                    val clamped = db.coerceIn(minDb, maxDb)
                    val normalized = (maxDb - clamped) / dbSpan
                    return normalized * heightPx
                }

                val y12 = dbToY(12f)
                val y0 = dbToY(0f)
                val yMinus12 = dbToY(-12f)
                val dashedEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f), 0f)

                drawLine(
                    color = gridColor.copy(alpha = 0.35f),
                    start = Offset(0f, y0),
                    end = Offset(widthPx, y0),
                    strokeWidth = 1.dp.toPx()
                )
                drawLine(
                    color = gridColor.copy(alpha = 0.16f),
                    start = Offset(0f, y12),
                    end = Offset(widthPx, y12),
                    strokeWidth = 1.dp.toPx(),
                    pathEffect = dashedEffect
                )
                drawLine(
                    color = gridColor.copy(alpha = 0.16f),
                    start = Offset(0f, yMinus12),
                    end = Offset(widthPx, yMinus12),
                    strokeWidth = 1.dp.toPx(),
                    pathEffect = dashedEffect
                )

                if (responseDb.isEmpty()) return@Canvas

                val curvePath = Path()
                val fillPath = Path()

                val numPoints = responseDb.size
                val stepX = widthPx / (numPoints - 1).coerceAtLeast(1)

                var prevX = 0f
                var prevY = dbToY(if (isEnabled) responseDb[0] else 0f)

                curvePath.moveTo(prevX, prevY)
                fillPath.moveTo(prevX, y0)
                fillPath.lineTo(prevX, prevY)

                for (i in 1 until numPoints) {
                    val currentX = i * stepX
                    val currentY = dbToY(if (isEnabled) responseDb[i] else 0f)
                    val midX = (prevX + currentX) / 2f
                    curvePath.cubicTo(midX, prevY, midX, currentY, currentX, currentY)
                    fillPath.cubicTo(midX, prevY, midX, currentY, currentX, currentY)
                    prevX = currentX
                    prevY = currentY
                }

                fillPath.lineTo(prevX, y0)
                fillPath.close()

                if (isEnabled) {
                    drawPath(
                        path = fillPath,
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                glowColor.copy(alpha = 0.25f),
                                strokeColor.copy(alpha = 0.10f),
                                Color.Transparent
                            ),
                            startY = y12,
                            endY = yMinus12
                        )
                    )
                }

                drawPath(
                    path = curvePath,
                    color = strokeColor,
                    style = Stroke(width = 2.6.dp.toPx())
                )
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val freqLabels = listOf("32 Hz", "125 Hz", "500 Hz", "2 kHz", "8 kHz", "16 kHz")
            freqLabels.forEachIndexed { index, label ->
                if (index > 0) Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = label,
                    color = colors.textMuted,
                    fontSize = 9.sp,
                    fontFamily = FontFamily.Monospace
                )
            }
        }
    }
}
