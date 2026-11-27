package ui.motion

import android.app.ActivityManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
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
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import domain.motion.EffectiveMotionState
import domain.motion.GlobalMotionConfig
import domain.motion.MotionEffectsLevel
import domain.motion.ScreenTransitionStyle
import domain.motion.resolveEffectiveMotionState
import kotlin.math.abs

val LocalSonoraMotionConfig = compositionLocalOf { GlobalMotionConfig.DEFAULT }

val LocalEffectiveMotionState = compositionLocalOf {
    resolveEffectiveMotionState(GlobalMotionConfig.DEFAULT, systemAnimatorScale = 1f)
}

val LocalTrackTransitionDirection = compositionLocalOf { 0 }

/**
 * Reusable SONORA Motion System Tokens & Easing Curves.
 *
 * Calibrated for natural, responsive, 60/90/120Hz Android motion:
 * - Small button feedback: 100-160 ms
 * - Small UI / selection transitions: 150-220 ms
 * - Cards, artwork & content transitions: 200-300 ms
 * - Major screen & player transitions: 250-350 ms
 */
object SonoraMotionSpecs {
    const val BUTTON_FEEDBACK_MS = 120
    const val SMALL_UI_MS = 180
    const val SELECTION_MS = 170
    const val CARD_CONTENT_MS = 240
    const val ARTWORK_CROSSFADE_MS = 180
    const val MINI_PLAYER_ENTER_MS = 220
    const val MINI_PLAYER_EXIT_MS = 170
    const val DIALOG_ENTER_MS = 210
    const val DIALOG_EXIT_MS = 160
    const val SHEET_ENTER_MS = 280
    const val SHEET_EXIT_MS = 210
    const val SCREEN_ENTER_MS = 280
    const val SCREEN_EXIT_MS = 210
    const val PLAYER_EXPAND_MS = 310
    const val PLAYER_DISMISS_MS = 230
    const val COLOR_TRANSITION_MS = 340
    const val NAVIGATION_DEBOUNCE_MS = 260L

    /** Material 3 Emphasized Decelerate curve for smooth entrances. */
    val EmphasizedDecelerate = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1.0f)

    /** Material 3 Emphasized Accelerate curve for brisk exits. */
    val EmphasizedAccelerate = CubicBezierEasing(0.3f, 0.0f, 0.8f, 0.15f)

    /** Standard smooth curve for balanced state changes. */
    val StandardEasing = FastOutSlowInEasing

    fun buttonScaleSpec(motion: EffectiveMotionState): FiniteAnimationSpec<Float> {
        if (!motion.essentialFeedbackEnabled) return snap()
        return if (motion.effectiveEffectsLevel == MotionEffectsLevel.FULL_MOTION) {
            spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessHigh)
        } else {
            tween(durationMillis = motion.durationMillis(BUTTON_FEEDBACK_MS, essential = true), easing = StandardEasing)
        }
    }

    fun selectionFloatSpec(motion: EffectiveMotionState): FiniteAnimationSpec<Float> {
        if (!motion.essentialFeedbackEnabled) return snap()
        return if (motion.effectiveEffectsLevel == MotionEffectsLevel.FULL_MOTION) {
            spring(dampingRatio = motion.config.intensity.springDampingRatio, stiffness = 520f)
        } else {
            tween(durationMillis = motion.durationMillis(SELECTION_MS, essential = true), easing = StandardEasing)
        }
    }

    fun selectionColorSpec(motion: EffectiveMotionState): FiniteAnimationSpec<Color> {
        return colorSpec(motion, SELECTION_MS)
    }

    fun colorSpec(
        motion: EffectiveMotionState,
        baseDurationMs: Int = SELECTION_MS
    ): FiniteAnimationSpec<Color> {
        if (!motion.essentialFeedbackEnabled) return snap()
        val ms = motion.durationMillis(baseDurationMs, essential = true)
        return if (ms <= 0) snap() else tween(durationMillis = ms, easing = StandardEasing)
    }

    fun cardFloatSpec(motion: EffectiveMotionState): FiniteAnimationSpec<Float> {
        val ms = motion.durationMillis(CARD_CONTENT_MS)
        return if (ms <= 0) snap() else tween(durationMillis = ms, easing = EmphasizedDecelerate)
    }

    fun progressSpec(motion: EffectiveMotionState): FiniteAnimationSpec<Float> {
        val ms = motion.durationMillis(SMALL_UI_MS, essential = true)
        return if (ms <= 0) snap() else tween(durationMillis = ms, easing = LinearOutSlowInEasing)
    }

    fun gestureSpringSpec(motion: EffectiveMotionState): FiniteAnimationSpec<Float> {
        if (!motion.animationsEnabled) return snap()
        return spring(
            dampingRatio = if (motion.allowLargeScaleMovement) Spring.DampingRatioMediumBouncy else Spring.DampingRatioNoBouncy,
            stiffness = if (motion.allowLargeScaleMovement) Spring.StiffnessMedium else Spring.StiffnessMediumLow
        )
    }
}

