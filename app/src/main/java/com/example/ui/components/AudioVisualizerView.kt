package com.example.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.example.model.VisualizerStyle
import com.example.ui.theme.SonoraCyan
import com.example.ui.theme.SonoraGold
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun AudioVisualizerView(
    frequencies: FloatArray,
    waveform: FloatArray,
    style: VisualizerStyle,
    modifier: Modifier = Modifier,
    primaryColor: Color = SonoraGold,
    secondaryColor: Color = SonoraCyan
) {
    val rotationAnim = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        rotationAnim.animateTo(
            targetValue = 360f,
            animationSpec = infiniteRepeatable(
                animation = tween(20000, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            )
        )
    }

    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height

        when (style) {
            VisualizerStyle.SPECTRUM_BARS -> {
                val numBars = frequencies.size.coerceAtMost(32)
                val barSpacing = 4.dp.toPx()
                val totalSpacing = (numBars - 1) * barSpacing
                val barWidth = ((w - totalSpacing) / numBars).coerceAtLeast(2.dp.toPx())

                for (i in 0 until numBars) {
                    val mag = frequencies[i].coerceIn(0.04f, 1f)
                    val barHeight = h * mag * 0.95f
                    val x = i * (barWidth + barSpacing)
                    val y = h - barHeight

                    val brush = Brush.verticalGradient(
                        colors = listOf(secondaryColor, primaryColor),
                        startY = y,
                        endY = h
                    )

                    drawRoundRect(
                        brush = brush,
                        topLeft = Offset(x, y),
                        size = Size(barWidth, barHeight),
                        cornerRadius = CornerRadius(barWidth / 2, barWidth / 2)
                    )

                    // Peak cap glow dot
                    drawCircle(
                        color = secondaryColor,
                        radius = (barWidth / 2).coerceAtMost(3.dp.toPx()),
                        center = Offset(x + barWidth / 2, (y - 3.dp.toPx()).coerceAtLeast(0f))
                    )
                }
            }

            VisualizerStyle.WAVEFORM -> {
                val path = Path()
                val numSamples = waveform.size
                if (numSamples > 1) {
                    val stepX = w / (numSamples - 1)
                    val centerY = h / 2

                    for (i in 0 until numSamples) {
                        val s = waveform[i].coerceIn(-1f, 1f)
                        val x = i * stepX
                        val y = centerY + (s * (h / 2 * 0.85f))
                        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                    }

                    drawPath(
                        path = path,
                        brush = Brush.horizontalGradient(listOf(primaryColor, secondaryColor, primaryColor)),
                        style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
                    )
                }
            }

            VisualizerStyle.CIRCULAR_SPECTRUM -> {
                val center = Offset(w / 2, h / 2)
                val baseRadius = (minOf(w, h) / 4).coerceAtLeast(10f)
                val numBars = frequencies.size.coerceAtMost(32)
                val angleStep = (2 * Math.PI / numBars).toFloat()
                val rotRad = Math.toRadians(rotationAnim.value.toDouble()).toFloat()

                for (i in 0 until numBars) {
                    val angle = i * angleStep + rotRad
                    val mag = frequencies[i].coerceIn(0.05f, 1f)
                    val barLength = (baseRadius * 0.9f * mag)

                    val startX = center.x + baseRadius * cos(angle)
                    val startY = center.y + baseRadius * sin(angle)
                    val endX = center.x + (baseRadius + barLength) * cos(angle)
                    val endY = center.y + (baseRadius + barLength) * sin(angle)

                    drawLine(
                        brush = Brush.radialGradient(
                            listOf(primaryColor, secondaryColor),
                            center = center,
                            radius = baseRadius + barLength
                        ),
                        start = Offset(startX, startY),
                        end = Offset(endX, endY),
                        strokeWidth = 3.dp.toPx(),
                        cap = StrokeCap.Round
                    )
                }

                // Inner core ring
                drawCircle(
                    color = primaryColor.copy(alpha = 0.3f),
                    radius = baseRadius,
                    center = center,
                    style = Stroke(width = 2.dp.toPx())
                )
            }

            VisualizerStyle.AUDIO_RADAR -> {
                val center = Offset(w / 2, h / 2)
                val maxRadius = minOf(w, h) / 2
                val avgMag = frequencies.take(8).average().toFloat().coerceIn(0.05f, 1f)

                for (ring in 1..4) {
                    val ringRadius = (maxRadius / 4 * ring) * (0.8f + avgMag * 0.2f)
                    drawCircle(
                        color = if (ring % 2 == 0) primaryColor.copy(alpha = 0.25f * avgMag) else secondaryColor.copy(alpha = 0.35f * avgMag),
                        radius = ringRadius,
                        center = center,
                        style = Stroke(width = 2.dp.toPx())
                    )
                }

                // Radar sweep beam
                val sweepAngle = Math.toRadians(rotationAnim.value.toDouble()).toFloat()
                val beamEnd = Offset(
                    center.x + maxRadius * cos(sweepAngle),
                    center.y + maxRadius * sin(sweepAngle)
                )
                drawLine(
                    color = secondaryColor.copy(alpha = 0.7f),
                    start = center,
                    end = beamEnd,
                    strokeWidth = 2.dp.toPx(),
                    cap = StrokeCap.Round
                )
            }

            VisualizerStyle.NEBULA_PARTICLES -> {
                val center = Offset(w / 2, h / 2)
                val numParticles = 24
                for (i in 0 until numParticles) {
                    val fIndex = (i % frequencies.size)
                    val mag = frequencies[fIndex].coerceIn(0.1f, 1f)
                    val angle = (i * (2 * Math.PI / numParticles) + (rotationAnim.value * 0.02f)).toFloat()
                    val dist = (minOf(w, h) * 0.38f) * mag

                    val px = center.x + dist * cos(angle)
                    val py = center.y + dist * sin(angle)

                    val particleColor = if (i % 2 == 0) primaryColor else secondaryColor
                    drawCircle(
                        color = particleColor.copy(alpha = (mag * 0.8f).coerceIn(0.2f, 1f)),
                        radius = (3.dp.toPx() * (1f + mag * 1.5f)),
                        center = Offset(px, py)
                    )
                }
            }
        }
    }
}
