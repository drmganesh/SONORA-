package ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.Lyrics
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.MiniPlayerStyle
import com.example.model.PlaybackInfo
import com.example.model.Song
import com.example.ui.components.ArtworkAlbumView
import com.example.ui.theme.OutfitFontFamily
import com.example.ui.theme.PlusJakartaFontFamily
import kotlinx.coroutines.launch
import ui.motion.GestureAxisLock
import ui.motion.LocalEffectiveMotionState
import ui.motion.MiniPlayerGestureAction
import ui.motion.MiniPlayerGestureMath
import ui.motion.SonoraTransitions
import ui.motion.rememberMotionAwareHaptic
import ui.theme.EditorialColors
import kotlin.math.abs

/**
 * Multi-variant Floating Mini-Player seamlessly aligned above the Floating Bottom Navigation Bar.
 * Supports all 20 mini-player visual styles with:
 * - Horizontal swipe left/right to skip to next/previous track with live drag feedback
 * - Vertical swipe up to expand smoothly into the full music player
 * - Vertical swipe down to collapse into Stage 1 circular mini-player (or dismiss UI) without stopping playback
 * - Tap-to-open Now Playing, explicit playback controls, and conflict-free interactive seek scrubbing.
 */
@Composable
fun FloatingMiniPlayer(
    song: Song?,
    playbackInfo: PlaybackInfo,
    colors: EditorialColors,
    onTogglePlayPause: () -> Unit,
    onPlayNext: () -> Unit,
    onPlayPrevious: () -> Unit,
    onClick: () -> Unit,
    onSeekTo: (Long) -> Unit = {},
    onOpenLyrics: (() -> Unit)? = null,
    onSwipeUpExpand: () -> Unit = onClick,
    onSwipeDownCollapse: (() -> Unit)? = null,
    onSwipeDownDismiss: (() -> Unit)? = null,
    miniPlayerStyle: MiniPlayerStyle = MiniPlayerStyle.DEFAULT,
    trackDirection: Int = 0,
    frequencies: FloatArray = FloatArray(0),
    queue: List<Song> = emptyList(),
    queueIndex: Int = 0,
    weatherPlan: domain.weather.WeatherAtmospherePlan = domain.weather.WeatherAtmospherePlan.INACTIVE,
    weatherSettings: data.preferences.WeatherAmbientSettings = data.preferences.WeatherAmbientSettings.DEFAULT,
    modifier: Modifier = Modifier
) {
    if (song == null) return

    val dynamicPalette = ui.theme.LocalDynamicColorPalette.current
    val motionState = LocalEffectiveMotionState.current
    val performHaptic = rememberMotionAwareHaptic()
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current

    val progress = if (playbackInfo.durationMs > 0L) {
        (playbackInfo.currentPositionMs.toFloat() / playbackInfo.durationMs.toFloat()).coerceIn(0f, 1f)
    } else 0f

    var isSeekbarScrubbing by remember { mutableStateOf(false) }
    val latestIsSeekbarScrubbing by rememberUpdatedState(isSeekbarScrubbing)
    val latestOnSeekTo by rememberUpdatedState(onSeekTo)
    val latestOnPlayNext by rememberUpdatedState(onPlayNext)
    val latestOnPlayPrevious by rememberUpdatedState(onPlayPrevious)
    val latestOnSwipeUpExpand by rememberUpdatedState(onSwipeUpExpand)
    val latestOnSwipeDownAction by rememberUpdatedState(onSwipeDownCollapse ?: onSwipeDownDismiss)
    val latestMotionState by rememberUpdatedState(motionState)

    val miniSeekController = remember(playbackInfo) {
        MiniSeekController(
            playbackInfo = playbackInfo,
            onSeekTo = { targetMs -> latestOnSeekTo(targetMs) },
            onScrubbingChanged = { scrubbing -> isSeekbarScrubbing = scrubbing }
        )
    }

    val dragOffsetX = remember { Animatable(0f) }
    val dragOffsetY = remember { Animatable(0f) }
    val horizontalThresholdPx = with(density) { 52.dp.toPx() }
    val verticalUpThresholdPx = with(density) { 32.dp.toPx() }
    val verticalDownThresholdPx = with(density) { 36.dp.toPx() }
    val velocityThresholdPx = with(density) { 480.dp.toPx() }
    val maxHorizontalDragPx = with(density) { 78.dp.toPx() }
    val maxVerticalDragPx = with(density) { 62.dp.toPx() }

    val isCompactFloatingStyle = miniPlayerStyle == MiniPlayerStyle.CIRCULAR_PLAYER ||
        miniPlayerStyle == MiniPlayerStyle.FLOATING_CORNER_PLAYER
    val shape = when (miniPlayerStyle) {
        MiniPlayerStyle.EDGE_TO_EDGE_MINI -> RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp)
        MiniPlayerStyle.COMPACT_BAR -> RoundedCornerShape(16.dp)
        MiniPlayerStyle.ARTWORK_FOCUS, MiniPlayerStyle.BLUR_CARD, MiniPlayerStyle.EXPANDABLE_CARD -> RoundedCornerShape(22.dp)
        MiniPlayerStyle.WAVE_MINI, MiniPlayerStyle.NEON_MINI, MiniPlayerStyle.CASSETTE_MINI -> RoundedCornerShape(20.dp)
        MiniPlayerStyle.CIRCULAR_PLAYER, MiniPlayerStyle.FLOATING_CORNER_PLAYER -> RoundedCornerShape(32.dp)
        MiniPlayerStyle.MATERIAL_YOU_MINI -> RoundedCornerShape(26.dp)
        else -> RoundedCornerShape(24.dp)
    }
    val surfaceColor = when (miniPlayerStyle) {
        MiniPlayerStyle.ARTWORK_FOCUS -> colors.surfaceTinted
        MiniPlayerStyle.WAVE_MINI -> colors.surfaceElevated.copy(alpha = 0.94f)
        MiniPlayerStyle.AMOLED_BAR -> androidx.compose.ui.graphics.Color.Black
        MiniPlayerStyle.NEON_MINI -> androidx.compose.ui.graphics.Color(0xFF080912)
        else -> colors.miniPlayerBackground
    }
    val sourceLabel = if (song.isOnline) "ONLINE" else "LOCAL"
    val badge = remember(song.isOnline, song.streamCodec, song.providerName, song.mimeType, song.dataPath) {
        when {
            song.isOnline -> song.streamCodec.ifBlank { song.providerName.ifBlank { "STREAM" } }.uppercase()
            song.mimeType.contains("flac", ignoreCase = true) ||
                song.dataPath.endsWith(".flac", ignoreCase = true) -> "FLAC"
            else -> "HI-RES"
        }
    }

    val fontScale = density.fontScale

    BoxWithConstraints(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = when (miniPlayerStyle) {
            MiniPlayerStyle.FLOATING_CORNER_PLAYER -> Alignment.CenterEnd
            MiniPlayerStyle.CIRCULAR_PLAYER -> Alignment.Center
            else -> Alignment.Center
        }
    ) {
        val containerWidth = maxWidth
        val adaptiveHorizontalPad = when (miniPlayerStyle) {
            MiniPlayerStyle.EDGE_TO_EDGE_MINI -> 0.dp
            MiniPlayerStyle.COMPACT_BAR -> when {
                containerWidth < 340.dp -> 6.dp
                containerWidth < 380.dp -> 10.dp
                else -> 14.dp
            }
            MiniPlayerStyle.FLOATING_CORNER_PLAYER -> if (containerWidth < 360.dp) 10.dp else 16.dp
            else -> when {
                containerWidth < 340.dp -> 6.dp
                containerWidth < 390.dp -> 10.dp
                else -> 20.dp
            }
        }
        val innerRowPadH = if (containerWidth < 350.dp) 8.dp else 12.dp
        val controlBtnWidth = when {
            containerWidth < 340.dp -> 40.dp
            containerWidth < 380.dp -> 44.dp
            else -> 48.dp
        }
        val playBtnWidth = when {
            containerWidth < 340.dp -> 44.dp
            else -> 48.dp
        }
        val controlClusterWidth = (if (onOpenLyrics != null) controlBtnWidth else 0.dp) + playBtnWidth + controlBtnWidth + 8.dp
        val cardContentWidth = containerWidth - (adaptiveHorizontalPad * 2) - (innerRowPadH * 2)
        val availableTextWidth = cardContentWidth - 54.dp - controlClusterWidth
        val showSecondaryCodecBadge = availableTextWidth >= 175.dp && fontScale <= 1.15f
        val showSourceBadge = availableTextWidth >= 92.dp

    Surface(
        modifier = Modifier
            .then(if (isCompactFloatingStyle) Modifier else Modifier.fillMaxWidth())
            .padding(horizontal = adaptiveHorizontalPad)
            .graphicsLayer {
                val tx = dragOffsetX.value
                val ty = dragOffsetY.value
                translationX = tx
                translationY = ty
                val verticalDownFrac = (ty / maxVerticalDragPx).coerceIn(0f, 1f)
                val verticalUpFrac = (-ty / maxVerticalDragPx).coerceIn(0f, 1f)
                val scale = (1f + verticalUpFrac * 0.025f - verticalDownFrac * 0.05f).coerceIn(0.92f, 1.04f)
                scaleX = scale
                scaleY = scale
                alpha = (1f - verticalDownFrac * 0.32f).coerceIn(0.65f, 1f)
            }
            .shadow(
                elevation = if (miniPlayerStyle == MiniPlayerStyle.COMPACT_BAR) 10.dp else 14.dp,
                shape = shape,
                ambientColor = colors.shadowColor,
                spotColor = colors.shadowColor
            )
            .border(0.75.dp, colors.cardBorder, shape)
            .clip(shape)
            .clickable { onClick() }
            .semantics {
                val actions = mutableListOf(
                    CustomAccessibilityAction("Next track") {
                        latestOnPlayNext()
                        true
                    },
                    CustomAccessibilityAction("Previous track") {
                        latestOnPlayPrevious()
                        true
                    },
                    CustomAccessibilityAction("Expand full player") {
                        latestOnSwipeUpExpand()
                        true
                    }
                )
                latestOnSwipeDownAction?.let { downAction ->
                    actions += CustomAccessibilityAction(
                        if (onSwipeDownCollapse != null) "Minimize to circular player" else "Dismiss mini-player"
                    ) {
                        downAction()
                        true
                    }
                }
                customActions = actions
            }
            .pointerInput(horizontalThresholdPx, verticalUpThresholdPx, verticalDownThresholdPx) {
                val touchSlop = viewConfiguration.touchSlop
                var lastTriggerUptimeMs = Long.MIN_VALUE

                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val startedOnSeekbar = latestIsSeekbarScrubbing

                    var totalDx = 0f
                    var totalDy = 0f
                    var axisLock = GestureAxisLock.NONE
                    var cancelled = false
                    var lastUptimeMs = down.uptimeMillis
                    val velocityTracker = VelocityTracker()
                    velocityTracker.addPosition(down.uptimeMillis, down.position)

                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Main)
                        if (event.changes.count { it.pressed } >= 2) {
                            cancelled = true
                            break
                        }
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        lastUptimeMs = change.uptimeMillis
                        if (!change.pressed) break

                        val delta = change.positionChange()
                        totalDx += delta.x
                        totalDy += delta.y

                        if (axisLock == GestureAxisLock.NONE) {
                            val candidate = MiniPlayerGestureMath.resolveAxisLock(
                                totalDx = totalDx,
                                totalDy = totalDy,
                                touchSlop = touchSlop
                            )
                            if (candidate == GestureAxisLock.HORIZONTAL) {
                                // Never hijack horizontal drag from interactive seekbar scrubbing
                                if (startedOnSeekbar || latestIsSeekbarScrubbing || change.isConsumed) {
                                    cancelled = true
                                    break
                                }
                                axisLock = GestureAxisLock.HORIZONTAL
                            } else if (candidate == GestureAxisLock.VERTICAL) {
                                // Vertical swipe-up/down works smoothly even if gesture started near bottom seekbar edge
                                if (!latestIsSeekbarScrubbing || abs(totalDy) > abs(totalDx) * 1.6f) {
                                    axisLock = GestureAxisLock.VERTICAL
                                }
                            }
                        }

                        if (axisLock != GestureAxisLock.NONE) {
                            change.consume()
                            velocityTracker.addPosition(change.uptimeMillis, change.position)
                            if (axisLock == GestureAxisLock.HORIZONTAL) {
                                val dampedX = MiniPlayerGestureMath.dampedOffset(totalDx, maxHorizontalDragPx)
                                scope.launch { dragOffsetX.snapTo(dampedX) }
                            } else {
                                val dampedY = MiniPlayerGestureMath.dampedOffset(totalDy, maxVerticalDragPx)
                                scope.launch { dragOffsetY.snapTo(dampedY) }
                            }
                        }
                    }

                    val velocity = velocityTracker.calculateVelocity()
                    val cooldownElapsed = lastTriggerUptimeMs == Long.MIN_VALUE ||
                        (lastUptimeMs - lastTriggerUptimeMs) >= 180L

                    val decision = if (!cancelled && cooldownElapsed && axisLock != GestureAxisLock.NONE) {
                        MiniPlayerGestureMath.resolveDecision(
                            axisLock = axisLock,
                            totalDx = totalDx,
                            totalDy = totalDy,
                            velocityX = velocity.x,
                            velocityY = velocity.y,
                            horizontalThresholdPx = horizontalThresholdPx,
                            verticalUpThresholdPx = verticalUpThresholdPx,
                            verticalDownThresholdPx = verticalDownThresholdPx,
                            velocityThresholdPx = velocityThresholdPx,
                            allowExpandUp = true,
                            allowCollapseOrDismissDown = latestOnSwipeDownAction != null
                        )
                    } else {
                        MiniPlayerGestureAction.NONE
                    }

                    if (decision != MiniPlayerGestureAction.NONE) {
                        lastTriggerUptimeMs = lastUptimeMs
                    }

                    when (decision) {
                        MiniPlayerGestureAction.SKIP_NEXT -> {
                            performHaptic(HapticFeedbackType.TextHandleMove)
                            latestOnPlayNext()
                            scope.launch {
                                if (latestMotionState.animationsEnabled) {
                                    dragOffsetX.animateTo(
                                        0f,
                                        spring(dampingRatio = 0.78f, stiffness = Spring.StiffnessMedium)
                                    )
                                } else {
                                    dragOffsetX.snapTo(0f)
                                }
                            }
                        }
                        MiniPlayerGestureAction.SKIP_PREVIOUS -> {
                            performHaptic(HapticFeedbackType.TextHandleMove)
                            latestOnPlayPrevious()
                            scope.launch {
                                if (latestMotionState.animationsEnabled) {
                                    dragOffsetX.animateTo(
                                        0f,
                                        spring(dampingRatio = 0.78f, stiffness = Spring.StiffnessMedium)
                                    )
                                } else {
                                    dragOffsetX.snapTo(0f)
                                }
                            }
                        }
                        MiniPlayerGestureAction.EXPAND_FULL_PLAYER -> {
                            performHaptic(HapticFeedbackType.TextHandleMove)
                            scope.launch { dragOffsetY.snapTo(0f) }
                            latestOnSwipeUpExpand()
                        }
                        MiniPlayerGestureAction.COLLAPSE_OR_DISMISS -> {
                            performHaptic(HapticFeedbackType.TextHandleMove)
                            scope.launch { dragOffsetY.snapTo(0f) }
                            latestOnSwipeDownAction?.invoke()
                        }
                        MiniPlayerGestureAction.NONE -> {
                            scope.launch {
                                if (latestMotionState.animationsEnabled) {
                                    launch {
                                        dragOffsetX.animateTo(
                                            0f,
                                            spring(dampingRatio = 0.80f, stiffness = Spring.StiffnessMedium)
                                        )
                                    }
                                    launch {
                                        dragOffsetY.animateTo(
                                            0f,
                                            spring(dampingRatio = 0.80f, stiffness = Spring.StiffnessMedium)
                                        )
                                    }
                                } else {
                                    dragOffsetX.snapTo(0f)
                                    dragOffsetY.snapTo(0f)
                                }
                            }
                        }
                    }
                }
            }
            .testTag("mini_player"),
        color = surfaceColor,
        tonalElevation = 4.dp
    ) {
        val showMiniWeatherBackdrop = weatherSettings.enableMiniPlayerEffects &&
            weatherPlan.isActive &&
            domain.weather.WeatherAtmosphereEngine.supportsMiniPlayerAtmosphere(miniPlayerStyle)
        Box(modifier = Modifier.fillMaxWidth()) {
            if (showMiniWeatherBackdrop) {
                ui.components.WeatherAtmosphereOverlay(
                    plan = weatherPlan,
                    settings = weatherSettings,
                    colors = colors,
                    surfaceRole = domain.weather.WeatherSurfaceRole.MiniPlayer(miniPlayerStyle),
                    isActive = true,
                    screenVisible = true,
                    modifier = Modifier.matchParentSize(),
                    testTagName = "mini_player_weather_atmosphere_overlay"
                )
            }
            // Subtle directional visual cue while dragging horizontally or vertically
            val activeDragX = dragOffsetX.value
            val activeDragY = dragOffsetY.value
            if (abs(activeDragX) > 12f) {
                val cueAlpha = (abs(activeDragX) / horizontalThresholdPx).coerceIn(0.15f, 0.9f)
                val isNextCue = activeDragX < 0f
                Box(
                    modifier = Modifier
                        .align(if (isNextCue) Alignment.CenterEnd else Alignment.CenterStart)
                        .padding(horizontal = 10.dp)
                        .size(28.dp)
                        .graphicsLayer { alpha = cueAlpha }
                        .clip(CircleShape)
                        .background(colors.accentStrong.copy(alpha = 0.22f))
                        .testTag("mini_player_swipe_direction_cue"),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isNextCue) Icons.Rounded.SkipNext else Icons.Rounded.SkipPrevious,
                        contentDescription = null,
                        tint = colors.accentStrong,
                        modifier = Modifier.size(17.dp)
                    )
                }
            } else if (abs(activeDragY) > 10f) {
                val cueAlpha = (abs(activeDragY) / verticalUpThresholdPx).coerceIn(0.15f, 0.85f)
                val isUpCue = activeDragY < 0f
                Box(
                    modifier = Modifier
                        .align(if (isUpCue) Alignment.TopCenter else Alignment.BottomCenter)
                        .padding(vertical = 2.dp)
                        .graphicsLayer { alpha = cueAlpha }
                        .testTag("mini_player_vertical_cue"),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isUpCue) Icons.Rounded.KeyboardArrowUp else Icons.Rounded.KeyboardArrowDown,
                        contentDescription = null,
                        tint = colors.accentStrong,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
            CompositionLocalProvider(LocalMiniSeekController provides miniSeekController) {
        when (miniPlayerStyle) {
            MiniPlayerStyle.COMPACT_BAR -> {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = innerRowPadH, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AnimatedContent(
                            targetState = song.id,
                            transitionSpec = { SonoraTransitions.trackTransition(trackDirection, motionState) },
                            label = "compactMiniArtwork"
                        ) { _ ->
                            ArtworkAlbumView(
                                song = song,
                                modifier = Modifier.size(38.dp).aspectRatio(1f),
                                cornerRadius = 10.dp,
                                isPlaying = playbackInfo.isPlaying
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        AnimatedContent(
                            targetState = song.id,
                            transitionSpec = { SonoraTransitions.metadataTransition(trackDirection, motionState) },
                            modifier = Modifier.weight(1f),
                            label = "compactMiniMeta"
                        ) { _ ->
                            Text(
                                text = "${song.title} • ${song.artist}",
                                fontFamily = OutfitFontFamily,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 13.sp,
                                color = colors.textPrimary,
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        if (showSourceBadge) {
                            Spacer(modifier = Modifier.width(6.dp))
                            MiniPlayerSourceBadge(sourceLabel = sourceLabel, isOnline = song.isOnline, colors = colors)
                        }
                        if (onOpenLyrics != null) {
                            IconButton(
                                onClick = {
                                    performHaptic(HapticFeedbackType.TextHandleMove)
                                    onOpenLyrics()
                                },
                                modifier = Modifier
                                    .width(controlBtnWidth)
                                    .height(48.dp)
                                    .testTag("mini_lyrics_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Lyrics,
                                    contentDescription = "Open Lyrics",
                                    tint = colors.accentStrong,
                                    modifier = Modifier.size(19.dp)
                                )
                            }
                        }
                        IconButton(
                            onClick = {
                                performHaptic(HapticFeedbackType.TextHandleMove)
                                onTogglePlayPause()
                            },
                            modifier = Modifier
                                .width(playBtnWidth)
                                .height(48.dp)
                                .testTag("mini_play_pause_button")
                        ) {
                            AnimatedContent(
                                targetState = playbackInfo.isPlaying,
                                transitionSpec = {
                                    SonoraTransitions.playPauseIconTransition(motionState)
                                },
                                label = "compactMiniPlayIcon"
                            ) { playing ->
                                Icon(
                                    imageVector = if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                                    contentDescription = if (playing) "Pause" else "Play",
                                    tint = colors.accentStrong,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }
                        IconButton(
                            onClick = {
                                performHaptic(HapticFeedbackType.TextHandleMove)
                                onPlayNext()
                            },
                            modifier = Modifier
                                .width(controlBtnWidth)
                                .height(48.dp)
                                .testTag("mini_skip_next_button")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.SkipNext,
                                contentDescription = "Next Track",
                                tint = colors.textPrimary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                    InteractiveMiniSeekbar(
                        activeColor = colors.accentStrong,
                        trackColor = colors.cardBorder.copy(alpha = 0.4f),
                        trackHeight = 2.5.dp,
                        touchHeight = 14.dp
                    )
                }
            }

            MiniPlayerStyle.ARTWORK_FOCUS -> {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = innerRowPadH, end = (innerRowPadH - 2.dp).coerceAtLeast(4.dp), top = 10.dp, bottom = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val focusArtSize = if (containerWidth < 340.dp) 44.dp else 52.dp
                        AnimatedContent(
                            targetState = song.id,
                            transitionSpec = { SonoraTransitions.trackTransition(trackDirection, motionState) },
                            label = "focusMiniArtwork"
                        ) { _ ->
                            ArtworkAlbumView(
                                song = song,
                                modifier = Modifier
                                    .size(focusArtSize)
                                    .aspectRatio(1f)
                                    .border(1.dp, colors.cardBorder, RoundedCornerShape(14.dp)),
                                cornerRadius = 14.dp,
                                isPlaying = playbackInfo.isPlaying
                            )
                        }
                        Spacer(modifier = Modifier.width(if (containerWidth < 340.dp) 8.dp else 12.dp))
                        AnimatedContent(
                            targetState = song.id,
                            transitionSpec = { SonoraTransitions.metadataTransition(trackDirection, motionState) },
                            modifier = Modifier.weight(1f),
                            label = "focusMiniMeta"
                        ) { _ ->
                            Column(modifier = Modifier.fillMaxWidth()) {
                                Text(
                                    text = song.title,
                                    fontFamily = OutfitFontFamily,
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 15.sp,
                                    color = colors.textPrimary,
                                    maxLines = 1,
                                    softWrap = false,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    val focusSubtitle = if (playbackInfo.isBuffering && song.isOnline) {
                                        playbackInfo.statusMessage.ifBlank {
                                            playbackInfo.playbackStage.defaultMessage
                                        }.ifBlank { "Buffering audio..." }
                                    } else if (showSecondaryCodecBadge) {
                                        "${song.artist} • $badge"
                                    } else {
                                        song.artist
                                    }
                                    Text(
                                        text = focusSubtitle,
                                        fontFamily = PlusJakartaFontFamily,
                                        fontSize = 12.sp,
                                        color = if (playbackInfo.isBuffering) colors.accentStrong else colors.textSecondary,
                                        maxLines = 1,
                                        softWrap = false,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f, fill = false)
                                    )
                                    if (showSourceBadge) {
                                        Spacer(modifier = Modifier.width(6.dp))
                                        MiniPlayerSourceBadge(sourceLabel = sourceLabel, isOnline = song.isOnline, colors = colors)
                                    }
                                }
                            }
                        }
                        if (onOpenLyrics != null) {
                            IconButton(
                                onClick = {
                                    performHaptic(HapticFeedbackType.TextHandleMove)
                                    onOpenLyrics()
                                },
                                modifier = Modifier
                                    .width(controlBtnWidth)
                                    .height(48.dp)
                                    .testTag("mini_lyrics_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Lyrics,
                                    contentDescription = "Open Lyrics",
                                    tint = colors.accentStrong,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                        IconButton(
                            onClick = {
                                performHaptic(HapticFeedbackType.TextHandleMove)
                                onTogglePlayPause()
                            },
                            modifier = Modifier
                                .width(playBtnWidth)
                                .height(48.dp)
                                .testTag("mini_play_pause_button")
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .background(colors.accentStrong, RoundedCornerShape(14.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                AnimatedContent(
                                    targetState = playbackInfo.isPlaying,
                                    transitionSpec = {
                                        SonoraTransitions.playPauseIconTransition(motionState)
                                    },
                                    label = "focusMiniPlayIcon"
                                ) { playing ->
                                    Icon(
                                        imageVector = if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                                        contentDescription = if (playing) "Pause" else "Play",
                                        tint = colors.onAccentStrong,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                            }
                        }
                        IconButton(
                            onClick = {
                                performHaptic(HapticFeedbackType.TextHandleMove)
                                onPlayNext()
                            },
                            modifier = Modifier
                                .width(controlBtnWidth)
                                .height(48.dp)
                                .testTag("mini_skip_next_button")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.SkipNext,
                                contentDescription = "Next Track",
                                tint = colors.textPrimary,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                    InteractiveMiniSeekbar(
                        activeColor = colors.accentStrong,
                        trackColor = colors.cardBorder.copy(alpha = 0.45f),
                        trackHeight = 4.dp,
                        touchHeight = 16.dp,
                        rounded = true,
                        modifier = Modifier
                            .padding(horizontal = 12.dp)
                            .padding(bottom = 4.dp)
                    )
                }
            }

            MiniPlayerStyle.WAVE_MINI -> {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = innerRowPadH, vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val waveArtSize = if (containerWidth < 340.dp) 38.dp else 42.dp
                        AnimatedContent(
                            targetState = song.id,
                            transitionSpec = { SonoraTransitions.trackTransition(trackDirection, motionState) },
                            label = "waveMiniArtwork"
                        ) { _ ->
                            CircularArtwork(
                                song = song,
                                isPlaying = playbackInfo.isPlaying,
                                accentColor = colors.accentStrong,
                                shadowElevation = 4.dp,
                                modifier = Modifier.size(waveArtSize).aspectRatio(1f)
                            )
                        }
                        Spacer(modifier = Modifier.width(if (containerWidth < 340.dp) 8.dp else 12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            AnimatedContent(
                                targetState = song.id,
                                transitionSpec = { SonoraTransitions.metadataTransition(trackDirection, motionState) },
                                label = "waveMiniMeta"
                            ) { _ ->
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = song.title,
                                        fontFamily = OutfitFontFamily,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp,
                                        color = colors.textPrimary,
                                        maxLines = 1,
                                        softWrap = false,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier
                                            .weight(1f, fill = false)
                                            .testTag("mini_player_title")
                                    )
                                    if (showSourceBadge) {
                                        Spacer(modifier = Modifier.width(6.dp))
                                        MiniPlayerSourceBadge(sourceLabel = sourceLabel, isOnline = song.isOnline, colors = colors)
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            // Interactive micro waveform drag-to-seek strip
                            InteractiveMiniWaveformSeekbar(
                                activeColor = colors.accentStrong,
                                trackColor = colors.cardBorder.copy(alpha = 0.7f)
                            )
                        }
                        Spacer(modifier = Modifier.width(4.dp))
                        if (onOpenLyrics != null) {
                            IconButton(
                                onClick = {
                                    performHaptic(HapticFeedbackType.TextHandleMove)
                                    onOpenLyrics()
                                },
                                modifier = Modifier
                                    .width(controlBtnWidth)
                                    .height(48.dp)
                                    .testTag("mini_lyrics_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Lyrics,
                                    contentDescription = "Open Lyrics",
                                    tint = colors.accentStrong,
                                    modifier = Modifier.size(19.dp)
                                )
                            }
                        }
                        IconButton(
                            onClick = {
                                performHaptic(HapticFeedbackType.TextHandleMove)
                                onTogglePlayPause()
                            },
                            modifier = Modifier
                                .width(playBtnWidth)
                                .height(48.dp)
                                .testTag("mini_play_pause_button")
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(if (containerWidth < 340.dp) 36.dp else 38.dp)
                                    .background(colors.accentPill, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                AnimatedContent(
                                    targetState = playbackInfo.isPlaying,
                                    transitionSpec = {
                                        SonoraTransitions.playPauseIconTransition(motionState)
                                    },
                                    label = "waveMiniPlayIcon"
                                ) { playing ->
                                    Icon(
                                        imageVector = if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                                        contentDescription = if (playing) "Pause" else "Play",
                                        tint = colors.onAccentPill,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }
                        IconButton(
                            onClick = {
                                performHaptic(HapticFeedbackType.TextHandleMove)
                                onPlayNext()
                            },
                            modifier = Modifier
                                .width(controlBtnWidth)
                                .height(48.dp)
                                .testTag("mini_skip_next_button")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.SkipNext,
                                contentDescription = "Next Track",
                                tint = colors.textPrimary,
                                modifier = Modifier.size(21.dp)
                            )
                        }
                    }
                }
            }

            MiniPlayerStyle.FLOATING_PILL -> {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = innerRowPadH, vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val pillArtSize = if (containerWidth < 340.dp) 38.dp else 44.dp
                        AnimatedContent(
                            targetState = song.id,
                            transitionSpec = { SonoraTransitions.trackTransition(trackDirection, motionState) },
                            label = "pillMiniArtwork"
                        ) { _ ->
                            CircularArtwork(
                                song = song,
                                isPlaying = playbackInfo.isPlaying,
                                accentColor = colors.accentPill,
                                shadowElevation = 4.dp,
                                modifier = Modifier.size(pillArtSize).aspectRatio(1f)
                            )
                        }

                        Spacer(modifier = Modifier.width(if (containerWidth < 340.dp) 8.dp else 10.dp))

                        AnimatedContent(
                            targetState = song.id,
                            transitionSpec = { SonoraTransitions.metadataTransition(trackDirection, motionState) },
                            modifier = Modifier.weight(1f),
                            label = "pillMiniMeta"
                        ) { _ ->
                            Column(modifier = Modifier.fillMaxWidth()) {
                                Text(
                                    text = song.title,
                                    fontFamily = OutfitFontFamily,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    color = colors.textPrimary,
                                    maxLines = 1,
                                    softWrap = false,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.testTag("mini_player_title")
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    val pillSubtitle = if (playbackInfo.isBuffering && song.isOnline) {
                                        playbackInfo.statusMessage.ifBlank {
                                            playbackInfo.playbackStage.defaultMessage
                                        }.ifBlank { "Buffering audio..." }
                                    } else {
                                        song.artist
                                    }
                                    Text(
                                        text = pillSubtitle,
                                        fontFamily = PlusJakartaFontFamily,
                                        fontSize = 12.sp,
                                        color = if (playbackInfo.isBuffering) colors.accentStrong else colors.textSecondary,
                                        maxLines = 1,
                                        softWrap = false,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier
                                            .weight(1f, fill = false)
                                            .testTag("mini_player_artist")
                                    )
                                    if (showSourceBadge) {
                                        Spacer(modifier = Modifier.width(6.dp))
                                        MiniPlayerSourceBadge(sourceLabel = sourceLabel, isOnline = song.isOnline, colors = colors)
                                    }
                                    if (showSecondaryCodecBadge) {
                                        Spacer(modifier = Modifier.width(4.dp))
                                        AdaptiveSingleLineBadge(
                                            text = badge,
                                            containerColor = colors.accentPill,
                                            contentColor = colors.onAccentPill,
                                            cornerRadius = 6.dp,
                                            fontSize = 9.sp,
                                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 1.dp),
                                            modifier = Modifier.testTag("mini_player_codec_badge")
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.width(4.dp))

                        if (onOpenLyrics != null) {
                            IconButton(
                                onClick = {
                                    performHaptic(HapticFeedbackType.TextHandleMove)
                                    onOpenLyrics()
                                },
                                modifier = Modifier
                                    .width(controlBtnWidth)
                                    .height(48.dp)
                                    .testTag("mini_lyrics_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Lyrics,
                                    contentDescription = "Open Lyrics",
                                    tint = colors.accentStrong,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }

                        IconButton(
                            onClick = {
                                performHaptic(HapticFeedbackType.TextHandleMove)
                                onTogglePlayPause()
                            },
                            modifier = Modifier
                                .width(playBtnWidth)
                                .height(48.dp)
                                .clip(CircleShape)
                                .testTag("mini_play_pause_button")
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(if (containerWidth < 340.dp) 36.dp else 40.dp)
                                    .background(colors.accentPill, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                AnimatedContent(
                                    targetState = playbackInfo.isPlaying,
                                    transitionSpec = {
                                        SonoraTransitions.playPauseIconTransition(motionState)
                                    },
                                    label = "pillMiniPlayIcon"
                                ) { playing ->
                                    Icon(
                                        imageVector = if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                                        contentDescription = if (playing) "Pause" else "Play",
                                        tint = colors.onAccentPill,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                            }
                        }

                        IconButton(
                            onClick = {
                                performHaptic(HapticFeedbackType.TextHandleMove)
                                onPlayNext()
                            },
                            modifier = Modifier
                                .width(controlBtnWidth)
                                .height(48.dp)
                                .testTag("mini_skip_next_button")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.SkipNext,
                                contentDescription = "Next Track",
                                tint = colors.textPrimary,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }

                    // Interactive progress bar along bottom edge
                    InteractiveMiniSeekbar(
                        activeColor = colors.accentStrong,
                        trackColor = colors.cardBorder.copy(alpha = 0.5f),
                        trackHeight = 2.5.dp,
                        touchHeight = 14.dp
                    )
                }
            }

            else -> {
                RenderExtendedMiniPlayerStyle(
                    style = miniPlayerStyle,
                    song = song,
                    playbackInfo = playbackInfo,
                    progress = progress,
                    colors = colors,
                    palette = dynamicPalette,
                    frequencies = frequencies,
                    queue = queue,
                    queueIndex = queueIndex,
                    onTogglePlayPause = onTogglePlayPause,
                    onPlayNext = onPlayNext,
                    onPlayPrevious = onPlayPrevious,
                    onOpenLyrics = onOpenLyrics
                )
            }
        }
            }
        }
    }
    }
}

@Composable
private fun MiniPlayerSourceBadge(
    sourceLabel: String,
    isOnline: Boolean,
    colors: EditorialColors
) {
    Box(
        modifier = Modifier
            .wrapContentWidth()
            .testTag("mini_player_source_badge")
            .background(
                color = if (isOnline) colors.accentStrong.copy(alpha = 0.16f) else colors.surfaceTinted,
                shape = RoundedCornerShape(6.dp)
            )
            .border(
                width = 0.5.dp,
                color = if (isOnline) colors.accentStrong.copy(alpha = 0.45f) else colors.cardBorder,
                shape = RoundedCornerShape(6.dp)
            )
            .padding(horizontal = 6.dp, vertical = 1.dp)
    ) {
        Text(
            text = sourceLabel,
            fontFamily = PlusJakartaFontFamily,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            color = if (isOnline) colors.accentStrong else colors.textSecondary,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis
        )
    }
}
