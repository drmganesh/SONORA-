package data.download

import domain.download.DownloadFormatCandidate
import domain.download.DownloadMediaType
import domain.online.StreamDeliveryType
import domain.video.OnlineVideoItem
import domain.video.ResolvedVideoDetails
import domain.video.VideoAudioOption
import domain.video.VideoStreamOption
import java.util.Locale

data class VideoDownloadOption(
    val mediaType: DownloadMediaType,
    val candidate: DownloadFormatCandidate,
    val resolutionLabel: String,
    val summaryLabel: String
)

sealed interface VideoDownloadEligibility {
    data class Permitted(
        val videoOptions: List<VideoDownloadOption>,
        val audioOptions: List<VideoDownloadOption>
    ) : VideoDownloadEligibility

    data class NotPermitted(val reason: String) : VideoDownloadEligibility
}

/**
 * Policy- & DRM-respecting download eligibility evaluator for Online Video.
 *
 * - Never bypasses DRM, Widevine, FairPlay, or encrypted segmented streams.
 * - Rejects live streams and sources that do not offer a direct progressive downloadable stream.
 * - Exposes only genuinely available Video (muxed MP4/WebM) and Audio-Only formats.
 */
object VideoDownloadEligibilityEvaluator {

    fun evaluate(
        video: OnlineVideoItem,
        details: ResolvedVideoDetails?
    ): VideoDownloadEligibility {
        if (video.isLive) {
            return VideoDownloadEligibility.NotPermitted("Live broadcasts cannot be downloaded.")
        }
        if (video.contentUrl.isBlank()) {
            return VideoDownloadEligibility.NotPermitted("This video does not expose a downloadable source URL.")
        }
        if (details == null) {
            return VideoDownloadEligibility.NotPermitted("Video stream details are still loading.")
        }

        val videoOptions = details.videoStreams
            .asSequence()
            .filter { isPermittedVideoStream(it) }
            .distinctBy { "${it.heightPx}|${it.mimeType}" }
            .sortedByDescending { it.heightPx }
            .map { stream ->
                val ext = extensionForVideoMime(stream.mimeType)
                val estimatedBytes = estimateBytes(stream.bitrateKbps, video.durationSeconds)
                val resLabel = stream.resolutionLabel.ifBlank { "${stream.heightPx}p" }
                val candidate = DownloadFormatCandidate(
                    streamId = stream.id,
                    codecLabel = "${resLabel} ${ext.uppercase(Locale.US)}",
                    containerExtension = ext,
                    mimeType = stream.mimeType.ifBlank { if (ext == "webm") "video/webm" else "video/mp4" },
                    bitrateKbps = stream.bitrateKbps.coerceAtLeast(0),
                    contentLengthBytes = estimatedBytes,
                    streamUrl = stream.url,
                    isLossless = false,
                    isAudioOnly = false,
                    deliveryType = stream.deliveryType
                )
                VideoDownloadOption(
                    mediaType = DownloadMediaType.VIDEO,
                    candidate = candidate,
                    resolutionLabel = resLabel,
                    summaryLabel = buildString {
                        append(resLabel)
                        if (stream.fps >= 50) append(" ${stream.fps}fps")
                        append(" • ${ext.uppercase(Locale.US)}")
                        if (estimatedBytes > 0L) {
                            append(" • ~${domain.download.formatByteCount(estimatedBytes)}")
                        }
                    }
                )
            }
            .toList()

        val audioOptions = details.audioStreams
            .asSequence()
            .filter { isPermittedAudioStream(it) }
            .distinctBy { "${it.bitrateKbps}|${it.mimeType}" }
            .sortedByDescending { it.bitrateKbps }
            .map { audio ->
                val ext = extensionForAudioMime(audio.mimeType)
                val estimatedBytes = estimateBytes(audio.bitrateKbps, video.durationSeconds)
                val codecLabel = when (ext) {
                    "webm" -> "OPUS"
                    "mp3" -> "MP3"
                    else -> "AAC / M4A"
                }
                val candidate = DownloadFormatCandidate(
                    streamId = audio.id,
                    codecLabel = codecLabel,
                    containerExtension = ext,
                    mimeType = audio.mimeType.ifBlank { if (ext == "webm") "audio/webm" else "audio/mp4" },
                    bitrateKbps = audio.bitrateKbps.coerceAtLeast(0),
                    contentLengthBytes = estimatedBytes,
                    streamUrl = audio.url,
                    isLossless = false,
                    isAudioOnly = true,
                    deliveryType = StreamDeliveryType.PROGRESSIVE_HTTP
                )
                VideoDownloadOption(
                    mediaType = DownloadMediaType.AUDIO,
                    candidate = candidate,
                    resolutionLabel = if (audio.bitrateKbps > 0) "${audio.bitrateKbps} kbps" else codecLabel,
                    summaryLabel = buildString {
                        append(codecLabel)
                        if (audio.bitrateKbps > 0) append(" • ${audio.bitrateKbps} kbps")
                        if (estimatedBytes > 0L) {
                            append(" • ~${domain.download.formatByteCount(estimatedBytes)}")
                        }
                    }
                )
            }
            .toList()

        if (videoOptions.isEmpty() && audioOptions.isEmpty()) {
            return VideoDownloadEligibility.NotPermitted(
                "This source only offers protected or segmented streams and does not permit direct offline downloads."
            )
        }

        return VideoDownloadEligibility.Permitted(
            videoOptions = videoOptions,
            audioOptions = audioOptions
        )
    }

    private fun isPermittedVideoStream(stream: VideoStreamOption): Boolean {
        if (stream.url.isBlank()) return false
        if (stream.isVideoOnly) return false // Require direct muxed video+audio progressive stream for clean standalone playback
        if (stream.deliveryType != StreamDeliveryType.PROGRESSIVE_HTTP) return false
        val lower = "${stream.mimeType} ${stream.formatLabel} ${stream.url}".lowercase(Locale.US)
        if ("drm" in lower || "widevine" in lower || "fairplay" in lower || "playready" in lower) {
            return false
        }
        return true
    }

    private fun isPermittedAudioStream(audio: VideoAudioOption): Boolean {
        if (audio.url.isBlank()) return false
        val lower = "${audio.mimeType} ${audio.formatLabel} ${audio.url}".lowercase(Locale.US)
        if ("drm" in lower || "widevine" in lower || "fairplay" in lower || "playready" in lower) {
            return false
        }
        return true
    }

    private fun extensionForVideoMime(mime: String): String {
        val lower = mime.lowercase(Locale.US)
        return when {
            "webm" in lower -> "webm"
            else -> "mp4"
        }
    }

    private fun extensionForAudioMime(mime: String): String {
        val lower = mime.lowercase(Locale.US)
        return when {
            "webm" in lower || "opus" in lower -> "webm"
            "mpeg" in lower || "mp3" in lower -> "mp3"
            else -> "m4a"
        }
    }

    private fun estimateBytes(bitrateKbps: Int, durationSeconds: Long): Long {
        if (bitrateKbps <= 0 || durationSeconds <= 0L) return -1L
        return ((bitrateKbps.toLong() * 1000L) / 8L) * durationSeconds
    }
}
