package ui.player.styles

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Equalizer
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Lyrics
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.components.ArtworkAlbumView
import com.example.ui.theme.OutfitFontFamily
import com.example.ui.theme.PlusJakartaFontFamily
import domain.lyrics.LrcParser
import ui.components.AdaptiveSeekbar
import ui.components.ArtworkContainer
import ui.components.PlaybackControls
import ui.components.TrackMetadataBar
import ui.player.NowPlayingTopHeader
import ui.player.PlayerGestureController
import ui.player.PlayerRenderState
import ui.player.PlayerStyleCallbacks
import ui.player.PlayerStyleManager
import ui.theme.LocalEditorialColors

/**
 * Group D: Compact & Functional Now Playing Styles
 * - Modern Compact (one-handed ergonomic layout)
 * - Typography Player (bold kinetic track title centerpiece)
 * - Split Layout (two-zone architectural split, adaptive portrait/wide)
 * - Landscape Player (horizontal widescreen layout for landscape & tablets)
 * - Lyrics Focus (real-time synchronized lyrics showcase with tap-to-seek)
 */

@Composable
fun ModernCompactPlayer(
    state: PlayerRenderState,
    callbacks: PlayerStyleCallbacks,
    gestureController: PlayerGestureController,
    modifier: Modifier = Modifier
) {
    val colors = LocalEditorialColors.current
    val palette = state.dynamicPalette
    val song = state.song
    val duration = state.playbackInfo.durationMs.coerceAtLeast(song?.durationMs ?: 0L)
    val accent = PlayerStyleManager.resolveAccentColor(state.appearance, colors, palette)

    Box(
        modifier = modifier
            .fillMaxSize()
            .navigationBarsPadding()
            .testTag("player_style_modern_compact"),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 580.dp)
                .padding(horizontal = 20.dp, vertical = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            NowPlayingTopHeader(
                song = song,
                onClose = callbacks.onClose,
                onOpenQueue = callbacks.onOpenQueue,
                onOpenLyrics = callbacks.onOpenLyrics,
                onOpenSleepTimer = callbacks.onOpenSleepTimer,
                onOpenStyleWheel = callbacks.onArtworkLongPress,
                onToggleVisualizer = callbacks.onToggleVisualizer,
                isVisualizerActive = state.isVisualizerActive,
                onOpenEqualizer = callbacks.onOpenEqualizer,
                onOpenAudioStudio = callbacks.onOpenAudioEffects,
                onOpenOutputDevice = callbacks.onOpenOutputDevice,
                onToggleFocusMode = callbacks.onToggleFocusMode,
                onOpenCustomization = callbacks.onOpenCustomization
            )

            // Compact framed card with horizontal artwork + quick action rail
            Surface(
                shape = RoundedCornerShape(28.dp),
                color = colors.surface,
                border = BorderStroke(0.75.dp, colors.cardBorder),
                shadowElevation = 6.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        ArtworkContainer(
                            song = song,
                            playbackInfo = state.playbackInfo,
                            appearance = state.appearance,
                            gestureConfig = state.gestureConfig,
                            gestureController = gestureController,
                            onTogglePlayPause = callbacks.onTogglePlayPause,
                            onPlayNext = callbacks.onPlayNext,
                            onPlayPrevious = callbacks.onPlayPrevious,
                            onSeekTo = callbacks.onSeekTo,
                            onOpenQueue = callbacks.onOpenQueue,
                            onClose = callbacks.onClose,
                            onArtworkLongPress = callbacks.onArtworkLongPress,
                            onToggleFavorite = callbacks.onToggleFavorite,
                            onCyclePlayerStyle = callbacks.onCyclePlayerStyle,
                            defaultCornerRadius = 18.dp,
                            modifier = Modifier.size(124.dp)
                        )

                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = song?.title ?: "No Track Selected",
                                fontFamily = PlayerStyleManager.resolveTitleFontFamily(state.appearance.typographyOption),
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 19.sp,
                                color = colors.textPrimary,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = song?.artist ?: "Unknown Artist",
                                fontFamily = PlayerStyleManager.resolveBodyFontFamily(state.appearance.typographyOption),
                                fontSize = 13.sp,
                                color = colors.textSecondary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (state.appearance.showAudioFormatMetadata && song != null) {
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = colors.accentPill.copy(alpha = 0.78f)
                                ) {
                                    Text(
                                        text = song.audioFormatInfo,
                                        fontFamily = PlusJakartaFontFamily,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = colors.onAccentPill,
                                        maxLines = 1,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                    )
                                }
                            }
                        }
                    }

                    // Ergonomic thumb-zone quick chips
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        CompactQuickPill(
                            icon = Icons.Rounded.Favorite,
                            label = if (song?.isFavorite == true) "Liked" else "Like",
                            highlighted = song?.isFavorite == true,
                            accent = accent,
                            onClick = callbacks.onToggleFavorite,
                            modifier = Modifier.weight(1f)
                        )
                        CompactQuickPill(
                            icon = Icons.Rounded.Lyrics,
                            label = "Lyrics",
                            highlighted = false,
                            accent = accent,
                            onClick = callbacks.onOpenLyrics,
                            modifier = Modifier.weight(1f)
                        )
                        CompactQuickPill(
                            icon = Icons.AutoMirrored.Rounded.QueueMusic,
                            label = "Queue",
                            highlighted = false,
                            accent = accent,
                            onClick = callbacks.onOpenQueue,
                            modifier = Modifier.weight(1f)
                        )
                        CompactQuickPill(
                            icon = Icons.Rounded.Equalizer,
                            label = "EQ",
                            highlighted = false,
                            accent = accent,
                            onClick = callbacks.onOpenEqualizer,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                AdaptiveSeekbar(
                    currentPositionMs = state.playbackInfo.currentPositionMs,
                    durationMs = duration,
                    onSeekTo = callbacks.onSeekTo,
                    isPlaying = state.playbackInfo.isPlaying,
                    style = state.seekbarStyle,
                    appearance = state.appearance,
                    liveWaveform = state.waveformSamples,
                    hapticFeedbackEnabled = state.gestureConfig.hapticFeedbackEnabled,
                    accentColor = accent
                )

                PlaybackControls(
                    playbackInfo = state.playbackInfo,
                    onTogglePlayPause = callbacks.onTogglePlayPause,
                    onPlayNext = callbacks.onPlayNext,
                    onPlayPrevious = callbacks.onPlayPrevious,
                    onToggleShuffle = callbacks.onToggleShuffle,
                    onCycleRepeat = callbacks.onCycleRepeat,
                    appearance = state.appearance,
                    accentColor = accent,
                    primaryButtonSize = 64.dp
                )
            }
        }
    }
}

