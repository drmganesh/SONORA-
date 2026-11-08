package com.example.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.model.Song
import com.example.ui.theme.SonoraCardBorder
import com.example.ui.theme.SonoraCyan
import com.example.ui.theme.SonoraGold
import com.example.ui.theme.SonoraObsidian
import com.example.ui.theme.SonoraSurfaceDark

@Composable
fun ArtworkAlbumView(
    song: Song?,
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 20.dp,
    isPlaying: Boolean = false,
    isVinylRotating: Boolean = false
) {
    val infiniteTransition = rememberInfiniteTransition(label = "vinyl_rotation")
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(12000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation"
    )

    val shape = if (isVinylRotating) CircleShape else RoundedCornerShape(cornerRadius)

    Box(
        modifier = modifier
            .shadow(
                elevation = if (isPlaying) 16.dp else 8.dp,
                shape = shape,
                ambientColor = SonoraGold.copy(alpha = 0.35f),
                spotColor = SonoraCyan.copy(alpha = 0.35f)
            )
            .clip(shape)
            .background(
                Brush.radialGradient(
                    colors = listOf(SonoraSurfaceDark, SonoraObsidian)
                )
            )
            .border(1.dp, SonoraCardBorder, shape),
        contentAlignment = Alignment.Center
    ) {
        if (song?.uri != null) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(song.uri)
                    .crossfade(true)
                    .build(),
                contentDescription = "${song.album} artwork",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .then(if (isVinylRotating && isPlaying) Modifier.rotate(rotation) else Modifier)
            )
        }

        // Fallback Stylized Vinyl Disc if no image was loaded or image is blank
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
                    .size(140.dp)
                    .border(1.5.dp, SonoraCardBorder.copy(alpha = 0.5f), CircleShape)
            )
            Box(
                modifier = Modifier
                    .size(90.dp)
                    .border(1.5.dp, SonoraGold.copy(alpha = 0.3f), CircleShape)
            )
            // Center label
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .background(
                        Brush.linearGradient(listOf(SonoraGold, SonoraCyan)),
                        CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.MusicNote,
                    contentDescription = null,
                    tint = SonoraObsidian,
                    modifier = Modifier.size(28.dp)
                )
            }
        }
    }
}
