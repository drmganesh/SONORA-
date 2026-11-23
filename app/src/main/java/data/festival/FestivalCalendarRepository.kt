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
import domain.festival.FestivalRegionMode
import domain.festival.IndianFestivalRegion
import domain.festival.RegionalObservanceRule
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.LocalDate
import java.util.concurrent.TimeUnit

private const val FESTIVAL_REPO_TAG = "FestivalCalendarRepo"

data class FestivalSyncStatus(
    val lastSyncedEpochMs: Long = 0L,
    val lastSourceSummary: String = "IMD Rashtriya Panchang (Local Cache)",
    val isOfflineFallback: Boolean = false,
    val consecutiveFailures: Int = 0,
    val recordCount: Int = 0
)

/**
 * Single source of truth for SONORA's Pan-India Hindu Festival Calendar:
 * - Persists all current & upcoming festival dates in Room (`festival_calendar_entries`).
 * - Seeds verified Rashtriya Panchang + Regional Panchang catalog on first launch so offline
 *   detection works immediately without fabricating dates.
 * - Refreshes infrequently from MIT-licensed public calendar data only when stale (> 7 days)
 *   or explicitly requested by the user.
 * - Schedules battery-friendly periodic WorkManager sync with exponential backoff.
 */
class FestivalCalendarRepository(
    private val dao: FestivalCalendarDao,
    private val dataSource: FestivalCalendarDataSource = PublicFestivalCalendarDataSource(),
    private val clockEpochMs: () -> Long = { System.currentTimeMillis() }
) {
    companion object {
        /** Calendar cache staleness threshold (7 days). */
        const val STALE_AFTER_MS = 7L * 24L * 60L * 60L * 1000L

        /** WorkManager periodic sync cadence (7 days, battery-friendly). */
        const val PERIODIC_SYNC_WORK_NAME = "sonora_festival_calendar_periodic_sync"
    }

    private val syncMutex = Mutex()

    val cachedRecordsFlow: Flow<List<FestivalRecord>> =
        dao.observeAllEntries().map { entities -> entities.map { it.toDomain() } }

    val syncStatusFlow: Flow<FestivalSyncStatus> =
        dao.observeSyncMeta().map { meta ->
            if (meta == null) {
                FestivalSyncStatus()
            } else {
                FestivalSyncStatus(
                    lastSyncedEpochMs = meta.lastSyncedEpochMs,
                    lastSourceSummary = meta.lastSourceSummary,
                    isOfflineFallback = meta.isOfflineFallback,
                    consecutiveFailures = meta.consecutiveFailures,
                    recordCount = meta.recordCount
                )
            }
        }

    /**
     * Ensures Room has the verified Pan-India Panchang catalog seeded on first launch
     * without hitting the network synchronously or blocking startup.
     */
    suspend fun ensureInitialCacheSeeded() = syncMutex.withLock {
        val count = dao.countEntries()
        if (count > 0) return@withLock
        val now = clockEpochMs()
        val seed = dataSource.getVerifiedBaseCatalog(now)
        dao.replaceAllEntries(seed.map(FestivalCalendarEntity::fromDomain))
        dao.upsertSyncMeta(
            FestivalSyncMetaEntity(
                lastSyncedEpochMs = now,
                lastRemoteAttemptEpochMs = 0L,
                lastSourceSummary = "IMD Rashtriya Panchang & Regional Panji (Verified)",
                isOfflineFallback = false,
                consecutiveFailures = 0,
                recordCount = seed.size
            )
        )
    }

    /**
     * Refreshes the Room festival calendar from the public MIT feed ONLY when stale (> 7 days)
     * or when [force] is true. Gracefully keeps cached Room entries on offline/network errors.
     */
    suspend fun refreshIfStale(force: Boolean = false): Result<FestivalSyncStatus> = syncMutex.withLock {
        val now = clockEpochMs()
        val existingMeta = dao.getSyncMetaOnce()
        val existingCount = dao.countEntries()

        if (existingCount == 0) {
            val seed = dataSource.getVerifiedBaseCatalog(now)
            dao.replaceAllEntries(seed.map(FestivalCalendarEntity::fromDomain))
        }

        val isStale = existingMeta == null ||
            existingMeta.lastRemoteAttemptEpochMs == 0L ||
            (now - existingMeta.lastSyncedEpochMs) >= STALE_AFTER_MS

        if (!force && !isStale && existingCount > 0) {
            return@withLock Result.success(
                FestivalSyncStatus(
                    lastSyncedEpochMs = existingMeta!!.lastSyncedEpochMs,
                    lastSourceSummary = existingMeta.lastSourceSummary,
                    isOfflineFallback = existingMeta.isOfflineFallback,
                    consecutiveFailures = existingMeta.consecutiveFailures,
                    recordCount = existingCount
                )
            )
        }

        val fetchResult = dataSource.fetchLatestCatalog(nowEpochMs = now)
        fetchResult.fold(
            onSuccess = { payload ->
                dao.replaceAllEntries(payload.records.map(FestivalCalendarEntity::fromDomain))
                val meta = FestivalSyncMetaEntity(
                    lastSyncedEpochMs = now,
                    lastRemoteAttemptEpochMs = now,
                    lastSourceSummary = payload.sourceSummary,
                    isOfflineFallback = false,
                    consecutiveFailures = 0,
                    recordCount = payload.records.size
                )
                dao.upsertSyncMeta(meta)
                Result.success(
                    FestivalSyncStatus(
                        lastSyncedEpochMs = meta.lastSyncedEpochMs,
                        lastSourceSummary = meta.lastSourceSummary,
                        isOfflineFallback = false,
                        consecutiveFailures = 0,
                        recordCount = meta.recordCount
                    )
                )
            },
            onFailure = { error ->
                Log.w(FESTIVAL_REPO_TAG, "Public festival refresh unavailable; retaining local Room cache: ${error.message}")
                val countNow = dao.countEntries()
                val prevSynced = existingMeta?.lastSyncedEpochMs?.takeIf { it > 0L } ?: now
                val failures = (existingMeta?.consecutiveFailures ?: 0) + 1
                val meta = FestivalSyncMetaEntity(
                    lastSyncedEpochMs = prevSynced,
                    lastRemoteAttemptEpochMs = now,
                    lastSourceSummary = existingMeta?.lastSourceSummary ?: "IMD Rashtriya Panchang (Offline Cache)",
                    isOfflineFallback = true,
                    consecutiveFailures = failures,
                    recordCount = countNow
                )
                dao.upsertSyncMeta(meta)
                Result.failure(error)
            }
        )
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
        val records = dao.getAllEntriesOnce().map { it.toDomain() }
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
     * Schedules an infrequent, battery-safe WorkManager periodic refresh with exponential backoff.
     */
    fun schedulePeriodicRefresh(context: Context) {
        runCatching {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .setRequiresBatteryNotLow(true)
                .build()

            val request = PeriodicWorkRequestBuilder<FestivalCalendarRefreshWorker>(7, TimeUnit.DAYS)
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
