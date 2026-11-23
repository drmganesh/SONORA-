package ui.home

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.UserProfile
import com.example.ui.theme.OutfitFontFamily
import com.example.ui.theme.PlusJakartaFontFamily
import domain.home.HomeMusicMode
import kotlinx.coroutines.delay
import ui.components.DiscoveryScallopShape
import ui.components.UserProfileAvatar
import ui.theme.EditorialColors

/**
 * Compact Editorial Top Greeting Header:
 * Left: Dedicated circular User Profile Avatar (48-52dp) + responsive greeting + active mode subtitle
 * Right: Scalloped Music Mode Switcher button (Offline Music / Online Music / Online Video) + Settings button
 */
@Composable
fun GreetingHeader(
    greeting: String,
    userProfile: UserProfile,
    colors: EditorialColors,
    onAvatarClick: () -> Unit,
    onDiscoveryClick: () -> Unit,
    onSettingsClick: () -> Unit,
    activeMode: HomeMusicMode = HomeMusicMode.OFFLINE,
    reduceAnimations: Boolean = false,
    unreadVideoNotificationsCount: Int = 0,
    onVideoNotificationsClick: (() -> Unit)? = null,
    showRestoreNowPlayingIcon: Boolean = false,
    onRestoreNowPlayingClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current
    var previousMode by remember { mutableStateOf(activeMode) }
    var iconBurstScale by remember { mutableFloatStateOf(1f) }
    var iconTiltDegrees by remember { mutableFloatStateOf(0f) }

    var previousUnreadCount by remember { mutableIntStateOf(unreadVideoNotificationsCount) }
    var bellPulseScale by remember { mutableFloatStateOf(1f) }
    var bellRingAngle by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(activeMode, reduceAnimations) {
        if (activeMode != previousMode) {
            val movingForward = activeMode.ordinal > previousMode.ordinal
            previousMode = activeMode
            if (!reduceAnimations) {
                iconBurstScale = 1.16f
                iconTiltDegrees = if (movingForward) 14f else -14f
                delay(180)
                iconBurstScale = 1f
                iconTiltDegrees = 0f
            }
        }
    }

    LaunchedEffect(unreadVideoNotificationsCount, activeMode, reduceAnimations) {
        val arrived = unreadVideoNotificationsCount > previousUnreadCount
        previousUnreadCount = unreadVideoNotificationsCount
        if (arrived && activeMode == HomeMusicMode.ONLINE_VIDEO && !reduceAnimations) {
            bellPulseScale = 1.15f
            bellRingAngle = 14f
            delay(110)
            bellRingAngle = -12f
            delay(110)
            bellRingAngle = 8f
            delay(95)
            bellRingAngle = 0f
            bellPulseScale = 1f
        }
    }

    val animatedBellScale by animateFloatAsState(
        targetValue = bellPulseScale,
        animationSpec = if (reduceAnimations) snap() else spring(dampingRatio = 0.5f, stiffness = Spring.StiffnessMediumLow),
        label = "video_bell_scale"
    )
    val animatedBellAngle by animateFloatAsState(
        targetValue = bellRingAngle,
        animationSpec = if (reduceAnimations) snap() else spring(dampingRatio = 0.45f, stiffness = Spring.StiffnessMedium),
        label = "video_bell_ring"
    )

    val animatedScale by animateFloatAsState(
        targetValue = iconBurstScale,
        animationSpec = if (reduceAnimations) snap() else spring(dampingRatio = 0.5f, stiffness = Spring.StiffnessMediumLow),
        label = "mode_icon_scale"
    )
    val animatedTilt by animateFloatAsState(
        targetValue = iconTiltDegrees,
        animationSpec = if (reduceAnimations) snap() else spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessMedium),
        label = "mode_icon_tilt"
    )
    val isOnlineActive = activeMode != HomeMusicMode.OFFLINE
    val modeButtonContainer by animateColorAsState(
        targetValue = when (activeMode) {
            HomeMusicMode.OFFLINE -> colors.accentPill
            HomeMusicMode.ONLINE -> colors.accentStrong
            HomeMusicMode.ONLINE_VIDEO -> colors.accentStrong
        },
        animationSpec = if (reduceAnimations) snap() else tween(360),
        label = "mode_button_bg"
    )
    val modeButtonIconTint by animateColorAsState(
        targetValue = if (isOnlineActive) colors.onAccentStrong else colors.onAccentPill,
        animationSpec = if (reduceAnimations) snap() else tween(360),
        label = "mode_button_tint"
    )

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp, vertical = 10.dp)
            .testTag("greeting_header")
    ) {
        val compact = maxWidth < 360.dp
        val greetingSize = if (compact) 15.5.sp else 17.5.sp
        val avatarSize = if (compact) 48.dp else 52.dp

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                UserProfileAvatar(
                    userProfile = userProfile,
                    colors = colors,
                    size = avatarSize,
                    borderWidth = 1.5.dp,
                    shadowElevation = 6.dp,
                    reduceAnimations = reduceAnimations,
                    onClick = onAvatarClick,
                    modifier = Modifier.testTag("header_profile_avatar")
                )

                Spacer(modifier = Modifier.width(if (compact) 10.dp else 12.dp))

                Column {
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
                    AnimatedContent(
                        targetState = activeMode,
                        transitionSpec = {
                            if (reduceAnimations) {
                                fadeIn(snap()) togetherWith fadeOut(snap())
                            } else {
                                fadeIn(tween(220)) togetherWith fadeOut(tween(160))
                            }
                        },
                        label = "header_mode_indicator_anim"
                    ) { mode ->
                        val highlighted = mode != HomeMusicMode.OFFLINE
                        val badgeIcon = when (mode) {
                            HomeMusicMode.OFFLINE -> Icons.Rounded.Storage
                            HomeMusicMode.ONLINE -> Icons.Rounded.Headphones
                            HomeMusicMode.ONLINE_VIDEO -> Icons.Rounded.PlayCircle
                        }
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = if (highlighted) {
                                colors.accentStrong.copy(alpha = 0.14f)
                            } else {
                                colors.surfaceTinted
                            },
                            border = BorderStroke(
                                0.5.dp,
                                if (highlighted) colors.accentStrong.copy(alpha = 0.45f) else colors.cardBorder
                            ),
                            modifier = Modifier
                                .padding(top = 3.dp)
                                .testTag("header_mode_indicator")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = badgeIcon,
                                    contentDescription = null,
                                    tint = if (highlighted) colors.accentStrong else colors.textSecondary,
                                    modifier = Modifier.size(11.dp)
                                )
                                Text(
                                    text = mode.title,
                                    fontFamily = PlusJakartaFontFamily,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 10.5.sp,
                                    color = if (highlighted) colors.accentStrong else colors.textSecondary,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                // Persistent header Now Playing restore entry — strictly ONLY in Music modes when mini player was swiped away
                if (activeMode != HomeMusicMode.ONLINE_VIDEO &&
                    showRestoreNowPlayingIcon &&
                    onRestoreNowPlayingClick != null
                ) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .clickable {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onRestoreNowPlayingClick()
                            }
                            .semantics { contentDescription = "Open Now Playing and restore mini player" }
                            .testTag("header_restore_now_playing_button"),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(colors.accentPill)
                                .border(1.dp, colors.accentStrong.copy(alpha = 0.45f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.MusicNote,
                                contentDescription = null,
                                tint = colors.onAccentPill,
                                modifier = Modifier.size(19.dp)
                            )
                        }
                    }
                }

                // Global Online Video Notification Bell — strictly visible ONLY in Online Video mode
                if (activeMode == HomeMusicMode.ONLINE_VIDEO && onVideoNotificationsClick != null) {
                    val hasUnread = unreadVideoNotificationsCount > 0
                    val bellDescription = if (hasUnread) {
                        "Online video notifications, $unreadVideoNotificationsCount unread"
                    } else {
                        "Online video notifications"
                    }
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .clickable {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onVideoNotificationsClick()
                            }
                            .semantics { contentDescription = bellDescription }
                            .testTag("online_video_notifications_bell"),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .graphicsLayer {
                                    scaleX = animatedBellScale
                                    scaleY = animatedBellScale
                                    rotationZ = animatedBellAngle
                                }
                                .clip(CircleShape)
                                .background(
                                    if (hasUnread) colors.accentStrong.copy(alpha = 0.16f) else colors.accentPill
                                )
                                .border(
                                    width = if (hasUnread) 1.dp else 0.dp,
                                    color = if (hasUnread) colors.accentStrong.copy(alpha = 0.55f) else colors.cardBorder.copy(alpha = 0f),
                                    shape = CircleShape
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (hasUnread) Icons.Rounded.NotificationsActive else Icons.Rounded.Notifications,
                                contentDescription = null,
                                tint = if (hasUnread) colors.accentStrong else colors.onAccentPill,
                                modifier = Modifier.size(19.dp)
                            )
                        }

                        if (hasUnread) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(top = 6.dp, end = 6.dp)
                                    .size(11.dp)
                                    .clip(CircleShape)
                                    .background(colors.background)
                                    .padding(1.5.dp)
                                    .clip(CircleShape)
                                    .background(colors.accentStrong)
                                    .testTag("online_video_notifications_badge")
                            )
                        }
                    }
                }

                // Three-Mode Home Switcher Scallop Starburst Button (48.dp touch target)
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .clickable {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onDiscoveryClick()
                        }
                        .semantics {
                            contentDescription = "Switch Home mode, currently ${activeMode.title}"
                        }
                        .testTag("discovery_icon_button"),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .graphicsLayer {
                                scaleX = animatedScale
                                scaleY = animatedScale
                                rotationZ = animatedTilt
                            }
                            .clip(DiscoveryScallopShape)
                            .background(modeButtonContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        val headerIcon = when (activeMode) {
                            HomeMusicMode.OFFLINE -> Icons.Rounded.MusicNote
                            HomeMusicMode.ONLINE -> Icons.Rounded.Headphones
                            HomeMusicMode.ONLINE_VIDEO -> Icons.Rounded.PlayCircle
                        }
                        Icon(
                            imageVector = headerIcon,
                            contentDescription = "Switch Offline Music, Online Music, or Online Video mode",
                            tint = modeButtonIconTint,
                            modifier = Modifier.size(19.dp)
                        )
                    }

                    // Active-mode indicator dot anchored cleanly at the badge corner
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(top = 5.dp, end = 4.dp)
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(colors.background)
                            .padding(1.5.dp)
                            .clip(CircleShape)
                            .background(
                                if (isOnlineActive) colors.accentStrong else colors.textSecondary
                            )
                            .border(0.5.dp, colors.cardBorder, CircleShape)
                    )
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
