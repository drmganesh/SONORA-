package ui.lyrics

import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.DragInteraction
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AvTimer
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.FileOpen
import androidx.compose.material.icons.rounded.Lyrics
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.MyLocation
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.PlaybackInfo
import com.example.model.Song
import com.example.ui.components.ArtworkAlbumView
import com.example.ui.theme.OutfitFontFamily
import com.example.ui.theme.PlusJakartaFontFamily
import com.example.viewmodel.SonoraViewModel
import domain.lyrics.LyricsAppearanceConfig
import domain.lyrics.LyricsDocument
import domain.lyrics.LyricsFontFamilyOption
import domain.lyrics.LyricsLoadState
import domain.lyrics.LyricsPreset
import domain.lyrics.LyricsSearchCandidate
import domain.lyrics.LyricsSourceOption
import domain.lyrics.LyricsSourceType
import domain.lyrics.LyricsSyncEngine
import domain.lyrics.LyricsSyncType
import domain.lyrics.LyricsTimingState
import domain.lyrics.SyncedLyricLine
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import ui.motion.LocalEffectiveMotionState
import ui.motion.rememberMotionAwareHaptic
import ui.theme.DynamicColorPalette
import ui.theme.EditorialColors
import ui.theme.LocalDynamicColorPalette
import ui.theme.LocalEditorialColors
import ui.theme.LocalReduceAnimations
import kotlin.math.abs

/**
 * Full-Screen Synchronized Lyrics Experience wired to [SonoraViewModel].
 */
@Composable
fun FullScreenLyricsScreen(
    viewModel: SonoraViewModel,
    onBack: () -> Unit,
    onRequestImportLrc: () -> Unit,
    modifier: Modifier = Modifier
) {
    val song by viewModel.currentSong.collectAsState()
    val playbackInfo by viewModel.playbackInfo.collectAsState()
    val lyricsPositionMs by viewModel.lyricsPositionMs.collectAsState()
    val loadState by viewModel.lyricsLoadState.collectAsState()
    val appearance by viewModel.lyricsAppearance.collectAsState()
    val timingState by viewModel.lyricsTimingState.collectAsState()
    val availableSources by viewModel.lyricsAvailableSources.collectAsState()
    val searchResults by viewModel.lyricsSearchResults.collectAsState()
    val isSearching by viewModel.isSearchingLyrics.collectAsState()
    val feedbackMessage by viewModel.lyricsFeedbackMessage.collectAsState()

    val colors = LocalEditorialColors.current
    val palette = LocalDynamicColorPalette.current

    // Activate high-precision Media3 position polling while full-screen lyrics is visible
    DisposableEffect(Unit) {
        viewModel.setFullScreenLyricsVisible(true)
        onDispose {
            viewModel.setFullScreenLyricsVisible(false)
        }
    }

    LaunchedEffect(feedbackMessage) {
        if (feedbackMessage != null) {
            delay(3200L)
            viewModel.clearLyricsFeedback()
        }
    }

    val effectivePositionMs = if (playbackInfo.isPlaying) {
        lyricsPositionMs
    } else {
        playbackInfo.currentPositionMs
    }

    FullScreenLyricsContent(
        song = song,
        playbackInfo = playbackInfo,
        currentPositionMs = effectivePositionMs,
        loadState = loadState,
        appearance = appearance,
        timingState = timingState,
        availableSources = availableSources,
        searchResults = searchResults,
        isSearching = isSearching,
        feedbackMessage = feedbackMessage,
        colors = colors,
        palette = palette,
        onBack = onBack,
        onTogglePlayPause = { viewModel.togglePlayPause() },
        onPlayNext = { viewModel.playNext() },
        onPlayPrevious = { viewModel.playPrevious() },
        onSeekTo = { viewModel.seekTo(it) },
        onSeekToLyricLine = { viewModel.seekToLyricLine(it) },
        onAdjustTimingDelta = { viewModel.adjustLyricsTimingOffset(it) },
        onSetTimingOffset = { viewModel.setLyricsTimingOffset(it) },
        onResetTimingOffset = { viewModel.resetLyricsTimingOffset() },
        onSaveGlobalTimingOffset = { viewModel.setGlobalLyricsTimingOffset(it) },
        onUseGlobalTimingOffset = { viewModel.applyGlobalOffsetToCurrentTrack() },
        onApplyPreset = { viewModel.applyLyricsPreset(it) },
        onUpdateAppearance = { viewModel.updateLyricsAppearance(it) },
        onSelectSource = { viewModel.selectLyricsSource(it) },
        onRefreshLyrics = { viewModel.refreshCurrentLyrics() },
        onRequestImportLrc = onRequestImportLrc,
        onSearchOnline = { q -> viewModel.searchOnlineLyrics(q) },
        onApplySearchCandidate = { viewModel.applyLyricsSearchCandidate(it) },
        onSaveEditedLyrics = { viewModel.saveEditedLyrics(it) },
        onSaveLyricsLocally = { viewModel.saveCurrentLyricsLocally() },
        onDismissFeedback = { viewModel.clearLyricsFeedback() },
        modifier = modifier
    )
}

