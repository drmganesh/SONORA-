package data.cache

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

/**
 * WorkManager worker scheduled 5 minutes after both SONORA's Music and Video players close.
 * Clears temporary Video and Shorts cache segments only if both players remain closed,
 * preserving Music cache (managed by its own storage limit) and permanent downloads.
 */
class SonoraVideoCacheCleanupWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        return try {
            val manager = SonoraMediaCacheManager.getInstance(applicationContext)
            val cleared = manager.executeScheduledVideoCleanupIfPlayersClosed()
            Log.d("SonoraVideoCacheCleanup", "Scheduled video/Shorts cleanup executed: cleared=$cleared")
            Result.success()
        } catch (t: Throwable) {
            Log.w("SonoraVideoCacheCleanup", "Delayed video cache cleanup skipped due to error", t)
            Result.success()
        }
    }

    companion object {
        const val UNIQUE_WORK_NAME = "sonora_delayed_video_shorts_cache_cleanup"
    }
}
