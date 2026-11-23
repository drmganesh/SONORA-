package ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.model.MiniPlayerStyle
import domain.player.ExpandedMiniPlayerGestureOutcome
import domain.player.MiniPlayerGestureAxis
import domain.player.MiniPlayerGestureController
import ui.motion.LocalEffectiveMotionState
import ui.motion.rememberMotionAwareHaptic

/**
 * Single reusable Compose gesture wrapper powering ALL 20 [MiniPlayerStyle] designs uniformly.
 *
 * Delegates all gesture axis locking and outcome classification to [MiniPlayerGestureController]
 * so every visual mini-player style exhibits identical gesture responsiveness:
 * - Swipe left / right: Next / Previous track (never triggers dismiss)
 * - Swipe up: Expand full Now Playing screen
 * - Swipe down (Stage 1): Smoothly shrinks/translates and triggers [onSwipeDownMinimize]
 *   (Normal Mini Player -> Floating Circular Player, or Dismissed if Circular mode is disabled)
 * - Yields immediately when an inner [InteractiveMiniSeekbar] is scrubbing or a child consumes touch
 * - Exposes equivalent Accessibility [customActions] so assistive users have full parity
 */
fun Modifier.universalMiniPlayerGestures(
    miniPlayerStyle: MiniPlayerStyle,
    gesturesEnabled: Boolean,
    swipeDownEnabled: Boolean,
    swipeThresholdDp: Int,
    isScrubbingSeekbar: () -> Boolean,
    onPlayNext: () -> Unit,
    onPlayPrevious: () -> Unit,
    onExpandNowPlaying: () -> Unit,
    onSwipeDownMinimize: () -> Unit
): Modifier = composed {
    val motionState = LocalEffectiveMotionState.current
    val performHaptic = rememberMotionAwareHaptic()
    val density = LocalDensity.current

    val swipeThresholdPx = with(density) { swipeThresholdDp.coerceAtLeast(36).dp.toPx() }
    val velocityThresholdPx = with(density) { motionState.velocityThresholdDpPerSec.dp.toPx() }
    val verticalExpandPx = with(density) { 26.dp.toPx() }
    val verticalDismissPx = with(density) { 38.dp.toPx() }

    val minimizeActionLabel = stringResource(id = R.string.mini_player_action_minimize)
    val openNowPlayingLabel = stringResource(id = R.string.mini_player_action_open_now_playing)
    val nextActionLabel = stringResource(id = R.string.mini_player_action_next)
    val previousActionLabel = stringResource(id = R.string.mini_player_action_previous)

    val latestStyle by rememberUpdatedState(miniPlayerStyle)
    val latestGesturesEnabled by rememberUpdatedState(gesturesEnabled)
    val latestSwipeDownEnabled by rememberUpdatedState(swipeDownEnabled)
    val latestIsScrubbing by rememberUpdatedState(isScrubbingSeekbar)
    val latestOnPlayNext by rememberUpdatedState(onPlayNext)
    val latestOnPlayPrevious by rememberUpdatedState(onPlayPrevious)
    val latestOnExpandNowPlaying by rememberUpdatedState(onExpandNowPlaying)
    val latestOnSwipeDownMinimize by rememberUpdatedState(onSwipeDownMinimize)

    var totalDragX by remember { mutableFloatStateOf(0f) }
    var totalDragY by remember { mutableFloatStateOf(0f) }

    val downwardProgress = MiniPlayerGestureController.expandedDownwardProgress(
        dragY = if (swipeDownEnabled) totalDragY else 0f,
        verticalDismissPx = verticalDismissPx
    )

    val animatedDragOffsetX by animateFloatAsState(
        targetValue = if (motionState.animationsEnabled) (totalDragX * 0.28f).coerceIn(-42f, 42f) else 0f,
        animationSpec = if (motionState.animationsEnabled) {
            spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium)
        } else {
            snap()
        },
        label = "universalMiniDragX"
    )
    val animatedDragOffsetY by animateFloatAsState(
        targetValue = if (motionState.animationsEnabled && swipeDownEnabled && totalDragY > 0f) {
            (totalDragY * 0.50f).coerceIn(0f, 64f)
        } else 0f,
        animationSpec = if (motionState.animationsEnabled) {
            spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow)
        } else {
            snap()
        },
        label = "universalMiniDragY"
    )
    val animatedDownwardScale by animateFloatAsState(
        targetValue = if (motionState.animationsEnabled && downwardProgress > 0f) {
            1f - (downwardProgress * 0.14f)
        } else 1f,
        animationSpec = if (motionState.animationsEnabled) {
            spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMedium)
        } else {
            snap()
        },
        label = "universalMiniDownwardScale"
    )
    val animatedDownwardAlpha by animateFloatAsState(
        targetValue = if (motionState.animationsEnabled && downwardProgress > 0f) {
            (1f - (downwardProgress * 0.35f)).coerceIn(0.62f, 1f)
        } else 1f,
        animationSpec = if (motionState.animationsEnabled) {
            spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMedium)
        } else {
            snap()
        },
        label = "universalMiniDownwardAlpha"
    )

    this
        .semantics {
            val actions = mutableListOf(
                CustomAccessibilityAction(openNowPlayingLabel) {
                    latestOnExpandNowPlaying()
                    true
                }
            )
            if (latestSwipeDownEnabled) {
                actions.add(
                    CustomAccessibilityAction(minimizeActionLabel) {
                        latestOnSwipeDownMinimize()
                        true
                    }
                )
            }
            if (latestGesturesEnabled) {
                actions.add(
                    CustomAccessibilityAction(nextActionLabel) {
                        latestOnPlayNext()
                        true
                    }
                )
                actions.add(
                    CustomAccessibilityAction(previousActionLabel) {
                        latestOnPlayPrevious()
                        true
                    }
                )
            }
            customActions = actions
        }
        .pointerInput(
            gesturesEnabled,
            swipeDownEnabled,
            swipeThresholdPx,
            velocityThresholdPx,
            verticalExpandPx,
            verticalDismissPx
        ) {
            if (!gesturesEnabled && !swipeDownEnabled) return@pointerInput
            val touchSlop = viewConfiguration.touchSlop
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                // Yield immediately if an inner InteractiveMiniSeekbar or child control claimed this touch
                if (down.isConsumed || latestIsScrubbing()) return@awaitEachGesture
                val pointerId = down.id
                var accumX = 0f
                var accumY = 0f
                var lockedAxis = MiniPlayerGestureAxis.UNDECIDED
                val tracker = VelocityTracker()
                tracker.addPosition(down.uptimeMillis, down.position)
                totalDragX = 0f
                totalDragY = 0f

                while (true) {
                    val event = awaitPointerEvent()
                    val change = event.changes.firstOrNull { it.id == pointerId } ?: break
                    if (latestIsScrubbing() || change.isConsumed) {
                        totalDragX = 0f
                        totalDragY = 0f
                        break
                    }
                    if (!change.pressed) {
                        val velocity = tracker.calculateVelocity()
                        val outcome = MiniPlayerGestureController.classifyExpandedGesture(
                            lockedAxis = lockedAxis,
                            dragX = accumX,
                            dragY = accumY,
                            velocityX = velocity.x,
                            velocityY = velocity.y,
                            swipeThresholdPx = swipeThresholdPx,
                            velocityThresholdPx = velocityThresholdPx,
                            verticalExpandPx = verticalExpandPx,
                            verticalDismissPx = verticalDismissPx,
                            touchSlopPx = touchSlop,
                            horizontalGesturesEnabled = latestGesturesEnabled,
                            swipeDownEnabled = latestSwipeDownEnabled,
                            style = latestStyle
                        )
                        when (outcome) {
                            ExpandedMiniPlayerGestureOutcome.SWIPE_NEXT -> {
                                performHaptic(HapticFeedbackType.TextHandleMove)
                                latestOnPlayNext()
                            }
                            ExpandedMiniPlayerGestureOutcome.SWIPE_PREVIOUS -> {
                                performHaptic(HapticFeedbackType.TextHandleMove)
                                latestOnPlayPrevious()
                            }
                            ExpandedMiniPlayerGestureOutcome.SWIPE_UP_EXPAND -> {
                                performHaptic(HapticFeedbackType.TextHandleMove)
                                latestOnExpandNowPlaying()
                            }
                            ExpandedMiniPlayerGestureOutcome.SWIPE_DOWN_MINIMIZE -> {
                                performHaptic(HapticFeedbackType.TextHandleMove)
                                latestOnSwipeDownMinimize()
                            }
                            ExpandedMiniPlayerGestureOutcome.NONE -> Unit
                        }
                        totalDragX = 0f
                        totalDragY = 0f
                        break
                    }

                    val delta = change.positionChange()
                    accumX += delta.x
                    accumY += delta.y
                    tracker.addPosition(change.uptimeMillis, change.position)

                    if (lockedAxis == MiniPlayerGestureAxis.UNDECIDED) {
                        lockedAxis = MiniPlayerGestureController.lockGestureAxis(
                            accumX = accumX,
                            accumY = accumY,
                            touchSlopPx = touchSlop
                        )
                    }

                    if (lockedAxis != MiniPlayerGestureAxis.UNDECIDED) {
                        change.consume()
                        if (lockedAxis == MiniPlayerGestureAxis.HORIZONTAL && latestGesturesEnabled) {
                            totalDragX = accumX
                        } else if (lockedAxis == MiniPlayerGestureAxis.VERTICAL && latestSwipeDownEnabled && accumY > 0f) {
                            totalDragY = accumY
                        }
                    }
                }
                totalDragX = 0f
                totalDragY = 0f
            }
        }
        .graphicsLayer {
            translationX = animatedDragOffsetX
            translationY = animatedDragOffsetY
            scaleX = animatedDownwardScale
            scaleY = animatedDownwardScale
            alpha = animatedDownwardAlpha
        }
}
