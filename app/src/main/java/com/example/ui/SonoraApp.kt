package com.example.ui

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BookmarkAdded
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Downloading
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.ui.equalizer.AdaptiveEqualizerScreen
import com.example.ui.screens.LibraryScreen
import com.example.ui.screens.NowPlayingScreen
import com.example.ui.screens.PlaylistsScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.theme.OutfitFontFamily
import com.example.ui.theme.PlusJakartaFontFamily
import com.example.ui.theme.SonoraTheme
import com.example.viewmodel.DownloadFlowUiState
import com.example.viewmodel.SonoraViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import ui.audiostudio.AudioEffectsStudioScreen
import ui.components.FloatingMiniPlayer
import ui.downloads.ChooseDownloadLocationDialog
import ui.downloads.DownloadManagerScreen
import ui.downloads.DownloadQualitySelectorSheet
import ui.home.FloatingBottomNavigation
import ui.home.HomeScreen
import ui.home.SearchScreen
import ui.lyrics.FullScreenLyricsScreen
import ui.motion.LocalEffectiveMotionState
import ui.motion.LocalSonoraMotionConfig
import ui.motion.SonoraTransitions
import ui.motion.horizontalSwipeNavigation
import ui.motion.rememberEffectiveMotionState
import domain.home.HomeMusicMode
import domain.video.ChannelDetailUtils
import domain.video.OnlineVideoChannel
import domain.video.OnlineVideoItem
import domain.video.VideoChannelUiState
import ui.online.OnlineMusicScreen
import ui.queue.QueueScreen
import ui.settings.LibraryFolderManagementScreen
import ui.theme.rememberEditorialColors
import ui.video.ChannelDetailScreen
import ui.video.ChannelPlaylistDetailSheet
import ui.video.FloatingVideoMiniPlayer
import ui.video.VideoAccountActionDialog
import ui.video.VideoDownloadQualitySheet
import ui.video.VideoOptionsBottomSheet
import ui.video.VideoPlayerScreen
import ui.video.VideoPlaylistPickerDialog
import ui.video.openOfficialExternalUrl
import ui.video.shareVideoLink

