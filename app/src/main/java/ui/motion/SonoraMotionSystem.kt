package ui.motion

import android.provider.Settings
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import domain.motion.EffectiveMotionState
import domain.motion.GlobalMotionConfig
import domain.motion.ScreenTransitionStyle
import domain.motion.resolveEffectiveMotionState
import kotlin.math.abs

val LocalSonoraMotionConfig = compositionLocalOf { GlobalMotionConfig.DEFAULT }

val LocalEffectiveMotionState = compositionLocalOf {
    resolveEffectiveMotionState(GlobalMotionConfig.DEFAULT, systemAnimatorScale = 1f)
}

val LocalTrackTransitionDirection = compositionLocalOf { 0 }

@Composable
fun rememberSystemAnimatorScale(): Float {
    val context = LocalContext.current
    return remember(context) {
        try {
            Settings.Global.getFloat(
                context.contentResolver,
                Settings.Global.ANIMATOR_DURATION_SCALE,
                1f
            ).takeIf { it.isFinite() }?.coerceAtLeast(0f) ?: 1f
        } catch (_: Throwable) {
            1f
        }
    }
}

@Composable
fun rememberEffectiveMotionState(
    config: GlobalMotionConfig = LocalSonoraMotionConfig.current
): EffectiveMotionState {
    val systemScale = rememberSystemAnimatorScale()
    return remember(config, systemScale) {
        resolveEffectiveMotionState(config = config, systemAnimatorScale = systemScale)
    }
}

@Composable
fun rememberMotionAwareHaptic(): (HapticFeedbackType) -> Unit {
    val haptic = LocalHapticFeedback.current
    val motionState by rememberUpdatedState(LocalEffectiveMotionState.current)
    return remember(haptic) {
        { type ->
            if (motionState.hapticEnabled) {
                haptic.performHapticFeedback(type)
            }
        }
    }
}

/**
 * Shared transition builders ensuring consistent choreography across tabs, overlays, modals,
 * Home Offline/Online switching, and Now Playing track switches.
 */
object SonoraTransitions {

    private fun intOffsetSpec(motion: EffectiveMotionState, baseMs: Int = 280) =
        if (motion.transitionStyle == ScreenTransitionStyle.DYNAMIC) {
            spring<IntOffset>(
                dampingRatio = motion.config.intensity.springDampingRatio,
                stiffness = motion.config.intensity.springStiffness
            )
        } else {
            tween<IntOffset>(durationMillis = motion.durationMillis(baseMs), easing = FastOutSlowInEasing)
        }

    fun tabTransition(
        forward: Boolean,
        motion: EffectiveMotionState
    ): ContentTransform {
        if (!motion.animationsEnabled) {
            return EnterTransition.None togetherWith ExitTransition.None
        }
        val enterDur = motion.durationMillis(260)
        val exitDur = motion.durationMillis(200)
        val dir = if (forward) 1 else -1

        return when (motion.transitionStyle) {
            ScreenTransitionStyle.FADE -> {
                (fadeIn(tween(enterDur)) + scaleIn(tween(enterDur), initialScale = 0.985f)) togetherWith
                    fadeOut(tween(exitDur))
            }
            ScreenTransitionStyle.SLIDE -> {
                val offsetSpec = tween<IntOffset>(enterDur, easing = FastOutSlowInEasing)
                (slideInHorizontally(offsetSpec) { w -> (w * 0.24f * motion.distanceScale * dir).toInt() } +
                    fadeIn(tween(enterDur))) togetherWith
                    (slideOutHorizontally(tween(exitDur)) { w -> (-w * 0.18f * motion.distanceScale * dir).toInt() } +
                        fadeOut(tween(exitDur)))
            }
            ScreenTransitionStyle.SMOOTH -> {
                val offsetSpec = tween<IntOffset>(enterDur, easing = FastOutSlowInEasing)
                (slideInHorizontally(offsetSpec) { w -> (w * 0.12f * motion.distanceScale * dir).toInt() } +
                    fadeIn(tween(enterDur)) +
                    scaleIn(tween(enterDur), initialScale = 0.98f)) togetherWith
                    (slideOutHorizontally(tween(exitDur)) { w -> (-w * 0.10f * motion.distanceScale * dir).toInt() } +
                        fadeOut(tween(exitDur)))
            }
            ScreenTransitionStyle.DYNAMIC -> {
                val springSpec = intOffsetSpec(motion, 300)
                (slideInHorizontally(springSpec) { w -> (w * 0.16f * motion.distanceScale * dir).toInt() } +
                    fadeIn(tween(enterDur)) +
                    scaleIn(tween(enterDur), initialScale = 0.965f)) togetherWith
                    (slideOutHorizontally(tween(exitDur)) { w -> (-w * 0.12f * motion.distanceScale * dir).toInt() } +
                        fadeOut(tween(exitDur)) +
                        scaleOut(tween(exitDur), targetScale = 0.98f))
            }
        }
    }

