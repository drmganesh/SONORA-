package data.festival

import android.util.Log
import domain.festival.FestivalRecord
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.concurrent.atomic.AtomicBoolean

private const val SYNC_COORD_TAG = "FestivalSyncCoordinator"

/**
 * User-visible synchronization status states for SONORA's Public Festival Calendar.
 * User messages are always clean, friendly strings and NEVER contain raw JSON or stack traces.
 */
sealed class FestivalSyncUiState(
    val statusCode: String,
    val message: String,
    val debugDiagnostic: String? = null
) {
    val isFailure: Boolean
        get() = this is NoInternet || this is ServerError || this is InvalidResponse || this is DatabaseError

    data object Idle : FestivalSyncUiState("IDLE", "")

    data object Syncing : FestivalSyncUiState("SYNCING", "Syncing...")

    data class Updated(val recordCount: Int, val syncedAtEpochMs: Long) :
        FestivalSyncUiState("UPDATED", "Calendar updated successfully")

    data class AlreadyUpToDate(val syncedAtEpochMs: Long) :
        FestivalSyncUiState("UP_TO_DATE", "Calendar is already up to date")

    data object NoInternet :
        FestivalSyncUiState("OFFLINE", "No internet connection. Using cached calendar.")

    data class ServerError(
        val httpStatusCode: Int? = null,
        val diagnosticDetail: String? = null
    ) : FestivalSyncUiState(
        statusCode = "SERVER_ERROR",
        message = "Festival calendar server unavailable. Using cached data.",
        debugDiagnostic = PublicFestivalCalendarDataSource.sanitizeDiagnosticText(diagnosticDetail)
    )

    data class InvalidResponse(
        val reasonCode: String = "INVALID_RESPONSE",
        val diagnosticDetail: String? = null
    ) : FestivalSyncUiState(
        statusCode = "INVALID_RESPONSE",
        message = "Calendar data could not be verified. Using cached data.",
        debugDiagnostic = PublicFestivalCalendarDataSource.sanitizeDiagnosticText(diagnosticDetail)
    )

    data class DatabaseError(
        val diagnosticDetail: String? = null
    ) : FestivalSyncUiState(
        statusCode = "DATABASE_ERROR",
        message = "Could not save the updated calendar. Existing data preserved.",
        debugDiagnostic = PublicFestivalCalendarDataSource.sanitizeDiagnosticText(diagnosticDetail)
    )
}

enum class FestivalSyncTrigger {
    AUTOMATIC_DAILY_ON_OPEN,
    MANUAL_SYNC_NOW,
    PERIODIC_BACKGROUND_WORKER
}

data class FestivalSyncExecutionResult(
    val uiState: FestivalSyncUiState,
    val performedNetworkRequest: Boolean,
    val skippedAlreadySyncedToday: Boolean = false,
    val skippedDuplicateInFlight: Boolean = false
)

/**
 * Single process-wide synchronization coordinator for SONORA's Public Festival Calendar.
 *
 * Guarantees:
 * - Automatic daily sync runs AT MOST ONCE per local `Asia/Kolkata` calendar day after a successful sync.
 * - Never triggers repeatedly during recompositions, screen changes, or navigation.
 * - Prevents duplicate concurrent requests across app open, manual "Sync Now", and WorkManager.
 * - Checks connectivity before any network call; offline mode uses cached Room data cleanly.
 * - Applies bounded exponential backoff ONLY on transient server/network failures (never on 404/429/schema).
 * - Updates Room transactionally and NEVER replaces valid cached data with empty or malformed responses.
 * - Updates `lastSyncedEpochMs` ONLY after a verified Room transaction commit (or HTTP 304 confirmation).
 * - Emits structured safe diagnostic logs without exposing raw JSON bodies or private data.
 */
