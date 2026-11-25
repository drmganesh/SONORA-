package domain.video

import androidx.compose.runtime.Immutable
import kotlin.math.roundToInt

/**
 * Available safe viewport in pixels for the in-app floating PiP video overlay.
 * Margins exclude status bar, navigation bar, bottom chrome clearance, and screen edge padding.
 */
@Immutable
data class FloatingPipViewport(
    val widthPx: Float,
    val heightPx: Float,
    val edgeMarginPx: Float = 0f,
    val topSafeMarginPx: Float = edgeMarginPx,
    val bottomSafeMarginPx: Float = edgeMarginPx,
    val minVisibleWidthPx: Float = 0f
) {
    val safeInnerWidthPx: Float
        get() = (widthPx - (edgeMarginPx * 2f)).coerceAtLeast(1f)

    val safeInnerHeightPx: Float
        get() = (heightPx - topSafeMarginPx - bottomSafeMarginPx).coerceAtLeast(1f)
}

/**
 * Resolved position and dimensions (in pixels) for the floating PiP video container.
 */
@Immutable
data class FloatingPipBounds(
    val widthPx: Float,
    val heightPx: Float,
    val offsetX: Float,
    val offsetY: Float,
    val widthFraction: Float,
    val aspectRatio: Float
) {
    val rightPx: Float get() = offsetX + widthPx
    val bottomPx: Float get() = offsetY + heightPx
    val widthPercent: Int get() = (widthFraction * 100f).roundToInt()
}

/**
 * Pure, allocation-light geometry engine for pinch-to-resize floating PiP video sizing,
 * focal-point anchoring, aspect-ratio preservation, and screen-boundary clamping.
 */
object FloatingPipResizeCalculator {
    const val MIN_WIDTH_FRACTION: Float = 0.20f
    const val MAX_WIDTH_FRACTION: Float = 0.85f
    const val INITIAL_WIDTH_FRACTION: Float = 0.40f
    const val MAX_HEIGHT_FRACTION: Float = 0.72f
    const val DEFAULT_ASPECT_RATIO: Float = 16f / 9f

    private const val MIN_ASPECT_RATIO: Float = 0.25f
    private const val MAX_ASPECT_RATIO: Float = 4.0f

    /**
     * Resolves the video's true display aspect ratio from Media3 video dimensions,
     * pixel aspect ratio, and rotation metadata. Never forces non-16:9 videos into 16:9.
     */
    fun resolveVideoAspectRatio(
        videoWidth: Int,
        videoHeight: Int,
        pixelWidthHeightRatio: Float = 1f,
        unappliedRotationDegrees: Int = 0
    ): Float {
        return VideoAspectRatioCalculator.resolveDisplayAspectRatio(
            videoWidth = videoWidth,
            videoHeight = videoHeight,
            pixelWidthHeightRatio = pixelWidthHeightRatio,
            unappliedRotationDegrees = unappliedRotationDegrees
        )
    }

    /**
     * Computes clamped (widthPx, heightPx, effectiveWidthFraction) while strictly preserving
     * [aspectRatio] and keeping the PiP window inside [MIN_WIDTH_FRACTION]..[MAX_WIDTH_FRACTION]
     * of screen width and within safe viewport height in both portrait and landscape.
     */
    fun computeConstrainedSize(
        viewport: FloatingPipViewport,
        aspectRatio: Float,
        requestedWidthFraction: Float
    ): Triple<Float, Float, Float> {
        val safeViewportW = viewport.widthPx.coerceAtLeast(1f)
        val safeViewportH = viewport.heightPx.coerceAtLeast(1f)
        val safeInnerW = viewport.safeInnerWidthPx
        val safeInnerH = viewport.safeInnerHeightPx
        val safeRatio = aspectRatio.takeIf { it.isFinite() && it > 0f }
            ?.coerceIn(MIN_ASPECT_RATIO, MAX_ASPECT_RATIO)
            ?: DEFAULT_ASPECT_RATIO

        // Maximum width allowed by width-fraction cap, inner safe width, and safe height cap
        val maxWidthByFraction = (safeViewportW * MAX_WIDTH_FRACTION).coerceAtMost(safeInnerW)
        val maxHeightAllowed = (safeViewportH * MAX_HEIGHT_FRACTION).coerceAtMost(safeInnerH)
        val maxWidthByHeight = (maxHeightAllowed * safeRatio).coerceAtMost(safeInnerW)
        val effectiveMaxWidth = minOf(maxWidthByFraction, maxWidthByHeight).coerceAtLeast(1f)

        // Minimum width allowed (~20% of screen width, with optional minimum visible dp floor)
        val rawMinWidth = maxOf(
            safeViewportW * MIN_WIDTH_FRACTION,
            viewport.minVisibleWidthPx.coerceAtLeast(0f)
        )
        val effectiveMinWidth = minOf(rawMinWidth, effectiveMaxWidth).coerceAtLeast(1f)

        val clampedFractionRequest = requestedWidthFraction
            .takeIf { it.isFinite() }
            ?.coerceIn(MIN_WIDTH_FRACTION, MAX_WIDTH_FRACTION)
            ?: INITIAL_WIDTH_FRACTION

        val targetWidth = (safeViewportW * clampedFractionRequest)
            .coerceIn(effectiveMinWidth, effectiveMaxWidth)

        // Compute height directly from aspect ratio; re-derive width if height hit safeInnerH
        val rawHeight = targetWidth / safeRatio
        val finalHeight = rawHeight.coerceAtMost(safeInnerH).coerceAtLeast(1f)
        val finalWidth = (finalHeight * safeRatio).coerceAtMost(safeInnerW).coerceAtLeast(1f)
        val effectiveFraction = (finalWidth / safeViewportW).coerceIn(0.05f, 1f)

        return Triple(finalWidth, finalHeight, effectiveFraction)
    }

