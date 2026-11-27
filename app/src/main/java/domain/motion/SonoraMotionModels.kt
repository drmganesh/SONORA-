package domain.motion

import androidx.compose.runtime.Immutable
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * User-facing motion preference surfaced under Settings -> Appearance -> Motion Effects.
 *
 * - [FULL_MOTION] (Default): Natural, crisp springs, screen transitions, subtle parallax & ambient effects.
 * - [REDUCED_MOTION]: Shortened transitions; disables parallax, decorative loops, and large-scale movement.
 * - [MINIMAL_MOTION]: Retains only essential state feedback (fast fades, progress indicators, touch feedback).
 */
enum class MotionEffectsLevel(
    val label: String,
    val subtitle: String,
    val durationScale: Float,
    val distanceScale: Float
) {
    FULL_MOTION(
        label = "Full Motion",
        subtitle = "Fluid transitions, tactile springs & subtle depth",
        durationScale = 1.0f,
        distanceScale = 1.0f
    ),
    REDUCED_MOTION(
        label = "Reduced Motion",
        subtitle = "Shorter transitions without parallax or large movement",
        durationScale = 0.58f,
        distanceScale = 0.25f
    ),
    MINIMAL_MOTION(
        label = "Minimal Motion",
        subtitle = "Essential state feedback & progress indicators only",
        durationScale = 0.35f,
        distanceScale = 0f
    );

    companion object {
        val DEFAULT = FULL_MOTION

        fun fromName(raw: String?): MotionEffectsLevel =
            entries.firstOrNull { it.name.equals(raw, ignoreCase = true) } ?: DEFAULT
    }
}

/**
 * Global motion intensity controlling timing, spring stiffness, parallax depth, and micro-motion.
 */
enum class MotionIntensity(
    val label: String,
    val description: String,
    val durationMultiplier: Float,
    val distanceScale: Float,
    val springDampingRatio: Float,
    val springStiffness: Float
) {
    OFF(
        label = "Off",
        description = "Instant transitions without decorative motion",
        durationMultiplier = 0f,
        distanceScale = 0f,
        springDampingRatio = 1f,
        springStiffness = 10_000f
    ),
    SUBTLE(
        label = "Subtle",
        description = "Crisp, lightweight motion with minimal travel",
        durationMultiplier = 0.65f,
        distanceScale = 0.55f,
        springDampingRatio = 0.92f,
        springStiffness = 560f
    ),
    BALANCED(
        label = "Balanced",
        description = "Smooth 60fps transitions with refined depth",
        durationMultiplier = 1.0f,
        distanceScale = 1.0f,
        springDampingRatio = 0.84f,
        springStiffness = 420f
    ),
    EXPRESSIVE(
        label = "Expressive",
        description = "Fluid momentum, carousel scale depth & lush springs",
        durationMultiplier = 1.25f,
        distanceScale = 1.25f,
        springDampingRatio = 0.76f,
        springStiffness = 340f
    );

    companion object {
        fun fromName(raw: String?): MotionIntensity =
            entries.firstOrNull { it.name.equals(raw, ignoreCase = true) } ?: BALANCED
    }
}

/**
 * Global screen transition choreography preset.
 */
enum class ScreenTransitionStyle(
    val label: String,
    val description: String
) {
    SLIDE("Slide", "Crisp directional slides for tabs and push screens"),
    FADE("Fade", "Clean editorial crossfades and fade-through transitions"),
    SMOOTH("Smooth", "Balanced directional slide blended with soft fade & scale"),
    DYNAMIC("Dynamic", "Spring-driven depth with subtle scale and lift");

    companion object {
        fun fromName(raw: String?): ScreenTransitionStyle =
            entries.firstOrNull { it.name.equals(raw, ignoreCase = true) } ?: SMOOTH
    }
}

/**
 * Global swipe sensitivity affecting track skip and navigation gestures.
 */
