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
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Balance
import androidx.compose.material.icons.rounded.BluetoothConnected
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.FolderSpecial
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.LinearScale
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.PlayCircleOutline
import androidx.compose.material.icons.rounded.Policy
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.SpatialAudio
import androidx.compose.material.icons.rounded.Source
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.example.model.MiniPlayerStyle
import com.example.model.SingleTapAction
import com.example.model.SwipeAction
import com.example.ui.theme.OutfitFontFamily
import com.example.ui.theme.PlusJakartaFontFamily
import com.example.viewmodel.SonoraViewModel
import ui.player.PlayerCustomizationScreen
import ui.player.PlayerStyleSelector
import ui.settings.DynamicThemeSettingsScreen
import ui.settings.LibraryFolderManagementScreen
import ui.settings.SeekbarStyleScreen
import ui.theme.EditorialAccentSwatches
import ui.theme.EditorialColors
import ui.theme.HomeThemeMode
import ui.theme.LocalEditorialColors
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun SettingsScreen(
    viewModel: SonoraViewModel,
    onOpenNowPlaying: () -> Unit = {},
    onOpenEqualizer: () -> Unit = {},
    onOpenAudioStudio: () -> Unit = {},
    onOpenFolderManagement: (() -> Unit)? = null,
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

    var showPlayerStyleSelector by remember { mutableStateOf(false) }
    var showPlayerCustomization by remember { mutableStateOf(false) }
    var showSeekbarStyleScreen by remember { mutableStateOf(false) }
    var showDynamicThemeSettings by remember { mutableStateOf(false) }
    var showInternalFolderManagement by remember { mutableStateOf(false) }
    var showPrivacyDialog by remember { mutableStateOf(false) }
    var showLicensesDialog by remember { mutableStateOf(false) }
    var rescanRequested by remember { mutableStateOf(false) }

    var appearanceExpanded by remember { mutableStateOf(true) }
    var playbackExpanded by remember { mutableStateOf(true) }
    var audioExpanded by remember { mutableStateOf(true) }
    var libraryExpanded by remember { mutableStateOf(true) }
    var notificationsExpanded by remember { mutableStateOf(true) }
    var aboutExpanded by remember { mutableStateOf(true) }

    if (showInternalFolderManagement) {
        BackHandler { showInternalFolderManagement = false }
        LibraryFolderManagementScreen(
            viewModel = viewModel,
            onBack = { showInternalFolderManagement = false }
        )
        return
    }
    if (showDynamicThemeSettings) {
        DynamicThemeSettingsScreen(
            viewModel = viewModel,
            onBack = { showDynamicThemeSettings = false },
            onOpenPlayer = {
                showDynamicThemeSettings = false
                onOpenNowPlaying()
            }
        )
        return
    }
    if (showPlayerCustomization) {
        PlayerCustomizationScreen(
            viewModel = viewModel,
            onBack = { showPlayerCustomization = false },
            onOpenPlayer = {
                showPlayerCustomization = false
                onOpenNowPlaying()
            }
        )
        return
    }
    if (showPlayerStyleSelector) {
        PlayerStyleSelector(
            viewModel = viewModel,
            onBack = { showPlayerStyleSelector = false },
            onOpenPlayer = {
                showPlayerStyleSelector = false
                onOpenNowPlaying()
            }
        )
        return
    }
    if (showSeekbarStyleScreen) {
        SeekbarStyleScreen(
            viewModel = viewModel,
            onBack = { showSeekbarStyleScreen = false },
            onOpenPlayer = {
                showSeekbarStyleScreen = false
                onOpenNowPlaying()
            }
        )
        return
    }

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
                modifier = Modifier.fillMaxSize(),
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
                            title = "Dynamic Colours & Modes",
                            subtitle = if (dynamicColorsEnabled) "${dynamicAppearanceMode.displayName} • ${dynamicColorIntensity.displayName}" else "Off • ${dynamicThemeMode.displayName}",
                            colors = colors,
                            onClick = { showDynamicThemeSettings = true }
                        )

                        SettingsLinkRow(
                            icon = Icons.Rounded.Palette,
                            title = "Player Style",
                            subtitle = "${playerStyle.displayName} • 19 layouts",
                            colors = colors,
                            onClick = { showPlayerStyleSelector = true }
                        )

                        SettingsLinkRow(
                            icon = Icons.Rounded.Tune,
                            title = "Player Appearance & Layout",
                            subtitle = "Artwork size, corners, backdrop & button shape",
                            colors = colors,
                            onClick = { showPlayerCustomization = true }
                        )

                        SettingsLinkRow(
                            icon = Icons.Rounded.LinearScale,
                            title = "Seekbar Style",
                            subtitle = "${seekbarStyle.displayName} • 10 styles",
                            colors = colors,
                            onClick = { showSeekbarStyleScreen = true }
                        )

                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Mini-Player Style",
                            fontFamily = OutfitFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = colors.textPrimary
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        GestureOptionChipRow(
                            options = MiniPlayerStyle.entries.map { it to it.displayName },
                            selected = miniPlayerStyle,
                            onSelect = { viewModel.setMiniPlayerStyle(it) },
                            colors = colors
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

                // 5. NOTIFICATIONS
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
                        subtitle = "v1.0.0 Pro",
                        expanded = aboutExpanded,
                        onToggle = { aboutExpanded = !aboutExpanded },
                        colors = colors
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .background(colors.accentStrong, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "S",
                                    fontFamily = OutfitFontFamily,
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 22.sp,
                                    color = colors.onAccentStrong
                                )
                            }
                            Spacer(modifier = Modifier.width(14.dp))
                            Column {
                                Text(
                                    text = "SONORA Offline Player",
                                    fontFamily = OutfitFontFamily,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp,
                                    color = colors.textPrimary
                                )
                                Text(
                                    text = "Version 1.0.0 Pro • 100% Local Audio",
                                    fontFamily = PlusJakartaFontFamily,
                                    fontSize = 12.sp,
                                    color = colors.accentStrong
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))
                        HorizontalDivider(color = colors.cardBorder)
                        Spacer(modifier = Modifier.height(6.dp))

                        SettingsLinkRow(
                            icon = Icons.Rounded.Policy,
                            title = "Privacy Policy",
                            subtitle = "Zero telemetry • All files remain offline on device",
                            colors = colors,
                            onClick = { showPrivacyDialog = true }
                        )
                        SettingsLinkRow(
                            icon = Icons.Rounded.Source,
                            title = "Open-Source Libraries",
                            subtitle = "Jetpack Compose, Material 3, Room & AndroidX Media",
                            colors = colors,
                            onClick = { showLicensesDialog = true }
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
                    text = "SONORA operates 100% offline. No audio files, listening history, playlists, equalizer curves, or folder preferences ever leave your device. Permissions are requested strictly to index and play local audio files and display system media notifications.",
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
                    text = "• AndroidX Core, Activity Compose & Lifecycle (Apache 2.0)\n" +
                        "• Jetpack Compose & Material Design 3 (Apache 2.0)\n" +
                        "• AndroidX Media & MediaStyle NotificationCompat (Apache 2.0)\n" +
                        "• AndroidX Room Database & Preferences DataStore (Apache 2.0)\n" +
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
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
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
