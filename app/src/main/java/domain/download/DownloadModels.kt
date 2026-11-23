package domain.download

import android.net.Uri
import com.example.model.Song
import domain.online.OnlineTrack
import domain.online.StreamDeliveryType
import java.util.Locale

enum class DownloadMediaType(val label: String) {
    AUDIO("Music"),
    VIDEO("Video")
}

enum class DownloadState(val displayLabel: String) {
    WAITING("Waiting"),
    RESOLVING("Resolving"),
    DOWNLOADING("Downloading"),
    PAUSED("Paused"),
    COMPLETED("Completed"),
    FAILED("Failed"),
    CANCELLED("Cancelled");

    val isActive: Boolean
        get() = this == WAITING || this == RESOLVING || this == DOWNLOADING

    val isTerminal: Boolean
        get() = this == COMPLETED || this == FAILED || this == CANCELLED
}

enum class DownloadQualityOption(
    val label: String,
    val description: String
) {
    AUTO(
        label = "Auto",
        description = "Adapts to Wi-Fi or mobile data while keeping crisp audio"
    ),
    HIGH_QUALITY(
        label = "High Quality",
        description = "Clear 160–256 kbps audio stream for offline listening"
    ),
    BEST_AVAILABLE(
        label = "Best Available",
        description = "Highest bitrate audio stream offered by the source"
    ),
    ORIGINAL_FORMAT(
        label = "Original Available Format",
        description = "Preserves the native source container and codec without transcoding"
    )
}

enum class DownloadSortOption(val label: String) {
    RECENTLY_DOWNLOADED("Recently Downloaded"),
    ARTIST_ASC("Artist"),
    ALBUM_ASC("Album"),
    TITLE_ASC("Song Name")
}

data class DownloadFormatCandidate(
    val streamId: String,
    val codecLabel: String,
    val containerExtension: String,
    val mimeType: String,
    val bitrateKbps: Int,
    val contentLengthBytes: Long,
    val streamUrl: String,
    val isLossless: Boolean,
    val isAudioOnly: Boolean,
    val deliveryType: StreamDeliveryType
) {
    val summaryLabel: String
        get() = buildString {
            append(codecLabel)
            if (bitrateKbps > 0) {
                append(" • ")
                append(bitrateKbps)
                append(" kbps")
            }
            if (contentLengthBytes > 0L) {
                append(" • ")
                append(formatByteCount(contentLengthBytes))
            }
        }
}

data class DownloadQualitySheetData(
    val track: OnlineTrack,
    val isResolvingFormats: Boolean = false,
    val candidates: List<DownloadFormatCandidate> = emptyList(),
    val recommendedByPreset: Map<DownloadQualityOption, DownloadFormatCandidate> = emptyMap(),
    val selectedPreset: DownloadQualityOption = DownloadQualityOption.AUTO,
    val selectedManualStreamId: String? = null,
    val destinationFolderLabel: String = "",
    val errorMessage: String? = null
)

sealed class DownloadabilityResult {
    data class Downloadable(val track: OnlineTrack) : DownloadabilityResult()
    data class AlreadyDownloaded(val trackTitle: String, val trackId: Long) : DownloadabilityResult()
    data class AlreadyInQueue(val trackTitle: String, val taskId: String, val state: DownloadState) : DownloadabilityResult()
    data class Unsupported(val reason: String) : DownloadabilityResult()
}

data class DownloadTask(
    val taskId: String,
    val sourceKey: String,
    val serviceId: Int,
    val providerKey: String,
    val providerName: String,
    val contentUrl: String,
    val title: String,
    val artist: String,
    val album: String,
    val durationMs: Long,
    val artworkUrl: String?,
    val qualityOption: DownloadQualityOption,
    val selectedStreamId: String?,
    val formatLabel: String,
    val mimeType: String,
    val fileExtension: String,
    val bitrateKbps: Int,
    val state: DownloadState,
    val downloadedBytes: Long,
    val totalBytes: Long,
    val speedBytesPerSec: Long,
    val etaSeconds: Long,
    val targetFolderUri: String,
    val stagingFilePath: String?,
    val outputFileUri: String?,
    val failureReason: String?,
    val retryCount: Int,
    val createdAt: Long,
    val updatedAt: Long,
    val mediaType: DownloadMediaType = DownloadMediaType.AUDIO,
    val resolutionLabel: String? = null
) {
    val progressFraction: Float
        get() = if (totalBytes > 0L) {
            (downloadedBytes.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f)
        } else {
            if (state == DownloadState.COMPLETED) 1f else 0f
        }
}

