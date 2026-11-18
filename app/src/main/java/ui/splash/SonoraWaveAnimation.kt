package ui.splash

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

private val WaveGoldBright = Color(0xFFFDDD6E)
private val WaveGoldAmber = Color(0xFFE5A424)
private val WaveWarmIvory = Color(0xFFFFF5D6)

/**
 * Smooth, playback-inspired horizontal audio waveform animation beneath the official SONORA logo.
 * Produces a gentle left-to-right harmonic traveling wave without rapid strobing.
 */
@Composable
fun SonoraWaveAnimation(
    visibilityAlpha: Float,
    amplitudeProgress: Float,
    travelProgress: Float,
    modifier: Modifier = Modifier
) {
    if (visibilityAlpha <= 0.01f) return

    Canvas(
        modifier = modifier
            .widthIn(max = 220.dp)
            .fillMaxWidth(0.54f)
            .height(28.dp)
            .testTag("sonora_splash_waveform")
    ) {
        val barCount = 23
        val barWidthPx = 3.dp.toPx().coerceAtLeast(2f)
        val totalSpan = size.width
        val step = if (barCount > 1) (totalSpan - barWidthPx) / (barCount - 1) else totalSpan
        val centerY = size.height / 2f
        val maxHalfHeight = (size.height * 0.44f).coerceAtLeast(3f)
        val minHalfHeight = 1.75.dp.toPx()

        val sweepCenter = -0.15f + (travelProgress * 1.3f)
        val phaseShift = travelProgress * (2f * PI.toFloat() * 1.6f)

        for (index in 0 until barCount) {
            val normalizedX = index.toFloat() / (barCount - 1).toFloat()
            val distanceFromCenter = abs(normalizedX - 0.5f) * 2f
            val baseSilhouette = (1f - distanceFromCenter * 0.55f).coerceIn(0.28f, 1f)

            // Smooth left-to-right traveling wave pulse combined with subtle harmonic ripple
            val waveDistance = abs(normalizedX - sweepCenter)
            val travelBoost = (1f - (waveDistance / 0.34f)).coerceIn(0f, 1f)
            val harmonic = 0.5f + 0.5f * sin((normalizedX * 4.2f * PI.toFloat()) - phaseShift)

            val modulated = (0.22f + 0.48f * baseSilhouette + 0.30f * travelBoost) *
                (0.55f + 0.45f * harmonic) *
                amplitudeProgress.coerceIn(0f, 1f)

            val halfH = minHalfHeight + (maxHalfHeight - minHalfHeight) * modulated
            val left = index * step
            val top = centerY - halfH

            val barAlpha = (visibilityAlpha * (0.45f + 0.55f * (0.4f + 0.6f * travelBoost)))
                .coerceIn(0f, 1f)

            val brush = Brush.verticalGradient(
                colors = listOf(
                    WaveWarmIvory.copy(alpha = barAlpha),
                    WaveGoldBright.copy(alpha = barAlpha * 0.92f),
                    WaveGoldAmber.copy(alpha = barAlpha * 0.78f)
                ),
                startY = top,
                endY = top + halfH * 2f
            )

            drawRoundRect(
                brush = brush,
                topLeft = Offset(left, top),
                size = Size(barWidthPx, halfH * 2f),
                cornerRadius = CornerRadius(barWidthPx / 2f, barWidthPx / 2f)
            )
        }
    }
}
