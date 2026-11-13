package ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.Song
import com.example.ui.theme.OutfitFontFamily
import ui.components.CircularArtwork
import ui.components.DiscoveryScallopShape
import ui.theme.EditorialColors

/**
 * Compact Editorial Top Greeting Header:
 * Left: Circular profile/featured artwork + full responsive time-based greeting (never ellipsized)
 * Right: Scalloped Music Discovery button + Circular Settings button
 */
@Composable
fun GreetingHeader(
    greeting: String,
    featuredSong: Song?,
    isPlaying: Boolean,
    colors: EditorialColors,
    onAvatarClick: () -> Unit,
    onDiscoveryClick: () -> Unit,
    onSettingsClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp, vertical = 10.dp)
            .testTag("greeting_header")
    ) {
        val compact = maxWidth < 360.dp
        val greetingSize = if (compact) 15.5.sp else 17.5.sp
        val avatarSize = if (compact) 38.dp else 42.dp

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .clip(CircleShape)
                    .clickable { onAvatarClick() }
                    .padding(end = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CircularArtwork(
                    song = featuredSong,
                    fallbackLabel = "S",
                    isPlaying = isPlaying,
                    accentColor = colors.accentPill,
                    shadowElevation = 4.dp,
                    modifier = Modifier
                        .size(avatarSize)
                        .testTag("header_avatar_artwork")
                )

                Spacer(modifier = Modifier.width(if (compact) 9.dp else 12.dp))

                Text(
                    text = greeting,
                    fontFamily = OutfitFontFamily,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = greetingSize,
                    lineHeight = 20.sp,
                    letterSpacing = (-0.2).sp,
                    color = colors.textPrimary,
                    maxLines = 2
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // Music Discovery Scallop Starburst Button (48.dp touch target)
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .clickable { onDiscoveryClick() }
                        .testTag("discovery_icon_button"),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(DiscoveryScallopShape)
                            .background(colors.accentPill),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.MusicNote,
                            contentDescription = "Music discovery & theme styles",
                            tint = colors.onAccentPill,
                            modifier = Modifier.size(19.dp)
                        )
                    }
                }

                // Settings Gear Button (48.dp touch target)
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .clickable { onSettingsClick() }
                        .testTag("header_settings_button"),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(colors.accentPill),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Settings,
                            contentDescription = "Settings",
                            tint = colors.onAccentPill,
                            modifier = Modifier.size(19.dp)
                        )
                    }
                }
            }
        }
    }
}
