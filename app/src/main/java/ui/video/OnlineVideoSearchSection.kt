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
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistPlay
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Verified
import androidx.compose.material.icons.rounded.WifiOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.ui.theme.OutfitFontFamily
import com.example.ui.theme.PlusJakartaFontFamily
import domain.video.OnlineVideoChannel
import domain.video.OnlineVideoItem
import domain.video.OnlineVideoPlaylistSummary
import domain.video.VideoSearchFilter
import domain.video.VideoSearchUiState
import ui.online.OnlineErrorCard
import ui.theme.EditorialColors

/**
 * Dedicated Online Video Search controls header (strictly separate from Online Music search):
 * Filter chips (Videos / Channels / Playlists), offline banner, and NewPipe video search suggestions.
 */
@Composable
fun SearchVideoControlsHeader(
    isNetworkOnline: Boolean,
    selectedFilter: VideoSearchFilter,
    query: String,
    suggestions: List<String>,
    colors: EditorialColors,
    onSelectFilter: (VideoSearchFilter) -> Unit,
    onExecuteSuggestion: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("search_video_controls"),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (!isNetworkOnline) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = colors.surfaceTinted,
                border = BorderStroke(0.75.dp, colors.accentStrong.copy(alpha = 0.45f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Rounded.WifiOff,
                        contentDescription = null,
                        tint = colors.accentStrong,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "You're offline. Reconnect to search Online Videos via NewPipe Extractor.",
                        fontFamily = PlusJakartaFontFamily,
                        fontSize = 12.sp,
                        color = colors.textSecondary
                    )
                }
            }
        }

        LazyRow(
            contentPadding = PaddingValues(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(VideoSearchFilter.entries, key = { "vid_flt_${it.id}" }) { filter ->
                val active = filter == selectedFilter
                Surface(
                    onClick = { onSelectFilter(filter) },
                    shape = RoundedCornerShape(18.dp),
                    color = if (active) colors.accentPill else colors.surfaceTinted,
                    border = BorderStroke(0.75.dp, if (active) colors.accentStrong else colors.cardBorder),
                    modifier = Modifier
                        .height(38.dp)
                        .testTag("search_video_filter_${filter.id}")
                ) {
                    Box(
                        modifier = Modifier.padding(horizontal = 14.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = filter.label,
                            fontFamily = PlusJakartaFontFamily,
                            fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
                            fontSize = 12.5.sp,
                            color = if (active) colors.onAccentPill else colors.textSecondary
                        )
                    }
                }
            }
        }

        if (suggestions.isNotEmpty() && query.isNotBlank()) {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(suggestions, key = { "vid_sug_$it" }) { sug ->
                    Surface(
                        onClick = { onExecuteSuggestion(sug) },
                        shape = RoundedCornerShape(16.dp),
                        color = colors.surfaceElevated,
                        border = BorderStroke(0.75.dp, colors.cardBorder),
                        modifier = Modifier.height(36.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Search,
                                contentDescription = null,
                                tint = colors.accentStrong,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = sug,
                                fontFamily = PlusJakartaFontFamily,
                                fontSize = 12.sp,
                                color = colors.textPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Dedicated LazyListScope content for Online Video Search results (Videos, Channels, Playlists, History).
 */
fun LazyListScope.onlineVideoSearchLazyContent(
    searchState: VideoSearchUiState,
    searchHistory: List<String>,
    query: String,
    activeVideoId: String?,
    colors: EditorialColors,
    onExecuteSearch: (String) -> Unit,
    onRemoveHistory: (String) -> Unit,
    onClearHistory: () -> Unit,
    onPlayVideo: (OnlineVideoItem, List<OnlineVideoItem>) -> Unit,
    onOpenChannel: (OnlineVideoChannel) -> Unit,
    onOpenPlaylist: (OnlineVideoPlaylistSummary) -> Unit,
    onVideoMoreClick: (OnlineVideoItem) -> Unit,
    onLoadMore: () -> Unit
) {
    when (searchState) {
        is VideoSearchUiState.Idle -> {
            if (searchHistory.isNotEmpty()) {
                item(key = "vid_search_history_hdr") {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 22.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Rounded.History,
                                contentDescription = null,
                                tint = colors.accentStrong,
                                modifier = Modifier.size(17.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Recent Video Searches",
                                fontFamily = OutfitFontFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                color = colors.textPrimary
                            )
                        }
                        Text(
                            text = "Clear All",
                            fontFamily = PlusJakartaFontFamily,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 12.sp,
                            color = colors.accentStrong,
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .clickable(onClick = onClearHistory)
                                .padding(horizontal = 8.dp, vertical = 6.dp)
                        )
                    }
                }

                items(searchHistory, key = { "vsh_$it" }) { term ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 3.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(colors.surface)
                            .clickable { onExecuteSearch(term) }
                            .padding(start = 14.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.History,
                            contentDescription = null,
                            tint = colors.textMuted,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = term,
                            fontFamily = PlusJakartaFontFamily,
                            fontSize = 14.sp,
                            color = colors.textPrimary,
                            modifier = Modifier.weight(1f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        IconButton(
                            onClick = { onRemoveHistory(term) },
                            modifier = Modifier.size(48.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Close,
                                contentDescription = "Remove $term",
                                tint = colors.textMuted,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            } else {
                item(key = "vid_search_empty_prompt") {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 28.dp, vertical = 44.dp)
                            .testTag("search_video_empty_prompt"),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            modifier = Modifier
                                .size(64.dp)
                                .background(colors.accentPill, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.PlayCircle,
                                contentDescription = null,
                                tint = colors.onAccentPill,
                                modifier = Modifier.size(30.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(14.dp))
                        Text(
                            text = "Search Online Videos",
                            fontFamily = OutfitFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            color = colors.textPrimary
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Search videos, creators, channels, and playlists directly via NewPipe Extractor.",
                            fontFamily = PlusJakartaFontFamily,
                            fontSize = 13.sp,
                            color = colors.textSecondary,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }

        is VideoSearchUiState.Loading -> {
            item(key = "vid_search_loading") {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(36.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    CircularProgressIndicator(color = colors.accentStrong)
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Searching videos for \"${searchState.query}\"...",
                        fontFamily = PlusJakartaFontFamily,
                        fontSize = 13.sp,
                        color = colors.textSecondary
                    )
                }
            }
        }

        is VideoSearchUiState.Error -> {
            item(key = "vid_search_error") {
                OnlineErrorCard(
                    kind = searchState.kind,
                    message = searchState.message,
                    showRetry = searchState.isRetryable,
                    colors = colors,
                    onRetry = { onExecuteSearch(query) }
                )
            }
        }

        is VideoSearchUiState.Success -> {
            if (searchState.isEmpty) {
                item(key = "vid_search_no_results") {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(28.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No ${searchState.filter.label.lowercase()} found for \"${searchState.query}\".",
                            fontFamily = PlusJakartaFontFamily,
                            fontSize = 14.sp,
                            color = colors.textSecondary,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            } else {
                items(searchState.videos, key = { "vs_vid_${it.videoId}" }) { video ->
                    CompactVideoRowCard(
                        video = video,
                        colors = colors,
                        isPlayingNow = video.videoId == activeVideoId,
                        onClick = { onPlayVideo(video, searchState.videos) },
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
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)
                    )
                }

                items(searchState.channels, key = { "vs_ch_${it.channelId}" }) { channel ->
                    Surface(
                        onClick = { onOpenChannel(channel) },
                        shape = RoundedCornerShape(16.dp),
                        color = colors.surface,
                        border = BorderStroke(0.75.dp, colors.cardBorder),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 4.dp)
                            .testTag("video_search_channel_${channel.channelId}")
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            ChannelAvatarBadge(
                                avatarUrl = channel.avatarUrl,
                                channelName = channel.name,
                                colors = colors,
                                sizeDp = 48
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = channel.name,
                                        fontFamily = OutfitFontFamily,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.5.sp,
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
                                            modifier = Modifier.size(15.dp)
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
                    }
                }

                items(searchState.playlists, key = { "vs_pl_${it.externalUrl ?: it.name}" }) { pl ->
                    Surface(
                        onClick = { onOpenPlaylist(pl) },
                        shape = RoundedCornerShape(16.dp),
                        color = colors.surface,
                        border = BorderStroke(0.75.dp, colors.cardBorder),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 4.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .width(110.dp)
                                    .aspectRatio(16f / 9f)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(colors.surfaceTinted),
                                contentAlignment = Alignment.Center
                            ) {
                                if (!pl.displayThumbnailUrl.isNullOrBlank()) {
                                    AsyncImage(
                                        model = pl.displayThumbnailUrl,
                                        contentDescription = pl.name,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                } else {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Rounded.PlaylistPlay,
                                        contentDescription = null,
                                        tint = colors.accentStrong,
                                        modifier = Modifier.size(28.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = pl.name,
                                    fontFamily = OutfitFontFamily,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    color = colors.textPrimary,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                                val subtitle = listOfNotNull(
                                    pl.channelName,
                                    pl.videoCount.takeIf { it > 0L }?.let { "$it videos" }
                                ).joinToString(" • ")
                                if (subtitle.isNotBlank()) {
                                    Text(
                                        text = subtitle,
                                        fontFamily = PlusJakartaFontFamily,
                                        fontSize = 11.5.sp,
                                        color = colors.textSecondary
                                    )
                                }
                            }
                        }
                    }
                }

                if (searchState.nextPageToken != null) {
                    item(key = "vid_search_load_more") {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp, vertical = 12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            OutlinedButton(
                                onClick = onLoadMore,
                                enabled = !searchState.isLoadingMore,
                                shape = RoundedCornerShape(20.dp),
                                border = BorderStroke(1.dp, colors.accentStrong),
                                modifier = Modifier
                                    .height(48.dp)
                                    .testTag("search_video_load_more")
                            ) {
                                if (searchState.isLoadingMore) {
                                    CircularProgressIndicator(
                                        strokeWidth = 2.dp,
                                        color = colors.accentStrong,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                }
                                Text(
                                    text = if (searchState.isLoadingMore) "Loading More..." else "Load More Videos",
                                    fontFamily = PlusJakartaFontFamily,
                                    fontWeight = FontWeight.Bold,
                                    color = colors.accentStrong
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
