package ui.video

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistPlay
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.NotificationsOff
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material.icons.rounded.Subscriptions
import androidx.compose.material.icons.rounded.ThumbUp
import androidx.compose.material.icons.rounded.WatchLater
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.OutfitFontFamily
import com.example.ui.theme.PlusJakartaFontFamily
import domain.video.ChannelNotificationPreference
import domain.video.LikedVideoSortOption
import domain.video.OnlineVideoChannel
import domain.video.OnlineVideoItem
import domain.video.OnlineVideoPlaylistSummary
import ui.theme.EditorialColors

enum class VideoLibraryCategory(val id: String, val label: String) {
    LIKED("liked", "Liked Videos"),
    WATCH_LATER("watch_later", "Watch Later"),
    HISTORY("history", "Video History"),
    SAVED("saved", "Saved Videos"),
    CHANNELS("channels", "Followed Channels"),
    PLAYLISTS("playlists", "Video Playlists")
}

/**
 * Online Video Library section integrated inside SONORA's Library screen:
 * Liked Videos, Watch Later, Video History (with clear history), Saved Videos, Subscribed Channels, and Video Playlists.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OnlineVideoLibraryContent(
    likedVideos: List<OnlineVideoItem> = emptyList(),
    watchLaterVideos: List<OnlineVideoItem>,
    videoHistory: List<OnlineVideoItem>,
    savedVideos: List<OnlineVideoItem>,
    subscribedChannels: List<OnlineVideoChannel>,
    videoPlaylists: List<OnlineVideoPlaylistSummary>,
    activeVideoId: String?,
    colors: EditorialColors,
    onPlayVideo: (OnlineVideoItem, List<OnlineVideoItem>) -> Unit,
    onPlayAllLikedVideos: (List<OnlineVideoItem>) -> Unit = { list ->
        list.firstOrNull()?.let { onPlayVideo(it, list) }
    },
    onAddVideoToQueue: (OnlineVideoItem) -> Unit = {},
    onUnlikeVideo: (String) -> Unit = {},
    onClearAllLikedVideos: () -> Unit = {},
    onRemoveWatchLater: (OnlineVideoItem) -> Unit,
    onRemoveFromHistory: (String) -> Unit,
    onClearVideoHistory: () -> Unit,
    onRemoveSavedVideo: (OnlineVideoItem) -> Unit,
    onOpenChannel: (OnlineVideoChannel) -> Unit,
    onRemoveChannelBookmark: (OnlineVideoChannel) -> Unit,
    onUpdateChannelNotification: ((OnlineVideoChannel, ChannelNotificationPreference) -> Unit)? = null,
    onCreatePlaylist: (String) -> Unit,
    onRenamePlaylist: (Long, String) -> Unit,
    onDeletePlaylist: (Long) -> Unit,
    onRemoveVideoFromPlaylist: (Long, String) -> Unit,
    onVideoMoreClick: (OnlineVideoItem) -> Unit,
    onExploreOnlineVideos: () -> Unit,
    hasVideoMiniPlayer: Boolean = activeVideoId != null,
    modifier: Modifier = Modifier
) {
    var selectedCategory by remember { mutableStateOf(VideoLibraryCategory.LIKED) }
    var likedSortOption by remember { mutableStateOf(LikedVideoSortOption.RECENTLY_LIKED) }
    var showClearLikedDialog by remember { mutableStateOf(false) }
    var showClearHistoryDialog by remember { mutableStateOf(false) }
    var showCreatePlaylistDialog by remember { mutableStateOf(false) }
    var activePlaylistId by remember { mutableStateOf<Long?>(null) }
    var playlistToRename by remember { mutableStateOf<OnlineVideoPlaylistSummary?>(null) }
    var channelForNotifications by remember { mutableStateOf<OnlineVideoChannel?>(null) }

    val sortedLikedVideos = remember(likedVideos, likedSortOption) {
        LikedVideoSortOption.sort(likedVideos, likedSortOption)
    }

    val activePlaylist = remember(activePlaylistId, videoPlaylists) {
        videoPlaylists.firstOrNull { it.id == activePlaylistId }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .testTag("online_video_library_content")
    ) {
        // Category chips
        LazyRow(
            contentPadding = PaddingValues(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(VideoLibraryCategory.entries, key = { "vlc_${it.id}" }) { category ->
                val selected = category == selectedCategory
                val count = when (category) {
                    VideoLibraryCategory.LIKED -> likedVideos.size
                    VideoLibraryCategory.WATCH_LATER -> watchLaterVideos.size
                    VideoLibraryCategory.HISTORY -> videoHistory.size
                    VideoLibraryCategory.SAVED -> savedVideos.size
                    VideoLibraryCategory.CHANNELS -> subscribedChannels.size
                    VideoLibraryCategory.PLAYLISTS -> videoPlaylists.size
                }
                Surface(
                    onClick = { selectedCategory = category },
                    shape = RoundedCornerShape(18.dp),
                    color = if (selected) colors.accentPill else colors.surface,
                    border = BorderStroke(0.75.dp, if (selected) colors.accentStrong else colors.cardBorder),
                    modifier = Modifier
                        .height(38.dp)
                        .testTag("video_library_tab_${category.id}")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 13.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${category.label} ($count)",
                            fontFamily = PlusJakartaFontFamily,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                            fontSize = 12.sp,
                            color = if (selected) colors.onAccentPill else colors.textSecondary
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 20.dp,
                end = 20.dp,
                top = 4.dp,
                bottom = domain.player.MusicMiniPlayerVisibilityPolicy.listBottomContentPadding(
                    isMusicMiniPlayerVisible = false,
                    isVideoMiniPlayerVisible = hasVideoMiniPlayer
                )
            ),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            when (selectedCategory) {
                VideoLibraryCategory.LIKED -> {
                    if (sortedLikedVideos.isEmpty()) {
                        item(key = "liked_empty") {
                            VideoLibraryEmptyCard(
                                icon = Icons.Rounded.ThumbUp,
                                title = "No Liked Videos yet",
                                subtitle = "Tap SONORA Like while watching any video to build your personal Liked Videos collection.",
                                actionLabel = "Browse Online Videos",
                                colors = colors,
                                onAction = onExploreOnlineVideos
                            )
                        }
                    } else {
                        item(key = "liked_header_controls") {
                            Column(
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "${sortedLikedVideos.size} liked ${if (sortedLikedVideos.size == 1) "video" else "videos"}",
                                            fontFamily = OutfitFontFamily,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 14.sp,
                                            color = colors.textPrimary
                                        )
                                        Text(
                                            text = "Saved locally in SONORA",
                                            fontFamily = PlusJakartaFontFamily,
                                            fontSize = 11.5.sp,
                                            color = colors.textMuted
                                        )
                                    }
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Button(
                                            onClick = { onPlayAllLikedVideos(sortedLikedVideos) },
                                            shape = RoundedCornerShape(16.dp),
                                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = colors.accentStrong,
                                                contentColor = colors.onAccentStrong
                                            ),
                                            modifier = Modifier.testTag("play_all_liked_videos_button")
                                        ) {
                                            Icon(
                                                imageVector = Icons.Rounded.PlayArrow,
                                                contentDescription = null,
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(
                                                text = "Play All",
                                                fontFamily = PlusJakartaFontFamily,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 12.sp
                                            )
                                        }
                                        TextButton(
                                            onClick = { showClearLikedDialog = true },
                                            modifier = Modifier.testTag("clear_all_liked_videos_button")
                                        ) {
                                            Text(
                                                text = "Clear All",
                                                fontFamily = PlusJakartaFontFamily,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 12.sp,
                                                color = colors.accentStrong
                                            )
                                        }
                                    }
                                }

                                // Sort chips: Recently Liked, Title, Channel, Date
                                LazyRow(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    items(LikedVideoSortOption.entries, key = { "liked_sort_${it.id}" }) { option ->
                                        val isSelected = option == likedSortOption
                                        Surface(
                                            onClick = { likedSortOption = option },
                                            shape = RoundedCornerShape(14.dp),
                                            color = if (isSelected) colors.accentPill else colors.surfaceTinted,
                                            border = BorderStroke(
                                                width = if (isSelected) 1.dp else 0.6.dp,
                                                color = if (isSelected) colors.accentStrong else colors.cardBorder
                                            ),
                                            modifier = Modifier
                                                .height(34.dp)
                                                .testTag("liked_sort_${option.id}")
                                        ) {
                                            Box(
                                                modifier = Modifier.padding(horizontal = 12.dp),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text(
                                                    text = option.label,
                                                    fontFamily = PlusJakartaFontFamily,
                                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                                    fontSize = 11.5.sp,
                                                    color = if (isSelected) colors.onAccentPill else colors.textSecondary
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        items(sortedLikedVideos, key = { "lk_${it.videoId}" }) { video ->
                            CompactVideoRowCard(
                                video = video,
                                colors = colors,
                                isPlayingNow = video.videoId == activeVideoId,
                                extraMetaText = video.formattedLikedDate(),
                                onClick = { onPlayVideo(video, sortedLikedVideos) },
                                onChannelClick = video.channelUrl?.takeIf { it.isNotBlank() }?.let { url ->
                                    {
                                        onOpenChannel(
                                            OnlineVideoChannel(
                                                channelId = data.video.OnlineVideoDataSource.extractChannelIdFromUrl(url)
                                                    .ifBlank { video.channelName },
                                                channelUrl = url,
                                                name = video.channelName,
                                                avatarUrl = video.channelAvatarUrl
                                            )
                                        )
                                    }
                                },
                                onMoreClick = { onVideoMoreClick(video) },
                                secondaryActionIcon = Icons.AutoMirrored.Rounded.QueueMusic,
                                secondaryActionDescription = "Add ${video.title} to queue",
                                onSecondaryAction = { onAddVideoToQueue(video) },
                                trailingActionIcon = Icons.Rounded.Close,
                                trailingActionDescription = "Remove ${video.title} from Liked Videos",
                                onTrailingAction = { onUnlikeVideo(video.videoId) }
                            )
                        }
                    }
                }

                VideoLibraryCategory.WATCH_LATER -> {
                    if (watchLaterVideos.isEmpty()) {
                        item {
                            VideoLibraryEmptyCard(
                                icon = Icons.Rounded.WatchLater,
                                title = "Watch Later is empty",
                                subtitle = "Tap Watch Later on any video to save it here for quick playback.",
                                actionLabel = "Browse Online Videos",
                                colors = colors,
                                onAction = onExploreOnlineVideos
                            )
                        }
                    } else {
                        items(watchLaterVideos, key = { "wl_${it.videoId}" }) { video ->
                            CompactVideoRowCard(
                                video = video,
                                colors = colors,
                                isPlayingNow = video.videoId == activeVideoId,
                                onClick = { onPlayVideo(video, watchLaterVideos) },
                                onMoreClick = { onVideoMoreClick(video) },
                                trailingActionIcon = Icons.Rounded.Close,
                                trailingActionDescription = "Remove from Watch Later",
                                onTrailingAction = { onRemoveWatchLater(video) }
                            )
                        }
                    }
                }

                VideoLibraryCategory.HISTORY -> {
                    if (videoHistory.isNotEmpty()) {
                        item(key = "history_clear_bar") {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "${videoHistory.size} recently watched",
                                    fontFamily = PlusJakartaFontFamily,
                                    fontSize = 12.5.sp,
                                    color = colors.textSecondary
                                )
                                TextButton(
                                    onClick = { showClearHistoryDialog = true },
                                    modifier = Modifier.testTag("clear_video_history_button")
                                ) {
                                    Text(
                                        text = "Clear History",
                                        fontFamily = PlusJakartaFontFamily,
                                        fontWeight = FontWeight.Bold,
                                        color = colors.accentStrong
                                    )
                                }
                            }
                        }
                        items(videoHistory, key = { "vh_${it.videoId}" }) { video ->
                            CompactVideoRowCard(
                                video = video,
                                colors = colors,
                                isPlayingNow = video.videoId == activeVideoId,
                                onClick = { onPlayVideo(video, videoHistory) },
                                onChannelClick = video.channelUrl?.takeIf { it.isNotBlank() }?.let { url ->
                                    {
                                        onOpenChannel(
                                            OnlineVideoChannel(
                                                channelId = data.video.OnlineVideoDataSource.extractChannelIdFromUrl(url)
                                                    .ifBlank { video.channelName },
                                                channelUrl = url,
                                                name = video.channelName,
                                                avatarUrl = video.channelAvatarUrl
                                            )
                                        )
                                    }
                                },
                                onMoreClick = { onVideoMoreClick(video) },
                                trailingActionIcon = Icons.Rounded.Close,
                                trailingActionDescription = "Remove from history",
                                onTrailingAction = { onRemoveFromHistory(video.videoId) }
                            )
                        }
                    } else {
                        item {
                            VideoLibraryEmptyCard(
                                icon = Icons.Rounded.History,
                                title = "No Video History yet",
                                subtitle = "Videos you watch in SONORA appear here with automatic resume progress.",
                                actionLabel = "Explore Online Videos",
                                colors = colors,
                                onAction = onExploreOnlineVideos
                            )
                        }
                    }
                }

                VideoLibraryCategory.SAVED -> {
                    if (savedVideos.isEmpty()) {
                        item {
                            VideoLibraryEmptyCard(
                                icon = Icons.Rounded.Bookmark,
                                title = "No Saved Videos",
                                subtitle = "Bookmark your favorite videos to revisit them anytime without searching.",
                                actionLabel = "Discover Videos",
                                colors = colors,
                                onAction = onExploreOnlineVideos
                            )
                        }
                    } else {
                        items(savedVideos, key = { "sv_${it.videoId}" }) { video ->
                            CompactVideoRowCard(
                                video = video,
                                colors = colors,
                                isPlayingNow = video.videoId == activeVideoId,
                                onClick = { onPlayVideo(video, savedVideos) },
                                onChannelClick = video.channelUrl?.takeIf { it.isNotBlank() }?.let { url ->
                                    {
                                        onOpenChannel(
                                            OnlineVideoChannel(
                                                channelId = data.video.OnlineVideoDataSource.extractChannelIdFromUrl(url)
                                                    .ifBlank { video.channelName },
                                                channelUrl = url,
                                                name = video.channelName,
                                                avatarUrl = video.channelAvatarUrl
                                            )
                                        )
                                    }
                                },
                                onMoreClick = { onVideoMoreClick(video) },
                                trailingActionIcon = Icons.Rounded.Close,
                                trailingActionDescription = "Remove saved video",
                                onTrailingAction = { onRemoveSavedVideo(video) }
                            )
                        }
                    }
                }

                VideoLibraryCategory.CHANNELS -> {
                    if (subscribedChannels.isEmpty()) {
                        item {
                            VideoLibraryEmptyCard(
                                icon = Icons.Rounded.Subscriptions,
                                title = "No Followed Channels",
                                subtitle = "Follow channels locally in SONORA or connect an authorized YouTube account to keep track of creators here.",
                                actionLabel = "Find Channels",
                                colors = colors,
                                onAction = onExploreOnlineVideos
                            )
                        }
                    } else {
                        items(subscribedChannels, key = { "sub_ch_${it.channelId}" }) { channel ->
                            Surface(
                                onClick = { onOpenChannel(channel) },
                                shape = RoundedCornerShape(16.dp),
                                color = colors.surface,
                                border = BorderStroke(0.75.dp, colors.cardBorder),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("subscribed_channel_row_${channel.channelId}")
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    ChannelAvatarBadge(
                                        avatarUrl = channel.avatarUrl,
                                        channelName = channel.name,
                                        colors = colors,
                                        sizeDp = 44
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = channel.name,
                                            fontFamily = OutfitFontFamily,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 14.5.sp,
                                            color = colors.textPrimary,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        val alertsOn = channel.notificationPreference == ChannelNotificationPreference.ALL
                                        val statusLabel = if (alertsOn) {
                                            "Subscribed in SONORA • Alerts On"
                                        } else {
                                            "Subscribed in SONORA"
                                        }
                                        val detail = listOfNotNull(
                                            channel.formattedSubscribers(),
                                            statusLabel
                                        ).joinToString(" • ")
                                        Text(
                                            text = detail,
                                            fontFamily = PlusJakartaFontFamily,
                                            fontSize = 11.5.sp,
                                            color = colors.textSecondary,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    if (onUpdateChannelNotification != null) {
                                        val alertsOn = channel.notificationPreference == ChannelNotificationPreference.ALL
                                        IconButton(
                                            onClick = { channelForNotifications = channel },
                                            modifier = Modifier.testTag("channel_notif_toggle_${channel.channelId}")
                                        ) {
                                            Icon(
                                                imageVector = if (alertsOn) Icons.Rounded.NotificationsActive else Icons.Rounded.NotificationsOff,
                                                contentDescription = "Manage notifications for ${channel.name}",
                                                tint = if (alertsOn) colors.accentStrong else colors.textSecondary
                                            )
                                        }
                                    }
                                    IconButton(
                                        onClick = { onRemoveChannelBookmark(channel) },
                                        modifier = Modifier.testTag("unfollow_channel_${channel.channelId}")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.DeleteOutline,
                                            contentDescription = "Unsubscribe from ${channel.name}",
                                            tint = colors.textSecondary
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                VideoLibraryCategory.PLAYLISTS -> {
                    item(key = "create_video_playlist_btn") {
                        Surface(
                            onClick = { showCreatePlaylistDialog = true },
                            shape = RoundedCornerShape(16.dp),
                            color = colors.accentPill,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("create_video_playlist_button")
                        ) {
                            Row(
                                modifier = Modifier.fillMaxSize(),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Add,
                                    contentDescription = null,
                                    tint = colors.onAccentPill,
                                    modifier = Modifier.size(19.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Create Video Playlist",
                                    fontFamily = OutfitFontFamily,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.5.sp,
                                    color = colors.onAccentPill
                                )
                            }
                        }
                    }

                    if (videoPlaylists.isEmpty()) {
                        item {
                            VideoLibraryEmptyCard(
                                icon = Icons.AutoMirrored.Rounded.PlaylistPlay,
                                title = "No Video Playlists yet",
                                subtitle = "Organize music videos, tutorials, and regional favorites into playlists.",
                                actionLabel = "Create Playlist",
                                colors = colors,
                                onAction = { showCreatePlaylistDialog = true }
                            )
                        }
                    } else {
                        items(videoPlaylists, key = { "vpl_${it.id}" }) { pl ->
                            Surface(
                                onClick = { activePlaylistId = pl.id },
                                shape = RoundedCornerShape(16.dp),
                                color = colors.surface,
                                border = BorderStroke(0.75.dp, colors.cardBorder),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("video_playlist_row_${pl.id}")
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(46.dp)
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(colors.accentPill),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Rounded.PlaylistPlay,
                                            contentDescription = null,
                                            tint = colors.onAccentPill,
                                            modifier = Modifier.size(24.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = pl.name,
                                            fontFamily = OutfitFontFamily,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 14.5.sp,
                                            color = colors.textPrimary,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = "${pl.videos.size} videos",
                                            fontFamily = PlusJakartaFontFamily,
                                            fontSize = 12.sp,
                                            color = colors.textSecondary
                                        )
                                    }
                                    IconButton(onClick = { playlistToRename = pl }) {
                                        Icon(Icons.Rounded.Edit, contentDescription = "Rename", tint = colors.textSecondary, modifier = Modifier.size(19.dp))
                                    }
                                    IconButton(onClick = { onDeletePlaylist(pl.id) }) {
                                        Icon(Icons.Rounded.DeleteOutline, contentDescription = "Delete", tint = colors.textSecondary, modifier = Modifier.size(19.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showClearHistoryDialog) {
        AlertDialog(
            onDismissRequest = { showClearHistoryDialog = false },
            containerColor = colors.surface,
            title = { Text("Clear Video History?", fontFamily = OutfitFontFamily, fontWeight = FontWeight.Bold) },
            text = { Text("This clears all watched videos and resume timestamps stored on this device.", fontFamily = PlusJakartaFontFamily) },
            confirmButton = {
                Button(
                    onClick = {
                        onClearVideoHistory()
                        showClearHistoryDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = colors.accentStrong)
                ) {
                    Text("Clear", color = colors.onAccentStrong)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearHistoryDialog = false }) {
                    Text("Cancel", color = colors.textSecondary)
                }
            }
        )
    }

    if (showCreatePlaylistDialog) {
        var name by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showCreatePlaylistDialog = false },
            containerColor = colors.surface,
            title = { Text("New Video Playlist", fontFamily = OutfitFontFamily, fontWeight = FontWeight.Bold) },
            text = {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    placeholder = { Text("Playlist name...") },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = colors.accentStrong,
                        unfocusedBorderColor = colors.cardBorder
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (name.isNotBlank()) {
                            onCreatePlaylist(name.trim())
                            showCreatePlaylistDialog = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = colors.accentStrong)
                ) {
                    Text("Create", color = colors.onAccentStrong)
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreatePlaylistDialog = false }) {
                    Text("Cancel", color = colors.textSecondary)
                }
            }
        )
    }

    playlistToRename?.let { target ->
        var updated by remember(target.id) { mutableStateOf(target.name) }
        AlertDialog(
            onDismissRequest = { playlistToRename = null },
            containerColor = colors.surface,
            title = { Text("Rename Video Playlist", fontFamily = OutfitFontFamily, fontWeight = FontWeight.Bold) },
            text = {
                OutlinedTextField(
                    value = updated,
                    onValueChange = { updated = it },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (updated.isNotBlank()) {
                            onRenamePlaylist(target.id, updated.trim())
                            playlistToRename = null
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = colors.accentStrong)
                ) {
                    Text("Save", color = colors.onAccentStrong)
                }
            },
            dismissButton = {
                TextButton(onClick = { playlistToRename = null }) {
                    Text("Cancel", color = colors.textSecondary)
                }
            }
        )
    }

    activePlaylist?.let { pl ->
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { activePlaylistId = null },
            sheetState = sheetState,
            containerColor = colors.surface,
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp)
                    .padding(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = pl.name,
                            fontFamily = OutfitFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 19.sp,
                            color = colors.textPrimary
                        )
                        Text(
                            text = "${pl.videos.size} videos",
                            fontFamily = PlusJakartaFontFamily,
                            fontSize = 12.sp,
                            color = colors.textSecondary
                        )
                    }
                    if (pl.videos.isNotEmpty()) {
                        Button(
                            onClick = {
                                onPlayVideo(pl.videos.first(), pl.videos)
                                activePlaylistId = null
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = colors.accentStrong)
                        ) {
                            Icon(Icons.Rounded.PlayArrow, contentDescription = null, modifier = Modifier.size(17.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Play All", color = colors.onAccentStrong)
                        }
                    }
                }
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(pl.videos, key = { "pld_${it.videoId}" }) { vid ->
                        CompactVideoRowCard(
                            video = vid,
                            colors = colors,
                            isPlayingNow = vid.videoId == activeVideoId,
                            onClick = {
                                onPlayVideo(vid, pl.videos)
                                activePlaylistId = null
                            },
                            onMoreClick = { onVideoMoreClick(vid) },
                            trailingActionIcon = Icons.Rounded.Close,
                            trailingActionDescription = "Remove from playlist",
                            onTrailingAction = { onRemoveVideoFromPlaylist(pl.id, vid.videoId) }
                        )
                    }
                }
            }
        }
    }

    if (showClearLikedDialog) {
        AlertDialog(
            onDismissRequest = { showClearLikedDialog = false },
            containerColor = colors.surface,
            title = {
                Text(
                    text = "Clear All Liked Videos?",
                    fontFamily = OutfitFontFamily,
                    fontWeight = FontWeight.Bold,
                    color = colors.textPrimary
                )
            },
            text = {
                Text(
                    text = "This removes all ${likedVideos.size} locally liked videos from your SONORA library and clears their Like states.",
                    fontFamily = PlusJakartaFontFamily,
                    color = colors.textSecondary
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onClearAllLikedVideos()
                        showClearLikedDialog = false
                    },
                    modifier = Modifier.testTag("confirm_clear_all_liked_videos")
                ) {
                    Text(
                        text = "Clear All",
                        fontFamily = PlusJakartaFontFamily,
                        fontWeight = FontWeight.Bold,
                        color = colors.accentStrong
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearLikedDialog = false }) {
                    Text("Cancel", color = colors.textSecondary)
                }
            }
        )
    }

    channelForNotifications?.let { targetChannel ->
        val latestChannel = subscribedChannels.firstOrNull { it.channelId == targetChannel.channelId } ?: targetChannel
        NotificationBellOptionsSheet(
            channel = latestChannel,
            currentPreference = latestChannel.notificationPreference,
            colors = colors,
            onSelectPreference = { pref ->
                onUpdateChannelNotification?.invoke(latestChannel, pref)
                channelForNotifications = null
            },
            onDismiss = { channelForNotifications = null }
        )
    }
}

@Composable
private fun VideoLibraryEmptyCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    actionLabel: String,
    colors: EditorialColors,
    onAction: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(22.dp),
        color = colors.surface,
        border = BorderStroke(0.75.dp, colors.cardBorder),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(58.dp)
                    .clip(CircleShape)
                    .background(colors.accentPill),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = colors.onAccentPill,
                    modifier = Modifier.size(28.dp)
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = title,
                fontFamily = OutfitFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 17.sp,
                color = colors.textPrimary,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(5.dp))
            Text(
                text = subtitle,
                fontFamily = PlusJakartaFontFamily,
                fontSize = 12.5.sp,
                color = colors.textSecondary,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(14.dp))
            Button(
                onClick = onAction,
                shape = RoundedCornerShape(18.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = colors.accentStrong,
                    contentColor = colors.onAccentStrong
                )
            ) {
                Icon(Icons.Rounded.PlayCircle, contentDescription = null, modifier = Modifier.size(17.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(actionLabel, fontFamily = PlusJakartaFontFamily, fontWeight = FontWeight.Bold)
            }
        }
    }
}
