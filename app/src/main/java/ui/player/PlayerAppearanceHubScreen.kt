package ui.player

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
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
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.ScreenRotation
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.AnimationIntensity
import com.example.model.ArtworkAnimationOption
import com.example.model.ArtworkSizeOption
import com.example.model.ButtonShapeOption
import com.example.model.ControlArrangement
import com.example.model.ControlButtonSizeOption
import com.example.model.CornerRadiusOption
import com.example.model.LockScreenStyle
import com.example.model.MiniPlayerStyle
import com.example.model.PlaybackInfo
import com.example.model.PlayerBackgroundStyle
import com.example.model.PlayerLayoutOption
import com.example.model.PlayerStyle
import com.example.model.PlayerStyleGroup
import com.example.model.PlayerTypographyOption
import com.example.model.Song
import com.example.model.VisualizerStyle
import com.example.ui.theme.OutfitFontFamily
import com.example.ui.theme.PlusJakartaFontFamily
import com.example.viewmodel.SonoraViewModel
import ui.components.FloatingMiniPlayer
import ui.player.components.MiniPlayerStyleMiniPreview
import ui.player.components.PlayerStyleMiniPreview
import ui.player.seekbar.SeekbarStyle
import ui.theme.LocalDynamicColorPalette
import ui.theme.LocalEditorialColors
import kotlin.math.roundToInt

enum class PlayerAppearanceSection(val id: String, val label: String) {
    NOW_PLAYING("now_playing", "Now Playing Style"),
    MINI_PLAYER("mini_player", "Mini Player Style"),
    ARTWORK("artwork", "Artwork Settings"),
    ANIMATION("animation", "Animation Settings"),
    VISUALIZER("visualizer", "Visualizer Settings"),
    CONTROLS("controls", "Control Customization")
}

private val SAMPLE_PREVIEW_SONG = Song(
    id = -9001L,
    title = "Midnight Resonance",
    artist = "${domain.branding.AppBranding.STUDIO_NAME} Ensemble",
    album = "Velvet Horizons (Preview)",
    albumId = -1L,
    durationMs = 234_000L,
    uri = android.net.Uri.EMPTY,
    dataPath = "",
    mimeType = "audio/flac",
    sizeBytes = 0L,
    isDemoTrack = true,
    bitrateKbps = 960,
    sampleRateHz = 48000,
    bitDepth = 24,
    lyrics = """
        [00:00.00]Soft neon glowing through the midnight glass
        [00:14.00]Every chord echoing across the room
        [00:30.00]Feel the bassline breathing in slow motion
        [00:48.00]MAYUR live preview — real playback stays uninterrupted
        [01:08.00]Tap Apply to make this your Now Playing player
    """.trimIndent()
)

private val SAMPLE_PREVIEW_QUEUE = listOf(
    SAMPLE_PREVIEW_SONG.copy(id = -9000L, title = "Velvet Overture", artist = domain.branding.AppBranding.STUDIO_NAME),
    SAMPLE_PREVIEW_SONG,
    SAMPLE_PREVIEW_SONG.copy(id = -9002L, title = "Aurora Drift", artist = domain.branding.AppBranding.STUDIO_NAME)
)

/**
 * Dedicated Settings → Appearance → Music Player Hub:
 * - 6 structured sections (Now Playing Style, Mini Player Style, Artwork, Animation, Visualizer, Controls)
 * - 30 curated Now Playing styles (+ legacy toggle) with separate Preview & Favorite buttons
 * - 20 Mini Player styles with live interactive Mini Player preview bar & Favorite buttons
 * - Full-screen Live Style Preview overlay using currently playing song (or sample Preview Mode track)
 *   without interrupting real playback, plus Apply, Set as Landscape, and Reset to Default.
 */