    fun pushEnter(motion: EffectiveMotionState): EnterTransition {
        if (!motion.animationsEnabled) return EnterTransition.None
        val dur = motion.durationMillis(300)
        return when (motion.transitionStyle) {
            ScreenTransitionStyle.FADE -> fadeIn(tween(dur)) + scaleIn(tween(dur), initialScale = 0.96f)
            ScreenTransitionStyle.SLIDE -> slideInHorizontally(tween(dur, easing = FastOutSlowInEasing)) { w ->
                (w * 0.35f * motion.distanceScale).toInt().coerceAtLeast(1)
            } + fadeIn(tween(dur))
            ScreenTransitionStyle.SMOOTH -> slideInHorizontally(tween(dur, easing = FastOutSlowInEasing)) { w ->
                (w * 0.22f * motion.distanceScale).toInt().coerceAtLeast(1)
            } + fadeIn(tween(dur)) + scaleIn(tween(dur), initialScale = 0.97f)
            ScreenTransitionStyle.DYNAMIC -> slideInHorizontally(intOffsetSpec(motion, 320)) { w ->
                (w * 0.26f * motion.distanceScale).toInt().coerceAtLeast(1)
            } + fadeIn(tween(dur)) + scaleIn(tween(dur), initialScale = 0.95f)
        }
    }

    fun pushExit(motion: EffectiveMotionState): ExitTransition {
        if (!motion.animationsEnabled) return ExitTransition.None
        val dur = motion.durationMillis(240)
        return when (motion.transitionStyle) {
            ScreenTransitionStyle.FADE -> fadeOut(tween(dur)) + scaleOut(tween(dur), targetScale = 0.97f)
            ScreenTransitionStyle.SLIDE -> slideOutHorizontally(tween(dur, easing = FastOutSlowInEasing)) { w ->
                (w * 0.35f * motion.distanceScale).toInt().coerceAtLeast(1)
            } + fadeOut(tween(dur))
            ScreenTransitionStyle.SMOOTH,
            ScreenTransitionStyle.DYNAMIC -> slideOutHorizontally(tween(dur, easing = FastOutSlowInEasing)) { w ->
                (w * 0.24f * motion.distanceScale).toInt().coerceAtLeast(1)
            } + fadeOut(tween(dur)) + scaleOut(tween(dur), targetScale = 0.97f)
        }
    }

    fun modalEnter(motion: EffectiveMotionState): EnterTransition {
        if (!motion.animationsEnabled) return EnterTransition.None
        val dur = motion.durationMillis(320)
        val fraction = when (motion.transitionStyle) {
            ScreenTransitionStyle.FADE -> 0.08f
            ScreenTransitionStyle.SLIDE -> 1.0f
            ScreenTransitionStyle.SMOOTH -> 0.45f
            ScreenTransitionStyle.DYNAMIC -> 0.6f
        }
        return slideInVertically(
            animationSpec = intOffsetSpec(motion, 320),
            initialOffsetY = { h -> (h * fraction).toInt() }
        ) + fadeIn(tween(dur)) + scaleIn(tween(dur), initialScale = 0.96f)
    }