    /**
     * Returns initial docked bounds anchored cleanly at bottom-end inside safe margins.
     */
    fun defaultDockedBounds(
        viewport: FloatingPipViewport,
        aspectRatio: Float,
        requestedWidthFraction: Float = INITIAL_WIDTH_FRACTION
    ): FloatingPipBounds {
        val safeRatio = aspectRatio.takeIf { it.isFinite() && it > 0f }
            ?.coerceIn(MIN_ASPECT_RATIO, MAX_ASPECT_RATIO)
            ?: DEFAULT_ASPECT_RATIO
        val (widthPx, heightPx, effectiveFraction) = computeConstrainedSize(
            viewport = viewport,
            aspectRatio = safeRatio,
            requestedWidthFraction = requestedWidthFraction
        )
        val rawX = viewport.widthPx - viewport.edgeMarginPx - widthPx
        val rawY = viewport.heightPx - viewport.bottomSafeMarginPx - heightPx
        val (clampedX, clampedY) = clampPosition(
            offsetX = rawX,
            offsetY = rawY,
            widthPx = widthPx,
            heightPx = heightPx,
            viewport = viewport
        )
        return FloatingPipBounds(
            widthPx = widthPx,
            heightPx = heightPx,
            offsetX = clampedX,
            offsetY = clampedY,
            widthFraction = effectiveFraction,
            aspectRatio = safeRatio
        )
    }

    /**
     * Clamps the PiP top-left offset so the entire window remains strictly inside [viewport].
     */
    fun clampPosition(
        offsetX: Float,
        offsetY: Float,
        widthPx: Float,
        heightPx: Float,
        viewport: FloatingPipViewport
    ): Pair<Float, Float> {
        val minX = viewport.edgeMarginPx
        val maxX = (viewport.widthPx - viewport.edgeMarginPx - widthPx).coerceAtLeast(minX)
        val minY = viewport.topSafeMarginPx
        val maxY = (viewport.heightPx - viewport.bottomSafeMarginPx - heightPx).coerceAtLeast(minY)
        val safeX = if (offsetX.isFinite()) offsetX else maxX
        val safeY = if (offsetY.isFinite()) offsetY else maxY
        return safeX.coerceIn(minX, maxX) to safeY.coerceIn(minY, maxY)
    }