@Composable
fun SonoraApp(
    viewModel: SonoraViewModel,
    isIntroOverlayActive: Boolean = false,
    onVideoViewportBoundsChanged: ((android.graphics.Rect?) -> Unit)? = null
) {
    val themeMode by viewModel.themeMode.collectAsState()
    val dynamicPalette by viewModel.dynamicPalette.collectAsState()
    val homeThemeMode by viewModel.homeThemeMode.collectAsState()
    val customAccentArgb by viewModel.homeCustomAccentArgb.collectAsState()
    val reduceAnimations by viewModel.reduceAnimations.collectAsState()
    val motionConfig by viewModel.motionConfig.collectAsState()
    val motionState = rememberEffectiveMotionState(motionConfig)
    val effectiveReduceAnimations = reduceAnimations || !motionState.animationsEnabled
    val currentSong by viewModel.currentSong.collectAsState()
    val playbackInfo by viewModel.playbackInfo.collectAsState()
    val trackDirection by viewModel.trackTransitionDirection.collectAsState()
    val profileOnboardingState by viewModel.profileOnboardingState.collectAsState()
    val musicTasteOnboardingState by viewModel.musicTasteOnboardingState.collectAsState()
    val musicTasteProfile by viewModel.musicTasteProfile.collectAsState()
    val musicTasteArtistState by viewModel.musicTasteArtistState.collectAsState()
    val musicTasteDiscoveryState by viewModel.musicTasteDiscoveryState.collectAsState()
    val musicTastePreviewState by viewModel.musicTastePreviewState.collectAsState()
    val systemDark = androidx.compose.foundation.isSystemInDarkTheme()

    LaunchedEffect(systemDark) {
        viewModel.updateSystemDarkTheme(systemDark)
    }

    var currentTab by remember { mutableIntStateOf(0) } // 0 = Home, 1 = Search, 2 = Library, 3 = Online
    var launchOnlineSearch by remember { mutableStateOf(false) }
    var showSettingsScreen by remember { mutableStateOf(false) }
    var isNowPlayingExpanded by remember { mutableStateOf(false) }
    var showEqualizerScreen by remember { mutableStateOf(false) }
    var showAudioStudioScreen by remember { mutableStateOf(false) }
    var showPlaylistsScreen by remember { mutableStateOf(false) }
    var showQueueScreen by remember { mutableStateOf(false) }
    var showFolderManagementScreen by remember { mutableStateOf(false) }
    var showDynamicThemeScreen by remember { mutableStateOf(false) }
    var showMusicPlayerHubScreen by remember { mutableStateOf(false) }
    var showDownloadManagerScreen by remember { mutableStateOf(false) }
    var showLyricsScreen by remember { mutableStateOf(false) }
    var showMusicAnalyticsScreen by remember { mutableStateOf(false) }
    var showMusicTasteScreen by remember { mutableStateOf(false) }
    var musicTasteEditMode by remember { mutableStateOf<domain.onboarding.MusicTasteEditLaunchMode?>(null) }
    var openLibraryDownloadsTrigger by remember { mutableIntStateOf(0) }

    val downloadFlowState by viewModel.downloadFlowState.collectAsState()
    val downloadFeedbackMessage by viewModel.downloadFeedbackMessage.collectAsState()
    val libraryFeedbackMessage by viewModel.libraryFeedbackMessage.collectAsState()
    val sessionRestoreNotice by viewModel.sessionRestoreNotice.collectAsState()
    val homeMusicMode by viewModel.homeMusicMode.collectAsState()
    val librarySectionMode by viewModel.librarySectionMode.collectAsState()
    val pendingVideoDownload by viewModel.pendingVideoDownload.collectAsState()
    val downloadLocationConfig by viewModel.downloadLocationConfig.collectAsState()
    val appUpdateDialogState by viewModel.appUpdateDialogState.collectAsState()
    val postUpdateReminder by viewModel.postUpdateReminder.collectAsState()
    val appScope = rememberCoroutineScope()

    val videoPlayerState by viewModel.videoController.playerState.collectAsStateWithLifecycle()
    val videoChannelState by viewModel.videoController.channelState.collectAsStateWithLifecycle()
    val videoChannelPlaylistState by viewModel.videoController.channelPlaylistState.collectAsStateWithLifecycle()
    val videoAuthState by viewModel.videoController.authState.collectAsStateWithLifecycle()
    val videoAccountActionPrompt by viewModel.videoController.pendingAccountActionPrompt.collectAsStateWithLifecycle()
    val videoFeedbackMessage by viewModel.videoController.feedbackMessage.collectAsStateWithLifecycle()
    val watchLaterVideos by viewModel.videoController.watchLaterVideos.collectAsStateWithLifecycle()
    val savedVideos by viewModel.videoController.savedVideos.collectAsStateWithLifecycle()
    val videoHistory by viewModel.videoController.videoHistory.collectAsStateWithLifecycle()
    val videoPlaylists by viewModel.videoController.videoPlaylists.collectAsStateWithLifecycle()
    val videoRecommendationFeedback by viewModel.videoController.recommendationFeedback.collectAsStateWithLifecycle()
    val watchLaterVideoIds = remember(watchLaterVideos) { watchLaterVideos.map { it.videoId }.toSet() }
    val savedVideoIds = remember(savedVideos) { savedVideos.map { it.videoId }.toSet() }
    val watchProgressByVideoId = remember(videoHistory) {
        videoHistory.mapNotNull { item ->
            ChannelDetailUtils.computeWatchProgress(item.lastPositionMs, item.durationSeconds)?.let {
                item.videoId to it
            }
        }.toMap()
    }
    var videoOptionsTarget by remember { mutableStateOf<OnlineVideoItem?>(null) }
    var videoPlaylistPickerTarget by remember { mutableStateOf<OnlineVideoItem?>(null) }

    LaunchedEffect(downloadFeedbackMessage) {
        if (downloadFeedbackMessage != null) {
            delay(4200L)
            viewModel.clearDownloadFeedback()
        }
    }

    LaunchedEffect(libraryFeedbackMessage) {
        if (libraryFeedbackMessage != null) {
            delay(3600L)
            viewModel.clearLibraryFeedback()
        }
    }

    LaunchedEffect(videoFeedbackMessage) {
        if (videoFeedbackMessage != null) {
            delay(3600L)
            viewModel.videoController.clearFeedback()
        }
    }

    val context = LocalContext.current

    val downloadFolderLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        viewModel.onDownloadFolderSelected(uri)
    }

    val lrcImportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            viewModel.importLrcFile(uri)
        }
    }

    val permissionsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        val audioPermissionGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            results[Manifest.permission.READ_MEDIA_AUDIO] == true
        } else {
            results[Manifest.permission.READ_EXTERNAL_STORAGE] == true
        }
        if (audioPermissionGranted) {
            viewModel.loadLocalSongs()
        }
    }

    val apkInstallerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        viewModel.onPackageInstallerReturnedWithoutUpgrade(
            wasCancelled = result.resultCode == Activity.RESULT_CANCELED
        )
    }

    val unknownSourcesSettingsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        appScope.launch {
            when (val prep = viewModel.onReturnedFromUnknownAppSourcesSettings()) {
                is data.update.InstallPreparationResult.LaunchInstaller -> {
                    runCatching { apkInstallerLauncher.launch(prep.installIntent) }
                        .onFailure { viewModel.onPackageInstallerReturnedWithoutUpgrade(wasCancelled = false) }
                }
                else -> Unit
            }
        }
    }

    LaunchedEffect(profileOnboardingState, musicTasteOnboardingState, isIntroOverlayActive) {
        if (isIntroOverlayActive ||
            profileOnboardingState != data.preferences.ProfileOnboardingState.COMPLETED ||
            musicTasteOnboardingState != domain.onboarding.MusicTasteOnboardingState.COMPLETED
        ) {
            return@LaunchedEffect
        }
        viewModel.triggerAppUpdateLaunchFlow()
        val neededPermissions = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                neededPermissions.add(Manifest.permission.READ_MEDIA_AUDIO)
            }
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                neededPermissions.add(Manifest.permission.POST_NOTIFICATIONS)
            }
        } else {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                neededPermissions.add(Manifest.permission.READ_EXTERNAL_STORAGE)
            }
        }
        if (neededPermissions.isNotEmpty()) {
            permissionsLauncher.launch(neededPermissions.toTypedArray())
        }
    }

    val editorialColors = rememberEditorialColors(
        mode = homeThemeMode,
        customAccentArgb = customAccentArgb,
        dynamicPalette = dynamicPalette,
        reduceAnimations = effectiveReduceAnimations,
        animatedColorsEnabled = motionState.animatedAlbumColorsEnabled,
        colorDurationMs = motionState.durationMillis(600)
    )

    CompositionLocalProvider(
        LocalSonoraMotionConfig provides motionConfig,
        LocalEffectiveMotionState provides motionState
    ) {
        SonoraTheme(
            themeMode = themeMode,
            dynamicPalette = dynamicPalette,
            editorialColors = editorialColors,
            reduceAnimations = effectiveReduceAnimations
        ) {
            // Dedicated Android System PiP mode: render ONLY the active Media3 video surface
            // edge-to-edge, completely excluding Home, Bottom Navigation, Music Mini-Player,
            // recommendations, banners, sheets, and dialogs from the PiP window.
            if (domain.video.SystemPipPolicy.shouldRenderVideoOnlyPipUi(videoPlayerState)) {
                ui.video.SystemPipVideoScreen(
                    playerState = videoPlayerState,
                    colors = editorialColors,
                    onAttachSurfaceView = { viewModel.videoController.attachVideoSurfaceView(it) },
                    onDetachSurfaceView = { viewModel.videoController.detachVideoSurfaceView(it) },
                    onAttachTextureView = { viewModel.videoController.attachVideoSurface(it) },
                    onDetachTextureView = { viewModel.videoController.detachVideoSurface(it) }
                )
                return@SonoraTheme
            }

            when (profileOnboardingState) {
                data.preferences.ProfileOnboardingState.LOADING -> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(androidx.compose.ui.graphics.Color.Black)
                            .testTag("onboarding_loading_gate")
                    )
                    return@SonoraTheme
                }
                data.preferences.ProfileOnboardingState.REQUIRED -> {
                    ui.onboarding.FirstLaunchProfileOnboardingScreen(
                        colors = editorialColors,
                        reduceAnimations = effectiveReduceAnimations,
                        onCompleteProfile = { name, photoUri ->
                            viewModel.completeFirstLaunchProfile(name, photoUri)
                        }
                    )
                    return@SonoraTheme
                }
                data.preferences.ProfileOnboardingState.COMPLETED -> Unit
            }

            when (musicTasteOnboardingState) {
                domain.onboarding.MusicTasteOnboardingState.LOADING -> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(androidx.compose.ui.graphics.Color.Black)
                            .testTag("music_taste_loading_gate")
                    )
                    return@SonoraTheme
                }
                domain.onboarding.MusicTasteOnboardingState.REQUIRED -> {
                    ui.onboarding.MusicTasteOnboardingScreen(
                        colors = editorialColors,
                        reduceAnimations = effectiveReduceAnimations,
                        launchMode = domain.onboarding.MusicTasteEditLaunchMode.FULL_ONBOARDING,
                        profile = musicTasteProfile,
                        artistState = musicTasteArtistState,
                        discoveryState = musicTasteDiscoveryState,
                        previewState = musicTastePreviewState,
                        onLoadInitialArtists = { viewModel.loadInitialTasteArtists(it) },
                        onSearchArtists = { viewModel.searchTasteArtists(it) },
                        onToggleArtist = { viewModel.toggleTasteArtist(it) },
                        onClearArtists = { viewModel.clearTasteArtists() },
                        onToggleGenre = { viewModel.toggleTasteGenre(it) },
                        onClearGenres = { viewModel.clearTasteGenres() },
                        onLoadDiscoverySession = { viewModel.loadTasteDiscoverySession(it) },
                        onDecideTrack = { track, decision -> viewModel.decideTasteDiscoveryTrack(track, decision) },
                        onTogglePreview = { viewModel.toggleTasteDiscoveryPreview(it) },
                        onStopPreview = { viewModel.stopTasteDiscoveryPreview() },
                        onPreviousTrack = { viewModel.stepBackTasteDiscoveryTrack() },
                        onNextTrack = { viewModel.stepNextTasteDiscoveryTrack() },
                        onClearDiscoverySelections = { viewModel.clearTasteDiscoverySessionSelections() },
                        onSkipOnboarding = { viewModel.skipMusicTasteOnboarding() },
                        onCompleteOnboarding = { viewModel.completeMusicTasteOnboarding() },
                        onDismissEditMode = { viewModel.completeMusicTasteOnboarding() }
                    )
                    return@SonoraTheme
                }
                domain.onboarding.MusicTasteOnboardingState.COMPLETED -> Unit
            }

            val pushEnter = SonoraTransitions.pushEnter(motionState)
            val pushExit = SonoraTransitions.pushExit(motionState)
            val modalEnter = SonoraTransitions.modalEnter(motionState)
            val modalExit = SonoraTransitions.modalExit(motionState)

            val isVideoPlayerExpanded = videoPlayerState.hasActiveVideo && videoPlayerState.isPlayerExpanded
            val isChannelDetailOpen = videoChannelState !is VideoChannelUiState.Idle

            // Ordered back-stack dismissal matching exact visual Z-order (topmost overlay first)
            BackHandler(enabled = isVideoPlayerExpanded) {
                if (videoPlayerState.isFullScreenLandscape) {
                    viewModel.videoController.setFullScreenLandscape(false)
                } else {
                    viewModel.videoController.minimizeVideoPlayer()
                }
            }
            BackHandler(enabled = isChannelDetailOpen && !isVideoPlayerExpanded) {
                viewModel.videoController.dismissChannelProfile()
            }
            val isMusicTasteOverlayOpen = showMusicTasteScreen || musicTasteEditMode != null
            BackHandler(enabled = showMusicTasteScreen && musicTasteEditMode == null && !isChannelDetailOpen && !isVideoPlayerExpanded) {
                showMusicTasteScreen = false
            }
            BackHandler(enabled = showMusicAnalyticsScreen && !isMusicTasteOverlayOpen && !isChannelDetailOpen && !isVideoPlayerExpanded) {
                showMusicAnalyticsScreen = false
            }
            BackHandler(enabled = showLyricsScreen && !showMusicAnalyticsScreen && !isMusicTasteOverlayOpen && !isChannelDetailOpen && !isVideoPlayerExpanded) {
                showLyricsScreen = false
            }
            BackHandler(enabled = showDownloadManagerScreen && !showLyricsScreen && !showMusicAnalyticsScreen && !isMusicTasteOverlayOpen && !isVideoPlayerExpanded) {
                showDownloadManagerScreen = false
            }
            BackHandler(enabled = showMusicPlayerHubScreen && !showDownloadManagerScreen && !showLyricsScreen && !showMusicAnalyticsScreen && !isMusicTasteOverlayOpen && !isVideoPlayerExpanded) {
                showMusicPlayerHubScreen = false
            }
            BackHandler(enabled = showDynamicThemeScreen && !showMusicPlayerHubScreen && !showDownloadManagerScreen && !showLyricsScreen && !showMusicAnalyticsScreen && !isMusicTasteOverlayOpen && !isVideoPlayerExpanded) {
                showDynamicThemeScreen = false
            }
            BackHandler(enabled = showQueueScreen && !showDynamicThemeScreen && !showMusicPlayerHubScreen && !showDownloadManagerScreen && !showLyricsScreen && !showMusicAnalyticsScreen && !isMusicTasteOverlayOpen && !isVideoPlayerExpanded) {
                showQueueScreen = false
            }
            BackHandler(enabled = showFolderManagementScreen && !showQueueScreen && !showDynamicThemeScreen && !showMusicPlayerHubScreen && !showDownloadManagerScreen && !showLyricsScreen && !showMusicAnalyticsScreen && !isMusicTasteOverlayOpen && !isVideoPlayerExpanded) {
                showFolderManagementScreen = false
            }
            BackHandler(enabled = showAudioStudioScreen && !showFolderManagementScreen && !showQueueScreen && !showDynamicThemeScreen && !showMusicPlayerHubScreen && !showDownloadManagerScreen && !showLyricsScreen && !showMusicAnalyticsScreen && !isMusicTasteOverlayOpen && !isVideoPlayerExpanded) {
                showAudioStudioScreen = false
            }
            BackHandler(enabled = showEqualizerScreen && !showAudioStudioScreen && !showFolderManagementScreen && !showQueueScreen && !showDynamicThemeScreen && !showMusicPlayerHubScreen && !showDownloadManagerScreen && !showLyricsScreen && !showMusicAnalyticsScreen && !isMusicTasteOverlayOpen && !isVideoPlayerExpanded) {
                showEqualizerScreen = false
            }
            BackHandler(enabled = isNowPlayingExpanded && !showEqualizerScreen && !showAudioStudioScreen && !showFolderManagementScreen && !showQueueScreen && !showDynamicThemeScreen && !showMusicPlayerHubScreen && !showDownloadManagerScreen && !showLyricsScreen && !showMusicAnalyticsScreen && !isMusicTasteOverlayOpen && !isVideoPlayerExpanded) {
                isNowPlayingExpanded = false
            }
            BackHandler(enabled = showPlaylistsScreen && !isNowPlayingExpanded && !showEqualizerScreen && !showAudioStudioScreen && !showFolderManagementScreen && !showQueueScreen && !showDynamicThemeScreen && !showMusicPlayerHubScreen && !showDownloadManagerScreen && !showLyricsScreen && !showMusicAnalyticsScreen && !isMusicTasteOverlayOpen && !isVideoPlayerExpanded) {
                showPlaylistsScreen = false
            }
            BackHandler(enabled = showSettingsScreen && !showPlaylistsScreen && !isNowPlayingExpanded && !showEqualizerScreen && !showAudioStudioScreen && !showFolderManagementScreen && !showQueueScreen && !showDynamicThemeScreen && !showMusicPlayerHubScreen && !showDownloadManagerScreen && !showLyricsScreen && !showMusicAnalyticsScreen && !isMusicTasteOverlayOpen && !isVideoPlayerExpanded) {
                showSettingsScreen = false
            }
            BackHandler(enabled = currentTab != 0 && !showSettingsScreen && !showPlaylistsScreen && !isNowPlayingExpanded && !showEqualizerScreen && !showAudioStudioScreen && !showFolderManagementScreen && !showQueueScreen && !showDynamicThemeScreen && !showMusicPlayerHubScreen && !showDownloadManagerScreen && !showLyricsScreen && !showMusicAnalyticsScreen && !isMusicTasteOverlayOpen && !isVideoPlayerExpanded) {
                currentTab = 0
            }

            val showBottomChrome = !isVideoPlayerExpanded &&
                !isNowPlayingExpanded &&
                !showLyricsScreen &&
                !showMusicAnalyticsScreen &&
                !showMusicTasteScreen &&
                musicTasteEditMode == null &&
                !showEqualizerScreen &&
                !showAudioStudioScreen &&
                !showPlaylistsScreen &&
                !showQueueScreen &&
                !showFolderManagementScreen &&
                !showDynamicThemeScreen &&
                !showMusicPlayerHubScreen &&
                !showDownloadManagerScreen &&
                !showSettingsScreen

            Box(modifier = Modifier.fillMaxSize()) {
                // 1. Primary Destinations (Home <-> Search <-> Library <-> Online)
                AnimatedContent(
                    targetState = currentTab,
                    transitionSpec = {
                        SonoraTransitions.tabTransition(
                            forward = targetState >= initialState,
                            motion = motionState
                        )
                    },
                    label = "main_tab_transition",
                    modifier = Modifier
                        .fillMaxSize()
                        .horizontalSwipeNavigation(
                            enabled = showBottomChrome && currentTab != 0,
                            motion = motionState,
                            onSwipeLeft = {
                                if (currentTab < 3) currentTab += 1
                            },
                            onSwipeRight = {
                                if (currentTab > 0) currentTab -= 1
                            }
                        )
                ) { tab ->
                    when (tab) {
                        0 -> HomeScreen(
                            viewModel = viewModel,
                            colors = editorialColors,
                            screenVisible = !isIntroOverlayActive && showBottomChrome && currentTab == 0,
                            onOpenSettings = { showSettingsScreen = true },
                            onNavigateToSearch = { currentTab = 1 },
                            onOpenMusicAnalytics = { showMusicAnalyticsScreen = true },
                            onOpenMusicTaste = { showMusicTasteScreen = true },
                            onNavigateToLibrary = { currentTab = 2 },
                            onOpenOnlineMusic = { currentTab = 3 },
                            onOpenNowPlaying = {
                                isNowPlayingExpanded = true
                            },
                            onOpenEqualizer = { showEqualizerScreen = true },
                            onOpenAudioStudio = { showAudioStudioScreen = true },
                            onOpenPlaylists = { showPlaylistsScreen = true },
                            onOpenQueue = { showQueueScreen = true },
                            onOpenDynamicColors = { showDynamicThemeScreen = true },
                            onOpenPlayerStyles = { showMusicPlayerHubScreen = true }
                        )
                        1 -> SearchScreen(
                            viewModel = viewModel,
                            colors = editorialColors,
                            onOpenSettings = { showSettingsScreen = true },
                            onSearchOnline = { queryText ->
                                viewModel.updateOnlineSearchQuery(queryText)
                                if (queryText.isNotBlank()) {
                                    viewModel.executeOnlineSearch(queryText)
                                }
                                launchOnlineSearch = true
                                currentTab = 3
                            }
                        )
                        2 -> LibraryScreen(
                            viewModel = viewModel,
                            onOpenFolderManagement = { showFolderManagementScreen = true },
                            onOpenPlaylists = { showPlaylistsScreen = true },
                            onOpenDownloadManager = { showDownloadManagerScreen = true },
                            onOpenOnlineMusic = { currentTab = 3 },
                            onOpenOnlineVideo = {
                                viewModel.setHomeMusicMode(HomeMusicMode.ONLINE_VIDEO)
                                currentTab = 0
                            },
                            openDownloadsTrigger = openLibraryDownloadsTrigger
                        )
                        3 -> OnlineMusicScreen(
                            viewModel = viewModel,
                            colors = editorialColors,
                            initialSearchOpen = launchOnlineSearch,
                            onInitialSearchConsumed = { launchOnlineSearch = false },
                            onOpenSettings = { showSettingsScreen = true },
                            onOpenNowPlaying = {
                                isNowPlayingExpanded = true
                            }
                        )
                    }
                }

                // Shared bottom mini-player visibility (single source of truth)
                val showVideoMiniPlayer = videoPlayerState.hasActiveVideo && !isVideoPlayerExpanded
                val isInOnlineVideoExperience = domain.player.MusicMiniPlayerVisibilityPolicy.isInOnlineVideoExperience(
                    currentTab = currentTab,
                    homeMusicMode = homeMusicMode,
                    librarySectionMode = librarySectionMode,
                    isVideoPlayerExpanded = isVideoPlayerExpanded,
                    isVideoMiniPlayerVisible = showVideoMiniPlayer,
                    isChannelDetailOpen = isChannelDetailOpen
                )
                val showMusicMiniPlayer = domain.player.MusicMiniPlayerVisibilityPolicy.shouldShowMusicMiniPlayer(
                    hasCurrentSong = currentSong != null,
                    isInOnlineVideoExperience = isInOnlineVideoExperience
                )

                // 2. Persistent Floating Audio Mini-Player + Floating Pill Bottom Navigation
                // (In-app floating video PiP is mounted in a dedicated safe-area overlay layer
                // so playback and its live TextureView persist across tab & channel navigation.)
                if (showBottomChrome) {
                    Column(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .navigationBarsPadding(),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        if (!showVideoMiniPlayer) {
                            AnimatedVisibility(
                                visible = showMusicMiniPlayer && currentSong != null,
                                enter = if (motionState.animationsEnabled) {
                                    fadeIn(tween(durationMillis = 200, easing = FastOutSlowInEasing)) +
                                        slideInVertically(
                                            animationSpec = spring(
                                                dampingRatio = Spring.DampingRatioNoBouncy,
                                                stiffness = Spring.StiffnessMediumLow
                                            ),
                                            initialOffsetY = { it / 2 }
                                        ) +
                                        scaleIn(
                                            initialScale = 0.92f,
                                            animationSpec = tween(durationMillis = 200, easing = FastOutSlowInEasing)
                                        )
                                } else {
                                    fadeIn(snap())
                                },
                                exit = if (motionState.animationsEnabled) {
                                    fadeOut(tween(durationMillis = 170, easing = FastOutSlowInEasing)) +
                                        slideOutVertically(
                                            animationSpec = tween(durationMillis = 180, easing = FastOutSlowInEasing),
                                            targetOffsetY = { it / 2 }
                                        ) +
                                        scaleOut(
                                            targetScale = 0.86f,
                                            animationSpec = tween(durationMillis = 170, easing = FastOutSlowInEasing)
                                        )
                                } else {
                                    fadeOut(snap())
                                }
                            ) {
                                val miniPlayerStyle by viewModel.miniPlayerStyle.collectAsState()
                                val miniQueue by viewModel.queue.collectAsState()
                                val miniQueueIndex by viewModel.queueIndex.collectAsState()
                                val miniFreqs by viewModel.visualizerFrequencies.collectAsState()
                                val weatherAmbientSettings by viewModel.weatherAmbientSettings.collectAsState()
                                val weatherFetchState by viewModel.weatherFetchState.collectAsState()
                                val miniWeatherPlan = remember(
                                    weatherAmbientSettings,
                                    weatherFetchState.latestSnapshot,
                                    miniPlayerStyle,
                                    isInOnlineVideoExperience
                                ) {
                                    if (isInOnlineVideoExperience || !weatherAmbientSettings.enableMiniPlayerEffects) {
                                        domain.weather.WeatherAtmospherePlan.INACTIVE
                                    } else {
                                        domain.weather.WeatherAtmosphereEngine.resolvePlan(
                                            settings = weatherAmbientSettings,
                                            snapshot = weatherFetchState.latestSnapshot,
                                            surfaceRole = domain.weather.WeatherSurfaceRole.MiniPlayer(miniPlayerStyle)
                                        )
                                    }
                                }
                                FloatingMiniPlayer(
                                    song = currentSong,
                                    playbackInfo = playbackInfo,
                                    colors = editorialColors,
                                    onTogglePlayPause = { viewModel.togglePlayPause() },
                                    onPlayNext = { viewModel.playNext() },
                                    onPlayPrevious = { viewModel.playPrevious() },
                                    onSeekTo = { viewModel.seekTo(it) },
                                    onClick = {
                                        isNowPlayingExpanded = true
                                    },
                                    onOpenLyrics = { showLyricsScreen = true },
                                    miniPlayerStyle = miniPlayerStyle,
                                    trackDirection = trackDirection,
                                    frequencies = miniFreqs,
                                    queue = miniQueue,
                                    queueIndex = miniQueueIndex,
                                    weatherPlan = miniWeatherPlan,
                                    weatherSettings = weatherAmbientSettings
                                )
                            }
                        }

                        FloatingBottomNavigation(
                            selectedIndex = currentTab,
                            colors = editorialColors,
                            onSelectTab = { currentTab = it }
                        )
                    }
                }

                // 3. Settings Overlay (Forward/Back Horizontal Push)
                AnimatedVisibility(
                    visible = showSettingsScreen,
                    enter = pushEnter,
                    exit = pushExit
                ) {
                    SettingsScreen(
                        viewModel = viewModel,
                        onOpenNowPlaying = { isNowPlayingExpanded = true },
                        onOpenEqualizer = { showEqualizerScreen = true },
                        onOpenAudioStudio = { showAudioStudioScreen = true },
                        onOpenFolderManagement = { showFolderManagementScreen = true },
                        onOpenDownloadManager = { showDownloadManagerScreen = true },
                        onOpenOnlineMusic = {
                            showSettingsScreen = false
                            currentTab = 3
                        },
                        onOpenOnlineLibrary = {
                            viewModel.setLibrarySectionMode(domain.library.LibrarySectionMode.ONLINE)
                            showSettingsScreen = false
                            currentTab = 2
                        },
                        onBack = { showSettingsScreen = false }
                    )
                }

                // 4. Playlists Management Overlay (Forward/Back Push)
                AnimatedVisibility(
                    visible = showPlaylistsScreen,
                    enter = pushEnter,
                    exit = pushExit
                ) {
                    PlaylistsScreen(
                        viewModel = viewModel,
                        onBack = { showPlaylistsScreen = false }
                    )
                }

                // 5. Full Screen Now Playing Player Coordinator (Modal Up/Down Sheet)
                AnimatedVisibility(
                    visible = isNowPlayingExpanded,
                    enter = modalEnter,
                    exit = modalExit
                ) {
                    NowPlayingScreen(
                        viewModel = viewModel,
                        onClose = { isNowPlayingExpanded = false },
                        onOpenEqualizer = { showEqualizerScreen = true },
                        onOpenAudioStudio = { showAudioStudioScreen = true },
                        onOpenQueueScreen = { showQueueScreen = true },
                        onOpenLyricsScreen = { showLyricsScreen = true }
                    )
                }

                // 6. Adaptive 15-Band Equalizer Overlay
                AnimatedVisibility(
                    visible = showEqualizerScreen,
                    enter = pushEnter,
                    exit = pushExit
                ) {
                    AdaptiveEqualizerScreen(
                        viewModel = viewModel,
                        onBack = { showEqualizerScreen = false }
                    )
                }

                // 7. Audio Effects Studio (8D Orbit, 360° Spatial, Bass, Reverb) Overlay
                AnimatedVisibility(
                    visible = showAudioStudioScreen,
                    enter = pushEnter,
                    exit = pushExit
                ) {
                    AudioEffectsStudioScreen(
                        viewModel = viewModel,
                        onOpenEqualizer = {
                            showAudioStudioScreen = false
                            showEqualizerScreen = true
                        },
                        onBack = { showAudioStudioScreen = false }
                    )
                }

                // 8. Library Blacklist & Whitelist Folder Management Overlay
                AnimatedVisibility(
                    visible = showFolderManagementScreen,
                    enter = pushEnter,
                    exit = pushExit
                ) {
                    LibraryFolderManagementScreen(
                        viewModel = viewModel,
                        onBack = { showFolderManagementScreen = false }
                    )
                }

                // 9. Playing Queue & Recently Played Screen Overlay (Modal Up/Down)
                AnimatedVisibility(
                    visible = showQueueScreen,
                    enter = modalEnter,
                    exit = modalExit
                ) {
                    QueueScreen(
                        viewModel = viewModel,
                        onBack = { showQueueScreen = false },
                        onOpenNowPlaying = {
                            showQueueScreen = false
                            isNowPlayingExpanded = true
                        }
                    )
                }

                // 10. Dynamic Colours Personalization Overlay
                AnimatedVisibility(
                    visible = showDynamicThemeScreen,
                    enter = pushEnter,
                    exit = pushExit
                ) {
                    ui.settings.DynamicThemeSettingsScreen(
                        viewModel = viewModel,
                        onBack = { showDynamicThemeScreen = false },
                        onOpenPlayer = {
                            showDynamicThemeScreen = false
                            isNowPlayingExpanded = true
                        }
                    )
                }

                // 11. Music Player Customization Hub Overlay
                AnimatedVisibility(
                    visible = showMusicPlayerHubScreen,
                    enter = pushEnter,
                    exit = pushExit
                ) {
                    ui.player.PlayerAppearanceHubScreen(
                        viewModel = viewModel,
                        onBack = { showMusicPlayerHubScreen = false },
                        onOpenPlayer = {
                            showMusicPlayerHubScreen = false
                            isNowPlayingExpanded = true
                        }
                    )
                }

                // 12. Download Manager Overlay
                AnimatedVisibility(
                    visible = showDownloadManagerScreen,
                    enter = pushEnter,
                    exit = pushExit
                ) {
                    DownloadManagerScreen(
                        viewModel = viewModel,
                        onBack = { showDownloadManagerScreen = false },
                        onOpenLibraryDownloads = {
                            showDownloadManagerScreen = false
                            showSettingsScreen = false
                            isNowPlayingExpanded = false
                            openLibraryDownloadsTrigger++
                            currentTab = 2
                        }
                    )
                }

                // 12b. Full-Screen Synchronized Lyrics Experience Overlay
                AnimatedVisibility(
                    visible = showLyricsScreen,
                    enter = modalEnter,
                    exit = modalExit
                ) {
                    FullScreenLyricsScreen(
                        viewModel = viewModel,
                        onBack = { showLyricsScreen = false },
                        onRequestImportLrc = {
                            lrcImportLauncher.launch(arrayOf("text/*", "application/octet-stream", "*/*"))
                        }
                    )
                }

                // 12b-analytics. Profile Music Analytics Dashboard Overlay
                AnimatedVisibility(
                    visible = showMusicAnalyticsScreen,
                    enter = pushEnter,
                    exit = pushExit
                ) {
                    ui.profile.MusicAnalyticsScreen(
                        viewModel = viewModel,
                        onBack = { showMusicAnalyticsScreen = false }
                    )
                }

                // 12b-taste. Permanent Profile -> Music Taste Preferences Overlay
                AnimatedVisibility(
                    visible = showMusicTasteScreen,
                    enter = pushEnter,
                    exit = pushExit
                ) {
                    ui.profile.MusicTasteScreen(
                        profile = musicTasteProfile,
                        colors = editorialColors,
                        onBack = { showMusicTasteScreen = false },
                        onEditArtists = { musicTasteEditMode = domain.onboarding.MusicTasteEditLaunchMode.EDIT_ARTISTS },
                        onEditGenres = { musicTasteEditMode = domain.onboarding.MusicTasteEditLaunchMode.EDIT_GENRES },
                        onRestartSongDiscovery = {
                            musicTasteEditMode = domain.onboarding.MusicTasteEditLaunchMode.RESTART_DISCOVERY
                        },
                        onRemoveArtist = { viewModel.toggleTasteArtist(it) },
                        onRemoveGenre = { viewModel.toggleTasteGenre(it) },
                        onRemoveLikedTrack = { viewModel.removeLikedTasteDiscoveryTrack(it) },
                        onRemovePassedTrack = { viewModel.removePassedTasteDiscoveryTrack(it) },
                        onResetMusicTaste = { viewModel.resetMusicTastePreferences() }
                    )
                }

                // 12b-taste-editor. Single-step Music Taste Editor / Restart Discovery Overlay
                AnimatedVisibility(
                    visible = musicTasteEditMode != null,
                    enter = modalEnter,
                    exit = modalExit
                ) {
                    val activeEditMode = musicTasteEditMode
                    if (activeEditMode != null) {
                        ui.onboarding.MusicTasteOnboardingScreen(
                            colors = editorialColors,
                            reduceAnimations = effectiveReduceAnimations,
                            launchMode = activeEditMode,
                            profile = musicTasteProfile,
                            artistState = musicTasteArtistState,
                            discoveryState = musicTasteDiscoveryState,
                            previewState = musicTastePreviewState,
                            onLoadInitialArtists = { viewModel.loadInitialTasteArtists(it) },
                            onSearchArtists = { viewModel.searchTasteArtists(it) },
                            onToggleArtist = { viewModel.toggleTasteArtist(it) },
                            onClearArtists = { viewModel.clearTasteArtists() },
                            onToggleGenre = { viewModel.toggleTasteGenre(it) },
                            onClearGenres = { viewModel.clearTasteGenres() },
                            onLoadDiscoverySession = { viewModel.loadTasteDiscoverySession(it) },
                            onDecideTrack = { track, decision -> viewModel.decideTasteDiscoveryTrack(track, decision) },
                            onTogglePreview = { viewModel.toggleTasteDiscoveryPreview(it) },
                            onStopPreview = { viewModel.stopTasteDiscoveryPreview() },
                            onPreviousTrack = { viewModel.stepBackTasteDiscoveryTrack() },
                            onNextTrack = { viewModel.stepNextTasteDiscoveryTrack() },
                            onClearDiscoverySelections = { viewModel.clearTasteDiscoverySessionSelections() },
                            onSkipOnboarding = { musicTasteEditMode = null },
                            onCompleteOnboarding = { musicTasteEditMode = null },
                            onDismissEditMode = { musicTasteEditMode = null }
                        )
                    }
                }

                // 12b-ch. Dedicated Full-Screen YouTube-Style Channel Detail Page Overlay
                // Layered below VideoPlayerScreen so playback continues seamlessly in FloatingVideoMiniPlayer
                // when opening a channel from a video, and tapping a channel video expands cleanly on top.
                AnimatedVisibility(
                    visible = isChannelDetailOpen,
                    enter = pushEnter,
                    exit = pushExit
                ) {
                    val showChannelVideoMiniPlayer = videoPlayerState.hasActiveVideo && !isVideoPlayerExpanded
                    Box(modifier = Modifier.fillMaxSize()) {
                        ChannelDetailScreen(
                            state = videoChannelState,
                            authState = videoAuthState,
                            supportsNotificationApi = videoPlayerState.supportsNotificationApi,
                            activeVideoId = videoPlayerState.activeVideo?.videoId,
                            watchProgressByVideoId = watchProgressByVideoId,
                            hasBottomMiniPlayer = showChannelVideoMiniPlayer,
                            colors = editorialColors,
                            onBack = { viewModel.videoController.dismissChannelProfile() },
                            onRetryChannel = { url, name ->
                                viewModel.videoController.openChannelProfile(url, name, forceRefresh = true)
                            },
                            onRefreshChannel = { viewModel.videoController.refreshChannelProfile() },
                            onSelectTab = { viewModel.videoController.selectChannelTab(it) },
                            onRetryTab = { viewModel.videoController.retryChannelTab(it) },
                            onLoadMoreTab = { viewModel.videoController.loadMoreChannelTab(it) },
                            onPlayVideo = { item, pool ->
                                viewModel.videoController.playVideo(item, pool, expandPlayer = true)
                            },
                            onPlayAll = { videos ->
                                viewModel.videoController.playAllChannelVideos(videos)
                            },
                            onPlayBackground = { videos, channelName ->
                                viewModel.videoController.playChannelVideosInBackground(videos, channelName)
                            },
                            onPlayPopup = { videos ->
                                viewModel.videoController.playChannelVideosInPopup(videos)
                            },
                            onOpenPlaylist = { playlist ->
                                viewModel.videoController.openChannelPlaylistDetail(playlist)
                            },
                            onToggleFollowLocal = { channel ->
                                viewModel.videoController.toggleFollowChannelLocally(channel)
                            },
                            onToggleSubscribeProvider = { channel ->
                                viewModel.videoController.onToggleSubscribeChannel(channel)
                            },
                            onSelectNotificationPref = { channel, pref ->
                                viewModel.videoController.onSelectNotificationPreference(channel, pref)
                            },
                            onVideoMoreClick = { videoOptionsTarget = it }
                        )
                    }
                }

                // 12b-pip. Persistent In-App Floating PiP Video Player Overlay
                // Single TextureView-backed overlay layered above primary tabs & Channel Detail
                // and below full-screen VideoPlayerScreen so playback persists seamlessly
                // across navigation without recreating ExoPlayer or video surfaces.
                val showFloatingVideoPip = showVideoMiniPlayer && (showBottomChrome || isChannelDetailOpen)
                if (showFloatingVideoPip) {
                    FloatingVideoMiniPlayer(
                        playerState = videoPlayerState,
                        colors = editorialColors,
                        onExpand = { viewModel.videoController.expandVideoPlayer() },
                        onTogglePlayPause = { viewModel.videoController.toggleVideoPlayPause() },
                        onPlayNext = { viewModel.videoController.playNextInQueue() },
                        onClose = { viewModel.videoController.closeVideoPlayer() },
                        playbackProgressFlow = viewModel.videoController.playbackProgress,
                        onAttachTextureView = { viewModel.videoController.attachVideoSurface(it) },
                        onDetachTextureView = { viewModel.videoController.detachVideoSurface(it) },
                        bottomInsetClearance = if (showBottomChrome && !isChannelDetailOpen) 86.dp else 12.dp,
                        onVideoViewportBoundsChanged = onVideoViewportBoundsChanged,
                        modifier = Modifier
                            .fillMaxSize()
                            .statusBarsPadding()
                            .navigationBarsPadding()
                    )
                }

                // 12c. Full-Screen & Portrait Online Video Player + Video Details Overlay
                AnimatedVisibility(
                    visible = isVideoPlayerExpanded,
                    enter = modalEnter,
                    exit = modalExit
                ) {
                    VideoPlayerScreen(
                        playerState = videoPlayerState,
                        watchLaterIds = watchLaterVideoIds,
                        savedVideoIds = savedVideoIds,
                        videoPlaylists = videoPlaylists,
                        colors = editorialColors,
                        onAttachSurface = { viewModel.videoController.attachVideoSurface(it) },
                        onDetachSurface = { viewModel.videoController.detachVideoSurface(it) },
                        onTogglePlayPause = { viewModel.videoController.toggleVideoPlayPause() },
                        onSeekTo = { viewModel.videoController.seekVideoTo(it) },
                        onSeekBy = { viewModel.videoController.seekVideoBy(it) },
                        onPlayNext = { viewModel.videoController.playNextInQueue() },
                        onPlayPrevious = { viewModel.videoController.playPreviousInQueue() },
                        onSelectStream = { viewModel.videoController.selectVideoStream(it) },
                        onSelectAudio = { viewModel.videoController.selectVideoAudioTrack(it) },
                        onSelectSubtitle = { viewModel.videoController.selectVideoSubtitle(it) },
                        onSelectSpeed = { viewModel.videoController.setVideoPlaybackSpeed(it) },
                        onSelectAspectRatio = { viewModel.videoController.setVideoAspectRatioMode(it) },
                        onToggleFullScreen = { viewModel.videoController.setFullScreenLandscape(it) },
                        onMinimize = { viewModel.videoController.minimizeVideoPlayer() },
                        onRetry = { viewModel.videoController.retryActiveVideo() },
                        onToggleLike = { viewModel.videoController.onToggleLikeVideo() },
                        onToggleDislike = { viewModel.videoController.onToggleDislikeVideo() },
                        onToggleSubscribe = { viewModel.videoController.onToggleSubscribeChannel(it) },
                        onSelectNotificationPref = { channel, pref ->
                            viewModel.videoController.onSelectNotificationPreference(channel, pref)
                        },
                        onToggleWatchLater = { viewModel.videoController.toggleWatchLater(it) },
                        onToggleSaveVideo = { viewModel.videoController.toggleSaveVideo(it) },
                        onAddVideoToPlaylist = { pid, pname, item ->
                            viewModel.videoController.addVideoToPlaylist(pid, pname, item)
                        },
                        onCreateVideoPlaylist = { pname, item ->
                            viewModel.videoController.createVideoPlaylist(pname, item)
                        },
                        onPlayRelatedVideo = { item, pool ->
                            viewModel.videoController.playVideo(item, pool, expandPlayer = true)
                        },
                        onRemoveFromQueue = { viewModel.videoController.removeVideoFromQueue(it) },
                        onOpenChannel = { url, name ->
                            viewModel.videoController.openChannelProfile(url, name)
                        },
                        onVideoMoreClick = { videoOptionsTarget = it },
                        onSelectAutoQuality = { viewModel.videoController.selectAutoVideoQuality() },
                        onRequestDownloadVideo = { video, details ->
                            viewModel.requestDownloadVideo(video, details)
                        },
                        recommendationFeedback = videoRecommendationFeedback,
                        playbackProgressFlow = viewModel.videoController.playbackProgress,
                        onAttachSurfaceView = { viewModel.videoController.attachVideoSurfaceView(it) },
                        onDetachSurfaceView = { viewModel.videoController.detachVideoSurfaceView(it) },
                        onVideoViewportBoundsChanged = onVideoViewportBoundsChanged
                    )
                }

                // 13. Floating Download Feedback Toast Banner
                AnimatedVisibility(
                    visible = downloadFeedbackMessage != null,
                    enter = slideInVertically { it } + fadeIn(),
                    exit = slideOutVertically { it } + fadeOut(),
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .navigationBarsPadding()
                        .padding(
                            start = 18.dp,
                            end = 18.dp,
                            bottom = domain.player.MusicMiniPlayerVisibilityPolicy.floatingBannerBottomPadding(
                                showBottomChrome = showBottomChrome,
                                isMusicMiniPlayerVisible = showMusicMiniPlayer,
                                isVideoMiniPlayerVisible = showVideoMiniPlayer
                            )
                        )
                ) {
                    val messageText = downloadFeedbackMessage ?: ""
                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = editorialColors.surfaceElevated,
                        shadowElevation = 8.dp,
                        border = BorderStroke(1.dp, editorialColors.accentStrong.copy(alpha = 0.45f)),
                        modifier = Modifier
                            .widthIn(max = 540.dp)
                            .fillMaxWidth()
                            .testTag("download_feedback_banner")
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 14.dp, end = 6.dp, top = 8.dp, bottom = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Downloading,
                                contentDescription = null,
                                tint = editorialColors.accentStrong,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = messageText,
                                fontFamily = PlusJakartaFontFamily,
                                fontWeight = FontWeight.Medium,
                                fontSize = 12.5.sp,
                                color = editorialColors.textPrimary,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            if (!showDownloadManagerScreen) {
                                TextButton(
                                    onClick = {
                                        viewModel.clearDownloadFeedback()
                                        showDownloadManagerScreen = true
                                    }
                                ) {
                                    Text(
                                        text = "Queue",
                                        fontFamily = OutfitFontFamily,
                                        fontWeight = FontWeight.Bold,
                                        color = editorialColors.accentStrong
                                    )
                                }
                            }
                            IconButton(
                                onClick = { viewModel.clearDownloadFeedback() },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Close,
                                    contentDescription = "Dismiss",
                                    tint = editorialColors.textMuted,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }

                // 14. Floating Library Feedback Toast Banner (Save/Follow/Playlist/Delete confirmation)
                AnimatedVisibility(
                    visible = libraryFeedbackMessage != null && downloadFeedbackMessage == null,
                    enter = slideInVertically { it } + fadeIn(),
                    exit = slideOutVertically { it } + fadeOut(),
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .navigationBarsPadding()
                        .padding(
                            start = 18.dp,
                            end = 18.dp,
                            bottom = if (showBottomChrome) (if (currentSong != null) 144.dp else 84.dp) else 20.dp
                        )
                ) {
                    val libMessage = libraryFeedbackMessage ?: ""
                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = editorialColors.surfaceElevated,
                        shadowElevation = 8.dp,
                        border = BorderStroke(1.dp, editorialColors.accentStrong.copy(alpha = 0.45f)),
                        modifier = Modifier
                            .widthIn(max = 540.dp)
                            .fillMaxWidth()
                            .testTag("library_feedback_banner")
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 14.dp, end = 6.dp, top = 8.dp, bottom = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.BookmarkAdded,
                                contentDescription = null,
                                tint = editorialColors.accentStrong,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = libMessage,
                                fontFamily = PlusJakartaFontFamily,
                                fontWeight = FontWeight.Medium,
                                fontSize = 12.5.sp,
                                color = editorialColors.textPrimary,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(
                                onClick = { viewModel.clearLibraryFeedback() },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Close,
                                    contentDescription = "Dismiss",
                                    tint = editorialColors.textMuted,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }

                // 15. Floating Online Video Feedback Toast Banner
                AnimatedVisibility(
                    visible = videoFeedbackMessage != null && downloadFeedbackMessage == null && libraryFeedbackMessage == null,
                    enter = slideInVertically { it } + fadeIn(),
                    exit = slideOutVertically { it } + fadeOut(),
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .navigationBarsPadding()
                        .padding(
                            start = 18.dp,
                            end = 18.dp,
                            bottom = if (showBottomChrome) (if (currentSong != null || videoPlayerState.hasActiveVideo) 144.dp else 84.dp) else 20.dp
                        )
                ) {
                    val vidMsg = videoFeedbackMessage ?: ""
                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = editorialColors.surfaceElevated,
                        shadowElevation = 8.dp,
                        border = BorderStroke(1.dp, editorialColors.accentStrong.copy(alpha = 0.45f)),
                        modifier = Modifier
                            .widthIn(max = 540.dp)
                            .fillMaxWidth()
                            .testTag("video_feedback_banner")
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 14.dp, end = 6.dp, top = 8.dp, bottom = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.PlayCircle,
                                contentDescription = null,
                                tint = editorialColors.accentStrong,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = vidMsg,
                                fontFamily = PlusJakartaFontFamily,
                                fontWeight = FontWeight.Medium,
                                fontSize = 12.5.sp,
                                color = editorialColors.textPrimary,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(
                                onClick = { viewModel.videoController.clearFeedback() },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Close,
                                    contentDescription = "Dismiss",
                                    tint = editorialColors.textMuted,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }

                // 16. Graceful Session Restore Notice Banner (e.g., unavailable restored track while queue is preserved)
                AnimatedVisibility(
                    visible = sessionRestoreNotice != null && videoFeedbackMessage == null && downloadFeedbackMessage == null && libraryFeedbackMessage == null,
                    enter = slideInVertically { it } + fadeIn(),
                    exit = slideOutVertically { it } + fadeOut(),
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .navigationBarsPadding()
                        .padding(
                            start = 18.dp,
                            end = 18.dp,
                            bottom = domain.player.MusicMiniPlayerVisibilityPolicy.floatingBannerBottomPadding(
                                showBottomChrome = showBottomChrome,
                                isMusicMiniPlayerVisible = showMusicMiniPlayer,
                                isVideoMiniPlayerVisible = showVideoMiniPlayer
                            )
                        )
                ) {
                    val noticeMsg = sessionRestoreNotice ?: ""
                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = editorialColors.surfaceElevated,
                        shadowElevation = 8.dp,
                        border = BorderStroke(1.dp, editorialColors.accentStrong.copy(alpha = 0.45f)),
                        modifier = Modifier
                            .widthIn(max = 540.dp)
                            .fillMaxWidth()
                            .testTag("session_restore_notice_banner")
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 14.dp, end = 6.dp, top = 8.dp, bottom = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = noticeMsg,
                                fontFamily = PlusJakartaFontFamily,
                                fontWeight = FontWeight.Medium,
                                fontSize = 12.5.sp,
                                color = editorialColors.textPrimary,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(
                                onClick = { viewModel.dismissSessionRestoreNotice() },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Close,
                                    contentDescription = "Dismiss",
                                    tint = editorialColors.textMuted,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            }

            // Channel Playlist Detail Sheet (opened when tapping a playlist on ChannelDetailScreen)
            ChannelPlaylistDetailSheet(
                state = videoChannelPlaylistState,
                activeVideoId = videoPlayerState.activeVideo?.videoId,
                watchProgressByVideoId = watchProgressByVideoId,
                colors = editorialColors,
                onPlayVideo = { item, pool ->
                    viewModel.videoController.playVideo(item, pool, expandPlayer = true)
                },
                onPlayAll = { videos ->
                    viewModel.videoController.playAllChannelVideos(videos)
                },
                onPlayBackground = { videos, name ->
                    viewModel.videoController.playChannelVideosInBackground(videos, name)
                },
                onLoadMore = { viewModel.videoController.loadMoreChannelPlaylist() },
                onRetry = { pl -> viewModel.videoController.openChannelPlaylistDetail(pl) },
                onVideoMoreClick = { videoOptionsTarget = it },
                onDismiss = { viewModel.videoController.dismissChannelPlaylistDetail() }
            )

            // Official YouTube Account Action Authorization / Policy Dialog
            videoAccountActionPrompt?.let { prompt ->
                val activeChannel = videoPlayerState.activeVideo?.let { v ->
                    OnlineVideoChannel(
                        channelId = data.video.OnlineVideoDataSource.extractChannelIdFromUrl(v.channelUrl)
                            .ifBlank { v.channelName },
                        name = v.channelName,
                        channelUrl = v.channelUrl ?: "https://www.youtube.com",
                        avatarUrl = v.channelAvatarUrl
                    )
                }
                VideoAccountActionDialog(
                    result = prompt,
                    activeChannel = activeChannel,
                    colors = editorialColors,
                    onBookmarkChannelLocally = { viewModel.videoController.bookmarkChannelLocally(it) },
                    onDismiss = { viewModel.videoController.dismissAccountActionPrompt() }
                )
            }

            // Video Overflow & Playlist Picker Modals triggered from VideoPlayerScreen / VideoChannelSheet
            videoOptionsTarget?.let { target ->
                VideoOptionsBottomSheet(
                    video = target,
                    isWatchLater = target.videoId in watchLaterVideoIds,
                    isSaved = target.videoId in savedVideoIds,
                    colors = editorialColors,
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

            pendingVideoDownload?.let { (videoItem, details) ->
                VideoDownloadQualitySheet(
                    video = videoItem,
                    details = details,
                    folderLabel = downloadLocationConfig.folderLabel,
                    colors = editorialColors,
                    onSelectOption = { option -> viewModel.confirmVideoDownload(videoItem, option) },
                    onDismiss = { viewModel.dismissVideoDownloadSheet() }
                )
            }

            videoPlaylistPickerTarget?.let { target ->
                VideoPlaylistPickerDialog(
                    video = target,
                    playlists = videoPlaylists,
                    colors = editorialColors,
                    onSelectPlaylist = { playlist ->
                        viewModel.videoController.addVideoToPlaylist(playlist.id, playlist.name, target)
                    },
                    onCreatePlaylistWithVideo = { name ->
                        viewModel.videoController.createVideoPlaylist(name, target)
                    },
                    onDismiss = { videoPlaylistPickerTarget = null }
                )
            }

            // Download SAF Folder Prompt & Quality Selector Modals
            when (val flow = downloadFlowState) {
                DownloadFlowUiState.Idle -> Unit
                is DownloadFlowUiState.AskFolderLocation -> {
                    ChooseDownloadLocationDialog(
                        reasonMessage = flow.reasonMessage,
                        colors = editorialColors,
                        onChooseFolder = {
                            downloadFolderLauncher.launch(flow.suggestedInitialUri)
                        },
                        onDismiss = { viewModel.dismissDownloadFlow() }
                    )
                }
                is DownloadFlowUiState.QualitySelector -> {
                    DownloadQualitySelectorSheet(
                        data = flow.data,
                        colors = editorialColors,
                        onSelectPreset = { viewModel.selectDownloadQualityPreset(it) },
                        onSelectManualCandidate = { viewModel.selectDownloadFormatCandidate(it) },
                        onConfirmDownload = { viewModel.confirmStartDownload() },
                        onDismiss = { viewModel.dismissDownloadFlow() }
                    )
                }
            }

            // Official GitHub Release Update Dialogs (Available, Downloading, Verifying, Ready, Permission, Error)
            ui.update.SonoraAppUpdateDialogHost(
                dialogState = appUpdateDialogState,
                colors = editorialColors,
                onDownloadUpdate = { release -> viewModel.startAppUpdateDownload(release) },
                onLater = { viewModel.deferAppUpdateLater() },
                onCancelDownload = { viewModel.cancelAppUpdateDownload() },
                onInstallNow = { release, verifiedApk ->
                    appScope.launch {
                        when (val prep = viewModel.prepareInstallVerifiedUpdate(release, verifiedApk)) {
                            is data.update.InstallPreparationResult.LaunchInstaller -> {
                                runCatching { apkInstallerLauncher.launch(prep.installIntent) }
                                    .onFailure { viewModel.onPackageInstallerReturnedWithoutUpgrade(wasCancelled = false) }
                            }
                            else -> Unit
                        }
                    }
                },
                onOpenUnknownSourcesSettings = {
                    val settingsIntent = viewModel.buildManageUnknownAppSourcesIntent()
                    if (settingsIntent != null) {
                        runCatching { unknownSourcesSettingsLauncher.launch(settingsIntent) }
                    }
                },
                onDismiss = { viewModel.dismissAppUpdateDialog() }
            )

            // One-time Post-Update Security Reminder (shown once after verified successful update)
            postUpdateReminder?.let { reminder ->
                ui.update.PostUpdateSecurityReminderDialog(
                    state = reminder,
                    colors = editorialColors,
                    onOpenSettings = {
                        val settingsIntent = viewModel.buildManageUnknownAppSourcesIntent()
                        if (settingsIntent != null) {
                            runCatching { unknownSourcesSettingsLauncher.launch(settingsIntent) }
                        }
                    },
                    onDismiss = { viewModel.dismissPostUpdateReminder() }
                )
            }
        }
    }
}
