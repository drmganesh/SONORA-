package data.video

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.MainActivity
import com.example.R
import com.example.SonoraApplication
import com.example.data.SonoraDatabase
import data.online.NetworkConnectivityMonitor
import domain.online.OnlineResult
import domain.video.ChannelNotificationPreference
import domain.video.OnlineVideoItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.TimeUnit

data class ChannelUploadDiffResult(
    val newUploadsToNotify: List<OnlineVideoItem>,
    val updatedLastKnownVideoId: String?,
    val updatedNotifiedIds: Set<String>
)

/**
 * Pure new-upload detector comparing newly extracted channel videos against
 * the locally persisted baseline video ID and previously notified video IDs.
 *
 * - On the initial baseline check (lastKnownVideoId == null), seeds the latest upload ID
 *   without spamming old videos.
 * - On subsequent checks, returns only genuinely new uploads that have never been notified.
 * - Caps stored notified IDs to the most recent 60 IDs to keep Room rows compact.
 */
object ChannelUploadDiffDetector {

    private const val MAX_STORED_NOTIFIED_IDS = 60
    private const val MAX_NOTIFICATIONS_PER_CHANNEL_PER_RUN = 3

    fun parseCsv(csv: String?): Set<String> {
        if (csv.isNullOrBlank()) return emptySet()
        return csv.split(',')
            .asSequence()
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .toCollection(LinkedHashSet())
    }

    fun toCsv(ids: Collection<String>): String =
        ids.asSequence()
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()
            .take(MAX_STORED_NOTIFIED_IDS)
            .joinToString(",")

    fun detectNewUploads(
        latestVideos: List<OnlineVideoItem>,
        lastKnownVideoId: String?,
        previouslyNotifiedIds: Set<String>
    ): ChannelUploadDiffResult {
        val cleanVideos = latestVideos
            .filter { it.videoId.isNotBlank() }
            .distinctBy { it.videoId }

        if (cleanVideos.isEmpty()) {
            return ChannelUploadDiffResult(
                newUploadsToNotify = emptyList(),
                updatedLastKnownVideoId = lastKnownVideoId,
                updatedNotifiedIds = previouslyNotifiedIds
            )
        }

        val newestId = cleanVideos.first().videoId
        // First-ever baseline sync: record current feed IDs without sending historical spam
        if (lastKnownVideoId.isNullOrBlank()) {
            val seededNotified = LinkedHashSet<String>().apply {
                cleanVideos.forEach { add(it.videoId) }
                addAll(previouslyNotifiedIds)
            }
            return ChannelUploadDiffResult(
                newUploadsToNotify = emptyList(),
                updatedLastKnownVideoId = newestId,
                updatedNotifiedIds = seededNotified.take(MAX_STORED_NOTIFIED_IDS).toSet()
            )
        }

        if (newestId == lastKnownVideoId) {
            return ChannelUploadDiffResult(
                newUploadsToNotify = emptyList(),
                updatedLastKnownVideoId = lastKnownVideoId,
                updatedNotifiedIds = previouslyNotifiedIds
            )
        }

        val freshUploads = mutableListOf<OnlineVideoItem>()
        for (video in cleanVideos) {
            if (video.videoId == lastKnownVideoId || video.videoId in previouslyNotifiedIds) break
            freshUploads.add(video)
            if (freshUploads.size >= MAX_NOTIFICATIONS_PER_CHANNEL_PER_RUN) break
        }

        val mergedNotified = LinkedHashSet<String>().apply {
            freshUploads.forEach { add(it.videoId) }
            add(newestId)
            addAll(previouslyNotifiedIds)
        }

        return ChannelUploadDiffResult(
            newUploadsToNotify = freshUploads,
            updatedLastKnownVideoId = newestId,
            updatedNotifiedIds = mergedNotified.take(MAX_STORED_NOTIFIED_IDS).toSet()
        )
    }
}

