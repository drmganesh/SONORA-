package ui.splash

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

private val WaveGoldBright = Color(0xFFFDDD6E)
private val WaveGoldAmber = Color(0xFFE5A424)
private val WaveWarmIvory = Color(0xFFFFF5D6)
private const val TWO_PI_F = (2.0 * PI).toFloat()

/**
 * Smooth, playback-inspired horizontal audio waveform animation beneath the official SONORA logo.
 *
 * Uses [drawWithCache] to allocate a single shared vertical gradient shader and precomputed bar
 * silhouettes once per layout size, reading animated progress exclusively during the draw phase
 * for zero per-frame recompositions and zero per-frame heap allocations.
 */
@Composable
fun SonoraWaveAnimation(
    visibilityAlpha: Float,
    amplitudeProgress: Float,
    travelProgress: Float,
    barCount: Int = 23,
    modifier: Modifier = Modifier
) {
    SonoraWaveAnimation(
        visibilityAlphaProvider = { visibilityAlpha },
        amplitudeProgressProvider = { amplitudeProgress },
        travelProgressProvider = { travelProgress },
        barCount = barCount,
        modifier = modifier
    )
}

@Composable
fun SonoraWaveAnimation(
    visibilityAlphaProvider: () -> Float,
    amplitudeProgressProvider: () -> Float,
    travelProgressProvider: () -> Float,
    barCount: Int = 23,
    modifier: Modifier = Modifier
) {
    val safeBarCount = barCount.coerceAtLeast(5)

    Spacer(
        modifier = modifier
            .widthIn(max = 220.dp)
            .fillMaxWidth(0.54f)
            .height(28.dp)
            .drawWithCache {
                val barWidthPx = 3.dp.toPx().coerceAtLeast(2f)
                val cornerRadius = CornerRadius(barWidthPx * 0.5f, barWidthPx * 0.5f)
                val totalSpan = size.width
                val step = if (safeBarCount > 1) (totalSpan - barWidthPx) / (safeBarCount - 1) else totalSpan
                val centerY = size.height * 0.5f
                val maxHalfHeight = (size.height * 0.44f).coerceAtLeast(3f)
                val minHalfHeight = 1.75.dp.toPx()
                val heightRange = maxHalfHeight - minHalfHeight

                // Precompute static per-bar geometry & base silhouette once per canvas resize
                val normalizedXs = FloatArray(safeBarCount)
                val baseSilhouettes = FloatArray(safeBarCount)
                val barLefts = FloatArray(safeBarCount)
                val denom = (safeBarCount - 1).coerceAtLeast(1).toFloat()
                for (i in 0 until safeBarCount) {
                    val nx = i.toFloat() / denom
                    normalizedXs[i] = nx
                    val distFromCenter = abs(nx - 0.5f) * 2f
                    baseSilhouettes[i] = (1f - distFromCenter * 0.55f).coerceIn(0.28f, 1f)
                    barLefts[i] = i * step
                }

                // Single shared gradient brush reused across all bars and frames (0 allocations/frame)
                val sharedWaveBrush = Brush.verticalGradient(
                    colors = listOf(
                        WaveWarmIvory,
                        WaveGoldBright,
                        WaveGoldAmber
                    ),
                    startY = centerY - maxHalfHeight,
                    endY = centerY + maxHalfHeight
                )

                onDrawBehind {
                    val visibilityAlpha = visibilityAlphaProvider()
                    if (visibilityAlpha <= 0.01f) return@onDrawBehind

                    val amplitudeProgress = amplitudeProgressProvider().coerceIn(0f, 1f)
                    val travelProgress = travelProgressProvider()
                    val sweepCenter = -0.15f + (travelProgress * 1.3f)
                    val phaseShift = travelProgress * (TWO_PI_F * 1.6f)

                    for (index in 0 until safeBarCount) {
                        val normalizedX = normalizedXs[index]
                        val waveDistance = abs(normalizedX - sweepCenter)
                        val travelBoost = (1f - (waveDistance / 0.34f)).coerceIn(0f, 1f)
                        val harmonic = 0.5f + 0.5f * sin((normalizedX * 4.2f * PI.toFloat()) - phaseShift)

                        val modulated = (0.22f + 0.48f * baseSilhouettes[index] + 0.30f * travelBoost) *
                            (0.55f + 0.45f * harmonic) *
                            amplitudeProgress

                        val halfH = minHalfHeight + heightRange * modulated
                        val barAlpha = (visibilityAlpha * (0.45f + 0.55f * (0.4f + 0.6f * travelBoost)))
                            .coerceIn(0f, 1f)

                        drawRoundRect(
                            brush = sharedWaveBrush,
                            topLeft = Offset(barLefts[index], centerY - halfH),
                            size = Size(barWidthPx, halfH * 2f),
                            cornerRadius = cornerRadius,
                            alpha = barAlpha
                        )
                    }
                }
            }
            .testTag("sonora_splash_waveform")
    )
}