/**
 * Lightweight, testable navigation & action debounce guard preventing duplicate screen opens,
 * overlapping transitions, or repeated destructive actions from rapid taps.
 */
class NavigationDebounceGuard(
    private val cooldownMs: Long = SonoraMotionSpecs.NAVIGATION_DEBOUNCE_MS,
    private val clockMs: () -> Long = { SystemClock.elapsedRealtime() }
) {
    private var lastAcceptedMs: Long = -cooldownMs

    fun tryAcquire(): Boolean {
        val now = clockMs()
        if (now - lastAcceptedMs < cooldownMs) return false
        lastAcceptedMs = now
        return true
    }

    inline fun tryNavigate(action: () -> Unit): Boolean {
        if (!tryAcquire()) return false
        action()
        return true
    }

    fun reset() {
        lastAcceptedMs = -cooldownMs
    }
}

@Composable
fun rememberNavigationDebounce(
    cooldownMs: Long = SonoraMotionSpecs.NAVIGATION_DEBOUNCE_MS
): NavigationDebounceGuard = remember(cooldownMs) {
    NavigationDebounceGuard(cooldownMs = cooldownMs)
}

/**
 * Subtle tactile press-scale modifier evaluated strictly inside graphicsLayer (zero layout passes).
 * Respects Minimal Motion & Android system animator scale automatically.
 */
fun Modifier.sonoraPressFeedback(
    interactionSource: InteractionSource,
    pressedScale: Float = 0.965f
): Modifier = composed {
    val motion = LocalEffectiveMotionState.current
    val pressed by interactionSource.collectIsPressedAsState()
    val targetScale = if (pressed && motion.essentialFeedbackEnabled) {
        if (motion.effectiveEffectsLevel == MotionEffectsLevel.FULL_MOTION) pressedScale else 0.985f
    } else {
        1f
    }
    val scaleState = animateFloatAsState(
        targetValue = targetScale,
        animationSpec = SonoraMotionSpecs.buttonScaleSpec(motion),
        label = "sonora_press_scale"
    )
    this.graphicsLayer {
        val s = scaleState.value
        scaleX = s
        scaleY = s
    }
}

private fun readSystemAnimatorScale(context: Context): Float = try {
    Settings.Global.getFloat(
        context.contentResolver,
        Settings.Global.ANIMATOR_DURATION_SCALE,
        1f
    ).takeIf { it.isFinite() }?.coerceAtLeast(0f) ?: 1f
} catch (_: Throwable) {
    1f
}

private fun readPowerSaveMode(context: Context): Boolean = try {
    val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
    pm?.isPowerSaveMode == true
} catch (_: Throwable) {
    false
}

private fun readTouchExplorationEnabled(context: Context): Boolean = try {
    val am = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as? AccessibilityManager
    am?.isTouchExplorationEnabled == true
} catch (_: Throwable) {
    false
}

private fun readIsLowRamDevice(context: Context): Boolean = try {
    val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
    am?.isLowRamDevice == true
} catch (_: Throwable) {
    false
}

/**
 * Live-observes Android's ANIMATOR_DURATION_SCALE so toggling "Remove animations" in System Settings
 * updates SONORA immediately upon resume or live change.
 */