@Composable
private fun CompactQuickPill(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    highlighted: Boolean,
    accent: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalEditorialColors.current
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        color = if (highlighted) accent.copy(alpha = 0.18f) else colors.surfaceTinted,
        border = BorderStroke(0.75.dp, if (highlighted) accent else colors.cardBorder),
        modifier = modifier.height(48.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxSize().padding(horizontal = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = if (highlighted) accent else colors.textSecondary,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = label,
                fontFamily = PlusJakartaFontFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = 11.sp,
                color = if (highlighted) accent else colors.textPrimary,
                maxLines = 1
            )
        }
    }
}

@Composable
fun TypographyPlayer(
    state: PlayerRenderState,
    callbacks: PlayerStyleCallbacks,
    gestureController: PlayerGestureController,
    modifier: Modifier = Modifier
) {
    val colors = LocalEditorialColors.current
    val palette = state.dynamicPalette
    val song = state.song
    val duration = state.playbackInfo.durationMs.coerceAtLeast(song?.durationMs ?: 0L)
    val accent = PlayerStyleManager.resolveAccentColor(state.appearance, colors, palette)
    val scale = state.appearance.typographyScale.coerceIn(0.85f, 1.25f)
    val titleFont = PlayerStyleManager.resolveTitleFontFamily(state.appearance.typographyOption)
    val bodyFont = PlayerStyleManager.resolveBodyFontFamily(state.appearance.typographyOption)

    Box(
        modifier = modifier
            .fillMaxSize()
            .navigationBarsPadding()
            .testTag("player_style_typography_player"),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 620.dp)
                .padding(horizontal = 24.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            NowPlayingTopHeader(
                song = song,
                onClose = callbacks.onClose,
                onOpenQueue = callbacks.onOpenQueue,
                onOpenLyrics = callbacks.onOpenLyrics,
                onOpenSleepTimer = callbacks.onOpenSleepTimer,
                onOpenStyleWheel = callbacks.onArtworkLongPress,
                onToggleVisualizer = callbacks.onToggleVisualizer,
                isVisualizerActive = state.isVisualizerActive,
                onOpenEqualizer = callbacks.onOpenEqualizer,
                onOpenAudioStudio = callbacks.onOpenAudioEffects,
                onOpenOutputDevice = callbacks.onOpenOutputDevice,
                onToggleFocusMode = callbacks.onToggleFocusMode,
                onOpenCustomization = callbacks.onOpenCustomization
            )

            // Massive Typography Poster Centerpiece + Corner Artwork Medallion
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                verticalArrangement = Arrangement.Center
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = accent.copy(alpha = 0.16f),
                        border = BorderStroke(0.75.dp, accent.copy(alpha = 0.45f))
                    ) {
                        Text(
                            text = if (state.playbackInfo.isPlaying) "NOW PLAYING" else "PAUSED",
                            fontFamily = PlusJakartaFontFamily,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 10.sp,
                            letterSpacing = 1.6.sp,
                            color = accent,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }

                    ArtworkContainer(
                        song = song,
                        playbackInfo = state.playbackInfo,
                        appearance = state.appearance,
                        gestureConfig = state.gestureConfig,
                        gestureController = gestureController,
                        onTogglePlayPause = callbacks.onTogglePlayPause,
                        onPlayNext = callbacks.onPlayNext,
                        onPlayPrevious = callbacks.onPlayPrevious,
                        onSeekTo = callbacks.onSeekTo,
                        onOpenQueue = callbacks.onOpenQueue,
                        onClose = callbacks.onClose,
                        onArtworkLongPress = callbacks.onArtworkLongPress,
                        onToggleFavorite = callbacks.onToggleFavorite,
                        onCyclePlayerStyle = callbacks.onCyclePlayerStyle,
                        defaultCornerRadius = 18.dp,
                        modifier = Modifier.size(86.dp)
                    )
                }

                Spacer(modifier = Modifier.height(18.dp))

                Text(
                    text = song?.title ?: "SONORA",
                    fontFamily = titleFont,
                    fontWeight = FontWeight.Black,
                    fontSize = (40f * scale).sp,
                    lineHeight = (44f * scale).sp,
                    letterSpacing = (-1.2).sp,
                    color = colors.textPrimary,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = song?.artist ?: "Unknown Artist",
                    fontFamily = bodyFont,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = (20f * scale).sp,
                    color = accent,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                if (state.appearance.showAlbumMetadata && !song?.album.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = song?.album.orEmpty(),
                        fontFamily = bodyFont,
                        fontSize = (14f * scale).sp,
                        color = colors.textSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                AdaptiveSeekbar(
                    currentPositionMs = state.playbackInfo.currentPositionMs,
                    durationMs = duration,
                    onSeekTo = callbacks.onSeekTo,
                    isPlaying = state.playbackInfo.isPlaying,
                    style = state.seekbarStyle,
                    appearance = state.appearance,
                    liveWaveform = state.waveformSamples,
                    hapticFeedbackEnabled = state.gestureConfig.hapticFeedbackEnabled,
                    accentColor = accent
                )

                PlaybackControls(
                    playbackInfo = state.playbackInfo,
                    onTogglePlayPause = callbacks.onTogglePlayPause,
                    onPlayNext = callbacks.onPlayNext,
                    onPlayPrevious = callbacks.onPlayPrevious,
                    onToggleShuffle = callbacks.onToggleShuffle,
                    onCycleRepeat = callbacks.onCycleRepeat,
                    appearance = state.appearance,
                    accentColor = accent,
                    primaryButtonSize = 68.dp
                )
            }
        }
    }
}

