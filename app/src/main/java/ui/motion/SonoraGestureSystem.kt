package ui.motion

import kotlin.math.abs
import kotlin.math.atan

/**
 * Shared axis-lock and geometry math for SONORA's advanced gesture system:
 * - Mini-player horizontal track skipping, swipe-up expansion, and two-stage swipe-down collapse/dismiss
 * - In-app floating video PiP two-finger pinch-to-resize and one-finger drag with safe-area clamping
 * - Queue screen swipe-down return to full music player
 *
 * Pure functions keep gesture thresholds, clamping, and conflict rules deterministic and testable
 * across screen sizes, orientations, and Android API levels (API 26+).
 */
enum class GestureAxisLock {
    NONE,
    HORIZONTAL,
    VERTICAL
}

enum class MiniPlayerGestureAction {
    NONE,
    SKIP_NEXT,
    SKIP_PREVIOUS,
    EXPAND_FULL_PLAYER,
    COLLAPSE_OR_DISMISS
}

object MiniPlayerGestureMath {

    /**
     * Resolves directional lock once drag distance exceeds [touchSlop].
     * Uses directional dominance ratios so vertical scrolling never triggers horizontal track skips
     * and horizontal skips never trigger vertical expand/dismiss.
     */
    fun resolveAxisLock(
        totalDx: Float,
        totalDy: Float,
        touchSlop: Float
    ): GestureAxisLock {
        val absX = abs(totalDx)
        val absY = abs(totalDy)
        val slop = touchSlop.coerceAtLeast(1f)
        if (absX <= slop && absY <= slop) return GestureAxisLock.NONE
        if (absX > slop && absX >= absY * 1.25f) return GestureAxisLock.HORIZONTAL
        if (absY > slop && absY >= absX * 1.15f) return GestureAxisLock.VERTICAL
        return GestureAxisLock.NONE
    }

    /**
     * Smooth rubber-band damping for live GPU translation feedback during drag.
     */
    fun dampedOffset(rawPx: Float, maxTravelPx: Float): Float {
        if (maxTravelPx <= 0f) return 0f
        val normalized = rawPx / maxTravelPx
        return (maxTravelPx * (2f / Math.PI.toFloat()) * atan(normalized.toDouble()).toFloat())
    }

    /**
     * Evaluates whether a completed drag should commit a mini-player gesture action
     * or cancel and spring back to rest.
     */
    fun resolveDecision(
        axisLock: GestureAxisLock,
        totalDx: Float,
        totalDy: Float,
        velocityX: Float,
        velocityY: Float,
        horizontalThresholdPx: Float,
        verticalUpThresholdPx: Float,
        verticalDownThresholdPx: Float,
        velocityThresholdPx: Float,
        allowExpandUp: Boolean = true,
        allowCollapseOrDismissDown: Boolean = true
    ): MiniPlayerGestureAction {
        return when (axisLock) {
            GestureAxisLock.NONE -> MiniPlayerGestureAction.NONE
            GestureAxisLock.HORIZONTAL -> {
                val absDx = abs(totalDx)
                val absVx = abs(velocityX)
                val distancePassed = absDx >= horizontalThresholdPx
                val velocityPassed = absVx >= velocityThresholdPx &&
                    absDx >= horizontalThresholdPx * 0.35f &&
                    (totalDx * velocityX >= 0f)
                if (!distancePassed && !velocityPassed) {
                    MiniPlayerGestureAction.NONE
                } else {
                    val isSwipeLeft = if (absDx >= horizontalThresholdPx * 0.45f) {
                        totalDx < 0f
                    } else {
                        velocityX < 0f
                    }
                    if (isSwipeLeft) {
                        MiniPlayerGestureAction.SKIP_NEXT
                    } else {
                        MiniPlayerGestureAction.SKIP_PREVIOUS
                    }
                }
            }
            GestureAxisLock.VERTICAL -> {
                if (totalDy < 0f && allowExpandUp) {
                    val distancePassed = -totalDy >= verticalUpThresholdPx
                    val velocityPassed = -velocityY >= velocityThresholdPx &&
                        -totalDy >= verticalUpThresholdPx * 0.35f
                    if (distancePassed || velocityPassed) {
                        MiniPlayerGestureAction.EXPAND_FULL_PLAYER
                    } else {
                        MiniPlayerGestureAction.NONE
                    }
                } else if (totalDy > 0f && allowCollapseOrDismissDown) {
                    val distancePassed = totalDy >= verticalDownThresholdPx
                    val velocityPassed = velocityY >= velocityThresholdPx &&
                        totalDy >= verticalDownThresholdPx * 0.35f
                    if (distancePassed || velocityPassed) {
                        MiniPlayerGestureAction.COLLAPSE_OR_DISMISS
                    } else {
                        MiniPlayerGestureAction.NONE
                    }
                } else {
                    MiniPlayerGestureAction.NONE
                }
            }
        }
    }
}