@Composable
fun rememberSystemAnimatorScale(): Float {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var scale by remember(context) { mutableFloatStateOf(readSystemAnimatorScale(context)) }

    DisposableEffect(context, lifecycleOwner) {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                scale = readSystemAnimatorScale(context)
            }
        }
        val uri = Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE)
        runCatching { context.contentResolver.registerContentObserver(uri, false, observer) }

        val lifecycleObserver = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_START || event == Lifecycle.Event.ON_RESUME) {
                scale = readSystemAnimatorScale(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(lifecycleObserver)
        onDispose {
            runCatching { context.contentResolver.unregisterContentObserver(observer) }
            lifecycleOwner.lifecycle.removeObserver(lifecycleObserver)
        }
    }
    return scale
}

/**
 * Live-observes Android Battery Saver mode (`PowerManager.ACTION_POWER_SAVE_MODE_CHANGED`).
 */
@Composable
fun rememberSystemPowerSaveMode(): Boolean {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var isPowerSave by remember(context) { mutableStateOf(readPowerSaveMode(context)) }

    DisposableEffect(context, lifecycleOwner) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                isPowerSave = readPowerSaveMode(context)
            }
        }
        val filter = IntentFilter(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED)
        runCatching {
            androidx.core.content.ContextCompat.registerReceiver(
                context,
                receiver,
                filter,
                androidx.core.content.ContextCompat.RECEIVER_NOT_EXPORTED
            )
        }
        val lifecycleObserver = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_START || event == Lifecycle.Event.ON_RESUME) {
                isPowerSave = readPowerSaveMode(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(lifecycleObserver)
        onDispose {
            runCatching { context.unregisterReceiver(receiver) }
            lifecycleOwner.lifecycle.removeObserver(lifecycleObserver)
        }
    }
    return isPowerSave
}

