package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AcUnit
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Balance
import androidx.compose.material.icons.rounded.BluetoothConnected
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.CleaningServices
import androidx.compose.material.icons.rounded.CloudQueue
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material.icons.rounded.DownloadDone
import androidx.compose.material.icons.rounded.Downloading
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.FolderSpecial
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.LinearScale
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.PlayCircleOutline
import androidx.compose.material.icons.rounded.Policy
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.SdStorage
import androidx.compose.material.icons.rounded.SpatialAudio
import androidx.compose.material.icons.rounded.Source
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.DoubleTapAction
import com.example.model.LockScreenStyle
import com.example.model.SingleTapAction
import com.example.model.SwipeAction
import com.example.ui.theme.OutfitFontFamily
import com.example.ui.theme.PlusJakartaFontFamily
import com.example.viewmodel.SonoraViewModel
import domain.download.DownloadedTrack
import domain.download.formatByteCount
import ui.downloads.DownloadManagerScreen
import ui.home.animations.HomeAnimationSettingsScreen
import ui.player.PlayerAppearanceHubScreen
import ui.player.PlayerAppearanceSection
import data.cache.formatCacheLimitLabel
import data.cache.formatCacheSize
import ui.settings.BackgroundPlaybackSettingsScreen
import ui.settings.CacheManagementScreen
import ui.settings.DynamicThemeSettingsScreen
import ui.settings.LibraryFolderManagementScreen
import ui.settings.MusicExperienceSettingsScreen
import ui.settings.OnlineVideoPlayerGesturesScreen
import ui.settings.SeekbarStyleScreen
import ui.settings.SettingsAboutContent
import ui.settings.VideoExperienceSettingsScreen
import ui.settings.rememberInstalledAppVersion
import ui.motion.LocalEffectiveMotionState
import ui.motion.SonoraTransitions
import ui.theme.EditorialAccentSwatches
import ui.theme.EditorialColors
import ui.theme.HomeThemeMode
import ui.theme.LocalEditorialColors
import java.util.Locale
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SonoraViewModel,
    onOpenNowPlaying: () -> Unit = {},
    onOpenEqualizer: () -> Unit = {},
    onOpenAudioStudio: () -> Unit = {},
    onOpenFolderManagement: (() -> Unit)? = null,
    onOpenDownloadManager: (() -> Unit)? = null,
    onBack: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val colors = LocalEditorialColors.current

    val homeThemeMode by viewModel.homeThemeMode.collectAsState()
    val customAccentArgb by viewModel.homeCustomAccentArgb.collectAsState()
    val reduceAnimations by viewModel.reduceAnimations.collectAsState()
    val playerStyle by viewModel.playerStyle.collectAsState()
    val seekbarStyle by viewModel.seekbarStyle.collectAsState()
    val dynamicColorsEnabled by viewModel.dynamicColorsEnabled.collectAsState()
    val dynamicThemeMode by viewModel.dynamicThemeMode.collectAsState()
    val dynamicAppearanceMode by viewModel.dynamicAppearanceMode.collectAsState()
    val dynamicColorIntensity by viewModel.dynamicColorIntensity.collectAsState()
    val dynamicGradientEnabled by viewModel.dynamicGradientEnabled.collectAsState()
    val homeAnimationLevel by viewModel.homeAnimationLevel.collectAsState()
    val dailyMixAnimationStyle by viewModel.dailyMixAnimationStyle.collectAsState()
    val weatherAmbientSettings by viewModel.weatherAmbientSettings.collectAsState()
    val weatherFetchState by viewModel.weatherFetchState.collectAsState()

    val gaplessEnabled by viewModel.gaplessEnabled.collectAsState()
    val crossfadeSeconds by viewModel.crossfadeSeconds.collectAsState()
    val playbackSpeed by viewModel.playbackSpeed.collectAsState()
    val automixEnabled by viewModel.automixEnabled.collectAsState()
    val matchNextSongEnabled by viewModel.matchNextSongEnabled.collectAsState()
    val audioFocusEnabled by viewModel.audioFocusEnabled.collectAsState()
    val resumeOnLaunch by viewModel.resumeOnLaunch.collectAsState()
    val headsetAutoPlayPause by viewModel.headsetAutoPlayPause.collectAsState()

    val gestureConfig by viewModel.playerGestureConfig.collectAsState()
    val miniPlayerStyle by viewModel.miniPlayerStyle.collectAsState()
    val lockScreenStyle by viewModel.lockScreenStyle.collectAsState()

    val eqSettings by viewModel.equalizerSettings.collectAsState()
    val orbit8dEnabled by viewModel.orbit8dEnabled.collectAsState()
    val spatialConfig by viewModel.spatialConfig.collectAsState()
    val isTwsConnected by viewModel.isTwsConnected.collectAsState()
    val connectedAudioDeviceName by viewModel.connectedAudioDeviceName.collectAsState()

    val allSongs by viewModel.allSongs.collectAsState()
    val folderFilterSettings by viewModel.folderFilterSettings.collectAsState()
    val showLockScreenControls by viewModel.showLockScreenControls.collectAsState()
    val showNotificationFormatBadge by viewModel.showNotificationFormatBadge.collectAsState()
    val onlineContentCountry by viewModel.onlineContentCountry.collectAsState()
    val onlineContentLanguage by viewModel.onlineContentLanguage.collectAsState()
    val videoGestureSettings by viewModel.videoController.gestureSettings.collectAsState()
    val videoExperienceSettings by viewModel.videoExperienceSettings.collectAsState()
    val musicExperienceSettings by viewModel.musicExperienceSettings.collectAsState()

    val downloadStorageSummary by viewModel.downloadStorageSummary.collectAsState()
    val downloadedTracks by viewModel.downloadedTracks.collectAsState()
    val downloadTasks by viewModel.downloadTasks.collectAsState()
    val activeDownloadTasksCount by viewModel.activeDownloadTasksCount.collectAsState()
    val cacheSettings by viewModel.cacheSettings.collectAsState()
    val cacheStorageSnapshot by viewModel.cacheStorageSnapshot.collectAsState()

    val motionConfig by viewModel.motionConfig.collectAsState()

    var showMotionGesturesSettings by remember { mutableStateOf(false) }
    var showOnlineVideoPlayerGestures by remember { mutableStateOf(false) }
    var showVideoExperienceSettings by remember { mutableStateOf(false) }
    var showMusicExperienceSettings by remember { mutableStateOf(false) }
    var showHomeAnimationSettings by remember { mutableStateOf(false) }
    var showAmbientEffectsSettings by remember { mutableStateOf(false) }
    var showPlayerAppearanceHub by remember { mutableStateOf(false) }
    var playerAppearanceHubInitialSection by remember { mutableStateOf(PlayerAppearanceSection.NOW_PLAYING) }
    var showSeekbarStyleScreen by remember { mutableStateOf(false) }
    var showDynamicThemeSettings by remember { mutableStateOf(false) }
    var showInternalFolderManagement by remember { mutableStateOf(false) }
    var showInternalDownloadManager by remember { mutableStateOf(false) }
    var showCacheManagement by remember { mutableStateOf(false) }
    var showBackgroundPlaybackSettings by remember { mutableStateOf(false) }
    var showManageDownloadedFilesSheet by remember { mutableStateOf(false) }
    var showClearDownloadHistoryDialog by remember { mutableStateOf(false) }
    var downloadedTrackPendingDelete by remember { mutableStateOf<DownloadedTrack?>(null) }
    var showPrivacyDialog by remember { mutableStateOf(false) }
    var showLicensesDialog by remember { mutableStateOf(false) }
    var rescanRequested by remember { mutableStateOf(false) }

    var appearanceExpanded by remember { mutableStateOf(true) }
    var playbackExpanded by remember { mutableStateOf(true) }
    var audioExpanded by remember { mutableStateOf(true) }
    var libraryExpanded by remember { mutableStateOf(true) }
    var onlineExpanded by remember { mutableStateOf(true) }
    var onlineVideoExpanded by remember { mutableStateOf(true) }
    var storageCacheExpanded by remember { mutableStateOf(true) }
    var downloadsExpanded by remember { mutableStateOf(true) }
    var notificationsExpanded by remember { mutableStateOf(true) }
    var aboutExpanded by remember { mutableStateOf(true) }
    val installedAppVersion = rememberInstalledAppVersion()
    val motionState = LocalEffectiveMotionState.current
    val pushEnter = SonoraTransitions.pushEnter(motionState)
    val pushExit = SonoraTransitions.pushExit(motionState)

    Box(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(colors.backgroundBrush)
                .statusBarsPadding()
                .testTag("settings_screen"),
            contentAlignment = Alignment.TopCenter
        ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 760.dp)
        ) {
            // Editorial Top Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (onBack != null) {
                    Surface(
                        shape = CircleShape,
                        color = colors.surface,
                        shadowElevation = 2.dp,
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .clickable { onBack() }
                            .testTag("settings_back_button")
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                                contentDescription = "Back",
                                tint = colors.textPrimary
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(14.dp))
                }
                Column {
                    Text(
                        text = "Settings",
                        fontFamily = OutfitFontFamily,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 28.sp,
                        color = colors.textPrimary
                    )
                    Text(
                        text = "SONORA Aesthetic UI • Audio & Library",
                        fontFamily = PlusJakartaFontFamily,
                        fontSize = 12.sp,
                        color = colors.textSecondary
                    )
                }
            }

            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("settings_lazy_column"),
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 6.dp, bottom = 100.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // 1. APPEARANCE
                item {
                    ExpandableSettingsSection(
                        title = "APPEARANCE",
                        subtitle = homeThemeMode.displayName,
                        expanded = appearanceExpanded,
                        onToggle = { appearanceExpanded = !appearanceExpanded },
                        colors = colors
                    ) {
                        Text(
                            text = "App Theme Palette (9 Themes)",
                            fontFamily = OutfitFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = colors.textPrimary
                        )
                        Spacer(modifier = Modifier.height(10.dp))

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            HomeThemeMode.entries.forEach { mode ->
                                val selected = homeThemeMode == mode
                                Surface(
                                    shape = RoundedCornerShape(18.dp),
                                    color = if (selected) colors.accentPill.copy(alpha = 0.6f) else colors.surfaceTinted,
                                    modifier = Modifier
                                        .width(126.dp)
                                        .border(
                                            width = if (selected) 1.5.dp else 0.75.dp,
                                            color = if (selected) colors.accentStrong else colors.cardBorder,
                                            shape = RoundedCornerShape(18.dp)
                                        )
                                        .clip(RoundedCornerShape(18.dp))
                                        .clickable { viewModel.setHomeThemeMode(mode) }
                                        .testTag("settings_theme_${mode.id}")
                                ) {
                                    Column(modifier = Modifier.padding(12.dp)) {
                                        Box(modifier = Modifier.width(44.dp).height(26.dp)) {
                                            Box(
                                                modifier = Modifier
                                                    .size(24.dp)
                                                    .background(Color(mode.previewColorArgb), CircleShape)
                                                    .border(1.dp, colors.cardBorder, CircleShape)
                                            )
                                            Box(
                                                modifier = Modifier
                                                    .size(24.dp)
                                                    .offset(x = 14.dp)
                                                    .background(Color(mode.previewAccentArgb), CircleShape)
                                                    .border(1.dp, colors.cardBorder, CircleShape)
                                            )
                                        }
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Text(
                                            text = mode.displayName,
                                            fontFamily = OutfitFontFamily,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 12.sp,
                                            color = colors.textPrimary,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        Text(
                            text = "Custom Accent Hue",
                            fontFamily = OutfitFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = colors.textPrimary
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = if (customAccentArgb == null) colors.accentStrong else colors.surfaceTinted,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(14.dp))
                                    .clickable { viewModel.setHomeCustomAccentArgb(null) }
                            ) {
                                Text(
                                    text = "Default",
                                    fontFamily = PlusJakartaFontFamily,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp,
                                    color = if (customAccentArgb == null) colors.onAccentStrong else colors.textSecondary,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp)
                                )
                            }

                            EditorialAccentSwatches.presets.forEach { (name, argb) ->
                                val picked = customAccentArgb == argb
                                Box(
                                    modifier = Modifier
                                        .size(30.dp)
                                        .clip(CircleShape)
                                        .background(Color(argb))
                                        .border(
                                            width = if (picked) 2.5.dp else 1.dp,
                                            color = if (picked) colors.textPrimary else colors.cardBorder,
                                            shape = CircleShape
                                        )
                                        .clickable { viewModel.setHomeCustomAccentArgb(argb) },
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (picked) {
                                        Icon(Icons.Rounded.Check, contentDescription = name, tint = Color.Black, modifier = Modifier.size(16.dp))
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))
                        HorizontalDivider(color = colors.cardBorder)
                        Spacer(modifier = Modifier.height(10.dp))

                        SettingsSwitchRow(
                            title = "Reduce Animations",
                            subtitle = "Minimize motion transitions & glowing pulses",
                            checked = reduceAnimations,
                            onCheckedChange = { viewModel.setReduceAnimations(it) },
                            colors = colors
                        )

                        SettingsSwitchRow(
                            title = "Dynamic Album Art",
                            subtitle = "Adapt app colors automatically to playing cover",
                            checked = dynamicColorsEnabled,
                            onCheckedChange = { viewModel.setDynamicColorsEnabled(it) },
                            colors = colors
                        )

                        SettingsSwitchRow(
                            title = "Dynamic Gradient",
                            subtitle = "Smooth tonal album-art gradients behind screens",
                            checked = dynamicGradientEnabled,
                            onCheckedChange = { viewModel.setDynamicGradientEnabled(it) },
                            colors = colors
                        )

                        Spacer(modifier = Modifier.height(6.dp))

                        SettingsLinkRow(
                            icon = Icons.Rounded.AutoAwesome,
                            title = "Motion & Gestures",
                            subtitle = "${motionConfig.intensity.label} • ${motionConfig.transitionStyle.label} • ${motionConfig.gestureSensitivity.label} sensitivity",
                            colors = colors,
                            modifier = Modifier.testTag("settings_motion_gestures_row"),
                            onClick = { showMotionGesturesSettings = true }
                        )

                        SettingsLinkRow(
                            icon = Icons.Rounded.AutoAwesome,
                            title = "Home Animations",
                            subtitle = "${homeAnimationLevel.displayName} • Daily Mix: ${dailyMixAnimationStyle.displayName}",
                            colors = colors,
                            onClick = { showHomeAnimationSettings = true }
                        )

                        val ambientSubtitle = when (weatherAmbientSettings.mode) {
                            domain.weather.WeatherEffectMode.OFF ->
                                "Off • Weather-synced Odisha atmosphere"
                            domain.weather.WeatherEffectMode.MANUAL ->
                                "Manual • ${weatherAmbientSettings.manualPreset.label} (Simulated)"
                            domain.weather.WeatherEffectMode.AUTOMATIC -> {
                                val snap = weatherFetchState.latestSnapshot
                                val town = weatherAmbientSettings.selectedLocation.townName
                                if (snap != null) {
                                    "Auto • $town • ${snap.temperatureC.toInt()}°C ${snap.conditionLabel}"
                                } else {
                                    "Auto • $town • Open-Meteo weather"
                                }
                            }
                        }
                        val ambientIcon = when (weatherAmbientSettings.mode) {
                            domain.weather.WeatherEffectMode.OFF -> Icons.Rounded.CloudQueue
                            domain.weather.WeatherEffectMode.MANUAL ->
                                ui.components.atmosphereIconForPrimary(
                                    domain.weather.WeatherAtmosphereEngine.resolvePlan(
                                        settings = weatherAmbientSettings,
                                        snapshot = null,
                                        surfaceRole = domain.weather.WeatherSurfaceRole.SettingsPreview
                                    ).primary
                                )
                            domain.weather.WeatherEffectMode.AUTOMATIC -> {
                                val snap = weatherFetchState.latestSnapshot
                                if (snap != null) {
                                    ui.components.weatherIconForCode(snap.weatherCode, snap.isDay)
                                } else {
                                    Icons.Rounded.WbSunny
                                }
                            }
                        }
                        SettingsLinkRow(
                            icon = ambientIcon,
                            title = "Ambient Effects",
                            subtitle = ambientSubtitle,
                            colors = colors,
                            modifier = Modifier.testTag("settings_ambient_effects_row"),
                            onClick = { showAmbientEffectsSettings = true }
                        )

                        SettingsLinkRow(
                            icon = Icons.Rounded.AutoAwesome,
                            title = "Dynamic Colours & Modes",
                            subtitle = if (dynamicColorsEnabled) "${dynamicAppearanceMode.displayName} • ${dynamicColorIntensity.displayName}" else "Off • ${dynamicThemeMode.displayName}",
                            colors = colors,
                            onClick = { showDynamicThemeSettings = true }
                        )

                        SettingsLinkRow(
                            icon = Icons.Rounded.Palette,
                            title = "Music Player",
                            subtitle = "${playerStyle.displayName} • ${miniPlayerStyle.displayName} • Styles, artwork, visualizer & controls",
                            colors = colors,
                            modifier = Modifier.testTag("settings_music_player_row"),
                            onClick = {
                                playerAppearanceHubInitialSection = PlayerAppearanceSection.NOW_PLAYING
                                showPlayerAppearanceHub = true
                            }
                        )

                        SettingsLinkRow(
                            icon = Icons.Rounded.LinearScale,
                            title = "Seekbar Style",
                            subtitle = "${seekbarStyle.displayName} • 10 styles",
                            colors = colors,
                            onClick = { showSeekbarStyleScreen = true }
                        )

                        HorizontalDivider(color = colors.cardBorder, modifier = Modifier.padding(vertical = 10.dp))

                        SettingsSwitchRow(
                            title = "Player Gestures",
                            subtitle = "Master switch for artwork, screen edge & mini player gestures",
                            checked = gestureConfig.gesturesEnabled,
                            onCheckedChange = { enabled ->
                                viewModel.updatePlayerGestureConfig { it.copy(gesturesEnabled = enabled) }
                            },
                            colors = colors
                        )
                        if (gestureConfig.gesturesEnabled) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Single-Tap Artwork Action",
                                fontFamily = OutfitFontFamily,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 13.sp,
                                color = colors.textPrimary
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            GestureOptionChipRow(
                                options = SingleTapAction.entries.map { it to it.label },
                                selected = gestureConfig.singleTapAction,
                                onSelect = { action ->
                                    viewModel.updatePlayerGestureConfig { it.copy(singleTapAction = action) }
                                },
                                colors = colors
                            )

                            Spacer(modifier = Modifier.height(8.dp))
                            SettingsSwitchRow(
                                title = "Double-Tap Artwork Gesture",
                                subtitle = "Double-tap artwork to seek or trigger quick action",
                                checked = gestureConfig.doubleTapEnabled,
                                onCheckedChange = { enabled ->
                                    viewModel.updatePlayerGestureConfig { it.copy(doubleTapEnabled = enabled) }
                                },
                                colors = colors
                            )
                            if (gestureConfig.doubleTapEnabled) {
                                GestureOptionChipRow(
                                    options = DoubleTapAction.entries.map { it to it.label },
                                    selected = gestureConfig.doubleTapAction,
                                    onSelect = { action ->
                                        viewModel.updatePlayerGestureConfig { it.copy(doubleTapAction = action) }
                                    },
                                    colors = colors
                                )
                            }

                            Spacer(modifier = Modifier.height(8.dp))
                            SettingsSwitchRow(
                                title = "Horizontal Swipe on Artwork",
                                subtitle = "Swipe artwork left or right for track skip, seek or style",
                                checked = gestureConfig.swipeArtworkEnabled,
                                onCheckedChange = { enabled ->
                                    viewModel.updatePlayerGestureConfig { it.copy(swipeArtworkEnabled = enabled) }
                                },
                                colors = colors
                            )
                            if (gestureConfig.swipeArtworkEnabled) {
                                Text(
                                    text = "Swipe Left Action",
                                    fontFamily = PlusJakartaFontFamily,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 12.sp,
                                    color = colors.textSecondary,
                                    modifier = Modifier.padding(top = 4.dp, bottom = 4.dp)
                                )
                                GestureOptionChipRow(
                                    options = SwipeAction.entries.map { it to it.label },
                                    selected = gestureConfig.swipeLeftAction,
                                    onSelect = { action ->
                                        viewModel.updatePlayerGestureConfig { it.copy(swipeLeftAction = action) }
                                    },
                                    colors = colors
                                )
                                Text(
                                    text = "Swipe Right Action",
                                    fontFamily = PlusJakartaFontFamily,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 12.sp,
                                    color = colors.textSecondary,
                                    modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                                )
                                GestureOptionChipRow(
                                    options = SwipeAction.entries.map { it to it.label },
                                    selected = gestureConfig.swipeRightAction,
                                    onSelect = { action ->
                                        viewModel.updatePlayerGestureConfig { it.copy(swipeRightAction = action) }
                                    },
                                    colors = colors
                                )
                            }

                            Spacer(modifier = Modifier.height(6.dp))
                            SettingsSwitchRow(
                                title = "Long-Press Artwork for Style Wheel",
                                subtitle = "Open quick 19-style switcher wheel anywhere",
                                checked = gestureConfig.longPressStyleWheelEnabled,
                                onCheckedChange = { enabled ->
                                    viewModel.updatePlayerGestureConfig { it.copy(longPressStyleWheelEnabled = enabled) }
                                },
                                colors = colors
                            )
                            SettingsSwitchRow(
                                title = "Pinch-to-Zoom Artwork",
                                subtitle = "Two-finger zoom up to 2.2x on album artwork",
                                checked = gestureConfig.pinchZoomArtworkEnabled,
                                onCheckedChange = { enabled ->
                                    viewModel.updatePlayerGestureConfig { it.copy(pinchZoomArtworkEnabled = enabled) }
                                },
                                colors = colors
                            )
                            SettingsSwitchRow(
                                title = "Swipe Down to Minimize",
                                subtitle = "Pull down player artwork to return to library",
                                checked = gestureConfig.swipeDownToMinimizeEnabled,
                                onCheckedChange = { enabled ->
                                    viewModel.updatePlayerGestureConfig { it.copy(swipeDownToMinimizeEnabled = enabled) }
                                },
                                colors = colors
                            )
                            SettingsSwitchRow(
                                title = "Swipe Up to Open Queue",
                                subtitle = "Swipe up from artwork or dock to view Up Next",
                                checked = gestureConfig.swipeUpToOpenQueueEnabled,
                                onCheckedChange = { enabled ->
                                    viewModel.updatePlayerGestureConfig { it.copy(swipeUpToOpenQueueEnabled = enabled) }
                                },
                                colors = colors
                            )
                            SettingsSwitchRow(
                                title = "Right-Edge Volume Slide",
                                subtitle = "Slide vertically near right edge to adjust media volume",
                                checked = gestureConfig.rightEdgeVolumeEnabled,
                                onCheckedChange = { enabled ->
                                    viewModel.updatePlayerGestureConfig { it.copy(rightEdgeVolumeEnabled = enabled) }
                                },
                                colors = colors
                            )
                            SettingsSwitchRow(
                                title = "Left-Edge Brightness Slide",
                                subtitle = "Slide vertically near left edge to adjust screen brightness",
                                checked = gestureConfig.leftEdgeBrightnessEnabled,
                                onCheckedChange = { enabled ->
                                    viewModel.updatePlayerGestureConfig { it.copy(leftEdgeBrightnessEnabled = enabled) }
                                },
                                colors = colors
                            )
                            SettingsSwitchRow(
                                title = "Mini-Player Swipes",
                                subtitle = "Swipe mini-player horizontally to skip or up to expand",
                                checked = gestureConfig.miniPlayerGesturesEnabled,
                                onCheckedChange = { enabled ->
                                    viewModel.updatePlayerGestureConfig { it.copy(miniPlayerGesturesEnabled = enabled) }
                                },
                                colors = colors
                            )
                            SettingsSwitchRow(
                                title = "Haptic Gesture Feedback",
                                subtitle = "Subtle tactile confirmation when gestures trigger",
                                checked = gestureConfig.hapticFeedbackEnabled,
                                onCheckedChange = { enabled ->
                                    viewModel.updatePlayerGestureConfig { it.copy(hapticFeedbackEnabled = enabled) }
                                },
                                colors = colors
                            )

                            Spacer(modifier = Modifier.height(6.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Swipe Sensitivity", fontFamily = OutfitFontFamily, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = colors.textPrimary)
                                Text("${String.format(Locale.US, "%.2fx", gestureConfig.swipeSensitivity)}", fontFamily = PlusJakartaFontFamily, fontWeight = FontWeight.Bold, fontSize = 12.sp, color = colors.accentStrong)
                            }
                            Slider(
                                value = gestureConfig.swipeSensitivity,
                                onValueChange = { v ->
                                    val snapped = ((v * 20f).roundToInt() / 20f).coerceIn(0.5f, 1.8f)
                                    viewModel.updatePlayerGestureConfig { it.copy(swipeSensitivity = snapped) }
                                },
                                valueRange = 0.5f..1.8f,
                                colors = SliderDefaults.colors(
                                    thumbColor = colors.accentStrong,
                                    activeTrackColor = colors.accentStrong
                                )
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Swipe Distance Threshold", fontFamily = OutfitFontFamily, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = colors.textPrimary)
                                Text("${gestureConfig.swipeThresholdDp} dp", fontFamily = PlusJakartaFontFamily, fontWeight = FontWeight.Bold, fontSize = 12.sp, color = colors.accentStrong)
                            }
                            Slider(
                                value = gestureConfig.swipeThresholdDp.toFloat(),
                                onValueChange = { v ->
                                    viewModel.updatePlayerGestureConfig { it.copy(swipeThresholdDp = v.roundToInt().coerceIn(32, 110)) }
                                },
                                valueRange = 32f..110f,
                                colors = SliderDefaults.colors(
                                    thumbColor = colors.accentStrong,
                                    activeTrackColor = colors.accentStrong
                                )
                            )

                            Spacer(modifier = Modifier.height(4.dp))
                            OutlinedButton(
                                onClick = { viewModel.resetPlayerGestureConfigToDefault() },
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("settings_reset_gestures_button")
                            ) {
                                Text(
                                    text = "Reset Gestures to Default",
                                    fontFamily = PlusJakartaFontFamily,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 13.sp,
                                    color = colors.accentStrong
                                )
                            }
                        }
                    }
                }

                // 2. PLAYBACK
                item {
                    ExpandableSettingsSection(
                        title = "PLAYBACK",
                        subtitle = "${String.format(Locale.US, "%.2fx", playbackSpeed)} • ${if (crossfadeSeconds == 0) "No crossfade" else "${crossfadeSeconds}s crossfade"}",
                        expanded = playbackExpanded,
                        onToggle = { playbackExpanded = !playbackExpanded },
                        colors = colors
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Playback Speed", fontFamily = OutfitFontFamily, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = colors.textPrimary)
                            Text(
                                text = "${String.format(Locale.US, "%.2fx", playbackSpeed)}",
                                fontFamily = PlusJakartaFontFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = colors.accentStrong
                            )
                        }
                        Slider(
                            value = playbackSpeed,
                            onValueChange = {
                                val snapped = ((it * 20f).roundToInt() / 20f).coerceIn(0.5f, 2.0f)
                                viewModel.setPlaybackSpeed(snapped)
                            },
                            valueRange = 0.5f..2.0f,
                            colors = SliderDefaults.colors(
                                thumbColor = colors.accentStrong,
                                activeTrackColor = colors.accentStrong
                            )
                        )

                        Spacer(modifier = Modifier.height(6.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Crossfade Duration", fontFamily = OutfitFontFamily, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = colors.textPrimary)
                            Text(
                                text = if (crossfadeSeconds == 0) "OFF" else "${crossfadeSeconds}s",
                                fontFamily = PlusJakartaFontFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = colors.accentStrong
                            )
                        }
                        Slider(
                            value = crossfadeSeconds.toFloat(),
                            onValueChange = { viewModel.setCrossfadeSeconds(it.toInt()) },
                            valueRange = 0f..10f,
                            steps = 9,
                            colors = SliderDefaults.colors(
                                thumbColor = colors.accentStrong,
                                activeTrackColor = colors.accentStrong
                            )
                        )

                        SettingsSwitchRow(
                            title = "Gapless Playback",
                            subtitle = "Minimize transitions between consecutive tracks",
                            checked = gaplessEnabled,
                            onCheckedChange = { viewModel.setGaplessEnabled(it) },
                            colors = colors
                        )
                        SettingsSwitchRow(
                            title = "Automix Transition",
                            subtitle = "Auto-crossfade smoothly before track ends",
                            checked = automixEnabled,
                            onCheckedChange = { viewModel.toggleAutomix(it) },
                            colors = colors
                        )
                        SettingsSwitchRow(
                            title = "Harmonic Track Matching",
                            subtitle = "Prefer matching artist & genre flow in Automix",
                            checked = matchNextSongEnabled,
                            onCheckedChange = { viewModel.toggleMatchNextSong(it) },
                            colors = colors
                        )
                        SettingsSwitchRow(
                            title = "Audio Focus Management",
                            subtitle = "Pause for calls & duck for navigation prompts",
                            checked = audioFocusEnabled,
                            onCheckedChange = { viewModel.setAudioFocusEnabled(it) },
                            colors = colors
                        )
                        SettingsSwitchRow(
                            title = "Resume Playback on Launch",
                            subtitle = "Remember last active track & position",
                            checked = resumeOnLaunch,
                            onCheckedChange = { viewModel.setResumeOnLaunch(it) },
                            colors = colors
                        )
                        SettingsSwitchRow(
                            title = "Headset Auto Play / Pause",
                            subtitle = "Auto-pause when wired/Bluetooth audio disconnects",
                            checked = headsetAutoPlayPause,
                            onCheckedChange = { viewModel.setHeadsetAutoPlayPause(it) },
                            colors = colors
                        )

                        Spacer(modifier = Modifier.height(6.dp))
                        SettingsLinkRow(
                            icon = Icons.Rounded.PlayCircleOutline,
                            title = "Background Playback & Session",
                            subtitle = "Circular mini player, phone call resume, restore & Recents",
                            colors = colors,
                            modifier = Modifier.testTag("settings_open_background_session"),
                            onClick = { showBackgroundPlaybackSettings = true }
                        )

                        Spacer(modifier = Modifier.height(6.dp))
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = colors.surfaceTinted,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.Top
                            ) {
                                Icon(Icons.Rounded.Info, contentDescription = null, tint = colors.textMuted, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "ReplayGain tag normalization is not natively supported by Android MediaPlayer. Use Loudness Enhancer inside Audio Studio for level boosting.",
                                    fontFamily = PlusJakartaFontFamily,
                                    fontSize = 11.sp,
                                    lineHeight = 16.sp,
                                    color = colors.textSecondary
                                )
                            }
                        }
                    }
                }

                // 3. AUDIO
                item {
                    ExpandableSettingsSection(
                        title = "AUDIO",
                        subtitle = if (eqSettings.isEnabled) "15-Band EQ Active" else "EQ Bypassed",
                        expanded = audioExpanded,
                        onToggle = { audioExpanded = !audioExpanded },
                        colors = colors
                    ) {
                        SettingsLinkRow(
                            icon = Icons.Rounded.GraphicEq,
                            title = "15-Band Adaptive Equalizer",
                            subtitle = if (eqSettings.isEnabled) "${eqSettings.mode.name} • ${eqSettings.macroProfile.label}" else "Bypassed • Tap to sculpt sound",
                            colors = colors,
                            onClick = onOpenEqualizer
                        )

                        SettingsLinkRow(
                            icon = Icons.Rounded.SpatialAudio,
                            title = "Audio Studio (8D & 360°)",
                            subtitle = if (orbit8dEnabled) "8D Slow-Orbit Active • Spatial Stage" else "Bass Boost, Virtualizer, Reverb & 8D Orbit",
                            colors = colors,
                            onClick = onOpenAudioStudio
                        )

                        HorizontalDivider(color = colors.cardBorder, modifier = Modifier.padding(vertical = 8.dp))

                        SettingsSwitchRow(
                            title = "TWS Ultra-Wide Stereo",
                            subtitle = if (isTwsConnected) "Active on $connectedAudioDeviceName" else "Auto-engages for Bluetooth earbuds",
                            checked = spatialConfig.twsWideStereoEnabled,
                            onCheckedChange = { viewModel.toggleTwsWideStereo(it) },
                            colors = colors
                        )

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .clickable { viewModel.forceBalanceDualEarbuds() }
                                .padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Rounded.Balance, contentDescription = null, tint = colors.accentStrong)
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Recenter Dual-Earbud Balance", fontFamily = OutfitFontFamily, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = colors.textPrimary)
                                Text("Reset stereo L/R channel balance to 50/50", fontFamily = PlusJakartaFontFamily, fontSize = 12.sp, color = colors.textSecondary)
                            }
                        }

                        Spacer(modifier = Modifier.height(6.dp))
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = colors.surfaceTinted,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.Top
                            ) {
                                Icon(Icons.Rounded.Info, contentDescription = null, tint = colors.textMuted, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "True L+R PCM mono audio summing requires OS-level Accessibility Mono Audio on MediaPlayer devices.",
                                    fontFamily = PlusJakartaFontFamily,
                                    fontSize = 11.sp,
                                    lineHeight = 16.sp,
                                    color = colors.textSecondary
                                )
                            }
                        }
                    }
                }

                // 4. LIBRARY
                item {
                    val folderSummary = remember(folderFilterSettings) {
                        if (folderFilterSettings.whitelistEnabled) {
                            "Whitelist Active (${folderFilterSettings.whitelistedFolders.size}) • ${folderFilterSettings.blacklistedFolders.size} hidden"
                        } else if (folderFilterSettings.blacklistedFolders.isNotEmpty()) {
                            "${folderFilterSettings.blacklistedFolders.size} blacklisted • Whitelist Off"
                        } else {
                            "Blacklist & multi-folder Whitelist controls"
                        }
                    }
                    ExpandableSettingsSection(
                        title = "LIBRARY",
                        subtitle = "${allSongs.size} active tracks",
                        expanded = libraryExpanded,
                        onToggle = { libraryExpanded = !libraryExpanded },
                        colors = colors
                    ) {
                        SettingsLinkRow(
                            icon = Icons.Rounded.FolderSpecial,
                            title = "Folder Blacklist & Whitelist",
                            subtitle = folderSummary,
                            colors = colors,
                            onClick = {
                                onOpenFolderManagement?.invoke() ?: run { showInternalFolderManagement = true }
                            }
                        )

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .clickable {
                                    viewModel.loadLocalSongs()
                                    rescanRequested = true
                                }
                                .padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .background(colors.accentPill, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Rounded.Refresh, contentDescription = null, tint = colors.onAccentPill, modifier = Modifier.size(20.dp))
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Rescan Device Music", fontFamily = OutfitFontFamily, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = colors.textPrimary)
                                Text(
                                    text = if (rescanRequested) "Library refreshed (${allSongs.size} tracks)" else "${allSongs.size} tracks indexed offline",
                                    fontFamily = PlusJakartaFontFamily,
                                    fontSize = 12.sp,
                                    color = if (rescanRequested) colors.accentStrong else colors.textSecondary
                                )
                            }
                        }
                    }
                }

                // 5. ONLINE MUSIC & REGION
                item {
                    val countryName = domain.online.OnlineRegionCatalog.supportedCountries
                        .firstOrNull { it.code.equals(onlineContentCountry, ignoreCase = true) }
                        ?.name ?: onlineContentCountry
                    val languageName = domain.online.OnlineRegionCatalog.supportedLanguages
                        .firstOrNull { it.code.equals(onlineContentLanguage, ignoreCase = true) }
                        ?.name ?: onlineContentLanguage
                    ExpandableSettingsSection(
                        title = "ONLINE MUSIC & REGION",
                        subtitle = "$countryName ($onlineContentCountry) • $languageName ($onlineContentLanguage)",
                        expanded = onlineExpanded,
                        onToggle = { onlineExpanded = !onlineExpanded },
                        colors = colors
                    ) {
                        Text(
                            text = "Content Country",
                            fontFamily = OutfitFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = colors.textPrimary
                        )
                        Text(
                            text = "Sets charts and discovery country without changing language",
                            fontFamily = PlusJakartaFontFamily,
                            fontSize = 11.sp,
                            color = colors.textSecondary
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        GestureOptionChipRow(
                            options = domain.online.OnlineRegionCatalog.supportedCountries.map { it.code to "${it.name} (${it.code})" },
                            selected = onlineContentCountry,
                            onSelect = { viewModel.setOnlineContentCountry(it) },
                            colors = colors
                        )

                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Preferred Language",
                            fontFamily = OutfitFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = colors.textPrimary
                        )
                        Text(
                            text = "Sets Accept-Language (${onlineContentLanguage.lowercase()}-${onlineContentCountry.uppercase()}) without changing country",
                            fontFamily = PlusJakartaFontFamily,
                            fontSize = 11.sp,
                            color = colors.textSecondary
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        GestureOptionChipRow(
                            options = domain.online.OnlineRegionCatalog.supportedLanguages.map { it.code to "${it.name} (${it.code})" },
                            selected = onlineContentLanguage,
                            onSelect = { viewModel.setOnlineContentLanguage(it) },
                            colors = colors
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        HorizontalDivider(color = colors.cardBorder)
                        Spacer(modifier = Modifier.height(6.dp))
                        SettingsLinkRow(
                            icon = Icons.Rounded.AutoAwesome,
                            title = "Music Experience",
                            subtitle = "Artwork: ${musicExperienceSettings.albumArtworkQualityMode.label} • ${if (musicExperienceSettings.unlimitedQueueEnabled) "Unlimited Queue" else "Standard Queue"} • ${if (musicExperienceSettings.autoSongRadioEnabled) "Auto Radio" else "Manual Radio"}",
                            colors = colors,
                            modifier = Modifier.testTag("settings_music_experience_row"),
                            onClick = { showMusicExperienceSettings = true }
                        )
                    }
                }

                // 5b. ONLINE VIDEO
                item {
                    ExpandableSettingsSection(
                        title = "ONLINE VIDEO",
                        subtitle = "${if (videoExperienceSettings.autoPipEnabled) "Auto PiP" else "PiP Off"} • ${if (videoExperienceSettings.autoVideoQualityEnabled) "Auto Quality" else "Manual Quality"} • ${videoGestureSettings.defaultAspectRatio.shortLabel}",
                        expanded = onlineVideoExpanded,
                        onToggle = { onlineVideoExpanded = !onlineVideoExpanded },
                        colors = colors
                    ) {
                        SettingsLinkRow(
                            icon = Icons.Rounded.Tune,
                            title = "Video Experience",
                            subtitle = "Automatic PiP, swipe mini-player, pinch-to-zoom, adaptive quality & download folder",
                            colors = colors,
                            modifier = Modifier.testTag("settings_video_experience_row"),
                            onClick = { showVideoExperienceSettings = true }
                        )
                        SettingsLinkRow(
                            icon = Icons.Rounded.PlayCircleOutline,
                            title = "Player Gestures",
                            subtitle = "Swipe fullscreen, smart aspect ratio (${videoGestureSettings.defaultAspectRatio.shortLabel}), ${videoGestureSettings.doubleTapSeekSeconds}s double-tap & auto-orientation",
                            colors = colors,
                            modifier = Modifier.testTag("settings_online_video_gestures_row"),
                            onClick = { showOnlineVideoPlayerGestures = true }
                        )
                    }
                }

                // 5c. STORAGE & CACHE
                item {
                    val musicUsed = formatCacheSize(cacheStorageSnapshot.musicCacheBytes)
                    val videoUsed = formatCacheSize(cacheStorageSnapshot.totalVideoAndShortsBytes)
                    val musicLimit = formatCacheLimitLabel(cacheSettings.clampedMusicLimitBytes)
                    ExpandableSettingsSection(
                        title = "STORAGE & CACHE",
                        subtitle = "Music $musicUsed / $musicLimit • Video $videoUsed",
                        expanded = storageCacheExpanded,
                        onToggle = { storageCacheExpanded = !storageCacheExpanded },
                        colors = colors
                    ) {
                        SettingsLinkRow(
                            icon = Icons.Rounded.SdStorage,
                            title = "Cache Management",
                            subtitle = "Streaming cache switches, $musicLimit music limit, preloading & cleanup",
                            colors = colors,
                            modifier = Modifier.testTag("settings_cache_management_row"),
                            onClick = { showCacheManagement = true }
                        )
                    }
                }

                // 6. DOWNLOADS
                item {
                    val usedSpaceText = formatByteCount(downloadStorageSummary.totalBytes).ifEmpty { "0 MB" }
                    ExpandableSettingsSection(
                        title = "DOWNLOADS",
                        subtitle = "${downloadStorageSummary.trackCount} tracks • $usedSpaceText",
                        expanded = downloadsExpanded,
                        onToggle = { downloadsExpanded = !downloadsExpanded },
                        colors = colors
                    ) {
                        SettingsLinkRow(
                            icon = Icons.Rounded.FolderOpen,
                            title = "Download Location",
                            subtitle = if (downloadStorageSummary.folderLabel.isNotBlank()) {
                                "${downloadStorageSummary.folderLabel} • Tap to change"
                            } else {
                                "Not chosen yet • Tap to select folder"
                            },
                            colors = colors,
                            modifier = Modifier.testTag("settings_change_download_folder"),
                            onClick = { viewModel.requestChangeDownloadFolder() }
                        )
                        SettingsLinkRow(
                            icon = Icons.Rounded.Downloading,
                            title = "Open Download Manager",
                            subtitle = if (activeDownloadTasksCount > 0) {
                                "$activeDownloadTasksCount active • ${downloadTasks.size} total tasks"
                            } else {
                                "${downloadTasks.size} transfer history records"
                            },
                            colors = colors,
                            modifier = Modifier.testTag("settings_open_download_manager"),
                            onClick = {
                                onOpenDownloadManager?.invoke() ?: run { showInternalDownloadManager = true }
                            }
                        )
                        SettingsLinkRow(
                            icon = Icons.Rounded.DownloadDone,
                            title = "Manage Downloaded Files",
                            subtitle = "${downloadedTracks.size} offline files • $usedSpaceText used",
                            colors = colors,
                            modifier = Modifier.testTag("settings_manage_downloaded_files"),
                            onClick = { showManageDownloadedFilesSheet = true }
                        )
                        SettingsLinkRow(
                            icon = Icons.Rounded.CleaningServices,
                            title = "Clear Download History",
                            subtitle = "Clears task history only (keeps audio files safe)",
                            colors = colors,
                            modifier = Modifier.testTag("settings_clear_download_history"),
                            onClick = { showClearDownloadHistoryDialog = true }
                        )
                    }
                }

                // 7. NOTIFICATIONS
                item {
                    ExpandableSettingsSection(
                        title = "NOTIFICATIONS",
                        subtitle = if (showLockScreenControls) "Lock-screen enabled" else "Private lock-screen",
                        expanded = notificationsExpanded,
                        onToggle = { notificationsExpanded = !notificationsExpanded },
                        colors = colors
                    ) {
                        SettingsSwitchRow(
                            title = "Lock-Screen Media Controls",
                            subtitle = "Show artwork & playback buttons on lock screen",
                            checked = showLockScreenControls,
                            onCheckedChange = {
                                viewModel.setShowLockScreenControls(it)
                                viewModel.syncNotification()
                            },
                            colors = colors
                        )
                        SettingsSwitchRow(
                            title = "Audio Format Badge",
                            subtitle = "Show codec & bitrate info in media notification",
                            checked = showNotificationFormatBadge,
                            onCheckedChange = {
                                viewModel.setShowNotificationFormatBadge(it)
                                viewModel.syncNotification()
                            },
                            colors = colors
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Lock-Screen Media Style",
                            fontFamily = OutfitFontFamily,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp,
                            color = colors.textPrimary
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        GestureOptionChipRow(
                            options = LockScreenStyle.entries.map { it to it.displayName },
                            selected = lockScreenStyle,
                            onSelect = { style ->
                                viewModel.setLockScreenStyle(style)
                                viewModel.syncNotification()
                            },
                            colors = colors
                        )
                    }
                }

                // 6. ABOUT
                item {
                    ExpandableSettingsSection(
                        title = "ABOUT",
                        subtitle = "${installedAppVersion.compactLabel} • Dr. M. GANESWAR",
                        expanded = aboutExpanded,
                        onToggle = { aboutExpanded = !aboutExpanded },
                        colors = colors
                    ) {
                        SettingsAboutContent(
                            appVersion = installedAppVersion,
                            colors = colors,
                            reduceAnimations = reduceAnimations,
                            linksContent = {
                                SettingsLinkRow(
                                    icon = Icons.Rounded.Policy,
                                    title = "Privacy Policy",
                                    subtitle = "Zero telemetry • Local playback stays offline",
                                    colors = colors,
                                    onClick = { showPrivacyDialog = true }
                                )
                                SettingsLinkRow(
                                    icon = Icons.Rounded.Source,
                                    title = "Open-Source Libraries",
                                    subtitle = "NewPipe Extractor (GPL-3.0), Media3, Jetpack Compose & Room",
                                    colors = colors,
                                    onClick = { showLicensesDialog = true }
                                )
                            }
                        )
                    }
                }
            }
        }
    }

    if (showPrivacyDialog) {
        AlertDialog(
            onDismissRequest = { showPrivacyDialog = false },
            containerColor = colors.surface,
            title = {
                Text("Privacy Policy", fontFamily = OutfitFontFamily, fontWeight = FontWeight.Bold, color = colors.textPrimary)
            },
            text = {
                Text(
                    text = "SONORA collects zero telemetry and requires no accounts. Local library scanning, playlists, equalizer curves, and folder rules never leave your device. When you choose to use Online Music, search and stream metadata requests are sent directly to your selected provider via NewPipe Extractor.",
                    fontFamily = PlusJakartaFontFamily,
                    fontSize = 13.sp,
                    lineHeight = 19.sp,
                    color = colors.textSecondary
                )
            },
            confirmButton = {
                TextButton(onClick = { showPrivacyDialog = false }) {
                    Text("Close", color = colors.accentStrong, fontWeight = FontWeight.Bold)
                }
            }
        )
    }

    if (showLicensesDialog) {
        AlertDialog(
            onDismissRequest = { showLicensesDialog = false },
            containerColor = colors.surface,
            title = {
                Text("Open-Source Licenses", fontFamily = OutfitFontFamily, fontWeight = FontWeight.Bold, color = colors.textPrimary)
            },
            text = {
                Text(
                    text = "• NewPipe Extractor v0.27.1-sonora.1 — TeamNewPipe (GNU GPL-3.0)\n" +
                        "  https://github.com/TeamNewPipe/NewPipeExtractor\n" +
                        "• Jetpack Media3 ExoPlayer & OkHttp DataSource (Apache 2.0)\n" +
                        "• AndroidX Core, Activity Compose & Lifecycle (Apache 2.0)\n" +
                        "• Jetpack Compose & Material Design 3 (Apache 2.0)\n" +
                        "• AndroidX Room Database & Preferences DataStore (Apache 2.0)\n" +
                        "• Coil Image Loading & OkHttp (Apache 2.0)\n" +
                        "• Kotlin Coroutines & Serialization (Apache 2.0)",
                    fontFamily = PlusJakartaFontFamily,
                    fontSize = 13.sp,
                    lineHeight = 20.sp,
                    color = colors.textSecondary
                )
            },
            confirmButton = {
                TextButton(onClick = { showLicensesDialog = false }) {
                    Text("Close", color = colors.accentStrong, fontWeight = FontWeight.Bold)
                }
            }
        )
    }

        AnimatedVisibility(
            visible = showMotionGesturesSettings,
            enter = pushEnter,
            exit = pushExit
        ) {
            ui.settings.MotionGesturesSettingsScreen(
                viewModel = viewModel,
                onBack = { showMotionGesturesSettings = false }
            )
        }

        AnimatedVisibility(
            visible = showOnlineVideoPlayerGestures,
            enter = pushEnter,
            exit = pushExit
        ) {
            OnlineVideoPlayerGesturesScreen(
                settings = videoGestureSettings,
                onSwipeFullscreenChanged = { viewModel.videoController.setSwipeFullscreenEnabled(it) },
                onAutoAspectRatioChanged = { viewModel.videoController.setAutoAspectRatioEnabled(it) },
                onDefaultAspectRatioChanged = { viewModel.videoController.setDefaultAspectRatio(it) },
                onSwipeSensitivityChanged = { viewModel.videoController.setSwipeSensitivity(it) },
                onDoubleTapSeekSecondsChanged = { viewModel.videoController.setDoubleTapSeekSeconds(it) },
                onAutoOrientationChanged = { viewModel.videoController.setAutoOrientationEnabled(it) },
                onResetToDefaults = { viewModel.videoController.resetVideoGestureSettings() },
                onBack = { showOnlineVideoPlayerGestures = false }
            )
        }

        AnimatedVisibility(
            visible = showVideoExperienceSettings,
            enter = pushEnter,
            exit = pushExit
        ) {
            VideoExperienceSettingsScreen(
                viewModel = viewModel,
                onBack = { showVideoExperienceSettings = false }
            )
        }

        AnimatedVisibility(
            visible = showMusicExperienceSettings,
            enter = pushEnter,
            exit = pushExit
        ) {
            MusicExperienceSettingsScreen(
                viewModel = viewModel,
                onBack = { showMusicExperienceSettings = false }
            )
        }

        AnimatedVisibility(
            visible = showHomeAnimationSettings,
            enter = pushEnter,
            exit = pushExit
        ) {
            HomeAnimationSettingsScreen(
                viewModel = viewModel,
                onBack = { showHomeAnimationSettings = false }
            )
        }

        AnimatedVisibility(
            visible = showAmbientEffectsSettings,
            enter = pushEnter,
            exit = pushExit
        ) {
            ui.settings.AmbientEffectsSettingsScreen(
                viewModel = viewModel,
                onBack = { showAmbientEffectsSettings = false }
            )
        }

        AnimatedVisibility(
            visible = showInternalFolderManagement,
            enter = pushEnter,
            exit = pushExit
        ) {
            BackHandler { showInternalFolderManagement = false }
            LibraryFolderManagementScreen(
                viewModel = viewModel,
                onBack = { showInternalFolderManagement = false }
            )
        }

        AnimatedVisibility(
            visible = showDynamicThemeSettings,
            enter = pushEnter,
            exit = pushExit
        ) {
            DynamicThemeSettingsScreen(
                viewModel = viewModel,
                onBack = { showDynamicThemeSettings = false },
                onOpenPlayer = {
                    showDynamicThemeSettings = false
                    onOpenNowPlaying()
                }
            )
        }

        AnimatedVisibility(
            visible = showPlayerAppearanceHub,
            enter = pushEnter,
            exit = pushExit
        ) {
            PlayerAppearanceHubScreen(
                viewModel = viewModel,
                initialSection = playerAppearanceHubInitialSection,
                onBack = { showPlayerAppearanceHub = false },
                onOpenPlayer = {
                    showPlayerAppearanceHub = false
                    onOpenNowPlaying()
                }
            )
        }

        if (showClearDownloadHistoryDialog) {
            AlertDialog(
                onDismissRequest = { showClearDownloadHistoryDialog = false },
                containerColor = colors.surface,
                title = {
                    Text(
                        text = "Clear Download History?",
                        fontFamily = OutfitFontFamily,
                        fontWeight = FontWeight.Bold,
                        color = colors.textPrimary
                    )
                },
                text = {
                    Text(
                        text = "This clears finished and failed download history entries from Download Manager. Your downloaded audio files will NOT be deleted.",
                        fontFamily = PlusJakartaFontFamily,
                        fontSize = 13.sp,
                        color = colors.textSecondary
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            viewModel.clearDownloadHistory()
                            showClearDownloadHistoryDialog = false
                        },
                        modifier = Modifier.testTag("settings_confirm_clear_history")
                    ) {
                        Text("Clear History", color = colors.accentStrong, fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showClearDownloadHistoryDialog = false }) {
                        Text("Cancel", color = colors.textSecondary)
                    }
                }
            )
        }

        if (showManageDownloadedFilesSheet) {
            val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
            ModalBottomSheet(
                onDismissRequest = { showManageDownloadedFilesSheet = false },
                sheetState = sheetState,
                containerColor = colors.surface,
                contentColor = colors.textPrimary,
                shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 20.dp)
                        .padding(bottom = 24.dp)
                        .testTag("manage_downloaded_files_sheet")
                ) {
                    Text(
                        text = "Manage Downloaded Files",
                        fontFamily = OutfitFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp,
                        color = colors.textPrimary
                    )
                    Text(
                        text = "${downloadedTracks.size} tracks • ${formatByteCount(downloadStorageSummary.totalBytes).ifEmpty { "0 MB" }} in ${downloadStorageSummary.folderLabel}",
                        fontFamily = PlusJakartaFontFamily,
                        fontSize = 12.sp,
                        color = colors.textSecondary
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    if (downloadedTracks.isEmpty()) {
                        Text(
                            text = "No downloaded audio files stored.",
                            fontFamily = PlusJakartaFontFamily,
                            fontSize = 13.sp,
                            color = colors.textSecondary,
                            modifier = Modifier.padding(vertical = 20.dp)
                        )
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 360.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(downloadedTracks, key = { it.id }) { track ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(14.dp))
                                        .background(colors.surfaceTinted)
                                        .padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = track.title,
                                            fontFamily = OutfitFontFamily,
                                            fontWeight = FontWeight.SemiBold,
                                            fontSize = 14.sp,
                                            color = colors.textPrimary,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        val sizeStr = formatByteCount(track.fileSizeBytes)
                                        Text(
                                            text = listOfNotNull(
                                                track.artist,
                                                track.formatLabel,
                                                sizeStr.takeIf { it.isNotEmpty() }
                                            ).joinToString(" • "),
                                            fontFamily = PlusJakartaFontFamily,
                                            fontSize = 11.5.sp,
                                            color = colors.textSecondary,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    IconButton(
                                        onClick = { downloadedTrackPendingDelete = track },
                                        modifier = Modifier.testTag("settings_delete_downloaded_file_${track.id}")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.DeleteOutline,
                                            contentDescription = "Delete ${track.title}",
                                            tint = MaterialTheme.colorScheme.error
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        downloadedTrackPendingDelete?.let { track ->
            AlertDialog(
                onDismissRequest = { downloadedTrackPendingDelete = null },
                containerColor = colors.surface,
                title = {
                    Text(
                        text = "Delete Downloaded File?",
                        fontFamily = OutfitFontFamily,
                        fontWeight = FontWeight.Bold,
                        color = colors.textPrimary
                    )
                },
                text = {
                    Text(
                        text = "Permanently delete \"${track.title}\" from your downloads folder?",
                        fontFamily = PlusJakartaFontFamily,
                        fontSize = 13.sp,
                        color = colors.textSecondary
                    )
                },
                confirmButton = {
                    Button(
                        onClick = {
                            viewModel.deleteDownloadedTracks(listOf(track.id))
                            downloadedTrackPendingDelete = null
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error,
                            contentColor = MaterialTheme.colorScheme.onError
                        )
                    ) {
                        Text("Delete")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { downloadedTrackPendingDelete = null }) {
                        Text("Cancel", color = colors.textSecondary)
                    }
                }
            )
        }

        AnimatedVisibility(
            visible = showInternalDownloadManager,
            enter = pushEnter,
            exit = pushExit
        ) {
            BackHandler { showInternalDownloadManager = false }
            DownloadManagerScreen(
                viewModel = viewModel,
                onBack = { showInternalDownloadManager = false }
            )
        }

        AnimatedVisibility(
            visible = showCacheManagement,
            enter = pushEnter,
            exit = pushExit
        ) {
            CacheManagementScreen(
                viewModel = viewModel,
                onBack = { showCacheManagement = false }
            )
        }

        AnimatedVisibility(
            visible = showBackgroundPlaybackSettings,
            enter = pushEnter,
            exit = pushExit
        ) {
            BackgroundPlaybackSettingsScreen(
                viewModel = viewModel,
                onBack = { showBackgroundPlaybackSettings = false }
            )
        }

        AnimatedVisibility(
            visible = showSeekbarStyleScreen,
            enter = pushEnter,
            exit = pushExit
        ) {
            SeekbarStyleScreen(
                viewModel = viewModel,
                onBack = { showSeekbarStyleScreen = false },
                onOpenPlayer = {
                    showSeekbarStyleScreen = false
                    onOpenNowPlaying()
                }
            )
        }
    }
}

@Composable
private fun ExpandableSettingsSection(
    title: String,
    subtitle: String,
    expanded: Boolean,
    onToggle: () -> Unit,
    colors: EditorialColors,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = colors.surface,
        modifier = Modifier
            .fillMaxWidth()
            .shadow(4.dp, RoundedCornerShape(24.dp), ambientColor = colors.shadowColor, spotColor = colors.shadowColor)
            .border(0.75.dp, colors.cardBorder, RoundedCornerShape(24.dp))
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onToggle() }
                    .padding(horizontal = 18.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        fontFamily = PlusJakartaFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        letterSpacing = 1.2.sp,
                        color = colors.accentStrong
                    )
                    Text(
                        text = subtitle,
                        fontFamily = PlusJakartaFontFamily,
                        fontSize = 12.sp,
                        color = colors.textMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Icon(
                    imageVector = if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                    contentDescription = if (expanded) "Collapse $title" else "Expand $title",
                    tint = colors.textSecondary
                )
            }

            AnimatedVisibility(visible = expanded) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 18.dp, end = 18.dp, bottom = 18.dp),
                    content = content
                )
            }
        }
    }
}