@Composable
fun PlayerAppearanceHubScreen(
    viewModel: SonoraViewModel,
    onBack: () -> Unit,
    onOpenPlayer: (() -> Unit)? = null,
    initialSection: PlayerAppearanceSection = PlayerAppearanceSection.NOW_PLAYING,
    modifier: Modifier = Modifier
) {
    val colors = LocalEditorialColors.current
    val palette = LocalDynamicColorPalette.current

    val currentStyle by viewModel.playerStyle.collectAsState()
    val landscapeStyle by viewModel.landscapePlayerStyle.collectAsState()
    val miniPlayerStyle by viewModel.miniPlayerStyle.collectAsState()
    val lockScreenStyle by viewModel.lockScreenStyle.collectAsState()
    val favoriteStyles by viewModel.favoritePlayerStyles.collectAsState()
    val favoriteMiniStyles by viewModel.favoriteMiniPlayerStyles.collectAsState()
    val appearance by viewModel.playerAppearance.collectAsState()
    val gestureConfig by viewModel.playerGestureConfig.collectAsState()
    val seekbarStyle by viewModel.seekbarStyle.collectAsState()
    val visualizerStyle by viewModel.visualizerStyle.collectAsState()

    val currentSong by viewModel.currentSong.collectAsState()
    val playbackInfo by viewModel.playbackInfo.collectAsState()
    val queue by viewModel.queue.collectAsState()
    val queueIndex by viewModel.queueIndex.collectAsState()
    val currentTrackLyrics by viewModel.currentTrackLyrics.collectAsState()
    val visualizerFrequencies by viewModel.visualizerFrequencies.collectAsState()
    val waveformSamples by viewModel.waveformSamples.collectAsState()

    var activeSection by remember(initialSection) { mutableStateOf(initialSection) }
    var selectedGroup by remember { mutableStateOf(PlayerStyleGroup.ALL) }
    var showLegacyStyles by remember { mutableStateOf(true) }
    var livePreviewFullStyle by remember { mutableStateOf<PlayerStyle?>(null) }
    var livePreviewMiniStyle by remember { mutableStateOf<MiniPlayerStyle?>(null) }

    val filteredNowPlayingStyles = remember(selectedGroup, favoriteStyles, showLegacyStyles) {
        val base = if (showLegacyStyles) PlayerStyle.entries else PlayerStyle.entries.filter { it.isFeaturedInExpandedCollection }
        base.filter { it.matchesGroup(selectedGroup, favoriteStyles) }
    }

    val effectiveMiniPreviewSong = currentSong ?: SAMPLE_PREVIEW_SONG
    val effectiveMiniPreviewPlayback = if (currentSong != null) {
        playbackInfo
    } else {
        PlaybackInfo(isPlaying = true, currentPositionMs = 74_000L, durationMs = SAMPLE_PREVIEW_SONG.durationMs)
    }

    BackHandler(enabled = livePreviewFullStyle != null) {
        livePreviewFullStyle = null
    }
    BackHandler(enabled = livePreviewFullStyle == null) {
        onBack()
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(colors.backgroundBrush)
            .statusBarsPadding()
            .navigationBarsPadding()
            .testTag("player_appearance_hub_screen"),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 860.dp)
        ) {
            // Top Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Surface(
                        shape = CircleShape,
                        color = colors.surface,
                        shadowElevation = 2.dp,
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .clickable { onBack() }
                            .testTag("appearance_hub_back_button")
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back", tint = colors.textPrimary)
                        }
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "Music Player",
                            fontFamily = OutfitFontFamily,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 23.sp,
                            color = colors.textPrimary
                        )
                        Text(
                            text = "${PlayerStyle.entries.size} Styles • ${MiniPlayerStyle.entries.size} Mini Players • Live Preview",
                            fontFamily = PlusJakartaFontFamily,
                            fontSize = 11.sp,
                            color = colors.textSecondary
                        )
                    }
                }

                Surface(
                    onClick = { viewModel.resetAllPlayerCustomizationToDefault() },
                    shape = RoundedCornerShape(14.dp),
                    color = colors.surfaceTinted,
                    border = BorderStroke(0.75.dp, colors.cardBorder),
                    modifier = Modifier.testTag("hub_reset_all_button")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Rounded.RestartAlt, contentDescription = "Reset to Default", tint = colors.accentStrong, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Reset",
                            fontFamily = PlusJakartaFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            color = colors.accentStrong
                        )
                    }
                }
            }

            // 6 Dedicated Section Tabs
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                PlayerAppearanceSection.entries.forEach { section ->
                    val selected = section == activeSection
                    Surface(
                        onClick = { activeSection = section },
                        shape = RoundedCornerShape(16.dp),
                        color = if (selected) colors.accentStrong else colors.surface,
                        border = BorderStroke(0.75.dp, if (selected) colors.accentStrong else colors.cardBorder),
                        modifier = Modifier.testTag("hub_section_tab_${section.id}")
                    ) {
                        Text(
                            text = section.label,
                            fontFamily = PlusJakartaFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            color = if (selected) colors.onAccentStrong else colors.textSecondary,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp)
                        )
                    }
                }
            }

            // Section Content
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .testTag("appearance_hub_lazy_column"),
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 10.dp, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                when (activeSection) {
                    PlayerAppearanceSection.NOW_PLAYING -> {
                        // Active Portrait + Landscape summary card
                        item {
                            Surface(
                                shape = RoundedCornerShape(22.dp),
                                color = colors.surface,
                                border = BorderStroke(0.75.dp, colors.cardBorder),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(14.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "ACTIVE STYLES",
                                            fontFamily = PlusJakartaFontFamily,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 10.sp,
                                            letterSpacing = 1.1.sp,
                                            color = colors.accentStrong
                                        )
                                        Text(
                                            text = "Portrait: ${currentStyle.displayName}",
                                            fontFamily = OutfitFontFamily,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 15.sp,
                                            color = colors.textPrimary
                                        )
                                        Text(
                                            text = "Landscape: ${landscapeStyle.displayName}",
                                            fontFamily = PlusJakartaFontFamily,
                                            fontSize = 12.sp,
                                            color = colors.textSecondary
                                        )
                                    }
                                    OutlinedButton(
                                        onClick = { livePreviewFullStyle = currentStyle },
                                        shape = RoundedCornerShape(14.dp),
                                        modifier = Modifier.testTag("hub_preview_active_style_button")
                                    ) {
                                        Icon(Icons.Rounded.Visibility, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Live Preview", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }

                        // Direct Landscape Player Style selector
                        item {
                            HubChipGroupCard(
                                title = "Landscape Player Style",
                                subtitle = "Automatically applied when viewing Now Playing in landscape",
                                options = PlayerStyle.entries.map { it to it.displayName },
                                selected = landscapeStyle,
                                onSelect = { viewModel.setLandscapePlayerStyle(it) },
                                modifier = Modifier.testTag("hub_landscape_style_selector")
                            )
                        }

                        // Group filter chips (All, Favorites, Modern & Premium, Artwork Focused, Immersive & Animated, Compact & Functional)
                        item {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                PlayerStyleGroup.entries.forEach { group ->
                                    val selected = group == selectedGroup
                                    Surface(
                                        onClick = { selectedGroup = group },
                                        shape = RoundedCornerShape(14.dp),
                                        color = if (selected) colors.accentPill else colors.surface,
                                        border = BorderStroke(0.75.dp, if (selected) colors.accentStrong else colors.cardBorder),
                                        modifier = Modifier.testTag("hub_group_chip_${group.id}")
                                    ) {
                                        Text(
                                            text = group.displayName,
                                            fontFamily = PlusJakartaFontFamily,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 11.sp,
                                            color = if (selected) colors.onAccentPill else colors.textSecondary,
                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp)
                                        )
                                    }
                                }
                            }
                        }

                        // 2-Column Style Gallery Cards with separate Preview & Favorite buttons
                        val rows = filteredNowPlayingStyles.chunked(2)
                        if (rows.isEmpty()) {
                            item {
                                Surface(
                                    shape = RoundedCornerShape(20.dp),
                                    color = colors.surface,
                                    border = BorderStroke(0.75.dp, colors.cardBorder),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        text = "No favorite player styles yet. Tap the heart icon on any style card to pin it here.",
                                        fontFamily = PlusJakartaFontFamily,
                                        fontSize = 13.sp,
                                        color = colors.textSecondary,
                                        modifier = Modifier.padding(18.dp)
                                    )
                                }
                            }
                        } else {
                            items(rows.size, key = { idx -> rows[idx].joinToString("_") { it.id } }) { rowIdx ->
                                val rowStyles = rows[rowIdx]
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    rowStyles.forEach { style ->
                                        val isSelected = style == currentStyle
                                        val isFav = favoriteStyles.contains(style.id)
                                        NowPlayingStyleGalleryCard(
                                            style = style,
                                            isSelected = isSelected,
                                            isFavorite = isFav,
                                            song = currentSong,
                                            onSelect = { viewModel.setPlayerStyle(style) },
                                            onPreview = { livePreviewFullStyle = style },
                                            onToggleFavorite = { viewModel.toggleFavoritePlayerStyle(style) },
                                            modifier = Modifier.weight(1f)
                                        )
                                    }
                                    if (rowStyles.size == 1) {
                                        Spacer(modifier = Modifier.weight(1f))
                                    }
                                }
                            }
                        }

                        // Toggle for preserved Legacy SONORA Styles
                        item {
                            HubToggleCard(
                                title = "Show Legacy ${domain.branding.AppBranding.APP_NAME} Styles (+10)",
                                subtitle = "Includes Bento, Dial, Morph, Sticker, Orbit, Hero, Neon, Poster & Gesture",
                                checked = showLegacyStyles,
                                onCheckedChange = { showLegacyStyles = it },
                                modifier = Modifier.testTag("hub_legacy_styles_toggle")
                            )
                        }
                    }

                    PlayerAppearanceSection.MINI_PLAYER -> {
                        // Live Interactive Mini Player Preview Stage
                        item {
                            val activePreviewMini = livePreviewMiniStyle ?: miniPlayerStyle
                            Surface(
                                shape = RoundedCornerShape(22.dp),
                                color = colors.surface,
                                border = BorderStroke(0.75.dp, colors.cardBorder),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.fillMaxWidth().padding(14.dp),
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = if (currentSong == null) "LIVE MINI PLAYER PREVIEW (PREVIEW MODE)" else "LIVE MINI PLAYER PREVIEW",
                                                fontFamily = PlusJakartaFontFamily,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 10.sp,
                                                letterSpacing = 1.sp,
                                                color = colors.accentStrong
                                            )
                                            Text(
                                                text = activePreviewMini.displayName,
                                                fontFamily = OutfitFontFamily,
                                                fontWeight = FontWeight.ExtraBold,
                                                fontSize = 16.sp,
                                                color = colors.textPrimary
                                            )
                                        }
                                        if (livePreviewMiniStyle != null && livePreviewMiniStyle != miniPlayerStyle) {
                                            Button(
                                                onClick = {
                                                    livePreviewMiniStyle?.let { viewModel.setMiniPlayerStyle(it) }
                                                    livePreviewMiniStyle = null
                                                },
                                                shape = RoundedCornerShape(14.dp),
                                                colors = ButtonDefaults.buttonColors(containerColor = colors.accentStrong),
                                                modifier = Modifier.testTag("apply_previewed_mini_style_button")
                                            ) {
                                                Text("Apply", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                            }
                                        }
                                    }

                                    FloatingMiniPlayer(
                                        song = effectiveMiniPreviewSong,
                                        playbackInfo = effectiveMiniPreviewPlayback,
                                        colors = colors,
                                        onTogglePlayPause = { if (currentSong != null) viewModel.togglePlayPause() },
                                        onPlayNext = { if (currentSong != null) viewModel.playNext() },
                                        onPlayPrevious = { if (currentSong != null) viewModel.playPrevious() },
                                        onSeekTo = { targetMs ->
                                            if (currentSong != null) viewModel.seekTo(targetMs)
                                        },
                                        onClick = { livePreviewFullStyle = currentStyle },
                                        miniPlayerStyle = activePreviewMini,
                                        frequencies = visualizerFrequencies,
                                        queue = if (queue.isNotEmpty()) queue else SAMPLE_PREVIEW_QUEUE,
                                        queueIndex = if (queue.isNotEmpty()) queueIndex else 1
                                    )
                                }
                            }
                        }

                        // 20 Mini Player Style Cards with separate Preview & Favorite buttons
                        val miniRows = MiniPlayerStyle.entries.chunked(2)
                        items(miniRows.size, key = { idx -> "mini_row_$idx" }) { rowIdx ->
                            val rowItems = miniRows[rowIdx]
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                rowItems.forEach { variant ->
                                    val isSelected = variant == miniPlayerStyle
                                    val isFav = favoriteMiniStyles.contains(variant.id)
                                    Surface(
                                        shape = RoundedCornerShape(20.dp),
                                        color = if (isSelected) colors.accentPill.copy(alpha = 0.36f) else colors.surface,
                                        border = BorderStroke(if (isSelected) 1.6.dp else 0.75.dp, if (isSelected) colors.accentStrong else colors.cardBorder),
                                        modifier = Modifier
                                            .weight(1f)
                                            .clip(RoundedCornerShape(20.dp))
                                            .clickable {
                                                viewModel.setMiniPlayerStyle(variant)
                                                livePreviewMiniStyle = null
                                            }
                                            .testTag("hub_mini_style_card_${variant.id}")
                                    ) {
                                        Column(
                                            modifier = Modifier.fillMaxWidth().padding(10.dp),
                                            verticalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            MiniPlayerStyleMiniPreview(
                                                style = variant,
                                                isSelected = isSelected,
                                                song = currentSong
                                            )
                                            Text(
                                                text = variant.displayName,
                                                fontFamily = OutfitFontFamily,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 13.sp,
                                                color = if (isSelected) colors.accentStrong else colors.textPrimary,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Text(
                                                text = variant.subtitle,
                                                fontFamily = PlusJakartaFontFamily,
                                                fontSize = 10.sp,
                                                color = colors.textSecondary,
                                                maxLines = 2,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Surface(
                                                    onClick = { livePreviewMiniStyle = variant },
                                                    shape = RoundedCornerShape(10.dp),
                                                    color = colors.surfaceTinted,
                                                    modifier = Modifier.testTag("preview_mini_style_${variant.id}")
                                                ) {
                                                    Row(
                                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                                                        verticalAlignment = Alignment.CenterVertically
                                                    ) {
                                                        Icon(Icons.Rounded.Visibility, contentDescription = "Preview", tint = colors.accentStrong, modifier = Modifier.size(13.dp))
                                                        Spacer(modifier = Modifier.width(4.dp))
                                                        Text("Preview", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = colors.textPrimary)
                                                    }
                                                }
                                                IconButton(
                                                    onClick = { viewModel.toggleFavoriteMiniPlayerStyle(variant) },
                                                    modifier = Modifier.size(36.dp).testTag("favorite_mini_style_${variant.id}")
                                                ) {
                                                    Icon(
                                                        imageVector = if (isFav) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                                                        contentDescription = "Favorite Mini Style",
                                                        tint = if (isFav) colors.accentStrong else colors.textMuted,
                                                        modifier = Modifier.size(18.dp)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                                if (rowItems.size == 1) Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                    }

                    PlayerAppearanceSection.ARTWORK -> {
                        item {
                            HubChipGroupCard(
                                title = "Player Layout",
                                subtitle = "Override artwork & controls arrangement across styles",
                                options = PlayerLayoutOption.entries.map { it to it.label },
                                selected = appearance.layoutOption,
                                onSelect = { opt -> viewModel.updatePlayerAppearance { it.copy(layoutOption = opt) } }
                            )
                        }
                        item {
                            HubChipGroupCard(
                                title = "Artwork Size",
                                subtitle = "Scale the primary album artwork container",
                                options = ArtworkSizeOption.entries.map { it to it.label },
                                selected = appearance.artworkSize,
                                onSelect = { opt -> viewModel.updatePlayerAppearance { it.copy(artworkSize = opt) } }
                            )
                        }
                        item {
                            HubChipGroupCard(
                                title = "Artwork Corner Radius",
                                subtitle = "Sharp square, soft rounded, or circular album frame",
                                options = CornerRadiusOption.entries.map { it to it.label },
                                selected = appearance.cornerRadius,
                                onSelect = { opt -> viewModel.updatePlayerAppearance { it.copy(cornerRadius = opt) } }
                            )
                        }
                        item {
                            HubChipGroupCard(
                                title = "Backdrop Atmosphere",
                                subtitle = "Global background layer behind Now Playing styles",
                                options = PlayerBackgroundStyle.entries.map { it to it.label },
                                selected = appearance.backgroundStyle,
                                onSelect = { opt -> viewModel.updatePlayerAppearance { it.copy(backgroundStyle = opt) } }
                            )
                        }
                        item {
                            HubSliderCard(
                                title = "Background Blur Intensity",
                                valueLabel = "${(appearance.blurIntensity * 100).roundToInt()}%",
                                value = appearance.blurIntensity,
                                valueRange = 0f..1f,
                                onValueChange = { v -> viewModel.updatePlayerAppearance { it.copy(blurIntensity = v) } }
                            )
                        }
                        item {
                            HubSliderCard(
                                title = "Gradient Intensity",
                                valueLabel = "${(appearance.gradientIntensity * 100).roundToInt()}%",
                                value = appearance.gradientIntensity,
                                valueRange = 0f..1f,
                                onValueChange = { v -> viewModel.updatePlayerAppearance { it.copy(gradientIntensity = v) } }
                            )
                        }
                        item {
                            HubSliderCard(
                                title = "Dynamic Album Color Intensity",
                                valueLabel = "${(appearance.dynamicColorIntensity * 100).roundToInt()}%",
                                value = appearance.dynamicColorIntensity,
                                valueRange = 0f..1f,
                                onValueChange = { v -> viewModel.updatePlayerAppearance { it.copy(dynamicColorIntensity = v) } }
                            )
                        }
                        item {
                            HubToggleCard(
                                title = "Dynamic Album Accent",
                                subtitle = "Extract accent colors directly from the active song artwork",
                                checked = appearance.useDynamicAlbumAccent,
                                onCheckedChange = { checked -> viewModel.updatePlayerAppearance { it.copy(useDynamicAlbumAccent = checked) } }
                            )
                        }
                    }

                    PlayerAppearanceSection.ANIMATION -> {
                        item {
                            HubChipGroupCard(
                                title = "Animation Intensity",
                                subtitle = "Control motion amplitude or set Off for maximum battery life",
                                options = AnimationIntensity.entries.map { it to it.label },
                                selected = appearance.animationIntensity,
                                onSelect = { opt -> viewModel.updatePlayerAppearance { it.copy(animationIntensity = opt) } }
                            )
                        }
                        item {
                            HubChipGroupCard(
                                title = "Album Artwork Animation",
                                subtitle = "Motion applied to album artwork while playing",
                                options = ArtworkAnimationOption.entries.map { it to it.label },
                                selected = appearance.artworkAnimation,
                                onSelect = { opt -> viewModel.updatePlayerAppearance { it.copy(artworkAnimation = opt) } }
                            )
                        }
                    }

                    PlayerAppearanceSection.VISUALIZER -> {
                        item {
                            HubChipGroupCard(
                                title = "Audio Visualizer Type",
                                subtitle = "Choose waveform, frequency bars, circular rings, or spectrum",
                                options = VisualizerStyle.entries.map { style ->
                                    style to style.name.replace('_', ' ').lowercase().replaceFirstChar { it.uppercase() }
                                },
                                selected = visualizerStyle,
                                onSelect = { viewModel.setVisualizerStyle(it) }
                            )
                        }
                        item {
                            Surface(
                                shape = RoundedCornerShape(20.dp),
                                color = colors.surface,
                                border = BorderStroke(0.75.dp, colors.cardBorder),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "Preview Immersive Audio Styles",
                                            fontFamily = OutfitFontFamily,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 15.sp,
                                            color = colors.textPrimary
                                        )
                                        Text(
                                            text = "Visualizers pause automatically when paused or off-screen to preserve battery.",
                                            fontFamily = PlusJakartaFontFamily,
                                            fontSize = 12.sp,
                                            color = colors.textSecondary
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Button(
                                        onClick = { livePreviewFullStyle = PlayerStyle.AUDIO_VISUALIZER },
                                        shape = RoundedCornerShape(14.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = colors.accentStrong)
                                    ) {
                                        Text("Preview", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }

                    PlayerAppearanceSection.CONTROLS -> {
                        item {
                            HubChipGroupCard(
                                title = "Control Button Shape",
                                subtitle = "Primary Play/Pause button geometry",
                                options = ButtonShapeOption.entries.map { it to it.label },
                                selected = appearance.buttonShape,
                                onSelect = { opt -> viewModel.updatePlayerAppearance { it.copy(buttonShape = opt) } }
                            )
                        }
                        item {
                            HubChipGroupCard(
                                title = "Control Button Size",
                                subtitle = "Scale transport buttons for one-handed or large-screen reach",
                                options = ControlButtonSizeOption.entries.map { it to it.label },
                                selected = appearance.buttonSize,
                                onSelect = { opt -> viewModel.updatePlayerAppearance { it.copy(buttonSize = opt) } }
                            )
                        }
                        item {
                            HubChipGroupCard(
                                title = "Control Arrangement",
                                subtitle = "Horizontal spacing of transport icons",
                                options = ControlArrangement.entries.map { it to it.label },
                                selected = appearance.controlArrangement,
                                onSelect = { opt -> viewModel.updatePlayerAppearance { it.copy(controlArrangement = opt) } }
                            )
                        }
                        item {
                            HubChipGroupCard(
                                title = "Player Typography",
                                subtitle = "Font family & display character for track titles",
                                options = PlayerTypographyOption.entries.map { it to it.label },
                                selected = appearance.typographyOption,
                                onSelect = { opt -> viewModel.updatePlayerAppearance { it.copy(typographyOption = opt) } }
                            )
                        }
                        item {
                            HubSliderCard(
                                title = "Typography Scale",
                                valueLabel = "${(appearance.typographyScale * 100).roundToInt()}%",
                                value = appearance.typographyScale,
                                valueRange = 0.85f..1.25f,
                                onValueChange = { v -> viewModel.updatePlayerAppearance { it.copy(typographyScale = v) } }
                            )
                        }
                        item {
                            HubChipGroupCard(
                                title = "Progress Bar Style",
                                subtitle = "Choose from ${domain.branding.AppBranding.APP_NAME}'s custom interactive seekbar designs",
                                options = SeekbarStyle.entries.map { it to it.displayName },
                                selected = seekbarStyle,
                                onSelect = { viewModel.setSeekbarStyle(it) }
                            )
                        }
                        item {
                            HubSliderCard(
                                title = "Progress Bar Thickness",
                                valueLabel = "${(appearance.seekbarThicknessScale * 100).roundToInt()}%",
                                value = appearance.seekbarThicknessScale,
                                valueRange = 0.75f..1.6f,
                                onValueChange = { v -> viewModel.updatePlayerAppearance { it.copy(seekbarThicknessScale = v) } }
                            )
                        }
                        item {
                            HubToggleCard(
                                title = "Show Album Name",
                                subtitle = "Display album title next to artist name",
                                checked = appearance.showAlbumMetadata,
                                onCheckedChange = { c -> viewModel.updatePlayerAppearance { it.copy(showAlbumMetadata = c) } }
                            )
                        }
                        item {
                            HubToggleCard(
                                title = "Show Audio Format Badge",
                                subtitle = "Show FLAC / Hi-Res / codec badge in Now Playing",
                                checked = appearance.showAudioFormatMetadata,
                                onCheckedChange = { c -> viewModel.updatePlayerAppearance { it.copy(showAudioFormatMetadata = c) } }
                            )
                        }
                        item {
                            HubToggleCard(
                                title = "Show Lyrics Shortcut",
                                subtitle = "Keep synced lyrics button accessible inside Now Playing",
                                checked = appearance.showLyricsShortcut,
                                onCheckedChange = { c ->
                                    viewModel.updatePlayerAppearance { it.copy(showLyricsShortcut = c) }
                                }
                            )
                        }
                        item {
                            HubToggleCard(
                                title = "Show Queue Shortcut",
                                subtitle = "Keep quick queue button accessible inside Now Playing",
                                checked = appearance.showQueueShortcut,
                                onCheckedChange = { c ->
                                    viewModel.updatePlayerAppearance { it.copy(showQueueShortcut = c) }
                                }
                            )
                        }
                        item {
                            HubChipGroupCard(
                                title = "Lock-Screen Media Style",
                                subtitle = "Customize notification and lock-screen player presentation",
                                options = LockScreenStyle.entries.map { it to it.displayName },
                                selected = lockScreenStyle,
                                onSelect = {
                                    viewModel.setLockScreenStyle(it)
                                    viewModel.syncNotification()
                                }
                            )
                        }
                    }
                }
            }
        }

        // Full-Screen Interactive Live Style Preview Modal (Never Interrupts Playback)
        AnimatedVisibility(
            visible = livePreviewFullStyle != null,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            livePreviewFullStyle?.let { targetPreviewStyle ->
                val isSampleMode = currentSong == null
                var sampleIsPlaying by remember(targetPreviewStyle) { mutableStateOf(true) }
                var samplePosMs by remember(targetPreviewStyle) { mutableLongStateOf(48_000L) }
                val previewGestureController = rememberPlayerGestureController()

                val previewState = remember(
                    targetPreviewStyle,
                    currentSong,
                    playbackInfo,
                    sampleIsPlaying,
                    samplePosMs,
                    seekbarStyle,
                    appearance,
                    gestureConfig,
                    palette,
                    visualizerFrequencies,
                    waveformSamples,
                    visualizerStyle,
                    queue,
                    queueIndex,
                    currentTrackLyrics
                ) {
                    PlayerRenderState(
                        song = currentSong ?: SAMPLE_PREVIEW_SONG,
                        playbackInfo = if (isSampleMode) {
                            PlaybackInfo(
                                isPlaying = sampleIsPlaying,
                                currentPositionMs = samplePosMs,
                                durationMs = SAMPLE_PREVIEW_SONG.durationMs
                            )
                        } else {
                            playbackInfo
                        },
                        seekbarStyle = seekbarStyle,
                        appearance = appearance,
                        gestureConfig = gestureConfig,
                        dynamicPalette = palette,
                        frequencies = visualizerFrequencies,
                        waveformSamples = waveformSamples,
                        isVisualizerActive = false,
                        visualizerStyle = visualizerStyle,
                        isNowPlayingActive = true,
                        queue = if (isSampleMode) SAMPLE_PREVIEW_QUEUE else queue,
                        queueIndex = if (isSampleMode) 1 else queueIndex,
                        rawLyrics = if (isSampleMode) SAMPLE_PREVIEW_SONG.lyrics else currentTrackLyrics
                    )
                }

                val previewCallbacks = remember(isSampleMode, targetPreviewStyle) {
                    PlayerStyleCallbacks(
                        onTogglePlayPause = {
                            if (isSampleMode) sampleIsPlaying = !sampleIsPlaying
                            else viewModel.togglePlayPause()
                        },
                        onPlayNext = { if (!isSampleMode) viewModel.playNext() },
                        onPlayPrevious = { if (!isSampleMode) viewModel.playPrevious() },
                        onSeekTo = { pos ->
                            if (isSampleMode) samplePosMs = pos else viewModel.seekTo(pos)
                        },
                        onToggleShuffle = { if (!isSampleMode) viewModel.toggleShuffle() },
                        onCycleRepeat = { if (!isSampleMode) viewModel.cycleRepeatMode() },
                        onOpenQueue = {},
                        onOpenLyrics = {},
                        onOpenSleepTimer = {},
                        onClose = { livePreviewFullStyle = null },
                        onArtworkLongPress = {},
                        onToggleFavorite = { viewModel.toggleFavoritePlayerStyle(targetPreviewStyle) },
                        onOpenAudioEffects = {},
                        onOpenEqualizer = {},
                        onOpenOutputDevice = {},
                        onToggleFocusMode = {},
                        onOpenCustomization = {},
                        onCyclePlayerStyle = {},
                        onToggleVisualizer = {},
                        onCycleVisualizerStyle = {}
                    )
                }

                val isFav = favoriteStyles.contains(targetPreviewStyle.id)
                val isAppliedPortrait = targetPreviewStyle == currentStyle
                val isAppliedLandscape = targetPreviewStyle == landscapeStyle

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(colors.background)
                        .testTag("live_style_preview_overlay")
                ) {
                    androidx.compose.runtime.CompositionLocalProvider(
                        ui.player.seekbar.LocalPlaybackSeekCapability provides ui.player.seekbar.PlaybackSeekCapability(
                            isSeekable = previewState.playbackInfo.isSeekable,
                            isLiveStream = previewState.playbackInfo.isLiveStream
                        )
                    ) {
                        PlayerStyleRenderer(
                            style = targetPreviewStyle,
                            state = previewState,
                            callbacks = previewCallbacks,
                            gestureController = previewGestureController,
                            respectLayoutOverride = false,
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(bottom = 84.dp)
                        )
                    }

                    // Top Preview Mode Pill
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = Color(0xCC11131B),
                        border = BorderStroke(0.75.dp, Color.White.copy(alpha = 0.25f)),
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(top = 8.dp)
                            .testTag("live_preview_mode_banner")
                    ) {
                        Text(
                            text = if (isSampleMode) {
                                "Preview Mode • ${targetPreviewStyle.displayName}"
                            } else {
                                "Live Preview • ${targetPreviewStyle.displayName}"
                            },
                            fontFamily = PlusJakartaFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            color = Color.White,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                        )
                    }

                    // Bottom Action Bar: Close, Favorite, Set Landscape, Apply
                    Surface(
                        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
                        color = colors.surface.copy(alpha = 0.96f),
                        border = BorderStroke(0.75.dp, colors.cardBorder),
                        shadowElevation = 12.dp,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            IconButton(
                                onClick = { livePreviewFullStyle = null },
                                modifier = Modifier.size(48.dp).testTag("live_preview_close_button")
                            ) {
                                Icon(Icons.Rounded.Close, contentDescription = "Close Preview", tint = colors.textPrimary)
                            }
                            IconButton(
                                onClick = { viewModel.toggleFavoritePlayerStyle(targetPreviewStyle) },
                                modifier = Modifier.size(48.dp).testTag("live_preview_favorite_button")
                            ) {
                                Icon(
                                    imageVector = if (isFav) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                                    contentDescription = "Favorite Style",
                                    tint = if (isFav) colors.accentStrong else colors.textSecondary
                                )
                            }
                            OutlinedButton(
                                onClick = { viewModel.setLandscapePlayerStyle(targetPreviewStyle) },
                                shape = RoundedCornerShape(14.dp),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 10.dp),
                                modifier = Modifier.weight(1f).testTag("live_preview_apply_landscape_button")
                            ) {
                                Icon(Icons.Rounded.ScreenRotation, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = if (isAppliedLandscape) "Landscape ✓" else "Landscape",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1
                                )
                            }
                            Button(
                                onClick = {
                                    viewModel.setPlayerStyle(targetPreviewStyle)
                                    livePreviewFullStyle = null
                                },
                                shape = RoundedCornerShape(14.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = colors.accentStrong,
                                    contentColor = colors.onAccentStrong
                                ),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                                modifier = Modifier.weight(1.1f).testTag("live_preview_apply_button")
                            ) {
                                Text(
                                    text = if (isAppliedPortrait) "Applied ✓" else "Apply Style",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun NowPlayingStyleGalleryCard(
    style: PlayerStyle,
    isSelected: Boolean,
    isFavorite: Boolean,
    song: Song?,
    onSelect: () -> Unit,
    onPreview: () -> Unit,
    onToggleFavorite: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalEditorialColors.current
    Surface(
        shape = RoundedCornerShape(22.dp),
        color = if (isSelected) colors.accentPill.copy(alpha = 0.36f) else colors.surface,
        border = BorderStroke(
            width = if (isSelected) 1.75.dp else 0.75.dp,
            color = if (isSelected) colors.accentStrong else colors.cardBorder
        ),
        modifier = modifier
            .clip(RoundedCornerShape(22.dp))
            .clickable { onSelect() }
            .testTag("hub_player_style_card_${style.id}")
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(modifier = Modifier.fillMaxWidth()) {
                PlayerStyleMiniPreview(
                    style = style,
                    isSelected = isSelected,
                    song = song,
                    modifier = Modifier.fillMaxWidth().aspectRatio(1.12f)
                )
                if (isSelected) {
                    Surface(
                        shape = CircleShape,
                        color = colors.surface,
                        modifier = Modifier.align(Alignment.TopEnd).padding(5.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.CheckCircle,
                            contentDescription = "Applied",
                            tint = colors.accentStrong,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = style.displayName,
                    fontFamily = OutfitFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = if (isSelected) colors.accentStrong else colors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = style.description,
                    fontFamily = PlusJakartaFontFamily,
                    fontSize = 10.sp,
                    lineHeight = 13.sp,
                    color = colors.textSecondary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // Dedicated Preview Button + Favorite Heart Toggle
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    onClick = onPreview,
                    shape = RoundedCornerShape(10.dp),
                    color = colors.surfaceTinted,
                    border = BorderStroke(0.75.dp, colors.cardBorder),
                    modifier = Modifier.testTag("preview_style_${style.id}")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Visibility,
                            contentDescription = "Preview ${style.displayName}",
                            tint = colors.accentStrong,
                            modifier = Modifier.size(13.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Preview",
                            fontFamily = PlusJakartaFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 10.sp,
                            color = colors.textPrimary
                        )
                    }
                }

                IconButton(
                    onClick = onToggleFavorite,
                    modifier = Modifier.size(36.dp).testTag("favorite_style_${style.id}")
                ) {
                    Icon(
                        imageVector = if (isFavorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                        contentDescription = if (isFavorite) "Remove Favorite" else "Add Favorite",
                        tint = if (isFavorite) colors.accentStrong else colors.textMuted,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun <T> HubChipGroupCard(
    title: String,
    subtitle: String,
    options: List<Pair<T, String>>,
    selected: T,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalEditorialColors.current
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = colors.surface,
        border = BorderStroke(0.75.dp, colors.cardBorder),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = title,
                fontFamily = OutfitFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                color = colors.textPrimary
            )
            Text(
                text = subtitle,
                fontFamily = PlusJakartaFontFamily,
                fontSize = 12.sp,
                color = colors.textSecondary
            )
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                options.forEach { (value, label) ->
                    val isSel = value == selected
                    Surface(
                        onClick = { onSelect(value) },
                        shape = RoundedCornerShape(14.dp),
                        color = if (isSel) colors.accentStrong else colors.surfaceTinted,
                        border = BorderStroke(0.75.dp, if (isSel) colors.accentStrong else colors.cardBorder)
                    ) {
                        Text(
                            text = label,
                            fontFamily = PlusJakartaFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            color = if (isSel) colors.onAccentStrong else colors.textPrimary,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HubSliderCard(
    title: String,
    valueLabel: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit
) {
    val colors = LocalEditorialColors.current
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = colors.surface,
        border = BorderStroke(0.75.dp, colors.cardBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    fontFamily = OutfitFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = colors.textPrimary
                )
                Text(
                    text = valueLabel,
                    fontFamily = PlusJakartaFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    color = colors.accentStrong
                )
            }
            Slider(
                value = value.coerceIn(valueRange.start, valueRange.endInclusive),
                onValueChange = onValueChange,
                valueRange = valueRange,
                colors = SliderDefaults.colors(
                    thumbColor = colors.accentStrong,
                    activeTrackColor = colors.accentStrong,
                    inactiveTrackColor = colors.cardBorder
                )
            )
        }
    }
}

@Composable
private fun HubToggleCard(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalEditorialColors.current
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = colors.surface,
        border = BorderStroke(0.75.dp, colors.cardBorder),
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
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
                    fontSize = 11.sp,
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
}
