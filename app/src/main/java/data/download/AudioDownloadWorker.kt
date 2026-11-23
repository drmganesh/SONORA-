package data.download

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.content.Context
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import com.example.R
import com.example.SonoraApplication
import domain.download.formatByteCount
import kotlinx.coroutines.sync.withPermit

class AudioDownloadWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    companion object {
        const val KEY_TASK_ID = "task_id"
        private const val TAG = "AudioDownloadWorker"
        private const val NOTIFICATION_BASE_ID = 7400
        private const val COMPLETION_NOTIFICATION_OFFSET = 1000
    }

    override suspend fun doWork(): Result {
        val taskId = inputData.getString(KEY_TASK_ID) ?: return Result.failure()
        val coordinator = DownloadManagerCoordinator.getInstance(applicationContext)
        val initialTitle = coordinator.getTaskTitle(taskId) ?: "Downloading track"

        runCatching {
            setForeground(createForegroundInfo(taskId, initialTitle, 0L, 0L))
        }.onFailure {
            Log.w(TAG, "setForeground skipped: ${it.message}")
        }

        return coordinator.concurrencySemaphore.withPermit {
            var latestTitle = initialTitle
            val outcome = coordinator.executionEngine.executeTask(taskId) { downloaded, total, title ->
                latestTitle = title
                runCatching {
                    setForegroundAsync(createForegroundInfo(taskId, title, downloaded, total))
                }
            }
            val resolvedTitle = coordinator.getTaskTitle(taskId) ?: latestTitle
            when (outcome) {
                DownloadExecutionOutcome.Completed -> {
                    postTerminalNotification(
                        taskId = taskId,
                        title = "Downloaded • $resolvedTitle",
                        message = "Saved for offline listening in Library → Downloads."
                    )
                    Result.success()
                }
                DownloadExecutionOutcome.Paused -> Result.success()
                DownloadExecutionOutcome.Cancelled -> Result.success()
                is DownloadExecutionOutcome.Failed -> {
                    postTerminalNotification(
                        taskId = taskId,
                        title = "Download failed • $resolvedTitle",
                        message = outcome.reason
                    )
                    // Errors and retry actions are surfaced cleanly in DownloadManager UI without infinite background loops
                    Result.failure()
                }
            }
        }
    }

    private fun createForegroundInfo(
        taskId: String,
        title: String,
        downloadedBytes: Long,
        totalBytes: Long
    ): ForegroundInfo {
        val progressPercent = if (totalBytes > 0L) {
            ((downloadedBytes * 100L) / totalBytes).toInt().coerceIn(0, 100)
        } else {
            0
        }
        val contentText = when {
            totalBytes > 0L -> "${formatByteCount(downloadedBytes)} / ${formatByteCount(totalBytes)}"
            downloadedBytes > 0L -> "${formatByteCount(downloadedBytes)} downloaded"
            else -> "Preparing download..."
        }

        val notification: Notification = NotificationCompat.Builder(
            applicationContext,
            SonoraApplication.DOWNLOADS_CHANNEL_ID
        )
            .setSmallIcon(R.drawable.ic_notif_sonora_logo)
            .setContentTitle(title)
            .setContentText(contentText)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setProgress(100, progressPercent, totalBytes <= 0L)
            .build()

        val notificationId = NOTIFICATION_BASE_ID + (kotlin.math.abs(taskId.hashCode()) % 500)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(
                notificationId,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            ForegroundInfo(notificationId, notification)
        }
    }

    @SuppressLint("MissingPermission")
    private fun postTerminalNotification(taskId: String, title: String, message: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                applicationContext,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) return
        }
        runCatching {
            val manager = NotificationManagerCompat.from(applicationContext)
            if (!manager.areNotificationsEnabled()) return
            val notification = NotificationCompat.Builder(
                applicationContext,
                SonoraApplication.DOWNLOADS_CHANNEL_ID
            )
                .setSmallIcon(R.drawable.ic_notif_sonora_logo)
                .setContentTitle(title)
                .setContentText(message)
                .setStyle(NotificationCompat.BigTextStyle().bigText(message))
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build()
            val id = NOTIFICATION_BASE_ID + COMPLETION_NOTIFICATION_OFFSET +
                (kotlin.math.abs(taskId.hashCode()) % 500)
            manager.notify(id, notification)
        }
    }
}

