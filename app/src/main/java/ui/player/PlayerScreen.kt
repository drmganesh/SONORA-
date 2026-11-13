package ui.player

import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioManager
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Balance
import androidx.compose.material.icons.filled.BluetoothAudio
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SpatialAudio
import androidx.compose.material.icons.filled.Speaker
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Usb
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.PlayerStyle
import com.example.ui.components.ArtworkAlbumView
import com.example.ui.theme.OutfitFontFamily
import com.example.ui.theme.PlusJakartaFontFamily
import com.example.util.LyricsExtractor
import com.example.viewmodel.SonoraViewModel
import ui.theme.HomeThemeMode
import ui.theme.LocalDynamicColorPalette
import ui.theme.LocalEditorialColors
import ui.theme.LocalReduceAnimations

/**
 * Unified PlayerScreen Coordinator
 * Hosts all 9 customizable Player Styles on SONORA AESTHETIC UI, plus:
 * - Full Artwork Focus Mode
 * - Real AudioManager Output Device Sheet + Dual-Earbud Recenter
 * - Quick Playback Speed selector
 * - Unified Equalizer, Audio Effects Studio (8D), Queue, Lyrics, and Sleep Timer
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerScreen(
    viewModel: SonoraViewModel,
    onClose: () -> Unit,
    onOpenEqualizer: (() -> Unit)? = null,
    onOpenAudioStudio: (() -> Unit)? = null,
    onOpenQueueScreen: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val colors = LocalEditorialColors.current
    val reduceAnimations = LocalReduceAnimations.current
    val palette = LocalDynamicColorPalette.current
    val homeThemeMode by viewModel.homeThemeMode.collectAsState()
    val dynamicColorsEnabled by viewModel.dynamicColorsEnabled.collectAsState()

    val currentStyle by viewModel.playerStyle.collectAsState()
    val currentSeekbarStyle by viewModel.seekbarStyle.collectAsState()
    val currentSong by viewModel.currentSong.collectAsState()
    val playbackInfo by viewModel.playbackInfo.collectAsState()
    val queue by viewModel.queue.collectAsState()
    val queueIndex by viewModel.queueIndex.collectAsState()
    val currentTrackLyrics by viewModel.currentTrackLyrics.collectAsState()
    val playbackSpeed by viewModel.playbackSpeed.collectAsState()
    val connectedAudioDevice by viewModel.connectedAudioDeviceName.collectAsState()
    val isTwsConnected by viewModel.isTwsConnected.collectAsState()

    val gesturesEnabled by viewModel.gesturesEnabled.collectAsState()
    val swipeArtworkToSkip by viewModel.swipeArtworkToSkip.collectAsState()
    val doubleTapToSeek by viewModel.doubleTapToSeek.collectAsState()
    val swipeDownToCollapse by viewModel.swipeDownToCollapse.collectAsState()

    val visualizerFrequencies by viewModel.visualizerFrequencies.collectAsState()
    val waveformSamples by viewModel.waveformSamples.collectAsState()
    val visualizerStyle by viewModel.visualizerStyle.collectAsState()
    var isVisualizerActive by remember { mutableStateOf(false) }

    var isArtworkFocusMode by remember { mutableStateOf(false) }
    var showStyleWheel by remember { mutableStateOf(false) }
    var showQueueSheet by remember { mutableStateOf(false) }
    var showLyricsSheet by remember { mutableStateOf(false) }
    var showSleepTimerDialog by remember { mutableStateOf(false) }
    var showOutputDeviceSheet by remember { mutableStateOf(false) }
    var showAudioEffectsModal by remember { mutableStateOf(false) }
    var showEqualizerModal by remember { mutableStateOf(false) }

    BackHandler(enabled = isArtworkFocusMode) {
        isArtworkFocusMode = false
    }
    BackHandler(enabled = showStyleWheel) {
        showStyleWheel = false
    }

    val openEqualizerAction: () -> Unit = {
        if (onOpenEqualizer != null) onOpenEqualizer() else showEqualizerModal = true
    }
    val openAudioStudioAction: () -> Unit = {
        if (onOpenAudioStudio != null) onOpenAudioStudio() else showAudioEffectsModal = true
    }
    val openQueueAction: () -> Unit = {
        if (onOpenQueueScreen != null) onOpenQueueScreen() else showQueueSheet = true
    }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val glowAlpha = if (homeThemeMode == HomeThemeMode.DYNAMIC_ALBUM_ART || dynamicColorsEnabled) {
        if (colors.isDark) 0.24f else 0.14f
    } else 0.06f

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background)
    ) {
        // Subtle Ambient Album Glow (preserves editorial background contrast)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            palette.primary.copy(alpha = glowAlpha),
                            colors.accentStrong.copy(alpha = glowAlpha * 0.45f),
                            Color.Transparent
                        )
                    )
                )
        )

        Column(modifier = Modifier.fillMaxSize()) {
            Box(modifier = Modifier.weight(1f)) {
                AnimatedContent(
                    targetState = currentStyle,
                    transitionSpec = {
                        if (reduceAnimations) {
                            fadeIn(animationSpec = snap()) togetherWith fadeOut(animationSpec = snap())
                        } else {
                            (fadeIn(animationSpec = tween(260)) + scaleIn(initialScale = 0.97f, animationSpec = tween(260)))
                                .togetherWith(fadeOut(animationSpec = tween(180)))
                        }
                    },
                    label = "playerStyleSwitch"
                ) { targetStyle ->
                    when (targetStyle) {
                        PlayerStyle.CLASSIC -> ClassicPlayer(
                            song = currentSong,
                            playbackInfo = playbackInfo,
                            onTogglePlayPause = { viewModel.togglePlayPause() },
                            onPlayNext = { viewModel.playNext() },
                            onPlayPrevious = { viewModel.playPrevious() },
                            onSeekTo = { viewModel.seekTo(it) },
                            onToggleShuffle = { viewModel.toggleShuffle() },
                            onCycleRepeat = { viewModel.cycleRepeatMode() },
                            onOpenQueue = openQueueAction,
                            onOpenLyrics = { showLyricsSheet = true },
                            onOpenSleepTimer = { showSleepTimerDialog = true },
                            onClose = onClose,
                            onArtworkLongPress = { showStyleWheel = true },
                            seekbarStyle = currentSeekbarStyle,
                            onToggleFavorite = { currentSong?.let { viewModel.toggleFavorite(it) } },
                            onOpenAudioEffects = openAudioStudioAction,
                            onOpenEqualizer = openEqualizerAction,
                            onOpenOutputDevice = { showOutputDeviceSheet = true },
                            onToggleFocusMode = { isArtworkFocusMode = true },
                            isVisualizerActive = isVisualizerActive,
                            onToggleVisualizer = { isVisualizerActive = !isVisualizerActive },
                            frequencies = visualizerFrequencies,
                            waveform = waveformSamples,
                            visualizerStyle = visualizerStyle,
                            onCycleVisualizerStyle = { viewModel.cycleVisualizerStyle() }
                        )
                        PlayerStyle.GESTURE -> GesturePlayer(
                            song = currentSong,
                            playbackInfo = playbackInfo,
                            onTogglePlayPause = { viewModel.togglePlayPause() },
                            onPlayNext = { viewModel.playNext() },
                            onPlayPrevious = { viewModel.playPrevious() },
                            onSeekTo = { viewModel.seekTo(it) },
                            onToggleShuffle = { viewModel.toggleShuffle() },
                            onCycleRepeat = { viewModel.cycleRepeatMode() },
                            onOpenQueue = openQueueAction,
                            onOpenLyrics = { showLyricsSheet = true },
                            onOpenSleepTimer = { showSleepTimerDialog = true },
                            onClose = onClose,
                            onArtworkLongPress = { showStyleWheel = true },
                            seekbarStyle = currentSeekbarStyle,
                            gesturesEnabled = gesturesEnabled,
                            swipeArtworkToSkip = swipeArtworkToSkip,
                            doubleTapToSeek = doubleTapToSeek,
                            swipeDownToCollapse = swipeDownToCollapse
                        )
                        PlayerStyle.EDITORIAL -> EditorialPlayer(
                            song = currentSong,
                            playbackInfo = playbackInfo,
                            onTogglePlayPause = { viewModel.togglePlayPause() },
                            onPlayNext = { viewModel.playNext() },
                            onPlayPrevious = { viewModel.playPrevious() },
                            onSeekTo = { viewModel.seekTo(it) },
                            onToggleShuffle = { viewModel.toggleShuffle() },
                            onCycleRepeat = { viewModel.cycleRepeatMode() },
                            onOpenQueue = openQueueAction,
                            onOpenLyrics = { showLyricsSheet = true },
                            onOpenSleepTimer = { showSleepTimerDialog = true },
                            onClose = onClose,
                            onArtworkLongPress = { showStyleWheel = true },
                            seekbarStyle = currentSeekbarStyle
                        )
                        PlayerStyle.CANVAS -> CanvasPlayer(
                            song = currentSong,
                            playbackInfo = playbackInfo,
                            onTogglePlayPause = { viewModel.togglePlayPause() },
                            onPlayNext = { viewModel.playNext() },
                            onPlayPrevious = { viewModel.playPrevious() },
                            onSeekTo = { viewModel.seekTo(it) },
                            onToggleShuffle = { viewModel.toggleShuffle() },
                            onCycleRepeat = { viewModel.cycleRepeatMode() },
                            onOpenQueue = openQueueAction,
                            onOpenLyrics = { showLyricsSheet = true },
                            onOpenSleepTimer = { showSleepTimerDialog = true },
                            onClose = onClose,
                            onArtworkLongPress = { showStyleWheel = true },
                            seekbarStyle = currentSeekbarStyle
                        )
                        PlayerStyle.BENTO -> BentoPlayer(
                            song = currentSong,
                            playbackInfo = playbackInfo,
                            onTogglePlayPause = { viewModel.togglePlayPause() },
                            onPlayNext = { viewModel.playNext() },
                            onPlayPrevious = { viewModel.playPrevious() },
                            onSeekTo = { viewModel.seekTo(it) },
                            onToggleShuffle = { viewModel.toggleShuffle() },
                            onCycleRepeat = { viewModel.cycleRepeatMode() },
                            onOpenQueue = openQueueAction,
                            onOpenLyrics = { showLyricsSheet = true },
                            onOpenSleepTimer = { showSleepTimerDialog = true },
                            onClose = onClose,
                            onArtworkLongPress = { showStyleWheel = true },
                            seekbarStyle = currentSeekbarStyle
                        )
                        PlayerStyle.STICKER -> StickerPlayer(
                            song = currentSong,
                            playbackInfo = playbackInfo,
                            onTogglePlayPause = { viewModel.togglePlayPause() },
                            onPlayNext = { viewModel.playNext() },
                            onPlayPrevious = { viewModel.playPrevious() },
                            onSeekTo = { viewModel.seekTo(it) },
                            onToggleShuffle = { viewModel.toggleShuffle() },
                            onCycleRepeat = { viewModel.cycleRepeatMode() },
                            onOpenQueue = openQueueAction,
                            onOpenLyrics = { showLyricsSheet = true },
                            onOpenSleepTimer = { showSleepTimerDialog = true },
                            onClose = onClose,
                            onArtworkLongPress = { showStyleWheel = true },
                            seekbarStyle = currentSeekbarStyle
                        )
                        PlayerStyle.MORPH -> MorphPlayer(
                            song = currentSong,
                            playbackInfo = playbackInfo,
                            onTogglePlayPause = { viewModel.togglePlayPause() },
                            onPlayNext = { viewModel.playNext() },
                            onPlayPrevious = { viewModel.playPrevious() },
                            onSeekTo = { viewModel.seekTo(it) },
                            onToggleShuffle = { viewModel.toggleShuffle() },
                            onCycleRepeat = { viewModel.cycleRepeatMode() },
                            onOpenQueue = openQueueAction,
                            onOpenLyrics = { showLyricsSheet = true },
                            onOpenSleepTimer = { showSleepTimerDialog = true },
                            onClose = onClose,
                            onArtworkLongPress = { showStyleWheel = true },
                            seekbarStyle = currentSeekbarStyle
                        )
                        PlayerStyle.DIAL -> DialPlayer(
                            song = currentSong,
                            playbackInfo = playbackInfo,
                            onTogglePlayPause = { viewModel.togglePlayPause() },
                            onPlayNext = { viewModel.playNext() },
                            onPlayPrevious = { viewModel.playPrevious() },
                            onSeekTo = { viewModel.seekTo(it) },
                            onToggleShuffle = { viewModel.toggleShuffle() },
                            onCycleRepeat = { viewModel.cycleRepeatMode() },
                            onOpenQueue = openQueueAction,
                            onOpenLyrics = { showLyricsSheet = true },
                            onOpenSleepTimer = { showSleepTimerDialog = true },
                            onClose = onClose,
                            onArtworkLongPress = { showStyleWheel = true },
                            seekbarStyle = currentSeekbarStyle
                        )
                        PlayerStyle.HERO -> HeroPlayer(
                            song = currentSong,
                            playbackInfo = playbackInfo,
                            onTogglePlayPause = { viewModel.togglePlayPause() },
                            onPlayNext = { viewModel.playNext() },
                            onPlayPrevious = { viewModel.playPrevious() },
                            onSeekTo = { viewModel.seekTo(it) },
                            onToggleShuffle = { viewModel.toggleShuffle() },
                            onCycleRepeat = { viewModel.cycleRepeatMode() },
                            onOpenQueue = openQueueAction,
                            onOpenLyrics = { showLyricsSheet = true },
                            onOpenSleepTimer = { showSleepTimerDialog = true },
                            onClose = onClose,
                            onArtworkLongPress = { showStyleWheel = true },
                            seekbarStyle = currentSeekbarStyle
                        )
                    }
                }
            }

            // Sleek Shared Bottom Utility & Queue Swipe Dock across all 9 Player Styles
            PlayerUtilityStrip(
                connectedDevice = connectedAudioDevice,
                playbackSpeed = playbackSpeed,
                onOpenOutputDevice = { showOutputDeviceSheet = true },
                onCycleSpeed = {
                    val speeds = listOf(0.75f, 1.0f, 1.25f, 1.5f, 2.0f)
                    val curIdx = speeds.indexOfFirst { kotlin.math.abs(it - playbackSpeed) < 0.08f }
                    val next = speeds[(curIdx + 1) % speeds.size]
                    viewModel.setPlaybackSpeed(next)
                },
                onOpenEqualizer = openEqualizerAction,
                onOpenAudioStudio = openAudioStudioAction,
                onToggleFocusMode = { isArtworkFocusMode = true },
                onOpenQueue = openQueueAction
            )
        }

        // Full Artwork Focus Mode Overlay
        AnimatedVisibility(
            visible = isArtworkFocusMode,
            enter = if (reduceAnimations) fadeIn(snap()) else fadeIn(tween(240)),
            exit = if (reduceAnimations) fadeOut(snap()) else fadeOut(tween(180))
        ) {
            ArtworkFocusOverlay(
                song = currentSong,
                isPlaying = playbackInfo.isPlaying,
                onTogglePlayPause = { viewModel.togglePlayPause() },
                onPlayNext = { viewModel.playNext() },
                onPlayPrevious = { viewModel.playPrevious() },
                onExitFocus = { isArtworkFocusMode = false }
            )
        }

        // Quick Style Switching Modal (Style Wheel)
        if (showStyleWheel) {
            PlayerStyleWheel(
                currentStyle = currentStyle,
                onSelectStyle = { newStyle -> viewModel.setPlayerStyle(newStyle) },
                onDismiss = { showStyleWheel = false }
            )
        }

        // Audio Output Device Selector Bottom Sheet
        if (showOutputDeviceSheet) {
            AudioOutputDeviceSheet(
                connectedDeviceName = connectedAudioDevice,
                isTwsConnected = isTwsConnected,
                onRecenterBalance = { viewModel.forceBalanceDualEarbuds() },
                onOpenAudioStudio = {
                    showOutputDeviceSheet = false
                    openAudioStudioAction()
                },
                onDismiss = { showOutputDeviceSheet = false }
            )
        }

        // Fallback Queue Bottom Sheet (when no full QueueScreen override)
        if (showQueueSheet) {
            ModalBottomSheet(
                onDismissRequest = { showQueueSheet = false },
                sheetState = sheetState,
                containerColor = colors.surface,
                contentColor = colors.textPrimary,
                shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "PLAYING QUEUE",
                                fontFamily = OutfitFontFamily,
                                color = colors.accentStrong,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp
                            )
                            Text(
                                text = "${queue.size} tracks in line",
                                fontFamily = PlusJakartaFontFamily,
                                color = colors.textSecondary,
                                fontSize = 13.sp
                            )
                        }
                        Row {
                            TextButton(onClick = { viewModel.shuffleRemainingQueue() }) {
                                Text("Shuffle Next", fontFamily = PlusJakartaFontFamily, color = colors.accentStrong)
                            }
                            TextButton(onClick = { viewModel.clearQueue() }) {
                                Text("Clear", fontFamily = PlusJakartaFontFamily, color = colors.accentStrong)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(360.dp),
                        contentPadding = PaddingValues(vertical = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        itemsIndexed(queue) { index, qSong ->
                            val isCurrent = index == queueIndex
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(14.dp))
                                    .clickable { viewModel.playSong(qSong, queue, index) },
                                color = if (isCurrent) colors.accentPill else colors.surfaceElevated,
                                border = BorderStroke(
                                    1.dp,
                                    if (isCurrent) colors.accentStrong.copy(alpha = 0.4f) else colors.cardBorder
                                )
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "${index + 1}",
                                        fontFamily = PlusJakartaFontFamily,
                                        color = if (isCurrent) colors.onAccentPill else colors.textMuted,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.width(28.dp)
                                    )
                                    ArtworkAlbumView(
                                        song = qSong,
                                        modifier = Modifier.size(40.dp),
                                        cornerRadius = 8.dp,
                                        isPlaying = isCurrent && playbackInfo.isPlaying
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = qSong.title,
                                            fontFamily = OutfitFontFamily,
                                            color = if (isCurrent) colors.onAccentPill else colors.textPrimary,
                                            fontSize = 14.sp,
                                            fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Medium,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = qSong.artist,
                                            fontFamily = PlusJakartaFontFamily,
                                            color = if (isCurrent) colors.onAccentPill.copy(alpha = 0.78f) else colors.textSecondary,
                                            fontSize = 12.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    IconButton(
                                        onClick = { viewModel.removeFromQueue(index) },
                                        modifier = Modifier.size(48.dp)
                                    ) {
                                        Icon(Icons.Default.Delete, contentDescription = "Remove", tint = colors.textMuted, modifier = Modifier.size(18.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Synchronized Lyrics Modal Bottom Sheet
        if (showLyricsSheet) {
            val lyrics = currentTrackLyrics ?: currentSong?.lyrics
            var fontSizeSp by remember { mutableFloatStateOf(16f) }
            val parsedLines = remember(lyrics) { LyricsExtractor.parseLrcLines(lyrics) }
            val activeLineIdx = remember(parsedLines, playbackInfo.currentPositionMs) {
                LyricsExtractor.findActiveLineIndex(parsedLines, playbackInfo.currentPositionMs)
            }
            val lyricsListState = rememberLazyListState()

            LaunchedEffect(activeLineIdx) {
                if (activeLineIdx >= 0 && activeLineIdx < parsedLines.size) {
                    if (reduceAnimations) {
                        lyricsListState.scrollToItem((activeLineIdx - 2).coerceAtLeast(0))
                    } else {
                        lyricsListState.animateScrollToItem((activeLineIdx - 2).coerceAtLeast(0))
                    }
                }
            }

            ModalBottomSheet(
                onDismissRequest = { showLyricsSheet = false },
                containerColor = colors.surface,
                contentColor = colors.textPrimary,
                shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "SYNCHRONIZED LYRICS",
                                fontFamily = OutfitFontFamily,
                                color = colors.accentStrong,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp
                            )
                            Text(
                                text = currentSong?.title ?: "",
                                fontFamily = PlusJakartaFontFamily,
                                color = colors.textSecondary,
                                fontSize = 12.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clip(RoundedCornerShape(14.dp))
                                .background(colors.surfaceElevated)
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = "A-",
                                fontFamily = PlusJakartaFontFamily,
                                color = colors.textSecondary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier
                                    .clickable { fontSizeSp = (fontSizeSp - 2f).coerceAtLeast(12f) }
                                    .padding(horizontal = 8.dp, vertical = 6.dp)
                            )
                            Text(
                                text = "A+",
                                fontFamily = PlusJakartaFontFamily,
                                color = colors.accentStrong,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier
                                    .clickable { fontSizeSp = (fontSizeSp + 2f).coerceAtMost(24f) }
                                    .padding(horizontal = 8.dp, vertical = 6.dp)
                            )
                            IconButton(onClick = { showLyricsSheet = false }, modifier = Modifier.size(40.dp)) {
                                Icon(Icons.Default.Close, contentDescription = "Close", tint = colors.textPrimary, modifier = Modifier.size(18.dp))
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    if (lyrics.isNullOrBlank()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(200.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "No synced lyrics found for this song.\nSupports standard .lrc file tags.",
                                fontFamily = PlusJakartaFontFamily,
                                color = colors.textMuted,
                                textAlign = TextAlign.Center,
                                fontSize = 14.sp
                            )
                        }
                    } else {
                        LazyColumn(
                            state = lyricsListState,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(400.dp),
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            if (parsedLines.isNotEmpty()) {
                                itemsIndexed(parsedLines) { index, line ->
                                    val isCurrentLine = index == activeLineIdx
                                    Text(
                                        text = line.text,
                                        fontFamily = if (isCurrentLine) OutfitFontFamily else PlusJakartaFontFamily,
                                        color = if (isCurrentLine) colors.accentStrong else colors.textSecondary.copy(alpha = 0.7f),
                                        fontSize = if (isCurrentLine) (fontSizeSp + 3f).sp else fontSizeSp.sp,
                                        fontWeight = if (isCurrentLine) FontWeight.Bold else FontWeight.Normal,
                                        lineHeight = (fontSizeSp * 1.4f).sp,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { viewModel.seekTo(line.timestampMs) }
                                            .padding(vertical = 4.dp)
                                    )
                                }
                            } else {
                                item {
                                    Text(
                                        text = lyrics,
                                        fontFamily = PlusJakartaFontFamily,
                                        color = colors.textSecondary,
                                        fontSize = fontSizeSp.sp,
                                        lineHeight = (fontSizeSp * 1.5f).sp
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Sleep Timer Dialog
        if (showSleepTimerDialog) {
            val timerOptions = listOf(15, 30, 45, 60, 90)
            AlertDialog(
                onDismissRequest = { showSleepTimerDialog = false },
                containerColor = colors.surface,
                title = {
                    Column {
                        Text("Sleep Timer", fontFamily = OutfitFontFamily, color = colors.textPrimary, fontWeight = FontWeight.Bold)
                        val remaining = playbackInfo.sleepTimerMinutesRemaining
                        if (remaining != null && remaining > 0) {
                            Text(
                                text = "$remaining min remaining",
                                fontFamily = PlusJakartaFontFamily,
                                color = colors.accentStrong,
                                fontSize = 12.sp
                            )
                        }
                    }
                },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        timerOptions.forEach { minutes ->
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable {
                                        viewModel.setSleepTimer(minutes)
                                        showSleepTimerDialog = false
                                    },
                                color = colors.accentPill
                            ) {
                                Text(
                                    text = "$minutes minutes",
                                    fontFamily = PlusJakartaFontFamily,
                                    color = colors.onAccentPill,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.padding(14.dp)
                                )
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        viewModel.setSleepTimer(0)
                        showSleepTimerDialog = false
                    }) {
                        Text("Turn Off", fontFamily = PlusJakartaFontFamily, color = colors.accentStrong)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showSleepTimerDialog = false }) {
                        Text("Cancel", fontFamily = PlusJakartaFontFamily, color = colors.textMuted)
                    }
                }
            )
        }

        // Fallback Inline Equalizer Modal Sheet
        if (showEqualizerModal) {
            ModalBottomSheet(
                onDismissRequest = { showEqualizerModal = false },
                sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
                containerColor = colors.surface,
                contentColor = colors.textPrimary,
                shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
            ) {
                Box(modifier = Modifier.fillMaxWidth().height(660.dp)) {
                    com.example.ui.equalizer.AdaptiveEqualizerScreen(
                        viewModel = viewModel,
                        onBack = { showEqualizerModal = false }
                    )
                }
            }
        }

        // Fallback Audio Effects Modal Sheet
        if (showAudioEffectsModal) {
            val bassBoost by viewModel.bassBoost.collectAsState()
            val virtualizer by viewModel.virtualizer.collectAsState()
            val loudnessGainMb by viewModel.loudnessGainMb.collectAsState()

            ModalBottomSheet(
                onDismissRequest = { showAudioEffectsModal = false },
                sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
                containerColor = colors.surface,
                contentColor = colors.textPrimary,
                shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "AUDIO EFFECTS ENGINE",
                                fontFamily = OutfitFontFamily,
                                color = colors.accentStrong,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp
                            )
                            Text(
                                text = "Hi-Fi DSP Bass & Soundstage Enhancements",
                                fontFamily = PlusJakartaFontFamily,
                                color = colors.textSecondary,
                                fontSize = 12.sp
                            )
                        }
                        IconButton(onClick = { showAudioEffectsModal = false }, modifier = Modifier.size(48.dp)) {
                            Icon(Icons.Default.Close, contentDescription = "Close", tint = colors.textPrimary)
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Deep Bass Boost", fontFamily = OutfitFontFamily, color = colors.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                        Text("${bassBoost / 10}%", fontFamily = PlusJakartaFontFamily, color = colors.accentStrong, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                    Slider(
                        value = bassBoost.toFloat(),
                        onValueChange = { viewModel.setBassBoost(it.toInt()) },
                        valueRange = 0f..1000f,
                        colors = SliderDefaults.colors(
                            thumbColor = colors.accentStrong,
                            activeTrackColor = colors.accentStrong,
                            inactiveTrackColor = colors.cardBorder
                        )
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Stereo Soundstage Width", fontFamily = OutfitFontFamily, color = colors.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                        Text("${virtualizer / 10}%", fontFamily = PlusJakartaFontFamily, color = colors.accentStrong, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                    Slider(
                        value = virtualizer.toFloat(),
                        onValueChange = { viewModel.setVirtualizer(it.toInt()) },
                        valueRange = 0f..1000f,
                        colors = SliderDefaults.colors(
                            thumbColor = colors.accentStrong,
                            activeTrackColor = colors.accentStrong,
                            inactiveTrackColor = colors.cardBorder
                        )
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Dynamic Loudness Gain", fontFamily = OutfitFontFamily, color = colors.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                        Text("${loudnessGainMb / 100} dB", fontFamily = PlusJakartaFontFamily, color = colors.accentStrong, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                    Slider(
                        value = loudnessGainMb.toFloat(),
                        onValueChange = { viewModel.setLoudness(it.toInt()) },
                        valueRange = 0f..1000f,
                        colors = SliderDefaults.colors(
                            thumbColor = colors.accentStrong,
                            activeTrackColor = colors.accentStrong,
                            inactiveTrackColor = colors.cardBorder
                        )
                    )

                    Spacer(modifier = Modifier.height(18.dp))
                }
            }
        }
    }
}

@Composable
private fun PlayerUtilityStrip(
    connectedDevice: String,
    playbackSpeed: Float,
    onOpenOutputDevice: () -> Unit,
    onCycleSpeed: () -> Unit,
    onOpenEqualizer: () -> Unit,
    onOpenAudioStudio: () -> Unit,
    onToggleFocusMode: () -> Unit,
    onOpenQueue: () -> Unit
) {
    val colors = LocalEditorialColors.current
    var dragY by remember { mutableFloatStateOf(0f) }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .pointerInput(Unit) {
                detectVerticalDragGestures(
                    onDragStart = { dragY = 0f },
                    onDragEnd = {
                        if (dragY < -50f) onOpenQueue()
                        dragY = 0f
                    },
                    onVerticalDrag = { change, amount ->
                        change.consume()
                        dragY += amount
                    }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 600.dp),
            shape = RoundedCornerShape(22.dp),
            color = colors.surface,
            border = BorderStroke(1.dp, colors.cardBorder),
            tonalElevation = 2.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .clickable(onClick = onOpenOutputDevice)
                        .padding(horizontal = 8.dp)
                        .testTag("player_output_device_button"),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Headphones, contentDescription = "Audio Output Device", tint = colors.accentStrong, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(5.dp))
                    Text(
                        text = connectedDevice.take(12),
                        fontFamily = PlusJakartaFontFamily,
                        color = colors.textPrimary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Row(
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .clickable(onClick = onCycleSpeed)
                        .padding(horizontal = 8.dp)
                        .testTag("player_speed_button"),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Speed, contentDescription = "Playback Speed", tint = colors.accentStrong, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "${String.format(java.util.Locale.US, "%.2g", playbackSpeed)}x",
                        fontFamily = PlusJakartaFontFamily,
                        color = colors.textPrimary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                IconButton(
                    onClick = onOpenEqualizer,
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("player_eq_button")
                ) {
                    Icon(Icons.Default.GraphicEq, contentDescription = "Equalizer", tint = colors.accentStrong, modifier = Modifier.size(18.dp))
                }

                IconButton(
                    onClick = onOpenAudioStudio,
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("player_studio_button")
                ) {
                    Icon(Icons.Default.SpatialAudio, contentDescription = "Audio Effects Studio", tint = colors.textPrimary, modifier = Modifier.size(18.dp))
                }

                IconButton(
                    onClick = onToggleFocusMode,
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("player_focus_mode_button")
                ) {
                    Icon(Icons.Default.CenterFocusStrong, contentDescription = "Artwork Focus Mode", tint = colors.textPrimary, modifier = Modifier.size(18.dp))
                }

                IconButton(
                    onClick = onOpenQueue,
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("player_queue_drawer_button")
                ) {
                    Icon(Icons.Default.KeyboardArrowUp, contentDescription = "Open Queue", tint = colors.accentStrong, modifier = Modifier.size(20.dp))
                }
            }
        }
    }
}

@Composable
private fun ArtworkFocusOverlay(
    song: com.example.model.Song?,
    isPlaying: Boolean,
    onTogglePlayPause: () -> Unit,
    onPlayNext: () -> Unit,
    onPlayPrevious: () -> Unit,
    onExitFocus: () -> Unit
) {
    val colors = LocalEditorialColors.current
    var dragX by remember { mutableFloatStateOf(0f) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
            .statusBarsPadding()
            .navigationBarsPadding()
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragStart = { dragX = 0f },
                    onDragEnd = {
                        if (dragX < -80f) onPlayNext() else if (dragX > 80f) onPlayPrevious()
                        dragX = 0f
                    },
                    onHorizontalDrag = { change, amount ->
                        change.consume()
                        dragX += amount
                    }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 600.dp)
                .padding(horizontal = 24.dp, vertical = 18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = colors.accentPill
                ) {
                    Text(
                        text = "FOCUS VIEW",
                        fontFamily = OutfitFontFamily,
                        color = colors.onAccentPill,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.4.sp,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }

                IconButton(
                    onClick = onExitFocus,
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("exit_focus_mode_button")
                ) {
                    Icon(Icons.Default.FullscreenExit, contentDescription = "Exit Focus View", tint = colors.textPrimary, modifier = Modifier.size(26.dp))
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .shadow(22.dp, RoundedCornerShape(36.dp), ambientColor = colors.shadowColor, spotColor = colors.shadowColor)
                    .clip(RoundedCornerShape(36.dp))
                    .clickable(onClick = onTogglePlayPause),
                contentAlignment = Alignment.Center
            ) {
                ArtworkAlbumView(
                    song = song,
                    modifier = Modifier.fillMaxSize(),
                    cornerRadius = 36.dp,
                    isPlaying = isPlaying
                )
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = song?.title ?: "No Track Selected",
                    fontFamily = OutfitFontFamily,
                    color = colors.textPrimary,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.ExtraBold,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = song?.artist ?: "Unknown Artist",
                    fontFamily = PlusJakartaFontFamily,
                    color = colors.textSecondary,
                    fontSize = 15.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(16.dp))
                Surface(
                    onClick = onTogglePlayPause,
                    modifier = Modifier.size(64.dp),
                    shape = CircleShape,
                    color = colors.accentStrong,
                    shadowElevation = 8.dp
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = if (isPlaying) "Pause" else "Play",
                            tint = colors.onAccentStrong,
                            modifier = Modifier.size(34.dp)
                        )
                    }
                }
            }
        }
    }
}

private data class OutputRouteItem(
    val name: String,
    val subtitle: String,
    val icon: ImageVector,
    val isActive: Boolean
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AudioOutputDeviceSheet(
    connectedDeviceName: String,
    isTwsConnected: Boolean,
    onRecenterBalance: () -> Unit,
    onOpenAudioStudio: () -> Unit,
    onDismiss: () -> Unit
) {
    val colors = LocalEditorialColors.current
    val context = LocalContext.current

    val outputDevices = remember(connectedDeviceName, isTwsConnected) {
        val am = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        val raw = am?.getDevices(AudioManager.GET_DEVICES_OUTPUTS)?.toList().orEmpty()
        val mapped = raw.mapNotNull { dev ->
            when (dev.type) {
                AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
                AudioDeviceInfo.TYPE_BLE_HEADSET,
                AudioDeviceInfo.TYPE_BLE_SPEAKER,
                AudioDeviceInfo.TYPE_BLUETOOTH_SCO -> {
                    val label = dev.productName?.toString()?.takeIf { it.isNotBlank() } ?: "Bluetooth Audio"
                    OutputRouteItem(
                        name = label,
                        subtitle = if (isTwsConnected) "Wireless Hi-Fi • TWS Wide Ready" else "Wireless Bluetooth Route",
                        icon = Icons.Default.BluetoothAudio,
                        isActive = connectedDeviceName.contains(label, ignoreCase = true) || isTwsConnected
                    )
                }
                AudioDeviceInfo.TYPE_WIRED_HEADSET,
                AudioDeviceInfo.TYPE_WIRED_HEADPHONES -> {
                    val label = dev.productName?.toString()?.takeIf { it.isNotBlank() } ?: "Wired Headphones"
                    OutputRouteItem(
                        name = label,
                        subtitle = "3.5mm Analog Output",
                        icon = Icons.Default.Headphones,
                        isActive = connectedDeviceName.contains("Wired", ignoreCase = true)
                    )
                }
                AudioDeviceInfo.TYPE_USB_DEVICE,
                AudioDeviceInfo.TYPE_USB_HEADSET,
                AudioDeviceInfo.TYPE_USB_ACCESSORY -> {
                    val label = dev.productName?.toString()?.takeIf { it.isNotBlank() } ?: "USB DAC / Audio"
                    OutputRouteItem(
                        name = label,
                        subtitle = "USB Digital Audio Output",
                        icon = Icons.Default.Usb,
                        isActive = connectedDeviceName.contains("USB", ignoreCase = true)
                    )
                }
                AudioDeviceInfo.TYPE_BUILTIN_SPEAKER -> {
                    OutputRouteItem(
                        name = "Phone Speaker",
                        subtitle = "Built-in Stereo Speaker",
                        icon = Icons.Default.Speaker,
                        isActive = !isTwsConnected && connectedDeviceName.contains("Speaker", ignoreCase = true)
                    )
                }
                else -> null
            }
        }.distinctBy { it.name }

        mapped.ifEmpty {
            listOf(
                OutputRouteItem(
                    name = connectedDeviceName,
                    subtitle = if (isTwsConnected) "Wireless Hi-Fi • Active" else "Active Android Audio Route",
                    icon = if (isTwsConnected) Icons.Default.BluetoothAudio else Icons.Default.Speaker,
                    isActive = true
                )
            )
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = colors.surface,
        contentColor = colors.textPrimary,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "AUDIO OUTPUT",
                        fontFamily = OutfitFontFamily,
                        color = colors.accentStrong,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.2.sp
                    )
                    Text(
                        text = "Connected Hardware Routes",
                        fontFamily = OutfitFontFamily,
                        color = colors.textPrimary,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                IconButton(onClick = onDismiss, modifier = Modifier.size(48.dp)) {
                    Icon(Icons.Default.Close, contentDescription = "Close", tint = colors.textPrimary)
                }
            }

            outputDevices.forEach { device ->
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = if (device.isActive) colors.accentPill else colors.surfaceElevated,
                    border = BorderStroke(
                        1.dp,
                        if (device.isActive) colors.accentStrong.copy(alpha = 0.45f) else colors.cardBorder
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = device.icon,
                            contentDescription = null,
                            tint = if (device.isActive) colors.onAccentPill else colors.accentStrong,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = device.name,
                                fontFamily = OutfitFontFamily,
                                color = if (device.isActive) colors.onAccentPill else colors.textPrimary,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = device.subtitle,
                                fontFamily = PlusJakartaFontFamily,
                                color = if (device.isActive) colors.onAccentPill.copy(alpha = 0.78f) else colors.textSecondary,
                                fontSize = 12.sp
                            )
                        }
                        if (device.isActive) {
                            Icon(Icons.Default.CheckCircle, contentDescription = "Active", tint = colors.onAccentPill, modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = onRecenterBalance,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = colors.surfaceElevated, contentColor = colors.textPrimary)
                ) {
                    Icon(Icons.Default.Balance, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Recenter Balance", fontFamily = PlusJakartaFontFamily, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }

                Button(
                    onClick = onOpenAudioStudio,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = colors.accentStrong, contentColor = colors.onAccentStrong)
                ) {
                    Icon(Icons.Default.SpatialAudio, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Audio Studio", fontFamily = PlusJakartaFontFamily, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
        }
    }
}
