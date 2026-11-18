package com.example.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.ui.equalizer.AdaptiveEqualizerScreen
import com.example.ui.screens.LibraryScreen
import com.example.ui.screens.NowPlayingScreen
import com.example.ui.screens.PlaylistsScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.theme.SonoraTheme
import com.example.viewmodel.SonoraViewModel
import ui.audiostudio.AudioEffectsStudioScreen
import ui.components.FloatingMiniPlayer
import ui.home.FloatingBottomNavigation
import ui.home.HomeScreen
import ui.home.SearchScreen
import ui.motion.LocalEffectiveMotionState
import ui.motion.LocalSonoraMotionConfig
import ui.motion.SonoraTransitions
import ui.motion.horizontalSwipeNavigation
import ui.motion.rememberEffectiveMotionState
import ui.online.OnlineMusicScreen
import ui.queue.QueueScreen
import ui.settings.LibraryFolderManagementScreen
import ui.theme.rememberEditorialColors

@Composable
fun SonoraApp(viewModel: SonoraViewModel) {
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
    var showPlayerStyleScreen by remember { mutableStateOf(false) }

    val context = LocalContext.current

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

    LaunchedEffect(Unit) {
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
            val pushEnter = SonoraTransitions.pushEnter(motionState)
            val pushExit = SonoraTransitions.pushExit(motionState)
            val modalEnter = SonoraTransitions.modalEnter(motionState)
            val modalExit = SonoraTransitions.modalExit(motionState)

            // Ordered back-stack dismissal matching exact visual Z-order (topmost overlay first)
            BackHandler(enabled = showPlayerStyleScreen) {
                showPlayerStyleScreen = false
            }
            BackHandler(enabled = showDynamicThemeScreen && !showPlayerStyleScreen) {
                showDynamicThemeScreen = false
            }
            BackHandler(enabled = showQueueScreen && !showDynamicThemeScreen && !showPlayerStyleScreen) {
                showQueueScreen = false
            }
            BackHandler(enabled = showFolderManagementScreen && !showQueueScreen && !showDynamicThemeScreen && !showPlayerStyleScreen) {
                showFolderManagementScreen = false
            }
            BackHandler(enabled = showAudioStudioScreen && !showFolderManagementScreen && !showQueueScreen && !showDynamicThemeScreen && !showPlayerStyleScreen) {
                showAudioStudioScreen = false
            }
            BackHandler(enabled = showEqualizerScreen && !showAudioStudioScreen && !showFolderManagementScreen && !showQueueScreen && !showDynamicThemeScreen && !showPlayerStyleScreen) {
                showEqualizerScreen = false
            }
            BackHandler(enabled = isNowPlayingExpanded && !showEqualizerScreen && !showAudioStudioScreen && !showFolderManagementScreen && !showQueueScreen && !showDynamicThemeScreen && !showPlayerStyleScreen) {
                isNowPlayingExpanded = false
            }
            BackHandler(enabled = showPlaylistsScreen && !isNowPlayingExpanded && !showEqualizerScreen && !showAudioStudioScreen && !showFolderManagementScreen && !showQueueScreen && !showDynamicThemeScreen && !showPlayerStyleScreen) {
                showPlaylistsScreen = false
            }
            BackHandler(enabled = showSettingsScreen && !showPlaylistsScreen && !isNowPlayingExpanded && !showEqualizerScreen && !showAudioStudioScreen && !showFolderManagementScreen && !showQueueScreen && !showDynamicThemeScreen && !showPlayerStyleScreen) {
                showSettingsScreen = false
            }
            BackHandler(enabled = currentTab != 0 && !showSettingsScreen && !showPlaylistsScreen && !isNowPlayingExpanded && !showEqualizerScreen && !showAudioStudioScreen && !showFolderManagementScreen && !showQueueScreen && !showDynamicThemeScreen && !showPlayerStyleScreen) {
                currentTab = 0
            }

            val showBottomChrome = !isNowPlayingExpanded &&
                !showEqualizerScreen &&
                !showAudioStudioScreen &&
                !showPlaylistsScreen &&
                !showQueueScreen &&
                !showFolderManagementScreen &&
                !showDynamicThemeScreen &&
                !showPlayerStyleScreen &&
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
                            screenVisible = showBottomChrome && currentTab == 0,
                            onOpenSettings = { showSettingsScreen = true },
                            onNavigateToSearch = { currentTab = 1 },
                            onNavigateToLibrary = { currentTab = 2 },
                            onOpenOnlineMusic = { currentTab = 3 },
                            onOpenNowPlaying = { isNowPlayingExpanded = true },
                            onOpenEqualizer = { showEqualizerScreen = true },
                            onOpenAudioStudio = { showAudioStudioScreen = true },
                            onOpenPlaylists = { showPlaylistsScreen = true },
                            onOpenQueue = { showQueueScreen = true },
                            onOpenDynamicColors = { showDynamicThemeScreen = true },
                            onOpenPlayerStyles = { showPlayerStyleScreen = true }
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
                            onOpenPlaylists = { showPlaylistsScreen = true }
                        )
                        3 -> OnlineMusicScreen(
                            viewModel = viewModel,
                            colors = editorialColors,
                            initialSearchOpen = launchOnlineSearch,
                            onInitialSearchConsumed = { launchOnlineSearch = false },
                            onOpenSettings = { showSettingsScreen = true }
                        )
                    }
                }

                // 2. Persistent Floating Mini-Player + Floating Pill Bottom Navigation
                if (showBottomChrome) {
                    Column(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .navigationBarsPadding(),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        if (currentSong != null) {
                            val gesturesEnabled by viewModel.gesturesEnabled.collectAsState()
                            val miniPlayerGestures by viewModel.miniPlayerGestures.collectAsState()
                            val miniPlayerStyle by viewModel.miniPlayerStyle.collectAsState()

                            FloatingMiniPlayer(
                                song = currentSong,
                                playbackInfo = playbackInfo,
                                colors = editorialColors,
                                onTogglePlayPause = { viewModel.togglePlayPause() },
                                onPlayNext = { viewModel.playNext() },
                                onPlayPrevious = { viewModel.playPrevious() },
                                onClick = { isNowPlayingExpanded = true },
                                gesturesEnabled = gesturesEnabled && miniPlayerGestures,
                                miniPlayerStyle = miniPlayerStyle,
                                trackDirection = trackDirection,
                                swipeThresholdDp = motionState.swipeThresholdDp
                            )
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
                        onOpenQueueScreen = { showQueueScreen = true }
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

                // 11. Now Playing Player Style Selector Overlay
                AnimatedVisibility(
                    visible = showPlayerStyleScreen,
                    enter = pushEnter,
                    exit = pushExit
                ) {
                    ui.player.PlayerStyleSelector(
                        viewModel = viewModel,
                        onBack = { showPlayerStyleScreen = false },
                        onOpenPlayer = {
                            showPlayerStyleScreen = false
                            isNowPlayingExpanded = true
                        }
                    )
                }
            }
        }
    }
}