enum class GestureSensitivityLevel(
    val label: String,
    val description: String,
    val playerSensitivity: Float,
    val thresholdDp: Int,
    val velocityThresholdDpPerSec: Float
) {
    LOW(
        label = "Low",
        description = "Deliberate longer swipes prevent accidental skips",
        playerSensitivity = 0.72f,
        thresholdDp = 84,
        velocityThresholdDpPerSec = 520f
    ),
    MEDIUM(
        label = "Medium",
        description = "Balanced responsiveness for everyday one-hand control",
        playerSensitivity = 1.0f,
        thresholdDp = 64,
        velocityThresholdDpPerSec = 360f
    ),
    HIGH(
        label = "High",
        description = "Quick flicks and shorter drags respond immediately",
        playerSensitivity = 1.38f,
        thresholdDp = 44,
        velocityThresholdDpPerSec = 240f
    );

    companion object {
        fun fromName(raw: String?): GestureSensitivityLevel =
            entries.firstOrNull { it.name.equals(raw, ignoreCase = true) } ?: MEDIUM

        fun fromPlayerConfig(sensitivity: Float, thresholdDp: Int): GestureSensitivityLevel = when {
            sensitivity <= 0.85f || thresholdDp >= 78 -> LOW
            sensitivity >= 1.2f || thresholdDp <= 52 -> HIGH
            else -> MEDIUM
        }
    }
}

/**
 * User-configurable motion and gesture preferences across the entire SONORA app.
 */
@Immutable
data class GlobalMotionConfig(
    val motionEffectsLevel: MotionEffectsLevel = MotionEffectsLevel.FULL_MOTION,
    val intensity: MotionIntensity = MotionIntensity.BALANCED,
    val transitionStyle: ScreenTransitionStyle = ScreenTransitionStyle.SMOOTH,
    val gestureSensitivity: GestureSensitivityLevel = GestureSensitivityLevel.MEDIUM,
    val swipeNavigationEnabled: Boolean = true,
    val artworkTransitionsEnabled: Boolean = true,
    val hapticFeedbackEnabled: Boolean = true,
    val parallaxEffectsEnabled: Boolean = true,
    val animatedAlbumColorsEnabled: Boolean = true
) {
    companion object {
        val DEFAULT = GlobalMotionConfig()
    }
}

/**
 * Resolved runtime motion parameters honoring app preferences, Android system
 * ANIMATOR_DURATION_SCALE, Battery Saver, low-RAM devices, and accessibility settings.
 */
@Immutable
data class EffectiveMotionState(
    val config: GlobalMotionConfig,
    val effectiveEffectsLevel: MotionEffectsLevel,
    val systemAnimatorScale: Float,
    val isPowerSaveMode: Boolean,
    val isLowRamDevice: Boolean,
    val isTouchExplorationEnabled: Boolean,
    val animationsEnabled: Boolean,
    val essentialFeedbackEnabled: Boolean,
    val decorativeMotionEnabled: Boolean,
    val ambientEffectsEnabled: Boolean,
    val allowLargeScaleMovement: Boolean,
    val durationScale: Float,
    val distanceScale: Float,
    val transitionStyle: ScreenTransitionStyle,
    val swipeNavigationEnabled: Boolean,
    val artworkTransitionsEnabled: Boolean,
    val hapticEnabled: Boolean,
    val parallaxAndScaleEnabled: Boolean,
    val animatedAlbumColorsEnabled: Boolean,
    val swipeThresholdDp: Int,
    val velocityThresholdDpPerSec: Float
) {
    /**
     * Resolves a duration in milliseconds honoring the active motion level & system animator scale.
     * For [essential] feedback (button press, toggle, progress), retains crisp non-zero feedback
     * in Minimal Motion unless the Android OS has disabled animators globally (scale == 0).
     */
    fun durationMillis(baseMs: Int, essential: Boolean = false): Int {
        if (baseMs <= 0 || systemAnimatorScale <= 0.01f) return 0
        if (!animationsEnabled && !essential) return 0
        if (effectiveEffectsLevel == MotionEffectsLevel.MINIMAL_MOTION) {
            return if (essential) {
                (baseMs * 0.45f * systemAnimatorScale).roundToInt().coerceIn(70, 140)
            } else {
                0
            }
        }
        return (baseMs * durationScale).roundToInt().coerceIn(75, 420)
    }

    fun shouldTriggerSwipe(dragPx: Float, velocityPxPerSec: Float, thresholdPx: Float, velocityPxThreshold: Float): Boolean {
        return abs(dragPx) >= thresholdPx || abs(velocityPxPerSec) >= velocityPxThreshold
    }
}

