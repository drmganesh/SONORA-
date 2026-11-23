package ui.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.HighQuality
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.PictureInPictureAlt
import androidx.compose.material.icons.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Radio
import androidx.compose.material.icons.rounded.Restore
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.OutfitFontFamily
import com.example.ui.theme.PlusJakartaFontFamily
import com.example.viewmodel.SonoraViewModel
import data.preferences.AlbumArtworkQualityMode
import domain.video.VideoAspectRatioMode
import ui.theme.EditorialColors
import ui.theme.LocalEditorialColors

/**
 * Dedicated Settings group for Part 1 & Part 7: Video Experience.
 * Persists every setting via DataStore (ExperiencePreferences & VideoGesturePreferences).
 */
@Composable
fun VideoExperienceSettingsScreen(
    viewModel: SonoraViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler { onBack() }
    val colors = LocalEditorialColors.current
    val videoSettings by viewModel.videoExperienceSettings.collectAsState()
    val videoGestureSettings by viewModel.videoController.gestureSettings.collectAsState()
    val downloadLocation by viewModel.downloadLocationConfig.collectAsState()
    val wifiQualityOptions = listOf(480 to "480p", 720 to "720p HD", 1080 to "1080p FHD", 1440 to "1440p QHD", 2160 to "2160p 4K")

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(colors.backgroundBrush)
            .statusBarsPadding()
            .navigationBarsPadding()
            .testTag("video_experience_settings_screen"),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 760.dp)
        ) {
            ExperienceHeader(
                title = "Video Experience",
                subtitle = "Picture-in-Picture, Gestures, Adaptive Quality & Downloads",
                onBack = onBack,
                colors = colors,
                backTag = "video_experience_back_button"
            )

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                item(key = "video_pip_gestures_card") {
                    ExperienceCard(
                        title = "PLAYER & GESTURES",
                        colors = colors
                    ) {
                        ExperienceSwitchRow(
                            title = "Automatic PiP",
                            subtitle = "Automatically enter Android Picture-in-Picture when pressing Home or switching apps during video playback (API 26+)",
                            checked = videoSettings.autoPipEnabled,
                            onCheckedChange = { viewModel.setAutoPipEnabled(it) },
                            colors = colors,
                            tag = "setting_auto_pip_switch"
                        )
                        HorizontalDivider(color = colors.cardBorder, modifier = Modifier.padding(vertical = 6.dp))
                        ExperienceSwitchRow(
                            title = "In-App Swipe Mini Player",
                            subtitle = "Swipe down from the center of a portrait video to minimize into the floating in-app mini player",
                            checked = videoSettings.inAppSwipeMiniPlayerEnabled,
                            onCheckedChange = { viewModel.setInAppSwipeMiniPlayerEnabled(it) },
                            colors = colors,
                            tag = "setting_inapp_mini_player_switch"
                        )
                        HorizontalDivider(color = colors.cardBorder, modifier = Modifier.padding(vertical = 6.dp))
                        ExperienceSwitchRow(
                            title = "Pinch to Zoom",
                            subtitle = "Pinch outward/inward to zoom (1x–3x), drag with two fingers to pan, double-tap to reset",
                            checked = videoSettings.pinchToZoomEnabled,
                            onCheckedChange = { viewModel.setPinchToZoomEnabled(it) },
                            colors = colors,
                            tag = "setting_pinch_zoom_switch"
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "Default Aspect Ratio",
                            fontFamily = OutfitFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = colors.textPrimary
                        )
                        Text(
                            text = "Applied automatically across portrait and landscape video playback",
                            fontFamily = PlusJakartaFontFamily,
                            fontSize = 12.sp,
                            color = colors.textSecondary
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        ExperienceChipRow(
                            options = VideoAspectRatioMode.entries.map { it to it.shortLabel },
                            selected = videoGestureSettings.defaultAspectRatio,
                            onSelect = { viewModel.videoController.setDefaultAspectRatio(it) },
                            colors = colors,
                            tagPrefix = "setting_aspect_"
                        )
                    }
                }

                item(key = "video_quality_network_card") {
                    ExperienceCard(
                        title = "ADAPTIVE VIDEO QUALITY",
                        colors = colors
                    ) {
                        ExperienceSwitchRow(
                            title = "Auto Video Quality",
                            subtitle = "Adapt video resolution automatically using live bandwidth, network type, and buffer health",
                            checked = videoSettings.autoVideoQualityEnabled,
                            onCheckedChange = { viewModel.setAutoVideoQualityEnabled(it) },
                            colors = colors,
                            tag = "setting_auto_video_quality_switch"
                        )
                        HorizontalDivider(color = colors.cardBorder, modifier = Modifier.padding(vertical = 6.dp))
                        Text(
                            text = "Preferred Wi-Fi Quality",
                            fontFamily = OutfitFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = colors.textPrimary
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        ExperienceChipRow(
                            options = wifiQualityOptions,
                            selected = videoSettings.preferredWifiQualityHeight,
                            onSelect = { viewModel.setPreferredWifiQualityHeight(it) },
                            colors = colors,
                            tagPrefix = "setting_wifi_q_"
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        ExperienceSwitchRow(
                            title = "Prefer Highest Quality on Wi-Fi",
                            subtitle = "Choose the sharpest available stream when connected to unmetered Wi-Fi",
                            checked = videoSettings.preferHighestQualityOnWifi,
                            onCheckedChange = { viewModel.setPreferHighestQualityOnWifi(it) },
                            colors = colors,
                            tag = "setting_prefer_hq_wifi_switch"
                        )
                        HorizontalDivider(color = colors.cardBorder, modifier = Modifier.padding(vertical = 6.dp))
                        ExperienceSwitchRow(
                            title = "Mobile Data Saver",
                            subtitle = "Cap Auto quality around 360p–480p on metered cellular connections",
                            checked = videoSettings.mobileDataSaverEnabled,
                            onCheckedChange = { viewModel.setMobileDataSaverEnabled(it) },
                            colors = colors,
                            tag = "setting_mobile_data_saver_switch"
                        )
                        HorizontalDivider(color = colors.cardBorder, modifier = Modifier.padding(vertical = 6.dp))
                        ExperienceSwitchRow(
                            title = "Allow High Quality on Mobile Data",
                            subtitle = "Permit 720p/1080p+ streaming on fast mobile networks when Data Saver is off",
                            checked = videoSettings.allowHighQualityOnMobileData,
                            onCheckedChange = { viewModel.setAllowHighQualityOnMobileData(it) },
                            colors = colors,
                            tag = "setting_allow_hq_mobile_switch"
                        )
                    }
                }

                item(key = "video_downloads_recs_card") {
                    ExperienceCard(
                        title = "VIDEO DOWNLOADS & FEED",
                        colors = colors
                    ) {
                        Surface(
                            onClick = { viewModel.requestChangeDownloadFolder() },
                            shape = RoundedCornerShape(16.dp),
                            color = colors.surfaceTinted,
                            border = BorderStroke(0.75.dp, colors.cardBorder),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("setting_video_download_location_row")
                        ) {
                            Row(
                                modifier = Modifier.padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .background(colors.accentPill, CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.FolderOpen,
                                        contentDescription = null,
                                        tint = colors.onAccentPill,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Video Download Location",
                                        fontFamily = OutfitFontFamily,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp,
                                        color = colors.textPrimary
                                    )
                                    Text(
                                        text = downloadLocation.folderLabel.ifBlank { "Tap to choose folder via Storage Access Framework" },
                                        fontFamily = PlusJakartaFontFamily,
                                        fontSize = 12.sp,
                                        color = colors.textSecondary,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        OutlinedButton(
                            onClick = { viewModel.videoController.resetVideoRecommendationFeedback() },
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("setting_reset_video_rec_feedback")
                        ) {
                            Icon(Icons.Rounded.Restore, contentDescription = null, tint = colors.accentStrong, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Reset Hidden Videos & Channel Filters",
                                fontFamily = PlusJakartaFontFamily,
                                fontWeight = FontWeight.SemiBold,
                                color = colors.accentStrong
                            )
                        }
                    }
                }

                item { Spacer(modifier = Modifier.height(24.dp)) }
            }
        }
    }
}

/**
 * Dedicated Settings group for Parts 3, 4, 5, 6 & 7: Music Experience.
 * Persists every setting via DataStore (ExperiencePreferences).
 */
@Composable
fun MusicExperienceSettingsScreen(
    viewModel: SonoraViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler { onBack() }
    val colors = LocalEditorialColors.current
    val musicSettings by viewModel.musicExperienceSettings.collectAsState()

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(colors.backgroundBrush)
            .statusBarsPadding()
            .navigationBarsPadding()
            .testTag("music_experience_settings_screen"),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 760.dp)
        ) {
            ExperienceHeader(
                title = "Music Experience",
                subtitle = "Discovery, Song Radio, Unlimited Queue, Mini Player & Artwork",
                onBack = onBack,
                colors = colors,
                backTag = "music_experience_back_button"
            )

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                item(key = "music_discovery_radio_card") {
                    ExperienceCard(
                        title = "DISCOVERY & SONG RADIO",
                        colors = colors
                    ) {
                        ExperienceSwitchRow(
                            title = "Music Recommendations",
                            subtitle = "Show Made For You mixes, Based on Your Listening, Mood & Activity, and India Music Discovery",
                            checked = musicSettings.musicRecommendationsEnabled,
                            onCheckedChange = { viewModel.setMusicRecommendationsEnabled(it) },
                            colors = colors,
                            tag = "setting_music_recommendations_switch"
                        )
                        HorizontalDivider(color = colors.cardBorder, modifier = Modifier.padding(vertical = 6.dp))
                        ExperienceSwitchRow(
                            title = "Automatic Song Radio",
                            subtitle = "Automatically top up queue with similar non-repeating tracks when radio is running",
                            checked = musicSettings.autoSongRadioEnabled,
                            onCheckedChange = { viewModel.setAutomaticSongRadioEnabled(it) },
                            colors = colors,
                            tag = "setting_auto_song_radio_switch"
                        )
                    }
                }

                item(key = "music_queue_mini_card") {
                    ExperienceCard(
                        title = "QUEUE & MINI PLAYER",
                        colors = colors
                    ) {
                        ExperienceSwitchRow(
                            title = "Unlimited Queue",
                            subtitle = "Persist arbitrarily large queues and active track position across app restarts in Room",
                            checked = musicSettings.unlimitedQueueEnabled,
                            onCheckedChange = { viewModel.setUnlimitedQueueEnabled(it) },
                            colors = colors,
                            tag = "setting_unlimited_queue_switch"
                        )
                        HorizontalDivider(color = colors.cardBorder, modifier = Modifier.padding(vertical = 6.dp))
                        ExperienceSwitchRow(
                            title = "Swipe Down to Dismiss Mini Player",
                            subtitle = "Hide the bottom mini player UI with a downward swipe while keeping music & foreground service playing",
                            checked = musicSettings.swipeDownDismissMiniPlayerEnabled,
                            onCheckedChange = { viewModel.setSwipeDownDismissMiniPlayerEnabled(it) },
                            colors = colors,
                            tag = "setting_swipe_dismiss_mini_switch"
                        )
                    }
                }

                item(key = "music_artwork_quality_card") {
                    ExperienceCard(
                        title = "ALBUM ARTWORK QUALITY",
                        colors = colors
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Rounded.Image,
                                contentDescription = null,
                                tint = colors.accentStrong,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Online Artwork Resolution",
                                fontFamily = OutfitFontFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = colors.textPrimary
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Uses genuine SD/HD provider artwork URLs with separate cache tiers and automatic fallback. Local tracks always prefer embedded artwork.",
                            fontFamily = PlusJakartaFontFamily,
                            fontSize = 12.sp,
                            color = colors.textSecondary
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        ExperienceChipRow(
                            options = AlbumArtworkQualityMode.entries.map { it to it.label },
                            selected = musicSettings.albumArtworkQualityMode,
                            onSelect = { viewModel.setAlbumArtworkQualityMode(it) },
                            colors = colors,
                            tagPrefix = "setting_artwork_quality_"
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = colors.surfaceTinted,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = musicSettings.albumArtworkQualityMode.subtitle,
                                fontFamily = PlusJakartaFontFamily,
                                fontSize = 12.sp,
                                color = colors.textSecondary,
                                modifier = Modifier.padding(12.dp)
                            )
                        }
                    }
                }

                item { Spacer(modifier = Modifier.height(24.dp)) }
            }
        }
    }
}

