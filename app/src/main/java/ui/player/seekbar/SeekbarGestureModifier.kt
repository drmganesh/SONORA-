package ui.player.seekbar

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import kotlin.math.abs

/**
 * Shared seek capability derived from Media3 ExoPlayer / SonoraAudioEngine.
 * Provided via [LocalPlaybackSeekCapability] at the PlayerScreen level so every
 * Now Playing style automatically reflects real live-stream and non-seekable states.
 */
@Immutable
data class PlaybackSeekCapability(
    val isSeekable: Boolean = true,
    val isLiveStream: Boolean = false
)

val LocalPlaybackSeekCapability = compositionLocalOf { PlaybackSeekCapability() }

/**
 * Unified, conflict-free horizontal scrub-and-tap gesture modifier for all SONORA music seekbars.
 *
 * Guarantees:
 * - Supports both dragging forward/backward and direct tap-to-seek.
 * - Updates preview progress smoothly on every pointer move while dragging.
 * - Commits [onScrubEnd] EXACTLY ONCE when the pointer is released (no ExoPlayer seek spam).
 * - Tracks the latest dragged fraction locally inside the gesture coroutine (never stale).
 * - Consumes horizontal drag events so parent swipe-to-skip/dismiss handlers never conflict.
 * - Exposes TalkBack accessibility [ProgressBarRangeInfo] and [setProgress] actions.
 */
fun Modifier.scrubSeekGestures(
    enabled: Boolean,
    progress: Float,
    onScrubStart: (Float) -> Unit,
    onScrubUpdate: (Float) -> Unit,
    onScrubEnd: (Float) -> Unit,
    onScrubCancel: () -> Unit = {}
): Modifier = composed {
    val latestEnabled by rememberUpdatedState(enabled)
    val latestOnStart by rememberUpdatedState(onScrubStart)
    val latestOnUpdate by rememberUpdatedState(onScrubUpdate)
    val latestOnEnd by rememberUpdatedState(onScrubEnd)
    val latestOnCancel by rememberUpdatedState(onScrubCancel)

    this
        .semantics {
            progressBarRangeInfo = ProgressBarRangeInfo(
                current = progress.coerceIn(0f, 1f),
                range = 0f..1f
            )
            if (enabled) {
                setProgress { targetValue ->
                    val clamped = targetValue.coerceIn(0f, 1f)
                    latestOnUpdate(clamped)
                    latestOnEnd(clamped)
                    true
                }
            }
        }
        .pointerInput(enabled) {
            if (!enabled) return@pointerInput
            val slop = viewConfiguration.touchSlop
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                if (!latestEnabled || size.width <= 0) return@awaitEachGesture

                val pointerId = down.id
                val startX = down.position.x
                val startY = down.position.y
                var latestFraction = (startX / size.width.toFloat()).coerceIn(0f, 1f)
                var hasLockedHorizontal = false
                var hasAbortedVertical = false

                // Immediately notify preview start and claim touch so parent horizontal swipe stays idle
                down.consume()
                latestOnStart(latestFraction)

                while (true) {
                    val event = awaitPointerEvent()
                    val change = event.changes.firstOrNull { it.id == pointerId } ?: break

                    if (!change.pressed) {
                        change.consume()
                        if (!hasAbortedVertical) {
                            latestOnEnd(latestFraction)
                        } else {
                            latestOnCancel()
                        }
                        break
                    }

                    val dx = change.position.x - startX
                    val dy = change.position.y - startY

                    if (!hasLockedHorizontal && (abs(dx) > slop * 0.5f || abs(dy) > slop * 1.6f)) {
                        if (abs(dy) > abs(dx) * 1.8f && abs(dy) > slop * 1.6f) {
                            // Clear vertical gesture — abort scrub cleanly without seeking
                            hasAbortedVertical = true
                            latestOnCancel()
                            break
                        } else {
                            hasLockedHorizontal = true
                        }
                    }

                    val delta = change.positionChange()
                    if (abs(delta.x) > 0f || hasLockedHorizontal) {
                        change.consume()
                        latestFraction = (change.position.x / size.width.toFloat()).coerceIn(0f, 1f)
                        latestOnUpdate(latestFraction)
                    }
                }
            }
        }
}
