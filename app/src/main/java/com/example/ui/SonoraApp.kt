package com.example.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.ui.components.MiniPlayerView
import com.example.ui.screens.EqualizerSpatialScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.LibraryScreen
import com.example.ui.screens.NowPlayingScreen
import com.example.ui.screens.PlaylistsScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.theme.SonoraCardBorder
import com.example.ui.theme.SonoraGold
import com.example.ui.theme.SonoraObsidian
import com.example.ui.theme.SonoraSurfaceDark
import com.example.ui.theme.SonoraSurfaceElevated
import com.example.ui.theme.SonoraTextMuted
import com.example.ui.theme.SonoraTextPrimary
import com.example.ui.theme.SonoraTheme
import com.example.viewmodel.SonoraViewModel

sealed class NavigationTab(val index: Int, val title: String, val icon: ImageVector) {
    object Home : NavigationTab(0, "Home", Icons.Default.Home)
    object Library : NavigationTab(1, "Library", Icons.Default.LibraryMusic)
    object Playlists : NavigationTab(2, "Playlists", Icons.Default.QueueMusic)
    object Equalizer : NavigationTab(3, "Equalizer", Icons.Default.GraphicEq)
    object Settings : NavigationTab(4, "Settings", Icons.Default.Settings)

    companion object {
        val items = listOf(Home, Library, Playlists, Equalizer, Settings)
    }
}

@Composable
fun SonoraApp(viewModel: SonoraViewModel) {
    val themeMode by viewModel.themeMode.collectAsState()
    val currentSong by viewModel.currentSong.collectAsState()
    val playbackInfo by viewModel.playbackInfo.collectAsState()

    var currentTab by remember { mutableIntStateOf(0) }
    var isNowPlayingExpanded by remember { mutableStateOf(false) }

    val context = LocalContext.current

    // Audio storage permissions request launcher
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

    SonoraTheme(themeMode = themeMode) {
        BackHandler(enabled = isNowPlayingExpanded) {
            isNowPlayingExpanded = false
        }

        Scaffold(
            modifier = Modifier.fillMaxSize(),
            bottomBar = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                ) {
                    // Floating Mini-Player (visible when a song exists and full screen is not open)
                    if (currentSong != null && !isNowPlayingExpanded) {
                        MiniPlayerView(
                            song = currentSong,
                            playbackInfo = playbackInfo,
                            onTogglePlayPause = { viewModel.togglePlayPause() },
                            onPlayNext = { viewModel.playNext() },
                            onClick = { isNowPlayingExpanded = true }
                        )
                    }

                    // Material 3 Navigation Bar
                    NavigationBar(
                        containerColor = SonoraSurfaceDark,
                        tonalElevation = 8.dp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, SonoraCardBorder, RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
                    ) {
                        NavigationTab.items.forEach { tab ->
                            val isSelected = currentTab == tab.index
                            NavigationBarItem(
                                selected = isSelected,
                                onClick = { currentTab = tab.index },
                                icon = {
                                    Icon(
                                        imageVector = tab.icon,
                                        contentDescription = tab.title,
                                        tint = if (isSelected) SonoraGold else SonoraTextMuted
                                    )
                                },
                                label = {
                                    Text(
                                        text = tab.title,
                                        color = if (isSelected) SonoraGold else SonoraTextMuted,
                                        fontSize = 11.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                    )
                                },
                                colors = NavigationBarItemDefaults.colors(
                                    indicatorColor = SonoraSurfaceElevated,
                                    selectedIconColor = SonoraGold,
                                    unselectedIconColor = SonoraTextMuted
                                ),
                                modifier = Modifier.testTag("nav_tab_${tab.title.lowercase()}")
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                when (currentTab) {
                    0 -> HomeScreen(
                        viewModel = viewModel,
                        onNavigateToEqualizer = { currentTab = 3 },
                        onNavigateToPlaylists = { currentTab = 2 },
                        onOpenNowPlaying = { isNowPlayingExpanded = true }
                    )
                    1 -> LibraryScreen(
                        viewModel = viewModel
                    )
                    2 -> PlaylistsScreen(
                        viewModel = viewModel
                    )
                    3 -> EqualizerSpatialScreen(
                        viewModel = viewModel
                    )
                    4 -> SettingsScreen(
                        viewModel = viewModel,
                        onNavigateToEqualizer = { currentTab = 3 }
                    )
                }
            }
        }

        // Full Screen Animated Now Playing Overlay
        AnimatedVisibility(
            visible = isNowPlayingExpanded,
            enter = slideInVertically(initialOffsetY = { it }),
            exit = slideOutVertically(targetOffsetY = { it })
        ) {
            NowPlayingScreen(
                viewModel = viewModel,
                onClose = { isNowPlayingExpanded = false },
                onNavigateToEqualizer = {
                    isNowPlayingExpanded = false
                    currentTab = 3
                }
            )
        }
    }
}
