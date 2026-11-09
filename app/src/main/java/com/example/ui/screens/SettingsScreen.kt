package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Balance
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.BluetoothConnected
import androidx.compose.material.icons.filled.ColorLens
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SpatialAudio
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.ThemeMode
import com.example.model.VisualizerStyle
import com.example.ui.theme.SonoraCardBorder
import com.example.ui.theme.SonoraCyan
import com.example.ui.theme.SonoraGold
import com.example.ui.theme.SonoraGoldDark
import com.example.ui.theme.SonoraObsidian
import com.example.ui.theme.SonoraOnGold
import com.example.ui.theme.SonoraSurfaceDark
import com.example.ui.theme.SonoraSurfaceElevated
import com.example.ui.theme.SonoraTextMuted
import com.example.ui.theme.SonoraTextPrimary
import com.example.ui.theme.SonoraTextSecondary
import com.example.viewmodel.SonoraViewModel

import com.example.ui.theme.SonoraDustyRose
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Swipe
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.filled.DoubleArrow
import androidx.compose.material.icons.filled.LinearScale
import ui.player.PlayerStyleSelector
import ui.settings.SeekbarStyleScreen

@Composable
fun SettingsScreen(
    viewModel: SonoraViewModel,
    onNavigateToEqualizer: () -> Unit,
    onOpenNowPlaying: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val themeMode by viewModel.themeMode.collectAsState()
    val playerStyle by viewModel.playerStyle.collectAsState()
    val seekbarStyle by viewModel.seekbarStyle.collectAsState()
    val gaplessEnabled by viewModel.gaplessEnabled.collectAsState()
    val crossfadeSeconds by viewModel.crossfadeSeconds.collectAsState()
    val playbackInfo by viewModel.playbackInfo.collectAsState()
    val allSongs by viewModel.allSongs.collectAsState()
    val spatialConfig by viewModel.spatialConfig.collectAsState()
    val isTwsConnected by viewModel.isTwsConnected.collectAsState()
    val connectedAudioDeviceName by viewModel.connectedAudioDeviceName.collectAsState()

    val gesturesEnabled by viewModel.gesturesEnabled.collectAsState()
    val swipeArtworkToSkip by viewModel.swipeArtworkToSkip.collectAsState()
    val doubleTapToSeek by viewModel.doubleTapToSeek.collectAsState()
    val swipeDownToCollapse by viewModel.swipeDownToCollapse.collectAsState()
    val miniPlayerGestures by viewModel.miniPlayerGestures.collectAsState()

    var showRescanToast by remember { mutableStateOf(false) }
    var showPlayerStyleSelector by remember { mutableStateOf(false) }
    var showSeekbarStyleScreen by remember { mutableStateOf(false) }

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

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("settings_screen"),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 120.dp)
    ) {
        // Header
        item {
            Text(
                text = "Preferences & Studio Settings",
                color = SonoraTextPrimary,
                fontSize = 24.sp,
                fontWeight = FontWeight.ExtraBold,
                modifier = Modifier.padding(bottom = 16.dp)
            )
        }

        // Section: Appearance & Theming
        item {
            SettingsCategoryHeader(title = "APPEARANCE & THEME")

            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp)),
                color = SonoraSurfaceElevated
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, SonoraCardBorder, RoundedCornerShape(18.dp))
                        .padding(14.dp)
                ) {
                    Text(
                        text = "Display Palette",
                        color = SonoraTextPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    val themes = listOf(
                        "AMOLED Black" to ThemeMode.AMOLED_BLACK,
                        "Obsidian Dark" to ThemeMode.DARK,
                        "Studio Light" to ThemeMode.LIGHT,
                        "Dynamic You" to ThemeMode.DYNAMIC_YOU
                    )

                    themes.forEach { (label, mode) ->
                        val isSelected = themeMode == mode
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .clickable { viewModel.setThemeMode(mode) }
                                .padding(vertical = 8.dp, horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(18.dp)
                                    .border(
                                        2.dp,
                                        if (isSelected) SonoraGold else SonoraTextMuted,
                                        CircleShape
                                    )
                                    .padding(3.dp)
                            ) {
                                if (isSelected) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .background(SonoraGold, CircleShape)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = label,
                                color = if (isSelected) SonoraGold else SonoraTextPrimary,
                                fontSize = 13.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Player Style Architecture Selection Card
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .clickable { showPlayerStyleSelector = true },
                color = SonoraSurfaceElevated
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, SonoraDustyRose.copy(alpha = 0.5f), RoundedCornerShape(18.dp))
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = CircleShape,
                        color = SonoraDustyRose.copy(alpha = 0.2f),
                        modifier = Modifier.size(44.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.Default.Palette,
                                contentDescription = null,
                                tint = SonoraDustyRose,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "Player Style",
                                color = SonoraTextPrimary,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = SonoraDustyRose.copy(alpha = 0.25f)
                            ) {
                                Text(
                                    text = playerStyle.displayName,
                                    color = SonoraDustyRose,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                )
                            }
                        }
                        Text(
                            text = "${playerStyle.subtitle} • 9 layouts available",
                            color = SonoraTextSecondary,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }
                    Icon(
                        Icons.Default.ChevronRight,
                        contentDescription = "Open Player Style",
                        tint = SonoraDustyRose,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Seekbar Style Architecture Selection Card
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .clickable { showSeekbarStyleScreen = true },
                color = SonoraSurfaceElevated
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, SonoraDustyRose.copy(alpha = 0.5f), RoundedCornerShape(18.dp))
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = CircleShape,
                        color = SonoraDustyRose.copy(alpha = 0.2f),
                        modifier = Modifier.size(44.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.Default.LinearScale,
                                contentDescription = null,
                                tint = SonoraDustyRose,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "Seekbar Style",
                                color = SonoraTextPrimary,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = SonoraDustyRose.copy(alpha = 0.25f)
                            ) {
                                Text(
                                    text = seekbarStyle.displayName,
                                    color = SonoraDustyRose,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                )
                            }
                        }
                        Text(
                            text = "${seekbarStyle.description} • 9 styles available",
                            color = SonoraTextSecondary,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }
                    Icon(
                        Icons.Default.ChevronRight,
                        contentDescription = "Open Seekbar Style",
                        tint = SonoraDustyRose,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        }

        // Section: Gesture Controls
        item {
            Spacer(modifier = Modifier.height(16.dp))
            SettingsCategoryHeader(title = "GESTURE CONTROLS")

            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp)),
                color = SonoraSurfaceElevated
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, SonoraCardBorder, RoundedCornerShape(18.dp))
                        .padding(14.dp)
                ) {
                    // Master Gesture Toggle
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Enable Gestures", color = SonoraTextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                            Text("Fluid Poweramp-style swipe & tap controls", color = SonoraTextSecondary, fontSize = 12.sp)
                        }
                        Switch(
                            checked = gesturesEnabled,
                            onCheckedChange = { viewModel.toggleGesturesEnabled(it) },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.Black,
                                checkedTrackColor = SonoraGold
                            )
                        )
                    }

                    if (gesturesEnabled) {
                        Spacer(modifier = Modifier.height(14.dp))

                        // Swipe Artwork to Skip
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Swipe Artwork to Skip", color = SonoraTextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                                Text("Swipe left for Next track, right for Previous track", color = SonoraTextSecondary, fontSize = 11.sp)
                            }
                            Switch(
                                checked = swipeArtworkToSkip,
                                onCheckedChange = { viewModel.toggleSwipeArtworkToSkip(it) },
                                colors = SwitchDefaults.colors(checkedThumbColor = SonoraGold)
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Double Tap to Seek
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Double-Tap Artwork to Seek", color = SonoraTextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                                Text("Double-tap left for -10s, right for +10s", color = SonoraTextSecondary, fontSize = 11.sp)
                            }
                            Switch(
                                checked = doubleTapToSeek,
                                onCheckedChange = { viewModel.toggleDoubleTapToSeek(it) },
                                colors = SwitchDefaults.colors(checkedThumbColor = SonoraCyan)
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Swipe Down to Collapse
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Swipe Down to Collapse", color = SonoraTextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                                Text("Swipe down on artwork to close Now Playing screen", color = SonoraTextSecondary, fontSize = 11.sp)
                            }
                            Switch(
                                checked = swipeDownToCollapse,
                                onCheckedChange = { viewModel.toggleSwipeDownToCollapse(it) },
                                colors = SwitchDefaults.colors(checkedThumbColor = SonoraGold)
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Mini Player Swipe Gestures
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Mini Player Gestures", color = SonoraTextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                                Text("Swipe left/right to change tracks, swipe up to expand", color = SonoraTextSecondary, fontSize = 11.sp)
                            }
                            Switch(
                                checked = miniPlayerGestures,
                                onCheckedChange = { viewModel.toggleMiniPlayerGestures(it) },
                                colors = SwitchDefaults.colors(checkedThumbColor = SonoraCyan)
                            )
                        }
                    }
                }
            }
        }

        // Section: Audio Engine Settings
        item {
            Spacer(modifier = Modifier.height(16.dp))
            SettingsCategoryHeader(title = "HI-FI AUDIO ENGINE")

            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp)),
                color = SonoraSurfaceElevated
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, SonoraCardBorder, RoundedCornerShape(18.dp))
                        .padding(14.dp)
                ) {
                    // Gapless Playback Switch
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Gapless Playback", color = SonoraTextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                            Text("Zero latency between sequential tracks", color = SonoraTextSecondary, fontSize = 12.sp)
                        }
                        Switch(
                            checked = gaplessEnabled,
                            onCheckedChange = { viewModel.setGaplessEnabled(it) },
                            colors = SwitchDefaults.colors(checkedThumbColor = SonoraGold)
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Crossfade Duration Slider
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Crossfade Duration", color = SonoraTextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                            Text(
                                if (crossfadeSeconds == 0) "OFF" else "${crossfadeSeconds}s",
                                color = SonoraCyan,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Slider(
                            value = crossfadeSeconds.toFloat(),
                            onValueChange = { viewModel.setCrossfadeSeconds(it.toInt()) },
                            valueRange = 0f..10f,
                            steps = 9,
                            colors = SliderDefaults.colors(thumbColor = SonoraCyan, activeTrackColor = SonoraCyan)
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Automix Seamless Crossfade Switch
                    val automixEnabled by viewModel.automixEnabled.collectAsState()
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Automix Transition", color = SonoraTextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                            Text("Auto-crossfades smoothly as track approaches ending", color = SonoraTextSecondary, fontSize = 12.sp)
                        }
                        Switch(
                            checked = automixEnabled,
                            onCheckedChange = { viewModel.toggleAutomix(it) },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.Black,
                                checkedTrackColor = SonoraCyan
                            )
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Match Next Song Switch
                    val matchNextSongEnabled by viewModel.matchNextSongEnabled.collectAsState()
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Harmonic Track Matching", color = SonoraTextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                            Text("Intelligently picks next track matching genre & artist flow", color = SonoraTextSecondary, fontSize = 12.sp)
                        }
                        Switch(
                            checked = matchNextSongEnabled,
                            onCheckedChange = { viewModel.toggleMatchNextSong(it) },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.Black,
                                checkedTrackColor = SonoraGold
                            )
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Rescan Library Option
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .clickable {
                                viewModel.loadLocalSongs()
                                showRescanToast = true
                            }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = null, tint = SonoraGold)
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text("Rescan Music Storage", color = SonoraTextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                            Text("Scanned ${allSongs.size} audio tracks", color = SonoraTextSecondary, fontSize = 12.sp)
                        }
                    }
                }
            }
        }

        // Section: Album Artwork & Embedded Lyrics
        item {
            Spacer(modifier = Modifier.height(16.dp))
            SettingsCategoryHeader(title = "ALBUM ARTWORK & LYRICS")

            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp)),
                color = SonoraSurfaceElevated
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, SonoraCardBorder, RoundedCornerShape(18.dp))
                        .padding(14.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Palette, contentDescription = null, tint = SonoraGold)
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Embedded Album Art Extraction", color = SonoraTextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                            Text("Automatic extraction from ID3v2 APIC & FLAC blocks with LRU memory caching", color = SonoraTextSecondary, fontSize = 12.sp)
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.GraphicEq, contentDescription = null, tint = SonoraCyan)
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Embedded & Synced Lyrics", color = SonoraTextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                            Text("Reads ID3v2 USLT frames, Vorbis lyrics, and sidecar .lrc files with tap-to-seek", color = SonoraTextSecondary, fontSize = 12.sp)
                        }
                    }
                }
            }
        }

        // Section: Audio Hardware & Headset Behavior
        item {
            Spacer(modifier = Modifier.height(16.dp))
            SettingsCategoryHeader(title = "HEADSET & INTERRUPTIONS")

            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp)),
                color = SonoraSurfaceElevated
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, SonoraCardBorder, RoundedCornerShape(18.dp))
                        .padding(14.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Headphones, contentDescription = null, tint = SonoraGold)
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Pause on Unplug", color = SonoraTextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                            Text("Automatic audio cut when headphones disconnect (Active)", color = SonoraTextSecondary, fontSize = 12.sp)
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.VolumeUp, contentDescription = null, tint = SonoraCyan)
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Audio Focus Management", color = SonoraTextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                            Text("Ducks volume for notifications, pauses for calls (Active)", color = SonoraTextSecondary, fontSize = 12.sp)
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // TWS Ultra-Wide Stereo Switch
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.BluetoothConnected,
                            contentDescription = null,
                            tint = if (isTwsConnected) SonoraCyan else SonoraTextMuted
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("TWS Ultra-Wide Stereo", color = SonoraTextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                            Text(
                                if (isTwsConnected) "Active on $connectedAudioDeviceName • Balanced Dual-Earbud 360°"
                                else "Auto-activates when Bluetooth TWS earbuds connect",
                                color = if (isTwsConnected) SonoraCyan else SonoraTextSecondary,
                                fontSize = 12.sp
                            )
                        }
                        Switch(
                            checked = spatialConfig.twsWideStereoEnabled,
                            onCheckedChange = { viewModel.toggleTwsWideStereo(it) },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.Black,
                                checkedTrackColor = SonoraCyan,
                                uncheckedThumbColor = SonoraTextMuted,
                                uncheckedTrackColor = SonoraSurfaceDark
                            )
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Quick-Fix Dual Earbud Balance
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { viewModel.forceBalanceDualEarbuds() }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Balance, contentDescription = null, tint = SonoraGold)
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Fix Left/Right Earbud Imbalance", color = SonoraTextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                            Text("Forces symmetrical 100% stereo gain & binaural routing to both earbuds", color = SonoraTextSecondary, fontSize = 12.sp)
                        }
                    }
                }
            }
        }

        // Section: About SONORA
        item {
            Spacer(modifier = Modifier.height(16.dp))
            SettingsCategoryHeader(title = "ABOUT SONORA")

            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp)),
                color = SonoraSurfaceElevated
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, SonoraCardBorder, RoundedCornerShape(18.dp))
                        .padding(16.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .background(
                                    androidx.compose.ui.graphics.Brush.linearGradient(
                                        listOf(SonoraGold, SonoraCyan)
                                    ),
                                    CircleShape
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("S", color = SonoraObsidian, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold)
                        }
                        Spacer(modifier = Modifier.width(14.dp))
                        Column {
                            Text("SONORA Hi-Fi Audio", color = SonoraTextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                            Text("Version 1.0.0 Pro • 100% Offline", color = SonoraGold, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Built with AndroidX, Jetpack Compose, Material Design 3, and Poweramp-inspired DSP audio architecture. Supports 360° virtual soundstage, 15-band graphic equalizer, real-time audio visualizers, and offline storage access.",
                        color = SonoraTextSecondary,
                        fontSize = 12.sp,
                        lineHeight = 18.sp
                    )
                }
            }
        }
    }
}

@Composable
fun SettingsCategoryHeader(title: String) {
    Text(
        text = title,
        color = SonoraTextMuted,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.sp,
        modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
    )
}
