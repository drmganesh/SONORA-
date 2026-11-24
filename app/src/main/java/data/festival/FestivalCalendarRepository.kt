package data.festival

import android.content.Context
import android.util.Log
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import domain.festival.ActiveFestivalResolution
import domain.festival.FestivalDateResolver
import domain.festival.FestivalRecord
import domain.festival.FestivalRegionFilter
import domain.festival.FestivalRegionMode
import domain.festival.IndianFestivalRegion
import domain.festival.RegionalObservanceRule
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import java.util.concurrent.TimeUnit

private const val FESTIVAL_REPO_TAG = "FestivalCalendarRepo"

data class FestivalSyncStatus(
    val lastSyncedEpochMs: Long = 0L,
    val lastAttemptedEpochMs: Long = 0L,
    val lastSourceSummary: String = "IMD Rashtriya Panchang (Verified Cache)",
    val sourceAttribution: String = PublicFestivalCalendarDataSource.FULL_SOURCE_ATTRIBUTION,
    val datasetVersion: String = "rashtriya-panchang-2026.1",
    val isOfflineFallback: Boolean = false,
    val consecutiveFailures: Int = 0,
    val recordCount: Int = 0,
    val lastSyncStatusCode: String = "IDLE",
    val lastErrorMessage: String? = null
) {
    /** Separated explicit aliases for sync status clarity. */
    val lastSuccessfulSyncAt: Long get() = lastSyncedEpochMs
    val lastAttemptAt: Long get() = lastAttemptedEpochMs
    val syncStatus: String get() = lastSyncStatusCode
    val lastSyncError: String? get() = lastErrorMessage
    val cachedFestivalCount: Int get() = recordCount

    fun isSyncedToday(nowEpochMs: Long = System.currentTimeMillis()): Boolean =
        FestivalCalendarSyncCoordinator.hasSyncedSuccessfullyToday(lastSyncedEpochMs, nowEpochMs)
}

/**
 * Single source of truth for SONORA's Pan-India Hindu Festival Calendar:
 * - Persists festival dates & sync metadata in Room (`festival_calendar_entries`, `festival_sync_meta`).
 * - Delegates ALL synchronization (automatic daily app-open sync, manual Sync Now, WorkManager) to
 *   [FestivalCalendarSyncCoordinator] so duplicate network calls are impossible.
 * - Exposes upcoming cached festivals filtered for the user's selected region.
 */
