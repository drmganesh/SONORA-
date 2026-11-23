package domain.video

import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Eight display aspect ratio modes supported by SONORA's Online Video player.
 * Default is [AUTO], which preserves the video's original pixel proportions without distortion.
 * Only [STRETCH] allows non-uniform stretching when explicitly selected by the user.
 */
enum class VideoAspectRatioMode(
    val id: String,
    val shortLabel: String,
    val displayTitle: String,
    val description: String,
    val targetFrameRatio: Float? = null
) {
    AUTO(
        id = "auto",
        shortLabel = "Auto",
        displayTitle = "Auto (Recommended)",
        description = "Uses the original video aspect ratio without cropping or distortion."
    ),
    FIT(
        id = "fit",
        shortLabel = "Fit",
        displayTitle = "Fit Screen",
        description = "Shows the complete video, allowing empty space where necessary."
    ),
    FILL(
        id = "fill",
        shortLabel = "Fill",
        displayTitle = "Fill Screen",
        description = "Fills the available player area while cropping excess edges."
    ),
    ZOOM(
        id = "zoom",
        shortLabel = "Zoom",
        displayTitle = "Zoom Crop",
        description = "Zooms smoothly into the frame while keeping proportions true."
    ),
    ORIGINAL(
        id = "original",
        shortLabel = "Original",
        displayTitle = "Original Size",
        description = "Renders the native unscaled source aspect ratio centered cleanly."
    ),
    RATIO_16_9(
        id = "16_9",
        shortLabel = "16:9",
        displayTitle = "16:9",
        description = "Displays in a 16:9 frame without distorting the original image.",
        targetFrameRatio = 16f / 9f
    ),
    RATIO_4_3(
        id = "4_3",
        shortLabel = "4:3",
        displayTitle = "4:3",
        description = "Displays in a 4:3 frame without distorting the original image.",
        targetFrameRatio = 4f / 3f
    ),
    RATIO_1_1(
        id = "1_1",
        shortLabel = "1:1",
        displayTitle = "1:1",
        description = "Displays in a 1:1 square frame without distorting the original image.",
        targetFrameRatio = 1f
    ),
    RATIO_9_16(
        id = "9_16",
        shortLabel = "9:16",
        displayTitle = "9:16",
        description = "Displays in a 9:16 vertical frame without distorting the original image.",
        targetFrameRatio = 9f / 16f
    ),
    STRETCH(
        id = "stretch",
        shortLabel = "Stretch",
        displayTitle = "Stretch (Optional)",
        description = "Stretches video edges to fill the viewport. Only stretches when explicitly selected."
    );

    companion object {
        fun fromId(raw: String?): VideoAspectRatioMode =
            entries.firstOrNull { it.id.equals(raw, ignoreCase = true) } ?: AUTO
    }
}

/**
 * Configurable vertical swipe distance threshold for central fullscreen gestures.
 */
enum class VideoSwipeSensitivity(
    val id: String,
    val label: String,
    val subtitle: String,
    val thresholdDp: Float
) {
    LOW(
        id = "low",
        label = "Low",
        subtitle = "Longer swipe required to prevent accidental triggers",
        thresholdDp = 96f
    ),
    BALANCED(
        id = "balanced",
        label = "Balanced",
        subtitle = "Recommended response for natural fullscreen gestures",
        thresholdDp = 64f
    ),
    HIGH(
        id = "high",
        label = "High",
        subtitle = "Quick fullscreen transition with a shorter swipe",
        thresholdDp = 42f
    );

    companion object {
        fun fromId(raw: String?): VideoSwipeSensitivity =
            entries.firstOrNull { it.id.equals(raw, ignoreCase = true) } ?: BALANCED
    }
}

/**
 * Persisted user settings under Settings → Online Video → Player Gestures.
 */
data class VideoPlayerGestureSettings(
    val swipeFullscreenEnabled: Boolean = true,
    val autoAspectRatioEnabled: Boolean = true,
    val defaultAspectRatio: VideoAspectRatioMode = VideoAspectRatioMode.AUTO,
    val swipeSensitivity: VideoSwipeSensitivity = VideoSwipeSensitivity.BALANCED,
    val doubleTapSeekSeconds: Int = 10,
    val autoOrientationEnabled: Boolean = true
) {
    companion object {
        val supportedSeekDurationsSeconds = listOf(5, 10, 15, 20, 30)
    }

    val doubleTapSeekMs: Long
        get() = doubleTapSeekSeconds.coerceIn(5, 30) * 1000L
}

