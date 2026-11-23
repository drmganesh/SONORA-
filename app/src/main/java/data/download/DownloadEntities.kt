package data.download

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import domain.download.DownloadMediaType
import domain.download.DownloadQualityOption
import domain.download.DownloadState
import domain.download.DownloadTask
import domain.download.DownloadedTrack

@Entity(
    tableName = "download_tasks",
    indices = [
        Index(value = ["sourceKey"], unique = true),
        Index(value = ["state", "updatedAt"])
    ]
)
data class DownloadTaskEntity(
    @PrimaryKey
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
    val qualityOption: String,
    val selectedStreamId: String?,
    val formatLabel: String,
    val mimeType: String,
    val fileExtension: String,
    val bitrateKbps: Int,
    val state: String,
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
    val mediaType: String = DownloadMediaType.AUDIO.name,
    val resolutionLabel: String? = null
) {
    fun toDomain(): DownloadTask {
        val parsedState = runCatching { DownloadState.valueOf(state) }.getOrDefault(DownloadState.FAILED)
        val parsedQuality = runCatching { DownloadQualityOption.valueOf(qualityOption) }
            .getOrDefault(DownloadQualityOption.AUTO)
        val parsedMediaType = runCatching { DownloadMediaType.valueOf(mediaType) }
            .getOrDefault(DownloadMediaType.AUDIO)
        return DownloadTask(
            taskId = taskId,
            sourceKey = sourceKey,
            serviceId = serviceId,
            providerKey = providerKey,
            providerName = providerName,
            contentUrl = contentUrl,
            title = title,
            artist = artist,
            album = album,
            durationMs = durationMs,
            artworkUrl = artworkUrl,
            qualityOption = parsedQuality,
            selectedStreamId = selectedStreamId,
            formatLabel = formatLabel,
            mimeType = mimeType,
            fileExtension = fileExtension,
            bitrateKbps = bitrateKbps,
            state = parsedState,
            downloadedBytes = downloadedBytes,
            totalBytes = totalBytes,
            speedBytesPerSec = speedBytesPerSec,
            etaSeconds = etaSeconds,
            targetFolderUri = targetFolderUri,
            stagingFilePath = stagingFilePath,
            outputFileUri = outputFileUri,
            failureReason = failureReason,
            retryCount = retryCount,
            createdAt = createdAt,
            updatedAt = updatedAt,
            mediaType = parsedMediaType,
            resolutionLabel = resolutionLabel
        )
    }

    companion object {
        fun fromDomain(task: DownloadTask): DownloadTaskEntity = DownloadTaskEntity(
            taskId = task.taskId,
            sourceKey = task.sourceKey,
            serviceId = task.serviceId,
            providerKey = task.providerKey,
            providerName = task.providerName,
            contentUrl = task.contentUrl,
            title = task.title,
            artist = task.artist,
            album = task.album,
            durationMs = task.durationMs,
            artworkUrl = task.artworkUrl,
            qualityOption = task.qualityOption.name,
            selectedStreamId = task.selectedStreamId,
            formatLabel = task.formatLabel,
            mimeType = task.mimeType,
            fileExtension = task.fileExtension,
            bitrateKbps = task.bitrateKbps,
            state = task.state.name,
            downloadedBytes = task.downloadedBytes,
            totalBytes = task.totalBytes,
            speedBytesPerSec = task.speedBytesPerSec,
            etaSeconds = task.etaSeconds,
            targetFolderUri = task.targetFolderUri,
            stagingFilePath = task.stagingFilePath,
            outputFileUri = task.outputFileUri,
            failureReason = task.failureReason,
            retryCount = task.retryCount,
            createdAt = task.createdAt,
            updatedAt = task.updatedAt,
            mediaType = task.mediaType.name,
            resolutionLabel = task.resolutionLabel
        )
    }
}

@Entity(
    tableName = "downloaded_tracks",
    indices = [
        Index(value = ["sourceKey"], unique = true),
        Index(value = ["downloadedAt"])
    ]
)
data class DownloadedTrackEntity(
    @PrimaryKey
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
    val mediaType: String = DownloadMediaType.AUDIO.name,
    val resolutionLabel: String? = null
) {
    fun toDomain(isMissingFile: Boolean = false): DownloadedTrack = DownloadedTrack(
        id = id,
        sourceKey = sourceKey,
        serviceId = serviceId,
        providerKey = providerKey,
        providerName = providerName,
        contentUrl = contentUrl,
        title = title,
        artist = artist,
        album = album,
        durationMs = durationMs,
        artworkUrl = artworkUrl,
        localArtworkPath = localArtworkPath,
        localFileUri = localFileUri,
        treeFolderUri = treeFolderUri,
        displayFileName = displayFileName,
        formatLabel = formatLabel,
        mimeType = mimeType,
        bitrateKbps = bitrateKbps,
        sampleRateHz = sampleRateHz,
        fileSizeBytes = fileSizeBytes,
        downloadedAt = downloadedAt,
        isMissingFile = isMissingFile,
        mediaType = runCatching { DownloadMediaType.valueOf(mediaType) }.getOrDefault(DownloadMediaType.AUDIO),
        resolutionLabel = resolutionLabel
    )

    companion object {
        fun fromDomain(track: DownloadedTrack): DownloadedTrackEntity = DownloadedTrackEntity(
            id = track.id,
            sourceKey = track.sourceKey,
            serviceId = track.serviceId,
            providerKey = track.providerKey,
            providerName = track.providerName,
            contentUrl = track.contentUrl,
            title = track.title,
            artist = track.artist,
            album = track.album,
            durationMs = track.durationMs,
            artworkUrl = track.artworkUrl,
            localArtworkPath = track.localArtworkPath,
            localFileUri = track.localFileUri,
            treeFolderUri = track.treeFolderUri,
            displayFileName = track.displayFileName,
            formatLabel = track.formatLabel,
            mimeType = track.mimeType,
            bitrateKbps = track.bitrateKbps,
            sampleRateHz = track.sampleRateHz,
            fileSizeBytes = track.fileSizeBytes,
            downloadedAt = track.downloadedAt,
            mediaType = track.mediaType.name,
            resolutionLabel = track.resolutionLabel
        )
    }
}
