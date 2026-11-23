package domain.player

import com.example.model.MiniPlayerStyle
import kotlin.math.abs
import kotlin.math.hypot

/**
 * Unified single visual presentation state for the SONORA music mini player across ALL
 * [MiniPlayerStyle] designs (all 20 styles share this exact state model).
 *
 * Strictly decoupled from media playback state:
 * - Transitioning to [CIRCULAR_MINI_PLAYER] or [DISMISSED] NEVER pauses audio,
 *   clears the queue, or stops [com.example.service.SonoraMusicService].
 * - Entering Online Video mode temporarily suppresses display via [MusicMiniPlayerVisibilityPolicy]
 *   without overwriting or resetting this persisted visual state.
 */
enum class MiniPlayerDisplayState {
    EXPANDED_MINI_PLAYER,
    CIRCULAR_MINI_PLAYER,
    DISMISSED;

    companion object {
        fun fromStored(value: String?): MiniPlayerDisplayState {
            if (value.isNullOrBlank()) return EXPANDED_MINI_PLAYER
            return entries.firstOrNull { it.name.equals(value.trim(), ignoreCase = true) }
                ?: EXPANDED_MINI_PLAYER
        }
    }
}

/**
 * Classified gesture outcome for the expanded music mini player (applies uniformly to all 20 styles).
 */
enum class ExpandedMiniPlayerGestureOutcome {
    NONE,
    SWIPE_NEXT,
    SWIPE_PREVIOUS,
    SWIPE_UP_EXPAND,
    SWIPE_DOWN_MINIMIZE
}

/**
 * Classified gesture outcome for the floating circular mini player (Stage 2).
 */
enum class CircularMiniPlayerGestureOutcome {
    NONE,
    REPOSITION_DRAG,
    SWIPE_DOWN_DISMISS
}

/**
 * Axis lock constants shared by universal mini-player gesture detectors.
 */
object MiniPlayerGestureAxis {
    const val UNDECIDED = 0
    const val HORIZONTAL = 1
    const val VERTICAL = 2
}

/**
 * Shared, style-independent gesture controller & two-stage state machine for ALL SONORA
 * music mini-player designs ([MiniPlayerStyle.entries]).
 *
 * Two-stage downward swipe flow:
 * ```
 * EXPANDED_MINI_PLAYER  --swipe down-->  CIRCULAR_MINI_PLAYER  --swipe down-->  DISMISSED
 * ```
 * Restoration flow:
 * - Tap circular floating player -> [MiniPlayerDisplayState.EXPANDED_MINI_PLAYER]
 * - Tap persistent playback entry -> [MiniPlayerDisplayState.EXPANDED_MINI_PLAYER]
 * - Start a new playback session -> [MiniPlayerDisplayState.EXPANDED_MINI_PLAYER]
 */
object MiniPlayerGestureController {

    /**
     * Advances the two-stage downward swipe state machine.
     * Identical across every [MiniPlayerStyle].
     */
    fun onSwipeDown(
        currentState: MiniPlayerDisplayState,
        circularMiniPlayerEnabled: Boolean,
        swipeDownDismissEnabled: Boolean = true
    ): MiniPlayerDisplayState {
        return when (currentState) {
            MiniPlayerDisplayState.EXPANDED_MINI_PLAYER -> when {
                circularMiniPlayerEnabled -> MiniPlayerDisplayState.CIRCULAR_MINI_PLAYER
                swipeDownDismissEnabled -> MiniPlayerDisplayState.DISMISSED
                else -> MiniPlayerDisplayState.EXPANDED_MINI_PLAYER
            }
            MiniPlayerDisplayState.CIRCULAR_MINI_PLAYER -> {
                if (swipeDownDismissEnabled) MiniPlayerDisplayState.DISMISSED
                else MiniPlayerDisplayState.CIRCULAR_MINI_PLAYER
            }
            MiniPlayerDisplayState.DISMISSED -> MiniPlayerDisplayState.DISMISSED
        }
    }

