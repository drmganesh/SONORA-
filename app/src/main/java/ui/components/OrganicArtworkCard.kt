package ui.components

import android.graphics.Bitmap
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.model.Song
import com.example.util.AlbumArtHelper
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Asymmetric sweeping organic hero artwork shape inspired by editorial music collages:
 * smooth diagonal rounded-droplet/leaf curvature matching the attached reference direction.
 */
val OrganicHeroLeafShape: Shape = GenericShape { size, _ ->
    val w = size.width
    val h = size.height
    moveTo(w * 0.56f, h * 0.03f)
    cubicTo(
        w * 0.88f, h * 0.01f,
        w * 1.01f, h * 0.25f,
        w * 0.96f, h * 0.60f
    )
    cubicTo(
        w * 0.91f, h * 0.92f,
        w * 0.65f, h * 1.00f,
        w * 0.30f, h * 0.97f
    )
    cubicTo(
        w * 0.02f, h * 0.95f,
        -w * 0.04f, h * 0.66f,
        w * 0.14f, h * 0.38f
    )
    cubicTo(
        w * 0.28f, h * 0.15f,
        w * 0.38f, h * 0.04f,
        w * 0.56f, h * 0.03f
    )
    close()
}

/**
 * Soft asymmetrical pebble shape for secondary editorial collage & mix cards.
 */
val OrganicPebbleShape: Shape = GenericShape { size, _ ->
    val w = size.width
    val h = size.height
    moveTo(w * 0.50f, 0f)
    cubicTo(w * 0.88f, 0f, w, h * 0.16f, w, h * 0.52f)
    cubicTo(w, h * 0.86f, w * 0.82f, h, h * 0.50f.coerceAtMost(w * 0.48f), h)
    cubicTo(w * 0.14f, h, 0f, h * 0.84f, 0f, h * 0.48f)
    cubicTo(0f, h * 0.14f, w * 0.16f, 0f, w * 0.50f, 0f)
    close()
}

/**
 * 9-petal scalloped flower badge shape used for the Music Discovery icon container.
 */
fun createScallopedShape(lobes: Int = 9, depthFraction: Float = 0.14f): Shape =
    GenericShape { size, _ ->
        val cx = size.width / 2f
        val cy = size.height / 2f
        val outerR = minOf(cx, cy)
        val innerR = outerR * (1f - depthFraction)
        val steps = lobes * 12
        val path = Path()
        for (i in 0..steps) {
            val theta = (2.0 * PI * i) / steps
            val wave = (0.5 + 0.5 * cos(lobes * theta)).toFloat()
            val r = innerR + (outerR - innerR) * wave
            val x = cx + r * cos(theta).toFloat()
            val y = cy + r * sin(theta).toFloat()
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        path.close()
        addPath(path)
    }

val DiscoveryScallopShape: Shape = createScallopedShape(lobes = 9, depthFraction = 0.16f)

/**
 * Renders real album artwork clipped into an expressive organic shape with soft shadows
 * and tap / long-press support.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun OrganicArtworkCard(
    song: Song?,
    contentDescription: String,
    shape: Shape = OrganicHeroLeafShape,
    fallbackTint: Color = Color(0xFFDCE6FF),
    iconTint: Color = Color(0xFF2B4D76),
    shadowColor: Color = Color(0xFF7B74A8).copy(alpha = 0.25f),
    shadowElevation: Dp = 14.dp,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val bitmap by produceState<Bitmap?>(initialValue = null, key1 = song?.id, key2 = song?.albumId) {
        value = AlbumArtHelper.loadAlbumArt(context, song)
    }

    Box(
        modifier = modifier
            .shadow(
                elevation = shadowElevation,
                shape = shape,
                ambientColor = shadowColor,
                spotColor = shadowColor
            )
            .clip(shape)
            .background(
                Brush.linearGradient(
                    colors = listOf(fallbackTint, fallbackTint.copy(alpha = 0.65f))
                )
            )
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick?.let { cb ->
                    {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        cb()
                    }
                }
            ),
        contentAlignment = Alignment.Center
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap!!.asImageBitmap(),
                contentDescription = contentDescription,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Icon(
                imageVector = Icons.Rounded.MusicNote,
                contentDescription = contentDescription,
                tint = iconTint.copy(alpha = 0.7f),
                modifier = Modifier.size(44.dp)
            )
        }
    }
}