fun resolveEffectiveMotionState(
    config: GlobalMotionConfig,
    systemAnimatorScale: Float = 1f,
    isPowerSaveMode: Boolean = false,
    isLowRamDevice: Boolean = false,
    isTouchExplorationEnabled: Boolean = false
): EffectiveMotionState {
    val sanitizedSystemScale = systemAnimatorScale.takeIf { it.isFinite() }?.coerceAtLeast(0f) ?: 1f
    val systemAnimatorsOff = sanitizedSystemScale <= 0.01f

    // Downgrade effective level automatically under system Reduce Motion, Battery Saver, Low-RAM, or Intensity.OFF
    val effectiveLevel = when {
        systemAnimatorsOff || config.intensity == MotionIntensity.OFF -> MotionEffectsLevel.MINIMAL_MOTION
        config.motionEffectsLevel == MotionEffectsLevel.MINIMAL_MOTION -> MotionEffectsLevel.MINIMAL_MOTION
        config.motionEffectsLevel == MotionEffectsLevel.REDUCED_MOTION -> MotionEffectsLevel.REDUCED_MOTION
        isPowerSaveMode || isLowRamDevice || isTouchExplorationEnabled -> MotionEffectsLevel.REDUCED_MOTION
        else -> MotionEffectsLevel.FULL_MOTION
    }

    val animationsEnabled = !systemAnimatorsOff &&
        config.intensity != MotionIntensity.OFF &&
        effectiveLevel != MotionEffectsLevel.MINIMAL_MOTION

    val essentialFeedbackEnabled = !systemAnimatorsOff

    val allowLargeScaleMovement = animationsEnabled &&
        effectiveLevel == MotionEffectsLevel.FULL_MOTION &&
        !isPowerSaveMode

    val decorativeMotionEnabled = allowLargeScaleMovement && !isLowRamDevice

    val ambientEffectsEnabled = !systemAnimatorsOff &&
        effectiveLevel != MotionEffectsLevel.MINIMAL_MOTION &&
        config.intensity != MotionIntensity.OFF

    val rawDurationScale = config.intensity.durationMultiplier *
        effectiveLevel.durationScale *
        sanitizedSystemScale

    val combinedDurationScale = if (!animationsEnabled) {
        0f
    } else {
        rawDurationScale.coerceIn(0.35f, 1.25f)
    }

    val effectiveDistanceScale = when {
        !animationsEnabled -> 0f
        !allowLargeScaleMovement -> (config.intensity.distanceScale * effectiveLevel.distanceScale).coerceIn(0f, 0.25f)
        else -> (config.intensity.distanceScale * effectiveLevel.distanceScale).coerceIn(0f, 1.25f)
    }

    val effectiveStyle = if (!allowLargeScaleMovement && animationsEnabled) {
        ScreenTransitionStyle.FADE
    } else {
        config.transitionStyle
    }

    return EffectiveMotionState(
        config = config,
        effectiveEffectsLevel = effectiveLevel,
        systemAnimatorScale = sanitizedSystemScale,
        isPowerSaveMode = isPowerSaveMode,
        isLowRamDevice = isLowRamDevice,
        isTouchExplorationEnabled = isTouchExplorationEnabled,
        animationsEnabled = animationsEnabled,
        essentialFeedbackEnabled = essentialFeedbackEnabled,
        decorativeMotionEnabled = decorativeMotionEnabled,
        ambientEffectsEnabled = ambientEffectsEnabled,
        allowLargeScaleMovement = allowLargeScaleMovement,
        durationScale = combinedDurationScale,
        distanceScale = effectiveDistanceScale,
        transitionStyle = effectiveStyle,
        swipeNavigationEnabled = config.swipeNavigationEnabled && !isTouchExplorationEnabled,
        artworkTransitionsEnabled = (animationsEnabled || essentialFeedbackEnabled) && config.artworkTransitionsEnabled,
        hapticEnabled = config.hapticFeedbackEnabled,
        parallaxAndScaleEnabled = allowLargeScaleMovement &&
            !isLowRamDevice &&
            config.parallaxEffectsEnabled,
        animatedAlbumColorsEnabled = animationsEnabled && config.animatedAlbumColorsEnabled && !isPowerSaveMode,
        swipeThresholdDp = config.gestureSensitivity.thresholdDp,
        velocityThresholdDpPerSec = config.gestureSensitivity.velocityThresholdDpPerSec
    )
}