/**
 * Resolved geometry for rendering a video surface inside a player viewport without unwanted distortion.
 *
 * - [frameWidthPx] x [frameHeightPx] defines the clipped display container inside the viewport.
 * - [surfaceWidthPx] x [surfaceHeightPx] defines the actual Media3 TextureView size centered inside the frame.
 */
data class VideoSurfaceLayout(
    val frameWidthPx: Float,
    val frameHeightPx: Float,
    val surfaceWidthPx: Float,
    val surfaceHeightPx: Float,
    val effectiveVideoAspectRatio: Float,
    val displayedFrameAspectRatio: Float,
    val isCropped: Boolean,
    val isDistorted: Boolean,
    val detectedRatioLabel: String
)

enum class VideoTouchZone {
    SYSTEM_EDGE_GUARD,
    LEFT_BRIGHTNESS,
    CENTER_FULLSCREEN,
    RIGHT_VOLUME
}

enum class FullscreenSwipeDecision {
    NONE,
    ENTER_FULLSCREEN,
    EXIT_FULLSCREEN,
    MINIMIZE_TO_MINI_PLAYER
}

/**
 * Pure pinch-to-zoom and two-finger pan geometry state for the central video viewport.
 * Keeps scaling uniform (scaleX == scaleY) so the video is never stretched or distorted.
 */
data class VideoPinchZoomState(
    val scale: Float = 1f,
    val offsetX: Float = 0f,
    val offsetY: Float = 0f
) {
    val isZoomed: Boolean
        get() = scale > 1.02f
}

object VideoPinchZoomMath {
    const val MIN_SCALE = 1.0f
    const val MAX_SCALE = 3.0f

    fun applyTransform(
        current: VideoPinchZoomState,
        zoomChange: Float,
        panX: Float,
        panY: Float,
        viewportWidthPx: Float,
        viewportHeightPx: Float
    ): VideoPinchZoomState {
        if (viewportWidthPx <= 0f || viewportHeightPx <= 0f) return current
        val nextScale = (current.scale * zoomChange).coerceIn(MIN_SCALE, MAX_SCALE)
        if (nextScale <= 1.01f) {
            return VideoPinchZoomState(scale = 1f, offsetX = 0f, offsetY = 0f)
        }
        val maxOffsetX = ((viewportWidthPx * (nextScale - 1f)) / 2f).coerceAtLeast(0f)
        val maxOffsetY = ((viewportHeightPx * (nextScale - 1f)) / 2f).coerceAtLeast(0f)
        val nextX = (current.offsetX + panX).coerceIn(-maxOffsetX, maxOffsetX)
        val nextY = (current.offsetY + panY).coerceIn(-maxOffsetY, maxOffsetY)
        return VideoPinchZoomState(scale = nextScale, offsetX = nextX, offsetY = nextY)
    }

    fun reset(): VideoPinchZoomState = VideoPinchZoomState()
}

/**
 * Pure aspect ratio engine supporting 16:9, 18:9, 19.5:9, 20:9, 4:3, 3:2, 1:1, 9:16, 4:5,
 * and arbitrary custom ratios with Media3 rotation and pixel-aspect-ratio metadata.
 */
object VideoAspectRatioCalculator {

    private const val DEFAULT_FALLBACK_RATIO = 16f / 9f
    private const val EPSILON = 0.015f

    private data class KnownRatio(val label: String, val ratio: Float)

    private val knownRatios = listOf(
        KnownRatio("21:9", 21f / 9f),
        KnownRatio("20:9", 20f / 9f),
        KnownRatio("19.5:9", 19.5f / 9f),
        KnownRatio("18:9", 18f / 9f),
        KnownRatio("16:9", 16f / 9f),
        KnownRatio("3:2", 3f / 2f),
        KnownRatio("4:3", 4f / 3f),
        KnownRatio("1:1", 1f),
        KnownRatio("4:5", 4f / 5f),
        KnownRatio("9:16", 9f / 16f)
    )

    /**
     * Resolves display dimensions after applying Media3 pixelWidthHeightRatio and rotation metadata.
     * Returns 16x9 fallback gracefully when dimensions are unknown (<= 0).
     */
    fun resolveEffectiveDimensions(
        videoWidth: Int,
        videoHeight: Int,
        pixelWidthHeightRatio: Float = 1f,
        unappliedRotationDegrees: Int = 0
    ): Pair<Float, Float> {
        if (videoWidth <= 0 || videoHeight <= 0) {
            return 16f to 9f
        }
        val safePar = if (pixelWidthHeightRatio.isFinite() && pixelWidthHeightRatio > 0.05f) {
            pixelWidthHeightRatio
        } else {
            1f
        }
        val adjustedW = videoWidth.toFloat() * safePar
        val adjustedH = videoHeight.toFloat()
        val normalizedRotation = ((unappliedRotationDegrees % 360) + 360) % 360
        return if (normalizedRotation == 90 || normalizedRotation == 270) {
            adjustedH to adjustedW
        } else {
            adjustedW to adjustedH
        }
    }