data class DownloadedTrack(
    val id: Long,
    val sourceKey: String,
    val serviceId: Int,
    val providerKey: String,
    val providerName: String,
    val contentUrl: String,
    val title: String,
    val artist: String,
    val album: String,
    val durationMs: Long,
    val artworkUrl: String?,
    val localArtworkPath: String?,
    val localFileUri: String,
    val treeFolderUri: String,
    val displayFileName: String,
    val formatLabel: String,
    val mimeType: String,
    val bitrateKbps: Int,
    val sampleRateHz: Int,
    val fileSizeBytes: Long,
    val downloadedAt: Long,
    val isMissingFile: Boolean = false,
    val mediaType: DownloadMediaType = DownloadMediaType.AUDIO,
    val resolutionLabel: String? = null
) {
    fun toSong(): Song {
        val artworkUriString = localArtworkPath?.takeIf { it.isNotBlank() }?.let { "file://$it" } ?: artworkUrl
        return Song(
            id = id,
            title = title,
            artist = artist,
            album = album.ifBlank { "Downloads" },
            albumId = kotlin.math.abs(id),
            durationMs = durationMs,
            uri = Uri.parse(localFileUri),
            dataPath = localFileUri,
            mimeType = mimeType,
            sizeBytes = fileSizeBytes,
            bitrateKbps = bitrateKbps.coerceAtLeast(0),
            sampleRateHz = sampleRateHz.coerceAtLeast(0),
            bitDepth = 16,
            dateAdded = downloadedAt,
            isFavorite = false,
            playCount = 0,
            lastPlayedTimestamp = 0L,
            isDemoTrack = false,
            isOnline = false,
            sourceType = "DOWNLOADED",
            serviceId = serviceId,
            providerKey = providerKey,
            providerName = providerName,
            contentUrl = contentUrl,
            artworkUrl = artworkUriString,
            streamCodec = formatLabel,
            streamQualityLabel = if (bitrateKbps > 0) "$formatLabel • $bitrateKbps kbps" else formatLabel,
            isBitrateKnown = bitrateKbps > 0,
            isSampleRateKnown = sampleRateHz > 0
        )
    }
}

data class DownloadStorageSummary(
    val trackCount: Int = 0,
    val totalBytes: Long = 0L,
    val folderLabel: String = "Not configured",
    val hasFolderAccess: Boolean = false
)

fun formatByteCount(bytes: Long): String {
    if (bytes <= 0L) return ""
    val kb = 1024.0
    val mb = kb * 1024.0
    val gb = mb * 1024.0
    return when {
        bytes >= gb -> String.format(Locale.US, "%.2f GB", bytes / gb)
        bytes >= mb -> String.format(Locale.US, "%.1f MB", bytes / mb)
        bytes >= kb -> String.format(Locale.US, "%.0f KB", bytes / kb)
        else -> "$bytes B"
    }
}

fun formatDownloadSpeed(bytesPerSec: Long): String {
    if (bytesPerSec <= 0L) return ""
    return "${formatByteCount(bytesPerSec)}/s"
}

fun formatEtaDuration(seconds: Long): String {
    if (seconds <= 0L) return ""
    val mins = seconds / 60L
    val secs = seconds % 60L
    return when {
        mins >= 60L -> "${mins / 60L}h ${mins % 60L}m left"
        mins > 0L -> "${mins}m ${secs}s left"
        else -> "${secs}s left"
    }
}
