package ui.components

import android.graphics.Bitmap
import androidx.compose.animation.core.FastOutSlowInEasing
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import com.example.ui.theme.OutfitFontFamily
import com.example.util.AlbumArtHelper

/**
 * Circular artwork container loading real embedded or MediaStore album art asynchronously
 * with crisp editorial fallback styling.
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
    borderColor: Color = Color.Transparent
) {
    val context = LocalContext.current
    val bitmap by produceState<Bitmap?>(initialValue = null, key1 = song?.id, key2 = song?.albumId) {
        value = AlbumArtHelper.loadAlbumArt(context, song)
    }

    val ringAlpha = if (isPlaying) {
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
    } else 0f

    val effectiveBorderWidth = if (isPlaying && borderWidth == 0.dp) 2.5.dp else borderWidth
    val effectiveBorderColor = if (isPlaying && borderColor == Color.Transparent) {
        accentColor.copy(alpha = ringAlpha)
    } else {
        borderColor
    }

    Box(
        modifier = modifier
            .shadow(
                elevation = shadowElevation,
                shape = CircleShape,
                ambientColor = accentColor.copy(alpha = 0.35f),
                spotColor = accentColor.copy(alpha = 0.35f)
            )
            .clip(CircleShape)
            .background(
                Brush.linearGradient(
                    colors = listOf(
                        accentColor.copy(alpha = 0.85f),
                        accentColor.copy(alpha = 0.45f)
                    )
                )
            )
            .then(
                if (effectiveBorderWidth > 0.dp) {
                    Modifier.border(effectiveBorderWidth, effectiveBorderColor, CircleShape)
                } else Modifier
            ),
        contentAlignment = Alignment.Center
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap!!.asImageBitmap(),
                contentDescription = "${song?.album ?: song?.title ?: "Track"} artwork",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            val initial = (fallbackLabel ?: song?.title ?: song?.artist)
                ?.trim()
                ?.firstOrNull()
                ?.uppercaseChar()
                ?.toString()
            if (initial != null) {
                Text(
                    text = initial,
                    fontFamily = OutfitFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = Color(0xFF1A1B24)
                )
            } else {
                Icon(
                    imageVector = Icons.Rounded.MusicNote,
                    contentDescription = "Album artwork",
                    tint = Color(0xFF1A1B24)
                )
            }
        }
    }
}