    fun modalExit(motion: EffectiveMotionState): ExitTransition {
        if (!motion.animationsEnabled) return ExitTransition.None
        val dur = motion.durationMillis(240)
        val fraction = when (motion.transitionStyle) {
            ScreenTransitionStyle.FADE -> 0.08f
            ScreenTransitionStyle.SLIDE -> 1.0f
            ScreenTransitionStyle.SMOOTH -> 0.45f
            ScreenTransitionStyle.DYNAMIC -> 0.6f
        }
        return slideOutVertically(
            animationSpec = tween(dur, easing = FastOutSlowInEasing),
            targetOffsetY = { h -> (h * fraction).toInt() }
        ) + fadeOut(tween(dur))
    }

    fun homeModeTransition(toOnline: Boolean, motion: EffectiveMotionState): ContentTransform {
        if (!motion.animationsEnabled) {
            return EnterTransition.None togetherWith ExitTransition.None
        }
        val enterDur = motion.durationMillis(300)
        val exitDur = motion.durationMillis(220)
        val dir = if (toOnline) 1 else -1
        val slideOffset = if (motion.transitionStyle == ScreenTransitionStyle.FADE) 0.04f else 0.16f
        return (slideInHorizontally(intOffsetSpec(motion, 300)) { w ->
            (w * slideOffset * motion.distanceScale * dir).toInt()
        } + fadeIn(tween(enterDur)) + scaleIn(tween(enterDur), initialScale = 0.975f)) togetherWith
            (slideOutHorizontally(tween(exitDur)) { w ->
                (-w * slideOffset * motion.distanceScale * dir).toInt()
            } + fadeOut(tween(exitDur)))
    }

    fun trackTransition(direction: Int, motion: EffectiveMotionState): ContentTransform {
        if (!motion.artworkTransitionsEnabled) {
            return EnterTransition.None togetherWith ExitTransition.None
        }
        val enterDur = motion.durationMillis(260)
        val exitDur = motion.durationMillis(190)
        if (direction == 0) {
            return (fadeIn(tween(enterDur)) + scaleIn(tween(enterDur), initialScale = 0.95f)) togetherWith
                (fadeOut(tween(exitDur)) + scaleOut(tween(exitDur), targetScale = 0.97f))
        }
        val sign = if (direction > 0) 1 else -1
        return (slideInHorizontally(tween(enterDur, easing = FastOutSlowInEasing)) { w ->
            (w * 0.22f * motion.distanceScale * sign).toInt()
        } + fadeIn(tween(enterDur)) + scaleIn(tween(enterDur), initialScale = 0.94f)) togetherWith
            (slideOutHorizontally(tween(exitDur, easing = FastOutSlowInEasing)) { w ->
                (-w * 0.18f * motion.distanceScale * sign).toInt()
            } + fadeOut(tween(exitDur)) + scaleOut(tween(exitDur), targetScale = 0.94f))
    }

    fun metadataTransition(direction: Int, motion: EffectiveMotionState): ContentTransform {
        if (!motion.artworkTransitionsEnabled) {
            return EnterTransition.None togetherWith ExitTransition.None
        }
        val enterDur = motion.durationMillis(230)
        val exitDur = motion.durationMillis(160)
        if (direction == 0) {
            return fadeIn(tween(enterDur)) togetherWith fadeOut(tween(exitDur))
        }
        val sign = if (direction > 0) 1 else -1
        return (slideInHorizontally(tween(enterDur, easing = FastOutSlowInEasing)) { w ->
            (w * 0.16f * motion.distanceScale * sign).toInt()
        } + fadeIn(tween(enterDur))) togetherWith
            (slideOutHorizontally(tween(exitDur, easing = FastOutSlowInEasing)) { w ->
                (-w * 0.14f * motion.distanceScale * sign).toInt()
            } + fadeOut(tween(exitDur)))
    }
}

/**
 * Adds subtle viewport-center proximity scale and depth fade to horizontal carousel cards
 * exclusively inside graphicsLayer (zero extra recompositions while scrolling).
 */