/**
 * Immutable layout rectangle in pixels for the in-app floating video PiP surface.
 */
data class FloatingPipRectPx(
    val x: Float,
    val y: Float,
    val width: Float,
    val height: Float,
    val widthFraction: Float
)

/**
 * Pure adaptive geometry solver for SONORA's in-app floating video PiP:
 * - Adaptive width limits: 20% minimum, 40% default, 85% maximum of available viewport width
 * - Preserves exact video aspect ratio (16:9, 9:16, 4:3, 1:1, rotated) without distortion
 * - Clamps position inside safe screen bounds after dragging, pinching, or orientation changes
 */
object FloatingPipLayoutMath {
    const val MIN_WIDTH_FRACTION = 0.20f
    const val DEFAULT_WIDTH_FRACTION = 0.40f
    const val MAX_WIDTH_FRACTION = 0.85f
    const val MAX_HEIGHT_FRACTION = 0.78f

    data class PipSizePx(
        val widthPx: Float,
        val heightPx: Float,
        val effectiveWidthFraction: Float,
        val minWidthFraction: Float,
        val maxWidthFraction: Float
    )

    fun resolveSizePx(
        viewportWidthPx: Float,
        viewportHeightPx: Float,
        edgeMarginPx: Float,
        topMarginPx: Float,
        bottomMarginPx: Float,
        videoAspectRatio: Float,
        requestedWidthFraction: Float
    ): PipSizePx {
        val safeViewportW = viewportWidthPx.coerceAtLeast(1f)
        val safeViewportH = viewportHeightPx.coerceAtLeast(1f)
        val ratio = videoAspectRatio.coerceIn(0.25f, 4.0f)

        val safeInnerW = (safeViewportW - edgeMarginPx * 2f).coerceAtLeast(1f)
        val safeInnerH = (safeViewportH - topMarginPx - bottomMarginPx).coerceAtLeast(1f)

        val maxHeightAllowed = (safeViewportH * MAX_HEIGHT_FRACTION).coerceAtMost(safeInnerH).coerceAtLeast(1f)
        val maxWidthForHeight = (maxHeightAllowed * ratio).coerceAtMost(safeInnerW)
        val maxWidthByScreen = (safeViewportW * MAX_WIDTH_FRACTION).coerceAtMost(safeInnerW)
        val effectiveMaxW = minOf(maxWidthByScreen, maxWidthForHeight).coerceAtLeast(1f)

        val rawMinW = (safeViewportW * MIN_WIDTH_FRACTION).coerceAtMost(safeInnerW)
        val effectiveMinW = rawMinW.coerceAtMost(effectiveMaxW).coerceAtLeast(1f)

        val clampedFraction = requestedWidthFraction.coerceIn(MIN_WIDTH_FRACTION, MAX_WIDTH_FRACTION)
        val desiredW = (safeViewportW * clampedFraction).coerceIn(effectiveMinW, effectiveMaxW)
        val finalH = (desiredW / ratio).coerceAtMost(safeInnerH).coerceAtLeast(1f)
        val finalW = (finalH * ratio).coerceAtMost(safeInnerW).coerceAtLeast(1f)

        return PipSizePx(
            widthPx = finalW,
            heightPx = finalH,
            effectiveWidthFraction = (finalW / safeViewportW).coerceIn(MIN_WIDTH_FRACTION, MAX_WIDTH_FRACTION),
            minWidthFraction = (effectiveMinW / safeViewportW).coerceIn(0.05f, MAX_WIDTH_FRACTION),
            maxWidthFraction = (effectiveMaxW / safeViewportW).coerceIn(MIN_WIDTH_FRACTION, MAX_WIDTH_FRACTION)
        )
    }

    fun clampOffsetPx(
        x: Float,
        y: Float,
        widthPx: Float,
        heightPx: Float,
        viewportWidthPx: Float,
        viewportHeightPx: Float,
        edgeMarginPx: Float,
        topMarginPx: Float,
        bottomMarginPx: Float
    ): Pair<Float, Float> {
        val minX = edgeMarginPx
        val maxX = (viewportWidthPx - edgeMarginPx - widthPx).coerceAtLeast(minX)
        val minY = topMarginPx
        val maxY = (viewportHeightPx - bottomMarginPx - heightPx).coerceAtLeast(minY)
        return x.coerceIn(minX, maxX) to y.coerceIn(minY, maxY)
    }

