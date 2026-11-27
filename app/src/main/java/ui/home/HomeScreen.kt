package ui.home

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Equalizer
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SpatialAudio
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.Album
import com.example.model.Artist
import com.example.model.Song
import com.example.ui.theme.OutfitFontFamily
import com.example.ui.theme.PlusJakartaFontFamily
import com.example.viewmodel.SonoraViewModel
import data.online.OnlineTrackMapper
import domain.home.HomeDiscoveryEngine
import domain.home.HomeMusicMode
import domain.home.LocalLibraryStats
import domain.online.DiscoverSectionState
import domain.online.OnlineErrorKind
import domain.online.OnlineItemKind
import domain.online.OnlineMusicProvider
import domain.online.OnlineTrack
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import ui.components.CircularArtwork
import ui.components.CollectionDetailSheet
import ui.components.EditorialArtworkCard
import ui.components.EditorialPlaylistPickerDialog
import ui.library.OnlinePlaylistPickerDialog
import ui.components.HomeModeSwitcherSheet
import ui.components.MusicContextMenuSheet
import ui.online.OnlineCollectionDetailsSheet
import ui.online.OnlineErrorCard
import ui.online.OnlineQualitySelectorSheet
import ui.online.OnlineRegionSheet
import ui.online.OnlineTrackOptionsSheet
import ui.online.buildArtistExploreTrack
import ui.motion.LocalEffectiveMotionState
import ui.motion.SonoraTransitions
import ui.motion.carouselItemDepthEffect
import ui.motion.horizontalSwipeNavigation
import ui.profile.UserProfileSheet
import domain.video.OnlineVideoItem
import ui.theme.EditorialAccentSwatches
import ui.theme.EditorialColors
import ui.theme.HomeThemeMode
import ui.video.OnlineVideoHomeContent
import ui.video.VideoNotificationsPanel
import ui.video.VideoOptionsBottomSheet
import ui.video.VideoPlaylistPickerDialog
import ui.video.openOfficialExternalUrl
import ui.video.shareVideoLink

private data class ActiveCollectionModal(
    val title: String,
    val subtitle: String,
    val songs: List<Song>
)

