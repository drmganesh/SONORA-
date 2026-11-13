package ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.BrightnessHigh
import androidx.compose.material.icons.rounded.FastForward
import androidx.compose.material.icons.rounded.FastRewind
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.ZoomIn
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.OutfitFontFamily
import com.example.ui.theme.PlusJakartaFontFamily
import ui.theme.LocalEditorialColors

enum class GestureHudType {
    VOLUME,
    BRIGHTNESS,
    SEEK_FORWARD,
    SEEK_BACKWARD,
    NEXT_TRACK,
    PREVIOUS_TRACK,
    PLAY_PAUSE,
    FAVORITE,
    STYLE_CHANGED,
    ZOOM,
    QUEUE
}

data class GestureHudState(
    val type: GestureHudType,
    val label: String,
    val progress: Float? = null,
    val isPlaying: Boolean = true
)

/**
 * Floating translucent HUD overlay showing live gesture feedback:
 * system music volume, brightness slider, seek deltas, track skip, or artwork zoom.
 */
@Composable
fun GestureOverlay(
    hudState: GestureHudState?,
    modifier: Modifier = Modifier
) {
    val colors = LocalEditorialColors.current

    AnimatedVisibility(
        visible = hudState != null,
        enter = fadeIn() + scaleIn(initialScale = 0.92f),
        exit = fadeOut() + scaleOut(targetScale = 0.94f),
        modifier = modifier
    ) {
        if (hudState != null) {
            val icon: ImageVector = when (hudState.type) {
                GestureHudType.VOLUME -> Icons.AutoMirrored.Rounded.VolumeUp
                GestureHudType.BRIGHTNESS -> Icons.Rounded.BrightnessHigh
                GestureHudType.SEEK_FORWARD -> Icons.Rounded.FastForward
                GestureHudType.SEEK_BACKWARD -> Icons.Rounded.FastRewind
                GestureHudType.NEXT_TRACK -> Icons.Rounded.SkipNext
                GestureHudType.PREVIOUS_TRACK -> Icons.Rounded.SkipPrevious
                GestureHudType.PLAY_PAUSE -> if (hudState.isPlaying) Icons.Rounded.PlayArrow else Icons.Rounded.Pause
                GestureHudType.FAVORITE -> Icons.Rounded.Favorite
                GestureHudType.STYLE_CHANGED -> Icons.Rounded.Palette
                GestureHudType.ZOOM -> Icons.Rounded.ZoomIn
                GestureHudType.QUEUE -> Icons.Rounded.SkipNext
            }

            Surface(
                shape = RoundedCornerShape(22.dp),
                color = colors.surface.copy(alpha = 0.94f),
                border = BorderStroke(1.dp, colors.accentStrong.copy(alpha = 0.45f)),
                shadowElevation = 14.dp
            ) {
                Column(
                    modifier = Modifier
                        .padding(horizontal = 20.dp, vertical = 14.dp)
                        .width(176.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = hudState.label,
                            tint = colors.accentStrong,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = hudState.label,
                            fontFamily = OutfitFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = colors.textPrimary
                        )
                    }

                    if (hudState.progress != null) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp))
                                .background(colors.cardBorder)
                        ) {
                            LinearProgressIndicator(
                                progress = { hudState.progress.coerceIn(0f, 1f) },
                                modifier = Modifier.fillMaxWidth().height(6.dp),
                                color = colors.accentStrong,
                                trackColor = colors.cardBorder,
                                strokeCap = StrokeCap.Round
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = if (hudState.type == GestureHudType.VOLUME) "Media Volume" else "Screen Brightness",
                            fontFamily = PlusJakartaFontFamily,
                            fontSize = 10.sp,
                            color = colors.textMuted
                        )
                    }
                }
            }
        }
    }
}
