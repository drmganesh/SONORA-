package ui.settings

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.OutfitFontFamily
import com.example.ui.theme.PlusJakartaFontFamily
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
import ui.theme.LocalEditorialColors
import ui.theme.LocalReduceAnimations

@Composable
fun SeekbarStyleCard(
    style: SeekbarStyle,
    isSelected: Boolean,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalEditorialColors.current
    val reduceAnimations = LocalReduceAnimations.current

    val sampleProgress: Float = if (reduceAnimations) {
        0.58f
    } else {
        val infiniteTransition = rememberInfiniteTransition(label = "sampleProgress_${style.id}")
        val animated by infiniteTransition.animateFloat(
            initialValue = 0.35f,
            targetValue = 0.75f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 4000, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "sampleProgress"
        )
        animated
    }

    Surface(
        shape = RoundedCornerShape(22.dp),
        color = if (isSelected) colors.accentPill.copy(alpha = 0.35f) else colors.surface,
        tonalElevation = if (isSelected) 4.dp else 1.dp,
        modifier = modifier
            .fillMaxWidth()
            .border(
                width = if (isSelected) 1.5.dp else 0.75.dp,
                color = if (isSelected) colors.accentStrong else colors.cardBorder,
                shape = RoundedCornerShape(22.dp)
            )
            .clip(RoundedCornerShape(22.dp))
            .clickable { onSelect() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp)
        ) {
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
                        fontFamily = OutfitFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 17.sp,
                        color = if (isSelected) colors.accentStrong else colors.textPrimary
                    )
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = if (isSelected) colors.accentPill else colors.surfaceTinted
                    ) {
                        Text(
                            text = style.subtitle,
                            fontFamily = PlusJakartaFontFamily,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 10.sp,
                            color = if (isSelected) colors.onAccentPill else colors.textSecondary,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                }

                if (isSelected) {
                    Icon(
                        imageVector = Icons.Rounded.CheckCircle,
                        contentDescription = "Active Style",
                        tint = colors.accentStrong,
                        modifier = Modifier.size(22.dp)
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .size(20.dp)
                            .border(1.5.dp, colors.textMuted.copy(alpha = 0.5f), CircleShape)
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = style.description,
                fontFamily = PlusJakartaFontFamily,
                fontSize = 12.sp,
                lineHeight = 17.sp,
                color = colors.textSecondary
            )

            Spacer(modifier = Modifier.height(14.dp))

            Surface(
                shape = RoundedCornerShape(14.dp),
                color = colors.surfaceTinted,
                modifier = Modifier
                    .fillMaxWidth()
                    .border(0.5.dp, colors.cardBorder, RoundedCornerShape(14.dp))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 10.dp)
                ) {
                    val accent = colors.accentStrong
                    when (style) {
                        SeekbarStyle.BLOCKS -> BlocksSeekbar(sampleProgress, {}, {}, isPlaying = true, enabled = false, accentColor = accent)
                        SeekbarStyle.WAVEFORM -> WaveformSeekbar(sampleProgress, {}, {}, isPlaying = true, enabled = false, accentColor = accent)
                        SeekbarStyle.WAVE_LINE -> WaveLineSeekbar(sampleProgress, {}, {}, isPlaying = true, enabled = false, accentColor = accent)
                        SeekbarStyle.CLASSIC -> ClassicSeekbar(sampleProgress, {}, {}, isPlaying = true, enabled = false, accentColor = accent)
                        SeekbarStyle.DOTS -> DotsSeekbar(sampleProgress, {}, {}, isPlaying = true, enabled = false, accentColor = accent)
                        SeekbarStyle.GRADIENT -> GradientSeekbar(sampleProgress, {}, {}, isPlaying = true, enabled = false)
                        SeekbarStyle.NEON_GLOW -> NeonGlowSeekbar(sampleProgress, {}, {}, isPlaying = true, enabled = false, accentColor = accent)
                        SeekbarStyle.MATERIAL3 -> Material3Seekbar(sampleProgress, {}, {}, isPlaying = true, enabled = false, accentColor = accent)
                        SeekbarStyle.M3_EXPRESSIVE -> M3ExpressiveSeekbar(sampleProgress, {}, {}, isPlaying = true, enabled = false, accentColor = accent)
                    }
                }
            }
        }
    }
}