    fun fromNormalizedPosition(
        normalizedX: Float,
        normalizedY: Float,
        widthPx: Float,
        heightPx: Float,
        viewportWidthPx: Float,
        viewportHeightPx: Float,
        edgeMarginPx: Float,
        topMarginPx: Float,
        bottomMarginPx: Float
    ): Pair<Float, Float> {
        val minX = edgeMarginPx
        val maxX = (viewportWidthPx - edgeMarginPx - widthPx).coerceAtLeast(minX)
        val minY = topMarginPx
        val maxY = (viewportHeightPx - bottomMarginPx - heightPx).coerceAtLeast(minY)
        val x = minX + (maxX - minX) * normalizedX.coerceIn(0f, 1f)
        val y = minY + (maxY - minY) * normalizedY.coerceIn(0f, 1f)
        return x to y
    }

    fun toNormalizedPosition(
        x: Float,
        y: Float,
        widthPx: Float,
        heightPx: Float,
        viewportWidthPx: Float,
        viewportHeightPx: Float,
        edgeMarginPx: Float,
        topMarginPx: Float,
        bottomMarginPx: Float
    ): Pair<Float, Float> {
        val minX = edgeMarginPx
        val maxX = (viewportWidthPx - edgeMarginPx - widthPx).coerceAtLeast(minX)
        val minY = topMarginPx
        val maxY = (viewportHeightPx - bottomMarginPx - heightPx).coerceAtLeast(minY)
        val spanX = maxX - minX
        val spanY = maxY - minY
        val normX = if (spanX > 0.5f) ((x - minX) / spanX).coerceIn(0f, 1f) else 1f
        val normY = if (spanY > 0.5f) ((y - minY) / spanY).coerceIn(0f, 1f) else 1f
        return normX to normY
    }

    /**
     * Applies a two-finger pinch zoom factor while keeping the floating PiP anchored smoothly
     * around its center and immediately clamping within safe screen boundaries.
     */
    fun applyPinchZoom(
        currentRect: FloatingPipRectPx,
        zoomFactor: Float,
        panDx: Float,
        panDy: Float,
        viewportWidthPx: Float,
        viewportHeightPx: Float,
        edgeMarginPx: Float,
        topMarginPx: Float,
        bottomMarginPx: Float,
        videoAspectRatio: Float
    ): FloatingPipRectPx {
        val safeZoom = if (zoomFactor.isFinite() && zoomFactor > 0f) zoomFactor else 1f
        val targetFraction = (currentRect.widthFraction * safeZoom)
            .coerceIn(MIN_WIDTH_FRACTION, MAX_WIDTH_FRACTION)

        val newSize = resolveSizePx(
            viewportWidthPx = viewportWidthPx,
            viewportHeightPx = viewportHeightPx,
            edgeMarginPx = edgeMarginPx,
            topMarginPx = topMarginPx,
            bottomMarginPx = bottomMarginPx,
            videoAspectRatio = videoAspectRatio,
            requestedWidthFraction = targetFraction
        )

        val oldCenterX = currentRect.x + currentRect.width / 2f
        val oldCenterY = currentRect.y + currentRect.height / 2f
        val desiredX = oldCenterX - newSize.widthPx / 2f + panDx
        val desiredY = oldCenterY - newSize.heightPx / 2f + panDy

        val (clampedX, clampedY) = clampOffsetPx(
            x = desiredX,
            y = desiredY,
            widthPx = newSize.widthPx,
            heightPx = newSize.heightPx,
            viewportWidthPx = viewportWidthPx,
            viewportHeightPx = viewportHeightPx,
            edgeMarginPx = edgeMarginPx,
            topMarginPx = topMarginPx,
            bottomMarginPx = bottomMarginPx
        )

        return FloatingPipRectPx(
            x = clampedX,
            y = clampedY,
            width = newSize.widthPx,
            height = newSize.heightPx,
            widthFraction = newSize.effectiveWidthFraction
        )
    }
}

object QueueDismissMath {
    /**
     * Determines whether a downward drag or fling on the Queue overlay should return
     * to the full music player underneath.
     */
    fun shouldDismissToPlayer(
        dragDownPx: Float,
        velocityYPxPerSec: Float,
        distanceThresholdPx: Float,
        velocityThresholdPxPerSec: Float
    ): Boolean {
        if (dragDownPx <= 0f) return false
        if (dragDownPx >= distanceThresholdPx) return true
        return velocityYPxPerSec >= velocityThresholdPxPerSec &&
            dragDownPx >= distanceThresholdPx * 0.28f
    }
}
