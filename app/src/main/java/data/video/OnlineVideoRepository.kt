package data.video

import com.example.data.SonoraDao
import domain.online.OnlineResult
import domain.video.ChannelNotificationPreference
import domain.video.ChannelTabType
import domain.video.OnlineVideoChannel
import domain.video.OnlineVideoItem
import domain.video.OnlineVideoPlaylistSummary
import domain.video.ProviderAuthState
import domain.video.ResolvedVideoDetails
import domain.video.VideoAccountActionResult
import domain.video.VideoHomeSectionType
import domain.video.VideoRatingState
import domain.video.VideoRegionalLanguage
import domain.video.VideoSearchFilter
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

/**
 * Unified repository for SONORA Online Video:
 * - NewPipe discovery, search, channel profiles, and stream resolution
 * - Local Room persistence for Watch Later, Video History, Saved Videos, Subscribed Channels, Video Playlists, and Video Search History
 * - Official YouTube Data API v3 account actions (Like, Dislike, Subscribe, Notification Bell capability checks)
 */
class OnlineVideoRepository(
    private val dao: SonoraDao,
    private val dataSource: OnlineVideoDataSource,
    private val accountActionProvider: VideoAccountActionProvider = YouTubeAccountActionClient()
) {
    val supportsNotificationPreferenceApi: Boolean
        get() = accountActionProvider.supportsNotificationPreferenceApi

    val watchLaterVideosFlow: Flow<List<OnlineVideoItem>> =
        dao.getWatchLaterVideosFlow().map { list -> list.map { it.toDomain() } }

    val savedVideosFlow: Flow<List<OnlineVideoItem>> =
        dao.getSavedVideosFlow().map { list -> list.map { it.toDomain() } }

    val videoHistoryFlow: Flow<List<OnlineVideoItem>> =
        dao.getVideoHistoryFlow().map { list -> list.map { it.toDomain() } }

    val likedVideosFlow: Flow<List<OnlineVideoItem>> =
        dao.getLikedVideosFlow().map { list -> list.map { it.toDomain() } }

    val subscribedChannelsFlow: Flow<List<OnlineVideoChannel>> =
        dao.getSubscribedVideoChannelsFlow().map { list -> list.map { it.toDomain() } }

    val videoSearchHistoryFlow: Flow<List<String>> =
        dao.getVideoSearchHistoryFlow().map { list -> list.map { it.query } }

    val videoNotificationsFlow: Flow<List<domain.video.VideoNotificationItem>> =
        dao.getAllVideoNotificationsFlow().map { list -> list.map { it.toDomain() } }

    val unreadVideoNotificationCountFlow: Flow<Int> =
        dao.getUnreadVideoNotificationCountFlow()

    val videoPlaylistsFlow: Flow<List<OnlineVideoPlaylistSummary>> = combine(
        dao.getAllVideoPlaylistsFlow(),
        dao.getAllVideoPlaylistItemsFlow(),
        dao.getAllStoredVideosFlow()
    ) { playlists, crossRefs, storedVideos ->
        val videoMap = storedVideos.associateBy({ it.videoId }, { it.toDomain() })
        val grouped = crossRefs.groupBy { it.playlistId }
        playlists.map { pl ->
            val items = grouped[pl.id].orEmpty()
                .sortedBy { it.orderIndex }
                .mapNotNull { videoMap[it.videoId] }
            OnlineVideoPlaylistSummary(
                id = pl.id,
                externalUrl = pl.externalUrl,
                name = pl.name,
                thumbnailUrl = pl.thumbnailUrl ?: items.firstOrNull()?.thumbnailUrl,
                videoCount = items.size.toLong(),
                createdAt = pl.createdAt,
                updatedAt = pl.updatedAt,
                videos = items
            )
        }
    }

    fun clearDiscoveryCaches() = dataSource.clearDiscoveryCaches()
    fun clearDetailsCache(videoId: String? = null) = dataSource.clearDetailsCache(videoId)

    suspend fun fetchHomeSection(
        section: VideoHomeSectionType,
        countryCode: String,
        languageCode: String,
        regionalLanguage: VideoRegionalLanguage,
        recentWatchHint: String? = null,
        forceRefresh: Boolean = false
    ): OnlineResult<VideoFeedPage> = dataSource.fetchHomeSection(
        section = section,
        countryCode = countryCode,
        languageCode = languageCode,
        regionalLanguage = regionalLanguage,
        recentWatchHint = recentWatchHint,
        forceRefresh = forceRefresh
    )

    suspend fun loadMoreHomeSection(nextPageToken: String): OnlineResult<VideoFeedPage> =
        dataSource.loadMoreHomeSection(nextPageToken)

    suspend fun getVideoSuggestions(query: String): List<String> =
        dataSource.getVideoSuggestions(query)

    suspend fun searchVideos(query: String, filter: VideoSearchFilter): OnlineResult<VideoSearchPage> {
        val trimmed = query.trim()
        if (trimmed.isNotBlank()) {
            dao.upsertVideoSearchHistory(VideoSearchHistoryEntity(query = trimmed, timestamp = System.currentTimeMillis()))
        }
        return dataSource.searchOnlineVideos(trimmed, filter)
    }

    suspend fun loadMoreSearch(nextPageToken: String): OnlineResult<VideoSearchPage> =
        dataSource.loadMoreVideoSearch(nextPageToken)

    suspend fun removeSearchHistory(query: String) = dao.deleteVideoSearchHistory(query)
    suspend fun clearSearchHistory() = dao.clearVideoSearchHistory()

    suspend fun resolveVideoDetails(video: OnlineVideoItem, forceRefresh: Boolean = false): OnlineResult<ResolvedVideoDetails> =
        dataSource.resolveVideoDetails(video, forceRefresh)

    suspend fun fetchChannelProfile(channelUrl: String, fallbackName: String): OnlineResult<OnlineVideoChannel> =
        dataSource.fetchChannelProfile(channelUrl, fallbackName)

    suspend fun fetchChannelProfileDetailed(
        channelUrl: String,
        fallbackName: String,
        forceRefresh: Boolean = false
    ): OnlineResult<ChannelProfileResult> =
        dataSource.fetchChannelProfileDetailed(channelUrl, fallbackName, forceRefresh)

    suspend fun fetchChannelTab(
        channelUrl: String,
        channelId: String,
        tabType: ChannelTabType,
        forceRefresh: Boolean = false
    ): OnlineResult<ChannelTabPageResult> =
        dataSource.fetchChannelTab(channelUrl, channelId, tabType, forceRefresh)

    suspend fun loadMoreChannelTab(nextPageToken: String): OnlineResult<ChannelTabPageResult> =
        dataSource.loadMoreChannelTab(nextPageToken)

    fun clearChannelCache(channelUrlOrId: String? = null) = dataSource.clearChannelCache(channelUrlOrId)

    suspend fun fetchExternalPlaylist(playlistUrl: String, fallbackTitle: String): OnlineResult<OnlineVideoPlaylistSummary> =
        dataSource.fetchExternalPlaylist(playlistUrl, fallbackTitle)

    suspend fun fetchExternalPlaylistDetailed(
        playlistUrl: String,
        fallbackTitle: String,
        forceRefresh: Boolean = false
    ): OnlineResult<ExternalPlaylistPageResult> =
        dataSource.fetchExternalPlaylistDetailed(playlistUrl, fallbackTitle, forceRefresh)

    suspend fun loadMoreExternalPlaylist(nextPageToken: String): OnlineResult<Pair<List<OnlineVideoItem>, String?>> =
        dataSource.loadMoreExternalPlaylist(nextPageToken)

    // --- Watch Later, Saved Videos, History (Room persistence of stable identifiers only) ---

    suspend fun recordVideoWatched(video: OnlineVideoItem, positionMs: Long = 0L) {
        val now = System.currentTimeMillis()
        val existing = dao.getSavedVideoById(video.videoId)
        val merged = if (existing != null) {
            existing.copy(
                title = video.title.ifBlank { existing.title },
                channelName = video.channelName.ifBlank { existing.channelName },
                channelUrl = video.channelUrl ?: existing.channelUrl,
                channelAvatarUrl = video.channelAvatarUrl ?: existing.channelAvatarUrl,
                thumbnailUrl = video.thumbnailUrl ?: existing.thumbnailUrl,
                durationSeconds = video.durationSeconds.takeIf { it > 0L } ?: existing.durationSeconds,
                viewCount = video.viewCount ?: existing.viewCount,
                uploadDateText = video.uploadDateText ?: existing.uploadDateText,
                lastPositionMs = positionMs.coerceAtLeast(0L),
                watchCount = existing.watchCount + 1,
                lastWatchedAt = now
            )
        } else {
            video.toEntity(lastPositionMs = positionMs, watchCount = 1, lastWatchedAt = now)
        }
        dao.upsertSavedVideo(merged)
    }

    suspend fun updateWatchProgress(videoId: String, positionMs: Long) {
        dao.updateVideoWatchProgress(videoId, positionMs.coerceAtLeast(0L), System.currentTimeMillis())
    }

    suspend fun toggleWatchLater(video: OnlineVideoItem): Boolean {
        val existing = dao.getSavedVideoById(video.videoId)
        val next = !(existing?.isWatchLater ?: false)
        val now = System.currentTimeMillis()
        if (existing != null) {
            dao.updateVideoWatchLater(video.videoId, next, if (next) now else 0L)
            if (!next) dao.pruneUnreferencedVideos()
        } else if (next) {
            dao.upsertSavedVideo(video.toEntity(isWatchLater = true, watchLaterAt = now))
        }
        return next
    }

    suspend fun toggleSaveVideo(video: OnlineVideoItem): Boolean {
        val existing = dao.getSavedVideoById(video.videoId)
        val next = !(existing?.isSaved ?: false)
        val now = System.currentTimeMillis()
        if (existing != null) {
            dao.updateVideoSaved(video.videoId, next, if (next) now else 0L)
            if (!next) dao.pruneUnreferencedVideos()
        } else if (next) {
            dao.upsertSavedVideo(video.toEntity(isSaved = true, savedAt = now))
        }
        return next
    }

    suspend fun removeFromHistory(videoId: String) {
        dao.removeVideoFromHistory(videoId)
        dao.pruneUnreferencedVideos()
    }

    suspend fun clearVideoHistory() {
        dao.clearAllVideoHistory()
        dao.pruneUnreferencedVideos()
    }

    // --- SONORA Local Video Like / Dislike (mutually exclusive, persisted in Room) ---

    suspend fun getLocalVideoRating(videoId: String): VideoRatingState {
        val stored = dao.getSavedVideoById(videoId) ?: return VideoRatingState.NONE
        return VideoRatingState.fromName(stored.localRating)
    }

    suspend fun setLocalVideoRating(video: OnlineVideoItem, targetRating: VideoRatingState): VideoRatingState {
        val existing = dao.getSavedVideoById(video.videoId)
        val likedTimestamp = if (targetRating == VideoRatingState.LIKED) System.currentTimeMillis() else 0L
        if (existing != null) {
            if (targetRating == VideoRatingState.LIKED) {
                dao.upsertSavedVideo(
                    existing.copy(
                        title = video.title.ifBlank { existing.title },
                        channelName = video.channelName.ifBlank { existing.channelName },
                        channelUrl = video.channelUrl ?: existing.channelUrl,
                        channelAvatarUrl = video.channelAvatarUrl ?: existing.channelAvatarUrl,
                        thumbnailUrl = video.thumbnailUrl ?: existing.thumbnailUrl,
                        durationSeconds = if (video.durationSeconds > 0L) video.durationSeconds else existing.durationSeconds,
                        viewCount = video.viewCount ?: existing.viewCount,
                        uploadDateText = video.uploadDateText ?: existing.uploadDateText,
                        localRating = targetRating.name,
                        likedAt = likedTimestamp
                    )
                )
            } else {
                dao.updateVideoLocalRatingWithTimestamp(video.videoId, targetRating.name, 0L)
                if (targetRating == VideoRatingState.NONE) {
                    dao.pruneUnreferencedVideos()
                }
            }
        } else if (targetRating != VideoRatingState.NONE) {
            dao.upsertSavedVideo(
                video.toEntity(
                    localRating = targetRating.name,
                    likedAt = likedTimestamp
                )
            )
        }

        // Sync local recommendation signals cleanly
        val likeKey = "${VideoFeedbackType.LIKED_VIDEO}:${video.videoId}"
        val dislikeKey = "${VideoFeedbackType.DISLIKED_VIDEO}:${video.videoId}"
        when (targetRating) {
            VideoRatingState.LIKED -> {
                dao.deleteVideoRecommendationFeedback(dislikeKey)
                dao.upsertVideoRecommendationFeedback(
                    VideoRecommendationFeedbackEntity(
                        feedbackKey = likeKey,
                        feedbackType = VideoFeedbackType.LIKED_VIDEO,
                        targetValue = video.videoId
                    )
                )
            }
            VideoRatingState.DISLIKED -> {
                dao.deleteVideoRecommendationFeedback(likeKey)
                dao.upsertVideoRecommendationFeedback(
                    VideoRecommendationFeedbackEntity(
                        feedbackKey = dislikeKey,
                        feedbackType = VideoFeedbackType.DISLIKED_VIDEO,
                        targetValue = video.videoId
                    )
                )
            }
            VideoRatingState.NONE -> {
                dao.deleteVideoRecommendationFeedback(likeKey)
                dao.deleteVideoRecommendationFeedback(dislikeKey)
            }
        }
        return targetRating
    }

    suspend fun unlikeVideo(videoId: String) {
        dao.updateVideoLocalRatingWithTimestamp(videoId, VideoRatingState.NONE.name, 0L)
        dao.deleteVideoRecommendationFeedback("${VideoFeedbackType.LIKED_VIDEO}:$videoId")
        dao.pruneUnreferencedVideos()
    }

    suspend fun clearAllLikedVideos() {
        dao.clearAllLikedVideos()
        dao.clearVideoRecommendationFeedbackByType(VideoFeedbackType.LIKED_VIDEO)
        dao.pruneUnreferencedVideos()
    }

    // --- Video Playlists ---

    suspend fun createVideoPlaylist(name: String, initialVideo: OnlineVideoItem? = null): Long {
        val clean = name.trim().ifBlank { "My Video Playlist" }
        val now = System.currentTimeMillis()
        val id = dao.insertVideoPlaylist(
            VideoPlaylistEntity(
                name = clean,
                thumbnailUrl = initialVideo?.thumbnailUrl,
                createdAt = now,
                updatedAt = now
            )
        )
        if (initialVideo != null) {
            addVideoToPlaylist(id, initialVideo)
        }
        return id
    }

    suspend fun addVideoToPlaylist(playlistId: Long, video: OnlineVideoItem) {
        val existing = dao.getSavedVideoById(video.videoId)
        if (existing == null) {
            dao.upsertSavedVideo(video.toEntity())
        }
        val nextIdx = dao.getMaxVideoPlaylistOrderIndex(playlistId) + 1
        dao.insertVideoPlaylistItem(
            VideoPlaylistItemCrossRef(
                playlistId = playlistId,
                videoId = video.videoId,
                orderIndex = nextIdx,
                addedAt = System.currentTimeMillis()
            )
        )
        video.thumbnailUrl?.let { dao.updateVideoPlaylistThumbnail(playlistId, it) }
    }

    suspend fun removeVideoFromPlaylist(playlistId: Long, videoId: String) {
        dao.removeVideoFromPlaylist(playlistId, videoId)
        dao.pruneUnreferencedVideos()
    }

    suspend fun renameVideoPlaylist(playlistId: Long, newName: String) {
        val clean = newName.trim()
        if (clean.isNotBlank()) dao.renameVideoPlaylist(playlistId, clean)
    }

    suspend fun deleteVideoPlaylist(playlistId: Long) {
        dao.clearVideoPlaylistItems(playlistId)
        dao.deleteVideoPlaylist(playlistId)
        dao.pruneUnreferencedVideos()
    }

    // --- Subscribed Channels & Official Provider Actions ---

    suspend fun saveLocalChannelBookmark(
        channel: OnlineVideoChannel,
        providerSynced: Boolean = false,
        initialLastKnownVideoId: String? = null
    ) {
        val existing = dao.getSubscribedVideoChannelById(channel.channelId)
        val preservedPref = existing?.notificationPref ?: channel.notificationPreference.id
        val preservedLastId = existing?.lastKnownVideoId ?: initialLastKnownVideoId
        val preservedNotifiedCsv = existing?.notifiedVideoIdsCsv?.takeIf { it.isNotBlank() }
            ?: initialLastKnownVideoId.orEmpty()
        dao.upsertSubscribedVideoChannel(
            SubscribedVideoChannelEntity(
                channelId = channel.channelId,
                stableId = channel.stableId,
                channelUrl = channel.channelUrl,
                name = channel.name,
                avatarUrl = channel.avatarUrl ?: existing?.avatarUrl,
                subscriberCount = channel.subscriberCount ?: existing?.subscriberCount,
                isProviderSynced = providerSynced,
                notificationPref = preservedPref,
                subscribedAt = existing?.subscribedAt ?: System.currentTimeMillis(),
                lastKnownVideoId = preservedLastId,
                lastCheckedAt = existing?.lastCheckedAt ?: if (preservedLastId != null) System.currentTimeMillis() else 0L,
                notifiedVideoIdsCsv = preservedNotifiedCsv
            )
        )
    }

    suspend fun updateLocalChannelNotificationPreference(
        channelId: String,
        preference: ChannelNotificationPreference
    ) {
        dao.updateChannelNotificationPref(channelId, preference.id)
    }

    suspend fun seedChannelBaselineUploadIfMissing(channelId: String, channelUrl: String, fallbackName: String) {
        val entity = dao.getSubscribedVideoChannelById(channelId) ?: return
        if (!entity.lastKnownVideoId.isNullOrBlank()) return
        val res = dataSource.fetchChannelProfileDetailed(channelUrl, fallbackName, forceRefresh = false)
        if (res is OnlineResult.Success) {
            val newestId = res.value.initialTabPage?.videos?.firstOrNull()?.videoId
                ?: res.value.channel.videos.firstOrNull()?.videoId
            if (!newestId.isNullOrBlank()) {
                val existingNotified = ChannelUploadDiffDetector.parseCsv(entity.notifiedVideoIdsCsv)
                dao.updateChannelUploadCheckState(
                    channelId = channelId,
                    lastKnownVideoId = newestId,
                    lastCheckedAt = System.currentTimeMillis(),
                    notifiedVideoIdsCsv = ChannelUploadDiffDetector.toCsv(existingNotified + newestId)
                )
            }
        }
    }

    suspend fun removeLocalChannelBookmark(channelId: String) {
        dao.deleteSubscribedVideoChannel(channelId)
    }

    // --- SONORA Followed-Channel Notification Center ---

    suspend fun insertVideoNotifications(items: List<VideoNotificationEntity>) {
        if (items.isNotEmpty()) {
            dao.insertVideoNotificationsIgnore(items)
        }
    }

    suspend fun markVideoNotificationRead(notificationId: String) {
        dao.markVideoNotificationRead(notificationId)
    }

    suspend fun markAllVideoNotificationsRead() {
        dao.markAllVideoNotificationsRead()
    }

    suspend fun deleteVideoNotification(notificationId: String) {
        dao.deleteVideoNotification(notificationId)
    }

    suspend fun clearAllVideoNotifications() {
        dao.clearAllVideoNotifications()
    }

    suspend fun syncFollowedChannelNotificationsNow(forceRefresh: Boolean = true): ChannelUploadSyncResult =
        ChannelUploadSyncEngine.syncEnabledChannels(
            dao = dao,
            dataSource = dataSource,
            forceRefresh = forceRefresh
        )

    suspend fun fetchProviderRating(videoId: String, authState: ProviderAuthState): VideoRatingState? =
        accountActionProvider.getVideoRating(videoId, authState)

    suspend fun submitVideoRating(
        videoId: String,
        targetRating: VideoRatingState,
        authState: ProviderAuthState
    ): VideoAccountActionResult = accountActionProvider.rateVideo(videoId, targetRating, authState)

    suspend fun fetchProviderSubscriptionStatus(channelId: String, authState: ProviderAuthState): Boolean? =
        accountActionProvider.isChannelSubscribed(channelId, authState)

    suspend fun submitChannelSubscription(
        channel: OnlineVideoChannel,
        subscribe: Boolean,
        authState: ProviderAuthState
    ): VideoAccountActionResult {
        val result = accountActionProvider.setChannelSubscription(
            channelId = channel.channelId,
            channelUrl = channel.channelUrl,
            subscribe = subscribe,
            authState = authState
        )
        if (result is VideoAccountActionResult.Confirmed) {
            if (result.updatedSubscribed == true) {
                saveLocalChannelBookmark(channel, providerSynced = true)
            } else if (result.updatedSubscribed == false) {
                removeLocalChannelBookmark(channel.channelId)
            }
        }
        return result
    }

    suspend fun submitChannelNotificationPreference(
        channel: OnlineVideoChannel,
        preference: ChannelNotificationPreference,
        authState: ProviderAuthState
    ): VideoAccountActionResult {
        val result = accountActionProvider.updateChannelNotificationPreference(
            channelId = channel.channelId,
            channelUrl = channel.channelUrl,
            preference = preference,
            authState = authState
        )
        if (result is VideoAccountActionResult.Confirmed && result.updatedNotificationPref != null) {
            dao.updateChannelNotificationPref(channel.channelId, result.updatedNotificationPref.id)
        }
        return result
    }

    private fun OnlineVideoItem.toEntity(
        isWatchLater: Boolean = this.isWatchLater,
        isSaved: Boolean = this.isSaved,
        lastPositionMs: Long = this.lastPositionMs,
        watchCount: Int = 0,
        lastWatchedAt: Long = this.lastWatchedAt,
        savedAt: Long = 0L,
        watchLaterAt: Long = 0L,
        localRating: String = VideoRatingState.NONE.name,
        likedAt: Long = this.likedAt
    ): SavedVideoEntity = SavedVideoEntity(
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
        watchCount = watchCount,
        lastWatchedAt = lastWatchedAt,
        savedAt = savedAt,
        watchLaterAt = watchLaterAt,
        localRating = localRating,
        likedAt = likedAt
    )
}
