package data.festival

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.data.SonoraDatabase

/**
 * Infrequent, battery-respecting WorkManager worker that refreshes SONORA's cached
 * Pan-India Hindu festival calendar in Room when online and battery is not low.
 * Uses exponential backoff on transient network failures.
 */
class FestivalCalendarRefreshWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val db = SonoraDatabase.getDatabase(applicationContext)
        val repository = FestivalCalendarRepository(dao = db.festivalCalendarDao())
        repository.ensureInitialCacheSeeded()
        val syncResult = repository.refreshIfStale(force = false)
        return if (syncResult.isSuccess) {
            Result.success()
        } else if (runAttemptCount < 3) {
            Result.retry()
        } else {
            // Preserve local Room cache gracefully without draining battery on endless retries
            Result.success()
        }
    }
}
