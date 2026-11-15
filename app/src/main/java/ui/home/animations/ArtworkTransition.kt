package ui.home.animations

import android.graphics.Bitmap
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
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

/**
 * Renders cached/async local album artwork with:
 * - Smooth crossfade + subtle scale transition when [song] changes
 * - Bounded internal Slow Zoom (contained strictly inside parent clip)
 * - Optional gentle decorative rotation ONLY for selected circular playing medallions
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

    // Slow Zoom scale stays strictly inside parent clipping boundary
    val targetZoom = if (motionEnabled && enableSlowZoom && level.amplitudeScale > 0f) {
        val zoomDelta = (zoomRange * level.amplitudeScale).coerceIn(0f, 0.075f)
        val transition = rememberInfiniteTransition(label = "slow_zoom_${song?.id ?: 0L}")
        val scale by transition.animateFloat(
            initialValue = 1.0f,
            targetValue = 1.0f + zoomDelta,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = zoomDurationMs, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "slow_zoom_scale"
        )
        scale
    } else {
        1.0f
    }

    val settledZoom by animateFloatAsState(
        targetValue = targetZoom,
        animationSpec = tween(320),
        label = "slow_zoom_settle"
    )
    val effectiveZoom = if (motionEnabled && enableSlowZoom) targetZoom else settledZoom

    // Optional gentle decorative rotation for active selected circular artwork
    val rotationDeg = if (motionEnabled && decorativeRotationActive) {
        val rotTransition = rememberInfiniteTransition(label = "decor_rot")
        val deg by rotTransition.animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 22000, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "decor_rot_deg"
        )
        deg
    } else {
        0f
    }

    AnimatedContent(
        targetState = song,
        transitionSpec = {
            if (!motionEnabled) {
                fadeIn(snap()) togetherWith fadeOut(snap())
            } else {
                (fadeIn(tween(420, easing = FastOutSlowInEasing)) +
                    scaleIn(initialScale = 0.94f, animationSpec = tween(420, easing = FastOutSlowInEasing))) togetherWith
                    (fadeOut(tween(300, easing = FastOutSlowInEasing)) +
                        scaleOut(targetScale = 1.03f, animationSpec = tween(300)))
            }
        },
        contentKey = { target -> "${target?.id ?: -1L}_${target?.albumId ?: -1L}" },
        label = "album_art_crossfade",
        modifier = modifier
    ) { targetSong ->
        val cached = AlbumArtHelper.getCachedBitmap(targetSong)
        val bitmap by produceState<Bitmap?>(
            initialValue = cached,
            key1 = targetSong?.id,
            key2 = targetSong?.albumId
        ) {
            if (value == null) {
                value = AlbumArtHelper.loadAlbumArt(context, targetSong)
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.linearGradient(
                        colors = listOf(fallbackTint, fallbackTint.copy(alpha = 0.62f))
                    )
                )
                .graphicsLayer {
                    scaleX = effectiveZoom
                    scaleY = effectiveZoom
                    rotationZ = rotationDeg
                },
            contentAlignment = Alignment.Center
        ) {
            val loadedBitmap = bitmap
            if (loadedBitmap != null) {
                Image(
                    bitmap = loadedBitmap.asImageBitmap(),
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
