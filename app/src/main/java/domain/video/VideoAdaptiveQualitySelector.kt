package domain.video

import data.preferences.VideoExperienceSettings

data class AdaptiveVideoQualityDecision(
    val targetMaxHeightPx: Int,
    val selectedStream: VideoStreamOption?,
    val isAutoMode: Boolean,
    val reasonLabel: String
)

/**
 * Adaptive video quality selector preserving original source quality for SONORA's Online Video player.
 *
 * Default behavior ("Auto: Best Available Quality"):
 * - Selects ONLY from streams actually available for the current video (never invents resolutions).
 * - Prefers the highest stable original resolution supported by the source and device (including
 *   1080p Full HD, 1440p 2K, 2160p 4K when actually present).
 * - Does NOT artificially cap mobile data to 480p or Wi-Fi to 1080p unless Data Saver is explicitly enabled.
 * - Avoids downgrading on uncalibrated cold-start bandwidth estimates; steps down ONLY when genuine
 *   measured bandwidth constraints or rebuffering stalls require it, and recovers upward once stable.
 * - Prefers highest bitrate/fps at each resolution instead of biasing lower-bitrate muxed streams.
 */
object VideoAdaptiveQualitySelector {

    val STANDARD_QUALITY_TIERS = listOf(144, 240, 360, 480, 720, 1080, 1440, 2160, 4320)

    fun formatResolutionLabel(heightPx: Int, fallbackLabel: String = ""): String {
        return when {
            heightPx >= 4320 -> "4320p 8K"
            heightPx >= 2160 -> "2160p 4K"
            heightPx >= 1440 -> "1440p 2K"
            heightPx >= 1080 -> "1080p Full HD"
            heightPx >= 720 -> "720p HD"
            heightPx >= 480 -> "480p"
            heightPx >= 360 -> "360p"
            heightPx >= 240 -> "240p"
            heightPx >= 144 -> "144p"
            heightPx > 0 -> "${heightPx}p"
            fallbackLabel.isNotBlank() -> fallbackLabel
            else -> "Auto"
        }
    }

    fun selectBestQuality(
        availableStreams: List<VideoStreamOption>,
        settings: VideoExperienceSettings,
        isMeteredMobile: Boolean,
        estimatedBandwidthBps: Long,
        bufferedAheadMs: Long,
        rebufferCount: Int,
        deviceMaxHeightPx: Int = 2160,
        isInitialStartup: Boolean = false
    ): AdaptiveVideoQualityDecision {
        val validStreams = availableStreams.filter { it.heightPx > 0 }
        val maxSourceHeight = validStreams.maxOfOrNull { it.heightPx } ?: 0
        val effectiveDeviceMax = deviceMaxHeightPx.coerceAtLeast(360)

        // 1. Honor explicit manual quality if user selected one and Auto is disabled
        if (!settings.autoVideoQualityEnabled && settings.lastSelectedVideoQualityHeight > 0) {
            val manualHeight = settings.lastSelectedVideoQualityHeight
            val chosen = pickClosestStreamAtOrBelow(validStreams, manualHeight)
                ?: validStreams.minByOrNull { kotlin.math.abs(it.heightPx - manualHeight) }
            val chosenLabel = chosen?.let { formatResolutionLabel(it.heightPx, it.resolutionLabel) }
                ?: formatResolutionLabel(manualHeight)
            return AdaptiveVideoQualityDecision(
                targetMaxHeightPx = chosen?.heightPx ?: manualHeight,
                selectedStream = chosen,
                isAutoMode = false,
                reasonLabel = "Manual • $chosenLabel"
            )
        }

        // 2. Compute Auto: Best Available ceiling (respecting source + device + explicit Data Saver)
        val desiredMaxHeight = if (maxSourceHeight > 0) {
            minOf(maxSourceHeight, effectiveDeviceMax)
        } else {
            effectiveDeviceMax
        }

        val networkCapHeight = when {
            isMeteredMobile && settings.mobileDataSaverEnabled -> 480
            isMeteredMobile && !settings.allowHighQualityOnMobileData -> 720
            !isMeteredMobile && settings.preferHighestQualityOnWifi -> {
                maxOf(desiredMaxHeight, settings.preferredWifiQualityHeight).coerceAtMost(effectiveDeviceMax)
            }
            !isMeteredMobile -> settings.preferredWifiQualityHeight.coerceAtMost(effectiveDeviceMax)
            else -> desiredMaxHeight
        }

        // 3. Apply measured bandwidth cap ONLY when genuinely measured & constraining playback.
        // Avoid cold-start downgrade before real throughput samples or rebuffers occur.
        val bandwidthCapHeight = when {
            isInitialStartup && rebufferCount == 0 && bufferedAheadMs == 0L -> networkCapHeight
            estimatedBandwidthBps <= 0L -> networkCapHeight
            estimatedBandwidthBps < 350_000L -> 240
            estimatedBandwidthBps < 750_000L -> 360
            estimatedBandwidthBps < 1_400_000L -> 480
            estimatedBandwidthBps < 2_800_000L -> 720
            estimatedBandwidthBps < 6_500_000L -> 1080
            estimatedBandwidthBps < 14_000_000L -> 1440
            else -> 4320
        }

        // 4. Buffer health & rebuffer step-down (only when genuine stalls occur)
        var targetHeight = minOf(networkCapHeight, bandwidthCapHeight, effectiveDeviceMax)
        if (rebufferCount >= 2 || (bufferedAheadMs in 1L..2_500L && rebufferCount >= 1)) {
            targetHeight = stepDownTier(targetHeight)
        }
        if (rebufferCount >= 4) {
            targetHeight = stepDownTier(targetHeight)
        }

        val chosen = pickClosestStreamAtOrBelow(validStreams, targetHeight)
            ?: validStreams.minByOrNull { it.heightPx }

        val actualHeight = chosen?.heightPx ?: targetHeight
        val actualLabel = formatResolutionLabel(actualHeight, chosen?.resolutionLabel.orEmpty())
        val qualifier = when {
            isMeteredMobile && settings.mobileDataSaverEnabled -> "Data Saver"
            rebufferCount >= 1 && actualHeight < desiredMaxHeight -> "Adapting"
            else -> "Best Available"
        }

        return AdaptiveVideoQualityDecision(
            targetMaxHeightPx = actualHeight.coerceAtLeast(targetHeight).coerceAtMost(effectiveDeviceMax),
            selectedStream = chosen,
            isAutoMode = true,
            reasonLabel = "Auto: $qualifier ($actualLabel)"
        )
    }

