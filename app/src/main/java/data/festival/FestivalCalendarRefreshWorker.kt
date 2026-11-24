package data.festival

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.data.SonoraDatabase

/**
 * Battery-respecting WorkManager worker that delegates to [FestivalCalendarSyncCoordinator]
 * when online and battery is not low. Never runs continuously; respects the once-per-IST-day gate
 * and uses exponential backoff on transient failures.
 */
class FestivalCalendarRefreshWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val db = SonoraDatabase.getDatabase(applicationContext)
        val repository = FestivalCalendarRepository(
            dao = db.festivalCalendarDao(),
            isNetworkAvailable = { hasActiveInternet(applicationContext) }
        )
        val result = repository.syncCoordinator.syncFromBackgroundWorker()
        return when (result.uiState) {
            is FestivalSyncUiState.ServerError -> if (runAttemptCount < 3) Result.retry() else Result.success()
            else -> Result.success()
        }
    }

    private fun hasActiveInternet(context: Context): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }
}
