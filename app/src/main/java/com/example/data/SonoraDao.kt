package com.example.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface SonoraDao {

    @Query("SELECT * FROM song_metadata")
    fun getAllMetadataFlow(): Flow<List<SongMetadataEntity>>

    @Query("SELECT * FROM song_metadata WHERE id = :id")
    suspend fun getMetadata(id: Long): SongMetadataEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertMetadata(metadata: SongMetadataEntity)

    @Query("UPDATE song_metadata SET isFavorite = :isFavorite WHERE id = :id")
    suspend fun updateFavorite(id: Long, isFavorite: Boolean)

    @Query("UPDATE song_metadata SET playCount = playCount + 1, lastPlayedTimestamp = :timestamp WHERE id = :id")
    suspend fun incrementPlayCount(id: Long, timestamp: Long = System.currentTimeMillis())

    @Transaction
    suspend fun upsertFavorite(id: Long, isFavorite: Boolean) {
        val existing = getMetadata(id)
        if (existing == null) {
            upsertMetadata(SongMetadataEntity(id = id, isFavorite = isFavorite))
        } else {
            updateFavorite(id, isFavorite)
        }
    }

    @Transaction
    suspend fun upsertIncrementPlayCount(id: Long, timestamp: Long = System.currentTimeMillis()) {
        val existing = getMetadata(id)
        if (existing == null) {
            upsertMetadata(
                SongMetadataEntity(
                    id = id,
                    playCount = 1,
                    lastPlayedTimestamp = timestamp
                )
            )
        } else {
            incrementPlayCount(id, timestamp)
        }
    }

    @Query("SELECT * FROM playlists ORDER BY createdAt DESC")
    fun getAllPlaylistsFlow(): Flow<List<PlaylistEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlaylist(playlist: PlaylistEntity): Long

    @Query("DELETE FROM playlists WHERE id = :playlistId")
    suspend fun deletePlaylist(playlistId: Long)

    @Query("UPDATE playlists SET name = :newName WHERE id = :playlistId")
    suspend fun renamePlaylist(playlistId: Long, newName: String)

    @Query("UPDATE playlists SET name = :newName, coverArtUri = :coverArtUri WHERE id = :playlistId")
    suspend fun updatePlaylistDetails(playlistId: Long, newName: String, coverArtUri: String?)

    @Query("SELECT * FROM playlist_songs WHERE playlistId = :playlistId ORDER BY orderIndex ASC")
    fun getPlaylistSongsFlow(playlistId: Long): Flow<List<PlaylistSongCrossRef>>

    @Query("SELECT * FROM playlist_songs")
    fun getAllPlaylistSongCrossRefsFlow(): Flow<List<PlaylistSongCrossRef>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlaylistSong(crossRef: PlaylistSongCrossRef)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlaylistSongs(crossRefs: List<PlaylistSongCrossRef>)

    @Query("DELETE FROM playlist_songs WHERE playlistId = :playlistId AND songId = :songId")
    suspend fun removeSongFromPlaylist(playlistId: Long, songId: Long)

    @Query("DELETE FROM playlist_songs WHERE playlistId = :playlistId")
    suspend fun clearPlaylistSongs(playlistId: Long)

    @Transaction
    suspend fun replacePlaylistOrder(playlistId: Long, orderedSongIds: List<Long>) {
        clearPlaylistSongs(playlistId)
        val refs = orderedSongIds.distinct().mapIndexed { index, songId ->
            PlaylistSongCrossRef(playlistId = playlistId, songId = songId, orderIndex = index)
        }
        if (refs.isNotEmpty()) {
            insertPlaylistSongs(refs)
        }
    }

    @Query("SELECT * FROM custom_eq_presets")
    fun getCustomEqPresetsFlow(): Flow<List<CustomEqPresetEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCustomEqPreset(preset: CustomEqPresetEntity)

    @Query("DELETE FROM custom_eq_presets WHERE name = :name")
    suspend fun deleteCustomEqPreset(name: String)

    // --- Online Music Persistence (canonical content URLs only, zero expiring stream URLs) ---

    @Query("SELECT * FROM online_tracks WHERE isSaved = 1 ORDER BY savedTimestamp DESC")
    fun getSavedOnlineTracksFlow(): Flow<List<OnlineTrackEntity>>

    @Query("SELECT * FROM online_tracks WHERE lastPlayedTimestamp > 0 ORDER BY lastPlayedTimestamp DESC LIMIT :limit")
    fun getRecentlyPlayedOnlineTracksFlow(limit: Int = 20): Flow<List<OnlineTrackEntity>>

    @Query("SELECT * FROM online_tracks WHERE isFavorite = 1 ORDER BY lastPlayedTimestamp DESC")
    fun getFavoriteOnlineTracksFlow(): Flow<List<OnlineTrackEntity>>

    @Query("SELECT * FROM online_tracks")
    fun getAllOnlineTracksFlow(): Flow<List<OnlineTrackEntity>>

    @Query("SELECT * FROM online_tracks WHERE stableId = :stableId")
    suspend fun getOnlineTrackById(stableId: Long): OnlineTrackEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertOnlineTrack(track: OnlineTrackEntity)

    @Query("UPDATE online_tracks SET isSaved = :isSaved, savedTimestamp = :savedTimestamp WHERE stableId = :stableId")
    suspend fun updateOnlineTrackSaved(stableId: Long, isSaved: Boolean, savedTimestamp: Long)

    @Query("UPDATE online_tracks SET isFavorite = :isFavorite WHERE stableId = :stableId")
    suspend fun updateOnlineTrackFavorite(stableId: Long, isFavorite: Boolean)

    @Query("UPDATE online_tracks SET playCount = playCount + 1, lastPlayedTimestamp = :timestamp WHERE stableId = :stableId")
    suspend fun incrementOnlinePlayCount(stableId: Long, timestamp: Long = System.currentTimeMillis())

    @Query("SELECT * FROM online_search_history ORDER BY timestamp DESC LIMIT :limit")
    fun getOnlineSearchHistoryFlow(limit: Int = 15): Flow<List<OnlineSearchHistoryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertOnlineSearchHistory(entry: OnlineSearchHistoryEntity)

    @Query("DELETE FROM online_search_history WHERE `query` = :query")
    suspend fun deleteOnlineSearchHistory(query: String)

    @Query("DELETE FROM online_search_history")
    suspend fun clearOnlineSearchHistory()

    // --- Music Download Manager & Downloaded Tracks ---

    @Query("SELECT * FROM download_tasks ORDER BY createdAt DESC")
    fun getAllDownloadTasksFlow(): Flow<List<data.download.DownloadTaskEntity>>

    @Query("SELECT * FROM download_tasks WHERE state IN ('WAITING', 'RESOLVING', 'DOWNLOADING', 'PAUSED') ORDER BY createdAt ASC")
    fun getActiveDownloadTasksFlow(): Flow<List<data.download.DownloadTaskEntity>>

    @Query("SELECT * FROM download_tasks WHERE taskId = :taskId")
    suspend fun getDownloadTaskById(taskId: String): data.download.DownloadTaskEntity?

    @Query("SELECT * FROM download_tasks WHERE sourceKey = :sourceKey LIMIT 1")
    suspend fun getDownloadTaskBySourceKey(sourceKey: String): data.download.DownloadTaskEntity?

    @Query("SELECT * FROM download_tasks WHERE state IN ('WAITING', 'RESOLVING', 'DOWNLOADING')")
    suspend fun getInFlightDownloadTasks(): List<data.download.DownloadTaskEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertDownloadTask(task: data.download.DownloadTaskEntity)

    @Query(
        "UPDATE download_tasks SET state = :state, downloadedBytes = :downloadedBytes, totalBytes = :totalBytes, " +
            "speedBytesPerSec = :speedBytesPerSec, etaSeconds = :etaSeconds, updatedAt = :updatedAt WHERE taskId = :taskId"
    )
    suspend fun updateDownloadProgress(
        taskId: String,
        state: String,
        downloadedBytes: Long,
        totalBytes: Long,
        speedBytesPerSec: Long,
        etaSeconds: Long,
        updatedAt: Long = System.currentTimeMillis()
    )

    @Query(
        "UPDATE download_tasks SET state = :state, failureReason = :failureReason, speedBytesPerSec = 0, " +
            "etaSeconds = 0, updatedAt = :updatedAt WHERE taskId = :taskId"
    )
    suspend fun updateDownloadState(
        taskId: String,
        state: String,
        failureReason: String? = null,
        updatedAt: Long = System.currentTimeMillis()
    )

    @Query("DELETE FROM download_tasks WHERE taskId = :taskId")
    suspend fun deleteDownloadTask(taskId: String)

    @Query("DELETE FROM download_tasks WHERE state IN ('COMPLETED', 'FAILED', 'CANCELLED')")
    suspend fun clearCompletedDownloadHistory()

    @Query("SELECT * FROM downloaded_tracks ORDER BY downloadedAt DESC")
    fun getAllDownloadedTracksFlow(): Flow<List<data.download.DownloadedTrackEntity>>

    @Query("SELECT * FROM downloaded_tracks ORDER BY downloadedAt DESC")
    suspend fun getAllDownloadedTracks(): List<data.download.DownloadedTrackEntity>

    @Query("SELECT * FROM downloaded_tracks WHERE id = :id")
    suspend fun getDownloadedTrackById(id: Long): data.download.DownloadedTrackEntity?

    @Query("SELECT * FROM downloaded_tracks WHERE sourceKey = :sourceKey LIMIT 1")
    suspend fun getDownloadedTrackBySourceKey(sourceKey: String): data.download.DownloadedTrackEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertDownloadedTrack(track: data.download.DownloadedTrackEntity)

    @Query("DELETE FROM downloaded_tracks WHERE id = :id")
    suspend fun deleteDownloadedTrack(id: Long)

    @Query("DELETE FROM downloaded_tracks WHERE id IN (:ids)")
    suspend fun deleteDownloadedTracksByIds(ids: List<Long>)

    // --- Online Library Collections (Albums, Followed Artists, Online Playlists, History) ---

    @Query("SELECT * FROM online_tracks WHERE playCount > 0 OR lastPlayedTimestamp > 0 ORDER BY lastPlayedTimestamp DESC LIMIT :limit")
    fun getOnlineListeningHistoryFlow(limit: Int = 200): Flow<List<OnlineTrackEntity>>

    @Query("UPDATE online_tracks SET lastPlayedTimestamp = 0, playCount = 0")
    suspend fun clearOnlineListeningHistory()

    @Query("SELECT * FROM saved_online_albums ORDER BY savedTimestamp DESC")
    fun getSavedOnlineAlbumsFlow(): Flow<List<SavedOnlineAlbumEntity>>

    @Query("SELECT * FROM saved_online_albums WHERE stableId = :stableId")
    suspend fun getSavedOnlineAlbumById(stableId: Long): SavedOnlineAlbumEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSavedOnlineAlbum(album: SavedOnlineAlbumEntity)

    @Query("DELETE FROM saved_online_albums WHERE stableId = :stableId")
    suspend fun deleteSavedOnlineAlbum(stableId: Long)

    @Query("SELECT * FROM followed_online_artists ORDER BY followedTimestamp DESC")
    fun getFollowedOnlineArtistsFlow(): Flow<List<FollowedOnlineArtistEntity>>

    @Query("SELECT * FROM followed_online_artists WHERE stableId = :stableId")
    suspend fun getFollowedOnlineArtistById(stableId: Long): FollowedOnlineArtistEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertFollowedOnlineArtist(artist: FollowedOnlineArtistEntity)

    @Query("DELETE FROM followed_online_artists WHERE stableId = :stableId")
    suspend fun deleteFollowedOnlineArtist(stableId: Long)

    @Query("SELECT * FROM online_playlists ORDER BY updatedAt DESC")
    fun getAllOnlinePlaylistsFlow(): Flow<List<OnlinePlaylistEntity>>

    @Query("SELECT * FROM online_playlists WHERE id = :playlistId")
    suspend fun getOnlinePlaylistById(playlistId: Long): OnlinePlaylistEntity?

    @Query("SELECT * FROM online_playlists WHERE externalContentUrl = :externalUrl LIMIT 1")
    suspend fun getOnlinePlaylistByExternalUrl(externalUrl: String): OnlinePlaylistEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOnlinePlaylist(playlist: OnlinePlaylistEntity): Long

    @Query("UPDATE online_playlists SET name = :newName, updatedAt = :updatedAt WHERE id = :playlistId")
    suspend fun renameOnlinePlaylist(playlistId: Long, newName: String, updatedAt: Long = System.currentTimeMillis())

    @Query("UPDATE online_playlists SET artworkUrl = :artworkUrl, updatedAt = :updatedAt WHERE id = :playlistId")
    suspend fun updateOnlinePlaylistArtwork(playlistId: Long, artworkUrl: String?, updatedAt: Long = System.currentTimeMillis())

    @Query("DELETE FROM online_playlists WHERE id = :playlistId")
    suspend fun deleteOnlinePlaylist(playlistId: Long)

    @Query("SELECT * FROM online_playlist_tracks ORDER BY orderIndex ASC, addedAt ASC")
    fun getAllOnlinePlaylistTrackCrossRefsFlow(): Flow<List<OnlinePlaylistTrackCrossRef>>

    @Query("SELECT COALESCE(MAX(orderIndex), -1) FROM online_playlist_tracks WHERE playlistId = :playlistId")
    suspend fun getMaxOnlinePlaylistOrderIndex(playlistId: Long): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOnlinePlaylistTrack(crossRef: OnlinePlaylistTrackCrossRef)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOnlinePlaylistTracks(crossRefs: List<OnlinePlaylistTrackCrossRef>)

    @Query("DELETE FROM online_playlist_tracks WHERE playlistId = :playlistId AND trackStableId = :trackStableId")
    suspend fun removeTrackFromOnlinePlaylist(playlistId: Long, trackStableId: Long)

    @Query("DELETE FROM online_playlist_tracks WHERE playlistId = :playlistId")
    suspend fun clearOnlinePlaylistTracks(playlistId: Long)

    @Query("DELETE FROM playlist_songs WHERE songId IN (:songIds)")
    suspend fun removeSongsFromAllLocalPlaylists(songIds: List<Long>)

    // --- Track Lyrics & Per-Track Timing Offsets ---

    @Query("SELECT * FROM track_lyrics WHERE trackKey = :trackKey LIMIT 1")
    suspend fun getTrackLyrics(trackKey: String): data.lyrics.TrackLyricsEntity?

    @Query("SELECT * FROM track_lyrics WHERE trackKey = :trackKey LIMIT 1")
    fun getTrackLyricsFlow(trackKey: String): Flow<data.lyrics.TrackLyricsEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertTrackLyrics(entity: data.lyrics.TrackLyricsEntity)

    @Query(
        "UPDATE track_lyrics SET timingOffsetMs = :offsetMs, hasCustomOffset = :hasCustomOffset, updatedAt = :updatedAt WHERE trackKey = :trackKey"
    )
    suspend fun updateTrackLyricsOffset(
        trackKey: String,
        offsetMs: Long,
        hasCustomOffset: Boolean,
        updatedAt: Long = System.currentTimeMillis()
    )

    @Query("DELETE FROM track_lyrics WHERE trackKey = :trackKey")
    suspend fun deleteTrackLyrics(trackKey: String)

    // --- Online Video Library (Watch Later, History, Saved Videos, Channels, Playlists, Search History) ---

    @Query("SELECT * FROM saved_videos WHERE videoId = :videoId LIMIT 1")
    suspend fun getSavedVideoById(videoId: String): data.video.SavedVideoEntity?

    @Query("SELECT * FROM saved_videos")
    fun getAllStoredVideosFlow(): Flow<List<data.video.SavedVideoEntity>>

    @Query("SELECT * FROM saved_videos WHERE isWatchLater = 1 ORDER BY watchLaterAt DESC")
    fun getWatchLaterVideosFlow(): Flow<List<data.video.SavedVideoEntity>>

    @Query("SELECT * FROM saved_videos WHERE isSaved = 1 ORDER BY savedAt DESC")
    fun getSavedVideosFlow(): Flow<List<data.video.SavedVideoEntity>>

    @Query("SELECT * FROM saved_videos WHERE lastWatchedAt > 0 ORDER BY lastWatchedAt DESC LIMIT 100")
    fun getVideoHistoryFlow(): Flow<List<data.video.SavedVideoEntity>>

    @Query("SELECT * FROM saved_videos WHERE localRating = 'LIKED' ORDER BY likedAt DESC, lastWatchedAt DESC")
    fun getLikedVideosFlow(): Flow<List<data.video.SavedVideoEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSavedVideo(entity: data.video.SavedVideoEntity)

    @Query("UPDATE saved_videos SET isWatchLater = :isWatchLater, watchLaterAt = :timestamp WHERE videoId = :videoId")
    suspend fun updateVideoWatchLater(videoId: String, isWatchLater: Boolean, timestamp: Long)

    @Query("UPDATE saved_videos SET isSaved = :isSaved, savedAt = :timestamp WHERE videoId = :videoId")
    suspend fun updateVideoSaved(videoId: String, isSaved: Boolean, timestamp: Long)

    @Query("UPDATE saved_videos SET lastPositionMs = :positionMs, lastWatchedAt = :watchedAt WHERE videoId = :videoId")
    suspend fun updateVideoWatchProgress(videoId: String, positionMs: Long, watchedAt: Long)

    @Query("UPDATE saved_videos SET lastWatchedAt = 0, lastPositionMs = 0, watchCount = 0 WHERE videoId = :videoId")
    suspend fun removeVideoFromHistory(videoId: String)

    @Query("UPDATE saved_videos SET lastWatchedAt = 0, lastPositionMs = 0, watchCount = 0")
    suspend fun clearAllVideoHistory()

    @Query("UPDATE saved_videos SET localRating = :rating WHERE videoId = :videoId")
    suspend fun updateVideoLocalRating(videoId: String, rating: String)

    @Query("UPDATE saved_videos SET localRating = :rating, likedAt = :likedAt WHERE videoId = :videoId")
    suspend fun updateVideoLocalRatingWithTimestamp(videoId: String, rating: String, likedAt: Long)

    @Query("UPDATE saved_videos SET localRating = 'NONE', likedAt = 0 WHERE localRating = 'LIKED'")
    suspend fun clearAllLikedVideos()

    @Query("DELETE FROM saved_videos WHERE isWatchLater = 0 AND isSaved = 0 AND lastWatchedAt = 0 AND localRating = 'NONE' AND videoId NOT IN (SELECT videoId FROM video_playlist_items)")
    suspend fun pruneUnreferencedVideos()

    @Query("SELECT * FROM subscribed_video_channels ORDER BY subscribedAt DESC")
    fun getSubscribedVideoChannelsFlow(): Flow<List<data.video.SubscribedVideoChannelEntity>>

    @Query("SELECT * FROM subscribed_video_channels ORDER BY subscribedAt DESC")
    suspend fun getAllSubscribedVideoChannelsOnce(): List<data.video.SubscribedVideoChannelEntity>

    @Query("SELECT * FROM subscribed_video_channels WHERE channelId = :channelId LIMIT 1")
    suspend fun getSubscribedVideoChannelById(channelId: String): data.video.SubscribedVideoChannelEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSubscribedVideoChannel(entity: data.video.SubscribedVideoChannelEntity)

    @Query("UPDATE subscribed_video_channels SET notificationPref = :pref WHERE channelId = :channelId")
    suspend fun updateChannelNotificationPref(channelId: String, pref: String)

    @Query(
        "UPDATE subscribed_video_channels SET lastKnownVideoId = :lastKnownVideoId, lastCheckedAt = :lastCheckedAt, notifiedVideoIdsCsv = :notifiedVideoIdsCsv WHERE channelId = :channelId"
    )
    suspend fun updateChannelUploadCheckState(
        channelId: String,
        lastKnownVideoId: String?,
        lastCheckedAt: Long,
        notifiedVideoIdsCsv: String
    )

    @Query("DELETE FROM subscribed_video_channels WHERE channelId = :channelId")
    suspend fun deleteSubscribedVideoChannel(channelId: String)

    @Query("SELECT * FROM video_playlists ORDER BY updatedAt DESC")
    fun getAllVideoPlaylistsFlow(): Flow<List<data.video.VideoPlaylistEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertVideoPlaylist(playlist: data.video.VideoPlaylistEntity): Long

    @Query("UPDATE video_playlists SET name = :newName, updatedAt = :updatedAt WHERE id = :playlistId")
    suspend fun renameVideoPlaylist(playlistId: Long, newName: String, updatedAt: Long = System.currentTimeMillis())

    @Query("UPDATE video_playlists SET thumbnailUrl = :thumbUrl, updatedAt = :updatedAt WHERE id = :playlistId")
    suspend fun updateVideoPlaylistThumbnail(playlistId: Long, thumbUrl: String?, updatedAt: Long = System.currentTimeMillis())

    @Query("DELETE FROM video_playlists WHERE id = :playlistId")
    suspend fun deleteVideoPlaylist(playlistId: Long)

    @Query("SELECT * FROM video_playlist_items ORDER BY orderIndex ASC, addedAt ASC")
    fun getAllVideoPlaylistItemsFlow(): Flow<List<data.video.VideoPlaylistItemCrossRef>>

    @Query("SELECT COALESCE(MAX(orderIndex), -1) FROM video_playlist_items WHERE playlistId = :playlistId")
    suspend fun getMaxVideoPlaylistOrderIndex(playlistId: Long): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertVideoPlaylistItem(crossRef: data.video.VideoPlaylistItemCrossRef)

    @Query("DELETE FROM video_playlist_items WHERE playlistId = :playlistId AND videoId = :videoId")
    suspend fun removeVideoFromPlaylist(playlistId: Long, videoId: String)

    @Query("DELETE FROM video_playlist_items WHERE playlistId = :playlistId")
    suspend fun clearVideoPlaylistItems(playlistId: Long)

    @Query("SELECT * FROM video_search_history ORDER BY timestamp DESC LIMIT 20")
    fun getVideoSearchHistoryFlow(): Flow<List<data.video.VideoSearchHistoryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertVideoSearchHistory(entry: data.video.VideoSearchHistoryEntity)

    @Query("DELETE FROM video_search_history WHERE query = :query")
    suspend fun deleteVideoSearchHistory(query: String)

    @Query("DELETE FROM video_search_history")
    suspend fun clearVideoSearchHistory()

    // --- Unlimited Music Queue Persistence ---

    @Query("SELECT * FROM music_queue_items ORDER BY orderIndex ASC, queueItemId ASC")
    suspend fun getAllMusicQueueItems(): List<data.queue.MusicQueueItemEntity>

    @Query("SELECT COUNT(*) FROM music_queue_items")
    suspend fun getMusicQueueCount(): Int

    @Query("SELECT COALESCE(MAX(queueItemId), 0) FROM music_queue_items")
    suspend fun getMaxMusicQueueItemId(): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertMusicQueueItems(items: List<data.queue.MusicQueueItemEntity>)

    @Query("DELETE FROM music_queue_items WHERE queueItemId = :queueItemId")
    suspend fun deleteMusicQueueItem(queueItemId: Long)

    @Query("DELETE FROM music_queue_items")
    suspend fun clearAllMusicQueueItems()

    @Transaction
    suspend fun replaceMusicQueueChunked(items: List<data.queue.MusicQueueItemEntity>) {
        clearAllMusicQueueItems()
        if (items.isEmpty()) return
        items.chunked(250).forEach { chunk ->
            upsertMusicQueueItems(chunk)
        }
    }

    @Query("SELECT * FROM music_queue_meta WHERE id = 1 LIMIT 1")
    suspend fun getMusicQueueMeta(): data.queue.MusicQueueMetaEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertMusicQueueMeta(meta: data.queue.MusicQueueMetaEntity)

    // --- Video Recommendation Feedback & Interests ---

    @Query("SELECT * FROM video_recommendation_feedback ORDER BY createdAt DESC")
    fun getAllVideoRecommendationFeedbackFlow(): Flow<List<data.video.VideoRecommendationFeedbackEntity>>

    @Query("SELECT * FROM video_recommendation_feedback ORDER BY createdAt DESC")
    suspend fun getAllVideoRecommendationFeedback(): List<data.video.VideoRecommendationFeedbackEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertVideoRecommendationFeedback(entity: data.video.VideoRecommendationFeedbackEntity)

    @Query("DELETE FROM video_recommendation_feedback WHERE feedbackKey = :feedbackKey")
    suspend fun deleteVideoRecommendationFeedback(feedbackKey: String)

    @Query("DELETE FROM video_recommendation_feedback WHERE feedbackType = :feedbackType")
    suspend fun clearVideoRecommendationFeedbackByType(feedbackType: String)

    @Query("DELETE FROM video_recommendation_feedback")
    suspend fun clearAllVideoRecommendationFeedback()

    // --- SONORA Online Video Followed-Channel Notifications ---

    @Query("SELECT * FROM video_notifications ORDER BY createdAt DESC LIMIT 150")
    fun getAllVideoNotificationsFlow(): Flow<List<data.video.VideoNotificationEntity>>

    @Query("SELECT COUNT(*) FROM video_notifications WHERE isRead = 0")
    fun getUnreadVideoNotificationCountFlow(): Flow<Int>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertVideoNotificationsIgnore(items: List<data.video.VideoNotificationEntity>)

    @Query("UPDATE video_notifications SET isRead = 1 WHERE notificationId = :notificationId")
    suspend fun markVideoNotificationRead(notificationId: String)

    @Query("UPDATE video_notifications SET isRead = 1 WHERE isRead = 0")
    suspend fun markAllVideoNotificationsRead()

    @Query("DELETE FROM video_notifications WHERE notificationId = :notificationId")
    suspend fun deleteVideoNotification(notificationId: String)

    @Query("DELETE FROM video_notifications")
    suspend fun clearAllVideoNotifications()

    @Query("DELETE FROM video_notifications WHERE channelId = :channelId")
    suspend fun deleteVideoNotificationsForChannel(channelId: String)

    // --- Listening Analytics Events (Separate from Downloads & Playlists) ---

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertListeningEvent(event: data.analytics.ListeningEventEntity): Long

    @Query("SELECT * FROM listening_events ORDER BY endedAtMs DESC")
    fun getAllListeningEventsFlow(): Flow<List<data.analytics.ListeningEventEntity>>

    @Query("SELECT * FROM listening_events ORDER BY endedAtMs DESC")
    suspend fun getAllListeningEventsOnce(): List<data.analytics.ListeningEventEntity>

    @Query("DELETE FROM listening_events")
    suspend fun clearAllListeningEvents()

    // --- Playback Session Snapshot (App Restart & Explicit Stop Tracking) ---

    @Query("SELECT * FROM playback_session_snapshot WHERE id = 1 LIMIT 1")
    suspend fun getPlaybackSessionSnapshot(): data.analytics.PlaybackSessionSnapshotEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertPlaybackSessionSnapshot(snapshot: data.analytics.PlaybackSessionSnapshotEntity)
}