class FestivalCalendarRepository(
    private val dao: FestivalCalendarDao,
    private val dataSource: FestivalCalendarDataSource = PublicFestivalCalendarDataSource(),
    private val isNetworkAvailable: () -> Boolean = { true },
    private val clockEpochMs: () -> Long = { System.currentTimeMillis() }
) {
    companion object {
        /** Single unique WorkManager periodic sync name (no duplicate jobs). */
        const val PERIODIC_SYNC_WORK_NAME = "sonora_festival_calendar_periodic_sync"

        /**
         * Selects upcoming cached festivals for the user's region starting from [fromDate] (inclusive).
         * Filters out any invalid or malformed entries safely.
         */
        fun filterUpcomingFestivals(
            records: List<FestivalRecord>,
            fromDate: LocalDate,
            regionMode: FestivalRegionMode,
            selectedState: IndianFestivalRegion,
            includeRegionalInAllIndia: Boolean,
            nowEpochMs: Long = System.currentTimeMillis(),
            limit: Int = 20
        ): List<FestivalRecord> {
            return records
                .asSequence()
                .filterNot { it.isExpiredAt(nowEpochMs) }
                .filterNot { PublicFestivalCalendarDataSource.looksLikeRawJsonOrCode(it.canonicalName) }
                .filter { record ->
                    FestivalRegionFilter.matches(
                        record = record,
                        regionMode = regionMode,
                        selectedState = selectedState,
                        includeRegionalInAllIndia = includeRegionalInAllIndia
                    )
                }
                .filter { record ->
                    val end = record.endDateOrNull() ?: record.startDateOrNull() ?: return@filter false
                    !end.isBefore(fromDate)
                }
                .sortedWith(
                    compareBy<FestivalRecord> { it.startDateIso }
                        .thenByDescending { it.sourcePriority.rank }
                        .thenBy { it.canonicalName }
                )
                .distinctBy { "${it.festivalSlug}_${it.startDateIso}" }
                .take(limit)
                .toList()
        }
    }

    val syncCoordinator = FestivalCalendarSyncCoordinator(
        dao = dao,
        dataSource = dataSource,
        isNetworkAvailable = isNetworkAvailable,
        clockEpochMs = clockEpochMs
    )

    val syncUiState: StateFlow<FestivalSyncUiState> = syncCoordinator.syncUiState

    val cachedRecordsFlow: Flow<List<FestivalRecord>> =
        dao.observeAllEntries().map { entities ->
            entities
                .map { it.toDomain() }
                .filterNot { PublicFestivalCalendarDataSource.looksLikeRawJsonOrCode(it.canonicalName) }
        }

    val syncStatusFlow: Flow<FestivalSyncStatus> =
        dao.observeSyncMeta().map { meta ->
            if (meta == null) {
                FestivalSyncStatus()
            } else {
                FestivalSyncStatus(
                    lastSyncedEpochMs = meta.lastSyncedEpochMs,
                    lastAttemptedEpochMs = meta.lastRemoteAttemptEpochMs,
                    lastSourceSummary = meta.lastSourceSummary,
                    sourceAttribution = meta.sourceAttribution,
                    datasetVersion = meta.datasetVersion,
                    isOfflineFallback = meta.isOfflineFallback,
                    consecutiveFailures = meta.consecutiveFailures,
                    recordCount = meta.recordCount,
                    lastSyncStatusCode = meta.lastSyncStatusCode,
                    // Sanitize persisted error string so legacy raw JSON strings from prior builds never leak
                    lastErrorMessage = PublicFestivalCalendarDataSource.sanitizeDiagnosticText(meta.lastErrorMessage)
                )
            }
        }

    suspend fun ensureInitialCacheSeeded() {
        syncCoordinator.ensureLocalSeedPresent()
    }

    /**
     * Automatic daily sync when SONORA opens (max once per `Asia/Kolkata` calendar day after success).
     */
    suspend fun syncOnAppOpenIfNeeded(automaticDailySyncEnabled: Boolean = true): FestivalSyncExecutionResult =
        syncCoordinator.syncOnAppOpenIfNeeded(automaticDailySyncEnabled = automaticDailySyncEnabled)

    /**
     * Manual "Sync Now" triggered by user.
     */
    suspend fun syncNowManually(): FestivalSyncExecutionResult =
        syncCoordinator.syncNowManually()

    /**
     * Backward-compatible wrapper delegating to the single sync coordinator.
     */
    suspend fun refreshIfStale(force: Boolean = false): Result<FestivalSyncStatus> {
        val execution = if (force) {
            syncCoordinator.syncNowManually()
        } else {
            syncCoordinator.syncOnAppOpenIfNeeded(automaticDailySyncEnabled = true)
        }
        val meta = dao.getSyncMetaOnce()
        val status = FestivalSyncStatus(
            lastSyncedEpochMs = meta?.lastSyncedEpochMs ?: 0L,
            lastAttemptedEpochMs = meta?.lastRemoteAttemptEpochMs ?: 0L,
            lastSourceSummary = meta?.lastSourceSummary ?: "IMD Rashtriya Panchang (Verified Cache)",
            sourceAttribution = meta?.sourceAttribution ?: PublicFestivalCalendarDataSource.FULL_SOURCE_ATTRIBUTION,
            datasetVersion = meta?.datasetVersion ?: "rashtriya-panchang-2026.1",
            isOfflineFallback = meta?.isOfflineFallback == true,
            consecutiveFailures = meta?.consecutiveFailures ?: 0,
            recordCount = meta?.recordCount ?: dao.countEntries(),
            lastSyncStatusCode = meta?.lastSyncStatusCode ?: execution.uiState.statusCode,
            lastErrorMessage = PublicFestivalCalendarDataSource.sanitizeDiagnosticText(meta?.lastErrorMessage)
        )
        return if (execution.uiState.isFailure) {
            Result.failure(RuntimeException(execution.uiState.message))
        } else {
            Result.success(status)
        }
    }

    /**
     * Resolves active festivals for [date] purely from cached Room records.
     */
    suspend fun resolveForDateOnce(
        date: LocalDate,
        regionMode: FestivalRegionMode,
        selectedState: IndianFestivalRegion,
        includeRegionalInAllIndia: Boolean,
        observanceRule: RegionalObservanceRule = RegionalObservanceRule.DEFAULT
    ): ActiveFestivalResolution {
        val records = dao.getAllEntriesOnce()
            .map { it.toDomain() }
            .filterNot { PublicFestivalCalendarDataSource.looksLikeRawJsonOrCode(it.canonicalName) }
        val meta = dao.getSyncMetaOnce()
        return FestivalDateResolver.resolveForDate(
            records = records,
            date = date,
            regionMode = regionMode,
            selectedState = selectedState,
            includeRegionalInAllIndia = includeRegionalInAllIndia,
            observanceRule = observanceRule,
            nowEpochMs = clockEpochMs(),
            lastUpdatedEpochMs = meta?.lastSyncedEpochMs ?: 0L,
            isFromOfflineCache = meta?.isOfflineFallback == true
        )
    }

    /**
     * Schedules a single unique WorkManager periodic sync (daily safety fallback when online & battery not low)
     * with `ExistingPeriodicWorkPolicy.KEEP` so duplicate jobs are never created.
     */
    fun schedulePeriodicRefresh(context: Context) {
        runCatching {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .setRequiresBatteryNotLow(true)
                .build()

            val request = PeriodicWorkRequestBuilder<FestivalCalendarRefreshWorker>(1, TimeUnit.DAYS)
                .setConstraints(constraints)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.MINUTES)
                .build()

            WorkManager.getInstance(context.applicationContext).enqueueUniquePeriodicWork(
                PERIODIC_SYNC_WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }.onFailure { e ->
            Log.w(FESTIVAL_REPO_TAG, "WorkManager scheduling skipped: ${e.message}")
        }
    }
}