/**
 * Periodic WorkManager background worker that checks followed SONORA channels with
 * "All Notifications" enabled via existing NewPipe Extractor channel extraction.
 *
 * Respects Android background constraints, POST_NOTIFICATIONS runtime permission,
 * and deduplicates uploads via Room persistence.
 */
class ChannelUploadNotificationWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    companion object {
        private const val TAG = "ChannelUploadWorker"
        const val UNIQUE_WORK_NAME = "sonora_channel_upload_periodic_check"

        const val EXTRA_OPEN_VIDEO_ID = "sonora_notif_video_id"
        const val EXTRA_OPEN_VIDEO_URL = "sonora_notif_video_url"
        const val EXTRA_OPEN_VIDEO_TITLE = "sonora_notif_video_title"
        const val EXTRA_OPEN_VIDEO_CHANNEL = "sonora_notif_video_channel"
        const val EXTRA_OPEN_VIDEO_CHANNEL_URL = "sonora_notif_video_channel_url"
        const val EXTRA_OPEN_VIDEO_THUMBNAIL = "sonora_notif_video_thumbnail"
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            val db = SonoraDatabase.getDatabase(applicationContext)
            val dao = db.dao()
            val connectivityMonitor = NetworkConnectivityMonitor(applicationContext)
            if (!connectivityMonitor.isCurrentlyOnline()) {
                return@withContext Result.retry()
            }
            val dataSource = OnlineVideoDataSource(connectivityMonitor)
            val syncResult = ChannelUploadSyncEngine.syncEnabledChannels(
                dao = dao,
                dataSource = dataSource,
                forceRefresh = true
            )
            if (syncResult.enabledChannelCount == 0) {
                ChannelUploadNotificationScheduler.cancel(applicationContext)
                return@withContext Result.success()
            }
            if (syncResult.newNotifications.isNotEmpty() && canPostNotifications(applicationContext)) {
                syncResult.newNotifications.forEach { entity ->
                    if (!entity.videoId.isNullOrBlank() && !entity.videoUrl.isNullOrBlank()) {
                        postUploadNotification(
                            context = applicationContext,
                            channelName = entity.channelName,
                            video = OnlineVideoItem(
                                videoId = entity.videoId,
                                contentUrl = entity.videoUrl,
                                title = entity.videoTitle ?: entity.message,
                                channelName = entity.channelName,
                                channelUrl = entity.channelUrl,
                                channelAvatarUrl = entity.channelAvatarUrl,
                                thumbnailUrl = entity.thumbnailUrl,
                                uploadDateText = entity.uploadDateText
                            )
                        )
                    }
                }
            }
            Result.success()
        } catch (t: Throwable) {
            Log.w(TAG, "Periodic channel upload check failed", t)
            Result.retry()
        }
    }

    private fun canPostNotifications(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) return false
        }
        return NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    private fun postUploadNotification(
        context: Context,
        channelName: String,
        video: OnlineVideoItem
    ) {
        val openIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
            putExtra(EXTRA_OPEN_VIDEO_ID, video.videoId)
            putExtra(EXTRA_OPEN_VIDEO_URL, video.contentUrl)
            putExtra(EXTRA_OPEN_VIDEO_TITLE, video.title)
            putExtra(EXTRA_OPEN_VIDEO_CHANNEL, channelName)
            putExtra(EXTRA_OPEN_VIDEO_CHANNEL_URL, video.channelUrl)
            putExtra(EXTRA_OPEN_VIDEO_THUMBNAIL, video.thumbnailUrl)
        }
        val requestCode = ("sonora_upload_${video.videoId}").hashCode()
        val pendingIntent = PendingIntent.getActivity(
            context,
            requestCode,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val thumbBitmap = loadThumbnailBitmap(video.thumbnailUrl)

        val builder = NotificationCompat.Builder(context, SonoraApplication.CHANNEL_UPLOADS_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(channelName)
            .setContentText(video.title)
            .setSubText("New upload")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)

        if (thumbBitmap != null) {
            builder.setLargeIcon(thumbBitmap)
            builder.setStyle(
                NotificationCompat.BigPictureStyle()
                    .bigPicture(thumbBitmap)
                    .bigLargeIcon(null as Bitmap?)
                    .setBigContentTitle(channelName)
                    .setSummaryText(video.title)
            )
        } else {
            builder.setStyle(
                NotificationCompat.BigTextStyle()
                    .setBigContentTitle(channelName)
                    .bigText(video.title)
            )
        }

        try {
            NotificationManagerCompat.from(context).notify(requestCode, builder.build())
        } catch (se: SecurityException) {
            Log.w(TAG, "Notification permission revoked before posting upload alert", se)
        }
    }

    private fun loadThumbnailBitmap(urlString: String?): Bitmap? {
        val safeUrl = util.SecureUrlValidator.parseSafeHttpUrl(
            rawUrl = urlString,
            allowHttp = false,
            allowPrivateHosts = false
        ) ?: return null
        return runCatching {
            val conn = (URL(safeUrl.toString()).openConnection() as HttpURLConnection).apply {
                connectTimeout = 5_000
                readTimeout = 5_000
                instanceFollowRedirects = false
                doInput = true
                connect()
            }
            try {
                if (conn.responseCode in 200..299) {
                    val contentType = conn.contentType.orEmpty().lowercase()
                    if (contentType.isNotBlank() && !contentType.startsWith("image/")) {
                        return@runCatching null
                    }
                    val bytes = conn.inputStream.use { input ->
                        util.SecureUrlValidator.readBoundedBytes(input, util.SecureUrlValidator.MAX_IMAGE_BYTES)
                    }
                    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
                    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@runCatching null
                    var sample = 1
                    while (bounds.outWidth / sample > 640 || bounds.outHeight / sample > 360) {
                        sample *= 2
                    }
                    val opts = BitmapFactory.Options().apply {
                        inSampleSize = sample
                        inPreferredConfig = Bitmap.Config.RGB_565
                    }
                    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
                } else {
                    null
                }
            } finally {
                conn.disconnect()
            }
        }.onFailure { err ->
            Log.w(TAG, "Thumbnail download skipped: ${err.javaClass.simpleName}")
        }.getOrNull()
    }
}

