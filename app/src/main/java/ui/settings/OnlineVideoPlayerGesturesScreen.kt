package ui.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AspectRatio
import androidx.compose.material.icons.rounded.FastForward
import androidx.compose.material.icons.rounded.ScreenRotation
import androidx.compose.material.icons.rounded.SwipeVertical
import androidx.compose.material.icons.rounded.TouchApp
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.OutfitFontFamily
import com.example.ui.theme.PlusJakartaFontFamily
import domain.video.VideoAspectRatioMode
import domain.video.VideoPlayerGestureSettings
import domain.video.VideoSwipeSensitivity
import ui.theme.EditorialColors
import ui.theme.LocalEditorialColors

/**
 * Settings → Online Video → Player Gestures screen.
 * Persists all 6 Online Video aspect ratio and swipe gesture settings via DataStore.
 */
@Composable
fun OnlineVideoPlayerGesturesScreen(
    settings: VideoPlayerGestureSettings,
    onSwipeFullscreenChanged: (Boolean) -> Unit,
    onAutoAspectRatioChanged: (Boolean) -> Unit,
    onDefaultAspectRatioChanged: (VideoAspectRatioMode) -> Unit,
    onSwipeSensitivityChanged: (VideoSwipeSensitivity) -> Unit,
    onDoubleTapSeekSecondsChanged: (Int) -> Unit,
    onAutoOrientationChanged: (Boolean) -> Unit,
    onResetToDefaults: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalEditorialColors.current
    BackHandler { onBack() }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(colors.backgroundBrush)
            .statusBarsPadding()
            .navigationBarsPadding()
            .testTag("online_video_player_gestures_screen"),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 760.dp)
        ) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = CircleShape,
                    color = colors.surface,
                    shadowElevation = 2.dp,
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .clickable { onBack() }
                        .testTag("video_gestures_back_button")
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = "Back to Settings",
                            tint = colors.textPrimary
                        )
                    }
                }
                Spacer(modifier = Modifier.width(14.dp))
                Column {
                    Text(
                        text = "Player Gestures",
                        fontFamily = OutfitFontFamily,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 24.sp,
                        color = colors.textPrimary
                    )
                    Text(
                        text = "Online Video • Aspect Ratio & Fullscreen Swipes",
                        fontFamily = PlusJakartaFontFamily,
                        fontSize = 12.sp,
                        color = colors.textSecondary
                    )
                }
            }

            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("video_gestures_list"),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Visual Zone Guide Card
                item {
                    Surface(
                        shape = RoundedCornerShape(22.dp),
                        color = colors.surface,
                        border = BorderStroke(0.75.dp, colors.cardBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(
                                text = "Smart Gesture Zones",
                                fontFamily = OutfitFontFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.5.sp,
                                color = colors.textPrimary
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                ZoneBadgeCard(
                                    title = "Left 25%",
                                    subtitle = "Brightness",
                                    highlighted = false,
                                    colors = colors,
                                    modifier = Modifier.weight(1f)
                                )
                                ZoneBadgeCard(
                                    title = "Center 50%",
                                    subtitle = if (settings.swipeFullscreenEnabled) "Swipe Fullscreen" else "Disabled",
                                    highlighted = settings.swipeFullscreenEnabled,
                                    colors = colors,
                                    modifier = Modifier.weight(1.4f)
                                )
                                ZoneBadgeCard(
                                    title = "Right 25%",
                                    subtitle = "Volume",
                                    highlighted = false,
                                    colors = colors,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            Text(
                                text = "Swipe up from the middle of a portrait video to enter landscape full screen. Swipe down in full screen to return smoothly to portrait without stopping playback.",
                                fontFamily = PlusJakartaFontFamily,
                                fontSize = 11.5.sp,
                                lineHeight = 16.sp,
                                color = colors.textSecondary
                            )
                        }
                    }
                }

                // 1. Fullscreen Swipe & Orientation Controls
                item {
                    VideoGestureSettingsCard(
                        icon = Icons.Rounded.SwipeVertical,
                        title = "Fullscreen & Orientation",
                        colors = colors
                    ) {
                        VideoGestureSwitchRow(
                            title = "Enable Swipe Fullscreen",
                            subtitle = "Central vertical swipe up for landscape full screen, swipe down to return to portrait",
                            checked = settings.swipeFullscreenEnabled,
                            onCheckedChange = onSwipeFullscreenChanged,
                            colors = colors,
                            testTag = "setting_video_swipe_fullscreen"
                        )

                        HorizontalDivider(color = colors.cardBorder, modifier = Modifier.padding(vertical = 8.dp))

                        VideoGestureSwitchRow(
                            title = "Automatic Screen Orientation",
                            subtitle = "Follow manual device rotation between portrait and landscape full screen",
                            checked = settings.autoOrientationEnabled,
                            onCheckedChange = onAutoOrientationChanged,
                            colors = colors,
                            testTag = "setting_video_auto_orientation"
                        )

                        if (settings.swipeFullscreenEnabled) {
                            HorizontalDivider(color = colors.cardBorder, modifier = Modifier.padding(vertical = 10.dp))
                            Text(
                                text = "Swipe Sensitivity",
                                fontFamily = OutfitFontFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.5.sp,
                                color = colors.textPrimary
                            )
                            Text(
                                text = "${settings.swipeSensitivity.subtitle} (${settings.swipeSensitivity.thresholdDp.toInt()} dp threshold)",
                                fontFamily = PlusJakartaFontFamily,
                                fontSize = 11.5.sp,
                                color = colors.textSecondary
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                VideoSwipeSensitivity.entries.forEach { sensitivity ->
                                    val active = settings.swipeSensitivity == sensitivity
                                    Surface(
                                        onClick = { onSwipeSensitivityChanged(sensitivity) },
                                        shape = RoundedCornerShape(14.dp),
                                        color = if (active) colors.accentStrong else colors.surfaceTinted,
                                        border = BorderStroke(0.75.dp, if (active) colors.accentStrong else colors.cardBorder),
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(42.dp)
                                            .testTag("setting_video_sensitivity_${sensitivity.id}")
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Text(
                                                text = sensitivity.label,
                                                fontFamily = PlusJakartaFontFamily,
                                                fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
                                                fontSize = 12.5.sp,
                                                color = if (active) colors.onAccentStrong else colors.textPrimary
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // 2. Smart Aspect Ratio Controls
                item {
                    VideoGestureSettingsCard(
                        icon = Icons.Rounded.AspectRatio,
                        title = "Aspect Ratio",
                        colors = colors
                    ) {
                        VideoGestureSwitchRow(
                            title = "Enable Automatic Aspect Ratio",
                            subtitle = "Adapt viewport dynamically using Media3 video dimensions & rotation metadata",
                            checked = settings.autoAspectRatioEnabled,
                            onCheckedChange = onAutoAspectRatioChanged,
                            colors = colors,
                            testTag = "setting_video_auto_aspect_ratio"
                        )

                        HorizontalDivider(color = colors.cardBorder, modifier = Modifier.padding(vertical = 10.dp))

                        Text(
                            text = "Default Aspect Ratio",
                            fontFamily = OutfitFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.5.sp,
                            color = colors.textPrimary
                        )
                        Text(
                            text = settings.defaultAspectRatio.description,
                            fontFamily = PlusJakartaFontFamily,
                            fontSize = 11.5.sp,
                            color = colors.textSecondary
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            VideoAspectRatioMode.entries.forEach { mode ->
                                val active = settings.defaultAspectRatio == mode
                                Surface(
                                    onClick = { onDefaultAspectRatioChanged(mode) },
                                    shape = RoundedCornerShape(14.dp),
                                    color = if (active) colors.accentStrong else colors.surfaceTinted,
                                    border = BorderStroke(0.75.dp, if (active) colors.accentStrong else colors.cardBorder),
                                    modifier = Modifier
                                        .height(40.dp)
                                        .testTag("setting_video_default_ratio_${mode.id}")
                                ) {
                                    Box(
                                        contentAlignment = Alignment.Center,
                                        modifier = Modifier.padding(horizontal = 14.dp)
                                    ) {
                                        Text(
                                            text = if (mode == VideoAspectRatioMode.AUTO) "Auto (Rec.)" else mode.shortLabel,
                                            fontFamily = PlusJakartaFontFamily,
                                            fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
                                            fontSize = 12.sp,
                                            color = if (active) colors.onAccentStrong else colors.textPrimary
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // 3. Double-Tap Seeking Controls
                item {
                    VideoGestureSettingsCard(
                        icon = Icons.Rounded.FastForward,
                        title = "Double-Tap Seeking",
                        colors = colors
                    ) {
                        Text(
                            text = "Double-Tap Seek Duration",
                            fontFamily = OutfitFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.5.sp,
                            color = colors.textPrimary
                        )
                        Text(
                            text = "Double-tap left to rewind ${settings.doubleTapSeekSeconds}s or right to skip forward ${settings.doubleTapSeekSeconds}s",
                            fontFamily = PlusJakartaFontFamily,
                            fontSize = 11.5.sp,
                            color = colors.textSecondary
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            VideoPlayerGestureSettings.supportedSeekDurationsSeconds.forEach { sec ->
                                val active = settings.doubleTapSeekSeconds == sec
                                Surface(
                                    onClick = { onDoubleTapSeekSecondsChanged(sec) },
                                    shape = RoundedCornerShape(14.dp),
                                    color = if (active) colors.accentStrong else colors.surfaceTinted,
                                    border = BorderStroke(0.75.dp, if (active) colors.accentStrong else colors.cardBorder),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(42.dp)
                                        .testTag("setting_video_seek_${sec}s")
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text(
                                            text = "${sec}s",
                                            fontFamily = PlusJakartaFontFamily,
                                            fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
                                            fontSize = 12.5.sp,
                                            color = if (active) colors.onAccentStrong else colors.textPrimary
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // 4. Reset to Defaults
                item {
                    OutlinedButton(
                        onClick = onResetToDefaults,
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(1.dp, colors.cardBorder),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("setting_video_gestures_reset")
                    ) {
                        Text(
                            text = "Reset Video Gesture Settings to Default",
                            fontFamily = PlusJakartaFontFamily,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp,
                            color = colors.accentStrong
                        )
                    }
                    Spacer(modifier = Modifier.height(24.dp))
                }
            }
        }
    }
}

@Composable
private fun ZoneBadgeCard(
    title: String,
    subtitle: String,
    highlighted: Boolean,
    colors: EditorialColors,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = if (highlighted) colors.accentPill.copy(alpha = 0.65f) else colors.surfaceTinted,
        border = BorderStroke(0.75.dp, if (highlighted) colors.accentStrong else colors.cardBorder),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = title,
                fontFamily = OutfitFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp,
                color = colors.textPrimary
            )
            Text(
                text = subtitle,
                fontFamily = PlusJakartaFontFamily,
                fontSize = 10.5.sp,
                color = if (highlighted) colors.accentStrong else colors.textSecondary
            )
        }
    }
}

@Composable
private fun VideoGestureSettingsCard(
    icon: ImageVector,
    title: String,
    colors: EditorialColors,
    content: @Composable () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(22.dp),
        color = colors.surface,
        border = BorderStroke(0.75.dp, colors.cardBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = colors.accentStrong,
                    modifier = Modifier.size(19.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = title,
                    fontFamily = OutfitFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = colors.textPrimary
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
            content()
        }
    }
}

@Composable
private fun VideoGestureSwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    colors: EditorialColors,
    testTag: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(vertical = 4.dp)
            .testTag(testTag),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontFamily = OutfitFontFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp,
                color = colors.textPrimary
            )
            Text(
                text = subtitle,
                fontFamily = PlusJakartaFontFamily,
                fontSize = 11.5.sp,
                lineHeight = 16.sp,
                color = colors.textSecondary
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = colors.onAccentStrong,
                checkedTrackColor = colors.accentStrong
            )
        )
    }
}
