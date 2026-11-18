package ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
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
import ui.motion.LocalEffectiveMotionState
import ui.motion.LocalTrackTransitionDirection
import ui.motion.SonoraTransitions
import ui.motion.rememberMotionAwareHaptic
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
    val motionState = LocalEffectiveMotionState.current
    val performHaptic = rememberMotionAwareHaptic()
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

    val playInteractionSource = remember { MutableInteractionSource() }
    val isPlayPressed by playInteractionSource.collectIsPressedAsState()
    val playButtonScale by animateFloatAsState(
        targetValue = when {
            !motionState.animationsEnabled -> 1f
            isPlayPressed -> 0.91f
            playbackInfo.isPlaying -> 1.03f
            else -> 1f
        },
        animationSpec = if (motionState.animationsEnabled) {
            spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium)
        } else {
            snap()
        },
        label = "playButtonScale"
    )

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = horizontalArrangement,
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (appearance.controlArrangement != ControlArrangement.MINIMAL_DECK) {
            IconButton(
                onClick = {
                    performHaptic(HapticFeedbackType.TextHandleMove)
                    onToggleShuffle()
                },
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
            onClick = {
                performHaptic(HapticFeedbackType.TextHandleMove)
                onPlayPrevious()
            },
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
            onClick = {
                performHaptic(HapticFeedbackType.TextHandleMove)
                onTogglePlayPause()
            },
            interactionSource = playInteractionSource,
            modifier = Modifier
                .size(width = playWidth, height = primaryButtonSize)
                .graphicsLayer {
                    scaleX = playButtonScale
                    scaleY = playButtonScale
                }
                .testTag("play_pause_button"),
            shape = buttonShape,
            color = accentColor,
            shadowElevation = 8.dp
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                AnimatedContent(
                    targetState = playbackInfo.isPlaying,
                    transitionSpec = {
                        if (!motionState.animationsEnabled) {
                            fadeIn(snap()) togetherWith fadeOut(snap())
                        } else {
                            (scaleIn(tween(180), initialScale = 0.7f) + fadeIn(tween(160))) togetherWith
                                (scaleOut(tween(140), targetScale = 0.7f) + fadeOut(tween(140)))
                        }
                    },
                    label = "playPauseIconTransition"
                ) { playing ->
                    Icon(
                        imageVector = if (playing) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (playing) "Pause" else "Play",
                        tint = onAccentColor,
                        modifier = Modifier.size(34.dp)
                    )
                }
            }
        }

        IconButton(
            onClick = {
                performHaptic(HapticFeedbackType.TextHandleMove)
                onPlayNext()
            },
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
                onClick = {
                    performHaptic(HapticFeedbackType.TextHandleMove)
                    onCycleRepeat()
                },
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
    val motionState = LocalEffectiveMotionState.current
    val trackDirection = LocalTrackTransitionDirection.current
    val performHaptic = rememberMotionAwareHaptic()
    val scale = appearance.typographyScale.coerceIn(0.85f, 1.25f)

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        AnimatedContent(
            targetState = song?.id,
            transitionSpec = { SonoraTransitions.metadataTransition(trackDirection, motionState) },
            modifier = Modifier.weight(1f),
            label = "trackMetadataTransition"
        ) { _ ->
            Column(modifier = Modifier.fillMaxWidth()) {
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
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            if (onToggleFavorite != null) {
                IconButton(
                    onClick = {
                        performHaptic(HapticFeedbackType.TextHandleMove)
                        onToggleFavorite()
                    },
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
