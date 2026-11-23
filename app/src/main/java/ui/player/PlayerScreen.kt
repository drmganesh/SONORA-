package ui.player

import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioManager
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
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
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.HighQuality
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Lyrics
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Radio
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SpatialAudio
import androidx.compose.material.icons.filled.Speaker
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Usb
import data.artwork.ArtworkDisplayRole
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.PlayerBackgroundStyle
import com.example.model.PlayerStyle
import com.example.ui.components.ArtworkAlbumView
import com.example.ui.theme.OutfitFontFamily
import com.example.ui.theme.PlusJakartaFontFamily
import com.example.util.LyricsExtractor
import com.example.viewmodel.SonoraViewModel
import ui.components.GestureOverlay
import ui.online.OnlineQualitySelectorSheet
import ui.player.styles.GlassPlayer
import ui.player.styles.MaterialYouPlayer
import ui.player.styles.MinimalPlayer
import ui.player.styles.NeonPlayer
import ui.player.styles.OrbitPlayer
import ui.player.styles.PolaroidPlayer
import ui.player.styles.PosterPlayer
import ui.player.styles.SpectrumPlayer
import ui.player.styles.VinylPlayer
import ui.player.styles.WaveformPlayer
import ui.motion.LocalEffectiveMotionState
import ui.motion.LocalTrackTransitionDirection
import ui.motion.SonoraTransitions
import ui.motion.rememberMotionAwareHaptic
import ui.library.OnlinePlaylistPickerDialog
import ui.theme.HomeThemeMode
import ui.theme.LocalDynamicColorPalette
import ui.theme.LocalEditorialColors
import ui.theme.LocalReduceAnimations

