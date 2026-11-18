package ui.player

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.media.AudioManager
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import com.example.model.DoubleTapAction
import com.example.model.PlayerGestureConfig
import com.example.model.SingleTapAction
import com.example.model.SwipeAction
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import ui.components.GestureHudState
import ui.components.GestureHudType
import ui.motion.LocalEffectiveMotionState
import kotlin.math.abs

@Stable
class PlayerGestureController(
    private val context: Context,
    private val coroutineScope: kotlinx.coroutines.CoroutineScope
) {
    var hudState by mutableStateOf<GestureHudState?>(null)
        private set

    var artworkZoomScale by mutableFloatStateOf(1f)
        private set

    var artworkDragOffsetX by mutableFloatStateOf(0f)
        private set

    var controlsVisible by mutableStateOf(true)
        private set

    private var clearHudJob: Job? = null
    private var accumulatedVolumeDelta = 0f
    private var activeBrightness = -1f

    fun showHud(state: GestureHudState, durationMs: Long = 1200L) {
        clearHudJob?.cancel()
        hudState = state
        clearHudJob = coroutineScope.launch {
            delay(durationMs)
            hudState = null
        }
    }

    fun toggleControlsVisibility() {
        controlsVisible = !controlsVisible
    }

    fun updateArtworkDragOffset(offsetX: Float) {
        artworkDragOffsetX = offsetX
    }

    fun resetArtworkDragOffset() {
        artworkDragOffsetX = 0f
    }

    fun updateZoomScale(zoomFactor: Float) {
        val next = (artworkZoomScale * zoomFactor).coerceIn(1f, 2.2f)
        artworkZoomScale = next
        if (next > 1.02f) {
            showHud(GestureHudState(GestureHudType.ZOOM, "Zoom ${String.format(java.util.Locale.US, "%.1fx", next)}"), 900L)
        } else {
            artworkZoomScale = 1f
        }
    }

    fun resetZoom() {
        artworkZoomScale = 1f
    }

    fun adjustMusicVolume(deltaY: Float, totalHeightPx: Float) {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
        val maxVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
        val curVol = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)

        val stepThresholdPx = (totalHeightPx / maxVol.toFloat()).coerceIn(18f, 52f)
        accumulatedVolumeDelta += -deltaY // dragging up increases volume
        val steps = (accumulatedVolumeDelta / stepThresholdPx).toInt()
        if (steps != 0) {
            accumulatedVolumeDelta -= steps * stepThresholdPx
            val nextVol = (curVol + steps).coerceIn(0, maxVol)
            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, nextVol, 0)
            val pct = ((nextVol.toFloat() / maxVol.toFloat()) * 100f).toInt()
            showHud(
                GestureHudState(
                    type = GestureHudType.VOLUME,
                    label = "$pct%",
                    progress = nextVol.toFloat() / maxVol.toFloat()
                )
            )
        } else {
            val pct = ((curVol.toFloat() / maxVol.toFloat()) * 100f).toInt()
            showHud(
                GestureHudState(
                    type = GestureHudType.VOLUME,
                    label = "$pct%",
                    progress = curVol.toFloat() / maxVol.toFloat()
                )
            )
        }
    }

    fun resetEdgeDragAccumulators() {
        accumulatedVolumeDelta = 0f
    }

    private tailrec fun Context.findActivity(): Activity? = when (this) {
        is Activity -> this
        is ContextWrapper -> baseContext?.findActivity()
        else -> null
    }

    fun adjustScreenBrightness(deltaY: Float, totalHeightPx: Float) {
        val activity = context.findActivity() ?: return
        val window = activity.window ?: return
        val lp = window.attributes
        if (activeBrightness < 0f) {
            activeBrightness = if (lp.screenBrightness in 0.01f..1.0f) lp.screenBrightness else 0.5f
        }
        val delta = (-deltaY / totalHeightPx.coerceAtLeast(300f)) * 1.15f
        val next = (activeBrightness + delta).coerceIn(0.04f, 1.0f)
        activeBrightness = next
        lp.screenBrightness = next
        window.attributes = lp
        val pct = (next * 100f).toInt()
        showHud(
            GestureHudState(
                type = GestureHudType.BRIGHTNESS,
                label = "$pct%",
                progress = next
            )
        )
    }
}

