package ui.video

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.core.content.ContextCompat
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.AspectRatio
import androidx.compose.material.icons.rounded.BookmarkAdd
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.HighQuality
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.NotificationsNone
import androidx.compose.material.icons.rounded.NotificationsOff
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Subtitles
import androidx.compose.material.icons.rounded.Verified
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.ui.theme.OutfitFontFamily
import com.example.ui.theme.PlusJakartaFontFamily
import data.download.VideoDownloadEligibility
import data.download.VideoDownloadEligibilityEvaluator
import data.download.VideoDownloadOption
import domain.download.DownloadMediaType
import domain.video.ChannelNotificationPreference
import domain.video.OnlineVideoChannel
import domain.video.OnlineVideoItem
import domain.video.ResolvedVideoDetails
import domain.video.VideoAccountActionResult
import domain.video.VideoAdaptiveQualitySelector
import domain.video.VideoAspectRatioMode
import domain.video.VideoAudioOption
import domain.video.VideoChannelUiState
import domain.video.VideoStreamOption
import domain.video.VideoSubtitleOption
import ui.theme.EditorialColors

fun openOfficialExternalUrl(context: Context, url: String): Boolean {
    val safeUri = util.SecureUrlValidator.sanitizeExternalBrowseUrl(url) ?: return false
    return runCatching {
        val intent = Intent(Intent.ACTION_VIEW, safeUri).apply {
            addCategory(Intent.CATEGORY_BROWSABLE)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
        true
    }.getOrDefault(false)
}

fun shareVideoLink(context: Context, video: OnlineVideoItem) {
    runCatching {
        val sendIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, video.title)
            putExtra(Intent.EXTRA_TEXT, "${video.title} — ${video.contentUrl}")
        }
        val chooser = Intent.createChooser(sendIntent, "Share video").apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(chooser)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VideoSettingsSheet(
    videoStreams: List<VideoStreamOption>,
    selectedVideoStream: VideoStreamOption?,
    isAutoQualityMode: Boolean = true,
    qualityStatusLabel: String = "Auto: Best Available",
    actualResolutionDisplay: String = "",
    actualFormatDisplay: String = "",
    audioStreams: List<VideoAudioOption>,
    selectedAudioStream: VideoAudioOption?,
    subtitles: List<VideoSubtitleOption>,
    selectedSubtitle: VideoSubtitleOption?,
    playbackSpeed: Float,
    aspectRatioMode: VideoAspectRatioMode,
    detectedAspectRatioLabel: String,
    hasKnownDimensions: Boolean,
    colors: EditorialColors,
    onSelectAutoQuality: () -> Unit = {},
    onSelectVideoStream: (VideoStreamOption) -> Unit,
    onSelectAudioStream: (VideoAudioOption) -> Unit,
    onSelectSubtitle: (VideoSubtitleOption?) -> Unit,
    onSelectSpeed: (Float) -> Unit,
    onOpenAspectRatioSelector: () -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val speedOptions = listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 1.75f, 2.0f)

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
                .testTag("video_settings_sheet"),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "Video Playback Controls",
                fontFamily = OutfitFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp,
                color = colors.textPrimary
            )

            // 0. Aspect Ratio Selector Entry Button
            SettingsSectionBlock(
                icon = Icons.Rounded.AspectRatio,
                title = "Aspect Ratio",
                colors = colors
            ) {
                val activeDescription = if (aspectRatioMode == VideoAspectRatioMode.AUTO && hasKnownDimensions) {
                    "Auto ($detectedAspectRatioLabel detected) • Original proportions"
                } else {
                    "${aspectRatioMode.displayTitle} • ${aspectRatioMode.description}"
                }
                Surface(
                    onClick = {
                        onDismiss()
                        onOpenAspectRatioSelector()
                    },
                    shape = RoundedCornerShape(16.dp),
                    color = colors.accentPill.copy(alpha = 0.55f),
                    border = BorderStroke(1.dp, colors.accentStrong.copy(alpha = 0.7f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("video_settings_aspect_ratio_button")
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (aspectRatioMode == VideoAspectRatioMode.AUTO && hasKnownDimensions) {
                                    "Auto ($detectedAspectRatioLabel)"
                                } else {
                                    aspectRatioMode.displayTitle
                                },
                                fontFamily = OutfitFontFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = colors.textPrimary
                            )
                            Text(
                                text = activeDescription,
                                fontFamily = PlusJakartaFontFamily,
                                fontSize = 11.5.sp,
                                color = colors.textSecondary,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Icon(
                            imageVector = Icons.Rounded.ChevronRight,
                            contentDescription = "Choose aspect ratio",
                            tint = colors.accentStrong,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            }

            // 1. Resolution selector (Auto: Best Available + strictly available stream options for this video)
            SettingsSectionBlock(
                icon = Icons.Rounded.HighQuality,
                title = "Video Quality",
                colors = colors
            ) {
                val distinctStreams = VideoAdaptiveQualitySelector.distinctAvailableQualityOptions(videoStreams)
                val availableSummary = if (distinctStreams.isNotEmpty()) {
                    distinctStreams.joinToString(" • ") { "${it.heightPx}p" }
                } else {
                    "Provider default stream"
                }
                val resolvedCurrentRes = actualResolutionDisplay.ifBlank {
                    selectedVideoStream?.qualityTierLabel ?: "Resolving..."
                }
                val resolvedCurrentFmt = actualFormatDisplay.ifBlank {
                    selectedVideoStream?.let { s ->
                        val delivery = if (s.isVideoOnly) "Adaptive Video + Audio" else "Muxed Stream"
                        buildList {
                            add(s.codecFamily)
                            add(delivery)
                            if (s.fps >= 50) add("${s.fps}fps")
                            if (s.bitrateKbps > 0) add("${s.bitrateKbps} kbps")
                        }.joinToString(" • ")
                    } ?: "Stream"
                }
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = colors.surfaceTinted,
                    border = BorderStroke(0.75.dp, colors.cardBorder),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("video_quality_live_info_card")
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        Text(
                            text = "Playing: $resolvedCurrentRes",
                            fontFamily = OutfitFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = colors.textPrimary,
                            modifier = Modifier.testTag("video_quality_current_resolution")
                        )
                        Text(
                            text = "Mode: ${if (isAutoQualityMode) "Auto (Best Available)" else "Manual"} • Format: $resolvedCurrentFmt",
                            fontFamily = PlusJakartaFontFamily,
                            fontSize = 11.5.sp,
                            color = colors.textSecondary,
                            modifier = Modifier.testTag("video_quality_current_format")
                        )
                        Text(
                            text = "Available: $availableSummary",
                            fontFamily = PlusJakartaFontFamily,
                            fontSize = 11.sp,
                            color = colors.textMuted,
                            modifier = Modifier.testTag("video_quality_available_summary")
                        )
                    }
                }

                SelectableOptionRow(
                    label = if (isAutoQualityMode && qualityStatusLabel.startsWith("Auto")) {
                        qualityStatusLabel
                    } else {
                        "Auto: Best Available Quality"
                    },
                    subtitle = "Prefers original source quality; steps down only when bandwidth or buffering requires it",
                    isSelected = isAutoQualityMode,
                    colors = colors,
                    modifier = Modifier.testTag("video_quality_option_auto"),
                    onClick = {
                        onSelectAutoQuality()
                        onDismiss()
                    }
                )
                if (distinctStreams.isEmpty()) {
                    Text(
                        text = "No separate manual resolutions supplied by the provider for this stream.",
                        fontFamily = PlusJakartaFontFamily,
                        fontSize = 12.sp,
                        color = colors.textSecondary
                    )
                } else {
                    distinctStreams.forEach { stream ->
                        val active = !isAutoQualityMode && stream.id == selectedVideoStream?.id
                        val tierLabel = VideoAdaptiveQualitySelector.formatResolutionLabel(stream.heightPx, stream.resolutionLabel)
                        val detailSubtitle = buildList {
                            add(stream.codecFamily)
                            add(if (stream.isVideoOnly) "Adaptive Video + Audio" else "Direct Muxed")
                            if (stream.bitrateKbps > 0) add("${stream.bitrateKbps} kbps")
                        }.joinToString(" • ")
                        SelectableOptionRow(
                            label = "$tierLabel${if (stream.fps >= 50) " • ${stream.fps}fps" else ""}",
                            subtitle = detailSubtitle,
                            isSelected = active,
                            colors = colors,
                            modifier = Modifier.testTag("video_quality_option_${stream.heightPx}"),
                            onClick = {
                                onSelectVideoStream(stream)
                                onDismiss()
                            }
                        )
                    }
                }
            }

            // 2. Playback Speed selector
            SettingsSectionBlock(
                icon = Icons.Rounded.Speed,
                title = "Playback Speed",
                colors = colors
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    speedOptions.forEach { speed ->
                        val active = kotlin.math.abs(speed - playbackSpeed) < 0.01f
                        Surface(
                            onClick = { onSelectSpeed(speed) },
                            shape = RoundedCornerShape(14.dp),
                            color = if (active) colors.accentStrong else colors.surfaceTinted,
                            border = BorderStroke(0.75.dp, if (active) colors.accentStrong else colors.cardBorder),
                            modifier = Modifier
                                .weight(1f)
                                .height(40.dp)
                                .testTag("video_speed_${speed}")
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = if (speed == 1.0f) "1x" else "${speed}x",
                                    fontFamily = PlusJakartaFontFamily,
                                    fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
                                    fontSize = 11.sp,
                                    color = if (active) colors.onAccentStrong else colors.textPrimary
                                )
                            }
                        }
                    }
                }
            }

            // 3. Subtitles selector
            SettingsSectionBlock(
                icon = Icons.Rounded.Subtitles,
                title = "Subtitles / Captions",
                colors = colors
            ) {
                SelectableOptionRow(
                    label = "Off",
                    subtitle = "Hide captions",
                    isSelected = selectedSubtitle == null,
                    colors = colors,
                    modifier = Modifier.testTag("video_subtitle_off"),
                    onClick = {
                        onSelectSubtitle(null)
                        onDismiss()
                    }
                )
                subtitles.forEach { sub ->
                    val active = sub.id == selectedSubtitle?.id
                    SelectableOptionRow(
                        label = sub.displayName,
                        subtitle = sub.languageTag.uppercase(),
                        isSelected = active,
                        colors = colors,
                        modifier = Modifier.testTag("video_subtitle_${sub.languageTag}"),
                        onClick = {
                            onSelectSubtitle(sub)
                            onDismiss()
                        }
                    )
                }
            }

            // 4. Audio Track selector
            if (audioStreams.isNotEmpty()) {
                SettingsSectionBlock(
                    icon = Icons.Rounded.GraphicEq,
                    title = "Audio Track",
                    colors = colors
                ) {
                    audioStreams.take(8).forEach { aud ->
                        val active = aud.id == selectedAudioStream?.id
                        SelectableOptionRow(
                            label = aud.displayLabel,
                            subtitle = aud.formatLabel,
                            isSelected = active,
                            colors = colors,
                            onClick = {
                                onSelectAudioStream(aud)
                                onDismiss()
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsSectionBlock(
    icon: ImageVector,
    title: String,
    colors: EditorialColors,
    content: @Composable () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = colors.accentStrong,
                modifier = Modifier.size(18.dp)
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
        content()
    }
}

@Composable
private fun SelectableOptionRow(
    label: String,
    subtitle: String,
    isSelected: Boolean,
    colors: EditorialColors,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        color = if (isSelected) colors.accentPill.copy(alpha = 0.6f) else colors.surfaceTinted,
        border = BorderStroke(
            width = if (isSelected) 1.25.dp else 0.5.dp,
            color = if (isSelected) colors.accentStrong else colors.cardBorder
        ),
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = label,
                    fontFamily = PlusJakartaFontFamily,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                    fontSize = 13.5.sp,
                    color = colors.textPrimary
                )
                Text(
                    text = subtitle,
                    fontFamily = PlusJakartaFontFamily,
                    fontSize = 11.5.sp,
                    color = colors.textSecondary
                )
            }
            if (isSelected) {
                Icon(
                    imageVector = Icons.Rounded.Check,
                    contentDescription = "Selected",
                    tint = colors.accentStrong,
                    modifier = Modifier.size(19.dp)
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VideoQueueSheet(
    queue: List<OnlineVideoItem>,
    activeVideoId: String?,
    colors: EditorialColors,
    onPlayVideo: (OnlineVideoItem) -> Unit,
    onRemoveFromQueue: (String) -> Unit,
    onMoreClick: (OnlineVideoItem) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
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
                .padding(horizontal = 18.dp)
                .padding(bottom = 24.dp)
                .testTag("video_queue_sheet")
        ) {
            Text(
                text = "Up Next Queue (${queue.size})",
                fontFamily = OutfitFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 19.sp,
                color = colors.textPrimary
            )
            Spacer(modifier = Modifier.height(12.dp))
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(queue, key = { "vq_${it.videoId}" }) { item ->
                    val isCurrent = item.videoId == activeVideoId
                    CompactVideoRowCard(
                        video = item,
                        colors = colors,
                        isPlayingNow = isCurrent,
                        onClick = {
                            onPlayVideo(item)
                            onDismiss()
                        },
                        onMoreClick = { onMoreClick(item) },
                        trailingActionIcon = if (!isCurrent) Icons.Rounded.Close else null,
                        trailingActionDescription = "Remove from queue",
                        onTrailingAction = if (!isCurrent) {
                            { onRemoveFromQueue(item.videoId) }
                        } else null
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationBellOptionsSheet(
    channel: OnlineVideoChannel,
    currentPreference: ChannelNotificationPreference,
    supportsApi: Boolean = false,
    colors: EditorialColors,
    onSelectPreference: (ChannelNotificationPreference) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var permissionDeniedNote by remember { mutableStateOf(false) }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            permissionDeniedNote = false
            onSelectPreference(ChannelNotificationPreference.ALL)
            onDismiss()
        } else {
            permissionDeniedNote = true
        }
    }

    val isAllEnabled = currentPreference == ChannelNotificationPreference.ALL

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
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp)
                .testTag("notification_bell_sheet"),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "${domain.branding.AppBranding.APP_NAME} Notifications • ${channel.name}",
                fontFamily = OutfitFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 19.sp,
                color = colors.textPrimary
            )

            Surface(
                shape = RoundedCornerShape(16.dp),
                color = colors.surfaceTinted,
                border = BorderStroke(0.75.dp, colors.cardBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Info,
                        contentDescription = null,
                        tint = colors.accentStrong,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "${domain.branding.AppBranding.APP_NAME} checks followed channels periodically in the background and alerts you locally when new uploads appear. Manage per-channel preferences anytime.",
                        fontFamily = PlusJakartaFontFamily,
                        fontSize = 12.sp,
                        lineHeight = 17.sp,
                        color = colors.textSecondary
                    )
                }
            }

            if (permissionDeniedNote) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = colors.accentPill.copy(alpha = 0.5f),
                    border = BorderStroke(1.dp, colors.accentStrong.copy(alpha = 0.65f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Android notification permission is needed before ${domain.branding.AppBranding.APP_NAME} can deliver new-upload alerts. Allow notifications in system settings and try again.",
                        fontFamily = PlusJakartaFontFamily,
                        fontSize = 12.sp,
                        color = colors.textPrimary,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }

            // All Notifications option
            Surface(
                onClick = {
                    val needsRuntimePermission = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                        ContextCompat.checkSelfPermission(
                            context,
                            Manifest.permission.POST_NOTIFICATIONS
                        ) != PackageManager.PERMISSION_GRANTED
                    if (needsRuntimePermission) {
                        notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    } else {
                        onSelectPreference(ChannelNotificationPreference.ALL)
                        onDismiss()
                    }
                },
                shape = RoundedCornerShape(16.dp),
                color = if (isAllEnabled) colors.accentPill else colors.surfaceTinted,
                border = BorderStroke(
                    width = if (isAllEnabled) 1.25.dp else 0.75.dp,
                    color = if (isAllEnabled) colors.accentStrong else colors.cardBorder
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("notif_pref_all")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Rounded.NotificationsActive,
                        contentDescription = null,
                        tint = colors.accentStrong,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "All Notifications",
                            fontFamily = OutfitFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = colors.textPrimary
                        )
                        Text(
                            text = "Notify me when ${domain.branding.AppBranding.APP_NAME} detects new videos from this channel",
                            fontFamily = PlusJakartaFontFamily,
                            fontSize = 12.sp,
                            color = colors.textSecondary
                        )
                    }
                    if (isAllEnabled) {
                        Icon(
                            imageVector = Icons.Rounded.Check,
                            contentDescription = "Enabled",
                            tint = colors.accentStrong,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            // Notifications Off option
            Surface(
                onClick = {
                    onSelectPreference(ChannelNotificationPreference.NONE)
                    onDismiss()
                },
                shape = RoundedCornerShape(16.dp),
                color = if (!isAllEnabled) colors.accentPill else colors.surfaceTinted,
                border = BorderStroke(
                    width = if (!isAllEnabled) 1.25.dp else 0.75.dp,
                    color = if (!isAllEnabled) colors.accentStrong else colors.cardBorder
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("notif_pref_none")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Rounded.NotificationsOff,
                        contentDescription = null,
                        tint = if (!isAllEnabled) colors.accentStrong else colors.textSecondary,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Notifications Off",
                            fontFamily = OutfitFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = colors.textPrimary
                        )
                        Text(
                            text = "Stay subscribed in ${domain.branding.AppBranding.APP_NAME} without background upload alerts",
                            fontFamily = PlusJakartaFontFamily,
                            fontSize = 12.sp,
                            color = colors.textSecondary
                        )
                    }
                    if (!isAllEnabled) {
                        Icon(
                            imageVector = Icons.Rounded.Check,
                            contentDescription = "Selected",
                            tint = colors.accentStrong,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
    }
}

/**
 * SONORA Save Sheet opened from the Online Video Interaction Bar:
 * lets the user save/remove a video in SONORA's Online Library (Saved Videos / Watch Later)
 * or add/create a SONORA Video Playlist in one tap.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SonoraSaveVideoSheet(
    video: OnlineVideoItem,
    isSaved: Boolean,
    isWatchLater: Boolean,
    playlists: List<domain.video.OnlineVideoPlaylistSummary>,
    colors: EditorialColors,
    onToggleSave: () -> Unit,
    onToggleWatchLater: () -> Unit,
    onSelectPlaylist: (domain.video.OnlineVideoPlaylistSummary) -> Unit,
    onOpenCreatePlaylist: () -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
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
                .testTag("sonora_save_video_sheet"),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = "Save in ${domain.branding.AppBranding.APP_NAME}",
                fontFamily = OutfitFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 19.sp,
                color = colors.textPrimary
            )
            Text(
                text = video.title,
                fontFamily = PlusJakartaFontFamily,
                fontSize = 12.5.sp,
                color = colors.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            SelectableOptionRow(
                label = if (isSaved) "Saved in Online Library" else "Save to Online Library",
                subtitle = if (isSaved) "Tap to remove from Saved Videos" else "Keep in Online Library → Saved Videos",
                isSelected = isSaved,
                colors = colors,
                modifier = Modifier.testTag("sonora_save_sheet_toggle_saved"),
                onClick = {
                    onToggleSave()
                }
            )

            SelectableOptionRow(
                label = if (isWatchLater) "In Watch Later" else "Watch Later",
                subtitle = if (isWatchLater) "Tap to remove from Watch Later" else "Queue in Online Library → Watch Later",
                isSelected = isWatchLater,
                colors = colors,
                modifier = Modifier.testTag("sonora_save_sheet_toggle_watch_later"),
                onClick = {
                    onToggleWatchLater()
                }
            )

            HorizontalDivider(color = colors.cardBorder, modifier = Modifier.padding(vertical = 4.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Video Playlists",
                    fontFamily = OutfitFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = colors.textPrimary
                )
                TextButton(
                    onClick = {
                        onDismiss()
                        onOpenCreatePlaylist()
                    },
                    modifier = Modifier.testTag("sonora_save_sheet_new_playlist")
                ) {
                    Text(
                        text = "+ New Playlist",
                        fontFamily = PlusJakartaFontFamily,
                        fontWeight = FontWeight.Bold,
                        color = colors.accentStrong
                    )
                }
            }

            if (playlists.isEmpty()) {
                Text(
                    text = "No ${domain.branding.AppBranding.APP_NAME} video playlists yet. Tap + New Playlist to create one.",
                    fontFamily = PlusJakartaFontFamily,
                    fontSize = 12.sp,
                    color = colors.textSecondary
                )
            } else {
                playlists.forEach { playlist ->
                    val alreadyIn = playlist.videos.any { it.videoId == video.videoId }
                    SelectableOptionRow(
                        label = playlist.name,
                        subtitle = "${playlist.videoCount} videos${if (alreadyIn) " • Added" else ""}",
                        isSelected = alreadyIn,
                        colors = colors,
                        modifier = Modifier.testTag("sonora_save_sheet_playlist_${playlist.id}"),
                        onClick = {
                            onSelectPlaylist(playlist)
                            onDismiss()
                        }
                    )
                }
            }
        }
    }
}

/**
 * Transparent official account action prompt when an action requires OAuth2 authorization
 * or is unsupported by the official YouTube Data API v3. Never fakes confirmed actions.
 */
@Composable
fun VideoAccountActionDialog(
    result: VideoAccountActionResult,
    activeChannel: OnlineVideoChannel?,
    colors: EditorialColors,
    onBookmarkChannelLocally: ((OnlineVideoChannel) -> Unit)?,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val (title, explanation, officialUrl) = when (result) {
        is VideoAccountActionResult.RequiresAuthorization ->
            Triple(result.actionTitle, result.explanation, result.officialWebUrl)
        is VideoAccountActionResult.UnsupportedByProviderApi ->
            Triple(result.actionTitle, result.explanation, result.officialWebUrl)
        is VideoAccountActionResult.Failed ->
            Triple("Account Action Unavailable", result.message, result.officialWebUrl)
        is VideoAccountActionResult.Confirmed -> return
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.surface,
        titleContentColor = colors.textPrimary,
        textContentColor = colors.textSecondary,
        modifier = Modifier.testTag("video_account_action_dialog"),
        title = {
            Text(
                text = title,
                fontFamily = OutfitFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = explanation,
                    fontFamily = PlusJakartaFontFamily,
                    fontSize = 13.sp,
                    lineHeight = 19.sp,
                    color = colors.textSecondary
                )
                if (activeChannel != null && onBookmarkChannelLocally != null &&
                    title.contains("Subscribe", ignoreCase = true)
                ) {
                    OutlinedButton(
                        onClick = {
                            onBookmarkChannelLocally(activeChannel)
                            onDismiss()
                        },
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(0.75.dp, colors.cardBorder),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("bookmark_channel_locally_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.BookmarkAdd,
                            contentDescription = null,
                            tint = colors.accentStrong,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Bookmark in ${domain.branding.AppBranding.APP_NAME} Library",
                            fontFamily = PlusJakartaFontFamily,
                            fontWeight = FontWeight.SemiBold,
                            color = colors.textPrimary
                        )
                    }
                }
            }
        },
        confirmButton = {
            if (!officialUrl.isNullOrBlank()) {
                Button(
                    onClick = {
                        openOfficialExternalUrl(context, officialUrl)
                        onDismiss()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = colors.accentStrong,
                        contentColor = colors.onAccentStrong
                    ),
                    modifier = Modifier.testTag("open_official_provider_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.OpenInNew,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Open YouTube")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Dismiss", color = colors.textSecondary)
            }
        }
    )
}

/**
 * Full Channel Profile Bottom Sheet showing channel banner, avatar, subscriber count,
 * Subscribe / Bookmark controls, and channel uploads fetched via NewPipe Extractor.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VideoChannelSheet(
    state: VideoChannelUiState,
    activeVideoId: String?,
    colors: EditorialColors,
    onPlayVideo: (OnlineVideoItem, List<OnlineVideoItem>) -> Unit,
    onToggleSubscribe: (OnlineVideoChannel) -> Unit,
    onBookmarkChannel: (OnlineVideoChannel) -> Unit,
    onRetry: (String, String) -> Unit,
    onVideoMoreClick: (OnlineVideoItem) -> Unit,
    onDismiss: () -> Unit
) {
    if (state is VideoChannelUiState.Idle) return
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = colors.surface,
        contentColor = colors.textPrimary,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    ) {
        when (state) {
            is VideoChannelUiState.Loading -> {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(36.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    CircularProgressIndicator(color = colors.accentStrong)
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Loading ${state.name}...",
                        fontFamily = PlusJakartaFontFamily,
                        fontSize = 13.5.sp,
                        color = colors.textSecondary
                    )
                }
            }
            is VideoChannelUiState.Error -> {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = state.message,
                        fontFamily = PlusJakartaFontFamily,
                        fontSize = 13.5.sp,
                        color = colors.textSecondary
                    )
                    Button(
                        onClick = { onRetry(state.channelUrl, state.channelName) },
                        colors = ButtonDefaults.buttonColors(containerColor = colors.accentStrong)
                    ) {
                        Text("Retry", color = colors.onAccentStrong)
                    }
                }
            }
            is VideoChannelUiState.Success -> {
                val channel = state.channel
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 18.dp)
                        .testTag("video_channel_sheet"),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    item(key = "channel_header") {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            if (!channel.bannerUrl.isNullOrBlank()) {
                                AsyncImage(
                                    model = channel.bannerUrl,
                                    contentDescription = "${channel.name} banner",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(92.dp)
                                        .clip(RoundedCornerShape(16.dp))
                                )
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                ChannelAvatarBadge(
                                    avatarUrl = channel.avatarUrl,
                                    channelName = channel.name,
                                    colors = colors,
                                    sizeDp = 54
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = channel.name,
                                            fontFamily = OutfitFontFamily,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 18.sp,
                                            color = colors.textPrimary,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        if (channel.isVerified) {
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Icon(
                                                imageVector = Icons.Rounded.Verified,
                                                contentDescription = "Verified",
                                                tint = colors.accentStrong,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                    channel.formattedSubscribers()?.let { subs ->
                                        Text(
                                            text = subs,
                                            fontFamily = PlusJakartaFontFamily,
                                            fontSize = 12.sp,
                                            color = colors.textSecondary
                                        )
                                    }
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Button(
                                    onClick = { onToggleSubscribe(channel) },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (channel.isSubscribedOnProvider) colors.surfaceTinted else colors.accentStrong,
                                        contentColor = if (channel.isSubscribedOnProvider) colors.textPrimary else colors.onAccentStrong
                                    ),
                                    shape = RoundedCornerShape(16.dp),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(44.dp)
                                ) {
                                    Text(
                                        text = if (channel.isSubscribedOnProvider) "Subscribed" else "Subscribe",
                                        fontFamily = PlusJakartaFontFamily,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp
                                    )
                                }
                                OutlinedButton(
                                    onClick = { onBookmarkChannel(channel) },
                                    shape = RoundedCornerShape(16.dp),
                                    border = BorderStroke(0.75.dp, colors.cardBorder),
                                    modifier = Modifier.height(44.dp)
                                ) {
                                    Text(
                                        text = if (channel.isSubscribedLocal) "Bookmarked" else "Bookmark",
                                        fontFamily = PlusJakartaFontFamily,
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 12.5.sp,
                                        color = colors.textPrimary
                                    )
                                }
                            }

                            channel.description?.takeIf { it.isNotBlank() }?.let { desc ->
                                Text(
                                    text = desc,
                                    fontFamily = PlusJakartaFontFamily,
                                    fontSize = 12.sp,
                                    color = colors.textSecondary,
                                    maxLines = 3,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            HorizontalDivider(color = colors.cardBorder)
                            Text(
                                text = "Channel Videos (${channel.videos.size})",
                                fontFamily = OutfitFontFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                color = colors.textPrimary
                            )
                        }
                    }

                    items(channel.videos, key = { "ch_vid_${it.videoId}" }) { video ->
                        CompactVideoRowCard(
                            video = video,
                            colors = colors,
                            isPlayingNow = video.videoId == activeVideoId,
                            onClick = {
                                onPlayVideo(video, channel.videos)
                                onDismiss()
                            },
                            onMoreClick = { onVideoMoreClick(video) }
                        )
                    }
                    item { Spacer(modifier = Modifier.height(24.dp)) }
                }
            }
            VideoChannelUiState.Idle -> Unit
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VideoAspectRatioSheet(
    currentMode: VideoAspectRatioMode,
    videoWidth: Int,
    videoHeight: Int,
    unappliedRotationDegrees: Int,
    detectedRatioLabel: String,
    hasKnownDimensions: Boolean,
    colors: EditorialColors,
    onSelectMode: (VideoAspectRatioMode) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
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
                .testTag("video_aspect_ratio_sheet"),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Rounded.AspectRatio,
                        contentDescription = null,
                        tint = colors.accentStrong,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Aspect Ratio",
                        fontFamily = OutfitFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp,
                        color = colors.textPrimary
                    )
                }

                val badgeText = if (hasKnownDimensions && videoWidth > 0 && videoHeight > 0) {
                    val rot = if (unappliedRotationDegrees % 360 != 0) " • ${unappliedRotationDegrees}°" else ""
                    "$detectedRatioLabel (${videoWidth}×${videoHeight}$rot)"
                } else {
                    "Auto Detect"
                }
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = colors.surfaceTinted,
                    border = BorderStroke(0.75.dp, colors.cardBorder)
                ) {
                    Text(
                        text = badgeText,
                        fontFamily = PlusJakartaFontFamily,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 11.sp,
                        color = colors.accentStrong,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                    )
                }
            }

            Text(
                text = "Changes apply immediately without interrupting playback. Saved automatically as your preferred ratio.",
                fontFamily = PlusJakartaFontFamily,
                fontSize = 12.sp,
                color = colors.textSecondary
            )

            Spacer(modifier = Modifier.height(2.dp))

            VideoAspectRatioMode.entries.forEach { mode ->
                val active = mode == currentMode
                val title = if (mode == VideoAspectRatioMode.AUTO && hasKnownDimensions) {
                    "Auto (Recommended) • $detectedRatioLabel"
                } else {
                    mode.displayTitle
                }
                SelectableOptionRow(
                    label = title,
                    subtitle = mode.description,
                    isSelected = active,
                    colors = colors,
                    modifier = Modifier.testTag("aspect_ratio_option_${mode.id}"),
                    onClick = {
                        onSelectMode(mode)
                        onDismiss()
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VideoDownloadQualitySheet(
    video: OnlineVideoItem,
    details: ResolvedVideoDetails,
    folderLabel: String,
    colors: EditorialColors,
    onSelectOption: (VideoDownloadOption) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val eligibility = VideoDownloadEligibilityEvaluator.evaluate(video, details)
    val selectedTab = androidx.compose.runtime.remember {
        androidx.compose.runtime.mutableStateOf(DownloadMediaType.VIDEO)
    }

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
                .testTag("video_download_quality_sheet"),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Download Media",
                fontFamily = OutfitFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp,
                color = colors.textPrimary
            )
            Text(
                text = "${video.title} • Saving to ${folderLabel.ifBlank { "Selected Folder" }}",
                fontFamily = PlusJakartaFontFamily,
                fontSize = 12.sp,
                color = colors.textSecondary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            when (eligibility) {
                is VideoDownloadEligibility.NotPermitted -> {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = colors.surfaceTinted,
                        border = BorderStroke(0.75.dp, colors.cardBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = eligibility.reason,
                            fontFamily = PlusJakartaFontFamily,
                            fontSize = 13.sp,
                            color = colors.textSecondary,
                            modifier = Modifier.padding(14.dp)
                        )
                    }
                }
                is VideoDownloadEligibility.Permitted -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val videoSelected = selectedTab.value == DownloadMediaType.VIDEO
                        Surface(
                            onClick = { selectedTab.value = DownloadMediaType.VIDEO },
                            shape = RoundedCornerShape(14.dp),
                            color = if (videoSelected) colors.accentStrong else colors.surfaceTinted,
                            border = BorderStroke(0.75.dp, if (videoSelected) colors.accentStrong else colors.cardBorder),
                            modifier = Modifier
                                .weight(1f)
                                .height(44.dp)
                                .testTag("video_download_tab_video")
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = "Video (${eligibility.videoOptions.size})",
                                    fontFamily = PlusJakartaFontFamily,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    color = if (videoSelected) colors.onAccentStrong else colors.textPrimary
                                )
                            }
                        }
                        Surface(
                            onClick = { selectedTab.value = DownloadMediaType.AUDIO },
                            shape = RoundedCornerShape(14.dp),
                            color = if (!videoSelected) colors.accentStrong else colors.surfaceTinted,
                            border = BorderStroke(0.75.dp, if (!videoSelected) colors.accentStrong else colors.cardBorder),
                            modifier = Modifier
                                .weight(1f)
                                .height(44.dp)
                                .testTag("video_download_tab_audio")
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = "Audio Only (${eligibility.audioOptions.size})",
                                    fontFamily = PlusJakartaFontFamily,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    color = if (!videoSelected) colors.onAccentStrong else colors.textPrimary
                                )
                            }
                        }
                    }

                    val activeOptions = if (selectedTab.value == DownloadMediaType.VIDEO) {
                        eligibility.videoOptions
                    } else {
                        eligibility.audioOptions
                    }

                    if (activeOptions.isEmpty()) {
                        Text(
                            text = if (selectedTab.value == DownloadMediaType.VIDEO) {
                                "No direct progressive video format is offered by this source. Switch to Audio Only."
                            } else {
                                "No standalone audio stream is available for this video."
                            },
                            fontFamily = PlusJakartaFontFamily,
                            fontSize = 12.5.sp,
                            color = colors.textSecondary
                        )
                    } else {
                        activeOptions.forEach { option ->
                            SelectableOptionRow(
                                label = option.resolutionLabel,
                                subtitle = option.summaryLabel,
                                isSelected = false,
                                colors = colors,
                                modifier = Modifier.testTag("video_dl_opt_${option.mediaType.name.lowercase()}_${option.resolutionLabel}"),
                                onClick = {
                                    onSelectOption(option)
                                    onDismiss()
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

