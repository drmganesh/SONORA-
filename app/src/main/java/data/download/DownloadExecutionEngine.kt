package data.download

import android.util.Log
import com.example.data.SonoraDao
import data.online.NewPipeInitializer
import data.online.OnlineMusicRepository
import data.online.OnlineTrackMapper
import data.video.OnlineVideoDataSource
import domain.download.DownloadFormatCandidate
import domain.download.DownloadMediaType
import domain.download.DownloadQualityOption
import domain.download.DownloadState
import domain.download.DownloadTask
import domain.download.formatByteCount
import domain.online.AudioQualityPreference
import domain.online.OnlineItemKind
import domain.online.OnlineResult
import domain.online.OnlineTrack
import domain.video.OnlineVideoItem
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

enum class DownloadControlSignal {
    PAUSE,
    CANCEL
}

sealed class DownloadExecutionOutcome {
    data object Completed : DownloadExecutionOutcome()
    data object Paused : DownloadExecutionOutcome()
    data object Cancelled : DownloadExecutionOutcome()
    data class Failed(val reason: String, val isRetryable: Boolean) : DownloadExecutionOutcome()
}

class DownloadExecutionEngine(
    private val dao: SonoraDao,
    private val onlineRepository: OnlineMusicRepository,
    private val storageHelper: DownloadStorageHelper,
    private val streamSelector: DownloadStreamSelector,
    private val okHttpClient: OkHttpClient = NewPipeInitializer.getDownloader().okHttpClient,
    private val requirePersistedSafPermission: Boolean = true,
    private val streamResolverOverride: (suspend (DownloadTask, Boolean) -> OnlineResult<domain.online.ResolvedOnlineStreamResult>)? = null,
    private val videoDataSource: OnlineVideoDataSource? = null
) {

    companion object {
        private const val TAG = "DownloadExecutionEngine"
        private const val PROGRESS_EMIT_INTERVAL_MS = 250L
        private const val COPY_BUFFER_SIZE = 16 * 1024
        private const val MAX_URL_REFRESH_ATTEMPTS = 1
    }

    private val controlSignals = ConcurrentHashMap<String, DownloadControlSignal>()

    fun signalPause(taskId: String) {
        controlSignals[taskId] = DownloadControlSignal.PAUSE
    }

    fun signalCancel(taskId: String) {
        controlSignals[taskId] = DownloadControlSignal.CANCEL
    }

    fun clearSignal(taskId: String) {
        controlSignals.remove(taskId)
    }

    suspend fun executeTask(
        taskId: String,
        onProgress: suspend (downloadedBytes: Long, totalBytes: Long, title: String) -> Unit = { _, _, _ -> }
    ): DownloadExecutionOutcome = withContext(Dispatchers.IO) {
        val initialEntity = dao.getDownloadTaskById(taskId)
            ?: return@withContext DownloadExecutionOutcome.Failed("Download task was removed.", isRetryable = false)

        val initialSignal = controlSignals.remove(taskId)
        if (initialSignal == DownloadControlSignal.CANCEL || initialEntity.state == DownloadState.CANCELLED.name) {
            storageHelper.deleteStagingFile(initialEntity.stagingFilePath)
            dao.updateDownloadState(taskId, DownloadState.CANCELLED.name, null)
            return@withContext DownloadExecutionOutcome.Cancelled
        }
        if (initialSignal == DownloadControlSignal.PAUSE || initialEntity.state == DownloadState.PAUSED.name) {
            dao.updateDownloadState(taskId, DownloadState.PAUSED.name, null)
            return@withContext DownloadExecutionOutcome.Paused
        }

        // Verify SAF destination permission upfront before hitting network
        if (!storageHelper.isTreeUriAccessible(initialEntity.targetFolderUri, requirePersistedRecord = requirePersistedSafPermission)) {
            val reason = "Download folder permission is missing or expired. Please choose a download folder in Settings."
            dao.updateDownloadState(taskId, DownloadState.FAILED.name, reason)
            return@withContext DownloadExecutionOutcome.Failed(reason, isRetryable = false)
        }

        var workingTask = initialEntity.toDomain()
        var refreshAttempts = 0

        while (true) {
            currentCoroutineContext().ensureActive()

            if (controlSignals[taskId] == DownloadControlSignal.CANCEL) {
                controlSignals.remove(taskId)
                storageHelper.deleteStagingFile(workingTask.stagingFilePath)
                dao.updateDownloadState(taskId, DownloadState.CANCELLED.name, null)
                return@withContext DownloadExecutionOutcome.Cancelled
            }
            if (controlSignals[taskId] == DownloadControlSignal.PAUSE) {
                controlSignals.remove(taskId)
                dao.updateDownloadState(taskId, DownloadState.PAUSED.name, null)
                return@withContext DownloadExecutionOutcome.Paused
            }

            // 1. Resolve fresh stream URL via OnlineMusicRepository / NewPipeExtractor
            dao.upsertDownloadTask(
                DownloadTaskEntity.fromDomain(
                    workingTask.copy(
                        state = DownloadState.RESOLVING,
                        failureReason = null,
                        updatedAt = System.currentTimeMillis()
                    )
                )
            )

            val selectedCandidate: DownloadFormatCandidate
            val requestHeaders: Map<String, String>
            val resolvedTitle: String
            val resolvedArtist: String
            val resolvedDurationMs: Long
            val resolvedArtworkUrl: String?
            var resolvedResolutionLabel: String? = workingTask.resolutionLabel

            if (workingTask.mediaType == DownloadMediaType.VIDEO && streamResolverOverride == null) {
                val ds = videoDataSource
                if (ds == null) {
                    val msg = "Video download source is unavailable."
                    dao.updateDownloadState(taskId, DownloadState.FAILED.name, msg)
                    return@withContext DownloadExecutionOutcome.Failed(msg, isRetryable = false)
                }
                val seedVideo = OnlineVideoItem(
                    videoId = OnlineVideoDataSource.extractVideoIdFromUrl(workingTask.contentUrl),
                    contentUrl = workingTask.contentUrl,
                    title = workingTask.title,
                    channelName = workingTask.artist,
                    durationSeconds = workingTask.durationMs / 1000L,
                    thumbnailUrl = workingTask.artworkUrl
                )
                val videoResult = ds.resolveVideoDetails(seedVideo, forceRefresh = refreshAttempts > 0)
                if (videoResult is OnlineResult.Error) {
                    val msg = videoResult.message
                    dao.updateDownloadState(taskId, DownloadState.FAILED.name, msg)
                    return@withContext DownloadExecutionOutcome.Failed(msg, isRetryable = videoResult.isRetryable)
                }
                val details = (videoResult as OnlineResult.Success).value
                val eligibility = VideoDownloadEligibilityEvaluator.evaluate(details.video, details)
                if (eligibility !is VideoDownloadEligibility.Permitted || eligibility.videoOptions.isEmpty()) {
                    val msg = (eligibility as? VideoDownloadEligibility.NotPermitted)?.reason
                        ?: "No downloadable progressive video format available for this source."
                    dao.updateDownloadState(taskId, DownloadState.FAILED.name, msg)
                    return@withContext DownloadExecutionOutcome.Failed(msg, isRetryable = false)
                }
                val matchedOption = workingTask.selectedStreamId?.let { id ->
                    eligibility.videoOptions.firstOrNull { it.candidate.streamId == id }
                } ?: workingTask.resolutionLabel?.let { resLabel ->
                    eligibility.videoOptions.firstOrNull { it.resolutionLabel.equals(resLabel, ignoreCase = true) }
                } ?: eligibility.videoOptions.first()

                selectedCandidate = matchedOption.candidate
                resolvedResolutionLabel = matchedOption.resolutionLabel
                requestHeaders = details.videoStreams.firstOrNull { it.url == matchedOption.candidate.streamUrl }?.headers.orEmpty()
                resolvedTitle = details.video.title
                resolvedArtist = details.video.channelName
                resolvedDurationMs = details.video.durationMs
                resolvedArtworkUrl = details.video.thumbnailUrl ?: workingTask.artworkUrl
            } else {
                val trackSeed = OnlineTrack(
                    stableId = OnlineTrackMapper.computeStableId(workingTask.providerKey, workingTask.contentUrl),
                    serviceId = workingTask.serviceId,
                    providerKey = workingTask.providerKey,
                    providerName = workingTask.providerName,
                    contentUrl = workingTask.contentUrl,
                    title = workingTask.title,
                    artist = workingTask.artist,
                    album = workingTask.album,
                    durationMs = workingTask.durationMs,
                    artworkUrl = workingTask.artworkUrl,
                    kind = OnlineItemKind.TRACK
                )

                val resolvedResult = streamResolverOverride?.invoke(workingTask, refreshAttempts > 0)
                    ?: onlineRepository.resolvePlayableStream(
                        song = OnlineTrackMapper.toUnifiedSong(trackSeed),
                        qualityPreference = AudioQualityPreference.HIGHEST,
                        forceRefresh = refreshAttempts > 0
                    )

                if (resolvedResult is OnlineResult.Error) {
                    val msg = resolvedResult.message
                    dao.updateDownloadState(taskId, DownloadState.FAILED.name, msg)
                    return@withContext DownloadExecutionOutcome.Failed(msg, isRetryable = resolvedResult.isRetryable)
                }

                val resolved = (resolvedResult as OnlineResult.Success).value
                val candidatesResult = streamSelector.buildCandidatesFromResolved(resolved)
                if (candidatesResult.isFailure) {
                    val msg = candidatesResult.exceptionOrNull()?.message ?: "No downloadable audio format available."
                    dao.updateDownloadState(taskId, DownloadState.FAILED.name, msg)
                    return@withContext DownloadExecutionOutcome.Failed(msg, isRetryable = false)
                }

                val candidates = candidatesResult.getOrThrow()
                selectedCandidate = workingTask.selectedStreamId?.let { id ->
                    candidates.firstOrNull { it.streamId == id }
                } ?: streamSelector.selectCandidateForPreset(candidates, workingTask.qualityOption)
                requestHeaders = resolved.selectedStream.headers
                resolvedTitle = resolved.title
                resolvedArtist = resolved.artist
                resolvedDurationMs = resolved.durationMs
                resolvedArtworkUrl = resolved.artworkUrl
            }

            val stagingFile = workingTask.stagingFilePath
                ?.takeIf { it.isNotBlank() }
                ?.let { File(it) }
                ?: storageHelper.getStagingFile(taskId, selectedCandidate.containerExtension)
            stagingFile.parentFile?.mkdirs()

            val existingStagingBytes = if (stagingFile.exists()) stagingFile.length().coerceAtLeast(0L) else 0L

            try {
                storageHelper.checkAvailableStorage(
                    expectedTotalBytes = workingTask.totalBytes.takeIf { it > 0 } ?: selectedCandidate.contentLengthBytes,
                    alreadyDownloadedBytes = existingStagingBytes
                )
            } catch (e: InsufficientStorageException) {
                val msg = e.message ?: "Insufficient storage space."
                dao.updateDownloadState(taskId, DownloadState.FAILED.name, msg)
                return@withContext DownloadExecutionOutcome.Failed(msg, isRetryable = false)
            }

            workingTask = workingTask.copy(
                selectedStreamId = selectedCandidate.streamId,
                formatLabel = selectedCandidate.codecLabel,
                mimeType = selectedCandidate.mimeType,
                fileExtension = selectedCandidate.containerExtension,
                bitrateKbps = selectedCandidate.bitrateKbps,
                resolutionLabel = resolvedResolutionLabel,
                stagingFilePath = stagingFile.absolutePath,
                state = DownloadState.DOWNLOADING,
                updatedAt = System.currentTimeMillis()
            )
            dao.upsertDownloadTask(DownloadTaskEntity.fromDomain(workingTask))

            val transferClient = okHttpClient.newBuilder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(25, TimeUnit.SECONDS)
                .build()

            val requestBuilder = Request.Builder().url(selectedCandidate.streamUrl)
            requestHeaders.forEach { entry ->
                requestBuilder.header(entry.key, entry.value)
            }
            if (existingStagingBytes > 0L) {
                requestBuilder.header("Range", "bytes=$existingStagingBytes-")
            }

            try {
                transferClient.newCall(requestBuilder.build()).execute().use { response ->
                    val code = response.code
                    if ((code == 403 || code == 410) && refreshAttempts < MAX_URL_REFRESH_ATTEMPTS) {
                        Log.w(TAG, "Stream expired (HTTP $code) for $taskId, invalidating cache and refreshing URL")
                        onlineRepository.invalidateStreamCache(workingTask.contentUrl)
                        videoDataSource?.clearDetailsCache()
                        refreshAttempts++
                        return@use
                    }
                    if (code == 429) {
                        val msg = "Source rate-limited download requests (HTTP 429). Retry shortly."
                        dao.updateDownloadState(taskId, DownloadState.FAILED.name, msg)
                        return@withContext DownloadExecutionOutcome.Failed(msg, isRetryable = true)
                    }
                    if (code == 416 && existingStagingBytes > 0L) {
                        // Partial staging mismatch with remote resource; restart staging cleanly
                        storageHelper.deleteStagingFile(stagingFile.absolutePath)
                        refreshAttempts++
                        return@use
                    }
                    if (!response.isSuccessful) {
                        val msg = "Download failed with HTTP $code."
                        dao.updateDownloadState(taskId, DownloadState.FAILED.name, msg)
                        return@withContext DownloadExecutionOutcome.Failed(msg, isRetryable = code >= 500)
                    }

                    val body = response.body
                        ?: run {
                            val msg = "Empty audio response received from server."
                            dao.updateDownloadState(taskId, DownloadState.FAILED.name, msg)
                            return@withContext DownloadExecutionOutcome.Failed(msg, isRetryable = true)
                        }

                    val isPartialResume = (code == 206 && existingStagingBytes > 0L)
                    if (!isPartialResume && existingStagingBytes > 0L) {
                        // Server returned full 200 instead of 206; reset staging offset cleanly
                        storageHelper.deleteStagingFile(stagingFile.absolutePath)
                    }

                    val startOffset = if (isPartialResume) existingStagingBytes else 0L
                    val responseContentLength = body.contentLength().takeIf { it > 0L } ?: -1L
                    val expectedTotalBytes = parseTotalBytesFromResponse(
                        contentRangeHeader = response.header("Content-Range"),
                        responseContentLength = responseContentLength,
                        startOffset = startOffset
                    )

                    if (expectedTotalBytes > 0L) {
                        try {
                            storageHelper.checkAvailableStorage(expectedTotalBytes, startOffset)
                        } catch (e: InsufficientStorageException) {
                            val msg = e.message ?: "Insufficient storage space."
                            dao.updateDownloadState(taskId, DownloadState.FAILED.name, msg)
                            return@withContext DownloadExecutionOutcome.Failed(msg, isRetryable = false)
                        }
                    }

                    var downloadedBytes = startOffset
                    var windowStartBytes = downloadedBytes
                    var windowStartTimeMs = System.currentTimeMillis()
                    var lastEmitMs = 0L

                    val buffer = ByteArray(COPY_BUFFER_SIZE)
                    body.byteStream().use { input ->
                        FileOutputStream(stagingFile, isPartialResume).use { output ->
                            while (true) {
                                currentCoroutineContext().ensureActive()

                                when (controlSignals[taskId]) {
                                    DownloadControlSignal.CANCEL -> {
                                        controlSignals.remove(taskId)
                                        output.flush()
                                        output.close()
                                        storageHelper.deleteStagingFile(stagingFile.absolutePath)
                                        dao.updateDownloadState(taskId, DownloadState.CANCELLED.name, null)
                                        return@withContext DownloadExecutionOutcome.Cancelled
                                    }

                                    DownloadControlSignal.PAUSE -> {
                                        controlSignals.remove(taskId)
                                        output.flush()
                                        dao.updateDownloadProgress(
                                            taskId = taskId,
                                            state = DownloadState.PAUSED.name,
                                            downloadedBytes = downloadedBytes,
                                            totalBytes = expectedTotalBytes.coerceAtLeast(0L),
                                            speedBytesPerSec = 0L,
                                            etaSeconds = 0L
                                        )
                                        return@withContext DownloadExecutionOutcome.Paused
                                    }

                                    null -> Unit
                                }

                                val read = input.read(buffer)
                                if (read == -1) break
                                output.write(buffer, 0, read)
                                downloadedBytes += read

                                val nowMs = System.currentTimeMillis()
                                if (nowMs - lastEmitMs >= PROGRESS_EMIT_INTERVAL_MS) {
                                    val elapsedMs = (nowMs - windowStartTimeMs).coerceAtLeast(1L)
                                    val speedBytesPerSec = ((downloadedBytes - windowStartBytes) * 1000L) / elapsedMs
                                    val etaSeconds = if (expectedTotalBytes > downloadedBytes && speedBytesPerSec > 0L) {
                                        (expectedTotalBytes - downloadedBytes) / speedBytesPerSec
                                    } else {
                                        0L
                                    }

                                    dao.updateDownloadProgress(
                                        taskId = taskId,
                                        state = DownloadState.DOWNLOADING.name,
                                        downloadedBytes = downloadedBytes,
                                        totalBytes = expectedTotalBytes.coerceAtLeast(0L),
                                        speedBytesPerSec = speedBytesPerSec.coerceAtLeast(0L),
                                        etaSeconds = etaSeconds.coerceAtLeast(0L),
                                        updatedAt = nowMs
                                    )
                                    onProgress(downloadedBytes, expectedTotalBytes, workingTask.title)

                                    lastEmitMs = nowMs
                                    if (elapsedMs >= 1500L) {
                                        windowStartBytes = downloadedBytes
                                        windowStartTimeMs = nowMs
                                    }
                                }
                            }
                            output.flush()
                        }
                    }

                    // Verify full expected payload if server gave Content-Length
                    val actualStagingSize = stagingFile.length()
                    if (actualStagingSize <= 0L) {
                        val msg = "Downloaded audio file was empty."
                        dao.updateDownloadState(taskId, DownloadState.FAILED.name, msg)
                        return@withContext DownloadExecutionOutcome.Failed(msg, isRetryable = true)
                    }
                    if (expectedTotalBytes > 0L && actualStagingSize < expectedTotalBytes) {
                        val msg = "Connection ended early (${formatByteCount(actualStagingSize)} of ${formatByteCount(expectedTotalBytes)})."
                        dao.updateDownloadProgress(
                            taskId = taskId,
                            state = DownloadState.FAILED.name,
                            downloadedBytes = actualStagingSize,
                            totalBytes = expectedTotalBytes,
                            speedBytesPerSec = 0L,
                            etaSeconds = 0L
                        )
                        dao.updateDownloadState(taskId, DownloadState.FAILED.name, msg)
                        return@withContext DownloadExecutionOutcome.Failed(msg, isRetryable = true)
                    }

                    // Extract real container/tag metadata while staging file is local
                    val extractedMeta = storageHelper.extractMetadataFromFile(stagingFile)
                    val downloadedId = storageHelper.computeDownloadedSongId(workingTask.sourceKey)

                    val artworkBytes = extractedMeta.embeddedArtworkBytes
                        ?: fetchRemoteArtworkBytes(transferClient, resolvedArtworkUrl ?: workingTask.artworkUrl)
                    val localArtworkPath = storageHelper.saveArtworkBytes(downloadedId, artworkBytes)

                    val (committedUri, displayFileName) = storageHelper.commitStagingFileToTargetFolder(
                        task = workingTask,
                        stagingFile = stagingFile,
                        requirePersistedRecord = requirePersistedSafPermission
                    )
                    storageHelper.deleteStagingFile(stagingFile.absolutePath)

                    val now = System.currentTimeMillis()
                    val finalTitle = resolvedTitle.takeIf { it.isNotBlank() } ?: workingTask.title
                    val finalArtist = resolvedArtist.takeIf { it.isNotBlank() } ?: workingTask.artist
                    val finalAlbum = extractedMeta.album?.takeIf { it.isNotBlank() } ?: workingTask.album.ifBlank {
                        if (workingTask.mediaType == DownloadMediaType.VIDEO) "Downloaded Videos" else "Downloads"
                    }
                    val finalDurationMs = if (extractedMeta.durationMs > 0L) {
                        extractedMeta.durationMs
                    } else if (resolvedDurationMs > 0L) {
                        resolvedDurationMs
                    } else {
                        workingTask.durationMs
                    }
                    val finalBitrateKbps = if (extractedMeta.bitrateKbps > 0) extractedMeta.bitrateKbps else workingTask.bitrateKbps

                    val downloadedEntity = DownloadedTrackEntity(
                        id = downloadedId,
                        sourceKey = workingTask.sourceKey,
                        serviceId = workingTask.serviceId,
                        providerKey = workingTask.providerKey,
                        providerName = workingTask.providerName,
                        contentUrl = workingTask.contentUrl,
                        title = finalTitle,
                        artist = finalArtist,
                        album = finalAlbum,
                        durationMs = finalDurationMs,
                        artworkUrl = resolvedArtworkUrl ?: workingTask.artworkUrl,
                        localArtworkPath = localArtworkPath,
                        localFileUri = committedUri,
                        treeFolderUri = workingTask.targetFolderUri,
                        displayFileName = displayFileName,
                        formatLabel = workingTask.formatLabel,
                        mimeType = workingTask.mimeType,
                        bitrateKbps = finalBitrateKbps,
                        sampleRateHz = extractedMeta.sampleRateHz,
                        fileSizeBytes = actualStagingSize,
                        downloadedAt = now,
                        mediaType = workingTask.mediaType.name,
                        resolutionLabel = workingTask.resolutionLabel
                    )
                    dao.upsertDownloadedTrack(downloadedEntity)

                    val completedEntity = DownloadTaskEntity.fromDomain(
                        workingTask.copy(
                            title = finalTitle,
                            artist = finalArtist,
                            album = finalAlbum,
                            durationMs = finalDurationMs,
                            bitrateKbps = finalBitrateKbps,
                            state = DownloadState.COMPLETED,
                            downloadedBytes = actualStagingSize,
                            totalBytes = actualStagingSize,
                            speedBytesPerSec = 0L,
                            etaSeconds = 0L,
                            stagingFilePath = null,
                            outputFileUri = committedUri,
                            failureReason = null,
                            updatedAt = now
                        )
                    )
                    dao.upsertDownloadTask(completedEntity)
                    onProgress(actualStagingSize, actualStagingSize, finalTitle)
                    return@withContext DownloadExecutionOutcome.Completed
                }
                // If response.use returned cleanly without completing/failing above, loop once for URL refresh / Range reset
                if (refreshAttempts > MAX_URL_REFRESH_ATTEMPTS) {
                    val msg = "Download failed after stream refresh attempt."
                    dao.updateDownloadState(taskId, DownloadState.FAILED.name, msg)
                    return@withContext DownloadExecutionOutcome.Failed(msg, isRetryable = true)
                }
            } catch (c: CancellationException) {
                if (controlSignals[taskId] == DownloadControlSignal.CANCEL) {
                    controlSignals.remove(taskId)
                    storageHelper.deleteStagingFile(stagingFile.absolutePath)
                    dao.updateDownloadState(taskId, DownloadState.CANCELLED.name, null)
                    return@withContext DownloadExecutionOutcome.Cancelled
                } else {
                    dao.updateDownloadState(taskId, DownloadState.PAUSED.name, null)
                    return@withContext DownloadExecutionOutcome.Paused
                }
            } catch (e: StorageAccessLostException) {
                val msg = e.message ?: "Selected download folder access was revoked."
                dao.updateDownloadState(taskId, DownloadState.FAILED.name, msg)
                return@withContext DownloadExecutionOutcome.Failed(msg, isRetryable = false)
            } catch (e: InsufficientStorageException) {
                val msg = e.message ?: "Insufficient storage space."
                dao.updateDownloadState(taskId, DownloadState.FAILED.name, msg)
                return@withContext DownloadExecutionOutcome.Failed(msg, isRetryable = false)
            } catch (e: IOException) {
                Log.w(TAG, "Network/IO error downloading $taskId: ${e.message}")
                val currentLen = if (stagingFile.exists()) stagingFile.length() else existingStagingBytes
                dao.updateDownloadProgress(
                    taskId = taskId,
                    state = DownloadState.FAILED.name,
                    downloadedBytes = currentLen,
                    totalBytes = workingTask.totalBytes.coerceAtLeast(0L),
                    speedBytesPerSec = 0L,
                    etaSeconds = 0L
                )
                val msg = e.message?.takeIf { it.isNotBlank() } ?: "Network error interrupted download."
                dao.updateDownloadState(taskId, DownloadState.FAILED.name, msg)
                return@withContext DownloadExecutionOutcome.Failed(msg, isRetryable = true)
            } catch (t: Throwable) {
                Log.e(TAG, "Unexpected download failure for $taskId", t)
                val msg = t.message?.takeIf { it.isNotBlank() } ?: "Unable to complete download."
                dao.updateDownloadState(taskId, DownloadState.FAILED.name, msg)
                return@withContext DownloadExecutionOutcome.Failed(msg, isRetryable = false)
            }
        }
        @Suppress("UNREACHABLE_CODE")
        DownloadExecutionOutcome.Failed("Unexpected download exit.", isRetryable = false)
    }

    private fun parseTotalBytesFromResponse(
        contentRangeHeader: String?,
        responseContentLength: Long,
        startOffset: Long
    ): Long {
        if (!contentRangeHeader.isNullOrBlank()) {
            val slashPart = contentRangeHeader.substringAfter('/', "").trim()
            val totalFromHeader = slashPart.toLongOrNull()
            if (totalFromHeader != null && totalFromHeader > 0L) {
                return totalFromHeader
            }
        }
        return if (responseContentLength > 0L) startOffset + responseContentLength else -1L
    }

    private fun fetchRemoteArtworkBytes(client: OkHttpClient, artworkUrl: String?): ByteArray? {
        if (artworkUrl.isNullOrBlank()) return null
        return runCatching {
            val req = Request.Builder().url(artworkUrl).build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@use null
                val len = resp.body?.contentLength() ?: -1L
                if (len > 4 * 1024 * 1024L) return@use null
                resp.body?.bytes()?.takeIf { it.isNotEmpty() && it.size <= 4 * 1024 * 1024 }
            }
        }.getOrNull()
    }
}