@Composable
fun rememberPlayerGestureController(): PlayerGestureController {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    return remember(context, scope) { PlayerGestureController(context, scope) }
}

/**
 * Unified modifier attached to player artwork containers across all 19 styles.
 * Supports: Single tap, Double tap (left/right seek or play/pause/favorite), Long press (Style Wheel),
 * Horizontal swipe (velocity + threshold aware configurable action), Vertical swipe (minimize / queue),
 * and multi-touch Pinch-to-Zoom without conflicting with single-finger swipes.
 */
fun Modifier.playerArtworkGestures(
    config: PlayerGestureConfig,
    controller: PlayerGestureController,
    currentPositionMs: Long,
    durationMs: Long,
    isPlaying: Boolean,
    onTogglePlayPause: () -> Unit,
    onPlayNext: () -> Unit,
    onPlayPrevious: () -> Unit,
    onSeekTo: (Long) -> Unit,
    onOpenQueue: () -> Unit,
    onClose: () -> Unit,
    onArtworkLongPress: () -> Unit,
    onToggleFavorite: (() -> Unit)? = null,
    onCyclePlayerStyle: (() -> Unit)? = null
): Modifier = composed {
    val haptic = LocalHapticFeedback.current
    val density = LocalDensity.current
    val motionState = LocalEffectiveMotionState.current
    val baseThresholdPx = with(density) { config.swipeThresholdDp.dp.toPx() }
    val effectiveHorizontalThreshold = (baseThresholdPx / config.swipeSensitivity.coerceIn(0.5f, 1.8f)).coerceAtLeast(36f)
    val effectiveVerticalThreshold = (baseThresholdPx * 1.25f / config.swipeSensitivity.coerceIn(0.5f, 1.8f)).coerceAtLeast(56f)
    val velocityThresholdPx = with(density) { motionState.velocityThresholdDpPerSec.dp.toPx() }

    val latestConfig by rememberUpdatedState(config)
    val latestMotionState by rememberUpdatedState(motionState)
    val latestPositionMs by rememberUpdatedState(currentPositionMs)
    val latestDurationMs by rememberUpdatedState(durationMs)
    val latestIsPlaying by rememberUpdatedState(isPlaying)
    val latestTogglePlayPause by rememberUpdatedState(onTogglePlayPause)
    val latestPlayNext by rememberUpdatedState(onPlayNext)
    val latestPlayPrevious by rememberUpdatedState(onPlayPrevious)
    val latestSeekTo by rememberUpdatedState(onSeekTo)
    val latestOpenQueue by rememberUpdatedState(onOpenQueue)
    val latestClose by rememberUpdatedState(onClose)
    val latestArtworkLongPress by rememberUpdatedState(onArtworkLongPress)
    val latestToggleFavorite by rememberUpdatedState(onToggleFavorite)
    val latestCycleStyle by rememberUpdatedState(onCyclePlayerStyle)

    fun triggerHaptic(type: HapticFeedbackType = HapticFeedbackType.TextHandleMove) {
        if (latestConfig.hapticFeedbackEnabled && latestMotionState.hapticEnabled) {
            haptic.performHapticFeedback(type)
        }
    }

    fun dispatchSwipe(action: SwipeAction, isLeftSwipe: Boolean) {
        when (action) {
            SwipeAction.SKIP_TRACK -> {
                triggerHaptic()
                if (isLeftSwipe) {
                    latestPlayNext()
                    controller.showHud(GestureHudState(GestureHudType.NEXT_TRACK, "Next Track"))
                } else {
                    latestPlayPrevious()
                    controller.showHud(GestureHudState(GestureHudType.PREVIOUS_TRACK, "Previous Track"))
                }
            }
            SwipeAction.SEEK_15S -> {
                triggerHaptic()
                val pos = latestPositionMs
                val dur = latestDurationMs
                if (isLeftSwipe) {
                    val target = (pos + 15_000L).coerceAtMost(dur.coerceAtLeast(pos))
                    latestSeekTo(target)
                    controller.showHud(GestureHudState(GestureHudType.SEEK_FORWARD, "Forward +15s"))
                } else {
                    val target = (pos - 15_000L).coerceAtLeast(0L)
                    latestSeekTo(target)
                    controller.showHud(GestureHudState(GestureHudType.SEEK_BACKWARD, "Rewind -15s"))
                }
            }
            SwipeAction.CYCLE_STYLE -> {
                triggerHaptic()
                latestCycleStyle?.invoke()
                controller.showHud(GestureHudState(GestureHudType.STYLE_CHANGED, "Style Switched"))
            }
            SwipeAction.OPEN_QUEUE -> {
                triggerHaptic()
                latestOpenQueue()
            }
            SwipeAction.NONE -> Unit
        }
    }

    this
        .pointerInput(
            config.gesturesEnabled,
            config.singleTapAction,
            config.doubleTapEnabled,
            config.doubleTapAction,
            config.longPressStyleWheelEnabled
        ) {
            detectTapGestures(
                onTap = {
                    val curConfig = latestConfig
                    if (controller.artworkZoomScale > 1.05f) {
                        controller.resetZoom()
                        return@detectTapGestures
                    }
                    if (!curConfig.gesturesEnabled) {
                        latestTogglePlayPause()
                        return@detectTapGestures
                    }
                    when (curConfig.singleTapAction) {
                        SingleTapAction.TOGGLE_PLAY_PAUSE -> {
                            triggerHaptic()
                            val wasPlaying = latestIsPlaying
                            latestTogglePlayPause()
                            controller.showHud(
                                GestureHudState(
                                    GestureHudType.PLAY_PAUSE,
                                    if (wasPlaying) "Paused" else "Playing",
                                    isPlaying = !wasPlaying
                                ),
                                durationMs = 750L
                            )
                        }
                        SingleTapAction.TOGGLE_CONTROLS_VISIBILITY -> {
                            triggerHaptic()
                            controller.toggleControlsVisibility()
                        }
                        SingleTapAction.NONE -> Unit
                    }
                },
                onLongPress = {
                    val curConfig = latestConfig
                    if (!curConfig.gesturesEnabled || curConfig.longPressStyleWheelEnabled) {
                        triggerHaptic(HapticFeedbackType.LongPress)
                        latestArtworkLongPress()
                    }
                },
                onDoubleTap = { offset ->
                    val curConfig = latestConfig
                    if (!curConfig.gesturesEnabled || !curConfig.doubleTapEnabled) return@detectTapGestures
                    when (curConfig.doubleTapAction) {
                        DoubleTapAction.SEEK_10S -> {
                            triggerHaptic()
                            val pos = latestPositionMs
                            val dur = latestDurationMs
                            if (offset.x < size.width / 2f) {
                                val nextPos = (pos - 10_000L).coerceAtLeast(0L)
                                latestSeekTo(nextPos)
                                controller.showHud(GestureHudState(GestureHudType.SEEK_BACKWARD, "Rewind -10s"))
                            } else {
                                val nextPos = (pos + 10_000L).coerceAtMost(dur.coerceAtLeast(pos))
                                latestSeekTo(nextPos)
                                controller.showHud(GestureHudState(GestureHudType.SEEK_FORWARD, "Forward +10s"))
                            }
                        }
                        DoubleTapAction.PLAY_PAUSE -> {
                            triggerHaptic()
                            val wasPlaying = latestIsPlaying
                            latestTogglePlayPause()
                            controller.showHud(
                                GestureHudState(
                                    GestureHudType.PLAY_PAUSE,
                                    if (wasPlaying) "Paused" else "Playing",
                                    isPlaying = !wasPlaying
                                )
                            )
                        }
                        DoubleTapAction.TOGGLE_FAVORITE -> {
                            triggerHaptic()
                            latestToggleFavorite?.invoke()
                            controller.showHud(GestureHudState(GestureHudType.FAVORITE, "Favorite Updated"))
                        }
                        DoubleTapAction.NONE -> Unit
                    }
                }
            )
        }
        .pointerInput(
            config.gesturesEnabled,
            config.swipeArtworkEnabled,
            config.swipeLeftAction,
            config.swipeRightAction,
            effectiveHorizontalThreshold,
            velocityThresholdPx
        ) {
            if (!config.gesturesEnabled || !config.swipeArtworkEnabled) {
                controller.resetArtworkDragOffset()
                return@pointerInput
            }
            var dragX = 0f
            var tracker: VelocityTracker? = null
            detectHorizontalDragGestures(
                onDragStart = {
                    dragX = 0f
                    tracker = VelocityTracker()
                    controller.resetArtworkDragOffset()
                },
                onDragCancel = {
                    dragX = 0f
                    tracker = null
                    controller.resetArtworkDragOffset()
                },
                onDragEnd = {
                    val velocityX = tracker?.calculateVelocity()?.x ?: 0f
                    val triggered = latestMotionState.shouldTriggerSwipe(
                        dragPx = dragX,
                        velocityPxPerSec = velocityX,
                        thresholdPx = effectiveHorizontalThreshold,
                        velocityPxThreshold = velocityThresholdPx
                    )
                    if (triggered) {
                        val isLeftSwipe = if (abs(dragX) > effectiveHorizontalThreshold * 0.45f) dragX < 0f else velocityX < 0f
                        dispatchSwipe(
                            action = if (isLeftSwipe) latestConfig.swipeLeftAction else latestConfig.swipeRightAction,
                            isLeftSwipe = isLeftSwipe
                        )
                    }
                    dragX = 0f
                    tracker = null
                    controller.resetArtworkDragOffset()
                },
                onHorizontalDrag = { change, dragAmount ->
                    change.consume()
                    dragX += dragAmount
                    tracker?.addPosition(change.uptimeMillis, change.position)
                    controller.updateArtworkDragOffset(dragX)
                }
            )
        }
        .pointerInput(
            config.gesturesEnabled,
            config.swipeDownToMinimizeEnabled,
            config.swipeUpToOpenQueueEnabled,
            effectiveVerticalThreshold,
            velocityThresholdPx
        ) {
            if (!config.gesturesEnabled) return@pointerInput
            if (!config.swipeDownToMinimizeEnabled && !config.swipeUpToOpenQueueEnabled) return@pointerInput
            var dragY = 0f
            var tracker: VelocityTracker? = null
            detectVerticalDragGestures(
                onDragStart = {
                    dragY = 0f
                    tracker = VelocityTracker()
                },
                onDragCancel = {
                    dragY = 0f
                    tracker = null
                },
                onDragEnd = {
                    val velocityY = tracker?.calculateVelocity()?.y ?: 0f
                    val downTriggered = latestConfig.swipeDownToMinimizeEnabled &&
                        (dragY > effectiveVerticalThreshold || velocityY > velocityThresholdPx)
                    val upTriggered = latestConfig.swipeUpToOpenQueueEnabled &&
                        (dragY < -effectiveVerticalThreshold || velocityY < -velocityThresholdPx)
                    if (downTriggered) {
                        triggerHaptic()
                        latestClose()
                    } else if (upTriggered) {
                        triggerHaptic()
                        latestOpenQueue()
                    }
                    dragY = 0f
                    tracker = null
                },
                onVerticalDrag = { change, dragAmount ->
                    change.consume()
                    dragY += dragAmount
                    tracker?.addPosition(change.uptimeMillis, change.position)
                }
            )
        }
        .pointerInput(config.gesturesEnabled, config.pinchZoomArtworkEnabled) {
            if (!config.gesturesEnabled || !config.pinchZoomArtworkEnabled) return@pointerInput
            awaitEachGesture {
                awaitFirstDown(requireUnconsumed = false)
                do {
                    val event = awaitPointerEvent()
                    if (event.changes.size >= 2) {
                        val zoomChange = event.calculateZoom()
                        if (abs(zoomChange - 1f) > 0.01f) {
                            controller.updateZoomScale(zoomChange)
                            event.changes.forEach { it.consume() }
                        }
                    }
                } while (event.changes.any { it.pressed })
            }
        }
}

