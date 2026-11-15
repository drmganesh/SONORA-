package ui.home.animations

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.StartOffset
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Applies a subtle, organic vertical floating drift via GPU-accelerated graphicsLayer.
 * Automatically rests smoothly back to 0 when [enabled] is false or [amplitudeScale] is 0.
 * Distinct [durationMillis] and [phaseOffsetMillis] prevent synchronized robotic motion across items.
 */
@Composable
fun FloatingArtwork(
    enabled: Boolean,
    amplitudeScale: Float = 1f,
    baseAmplitudeDp: Dp = 3.5.dp,
    durationMillis: Int = 4600,
    phaseOffsetMillis: Int = 0,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    val density = LocalDensity.current
    val maxPx = with(density) { (baseAmplitudeDp * amplitudeScale.coerceIn(0f, 1.6f)).toPx() }

    val targetOffsetPx = if (enabled && maxPx > 0.25f) {
        val transition = rememberInfiniteTransition(label = "floating_art_$phaseOffsetMillis")
        val offset by transition.animateFloat(
            initialValue = -maxPx,
            targetValue = maxPx,
            animationSpec = infiniteRepeatable(
                animation = tween(
                    durationMillis = durationMillis.coerceIn(2200, 9000),
                    easing = FastOutSlowInEasing
                ),
                repeatMode = RepeatMode.Reverse,
                initialStartOffset = StartOffset(phaseOffsetMillis.coerceAtLeast(0))
            ),
            label = "floating_y_px"
        )
        offset
    } else {
        0f
    }

    val settledOffsetPx by animateFloatAsState(
        targetValue = targetOffsetPx,
        animationSpec = spring(),
        label = "floating_settle_y"
    )

    val effectiveTranslationY = if (enabled && maxPx > 0.25f) targetOffsetPx else settledOffsetPx

    Box(
        modifier = modifier.graphicsLayer {
            translationY = effectiveTranslationY
        },
        content = content
    )
}