    /**
     * Tapping the floating circular player expands back to the normal mini player.
     */
    fun onTapCircularPlayer(currentState: MiniPlayerDisplayState): MiniPlayerDisplayState {
        return if (currentState == MiniPlayerDisplayState.CIRCULAR_MINI_PLAYER) {
            MiniPlayerDisplayState.EXPANDED_MINI_PLAYER
        } else {
            currentState
        }
    }

    /**
     * Explicit restoration from dismissed or circular state via persistent header playback entry
     * or opening Now Playing.
     */
    fun onRestoreMiniPlayer(): MiniPlayerDisplayState = MiniPlayerDisplayState.EXPANDED_MINI_PLAYER

    /**
     * Starting a new playback session restores the expanded mini player cleanly.
     */
    fun onNewPlaybackSessionStarted(): MiniPlayerDisplayState = MiniPlayerDisplayState.EXPANDED_MINI_PLAYER

    /**
     * If the user disables the Circular Mini Player setting while collapsed to a circle,
     * expand back to the normal mini player without disturbing an intentionally dismissed state.
     */
    fun onCircularModeSettingChanged(
        currentState: MiniPlayerDisplayState,
        circularEnabled: Boolean
    ): MiniPlayerDisplayState {
        if (!circularEnabled && currentState == MiniPlayerDisplayState.CIRCULAR_MINI_PLAYER) {
            return MiniPlayerDisplayState.EXPANDED_MINI_PLAYER
        }
        return currentState
    }

    /**
     * Locks horizontal vs. vertical gesture axis once movement exceeds [touchSlopPx].
     */
    fun lockGestureAxis(
        accumX: Float,
        accumY: Float,
        touchSlopPx: Float
    ): Int {
        val absX = abs(accumX)
        val absY = abs(accumY)
        if (absX <= touchSlopPx && absY <= touchSlopPx) return MiniPlayerGestureAxis.UNDECIDED
        return if (absX >= absY) MiniPlayerGestureAxis.HORIZONTAL else MiniPlayerGestureAxis.VERTICAL
    }

    /**
     * Classifies a completed pointer gesture on the expanded mini player.
     * Style-agnostic: every [MiniPlayerStyle] uses this exact logic.
     */
    fun classifyExpandedGesture(
        lockedAxis: Int,
        dragX: Float,
        dragY: Float,
        velocityX: Float,
        velocityY: Float,
        swipeThresholdPx: Float,
        velocityThresholdPx: Float,
        verticalExpandPx: Float,
        verticalDismissPx: Float,
        touchSlopPx: Float,
        horizontalGesturesEnabled: Boolean,
        swipeDownEnabled: Boolean,
        style: MiniPlayerStyle = MiniPlayerStyle.DEFAULT
    ): ExpandedMiniPlayerGestureOutcome {
        // Explicit style reference guarantees uniform handling across every MiniPlayerStyle entry.
        @Suppress("UNUSED_VARIABLE")
        val handledStyle = style

        return when (lockedAxis) {
            MiniPlayerGestureAxis.HORIZONTAL -> {
                if (!horizontalGesturesEnabled) return ExpandedMiniPlayerGestureOutcome.NONE
                val triggeredByDistance = abs(dragX) >= swipeThresholdPx
                val triggeredByVelocity = abs(velocityX) >= velocityThresholdPx && abs(dragX) >= touchSlopPx * 1.4f
                if (!triggeredByDistance && !triggeredByVelocity) {
                    ExpandedMiniPlayerGestureOutcome.NONE
                } else {
                    val isLeftSwipe = if (abs(dragX) > swipeThresholdPx * 0.45f) dragX < 0f else velocityX < 0f
                    if (isLeftSwipe) {
                        ExpandedMiniPlayerGestureOutcome.SWIPE_NEXT
                    } else {
                        ExpandedMiniPlayerGestureOutcome.SWIPE_PREVIOUS
                    }
                }
            }

            MiniPlayerGestureAxis.VERTICAL -> {
                val upwardTriggered = horizontalGesturesEnabled &&
                    dragY < -touchSlopPx &&
                    (dragY <= -verticalExpandPx || velocityY <= -(velocityThresholdPx * 0.72f))

                val downwardTriggered = swipeDownEnabled &&
                    dragY > touchSlopPx &&
                    (
                        (dragY >= verticalDismissPx && velocityY >= -(velocityThresholdPx * 0.25f)) ||
                            (velocityY >= velocityThresholdPx * 0.72f && dragY >= touchSlopPx * 1.4f)
                        )

                when {
                    upwardTriggered -> ExpandedMiniPlayerGestureOutcome.SWIPE_UP_EXPAND
                    downwardTriggered -> ExpandedMiniPlayerGestureOutcome.SWIPE_DOWN_MINIMIZE
                    else -> ExpandedMiniPlayerGestureOutcome.NONE
                }
            }

            else -> ExpandedMiniPlayerGestureOutcome.NONE
        }
    }

