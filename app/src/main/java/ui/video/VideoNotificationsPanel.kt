package ui.video

import android.graphics.Bitmap
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.Done
import androidx.compose.material.icons.rounded.DoneAll
import androidx.compose.material.icons.rounded.NotificationsNone
import androidx.compose.material.icons.rounded.PlayCircleOutline
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import coil.size.Precision
import com.example.R
import com.example.ui.theme.OutfitFontFamily
import com.example.ui.theme.PlusJakartaFontFamily
import domain.video.VideoNotificationItem
import domain.video.VideoNotificationType
import ui.theme.EditorialColors

/**
 * Global Online Video "All Notifications" panel displaying local upload & channel update
 * notifications for SONORA-followed channels with notifications enabled.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VideoNotificationsPanel(
    notifications: List<VideoNotificationItem>,
    isSyncing: Boolean,
    colors: EditorialColors,
    onOpenVideo: (VideoNotificationItem) -> Unit,
    onOpenChannel: (VideoNotificationItem) -> Unit,
    onMarkAsRead: (String) -> Unit,
    onMarkAllRead: () -> Unit,
    onDeleteNotification: (String) -> Unit,
    onClearAll: () -> Unit,
    onCheckNow: () -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = colors.surfaceElevated,
        contentColor = colors.textPrimary,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        modifier = Modifier.testTag("video_notifications_sheet")
    ) {
        VideoNotificationsPanelContent(
            notifications = notifications,
            isSyncing = isSyncing,
            colors = colors,
            onOpenVideo = { item ->
                onDismiss()
                onOpenVideo(item)
            },
            onOpenChannel = { item ->
                onDismiss()
                onOpenChannel(item)
            },
            onMarkAsRead = onMarkAsRead,
            onMarkAllRead = onMarkAllRead,
            onDeleteNotification = onDeleteNotification,
            onClearAll = onClearAll,
            onCheckNow = onCheckNow,
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
        )
    }
}

@Composable
fun VideoNotificationsPanelContent(
    notifications: List<VideoNotificationItem>,
    isSyncing: Boolean,
    colors: EditorialColors,
    onOpenVideo: (VideoNotificationItem) -> Unit,
    onOpenChannel: (VideoNotificationItem) -> Unit,
    onMarkAsRead: (String) -> Unit,
    onMarkAllRead: () -> Unit,
    onDeleteNotification: (String) -> Unit,
    onClearAll: () -> Unit,
    onCheckNow: () -> Unit,
    modifier: Modifier = Modifier
) {
    val unreadCount = remember(notifications) { notifications.count { !it.isRead } }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(bottom = 12.dp)
            .testTag("video_notifications_panel_content")
    ) {
        // Header Row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = stringResource(R.string.video_notifications_title),
                        fontFamily = OutfitFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp,
                        color = colors.textPrimary,
                        modifier = Modifier.testTag("video_notifications_title")
                    )
                    if (unreadCount > 0) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = colors.accentStrong.copy(alpha = 0.16f),
                            border = BorderStroke(0.5.dp, colors.accentStrong.copy(alpha = 0.45f))
                        ) {
                            Text(
                                text = "$unreadCount new",
                                fontFamily = PlusJakartaFontFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp,
                                color = colors.accentStrong,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = stringResource(R.string.video_notifications_periodic_note),
                    fontFamily = PlusJakartaFontFamily,
                    fontSize = 11.5.sp,
                    color = colors.textSecondary
                )
            }

            IconButton(
                onClick = onCheckNow,
                enabled = !isSyncing,
                modifier = Modifier
                    .size(48.dp)
                    .testTag("video_notifications_check_now")
            ) {
                if (isSyncing) {
                    CircularProgressIndicator(
                        color = colors.accentStrong,
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(18.dp)
                    )
                } else {
                    Icon(
                        imageVector = Icons.Rounded.Refresh,
                        contentDescription = stringResource(R.string.video_notifications_check_now),
                        tint = colors.accentStrong,
                        modifier = Modifier.size(21.dp)
                    )
                }
            }
        }

        // Bulk Action Row (Mark all read / Clear all)
        if (notifications.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (unreadCount > 0) {
                    Surface(
                        onClick = onMarkAllRead,
                        shape = RoundedCornerShape(14.dp),
                        color = colors.accentStrong.copy(alpha = 0.14f),
                        border = BorderStroke(0.75.dp, colors.accentStrong.copy(alpha = 0.4f)),
                        modifier = Modifier.testTag("video_notifications_mark_all_read")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.DoneAll,
                                contentDescription = null,
                                tint = colors.accentStrong,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = stringResource(R.string.video_notifications_mark_all_read),
                                fontFamily = PlusJakartaFontFamily,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 12.sp,
                                color = colors.accentStrong
                            )
                        }
                    }
                }

                Surface(
                    onClick = onClearAll,
                    shape = RoundedCornerShape(14.dp),
                    color = colors.surfaceTinted,
                    border = BorderStroke(0.75.dp, colors.cardBorder),
                    modifier = Modifier.testTag("video_notifications_clear_all")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.DeleteSweep,
                            contentDescription = null,
                            tint = colors.textSecondary,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = stringResource(R.string.video_notifications_clear_all),
                            fontFamily = PlusJakartaFontFamily,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 12.sp,
                            color = colors.textPrimary
                        )
                    }
                }
            }
        }

        HorizontalDivider(
            color = colors.cardBorder.copy(alpha = 0.5f),
            modifier = Modifier.padding(top = 4.dp)
        )

        if (notifications.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 28.dp, vertical = 44.dp)
                    .testTag("video_notifications_empty_state"),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .background(colors.accentStrong.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.NotificationsNone,
                        contentDescription = null,
                        tint = colors.accentStrong,
                        modifier = Modifier.size(30.dp)
                    )
                }
                Spacer(modifier = Modifier.height(14.dp))
                Text(
                    text = stringResource(R.string.video_notifications_empty_title),
                    fontFamily = OutfitFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp,
                    color = colors.textPrimary,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = stringResource(R.string.video_notifications_empty_subtitle),
                    fontFamily = PlusJakartaFontFamily,
                    fontSize = 12.5.sp,
                    lineHeight = 18.sp,
                    color = colors.textSecondary,
                    textAlign = TextAlign.Center
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 520.dp)
                    .testTag("video_notifications_list"),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(
                    items = notifications,
                    key = { it.notificationId }
                ) { item ->
                    VideoNotificationCard(
                        item = item,
                        colors = colors,
                        onOpenVideo = { onOpenVideo(item) },
                        onOpenChannel = { onOpenChannel(item) },
                        onMarkAsRead = { onMarkAsRead(item.notificationId) },
                        onDelete = { onDeleteNotification(item.notificationId) }
                    )
                }
            }
        }
    }
}

@Composable
private fun VideoNotificationCard(
    item: VideoNotificationItem,
    colors: EditorialColors,
    onOpenVideo: () -> Unit,
    onOpenChannel: () -> Unit,
    onMarkAsRead: () -> Unit,
    onDelete: () -> Unit
) {
    val context = LocalContext.current
    val isVideoTarget = item.isVideoNotification
    val eventBadge = item.type.badgeLabel
    val timeLabel = remember(item.uploadDateText, item.createdAt) {
        formatNotificationTimestamp(item.uploadDateText, item.createdAt)
    }

    Surface(
        shape = RoundedCornerShape(18.dp),
        color = if (!item.isRead) {
            colors.accentStrong.copy(alpha = 0.08f)
        } else {
            colors.surfaceTinted
        },
        border = BorderStroke(
            width = if (!item.isRead) 1.dp else 0.5.dp,
            color = if (!item.isRead) colors.accentStrong.copy(alpha = 0.35f) else colors.cardBorder
        ),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .clickable {
                if (isVideoTarget) onOpenVideo() else onOpenChannel()
            }
            .testTag("video_notification_item_${item.notificationId}")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 11.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Unread dot + clickable Channel Avatar
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .clickable(onClick = onOpenChannel)
                        .testTag("video_notification_channel_${item.notificationId}"),
                    contentAlignment = Alignment.Center
                ) {
                    ChannelAvatarBadge(
                        avatarUrl = item.channelAvatarUrl,
                        channelName = item.channelName,
                        colors = colors,
                        sizeDp = 40
                    )
                    if (!item.isRead) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(colors.surfaceElevated)
                                .padding(1.5.dp)
                                .clip(CircleShape)
                                .background(colors.accentStrong)
                                .testTag("video_notification_unread_dot_${item.notificationId}")
                        )
                    }
                }

                // Channel Name, Event Label, Video Title & Timestamp
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = item.channelName,
                            fontFamily = PlusJakartaFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            color = colors.accentStrong,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .weight(1f, fill = false)
                                .clickable(onClick = onOpenChannel)
                        )
                        Text(
                            text = "• $eventBadge",
                            fontFamily = PlusJakartaFontFamily,
                            fontWeight = FontWeight.Medium,
                            fontSize = 11.sp,
                            color = colors.textSecondary,
                            maxLines = 1
                        )
                    }
                    Spacer(modifier = Modifier.height(3.dp))
                    Text(
                        text = item.videoTitle?.takeIf { it.isNotBlank() } ?: item.message,
                        fontFamily = OutfitFontFamily,
                        fontWeight = if (!item.isRead) FontWeight.Bold else FontWeight.Medium,
                        fontSize = 13.5.sp,
                        lineHeight = 18.sp,
                        color = colors.textPrimary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (timeLabel.isNotBlank()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = timeLabel,
                            fontFamily = PlusJakartaFontFamily,
                            fontSize = 11.sp,
                            color = colors.textSecondary,
                            maxLines = 1
                        )
                    }
                }

                // Video Thumbnail when available (bounded decode)
                val thumbUrl = item.thumbnailUrl
                if (!thumbUrl.isNullOrBlank()) {
                    val thumbReq = remember(thumbUrl) {
                        ImageRequest.Builder(context)
                            .data(thumbUrl)
                            .size(288, 162)
                            .precision(Precision.INEXACT)
                            .bitmapConfig(Bitmap.Config.RGB_565)
                            .crossfade(false)
                            .build()
                    }
                    Box(
                        modifier = Modifier
                            .width(86.dp)
                            .aspectRatio(16f / 9f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(colors.surfaceElevated),
                        contentAlignment = Alignment.Center
                    ) {
                        AsyncImage(
                            model = thumbReq,
                            contentDescription = item.videoTitle ?: item.channelName,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.matchParentSize()
                        )
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(colors.surfaceElevated),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.PlayCircleOutline,
                            contentDescription = null,
                            tint = colors.textSecondary,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            }

            // Secondary row: Read status / Mark as Read + Clear individual notification
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = if (item.isRead) "Read" else "Unread",
                    fontFamily = PlusJakartaFontFamily,
                    fontWeight = if (item.isRead) FontWeight.Normal else FontWeight.SemiBold,
                    fontSize = 11.sp,
                    color = if (item.isRead) colors.textSecondary else colors.accentStrong
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    if (!item.isRead) {
                        IconButton(
                            onClick = onMarkAsRead,
                            modifier = Modifier
                                .size(48.dp)
                                .testTag("video_notification_mark_read_${item.notificationId}")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Done,
                                contentDescription = "Mark notification as read",
                                tint = colors.accentStrong,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("video_notification_clear_${item.notificationId}")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = "Clear notification",
                            tint = colors.textSecondary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}

internal fun formatNotificationTimestamp(uploadDateText: String?, createdAtMs: Long, nowMs: Long = System.currentTimeMillis()): String {
    if (!uploadDateText.isNullOrBlank()) return uploadDateText.trim()
    val diffMs = (nowMs - createdAtMs).coerceAtLeast(0L)
    val minutes = diffMs / 60_000L
    val hours = minutes / 60L
    val days = hours / 24L
    return when {
        minutes < 1L -> "Just now"
        minutes < 60L -> "${minutes}m ago"
        hours < 24L -> "${hours}h ago"
        days < 7L -> "${days}d ago"
        else -> "${days / 7L}w ago"
    }
}
