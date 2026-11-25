package ui.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.BookmarkAdded
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.WifiOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.DailyMix
import com.example.model.Playlist
import com.example.model.Song
import com.example.ui.theme.OutfitFontFamily
import com.example.ui.theme.PlusJakartaFontFamily
import data.online.OnlineTrackMapper
import domain.online.DiscoverMusicLanguage
import domain.online.DiscoverSectionState
import domain.online.DiscoverSectionType
import domain.online.OnlineErrorKind
import domain.online.OnlineHomeMixBuilder
import domain.online.OnlineItemKind
import domain.online.OnlineMusicProvider
import domain.online.OnlineRecommendationProfile
import domain.online.OnlineTrack
import domain.online.SonoraRecommendUiState
import ui.home.animations.DailyMixAnimationStyle
import ui.home.animations.HomeAnimationLevel
import ui.motion.LocalEffectiveMotionState
import ui.motion.carouselItemDepthEffect
import ui.online.OnlineErrorCard
import ui.online.buildArtistExploreTrack
import ui.theme.EditorialColors

private val INDIA_REGIONAL_QUICK_LANGUAGES = listOf(
    DiscoverMusicLanguage.ALL,
    DiscoverMusicLanguage.HINDI,
    DiscoverMusicLanguage.ODIA,
    DiscoverMusicLanguage.TAMIL,
    DiscoverMusicLanguage.TELUGU,
    DiscoverMusicLanguage.MALAYALAM,
    DiscoverMusicLanguage.KANNADA,
    DiscoverMusicLanguage.BENGALI,
    DiscoverMusicLanguage.PUNJABI
)

/**
 * Full dedicated Online Music Home experience rendered when [domain.home.HomeMusicMode.ONLINE]
 * is active. Powered entirely by NewPipe Extractor + Room online history + shared Media3 playback.
 */
