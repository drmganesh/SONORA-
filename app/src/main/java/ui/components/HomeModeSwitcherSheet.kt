package ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.rounded.WifiOff
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.OutfitFontFamily
import com.example.ui.theme.PlusJakartaFontFamily
import domain.home.HomeMusicMode
import ui.theme.EditorialColors

/**
 * Animated three-mode Home selector sheet opened from the Home header icon.
 * Lets the listener switch seamlessly between Offline Music, Online Music, and Online Video
 * without stopping active music or video playback unnecessarily.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeModeSwitcherSheet(
    currentMode: HomeMusicMode,
    isNetworkOnline: Boolean,
    localTrackCount: Int,
    colors: EditorialColors,
    reduceAnimations: Boolean = false,
    onSelectMode: (HomeMusicMode) -> Unit,
    onOpenThemesAndShuffle: () -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val haptic = LocalHapticFeedback.current

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = colors.surface,
        contentColor = colors.textPrimary,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp)
                .testTag("home_mode_switcher_sheet")
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(colors.accentPill),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = when (currentMode) {
                            HomeMusicMode.OFFLINE -> Icons.Rounded.MusicNote
                            HomeMusicMode.ONLINE -> Icons.Rounded.Headphones
                            HomeMusicMode.ONLINE_VIDEO -> Icons.Rounded.PlayCircle
                        },
                        contentDescription = null,
                        tint = colors.onAccentPill,
                        modifier = Modifier.size(22.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "SONORA Experience",
                        fontFamily = OutfitFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp,
                        color = colors.textPrimary
                    )
                    Text(
                        text = "Switch Home between Offline Music, Online Music & Online Video",
                        fontFamily = PlusJakartaFontFamily,
                        fontSize = 12.sp,
                        color = colors.textSecondary
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Animated three-option segmented pill bar for instant 1-tap toggle
            Surface(
                shape = RoundedCornerShape(22.dp),
                color = colors.surfaceTinted,
                border = BorderStroke(0.75.dp, colors.cardBorder),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("home_mode_segmented_selector")
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(5.dp),
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    HomeMusicMode.entries.forEach { mode ->
                        val selected = currentMode == mode
                        val segmentColor by animateColorAsState(
                            targetValue = if (selected) colors.accentStrong else colors.surfaceTinted,
                            animationSpec = if (reduceAnimations) snap() else tween(260),
                            label = "segment_bg_${mode.id}"
                        )
                        val segmentTextColor by animateColorAsState(
                            targetValue = if (selected) colors.onAccentStrong else colors.textSecondary,
                            animationSpec = if (reduceAnimations) snap() else tween(260),
                            label = "segment_fg_${mode.id}"
                        )
                        val segmentScale by animateFloatAsState(
                            targetValue = if (selected) 1f else 0.97f,
                            animationSpec = if (reduceAnimations) snap() else spring(dampingRatio = 0.7f),
                            label = "segment_scale_${mode.id}"
                        )
                        val modeIcon = when (mode) {
                            HomeMusicMode.OFFLINE -> Icons.Rounded.Storage
                            HomeMusicMode.ONLINE -> Icons.Rounded.Headphones
                            HomeMusicMode.ONLINE_VIDEO -> Icons.Rounded.PlayCircle
                        }
                        val shortSegmentLabel = when (mode) {
                            HomeMusicMode.OFFLINE -> "Offline"
                            HomeMusicMode.ONLINE -> "Music"
                            HomeMusicMode.ONLINE_VIDEO -> "Video"
                        }

                        Surface(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                onSelectMode(mode)
                                onDismiss()
                            },
                            shape = RoundedCornerShape(18.dp),
                            color = segmentColor,
                            shadowElevation = if (selected) 4.dp else 0.dp,
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .graphicsLayer {
                                    scaleX = segmentScale
                                    scaleY = segmentScale
                                }
                                .testTag("home_mode_segment_${mode.id}")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    imageVector = modeIcon,
                                    contentDescription = mode.title,
                                    tint = segmentTextColor,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(5.dp))
                                Text(
                                    text = shortSegmentLabel,
                                    fontFamily = PlusJakartaFontFamily,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp,
                                    color = segmentTextColor,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Detailed three-mode option cards
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                ModeOptionDetailCard(
                    title = "Offline Music",
                    subtitle = "$localTrackCount local tracks • Albums, Daily Mixes & 15-Band Audio Studio",
                    badgeText = "OFFLINE",
                    icon = Icons.Rounded.Storage,
                    isSelected = currentMode == HomeMusicMode.OFFLINE,
                    colors = colors,
                    reduceAnimations = reduceAnimations,
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onSelectMode(HomeMusicMode.OFFLINE)
                        onDismiss()
                    },
                    modifier = Modifier.testTag("mode_option_offline")
                )

                ModeOptionDetailCard(
                    title = "Online Music",
                    subtitle = if (isNetworkOnline) {
                        "India charts, Hindi & regional songs, playlists & recommendations"
                    } else {
                        "Currently offline • Switch anytime; cached music stays ready"
                    },
                    badgeText = "MUSIC",
                    icon = if (isNetworkOnline) Icons.Rounded.Headphones else Icons.Rounded.WifiOff,
                    isSelected = currentMode == HomeMusicMode.ONLINE,
                    colors = colors,
                    reduceAnimations = reduceAnimations,
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onSelectMode(HomeMusicMode.ONLINE)
                        onDismiss()
                    },
                    modifier = Modifier.testTag("mode_option_online")
                )

                ModeOptionDetailCard(
                    title = "Online Video",
                    subtitle = if (isNetworkOnline) {
                        "Trending & regional Indian videos, HD playback, channels & Watch Later"
                    } else {
                        "Currently offline • Browse Watch Later & saved Video Library"
                    },
                    badgeText = "VIDEO",
                    icon = if (isNetworkOnline) Icons.Rounded.PlayCircle else Icons.Rounded.WifiOff,
                    isSelected = currentMode == HomeMusicMode.ONLINE_VIDEO,
                    colors = colors,
                    reduceAnimations = reduceAnimations,
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onSelectMode(HomeMusicMode.ONLINE_VIDEO)
                        onDismiss()
                    },
                    modifier = Modifier.testTag("mode_option_online_video")
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "Switching Home modes never stops your active music or video playback.",
                fontFamily = PlusJakartaFontFamily,
                fontSize = 11.5.sp,
                color = colors.textMuted,
                modifier = Modifier.padding(horizontal = 4.dp)
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Secondary link preserving Discovery & Editorial Themes customization sheet
            Surface(
                onClick = {
                    onDismiss()
                    onOpenThemesAndShuffle()
                },
                shape = RoundedCornerShape(18.dp),
                color = colors.surfaceTinted,
                border = BorderStroke(0.75.dp, colors.cardBorder),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .testTag("open_theme_customization_button")
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Rounded.Palette,
                            contentDescription = null,
                            tint = colors.accentStrong,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Home Themes & Discovery Shuffle",
                            fontFamily = PlusJakartaFontFamily,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp,
                            color = colors.textPrimary
                        )
                    }
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
                        contentDescription = "Open themes",
                        tint = colors.textMuted,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun ModeOptionDetailCard(
    title: String,
    subtitle: String,
    badgeText: String,
    icon: ImageVector,
    isSelected: Boolean,
    colors: EditorialColors,
    reduceAnimations: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val bgColor by animateColorAsState(
        targetValue = if (isSelected) colors.accentPill.copy(alpha = 0.65f) else colors.surfaceTinted,
        animationSpec = if (reduceAnimations) snap() else tween(260),
        label = "mode_card_bg"
    )
    val borderColor by animateColorAsState(
        targetValue = if (isSelected) colors.accentStrong else colors.cardBorder,
        animationSpec = if (reduceAnimations) snap() else tween(260),
        label = "mode_card_border"
    )

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(20.dp),
        color = bgColor,
        border = BorderStroke(if (isSelected) 1.5.dp else 0.75.dp, borderColor),
        modifier = modifier
            .fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(if (isSelected) colors.accentStrong else colors.surface),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (isSelected) colors.onAccentStrong else colors.accentStrong,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = title,
                        fontFamily = OutfitFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = colors.textPrimary
                    )
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = if (isSelected) colors.accentStrong else colors.surface,
                        border = BorderStroke(0.5.dp, colors.cardBorder)
                    ) {
                        Text(
                            text = badgeText,
                            fontFamily = PlusJakartaFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 9.sp,
                            color = if (isSelected) colors.onAccentStrong else colors.textMuted,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    fontFamily = PlusJakartaFontFamily,
                    fontSize = 12.sp,
                    color = colors.textSecondary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (isSelected) {
                Spacer(modifier = Modifier.width(8.dp))
                Icon(
                    imageVector = Icons.Rounded.CheckCircle,
                    contentDescription = "$title active",
                    tint = colors.accentStrong,
                    modifier = Modifier.size(22.dp)
                )
            }
        }
    }
}
