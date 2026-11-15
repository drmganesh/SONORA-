package ui.home.animations

import android.animation.ValueAnimator
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import ui.theme.LocalReduceAnimations

/**
 * Intensity presets for Home Screen album artwork and editorial motion.
 */
enum class HomeAnimationLevel(
    val id: String,
    val displayName: String,
    val subtitle: String,
    val amplitudeScale: Float
) {
    OFF(
        id = "off",
        displayName = "Off",
        subtitle = "Static editorial layout with zero decorative motion",
        amplitudeScale = 0f
    ),
    SUBTLE(
        id = "subtle",
        displayName = "Subtle",
        subtitle = "Whisper-quiet floating and soft crossfades",
        amplitudeScale = 0.55f
    ),
    BALANCED(
        id = "balanced",
        displayName = "Balanced",
        subtitle = "Fluid organic motion tuned for everyday listening",
        amplitudeScale = 1.0f
    ),
    EXPRESSIVE(
        id = "expressive",
        displayName = "Expressive",
        subtitle = "Rich depth parallax, gentle morphing & layered bloom",
        amplitudeScale = 1.45f
    );

    companion object {
        val DEFAULT = BALANCED

        fun fromId(id: String?): HomeAnimationLevel {
            return entries.firstOrNull { it.id.equals(id, ignoreCase = true) } ?: DEFAULT
        }
    }
}

/**
 * Selectable transition & motion styles for Daily Mix cards.
 */
enum class DailyMixAnimationStyle(
    val id: String,
    val displayName: String,
    val subtitle: String
) {
    SLIDE(
        id = "slide",
        displayName = "Slide",
        subtitle = "Smooth horizontal card & artwork slide with spring easing"
    ),
    MIX(
        id = "mix",
        displayName = "Mix",
        subtitle = "Album artwork tiles rearrange smoothly when changing mixes"
    ),
    FADE(
        id = "fade",
        displayName = "Fade",
        subtitle = "Independent opacity crossfade for artwork and typography"
    ),
    PULSE(
        id = "pulse",
        displayName = "Pulse",
        subtitle = "Gentle rhythmic scaling active only while music is playing"
    ),
    GLITCH(
        id = "glitch",
        displayName = "Glitch",
        subtitle = "Brief artistic slice & RGB shift on mix switch, crisp at rest"
    );

    companion object {
        val DEFAULT = SLIDE

        fun fromId(id: String?): DailyMixAnimationStyle {
            return entries.firstOrNull { it.id.equals(id, ignoreCase = true) } ?: DEFAULT
        }
    }
}

data class HomeAnimationSettingsState(
    val animationLevel: HomeAnimationLevel = HomeAnimationLevel.DEFAULT,
    val dailyMixStyle: DailyMixAnimationStyle = DailyMixAnimationStyle.DEFAULT
)

/**
 * Checks whether decorative Home animations should actively tick:
 * - Respects user's HomeAnimationLevel and Reduce Animations toggle
 * - Respects Android system animation scale (API 26+ ValueAnimator + Settings.Global)
 * - Automatically pauses when the Home screen is hidden or the lifecycle is not STARTED
 */
@Composable
fun rememberShouldAnimateHomeMotion(
    level: HomeAnimationLevel,
    screenVisible: Boolean = true,
    ignoreVisibilityForPreview: Boolean = false
): Boolean {
    val reduceAnimations = LocalReduceAnimations.current
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var isLifecycleStarted by remember(lifecycleOwner) {
        mutableStateOf(lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED))
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, _ ->
            isLifecycleStarted = lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val systemAnimatorsEnabled = remember(context, isLifecycleStarted) {
        val animatorFlag = try {
            ValueAnimator.areAnimatorsEnabled()
        } catch (_: Throwable) {
            true
        }
        val scale = try {
            Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
        } catch (_: Throwable) {
            1f
        }
        animatorFlag && scale > 0f
    }

    if (level == HomeAnimationLevel.OFF || reduceAnimations || !systemAnimatorsEnabled) {
        return false
    }
    if (!isLifecycleStarted) return false
    return ignoreVisibilityForPreview || screenVisible
}
