package data.download

import data.online.NetworkConnectivityMonitor
import data.online.NewPipeInitializer
import data.online.OnlineMusicRepository
import data.online.OnlineTrackMapper
import domain.download.DownloadFormatCandidate
import domain.download.DownloadQualityOption
import domain.download.DownloadabilityResult
import domain.online.AudioQualityPreference
import domain.online.OnlineAudioStreamOption
import domain.online.OnlineItemKind
import domain.online.OnlineResult
import domain.online.OnlineTrack
import domain.online.ResolvedOnlineStreamResult
import domain.online.StreamDeliveryType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

class DownloadStreamSelector(
    private val onlineRepository: OnlineMusicRepository,
    private val connectivityMonitor: NetworkConnectivityMonitor,
    private val storageHelper: DownloadStorageHelper,
    private val okHttpClient: OkHttpClient = NewPipeInitializer.getDownloader().okHttpClient
) {

    fun checkPreconditions(
        track: OnlineTrack,
        existingDownloaded: DownloadedTrackEntity?,
        existingTask: DownloadTaskEntity?
    ): DownloadabilityResult {
        if (track.kind != OnlineItemKind.TRACK) {
            return DownloadabilityResult.Unsupported(
                "Open this ${track.kind.name.lowercase()} and pick individual tracks to download."
            )
        }
        if (track.isLive) {
            return DownloadabilityResult.Unsupported(
                "Live broadcasts and streams cannot be downloaded offline."
            )
        }
        if (track.contentUrl.isBlank()) {
            return DownloadabilityResult.Unsupported(
                "This track does not provide a valid online stream link."
            )
        }
        if (existingDownloaded != null && storageHelper.doesDownloadedFileExist(existingDownloaded.localFileUri)) {
            return DownloadabilityResult.AlreadyDownloaded(
                trackTitle = existingDownloaded.title,
                trackId = existingDownloaded.id
            )
        }
        if (existingTask != null &&
            (existingTask.state in listOf("WAITING", "RESOLVING", "DOWNLOADING", "PAUSED"))
        ) {
            val state = runCatching { domain.download.DownloadState.valueOf(existingTask.state) }
                .getOrDefault(domain.download.DownloadState.WAITING)
            return DownloadabilityResult.AlreadyInQueue(
                trackTitle = existingTask.title,
                taskId = existingTask.taskId,
                state = state
            )
        }
        return DownloadabilityResult.Downloadable(track)
    }

    suspend fun extractDownloadableFormats(
        track: OnlineTrack,
        forceRefresh: Boolean = false
    ): Result<List<DownloadFormatCandidate>> = withContext(Dispatchers.IO) {
        val song = OnlineTrackMapper.toUnifiedSong(track)
        when (val result = onlineRepository.resolvePlayableStream(
            song = song,
            qualityPreference = AudioQualityPreference.HIGHEST,
            forceRefresh = forceRefresh
        )) {
            is OnlineResult.Error -> Result.failure(IllegalStateException(result.message))
            is OnlineResult.Success -> buildCandidatesFromResolved(result.value)
        }
    }

    fun buildCandidatesFromResolved(
        resolved: ResolvedOnlineStreamResult
    ): Result<List<DownloadFormatCandidate>> {
        if (resolved.isLive) {
            return Result.failure(IllegalStateException("Live streams cannot be downloaded."))
        }
        val downloadableStreams = resolved.availableStreams
            .filter { isStreamDownloadable(it) }
            .distinctBy { "${it.id}|${it.mimeType}|${it.averageBitrateKbps}" }

        if (downloadableStreams.isEmpty()) {
            val hasSegmentedOnly = resolved.availableStreams.any {
                it.deliveryType == StreamDeliveryType.DASH || it.deliveryType == StreamDeliveryType.HLS
            }
            val msg = if (hasSegmentedOnly) {
                "This stream uses segmented or protected playback (HLS/DASH) and cannot be saved directly offline."
            } else {
                "No downloadable audio stream was found for this track."
            }
            return Result.failure(IllegalStateException(msg))
        }

        val candidates = downloadableStreams
            .sortedWith(
                compareByDescending<OnlineAudioStreamOption> { it.isAudioOnly }
                    .thenByDescending { it.isLossless }
                    .thenByDescending { it.averageBitrateKbps }
            )
            .map { toFormatCandidate(it) }

        return Result.success(candidates)
    }

    suspend fun enrichContentLengthsIfUnknown(
        candidates: List<DownloadFormatCandidate>,
        headers: Map<String, String> = emptyMap(),
        maxProbeCount: Int = 3
    ): List<DownloadFormatCandidate> = withContext(Dispatchers.IO) {
        val probeClient = okHttpClient.newBuilder()
            .connectTimeout(4, TimeUnit.SECONDS)
            .readTimeout(4, TimeUnit.SECONDS)
            .build()
        var probed = 0
        candidates.map { candidate ->
            if (candidate.contentLengthBytes > 0L || probed >= maxProbeCount) {
                candidate
            } else {
                probed++
                val contentLength = probeContentLength(probeClient, candidate.streamUrl, headers)
                if (contentLength > 0L) candidate.copy(contentLengthBytes = contentLength) else candidate
            }
        }
    }

    fun buildPresetRecommendations(
        candidates: List<DownloadFormatCandidate>,
        isMeteredNetwork: Boolean = connectivityMonitor.isMeteredConnection.value
    ): Map<DownloadQualityOption, DownloadFormatCandidate> {
        if (candidates.isEmpty()) return emptyMap()
        return DownloadQualityOption.entries.associateWith { option ->
            selectCandidateForPreset(candidates, option, isMeteredNetwork)
        }
    }

    fun selectCandidateForPreset(
        candidates: List<DownloadFormatCandidate>,
        option: DownloadQualityOption,
        isMeteredNetwork: Boolean = connectivityMonitor.isMeteredConnection.value
    ): DownloadFormatCandidate {
        val audioOnly = candidates.filter { it.isAudioOnly }.ifEmpty { candidates }
        return when (option) {
            DownloadQualityOption.AUTO -> {
                if (isMeteredNetwork) {
                    audioOnly.filter { it.averageBitrateForSort in 64..160 }
                        .maxByOrNull { it.averageBitrateForSort }
                        ?: audioOnly.minByOrNull { kotlin.math.abs(it.averageBitrateForSort - 128) }
                        ?: audioOnly.first()
                } else {
                    audioOnly.filter { it.averageBitrateForSort in 128..256 }
                        .maxByOrNull { it.averageBitrateForSort }
                        ?: audioOnly.maxByOrNull { it.averageBitrateForSort }
                        ?: audioOnly.first()
                }
            }

            DownloadQualityOption.HIGH_QUALITY -> {
                audioOnly.filter { it.averageBitrateForSort in 144..256 }
                    .maxByOrNull { it.averageBitrateForSort }
                    ?: audioOnly.maxByOrNull { it.averageBitrateForSort }
                    ?: audioOnly.first()
            }

            DownloadQualityOption.BEST_AVAILABLE -> {
                audioOnly.maxWithOrNull(
                    compareBy<DownloadFormatCandidate> { it.isLossless }
                        .thenBy { it.bitrateKbps }
                        .thenBy { if (it.containerExtension == "m4a" || it.containerExtension == "webm") 1 else 0 }
                ) ?: candidates.first()
            }

            DownloadQualityOption.ORIGINAL_FORMAT -> {
                // Preserve native/original source format: prefer Opus/WebM or MP3/native stream as supplied
                audioOnly.firstOrNull {
                    it.codecLabel.contains("OPUS", ignoreCase = true) ||
                        it.codecLabel.contains("FLAC", ignoreCase = true) ||
                        it.codecLabel.contains("MP3", ignoreCase = true)
                } ?: audioOnly.first()
            }
        }
    }

    private fun isStreamDownloadable(opt: OnlineAudioStreamOption): Boolean {
        if (opt.url.isBlank()) return false
        if (opt.deliveryType != StreamDeliveryType.PROGRESSIVE_HTTP) return false
        val lowerMime = opt.mimeType.lowercase()
        val lowerCodec = opt.codec.lowercase()
        if ("drm" in lowerMime || "drm" in lowerCodec || "widevine" in lowerCodec || "fairplay" in lowerCodec) {
            return false
        }
        return true
    }

    private fun toFormatCandidate(opt: OnlineAudioStreamOption): DownloadFormatCandidate {
        val lowerMime = opt.mimeType.lowercase()
        val lowerCodec = opt.codec.lowercase()
        val lowerFormat = opt.formatName.lowercase()

        val isKnownLossy = "opus" in lowerCodec || "opus" in lowerMime || "webm" in lowerMime || "webm" in lowerFormat ||
            "aac" in lowerCodec || "mp4a" in lowerCodec || "m4a" in lowerFormat || "mp4" in lowerMime ||
            "mp3" in lowerMime || "mpeg" in lowerMime || "mp3" in lowerCodec ||
            "vorbis" in lowerCodec || "ogg" in lowerMime
        val isGenuineLossless = !isKnownLossy && (
            "flac" in lowerMime || "flac" in lowerCodec ||
                "alac" in lowerMime || "alac" in lowerCodec ||
                "wav" in lowerMime || "pcm" in lowerCodec ||
                opt.isLossless
            )

        val (codecLabel, ext) = when {
            "opus" in lowerCodec || "opus" in lowerMime || "webm" in lowerMime || "webm" in lowerFormat -> "OPUS" to "webm"
            "mp3" in lowerMime || "mpeg" in lowerMime || "mp3" in lowerCodec -> "MP3" to "mp3"
            "vorbis" in lowerCodec || "ogg" in lowerMime -> "OGG VORBIS" to "ogg"
            "aac" in lowerCodec || "mp4a" in lowerCodec || "m4a" in lowerFormat || "mp4" in lowerMime -> "AAC / M4A" to "m4a"
            isGenuineLossless || "flac" in lowerMime || "flac" in lowerCodec -> "FLAC" to "flac"
            !opt.isAudioOnly -> "MP4 AUDIO" to "mp4"
            else -> opt.codec.ifBlank { opt.formatName.ifBlank { "AUDIO" } }.uppercase() to "m4a"
        }

        return DownloadFormatCandidate(
            streamId = opt.id,
            codecLabel = codecLabel,
            containerExtension = ext,
            mimeType = opt.mimeType.ifBlank { storageHelper.mimeTypeForExtension(ext) },
            bitrateKbps = opt.averageBitrateKbps.coerceAtLeast(0),
            contentLengthBytes = -1L,
            streamUrl = opt.url,
            isLossless = isGenuineLossless,
            isAudioOnly = opt.isAudioOnly,
            deliveryType = opt.deliveryType
        )
    }

    private fun probeContentLength(
        client: OkHttpClient,
        url: String,
        headers: Map<String, String>
    ): Long {
        return runCatching {
            val builder = Request.Builder().url(url).head()
            headers.forEach { (k, v) -> builder.header(k, v) }
            client.newCall(builder.build()).execute().use { resp ->
                if (!resp.isSuccessful) return -1L
                resp.header("Content-Length")?.toLongOrNull()?.takeIf { it > 0L } ?: -1L
            }
        }.getOrDefault(-1L)
    }

    private val DownloadFormatCandidate.averageBitrateForSort: Int
        get() = bitrateKbps.coerceAtLeast(0)
}
