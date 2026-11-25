package domain.video

import androidx.compose.runtime.Immutable
import kotlin.math.roundToInt

/**
 * Deduplicated snapshot of Android System Picture-in-Picture parameters so MainActivity
 * only updates [android.app.PictureInPictureParams] when PiP-relevant state changes.
 */
@Immutable
data class SystemPipParamsSnapshot(
    val canEnterPip: Boolean,
    val autoEnterEnabled: Boolean,
    val isPlaying: Boolean,
    val hasNextInQueue: Boolean,
    val aspectNumerator: Int,
    val aspectDenominator: Int,
    val sourceRectLeft: Int = 0,
    val sourceRectTop: Int = 0,
    val sourceRectRight: Int = 0,
    val sourceRectBottom: Int = 0
) {
    val hasValidSourceRect: Boolean
        get() = sourceRectRight > sourceRectLeft && sourceRectBottom > sourceRectTop
}

/**
 * Pure policy and aspect-ratio calculator for Android System Picture-in-Picture (API 26+).
 * Ensures PiP is only entered for valid active videos (never music-only mode), renders ONLY
 * the video surface while in system PiP, and respects Android's strict aspect ratio bounds.
 */
object SystemPipPolicy {
    // Android enforces aspect ratios within [1 / 2.39, 2.39] (~0.41841 .. 2.39).
    // Slight safety inset avoids floating-point rounding IllegalArgumentException on edge ratios.
    const val MIN_ANDROID_PIP_ASPECT_RATIO: Float = 0.42f
    const val MAX_ANDROID_PIP_ASPECT_RATIO: Float = 2.389f
    private const val RATIONAL_PRECISION: Int = 1000

    /**
     * Returns true only when a valid video is active and not in audio-only background mode.
     * Prevents entering PiP while displaying music-only content.
     */
    fun canEnterSystemPip(state: VideoPlayerUiState): Boolean {
        return state.hasActiveVideo && !state.backgroundPlaybackAllowed
    }

    /**
     * Returns true when pressing Home / swiping up should automatically enter Android System PiP.
     */
    fun shouldAutoEnterSystemPip(
        state: VideoPlayerUiState,
        autoPipEnabled: Boolean
    ): Boolean {
        return autoPipEnabled &&
            canEnterSystemPip(state) &&
            state.isPlaying &&
            !state.hasVideoEnded
    }

    /**
     * Returns true when Compose must switch to the dedicated video-only System PiP surface
     * and omit Home screen, bottom navigation, music mini-player, and all non-video UI.
     */
    fun shouldRenderVideoOnlyPipUi(state: VideoPlayerUiState): Boolean {
        return state.isInPictureInPicture && state.hasActiveVideo
    }

    /**
     * Computes a valid Android PiP aspect ratio Pair(numerator, denominator) from the video's
     * true display dimensions, pixel aspect ratio, and rotation metadata.
     * Supports landscape (16:9, 4:3, cinematic), square (1:1), and portrait (9:16, 4:5) videos
     * without forcing 16:9 or exceeding Android's PiP aspect ratio constraints.
     */
    fun computePipAspectRatio(
        videoWidth: Int,
        videoHeight: Int,
        pixelWidthHeightRatio: Float = 1f,
        unappliedRotationDegrees: Int = 0
    ): Pair<Int, Int> {
        val displayRatio = VideoAspectRatioCalculator.resolveDisplayAspectRatio(
            videoWidth = videoWidth,
            videoHeight = videoHeight,
            pixelWidthHeightRatio = pixelWidthHeightRatio,
            unappliedRotationDegrees = unappliedRotationDegrees
        )
        val clamped = displayRatio
            .takeIf { it.isFinite() && it > 0f }
            ?.coerceIn(MIN_ANDROID_PIP_ASPECT_RATIO, MAX_ANDROID_PIP_ASPECT_RATIO)
            ?: (16f / 9f)
        val numerator = (clamped * RATIONAL_PRECISION).roundToInt().coerceIn(420, 2389)
        return numerator to RATIONAL_PRECISION
    }

    fun buildParamsSnapshot(
        state: VideoPlayerUiState,
        autoPipEnabled: Boolean,
        sourceRectLeft: Int = 0,
        sourceRectTop: Int = 0,
        sourceRectRight: Int = 0,
        sourceRectBottom: Int = 0
    ): SystemPipParamsSnapshot {
        val (num, den) = computePipAspectRatio(
            videoWidth = state.videoWidth,
            videoHeight = state.videoHeight,
            pixelWidthHeightRatio = state.pixelWidthHeightRatio,
            unappliedRotationDegrees = state.unappliedRotationDegrees
        )
        val canEnter = canEnterSystemPip(state)
        val allowAuto = shouldAutoEnterSystemPip(state, autoPipEnabled)
        return SystemPipParamsSnapshot(
            canEnterPip = canEnter,
            autoEnterEnabled = allowAuto,
            isPlaying = state.isPlaying,
            hasNextInQueue = state.hasNextInQueue,
            aspectNumerator = num,
            aspectDenominator = den,
            sourceRectLeft = if (canEnter) sourceRectLeft else 0,
            sourceRectTop = if (canEnter) sourceRectTop else 0,
            sourceRectRight = if (canEnter) sourceRectRight else 0,
            sourceRectBottom = if (canEnter) sourceRectBottom else 0
        )
    }
}