/**
 * Unified PlayerScreen Coordinator
 * Hosts all 19 customizable Player Styles on SONORA AESTHETIC UI, plus:
 * - Full Artwork Focus Mode
 * - Player Appearance Customization Screen
 * - Real AudioManager Output Device Sheet + Dual-Earbud Recenter
 * - Quick Playback Speed selector & Unified Gesture HUD Overlay
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
    onOpenLyricsScreen: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val colors = LocalEditorialColors.current
    val motionState = LocalEffectiveMotionState.current
    val reduceAnimations = LocalReduceAnimations.current || !motionState.animationsEnabled
    val trackDirection by viewModel.trackTransitionDirection.collectAsState()
    val palette = LocalDynamicColorPalette.current
    val homeThemeMode by viewModel.homeThemeMode.collectAsState()
    val dynamicColorsEnabled by viewModel.dynamicColorsEnabled.collectAsState()

    val currentPortraitStyle by viewModel.playerStyle.collectAsState()
    val landscapePlayerStyle by viewModel.landscapePlayerStyle.collectAsState()
    val currentSeekbarStyle by viewModel.seekbarStyle.collectAsState()
    val currentSong by viewModel.currentSong.collectAsState()
    val playbackInfo by viewModel.playbackInfo.collectAsState()
    val queue by viewModel.queue.collectAsState()
    val queueIndex by viewModel.queueIndex.collectAsState()
    val currentTrackLyrics by viewModel.currentTrackLyrics.collectAsState()
    val playbackSpeed by viewModel.playbackSpeed.collectAsState()
    val connectedAudioDevice by viewModel.connectedAudioDeviceName.collectAsState()
    val isTwsConnected by viewModel.isTwsConnected.collectAsState()
    val qualityPreference by viewModel.onlineQualityPreference.collectAsState()
    val currentResolvedStream by viewModel.currentResolvedStream.collectAsState()
    val isMeteredNetwork by viewModel.isMeteredNetwork.collectAsState()
    val isNetworkOnline by viewModel.isNetworkOnline.collectAsState()
    val downloadedKeys by viewModel.downloadedSourceKeys.collectAsState()
    val isCurrentDownloaded = remember(currentSong, downloadedKeys) {
        currentSong?.let { viewModel.isSongDownloaded(it) } == true
    }
    val onlineSavedTracks by viewModel.onlineSavedTracks.collectAsState()
    val onlinePlaylists by viewModel.onlinePlaylists.collectAsState()
    val isCurrentSavedOnline = remember(currentSong, onlineSavedTracks) {
        val id = currentSong?.id
        id != null && onlineSavedTracks.any { it.id == id }
    }
    var showOnlinePlaylistPicker by remember { mutableStateOf(false) }

    val playerAppearance by viewModel.playerAppearance.collectAsState()
    val playerGestureConfig by viewModel.playerGestureConfig.collectAsState()
    val gestureController = rememberPlayerGestureController()

    val visualizerFrequencies by viewModel.visualizerFrequencies.collectAsState()
    val waveformSamples by viewModel.waveformSamples.collectAsState()
    val visualizerStyle by viewModel.visualizerStyle.collectAsState()
    var isVisualizerActive by remember { mutableStateOf(false) }

    var isArtworkFocusMode by remember { mutableStateOf(false) }
    var showStyleWheel by remember { mutableStateOf(false) }
    var showCustomizationScreen by remember { mutableStateOf(false) }
    var showQueueSheet by remember { mutableStateOf(false) }
    var showLyricsSheet by remember { mutableStateOf(false) }
    var showSleepTimerDialog by remember { mutableStateOf(false) }
    var showOutputDeviceSheet by remember { mutableStateOf(false) }
    var showAudioEffectsModal by remember { mutableStateOf(false) }
    var showEqualizerModal by remember { mutableStateOf(false) }
    var showQualitySelectorSheet by remember { mutableStateOf(false) }
    var showSongRadioSheet by remember { mutableStateOf(false) }
    val songRadioSession by viewModel.songRadioSession.collectAsState()
    val currentSongRecommendations by viewModel.currentSongRecommendations.collectAsState()
    val isLoadingCurrentSongRecs by viewModel.isLoadingCurrentSongRecommendations.collectAsState()

    BackHandler(enabled = showCustomizationScreen) {
        showCustomizationScreen = false
    }
    BackHandler(enabled = isArtworkFocusMode && !showCustomizationScreen) {
        isArtworkFocusMode = false
    }
    BackHandler(enabled = showStyleWheel && !showCustomizationScreen) {
        showStyleWheel = false
    }

    val openEqualizerAction: () -> Unit = remember(onOpenEqualizer) {
        {
            if (onOpenEqualizer != null) onOpenEqualizer() else showEqualizerModal = true
        }
    }
    val openAudioStudioAction: () -> Unit = remember(onOpenAudioStudio) {
        {
            if (onOpenAudioStudio != null) onOpenAudioStudio() else showAudioEffectsModal = true
        }
    }
    val openQueueAction: () -> Unit = remember(onOpenQueueScreen) {
        {
            if (onOpenQueueScreen != null) onOpenQueueScreen() else showQueueSheet = true
        }
    }
    val openLyricsAction: () -> Unit = remember(onOpenLyricsScreen) {
        {
            if (onOpenLyricsScreen != null) onOpenLyricsScreen() else showLyricsSheet = true
        }
    }

    val renderState = remember(
        currentSong,
        playbackInfo,
        currentSeekbarStyle,
        playerAppearance,
        playerGestureConfig,
        palette,
        visualizerFrequencies,
        waveformSamples,
        isVisualizerActive,
        visualizerStyle,
        queue,
        queueIndex,
        currentTrackLyrics
    ) {
        PlayerRenderState(
            song = currentSong,
            playbackInfo = playbackInfo,
            seekbarStyle = currentSeekbarStyle,
            appearance = playerAppearance,
            gestureConfig = playerGestureConfig,
            dynamicPalette = palette,
            frequencies = visualizerFrequencies,
            waveformSamples = waveformSamples,
            isVisualizerActive = isVisualizerActive,
            visualizerStyle = visualizerStyle,
            isNowPlayingActive = true,
            queue = queue,
            queueIndex = queueIndex,
            rawLyrics = currentTrackLyrics
        )
    }

    val styleCallbacks = remember(openQueueAction, openLyricsAction, openAudioStudioAction, openEqualizerAction, currentSong) {
        PlayerStyleCallbacks(
            onTogglePlayPause = { viewModel.togglePlayPause() },
            onPlayNext = { viewModel.playNext() },
            onPlayPrevious = { viewModel.playPrevious() },
            onSeekTo = { viewModel.seekTo(it) },
            onToggleShuffle = { viewModel.toggleShuffle() },
            onCycleRepeat = { viewModel.cycleRepeatMode() },
            onOpenQueue = openQueueAction,
            onOpenLyrics = openLyricsAction,
            onOpenSleepTimer = { showSleepTimerDialog = true },
            onClose = onClose,
            onArtworkLongPress = { showStyleWheel = true },
            onToggleFavorite = { currentSong?.let { viewModel.toggleFavorite(it) } },
            onOpenAudioEffects = openAudioStudioAction,
            onOpenEqualizer = openEqualizerAction,
            onOpenOutputDevice = { showOutputDeviceSheet = true },
            onToggleFocusMode = { isArtworkFocusMode = true },
            onOpenCustomization = { showCustomizationScreen = true },
            onCyclePlayerStyle = { viewModel.cyclePlayerStyle() },
            onToggleVisualizer = { isVisualizerActive = !isVisualizerActive },
            onCycleVisualizerStyle = { viewModel.cycleVisualizerStyle() }
        )
    }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val glowAlpha = if (homeThemeMode == HomeThemeMode.DYNAMIC_ALBUM_ART || dynamicColorsEnabled) {
        if (colors.isDark) 0.24f else 0.14f
    } else 0.08f
    val rootBackground = when (playerAppearance.backgroundStyle) {
        PlayerBackgroundStyle.AMOLED_PURE -> Color(0xFF000000)
        PlayerBackgroundStyle.SOLID_SURFACE -> colors.surface
        else -> colors.background
    }
    val useThemeBrush = playerAppearance.backgroundStyle == PlayerBackgroundStyle.THEME_DEFAULT ||
        playerAppearance.backgroundStyle == PlayerBackgroundStyle.GRADIENT_GLOW

    val nowPlayingDownloadAction = remember(currentSong, isCurrentDownloaded) {
        currentSong?.let { song ->
            NowPlayingDownloadAction(
                isDownloaded = isCurrentDownloaded,
                onDownloadClick = { viewModel.requestDownloadSong(song) }
            )
        }
    }

    val nowPlayingOnlineLibraryAction = remember(currentSong, isCurrentSavedOnline) {
        currentSong?.takeIf { it.isOnline }?.let { song ->
            NowPlayingOnlineLibraryAction(
                isSaved = isCurrentSavedOnline,
                onToggleSaveClick = { viewModel.toggleSaveOnlineTrack(song) },
                onAddToOnlinePlaylistClick = { showOnlinePlaylistPicker = true }
            )
        }
    }

    val seekCapability = remember(playbackInfo.isSeekable, playbackInfo.isLiveStream) {
        ui.player.seekbar.PlaybackSeekCapability(
            isSeekable = playbackInfo.isSeekable,
            isLiveStream = playbackInfo.isLiveStream
        )
    }

    CompositionLocalProvider(
        LocalTrackTransitionDirection provides trackDirection,
        LocalNowPlayingDownloadAction provides nowPlayingDownloadAction,
        LocalNowPlayingOnlineLibraryAction provides nowPlayingOnlineLibraryAction,
        LocalNowPlayingStopAction provides { viewModel.stopPlayback() },
        ui.player.seekbar.LocalPlaybackSeekCapability provides seekCapability
    ) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .then(
                if (useThemeBrush) Modifier.background(colors.backgroundBrush)
                else Modifier.background(rootBackground)
            )
            .playerScreenEdgeGestures(
                config = playerGestureConfig,
                controller = gestureController,
                onClose = onClose,
                onOpenQueue = openQueueAction
            )
    ) {
        // Configurable Player Background Atmosphere
        when (playerAppearance.backgroundStyle) {
            PlayerBackgroundStyle.THEME_DEFAULT,
            PlayerBackgroundStyle.SOLID_SURFACE,
            PlayerBackgroundStyle.AMOLED_PURE -> Unit

            PlayerBackgroundStyle.GRADIENT_GLOW -> {
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
            }

            PlayerBackgroundStyle.BLURRED_ARTWORK -> {
                ArtworkAlbumView(
                    song = currentSong,
                    modifier = Modifier
                        .fillMaxSize()
                        .alpha(if (colors.isDark) 0.22f else 0.14f),
                    cornerRadius = 0.dp,
                    isPlaying = playbackInfo.isPlaying,
                    artworkRole = ArtworkDisplayRole.NOW_PLAYING
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    rootBackground.copy(alpha = 0.72f),
                                    rootBackground.copy(alpha = 0.56f),
                                    rootBackground.copy(alpha = 0.88f)
                                )
                            )
                        )
                )
            }
        }

        Column(modifier = Modifier.fillMaxSize()) {
            androidx.compose.foundation.layout.BoxWithConstraints(modifier = Modifier.weight(1f)) {
                val isLandscapeContainer = maxWidth > maxHeight && maxWidth >= 560.dp
                val activeStyle = if (isLandscapeContainer) landscapePlayerStyle else currentPortraitStyle

                AnimatedContent(
                    targetState = activeStyle,
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
                    PlayerStyleRenderer(
                        style = targetStyle,
                        state = renderState,
                        callbacks = styleCallbacks,
                        gestureController = gestureController
                    )
                }
            }

            // Online Buffering & Playback Error Status Strip
            val showLoadingStatus = currentSong?.isOnline == true &&
                playbackInfo.isBuffering &&
                playbackInfo.playbackStage != com.example.model.OnlinePlaybackStage.PLAYING &&
                playbackInfo.playbackStage != com.example.model.OnlinePlaybackStage.FAILED &&
                playbackInfo.playbackError.isNullOrBlank()
            if (showLoadingStatus) {
                val stageText = playbackInfo.statusMessage.ifBlank {
                    playbackInfo.playbackStage.defaultMessage
                }.ifBlank { "Buffering audio..." }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 2.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = colors.accentPill.copy(alpha = 0.85f),
                        border = BorderStroke(0.75.dp, colors.accentStrong.copy(alpha = 0.4f)),
                        modifier = Modifier.testTag("player_buffering_pill")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(14.dp),
                                strokeWidth = 2.dp,
                                color = colors.onAccentPill
                            )
                            Text(
                                text = stageText,
                                fontFamily = PlusJakartaFontFamily,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = colors.onAccentPill
                            )
                        }
                    }
                }
            }

            playbackInfo.playbackError?.takeIf { it.isNotBlank() }?.let { errMsg ->
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = colors.surfaceElevated,
                    border = BorderStroke(1.dp, colors.accentStrong.copy(alpha = 0.55f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 18.dp, vertical = 4.dp)
                        .testTag("player_error_banner")
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = errMsg,
                            fontFamily = PlusJakartaFontFamily,
                            fontSize = 12.sp,
                            color = colors.textPrimary,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (playbackInfo.playbackErrorKind == domain.online.OnlineErrorKind.RATE_LIMITED_OR_CAPTCHA) {
                                TextButton(
                                    onClick = { viewModel.switchToSoundCloudFallback() },
                                    modifier = Modifier.testTag("player_soundcloud_fallback_button")
                                ) {
                                    Text(
                                        text = "SoundCloud",
                                        fontFamily = PlusJakartaFontFamily,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp,
                                        color = colors.accentStrong
                                    )
                                }
                            }
                            if (queue.size > 1) {
                                TextButton(
                                    onClick = {
                                        viewModel.clearPlaybackError()
                                        viewModel.playNext(withCrossfade = false)
                                    },
                                    modifier = Modifier.testTag("player_skip_unavailable_button")
                                ) {
                                    Text(
                                        text = "Skip Next",
                                        fontFamily = PlusJakartaFontFamily,
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 12.sp,
                                        color = colors.textSecondary
                                    )
                                }
                            }
                            TextButton(
                                onClick = { viewModel.retryCurrentPlayback() },
                                modifier = Modifier.testTag("player_retry_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = "Retry",
                                    tint = colors.accentStrong,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "Retry",
                                    fontFamily = PlusJakartaFontFamily,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp,
                                    color = colors.accentStrong
                                )
                            }
                        }
                    }
                }
            }

            // Sleek Shared Bottom Utility & Queue Swipe Dock across all 19 Player Styles
            PlayerUtilityStrip(
                connectedDevice = connectedAudioDevice,
                playbackSpeed = playbackSpeed,
                isOnlineTrack = currentSong?.isOnline == true,
                isDownloaded = isCurrentDownloaded,
                isRadioActive = songRadioSession.isActive,
                onDownloadTrack = { currentSong?.let { viewModel.requestDownloadSong(it) } },
                onOpenOutputDevice = { showOutputDeviceSheet = true },
                onCycleSpeed = {
                    val speeds = listOf(0.75f, 1.0f, 1.25f, 1.5f, 2.0f)
                    val next = speeds.firstOrNull { it > playbackSpeed + 0.05f } ?: speeds.first()
                    viewModel.setPlaybackSpeed(next)
                },
                onOpenStreamQuality = { showQualitySelectorSheet = true },
                onOpenSongRadio = {
                    viewModel.loadRecommendationsForCurrentSong(forceRefresh = false)
                    showSongRadioSheet = true
                },
                onOpenLyrics = openLyricsAction,
                onOpenEqualizer = openEqualizerAction,
                onOpenAudioStudio = openAudioStudioAction,
                onToggleFocusMode = { isArtworkFocusMode = true },
                onOpenQueue = openQueueAction
            )
        }

        // Unified Gesture HUD Feedback (Seek / Skip / Volume / Brightness / Zoom)
        GestureOverlay(
            hudState = gestureController.hudState,
            modifier = Modifier.align(Alignment.Center)
        )

        // Full Artwork Focus Mode Overlay
        AnimatedVisibility(
            visible = isArtworkFocusMode,
            enter = if (reduceAnimations) fadeIn(snap()) else fadeIn(tween(motionState.durationMillis(240))) + scaleIn(tween(motionState.durationMillis(240)), initialScale = 0.96f),
            exit = if (reduceAnimations) fadeOut(snap()) else fadeOut(tween(motionState.durationMillis(180)))
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
                currentStyle = currentPortraitStyle,
                onSelectStyle = { newStyle -> viewModel.setPlayerStyle(newStyle) },
                onDismiss = { showStyleWheel = false },
                song = currentSong
            )
        }

        // Online Playlist Picker from Now Playing overflow menu
        if (showOnlinePlaylistPicker) {
            currentSong?.takeIf { it.isOnline }?.let { onlineSong ->
                OnlinePlaylistPickerDialog(
                    song = onlineSong,
                    playlists = onlinePlaylists,
                    colors = colors,
                    onDismiss = { showOnlinePlaylistPicker = false },
                    onSelectPlaylist = { target ->
                        viewModel.addTrackToOnlinePlaylist(target.id, onlineSong, target.name)
                    },
                    onCreatePlaylistWithSong = { name ->
                        viewModel.createOnlinePlaylist(name, initialTrack = onlineSong)
                    }
                )
            }
        }

        // Now Playing Appearance & Style Hub Overlay
        AnimatedVisibility(
            visible = showCustomizationScreen,
            enter = SonoraTransitions.pushEnter(motionState),
            exit = SonoraTransitions.pushExit(motionState)
        ) {
            PlayerAppearanceHubScreen(
                viewModel = viewModel,
                onBack = { showCustomizationScreen = false }
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
                                    if (qSong.isOnline) {
                                        val qDownloaded = viewModel.isSongDownloaded(qSong)
                                        IconButton(
                                            onClick = { viewModel.requestDownloadSong(qSong) },
                                            modifier = Modifier
                                                .size(44.dp)
                                                .testTag("queue_sheet_download_${qSong.id}")
                                        ) {
                                            Icon(
                                                imageVector = if (qDownloaded) Icons.Default.DownloadDone else Icons.Default.Download,
                                                contentDescription = if (qDownloaded) "Downloaded Offline" else "Download Track",
                                                tint = if (qDownloaded) colors.accentStrong else colors.textSecondary,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
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

        // Full-Screen Synchronized Lyrics Fallback Overlay (when PlayerScreen is hosted standalone)
        AnimatedVisibility(
            visible = showLyricsSheet,
            enter = SonoraTransitions.modalEnter(motionState),
            exit = SonoraTransitions.modalExit(motionState)
        ) {
            ui.lyrics.FullScreenLyricsScreen(
                viewModel = viewModel,
                onBack = { showLyricsSheet = false },
                onRequestImportLrc = {}
            )
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

        if (showQualitySelectorSheet) {
            OnlineQualitySelectorSheet(
                currentPreference = qualityPreference,
                resolvedStream = currentResolvedStream,
                isMeteredNetwork = isMeteredNetwork,
                isNetworkOnline = isNetworkOnline,
                colors = colors,
                onSelectPreference = { viewModel.selectOnlineQualityPreference(it) },
                onSelectManualStream = { viewModel.selectManualOnlineStreamOption(it) },
                onDismiss = { showQualitySelectorSheet = false }
            )
        }

        if (showSongRadioSheet) {
            ModalBottomSheet(
                onDismissRequest = { showSongRadioSheet = false },
                sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
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
                        .testTag("song_radio_sheet"),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "SONG RADIO & SIMILAR TRACKS",
                                fontFamily = OutfitFontFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp,
                                letterSpacing = 1.sp,
                                color = colors.accentStrong
                            )
                            Text(
                                text = currentSong?.let { "Based on ${it.title}" } ?: "Similar Recommendations",
                                fontFamily = OutfitFontFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = 17.sp,
                                color = colors.textPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Button(
                            onClick = {
                                if (songRadioSession.isActive) {
                                    viewModel.stopSongRadio()
                                } else {
                                    currentSong?.let { viewModel.startSongRadio(it, autoPlayFirstAdded = false) }
                                }
                            },
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (songRadioSession.isActive) colors.surfaceTinted else colors.accentStrong,
                                contentColor = if (songRadioSession.isActive) colors.textPrimary else colors.onAccentStrong
                            ),
                            modifier = Modifier.testTag("song_radio_toggle_button")
                        ) {
                            Icon(Icons.Default.Radio, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (songRadioSession.isActive) "Stop Radio" else "Start Radio",
                                fontFamily = PlusJakartaFontFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                        }
                    }

                    if (isLoadingCurrentSongRecs) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(120.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(color = colors.accentStrong, strokeWidth = 2.5.dp)
                        }
                    } else if (currentSongRecommendations.isEmpty()) {
                        Text(
                            text = "Tap Start Radio or Refresh to discover continuous playable songs similar to the current track.",
                            fontFamily = PlusJakartaFontFamily,
                            fontSize = 13.sp,
                            color = colors.textSecondary
                        )
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 360.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            itemsIndexed(currentSongRecommendations, key = { idx, s -> "rec_${s.id}_$idx" }) { idx, recSong ->
                                Surface(
                                    onClick = {
                                        viewModel.playSong(recSong, currentSongRecommendations, idx)
                                        showSongRadioSheet = false
                                    },
                                    shape = RoundedCornerShape(16.dp),
                                    color = colors.surfaceTinted,
                                    border = BorderStroke(0.75.dp, colors.cardBorder),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        ArtworkAlbumView(
                                            song = recSong,
                                            modifier = Modifier.size(44.dp),
                                            cornerRadius = 10.dp
                                        )
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = recSong.title,
                                                fontFamily = OutfitFontFamily,
                                                fontWeight = FontWeight.SemiBold,
                                                fontSize = 14.sp,
                                                color = colors.textPrimary,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Text(
                                                text = "${recSong.artist} • ${recSong.durationFormatted}",
                                                fontFamily = PlusJakartaFontFamily,
                                                fontSize = 12.sp,
                                                color = colors.textSecondary,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                        TextButton(onClick = { viewModel.addToQueue(recSong) }) {
                                            Text("Queue", color = colors.accentStrong, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    }
}

@Composable
private fun PlayerUtilityStrip(
    connectedDevice: String,
    playbackSpeed: Float,
    isOnlineTrack: Boolean,
    isDownloaded: Boolean,
    isRadioActive: Boolean,
    onDownloadTrack: () -> Unit,
    onOpenOutputDevice: () -> Unit,
    onCycleSpeed: () -> Unit,
    onOpenStreamQuality: () -> Unit,
    onOpenSongRadio: () -> Unit,
    onOpenLyrics: () -> Unit,
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
                    onClick = onOpenLyrics,
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("player_utility_lyrics_button")
                ) {
                    Icon(Icons.Default.Lyrics, contentDescription = "Full-Screen Lyrics", tint = colors.accentStrong, modifier = Modifier.size(18.dp))
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

                if (isOnlineTrack) {
                    IconButton(
                        onClick = onDownloadTrack,
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("player_download_button")
                    ) {
                        Icon(
                            if (isDownloaded) Icons.Default.DownloadDone else Icons.Default.Download,
                            contentDescription = if (isDownloaded) "Downloaded Offline" else "Download Track",
                            tint = colors.accentStrong,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    IconButton(
                        onClick = onOpenStreamQuality,
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("player_stream_quality_button")
                    ) {
                        Icon(Icons.Default.HighQuality, contentDescription = "Stream Quality", tint = colors.accentStrong, modifier = Modifier.size(18.dp))
                    }
                }

                IconButton(
                    onClick = onOpenSongRadio,
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("player_song_radio_button")
                ) {
                    Icon(
                        Icons.Default.Radio,
                        contentDescription = if (isRadioActive) "Song Radio Active" else "Start Song Radio",
                        tint = if (isRadioActive) colors.accentStrong else colors.textPrimary,
                        modifier = Modifier.size(18.dp)
                    )
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
    val motionState = LocalEffectiveMotionState.current
    val trackDirection = LocalTrackTransitionDirection.current
    val performHaptic = rememberMotionAwareHaptic()
    val density = LocalDensity.current
    val swipeThresholdPx = with(density) { motionState.swipeThresholdDp.dp.toPx() }
    val velocityThresholdPx = with(density) { motionState.velocityThresholdDpPerSec.dp.toPx() }
    var dragX by remember { mutableFloatStateOf(0f) }
    val animatedDragOffset by animateFloatAsState(
        targetValue = if (motionState.animationsEnabled) (dragX * 0.25f).coerceIn(-68f, 68f) else 0f,
        animationSpec = if (motionState.animationsEnabled) spring() else snap(),
        label = "focusOverlayDrag"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.backgroundBrush)
            .statusBarsPadding()
            .navigationBarsPadding()
            .pointerInput(swipeThresholdPx, velocityThresholdPx) {
                var tracker: VelocityTracker? = null
                detectHorizontalDragGestures(
                    onDragStart = {
                        dragX = 0f
                        tracker = VelocityTracker()
                    },
                    onDragCancel = {
                        dragX = 0f
                        tracker = null
                    },
                    onDragEnd = {
                        val velocityX = tracker?.calculateVelocity()?.x ?: 0f
                        val triggered = motionState.shouldTriggerSwipe(
                            dragPx = dragX,
                            velocityPxPerSec = velocityX,
                            thresholdPx = swipeThresholdPx,
                            velocityPxThreshold = velocityThresholdPx
                        )
                        if (triggered) {
                            performHaptic(HapticFeedbackType.TextHandleMove)
                            val isLeftSwipe = if (kotlin.math.abs(dragX) > swipeThresholdPx * 0.45f) dragX < 0f else velocityX < 0f
                            if (isLeftSwipe) onPlayNext() else onPlayPrevious()
                        }
                        dragX = 0f
                        tracker = null
                    },
                    onHorizontalDrag = { change, amount ->
                        change.consume()
                        dragX += amount
                        tracker?.addPosition(change.uptimeMillis, change.position)
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
                    .graphicsLayer {
                        translationX = animatedDragOffset
                    }
                    .shadow(22.dp, RoundedCornerShape(36.dp), ambientColor = colors.shadowColor, spotColor = colors.shadowColor)
                    .clip(RoundedCornerShape(36.dp))
                    .clickable(onClick = {
                        performHaptic(HapticFeedbackType.TextHandleMove)
                        onTogglePlayPause()
                    }),
                contentAlignment = Alignment.Center
            ) {
                AnimatedContent(
                    targetState = song?.id,
                    transitionSpec = { SonoraTransitions.trackTransition(trackDirection, motionState) },
                    modifier = Modifier.fillMaxSize(),
                    label = "focusOverlayArtworkTransition"
                ) { _ ->
                    ArtworkAlbumView(
                        song = song,
                        modifier = Modifier.fillMaxSize(),
                        cornerRadius = 36.dp,
                        isPlaying = isPlaying
                    )
                }
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                AnimatedContent(
                    targetState = song?.id,
                    transitionSpec = { SonoraTransitions.metadataTransition(trackDirection, motionState) },
                    label = "focusOverlayMetaTransition"
                ) { _ ->
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
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
                Surface(
                    onClick = {
                        performHaptic(HapticFeedbackType.TextHandleMove)
                        onTogglePlayPause()
                    },
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
            when {
                dev.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP ||
                    dev.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO ||
                    (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S &&
                        (dev.type == AudioDeviceInfo.TYPE_BLE_HEADSET ||
                            dev.type == AudioDeviceInfo.TYPE_BLE_SPEAKER)) -> {
                    val label = dev.productName?.toString()?.takeIf { it.isNotBlank() } ?: "Bluetooth Audio"
                    OutputRouteItem(
                        name = label,
                        subtitle = if (isTwsConnected) "Wireless Hi-Fi • TWS Wide Ready" else "Wireless Bluetooth Route",
                        icon = Icons.Default.BluetoothAudio,
                        isActive = connectedDeviceName.contains(label, ignoreCase = true) || isTwsConnected
                    )
                }
                dev.type == AudioDeviceInfo.TYPE_WIRED_HEADSET ||
                    dev.type == AudioDeviceInfo.TYPE_WIRED_HEADPHONES -> {
                    val label = dev.productName?.toString()?.takeIf { it.isNotBlank() } ?: "Wired Headphones"
                    OutputRouteItem(
                        name = label,
                        subtitle = "3.5mm Analog Output",
                        icon = Icons.Default.Headphones,
                        isActive = connectedDeviceName.contains("Wired", ignoreCase = true)
                    )
                }
                dev.type == AudioDeviceInfo.TYPE_USB_DEVICE ||
                    dev.type == AudioDeviceInfo.TYPE_USB_HEADSET ||
                    dev.type == AudioDeviceInfo.TYPE_USB_ACCESSORY -> {
                    val label = dev.productName?.toString()?.takeIf { it.isNotBlank() } ?: "USB DAC / Audio"
                    OutputRouteItem(
                        name = label,
                        subtitle = "USB Digital Audio Output",
                        icon = Icons.Default.Usb,
                        isActive = connectedDeviceName.contains("USB", ignoreCase = true)
                    )
                }
                dev.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER -> {
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