    /**
     * Calculates `aspectRatio = effectiveWidth / effectiveHeight` safely.
     */
    fun resolveDisplayAspectRatio(
        videoWidth: Int,
        videoHeight: Int,
        pixelWidthHeightRatio: Float = 1f,
        unappliedRotationDegrees: Int = 0
    ): Float {
        if (videoWidth <= 0 || videoHeight <= 0) return DEFAULT_FALLBACK_RATIO
        val (w, h) = resolveEffectiveDimensions(
            videoWidth = videoWidth,
            videoHeight = videoHeight,
            pixelWidthHeightRatio = pixelWidthHeightRatio,
            unappliedRotationDegrees = unappliedRotationDegrees
        )
        if (w <= 0f || h <= 0f) return DEFAULT_FALLBACK_RATIO
        return (w / h).coerceIn(0.25f, 4.0f)
    }

    /**
     * Maps a numeric ratio to its human-readable standard label (16:9, 18:9, 19.5:9, 20:9, 4:3,
     * 3:2, 1:1, 9:16, 4:5) or a clean custom ratio string.
     */
    fun detectRatioLabel(aspectRatio: Float): String {
        val safe = aspectRatio.coerceIn(0.25f, 4.0f)
        val best = knownRatios.minByOrNull { abs(it.ratio - safe) }
        if (best != null && abs(best.ratio - safe) <= 0.035f) {
            return best.label
        }
        return if (safe >= 1f) {
            String.format(Locale.US, "%.2f:1", safe)
        } else {
            String.format(Locale.US, "1:%.2f", 1f / safe)
        }
    }

    /**
     * Computes a responsive portrait container aspect ratio so vertical/square/cinematic videos
     * adapt gracefully in portrait mode without overwhelming the details layout.
     */
    fun computePortraitContainerAspectRatio(
        videoAspectRatio: Float,
        mode: VideoAspectRatioMode,
        autoAspectRatioEnabled: Boolean
    ): Float {
        val safeVideoRatio = videoAspectRatio.coerceIn(0.35f, 3.0f)
        return when (mode) {
            VideoAspectRatioMode.AUTO -> {
                if (autoAspectRatioEnabled) {
                    safeVideoRatio.coerceIn(0.75f, 2.25f)
                } else {
                    DEFAULT_FALLBACK_RATIO
                }
            }
            VideoAspectRatioMode.FIT,
            VideoAspectRatioMode.ORIGINAL -> safeVideoRatio.coerceIn(0.85f, 2.25f)
            VideoAspectRatioMode.FILL,
            VideoAspectRatioMode.ZOOM,
            VideoAspectRatioMode.STRETCH -> DEFAULT_FALLBACK_RATIO
            VideoAspectRatioMode.RATIO_16_9 -> 16f / 9f
            VideoAspectRatioMode.RATIO_4_3 -> 4f / 3f
            VideoAspectRatioMode.RATIO_1_1 -> 1f
            VideoAspectRatioMode.RATIO_9_16 -> 0.75f
        }
    }