class FestivalCalendarSyncCoordinator(
    private val dao: FestivalCalendarDao,
    private val dataSource: FestivalCalendarDataSource = PublicFestivalCalendarDataSource(),
    private val isNetworkAvailable: () -> Boolean = { true },
    private val clockEpochMs: () -> Long = { System.currentTimeMillis() },
    private val backoffDelayMs: suspend (Long) -> Unit = { ms -> delay(ms) },
    private val indiaZone: ZoneId = INDIA_ZONE,
    private val diagnosticLogger: (String) -> Unit = { line -> Log.i(SYNC_COORD_TAG, line) }
) {
    companion object {
        val INDIA_ZONE: ZoneId = ZoneId.of("Asia/Kolkata")
        private const val MAX_NETWORK_ATTEMPTS = 3
        private const val INITIAL_BACKOFF_MS = 1_000L

        fun localDateInIndia(epochMs: Long, zone: ZoneId = INDIA_ZONE): LocalDate =
            Instant.ofEpochMilli(epochMs).atZone(zone).toLocalDate()

        fun hasSyncedSuccessfullyToday(
            lastSuccessfulSyncEpochMs: Long,
            nowEpochMs: Long,
            zone: ZoneId = INDIA_ZONE
        ): Boolean {
            if (lastSuccessfulSyncEpochMs <= 0L) return false
            val lastDate = localDateInIndia(lastSuccessfulSyncEpochMs, zone)
            val today = localDateInIndia(nowEpochMs, zone)
            return !lastDate.isBefore(today)
        }
    }

    private val seedMutex = Mutex()
    private val syncMutex = Mutex()
    private val seedEnsured = AtomicBoolean(false)
    private val inFlight = AtomicBoolean(false)

    private val _syncUiState = MutableStateFlow<FestivalSyncUiState>(FestivalSyncUiState.Idle)
    val syncUiState: StateFlow<FestivalSyncUiState> = _syncUiState.asStateFlow()

    val isSyncInFlight: Boolean get() = inFlight.get()

    /**
     * Ensures Room has the verified Pan-India Panchang seed catalog (82 baseline records) on first
     * launch so offline festival detection works immediately. Does NOT fabricate a remote sync
     * timestamp (`lastSyncedEpochMs` remains 0L until an actual public calendar sync commits).
     */
    suspend fun ensureLocalSeedPresent() {
        if (seedEnsured.get()) return
        seedMutex.withLock {
            if (seedEnsured.get()) return@withLock
            val count = dao.countEntries()
            val existingMeta = dao.getSyncMetaOnce()
            if (count > 0 && existingMeta != null) {
                seedEnsured.set(true)
                return@withLock
            }

            val now = clockEpochMs()
            val seed = dataSource.getVerifiedBaseCatalog(now)
            val version = PublicFestivalCalendarDataSource.computeDatasetVersion(seed)
            val meta = existingMeta ?: FestivalSyncMetaEntity(
                lastSyncedEpochMs = 0L,
                lastRemoteAttemptEpochMs = 0L,
                lastSourceSummary = "IMD Rashtriya Panchang & Regional Panji (Verified Cache)",
                isOfflineFallback = false,
                consecutiveFailures = 0,
                recordCount = seed.size,
                datasetVersion = version,
                sourceAttribution = PublicFestivalCalendarDataSource.FULL_SOURCE_ATTRIBUTION,
                lastSyncStatusCode = "IDLE",
                lastErrorMessage = null
            )
            if (count == 0) {
                dao.commitSyncUpdate(seed.map(FestivalCalendarEntity::fromDomain), meta)
            } else {
                dao.upsertSyncMeta(meta.copy(recordCount = count))
            }
            seedEnsured.set(true)
        }
    }

    /**
     * Automatic daily synchronization entry point called when SONORA opens/foregrounds.
     * - If another sync is already running -> returns immediately without duplicate requests.
     * - If already synchronized successfully during the current `Asia/Kolkata` day -> uses cached data.
     * - If offline -> keeps cached Room data cleanly and will retry on a future app opening.
     */
    suspend fun syncOnAppOpenIfNeeded(
        automaticDailySyncEnabled: Boolean = true
    ): FestivalSyncExecutionResult {
        if (!automaticDailySyncEnabled) {
            ensureLocalSeedPresent()
            return FestivalSyncExecutionResult(
                uiState = _syncUiState.value,
                performedNetworkRequest = false
            )
        }
        if (inFlight.get()) {
            return FestivalSyncExecutionResult(
                uiState = FestivalSyncUiState.Syncing,
                performedNetworkRequest = false,
                skippedDuplicateInFlight = true
            )
        }

        ensureLocalSeedPresent()

        val now = clockEpochMs()
        val meta = dao.getSyncMetaOnce()
        if (meta != null && hasSyncedSuccessfullyToday(meta.lastSyncedEpochMs, now, indiaZone)) {
            return FestivalSyncExecutionResult(
                uiState = FestivalSyncUiState.AlreadyUpToDate(meta.lastSyncedEpochMs),
                performedNetworkRequest = false,
                skippedAlreadySyncedToday = true
            )
        }

        return executeSynchronization(trigger = FestivalSyncTrigger.AUTOMATIC_DAILY_ON_OPEN)
    }

    /**
     * Manual synchronization entry point triggered by "Sync Now" or the top bar sync button.
     * Prevents simultaneous manual requests, immediately checks connectivity, and reports exact UI states.
     */
    suspend fun syncNowManually(): FestivalSyncExecutionResult {
        if (inFlight.get()) {
            return FestivalSyncExecutionResult(
                uiState = FestivalSyncUiState.Syncing,
                performedNetworkRequest = false,
                skippedDuplicateInFlight = true
            )
        }
        ensureLocalSeedPresent()
        return executeSynchronization(trigger = FestivalSyncTrigger.MANUAL_SYNC_NOW)
    }

    /**
     * WorkManager background sync entry point sharing the same coordinator lock & daily gate.
     */
    suspend fun syncFromBackgroundWorker(): FestivalSyncExecutionResult {
        if (inFlight.get()) {
            return FestivalSyncExecutionResult(
                uiState = FestivalSyncUiState.Syncing,
                performedNetworkRequest = false,
                skippedDuplicateInFlight = true
            )
        }
        ensureLocalSeedPresent()
        val now = clockEpochMs()
        val meta = dao.getSyncMetaOnce()
        if (meta != null && hasSyncedSuccessfullyToday(meta.lastSyncedEpochMs, now, indiaZone)) {
            return FestivalSyncExecutionResult(
                uiState = FestivalSyncUiState.AlreadyUpToDate(meta.lastSyncedEpochMs),
                performedNetworkRequest = false,
                skippedAlreadySyncedToday = true
            )
        }
        return executeSynchronization(trigger = FestivalSyncTrigger.PERIODIC_BACKGROUND_WORKER)
    }

    private suspend fun executeSynchronization(
        trigger: FestivalSyncTrigger
    ): FestivalSyncExecutionResult {
        // Step 2: Strict single-flight gate to prevent duplicate simultaneous requests
        if (!inFlight.compareAndSet(false, true)) {
            return FestivalSyncExecutionResult(
                uiState = FestivalSyncUiState.Syncing,
                performedNetworkRequest = false,
                skippedDuplicateInFlight = true
            )
        }

        try {
            return syncMutex.withLock {
                val startedAt = clockEpochMs()
                logEvent("SYNC_STARTED trigger=${trigger.name}")

                val existingMeta = dao.getSyncMetaOnce()
                val existingEntities = dao.getAllEntriesOnce()
                val existingRecords: List<FestivalRecord> = existingEntities.map { it.toDomain() }

                // Re-check daily gate inside lock in case a preceding sync just succeeded today
                if (trigger != FestivalSyncTrigger.MANUAL_SYNC_NOW &&
                    existingMeta != null &&
                    hasSyncedSuccessfullyToday(existingMeta.lastSyncedEpochMs, startedAt, indiaZone)
                ) {
                    val state = FestivalSyncUiState.AlreadyUpToDate(existingMeta.lastSyncedEpochMs)
                    logEvent("SYNC_COMPLETED status=SKIPPED_ALREADY_SYNCED_TODAY cachedCount=${existingRecords.size}")
                    return@withLock FestivalSyncExecutionResult(
                        uiState = state,
                        performedNetworkRequest = false,
                        skippedAlreadySyncedToday = true
                    )
                }

                // Step 3: Check network availability BEFORE making any network call
                if (!isNetworkAvailable()) {
                    logEvent("SYNC_FAILED category=OFFLINE detail=No active network cachedCount=${existingRecords.size}")
                    recordFailedAttemptSafely(
                        existingMeta = existingMeta,
                        existingCount = existingRecords.size,
                        attemptEpochMs = startedAt,
                        statusCode = FestivalSyncUiState.NoInternet.statusCode,
                        safeErrorDetail = "Network unavailable"
                    )
                    _syncUiState.value = FestivalSyncUiState.NoInternet
                    return@withLock FestivalSyncExecutionResult(
                        uiState = FestivalSyncUiState.NoInternet,
                        performedNetworkRequest = false
                    )
                }

                // Emit Syncing state
                _syncUiState.value = FestivalSyncUiState.Syncing

                // Step 4–10: Fetch, validate HTTP status + content-type, parse national & regional
                // collections, validate records, deduplicate, and merge with existing cache.
                logEvent(
                    "SOURCE_REQUESTED url=${PublicFestivalCalendarDataSource.MIT_FESTIVALS_LATEST_URL} " +
                        "hasEtag=${!existingMeta?.remoteEtag.isNullOrBlank()}"
                )

                var lastError: Throwable? = null
                var fetchSuccess: FestivalFeedFetchResult? = null
                var attempt = 0
                while (attempt < MAX_NETWORK_ATTEMPTS) {
                    attempt++
                    val attemptResult = dataSource.fetchLatestCatalog(
                        nowEpochMs = clockEpochMs(),
                        ifNoneMatch = existingMeta?.remoteEtag,
                        ifModifiedSince = existingMeta?.remoteLastModified,
                        existingCachedRecords = existingRecords
                    )
                    if (attemptResult.isSuccess) {
                        fetchSuccess = attemptResult.getOrNull()
                        break
                    } else {
                        lastError = attemptResult.exceptionOrNull()
                        val shouldRetry = isTransientRetryableFailure(lastError)
                        if (!shouldRetry || attempt >= MAX_NETWORK_ATTEMPTS) {
                            break
                        }
                        val waitMs = INITIAL_BACKOFF_MS * (1L shl (attempt - 1))
                        logEvent("SYNC_RETRY attempt=$attempt backoffMs=$waitMs exceptionType=${lastError?.javaClass?.simpleName}")
                        backoffDelayMs(waitMs)
                    }
                }

                val payload = fetchSuccess
                if (payload == null) {
                    val failureState = classifyFetchFailure(lastError)
                    val safeDiagnostic = failureState.debugDiagnostic
                        ?: PublicFestivalCalendarDataSource.sanitizeDiagnosticText(lastError?.message)
                        ?: lastError?.javaClass?.simpleName
                        ?: "Sync failed"

                    logEvent(
                        "SYNC_FAILED category=${failureState.statusCode} " +
                            "exceptionType=${lastError?.javaClass?.simpleName ?: "Unknown"} " +
                            "detail=$safeDiagnostic cachedCount=${existingRecords.size}"
                    )
                    recordFailedAttemptSafely(
                        existingMeta = existingMeta,
                        existingCount = existingRecords.size,
                        attemptEpochMs = clockEpochMs(),
                        statusCode = failureState.statusCode,
                        safeErrorDetail = safeDiagnostic
                    )
                    _syncUiState.value = failureState
                    return@withLock FestivalSyncExecutionResult(
                        uiState = failureState,
                        performedNetworkRequest = true
                    )
                }

                logEvent(
                    "HTTP_RESPONSE status=${payload.httpStatusCode} " +
                        "contentType=${payload.contentType ?: "unspecified"} notModified=${payload.isNotModified}"
                )
                if (!payload.isNotModified) {
                    logEvent(
                        "PARSE_RESULT collections=${payload.collectionsFound.joinToString(",")} " +
                            "validRecords=${payload.fetchedRemoteCount} rejectedRecords=${payload.rejectedRemoteCount}"
                    )
                }

                val completedAt = clockEpochMs()
                val previousVersion = existingMeta?.datasetVersion.orEmpty()

                // Handle HTTP 304 Not Modified or identical validated dataset digest -> Already up to date
                if (payload.isNotModified ||
                    (previousVersion.isNotBlank() && payload.datasetVersion == previousVersion && existingRecords.isNotEmpty())
                ) {
                    val upToDateMeta = (existingMeta ?: defaultSeedMeta(existingRecords.size)).copy(
                        lastSyncedEpochMs = completedAt,
                        lastRemoteAttemptEpochMs = completedAt,
                        lastSourceSummary = payload.sourceSummary,
                        sourceAttribution = payload.sourceAttribution,
                        datasetVersion = payload.datasetVersion,
                        remoteEtag = payload.etag ?: existingMeta?.remoteEtag,
                        remoteLastModified = payload.lastModified ?: existingMeta?.remoteLastModified,
                        isOfflineFallback = false,
                        consecutiveFailures = 0,
                        recordCount = existingRecords.size,
                        lastSyncStatusCode = "UP_TO_DATE",
                        lastErrorMessage = null
                    )
                    return@withLock try {
                        dao.upsertSyncMeta(upToDateMeta)
                        logEvent("DB_TRANSACTION result=COMMITTED_META_ONLY records=${existingRecords.size}")
                        val state = FestivalSyncUiState.AlreadyUpToDate(completedAt)
                        _syncUiState.value = state
                        logEvent("SYNC_COMPLETED status=UP_TO_DATE cachedCount=${existingRecords.size} version=${payload.datasetVersion}")
                        FestivalSyncExecutionResult(
                            uiState = state,
                            performedNetworkRequest = true
                        )
                    } catch (dbEx: Throwable) {
                        handleDatabaseCommitFailure(dbEx, existingMeta, existingRecords.size, completedAt)
                    }
                }

                // Final validation gate before atomic transactional Room commit
                if (!PublicFestivalCalendarDataSource.validateRecordsForCommit(payload.records)) {
                    val invalidState = FestivalSyncUiState.InvalidResponse(
                        reasonCode = "COMMIT_VALIDATION_FAILED",
                        diagnosticDetail = "Merged calendar failed commit validation"
                    )
                    logEvent("SYNC_FAILED category=INVALID_RESPONSE detail=COMMIT_VALIDATION_FAILED cachedCount=${existingRecords.size}")
                    recordFailedAttemptSafely(
                        existingMeta = existingMeta,
                        existingCount = existingRecords.size,
                        attemptEpochMs = completedAt,
                        statusCode = invalidState.statusCode,
                        safeErrorDetail = invalidState.debugDiagnostic
                    )
                    _syncUiState.value = invalidState
                    return@withLock FestivalSyncExecutionResult(
                        uiState = invalidState,
                        performedNetworkRequest = true
                    )
                }

                val updatedMeta = FestivalSyncMetaEntity(
                    lastSyncedEpochMs = completedAt,
                    lastRemoteAttemptEpochMs = completedAt,
                    lastSourceSummary = payload.sourceSummary,
                    isOfflineFallback = false,
                    consecutiveFailures = 0,
                    recordCount = payload.records.size,
                    datasetVersion = payload.datasetVersion,
                    remoteEtag = payload.etag,
                    remoteLastModified = payload.lastModified,
                    sourceAttribution = payload.sourceAttribution,
                    lastSyncStatusCode = "UPDATED",
                    lastErrorMessage = null
                )

                // Step 11 & 12: Save atomically in a single Room @Transaction; lastSyncedEpochMs
                // is only persisted if the entire transaction commits cleanly.
                try {
                    dao.commitSyncUpdate(
                        entries = payload.records.map(FestivalCalendarEntity::fromDomain),
                        meta = updatedMeta
                    )
                    logEvent("DB_TRANSACTION result=COMMITTED records=${payload.records.size}")
                    val updatedState = FestivalSyncUiState.Updated(
                        recordCount = payload.records.size,
                        syncedAtEpochMs = completedAt
                    )
                    _syncUiState.value = updatedState
                    logEvent("SYNC_COMPLETED status=UPDATED cachedCount=${payload.records.size} version=${payload.datasetVersion}")
                    FestivalSyncExecutionResult(
                        uiState = updatedState,
                        performedNetworkRequest = true
                    )
                } catch (dbEx: Throwable) {
                    handleDatabaseCommitFailure(dbEx, existingMeta, existingRecords.size, completedAt)
                }
            }
        } finally {
            inFlight.set(false)
        }
    }

    private suspend fun handleDatabaseCommitFailure(
        dbEx: Throwable,
        existingMeta: FestivalSyncMetaEntity?,
        existingCount: Int,
        attemptEpochMs: Long
    ): FestivalSyncExecutionResult {
        val safeDetail = PublicFestivalCalendarDataSource.sanitizeDiagnosticText(
            "${dbEx.javaClass.simpleName}: ${dbEx.message.orEmpty()}"
        ) ?: dbEx.javaClass.simpleName

        logEvent("DB_TRANSACTION result=FAILED exceptionType=${dbEx.javaClass.simpleName} detail=$safeDetail")
        logEvent("SYNC_FAILED category=DATABASE_ERROR exceptionType=${dbEx.javaClass.simpleName} detail=$safeDetail cachedCount=$existingCount")

        recordFailedAttemptSafely(
            existingMeta = existingMeta,
            existingCount = existingCount,
            attemptEpochMs = attemptEpochMs,
            statusCode = "DATABASE_ERROR",
            safeErrorDetail = safeDetail
        )
        val dbState = FestivalSyncUiState.DatabaseError(diagnosticDetail = safeDetail)
        _syncUiState.value = dbState
        return FestivalSyncExecutionResult(
            uiState = dbState,
            performedNetworkRequest = true
        )
    }

    private suspend fun recordFailedAttemptSafely(
        existingMeta: FestivalSyncMetaEntity?,
        existingCount: Int,
        attemptEpochMs: Long,
        statusCode: String,
        safeErrorDetail: String?
    ) {
        val sanitized = PublicFestivalCalendarDataSource.sanitizeDiagnosticText(safeErrorDetail)
        val failedMeta = (existingMeta ?: defaultSeedMeta(existingCount)).copy(
            // CRITICAL: NEVER modify lastSyncedEpochMs on failure!
            lastRemoteAttemptEpochMs = attemptEpochMs,
            isOfflineFallback = true,
            consecutiveFailures = (existingMeta?.consecutiveFailures ?: 0) + 1,
            lastSyncStatusCode = statusCode,
            lastErrorMessage = sanitized,
            recordCount = existingCount
        )
        runCatching { dao.upsertSyncMeta(failedMeta) }
            .onFailure { metaEx ->
                Log.w(SYNC_COORD_TAG, "Unable to persist failure metadata: ${metaEx.javaClass.simpleName}")
            }
    }

    private fun isTransientRetryableFailure(error: Throwable?): Boolean {
        if (error == null) return false
        return when (error) {
            is FestivalInvalidResponseException,
            is IllegalArgumentException,
            is IllegalStateException,
            is org.json.JSONException -> false
            is FestivalServerException -> {
                // Do not hammer the server on HTTP 404 or HTTP 429 rate limits
                !error.isRateLimited && error.httpStatusCode != 404
            }
            else -> true
        }
    }

    private fun classifyFetchFailure(error: Throwable?): FestivalSyncUiState {
        return when (error) {
            is FestivalInvalidResponseException -> FestivalSyncUiState.InvalidResponse(
                reasonCode = error.reasonCode,
                diagnosticDetail = "${error.reasonCode}: ${error.message}"
            )
            is org.json.JSONException,
            is IllegalArgumentException,
            is IllegalStateException -> FestivalSyncUiState.InvalidResponse(
                reasonCode = "INVALID_RESPONSE",
                diagnosticDetail = error.javaClass.simpleName
            )
            is FestivalServerException -> FestivalSyncUiState.ServerError(
                httpStatusCode = error.httpStatusCode,
                diagnosticDetail = error.message
            )
            else -> FestivalSyncUiState.ServerError(
                httpStatusCode = null,
                diagnosticDetail = "${error?.javaClass?.simpleName ?: "NetworkError"}: ${error?.message.orEmpty()}"
            )
        }
    }

    private fun logEvent(message: String) {
        runCatching { diagnosticLogger(message) }
    }

    private fun defaultSeedMeta(recordCount: Int): FestivalSyncMetaEntity = FestivalSyncMetaEntity(
        lastSyncedEpochMs = 0L,
        lastRemoteAttemptEpochMs = 0L,
        lastSourceSummary = "IMD Rashtriya Panchang & Regional Panji (Verified Cache)",
        isOfflineFallback = false,
        consecutiveFailures = 0,
        recordCount = recordCount,
        datasetVersion = "rashtriya-panchang-2026.1",
        sourceAttribution = PublicFestivalCalendarDataSource.FULL_SOURCE_ATTRIBUTION,
        lastSyncStatusCode = "IDLE",
        lastErrorMessage = null
    )
}