/**
 * Stateless, testable Full-Screen Synchronized Lyrics Composable.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FullScreenLyricsContent(
    song: Song?,
    playbackInfo: PlaybackInfo,
    currentPositionMs: Long,
    loadState: LyricsLoadState,
    appearance: LyricsAppearanceConfig,
    timingState: LyricsTimingState,
    availableSources: List<LyricsSourceOption>,
    searchResults: List<LyricsSearchCandidate>,
    isSearching: Boolean,
    feedbackMessage: String?,
    colors: EditorialColors,
    palette: DynamicColorPalette,
    onBack: () -> Unit,
    onTogglePlayPause: () -> Unit,
    onPlayNext: () -> Unit,
    onPlayPrevious: () -> Unit,
    onSeekTo: (Long) -> Unit,
    onSeekToLyricLine: (SyncedLyricLine) -> Unit,
    onAdjustTimingDelta: (Long) -> Unit,
    onSetTimingOffset: (Long) -> Unit,
    onResetTimingOffset: () -> Unit,
    onSaveGlobalTimingOffset: (Long) -> Unit,
    onUseGlobalTimingOffset: () -> Unit,
    onApplyPreset: (LyricsPreset) -> Unit,
    onUpdateAppearance: ((LyricsAppearanceConfig) -> LyricsAppearanceConfig) -> Unit,
    onSelectSource: (LyricsSourceType) -> Unit,
    onRefreshLyrics: () -> Unit,
    onRequestImportLrc: () -> Unit,
    onSearchOnline: (String) -> Unit,
    onApplySearchCandidate: (LyricsSearchCandidate) -> Unit,
    onSaveEditedLyrics: (String) -> Unit,
    onSaveLyricsLocally: () -> Unit,
    onDismissFeedback: () -> Unit,
    modifier: Modifier = Modifier
) {
    val motionState = LocalEffectiveMotionState.current
    val reduceAnimations = LocalReduceAnimations.current || !motionState.animationsEnabled
    val performHaptic = rememberMotionAwareHaptic()
    val clipboardManager = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current

    var showTimingSheet by remember { mutableStateOf(false) }
    var showAppearanceSheet by remember { mutableStateOf(false) }
    var showSourceSheet by remember { mutableStateOf(false) }
    var showSearchSheet by remember { mutableStateOf(false) }
    var showEditorSheet by remember { mutableStateOf(false) }
    var localCopyToast by remember { mutableStateOf<String?>(null) }
    var showDoubleTapIndicator by remember { mutableStateOf(false) }

    // Selection mode for copying one or multiple lyric lines on long press
    val selectedLineIndices = remember(song?.id) { mutableStateListOf<Int>() }
    val isSelectionMode by remember { derivedStateOf { selectedLineIndices.isNotEmpty() } }

    BackHandler(enabled = isSelectionMode) {
        selectedLineIndices.clear()
    }
    BackHandler(enabled = !isSelectionMode) {
        onBack()
    }

    LaunchedEffect(localCopyToast) {
        if (localCopyToast != null) {
            delay(2400L)
            localCopyToast = null
        }
    }

    LaunchedEffect(showDoubleTapIndicator) {
        if (showDoubleTapIndicator) {
            delay(650L)
            showDoubleTapIndicator = false
        }
    }

    // Dynamic album-art highlight accent with guaranteed legibility
    val dynamicAccent = remember(palette, colors, appearance.amoledDarkBackground) {
        if (colors.isDark || appearance.amoledDarkBackground) {
            lerp(palette.accent, Color.White, 0.24f)
        } else {
            colors.accentStrong
        }
    }
    val activeHighlightColor = if (appearance.useDynamicHighlightColor) {
        dynamicAccent
    } else {
        Color(appearance.customHighlightColorArgb)
    }

    val isDarkStage = colors.isDark || appearance.amoledDarkBackground
    val primaryTextColor = if (isDarkStage) Color(0xFFF7F8FC) else Color(0xFF14151B)
    val secondaryTextColor = if (isDarkStage) Color(0xFFD0D4E0) else Color(0xFF3E4250)
    val mutedLineColor = if (isDarkStage) Color(0xFFB7BDCE) else Color(0xFF53586B)
    val stageBaseColor = when {
        appearance.amoledDarkBackground -> Color(0xFF000000)
        colors.isDark -> Color(0xFF090B10)
        else -> Color(0xFFF4F5FA)
    }

    val loadedDocument = (loadState as? LyricsLoadState.Loaded)?.document
    val lines = loadedDocument?.lines.orEmpty()
    val embeddedOffsetMs = loadedDocument?.embeddedOffsetMs ?: 0L

    // Compute active line index strictly from Media3 playback position + offset
    val activeLineIndex by remember(lines, currentPositionMs, timingState.effectiveOffsetMs, embeddedOffsetMs) {
        derivedStateOf {
            LyricsSyncEngine.findActiveLineIndex(
                lines = lines,
                playbackPositionMs = currentPositionMs,
                userOffsetMs = timingState.effectiveOffsetMs,
                embeddedOffsetMs = embeddedOffsetMs
            )
        }
    }

    val listState = rememberLazyListState()
    var autoScrollPaused by remember(song?.id) { mutableStateOf(false) }
    var lastAutoScrolledIndex by remember(song?.id) { mutableIntStateOf(-1) }
    var lastTrackedPositionMs by remember(song?.id) { mutableStateOf(currentPositionMs) }

    // Detect genuine manual user drag vs programmatic auto-scroll
    LaunchedEffect(listState) {
        listState.interactionSource.interactions.collect { interaction ->
            if (interaction is DragInteraction.Start) {
                autoScrollPaused = true
            }
        }
    }

    // Detect external seek jumps so active line immediately re-centers if auto-scroll is active
    LaunchedEffect(currentPositionMs) {
        val jump = abs(currentPositionMs - lastTrackedPositionMs)
        lastTrackedPositionMs = currentPositionMs
        if (jump > 2_000L && !autoScrollPaused && activeLineIndex in lines.indices) {
            lastAutoScrolledIndex = -1
        }
    }

    val lyricFontFamily = remember(appearance.fontFamily) {
        when (appearance.fontFamily) {
            LyricsFontFamilyOption.OUTFIT -> OutfitFontFamily
            LyricsFontFamilyOption.PLUS_JAKARTA -> PlusJakartaFontFamily
            LyricsFontFamilyOption.SERIF -> FontFamily.Serif
            LyricsFontFamilyOption.SANS_SERIF -> FontFamily.SansSerif
            LyricsFontFamilyOption.MONOSPACE -> FontFamily.Monospace
        }
    }

    val durationMs = playbackInfo.durationMs.coerceAtLeast(song?.durationMs ?: 0L)

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(stageBaseColor)
            .testTag("full_screen_lyrics_screen")
    ) {
        // 1. Immersive Blurred Album-Art Background (API 31+ RenderEffect blur + API 26-30 safe atmospheric veil)
        if (!appearance.amoledDarkBackground) {
            if (song != null) {
                val blurModifier = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && appearance.backgroundBlurDp > 0.5f) {
                    Modifier.blur(appearance.backgroundBlurDp.dp)
                } else {
                    Modifier
                }
                ArtworkAlbumView(
                    song = song,
                    modifier = Modifier
                        .fillMaxSize()
                        .then(blurModifier)
                        .graphicsLayer {
                            // Subtle scale hides edge artifacts when blurred
                            scaleX = 1.14f
                            scaleY = 1.14f
                        }
                        .alpha(if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) 0.72f else 0.34f),
                    cornerRadius = 0.dp,
                    isPlaying = playbackInfo.isPlaying
                )
            }

            // Dynamic album-art ambient glow mesh
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.radialGradient(
                            colors = listOf(
                                palette.glowColor.copy(alpha = if (isDarkStage) 0.34f else 0.20f),
                                palette.gradientMiddle.copy(alpha = if (isDarkStage) 0.28f else 0.12f),
                                Color.Transparent
                            )
                        )
                    )
            )

            // Readability scrim controlled by user Background Opacity preference
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                stageBaseColor.copy(alpha = (appearance.backgroundOverlayOpacity + 0.16f).coerceIn(0.35f, 0.94f)),
                                stageBaseColor.copy(alpha = appearance.backgroundOverlayOpacity.coerceIn(0.25f, 0.90f)),
                                stageBaseColor.copy(alpha = (appearance.backgroundOverlayOpacity + 0.22f).coerceIn(0.45f, 0.96f))
                            )
                        )
                    )
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            // 2. HEADER (Supports horizontal swipe left/right for Next/Previous track)
            var headerDragX by remember { mutableFloatStateOf(0f) }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp)
                    .pointerInput(Unit) {
                        detectHorizontalDragGestures(
                            onDragStart = { headerDragX = 0f },
                            onDragCancel = { headerDragX = 0f },
                            onDragEnd = {
                                if (abs(headerDragX) > 120f) {
                                    performHaptic(HapticFeedbackType.TextHandleMove)
                                    if (headerDragX < 0f) onPlayNext() else onPlayPrevious()
                                }
                                headerDragX = 0f
                            },
                            onHorizontalDrag = { _, delta -> headerDragX += delta }
                        )
                    },
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = {
                        if (isSelectionMode) selectedLineIndices.clear() else onBack()
                    },
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("lyrics_back_button")
                ) {
                    Icon(
                        imageVector = if (isSelectionMode) Icons.Rounded.Close else Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = if (isSelectionMode) "Cancel selection" else "Back",
                        tint = primaryTextColor
                    )
                }

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = if (isSelectionMode) {
                            "${selectedLineIndices.size} line${if (selectedLineIndices.size == 1) "" else "s"} selected"
                        } else {
                            song?.title ?: "No Track Playing"
                        },
                        fontFamily = OutfitFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = primaryTextColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.testTag("lyrics_header_title")
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = song?.artist ?: "Select a song to view lyrics",
                        fontFamily = PlusJakartaFontFamily,
                        fontSize = 12.5.sp,
                        color = secondaryTextColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.testTag("lyrics_header_artist")
                    )
                    if (loadedDocument != null) {
                        Spacer(modifier = Modifier.height(5.dp))
                        Surface(
                            onClick = { showSourceSheet = true },
                            shape = RoundedCornerShape(50),
                            color = activeHighlightColor.copy(alpha = 0.16f),
                            border = BorderStroke(0.75.dp, activeHighlightColor.copy(alpha = 0.4f)),
                            modifier = Modifier.testTag("lyrics_source_badge")
                        ) {
                            Text(
                                text = "${loadedDocument.syncType.displayLabel} • ${loadedDocument.sourceLabel}",
                                fontFamily = PlusJakartaFontFamily,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 10.5.sp,
                                color = if (isDarkStage) activeHighlightColor else primaryTextColor,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp)
                            )
                        }
                    }
                }

                if (isSelectionMode) {
                    IconButton(
                        onClick = {
                            val copied = selectedLineIndices
                                .sorted()
                                .mapNotNull { idx -> lines.getOrNull(idx)?.text }
                                .joinToString("\n")
                            if (copied.isNotBlank()) {
                                clipboardManager.setText(AnnotatedString(copied))
                                localCopyToast = "Copied ${selectedLineIndices.size} lyric line(s)"
                            }
                            selectedLineIndices.clear()
                        },
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("lyrics_copy_selected_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.ContentCopy,
                            contentDescription = "Copy selected lyrics",
                            tint = activeHighlightColor
                        )
                    }
                } else {
                    IconButton(
                        onClick = { showAppearanceSheet = true },
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("lyrics_settings_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Tune,
                            contentDescription = "Lyrics Appearance Settings",
                            tint = primaryTextColor
                        )
                    }
                }
            }

            // 3. MAIN LYRICS VIEWPORT
            BoxWithConstraints(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onDoubleTap = {
                                performHaptic(HapticFeedbackType.TextHandleMove)
                                onTogglePlayPause()
                                showDoubleTapIndicator = true
                            }
                        )
                    }
            ) {
                val viewportHeightPx = with(density) { maxHeight.toPx() }
                val verticalCenterPadding = (maxHeight * 0.30f).coerceIn(32.dp, 180.dp)

                // Smooth automatic scroll keeping active lyric centered without flickering
                LaunchedEffect(activeLineIndex, autoScrollPaused, lines.size, appearance.scrollSpeed) {
                    if (!autoScrollPaused && activeLineIndex in lines.indices && activeLineIndex != lastAutoScrolledIndex) {
                        lastAutoScrolledIndex = activeLineIndex
                        val centerOffsetPx = -(viewportHeightPx * 0.24f).toInt()
                        if (reduceAnimations) {
                            listState.scrollToItem(index = activeLineIndex, scrollOffset = centerOffsetPx)
                        } else {
                            listState.animateScrollToItem(index = activeLineIndex, scrollOffset = centerOffsetPx)
                        }
                    }
                }

                when (loadState) {
                    LyricsLoadState.Idle -> {
                        LyricsEmptyStateCard(
                            title = "No Track Playing",
                            message = "Start playing any offline or online song to enjoy real-time synchronized lyrics.",
                            accentColor = activeHighlightColor,
                            primaryTextColor = primaryTextColor,
                            secondaryTextColor = secondaryTextColor,
                            isDarkStage = isDarkStage,
                            onSearch = null,
                            onImportLrc = null,
                            onEdit = null
                        )
                    }

                    is LyricsLoadState.Loading -> {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(32.dp)
                                .testTag("lyrics_loading_state"),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            CircularProgressIndicator(
                                color = activeHighlightColor,
                                strokeWidth = 3.dp,
                                modifier = Modifier.size(36.dp)
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = loadState.message,
                                fontFamily = PlusJakartaFontFamily,
                                fontSize = 14.sp,
                                color = secondaryTextColor,
                                textAlign = TextAlign.Center
                            )
                        }
                    }

                    is LyricsLoadState.NotFound, is LyricsLoadState.Error -> {
                        val reason = when (loadState) {
                            is LyricsLoadState.NotFound -> loadState.reason
                            is LyricsLoadState.Error -> loadState.message
                            else -> ""
                        }
                        LyricsEmptyStateCard(
                            title = "Lyrics Not Found",
                            message = reason,
                            accentColor = activeHighlightColor,
                            primaryTextColor = primaryTextColor,
                            secondaryTextColor = secondaryTextColor,
                            isDarkStage = isDarkStage,
                            onSearch = {
                                val defaultQuery = listOfNotNull(
                                    song?.title?.takeIf { it.isNotBlank() },
                                    song?.artist?.takeIf { it.isNotBlank() && !it.equals("Unknown Artist", true) }
                                ).joinToString(" ")
                                if (defaultQuery.isNotBlank()) onSearchOnline(defaultQuery)
                                showSearchSheet = true
                            },
                            onImportLrc = onRequestImportLrc,
                            onEdit = { showEditorSheet = true }
                        )
                    }

                    is LyricsLoadState.Loaded -> {
                        val doc = loadState.document
                        if (doc.isInstrumental && doc.lines.isEmpty()) {
                            LyricsEmptyStateCard(
                                title = "Instrumental Track",
                                message = "This track is marked as instrumental. You can still import an .lrc file or add custom notes.",
                                accentColor = activeHighlightColor,
                                primaryTextColor = primaryTextColor,
                                secondaryTextColor = secondaryTextColor,
                                isDarkStage = isDarkStage,
                                onSearch = { showSearchSheet = true },
                                onImportLrc = onRequestImportLrc,
                                onEdit = { showEditorSheet = true }
                            )
                        } else {
                            LazyColumn(
                                state = listState,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .widthIn(max = 680.dp)
                                    .align(Alignment.Center)
                                    .testTag("lyrics_lazy_column"),
                                contentPadding = PaddingValues(
                                    start = 24.dp,
                                    end = 24.dp,
                                    top = if (doc.isSynchronized) verticalCenterPadding else 20.dp,
                                    bottom = if (doc.isSynchronized) verticalCenterPadding else 40.dp
                                ),
                                verticalArrangement = Arrangement.spacedBy(
                                    (appearance.fontSizeSp * (appearance.lineSpacingMultiplier - 0.85f)).coerceIn(10f, 34f).dp
                                )
                            ) {
                                if (!doc.isSynchronized) {
                                    item(key = "unsynced_banner") {
                                        Surface(
                                            shape = RoundedCornerShape(14.dp),
                                            color = activeHighlightColor.copy(alpha = 0.14f),
                                            border = BorderStroke(0.75.dp, activeHighlightColor.copy(alpha = 0.35f)),
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(bottom = 8.dp)
                                                .testTag("lyrics_unsynced_banner")
                                        ) {
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(horizontal = 14.dp, vertical = 10.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Text(
                                                    text = "Plain text lyrics (no timestamps)",
                                                    fontFamily = PlusJakartaFontFamily,
                                                    fontSize = 12.sp,
                                                    color = primaryTextColor,
                                                    modifier = Modifier.weight(1f)
                                                )
                                                Text(
                                                    text = "Find Synced",
                                                    fontFamily = OutfitFontFamily,
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 12.sp,
                                                    color = activeHighlightColor,
                                                    modifier = Modifier
                                                        .clip(RoundedCornerShape(8.dp))
                                                        .combinedClickable(
                                                            onClick = {
                                                                val q = "${song?.title.orEmpty()} ${song?.artist.orEmpty()}".trim()
                                                                if (q.isNotBlank()) onSearchOnline(q)
                                                                showSearchSheet = true
                                                            }
                                                        )
                                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                                )
                                            }
                                        }
                                    }
                                }

                                itemsIndexed(
                                    items = doc.lines,
                                    key = { index, item -> "${doc.trackKey}_${index}_${item.startTimeMs}" },
                                    contentType = { _, _ -> "lyric_line" }
                                ) { index, line ->
                                    val isCurrent = doc.isSynchronized && index == activeLineIndex
                                    val isPast = doc.isSynchronized && activeLineIndex >= 0 && index < activeLineIndex
                                    val isSelected = selectedLineIndices.contains(index)

                                    SyncedLyricLineRow(
                                        line = line,
                                        isCurrent = isCurrent,
                                        isPast = isPast,
                                        isSynchronizedDoc = doc.isSynchronized,
                                        isSelected = isSelected,
                                        // Only stream high-frequency playback ticks to the currently highlighted line
                                        // so all other visible lyric rows skip recomposition during playback.
                                        currentPositionMs = if (isCurrent) currentPositionMs else 0L,
                                        userOffsetMs = timingState.effectiveOffsetMs,
                                        embeddedOffsetMs = doc.embeddedOffsetMs,
                                        appearance = appearance,
                                        fontFamily = lyricFontFamily,
                                        highlightColor = activeHighlightColor,
                                        primaryTextColor = primaryTextColor,
                                        mutedTextColor = mutedLineColor,
                                        reduceAnimations = reduceAnimations,
                                        onTap = {
                                            if (isSelectionMode) {
                                                if (isSelected) selectedLineIndices.remove(index)
                                                else selectedLineIndices.add(index)
                                            } else if (line.isTimed) {
                                                performHaptic(HapticFeedbackType.TextHandleMove)
                                                autoScrollPaused = false
                                                lastAutoScrolledIndex = -1
                                                onSeekToLyricLine(line)
                                            }
                                        },
                                        onDoubleTap = {
                                            performHaptic(HapticFeedbackType.TextHandleMove)
                                            onTogglePlayPause()
                                            showDoubleTapIndicator = true
                                        },
                                        onLongPress = {
                                            performHaptic(HapticFeedbackType.LongPress)
                                            if (!isSelected) {
                                                selectedLineIndices.add(index)
                                                clipboardManager.setText(AnnotatedString(line.text))
                                                localCopyToast = "Line copied — tap more lines to select"
                                            } else {
                                                selectedLineIndices.remove(index)
                                            }
                                        }
                                    )
                                }
                            }
                        }
                    }
                }

                // Floating "Resume Auto Scroll" button when user manually scrolls synchronized lyrics
                androidx.compose.animation.AnimatedVisibility(
                    visible = autoScrollPaused && loadedDocument?.isSynchronized == true,
                    enter = fadeIn(tween(180)) + slideInVertically { it / 2 },
                    exit = fadeOut(tween(160)) + slideOutVertically { it / 2 },
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 14.dp)
                ) {
                    Surface(
                        onClick = {
                            performHaptic(HapticFeedbackType.TextHandleMove)
                            autoScrollPaused = false
                            lastAutoScrolledIndex = -1
                            if (activeLineIndex in lines.indices) {
                                scope.launch {
                                    val centerOffsetPx = -(viewportHeightPx * 0.24f).toInt()
                                    if (reduceAnimations) {
                                        listState.scrollToItem(activeLineIndex, centerOffsetPx)
                                    } else {
                                        listState.animateScrollToItem(activeLineIndex, centerOffsetPx)
                                    }
                                }
                            }
                        },
                        shape = RoundedCornerShape(50),
                        color = activeHighlightColor,
                        shadowElevation = 8.dp,
                        modifier = Modifier
                            .heightIn(min = 44.dp)
                            .testTag("lyrics_resume_auto_scroll")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.MyLocation,
                                contentDescription = null,
                                tint = Color(0xFF101014),
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = "Resume Auto Scroll",
                                fontFamily = OutfitFontFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = Color(0xFF101014)
                            )
                        }
                    }
                }

                // Brief visual HUD pulse on double-tap play/pause
                androidx.compose.animation.AnimatedVisibility(
                    visible = showDoubleTapIndicator,
                    enter = fadeIn(tween(120)) + scaleIn(initialScale = 0.8f),
                    exit = fadeOut(tween(240)) + scaleOut(targetScale = 1.1f),
                    modifier = Modifier.align(Alignment.Center)
                ) {
                    Surface(
                        shape = CircleShape,
                        color = Color(0xAA000000),
                        border = BorderStroke(1.dp, activeHighlightColor.copy(alpha = 0.5f)),
                        modifier = Modifier.size(72.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = if (playbackInfo.isPlaying) Icons.Rounded.PlayArrow else Icons.Rounded.Pause,
                                contentDescription = null,
                                tint = activeHighlightColor,
                                modifier = Modifier.size(36.dp)
                            )
                        }
                    }
                }

                // Feedback / Copy Toast Banner
                val activeToast = localCopyToast ?: feedbackMessage
                androidx.compose.animation.AnimatedVisibility(
                    visible = activeToast != null,
                    enter = fadeIn(tween(180)) + slideInVertically { -it / 2 },
                    exit = fadeOut(tween(180)) + slideOutVertically { -it / 2 },
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 8.dp, start = 20.dp, end = 20.dp)
                ) {
                    Surface(
                        onClick = {
                            localCopyToast = null
                            onDismissFeedback()
                        },
                        shape = RoundedCornerShape(50),
                        color = colors.surfaceElevated.copy(alpha = 0.94f),
                        border = BorderStroke(1.dp, activeHighlightColor.copy(alpha = 0.45f)),
                        shadowElevation = 6.dp,
                        modifier = Modifier.testTag("lyrics_feedback_toast")
                    ) {
                        Text(
                            text = activeToast.orEmpty(),
                            fontFamily = PlusJakartaFontFamily,
                            fontWeight = FontWeight.Medium,
                            fontSize = 12.sp,
                            color = colors.textPrimary,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                        )
                    }
                }
            }

            // 4. FOOTER PLAYBACK & TIMING DOCK (Also supports horizontal swipe for Next/Prev)
            var footerDragX by remember { mutableFloatStateOf(0f) }
            Surface(
                shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
                color = if (appearance.amoledDarkBackground) {
                    Color(0xFF0A0A0D)
                } else if (isDarkStage) {
                    Color(0x9911141E)
                } else {
                    Color(0xCCFFFFFF)
                },
                border = BorderStroke(0.75.dp, primaryTextColor.copy(alpha = 0.12f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .pointerInput(Unit) {
                        detectHorizontalDragGestures(
                            onDragStart = { footerDragX = 0f },
                            onDragCancel = { footerDragX = 0f },
                            onDragEnd = {
                                if (abs(footerDragX) > 120f) {
                                    performHaptic(HapticFeedbackType.TextHandleMove)
                                    if (footerDragX < 0f) onPlayNext() else onPlayPrevious()
                                }
                                footerDragX = 0f
                            },
                            onHorizontalDrag = { _, delta -> footerDragX += delta }
                        )
                    }
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 12.dp)
                ) {
                    // Interactive Seek Slider + Playback Time
                    val sliderMax = durationMs.coerceAtLeast(1L).toFloat()
                    var isDraggingSlider by remember { mutableStateOf(false) }
                    var sliderDraftPos by remember { mutableFloatStateOf(0f) }
                    val displayedPosMs = if (isDraggingSlider) sliderDraftPos.toLong() else currentPositionMs.coerceIn(0L, durationMs.coerceAtLeast(0L))

                    Slider(
                        value = displayedPosMs.toFloat().coerceIn(0f, sliderMax),
                        onValueChange = {
                            isDraggingSlider = true
                            sliderDraftPos = it
                        },
                        onValueChangeFinished = {
                            isDraggingSlider = false
                            autoScrollPaused = false
                            lastAutoScrolledIndex = -1
                            onSeekTo(sliderDraftPos.toLong())
                        },
                        valueRange = 0f..sliderMax,
                        colors = SliderDefaults.colors(
                            thumbColor = activeHighlightColor,
                            activeTrackColor = activeHighlightColor,
                            inactiveTrackColor = primaryTextColor.copy(alpha = 0.2f)
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(28.dp)
                            .testTag("lyrics_progress_slider")
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = formatLyricsClock(displayedPosMs),
                            fontFamily = PlusJakartaFontFamily,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = secondaryTextColor,
                            modifier = Modifier.testTag("lyrics_current_time")
                        )
                        Text(
                            text = formatLyricsClock(durationMs),
                            fontFamily = PlusJakartaFontFamily,
                            fontSize = 11.sp,
                            color = secondaryTextColor
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    // Transport Controls + Timing & Source Shortcuts
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Sources Shortcut
                        IconButton(
                            onClick = { showSourceSheet = true },
                            modifier = Modifier
                                .size(48.dp)
                                .testTag("lyrics_sources_button")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Lyrics,
                                contentDescription = "Lyrics Sources & Editor",
                                tint = secondaryTextColor,
                                modifier = Modifier.size(22.dp)
                            )
                        }

                        // Previous Track
                        IconButton(
                            onClick = {
                                performHaptic(HapticFeedbackType.TextHandleMove)
                                autoScrollPaused = false
                                lastAutoScrolledIndex = -1
                                onPlayPrevious()
                            },
                            modifier = Modifier
                                .size(48.dp)
                                .testTag("lyrics_previous_button")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.SkipPrevious,
                                contentDescription = "Previous track",
                                tint = primaryTextColor,
                                modifier = Modifier.size(32.dp)
                            )
                        }

                        // Primary Play/Pause Button
                        Surface(
                            onClick = {
                                performHaptic(HapticFeedbackType.TextHandleMove)
                                onTogglePlayPause()
                            },
                            shape = CircleShape,
                            color = activeHighlightColor,
                            shadowElevation = 6.dp,
                            modifier = Modifier
                                .size(58.dp)
                                .testTag("lyrics_play_pause_button")
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                AnimatedContent(
                                    targetState = playbackInfo.isPlaying,
                                    transitionSpec = {
                                        if (reduceAnimations) fadeIn(snap()) togetherWith fadeOut(snap())
                                        else (scaleIn(tween(150), initialScale = 0.75f) + fadeIn(tween(150))) togetherWith
                                            (scaleOut(tween(120), targetScale = 0.75f) + fadeOut(tween(120)))
                                    },
                                    label = "lyricsPlayPauseIcon"
                                ) { playing ->
                                    Icon(
                                        imageVector = if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                                        contentDescription = if (playing) "Pause" else "Play",
                                        tint = Color(0xFF101014),
                                        modifier = Modifier.size(30.dp)
                                    )
                                }
                            }
                        }

                        // Next Track
                        IconButton(
                            onClick = {
                                performHaptic(HapticFeedbackType.TextHandleMove)
                                autoScrollPaused = false
                                lastAutoScrolledIndex = -1
                                onPlayNext()
                            },
                            modifier = Modifier
                                .size(48.dp)
                                .testTag("lyrics_next_button")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.SkipNext,
                                contentDescription = "Next track",
                                tint = primaryTextColor,
                                modifier = Modifier.size(32.dp)
                            )
                        }

                        // Timing Adjustment Shortcut with live offset badge
                        Surface(
                            onClick = {
                                performHaptic(HapticFeedbackType.TextHandleMove)
                                showTimingSheet = true
                            },
                            shape = RoundedCornerShape(14.dp),
                            color = if (timingState.effectiveOffsetMs != 0L) {
                                activeHighlightColor.copy(alpha = 0.2f)
                            } else {
                                Color.Transparent
                            },
                            border = BorderStroke(
                                width = 1.dp,
                                color = if (timingState.effectiveOffsetMs != 0L) {
                                    activeHighlightColor.copy(alpha = 0.55f)
                                } else {
                                    primaryTextColor.copy(alpha = 0.2f)
                                }
                            ),
                            modifier = Modifier
                                .heightIn(min = 48.dp)
                                .testTag("lyrics_timing_shortcut_button")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.AvTimer,
                                    contentDescription = "Lyrics timing adjustment",
                                    tint = if (timingState.effectiveOffsetMs != 0L) activeHighlightColor else secondaryTextColor,
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    text = if (timingState.effectiveOffsetMs == 0L) "Sync" else timingState.formattedOffset(),
                                    fontFamily = PlusJakartaFontFamily,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp,
                                    color = if (timingState.effectiveOffsetMs != 0L) activeHighlightColor else secondaryTextColor
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // Modal Bottom Sheets
    if (showTimingSheet) {
        LyricsTimingSheet(
            timingState = timingState,
            accentColor = activeHighlightColor,
            colors = colors,
            onAdjustDelta = onAdjustTimingDelta,
            onSetOffset = onSetTimingOffset,
            onReset = onResetTimingOffset,
            onSaveAsGlobalDefault = onSaveGlobalTimingOffset,
            onUseGlobalDefault = onUseGlobalTimingOffset,
            onDismiss = { showTimingSheet = false }
        )
    }

    if (showAppearanceSheet) {
        LyricsAppearanceSheet(
            config = appearance,
            dynamicAccentColor = dynamicAccent,
            colors = colors,
            onApplyPreset = onApplyPreset,
            onUpdateConfig = onUpdateAppearance,
            onDismiss = { showAppearanceSheet = false }
        )
    }

    if (showSourceSheet) {
        LyricsSourceManagerSheet(
            document = loadedDocument,
            availableSources = availableSources,
            accentColor = activeHighlightColor,
            colors = colors,
            onSelectSource = onSelectSource,
            onOpenSearch = {
                val defaultQuery = listOfNotNull(
                    song?.title?.takeIf { it.isNotBlank() },
                    song?.artist?.takeIf { it.isNotBlank() && !it.equals("Unknown Artist", true) }
                ).joinToString(" ")
                if (defaultQuery.isNotBlank()) onSearchOnline(defaultQuery)
                showSearchSheet = true
            },
            onRefresh = onRefreshLyrics,
            onImportLrc = onRequestImportLrc,
            onOpenEditor = { showEditorSheet = true },
            onSaveLocally = onSaveLyricsLocally,
            onDismiss = { showSourceSheet = false }
        )
    }

    if (showSearchSheet) {
        val defaultQuery = remember(song?.id) {
            listOfNotNull(
                song?.title?.takeIf { it.isNotBlank() },
                song?.artist?.takeIf { it.isNotBlank() && !it.equals("Unknown Artist", true) }
            ).joinToString(" ")
        }
        LyricsOnlineSearchSheet(
            initialQuery = defaultQuery,
            results = searchResults,
            isSearching = isSearching,
            accentColor = activeHighlightColor,
            colors = colors,
            onSearch = onSearchOnline,
            onSelectCandidate = onApplySearchCandidate,
            onDismiss = { showSearchSheet = false }
        )
    }

    if (showEditorSheet) {
        LyricsEditorSheet(
            initialRawText = loadedDocument?.rawText.orEmpty(),
            currentPositionMs = currentPositionMs,
            accentColor = activeHighlightColor,
            colors = colors,
            onSave = onSaveEditedLyrics,
            onDismiss = { showEditorSheet = false }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SyncedLyricLineRow(
    line: SyncedLyricLine,
    isCurrent: Boolean,
    isPast: Boolean,
    isSynchronizedDoc: Boolean,
    isSelected: Boolean,
    currentPositionMs: Long,
    userOffsetMs: Long,
    embeddedOffsetMs: Long,
    appearance: LyricsAppearanceConfig,
    fontFamily: FontFamily,
    highlightColor: Color,
    primaryTextColor: Color,
    mutedTextColor: Color,
    reduceAnimations: Boolean,
    onTap: () -> Unit,
    onDoubleTap: () -> Unit,
    onLongPress: () -> Unit
) {
    val animSpec = if (reduceAnimations) snap<Float>() else spring(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = Spring.StiffnessMediumLow
    )
    val targetScale = when {
        !isSynchronizedDoc -> 1f
        isCurrent -> appearance.currentLineScale
        else -> 0.96f
    }
    val animatedScale by animateFloatAsState(
        targetValue = targetScale,
        animationSpec = animSpec,
        label = "lyricLineScale"
    )
    val targetAlpha = when {
        isSelected -> 1f
        !isSynchronizedDoc -> 0.92f
        isCurrent -> 1f
        isPast -> 0.36f
        else -> 0.56f
    }
    val animatedAlpha by animateFloatAsState(
        targetValue = targetAlpha,
        animationSpec = if (reduceAnimations) snap() else tween(260),
        label = "lyricLineAlpha"
    )
    val animatedLineColor by animateColorAsState(
        targetValue = when {
            !isSynchronizedDoc -> primaryTextColor
            isCurrent -> highlightColor
            isPast -> mutedTextColor
            else -> primaryTextColor
        },
        animationSpec = if (reduceAnimations) snap() else tween(240),
        label = "lyricLineColor"
    )

    val useWordKaraoke = isCurrent && line.hasWordTiming && appearance.wordHighlightEnabled
    val annotatedLine = if (useWordKaraoke) {
        val wordState = LyricsSyncEngine.findWordHighlightState(
            line = line,
            playbackPositionMs = currentPositionMs,
            userOffsetMs = userOffsetMs,
            embeddedOffsetMs = embeddedOffsetMs
        )
        buildAnnotatedString {
            line.words.forEachIndexed { wordIdx, word ->
                val wordColor = when {
                    wordIdx < wordState.activeWordIndex -> highlightColor
                    wordIdx == wordState.activeWordIndex -> lerp(
                        primaryTextColor.copy(alpha = 0.55f),
                        highlightColor,
                        wordState.activeWordProgress.coerceIn(0.25f, 1f)
                    )
                    else -> primaryTextColor.copy(alpha = 0.44f)
                }
                val start = length
                append(word.text)
                addStyle(
                    style = SpanStyle(
                        color = wordColor,
                        fontWeight = if (wordIdx <= wordState.activeWordIndex) FontWeight.ExtraBold else FontWeight.SemiBold
                    ),
                    start = start,
                    end = length
                )
            }
        }
    } else {
        null
    }

    val containerModifier = Modifier
        .fillMaxWidth()
        .clip(RoundedCornerShape(16.dp))
        .background(
            when {
                isSelected -> highlightColor.copy(alpha = 0.22f)
                isCurrent && appearance.preset == LyricsPreset.KARAOKE -> highlightColor.copy(alpha = 0.10f)
                else -> Color.Transparent
            }
        )
        .combinedClickable(
            onClick = onTap,
            onDoubleClick = onDoubleTap,
            onLongClick = onLongPress
        )
        .padding(horizontal = 10.dp, vertical = 6.dp)
        .graphicsLayer {
            scaleX = animatedScale
            scaleY = animatedScale
            alpha = animatedAlpha
            transformOrigin = TransformOrigin(0f, 0.5f)
        }
        .testTag(if (isCurrent) "lyrics_active_line_${line.index}" else "lyrics_line_${line.index}")

    Box(modifier = containerModifier) {
        if (annotatedLine != null) {
            Text(
                text = annotatedLine,
                fontFamily = fontFamily,
                fontSize = appearance.fontSizeSp.sp,
                lineHeight = (appearance.fontSizeSp * appearance.lineSpacingMultiplier).sp,
                fontWeight = FontWeight.ExtraBold,
                modifier = Modifier.testTag("lyrics_word_synced_line_${line.index}")
            )
        } else {
            Text(
                text = line.text,
                fontFamily = fontFamily,
                fontSize = appearance.fontSizeSp.sp,
                lineHeight = (appearance.fontSizeSp * appearance.lineSpacingMultiplier).sp,
                fontWeight = if (isCurrent) FontWeight.ExtraBold else FontWeight.SemiBold,
                color = animatedLineColor
            )
        }
    }
}

@Composable
private fun LyricsEmptyStateCard(
    title: String,
    message: String,
    accentColor: Color,
    primaryTextColor: Color,
    secondaryTextColor: Color,
    isDarkStage: Boolean,
    onSearch: (() -> Unit)?,
    onImportLrc: (() -> Unit)?,
    onEdit: (() -> Unit)?
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 16.dp)
            .testTag("lyrics_empty_state"),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = if (isDarkStage) Color(0x88161925) else Color(0xCCFFFFFF),
            border = BorderStroke(1.dp, accentColor.copy(alpha = 0.28f)),
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 460.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 26.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Surface(
                    shape = CircleShape,
                    color = accentColor.copy(alpha = 0.16f),
                    modifier = Modifier.size(56.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Rounded.MusicNote,
                            contentDescription = null,
                            tint = accentColor,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(14.dp))
                Text(
                    text = title,
                    fontFamily = OutfitFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp,
                    color = primaryTextColor,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = message,
                    fontFamily = PlusJakartaFontFamily,
                    fontSize = 13.5.sp,
                    lineHeight = 20.sp,
                    color = secondaryTextColor,
                    textAlign = TextAlign.Center
                )

                if (onSearch != null || onImportLrc != null || onEdit != null) {
                    Spacer(modifier = Modifier.height(20.dp))
                    if (onSearch != null) {
                        Button(
                            onClick = onSearch,
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = accentColor),
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp)
                                .testTag("lyrics_empty_search_button")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Search,
                                contentDescription = null,
                                tint = Color(0xFF101014),
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Search Online Lyrics",
                                fontFamily = OutfitFontFamily,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF101014)
                            )
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        if (onImportLrc != null) {
                            OutlinedButton(
                                onClick = onImportLrc,
                                shape = RoundedCornerShape(14.dp),
                                border = BorderStroke(1.dp, accentColor.copy(alpha = 0.5f)),
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(min = 48.dp)
                                    .testTag("lyrics_empty_import_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.FileOpen,
                                    contentDescription = null,
                                    tint = accentColor,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Import .LRC",
                                    fontFamily = PlusJakartaFontFamily,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 12.5.sp,
                                    color = primaryTextColor
                                )
                            }
                        }
                        if (onEdit != null) {
                            OutlinedButton(
                                onClick = onEdit,
                                shape = RoundedCornerShape(14.dp),
                                border = BorderStroke(1.dp, primaryTextColor.copy(alpha = 0.25f)),
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(min = 48.dp)
                                    .testTag("lyrics_empty_edit_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Edit,
                                    contentDescription = null,
                                    tint = primaryTextColor,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Add Lyrics",
                                    fontFamily = PlusJakartaFontFamily,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 12.5.sp,
                                    color = primaryTextColor
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun formatLyricsClock(ms: Long): String {
    val safeSeconds = (ms.coerceAtLeast(0L) / 1000L)
    val minutes = safeSeconds / 60L
    val seconds = safeSeconds % 60L
    return String.format(java.util.Locale.US, "%d:%02d", minutes, seconds)
}