    /**
     * Classifies a completed gesture on the floating circular mini player (Stage 2).
     *
     * Distinguishes three distinct user intents reliably:
     * 1. Ordinary tap / tiny jitter (< [touchSlopPx]) -> [CircularMiniPlayerGestureOutcome.NONE]
     * 2. Intentional downward swipe or flick -> [CircularMiniPlayerGestureOutcome.SWIPE_DOWN_DISMISS]
     * 3. Free repositioning drag (horizontal, upward, or diagonal positioning) -> [CircularMiniPlayerGestureOutcome.REPOSITION_DRAG]
     */
    fun classifyCircularGesture(
        dragX: Float,
        dragY: Float,
        velocityX: Float,
        velocityY: Float,
        dismissDistancePx: Float,
        dismissVelocityPx: Float,
        touchSlopPx: Float,
        swipeDownDismissEnabled: Boolean = true
    ): CircularMiniPlayerGestureOutcome {
        val totalDistance = hypot(dragX, dragY)
        if (totalDistance < touchSlopPx) {
            return CircularMiniPlayerGestureOutcome.NONE
        }

        if (swipeDownDismissEnabled && dragY > 0f) {
            val predominantlyDownward = abs(dragX) <= dragY * 0.72f
            val notMovingBackUpward = velocityY >= -(dismissVelocityPx * 0.18f)
            val downwardFlick = predominantlyDownward &&
                velocityY >= dismissVelocityPx &&
                dragY >= (touchSlopPx * 2.2f).coerceAtLeast(dismissDistancePx * 0.35f) &&
                abs(velocityY) >= abs(velocityX) * 1.15f
            val deliberateDownwardSwipe = predominantlyDownward &&
                notMovingBackUpward &&
                dragY >= dismissDistancePx

            if (downwardFlick || deliberateDownwardSwipe) {
                return CircularMiniPlayerGestureOutcome.SWIPE_DOWN_DISMISS
            }
        }

        return CircularMiniPlayerGestureOutcome.REPOSITION_DRAG
    }

    /**
     * Normalized 0f..1f downward drag progress for smooth shrink/fade feedback on Stage 1.
     */
    fun expandedDownwardProgress(dragY: Float, verticalDismissPx: Float): Float {
        if (dragY <= 0f || verticalDismissPx <= 0f) return 0f
        return (dragY / (verticalDismissPx * 1.45f)).coerceIn(0f, 1f)
    }

    /**
     * Normalized 0f..1f downward dismiss preview progress on Stage 2 (circular player).
     * Returns 0f whenever the user is dragging horizontally/upward to reposition so free dragging
     * stays crisp at full opacity.
     */
    fun circularDismissPreviewProgress(
        dragX: Float,
        dragY: Float,
        dismissDistancePx: Float,
        touchSlopPx: Float
    ): Float {
        if (dragY <= touchSlopPx * 1.2f || dismissDistancePx <= 0f) return 0f
        if (abs(dragX) > dragY * 0.72f) return 0f
        val effective = (dragY - touchSlopPx * 1.2f).coerceAtLeast(0f)
        return (effective / dismissDistancePx).coerceIn(0f, 1f)
    }
}
