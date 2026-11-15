package ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.model.Song
import ui.home.animations.AlbumArtworkAnimator
import ui.home.animations.HomeAnimationLevel
import ui.home.animations.rememberShouldAnimateHomeMotion

/**
 * Circular artwork container loading real embedded or MediaStore album art asynchronously
 * with crisp editorial fallback styling, smooth crossfade transitions, optional staggered
 * floating drift, and optional gentle decorative rotation for selected playing items.
 */
@Composable
fun CircularArtwork(
    song: Song?,
    modifier: Modifier = Modifier,
    fallbackLabel: String? = null,
    isPlaying: Boolean = false,
    accentColor: Color = Color(0xFFDCE6FF),
    shadowElevation: Dp = 8.dp,
    borderWidth: Dp = 0.dp,
    borderColor: Color = Color.Transparent,
    animationLevel: HomeAnimationLevel = HomeAnimationLevel.OFF,
    screenVisible: Boolean = true,
    enableFloating: Boolean = false,
    floatingAmplitudeDp: Dp = 2.5.dp,
    floatingDurationMs: Int = 4400,
    floatingPhaseMs: Int = 0,
    enableSlowZoom: Boolean = false,
    decorativeRotation: Boolean = false
) {
    val motionEnabled = rememberShouldAnimateHomeMotion(
        level = animationLevel,
        screenVisible = screenVisible
    )

    val ringAlpha = if (isPlaying && motionEnabled) {
        val transition = rememberInfiniteTransition(label = "circ_pulse")
        val a by transition.animateFloat(
            initialValue = 0.45f,
            targetValue = 0.95f,
            animationSpec = infiniteRepeatable(
                animation = tween(1200, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "circ_ring_alpha"
        )
        a
    } else if (isPlaying) {
        0.82f
    } else {
        0f
    }

    val effectiveBorderWidth = if (isPlaying && borderWidth == 0.dp) 2.5.dp else borderWidth
    val effectiveBorderColor = if (isPlaying && borderColor == Color.Transparent) {
        accentColor.copy(alpha = ringAlpha)
    } else {
        borderColor
    }

    val effectiveLevel = if (animationLevel == HomeAnimationLevel.OFF) {
        HomeAnimationLevel.OFF
    } else {
        animationLevel
    }

    AlbumArtworkAnimator(
        song = song,
        contentDescription = "${song?.album ?: song?.title ?: "Track"} artwork",
        level = effectiveLevel,
        screenVisible = screenVisible,
        shape = CircleShape,
        fallbackTint = accentColor.copy(alpha = 0.85f),
        iconTint = Color(0xFF1A1B24),
        enableFloating = enableFloating,
        floatingAmplitudeDp = floatingAmplitudeDp,
        floatingDurationMs = floatingDurationMs,
        floatingPhaseMs = floatingPhaseMs,
        enableSlowZoom = enableSlowZoom,
        zoomRange = 0.036f,
        enableParallax = false,
        decorativeRotation = decorativeRotation,
        isPlaying = isPlaying,
        shadowColor = accentColor.copy(alpha = 0.35f),
        shadowElevation = shadowElevation,
        borderWidth = effectiveBorderWidth,
        borderColor = effectiveBorderColor,
        fallbackLabel = fallbackLabel ?: song?.title ?: song?.artist,
        fallbackIconSize = 24.dp,
        modifier = modifier
    )
}
