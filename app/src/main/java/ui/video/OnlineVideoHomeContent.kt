package ui.video

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.WifiOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import data.video.PersonalizedVideoShelf
import data.video.VideoRecommendationEngine
import domain.video.OnlineVideoItem
import domain.video.VideoHomeSectionType
import domain.video.VideoRegionalLanguage
import domain.video.VideoSectionState
import ui.online.OnlineErrorCard
import ui.theme.EditorialColors

/**
 * SONORA Online Video Home experience powered by NewPipe Extractor.
 * Renders India-prioritized discovery, 8 regional Indian language chips (Hindi, Odia, Tamil,
 * Telugu, Malayalam, Kannada, Bengali, Punjabi), and all 12 curated video sections with
 * skeleton loading, real metadata/artwork only, pagination, and graceful error recovery.
 */
@Composable
fun OnlineVideoHomeContent(
    sections: Map<VideoHomeSectionType, VideoSectionState>,
    recentlyWatched: List<OnlineVideoItem>,
    selectedRegionalLanguage: VideoRegionalLanguage,
    countryCode: String,
    isNetworkOnline: Boolean,
    activeVideoId: String?,
    colors: EditorialColors,
    onOpenVideoSearch: () -> Unit,
    onOpenRegionSheet: () -> Unit,
    onSelectRegionalLanguage: (VideoRegionalLanguage) -> Unit,
    onRefreshAll: () -> Unit,
    onRetrySection: (VideoHomeSectionType) -> Unit,
    onLoadMoreSection: (VideoHomeSectionType) -> Unit,
    onPlayVideo: (OnlineVideoItem, List<OnlineVideoItem>) -> Unit,
    onOpenChannel: (String, String) -> Unit,
    onVideoMoreClick: (OnlineVideoItem) -> Unit,
    personalizedShelves: List<PersonalizedVideoShelf> = emptyList(),
    selectedInterestTopics: List<String> = emptyList(),
    onToggleInterestTopic: ((String) -> Unit)? = null,
    hasVideoMiniPlayer: Boolean = false,
    modifier: Modifier = Modifier
) {
    val orderedSections = listOf(
        VideoHomeSectionType.RECENTLY_WATCHED,
        VideoHomeSectionType.TRENDING,
        VideoHomeSectionType.POPULAR_INDIAN,
        VideoHomeSectionType.POPULAR_MUSIC_VIDEOS,
        VideoHomeSectionType.RECOMMENDED,
        VideoHomeSectionType.REGIONAL,
        VideoHomeSectionType.RECENTLY_UPLOADED,
        VideoHomeSectionType.MUSIC_VIDEOS,
        VideoHomeSectionType.GAMING,
        VideoHomeSectionType.ENTERTAINMENT,
        VideoHomeSectionType.EDUCATIONAL,
        VideoHomeSectionType.TECHNOLOGY
    )

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("home_online_video_content"),
        contentPadding = PaddingValues(
            top = 4.dp,
            bottom = domain.player.MusicMiniPlayerVisibilityPolicy.listBottomContentPadding(
                isMusicMiniPlayerVisible = false,
                isVideoMiniPlayerVisible = hasVideoMiniPlayer
            )
        ),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        // Quick Video Search Bar + Region Selector Pill
        item(key = "video_home_toolbar") {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        onClick = onOpenVideoSearch,
                        shape = RoundedCornerShape(24.dp),
                        color = colors.surface,
                        border = BorderStroke(0.75.dp, colors.cardBorder),
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                            .testTag("video_home_search_bar")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Search,
                                contentDescription = "Search Online Videos",
                                tint = colors.accentStrong,
                                modifier = Modifier.size(19.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "Search videos, channels, playlists...",
                                fontFamily = PlusJakartaFontFamily,
                                fontSize = 13.sp,
                                color = colors.textMuted,
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    Surface(
                        onClick = onOpenRegionSheet,
                        shape = RoundedCornerShape(24.dp),
                        color = colors.accentPill,
                        modifier = Modifier
                            .heightIn(min = 48.dp)
                            .wrapContentWidth()
                            .testTag("video_home_region_button")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Language,
                                contentDescription = "Select Content Region",
                                tint = colors.onAccentPill,
                                modifier = Modifier.size(17.dp)
                            )
                            Spacer(modifier = Modifier.width(5.dp))
                            Text(
                                text = countryCode.uppercase(),
                                fontFamily = OutfitFontFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.5.sp,
                                color = colors.onAccentPill,
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                // India & Regional Language Chips (All India + Hindi, Odia, Tamil, Telugu, Malayalam, Kannada, Bengali, Punjabi)
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.testTag("video_regional_language_row")
                ) {
                    items(VideoRegionalLanguage.entries, key = { "vlang_${it.id}" }) { lang ->
                        val selected = lang == selectedRegionalLanguage
                        Surface(
                            onClick = { onSelectRegionalLanguage(lang) },
                            shape = RoundedCornerShape(18.dp),
                            color = if (selected) colors.accentStrong else colors.surface,
                            border = BorderStroke(
                                width = if (selected) 1.dp else 0.75.dp,
                                color = if (selected) colors.accentStrong else colors.cardBorder
                            ),
                            modifier = Modifier
                                .heightIn(min = 38.dp)
                                .wrapContentWidth()
                                .testTag("video_lang_chip_${lang.id}")
                        ) {
                            Box(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = lang.label,
                                    fontFamily = PlusJakartaFontFamily,
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                    fontSize = 12.5.sp,
                                    color = if (selected) colors.onAccentStrong else colors.textSecondary,
                                    maxLines = 1,
                                    softWrap = false,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }

                if (!isNetworkOnline) {
                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = colors.surfaceTinted,
                        border = BorderStroke(0.75.dp, colors.accentStrong.copy(alpha = 0.45f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp)
                            .testTag("video_offline_banner")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.WifiOff,
                                contentDescription = null,
                                tint = colors.accentStrong,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "You're offline",
                                    fontFamily = OutfitFontFamily,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.5.sp,
                                    color = colors.textPrimary
                                )
                                Text(
                                    text = "Reconnect to stream online videos, or open Library -> Video for your Watch Later list.",
                                    fontFamily = PlusJakartaFontFamily,
                                    fontSize = 12.sp,
                                    color = colors.textSecondary
                                )
                            }
                        }
                    }
                }
                // User Interest Chips for SONORA's transparent on-device recommendations
                if (onToggleInterestTopic != null) {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 20.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.testTag("video_interest_topics_row")
                    ) {
                        items(VideoRecommendationEngine.DEFAULT_INTEREST_TOPICS, key = { "vtopic_$it" }) { topic ->
                            val selected = selectedInterestTopics.any { it.equals(topic, ignoreCase = true) }
                            Surface(
                                onClick = { onToggleInterestTopic(topic) },
                                shape = RoundedCornerShape(16.dp),
                                color = if (selected) colors.accentPill else colors.surfaceTinted,
                                border = BorderStroke(
                                    width = if (selected) 1.dp else 0.6.dp,
                                    color = if (selected) colors.accentStrong else colors.cardBorder
                                ),
                                modifier = Modifier
                                    .height(34.dp)
                                    .testTag("video_interest_chip_${topic.lowercase()}")
                            ) {
                                Box(
                                    modifier = Modifier.padding(horizontal = 12.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = if (selected) "✓ $topic" else topic,
                                        fontFamily = PlusJakartaFontFamily,
                                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                        fontSize = 11.5.sp,
                                        color = if (selected) colors.onAccentPill else colors.textSecondary
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Personalized Video Recommendation Shelves (Continue Watching, Recommended for You, Trending, Similar, New Creators)
        personalizedShelves.forEach { shelf ->
            item(key = "pers_shelf_${shelf.id}") {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("personalized_video_shelf_${shelf.id}")
                ) {
                    VideoSectionHeader(
                        title = shelf.title,
                        subtitle = shelf.subtitle,
                        colors = colors
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    if (shelf.isCarousel) {
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 20.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(shelf.items, key = { "${shelf.id}_${it.videoId}" }) { video ->
                                HorizontalVideoCarouselCard(
                                    video = video,
                                    colors = colors,
                                    isPlayingNow = video.videoId == activeVideoId,
                                    onClick = { onPlayVideo(video, shelf.items) },
                                    onChannelClick = video.channelUrl?.takeIf { it.isNotBlank() }?.let { url ->
                                        { onOpenChannel(url, video.channelName) }
                                    },
                                    onMoreClick = { onVideoMoreClick(video) }
                                )
                            }
                        }
                    } else {
                        Column(
                            modifier = Modifier.padding(horizontal = 20.dp),
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            shelf.items.take(8).forEach { video ->
                                OnlineVideoCard(
                                    video = video,
                                    colors = colors,
                                    isPlayingNow = video.videoId == activeVideoId,
                                    onClick = { onPlayVideo(video, shelf.items) },
                                    onChannelClick = video.channelUrl?.let { url ->
                                        { onOpenChannel(url, video.channelName) }
                                    },
                                    onMoreClick = { onVideoMoreClick(video) }
                                )
                            }
                        }
                    }
                }
            }
        }

        // All 12 Curated Video Sections
        orderedSections.forEach { section ->
            if (section == VideoHomeSectionType.RECENTLY_WATCHED) {
                if (recentlyWatched.isNotEmpty()) {
                    item(key = "vid_sec_${section.id}") {
                        VideoSectionHeader(
                            title = section.title,
                            subtitle = section.subtitle,
                            colors = colors
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 20.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.testTag("video_section_${section.id}")
                        ) {
                            items(recentlyWatched.take(15), key = { "rw_${it.videoId}" }) { video ->
                                HorizontalVideoCarouselCard(
                                    video = video,
                                    colors = colors,
                                    isPlayingNow = video.videoId == activeVideoId,
                                    onClick = { onPlayVideo(video, recentlyWatched) },
                                    onChannelClick = video.channelUrl?.takeIf { it.isNotBlank() }?.let { url ->
                                        { onOpenChannel(url, video.channelName) }
                                    },
                                    onMoreClick = { onVideoMoreClick(video) }
                                )
                            }
                        }
                    }
                }
            } else {
                val state = sections[section] ?: VideoSectionState.Idle
                item(key = "vid_sec_${section.id}") {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("video_section_${section.id}")
                    ) {
                        VideoSectionHeader(
                            title = section.title,
                            subtitle = if (section == VideoHomeSectionType.REGIONAL && selectedRegionalLanguage != VideoRegionalLanguage.ALL_INDIA) {
                                "${selectedRegionalLanguage.label} picks across India"
                            } else {
                                section.subtitle
                            },
                            colors = colors,
                            trailingRefresh = state is VideoSectionState.Error,
                            onRefreshClick = { onRetrySection(section) }
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        when (state) {
                            is VideoSectionState.Idle,
                            is VideoSectionState.Loading -> {
                                if (section.isCarousel) {
                                    LazyRow(
                                        contentPadding = PaddingValues(horizontal = 20.dp),
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        items(3) {
                                            VideoCardSkeleton(colors = colors, isCarousel = true)
                                        }
                                    }
                                } else {
                                    Column(
                                        modifier = Modifier.padding(horizontal = 20.dp),
                                        verticalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        repeat(2) {
                                            VideoCardSkeleton(colors = colors, isCarousel = false)
                                        }
                                    }
                                }
                            }

                            is VideoSectionState.Error -> {
                                OnlineErrorCard(
                                    kind = state.kind,
                                    message = state.message,
                                    showRetry = state.isRetryable,
                                    colors = colors,
                                    onRetry = { onRetrySection(section) }
                                )
                            }

                            is VideoSectionState.Success -> {
                                if (state.items.isEmpty()) {
                                    Surface(
                                        shape = RoundedCornerShape(16.dp),
                                        color = colors.surfaceTinted,
                                        border = BorderStroke(0.5.dp, colors.cardBorder),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 20.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(14.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                imageVector = Icons.Rounded.PlayCircle,
                                                contentDescription = null,
                                                tint = colors.textMuted,
                                                modifier = Modifier.size(20.dp)
                                            )
                                            Spacer(modifier = Modifier.width(10.dp))
                                            Text(
                                                text = "No videos returned for ${section.title} right now.",
                                                fontFamily = PlusJakartaFontFamily,
                                                fontSize = 12.5.sp,
                                                color = colors.textSecondary
                                            )
                                        }
                                    }
                                } else if (section.isCarousel) {
                                    LazyRow(
                                        contentPadding = PaddingValues(horizontal = 20.dp),
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        items(state.items, key = { "${section.id}_${it.videoId}" }) { video ->
                                            HorizontalVideoCarouselCard(
                                                video = video,
                                                colors = colors,
                                                isPlayingNow = video.videoId == activeVideoId,
                                                onClick = { onPlayVideo(video, state.items) },
                                                onChannelClick = video.channelUrl?.takeIf { it.isNotBlank() }?.let { url ->
                                                    { onOpenChannel(url, video.channelName) }
                                                },
                                                onMoreClick = { onVideoMoreClick(video) }
                                            )
                                        }
                                        if (state.nextPageToken != null) {
                                            item(key = "${section.id}_load_more") {
                                                Surface(
                                                    onClick = { onLoadMoreSection(section) },
                                                    shape = RoundedCornerShape(18.dp),
                                                    color = colors.surfaceTinted,
                                                    border = BorderStroke(0.75.dp, colors.accentStrong.copy(alpha = 0.5f)),
                                                    modifier = Modifier
                                                        .width(116.dp)
                                                        .height(198.dp)
                                                        .testTag("video_load_more_${section.id}")
                                                ) {
                                                    Column(
                                                        modifier = Modifier.fillMaxSize(),
                                                        verticalArrangement = Arrangement.Center,
                                                        horizontalAlignment = Alignment.CenterHorizontally
                                                    ) {
                                                        if (state.isLoadingMore) {
                                                            CircularProgressIndicator(
                                                                color = colors.accentStrong,
                                                                strokeWidth = 2.dp,
                                                                modifier = Modifier.size(22.dp)
                                                            )
                                                        } else {
                                                            Box(
                                                                modifier = Modifier
                                                                    .size(40.dp)
                                                                    .clip(CircleShape)
                                                                    .background(colors.accentPill),
                                                                contentAlignment = Alignment.Center
                                                            ) {
                                                                Icon(
                                                                    imageVector = Icons.Rounded.Refresh,
                                                                    contentDescription = "Load more",
                                                                    tint = colors.onAccentPill,
                                                                    modifier = Modifier.size(20.dp)
                                                                )
                                                            }
                                                            Spacer(modifier = Modifier.height(8.dp))
                                                            Text(
                                                                text = "Load More",
                                                                fontFamily = PlusJakartaFontFamily,
                                                                fontWeight = FontWeight.Bold,
                                                                fontSize = 12.sp,
                                                                color = colors.accentStrong
                                                            )
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                } else {
                                    // Vertical feed layout for Recommended Videos
                                    Column(
                                        modifier = Modifier.padding(horizontal = 20.dp),
                                        verticalArrangement = Arrangement.spacedBy(14.dp)
                                    ) {
                                        state.items.take(8).forEach { video ->
                                            OnlineVideoCard(
                                                video = video,
                                                colors = colors,
                                                isPlayingNow = video.videoId == activeVideoId,
                                                onClick = { onPlayVideo(video, state.items) },
                                                onChannelClick = video.channelUrl?.let { url ->
                                                    { onOpenChannel(url, video.channelName) }
                                                },
                                                onMoreClick = { onVideoMoreClick(video) }
                                            )
                                        }
                                        if (state.nextPageToken != null) {
                                            OutlinedButton(
                                                onClick = { onLoadMoreSection(section) },
                                                enabled = !state.isLoadingMore,
                                                shape = RoundedCornerShape(18.dp),
                                                border = BorderStroke(1.dp, colors.accentStrong),
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .height(48.dp)
                                                    .testTag("video_load_more_${section.id}")
                                            ) {
                                                if (state.isLoadingMore) {
                                                    CircularProgressIndicator(
                                                        color = colors.accentStrong,
                                                        strokeWidth = 2.dp,
                                                        modifier = Modifier.size(18.dp)
                                                    )
                                                    Spacer(modifier = Modifier.width(8.dp))
                                                }
                                                Text(
                                                    text = if (state.isLoadingMore) "Loading More..." else "Load More Recommended Videos",
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
            }
        }

        // Refresh Footer
        item(key = "video_home_refresh_footer") {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                contentAlignment = Alignment.Center
            ) {
                OutlinedButton(
                    onClick = onRefreshAll,
                    shape = RoundedCornerShape(20.dp),
                    border = BorderStroke(0.75.dp, colors.cardBorder),
                    modifier = Modifier.height(46.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Refresh,
                        contentDescription = null,
                        tint = colors.accentStrong,
                        modifier = Modifier.size(17.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Refresh Video Feed",
                        fontFamily = PlusJakartaFontFamily,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp,
                        color = colors.textPrimary
                    )
                }
            }
        }
    }
}

@Composable
private fun VideoSectionHeader(
    title: String,
    subtitle: String,
    colors: EditorialColors,
    trailingRefresh: Boolean = false,
    onRefreshClick: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontFamily = OutfitFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 19.sp,
                letterSpacing = (-0.3).sp,
                color = colors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
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
        if (trailingRefresh && onRefreshClick != null) {
            Surface(
                onClick = onRefreshClick,
                shape = RoundedCornerShape(14.dp),
                color = colors.accentPill
            ) {
                Text(
                    text = "Retry",
                    fontFamily = PlusJakartaFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.5.sp,
                    color = colors.onAccentPill,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                )
            }
        }
    }
}