    /**
     * Deduplicates available streams strictly by actual available resolution height,
     * selecting the highest quality (highest fps, highest bitrate, hardware-friendly VP9/H.264)
     * stream per resolution tier without ever fabricating unavailable resolutions.
     */
    fun distinctAvailableQualityOptions(streams: List<VideoStreamOption>): List<VideoStreamOption> {
        if (streams.isEmpty()) return emptyList()
        return streams
            .filter { it.heightPx > 0 }
            .groupBy { it.heightPx }
            .values
            .map { group -> pickBestStreamWithinTier(group) }
            .sortedByDescending { it.heightPx }
    }

    fun stepDownTier(currentHeight: Int): Int {
        val lower = STANDARD_QUALITY_TIERS.lastOrNull { it < currentHeight }
        return lower ?: 144
    }

    /**
     * Prevents adaptive quality ping-ponging when bandwidth fluctuates near a tier boundary:
     * - Downgrades are allowed after a short cooldown (8s) or immediately on repeated stalls.
     * - Upgrades recover higher quality cleanly once network & buffer stabilize (>= 8s buffer, 0 stalls).
     */
    fun shouldSwitchQualityWithHysteresis(
        currentHeightPx: Int,
        candidateHeightPx: Int,
        elapsedSinceLastSwitchMs: Long,
        bufferedAheadMs: Long,
        rebufferCount: Int,
        networkTypeChanged: Boolean
    ): Boolean {
        if (candidateHeightPx <= 0 || candidateHeightPx == currentHeightPx) return false
        if (networkTypeChanged) return true
        val isDowngrade = candidateHeightPx < currentHeightPx
        return if (isDowngrade) {
            rebufferCount >= 2 || (rebufferCount >= 1 && bufferedAheadMs in 1L..3_000L) || elapsedSinceLastSwitchMs >= 8_000L
        } else {
            rebufferCount == 0 && bufferedAheadMs >= 8_000L && elapsedSinceLastSwitchMs >= 15_000L
        }
    }

    private fun pickClosestStreamAtOrBelow(
        streams: List<VideoStreamOption>,
        maxHeight: Int
    ): VideoStreamOption? {
        if (streams.isEmpty()) return null
        val atOrBelow = streams.filter { it.heightPx in 1..maxHeight }
        if (atOrBelow.isNotEmpty()) {
            val highestAvailable = atOrBelow.maxOf { it.heightPx }
            val topTier = atOrBelow.filter { it.heightPx == highestAvailable }
            return pickBestStreamWithinTier(topTier)
        }
        val lowestAvailable = streams.minOf { it.heightPx }
        return pickBestStreamWithinTier(streams.filter { it.heightPx == lowestAvailable })
    }

    /**
     * Within the same resolution height, prioritizes highest quality:
     * 1. Higher FPS (e.g. 60fps over 30fps)
     * 2. Higher Bitrate (preserves original sharpness over compressed muxed fallbacks)
     * 3. Hardware-friendly VP9/H.264 over software AV1 when bitrate & fps match
     */
    private fun pickBestStreamWithinTier(tierStreams: List<VideoStreamOption>): VideoStreamOption {
        return tierStreams.sortedWith(
            compareByDescending<VideoStreamOption> { it.fps }
                .thenByDescending { it.bitrateKbps }
                .thenBy { it.isAv1Codec }
                .thenByDescending { it.isVp9Codec || it.isAvcH264Codec }
                .thenByDescending { it.isVideoOnly } // adaptive video streams generally carry fuller bitrates
        ).first()
    }
}