@Composable
fun SplitLayoutPlayer(
    state: PlayerRenderState,
    callbacks: PlayerStyleCallbacks,
    gestureController: PlayerGestureController,
    modifier: Modifier = Modifier
) {
    val colors = LocalEditorialColors.current
    val palette = state.dynamicPalette
    val song = state.song
    val duration = state.playbackInfo.durationMs.coerceAtLeast(song?.durationMs ?: 0L)
    val accent = PlayerStyleManager.resolveAccentColor(state.appearance, colors, palette)

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .navigationBarsPadding()
            .testTag("player_style_split_layout")
    ) {
        val isSideBySide = maxWidth >= 560.dp

        if (isSideBySide) {
            LandscapePlayer(
                state = state,
                callbacks = callbacks,
                gestureController = gestureController,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Column(modifier = Modifier.fillMaxSize()) {
                // Top Half: Tinted Dynamic Artwork Showcase Zone
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1.05f)
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    palette.primary.copy(alpha = if (colors.isDark) 0.42f else 0.24f),
                                    palette.secondary.copy(alpha = if (colors.isDark) 0.28f else 0.16f)
                                )
                            )
                        )
                        .padding(horizontal = 20.dp, vertical = 6.dp)
                ) {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        NowPlayingTopHeader(
                            song = song,
                            onClose = callbacks.onClose,
                            onOpenQueue = callbacks.onOpenQueue,
                            onOpenLyrics = callbacks.onOpenLyrics,
                            onOpenSleepTimer = callbacks.onOpenSleepTimer,
                            onOpenStyleWheel = callbacks.onArtworkLongPress,
                            onToggleVisualizer = callbacks.onToggleVisualizer,
                            isVisualizerActive = state.isVisualizerActive,
                            onOpenEqualizer = callbacks.onOpenEqualizer,
                            onOpenAudioStudio = callbacks.onOpenAudioEffects,
                            onOpenOutputDevice = callbacks.onOpenOutputDevice,
                            onToggleFocusMode = callbacks.onToggleFocusMode,
                            onOpenCustomization = callbacks.onOpenCustomization
                        )

                        ArtworkContainer(
                            song = song,
                            playbackInfo = state.playbackInfo,
                            appearance = state.appearance,
                            gestureConfig = state.gestureConfig,
                            gestureController = gestureController,
                            onTogglePlayPause = callbacks.onTogglePlayPause,
                            onPlayNext = callbacks.onPlayNext,
                            onPlayPrevious = callbacks.onPlayPrevious,
                            onSeekTo = callbacks.onSeekTo,
                            onOpenQueue = callbacks.onOpenQueue,
                            onClose = callbacks.onClose,
                            onArtworkLongPress = callbacks.onArtworkLongPress,
                            onToggleFavorite = callbacks.onToggleFavorite,
                            onCyclePlayerStyle = callbacks.onCyclePlayerStyle,
                            defaultCornerRadius = 26.dp,
                            modifier = Modifier
                                .fillMaxHeight(0.84f)
                                .aspectRatio(1f)
                                .padding(bottom = 10.dp)
                        )
                    }
                }

                // Bottom Half: Architectural Control Surface
                Surface(
                    shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
                    color = colors.surface,
                    shadowElevation = 12.dp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(0.95f)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 24.dp, vertical = 20.dp),
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        TrackMetadataBar(
                            song = song,
                            appearance = state.appearance,
                            onToggleFavorite = callbacks.onToggleFavorite,
                            onOpenLyrics = callbacks.onOpenLyrics,
                            onOpenQueue = callbacks.onOpenQueue,
                            accentColor = accent
                        )

                        AdaptiveSeekbar(
                            currentPositionMs = state.playbackInfo.currentPositionMs,
                            durationMs = duration,
                            onSeekTo = callbacks.onSeekTo,
                            isPlaying = state.playbackInfo.isPlaying,
                            style = state.seekbarStyle,
                            appearance = state.appearance,
                            liveWaveform = state.waveformSamples,
                            hapticFeedbackEnabled = state.gestureConfig.hapticFeedbackEnabled,
                            accentColor = accent
                        )

                        PlaybackControls(
                            playbackInfo = state.playbackInfo,
                            onTogglePlayPause = callbacks.onTogglePlayPause,
                            onPlayNext = callbacks.onPlayNext,
                            onPlayPrevious = callbacks.onPlayPrevious,
                            onToggleShuffle = callbacks.onToggleShuffle,
                            onCycleRepeat = callbacks.onCycleRepeat,
                            appearance = state.appearance,
                            accentColor = accent,
                            primaryButtonSize = 68.dp
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun LandscapePlayer(
    state: PlayerRenderState,
    callbacks: PlayerStyleCallbacks,
    gestureController: PlayerGestureController,
    modifier: Modifier = Modifier
) {
    val colors = LocalEditorialColors.current
    val palette = state.dynamicPalette
    val song = state.song
    val duration = state.playbackInfo.durationMs.coerceAtLeast(song?.durationMs ?: 0L)
    val accent = PlayerStyleManager.resolveAccentColor(state.appearance, colors, palette)

    Column(
        modifier = modifier
            .fillMaxSize()
            .navigationBarsPadding()
            .padding(horizontal = 20.dp, vertical = 6.dp)
            .testTag("player_style_landscape_player")
    ) {
        NowPlayingTopHeader(
            song = song,
            onClose = callbacks.onClose,
            onOpenQueue = callbacks.onOpenQueue,
            onOpenLyrics = callbacks.onOpenLyrics,
            onOpenSleepTimer = callbacks.onOpenSleepTimer,
            onOpenStyleWheel = callbacks.onArtworkLongPress,
            onToggleVisualizer = callbacks.onToggleVisualizer,
            isVisualizerActive = state.isVisualizerActive,
            onOpenEqualizer = callbacks.onOpenEqualizer,
            onOpenAudioStudio = callbacks.onOpenAudioEffects,
            onOpenOutputDevice = callbacks.onOpenOutputDevice,
            onToggleFocusMode = callbacks.onToggleFocusMode,
            onOpenCustomization = callbacks.onOpenCustomization
        )

        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(vertical = 8.dp)
        ) {
            if (maxWidth >= 500.dp) {
                Row(
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.spacedBy(24.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Left Pane: Large Square Artwork bounded by both width and height
                    BoxWithConstraints(
                        modifier = Modifier
                            .weight(0.44f)
                            .fillMaxHeight(),
                        contentAlignment = Alignment.Center
                    ) {
                        val artSide = minOf(maxWidth * 0.92f, maxHeight * 0.92f).coerceAtLeast(88.dp)
                        ArtworkContainer(
                            song = song,
                            playbackInfo = state.playbackInfo,
                            appearance = state.appearance,
                            gestureConfig = state.gestureConfig,
                            gestureController = gestureController,
                            onTogglePlayPause = callbacks.onTogglePlayPause,
                            onPlayNext = callbacks.onPlayNext,
                            onPlayPrevious = callbacks.onPlayPrevious,
                            onSeekTo = callbacks.onSeekTo,
                            onOpenQueue = callbacks.onOpenQueue,
                            onClose = callbacks.onClose,
                            onArtworkLongPress = callbacks.onArtworkLongPress,
                            onToggleFavorite = callbacks.onToggleFavorite,
                            onCyclePlayerStyle = callbacks.onCyclePlayerStyle,
                            defaultCornerRadius = 26.dp,
                            modifier = Modifier.size(artSide)
                        )
                    }

                    // Right Pane: Metadata + Seekbar + Controls
                    Surface(
                        shape = RoundedCornerShape(28.dp),
                        color = colors.surface.copy(alpha = 0.92f),
                        border = BorderStroke(0.75.dp, colors.cardBorder),
                        modifier = Modifier
                            .weight(0.56f)
                            .fillMaxHeight(0.92f)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 20.dp, vertical = 18.dp),
                            verticalArrangement = Arrangement.SpaceBetween
                        ) {
                            TrackMetadataBar(
                                song = song,
                                appearance = state.appearance,
                                onToggleFavorite = callbacks.onToggleFavorite,
                                onOpenLyrics = callbacks.onOpenLyrics,
                                onOpenQueue = callbacks.onOpenQueue,
                                accentColor = accent
                            )

                            AdaptiveSeekbar(
                                currentPositionMs = state.playbackInfo.currentPositionMs,
                                durationMs = duration,
                                onSeekTo = callbacks.onSeekTo,
                                isPlaying = state.playbackInfo.isPlaying,
                                style = state.seekbarStyle,
                                appearance = state.appearance,
                                liveWaveform = state.waveformSamples,
                                hapticFeedbackEnabled = state.gestureConfig.hapticFeedbackEnabled,
                                accentColor = accent
                            )

                            PlaybackControls(
                                playbackInfo = state.playbackInfo,
                                onTogglePlayPause = callbacks.onTogglePlayPause,
                                onPlayNext = callbacks.onPlayNext,
                                onPlayPrevious = callbacks.onPlayPrevious,
                                onToggleShuffle = callbacks.onToggleShuffle,
                                onCycleRepeat = callbacks.onCycleRepeat,
                                appearance = state.appearance,
                                accentColor = accent,
                                primaryButtonSize = 62.dp
                            )
                        }
                    }
                }
            } else {
                // Compact portrait fallback when Landscape style is previewed or chosen in portrait
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .padding(bottom = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        ArtworkContainer(
                            song = song,
                            playbackInfo = state.playbackInfo,
                            appearance = state.appearance,
                            gestureConfig = state.gestureConfig,
                            gestureController = gestureController,
                            onTogglePlayPause = callbacks.onTogglePlayPause,
                            onPlayNext = callbacks.onPlayNext,
                            onPlayPrevious = callbacks.onPlayPrevious,
                            onSeekTo = callbacks.onSeekTo,
                            onOpenQueue = callbacks.onOpenQueue,
                            onClose = callbacks.onClose,
                            onArtworkLongPress = callbacks.onArtworkLongPress,
                            onToggleFavorite = callbacks.onToggleFavorite,
                            onCyclePlayerStyle = callbacks.onCyclePlayerStyle,
                            defaultCornerRadius = 20.dp,
                            modifier = Modifier.size(116.dp)
                        )
                        Box(modifier = Modifier.weight(1f)) {
                            TrackMetadataBar(
                                song = song,
                                appearance = state.appearance,
                                onToggleFavorite = callbacks.onToggleFavorite,
                                onOpenLyrics = callbacks.onOpenLyrics,
                                onOpenQueue = callbacks.onOpenQueue,
                                accentColor = accent
                            )
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(26.dp),
                        color = colors.surface.copy(alpha = 0.92f),
                        border = BorderStroke(0.75.dp, colors.cardBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 14.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            AdaptiveSeekbar(
                                currentPositionMs = state.playbackInfo.currentPositionMs,
                                durationMs = duration,
                                onSeekTo = callbacks.onSeekTo,
                                isPlaying = state.playbackInfo.isPlaying,
                                style = state.seekbarStyle,
                                appearance = state.appearance,
                                liveWaveform = state.waveformSamples,
                                hapticFeedbackEnabled = state.gestureConfig.hapticFeedbackEnabled,
                                accentColor = accent
                            )

                            PlaybackControls(
                                playbackInfo = state.playbackInfo,
                                onTogglePlayPause = callbacks.onTogglePlayPause,
                                onPlayNext = callbacks.onPlayNext,
                                onPlayPrevious = callbacks.onPlayPrevious,
                                onToggleShuffle = callbacks.onToggleShuffle,
                                onCycleRepeat = callbacks.onCycleRepeat,
                                appearance = state.appearance,
                                accentColor = accent,
                                primaryButtonSize = 60.dp
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun LyricsFocusPlayer(
    state: PlayerRenderState,
    callbacks: PlayerStyleCallbacks,
    gestureController: PlayerGestureController,
    modifier: Modifier = Modifier
) {
    val colors = LocalEditorialColors.current
    val palette = state.dynamicPalette
    val song = state.song
    val duration = state.playbackInfo.durationMs.coerceAtLeast(song?.durationMs ?: 0L)
    val accent = PlayerStyleManager.resolveAccentColor(state.appearance, colors, palette)

    val parsedLyrics = remember(state.rawLyrics, song?.lyrics) {
        LrcParser.parse(state.rawLyrics ?: song?.lyrics)
    }
    val lines = parsedLyrics.lines
    val currentPos = state.playbackInfo.currentPositionMs
    val activeIndex = remember(lines, currentPos) {
        if (lines.isEmpty()) -1
        else lines.indexOfLast { it.isTimed && currentPos >= it.startTimeMs }
    }
    val listState = rememberLazyListState()

    LaunchedEffect(activeIndex) {
        if (activeIndex >= 0 && lines.isNotEmpty()) {
            listState.animateScrollToItem((activeIndex - 1).coerceAtLeast(0))
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        palette.primary.copy(alpha = if (colors.isDark) 0.34f else 0.16f),
                        colors.background,
                        colors.background
                    )
                )
            )
            .navigationBarsPadding()
            .testTag("player_style_lyrics_focus"),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 620.dp)
                .padding(horizontal = 20.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            NowPlayingTopHeader(
                song = song,
                onClose = callbacks.onClose,
                onOpenQueue = callbacks.onOpenQueue,
                onOpenLyrics = callbacks.onOpenLyrics,
                onOpenSleepTimer = callbacks.onOpenSleepTimer,
                onOpenStyleWheel = callbacks.onArtworkLongPress,
                onToggleVisualizer = callbacks.onToggleVisualizer,
                isVisualizerActive = state.isVisualizerActive,
                onOpenEqualizer = callbacks.onOpenEqualizer,
                onOpenAudioStudio = callbacks.onOpenAudioEffects,
                onOpenOutputDevice = callbacks.onOpenOutputDevice,
                onToggleFocusMode = callbacks.onToggleFocusMode,
                onOpenCustomization = callbacks.onOpenCustomization
            )

            // Compact Now Playing Song Header Strip
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = colors.surface.copy(alpha = 0.88f),
                border = BorderStroke(0.75.dp, colors.cardBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    ArtworkAlbumView(
                        song = song,
                        modifier = Modifier.size(48.dp),
                        cornerRadius = 12.dp,
                        isPlaying = state.playbackInfo.isPlaying
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = song?.title ?: "No Track Selected",
                            fontFamily = OutfitFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = colors.textPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = song?.artist ?: "Unknown Artist",
                            fontFamily = PlusJakartaFontFamily,
                            fontSize = 12.sp,
                            color = colors.textSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Surface(
                        onClick = callbacks.onOpenLyrics,
                        shape = RoundedCornerShape(12.dp),
                        color = accent.copy(alpha = 0.16f)
                    ) {
                        Text(
                            text = "Full Lyrics",
                            fontFamily = PlusJakartaFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            color = accent,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        )
                    }
                }
            }

            // Center Real-Time Synchronized Lyrics Viewport
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(vertical = 12.dp)
            ) {
                if (lines.isNotEmpty()) {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(vertical = 18.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        itemsIndexed(lines, key = { idx, line -> "${idx}_${line.startTimeMs}" }) { index, line ->
                            val isActive = index == activeIndex
                            Text(
                                text = line.text,
                                fontFamily = PlayerStyleManager.resolveTitleFontFamily(state.appearance.typographyOption),
                                fontWeight = if (isActive) FontWeight.ExtraBold else FontWeight.SemiBold,
                                fontSize = if (isActive) 24.sp else 18.sp,
                                lineHeight = if (isActive) 31.sp else 25.sp,
                                color = if (isActive) accent else colors.textSecondary.copy(alpha = 0.62f),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable(enabled = line.isTimed) {
                                        if (line.isTimed) callbacks.onSeekTo(line.startTimeMs)
                                    }
                                    .padding(horizontal = 6.dp, vertical = 4.dp)
                            )
                        }
                    }
                } else {
                    Surface(
                        onClick = callbacks.onOpenLyrics,
                        shape = RoundedCornerShape(24.dp),
                        color = colors.surfaceTinted,
                        border = BorderStroke(0.75.dp, colors.cardBorder),
                        modifier = Modifier.align(Alignment.Center).fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Lyrics,
                                contentDescription = "Lyrics",
                                tint = accent,
                                modifier = Modifier.size(34.dp)
                            )
                            Text(
                                text = "Synced Lyrics Ready",
                                fontFamily = OutfitFontFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp,
                                color = colors.textPrimary
                            )
                            Text(
                                text = "No synced lyrics loaded for this track yet. Tap to open the Lyrics Manager or load an .lrc file.",
                                fontFamily = PlusJakartaFontFamily,
                                fontSize = 13.sp,
                                color = colors.textSecondary
                            )
                        }
                    }
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                AdaptiveSeekbar(
                    currentPositionMs = state.playbackInfo.currentPositionMs,
                    durationMs = duration,
                    onSeekTo = callbacks.onSeekTo,
                    isPlaying = state.playbackInfo.isPlaying,
                    style = state.seekbarStyle,
                    appearance = state.appearance,
                    liveWaveform = state.waveformSamples,
                    hapticFeedbackEnabled = state.gestureConfig.hapticFeedbackEnabled,
                    accentColor = accent
                )

                PlaybackControls(
                    playbackInfo = state.playbackInfo,
                    onTogglePlayPause = callbacks.onTogglePlayPause,
                    onPlayNext = callbacks.onPlayNext,
                    onPlayPrevious = callbacks.onPlayPrevious,
                    onToggleShuffle = callbacks.onToggleShuffle,
                    onCycleRepeat = callbacks.onCycleRepeat,
                    appearance = state.appearance,
                    accentColor = accent,
                    primaryButtonSize = 62.dp
                )
            }
        }
    }
}