@Composable
private fun ExperienceHeader(
    title: String,
    subtitle: String,
    onBack: () -> Unit,
    colors: EditorialColors,
    backTag: String
) {
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
                .testTag(backTag)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = "Back",
                    tint = colors.textPrimary
                )
            }
        }
        Spacer(modifier = Modifier.width(14.dp))
        Column {
            Text(
                text = title,
                fontFamily = OutfitFontFamily,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 24.sp,
                color = colors.textPrimary
            )
            Text(
                text = subtitle,
                fontFamily = PlusJakartaFontFamily,
                fontSize = 12.sp,
                color = colors.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun ExperienceCard(
    title: String,
    colors: EditorialColors,
    content: @Composable () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = colors.surface,
        border = BorderStroke(0.75.dp, colors.cardBorder),
        shadowElevation = 4.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp)
        ) {
            Text(
                text = title,
                fontFamily = PlusJakartaFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 11.5.sp,
                letterSpacing = 1.2.sp,
                color = colors.accentStrong
            )
            Spacer(modifier = Modifier.height(10.dp))
            content()
        }
    }
}

@Composable
private fun ExperienceSwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    colors: EditorialColors,
    tag: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
            Text(
                text = title,
                fontFamily = OutfitFontFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.5.sp,
                color = colors.textPrimary
            )
            Text(
                text = subtitle,
                fontFamily = PlusJakartaFontFamily,
                fontSize = 12.sp,
                color = colors.textSecondary
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = colors.onAccentStrong,
                checkedTrackColor = colors.accentStrong
            ),
            modifier = Modifier.testTag(tag)
        )
    }
}

@Composable
private fun <T> ExperienceChipRow(
    options: List<Pair<T, String>>,
    selected: T,
    onSelect: (T) -> Unit,
    colors: EditorialColors,
    tagPrefix: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        options.forEach { (value, label) ->
            val isSelected = value == selected
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = if (isSelected) colors.accentStrong else colors.surfaceTinted,
                modifier = Modifier
                    .clip(RoundedCornerShape(14.dp))
                    .border(
                        width = if (isSelected) 1.5.dp else 0.75.dp,
                        color = if (isSelected) colors.accentStrong else colors.cardBorder,
                        shape = RoundedCornerShape(14.dp)
                    )
                    .clickable { onSelect(value) }
                    .testTag("$tagPrefix${label.lowercase().replace(" ", "_")}")
            ) {
                Text(
                    text = label,
                    fontFamily = PlusJakartaFontFamily,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                    fontSize = 12.sp,
                    color = if (isSelected) colors.onAccentStrong else colors.textPrimary,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp)
                )
            }
        }
    }
}
