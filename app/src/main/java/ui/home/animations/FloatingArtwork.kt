package ui.home.animations

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.StartOffset
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Applies a subtle, organic vertical floating drift via GPU-accelerated graphicsLayer.
 * Reads animation state strictly in the draw phase (`graphicsLayer`) so the composable
 * never recomposes on animation frames.
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
    val active = enabled && maxPx > 0.25f

    val offsetPxState: State<Float> = if (active) {
        val transition = rememberInfiniteTransition(label = "floating_art_$phaseOffsetMillis")
        transition.animateFloat(
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
    } else {
        remember { mutableFloatStateOf(0f) }
    }

    Box(
        modifier = modifier.graphicsLayer {
            translationY = offsetPxState.value
        },
        content = content
    )
}