data class ChannelUploadSyncResult(
    val enabledChannelCount: Int,
    val checkedChannelCount: Int,
    val newNotifications: List<VideoNotificationEntity>
)

/**
 * Shared sync engine used by both [ChannelUploadNotificationWorker] (periodic background checks)
 * and manual/foreground refresh in [OnlineVideoRepository].
 *
 * Strictly monitors ONLY SONORA-followed channels whose notification preference is [ChannelNotificationPreference.ALL],
 * deduplicates by stable video ID, and persists records into `video_notifications` without overwriting read status.
 */
object ChannelUploadSyncEngine {

    fun buildNotificationEntity(
        channel: SubscribedVideoChannelEntity,
        video: OnlineVideoItem,
        resolvedChannelAvatar: String?,
        createdAtMs: Long
    ): VideoNotificationEntity {
        val type = when {
            video.isShort || video.isLive -> domain.video.VideoNotificationType.NEW_CHANNEL_CONTENT
            else -> domain.video.VideoNotificationType.NEW_VIDEO
        }
        val cleanChannelName = channel.name.ifBlank { video.channelName }
        val message = when {
            video.isLive -> "$cleanChannelName is live: ${video.title}"
            video.isShort -> "$cleanChannelName posted a new Short: ${video.title}"
            else -> video.title
        }
        return VideoNotificationEntity(
            notificationId = "video:${video.videoId}",
            type = type.id,
            channelId = channel.channelId,
            channelName = cleanChannelName,
            channelUrl = channel.channelUrl.ifBlank { video.channelUrl.orEmpty() },
            channelAvatarUrl = resolvedChannelAvatar ?: channel.avatarUrl ?: video.channelAvatarUrl,
            videoId = video.videoId,
            videoUrl = video.contentUrl,
            videoTitle = video.title,
            thumbnailUrl = video.thumbnailUrl,
            uploadDateText = video.uploadDateText,
            message = message,
            createdAt = createdAtMs,
            isRead = false
        )
    }