/**
 * Edge vertical drag detector placed on the full-screen Player container.
 * Uses safe inner edge zones (inset 24dp from screen borders) so Android system
 * edge-back navigation gestures remain completely unobstructed.
 */
fun Modifier.playerScreenEdgeGestures(
    config: PlayerGestureConfig,
    controller: PlayerGestureController,
    onClose: () -> Unit,
    onOpenQueue: () -> Unit
): Modifier = composed {
    val density = LocalDensity.current
    val haptic = LocalHapticFeedback.current
    val motionState = LocalEffectiveMotionState.current
    val systemBackSafePx = with(density) { 24.dp.toPx() }
    val edgeZoneOuterPx = with(density) { 80.dp.toPx() }
    val verticalSwipeThreshold = with(density) { (config.swipeThresholdDp * 1.4f).dp.toPx() } / config.swipeSensitivity.coerceIn(0.5f, 1.8f)
    val velocityThresholdPx = with(density) { motionState.velocityThresholdDpPerSec.dp.toPx() }

    val latestHapticEnabled by rememberUpdatedState(config.hapticFeedbackEnabled && motionState.hapticEnabled)
    val latestOnClose by rememberUpdatedState(onClose)
    val latestOnOpenQueue by rememberUpdatedState(onOpenQueue)

    this.pointerInput(
        config.gesturesEnabled,
        config.rightEdgeVolumeEnabled,
        config.leftEdgeBrightnessEnabled,
        config.swipeDownToMinimizeEnabled,
        config.swipeUpToOpenQueueEnabled,
        verticalSwipeThreshold,
        velocityThresholdPx
    ) {
        if (!config.gesturesEnabled) return@pointerInput
        var startX = 0f
        var dragY = 0f
        var inLeftEdge = false
        var inRightEdge = false
        var tracker: VelocityTracker? = null

        detectVerticalDragGestures(
            onDragStart = { offset ->
                startX = offset.x
                dragY = 0f
                tracker = VelocityTracker()
                controller.resetEdgeDragAccumulators()
                val w = size.width.toFloat()
                inLeftEdge = config.leftEdgeBrightnessEnabled &&
                    startX > systemBackSafePx && startX < edgeZoneOuterPx
                inRightEdge = config.rightEdgeVolumeEnabled &&
                    startX > (w - edgeZoneOuterPx) && startX < (w - systemBackSafePx)
            },
            onDragCancel = {
                inLeftEdge = false
                inRightEdge = false
                dragY = 0f
                tracker = null
            },
            onDragEnd = {
                if (!inLeftEdge && !inRightEdge) {
                    val velocityY = tracker?.calculateVelocity()?.y ?: 0f
                    if (config.swipeDownToMinimizeEnabled && (dragY > verticalSwipeThreshold || velocityY > velocityThresholdPx)) {
                        if (latestHapticEnabled) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        latestOnClose()
                    } else if (config.swipeUpToOpenQueueEnabled && (dragY < -verticalSwipeThreshold || velocityY < -velocityThresholdPx)) {
                        if (latestHapticEnabled) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        latestOnOpenQueue()
                    }
                }
                inLeftEdge = false
                inRightEdge = false
                dragY = 0f
                tracker = null
            },
            onVerticalDrag = { change, amount ->
                change.consume()
                dragY += amount
                tracker?.addPosition(change.uptimeMillis, change.position)
                val h = size.height.toFloat().coerceAtLeast(300f)
                if (inRightEdge) {
                    controller.adjustMusicVolume(amount, h)
                } else if (inLeftEdge) {
                    controller.adjustScreenBrightness(amount, h)
                }
            }
        )
    }
}
