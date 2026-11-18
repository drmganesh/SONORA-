package domain.motion

import androidx.compose.runtime.Immutable
import kotlin.math.abs
import kotlin.math.roundToInt

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
 * Global swipe sensitivity affecting track skip, mini-player, and navigation gestures.
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
 * Resolved runtime motion parameters honoring both app preferences and Android system
 * ANIMATOR_DURATION_SCALE / Reduce Motion accessibility settings.
 */
@Immutable
data class EffectiveMotionState(
    val config: GlobalMotionConfig,
    val systemAnimatorScale: Float,
    val animationsEnabled: Boolean,
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
    fun durationMillis(baseMs: Int): Int {
        if (!animationsEnabled || baseMs <= 0) return 0
        return (baseMs * durationScale).roundToInt().coerceIn(75, 950)
    }

    fun shouldTriggerSwipe(dragPx: Float, velocityPxPerSec: Float, thresholdPx: Float, velocityPxThreshold: Float): Boolean {
        return abs(dragPx) >= thresholdPx || abs(velocityPxPerSec) >= velocityPxThreshold
    }
}

fun resolveEffectiveMotionState(
    config: GlobalMotionConfig,
    systemAnimatorScale: Float = 1f
): EffectiveMotionState {
    val sanitizedSystemScale = systemAnimatorScale.takeIf { it.isFinite() }?.coerceAtLeast(0f) ?: 1f
    val animationsEnabled = config.intensity != MotionIntensity.OFF && sanitizedSystemScale > 0.01f
    val combinedDurationScale = if (!animationsEnabled) {
        0f
    } else {
        (config.intensity.durationMultiplier * sanitizedSystemScale).coerceIn(0.35f, 1.5f)
    }
    return EffectiveMotionState(
        config = config,
        systemAnimatorScale = sanitizedSystemScale,
        animationsEnabled = animationsEnabled,
        durationScale = combinedDurationScale,
        distanceScale = if (animationsEnabled) config.intensity.distanceScale else 0f,
        transitionStyle = config.transitionStyle,
        swipeNavigationEnabled = config.swipeNavigationEnabled,
        artworkTransitionsEnabled = animationsEnabled && config.artworkTransitionsEnabled,
        hapticEnabled = config.hapticFeedbackEnabled,
        parallaxAndScaleEnabled = animationsEnabled &&
            config.parallaxEffectsEnabled &&
            config.intensity != MotionIntensity.OFF,
        animatedAlbumColorsEnabled = animationsEnabled && config.animatedAlbumColorsEnabled,
        swipeThresholdDp = config.gestureSensitivity.thresholdDp,
        velocityThresholdDpPerSec = config.gestureSensitivity.velocityThresholdDpPerSec
    )
}
