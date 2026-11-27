package com.example.ui.components

import android.graphics.Bitmap
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
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
import com.example.ui.theme.SonoraCardBorder
import com.example.ui.theme.SonoraObsidian
import com.example.ui.theme.SonoraSurfaceDark
import com.example.util.AlbumArtHelper
import data.artwork.AlbumArtworkQualityManager
import data.artwork.ArtworkDisplayRole
import ui.motion.LocalEffectiveMotionState
import ui.motion.SonoraMotionSpecs

@Composable
fun ArtworkAlbumView(
    song: Song?,
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 20.dp,
    isPlaying: Boolean = false,
    isVinylRotating: Boolean = false,
    artworkRole: ArtworkDisplayRole = ArtworkDisplayRole.THUMBNAIL
) {
    val context = LocalContext.current
    val qualityMode = AlbumArtworkQualityManager.currentQualityMode
    val motionState = LocalEffectiveMotionState.current

    // Synchronously seed from in-memory LruCache to prevent 1-2 frame blank placeholder flashes
    // during LazyColumn item recycling and track switching.
    val initialCached = remember(song?.id, song?.albumId, song?.artworkUrl, artworkRole, qualityMode) {
        AlbumArtHelper.getCachedBitmap(song, artworkRole)
    }

    val artworkBitmap by produceState<Bitmap?>(
        initialValue = initialCached,
        key1 = song?.id,
        key2 = "${song?.albumId}_${song?.artworkUrl.orEmpty()}_${artworkRole.name}_${qualityMode.name}"
    ) {
        val current = AlbumArtHelper.getCachedBitmap(song, artworkRole)
        if (current != null) {
            value = current
        } else {
            value = null
            value = AlbumArtHelper.loadAlbumArt(context, song, artworkRole)
        }
    }

    // Cache ImageBitmap wrapper so animations never re-allocate it on draw/recompose
    val imageBitmap = remember(artworkBitmap) { artworkBitmap?.asImageBitmap() }

    // Smooth fade-in only when an uncached bitmap finishes decoding asynchronously
    val artworkAlpha = animateFloatAsState(
        targetValue = if (imageBitmap != null) 1f else 0f,
        animationSpec = if (initialCached != null || !motionState.essentialFeedbackEnabled) {
            snap()
        } else {
            tween(
                durationMillis = motionState.durationMillis(SonoraMotionSpecs.ARTWORK_CROSSFADE_MS, essential = true),
                easing = SonoraMotionSpecs.EmphasizedDecelerate
            )
        },
        label = "artwork_fade_in"
    )

    val palette = ui.theme.LocalDynamicColorPalette.current
    val shouldSpin = isVinylRotating && isPlaying && motionState.decorativeMotionEnabled
    val shouldBreathe = isPlaying && artworkRole == ArtworkDisplayRole.NOW_PLAYING && motionState.decorativeMotionEnabled

    // Keep infinite animation state as State<Float> and read strictly inside graphicsLayer
    // (draw phase) so ArtworkAlbumView never recomposes at 60-120fps.
    val rotationState: State<Float> = if (shouldSpin) {
        val infiniteTransition = rememberInfiniteTransition(label = "artwork_spin")
        infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(
                animation = tween(12000, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "rotation"
        )
    } else {
        remember { mutableFloatStateOf(0f) }
    }

    val breathingScaleState: State<Float> = if (shouldBreathe) {
        val infiniteTransition = rememberInfiniteTransition(label = "artwork_breathe")
        infiniteTransition.animateFloat(
            initialValue = 1f,
            targetValue = 1.018f,
            animationSpec = infiniteRepeatable(
                animation = tween(3200, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "breathingScale"
        )
    } else {
        remember { mutableFloatStateOf(1f) }
    }

    val shape = remember(isVinylRotating, cornerRadius) {
        if (isVinylRotating) CircleShape else RoundedCornerShape(cornerRadius)
    }
    val glowAlpha = if (isPlaying) 0.48f else 0.22f
    val shadowElevation = when {
        artworkRole == ArtworkDisplayRole.THUMBNAIL -> if (isPlaying) 6.dp else 3.dp
        isPlaying -> 14.dp
        else -> 8.dp
    }

    Box(
        modifier = modifier
            .graphicsLayer {
                val s = breathingScaleState.value
                scaleX = s
                scaleY = s
            }
            .shadow(
                elevation = shadowElevation,
                shape = shape,
                ambientColor = palette.glowColor.copy(alpha = glowAlpha),
                spotColor = palette.glowColor.copy(alpha = glowAlpha)
            )
            .clip(shape)
            .background(
                Brush.radialGradient(
                    colors = listOf(palette.surfaceVariant, palette.surface)
                )
            )
            .border(1.dp, palette.cardBorder, shape),
        contentAlignment = Alignment.Center
    ) {
        if (imageBitmap != null) {
            Image(
                bitmap = imageBitmap,
                contentDescription = "${song?.album ?: "Album"} Artwork",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        alpha = artworkAlpha.value
                        rotationZ = rotationState.value
                    }
            )
        } else {
            // Elegant Stylized Vinyl Disc Fallback
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.sweepGradient(
                            colors = listOf(
                                SonoraObsidian,
                                SonoraSurfaceDark,
                                SonoraObsidian,
                                Color(0xFF1E222D),
                                SonoraObsidian
                            )
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(130.dp)
                        .border(1.dp, SonoraCardBorder.copy(alpha = 0.6f), CircleShape)
                )
                Box(
                    modifier = Modifier
                        .size(86.dp)
                        .border(1.dp, palette.primary.copy(alpha = 0.35f), CircleShape)
                )
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .background(
                            Brush.linearGradient(listOf(palette.primary, palette.accent)),
                            CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    val initial = song?.title?.firstOrNull()?.uppercase() ?: "S"
                    Text(
                        text = initial,
                        color = Color.Black,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 22.sp
                    )
                }
            }
        }
    }
}
