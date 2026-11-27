package ui.player

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.SpatialAudio
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.OutfitFontFamily
import com.example.ui.theme.PlusJakartaFontFamily
import ui.theme.LocalEditorialColors

/**
 * Audio Shortcuts Section under Now Playing controls.
 * Adapts between horizontal icon+text cards on roomy widths and vertical stacked icon-over-text
 * cards on narrow screens or enlarged font scales so labels stay legible without severe clipping.
 */
@Composable
fun AudioShortcutsRow(
    onOpenAudioEffects: () -> Unit,
    onOpenEqualizer: (() -> Unit)? = null,
    onOpenLyrics: () -> Unit = {},
    onOpenSleepTimer: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val fontScale = LocalDensity.current.fontScale
    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 4.dp)
    ) {
        val cardCount = if (onOpenEqualizer != null) 4 else 3
        val perCardWidth = (maxWidth - (6.dp * (cardCount - 1))) / cardCount
        val useStackedLayout = perCardWidth < 94.dp || fontScale > 1.1f
        val horizontalGap = if (maxWidth < 340.dp) 6.dp else 8.dp

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(horizontalGap)
        ) {
            if (onOpenEqualizer != null) {
                AudioShortcutCard(
                    title = "Equalizer",
                    subtitle = "15-Band EQ",
                    icon = Icons.Default.GraphicEq,
                    isActive = true,
                    stacked = useStackedLayout,
                    onClick = onOpenEqualizer,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("shortcut_equalizer_button")
                )
            }

            AudioShortcutCard(
                title = "Studio FX",
                subtitle = "8D & Bass",
                icon = Icons.Default.SpatialAudio,
                isActive = onOpenEqualizer == null,
                stacked = useStackedLayout,
                onClick = onOpenAudioEffects,
                modifier = Modifier
                    .weight(1f)
                    .testTag("shortcut_audio_studio_button")
            )

            AudioShortcutCard(
                title = "Lyrics",
                subtitle = "Synced",
                icon = Icons.Default.Description,
                isActive = false,
                stacked = useStackedLayout,
                onClick = onOpenLyrics,
                modifier = Modifier
                    .weight(1f)
                    .testTag("shortcut_lyrics_button")
            )

            AudioShortcutCard(
                title = "Timer",
                subtitle = "Sleep",
                icon = Icons.Default.Schedule,
                isActive = false,
                stacked = useStackedLayout,
                onClick = onOpenSleepTimer,
                modifier = Modifier
                    .weight(1f)
                    .testTag("shortcut_timer_button")
            )
        }
    }
}

@Composable
private fun AudioShortcutCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    isActive: Boolean,
    stacked: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalEditorialColors.current

    Surface(
        modifier = modifier
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick),
        color = if (isActive) colors.accentPill else colors.surface,
        border = BorderStroke(
            width = 1.dp,
            color = if (isActive) colors.accentStrong.copy(alpha = 0.4f) else colors.cardBorder
        ),
        shape = RoundedCornerShape(16.dp),
        tonalElevation = if (isActive) 2.dp else 0.dp
    ) {
        if (stacked) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 6.dp, vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (isActive) colors.onAccentPill else colors.accentStrong,
                    modifier = Modifier.size(17.dp)
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = title,
                    fontFamily = OutfitFontFamily,
                    color = if (isActive) colors.onAccentPill else colors.textPrimary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = subtitle,
                    fontFamily = PlusJakartaFontFamily,
                    color = if (isActive) colors.onAccentPill.copy(alpha = 0.78f) else colors.textSecondary,
                    fontSize = 9.sp,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center
                )
            }
        } else {
            Row(
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (isActive) colors.onAccentPill else colors.accentStrong,
                    modifier = Modifier.size(16.dp)
                )

                Spacer(modifier = Modifier.width(6.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        fontFamily = OutfitFontFamily,
                        color = if (isActive) colors.onAccentPill else colors.textPrimary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = subtitle,
                        fontFamily = PlusJakartaFontFamily,
                        color = if (isActive) colors.onAccentPill.copy(alpha = 0.78f) else colors.textSecondary,
                        fontSize = 9.sp,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}