@Composable
private fun SettingsSwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    colors: EditorialColors
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
            Text(
                text = title,
                fontFamily = OutfitFontFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp,
                color = colors.textPrimary
            )
            Text(
                text = subtitle,
                fontFamily = PlusJakartaFontFamily,
                fontSize = 12.sp,
                color = colors.textSecondary
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = colors.onAccentStrong,
                checkedTrackColor = colors.accentStrong
            )
        )
    }
}

@Composable
private fun SettingsLinkRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    colors: EditorialColors,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable { onClick() }
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .background(colors.accentPill, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = colors.onAccentPill, modifier = Modifier.size(20.dp))
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontFamily = OutfitFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                color = colors.textPrimary
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
        Icon(Icons.Rounded.ChevronRight, contentDescription = "Open $title", tint = colors.textMuted)
    }
}

@Composable
private fun <T> GestureOptionChipRow(
    options: List<Pair<T, String>>,
    selected: T,
    onSelect: (T) -> Unit,
    colors: EditorialColors
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        options.forEach { (item, label) ->
            val isSelected = item == selected
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = if (isSelected) colors.accentStrong else colors.surfaceTinted,
                modifier = Modifier
                    .clip(RoundedCornerShape(14.dp))
                    .border(
                        width = if (isSelected) 1.5.dp else 0.75.dp,
                        color = if (isSelected) colors.accentStrong else colors.cardBorder,
                        shape = RoundedCornerShape(14.dp)
                    )
                    .clickable { onSelect(item) }
            ) {
                Text(
                    text = label,
                    fontFamily = PlusJakartaFontFamily,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                    fontSize = 12.sp,
                    color = if (isSelected) colors.onAccentStrong else colors.textPrimary,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                )
            }
        }
    }
}
