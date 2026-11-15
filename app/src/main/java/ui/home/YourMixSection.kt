package ui.home

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.Song
import com.example.ui.theme.OutfitFontFamily
import com.example.ui.theme.PlusJakartaFontFamily
import ui.components.CircularArtwork
import ui.home.animations.HomeAnimationLevel
import ui.home.animations.rememberShouldAnimateHomeMotion
import ui.theme.EditorialColors

data class MixArtistChip(
    val artistName: String,
    val representativeSong: Song
)

/**
 * Editorial Hero Section: Oversized responsive "Your" / "Mix" title (guaranteed never to wrap mid-word),
 * overlapping circular Play & Shuffle buttons, dynamic contributor subtitle, and animated artist label strip.
 */
@Composable
fun YourMixSection(
    contributorsSummary: String,
    artistChips: List<MixArtistChip>,
    isMixPlaying: Boolean,
    hasTracks: Boolean,
    colors: EditorialColors,
    onPlayMix: () -> Unit,
    onShuffleMix: () -> Unit,
    onArtistChipClick: (MixArtistChip) -> Unit,
    animationLevel: HomeAnimationLevel = HomeAnimationLevel.BALANCED,
    screenVisible: Boolean = true,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current
    val motionEnabled = rememberShouldAnimateHomeMotion(level = animationLevel, screenVisible = screenVisible)

    val playClusterTargetScale = if (motionEnabled && isMixPlaying && animationLevel.amplitudeScale > 0f) {
        val amp = (0.04f * animationLevel.amplitudeScale).coerceIn(0.015f, 0.06f)
        val trans = rememberInfiniteTransition(label = "your_mix_play_breathe")
        val s by trans.animateFloat(
            initialValue = 1.0f,
            targetValue = 1.0f + amp,
            animationSpec = infiniteRepeatable(
                animation = tween(1500, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "your_mix_play_scale"
        )
        s
    } else {
        1.0f
    }
    val settledPlayScale by animateFloatAsState(
        targetValue = playClusterTargetScale,
        animationSpec = spring(),
        label = "your_mix_play_settle"
    )
    val effectivePlayScale = if (motionEnabled && isMixPlaying) playClusterTargetScale else settledPlayScale

    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag("your_mix_section")
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 22.dp, vertical = 6.dp)
        ) {
            val headingFontSize = when {
                maxWidth < 310.dp -> 38.sp
                maxWidth < 360.dp -> 44.sp
                maxWidth < 410.dp -> 48.sp
                else -> 54.sp
            }
            val headingLineHeight = when {
                maxWidth < 310.dp -> 38.sp
                maxWidth < 360.dp -> 44.sp
                maxWidth < 410.dp -> 48.sp
                else -> 52.sp
            }
            val playClusterSize = if (maxWidth < 340.dp) 92.dp else 102.dp
            val playButtonSize = if (maxWidth < 340.dp) 78.dp else 88.dp

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Left: Explicit two-line editorial headline so "Your" never splits mid-word
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 10.dp)
                        .testTag("your_mix_heading")
                ) {
                    Text(
                        text = "Your",
                        fontFamily = OutfitFontFamily,
                        fontWeight = FontWeight.Black,
                        fontSize = headingFontSize,
                        lineHeight = headingLineHeight,
                        letterSpacing = (-1.5).sp,
                        softWrap = false,
                        maxLines = 1,
                        color = colors.textPrimary
                    )
                    Text(
                        text = "Mix",
                        fontFamily = OutfitFontFamily,
                        fontWeight = FontWeight.Black,
                        fontSize = headingFontSize,
                        lineHeight = headingLineHeight,
                        letterSpacing = (-1.5).sp,
                        softWrap = false,
                        maxLines = 1,
                        color = colors.textPrimary
                    )
                }

                // Right: Prominent circular Play button with overlapping Shuffle badge
                Box(
                    modifier = Modifier
                        .size(playClusterSize)
                        .padding(4.dp)
                        .graphicsLayer {
                            scaleX = effectivePlayScale
                            scaleY = effectivePlayScale
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Surface(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onPlayMix()
                        },
                        enabled = hasTracks,
                        shape = CircleShape,
                        color = colors.accentPill,
                        shadowElevation = 8.dp,
                        modifier = Modifier
                            .size(playButtonSize)
                            .align(Alignment.CenterStart)
                            .testTag("your_mix_play_button")
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = if (isMixPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                                contentDescription = if (isMixPlaying) "Pause Your Mix" else "Play Your Mix",
                                tint = colors.onAccentPill,
                                modifier = Modifier.size(34.dp)
                            )
                        }
                    }

                    // Overlapping Shuffle badge button (min 48.dp touch wrapper)
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .offset(x = 2.dp, y = 2.dp)
                            .size(48.dp)
                            .clip(CircleShape)
                            .clickable(enabled = hasTracks) {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onShuffleMix()
                            }
                            .testTag("your_mix_shuffle_button"),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .shadow(6.dp, CircleShape, ambientColor = colors.shadowColor)
                                .clip(CircleShape)
                                .background(colors.accentStrong)
                                .border(2.5.dp, colors.background, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Shuffle,
                                contentDescription = "Shuffle Your Mix",
                                tint = colors.onAccentStrong,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
        }

        // Dynamic artists contributor line
        Text(
            text = contributorsSummary,
            fontFamily = PlusJakartaFontFamily,
            fontWeight = FontWeight.Normal,
            fontSize = 14.sp,
            color = colors.textSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 22.dp)
                .padding(top = 6.dp)
        )

        // Horizontal artist / music labels strip
        if (artistChips.isNotEmpty()) {
            Spacer(modifier = Modifier.height(12.dp))
            LazyRow(
                contentPadding = PaddingValues(horizontal = 22.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.testTag("your_mix_artists_strip")
            ) {
                itemsIndexed(artistChips, key = { _, it -> it.artistName }) { idx, chip ->
                    Surface(
                        onClick = { onArtistChipClick(chip) },
                        shape = RoundedCornerShape(24.dp),
                        color = colors.surface.copy(alpha = 0.85f),
                        border = androidx.compose.foundation.BorderStroke(0.75.dp, colors.cardBorder),
                        modifier = Modifier.height(44.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(start = 5.dp, end = 14.dp, top = 4.dp, bottom = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CircularArtwork(
                                song = chip.representativeSong,
                                fallbackLabel = chip.artistName,
                                accentColor = colors.accentPill,
                                shadowElevation = 2.dp,
                                animationLevel = animationLevel,
                                screenVisible = screenVisible,
                                enableFloating = false,
                                floatingPhaseMs = idx * 320,
                                modifier = Modifier.size(34.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = chip.artistName,
                                fontFamily = PlusJakartaFontFamily,
                                fontWeight = FontWeight.Medium,
                                fontSize = 13.sp,
                                color = colors.textPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.widthIn(max = 140.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