    /**
     * Computes exact clipped frame and TextureView surface dimensions inside the given viewport.
     */
    fun computeSurfaceLayout(
        viewportWidthPx: Float,
        viewportHeightPx: Float,
        videoWidth: Int,
        videoHeight: Int,
        pixelWidthHeightRatio: Float = 1f,
        unappliedRotationDegrees: Int = 0,
        mode: VideoAspectRatioMode
    ): VideoSurfaceLayout {
        val vpW = viewportWidthPx.coerceAtLeast(1f)
        val vpH = viewportHeightPx.coerceAtLeast(1f)
        val vpRatio = vpW / vpH
        val videoRatio = resolveDisplayAspectRatio(
            videoWidth = videoWidth,
            videoHeight = videoHeight,
            pixelWidthHeightRatio = pixelWidthHeightRatio,
            unappliedRotationDegrees = unappliedRotationDegrees
        )
        val label = detectRatioLabel(videoRatio)

        return when (mode) {
            VideoAspectRatioMode.AUTO,
            VideoAspectRatioMode.FIT,
            VideoAspectRatioMode.ORIGINAL -> {
                val (fitW, fitH) = fitInside(vpW, vpH, videoRatio)
                VideoSurfaceLayout(
                    frameWidthPx = fitW,
                    frameHeightPx = fitH,
                    surfaceWidthPx = fitW,
                    surfaceHeightPx = fitH,
                    effectiveVideoAspectRatio = videoRatio,
                    displayedFrameAspectRatio = fitW / fitH,
                    isCropped = false,
                    isDistorted = false,
                    detectedRatioLabel = label
                )
            }

            VideoAspectRatioMode.FILL -> {
                val (coverW, coverH) = coverTarget(vpW, vpH, videoRatio)
                val cropped = (coverW > vpW + 1f) || (coverH > vpH + 1f)
                VideoSurfaceLayout(
                    frameWidthPx = vpW,
                    frameHeightPx = vpH,
                    surfaceWidthPx = coverW,
                    surfaceHeightPx = coverH,
                    effectiveVideoAspectRatio = videoRatio,
                    displayedFrameAspectRatio = vpRatio,
                    isCropped = cropped,
                    isDistorted = false,
                    detectedRatioLabel = label
                )
            }

            VideoAspectRatioMode.ZOOM -> {
                val (coverW, coverH) = coverTarget(vpW, vpH, videoRatio)
                val zoomFactor = 1.18f
                val zoomedW = coverW * zoomFactor
                val zoomedH = coverH * zoomFactor
                VideoSurfaceLayout(
                    frameWidthPx = vpW,
                    frameHeightPx = vpH,
                    surfaceWidthPx = zoomedW,
                    surfaceHeightPx = zoomedH,
                    effectiveVideoAspectRatio = videoRatio,
                    displayedFrameAspectRatio = vpRatio,
                    isCropped = true,
                    isDistorted = false,
                    detectedRatioLabel = label
                )
            }

            VideoAspectRatioMode.RATIO_16_9,
            VideoAspectRatioMode.RATIO_4_3,
            VideoAspectRatioMode.RATIO_1_1,
            VideoAspectRatioMode.RATIO_9_16 -> {
                val targetRatio = mode.targetFrameRatio ?: DEFAULT_FALLBACK_RATIO
                val (frameW, frameH) = fitInside(vpW, vpH, targetRatio)
                // Preserve original video proportions inside the chosen display frame (center-crop, never stretch)
                val (surfaceW, surfaceH) = coverTarget(frameW, frameH, videoRatio)
                val cropped = (surfaceW > frameW + 1f) || (surfaceH > frameH + 1f)
                VideoSurfaceLayout(
                    frameWidthPx = frameW,
                    frameHeightPx = frameH,
                    surfaceWidthPx = surfaceW,
                    surfaceHeightPx = surfaceH,
                    effectiveVideoAspectRatio = videoRatio,
                    displayedFrameAspectRatio = frameW / frameH,
                    isCropped = cropped,
                    isDistorted = false,
                    detectedRatioLabel = label
                )
            }

            VideoAspectRatioMode.STRETCH -> {
                val distorted = abs(vpRatio - videoRatio) > EPSILON
                VideoSurfaceLayout(
                    frameWidthPx = vpW,
                    frameHeightPx = vpH,
                    surfaceWidthPx = vpW,
                    surfaceHeightPx = vpH,
                    effectiveVideoAspectRatio = videoRatio,
                    displayedFrameAspectRatio = vpRatio,
                    isCropped = false,
                    isDistorted = distorted,
                    detectedRatioLabel = label
                )
            }
        }
    }

    private fun fitInside(maxW: Float, maxH: Float, targetRatio: Float): Pair<Float, Float> {
        val containerRatio = maxW / maxH
        return if (targetRatio > containerRatio) {
            val w = maxW
            val h = (maxW / targetRatio).coerceIn(1f, maxH)
            w to h
        } else {
            val h = maxH
            val w = (maxH * targetRatio).coerceIn(1f, maxW)
            w to h
        }
    }

    private fun coverTarget(targetW: Float, targetH: Float, sourceRatio: Float): Pair<Float, Float> {
        val targetRatio = targetW / targetH
        return if (sourceRatio > targetRatio) {
            val h = targetH
            val w = max(targetW, targetH * sourceRatio)
            w to h
        } else {
            val w = targetW
            val h = max(targetH, targetW / sourceRatio)
            w to h
        }
    }
}

/**
 * Pure conflict-free gesture classifier separating edge brightness/volume drags, system gesture
 * guard zones, horizontal scrubs, and central vertical fullscreen swipes.
 */
object VideoGestureClassifier {

    private const val HORIZONTAL_EDGE_GUARD_FRACTION = 0.02f
    private const val LEFT_ZONE_END_FRACTION = 0.25f
    private const val RIGHT_ZONE_START_FRACTION = 0.75f
    private const val TOP_GUARD_FRACTION = 0.12f
    private const val BOTTOM_GUARD_FRACTION = 0.16f
    private const val VERTICAL_DOMINANCE_FACTOR = 1.35f