/**
 * Redesigned Editorial Music Discovery Home Screen for SONORA.
 * Integrates seamlessly with real local library scanning, playback engine, Room metadata,
 * dynamic album-art theming, and floating bottom navigation.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun HomeScreen(
    viewModel: SonoraViewModel,
    colors: EditorialColors,
    onOpenSettings: () -> Unit,
    onNavigateToSearch: () -> Unit = {},
    onNavigateToLibrary: () -> Unit,
    onOpenOnlineMusic: () -> Unit = {},
    onOpenNowPlaying: () -> Unit,
    onOpenEqualizer: () -> Unit = {},
    onOpenAudioStudio: () -> Unit = {},
    onOpenPlaylists: () -> Unit = {},
    onOpenQueue: () -> Unit = {},
    onOpenDynamicColors: () -> Unit = {},
    onOpenPlayerStyles: () -> Unit = {},
    onOpenMusicAnalytics: () -> Unit = {},
    onOpenMusicTaste: () -> Unit = {},
    screenVisible: Boolean = true,
    modifier: Modifier = Modifier
) {
    val motionState = LocalEffectiveMotionState.current
    val allSongs by viewModel.allSongs.collectAsState()
    val currentSong by viewModel.currentSong.collectAsState()
    val playbackInfo by viewModel.playbackStatusSummary.collectAsState()
    val favorites by viewModel.favoriteSongs.collectAsState()
    val mostPlayed by viewModel.mostPlayedSongs.collectAsState()
    val recentlyPlayed by viewModel.recentlyPlayedSongs.collectAsState()
    val recentlyAdded by viewModel.recentlyAddedSongs.collectAsState()
    val albums by viewModel.albums.collectAsState()
    val favouriteAlbums by viewModel.favouriteAlbums.collectAsState()
    val favouriteArtists by viewModel.favouriteArtists.collectAsState()
    val dailyMixes by viewModel.dailyMixes.collectAsState()
    val yourMixSongs by viewModel.yourMixSongs.collectAsState()
    val yourMixContributors by viewModel.yourMixContributorsSummary.collectAsState()
    val playlists by viewModel.playlists.collectAsState()
    val homeMusicMode by viewModel.homeMusicMode.collectAsState()
    val localLibraryStats by viewModel.localLibraryStats.collectAsState()
    val homeThemeMode by viewModel.homeThemeMode.collectAsState()
    val customAccentArgb by viewModel.homeCustomAccentArgb.collectAsState()
    val userProfile by viewModel.userProfile.collectAsState()
    val musicTasteProfile by viewModel.musicTasteProfile.collectAsState()
    val reduceAnimations by viewModel.reduceAnimations.collectAsState()
    val homeAnimationLevel by viewModel.homeAnimationLevel.collectAsState()
    val dailyMixAnimationStyle by viewModel.dailyMixAnimationStyle.collectAsState()
    val weatherAmbientSettings by viewModel.weatherAmbientSettings.collectAsState()
    val weatherFetchState by viewModel.weatherFetchState.collectAsState()
    val festivalAtmosphereSettings by viewModel.festivalAtmosphereSettings.collectAsState()
    val activeFestivalResolution by viewModel.activeFestivalResolution.collectAsState()
    val virtualPetSettings by viewModel.virtualPetSettings.collectAsState()
    val appearanceMode by viewModel.dynamicAppearanceMode.collectAsState()
    val colorIntensity by viewModel.dynamicColorIntensity.collectAsState()
    val playerStyle by viewModel.playerStyle.collectAsState()

    val discoverSections by viewModel.discoverSectionsState.collectAsState()
    val recentlyPlayedOnline by viewModel.recentlyPlayedOnlineTracks.collectAsState()
    val selectedOnlineProvider by viewModel.selectedOnlineProvider.collectAsState()
    val discoverMusicLanguage by viewModel.discoverMusicLanguage.collectAsState()
    val onlineCountryCode by viewModel.onlineContentCountry.collectAsState()
    val onlineLanguageCode by viewModel.onlineContentLanguage.collectAsState()
    val recommendationProfile by viewModel.onlineRecommendationProfile.collectAsState()
    val isNetworkOnline by viewModel.isNetworkOnline.collectAsState()
    val isMeteredNetwork by viewModel.isMeteredNetwork.collectAsState()
    val onlineSavedTracks by viewModel.onlineSavedTracks.collectAsState()
    val allOnlineTracks by viewModel.allOnlineTracks.collectAsState()
    val currentResolvedStream by viewModel.currentResolvedStream.collectAsState()
    val onlineCollectionState by viewModel.onlineCollectionState.collectAsState()
    val onlineQualityPreference by viewModel.onlineQualityPreference.collectAsState()
    val onlinePlaylists by viewModel.onlinePlaylists.collectAsState()
    val savedOnlineAlbumIds by viewModel.savedOnlineAlbumIds.collectAsState()
    val followedOnlineArtistIds by viewModel.followedOnlineArtistIds.collectAsState()
    val savedOnlinePlaylistUrls by viewModel.savedOnlinePlaylistUrls.collectAsState()
    val sonoraRecommendState by viewModel.sonoraRecommendState.collectAsStateWithLifecycle()

    val videoHomeSections by viewModel.videoController.homeSections.collectAsStateWithLifecycle()
    val videoRecentlyWatched by viewModel.videoController.videoHistory.collectAsStateWithLifecycle()
    val videoRegionalLanguage by viewModel.videoController.regionalLanguage.collectAsStateWithLifecycle()
    val videoPlayerState by viewModel.videoController.playerState.collectAsStateWithLifecycle()
    val watchLaterVideos by viewModel.videoController.watchLaterVideos.collectAsStateWithLifecycle()
    val savedVideos by viewModel.videoController.savedVideos.collectAsStateWithLifecycle()
    val videoPlaylists by viewModel.videoController.videoPlaylists.collectAsStateWithLifecycle()
    val personalizedVideoShelves by viewModel.videoController.personalizedHomeShelves.collectAsStateWithLifecycle()
    val videoInterestTopics by viewModel.videoController.userInterestTopics.collectAsStateWithLifecycle()
    val videoNotifications by viewModel.videoController.videoNotifications.collectAsStateWithLifecycle()
    val unreadVideoNotificationCount by viewModel.videoController.unreadVideoNotificationCount.collectAsStateWithLifecycle()
    val isSyncingVideoNotifications by viewModel.videoController.isSyncingNotifications.collectAsStateWithLifecycle()

    val watchLaterVideoIds = remember(watchLaterVideos) { watchLaterVideos.map { it.videoId }.toSet() }
    val savedVideoIds = remember(savedVideos) { savedVideos.map { it.videoId }.toSet() }

    val savedOnlineIds = remember(onlineSavedTracks) { onlineSavedTracks.map { it.id }.toSet() }
    val favoriteOnlineIds = remember(allOnlineTracks) {
        allOnlineTracks.filter { it.isFavorite }.map { it.id }.toSet()
    }

    val greeting = remember { HomeDiscoveryEngine.currentGreeting() }
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()

    var isRefreshing by remember { mutableStateOf(false) }
    var contextMenuSong by remember { mutableStateOf<Song?>(null) }
    var onlineOptionsTarget by remember { mutableStateOf<OnlineTrack?>(null) }
    var videoOptionsTarget by remember { mutableStateOf<OnlineVideoItem?>(null) }
    var videoPlaylistPickerTarget by remember { mutableStateOf<OnlineVideoItem?>(null) }
    var playlistPickerSong by remember { mutableStateOf<Song?>(null) }
    var onlinePlaylistPickerSong by remember { mutableStateOf<Song?>(null) }
    var activeCollection by remember { mutableStateOf<ActiveCollectionModal?>(null) }
    var showModeSwitcherSheet by remember { mutableStateOf(false) }
    var showDiscoverySheet by remember { mutableStateOf(false) }
    var showProfileSheet by remember { mutableStateOf(false) }
    var showOnlineRegionSheet by remember { mutableStateOf(false) }
    var showOnlineQualitySheet by remember { mutableStateOf(false) }
    var showVideoNotificationsSheet by remember { mutableStateOf(false) }
    var showFestivalDetailsSheet by remember { mutableStateOf(false) }

    LaunchedEffect(homeMusicMode, isNetworkOnline) {
        if (homeMusicMode == HomeMusicMode.ONLINE_VIDEO && isNetworkOnline) {
            viewModel.videoController.syncFollowedChannelNotificationsNow(userInitiated = false)
        }
    }

    // Derived collage songs (distinct albums for artistic collage variety)
    val collageSongs = remember(allSongs, yourMixSongs) {
        val pool = (yourMixSongs + allSongs).distinctBy { it.albumId }
        if (pool.size >= 3) pool.take(4) else allSongs.take(4)
    }

    // Artist chips for Your Mix strip
    val artistChips = remember(yourMixSongs, allSongs) {
        val source = yourMixSongs.ifEmpty { allSongs }
        source.groupBy { it.artist }
            .entries
            .filter { it.key.isNotBlank() && !it.key.equals("<unknown>", ignoreCase = true) }
            .take(10)
            .map { (name, tracks) -> MixArtistChip(name, tracks.first()) }
    }

    // Quick picks for "Made for You" asymmetric bento
    val quickPicks = remember(allSongs, favorites, mostPlayed) {
        (favorites + mostPlayed + allSongs.shuffled()).distinctBy { it.id }.take(6)
    }

    // Featured spotlight album
    val featuredAlbum = remember(favouriteAlbums, albums) {
        favouriteAlbums.firstOrNull() ?: albums.firstOrNull()
    }
    val featuredAlbumSong = remember(featuredAlbum, allSongs) {
        featuredAlbum?.let { alb -> allSongs.firstOrNull { it.album == alb.title } }
    }

    val isMixActivelyPlaying = playbackInfo.isPlaying && currentSong != null &&
        yourMixSongs.any { it.id == currentSong?.id }

    val latestWeatherSnapshot = weatherFetchState.latestSnapshot
    val isLowRamDevice = remember(context) {
        val am = context.getSystemService(android.content.Context.ACTIVITY_SERVICE) as? android.app.ActivityManager
        am?.isLowRamDevice == true
    }
    val coordinatedAmbient = remember(
        homeMusicMode,
        playbackInfo.isPlaying,
        currentSong?.id,
        weatherAmbientSettings,
        latestWeatherSnapshot,
        festivalAtmosphereSettings,
        activeFestivalResolution,
        isLowRamDevice,
        reduceAnimations
    ) {
        domain.ambient.AmbientEffectCoordinator.coordinateHome(
            homeMusicMode = homeMusicMode,
            isMusicPlaying = playbackInfo.isPlaying,
            hasCurrentSong = currentSong != null,
            weatherSettings = weatherAmbientSettings,
            weatherSnapshot = latestWeatherSnapshot,
            festivalSettings = festivalAtmosphereSettings,
            festivalResolution = activeFestivalResolution,
            isLowRamDevice = isLowRamDevice,
            globalReduceAnimations = reduceAnimations
        )
    }

    val mayurPetSlot: (@Composable (androidx.compose.ui.unit.Dp) -> Unit)? = if (virtualPetSettings.enabled) {
        { petSize ->
            ui.pet.MayurVirtualPet(
                settings = virtualPetSettings,
                currentSong = currentSong,
                isPlaying = playbackInfo.isPlaying,
                visualizerFrequenciesFlow = viewModel.visualizerFrequencies,
                waveformSamplesFlow = viewModel.waveformSamples,
                weatherPlan = coordinatedAmbient.weatherPlan,
                weatherSnapshot = latestWeatherSnapshot,
                activeFestival = activeFestivalResolution,
                reduceAnimations = reduceAnimations,
                screenVisible = screenVisible,
                size = petSize
            )
        }
    } else {
        null
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(colors.backgroundBrush)
            .statusBarsPadding()
            .testTag("home_screen"),
        contentAlignment = Alignment.TopCenter
    ) {
        // Weather-synced ambient atmosphere layer (behind all Home content; non-interactive; off in Online Video)
        ui.components.WeatherAtmosphereOverlay(
            plan = coordinatedAmbient.weatherPlan,
            settings = weatherAmbientSettings,
            colors = colors,
            surfaceRole = domain.weather.WeatherSurfaceRole.HomeBackground,
            isActive = coordinatedAmbient.isWeatherActive,
            screenVisible = screenVisible,
            modifier = Modifier.fillMaxSize(),
            testTagName = "home_weather_atmosphere_overlay"
        )

        // Pan-India Festival Atmosphere layer (coordinated with Weather; battery-efficient; off in Online Video)
        ui.components.FestivalAnimationRenderer(
            plan = coordinatedAmbient.festivalPlan,
            colors = colors,
            screenVisible = screenVisible && coordinatedAmbient.isFestivalActive,
            modifier = Modifier.fillMaxSize(),
            testTagName = "home_festival_atmosphere_overlay"
        )

        Column(
            modifier = Modifier
                .widthIn(max = 760.dp)
                .fillMaxSize()
        ) {
            // Persistent Top Greeting Header with Offline / Online Mode Switcher
            GreetingHeader(
                greeting = greeting,
                userProfile = userProfile,
                colors = colors,
                activeMode = homeMusicMode,
                reduceAnimations = reduceAnimations,
                unreadVideoNotificationsCount = unreadVideoNotificationCount,
                onVideoNotificationsClick = if (homeMusicMode == HomeMusicMode.ONLINE_VIDEO) {
                    { showVideoNotificationsSheet = true }
                } else null,
                onAvatarClick = { showProfileSheet = true },
                onDiscoveryClick = { showModeSwitcherSheet = true },
                onSettingsClick = onOpenSettings
            )

            // Compact Festival Indicator shown ONLY on recognized festival dates (zero space when hidden)
            if (coordinatedAmbient.showHomeFestivalIndicator && activeFestivalResolution.primary != null) {
                ui.components.FestivalIndicator(
                    resolution = activeFestivalResolution,
                    colors = colors,
                    onClick = { showFestivalDetailsSheet = true }
                )
            }

            PullToRefreshBox(
                isRefreshing = isRefreshing,
                onRefresh = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    isRefreshing = true
                    when (homeMusicMode) {
                        HomeMusicMode.OFFLINE -> viewModel.loadLocalSongs()
                        HomeMusicMode.ONLINE -> if (isNetworkOnline) {
                            viewModel.refreshOnlineDiscovery(forceRefresh = true)
                        }
                        HomeMusicMode.ONLINE_VIDEO -> if (isNetworkOnline) {
                            viewModel.videoController.refreshHomeDiscovery(forceRefresh = true)
                        }
                    }
                    scope.launch {
                        delay(700)
                        isRefreshing = false
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .horizontalSwipeNavigation(
                        enabled = screenVisible,
                        motion = motionState,
                        onSwipeLeft = {
                            when (homeMusicMode) {
                                HomeMusicMode.OFFLINE -> viewModel.setHomeMusicMode(HomeMusicMode.ONLINE)
                                HomeMusicMode.ONLINE -> viewModel.setHomeMusicMode(HomeMusicMode.ONLINE_VIDEO)
                                HomeMusicMode.ONLINE_VIDEO -> onNavigateToSearch()
                            }
                        },
                        onSwipeRight = {
                            when (homeMusicMode) {
                                HomeMusicMode.ONLINE_VIDEO -> viewModel.setHomeMusicMode(HomeMusicMode.ONLINE)
                                HomeMusicMode.ONLINE -> viewModel.setHomeMusicMode(HomeMusicMode.OFFLINE)
                                HomeMusicMode.OFFLINE -> Unit
                            }
                        }
                    )
            ) {
                AnimatedContent(
                    targetState = homeMusicMode,
                    transitionSpec = {
                        SonoraTransitions.homeModeTransition(
                            toOnline = targetState.ordinal >= initialState.ordinal,
                            motion = motionState
                        )
                    },
                    label = "home_music_mode_transition",
                    modifier = Modifier.fillMaxSize()
                ) { mode ->
                    when (mode) {
                        HomeMusicMode.OFFLINE -> {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .testTag("home_offline_content")
                            ) {
                                LazyColumn(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .testTag("home_lazy_column"),
                                    contentPadding = PaddingValues(
                                        top = 4.dp,
                                        bottom = domain.player.MusicMiniPlayerVisibilityPolicy.listBottomContentPadding(
                                            isMusicMiniPlayerVisible = currentSong != null &&
                                                !videoPlayerState.hasActiveVideo,
                                            isVideoMiniPlayerVisible = videoPlayerState.hasActiveVideo && !videoPlayerState.isPlayerExpanded
                                        )
                                    ),
                                    verticalArrangement = Arrangement.spacedBy(26.dp)
                                ) {
                                    // Empty local library fallback
                                    if (allSongs.isEmpty()) {
                                        item(key = "empty_library_state") {
                                            EmptyLibraryEditorialCard(
                                                colors = colors,
                                                onScanLibrary = { viewModel.loadLocalSongs() },
                                                onOpenLibrary = onNavigateToLibrary,
                                                onOpenOnlineMusic = { viewModel.setHomeMusicMode(HomeMusicMode.ONLINE) }
                                            )
                                        }

                                        val activeHomeOnlineErr = playbackInfo.playbackError
                                        if (!activeHomeOnlineErr.isNullOrBlank() && currentSong?.isOnline == true) {
                                            item(key = "home_online_playback_error_empty") {
                                                val errKind = playbackInfo.playbackErrorKind ?: OnlineErrorKind.EXTRACTION_FAILED
                                                val canSwitchSc = errKind == OnlineErrorKind.RATE_LIMITED_OR_CAPTCHA &&
                                                    selectedOnlineProvider?.key != OnlineMusicProvider.KEY_SOUNDCLOUD
                                                OnlineErrorCard(
                                                    kind = errKind,
                                                    message = activeHomeOnlineErr,
                                                    showRetry = true,
                                                    colors = colors,
                                                    onRetry = { viewModel.retryCurrentPlayback() },
                                                    secondaryActionLabel = if (canSwitchSc) "Try SoundCloud" else null,
                                                    onSecondaryAction = if (canSwitchSc) {
                                                        { viewModel.switchToSoundCloudFallback() }
                                                    } else null,
                                                    onDismiss = { viewModel.clearPlaybackError() }
                                                )
                                            }
                                        }
                                    } else {
                                        // 1 & 2. Your Mix + Local Artist Shortcuts
                                        item(key = "your_mix_hero") {
                                            YourMixSection(
                                                contributorsSummary = yourMixContributors,
                                                artistChips = artistChips,
                                                isMixPlaying = isMixActivelyPlaying,
                                                hasTracks = yourMixSongs.isNotEmpty(),
                                                colors = colors,
                                                animationLevel = homeAnimationLevel,
                                                screenVisible = screenVisible,
                                                petContent = mayurPetSlot,
                                                onPlayMix = {
                                                    if (isMixActivelyPlaying) {
                                                        viewModel.togglePlayPause()
                                                    } else {
                                                        viewModel.playCollection(yourMixSongs, shuffle = false)
                                                    }
                                                },
                                                onShuffleMix = {
                                                    viewModel.playCollection(yourMixSongs, shuffle = true)
                                                },
                                                onArtistChipClick = { chip ->
                                                    val artistSongs = allSongs.filter { it.artist.equals(chip.artistName, ignoreCase = true) }
                                                    activeCollection = ActiveCollectionModal(
                                                        title = chip.artistName,
                                                        subtitle = "Artist Collection",
                                                        songs = artistSongs
                                                    )
                                                }
                                            )
                                        }

                                        // 3. Local Music Statistics Summary Card
                                        item(key = "local_library_stats") {
                                            LocalLibraryStatsCard(
                                                stats = localLibraryStats,
                                                colors = colors,
                                                onOpenLibrary = onNavigateToLibrary
                                            )
                                        }

                                        // 4. Featured Local Album Artwork Collage
                                        item(key = "featured_collage") {
                                            FeaturedArtworkCollage(
                                                collageSongs = collageSongs,
                                                colors = colors,
                                                animationLevel = homeAnimationLevel,
                                                screenVisible = screenVisible,
                                                isPlaying = playbackInfo.isPlaying,
                                                onArtworkClick = { song ->
                                                    val albumSongs = allSongs.filter { it.album == song.album }
                                                    if (albumSongs.isNotEmpty()) {
                                                        activeCollection = ActiveCollectionModal(
                                                            title = song.album.ifBlank { song.title },
                                                            subtitle = song.artist,
                                                            songs = albumSongs
                                                        )
                                                    } else {
                                                        viewModel.playSong(song, allSongs)
                                                    }
                                                },
                                                onArtworkLongClick = { song ->
                                                    contextMenuSong = song
                                                }
                                            )
                                        }

                                        // Quick Studio & Queue Pills (EQ, Audio Studio, Playlists, Queue)
                                        item(key = "quick_tools_strip") {
                                            QuickToolsEditorialStrip(
                                                playlistCount = playlists.size,
                                                colors = colors,
                                                onOpenEqualizer = onOpenEqualizer,
                                                onOpenAudioStudio = onOpenAudioStudio,
                                                onOpenPlaylists = onOpenPlaylists,
                                                onOpenQueue = onOpenQueue
                                            )
                                        }

                                        val activeHomeOnlineErr = playbackInfo.playbackError
                                        if (!activeHomeOnlineErr.isNullOrBlank() && currentSong?.isOnline == true) {
                                            item(key = "home_online_playback_error") {
                                                val errKind = playbackInfo.playbackErrorKind ?: OnlineErrorKind.EXTRACTION_FAILED
                                                val canSwitchSc = errKind == OnlineErrorKind.RATE_LIMITED_OR_CAPTCHA &&
                                                    selectedOnlineProvider?.key != OnlineMusicProvider.KEY_SOUNDCLOUD
                                                OnlineErrorCard(
                                                    kind = errKind,
                                                    message = activeHomeOnlineErr,
                                                    showRetry = true,
                                                    colors = colors,
                                                    onRetry = { viewModel.retryCurrentPlayback() },
                                                    secondaryActionLabel = if (canSwitchSc) "Try SoundCloud" else null,
                                                    onSecondaryAction = if (canSwitchSc) {
                                                        { viewModel.switchToSoundCloudFallback() }
                                                    } else null,
                                                    onDismiss = { viewModel.clearPlaybackError() }
                                                )
                                            }
                                        }

                                        // 5. Recently Played
                                        if (recentlyPlayed.isNotEmpty()) {
                                            item(key = "recently_played") {
                                                RecentlyPlayedSection(
                                                    songs = recentlyPlayed,
                                                    currentPlayingSongId = currentSong?.id,
                                                    isPlaying = playbackInfo.isPlaying,
                                                    colors = colors,
                                                    animationLevel = homeAnimationLevel,
                                                    screenVisible = screenVisible,
                                                    onSongClick = { song -> viewModel.playSong(song, recentlyPlayed) },
                                                    onSongLongClick = { song -> contextMenuSong = song }
                                                )
                                            }
                                        }

                                        // 6. Made for You
                                        item(key = "made_for_you") {
                                            MadeForYouEditorialSection(
                                                quickPicks = quickPicks,
                                                favorites = favorites,
                                                currentSongId = currentSong?.id,
                                                colors = colors,
                                                animationLevel = homeAnimationLevel,
                                                screenVisible = screenVisible,
                                                onSongClick = { song -> viewModel.playSong(song, quickPicks) },
                                                onSongLongClick = { song -> contextMenuSong = song },
                                                onOpenFavorites = {
                                                    if (favorites.isNotEmpty()) {
                                                        activeCollection = ActiveCollectionModal(
                                                            title = "Favourite Tracks",
                                                            subtitle = "Your liked collection",
                                                            songs = favorites
                                                        )
                                                    }
                                                }
                                            )
                                        }

                                        // 7. Local Daily Mixes
                                        if (dailyMixes.isNotEmpty()) {
                                            item(key = "daily_mixes") {
                                                DailyMixSection(
                                                    mixes = dailyMixes,
                                                    colors = colors,
                                                    animationStyle = dailyMixAnimationStyle,
                                                    animationLevel = homeAnimationLevel,
                                                    screenVisible = screenVisible,
                                                    currentPlayingSongId = currentSong?.id,
                                                    isPlaying = playbackInfo.isPlaying,
                                                    onMixClick = { mix ->
                                                        activeCollection = ActiveCollectionModal(
                                                            title = mix.title,
                                                            subtitle = mix.subtitle,
                                                            songs = mix.songs
                                                        )
                                                    },
                                                    onPlayMix = { mix ->
                                                        val isThisMixPlaying = playbackInfo.isPlaying && currentSong != null &&
                                                            mix.songs.any { it.id == currentSong?.id }
                                                        if (isThisMixPlaying) {
                                                            viewModel.togglePlayPause()
                                                        } else {
                                                            viewModel.playCollection(mix.songs, shuffle = false)
                                                        }
                                                    },
                                                    onShuffleMix = { mix ->
                                                        viewModel.playCollection(mix.songs, shuffle = true)
                                                    }
                                                )
                                            }
                                        }

                                        // 8. Favourite Albums
                                        if (favouriteAlbums.isNotEmpty()) {
                                            item(key = "favourite_albums") {
                                                FavouriteAlbumsSection(
                                                    albums = favouriteAlbums,
                                                    allSongs = allSongs,
                                                    colors = colors,
                                                    animationLevel = homeAnimationLevel,
                                                    screenVisible = screenVisible,
                                                    onAlbumClick = { album ->
                                                        val albumSongs = allSongs.filter { it.album == album.title }
                                                        activeCollection = ActiveCollectionModal(
                                                            title = album.title,
                                                            subtitle = album.artist,
                                                            songs = albumSongs
                                                        )
                                                    },
                                                    onAlbumLongClick = { song -> contextMenuSong = song }
                                                )
                                            }
                                        }

                                        // 9. Favourite Artists
                                        if (favouriteArtists.isNotEmpty()) {
                                            item(key = "favourite_artists") {
                                                FavouriteArtistsSection(
                                                    artists = favouriteArtists,
                                                    allSongs = allSongs,
                                                    colors = colors,
                                                    animationLevel = homeAnimationLevel,
                                                    screenVisible = screenVisible,
                                                    onArtistClick = { artist ->
                                                        val artistSongs = allSongs.filter { it.artist == artist.name }
                                                        activeCollection = ActiveCollectionModal(
                                                            title = artist.name,
                                                            subtitle = "${artist.albumCount} albums",
                                                            songs = artistSongs
                                                        )
                                                    }
                                                )
                                            }
                                        }

                                        // 10. Recently Added
                                        if (recentlyAdded.isNotEmpty()) {
                                            item(key = "recently_added") {
                                                RecentlyAddedSection(
                                                    songs = recentlyAdded.take(5),
                                                    currentPlayingSongId = currentSong?.id,
                                                    colors = colors,
                                                    animationLevel = homeAnimationLevel,
                                                    screenVisible = screenVisible,
                                                    onSongClick = { song -> viewModel.playSong(song, recentlyAdded) },
                                                    onSongLongClick = { song -> contextMenuSong = song },
                                                    onSeeAllClick = onNavigateToLibrary
                                                )
                                            }
                                        }

                                        // 11. Most Played
                                        val actualMostPlayed = mostPlayed.filter { it.playCount > 0 }.ifEmpty { mostPlayed.take(6) }
                                        if (actualMostPlayed.isNotEmpty()) {
                                            item(key = "most_played") {
                                                MostPlayedArchSection(
                                                    songs = actualMostPlayed,
                                                    colors = colors,
                                                    animationLevel = homeAnimationLevel,
                                                    screenVisible = screenVisible,
                                                    onSongClick = { song -> viewModel.playSong(song, actualMostPlayed) },
                                                    onSongLongClick = { song -> contextMenuSong = song }
                                                )
                                            }
                                        }

                                        // 12. Featured Music Spotlight Card
                                        item(key = "featured_music") {
                                            FeaturedMusicSection(
                                                featuredAlbum = featuredAlbum,
                                                representativeSong = featuredAlbumSong,
                                                colors = colors,
                                                animationLevel = homeAnimationLevel,
                                                screenVisible = screenVisible,
                                                isPlaying = playbackInfo.isPlaying && currentSong?.album == featuredAlbum?.title,
                                                onOpenAlbum = { album ->
                                                    val albumSongs = allSongs.filter { it.album == album.title }
                                                    activeCollection = ActiveCollectionModal(
                                                        title = album.title,
                                                        subtitle = album.artist,
                                                        songs = albumSongs
                                                    )
                                                },
                                                onPlayAlbum = { album ->
                                                    val albumSongs = allSongs.filter { it.album == album.title }
                                                    viewModel.playCollection(albumSongs, shuffle = false)
                                                },
                                                onShuffleAlbum = { album ->
                                                    val albumSongs = allSongs.filter { it.album == album.title }
                                                    viewModel.playCollection(albumSongs, shuffle = true)
                                                },
                                                onLongClick = { song -> contextMenuSong = song }
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        HomeMusicMode.ONLINE -> {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .testTag("home_online_content")
                            ) {
                                OnlineHomeContent(
                                    discoverSections = discoverSections,
                                    recentlyPlayedOnline = recentlyPlayedOnline,
                                    savedOnlineTracks = onlineSavedTracks,
                                    playlists = playlists,
                                    selectedProvider = selectedOnlineProvider,
                                    discoverLanguage = discoverMusicLanguage,
                                    countryCode = onlineCountryCode,
                                    recommendationProfile = recommendationProfile,
                                    isNetworkOnline = isNetworkOnline,
                                    currentSong = currentSong,
                                    isPlaying = playbackInfo.isPlaying,
                                    playbackError = playbackInfo.playbackError,
                                    playbackErrorKind = playbackInfo.playbackErrorKind,
                                    colors = colors,
                                    animationLevel = homeAnimationLevel,
                                    dailyMixAnimationStyle = dailyMixAnimationStyle,
                                    screenVisible = screenVisible,
                                    reduceAnimations = reduceAnimations,
                                    onSelectDiscoverLanguage = { viewModel.setDiscoverMusicLanguage(it) },
                                    onOpenRegionSheet = { showOnlineRegionSheet = true },
                                    onRetrySection = { viewModel.retryDiscoverSection(it) },
                                    onLoadMoreSection = { viewModel.loadMoreDiscoverSection(it) },
                                    onPlayCollection = { songs, shuffle ->
                                        viewModel.playCollection(songs, shuffle = shuffle)
                                        onOpenNowPlaying()
                                    },
                                    onOpenLocalCollectionModal = { title, subtitle, songs ->
                                        activeCollection = ActiveCollectionModal(title, subtitle, songs)
                                    },
                                    onTrackClick = { track, pool ->
                                        viewModel.playOnlineTrack(track, pool)
                                        onOpenNowPlaying()
                                    },
                                    onCollectionClick = { viewModel.openOnlineCollection(it) },
                                    onTrackLongClick = { onlineOptionsTarget = it },
                                    onRecentOnlineSongClick = { song ->
                                        viewModel.playSong(song, recentlyPlayedOnline)
                                        onOpenNowPlaying()
                                    },
                                    onRecentOnlineSongLongClick = { song ->
                                        onlineOptionsTarget = OnlineTrackMapper.toOnlineTrack(song)
                                    },
                                    onRetryPlayback = { viewModel.retryCurrentPlayback() },
                                    onSwitchSoundCloudFallback = { viewModel.switchToSoundCloudFallback() },
                                    onDismissPlaybackError = { viewModel.clearPlaybackError() },
                                    onOpenPlaylistsScreen = onOpenPlaylists,
                                    sonoraRecommendState = sonoraRecommendState,
                                    favoriteOnlineIds = favoriteOnlineIds,
                                    onRefreshSonoraRecommend = { viewModel.refreshSonoraRecommendations() },
                                    onPlayRecommendedTrack = { track, pool ->
                                        viewModel.playRecommendedTrack(track, pool) {
                                            onOpenNowPlaying()
                                        }
                                    },
                                    onAddRecommendedToQueue = { viewModel.addOnlineTrackToQueue(it) },
                                    onPlayRecommendedNext = { viewModel.enqueueOnlineTrackNext(it) },
                                    onToggleLikeRecommended = {
                                        viewModel.toggleFavorite(OnlineTrackMapper.toUnifiedSong(it))
                                    },
                                    onDislikeRecommended = { viewModel.dislikeRecommendedTrack(it) },
                                    onOpenRecommendedArtist = { viewModel.openRecommendedArtist(it) },
                                    onOpenRecommendedAlbum = { viewModel.openRecommendedAlbum(it) },
                                    hasMusicMiniPlayer = currentSong != null,
                                    petContent = mayurPetSlot
                                )
                            }
                        }

                        HomeMusicMode.ONLINE_VIDEO -> {
                            OnlineVideoHomeContent(
                                sections = videoHomeSections,
                                recentlyWatched = videoRecentlyWatched,
                                selectedRegionalLanguage = videoRegionalLanguage,
                                countryCode = onlineCountryCode,
                                isNetworkOnline = isNetworkOnline,
                                activeVideoId = videoPlayerState.activeVideo?.videoId,
                                colors = colors,
                                onOpenVideoSearch = onNavigateToSearch,
                                onOpenRegionSheet = { showOnlineRegionSheet = true },
                                onSelectRegionalLanguage = { viewModel.videoController.selectRegionalLanguage(it) },
                                onRefreshAll = { viewModel.videoController.refreshHomeDiscovery(forceRefresh = true) },
                                onRetrySection = { viewModel.videoController.loadHomeSection(it, forceRefresh = true) },
                                onLoadMoreSection = { viewModel.videoController.loadMoreHomeSection(it) },
                                onPlayVideo = { video, pool ->
                                    viewModel.videoController.playVideo(video, pool, expandPlayer = true)
                                },
                                onOpenChannel = { url, name ->
                                    viewModel.videoController.openChannelProfile(url, name)
                                },
                                onVideoMoreClick = { videoOptionsTarget = it },
                                personalizedShelves = personalizedVideoShelves,
                                selectedInterestTopics = videoInterestTopics,
                                onToggleInterestTopic = { viewModel.videoController.toggleUserInterestTopic(it) },
                                hasVideoMiniPlayer = videoPlayerState.hasActiveVideo && !videoPlayerState.isPlayerExpanded
                            )
                        }
                    }
                }
            }
        }
    }

    // Long-press Music Context Menu Bottom Sheet
    contextMenuSong?.let { target ->
        MusicContextMenuSheet(
            song = target,
            colors = colors,
            isDownloaded = viewModel.isSongDownloaded(target),
            onDownload = if (target.isOnline) {
                { viewModel.requestDownloadSong(target) }
            } else null,
            onDismiss = { contextMenuSong = null },
            onPlayNow = { viewModel.playSong(target, allSongs) },
            onPlayNext = { viewModel.playNextInQueue(target) },
            onAddToQueue = { viewModel.addToQueue(target) },
            onAddToPlaylist = { playlistPickerSong = target },
            onToggleFavorite = { viewModel.toggleFavorite(target) },
            onViewAlbum = {
                val albumSongs = allSongs.filter { it.album == target.album }
                activeCollection = ActiveCollectionModal(
                    title = target.album,
                    subtitle = target.artist,
                    songs = albumSongs
                )
            },
            onViewArtist = {
                val artistSongs = allSongs.filter { it.artist == target.artist }
                activeCollection = ActiveCollectionModal(
                    title = target.artist,
                    subtitle = "Artist Discography",
                    songs = artistSongs
                )
            }
        )
    }

    // Add to Playlist Picker (supports both local and online songs)
    playlistPickerSong?.let { target ->
        EditorialPlaylistPickerDialog(
            song = target,
            playlists = playlists,
            colors = colors,
            onDismiss = { playlistPickerSong = null },
            onSelectPlaylist = { pid ->
                if (target.isOnline) {
                    viewModel.addOnlineTrackToPlaylist(pid, target)
                } else {
                    viewModel.addSongToPlaylist(pid, target.id)
                }
                playlistPickerSong = null
            },
            onCreatePlaylist = { name ->
                viewModel.createPlaylist(name)
            }
        )
    }

    // Online Track Context Menu Bottom Sheet
    onlineOptionsTarget?.let { target ->
        val artistUrl = if (target.kind == OnlineItemKind.ARTIST) target.contentUrl else target.artistUrl.orEmpty()
        val isArtistFollowed = artistUrl.isNotBlank() &&
            OnlineTrackMapper.computeStableId(target.providerKey.ifBlank { "online" }, artistUrl.trim()) in followedOnlineArtistIds
        val isCollectionSaved = when (target.kind) {
            OnlineItemKind.ALBUM -> OnlineTrackMapper.computeStableId(target.providerKey.ifBlank { "online" }, target.contentUrl.trim()) in savedOnlineAlbumIds
            OnlineItemKind.PLAYLIST -> target.contentUrl.trim() in savedOnlinePlaylistUrls
            else -> false
        }
        OnlineTrackOptionsSheet(
            track = target,
            isSaved = target.stableId in savedOnlineIds,
            isFavorite = target.stableId in favoriteOnlineIds,
            isDownloaded = viewModel.isTrackDownloaded(target),
            isArtistFollowed = isArtistFollowed,
            isCollectionSaved = isCollectionSaved,
            resolvedStream = currentResolvedStream,
            colors = colors,
            onPlayNow = {
                if (target.kind == OnlineItemKind.TRACK) {
                    val discoveryPool = discoverSections.values
                        .filterIsInstance<DiscoverSectionState.Success>()
                        .flatMap { it.items }
                        .filter { it.kind == OnlineItemKind.TRACK }
                        .distinctBy { it.contentUrl }
                    viewModel.playOnlineTrack(target, discoveryPool)
                    onOpenNowPlaying()
                } else {
                    viewModel.openOnlineCollection(target)
                }
            },
            onPlayNext = { viewModel.enqueueOnlineTrackNext(target) },
            onAddToQueue = { viewModel.addOnlineTrackToQueue(target) },
            onToggleSave = { viewModel.toggleSaveOnlineTrack(target) },
            onToggleFavorite = { viewModel.toggleFavorite(OnlineTrackMapper.toUnifiedSong(target)) },
            onAddToPlaylist = { playlistPickerSong = OnlineTrackMapper.toUnifiedSong(target) },
            onAddToOnlinePlaylist = if (target.kind == OnlineItemKind.TRACK) {
                { onlinePlaylistPickerSong = OnlineTrackMapper.toUnifiedSong(target) }
            } else null,
            onToggleFollowArtist = if (artistUrl.isNotBlank()) {
                { viewModel.toggleFollowOnlineArtist(target) }
            } else null,
            onToggleSaveCollection = when (target.kind) {
                OnlineItemKind.ALBUM -> ({ viewModel.toggleSaveOnlineAlbum(target) })
                OnlineItemKind.PLAYLIST -> ({ viewModel.toggleSaveExternalOnlinePlaylist(target) })
                else -> null
            },
            onDownload = { viewModel.requestDownloadTrack(target) },
            onOpenQualitySettings = { showOnlineQualitySheet = true },
            onViewArtistChannel = buildArtistExploreTrack(target)?.let { channelItem ->
                { viewModel.openOnlineCollection(channelItem) }
            },
            onDismiss = { onlineOptionsTarget = null }
        )
    }

    onlinePlaylistPickerSong?.let { songTarget ->
        OnlinePlaylistPickerDialog(
            song = songTarget,
            playlists = onlinePlaylists,
            colors = colors,
            onDismiss = { onlinePlaylistPickerSong = null },
            onSelectPlaylist = { playlist ->
                viewModel.addTrackToOnlinePlaylist(playlist.id, songTarget, playlist.name)
            },
            onCreatePlaylistWithSong = { name ->
                viewModel.createOnlinePlaylist(name, initialTrack = songTarget)
            }
        )
    }

    // Online Artist / Album / Playlist Collection Details Sheet
    OnlineCollectionDetailsSheet(
        state = onlineCollectionState,
        currentSongId = currentSong?.id,
        isPlaying = playbackInfo.isPlaying,
        isBuffering = playbackInfo.isBuffering,
        savedIds = savedOnlineIds,
        colors = colors,
        isTrackDownloaded = { viewModel.isTrackDownloaded(it) },
        onDownloadTrack = { viewModel.requestDownloadTrack(it) },
        isCollectionSavedOrFollowed = { source ->
            when (source.kind) {
                OnlineItemKind.ARTIST -> OnlineTrackMapper.computeStableId(source.providerKey.ifBlank { "online" }, source.contentUrl.trim()) in followedOnlineArtistIds
                OnlineItemKind.ALBUM -> OnlineTrackMapper.computeStableId(source.providerKey.ifBlank { "online" }, source.contentUrl.trim()) in savedOnlineAlbumIds
                OnlineItemKind.PLAYLIST -> source.contentUrl.trim() in savedOnlinePlaylistUrls
                else -> false
            }
        },
        onToggleSaveOrFollowCollection = { source, details ->
            when (source.kind) {
                OnlineItemKind.ARTIST -> viewModel.toggleFollowOnlineArtist(source, details)
                OnlineItemKind.ALBUM -> viewModel.toggleSaveOnlineAlbum(source, details)
                OnlineItemKind.PLAYLIST -> viewModel.toggleSaveExternalOnlinePlaylist(source, details)
                else -> Unit
            }
        },
        onPlayTrack = { track, tracks ->
            viewModel.playOnlineTrack(track, tracks)
            onOpenNowPlaying()
        },
        onPlayAll = { tracks, shuffle ->
            val songs = tracks.map { OnlineTrackMapper.toUnifiedSong(it) }
            viewModel.playCollection(songs, shuffle = shuffle)
            onOpenNowPlaying()
        },
        onRetry = { item -> viewModel.openOnlineCollection(item) },
        onTrackMoreClick = { track -> onlineOptionsTarget = track },
        onDismiss = { viewModel.dismissOnlineCollection() }
    )

    if (showOnlineQualitySheet) {
        OnlineQualitySelectorSheet(
            currentPreference = onlineQualityPreference,
            resolvedStream = currentResolvedStream,
            isMeteredNetwork = isMeteredNetwork,
            isNetworkOnline = isNetworkOnline,
            colors = colors,
            onSelectPreference = { viewModel.selectOnlineQualityPreference(it) },
            onSelectManualStream = { viewModel.selectManualOnlineStreamOption(it) },
            onDismiss = { showOnlineQualitySheet = false }
        )
    }

    if (showOnlineRegionSheet) {
        OnlineRegionSheet(
            currentCountryCode = onlineCountryCode,
            currentLanguageCode = onlineLanguageCode,
            colors = colors,
            onSelectCountry = { viewModel.setOnlineContentCountry(it) },
            onSelectLanguage = { viewModel.setOnlineContentLanguage(it) },
            onDismiss = { showOnlineRegionSheet = false }
        )
    }

    // Album / Artist / Daily Mix Collection Detail Sheet
    activeCollection?.let { modal ->
        CollectionDetailSheet(
            title = modal.title,
            subtitle = modal.subtitle,
            songs = modal.songs,
            currentPlayingSongId = currentSong?.id,
            isPlaying = playbackInfo.isPlaying,
            colors = colors,
            onDismiss = { activeCollection = null },
            onPlayAll = {
                viewModel.playCollection(modal.songs, shuffle = false)
            },
            onShufflePlay = {
                viewModel.playCollection(modal.songs, shuffle = true)
            },
            onPlaySong = { track ->
                viewModel.playSong(track, modal.songs)
            },
            onSongMoreClick = { track ->
                contextMenuSong = track
            }
        )
    }

    // Home Music Mode Switcher Sheet (Offline Music <-> Online Music <-> Online Video)
    if (showModeSwitcherSheet) {
        HomeModeSwitcherSheet(
            currentMode = homeMusicMode,
            isNetworkOnline = isNetworkOnline,
            localTrackCount = localLibraryStats.trackCount,
            colors = colors,
            reduceAnimations = reduceAnimations,
            onSelectMode = { mode -> viewModel.setHomeMusicMode(mode) },
            onOpenThemesAndShuffle = { showDiscoverySheet = true },
            onDismiss = { showModeSwitcherSheet = false }
        )
    }

    videoOptionsTarget?.let { target ->
        VideoOptionsBottomSheet(
            video = target,
            isWatchLater = target.videoId in watchLaterVideoIds,
            isSaved = target.videoId in savedVideoIds,
            colors = colors,
            onPlayNow = { viewModel.videoController.playVideo(target, expandPlayer = true) },
            onPlayNext = { viewModel.videoController.enqueueVideoNext(target) },
            onToggleWatchLater = { viewModel.videoController.toggleWatchLater(target) },
            onToggleSave = { viewModel.videoController.toggleSaveVideo(target) },
            onAddToPlaylist = { videoPlaylistPickerTarget = target },
            onOpenChannel = target.channelUrl?.takeIf { it.isNotBlank() }?.let { url ->
                { viewModel.videoController.openChannelProfile(url, target.channelName) }
            },
            onShare = { shareVideoLink(context, target) },
            onOpenOfficialApp = { openOfficialExternalUrl(context, target.contentUrl) },
            onNotInterested = { viewModel.videoController.markVideoNotInterested(target) },
            onDontRecommendChannel = { viewModel.videoController.dontRecommendChannel(target) },
            onHideVideo = { viewModel.videoController.hideVideoFromFeed(target) },
            onDismiss = { videoOptionsTarget = null }
        )
    }

    videoPlaylistPickerTarget?.let { target ->
        VideoPlaylistPickerDialog(
            video = target,
            playlists = videoPlaylists,
            colors = colors,
            onSelectPlaylist = { playlist ->
                viewModel.videoController.addVideoToPlaylist(playlist.id, playlist.name, target)
            },
            onCreatePlaylistWithVideo = { name ->
                viewModel.videoController.createVideoPlaylist(name, target)
            },
            onDismiss = { videoPlaylistPickerTarget = null }
        )
    }

    if (showVideoNotificationsSheet) {
        VideoNotificationsPanel(
            notifications = videoNotifications,
            isSyncing = isSyncingVideoNotifications,
            colors = colors,
            onOpenVideo = { item -> viewModel.videoController.openNotificationItem(item) },
            onOpenChannel = { item -> viewModel.videoController.openNotificationChannel(item) },
            onMarkAsRead = { id -> viewModel.videoController.markVideoNotificationRead(id) },
            onMarkAllRead = { viewModel.videoController.markAllVideoNotificationsRead() },
            onDeleteNotification = { id -> viewModel.videoController.deleteVideoNotification(id) },
            onClearAll = { viewModel.videoController.clearAllVideoNotifications() },
            onCheckNow = { viewModel.videoController.syncFollowedChannelNotificationsNow(userInitiated = true) },
            onDismiss = { showVideoNotificationsSheet = false }
        )
    }

    // Discovery & Editorial Theme Quick Sheet (tapping starburst discovery icon)
    if (showDiscoverySheet) {
        DiscoveryAndThemeSheet(
            allSongs = allSongs,
            currentMode = homeThemeMode,
            customAccentArgb = customAccentArgb,
            colors = colors,
            onDismiss = { showDiscoverySheet = false },
            onDiscoverRandom = {
                val unplayed = allSongs.filter { it.playCount == 0 }.ifEmpty { allSongs }
                viewModel.playCollection(unplayed, shuffle = true)
                showDiscoverySheet = false
            },
            onSelectThemeMode = { mode -> viewModel.setHomeThemeMode(mode) },
            onSelectAccent = { argb -> viewModel.setHomeCustomAccentArgb(argb) }
        )
    }

    // Personal User Profile & Personalization Sheet (tapping top-left avatar)
    if (showProfileSheet) {
        UserProfileSheet(
            userProfile = userProfile,
            colors = colors,
            appearanceMode = appearanceMode,
            colorIntensity = colorIntensity,
            playerStyle = playerStyle,
            reduceAnimations = reduceAnimations,
            onDismiss = { showProfileSheet = false },
            onPickPhotoUri = { uri -> viewModel.updateUserProfilePhoto(uri) },
            onRemovePhoto = { viewModel.removeUserProfilePhoto() },
            onSaveProfileDetails = { name, handle -> viewModel.updateUserProfileDetails(name, handle) },
            onToggleReduceAnimations = { reduce -> viewModel.setReduceAnimations(reduce) },
            onOpenDynamicColors = onOpenDynamicColors,
            onOpenPlayerStyles = onOpenPlayerStyles,
            onOpenSettings = onOpenSettings,
            onOpenMusicAnalytics = onOpenMusicAnalytics,
            musicTasteSummary = musicTasteProfile.summarySubtitle(),
            onOpenMusicTaste = onOpenMusicTaste
        )
    }

    if (showFestivalDetailsSheet && activeFestivalResolution.hasActiveFestival) {
        ui.components.FestivalDetailsBottomSheet(
            resolution = activeFestivalResolution,
            colors = colors,
            onDismiss = { showFestivalDetailsSheet = false },
            onOpenFestivalSettings = onOpenSettings
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MadeForYouEditorialSection(
    quickPicks: List<Song>,
    favorites: List<Song>,
    currentSongId: Long?,
    colors: EditorialColors,
    animationLevel: ui.home.animations.HomeAnimationLevel,
    screenVisible: Boolean,
    onSongClick: (Song) -> Unit,
    onSongLongClick: (Song) -> Unit,
    onOpenFavorites: () -> Unit
) {
    if (quickPicks.isEmpty()) return
    val haptic = LocalHapticFeedback.current

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("made_for_you_section")
    ) {
        EditorialSectionTitle(
            title = "Made for You",
            subtitle = "Tailored quick picks & favourites",
            colors = colors
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Highlighted Favourites pill banner if favorites exist
        if (favorites.isNotEmpty()) {
            Surface(
                onClick = onOpenFavorites,
                shape = RoundedCornerShape(22.dp),
                color = colors.accentPill.copy(alpha = 0.72f),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 12.dp)
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
                                imageVector = Icons.Rounded.Favorite,
                                contentDescription = null,
                                tint = colors.accentStrong,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "Liked Songs Mix",
                                fontFamily = OutfitFontFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                color = colors.onAccentPill
                            )
                            Text(
                                text = "${favorites.size} saved favourites ready to play",
                                fontFamily = PlusJakartaFontFamily,
                                fontSize = 12.sp,
                                color = colors.onAccentPill.copy(alpha = 0.75f)
                            )
                        }
                    }

                    Icon(
                        imageVector = Icons.Rounded.PlayArrow,
                        contentDescription = "Open Liked Songs",
                        tint = colors.onAccentPill
                    )
                }
            }
        }

        // Compact horizontal 2-per-column quick pick tiles (varied compact row layout)
        val motionState = LocalEffectiveMotionState.current
        val listState = androidx.compose.foundation.lazy.rememberLazyListState()
        val snapFling = androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior(lazyListState = listState)
        val chunked = quickPicks.chunked(2)
        LazyRow(
            state = listState,
            flingBehavior = snapFling,
            contentPadding = PaddingValues(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            itemsIndexed(chunked, key = { idx, pair -> "quick_pair_${idx}_${pair.firstOrNull()?.id ?: idx}" }) { index, pair ->
                Column(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier
                        .width(248.dp)
                        .carouselItemDepthEffect(listState, index, motionState)
                ) {
                    pair.forEach { song ->
                        val isCurrent = currentSongId == song.id
                        Surface(
                            shape = RoundedCornerShape(18.dp),
                            color = if (isCurrent) colors.accentPill.copy(alpha = 0.55f) else colors.surface,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(18.dp))
                                .border(0.75.dp, colors.cardBorder, RoundedCornerShape(18.dp))
                                .combinedClickable(
                                    onClick = { onSongClick(song) },
                                    onLongClick = {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        onSongLongClick(song)
                                    }
                                )
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 9.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                CircularArtwork(
                                    song = song,
                                    accentColor = colors.accentPill,
                                    shadowElevation = 2.dp,
                                    animationLevel = animationLevel,
                                    screenVisible = screenVisible,
                                    modifier = Modifier.size(44.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = song.title,
                                        fontFamily = OutfitFontFamily,
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 14.sp,
                                        color = colors.textPrimary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = song.artist,
                                        fontFamily = PlusJakartaFontFamily,
                                        fontSize = 11.sp,
                                        color = colors.textSecondary,
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
    }
}

@Composable
private fun FavouriteAlbumsSection(
    albums: List<Album>,
    allSongs: List<Song>,
    colors: EditorialColors,
    animationLevel: ui.home.animations.HomeAnimationLevel,
    screenVisible: Boolean,
    onAlbumClick: (Album) -> Unit,
    onAlbumLongClick: (Song) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("favourite_albums_section")
    ) {
        EditorialSectionTitle(
            title = "Favourite Albums",
            subtitle = "Records you keep returning to",
            colors = colors
        )

        Spacer(modifier = Modifier.height(12.dp))

        val motionState = LocalEffectiveMotionState.current
        val listState = androidx.compose.foundation.lazy.rememberLazyListState()
        val snapFling = androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior(lazyListState = listState)

        LazyRow(
            state = listState,
            flingBehavior = snapFling,
            contentPadding = PaddingValues(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            itemsIndexed(albums, key = { _, alb -> "fav_alb_${alb.id}_${alb.title}" }) { index, album ->
                val repSong = remember(album.title, allSongs) {
                    allSongs.firstOrNull { it.album == album.title }
                }
                EditorialArtworkCard(
                    song = repSong,
                    title = album.title,
                    subtitle = "${album.artist} • ${album.songCount} songs",
                    colors = colors,
                    artworkSize = 148.dp,
                    cornerRadius = 24.dp,
                    showPlayBadge = true,
                    animationLevel = animationLevel,
                    screenVisible = screenVisible,
                    enableFloating = true,
                    floatingPhaseMs = index * 420,
                    floatingDurationMs = 4500 + (index % 3) * 400,
                    enableSlowZoom = true,
                    enableMorph = true,
                    enableParallax = true,
                    onClick = { onAlbumClick(album) },
                    onLongClick = repSong?.let { s -> { onAlbumLongClick(s) } },
                    modifier = Modifier
                        .width(156.dp)
                        .carouselItemDepthEffect(listState, index, motionState)
                )
            }
        }
    }
}

@Composable
private fun FavouriteArtistsSection(
    artists: List<Artist>,
    allSongs: List<Song>,
    colors: EditorialColors,
    animationLevel: ui.home.animations.HomeAnimationLevel,
    screenVisible: Boolean,
    onArtistClick: (Artist) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("favourite_artists_section")
    ) {
        EditorialSectionTitle(
            title = "Favourite Artists",
            subtitle = "Voices shaping your rotation",
            colors = colors
        )

        Spacer(modifier = Modifier.height(12.dp))

        val motionState = LocalEffectiveMotionState.current
        val listState = androidx.compose.foundation.lazy.rememberLazyListState()
        val snapFling = androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior(lazyListState = listState)

        LazyRow(
            state = listState,
            flingBehavior = snapFling,
            contentPadding = PaddingValues(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            itemsIndexed(artists, key = { _, art -> "fav_art_${art.name}" }) { index, artist ->
                val repSong = remember(artist.name, allSongs) {
                    allSongs.firstOrNull { it.artist == artist.name }
                }
                Column(
                    modifier = Modifier
                        .width(96.dp)
                        .carouselItemDepthEffect(listState, index, motionState)
                        .clip(RoundedCornerShape(16.dp))
                        .clickable { onArtistClick(artist) }
                        .padding(4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    CircularArtwork(
                        song = repSong,
                        fallbackLabel = artist.name,
                        accentColor = colors.accentPill,
                        shadowElevation = 6.dp,
                        borderWidth = 2.dp,
                        borderColor = colors.surface,
                        animationLevel = animationLevel,
                        screenVisible = screenVisible,
                        enableFloating = true,
                        floatingAmplitudeDp = 2.4.dp,
                        floatingDurationMs = 4200 + (index % 3) * 380,
                        floatingPhaseMs = index * 340,
                        enableSlowZoom = true,
                        decorativeRotation = false,
                        modifier = Modifier.size(84.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = artist.name,
                        fontFamily = OutfitFontFamily,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp,
                        color = colors.textPrimary,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(
                        text = "${artist.songCount} tracks",
                        fontFamily = PlusJakartaFontFamily,
                        fontSize = 11.sp,
                        color = colors.textSecondary,
                        textAlign = TextAlign.Center,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun RecentlyAddedSection(
    songs: List<Song>,
    currentPlayingSongId: Long?,
    colors: EditorialColors,
    animationLevel: ui.home.animations.HomeAnimationLevel,
    screenVisible: Boolean,
    onSongClick: (Song) -> Unit,
    onSongLongClick: (Song) -> Unit,
    onSeeAllClick: () -> Unit
) {
    val haptic = LocalHapticFeedback.current

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("recently_added_section")
    ) {
        EditorialSectionTitle(
            title = "Recently Added",
            subtitle = "Fresh arrivals in your local library",
            colors = colors,
            actionLabel = "See All",
            onActionClick = onSeeAllClick
        )

        Spacer(modifier = Modifier.height(12.dp))

        Surface(
            shape = RoundedCornerShape(26.dp),
            color = colors.surface,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .border(0.75.dp, colors.cardBorder, RoundedCornerShape(26.dp))
        ) {
            Column(modifier = Modifier.padding(vertical = 6.dp)) {
                songs.forEachIndexed { idx, song ->
                    val isCurrent = currentPlayingSongId == song.id
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .combinedClickable(
                                onClick = { onSongClick(song) },
                                onLongClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    onSongLongClick(song)
                                }
                            )
                            .padding(horizontal = 14.dp, vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularArtwork(
                            song = song,
                            isPlaying = isCurrent,
                            accentColor = colors.accentPill,
                            shadowElevation = 3.dp,
                            animationLevel = animationLevel,
                            screenVisible = screenVisible,
                            modifier = Modifier.size(46.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = song.title,
                                fontFamily = OutfitFontFamily,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 14.sp,
                                color = if (isCurrent) colors.accentStrong else colors.textPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "${song.artist} • ${song.durationFormatted}",
                                fontFamily = PlusJakartaFontFamily,
                                fontSize = 12.sp,
                                color = colors.textSecondary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        IconButton(
                            onClick = { onSongLongClick(song) },
                            modifier = Modifier.size(48.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.MoreVert,
                                contentDescription = "More actions for ${song.title}",
                                tint = colors.textMuted,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                    if (idx < songs.lastIndex) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp)
                                .height(0.5.dp)
                                .background(colors.cardBorder)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MostPlayedArchSection(
    songs: List<Song>,
    colors: EditorialColors,
    animationLevel: ui.home.animations.HomeAnimationLevel,
    screenVisible: Boolean,
    onSongClick: (Song) -> Unit,
    onSongLongClick: (Song) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("most_played_section")
    ) {
        EditorialSectionTitle(
            title = "Most Played",
            subtitle = "Your top spun tracks",
            colors = colors
        )

        Spacer(modifier = Modifier.height(12.dp))

        val motionState = LocalEffectiveMotionState.current
        val listState = androidx.compose.foundation.lazy.rememberLazyListState()
        val snapFling = androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior(lazyListState = listState)

        LazyRow(
            state = listState,
            flingBehavior = snapFling,
            contentPadding = PaddingValues(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            itemsIndexed(songs, key = { _, s -> "most_${s.id}" }) { index, song ->
                EditorialArtworkCard(
                    song = song,
                    title = song.title,
                    subtitle = if (song.playCount > 0) "${song.playCount} plays • ${song.artist}" else song.artist,
                    colors = colors,
                    artworkSize = 136.dp,
                    cornerRadius = 68.dp, // Arch / pill-rounded silhouette for layout variety
                    badgeText = "#${index + 1}",
                    animationLevel = animationLevel,
                    screenVisible = screenVisible,
                    enableFloating = true,
                    floatingPhaseMs = index * 390,
                    floatingDurationMs = 4600 + (index % 3) * 350,
                    enableSlowZoom = true,
                    enableParallax = true,
                    onClick = { onSongClick(song) },
                    onLongClick = { onSongLongClick(song) },
                    modifier = Modifier
                        .width(144.dp)
                        .carouselItemDepthEffect(listState, index, motionState)
                )
            }
        }
    }
}

@Composable
private fun EmptyLibraryEditorialCard(
    colors: EditorialColors,
    onScanLibrary: () -> Unit,
    onOpenLibrary: () -> Unit,
    onOpenOnlineMusic: () -> Unit = {}
) {
    Surface(
        shape = RoundedCornerShape(28.dp),
        color = colors.surface,
        shadowElevation = 8.dp,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 12.dp)
            .border(0.75.dp, colors.cardBorder, RoundedCornerShape(28.dp))
            .testTag("empty_library_card")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .background(colors.accentPill, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.LibraryMusic,
                    contentDescription = null,
                    tint = colors.onAccentPill,
                    modifier = Modifier.size(34.dp)
                )
            }

            Spacer(modifier = Modifier.height(18.dp))

            Text(
                text = "Your Editorial Stage Awaits",
                fontFamily = OutfitFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 22.sp,
                color = colors.textPrimary,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "No local audio files matched yet. Scan your device storage, check folder filters, or explore Online Music.",
                fontFamily = PlusJakartaFontFamily,
                fontSize = 13.sp,
                color = colors.textSecondary,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(20.dp))

            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Button(
                    onClick = onScanLibrary,
                    shape = RoundedCornerShape(24.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = colors.accentStrong,
                        contentColor = colors.onAccentStrong
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                ) {
                    Icon(Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Scan Music", fontFamily = PlusJakartaFontFamily, fontWeight = FontWeight.Bold)
                }

                Button(
                    onClick = onOpenLibrary,
                    shape = RoundedCornerShape(24.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = colors.accentPill,
                        contentColor = colors.onAccentPill
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                ) {
                    Text("Library", fontFamily = PlusJakartaFontFamily, fontWeight = FontWeight.Bold)
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Button(
                onClick = onOpenOnlineMusic,
                shape = RoundedCornerShape(24.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = colors.surfaceTinted,
                    contentColor = colors.textPrimary
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("empty_library_explore_online_button")
            ) {
                Icon(Icons.Rounded.Explore, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Explore Online Music", fontFamily = PlusJakartaFontFamily, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DiscoveryAndThemeSheet(
    allSongs: List<Song>,
    currentMode: HomeThemeMode,
    customAccentArgb: Long?,
    colors: EditorialColors,
    onDismiss: () -> Unit,
    onDiscoverRandom: () -> Unit,
    onSelectThemeMode: (HomeThemeMode) -> Unit,
    onSelectAccent: (Long?) -> Unit
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
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp)
                .testTag("discovery_theme_sheet")
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .background(colors.accentPill, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Rounded.Explore, contentDescription = null, tint = colors.onAccentPill)
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = "Discovery & Home Look",
                        fontFamily = OutfitFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp,
                        color = colors.textPrimary
                    )
                    Text(
                        text = "Shuffle fresh tracks or customize your editorial palette",
                        fontFamily = PlusJakartaFontFamily,
                        fontSize = 12.sp,
                        color = colors.textSecondary
                    )
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // Discovery instant mix button
            if (allSongs.isNotEmpty()) {
                Button(
                    onClick = onDiscoverRandom,
                    shape = RoundedCornerShape(22.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = colors.accentStrong,
                        contentColor = colors.onAccentStrong
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                ) {
                    Icon(Icons.Rounded.Shuffle, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Start Discovery Shuffle (${allSongs.size} tracks)",
                        fontFamily = PlusJakartaFontFamily,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(modifier = Modifier.height(20.dp))
            }

            Text(
                text = "${domain.branding.AppBranding.APP_NAME} AESTHETIC THEMES",
                fontFamily = PlusJakartaFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 11.sp,
                letterSpacing = 1.sp,
                color = colors.textMuted
            )

            Spacer(modifier = Modifier.height(10.dp))

            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(vertical = 2.dp)
            ) {
                items(HomeThemeMode.entries, key = { it.id }) { mode ->
                    val selected = currentMode == mode
                    Surface(
                        onClick = { onSelectThemeMode(mode) },
                        shape = RoundedCornerShape(18.dp),
                        color = if (selected) colors.accentPill.copy(alpha = 0.72f) else colors.surfaceTinted,
                        border = androidx.compose.foundation.BorderStroke(
                            width = if (selected) 1.5.dp else 0.75.dp,
                            color = if (selected) colors.accentStrong else colors.cardBorder
                        ),
                        modifier = Modifier.width(152.dp)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(horizontalArrangement = Arrangement.spacedBy((-6).dp)) {
                                    Box(
                                        modifier = Modifier
                                            .size(22.dp)
                                            .clip(CircleShape)
                                            .background(Color(mode.previewColorArgb))
                                            .border(1.dp, colors.cardBorder, CircleShape)
                                    )
                                    Box(
                                        modifier = Modifier
                                            .size(22.dp)
                                            .clip(CircleShape)
                                            .background(Color(mode.previewAccentArgb))
                                            .border(1.dp, colors.surface, CircleShape)
                                    )
                                }
                                if (selected) {
                                    Icon(
                                        imageVector = Icons.Rounded.Check,
                                        contentDescription = "Selected",
                                        tint = colors.accentStrong,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = mode.displayName,
                                fontFamily = OutfitFontFamily,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 13.sp,
                                color = colors.textPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = mode.subtitle,
                                fontFamily = PlusJakartaFontFamily,
                                fontSize = 11.sp,
                                color = colors.textSecondary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "CUSTOM ACCENT COLOUR",
                fontFamily = PlusJakartaFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 11.sp,
                letterSpacing = 1.sp,
                color = colors.textMuted
            )

            Spacer(modifier = Modifier.height(10.dp))

            LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                item {
                    val isDefault = customAccentArgb == null
                    Surface(
                        onClick = { onSelectAccent(null) },
                        shape = RoundedCornerShape(20.dp),
                        color = if (isDefault) colors.accentStrong else colors.surfaceTinted,
                        modifier = Modifier.height(42.dp)
                    ) {
                        Box(
                            modifier = Modifier.padding(horizontal = 14.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Default",
                                fontFamily = PlusJakartaFontFamily,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 12.sp,
                                color = if (isDefault) colors.onAccentStrong else colors.textPrimary
                            )
                        }
                    }
                }

                items(EditorialAccentSwatches.presets) { (name, argb) ->
                    val isPicked = customAccentArgb == argb
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(Color(argb))
                            .border(
                                width = if (isPicked) 3.dp else 1.dp,
                                color = if (isPicked) colors.textPrimary else colors.cardBorder,
                                shape = CircleShape
                            )
                            .clickable { onSelectAccent(argb) },
                        contentAlignment = Alignment.Center
                    ) {
                        if (isPicked) {
                            Icon(
                                imageVector = Icons.Rounded.Check,
                                contentDescription = name,
                                tint = if (Color(argb).luminance() > 0.45f) Color(0xFF15151C) else Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LocalLibraryStatsCard(
    stats: LocalLibraryStats,
    colors: EditorialColors,
    onOpenLibrary: () -> Unit
) {
    Surface(
        onClick = onOpenLibrary,
        shape = RoundedCornerShape(24.dp),
        color = colors.surface,
        border = BorderStroke(0.75.dp, colors.cardBorder),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .testTag("local_library_stats_card")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(colors.accentPill),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.LibraryMusic,
                            contentDescription = null,
                            tint = colors.onAccentPill,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "Local Music Library",
                            fontFamily = OutfitFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = colors.textPrimary
                        )
                        Text(
                            text = if (stats.losslessCount > 0) {
                                "${stats.formattedTotalDuration} • ${stats.losslessCount} lossless & hi-res"
                            } else {
                                "${stats.formattedTotalDuration} • Offline ready"
                            },
                            fontFamily = PlusJakartaFontFamily,
                            fontSize = 11.5.sp,
                            color = colors.textSecondary
                        )
                    }
                }
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = colors.surfaceTinted,
                    border = BorderStroke(0.5.dp, colors.cardBorder)
                ) {
                    Text(
                        text = "OFFLINE",
                        fontFamily = PlusJakartaFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 10.sp,
                        letterSpacing = 0.6.sp,
                        color = colors.accentStrong,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                LocalStatPill(
                    value = stats.trackCount.toString(),
                    label = "Tracks",
                    icon = Icons.Rounded.GraphicEq,
                    colors = colors,
                    modifier = Modifier.weight(1f)
                )
                LocalStatPill(
                    value = stats.albumCount.toString(),
                    label = "Albums",
                    icon = Icons.Rounded.Album,
                    colors = colors,
                    modifier = Modifier.weight(1f)
                )
                LocalStatPill(
                    value = stats.artistCount.toString(),
                    label = "Artists",
                    icon = Icons.Rounded.Person,
                    colors = colors,
                    modifier = Modifier.weight(1f)
                )
                LocalStatPill(
                    value = stats.favoritesCount.toString(),
                    label = "Liked",
                    icon = Icons.Rounded.Favorite,
                    colors = colors,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun LocalStatPill(
    value: String,
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    colors: EditorialColors,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = colors.surfaceTinted,
        border = BorderStroke(0.5.dp, colors.cardBorder),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = colors.accentStrong,
                modifier = Modifier.size(14.dp)
            )
            Spacer(modifier = Modifier.height(3.dp))
            Text(
                text = value,
                fontFamily = OutfitFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                color = colors.textPrimary,
                maxLines = 1
            )
            Text(
                text = label,
                fontFamily = PlusJakartaFontFamily,
                fontSize = 10.sp,
                color = colors.textSecondary,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun QuickToolsEditorialStrip(
    playlistCount: Int,
    colors: EditorialColors,
    onOpenEqualizer: () -> Unit,
    onOpenAudioStudio: () -> Unit,
    onOpenPlaylists: () -> Unit,
    onOpenQueue: () -> Unit
) {
    val entries = listOf(
        Triple("15-Band EQ", Icons.Rounded.Tune, onOpenEqualizer),
        Triple("Audio Studio", Icons.Rounded.SpatialAudio, onOpenAudioStudio),
        Triple("Playlists ($playlistCount)", Icons.Rounded.LibraryMusic, onOpenPlaylists),
        Triple("Up Next Queue", Icons.AutoMirrored.Rounded.QueueMusic, onOpenQueue)
    )

    LazyRow(
        contentPadding = PaddingValues(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.testTag("home_quick_tools_strip")
    ) {
        items(entries) { (label, icon, onClick) ->
            Surface(
                onClick = onClick,
                shape = RoundedCornerShape(22.dp),
                color = colors.surface,
                border = BorderStroke(0.75.dp, colors.cardBorder),
                modifier = Modifier
                    .height(48.dp)
                    .testTag("quick_tool_${label.lowercase().replace(Regex("[^a-z0-9]+"), "_")}")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(colors.accentPill),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = label,
                            tint = colors.onAccentPill,
                            modifier = Modifier.size(15.dp)
                        )
                    }
                    Text(
                        text = label,
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
