package ui.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.WifiOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.Song
import com.example.ui.theme.OutfitFontFamily
import com.example.ui.theme.PlusJakartaFontFamily
import domain.online.DiscoverMusicLanguage
import domain.online.DiscoverSectionState
import domain.online.DiscoverSectionType
import domain.online.OnlineItemKind
import domain.online.OnlineMusicProvider
import domain.online.OnlineRecommendationProfile
import domain.online.OnlineTrack
import ui.home.animations.HomeAnimationLevel
import ui.theme.EditorialColors

private val HOME_ONLINE_SUBSECTIONS: List<DiscoverSectionType> = listOf(
    DiscoverSectionType.TRENDING_MUSIC,
    DiscoverSectionType.POPULAR_SONGS,
    DiscoverSectionType.NEW_RELEASES,
    DiscoverSectionType.MUSIC_RECOMMENDATIONS,
    DiscoverSectionType.POPULAR_ARTISTS
)

/**
 * Editorial Home Screen section integrating real NewPipe Extractor online music discovery,
 * India regional language filters, personalized recommendations, pagination, and recently played online tracks.
 */
@Composable
fun HomeOnlineMusicSection(
    discoverSections: Map<DiscoverSectionType, DiscoverSectionState>,
    recentlyPlayedOnline: List<Song>,
    providers: List<OnlineMusicProvider>,
    selectedProvider: OnlineMusicProvider?,
    discoverLanguage: DiscoverMusicLanguage,
    countryCode: String,
    languageCode: String,
    recommendationProfile: OnlineRecommendationProfile,
    isNetworkOnline: Boolean,
    currentSongId: Long?,
    isPlaying: Boolean,
    colors: EditorialColors,
    animationLevel: HomeAnimationLevel,
    screenVisible: Boolean,
    reduceAnimations: Boolean,
    onSelectProvider: (OnlineMusicProvider) -> Unit,
    onSelectDiscoverLanguage: (DiscoverMusicLanguage) -> Unit,
    onOpenRegionSheet: () -> Unit,
    onRefreshAll: () -> Unit,
    onRetrySection: (DiscoverSectionType) -> Unit,
    onLoadMoreSection: (DiscoverSectionType) -> Unit,
    onTrackClick: (OnlineTrack, List<OnlineTrack>) -> Unit,
    onCollectionClick: (OnlineTrack) -> Unit,
    onTrackLongClick: (OnlineTrack) -> Unit,
    onRecentOnlineSongClick: (Song) -> Unit,
    onRecentOnlineSongLongClick: (Song) -> Unit,
    onViewAllClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag("home_online_music_section"),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        // Section Header: "Online Music" + Subtitle + View All / Region / Refresh controls
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 22.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f).padding(end = 10.dp)) {
                Text(
                    text = "Online Music",
                    fontFamily = OutfitFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 22.sp,
                    letterSpacing = (-0.3).sp,
                    color = colors.textPrimary
                )
                Text(
                    text = "Discover music from online streaming services.",
                    fontFamily = PlusJakartaFontFamily,
                    fontWeight = FontWeight.Normal,
                    fontSize = 12.sp,
                    color = colors.textSecondary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Surface(
                    onClick = onOpenRegionSheet,
                    shape = RoundedCornerShape(18.dp),
                    color = colors.surfaceTinted,
                    border = BorderStroke(0.75.dp, colors.cardBorder),
                    modifier = Modifier
                        .height(48.dp)
                        .testTag("home_online_region_button")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Public,
                            contentDescription = "Online Content Region",
                            tint = colors.accentStrong,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "${countryCode.uppercase()}•${languageCode.uppercase()}",
                            fontFamily = PlusJakartaFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            color = colors.textPrimary
                        )
                    }
                }

                Surface(
                    onClick = onViewAllClick,
                    shape = RoundedCornerShape(18.dp),
                    color = colors.accentPill,
                    modifier = Modifier
                        .height(48.dp)
                        .testTag("home_online_view_all_button")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "View All",
                            fontFamily = PlusJakartaFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            color = colors.onAccentPill
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
                            contentDescription = "View all Online Music",
                            tint = colors.onAccentPill,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
            }
        }

        // Compact Provider + Refresh strip + India Regional Language Pills
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (providers.isNotEmpty()) {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    item(key = "home_online_refresh_pill") {
                        Surface(
                            onClick = onRefreshAll,
                            shape = RoundedCornerShape(18.dp),
                            color = colors.surfaceTinted,
                            border = BorderStroke(0.75.dp, colors.cardBorder),
                            modifier = Modifier
                                .height(38.dp)
                                .testTag("home_online_refresh_button")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Refresh,
                                    contentDescription = "Refresh Online Music",
                                    tint = colors.accentStrong,
                                    modifier = Modifier.size(15.dp)
                                )
                                Spacer(modifier = Modifier.width(5.dp))
                                Text(
                                    text = "Refresh",
                                    fontFamily = PlusJakartaFontFamily,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 12.sp,
                                    color = colors.textPrimary
                                )
                            }
                        }
                    }

                    items(providers, key = { "home_prov_${it.key}" }) { provider ->
                        val selected = provider.key == selectedProvider?.key
                        Surface(
                            onClick = { onSelectProvider(provider) },
                            shape = RoundedCornerShape(18.dp),
                            color = if (selected) colors.accentStrong else colors.surface,
                            border = BorderStroke(
                                width = 0.75.dp,
                                color = if (selected) colors.accentStrong else colors.cardBorder
                            ),
                            modifier = Modifier
                                .height(38.dp)
                                .testTag("home_online_provider_${provider.key}")
                        ) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier.padding(horizontal = 14.dp)
                            ) {
                                Text(
                                    text = provider.displayName,
                                    fontFamily = PlusJakartaFontFamily,
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                    fontSize = 12.sp,
                                    color = if (selected) colors.onAccentStrong else colors.textSecondary
                                )
                            }
                        }
                    }
                }
            }

            LazyRow(
                contentPadding = PaddingValues(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.testTag("home_online_language_filters")
            ) {
                items(DiscoverMusicLanguage.entries, key = { "home_lang_${it.id}" }) { lang ->
                    val selected = lang == discoverLanguage
                    Surface(
                        onClick = { onSelectDiscoverLanguage(lang) },
                        shape = RoundedCornerShape(18.dp),
                        color = if (selected) colors.accentPill else colors.surfaceTinted,
                        border = BorderStroke(
                            width = if (selected) 1.25.dp else 0.75.dp,
                            color = if (selected) colors.accentStrong else colors.cardBorder
                        ),
                        modifier = Modifier
                            .height(36.dp)
                            .testTag("home_online_lang_${lang.id}")
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.padding(horizontal = 13.dp)
                        ) {
                            Text(
                                text = lang.label,
                                fontFamily = PlusJakartaFontFamily,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                fontSize = 12.sp,
                                color = if (selected) colors.onAccentPill else colors.textPrimary
                            )
                        }
                    }
                }
            }
        }

        // Graceful Offline Notice (keeps local Home completely usable!)
        if (!isNetworkOnline) {
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = colors.surfaceTinted,
                border = BorderStroke(0.75.dp, colors.accentStrong.copy(alpha = 0.45f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .testTag("home_online_offline_banner")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
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
                        text = "You're offline. Local music plays normally; reconnect to stream online music.",
                        fontFamily = PlusJakartaFontFamily,
                        fontSize = 12.sp,
                        color = colors.textSecondary
                    )
                }
            }
        }

        // 5 Independent Fault-Tolerant Discovery Subsections
        HOME_ONLINE_SUBSECTIONS.forEach { sectionType ->
            val state = discoverSections[sectionType] ?: DiscoverSectionState.Loading
            val sectionSubtitle = when {
                sectionType == DiscoverSectionType.MUSIC_RECOMMENDATIONS -> recommendationProfile.subtitle
                state is DiscoverSectionState.Success && state.sourceLabel.isNotBlank() ->
                    "${sectionType.subtitle} • ${state.sourceLabel}"
                else -> "${sectionType.subtitle} • ${selectedProvider?.displayName ?: "YT Music"}"
            }

            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("home_online_subsection_${sectionType.name.lowercase()}")
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 22.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = sectionType.title,
                            fontFamily = OutfitFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            color = colors.textPrimary
                        )
                        Text(
                            text = sectionSubtitle,
                            fontFamily = PlusJakartaFontFamily,
                            fontSize = 12.sp,
                            color = colors.textSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    if (state is DiscoverSectionState.Error && state.isRetryable) {
                        IconButton(
                            onClick = { onRetrySection(sectionType) },
                            modifier = Modifier.size(48.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Refresh,
                                contentDescription = "Retry ${sectionType.title}",
                                tint = colors.accentStrong
                            )
                        }
                    }
                }

                when (state) {
                    is DiscoverSectionState.Loading -> {
                        HomeOnlineSkeletonRow(
                            sectionType = sectionType,
                            colors = colors,
                            reduceMotion = reduceAnimations || animationLevel == HomeAnimationLevel.OFF
                        )
                    }

                    is DiscoverSectionState.Empty -> {
                        HomeOnlineSectionStatusCard(
                            sectionType = sectionType,
                            title = "Nothing in ${sectionType.title}",
                            message = state.message,
                            showRetry = false,
                            colors = colors,
                            onRetry = {}
                        )
                    }

                    is DiscoverSectionState.Error -> {
                        HomeOnlineSectionStatusCard(
                            sectionType = sectionType,
                            title = "${sectionType.title} unavailable",
                            message = state.message,
                            showRetry = state.isRetryable,
                            colors = colors,
                            onRetry = { onRetrySection(sectionType) }
                        )
                    }

                    is DiscoverSectionState.Success -> {
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 20.dp),
                            horizontalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            if (sectionType == DiscoverSectionType.POPULAR_ARTISTS) {
                                itemsIndexed(
                                    items = state.items,
                                    key = { _, it -> "home_art_${it.stableId}_${it.contentUrl}" }
                                ) { idx, item ->
                                    HomeOnlineArtistMedallion(
                                        artistTrack = item,
                                        index = idx,
                                        colors = colors,
                                        animationLevel = animationLevel,
                                        screenVisible = screenVisible,
                                        onClick = { onCollectionClick(item) },
                                        onLongClick = { onTrackLongClick(item) }
                                    )
                                }
                            } else {
                                itemsIndexed(
                                    items = state.items,
                                    key = { _, it -> "home_${sectionType.name}_${it.stableId}_${it.contentUrl}" }
                                ) { idx, item ->
                                    val isCurrent = currentSongId == item.stableId
                                    HomeOnlineTrendingCard(
                                        track = item,
                                        index = idx,
                                        isCurrent = isCurrent,
                                        isPlaying = isPlaying,
                                        colors = colors,
                                        animationLevel = animationLevel,
                                        screenVisible = screenVisible,
                                        onClick = {
                                            if (item.kind == OnlineItemKind.TRACK) {
                                                onTrackClick(item, state.items)
                                            } else {
                                                onCollectionClick(item)
                                            }
                                        },
                                        onLongClick = { onTrackLongClick(item) }
                                    )
                                }
                            }

                            if (state.hasMore) {
                                item(key = "home_load_more_${sectionType.name}") {
                                    HomeOnlineLoadMoreCard(
                                        sectionType = sectionType,
                                        isLoadingMore = state.isLoadingMore,
                                        colors = colors,
                                        onLoadMore = { onLoadMoreSection(sectionType) }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // 6. Recently Played Online (from persistent Room playback history)
        if (recentlyPlayedOnline.isNotEmpty()) {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("home_online_recent_section")
            ) {
                Column(modifier = Modifier.padding(horizontal = 22.dp)) {
                    Text(
                        text = "Recently Played Online",
                        fontFamily = OutfitFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = colors.textPrimary
                    )
                    Text(
                        text = "Your recent online streams ready to resume",
                        fontFamily = PlusJakartaFontFamily,
                        fontSize = 12.sp,
                        color = colors.textSecondary
                    )
                }

                LazyRow(
                    contentPadding = PaddingValues(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    itemsIndexed(
                        items = recentlyPlayedOnline.take(12),
                        key = { _, song -> "home_rec_onl_${song.id}" }
                    ) { idx, song ->
                        val isCurrent = currentSongId == song.id
                        HomeOnlineRecentMedallion(
                            song = song,
                            index = idx,
                            isCurrent = isCurrent,
                            isPlaying = isPlaying,
                            colors = colors,
                            animationLevel = animationLevel,
                            screenVisible = screenVisible,
                            onClick = { onRecentOnlineSongClick(song) },
                            onLongClick = { onRecentOnlineSongLongClick(song) }
                        )
                    }
                }
            }
        }
    }
}
