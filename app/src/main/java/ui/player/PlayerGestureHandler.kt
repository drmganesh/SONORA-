package ui.player

import android.app.Activity
import android.content.Context
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
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

    fun adjustScreenBrightness(deltaY: Float, totalHeightPx: Float) {
        val activity = context as? Activity ?: return
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
 * Unified modifier attached to player artwork containers across all 18+ styles.
 * Supports: Single tap, Double tap (left/right seek or play/pause/favorite), Long press (Style Wheel),
 * Horizontal swipe (configurable action + sensitivity/threshold), Vertical swipe (minimize / queue),
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
    val baseThresholdPx = with(density) { config.swipeThresholdDp.dp.toPx() }
    val effectiveHorizontalThreshold = (baseThresholdPx / config.swipeSensitivity.coerceIn(0.5f, 1.8f)).coerceAtLeast(36f)
    val effectiveVerticalThreshold = (baseThresholdPx * 1.25f / config.swipeSensitivity.coerceIn(0.5f, 1.8f)).coerceAtLeast(56f)

    fun triggerHaptic(type: HapticFeedbackType = HapticFeedbackType.TextHandleMove) {
        if (config.hapticFeedbackEnabled) haptic.performHapticFeedback(type)
    }

    fun dispatchSwipe(action: SwipeAction, isLeftSwipe: Boolean) {
        when (action) {
            SwipeAction.SKIP_TRACK -> {
                triggerHaptic()
                if (isLeftSwipe) {
                    onPlayNext()
                    controller.showHud(GestureHudState(GestureHudType.NEXT_TRACK, "Next Track"))
                } else {
                    onPlayPrevious()
                    controller.showHud(GestureHudState(GestureHudType.PREVIOUS_TRACK, "Previous Track"))
                }
            }
            SwipeAction.SEEK_15S -> {
                triggerHaptic()
                if (isLeftSwipe) {
                    val target = (currentPositionMs + 15_000L).coerceAtMost(durationMs.coerceAtLeast(currentPositionMs))
                    onSeekTo(target)
                    controller.showHud(GestureHudState(GestureHudType.SEEK_FORWARD, "Forward +15s"))
                } else {
                    val target = (currentPositionMs - 15_000L).coerceAtLeast(0L)
                    onSeekTo(target)
                    controller.showHud(GestureHudState(GestureHudType.SEEK_BACKWARD, "Rewind -15s"))
                }
            }
            SwipeAction.CYCLE_STYLE -> {
                triggerHaptic()
                onCyclePlayerStyle?.invoke()
                controller.showHud(GestureHudState(GestureHudType.STYLE_CHANGED, "Style Switched"))
            }
            SwipeAction.OPEN_QUEUE -> {
                triggerHaptic()
                onOpenQueue()
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
            config.longPressStyleWheelEnabled,
            currentPositionMs,
            durationMs,
            isPlaying
        ) {
            detectTapGestures(
                onTap = {
                    if (controller.artworkZoomScale > 1.05f) {
                        controller.resetZoom()
                        return@detectTapGestures
                    }
                    if (!config.gesturesEnabled) {
                        onTogglePlayPause()
                        return@detectTapGestures
                    }
                    when (config.singleTapAction) {
                        SingleTapAction.TOGGLE_PLAY_PAUSE -> {
                            triggerHaptic()
                            onTogglePlayPause()
                            controller.showHud(
                                GestureHudState(
                                    GestureHudType.PLAY_PAUSE,
                                    if (isPlaying) "Paused" else "Playing",
                                    isPlaying = !isPlaying
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
                    if (!config.gesturesEnabled || config.longPressStyleWheelEnabled) {
                        triggerHaptic(HapticFeedbackType.LongPress)
                        onArtworkLongPress()
                    }
                },
                onDoubleTap = { offset ->
                    if (!config.gesturesEnabled || !config.doubleTapEnabled) return@detectTapGestures
                    when (config.doubleTapAction) {
                        DoubleTapAction.SEEK_10S -> {
                            triggerHaptic()
                            if (offset.x < size.width / 2f) {
                                val nextPos = (currentPositionMs - 10_000L).coerceAtLeast(0L)
                                onSeekTo(nextPos)
                                controller.showHud(GestureHudState(GestureHudType.SEEK_BACKWARD, "Rewind -10s"))
                            } else {
                                val nextPos = (currentPositionMs + 10_000L).coerceAtMost(durationMs.coerceAtLeast(currentPositionMs))
                                onSeekTo(nextPos)
                                controller.showHud(GestureHudState(GestureHudType.SEEK_FORWARD, "Forward +10s"))
                            }
                        }
                        DoubleTapAction.PLAY_PAUSE -> {
                            triggerHaptic()
                            onTogglePlayPause()
                            controller.showHud(
                                GestureHudState(
                                    GestureHudType.PLAY_PAUSE,
                                    if (isPlaying) "Paused" else "Playing",
                                    isPlaying = !isPlaying
                                )
                            )
                        }
                        DoubleTapAction.TOGGLE_FAVORITE -> {
                            triggerHaptic()
                            onToggleFavorite?.invoke()
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
            effectiveHorizontalThreshold
        ) {
            if (!config.gesturesEnabled || !config.swipeArtworkEnabled) return@pointerInput
            var dragX = 0f
            detectHorizontalDragGestures(
                onDragStart = { dragX = 0f },
                onDragCancel = { dragX = 0f },
                onDragEnd = {
                    if (dragX < -effectiveHorizontalThreshold) {
                        dispatchSwipe(config.swipeLeftAction, isLeftSwipe = true)
                    } else if (dragX > effectiveHorizontalThreshold) {
                        dispatchSwipe(config.swipeRightAction, isLeftSwipe = false)
                    }
                    dragX = 0f
                },
                onHorizontalDrag = { change, dragAmount ->
                    change.consume()
                    dragX += dragAmount
                }
            )
        }
        .pointerInput(
            config.gesturesEnabled,
            config.swipeDownToMinimizeEnabled,
            config.swipeUpToOpenQueueEnabled,
            effectiveVerticalThreshold
        ) {
            if (!config.gesturesEnabled) return@pointerInput
            if (!config.swipeDownToMinimizeEnabled && !config.swipeUpToOpenQueueEnabled) return@pointerInput
            var dragY = 0f
            detectVerticalDragGestures(
                onDragStart = { dragY = 0f },
                onDragCancel = { dragY = 0f },
                onDragEnd = {
                    if (config.swipeDownToMinimizeEnabled && dragY > effectiveVerticalThreshold) {
                        triggerHaptic()
                        onClose()
                    } else if (config.swipeUpToOpenQueueEnabled && dragY < -effectiveVerticalThreshold) {
                        triggerHaptic()
                        onOpenQueue()
                    }
                    dragY = 0f
                },
                onVerticalDrag = { change, dragAmount ->
                    change.consume()
                    dragY += dragAmount
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
    val systemBackSafePx = with(density) { 22.dp.toPx() }
    val edgeZoneOuterPx = with(density) { 80.dp.toPx() }
    val verticalSwipeThreshold = with(density) { (config.swipeThresholdDp * 1.4f).dp.toPx() } / config.swipeSensitivity.coerceIn(0.5f, 1.8f)

    this.pointerInput(
        config.gesturesEnabled,
        config.rightEdgeVolumeEnabled,
        config.leftEdgeBrightnessEnabled,
        config.swipeDownToMinimizeEnabled,
        config.swipeUpToOpenQueueEnabled,
        verticalSwipeThreshold
    ) {
        if (!config.gesturesEnabled) return@pointerInput
        var startX = 0f
        var dragY = 0f
        var inLeftEdge = false
        var inRightEdge = false

        detectVerticalDragGestures(
            onDragStart = { offset ->
                startX = offset.x
                dragY = 0f
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
            },
            onDragEnd = {
                if (!inLeftEdge && !inRightEdge) {
                    if (config.swipeDownToMinimizeEnabled && dragY > verticalSwipeThreshold) {
                        if (config.hapticFeedbackEnabled) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onClose()
                    } else if (config.swipeUpToOpenQueueEnabled && dragY < -verticalSwipeThreshold) {
                        if (config.hapticFeedbackEnabled) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onOpenQueue()
                    }
                }
                inLeftEdge = false
                inRightEdge = false
                dragY = 0f
            },
            onVerticalDrag = { change, amount ->
                change.consume()
                dragY += amount
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