@Composable
fun rememberTouchExplorationEnabled(): Boolean {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var enabled by remember(context) { mutableStateOf(readTouchExplorationEnabled(context)) }

    DisposableEffect(context, lifecycleOwner) {
        val am = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as? AccessibilityManager
        val listener = AccessibilityManager.TouchExplorationStateChangeListener { isEnabled ->
            enabled = isEnabled
        }
        runCatching { am?.addTouchExplorationStateChangeListener(listener) }
        val lifecycleObserver = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                enabled = readTouchExplorationEnabled(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(lifecycleObserver)
        onDispose {
            runCatching { am?.removeTouchExplorationStateChangeListener(listener) }
            lifecycleOwner.lifecycle.removeObserver(lifecycleObserver)
        }
    }
    return enabled
}

@Composable
fun rememberEffectiveMotionState(
    config: GlobalMotionConfig = LocalSonoraMotionConfig.current
): EffectiveMotionState {
    val context = LocalContext.current
    val systemScale = rememberSystemAnimatorScale()
    val powerSave = rememberSystemPowerSaveMode()
    val touchExploration = rememberTouchExplorationEnabled()
    val lowRam = remember(context) { readIsLowRamDevice(context) }

    return remember(config, systemScale, powerSave, lowRam, touchExploration) {
        resolveEffectiveMotionState(
            config = config,
            systemAnimatorScale = systemScale,
            isPowerSaveMode = powerSave,
            isLowRamDevice = lowRam,
            isTouchExplorationEnabled = touchExploration
        )
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
 * mini-player, dialogs, Home Offline/Online switching, onboarding steps, and Now Playing track switches.
 */
object SonoraTransitions {

    private fun intOffsetSpec(motion: EffectiveMotionState, baseMs: Int = SonoraMotionSpecs.SCREEN_ENTER_MS) =
        if (motion.transitionStyle == ScreenTransitionStyle.DYNAMIC && motion.allowLargeScaleMovement) {
            spring<IntOffset>(
                dampingRatio = motion.config.intensity.springDampingRatio,
                stiffness = motion.config.intensity.springStiffness
            )
        } else {
            tween<IntOffset>(
                durationMillis = motion.durationMillis(baseMs),
                easing = SonoraMotionSpecs.EmphasizedDecelerate
            )
        }

    fun tabTransition(
        forward: Boolean,
        motion: EffectiveMotionState
    ): ContentTransform {
        if (!motion.animationsEnabled) {
            val minimalFade = motion.durationMillis(110, essential = true)
            return if (minimalFade > 0) {
                fadeIn(tween(minimalFade)) togetherWith fadeOut(tween(minimalFade))
            } else {
                EnterTransition.None togetherWith ExitTransition.None
            }
        }
        val enterDur = motion.durationMillis(230)
        val exitDur = motion.durationMillis(170)
        val dir = if (forward) 1 else -1

        if (!motion.allowLargeScaleMovement) {
            return fadeIn(tween(enterDur, easing = SonoraMotionSpecs.EmphasizedDecelerate)) togetherWith
                fadeOut(tween(exitDur, easing = SonoraMotionSpecs.EmphasizedAccelerate))
        }

        return when (motion.transitionStyle) {
            ScreenTransitionStyle.FADE -> {
                fadeIn(tween(enterDur, easing = SonoraMotionSpecs.EmphasizedDecelerate)) togetherWith
                    fadeOut(tween(exitDur, easing = SonoraMotionSpecs.EmphasizedAccelerate))
            }
            ScreenTransitionStyle.SLIDE -> {
                val offsetSpec = tween<IntOffset>(enterDur, easing = SonoraMotionSpecs.EmphasizedDecelerate)
                (slideInHorizontally(offsetSpec) { w -> (w * 0.16f * motion.distanceScale * dir).toInt() } +
                    fadeIn(tween(enterDur))) togetherWith
                    (slideOutHorizontally(tween(exitDur, easing = SonoraMotionSpecs.EmphasizedAccelerate)) { w ->
                        (-w * 0.12f * motion.distanceScale * dir).toInt()
                    } + fadeOut(tween(exitDur)))
            }
            ScreenTransitionStyle.SMOOTH -> {
                val offsetSpec = tween<IntOffset>(enterDur, easing = SonoraMotionSpecs.EmphasizedDecelerate)
                (slideInHorizontally(offsetSpec) { w -> (w * 0.08f * motion.distanceScale * dir).toInt() } +
                    fadeIn(tween(enterDur))) togetherWith
                    (slideOutHorizontally(tween(exitDur, easing = SonoraMotionSpecs.EmphasizedAccelerate)) { w ->
                        (-w * 0.06f * motion.distanceScale * dir).toInt()
                    } + fadeOut(tween(exitDur)))
            }
            ScreenTransitionStyle.DYNAMIC -> {
                val springSpec = intOffsetSpec(motion, 260)
                (slideInHorizontally(springSpec) { w -> (w * 0.12f * motion.distanceScale * dir).toInt() } +
                    fadeIn(tween(enterDur))) togetherWith
                    (slideOutHorizontally(tween(exitDur, easing = SonoraMotionSpecs.EmphasizedAccelerate)) { w ->
                        (-w * 0.09f * motion.distanceScale * dir).toInt()
                    } + fadeOut(tween(exitDur)))
            }
        }
    }

    fun pushEnter(motion: EffectiveMotionState): EnterTransition {
        if (!motion.animationsEnabled) {
            val minimalFade = motion.durationMillis(100, essential = true)
            return if (minimalFade > 0) fadeIn(tween(minimalFade)) else EnterTransition.None
        }
        val dur = motion.durationMillis(SonoraMotionSpecs.SCREEN_ENTER_MS)
        if (!motion.allowLargeScaleMovement) {
            return fadeIn(tween(dur, easing = SonoraMotionSpecs.EmphasizedDecelerate))
        }
        return when (motion.transitionStyle) {
            ScreenTransitionStyle.FADE -> fadeIn(tween(dur, easing = SonoraMotionSpecs.EmphasizedDecelerate))
            ScreenTransitionStyle.SLIDE -> slideInHorizontally(tween(dur, easing = SonoraMotionSpecs.EmphasizedDecelerate)) { w ->
                (w * 0.24f * motion.distanceScale).toInt().coerceAtLeast(1)
            } + fadeIn(tween(dur))
            ScreenTransitionStyle.SMOOTH -> slideInHorizontally(tween(dur, easing = SonoraMotionSpecs.EmphasizedDecelerate)) { w ->
                (w * 0.16f * motion.distanceScale).toInt().coerceAtLeast(1)
            } + fadeIn(tween(dur))
            ScreenTransitionStyle.DYNAMIC -> slideInHorizontally(intOffsetSpec(motion, 300)) { w ->
                (w * 0.20f * motion.distanceScale).toInt().coerceAtLeast(1)
            } + fadeIn(tween(dur)) + scaleIn(tween(dur), initialScale = 0.975f)
        }
    }

    fun pushExit(motion: EffectiveMotionState): ExitTransition {
        if (!motion.animationsEnabled) {
            val minimalFade = motion.durationMillis(90, essential = true)
            return if (minimalFade > 0) fadeOut(tween(minimalFade)) else ExitTransition.None
        }
        val dur = motion.durationMillis(SonoraMotionSpecs.SCREEN_EXIT_MS)
        if (!motion.allowLargeScaleMovement) {
            return fadeOut(tween(dur, easing = SonoraMotionSpecs.EmphasizedAccelerate))
        }
        return when (motion.transitionStyle) {
            ScreenTransitionStyle.FADE -> fadeOut(tween(dur, easing = SonoraMotionSpecs.EmphasizedAccelerate))
            ScreenTransitionStyle.SLIDE -> slideOutHorizontally(tween(dur, easing = SonoraMotionSpecs.EmphasizedAccelerate)) { w ->
                (w * 0.24f * motion.distanceScale).toInt().coerceAtLeast(1)
            } + fadeOut(tween(dur))
            ScreenTransitionStyle.SMOOTH,
            ScreenTransitionStyle.DYNAMIC -> slideOutHorizontally(tween(dur, easing = SonoraMotionSpecs.EmphasizedAccelerate)) { w ->
                (w * 0.18f * motion.distanceScale).toInt().coerceAtLeast(1)
            } + fadeOut(tween(dur))
        }
    }

    fun modalEnter(motion: EffectiveMotionState): EnterTransition {
        if (!motion.animationsEnabled) {
            val minimalFade = motion.durationMillis(110, essential = true)
            return if (minimalFade > 0) fadeIn(tween(minimalFade)) else EnterTransition.None
        }
        val dur = motion.durationMillis(SonoraMotionSpecs.PLAYER_EXPAND_MS)
        if (!motion.allowLargeScaleMovement) {
            return fadeIn(tween(dur, easing = SonoraMotionSpecs.EmphasizedDecelerate))
        }
        val fraction = when (motion.transitionStyle) {
            ScreenTransitionStyle.FADE -> 0.06f
            ScreenTransitionStyle.SLIDE -> 0.65f
            ScreenTransitionStyle.SMOOTH -> 0.28f
            ScreenTransitionStyle.DYNAMIC -> 0.36f
        }
        return slideInVertically(
            animationSpec = intOffsetSpec(motion, SonoraMotionSpecs.PLAYER_EXPAND_MS),
            initialOffsetY = { h -> (h * fraction).toInt() }
        ) + fadeIn(tween(dur, easing = SonoraMotionSpecs.EmphasizedDecelerate))
    }

    fun modalExit(motion: EffectiveMotionState): ExitTransition {
        if (!motion.animationsEnabled) {
            val minimalFade = motion.durationMillis(95, essential = true)
            return if (minimalFade > 0) fadeOut(tween(minimalFade)) else ExitTransition.None
        }
        val dur = motion.durationMillis(SonoraMotionSpecs.PLAYER_DISMISS_MS)
        if (!motion.allowLargeScaleMovement) {
            return fadeOut(tween(dur, easing = SonoraMotionSpecs.EmphasizedAccelerate))
        }
        val fraction = when (motion.transitionStyle) {
            ScreenTransitionStyle.FADE -> 0.06f
            ScreenTransitionStyle.SLIDE -> 0.65f
            ScreenTransitionStyle.SMOOTH -> 0.28f
            ScreenTransitionStyle.DYNAMIC -> 0.36f
        }
        return slideOutVertically(
            animationSpec = tween(dur, easing = SonoraMotionSpecs.EmphasizedAccelerate),
            targetOffsetY = { h -> (h * fraction).toInt() }
        ) + fadeOut(tween(dur, easing = SonoraMotionSpecs.EmphasizedAccelerate))
    }

    fun miniPlayerEnter(motion: EffectiveMotionState): EnterTransition {
        if (!motion.animationsEnabled) {
            val ms = motion.durationMillis(100, essential = true)
            return if (ms > 0) fadeIn(tween(ms)) else EnterTransition.None
        }
        val dur = motion.durationMillis(SonoraMotionSpecs.MINI_PLAYER_ENTER_MS)
        return if (motion.allowLargeScaleMovement) {
            fadeIn(tween(dur, easing = SonoraMotionSpecs.EmphasizedDecelerate)) +
                slideInVertically(
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioNoBouncy,
                        stiffness = Spring.StiffnessMediumLow
                    ),
                    initialOffsetY = { it / 3 }
                )
        } else {
            fadeIn(tween(dur, easing = SonoraMotionSpecs.EmphasizedDecelerate))
        }
    }

    fun miniPlayerExit(motion: EffectiveMotionState): ExitTransition {
        if (!motion.animationsEnabled) {
            val ms = motion.durationMillis(90, essential = true)
            return if (ms > 0) fadeOut(tween(ms)) else ExitTransition.None
        }
        val dur = motion.durationMillis(SonoraMotionSpecs.MINI_PLAYER_EXIT_MS)
        return if (motion.allowLargeScaleMovement) {
            fadeOut(tween(dur, easing = SonoraMotionSpecs.EmphasizedAccelerate)) +
                slideOutVertically(
                    animationSpec = tween(dur, easing = SonoraMotionSpecs.EmphasizedAccelerate),
                    targetOffsetY = { it / 3 }
                )
        } else {
            fadeOut(tween(dur, easing = SonoraMotionSpecs.EmphasizedAccelerate))
        }
    }

    fun toastEnter(motion: EffectiveMotionState): EnterTransition {
        val dur = motion.durationMillis(SonoraMotionSpecs.SMALL_UI_MS, essential = true)
        if (dur <= 0) return EnterTransition.None
        return if (motion.allowLargeScaleMovement) {
            slideInVertically(tween(dur, easing = SonoraMotionSpecs.EmphasizedDecelerate)) { it / 2 } +
                fadeIn(tween(dur))
        } else {
            fadeIn(tween(dur))
        }
    }

    fun toastExit(motion: EffectiveMotionState): ExitTransition {
        val dur = motion.durationMillis(150, essential = true)
        if (dur <= 0) return ExitTransition.None
        return if (motion.allowLargeScaleMovement) {
            slideOutVertically(tween(dur, easing = SonoraMotionSpecs.EmphasizedAccelerate)) { it / 2 } +
                fadeOut(tween(dur))
        } else {
            fadeOut(tween(dur))
        }
    }

    fun dialogEnter(motion: EffectiveMotionState): EnterTransition {
        val dur = motion.durationMillis(SonoraMotionSpecs.DIALOG_ENTER_MS, essential = true)
        if (dur <= 0) return EnterTransition.None
        return if (motion.allowLargeScaleMovement) {
            fadeIn(tween(dur)) + scaleIn(tween(dur, easing = SonoraMotionSpecs.EmphasizedDecelerate), initialScale = 0.94f)
        } else {
            fadeIn(tween(dur))
        }
    }

    fun dialogExit(motion: EffectiveMotionState): ExitTransition {
        val dur = motion.durationMillis(SonoraMotionSpecs.DIALOG_EXIT_MS, essential = true)
        if (dur <= 0) return ExitTransition.None
        return fadeOut(tween(dur, easing = SonoraMotionSpecs.EmphasizedAccelerate))
    }

    fun playPauseIconTransition(motion: EffectiveMotionState): ContentTransform {
        val dur = motion.durationMillis(SonoraMotionSpecs.BUTTON_FEEDBACK_MS, essential = true)
        if (dur <= 0) return fadeIn(snap()) togetherWith fadeOut(snap())
        return if (motion.allowLargeScaleMovement) {
            (scaleIn(tween(dur), initialScale = 0.78f) + fadeIn(tween(dur))) togetherWith
                (scaleOut(tween(dur), targetScale = 0.78f) + fadeOut(tween(dur)))
        } else {
            fadeIn(tween(dur)) togetherWith fadeOut(tween(dur))
        }
    }

    fun homeModeTransition(toOnline: Boolean, motion: EffectiveMotionState): ContentTransform {
        if (!motion.animationsEnabled) {
            val ms = motion.durationMillis(100, essential = true)
            return if (ms > 0) fadeIn(tween(ms)) togetherWith fadeOut(tween(ms))
            else EnterTransition.None togetherWith ExitTransition.None
        }
        val enterDur = motion.durationMillis(260)
        val exitDur = motion.durationMillis(190)
        if (!motion.allowLargeScaleMovement) {
            return fadeIn(tween(enterDur)) togetherWith fadeOut(tween(exitDur))
        }
        val dir = if (toOnline) 1 else -1
        val slideOffset = if (motion.transitionStyle == ScreenTransitionStyle.FADE) 0.04f else 0.12f
        return (slideInHorizontally(intOffsetSpec(motion, 260)) { w ->
            (w * slideOffset * motion.distanceScale * dir).toInt()
        } + fadeIn(tween(enterDur))) togetherWith
            (slideOutHorizontally(tween(exitDur, easing = SonoraMotionSpecs.EmphasizedAccelerate)) { w ->
                (-w * slideOffset * motion.distanceScale * dir).toInt()
            } + fadeOut(tween(exitDur)))
    }

    fun onboardingStepTransition(forward: Boolean, motion: EffectiveMotionState): ContentTransform {
        if (!motion.animationsEnabled) {
            val ms = motion.durationMillis(110, essential = true)
            return if (ms > 0) fadeIn(tween(ms)) togetherWith fadeOut(tween(ms))
            else EnterTransition.None togetherWith ExitTransition.None
        }
        val enterDur = motion.durationMillis(260)
        val exitDur = motion.durationMillis(190)
        if (!motion.allowLargeScaleMovement) {
            return fadeIn(tween(enterDur)) togetherWith fadeOut(tween(exitDur))
        }
        val dir = if (forward) 1 else -1
        return (slideInHorizontally(tween(enterDur, easing = SonoraMotionSpecs.EmphasizedDecelerate)) { w ->
            (w * 0.22f * motion.distanceScale * dir).toInt()
        } + fadeIn(tween(enterDur))) togetherWith
            (slideOutHorizontally(tween(exitDur, easing = SonoraMotionSpecs.EmphasizedAccelerate)) { w ->
                (-w * 0.16f * motion.distanceScale * dir).toInt()
            } + fadeOut(tween(exitDur)))
    }

    fun trackTransition(direction: Int, motion: EffectiveMotionState): ContentTransform {
        if (!motion.artworkTransitionsEnabled) {
            return EnterTransition.None togetherWith ExitTransition.None
        }
        val enterDur = motion.durationMillis(SonoraMotionSpecs.CARD_CONTENT_MS, essential = true)
        val exitDur = motion.durationMillis(170, essential = true)
        if (enterDur <= 0) return EnterTransition.None togetherWith ExitTransition.None
        if (direction == 0 || !motion.allowLargeScaleMovement) {
            return fadeIn(tween(enterDur)) togetherWith fadeOut(tween(exitDur))
        }
        val sign = if (direction > 0) 1 else -1
        return (slideInHorizontally(tween(enterDur, easing = SonoraMotionSpecs.EmphasizedDecelerate)) { w ->
            (w * 0.18f * motion.distanceScale * sign).toInt()
        } + fadeIn(tween(enterDur)) + scaleIn(tween(enterDur), initialScale = 0.96f)) togetherWith
            (slideOutHorizontally(tween(exitDur, easing = SonoraMotionSpecs.EmphasizedAccelerate)) { w ->
                (-w * 0.14f * motion.distanceScale * sign).toInt()
            } + fadeOut(tween(exitDur)))
    }

    fun metadataTransition(direction: Int, motion: EffectiveMotionState): ContentTransform {
        if (!motion.artworkTransitionsEnabled) {
            return EnterTransition.None togetherWith ExitTransition.None
        }
        val enterDur = motion.durationMillis(200, essential = true)
        val exitDur = motion.durationMillis(140, essential = true)
        if (enterDur <= 0) return EnterTransition.None togetherWith ExitTransition.None
        if (direction == 0 || !motion.allowLargeScaleMovement) {
            return fadeIn(tween(enterDur)) togetherWith fadeOut(tween(exitDur))
        }
        val sign = if (direction > 0) 1 else -1
        return (slideInHorizontally(tween(enterDur, easing = SonoraMotionSpecs.EmphasizedDecelerate)) { w ->
            (w * 0.12f * motion.distanceScale * sign).toInt()
        } + fadeIn(tween(enterDur))) togetherWith
            (slideOutHorizontally(tween(exitDur, easing = SonoraMotionSpecs.EmphasizedAccelerate)) { w ->
                (-w * 0.10f * motion.distanceScale * sign).toInt()
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
        val maxScaleDrop = if (motion.distanceScale > 1.1f) 0.045f else 0.032f
        val scale = 1f - (normalizedDist * maxScaleDrop)
        scaleX = scale
        scaleY = scale
        alpha = 1f - (normalizedDist * 0.10f)
    }
}

/**
 * Non-conflicting horizontal swipe modifier for parent tab/mode containers:
 * - Respects [EffectiveMotionState.swipeNavigationEnabled]
 * - Leaves a 24.dp inset on left/right screen edges so system back gestures remain untouched
 * - Yields automatically to vertical scrolling (requires clear horizontal dominance > 1.35x)
 * - Cancels safely if a second finger touches down (two-finger gestures never trigger 1-finger nav)
 * - Yields automatically to any child LazyRow, HorizontalPager, or Slider consuming horizontal drag
 * - Debounces rapid consecutive triggers to prevent accidental double-navigation
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
        val touchSlop = viewConfiguration.touchSlop
        var lastTriggerUptimeMs = Long.MIN_VALUE

        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = true)
            val width = size.width.toFloat()
            if (down.position.x < edgeExclusionPx || down.position.x > (width - edgeExclusionPx)) {
                return@awaitEachGesture
            }

            var totalDx = 0f
            var totalDy = 0f
            var lockedHorizontal = false
            var cancelled = false
            var lastEventUptimeMs = down.uptimeMillis
            val velocityTracker = VelocityTracker()
            velocityTracker.addPosition(down.uptimeMillis, down.position)

            while (true) {
                val event = awaitPointerEvent()
                val pressedCount = event.changes.count { it.pressed }
                if (pressedCount >= 2) {
                    cancelled = true
                    break
                }
                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                lastEventUptimeMs = change.uptimeMillis
                if (!change.pressed) break
                if (change.isConsumed) {
                    cancelled = true
                    break
                }
                val delta = change.positionChange()
                totalDx += delta.x
                totalDy += delta.y

                if (!lockedHorizontal) {
                    val absX = abs(totalDx)
                    val absY = abs(totalDy)
                    if (absY > touchSlop && absY >= absX) {
                        // Vertical scroll wins immediately
                        cancelled = true
                        break
                    }
                    if (absX > touchSlop && absX > absY * 1.35f) {
                        lockedHorizontal = true
                    }
                }

                if (lockedHorizontal) {
                    change.consume()
                    velocityTracker.addPosition(change.uptimeMillis, change.position)
                }
            }

            if (!cancelled && lockedHorizontal) {
                val velocityX = velocityTracker.calculateVelocity().x
                val triggered = motion.shouldTriggerSwipe(
                    dragPx = totalDx,
                    velocityPxPerSec = velocityX,
                    thresholdPx = thresholdPx,
                    velocityPxThreshold = velocityThresholdPx
                )
                val cooldownElapsed = lastTriggerUptimeMs == Long.MIN_VALUE ||
                    (lastEventUptimeMs - lastTriggerUptimeMs) >= 160L
                if (triggered && cooldownElapsed) {
                    lastTriggerUptimeMs = lastEventUptimeMs
                    val goingLeft = if (abs(totalDx) > thresholdPx * 0.45f) totalDx < 0f else velocityX < 0f
                    if (latestHapticEnabled) {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    }
                    if (goingLeft) latestOnSwipeLeft() else latestOnSwipeRight()
                }
            }
        }
    }
}
