package ui.home.animations

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp

/**
 * Provides subtle touch-responsive depth parallax without registering hardware sensors
 * or interfering with LazyColumn / LazyRow scrolling or clickable modifiers.
 */
@Composable
fun ParallaxArtwork(
    enabled: Boolean,
    amplitudeScale: Float = 1f,
    maxOffsetDp: Dp = 7.dp,
    maxTiltDegrees: Float = 2.4f,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    val density = LocalDensity.current
    val maxOffsetPx = with(density) { (maxOffsetDp * amplitudeScale.coerceIn(0f, 1.6f)).toPx() }
    val tiltRange = maxTiltDegrees * amplitudeScale.coerceIn(0f, 1.6f)

    var targetNormX by remember { mutableFloatStateOf(0f) }
    var targetNormY by remember { mutableFloatStateOf(0f) }
    var boxSize by remember { mutableFloatStateOf(1f) }
    var intSize by remember { mutableFloatStateOf(1f) }
    var measuredWidth by remember { mutableFloatStateOf(1f) }
    var measuredHeight by remember { mutableFloatStateOf(1f) }

    val animX by animateFloatAsState(
        targetValue = if (enabled) targetNormX else 0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioLowBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "parallax_x"
    )
    val animY by animateFloatAsState(
        targetValue = if (enabled) targetNormY else 0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioLowBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "parallax_y"
    )

    val pointerModifier = if (enabled && maxOffsetPx > 0.25f) {
        Modifier
            .onSizeChanged { s: IntSize ->
                measuredWidth = s.width.toFloat().coerceAtLeast(1f)
                measuredHeight = s.height.toFloat().coerceAtLeast(1f)
                boxSize = measuredWidth
                intSize = measuredHeight
            }
            .pointerInput(enabled) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        val active = event.changes.firstOrNull { it.pressed }
                        if (active != null) {
                            val nx = (((active.position.x / measuredWidth) - 0.5f) * 2f).coerceIn(-1f, 1f)
                            val ny = (((active.position.y / measuredHeight) - 0.5f) * 2f).coerceIn(-1f, 1f)
                            targetNormX = nx
                            targetNormY = ny
                        } else {
                            targetNormX = 0f
                            targetNormY = 0f
                        }
                    }
                }
            }
    } else {
        Modifier
    }

    Box(
        modifier = modifier
            .then(pointerModifier)
            .graphicsLayer {
                translationX = animX * maxOffsetPx
                translationY = animY * maxOffsetPx
                rotationY = animX * tiltRange
                rotationX = -animY * tiltRange
                cameraDistance = 16f * density.density
            },
        content = content
    )
}
