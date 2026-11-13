package ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.model.AnimationIntensity
import com.example.model.ArtworkAnimationOption
import com.example.model.PlaybackInfo
import com.example.model.PlayerAppearanceConfig
import com.example.model.PlayerGestureConfig
import com.example.model.Song
import com.example.ui.components.ArtworkAlbumView
import ui.player.PlayerGestureController
import ui.player.playerArtworkGestures
import ui.theme.LocalEditorialColors
import ui.theme.LocalReduceAnimations

/**
 * Shared appearance-aware artwork container with unified gesture handling,
 * optional pinch-to-zoom, and customizable corner radius & animation mode.
 */
@Composable
fun ArtworkContainer(
    song: Song?,
    playbackInfo: PlaybackInfo,
    appearance: PlayerAppearanceConfig,
    gestureConfig: PlayerGestureConfig,
    gestureController: PlayerGestureController,
    onTogglePlayPause: () -> Unit,
    onPlayNext: () -> Unit,
    onPlayPrevious: () -> Unit,
    onSeekTo: (Long) -> Unit,
    onOpenQueue: () -> Unit,
    onClose: () -> Unit,
    onArtworkLongPress: () -> Unit,
    modifier: Modifier = Modifier,
    defaultCornerRadius: Dp = 28.dp,
    isCircular: Boolean = false,
    forceVinylSpin: Boolean = false,
    onToggleFavorite: (() -> Unit)? = null,
    onCyclePlayerStyle: (() -> Unit)? = null,
    overlayContent: (@Composable BoxScope.() -> Unit)? = null
) {
    val colors = LocalEditorialColors.current
    val reduceAnimations = LocalReduceAnimations.current
    val duration = playbackInfo.durationMs.coerceAtLeast(song?.durationMs ?: 0L)

    val resolvedRadius = if (appearance == PlayerAppearanceConfig.DEFAULT) {
        defaultCornerRadius
    } else {
        appearance.cornerRadius.radiusDp.dp
    }
    val shape: Shape = if (isCircular) CircleShape else RoundedCornerShape(resolvedRadius)
    val spinVinyl = forceVinylSpin || (!reduceAnimations &&
        appearance.animationIntensity != AnimationIntensity.OFF &&
        appearance.artworkAnimation == ArtworkAnimationOption.VINYL_SPIN)

    val reactiveScale = if (!reduceAnimations &&
        playbackInfo.isPlaying &&
        appearance.animationIntensity != AnimationIntensity.OFF &&
        appearance.artworkAnimation == ArtworkAnimationOption.MORPH_PULSE
    ) {
        val infinite = rememberInfiniteTransition(label = "reactivePulse")
        val scale by infinite.animateFloat(
            initialValue = 1.0f,
            targetValue = 1f + 0.03f * appearance.animationIntensity.multiplier,
            animationSpec = infiniteRepeatable(
                animation = tween(1400, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "morphScale"
        )
        scale
    } else 1.0f

    Box(
        modifier = modifier
            .graphicsLayer {
                val totalScale = gestureController.artworkZoomScale * reactiveScale
                scaleX = totalScale
                scaleY = totalScale
            }
            .shadow(
                elevation = if (playbackInfo.isPlaying) 18.dp else 10.dp,
                shape = shape,
                ambientColor = colors.shadowColor,
                spotColor = colors.shadowColor
            )
            .clip(shape)
            .playerArtworkGestures(
                config = gestureConfig,
                controller = gestureController,
                currentPositionMs = playbackInfo.currentPositionMs,
                durationMs = duration,
                isPlaying = playbackInfo.isPlaying,
                onTogglePlayPause = onTogglePlayPause,
                onPlayNext = onPlayNext,
                onPlayPrevious = onPlayPrevious,
                onSeekTo = onSeekTo,
                onOpenQueue = onOpenQueue,
                onClose = onClose,
                onArtworkLongPress = onArtworkLongPress,
                onToggleFavorite = onToggleFavorite,
                onCyclePlayerStyle = onCyclePlayerStyle
            )
            .testTag("player_artwork_box"),
        contentAlignment = Alignment.Center
    ) {
        ArtworkAlbumView(
            song = song,
            modifier = Modifier.fillMaxSize(),
            cornerRadius = if (isCircular) 1000.dp else resolvedRadius,
            isPlaying = playbackInfo.isPlaying && (appearance.artworkAnimation != ArtworkAnimationOption.NONE || forceVinylSpin),
            isVinylRotating = isCircular && spinVinyl
        )
        overlayContent?.invoke(this)
    }
}
