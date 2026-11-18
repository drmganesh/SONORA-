package ui.splash

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.R

private val SplashGoldBright = Color(0xFFFDDD6E)
private val SplashGoldWarm = Color(0xFFE5A424)
private val SplashIvoryHighlight = Color(0xFFFFF8E6)

/**
 * Official SONORA Logo animation showcasing the untouched supplied logo asset with:
 * - Warm ambient golden radial glow behind the emblem
 * - Subtle expanding circular resonance wave ring (playback-inspired aura)
 * - Smooth scale-and-fade reveal
 * - Soft diagonal golden light sweep across the emblem surface
 */
@Composable
fun SonoraLogoAnimation(
    logoScale: Float,
    logoAlpha: Float,
    ambientGlowAlpha: Float,
    ringExpansionProgress: Float,
    lightSweepProgress: Float,
    reducedMotion: Boolean,
    logoSize: Dp = 132.dp,
    modifier: Modifier = Modifier
) {
    val logoShape = RoundedCornerShape(30.dp)
    val outerStageSize = logoSize * 1.72f

    Box(
        modifier = modifier
            .size(outerStageSize)
            .testTag("sonora_splash_logo"),
        contentAlignment = Alignment.Center
    ) {
        // Ambient golden aura + expanding circular sound-wave resonance ring
        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val baseLogoRadius = logoSize.toPx() * 0.44f

            if (ambientGlowAlpha > 0.01f) {
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            SplashGoldBright.copy(alpha = (0.34f * ambientGlowAlpha).coerceIn(0f, 1f)),
                            SplashGoldWarm.copy(alpha = (0.14f * ambientGlowAlpha).coerceIn(0f, 1f)),
                            Color.Transparent
                        ),
                        center = center,
                        radius = size.minDimension * 0.50f
                    ),
                    radius = size.minDimension * 0.50f,
                    center = center
                )
            }

            if (!reducedMotion && ringExpansionProgress in 0.01f..0.99f) {
                val ringRadius = baseLogoRadius + (size.minDimension * 0.25f * ringExpansionProgress)
                val ringAlpha = ((1f - ringExpansionProgress) * 0.48f).coerceIn(0f, 0.48f)
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            SplashIvoryHighlight.copy(alpha = ringAlpha),
                            SplashGoldBright.copy(alpha = ringAlpha * 0.7f)
                        ),
                        center = center,
                        radius = ringRadius
                    ),
                    radius = ringRadius,
                    center = center,
                    style = Stroke(width = (2.2.dp.toPx() * (1f - 0.55f * ringExpansionProgress)).coerceAtLeast(0.8f))
                )
            }

            // Playback-inspired gentle central focal aura pulse aligned at the vinyl center
            if (!reducedMotion && logoAlpha > 0.45f) {
                val centerPulse = (1f - kotlin.math.abs(ringExpansionProgress - 0.45f) * 2f).coerceIn(0f, 1f)
                if (centerPulse > 0.02f) {
                    drawCircle(
                        color = SplashGoldBright.copy(alpha = 0.16f * centerPulse * logoAlpha),
                        radius = logoSize.toPx() * 0.14f * (0.9f + 0.35f * centerPulse),
                        center = center
                    )
                }
            }
        }

        // Exact Official SONORA Logo (untouched asset) with scale/fade & diagonal golden light sweep
        Box(
            modifier = Modifier
                .size(logoSize)
                .graphicsLayer {
                    scaleX = logoScale
                    scaleY = logoScale
                    alpha = logoAlpha
                }
                .clip(logoShape)
                .drawWithContent {
                    drawContent()
                    if (!reducedMotion && lightSweepProgress in -0.3f..1.35f && logoAlpha > 0.2f) {
                        val width = size.width
                        val height = size.height
                        val bandStart = (lightSweepProgress - 0.28f) * (width + height)
                        val bandEnd = bandStart + (width * 0.48f)
                        drawRect(
                            brush = Brush.linearGradient(
                                colors = listOf(
                                    Color.Transparent,
                                    SplashIvoryHighlight.copy(alpha = 0.20f),
                                    SplashGoldBright.copy(alpha = 0.26f),
                                    Color.Transparent
                                ),
                                start = Offset(bandStart, 0f),
                                end = Offset(bandEnd, height)
                            ),
                            blendMode = BlendMode.SrcAtop
                        )
                    }
                }
        ) {
            Image(
                painter = painterResource(id = R.drawable.sonora_official_logo),
                contentDescription = stringResource(id = R.string.splash_logo_content_desc),
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}
