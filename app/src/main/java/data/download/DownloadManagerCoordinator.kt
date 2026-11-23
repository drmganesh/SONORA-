package data.download

import android.app.ActivityManager
import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.example.data.SonoraDao
import com.example.data.SonoraDatabase
import data.online.NetworkConnectivityMonitor
import data.online.OnlineMusicRepository
import data.preferences.DownloadLocationConfig
import data.preferences.DownloadPreferences
import data.video.OnlineVideoDataSource
import domain.download.DownloadFormatCandidate
import domain.download.DownloadMediaType
import domain.download.DownloadQualityOption
import domain.download.DownloadState
import domain.download.DownloadStorageSummary
import domain.download.DownloadTask
import domain.download.DownloadabilityResult
import domain.download.DownloadedTrack
import domain.online.OnlineTrack
import domain.video.OnlineVideoItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.withContext
import java.util.UUID
import java.util.concurrent.TimeUnit

class DownloadManagerCoordinator(
    private val appContext: Context,
    private val dao: SonoraDao,
    val preferences: DownloadPreferences,
    val storageHelper: DownloadStorageHelper,
    val onlineRepository: OnlineMusicRepository,
    val connectivityMonitor: NetworkConnectivityMonitor,
    val streamSelector: DownloadStreamSelector = DownloadStreamSelector(onlineRepository, connectivityMonitor, storageHelper),
    val videoDataSource: OnlineVideoDataSource = OnlineVideoDataSource(connectivityMonitor),
    val executionEngine: DownloadExecutionEngine = DownloadExecutionEngine(
        dao = dao,
        onlineRepository = onlineRepository,
        storageHelper = storageHelper,
        streamSelector = streamSelector,
        videoDataSource = videoDataSource
    ),
    private val workManagerProvider: () -> WorkManager = { WorkManager.getInstance(appContext) }
) {

    companion object {
        const val WORK_TAG_DOWNLOAD = "sonora_audio_download"
        @Volatile
        private var INSTANCE: DownloadManagerCoordinator? = null

        fun getInstance(context: Context): DownloadManagerCoordinator {
            return INSTANCE ?: synchronized(this) {
                val app = context.applicationContext
                val db = SonoraDatabase.getDatabase(app)
                val connectivity = NetworkConnectivityMonitor(app)
                val repo = OnlineMusicRepository(db.dao(), connectivity)
                val prefs = DownloadPreferences(app)
                val storage = DownloadStorageHelper(app)
                DownloadManagerCoordinator(
                    appContext = app,
                    dao = db.dao(),
                    preferences = prefs,
                    storageHelper = storage,
                    onlineRepository = repo,
                    connectivityMonitor = connectivity
                ).also { INSTANCE = it }
            }
        }
    }

    val maxConcurrentDownloads: Int by lazy {
        val am = appContext.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        if (am?.isLowRamDevice == true) 1 else 2
    }

    val concurrencySemaphore: Semaphore by lazy {
        Semaphore(maxConcurrentDownloads)
    }

    val allTasksFlow: Flow<List<DownloadTask>> =
        dao.getAllDownloadTasksFlow().map { list -> list.map { it.toDomain() } }

    val activeTasksFlow: Flow<List<DownloadTask>> =
        dao.getActiveDownloadTasksFlow().map { list -> list.map { it.toDomain() } }

    val downloadedTracksFlow: Flow<List<DownloadedTrack>> =
        dao.getAllDownloadedTracksFlow().map { list ->
            list.map { entity ->
                val exists = storageHelper.doesDownloadedFileExist(entity.localFileUri)
                entity.toDomain(isMissingFile = !exists)
            }
        }

    val downloadedMusicFlow: Flow<List<DownloadedTrack>> =
        downloadedTracksFlow.map { tracks -> tracks.filter { it.mediaType == DownloadMediaType.AUDIO } }

    val downloadedVideosFlow: Flow<List<DownloadedTrack>> =
        downloadedTracksFlow.map { tracks -> tracks.filter { it.mediaType == DownloadMediaType.VIDEO } }

    val storageSummaryFlow: Flow<DownloadStorageSummary> =
        combine(
            dao.getAllDownloadedTracksFlow(),
            preferences.locationConfigFlow
        ) { tracks, location ->
            DownloadStorageSummary(
                trackCount = tracks.size,
                totalBytes = tracks.sumOf { it.fileSizeBytes.coerceAtLeast(0L) },
                folderLabel = location.folderLabel.ifBlank { "Not configured" },
                hasFolderAccess = location.isConfigured &&
                    storageHelper.isTreeUriAccessible(location.treeUri)
            )
        }

    suspend fun getTaskTitle(taskId: String): String? = withContext(Dispatchers.IO) {
        dao.getDownloadTaskById(taskId)?.title
    }

    suspend fun checkDownloadability(track: OnlineTrack): DownloadabilityResult = withContext(Dispatchers.IO) {
        val sourceKey = storageHelper.computeCanonicalSourceKey(track.providerKey, track.contentUrl)
        val existingDownloaded = dao.getDownloadedTrackBySourceKey(sourceKey)
        val existingTask = dao.getDownloadTaskBySourceKey(sourceKey)
        streamSelector.checkPreconditions(track, existingDownloaded, existingTask)
    }

    suspend fun hasAccessibleDownloadFolder(requirePersistedRecord: Boolean = true): Boolean {
        val config = preferences.getLocationConfig()
        return config.isConfigured && storageHelper.isTreeUriAccessible(
            config.treeUri,
            requirePersistedRecord = requirePersistedRecord
        )
    }

    suspend fun persistSelectedFolder(treeUri: android.net.Uri): DownloadLocationConfig = withContext(Dispatchers.IO) {
        storageHelper.persistSafTreePermission(treeUri)
        val label = storageHelper.resolveFolderDisplayName(treeUri)
        val uriString = treeUri.toString()
        preferences.saveDownloadLocation(uriString, label)
        DownloadLocationConfig(treeUri = uriString, folderLabel = label)
    }

    suspend fun enqueueDownload(
        track: OnlineTrack,
        qualityOption: DownloadQualityOption,
        selectedCandidate: DownloadFormatCandidate,
        targetFolderUri: String,
        scheduleWork: Boolean = true
    ): Result<DownloadTask> = withContext(Dispatchers.IO) {
        val precheck = checkDownloadability(track)
        when (precheck) {
            is DownloadabilityResult.AlreadyDownloaded -> {
                return@withContext Result.failure(
                    IllegalStateException("\"${precheck.trackTitle}\" is already in Downloaded Music.")
                )
            }
            is DownloadabilityResult.AlreadyInQueue -> {
                return@withContext Result.failure(
                    IllegalStateException("\"${precheck.trackTitle}\" is already in the download queue.")
                )
            }
            is DownloadabilityResult.Unsupported -> {
                return@withContext Result.failure(IllegalStateException(precheck.reason))
            }
            is DownloadabilityResult.Downloadable -> Unit
        }

        val sourceKey = storageHelper.computeCanonicalSourceKey(track.providerKey, track.contentUrl)
        val existingEndedTask = dao.getDownloadTaskBySourceKey(sourceKey)
        existingEndedTask?.stagingFilePath?.let { storageHelper.deleteStagingFile(it) }

        val taskId = existingEndedTask?.taskId ?: UUID.randomUUID().toString()
        val now = System.currentTimeMillis()
        val task = DownloadTask(
            taskId = taskId,
            sourceKey = sourceKey,
            serviceId = track.serviceId,
            providerKey = track.providerKey,
            providerName = track.providerName,
            contentUrl = track.contentUrl,
            title = track.title,
            artist = track.artist,
            album = track.album.ifBlank { "Downloads" },
            durationMs = track.durationMs,
            artworkUrl = track.artworkUrl,
            qualityOption = qualityOption,
            selectedStreamId = selectedCandidate.streamId,
            formatLabel = selectedCandidate.codecLabel,
            mimeType = selectedCandidate.mimeType,
            fileExtension = selectedCandidate.containerExtension,
            bitrateKbps = selectedCandidate.bitrateKbps,
            state = DownloadState.WAITING,
            downloadedBytes = 0L,
            totalBytes = selectedCandidate.contentLengthBytes.coerceAtLeast(0L),
            speedBytesPerSec = 0L,
            etaSeconds = 0L,
            targetFolderUri = targetFolderUri,
            stagingFilePath = storageHelper.getStagingFile(taskId, selectedCandidate.containerExtension).absolutePath,
            outputFileUri = null,
            failureReason = null,
            retryCount = 0,
            createdAt = now,
            updatedAt = now
        )
        dao.upsertDownloadTask(DownloadTaskEntity.fromDomain(task))
        if (scheduleWork) {
            scheduleWorker(taskId)
        }
        Result.success(task)
    }

    suspend fun enqueueVideoDownload(
        video: OnlineVideoItem,
        option: VideoDownloadOption,
        targetFolderUri: String,
        scheduleWork: Boolean = true
    ): Result<DownloadTask> = withContext(Dispatchers.IO) {
        if (video.isLive) {
            return@withContext Result.failure(IllegalStateException("Live streams cannot be downloaded."))
        }
        if (video.contentUrl.isBlank()) {
            return@withContext Result.failure(IllegalStateException("Video source URL is missing."))
        }

        val mediaSuffix = if (option.mediaType == DownloadMediaType.VIDEO) "video" else "audio"
        val sourceKey = storageHelper.computeCanonicalSourceKey("${video.providerKey}:$mediaSuffix", video.contentUrl)
        val existingDownloaded = dao.getDownloadedTrackBySourceKey(sourceKey)
        if (existingDownloaded != null && storageHelper.doesDownloadedFileExist(existingDownloaded.localFileUri)) {
            val label = if (option.mediaType == DownloadMediaType.VIDEO) "Downloaded Videos" else "Downloaded Music"
            return@withContext Result.failure(IllegalStateException("\"${video.title}\" is already in $label."))
        }

        val existingTask = dao.getDownloadTaskBySourceKey(sourceKey)
        if (existingTask != null && existingTask.state in setOf(
                DownloadState.WAITING.name,
                DownloadState.RESOLVING.name,
                DownloadState.DOWNLOADING.name,
                DownloadState.PAUSED.name
            )
        ) {
            return@withContext Result.failure(IllegalStateException("\"${video.title}\" is already in the download queue."))
        }

        existingTask?.stagingFilePath?.let { storageHelper.deleteStagingFile(it) }
        val taskId = existingTask?.taskId ?: UUID.randomUUID().toString()
        val now = System.currentTimeMillis()
        val candidate = option.candidate
        val task = DownloadTask(
            taskId = taskId,
            sourceKey = sourceKey,
            serviceId = 0,
            providerKey = video.providerKey,
            providerName = video.providerName,
            contentUrl = video.contentUrl,
            title = video.title,
            artist = video.channelName.ifBlank { "Online Channel" },
            album = if (option.mediaType == DownloadMediaType.VIDEO) "Downloaded Videos" else "Downloaded Music",
            durationMs = video.durationMs,
            artworkUrl = video.thumbnailUrl,
            qualityOption = DownloadQualityOption.BEST_AVAILABLE,
            selectedStreamId = candidate.streamId,
            formatLabel = candidate.codecLabel,
            mimeType = candidate.mimeType,
            fileExtension = candidate.containerExtension,
            bitrateKbps = candidate.bitrateKbps,
            state = DownloadState.WAITING,
            downloadedBytes = 0L,
            totalBytes = candidate.contentLengthBytes.coerceAtLeast(0L),
            speedBytesPerSec = 0L,
            etaSeconds = 0L,
            targetFolderUri = targetFolderUri,
            stagingFilePath = storageHelper.getStagingFile(taskId, candidate.containerExtension).absolutePath,
            outputFileUri = null,
            failureReason = null,
            retryCount = 0,
            createdAt = now,
            updatedAt = now,
            mediaType = option.mediaType,
            resolutionLabel = option.resolutionLabel
        )
        dao.upsertDownloadTask(DownloadTaskEntity.fromDomain(task))
        if (scheduleWork) {
            scheduleWorker(taskId)
        }
        Result.success(task)
    }

    suspend fun pauseDownload(taskId: String) = withContext(Dispatchers.IO) {
        executionEngine.signalPause(taskId)
        runCatching { workManagerProvider().cancelUniqueWork(uniqueWorkName(taskId)) }
        dao.updateDownloadState(taskId, DownloadState.PAUSED.name, null)
    }

    suspend fun resumeDownload(taskId: String, scheduleWork: Boolean = true) = withContext(Dispatchers.IO) {
        val existing = dao.getDownloadTaskById(taskId) ?: return@withContext
        executionEngine.clearSignal(taskId)
        dao.upsertDownloadTask(
            existing.copy(
                state = DownloadState.WAITING.name,
                failureReason = null,
                updatedAt = System.currentTimeMillis()
            )
        )
        if (scheduleWork) {
            scheduleWorker(taskId)
        }
    }

    suspend fun retryDownload(taskId: String, scheduleWork: Boolean = true) = withContext(Dispatchers.IO) {
        val existing = dao.getDownloadTaskById(taskId) ?: return@withContext
        executionEngine.clearSignal(taskId)
        dao.upsertDownloadTask(
            existing.copy(
                state = DownloadState.WAITING.name,
                failureReason = null,
                retryCount = existing.retryCount + 1,
                updatedAt = System.currentTimeMillis()
            )
        )
        if (scheduleWork) {
            scheduleWorker(taskId)
        }
    }

    suspend fun cancelDownload(taskId: String) = withContext(Dispatchers.IO) {
        executionEngine.signalCancel(taskId)
        runCatching { workManagerProvider().cancelUniqueWork(uniqueWorkName(taskId)) }
        val existing = dao.getDownloadTaskById(taskId) ?: return@withContext
        storageHelper.deleteStagingFile(existing.stagingFilePath)
        dao.updateDownloadState(taskId, DownloadState.CANCELLED.name, null)
    }

    suspend fun removeDownloadTask(taskId: String) = withContext(Dispatchers.IO) {
        executionEngine.signalCancel(taskId)
        runCatching { workManagerProvider().cancelUniqueWork(uniqueWorkName(taskId)) }
        val existing = dao.getDownloadTaskById(taskId)
        storageHelper.deleteStagingFile(existing?.stagingFilePath)
        dao.deleteDownloadTask(taskId)
    }

    /**
     * Clears finished/failed/cancelled task rows from Download Manager history ONLY.
     * Never deletes audio files or removes valid downloaded library files from storage.
     */
    suspend fun clearDownloadHistory() = withContext(Dispatchers.IO) {
        dao.clearCompletedDownloadHistory()
    }

    /**
     * Deletes user-selected downloaded tracks and their downloaded SAF files ONLY.
     * Original user local library tracks outside downloaded_tracks are never touched.
     */
    suspend fun deleteDownloadedTracks(trackIds: List<Long>) = withContext(Dispatchers.IO) {
        if (trackIds.isEmpty()) return@withContext
        trackIds.forEach { id ->
            val entity = dao.getDownloadedTrackById(id)
            if (entity != null) {
                storageHelper.deleteDownloadedTrackFiles(entity)
                dao.getDownloadTaskBySourceKey(entity.sourceKey)?.let { task ->
                    dao.deleteDownloadTask(task.taskId)
                }
            }
        }
        dao.deleteDownloadedTracksByIds(trackIds)
    }

    /**
     * Reconciles tasks left in RESOLVING or DOWNLOADING after process death / restart.
     */
    suspend fun reconcileInterruptedTasks(autoResume: Boolean = true): Int = withContext(Dispatchers.IO) {
        val inFlight = dao.getInFlightDownloadTasks()
        var recovered = 0
        inFlight.forEach { task ->
            if (task.state == DownloadState.DOWNLOADING.name || task.state == DownloadState.RESOLVING.name) {
                recovered++
                val nextState = if (autoResume) DownloadState.WAITING.name else DownloadState.PAUSED.name
                dao.updateDownloadState(task.taskId, nextState, null)
                if (autoResume) {
                    scheduleWorker(task.taskId)
                }
            } else if (task.state == DownloadState.WAITING.name && autoResume) {
                scheduleWorker(task.taskId)
            }
        }
        recovered
    }

    private fun scheduleWorker(taskId: String) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
        val request = OneTimeWorkRequestBuilder<AudioDownloadWorker>()
            .setInputData(Data.Builder().putString(AudioDownloadWorker.KEY_TASK_ID, taskId).build())
            .setConstraints(constraints)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 10, TimeUnit.SECONDS)
            .addTag(WORK_TAG_DOWNLOAD)
            .build()
        workManagerProvider().enqueueUniqueWork(
            uniqueWorkName(taskId),
            ExistingWorkPolicy.REPLACE,
            request
        )
    }

    private fun uniqueWorkName(taskId: String): String = "sonora_download_$taskId"
}
