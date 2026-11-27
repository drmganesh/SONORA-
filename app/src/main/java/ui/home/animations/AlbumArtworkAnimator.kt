package ui.home.animations

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.model.Song

/**
 * Orchestrates premium editorial album artwork motion across Home Screen components:
 * Floating drift, touch-responsive depth parallax, bounded Slow Zoom, organic corner Morph,
 * optional decorative rotation (strictly for selected elements), and crossfade album transitions.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AlbumArtworkAnimator(
    song: Song?,
    contentDescription: String,
    level: HomeAnimationLevel,
    screenVisible: Boolean,
    shape: Shape,
    fallbackTint: Color,
    iconTint: Color,
    modifier: Modifier = Modifier,
    cornerMorphBaseDp: Dp? = null,
    cornerMorphDeltaDp: Dp = 0.dp,
    cornerMorphDurationMs: Int = 6400,
    enableFloating: Boolean = true,
    floatingAmplitudeDp: Dp = 3.dp,
    floatingDurationMs: Int = 4600,
    floatingPhaseMs: Int = 0,
    enableSlowZoom: Boolean = true,
    zoomRange: Float = 0.042f,
    enableParallax: Boolean = true,
    parallaxMaxOffsetDp: Dp = 6.dp,
    decorativeRotation: Boolean = false,
    isPlaying: Boolean = false,
    shadowColor: Color = Color(0xFF7B74A8).copy(alpha = 0.25f),
    shadowElevation: Dp = 12.dp,
    borderWidth: Dp = 0.dp,
    borderColor: Color = Color.Transparent,
    fallbackLabel: String? = null,
    fallbackIconSize: Dp = 40.dp,
    ignoreVisibilityForPreview: Boolean = false,
    onClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
    overlayContent: @Composable BoxScope.() -> Unit = {}
) {
    val motionState = ui.motion.LocalEffectiveMotionState.current
    val motionEnabled = rememberShouldAnimateHomeMotion(
        level = level,
        screenVisible = screenVisible,
        ignoreVisibilityForPreview = ignoreVisibilityForPreview
    )
    val haptic = LocalHapticFeedback.current
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val pressScale = animateFloatAsState(
        targetValue = if (isPressed && motionState.essentialFeedbackEnabled) 0.965f else 1f,
        animationSpec = ui.motion.SonoraMotionSpecs.buttonScaleSpec(motionState),
        label = "artwork_press_scale"
    )

    // Tactile press-only corner morphing (avoids continuous 60-120fps shadow/clip outline rebuilds while idle)
    val effectiveShape = if (cornerMorphBaseDp != null) {
        val delta = cornerMorphDeltaDp * level.amplitudeScale.coerceIn(0f, 1.35f)
        val targetCorner = if (isPressed && motionEnabled && delta > 0.5.dp) {
            (cornerMorphBaseDp + delta * 1.4f).coerceAtLeast(4.dp)
        } else {
            cornerMorphBaseDp
        }
        val animDp by animateDpAsState(
            targetValue = targetCorner,
            animationSpec = if (motionState.essentialFeedbackEnabled) {
                spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMedium)
            } else {
                androidx.compose.animation.core.snap()
            },
            label = "morph_dp"
        )
        remember(animDp) { RoundedCornerShape(animDp) }
    } else {
        shape
    }

    FloatingArtwork(
        enabled = motionEnabled && enableFloating,
        amplitudeScale = level.amplitudeScale,
        baseAmplitudeDp = floatingAmplitudeDp,
        durationMillis = floatingDurationMs,
        phaseOffsetMillis = floatingPhaseMs,
        modifier = modifier
    ) {
        ParallaxArtwork(
            enabled = motionEnabled && enableParallax,
            amplitudeScale = level.amplitudeScale,
            maxOffsetDp = parallaxMaxOffsetDp,
            modifier = Modifier.fillMaxSize()
        ) {
            val clickModifier = when {
                onClick != null && onLongClick != null -> Modifier.combinedClickable(
                    interactionSource = interactionSource,
                    indication = androidx.compose.material3.ripple(bounded = true),
                    onClick = onClick,
                    onLongClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onLongClick()
                    }
                )
                onClick != null -> Modifier.combinedClickable(
                    interactionSource = interactionSource,
                    indication = androidx.compose.material3.ripple(bounded = true),
                    onClick = onClick,
                    onLongClick = null
                )
                else -> Modifier
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        val s = pressScale.value
                        scaleX = s
                        scaleY = s
                    }
                    .shadow(
                        elevation = shadowElevation,
                        shape = effectiveShape,
                        ambientColor = shadowColor,
                        spotColor = shadowColor
                    )
                    .clip(effectiveShape)
                    .then(
                        if (borderWidth > 0.dp) {
                            Modifier.border(borderWidth, borderColor, effectiveShape)
                        } else Modifier
                    )
                    .then(clickModifier),
                contentAlignment = Alignment.Center
            ) {
                ArtworkTransition(
                    song = song,
                    contentDescription = contentDescription,
                    motionEnabled = motionEnabled,
                    level = level,
                    fallbackTint = fallbackTint,
                    iconTint = iconTint,
                    fallbackLabel = fallbackLabel,
                    fallbackIconSize = fallbackIconSize,
                    enableSlowZoom = enableSlowZoom,
                    zoomRange = zoomRange,
                    decorativeRotationActive = decorativeRotation && isPlaying
                )
                overlayContent()
            }
        }
    }
}