@Composable
fun OnlineHomeContent(
    discoverSections: Map<DiscoverSectionType, DiscoverSectionState>,
    recentlyPlayedOnline: List<Song>,
    savedOnlineTracks: List<Song>,
    playlists: List<Playlist>,
    selectedProvider: OnlineMusicProvider?,
    discoverLanguage: DiscoverMusicLanguage,
    countryCode: String,
    recommendationProfile: OnlineRecommendationProfile,
    isNetworkOnline: Boolean,
    currentSong: Song?,
    isPlaying: Boolean,
    playbackError: String?,
    playbackErrorKind: OnlineErrorKind?,
    colors: EditorialColors,
    animationLevel: HomeAnimationLevel,
    dailyMixAnimationStyle: DailyMixAnimationStyle,
    screenVisible: Boolean,
    reduceAnimations: Boolean,
    onSelectDiscoverLanguage: (DiscoverMusicLanguage) -> Unit,
    onOpenRegionSheet: () -> Unit,
    onRetrySection: (DiscoverSectionType) -> Unit,
    onLoadMoreSection: (DiscoverSectionType) -> Unit,
    onPlayCollection: (List<Song>, Boolean) -> Unit,
    onOpenLocalCollectionModal: (String, String, List<Song>) -> Unit,
    onTrackClick: (OnlineTrack, List<OnlineTrack>) -> Unit,
    onCollectionClick: (OnlineTrack) -> Unit,
    onTrackLongClick: (OnlineTrack) -> Unit,
    onRecentOnlineSongClick: (Song) -> Unit,
    onRecentOnlineSongLongClick: (Song) -> Unit,
    onRetryPlayback: () -> Unit,
    onSwitchSoundCloudFallback: () -> Unit,
    onDismissPlaybackError: () -> Unit,
    onOpenPlaylistsScreen: () -> Unit,
    sonoraRecommendState: SonoraRecommendUiState = SonoraRecommendUiState(),
    favoriteOnlineIds: Set<Long> = emptySet(),
    onRefreshSonoraRecommend: () -> Unit = {},
    onPlayRecommendedTrack: (OnlineTrack, List<OnlineTrack>) -> Unit = onTrackClick,
    onAddRecommendedToQueue: (OnlineTrack) -> Unit = {},
    onPlayRecommendedNext: (OnlineTrack) -> Unit = {},
    onToggleLikeRecommended: (OnlineTrack) -> Unit = {},
    onDislikeRecommended: (OnlineTrack) -> Unit = {},
    onOpenRecommendedArtist: (OnlineTrack) -> Unit = {},
    onOpenRecommendedAlbum: (OnlineTrack) -> Unit = {},
    hasMusicMiniPlayer: Boolean = currentSong != null,
    modifier: Modifier = Modifier
) {
    val onlineYourMix = remember(discoverSections, recentlyPlayedOnline, savedOnlineTracks, recommendationProfile, countryCode) {
        OnlineHomeMixBuilder.buildOnlineYourMix(
            discoverSections = discoverSections,
            recentlyPlayedOnline = recentlyPlayedOnline,
            savedOnlineTracks = savedOnlineTracks,
            recommendationProfile = recommendationProfile,
            countryCode = countryCode
        )
    }

    val onlineDailyMixes = remember(discoverSections, recentlyPlayedOnline, savedOnlineTracks, countryCode, discoverLanguage) {
        OnlineHomeMixBuilder.buildOnlineDailyMixes(
            discoverSections = discoverSections,
            recentlyPlayedOnline = recentlyPlayedOnline,
            savedOnlineTracks = savedOnlineTracks,
            countryCode = countryCode,
            discoverLanguage = discoverLanguage
        )
    }

    val isOnlineMixPlaying = isPlaying && currentSong != null &&
        onlineYourMix.songs.any { it.id == currentSong.id }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("home_lazy_column"),
        contentPadding = PaddingValues(
            top = 4.dp,
            bottom = domain.player.MusicMiniPlayerVisibilityPolicy.listBottomContentPadding(
                isMusicMiniPlayerVisible = hasMusicMiniPlayer,
                isVideoMiniPlayerVisible = false
            )
        ),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        // 1 & 2. Online Your Mix Hero + Real Online Artist Shortcuts
        item(key = "online_your_mix_hero") {
            YourMixSection(
                contributorsSummary = onlineYourMix.contributorsSummary,
                artistChips = onlineYourMix.artistChips,
                isMixPlaying = isOnlineMixPlaying,
                hasTracks = onlineYourMix.songs.isNotEmpty(),
                colors = colors,
                animationLevel = animationLevel,
                screenVisible = screenVisible,
                onPlayMix = {
                    if (onlineYourMix.songs.isNotEmpty()) {
                        onPlayCollection(onlineYourMix.songs, false)
                    }
                },
                onShuffleMix = {
                    if (onlineYourMix.songs.isNotEmpty()) {
                        onPlayCollection(onlineYourMix.songs, true)
                    }
                },
                onArtistChipClick = { chip ->
                    val matchingChannel = (discoverSections[DiscoverSectionType.POPULAR_ARTISTS] as? DiscoverSectionState.Success)
                        ?.items
                        ?.firstOrNull { it.title.equals(chip.artistName, ignoreCase = true) }
                    val targetCollection = matchingChannel
                        ?: buildArtistExploreTrack(OnlineTrackMapper.toOnlineTrack(chip.representativeSong))
                        ?: OnlineTrackMapper.toOnlineTrack(chip.representativeSong)
                    onCollectionClick(targetCollection)
                }
            )
        }

        // Graceful offline banner (rendered only when offline; zero empty space when online)
        if (!isNetworkOnline) {
            item(key = "online_home_offline_banner") {
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
                            text = "You're offline. Switch to Offline Music anytime or reconnect to stream.",
                            fontFamily = PlusJakartaFontFamily,
                            fontSize = 12.sp,
                            color = colors.textSecondary
                        )
                    }
                }
            }
        }

        // Playback error recovery banner when an online track fails resolution/bot check
        if (!playbackError.isNullOrBlank() && currentSong?.isOnline == true) {
            item(key = "online_home_playback_error") {
                val errKind = playbackErrorKind ?: OnlineErrorKind.EXTRACTION_FAILED
                val canSwitchSc = errKind == OnlineErrorKind.RATE_LIMITED_OR_CAPTCHA &&
                    selectedProvider?.key != OnlineMusicProvider.KEY_SOUNDCLOUD
                OnlineErrorCard(
                    kind = errKind,
                    message = playbackError,
                    showRetry = true,
                    colors = colors,
                    onRetry = onRetryPlayback,
                    secondaryActionLabel = if (canSwitchSc) "Try SoundCloud" else null,
                    onSecondaryAction = if (canSwitchSc) onSwitchSoundCloudFallback else null,
                    onDismiss = onDismissPlaybackError
                )
            }
        }

        // 3b. Sonora Recommend (NEW — Personalized Recommendations directly above Trending Music India)
        item(key = "sonora_recommend_section") {
            SonoraRecommendSection(
                state = sonoraRecommendState,
                favoriteOnlineIds = favoriteOnlineIds,
                currentSongId = currentSong?.id,
                isPlaying = isPlaying,
                colors = colors,
                animationLevel = animationLevel,
                screenVisible = screenVisible,
                reduceAnimations = reduceAnimations,
                onRefresh = onRefreshSonoraRecommend,
                onPlayTrack = onPlayRecommendedTrack,
                onAddToQueue = onAddRecommendedToQueue,
                onPlayNext = onPlayRecommendedNext,
                onToggleLike = onToggleLikeRecommended,
                onDislikeTrack = onDislikeRecommended,
                onOpenArtist = onOpenRecommendedArtist,
                onOpenAlbum = onOpenRecommendedAlbum,
                onMoreOptions = onTrackLongClick
            )
        }

        // 4. Trending Music in India
        item(key = "online_trending_section") {
            OnlineHomeCarouselSection(
                title = if (countryCode.equals("IN", ignoreCase = true)) "Trending Music in India" else "Trending Music",
                sectionType = DiscoverSectionType.TRENDING_MUSIC,
                state = discoverSections[DiscoverSectionType.TRENDING_MUSIC] ?: DiscoverSectionState.Loading,
                subtitleOverride = null,
                providerName = selectedProvider?.displayName ?: "YT Music",
                currentSongId = currentSong?.id,
                isPlaying = isPlaying,
                colors = colors,
                animationLevel = animationLevel,
                screenVisible = screenVisible,
                reduceAnimations = reduceAnimations,
                onRetry = { onRetrySection(DiscoverSectionType.TRENDING_MUSIC) },
                onLoadMore = { onLoadMoreSection(DiscoverSectionType.TRENDING_MUSIC) },
                onTrackClick = onTrackClick,
                onCollectionClick = onCollectionClick,
                onTrackLongClick = onTrackLongClick
            )
        }

        // 5. Popular Songs
        item(key = "online_popular_songs_section") {
            OnlineHomeCarouselSection(
                title = "Popular Songs",
                sectionType = DiscoverSectionType.POPULAR_SONGS,
                state = discoverSections[DiscoverSectionType.POPULAR_SONGS] ?: DiscoverSectionState.Loading,
                subtitleOverride = null,
                providerName = selectedProvider?.displayName ?: "YT Music",
                currentSongId = currentSong?.id,
                isPlaying = isPlaying,
                colors = colors,
                animationLevel = animationLevel,
                screenVisible = screenVisible,
                reduceAnimations = reduceAnimations,
                onRetry = { onRetrySection(DiscoverSectionType.POPULAR_SONGS) },
                onLoadMore = { onLoadMoreSection(DiscoverSectionType.POPULAR_SONGS) },
                onTrackClick = onTrackClick,
                onCollectionClick = onCollectionClick,
                onTrackLongClick = onTrackLongClick
            )
        }

        // 6. New Releases
        item(key = "online_new_releases_section") {
            OnlineHomeCarouselSection(
                title = "New Releases",
                sectionType = DiscoverSectionType.NEW_RELEASES,
                state = discoverSections[DiscoverSectionType.NEW_RELEASES] ?: DiscoverSectionState.Loading,
                subtitleOverride = null,
                providerName = selectedProvider?.displayName ?: "YT Music",
                currentSongId = currentSong?.id,
                isPlaying = isPlaying,
                colors = colors,
                animationLevel = animationLevel,
                screenVisible = screenVisible,
                reduceAnimations = reduceAnimations,
                onRetry = { onRetrySection(DiscoverSectionType.NEW_RELEASES) },
                onLoadMore = { onLoadMoreSection(DiscoverSectionType.NEW_RELEASES) },
                onTrackClick = onTrackClick,
                onCollectionClick = onCollectionClick,
                onTrackLongClick = onTrackLongClick
            )
        }

        // 7. Made for You Online (+ Saved Online Vault Spotlight)
        item(key = "online_made_for_you_section") {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("online_made_for_you_section"),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (savedOnlineTracks.isNotEmpty()) {
                    Surface(
                        onClick = {
                            onOpenLocalCollectionModal(
                                "Saved Online Vault",
                                "${savedOnlineTracks.size} bookmarked online streams",
                                savedOnlineTracks
                            )
                        },
                        shape = RoundedCornerShape(22.dp),
                        color = colors.accentPill.copy(alpha = 0.72f),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                Box(
                                    modifier = Modifier
                                        .size(38.dp)
                                        .background(colors.surface, CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.BookmarkAdded,
                                        contentDescription = null,
                                        tint = colors.accentStrong,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(
                                        text = "Saved Online Vault",
                                        fontFamily = OutfitFontFamily,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 15.sp,
                                        color = colors.onAccentPill
                                    )
                                    Text(
                                        text = "${savedOnlineTracks.size} saved online tracks ready to stream",
                                        fontFamily = PlusJakartaFontFamily,
                                        fontSize = 12.sp,
                                        color = colors.onAccentPill.copy(alpha = 0.78f)
                                    )
                                }
                            }
                            Icon(
                                imageVector = Icons.Rounded.PlayArrow,
                                contentDescription = "Open Saved Online Vault",
                                tint = colors.onAccentPill
                            )
                        }
                    }
                }

                OnlineHomeCarouselSection(
                    title = "Made for You Online",
                    sectionType = DiscoverSectionType.MUSIC_RECOMMENDATIONS,
                    state = discoverSections[DiscoverSectionType.MUSIC_RECOMMENDATIONS] ?: DiscoverSectionState.Loading,
                    subtitleOverride = recommendationProfile.subtitle,
                    providerName = selectedProvider?.displayName ?: "YT Music",
                    currentSongId = currentSong?.id,
                    isPlaying = isPlaying,
                    colors = colors,
                    animationLevel = animationLevel,
                    screenVisible = screenVisible,
                    reduceAnimations = reduceAnimations,
                    onRetry = { onRetrySection(DiscoverSectionType.MUSIC_RECOMMENDATIONS) },
                    onLoadMore = { onLoadMoreSection(DiscoverSectionType.MUSIC_RECOMMENDATIONS) },
                    onTrackClick = onTrackClick,
                    onCollectionClick = onCollectionClick,
                    onTrackLongClick = onTrackLongClick
                )
            }
        }

        // 8. Online Daily Mixes (Real NewPipe mixes — never fabricated)
        if (onlineDailyMixes.isNotEmpty()) {
            item(key = "online_daily_mixes_section") {
                DailyMixSection(
                    mixes = onlineDailyMixes,
                    colors = colors,
                    animationStyle = dailyMixAnimationStyle,
                    animationLevel = animationLevel,
                    screenVisible = screenVisible,
                    currentPlayingSongId = currentSong?.id,
                    isPlaying = isPlaying,
                    onMixClick = { mix: DailyMix ->
                        onOpenLocalCollectionModal(mix.title, mix.subtitle, mix.songs)
                    },
                    onPlayMix = { mix: DailyMix ->
                        onPlayCollection(mix.songs, false)
                    },
                    onShuffleMix = { mix: DailyMix ->
                        onPlayCollection(mix.songs, true)
                    },
                    modifier = Modifier.testTag("online_daily_mixes_section")
                )
            }
        }

        // 9. Popular Indian Artists
        item(key = "online_popular_artists_section") {
            OnlineHomeCarouselSection(
                title = if (countryCode.equals("IN", ignoreCase = true)) "Popular Indian Artists" else "Popular Artists",
                sectionType = DiscoverSectionType.POPULAR_ARTISTS,
                state = discoverSections[DiscoverSectionType.POPULAR_ARTISTS] ?: DiscoverSectionState.Loading,
                subtitleOverride = null,
                providerName = selectedProvider?.displayName ?: "YT Music",
                currentSongId = currentSong?.id,
                isPlaying = isPlaying,
                colors = colors,
                animationLevel = animationLevel,
                screenVisible = screenVisible,
                reduceAnimations = reduceAnimations,
                onRetry = { onRetrySection(DiscoverSectionType.POPULAR_ARTISTS) },
                onLoadMore = { onLoadMoreSection(DiscoverSectionType.POPULAR_ARTISTS) },
                onTrackClick = onTrackClick,
                onCollectionClick = onCollectionClick,
                onTrackLongClick = onTrackLongClick
            )
        }

        // 10. Regional Music (India Languages Filter Showcase)
        item(key = "online_regional_music_section") {
            RegionalIndiaMusicSection(
                selectedLanguage = discoverLanguage,
                countryCode = countryCode,
                colors = colors,
                onSelectLanguage = onSelectDiscoverLanguage,
                onOpenRegionSheet = onOpenRegionSheet
            )
        }

        // 11. Recently Played Online
        if (recentlyPlayedOnline.isNotEmpty()) {
            item(key = "online_recently_played_section") {
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("home_online_recent_section")
                ) {
                    EditorialSectionTitle(
                        title = "Recently Played Online",
                        subtitle = "Resume recent online streams cleanly",
                        colors = colors
                    )
                    val recentListState = rememberLazyListState()
                    val recentSnap = rememberSnapFlingBehavior(lazyListState = recentListState)
                    val motionState = LocalEffectiveMotionState.current
                    LazyRow(
                        state = recentListState,
                        flingBehavior = recentSnap,
                        contentPadding = PaddingValues(horizontal = 20.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        itemsIndexed(
                            items = recentlyPlayedOnline.take(12),
                            key = { _, song -> "onl_home_rec_${song.id}" }
                        ) { idx, song ->
                            val isCurrent = currentSong?.id == song.id
                            HomeOnlineRecentMedallion(
                                song = song,
                                index = idx,
                                isCurrent = isCurrent,
                                isPlaying = isPlaying,
                                colors = colors,
                                animationLevel = animationLevel,
                                screenVisible = screenVisible,
                                modifier = Modifier.carouselItemDepthEffect(recentListState, idx, motionState),
                                onClick = { onRecentOnlineSongClick(song) },
                                onLongClick = { onRecentOnlineSongLongClick(song) }
                            )
                        }
                    }
                }
            }
        }

        // 12. Online Playlists (NewPipe extracted playlists + Quick link to User Playlists)
        item(key = "online_playlists_section") {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("online_playlists_section")
            ) {
                OnlineHomeCarouselSection(
                    title = "Online Playlists",
                    sectionType = DiscoverSectionType.ONLINE_PLAYLISTS,
                    state = discoverSections[DiscoverSectionType.ONLINE_PLAYLISTS] ?: DiscoverSectionState.Loading,
                    subtitleOverride = null,
                    providerName = selectedProvider?.displayName ?: "YT Music",
                    currentSongId = currentSong?.id,
                    isPlaying = isPlaying,
                    colors = colors,
                    animationLevel = animationLevel,
                    screenVisible = screenVisible,
                    reduceAnimations = reduceAnimations,
                    onRetry = { onRetrySection(DiscoverSectionType.ONLINE_PLAYLISTS) },
                    onLoadMore = { onLoadMoreSection(DiscoverSectionType.ONLINE_PLAYLISTS) },
                    onTrackClick = onTrackClick,
                    onCollectionClick = onCollectionClick,
                    onTrackLongClick = onTrackLongClick
                )

                if (playlists.isNotEmpty()) {
                    Surface(
                        onClick = onOpenPlaylistsScreen,
                        shape = RoundedCornerShape(18.dp),
                        color = colors.surfaceTinted,
                        border = BorderStroke(0.75.dp, colors.cardBorder),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Rounded.QueueMusic,
                                    contentDescription = null,
                                    tint = colors.accentStrong,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = "Your SONORA Playlists (${playlists.size})",
                                    fontFamily = PlusJakartaFontFamily,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 13.sp,
                                    color = colors.textPrimary
                                )
                            }
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
                                contentDescription = "Open playlists",
                                tint = colors.textMuted,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun OnlineHomeCarouselSection(
    title: String,
    sectionType: DiscoverSectionType,
    state: DiscoverSectionState,
    subtitleOverride: String?,
    providerName: String,
    currentSongId: Long?,
    isPlaying: Boolean,
    colors: EditorialColors,
    animationLevel: HomeAnimationLevel,
    screenVisible: Boolean,
    reduceAnimations: Boolean,
    onRetry: () -> Unit,
    onLoadMore: () -> Unit,
    onTrackClick: (OnlineTrack, List<OnlineTrack>) -> Unit,
    onCollectionClick: (OnlineTrack) -> Unit,
    onTrackLongClick: (OnlineTrack) -> Unit
) {
    val sectionSubtitle = when {
        !subtitleOverride.isNullOrBlank() -> subtitleOverride
        state is DiscoverSectionState.Success && state.sourceLabel.isNotBlank() ->
            "${sectionType.subtitle} • ${state.sourceLabel}"
        else -> "${sectionType.subtitle} • $providerName"
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
                    text = title,
                    fontFamily = OutfitFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 19.sp,
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
                    onClick = onRetry,
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Refresh,
                        contentDescription = "Retry $title",
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
                    title = "Nothing in $title",
                    message = state.message,
                    showRetry = false,
                    colors = colors,
                    onRetry = {}
                )
            }

            is DiscoverSectionState.Error -> {
                HomeOnlineSectionStatusCard(
                    sectionType = sectionType,
                    title = "$title unavailable",
                    message = state.message,
                    showRetry = state.isRetryable,
                    colors = colors,
                    onRetry = onRetry
                )
            }

            is DiscoverSectionState.Success -> {
                val rowListState = rememberLazyListState()
                val rowSnap = rememberSnapFlingBehavior(lazyListState = rowListState)
                val motionState = LocalEffectiveMotionState.current
                LazyRow(
                    state = rowListState,
                    flingBehavior = rowSnap,
                    contentPadding = PaddingValues(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    if (sectionType == DiscoverSectionType.POPULAR_ARTISTS) {
                        itemsIndexed(
                            items = state.items,
                            key = { _, it -> "oh_art_${it.stableId}_${it.contentUrl}" }
                        ) { idx, item ->
                            HomeOnlineArtistMedallion(
                                artistTrack = item,
                                index = idx,
                                colors = colors,
                                animationLevel = animationLevel,
                                screenVisible = screenVisible,
                                modifier = Modifier.carouselItemDepthEffect(rowListState, idx, motionState),
                                onClick = { onCollectionClick(item) },
                                onLongClick = { onTrackLongClick(item) }
                            )
                        }
                    } else {
                        itemsIndexed(
                            items = state.items,
                            key = { _, it -> "oh_${sectionType.name}_${it.stableId}_${it.contentUrl}" }
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
                                modifier = Modifier.carouselItemDepthEffect(rowListState, idx, motionState),
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
                        item(key = "oh_load_more_${sectionType.name}") {
                            HomeOnlineLoadMoreCard(
                                sectionType = sectionType,
                                isLoadingMore = state.isLoadingMore,
                                colors = colors,
                                onLoadMore = onLoadMore
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RegionalIndiaMusicSection(
    selectedLanguage: DiscoverMusicLanguage,
    countryCode: String,
    colors: EditorialColors,
    onSelectLanguage: (DiscoverMusicLanguage) -> Unit,
    onOpenRegionSheet: () -> Unit
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("online_regional_music_section")
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
                    text = "Regional Music",
                    fontFamily = OutfitFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 19.sp,
                    color = colors.textPrimary
                )
                Text(
                    text = if (selectedLanguage == DiscoverMusicLanguage.ALL) {
                        "Filter Indian regional music across Hindi, Odia, Tamil, Telugu & more (${countryCode.uppercase()})"
                    } else {
                        "Exploring ${selectedLanguage.label} music across trending, charts & playlists"
                    },
                    fontFamily = PlusJakartaFontFamily,
                    fontSize = 12.sp,
                    color = colors.textSecondary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            IconButton(
                onClick = onOpenRegionSheet,
                modifier = Modifier.size(48.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Language,
                    contentDescription = "Change Content Country & Language",
                    tint = colors.accentStrong
                )
            }
        }

        LazyRow(
            contentPadding = PaddingValues(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.testTag("home_online_language_filters")
        ) {
            items(INDIA_REGIONAL_QUICK_LANGUAGES, key = { "reg_${it.id}" }) { lang ->
                val selected = lang == selectedLanguage
                Surface(
                    onClick = { onSelectLanguage(lang) },
                    shape = RoundedCornerShape(18.dp),
                    color = if (selected) colors.accentStrong else colors.surfaceTinted,
                    border = BorderStroke(
                        width = if (selected) 1.25.dp else 0.75.dp,
                        color = if (selected) colors.accentStrong else colors.cardBorder
                    ),
                    modifier = Modifier
                        .height(40.dp)
                        .testTag("home_online_lang_${lang.id}")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        if (selected) {
                            Icon(
                                imageVector = Icons.Rounded.GraphicEq,
                                contentDescription = null,
                                tint = colors.onAccentStrong,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                        Text(
                            text = lang.label,
                            fontFamily = PlusJakartaFontFamily,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                            fontSize = 12.5.sp,
                            color = if (selected) colors.onAccentStrong else colors.textPrimary
                        )
                    }
                }
            }
        }
    }
}
