package ui.components

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Lyrics
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.ButtonShapeOption
import com.example.model.ControlArrangement
import com.example.model.PlaybackInfo
import com.example.model.PlayerAppearanceConfig
import com.example.model.RepeatMode
import com.example.model.ShuffleMode
import com.example.model.Song
import com.example.ui.theme.OutfitFontFamily
import com.example.ui.theme.PlusJakartaFontFamily
import ui.theme.LocalEditorialColors

/**
 * Shared customizable transport deck honoring ControlArrangement, ButtonShapeOption,
 * and dynamic accent color across Player Styles.
 */
@Composable
fun PlaybackControls(
    playbackInfo: PlaybackInfo,
    onTogglePlayPause: () -> Unit,
    onPlayNext: () -> Unit,
    onPlayPrevious: () -> Unit,
    onToggleShuffle: () -> Unit,
    onCycleRepeat: () -> Unit,
    modifier: Modifier = Modifier,
    appearance: PlayerAppearanceConfig = PlayerAppearanceConfig.DEFAULT,
    accentColor: Color = LocalEditorialColors.current.accentStrong,
    onAccentColor: Color = LocalEditorialColors.current.onAccentStrong,
    iconTint: Color = LocalEditorialColors.current.textPrimary,
    mutedTint: Color = LocalEditorialColors.current.textMuted,
    primaryButtonSize: Dp = 72.dp
) {
    val buttonShape: Shape = when (appearance.buttonShape) {
        ButtonShapeOption.CIRCLE -> CircleShape
        ButtonShapeOption.SQUIRCLE -> RoundedCornerShape(22.dp)
        ButtonShapeOption.PILL -> RoundedCornerShape(36.dp)
    }
    val playWidth = if (appearance.buttonShape == ButtonShapeOption.PILL) primaryButtonSize * 1.32f else primaryButtonSize

    val horizontalArrangement = when (appearance.controlArrangement) {
        ControlArrangement.COMPACT_CENTER -> Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally)
        ControlArrangement.SPLIT_WIDE -> Arrangement.SpaceBetween
        ControlArrangement.BALANCED, ControlArrangement.MINIMAL_DECK -> Arrangement.SpaceEvenly
    }

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = horizontalArrangement,
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (appearance.controlArrangement != ControlArrangement.MINIMAL_DECK) {
            IconButton(
                onClick = onToggleShuffle,
                modifier = Modifier
                    .size(48.dp)
                    .testTag("shuffle_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Shuffle,
                    contentDescription = "Shuffle",
                    tint = if (playbackInfo.shuffleMode == ShuffleMode.ON) accentColor else mutedTint,
                    modifier = Modifier.size(22.dp)
                )
            }
        }

        IconButton(
            onClick = onPlayPrevious,
            modifier = Modifier
                .size(48.dp)
                .testTag("previous_button")
        ) {
            Icon(
                imageVector = Icons.Default.SkipPrevious,
                contentDescription = "Previous",
                tint = iconTint,
                modifier = Modifier.size(32.dp)
            )
        }

        Surface(
            onClick = onTogglePlayPause,
            modifier = Modifier
                .size(width = playWidth, height = primaryButtonSize)
                .testTag("play_pause_button"),
            shape = buttonShape,
            color = accentColor,
            shadowElevation = 8.dp
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = if (playbackInfo.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (playbackInfo.isPlaying) "Pause" else "Play",
                    tint = onAccentColor,
                    modifier = Modifier.size(34.dp)
                )
            }
        }

        IconButton(
            onClick = onPlayNext,
            modifier = Modifier
                .size(48.dp)
                .testTag("next_button")
        ) {
            Icon(
                imageVector = Icons.Default.SkipNext,
                contentDescription = "Next",
                tint = iconTint,
                modifier = Modifier.size(32.dp)
            )
        }

        if (appearance.controlArrangement != ControlArrangement.MINIMAL_DECK) {
            IconButton(
                onClick = onCycleRepeat,
                modifier = Modifier
                    .size(48.dp)
                    .testTag("repeat_button")
            ) {
                val icon = if (playbackInfo.repeatMode == RepeatMode.ONE) Icons.Default.RepeatOne else Icons.Default.Repeat
                val tint = if (playbackInfo.repeatMode != RepeatMode.OFF) accentColor else mutedTint
                Icon(
                    imageVector = icon,
                    contentDescription = "Repeat",
                    tint = tint,
                    modifier = Modifier.size(22.dp)
                )
            }
        }
    }
}

/**
 * Shared Track Metadata + Audio Quality & Action Row honoring visibility flags and typographyScale.
 */
@Composable
fun TrackMetadataBar(
    song: Song?,
    appearance: PlayerAppearanceConfig,
    onToggleFavorite: (() -> Unit)? = null,
    onOpenLyrics: (() -> Unit)? = null,
    onOpenQueue: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    titleColor: Color = LocalEditorialColors.current.textPrimary,
    subtitleColor: Color = LocalEditorialColors.current.textSecondary,
    accentColor: Color = LocalEditorialColors.current.accentStrong
) {
    val colors = LocalEditorialColors.current
    val scale = appearance.typographyScale.coerceIn(0.85f, 1.25f)

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = song?.title ?: "No Song Selected",
                fontFamily = OutfitFontFamily,
                color = titleColor,
                fontSize = (22f * scale).sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            val artistAlbum = if (appearance.showAlbumMetadata && !song?.album.isNullOrBlank()) {
                "${song?.artist ?: "Unknown Artist"} • ${song?.album}"
            } else {
                song?.artist ?: "Unknown Artist"
            }
            Text(
                text = artistAlbum,
                fontFamily = PlusJakartaFontFamily,
                color = subtitleColor,
                fontSize = (13f * scale).sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (appearance.showAudioFormatMetadata && song != null) {
                Spacer(modifier = Modifier.height(4.dp))
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = colors.accentPill.copy(alpha = 0.72f)
                ) {
                    Text(
                        text = song.audioFormatInfo,
                        fontFamily = PlusJakartaFontFamily,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = colors.onAccentPill,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                    )
                }
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            if (onToggleFavorite != null) {
                IconButton(
                    onClick = onToggleFavorite,
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("favorite_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Favorite,
                        contentDescription = "Favorite",
                        tint = if (song?.isFavorite == true) accentColor else colors.textMuted,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
            if (appearance.showLyricsShortcut && onOpenLyrics != null) {
                IconButton(
                    onClick = onOpenLyrics,
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("lyrics_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Lyrics,
                        contentDescription = "Lyrics",
                        tint = accentColor,
                        modifier = Modifier.size(21.dp)
                    )
                }
            }
            if (appearance.showQueueShortcut && onOpenQueue != null) {
                IconButton(
                    onClick = onOpenQueue,
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("metadata_queue_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.QueueMusic,
                        contentDescription = "Queue",
                        tint = subtitleColor,
                        modifier = Modifier.size(21.dp)
                    )
                }
            }
        }
    }
}