    /**
     * Classifies where a touch gesture started inside the video viewport.
     * Central fullscreen swipe MUST start in the middle video zone (25%..75% horizontally,
     * safely inset from top system status bar and bottom timeline/navigation gesture bar).
     */
    fun classifyTouchZone(
        startX: Float,
        startY: Float,
        viewportWidth: Float,
        viewportHeight: Float
    ): VideoTouchZone {
        if (viewportWidth <= 0f || viewportHeight <= 0f) {
            return VideoTouchZone.SYSTEM_EDGE_GUARD
        }
        val xFrac = startX / viewportWidth
        val yFrac = startY / viewportHeight

        if (yFrac < TOP_GUARD_FRACTION || yFrac > (1f - BOTTOM_GUARD_FRACTION)) {
            return VideoTouchZone.SYSTEM_EDGE_GUARD
        }
        if (xFrac < HORIZONTAL_EDGE_GUARD_FRACTION || xFrac > (1f - HORIZONTAL_EDGE_GUARD_FRACTION)) {
            return VideoTouchZone.SYSTEM_EDGE_GUARD
        }
        return when {
            xFrac < LEFT_ZONE_END_FRACTION -> VideoTouchZone.LEFT_BRIGHTNESS
            xFrac > RIGHT_ZONE_START_FRACTION -> VideoTouchZone.RIGHT_VOLUME
            else -> VideoTouchZone.CENTER_FULLSCREEN
        }
    }

    /**
     * Determines whether a central-zone drag should trigger a fullscreen transition or minimize to
     * SONORA's in-app floating mini player.
     * - Portrait mode: upward swipe (`totalDy <= -thresholdPx`) → [FullscreenSwipeDecision.ENTER_FULLSCREEN]
     * - Portrait mode: downward swipe (`totalDy >= thresholdPx`) → [FullscreenSwipeDecision.MINIMIZE_TO_MINI_PLAYER]
     * - Landscape fullscreen: downward swipe (`totalDy >= thresholdPx`) → [FullscreenSwipeDecision.EXIT_FULLSCREEN]
     * - Ignores short movements, horizontal drags, disabled settings, or active timeline/menu interactions.
     */
    fun evaluateVerticalFullscreenSwipe(
        totalDx: Float,
        totalDy: Float,
        thresholdPx: Float,
        isFullScreenLandscape: Boolean,
        isSwipeEnabled: Boolean,
        isBlockedByControlsOrMenu: Boolean = false,
        isSwipeDownToMiniPlayerEnabled: Boolean = false
    ): FullscreenSwipeDecision {
        if (isBlockedByControlsOrMenu || thresholdPx <= 0f) {
            return FullscreenSwipeDecision.NONE
        }
        val absDx = abs(totalDx)
        val absDy = abs(totalDy)
        if (absDy < thresholdPx) return FullscreenSwipeDecision.NONE
        if (absDy <= absDx * VERTICAL_DOMINANCE_FACTOR) return FullscreenSwipeDecision.NONE

        return when {
            !isFullScreenLandscape && isSwipeEnabled && totalDy <= -thresholdPx ->
                FullscreenSwipeDecision.ENTER_FULLSCREEN
            !isFullScreenLandscape && isSwipeDownToMiniPlayerEnabled && totalDy >= thresholdPx ->
                FullscreenSwipeDecision.MINIMIZE_TO_MINI_PLAYER
            isFullScreenLandscape && isSwipeEnabled && totalDy >= thresholdPx ->
                FullscreenSwipeDecision.EXIT_FULLSCREEN
            else -> FullscreenSwipeDecision.NONE
        }
    }

    /**
     * Normalized 0f..1f drag progress in the active valid direction for smooth visual preview.
     */
    fun computeDragProgress(
        totalDx: Float,
        totalDy: Float,
        thresholdPx: Float,
        isFullScreenLandscape: Boolean,
        allowPortraitSwipeDownMinimize: Boolean = false
    ): Float {
        if (thresholdPx <= 0f) return 0f
        val absDx = abs(totalDx)
        val absDy = abs(totalDy)
        if (absDy < 6f || absDy <= absDx * 1.15f) return 0f

        val directionalDistance = when {
            isFullScreenLandscape -> totalDy.coerceAtLeast(0f)
            totalDy < 0f -> -totalDy
            allowPortraitSwipeDownMinimize && totalDy > 0f -> totalDy
            else -> 0f
        }
        return min(1f, directionalDistance / thresholdPx)
    }
}
