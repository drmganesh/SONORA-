package com.example.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.snap
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.runtime.Composable
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
    val currentSong by viewModel.currentSong.collectAsState()
    val playbackInfo by viewModel.playbackInfo.collectAsState()
    val systemDark = androidx.compose.foundation.isSystemInDarkTheme()

    LaunchedEffect(systemDark) {
        viewModel.updateSystemDarkTheme(systemDark)
    }

    var currentTab by remember { mutableIntStateOf(0) } // 0 = Home, 1 = Search, 2 = Library
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
        val granted = results.values.any { it }
        if (granted) {
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
        reduceAnimations = reduceAnimations
    )

    SonoraTheme(
        themeMode = themeMode,
        dynamicPalette = dynamicPalette,
        editorialColors = editorialColors,
        reduceAnimations = reduceAnimations
    ) {
        val slideEnter = if (reduceAnimations) EnterTransition.None else slideInVertically(initialOffsetY = { it })
        val slideExit = if (reduceAnimations) ExitTransition.None else slideOutVertically(targetOffsetY = { it })
        val subtleEnter = if (reduceAnimations) EnterTransition.None else slideInVertically(initialOffsetY = { it / 6 }) + fadeIn()
        val subtleExit = if (reduceAnimations) ExitTransition.None else slideOutVertically(targetOffsetY = { it / 6 }) + fadeOut()

        // Ordered back-stack dismissal matching visual Z-order (topmost overlay first)
        BackHandler(enabled = showQueueScreen) {
            showQueueScreen = false
        }
        BackHandler(enabled = showFolderManagementScreen && !showQueueScreen) {
            showFolderManagementScreen = false
        }
        BackHandler(enabled = showPlayerStyleScreen && !showQueueScreen && !showFolderManagementScreen) {
            showPlayerStyleScreen = false
        }
        BackHandler(enabled = showDynamicThemeScreen && !showPlayerStyleScreen && !showQueueScreen && !showFolderManagementScreen) {
            showDynamicThemeScreen = false
        }
        BackHandler(enabled = showAudioStudioScreen && !showDynamicThemeScreen && !showPlayerStyleScreen && !showQueueScreen && !showFolderManagementScreen) {
            showAudioStudioScreen = false
        }
        BackHandler(enabled = showEqualizerScreen && !showAudioStudioScreen && !showDynamicThemeScreen && !showPlayerStyleScreen && !showQueueScreen && !showFolderManagementScreen) {
            showEqualizerScreen = false
        }
        BackHandler(enabled = isNowPlayingExpanded && !showEqualizerScreen && !showAudioStudioScreen && !showDynamicThemeScreen && !showPlayerStyleScreen && !showQueueScreen && !showFolderManagementScreen) {
            isNowPlayingExpanded = false
        }
        BackHandler(enabled = showPlaylistsScreen && !isNowPlayingExpanded && !showEqualizerScreen && !showAudioStudioScreen && !showDynamicThemeScreen && !showPlayerStyleScreen && !showQueueScreen && !showFolderManagementScreen) {
            showPlaylistsScreen = false
        }
        BackHandler(enabled = showSettingsScreen && !showPlaylistsScreen && !isNowPlayingExpanded && !showEqualizerScreen && !showAudioStudioScreen && !showDynamicThemeScreen && !showPlayerStyleScreen && !showQueueScreen && !showFolderManagementScreen) {
            showSettingsScreen = false
        }
        BackHandler(enabled = currentTab != 0 && !showSettingsScreen && !showPlaylistsScreen && !isNowPlayingExpanded && !showEqualizerScreen && !showAudioStudioScreen && !showDynamicThemeScreen && !showPlayerStyleScreen && !showQueueScreen && !showFolderManagementScreen) {
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
            // 1. Primary Destinations (Home, Search, Library)
            AnimatedContent(
                targetState = currentTab,
                transitionSpec = {
                    if (reduceAnimations) {
                        fadeIn(snap()) togetherWith fadeOut(snap())
                    } else {
                        fadeIn() togetherWith fadeOut()
                    }
                },
                label = "main_tab_transition",
                modifier = Modifier.fillMaxSize()
            ) { tab ->
                when (tab) {
                    0 -> HomeScreen(
                        viewModel = viewModel,
                        colors = editorialColors,
                        onOpenSettings = { showSettingsScreen = true },
                        onNavigateToLibrary = { currentTab = 2 },
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
                        onOpenSettings = { showSettingsScreen = true }
                    )
                    2 -> LibraryScreen(
                        viewModel = viewModel,
                        onOpenFolderManagement = { showFolderManagementScreen = true },
                        onOpenPlaylists = { showPlaylistsScreen = true }
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
                            miniPlayerStyle = miniPlayerStyle
                        )
                    }

                    FloatingBottomNavigation(
                        selectedIndex = currentTab,
                        colors = editorialColors,
                        onSelectTab = { currentTab = it }
                    )
                }
            }

            // 3. Settings Overlay
            AnimatedVisibility(
                visible = showSettingsScreen,
                enter = subtleEnter,
                exit = subtleExit
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

            // 4. Playlists Management Overlay
            AnimatedVisibility(
                visible = showPlaylistsScreen,
                enter = subtleEnter,
                exit = subtleExit
            ) {
                PlaylistsScreen(
                    viewModel = viewModel,
                    onBack = { showPlaylistsScreen = false }
                )
            }

            // 5. Full Screen Now Playing Player Coordinator (All 19 Styles)
            AnimatedVisibility(
                visible = isNowPlayingExpanded,
                enter = slideEnter,
                exit = slideExit
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
                enter = slideEnter,
                exit = slideExit
            ) {
                AdaptiveEqualizerScreen(
                    viewModel = viewModel,
                    onBack = { showEqualizerScreen = false }
                )
            }

            // 7. Audio Effects Studio (8D Orbit, 360° Spatial, Bass, Reverb) Overlay
            AnimatedVisibility(
                visible = showAudioStudioScreen,
                enter = slideEnter,
                exit = slideExit
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
                enter = subtleEnter,
                exit = subtleExit
            ) {
                LibraryFolderManagementScreen(
                    viewModel = viewModel,
                    onBack = { showFolderManagementScreen = false }
                )
            }

            // 9. Playing Queue & Recently Played Screen Overlay
            AnimatedVisibility(
                visible = showQueueScreen,
                enter = slideEnter,
                exit = slideExit
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

            // 10. Dynamic Colours Personalization Overlay (opened from Profile Sheet)
            AnimatedVisibility(
                visible = showDynamicThemeScreen,
                enter = subtleEnter,
                exit = subtleExit
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

            // 11. Now Playing Player Style Selector Overlay (opened from Profile Sheet)
            AnimatedVisibility(
                visible = showPlayerStyleScreen,
                enter = subtleEnter,
                exit = subtleExit
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
