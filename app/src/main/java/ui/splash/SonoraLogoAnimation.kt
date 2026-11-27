package ui.splash

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
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
 * - Warm ambient golden radial glow behind the emblem (shader cached via [drawWithCache])
 * - Subtle expanding circular resonance wave ring (zero per-frame shader allocations)
 * - Smooth GPU-accelerated scale-and-fade reveal via [graphicsLayer]
 * - Soft diagonal golden light sweep using a cached linear gradient translated across the clipped logo
 *   (no offscreen `BlendMode.SrcAtop` layer stall, skipped on reduced-motion / low-RAM devices)
 *
 * Reads all animated floats strictly inside draw/graphicsLayer phases via provider lambdas so
 * per-frame animation ticks cause zero Compose recompositions.
 */
@Composable
fun SonoraLogoAnimation(
    logoScaleProvider: () -> Float,
    logoAlphaProvider: () -> Float,
    ambientGlowAlphaProvider: () -> Float,
    ringExpansionProvider: () -> Float,
    lightSweepProvider: () -> Float,
    logoBitmap: ImageBitmap?,
    reducedMotion: Boolean,
    lowRamDevice: Boolean = false,
    logoSize: Dp = 132.dp,
    modifier: Modifier = Modifier
) {
    val logoShape = remember { RoundedCornerShape(30.dp) }
    val outerStageSize = remember(logoSize) { logoSize * 1.72f }
    val enableRichEffects = !reducedMotion && !lowRamDevice

    Box(
        modifier = modifier
            .size(outerStageSize)
            .drawWithCache {
                val center = Offset(size.width * 0.5f, size.height * 0.5f)
                val glowRadius = size.minDimension * 0.50f
                val baseLogoRadius = logoSize.toPx() * 0.44f
                val ringMaxDelta = size.minDimension * 0.25f
                val maxStrokeWidth = 2.2.dp.toPx()
                val focalBaseRadius = logoSize.toPx() * 0.14f

                val cachedAmbientGlow = Brush.radialGradient(
                    colors = listOf(
                        SplashGoldBright.copy(alpha = 0.34f),
                        SplashGoldWarm.copy(alpha = 0.14f),
                        Color.Transparent
                    ),
                    center = center,
                    radius = glowRadius
                )

                onDrawBehind {
                    val glowAlpha = ambientGlowAlphaProvider().coerceIn(0f, 1f)
                    if (glowAlpha > 0.01f) {
                        drawCircle(
                            brush = cachedAmbientGlow,
                            radius = glowRadius,
                            center = center,
                            alpha = glowAlpha
                        )
                    }

                    if (!reducedMotion) {
                        val ringProgress = ringExpansionProvider()
                        if (ringProgress in 0.01f..0.99f) {
                            val ringRadius = baseLogoRadius + (ringMaxDelta * ringProgress)
                            val ringAlpha = ((1f - ringProgress) * 0.48f).coerceIn(0f, 0.48f)
                            val strokePx = (maxStrokeWidth * (1f - 0.55f * ringProgress)).coerceAtLeast(0.8f)
                            drawCircle(
                                color = SplashGoldBright,
                                alpha = ringAlpha,
                                radius = ringRadius,
                                center = center,
                                style = Stroke(width = strokePx)
                            )
                        }

                        if (enableRichEffects) {
                            val currentLogoAlpha = logoAlphaProvider()
                            if (currentLogoAlpha > 0.45f) {
                                val centerPulse = (1f - kotlin.math.abs(ringProgress - 0.45f) * 2f).coerceIn(0f, 1f)
                                if (centerPulse > 0.02f) {
                                    drawCircle(
                                        color = SplashGoldBright,
                                        alpha = (0.16f * centerPulse * currentLogoAlpha).coerceIn(0f, 1f),
                                        radius = focalBaseRadius * (0.9f + 0.35f * centerPulse),
                                        center = center
                                    )
                                }
                            }
                        }
                    }
                }
            }
            .testTag("sonora_splash_logo"),
        contentAlignment = Alignment.Center
    ) {
        val logoModifier = Modifier
            .size(logoSize)
            .graphicsLayer {
                val scale = logoScaleProvider()
                scaleX = scale
                scaleY = scale
                alpha = logoAlphaProvider().coerceIn(0f, 1f)
            }
            .clip(logoShape)
            .then(
                if (enableRichEffects) {
                    Modifier.drawWithCache {
                        val bandWidth = size.width * 0.48f
                        val sweepSpan = size.width + size.height
                        val cachedSweepBrush = Brush.linearGradient(
                            colors = listOf(
                                Color.Transparent,
                                SplashIvoryHighlight.copy(alpha = 0.22f),
                                SplashGoldBright.copy(alpha = 0.24f),
                                Color.Transparent
                            ),
                            start = Offset(0f, 0f),
                            end = Offset(bandWidth, size.height)
                        )
                        onDrawWithContent {
                            drawContent()
                            val sweep = lightSweepProvider()
                            if (sweep in -0.3f..1.35f && logoAlphaProvider() > 0.2f) {
                                val offsetX = (sweep - 0.28f) * sweepSpan
                                translate(left = offsetX) {
                                    drawRect(
                                        brush = cachedSweepBrush,
                                        topLeft = Offset.Zero,
                                        size = size
                                    )
                                }
                            }
                        }
                    }
                } else {
                    Modifier
                }
            )

        Box(modifier = logoModifier) {
            if (logoBitmap != null) {
                Image(
                    bitmap = logoBitmap,
                    contentDescription = stringResource(id = R.string.splash_logo_content_desc),
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Image(
                    painter = painterResource(id = domain.branding.AppBranding.LOGO_DRAWABLE_RES),
                    contentDescription = stringResource(id = R.string.splash_logo_content_desc),
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}
