package ui.settings

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.SonoraCharcoal
import com.example.ui.theme.SonoraCharcoalBorder
import com.example.ui.theme.SonoraDustyRose
import com.example.ui.theme.SonoraDustyRoseLight
import com.example.ui.theme.SonoraTextMuted
import com.example.ui.theme.SonoraTextPrimary
import com.example.ui.theme.SonoraTextSecondary
import ui.player.seekbar.BlocksSeekbar
import ui.player.seekbar.ClassicSeekbar
import ui.player.seekbar.DotsSeekbar
import ui.player.seekbar.GradientSeekbar
import ui.player.seekbar.M3ExpressiveSeekbar
import ui.player.seekbar.Material3Seekbar
import ui.player.seekbar.NeonGlowSeekbar
import ui.player.seekbar.SeekbarStyle
import ui.player.seekbar.WaveLineSeekbar
import ui.player.seekbar.WaveformSeekbar

/**
 * Seekbar Style Selection Card with Live Animated Preview
 * Renders an actual interactive composable seekbar preview oscillating with sample progress.
 */
@Composable
fun SeekbarStyleCard(
    style: SeekbarStyle,
    isSelected: Boolean,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Smooth animated sample progress for the live preview
    val infiniteTransition = rememberInfiniteTransition(label = "sampleProgressTransition_${style.id}")
    val sampleProgress by infiniteTransition.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.75f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 4000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "sampleProgress"
    )

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .border(
                width = if (isSelected) 2.dp else 1.dp,
                color = if (isSelected) SonoraDustyRose else SonoraCharcoalBorder,
                shape = RoundedCornerShape(22.dp)
            )
            .clickable { onSelect() },
        color = if (isSelected) Color(0xFF191415) else SonoraCharcoal,
        tonalElevation = if (isSelected) 8.dp else 2.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp)
        ) {
            // Top Header: Name, Badge, and Selection Indicator
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = style.displayName,
                        color = if (isSelected) SonoraDustyRose else SonoraTextPrimary,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = if (isSelected) SonoraDustyRose.copy(alpha = 0.25f) else Color.White.copy(alpha = 0.08f)
                    ) {
                        Text(
                            text = style.subtitle,
                            color = if (isSelected) SonoraDustyRoseLight else SonoraTextMuted,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }

                if (isSelected) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = SonoraDustyRose
                        ) {
                            Text(
                                text = "ACTIVE",
                                color = Color.Black,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.ExtraBold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = "Active Style",
                            tint = SonoraDustyRose,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .size(20.dp)
                            .border(1.5.dp, SonoraTextMuted.copy(alpha = 0.5f), CircleShape)
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Short Description
            Text(
                text = style.description,
                color = SonoraTextSecondary,
                fontSize = 12.sp,
                lineHeight = 16.sp
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Live Animated Preview Container (Using the actual Composable!)
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp)),
                color = Color.Black.copy(alpha = 0.5f),
                border = androidx.compose.foundation.BorderStroke(0.5.dp, Color.White.copy(alpha = 0.06f))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 10.dp)
                ) {
                    when (style) {
                        SeekbarStyle.BLOCKS -> BlocksSeekbar(
                            progress = sampleProgress,
                            onSeekProgress = {},
                            onSeekConfirmed = {},
                            isPlaying = true,
                            enabled = false,
                            accentColor = if (isSelected) SonoraDustyRose else SonoraDustyRose.copy(alpha = 0.85f)
                        )

                        SeekbarStyle.WAVEFORM -> WaveformSeekbar(
                            progress = sampleProgress,
                            onSeekProgress = {},
                            onSeekConfirmed = {},
                            isPlaying = true,
                            enabled = false,
                            accentColor = if (isSelected) SonoraDustyRose else SonoraDustyRose.copy(alpha = 0.85f)
                        )

                        SeekbarStyle.WAVE_LINE -> WaveLineSeekbar(
                            progress = sampleProgress,
                            onSeekProgress = {},
                            onSeekConfirmed = {},
                            isPlaying = true,
                            enabled = false,
                            accentColor = if (isSelected) SonoraDustyRose else SonoraDustyRose.copy(alpha = 0.85f)
                        )

                        SeekbarStyle.CLASSIC -> ClassicSeekbar(
                            progress = sampleProgress,
                            onSeekProgress = {},
                            onSeekConfirmed = {},
                            isPlaying = true,
                            enabled = false,
                            accentColor = if (isSelected) SonoraDustyRose else SonoraDustyRose.copy(alpha = 0.85f)
                        )

                        SeekbarStyle.DOTS -> DotsSeekbar(
                            progress = sampleProgress,
                            onSeekProgress = {},
                            onSeekConfirmed = {},
                            isPlaying = true,
                            enabled = false,
                            accentColor = if (isSelected) SonoraDustyRose else SonoraDustyRose.copy(alpha = 0.85f)
                        )

                        SeekbarStyle.GRADIENT -> GradientSeekbar(
                            progress = sampleProgress,
                            onSeekProgress = {},
                            onSeekConfirmed = {},
                            isPlaying = true,
                            enabled = false
                        )

                        SeekbarStyle.NEON_GLOW -> NeonGlowSeekbar(
                            progress = sampleProgress,
                            onSeekProgress = {},
                            onSeekConfirmed = {},
                            isPlaying = true,
                            enabled = false,
                            accentColor = if (isSelected) SonoraDustyRose else SonoraDustyRose.copy(alpha = 0.85f)
                        )

                        SeekbarStyle.MATERIAL3 -> Material3Seekbar(
                            progress = sampleProgress,
                            onSeekProgress = {},
                            onSeekConfirmed = {},
                            isPlaying = true,
                            enabled = false,
                            accentColor = if (isSelected) SonoraDustyRose else SonoraDustyRose.copy(alpha = 0.85f)
                        )

                        SeekbarStyle.M3_EXPRESSIVE -> M3ExpressiveSeekbar(
                            progress = sampleProgress,
                            onSeekProgress = {},
                            onSeekConfirmed = {},
                            isPlaying = true,
                            enabled = false,
                            accentColor = if (isSelected) SonoraDustyRose else SonoraDustyRose.copy(alpha = 0.85f)
                        )
                    }
                }
            }
        }
    }
}
