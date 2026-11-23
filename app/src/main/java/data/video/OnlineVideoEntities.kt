package data.video

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import domain.video.ChannelNotificationPreference
import domain.video.OnlineVideoChannel
import domain.video.OnlineVideoItem

/**
 * Stores stable video identifiers and metadata for Watch Later, Saved Videos, and Video History.
 * Never persists temporary/expiring stream URLs.
 */
@Entity(
    tableName = "saved_videos",
    indices = [
        Index(value = ["isWatchLater", "watchLaterAt"]),
        Index(value = ["isSaved", "savedAt"]),
        Index(value = ["lastWatchedAt"]),
        Index(value = ["localRating", "likedAt"])
    ]
)
data class SavedVideoEntity(
    @PrimaryKey val videoId: String,
    val stableId: Long,
    val contentUrl: String,
    val title: String,
    val channelName: String,
    val channelUrl: String?,
    val channelAvatarUrl: String?,
    val thumbnailUrl: String?,
    val durationSeconds: Long,
    val viewCount: Long?,
    val uploadDateText: String?,
    val isLive: Boolean = false,
    val providerKey: String = "youtube",
    val providerName: String = "YouTube",
    val isWatchLater: Boolean = false,
    val isSaved: Boolean = false,
    val lastPositionMs: Long = 0L,
    val watchCount: Int = 0,
    val lastWatchedAt: Long = 0L,
    val savedAt: Long = 0L,
    val watchLaterAt: Long = 0L,
    val localRating: String = "NONE",
    val likedAt: Long = 0L
) {
    fun toDomain(): OnlineVideoItem = OnlineVideoItem(
        videoId = videoId,
        stableId = stableId,
        contentUrl = contentUrl,
        title = title,
        channelName = channelName,
        channelUrl = channelUrl,
        channelAvatarUrl = channelAvatarUrl,
        thumbnailUrl = thumbnailUrl,
        durationSeconds = durationSeconds,
        viewCount = viewCount,
        uploadDateText = uploadDateText,
        isLive = isLive,
        providerKey = providerKey,
        providerName = providerName,
        isWatchLater = isWatchLater,
        isSaved = isSaved,
        lastPositionMs = lastPositionMs,
        lastWatchedAt = lastWatchedAt,
        likedAt = likedAt
    )
}

@Entity(
    tableName = "subscribed_video_channels",
    indices = [Index(value = ["subscribedAt"])]
)
data class SubscribedVideoChannelEntity(
    @PrimaryKey val channelId: String,
    val stableId: Long,
    val channelUrl: String,
    val name: String,
    val avatarUrl: String?,
    val subscriberCount: Long?,
    val isProviderSynced: Boolean = false,
    val notificationPref: String = ChannelNotificationPreference.NONE.id,
    val subscribedAt: Long = System.currentTimeMillis(),
    val lastKnownVideoId: String? = null,
    val lastCheckedAt: Long = 0L,
    val notifiedVideoIdsCsv: String = ""
) {
    fun toDomain(): OnlineVideoChannel = OnlineVideoChannel(
        channelId = channelId,
        stableId = stableId,
        channelUrl = channelUrl,
        name = name,
        avatarUrl = avatarUrl,
        subscriberCount = subscriberCount,
        isSubscribedLocal = true,
        isSubscribedOnProvider = isProviderSynced,
        notificationPreference = ChannelNotificationPreference.fromId(notificationPref)
    )
}

@Entity(
    tableName = "video_playlists",
    indices = [Index(value = ["updatedAt"])]
)
data class VideoPlaylistEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val name: String,
    val externalUrl: String? = null,
    val thumbnailUrl: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "video_playlist_items",
    primaryKeys = ["playlistId", "videoId"],
    indices = [Index(value = ["playlistId", "orderIndex"])]
)
data class VideoPlaylistItemCrossRef(
    val playlistId: Long,
    val videoId: String,
    val orderIndex: Int,
    val addedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "video_search_history")
data class VideoSearchHistoryEntity(
    @PrimaryKey val query: String,
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * Local persistent notification record for SONORA-followed channels.
 * Keyed deterministically by stable notificationId (e.g., "video:<videoId>") to prevent duplicates
 * and preserve read/unread state across app restarts.
 */
@Entity(
    tableName = "video_notifications",
    indices = [
        Index(value = ["createdAt"]),
        Index(value = ["isRead", "createdAt"]),
        Index(value = ["channelId"])
    ]
)
data class VideoNotificationEntity(
    @PrimaryKey val notificationId: String,
    val type: String = domain.video.VideoNotificationType.NEW_VIDEO.id,
    val channelId: String,
    val channelName: String,
    val channelUrl: String,
    val channelAvatarUrl: String? = null,
    val videoId: String? = null,
    val videoUrl: String? = null,
    val videoTitle: String? = null,
    val thumbnailUrl: String? = null,
    val uploadDateText: String? = null,
    val message: String,
    val createdAt: Long = System.currentTimeMillis(),
    val isRead: Boolean = false
) {
    fun toDomain(): domain.video.VideoNotificationItem = domain.video.VideoNotificationItem(
        notificationId = notificationId,
        type = domain.video.VideoNotificationType.fromId(type),
        channelId = channelId,
        channelName = channelName,
        channelUrl = channelUrl,
        channelAvatarUrl = channelAvatarUrl,
        videoId = videoId,
        videoUrl = videoUrl,
        videoTitle = videoTitle,
        thumbnailUrl = thumbnailUrl,
        uploadDateText = uploadDateText,
        message = message,
        createdAt = createdAt,
        isRead = isRead
    )
}