fun Modifier.carouselItemDepthEffect(
    listState: LazyListState,
    itemIndex: Int,
    motion: EffectiveMotionState
): Modifier {
    if (!motion.parallaxAndScaleEnabled) return this
    return this.graphicsLayer {
        val layoutInfo = listState.layoutInfo
        val itemInfo = layoutInfo.visibleItemsInfo.firstOrNull { it.index == itemIndex } ?: return@graphicsLayer
        val viewportCenter = (layoutInfo.viewportStartOffset + layoutInfo.viewportEndOffset) / 2f
        val itemCenter = itemInfo.offset + itemInfo.size / 2f
        val maxDist = (layoutInfo.viewportEndOffset - layoutInfo.viewportStartOffset).coerceAtLeast(1) / 2f
        val normalizedDist = (abs(itemCenter - viewportCenter) / maxDist).coerceIn(0f, 1f)
        val maxScaleDrop = if (motion.distanceScale > 1.1f) 0.055f else 0.04f
        val scale = 1f - (normalizedDist * maxScaleDrop)
        scaleX = scale
        scaleY = scale
        alpha = 1f - (normalizedDist * 0.12f)
    }
}

/**
 * Non-conflicting horizontal swipe modifier for parent tab/mode containers:
 * - Respects [EffectiveMotionState.swipeNavigationEnabled]
 * - Leaves a 24.dp inset on left/right screen edges so system back gestures remain untouched
 * - Yields automatically to any child LazyRow, HorizontalPager, or Slider consuming horizontal drag
 * - Evaluates both drag distance and velocity against [GestureSensitivityLevel]
 */
fun Modifier.horizontalSwipeNavigation(
    enabled: Boolean,
    motion: EffectiveMotionState,
    onSwipeLeft: () -> Unit,
    onSwipeRight: () -> Unit
): Modifier = composed {
    val density = LocalDensity.current
    val haptic = LocalHapticFeedback.current
    val thresholdPx = with(density) { motion.swipeThresholdDp.dp.toPx() }
    val velocityThresholdPx = with(density) { motion.velocityThresholdDpPerSec.dp.toPx() }
    val edgeExclusionPx = with(density) { 24.dp.toPx() }

    val latestEnabled by rememberUpdatedState(enabled && motion.swipeNavigationEnabled)
    val latestHapticEnabled by rememberUpdatedState(motion.hapticEnabled)
    val latestOnSwipeLeft by rememberUpdatedState(onSwipeLeft)
    val latestOnSwipeRight by rememberUpdatedState(onSwipeRight)

    this.pointerInput(enabled, motion.swipeNavigationEnabled, thresholdPx, velocityThresholdPx) {
        if (!latestEnabled) return@pointerInput
        var totalDragX = 0f
        var startedInSystemEdge = false
        var velocityTracker: VelocityTracker? = null

        detectHorizontalDragGestures(
            onDragStart = { startOffset ->
                totalDragX = 0f
                val w = size.width.toFloat()
                startedInSystemEdge = startOffset.x < edgeExclusionPx || startOffset.x > (w - edgeExclusionPx)
                velocityTracker = VelocityTracker()
            },
            onDragCancel = {
                totalDragX = 0f
                velocityTracker = null
            },
            onDragEnd = {
                if (!startedInSystemEdge) {
                    val velocityX = velocityTracker?.calculateVelocity()?.x ?: 0f
                    val triggered = motion.shouldTriggerSwipe(
                        dragPx = totalDragX,
                        velocityPxPerSec = velocityX,
                        thresholdPx = thresholdPx,
                        velocityPxThreshold = velocityThresholdPx
                    )
                    if (triggered) {
                        val goingLeft = if (abs(totalDragX) > thresholdPx * 0.45f) totalDragX < 0f else velocityX < 0f
                        if (latestHapticEnabled) {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        }
                        if (goingLeft) latestOnSwipeLeft() else latestOnSwipeRight()
                    }
                }
                totalDragX = 0f
                velocityTracker = null
            },
            onHorizontalDrag = { change, dragAmount ->
                if (startedInSystemEdge || change.isConsumed) return@detectHorizontalDragGestures
                change.consume()
                totalDragX += dragAmount
                velocityTracker?.addPosition(change.uptimeMillis, change.position)
            }
        )
    }
}