    /**
     * Applies a cumulative pinch [zoomFactor] relative to [baseBounds], preserving the pinch
     * focal point where practical, strictly maintaining [aspectRatio], and clamping final
     * dimensions and coordinates inside [viewport].
     */
    fun applyPinchZoom(
        baseBounds: FloatingPipBounds,
        zoomFactor: Float,
        focalLocalX: Float,
        focalLocalY: Float,
        viewport: FloatingPipViewport,
        aspectRatio: Float
    ): FloatingPipBounds {
        val safeRatio = aspectRatio.takeIf { it.isFinite() && it > 0f }
            ?.coerceIn(MIN_ASPECT_RATIO, MAX_ASPECT_RATIO)
            ?: baseBounds.aspectRatio
        if (!zoomFactor.isFinite() || zoomFactor <= 0f) {
            val (clampedX, clampedY) = clampPosition(
                offsetX = baseBounds.offsetX,
                offsetY = baseBounds.offsetY,
                widthPx = baseBounds.widthPx,
                heightPx = baseBounds.heightPx,
                viewport = viewport
            )
            return baseBounds.copy(offsetX = clampedX, offsetY = clampedY, aspectRatio = safeRatio)
        }

        val safeZoom = zoomFactor.coerceIn(0.15f, 6.0f)
        val requestedFraction = (baseBounds.widthFraction * safeZoom)
            .coerceIn(MIN_WIDTH_FRACTION, MAX_WIDTH_FRACTION)

        val (newW, newH, effectiveFraction) = computeConstrainedSize(
            viewport = viewport,
            aspectRatio = safeRatio,
            requestedWidthFraction = requestedFraction
        )

        // Normalized focal coordinate inside the base PiP window [0..1]
        val relX = if (focalLocalX.isFinite() && baseBounds.widthPx > 0f) {
            (focalLocalX / baseBounds.widthPx).coerceIn(0f, 1f)
        } else {
            0.5f
        }
        val relY = if (focalLocalY.isFinite() && baseBounds.heightPx > 0f) {
            (focalLocalY / baseBounds.heightPx).coerceIn(0f, 1f)
        } else {
            0.5f
        }

        // Keep the pinch focal point fixed in parent viewport space before boundary clamping
        val focalParentX = baseBounds.offsetX + (relX * baseBounds.widthPx)
        val focalParentY = baseBounds.offsetY + (relY * baseBounds.heightPx)
        val rawNewX = focalParentX - (relX * newW)
        val rawNewY = focalParentY - (relY * newH)

        val (clampedX, clampedY) = clampPosition(
            offsetX = rawNewX,
            offsetY = rawNewY,
            widthPx = newW,
            heightPx = newH,
            viewport = viewport
        )

        return FloatingPipBounds(
            widthPx = newW,
            heightPx = newH,
            offsetX = clampedX,
            offsetY = clampedY,
            widthFraction = effectiveFraction,
            aspectRatio = safeRatio
        )
    }

    /**
     * Adapts existing PiP bounds cleanly across orientation changes (portrait <-> landscape),
     * split-screen resizing, inset changes, or video aspect ratio updates while preserving the
     * user's relative anchor corner and preferred width fraction.
     */
    fun adaptToViewportOrAspectRatio(
        previousBounds: FloatingPipBounds?,
        previousViewport: FloatingPipViewport?,
        newViewport: FloatingPipViewport,
        newAspectRatio: Float,
        preferredWidthFraction: Float
    ): FloatingPipBounds {
        if (previousBounds == null || previousViewport == null) {
            return defaultDockedBounds(
                viewport = newViewport,
                aspectRatio = newAspectRatio,
                requestedWidthFraction = preferredWidthFraction
            )
        }

        val safeRatio = newAspectRatio.takeIf { it.isFinite() && it > 0f }
            ?.coerceIn(MIN_ASPECT_RATIO, MAX_ASPECT_RATIO)
            ?: DEFAULT_ASPECT_RATIO

        val (newW, newH, effectiveFraction) = computeConstrainedSize(
            viewport = newViewport,
            aspectRatio = safeRatio,
            requestedWidthFraction = preferredWidthFraction
        )

        // Preserve normalized anchor inside the previous movable area (defaults to bottom-end 1f, 1f)
        val prevMinX = previousViewport.edgeMarginPx
        val prevMaxX = (previousViewport.widthPx - previousViewport.edgeMarginPx - previousBounds.widthPx)
            .coerceAtLeast(prevMinX)
        val prevRangeX = (prevMaxX - prevMinX)
        val anchorNormX = if (prevRangeX > 1f) {
            ((previousBounds.offsetX - prevMinX) / prevRangeX).coerceIn(0f, 1f)
        } else {
            1f
        }

        val prevMinY = previousViewport.topSafeMarginPx
        val prevMaxY = (previousViewport.heightPx - previousViewport.bottomSafeMarginPx - previousBounds.heightPx)
            .coerceAtLeast(prevMinY)
        val prevRangeY = (prevMaxY - prevMinY)
        val anchorNormY = if (prevRangeY > 1f) {
            ((previousBounds.offsetY - prevMinY) / prevRangeY).coerceIn(0f, 1f)
        } else {
            1f
        }

        val newMinX = newViewport.edgeMarginPx
        val newMaxX = (newViewport.widthPx - newViewport.edgeMarginPx - newW).coerceAtLeast(newMinX)
        val newMinY = newViewport.topSafeMarginPx
        val newMaxY = (newViewport.heightPx - newViewport.bottomSafeMarginPx - newH).coerceAtLeast(newMinY)

        val targetX = newMinX + anchorNormX * (newMaxX - newMinX)
        val targetY = newMinY + anchorNormY * (newMaxY - newMinY)

        val (clampedX, clampedY) = clampPosition(
            offsetX = targetX,
            offsetY = targetY,
            widthPx = newW,
            heightPx = newH,
            viewport = newViewport
        )

        return FloatingPipBounds(
            widthPx = newW,
            heightPx = newH,
            offsetX = clampedX,
            offsetY = clampedY,
            widthFraction = effectiveFraction,
            aspectRatio = safeRatio
        )
    }
}
