package ui.home.animations

import android.graphics.Bitmap
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.Song
import com.example.ui.theme.OutfitFontFamily
import com.example.util.AlbumArtHelper
import ui.motion.SonoraMotionSpecs

/**
 * Renders cached/async local album artwork with:
 * - Smooth crossfade + subtle scale transition when [song] changes
 * - Draw-phase-only Slow Zoom and decorative rotation (zero per-frame recompositions)
 * - Remembered ImageBitmap & synchronous LruCache seeding (zero frame allocations or flicker)
 */
@Composable
fun ArtworkTransition(
    song: Song?,
    contentDescription: String,
    motionEnabled: Boolean,
    level: HomeAnimationLevel,
    fallbackTint: Color,
    iconTint: Color,
    modifier: Modifier = Modifier,
    fallbackLabel: String? = null,
    fallbackIconSize: Dp = 40.dp,
    enableSlowZoom: Boolean = true,
    zoomRange: Float = 0.045f,
    zoomDurationMs: Int = 7800,
    decorativeRotationActive: Boolean = false
) {
    val context = LocalContext.current

    // Slow Zoom scale stays strictly inside parent clipping boundary and is read only in graphicsLayer
    val zoomState: State<Float> = if (motionEnabled && enableSlowZoom && level.amplitudeScale > 0f) {
        val zoomDelta = (zoomRange * level.amplitudeScale).coerceIn(0f, 0.075f)
        val transition = rememberInfiniteTransition(label = "slow_zoom_${song?.id ?: 0L}")
        transition.animateFloat(
            initialValue = 1.0f,
            targetValue = 1.0f + zoomDelta,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = zoomDurationMs, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "slow_zoom_scale"
        )
    } else {
        remember { mutableFloatStateOf(1.0f) }
    }

    // Optional gentle decorative rotation for active selected circular artwork (read only in graphicsLayer)
    val rotationState: State<Float> = if (motionEnabled && decorativeRotationActive) {
        val rotTransition = rememberInfiniteTransition(label = "decor_rot")
        rotTransition.animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 22000, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "decor_rot_deg"
        )
    } else {
        remember { mutableFloatStateOf(0f) }
    }

    AnimatedContent(
        targetState = song,
        transitionSpec = {
            if (!motionEnabled) {
                fadeIn(snap()) togetherWith fadeOut(snap())
            } else {
                (fadeIn(tween(SonoraMotionSpecs.CARD_CONTENT_MS, easing = SonoraMotionSpecs.EmphasizedDecelerate)) +
                    scaleIn(initialScale = 0.96f, animationSpec = tween(SonoraMotionSpecs.CARD_CONTENT_MS, easing = SonoraMotionSpecs.EmphasizedDecelerate))) togetherWith
                    (fadeOut(tween(SonoraMotionSpecs.SMALL_UI_MS, easing = SonoraMotionSpecs.EmphasizedAccelerate)) +
                        scaleOut(targetScale = 1.02f, animationSpec = tween(SonoraMotionSpecs.SMALL_UI_MS, easing = SonoraMotionSpecs.EmphasizedAccelerate)))
            }
        },
        contentKey = { target -> "${target?.id ?: -1L}_${target?.albumId ?: -1L}_${target?.artworkUrl.orEmpty()}" },
        label = "album_art_crossfade",
        modifier = modifier
    ) { targetSong ->
        val cached = remember(targetSong?.id, targetSong?.albumId, targetSong?.artworkUrl) {
            AlbumArtHelper.getCachedBitmap(targetSong)
        }
        val bitmap by produceState<Bitmap?>(
            initialValue = cached,
            key1 = targetSong?.id,
            key2 = targetSong?.albumId,
            key3 = targetSong?.artworkUrl
        ) {
            if (value == null) {
                value = AlbumArtHelper.loadAlbumArt(context, targetSong)
            }
        }
        val imageBitmap = remember(bitmap) { bitmap?.asImageBitmap() }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.linearGradient(
                        colors = listOf(fallbackTint, fallbackTint.copy(alpha = 0.62f))
                    )
                )
                .graphicsLayer {
                    val z = zoomState.value
                    scaleX = z
                    scaleY = z
                    rotationZ = rotationState.value
                },
            contentAlignment = Alignment.Center
        ) {
            if (imageBitmap != null) {
                Image(
                    bitmap = imageBitmap,
                    contentDescription = contentDescription,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                val initial = (fallbackLabel ?: targetSong?.title ?: targetSong?.artist)
                    ?.trim()
                    ?.firstOrNull()
                    ?.uppercaseChar()
                    ?.toString()
                if (!fallbackLabel.isNullOrBlank() && initial != null) {
                    Text(
                        text = initial,
                        fontFamily = OutfitFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = iconTint
                    )
                } else {
                    Icon(
                        imageVector = Icons.Rounded.MusicNote,
                        contentDescription = contentDescription,
                        tint = iconTint.copy(alpha = 0.72f),
                        modifier = Modifier.size(fallbackIconSize)
                    )
                }
            }
        }
    }
}