    suspend fun syncEnabledChannels(
        dao: com.example.data.SonoraDao,
        dataSource: OnlineVideoDataSource,
        forceRefresh: Boolean = true
    ): ChannelUploadSyncResult = withContext(Dispatchers.IO) {
        val followedChannels = dao.getAllSubscribedVideoChannelsOnce()
        val enabledChannels = followedChannels.filter {
            ChannelNotificationPreference.fromId(it.notificationPref) == ChannelNotificationPreference.ALL
        }
        if (enabledChannels.isEmpty()) {
            return@withContext ChannelUploadSyncResult(
                enabledChannelCount = 0,
                checkedChannelCount = 0,
                newNotifications = emptyList()
            )
        }

        val nowMs = System.currentTimeMillis()
        val createdEntities = mutableListOf<VideoNotificationEntity>()
        var checkedCount = 0

        for (channelEntity in enabledChannels) {
            if (channelEntity.channelUrl.isBlank()) continue
            val res = dataSource.fetchChannelProfileDetailed(
                channelUrl = channelEntity.channelUrl,
                fallbackName = channelEntity.name,
                forceRefresh = forceRefresh
            )
            if (res is OnlineResult.Success) {
                checkedCount++
                val latestVideos = res.value.initialTabPage?.videos.orEmpty()
                    .ifEmpty { res.value.channel.videos }
                val previouslyNotified = ChannelUploadDiffDetector.parseCsv(channelEntity.notifiedVideoIdsCsv)
                val diff = ChannelUploadDiffDetector.detectNewUploads(
                    latestVideos = latestVideos,
                    lastKnownVideoId = channelEntity.lastKnownVideoId,
                    previouslyNotifiedIds = previouslyNotified
                )

                dao.updateChannelUploadCheckState(
                    channelId = channelEntity.channelId,
                    lastKnownVideoId = diff.updatedLastKnownVideoId,
                    lastCheckedAt = nowMs,
                    notifiedVideoIdsCsv = ChannelUploadDiffDetector.toCsv(diff.updatedNotifiedIds)
                )

                if (diff.newUploadsToNotify.isNotEmpty()) {
                    val channelRecords = diff.newUploadsToNotify.mapIndexed { idx, upload ->
                        buildNotificationEntity(
                            channel = channelEntity,
                            video = upload,
                            resolvedChannelAvatar = res.value.channel.avatarUrl,
                            createdAtMs = nowMs - idx
                        )
                    }
                    dao.insertVideoNotificationsIgnore(channelRecords)
                    createdEntities.addAll(channelRecords)
                }
            }
        }

        ChannelUploadSyncResult(
            enabledChannelCount = enabledChannels.size,
            checkedChannelCount = checkedCount,
            newNotifications = createdEntities
        )
    }
}

object ChannelUploadNotificationScheduler {

    private const val CHECK_INTERVAL_MINUTES = 60L

    fun syncSchedule(context: Context, hasEnabledChannels: Boolean) {
        if (hasEnabledChannels) {
            schedulePeriodicChecks(context)
        } else {
            cancel(context)
        }
    }

    fun schedulePeriodicChecks(context: Context) {
        runCatching {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()
            val request = PeriodicWorkRequestBuilder<ChannelUploadNotificationWorker>(
                CHECK_INTERVAL_MINUTES,
                TimeUnit.MINUTES
            )
                .setConstraints(constraints)
                .build()

            WorkManager.getInstance(context.applicationContext)
                .enqueueUniquePeriodicWork(
                    ChannelUploadNotificationWorker.UNIQUE_WORK_NAME,
                    ExistingPeriodicWorkPolicy.KEEP,
                    request
                )
        }
    }

    fun cancel(context: Context) {
        runCatching {
            WorkManager.getInstance(context.applicationContext)
                .cancelUniqueWork(ChannelUploadNotificationWorker.UNIQUE_WORK_NAME)
        }
    }
}
