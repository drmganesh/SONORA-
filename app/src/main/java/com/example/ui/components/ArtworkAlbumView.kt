package com.example.ui.components

import android.graphics.Bitmap
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.Song
import com.example.ui.theme.SonoraCardBorder
import com.example.ui.theme.SonoraCyan
import com.example.ui.theme.SonoraGold
import com.example.ui.theme.SonoraObsidian
import com.example.ui.theme.SonoraSurfaceDark
import com.example.util.AlbumArtHelper

@Composable
fun ArtworkAlbumView(
    song: Song?,
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 20.dp,
    isPlaying: Boolean = false,
    isVinylRotating: Boolean = false
) {
    val context = LocalContext.current

    val artworkBitmap by produceState<Bitmap?>(initialValue = null, key1 = song?.id, key2 = song?.albumId) {
        value = AlbumArtHelper.loadAlbumArt(context, song)
    }

    val palette = ui.theme.LocalDynamicColorPalette.current

    val infiniteTransition = rememberInfiniteTransition(label = "artwork_anim")
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(12000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation"
    )

    val breathingScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (isPlaying) 1.02f else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(3200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "breathingScale"
    )
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.35f,
        targetValue = if (isPlaying) 0.65f else 0.25f,
        animationSpec = infiniteRepeatable(
            animation = tween(3200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glowAlpha"
    )

    val shape = if (isVinylRotating) CircleShape else RoundedCornerShape(cornerRadius)

    Box(
        modifier = modifier
            .scale(breathingScale)
            .shadow(
                elevation = if (isPlaying) 18.dp else 8.dp,
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
        if (artworkBitmap != null) {
            // Real Album Artwork (Extracted from embedded ID3/FLAC or MediaStore)
            Image(
                bitmap = artworkBitmap!!.asImageBitmap(),
                contentDescription = "${song?.album ?: "Album"} Artwork",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .then(if (isVinylRotating && isPlaying) Modifier.rotate(rotation) else Modifier)
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
                // Vinyl grooves
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
                // Center spindle label
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
