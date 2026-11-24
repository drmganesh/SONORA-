package com.example.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        SongMetadataEntity::class,
        PlaylistEntity::class,
        PlaylistSongCrossRef::class,
        CustomEqPresetEntity::class,
        OnlineTrackEntity::class,
        OnlineSearchHistoryEntity::class,
        SavedOnlineAlbumEntity::class,
        FollowedOnlineArtistEntity::class,
        OnlinePlaylistEntity::class,
        OnlinePlaylistTrackCrossRef::class,
        data.download.DownloadTaskEntity::class,
        data.download.DownloadedTrackEntity::class,
        data.lyrics.TrackLyricsEntity::class,
        data.video.SavedVideoEntity::class,
        data.video.SubscribedVideoChannelEntity::class,
        data.video.VideoPlaylistEntity::class,
        data.video.VideoPlaylistItemCrossRef::class,
        data.video.VideoSearchHistoryEntity::class,
        data.queue.MusicQueueItemEntity::class,
        data.queue.MusicQueueMetaEntity::class,
        data.video.VideoRecommendationFeedbackEntity::class,
        data.video.VideoNotificationEntity::class,
        data.analytics.ListeningEventEntity::class,
        data.analytics.PlaybackSessionSnapshotEntity::class,
        data.festival.FestivalCalendarEntity::class,
        data.festival.FestivalSyncMetaEntity::class
    ],
    version = 13,
    exportSchema = false
)
abstract class SonoraDatabase : RoomDatabase() {
    abstract fun dao(): SonoraDao
    abstract fun festivalCalendarDao(): data.festival.FestivalCalendarDao

    companion object {
        @Volatile
        private var INSTANCE: SonoraDatabase? = null

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `online_tracks` (
                        `stableId` INTEGER NOT NULL,
                        `serviceId` INTEGER NOT NULL,
                        `providerKey` TEXT NOT NULL,
                        `providerName` TEXT NOT NULL,
                        `contentUrl` TEXT NOT NULL,
                        `title` TEXT NOT NULL,
                        `artist` TEXT NOT NULL,
                        `artistUrl` TEXT,
                        `album` TEXT NOT NULL,
                        `durationMs` INTEGER NOT NULL,
                        `artworkUrl` TEXT,
                        `isSaved` INTEGER NOT NULL,
                        `isFavorite` INTEGER NOT NULL,
                        `playCount` INTEGER NOT NULL,
                        `lastPlayedTimestamp` INTEGER NOT NULL,
                        `savedTimestamp` INTEGER NOT NULL,
                        PRIMARY KEY(`stableId`)
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `online_search_history` (
                        `query` TEXT NOT NULL,
                        `providerKey` TEXT NOT NULL,
                        `timestamp` INTEGER NOT NULL,
                        PRIMARY KEY(`query`)
                    )
                    """.trimIndent()
                )
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `download_tasks` (
                        `taskId` TEXT NOT NULL,
                        `sourceKey` TEXT NOT NULL,
                        `serviceId` INTEGER NOT NULL,
                        `providerKey` TEXT NOT NULL,
                        `providerName` TEXT NOT NULL,
                        `contentUrl` TEXT NOT NULL,
                        `title` TEXT NOT NULL,
                        `artist` TEXT NOT NULL,
                        `album` TEXT NOT NULL,
                        `durationMs` INTEGER NOT NULL,
                        `artworkUrl` TEXT,
                        `qualityOption` TEXT NOT NULL,
                        `selectedStreamId` TEXT,
                        `formatLabel` TEXT NOT NULL,
                        `mimeType` TEXT NOT NULL,
                        `fileExtension` TEXT NOT NULL,
                        `bitrateKbps` INTEGER NOT NULL,
                        `state` TEXT NOT NULL,
                        `downloadedBytes` INTEGER NOT NULL,
                        `totalBytes` INTEGER NOT NULL,
                        `speedBytesPerSec` INTEGER NOT NULL,
                        `etaSeconds` INTEGER NOT NULL,
                        `targetFolderUri` TEXT NOT NULL,
                        `stagingFilePath` TEXT,
                        `outputFileUri` TEXT,
                        `failureReason` TEXT,
                        `retryCount` INTEGER NOT NULL,
                        `createdAt` INTEGER NOT NULL,
                        `updatedAt` INTEGER NOT NULL,
                        PRIMARY KEY(`taskId`)
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_download_tasks_sourceKey` ON `download_tasks` (`sourceKey`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_download_tasks_state_updatedAt` ON `download_tasks` (`state`, `updatedAt`)")

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `downloaded_tracks` (
                        `id` INTEGER NOT NULL,
                        `sourceKey` TEXT NOT NULL,
                        `serviceId` INTEGER NOT NULL,
                        `providerKey` TEXT NOT NULL,
                        `providerName` TEXT NOT NULL,
                        `contentUrl` TEXT NOT NULL,
                        `title` TEXT NOT NULL,
                        `artist` TEXT NOT NULL,
                        `album` TEXT NOT NULL,
                        `durationMs` INTEGER NOT NULL,
                        `artworkUrl` TEXT,
                        `localArtworkPath` TEXT,
                        `localFileUri` TEXT NOT NULL,
                        `treeFolderUri` TEXT NOT NULL,
                        `displayFileName` TEXT NOT NULL,
                        `formatLabel` TEXT NOT NULL,
                        `mimeType` TEXT NOT NULL,
                        `bitrateKbps` INTEGER NOT NULL,
                        `sampleRateHz` INTEGER NOT NULL,
                        `fileSizeBytes` INTEGER NOT NULL,
                        `downloadedAt` INTEGER NOT NULL,
                        PRIMARY KEY(`id`)
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_downloaded_tracks_sourceKey` ON `downloaded_tracks` (`sourceKey`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_downloaded_tracks_downloadedAt` ON `downloaded_tracks` (`downloadedAt`)")
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `saved_online_albums` (
                        `stableId` INTEGER NOT NULL,
                        `serviceId` INTEGER NOT NULL,
                        `providerKey` TEXT NOT NULL,
                        `providerName` TEXT NOT NULL,
                        `contentUrl` TEXT NOT NULL,
                        `title` TEXT NOT NULL,
                        `artist` TEXT NOT NULL,
                        `artworkUrl` TEXT,
                        `trackCount` INTEGER NOT NULL,
                        `savedTimestamp` INTEGER NOT NULL,
                        PRIMARY KEY(`stableId`)
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_saved_online_albums_savedTimestamp` ON `saved_online_albums` (`savedTimestamp`)")

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `followed_online_artists` (
                        `stableId` INTEGER NOT NULL,
                        `serviceId` INTEGER NOT NULL,
                        `providerKey` TEXT NOT NULL,
                        `providerName` TEXT NOT NULL,
                        `contentUrl` TEXT NOT NULL,
                        `name` TEXT NOT NULL,
                        `artworkUrl` TEXT,
                        `subscriberCount` INTEGER NOT NULL,
                        `followedTimestamp` INTEGER NOT NULL,
                        PRIMARY KEY(`stableId`)
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_followed_online_artists_followedTimestamp` ON `followed_online_artists` (`followedTimestamp`)")

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `online_playlists` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `name` TEXT NOT NULL,
                        `artworkUrl` TEXT,
                        `externalContentUrl` TEXT,
                        `providerKey` TEXT NOT NULL,
                        `providerName` TEXT NOT NULL,
                        `createdAt` INTEGER NOT NULL,
                        `updatedAt` INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_online_playlists_updatedAt` ON `online_playlists` (`updatedAt`)")

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `online_playlist_tracks` (
                        `playlistId` INTEGER NOT NULL,
                        `trackStableId` INTEGER NOT NULL,
                        `orderIndex` INTEGER NOT NULL,
                        `addedAt` INTEGER NOT NULL,
                        PRIMARY KEY(`playlistId`, `trackStableId`)
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_online_playlist_tracks_playlistId_orderIndex` ON `online_playlist_tracks` (`playlistId`, `orderIndex`)")
            }
        }

        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `track_lyrics` (
                        `trackKey` TEXT NOT NULL,
                        `songId` INTEGER NOT NULL,
                        `title` TEXT NOT NULL,
                        `artist` TEXT NOT NULL,
                        `rawLyrics` TEXT NOT NULL,
                        `sourceType` TEXT NOT NULL,
                        `sourceLabel` TEXT NOT NULL,
                        `syncType` TEXT NOT NULL,
                        `timingOffsetMs` INTEGER NOT NULL,
                        `hasCustomOffset` INTEGER NOT NULL,
                        `isUserEdited` INTEGER NOT NULL,
                        `isInstrumental` INTEGER NOT NULL,
                        `updatedAt` INTEGER NOT NULL,
                        PRIMARY KEY(`trackKey`)
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_track_lyrics_songId` ON `track_lyrics` (`songId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_track_lyrics_updatedAt` ON `track_lyrics` (`updatedAt`)")
            }
        }

        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `saved_videos` (
                        `videoId` TEXT NOT NULL,
                        `stableId` INTEGER NOT NULL,
                        `contentUrl` TEXT NOT NULL,
                        `title` TEXT NOT NULL,
                        `channelName` TEXT NOT NULL,
                        `channelUrl` TEXT,
                        `channelAvatarUrl` TEXT,
                        `thumbnailUrl` TEXT,
                        `durationSeconds` INTEGER NOT NULL,
                        `viewCount` INTEGER,
                        `uploadDateText` TEXT,
                        `isLive` INTEGER NOT NULL,
                        `providerKey` TEXT NOT NULL,
                        `providerName` TEXT NOT NULL,
                        `isWatchLater` INTEGER NOT NULL,
                        `isSaved` INTEGER NOT NULL,
                        `lastPositionMs` INTEGER NOT NULL,
                        `watchCount` INTEGER NOT NULL,
                        `lastWatchedAt` INTEGER NOT NULL,
                        `savedAt` INTEGER NOT NULL,
                        `watchLaterAt` INTEGER NOT NULL,
                        PRIMARY KEY(`videoId`)
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_saved_videos_isWatchLater_watchLaterAt` ON `saved_videos` (`isWatchLater`, `watchLaterAt`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_saved_videos_isSaved_savedAt` ON `saved_videos` (`isSaved`, `savedAt`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_saved_videos_lastWatchedAt` ON `saved_videos` (`lastWatchedAt`)")

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `subscribed_video_channels` (
                        `channelId` TEXT NOT NULL,
                        `stableId` INTEGER NOT NULL,
                        `channelUrl` TEXT NOT NULL,
                        `name` TEXT NOT NULL,
                        `avatarUrl` TEXT,
                        `subscriberCount` INTEGER,
                        `isProviderSynced` INTEGER NOT NULL,
                        `notificationPref` TEXT NOT NULL,
                        `subscribedAt` INTEGER NOT NULL,
                        PRIMARY KEY(`channelId`)
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_subscribed_video_channels_subscribedAt` ON `subscribed_video_channels` (`subscribedAt`)")

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `video_playlists` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `name` TEXT NOT NULL,
                        `externalUrl` TEXT,
                        `thumbnailUrl` TEXT,
                        `createdAt` INTEGER NOT NULL,
                        `updatedAt` INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_video_playlists_updatedAt` ON `video_playlists` (`updatedAt`)")

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `video_playlist_items` (
                        `playlistId` INTEGER NOT NULL,
                        `videoId` TEXT NOT NULL,
                        `orderIndex` INTEGER NOT NULL,
                        `addedAt` INTEGER NOT NULL,
                        PRIMARY KEY(`playlistId`, `videoId`)
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_video_playlist_items_playlistId_orderIndex` ON `video_playlist_items` (`playlistId`, `orderIndex`)")

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `video_search_history` (
                        `query` TEXT NOT NULL,
                        `timestamp` INTEGER NOT NULL,
                        PRIMARY KEY(`query`)
                    )
                    """.trimIndent()
                )
            }
        }

        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `music_queue_items` (
                        `queueItemId` INTEGER NOT NULL,
                        `orderIndex` INTEGER NOT NULL,
                        `songId` INTEGER NOT NULL,
                        `title` TEXT NOT NULL,
                        `artist` TEXT NOT NULL,
                        `album` TEXT NOT NULL,
                        `albumId` INTEGER NOT NULL,
                        `durationMs` INTEGER NOT NULL,
                        `uriString` TEXT NOT NULL,
                        `dataPath` TEXT NOT NULL,
                        `mimeType` TEXT NOT NULL,
                        `sizeBytes` INTEGER NOT NULL,
                        `bitrateKbps` INTEGER NOT NULL,
                        `sampleRateHz` INTEGER NOT NULL,
                        `isOnline` INTEGER NOT NULL,
                        `sourceType` TEXT NOT NULL,
                        `serviceId` INTEGER NOT NULL,
                        `providerKey` TEXT NOT NULL,
                        `providerName` TEXT NOT NULL,
                        `contentUrl` TEXT,
                        `artworkUrl` TEXT,
                        `streamCodec` TEXT NOT NULL,
                        `streamQualityLabel` TEXT NOT NULL,
                        `genre` TEXT NOT NULL,
                        `addedAtMs` INTEGER NOT NULL,
                        PRIMARY KEY(`queueItemId`)
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_music_queue_items_orderIndex` ON `music_queue_items` (`orderIndex`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_music_queue_items_songId` ON `music_queue_items` (`songId`)")

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `music_queue_meta` (
                        `id` INTEGER NOT NULL,
                        `currentQueueItemId` INTEGER NOT NULL,
                        `currentIndex` INTEGER NOT NULL,
                        `playbackPositionMs` INTEGER NOT NULL,
                        `updatedAtMs` INTEGER NOT NULL,
                        PRIMARY KEY(`id`)
                    )
                    """.trimIndent()
                )

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `video_recommendation_feedback` (
                        `feedbackKey` TEXT NOT NULL,
                        `feedbackType` TEXT NOT NULL,
                        `targetValue` TEXT NOT NULL,
                        `createdAt` INTEGER NOT NULL,
                        PRIMARY KEY(`feedbackKey`)
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_video_recommendation_feedback_feedbackType` ON `video_recommendation_feedback` (`feedbackType`)")

                db.execSQL("ALTER TABLE `download_tasks` ADD COLUMN `mediaType` TEXT NOT NULL DEFAULT 'AUDIO'")
                db.execSQL("ALTER TABLE `download_tasks` ADD COLUMN `resolutionLabel` TEXT")
                db.execSQL("ALTER TABLE `downloaded_tracks` ADD COLUMN `mediaType` TEXT NOT NULL DEFAULT 'AUDIO'")
                db.execSQL("ALTER TABLE `downloaded_tracks` ADD COLUMN `resolutionLabel` TEXT")
            }
        }

        val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `saved_videos` ADD COLUMN `localRating` TEXT NOT NULL DEFAULT 'NONE'")
                db.execSQL("ALTER TABLE `subscribed_video_channels` ADD COLUMN `lastKnownVideoId` TEXT")
                db.execSQL("ALTER TABLE `subscribed_video_channels` ADD COLUMN `lastCheckedAt` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `subscribed_video_channels` ADD COLUMN `notifiedVideoIdsCsv` TEXT NOT NULL DEFAULT ''")
            }
        }

        val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `video_notifications` (
                        `notificationId` TEXT NOT NULL,
                        `type` TEXT NOT NULL,
                        `channelId` TEXT NOT NULL,
                        `channelName` TEXT NOT NULL,
                        `channelUrl` TEXT NOT NULL,
                        `channelAvatarUrl` TEXT,
                        `videoId` TEXT,
                        `videoUrl` TEXT,
                        `videoTitle` TEXT,
                        `thumbnailUrl` TEXT,
                        `uploadDateText` TEXT,
                        `message` TEXT NOT NULL,
                        `createdAt` INTEGER NOT NULL,
                        `isRead` INTEGER NOT NULL,
                        PRIMARY KEY(`notificationId`)
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_video_notifications_createdAt` ON `video_notifications` (`createdAt`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_video_notifications_isRead_createdAt` ON `video_notifications` (`isRead`, `createdAt`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_video_notifications_channelId` ON `video_notifications` (`channelId`)")
            }
        }

        val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `saved_videos` ADD COLUMN `likedAt` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_saved_videos_localRating_likedAt` ON `saved_videos` (`localRating`, `likedAt`)")
                db.execSQL(
                    """
                    UPDATE `saved_videos`
                    SET `likedAt` = CASE
                        WHEN `savedAt` > 0 THEN `savedAt`
                        WHEN `lastWatchedAt` > 0 THEN `lastWatchedAt`
                        ELSE 1
                    END
                    WHERE `localRating` = 'LIKED' AND `likedAt` = 0
                    """.trimIndent()
                )
            }
        }

        val MIGRATION_10_11 = object : Migration(10, 11) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `listening_events` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `sessionToken` TEXT NOT NULL,
                        `songId` INTEGER NOT NULL,
                        `title` TEXT NOT NULL,
                        `artist` TEXT NOT NULL,
                        `album` TEXT NOT NULL,
                        `genre` TEXT NOT NULL,
                        `isOnline` INTEGER NOT NULL,
                        `providerKey` TEXT NOT NULL,
                        `artworkUrl` TEXT,
                        `listenedMs` INTEGER NOT NULL,
                        `startedAtMs` INTEGER NOT NULL,
                        `endedAtMs` INTEGER NOT NULL,
                        `dayEpoch` INTEGER NOT NULL,
                        `isPlayStart` INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_listening_events_startedAtMs` ON `listening_events` (`startedAtMs`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_listening_events_dayEpoch` ON `listening_events` (`dayEpoch`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_listening_events_songId` ON `listening_events` (`songId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_listening_events_artist` ON `listening_events` (`artist`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_listening_events_isOnline` ON `listening_events` (`isOnline`)")

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `playback_session_snapshot` (
                        `id` INTEGER NOT NULL,
                        `lastTrackId` INTEGER NOT NULL,
                        `isOnline` INTEGER NOT NULL,
                        `positionMs` INTEGER NOT NULL,
                        `queueIndex` INTEGER NOT NULL,
                        `repeatMode` TEXT NOT NULL,
                        `isShuffle` INTEGER NOT NULL,
                        `playbackSpeed` REAL NOT NULL,
                        `volume` REAL NOT NULL,
                        `contextType` TEXT NOT NULL,
                        `contextLabel` TEXT NOT NULL,
                        `lastPlayerStyle` TEXT NOT NULL,
                        `wasPlayingWhenSaved` INTEGER NOT NULL,
                        `explicitlyStopped` INTEGER NOT NULL,
                        `savedAtMs` INTEGER NOT NULL,
                        PRIMARY KEY(`id`)
                    )
                    """.trimIndent()
                )
            }
        }

        val MIGRATION_11_12 = object : Migration(11, 12) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `festival_calendar_entries` (
                        `recordKey` TEXT NOT NULL,
                        `festivalSlug` TEXT NOT NULL,
                        `canonicalName` TEXT NOT NULL,
                        `alternateRegionalNamesCsv` TEXT NOT NULL,
                        `startDateIso` TEXT NOT NULL,
                        `endDateIso` TEXT NOT NULL,
                        `tithi` TEXT,
                        `regionsCsv` TEXT NOT NULL,
                        `isPanIndia` INTEGER NOT NULL,
                        `categoryId` TEXT NOT NULL,
                        `shortDescription` TEXT NOT NULL,
                        `effectId` TEXT NOT NULL,
                        `iconGlyph` TEXT NOT NULL,
                        `sourceName` TEXT NOT NULL,
                        `sourceLicense` TEXT NOT NULL,
                        `sourcePriorityRank` INTEGER NOT NULL,
                        `verificationStatusId` TEXT NOT NULL,
                        `disagreementNote` TEXT,
                        `lastUpdatedEpochMs` INTEGER NOT NULL,
                        `expiresAtEpochMs` INTEGER NOT NULL,
                        PRIMARY KEY(`recordKey`)
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_festival_calendar_entries_startDateIso_endDateIso` ON `festival_calendar_entries` (`startDateIso`, `endDateIso`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_festival_calendar_entries_festivalSlug` ON `festival_calendar_entries` (`festivalSlug`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_festival_calendar_entries_isPanIndia` ON `festival_calendar_entries` (`isPanIndia`)")

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `festival_sync_meta` (
                        `id` INTEGER NOT NULL,
                        `lastSyncedEpochMs` INTEGER NOT NULL,
                        `lastRemoteAttemptEpochMs` INTEGER NOT NULL,
                        `lastSourceSummary` TEXT NOT NULL,
                        `isOfflineFallback` INTEGER NOT NULL,
                        `consecutiveFailures` INTEGER NOT NULL,
                        `recordCount` INTEGER NOT NULL,
                        PRIMARY KEY(`id`)
                    )
                    """.trimIndent()
                )
            }
        }

        val MIGRATION_12_13 = object : Migration(12, 13) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `festival_sync_meta` ADD COLUMN `datasetVersion` TEXT NOT NULL DEFAULT 'rashtriya-panchang-2026.1'")
                db.execSQL("ALTER TABLE `festival_sync_meta` ADD COLUMN `remoteEtag` TEXT")
                db.execSQL("ALTER TABLE `festival_sync_meta` ADD COLUMN `remoteLastModified` TEXT")
                db.execSQL("ALTER TABLE `festival_sync_meta` ADD COLUMN `sourceAttribution` TEXT NOT NULL DEFAULT 'IMD / PAC Rashtriya Panchang • Regional State Panjis • ganeshsp1/hindu-festivals-api (MIT) • satmargastro.com (CC BY 4.0)'")
                db.execSQL("ALTER TABLE `festival_sync_meta` ADD COLUMN `lastSyncStatusCode` TEXT NOT NULL DEFAULT 'IDLE'")
                db.execSQL("ALTER TABLE `festival_sync_meta` ADD COLUMN `lastErrorMessage` TEXT")
            }
        }

        fun getDatabase(context: Context): SonoraDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    SonoraDatabase::class.java,
                    "sonora_database"
                )
                    .addMigrations(
                        MIGRATION_1_2,
                        MIGRATION_2_3,
                        MIGRATION_3_4,
                        MIGRATION_4_5,
                        MIGRATION_5_6,
                        MIGRATION_6_7,
                        MIGRATION_7_8,
                        MIGRATION_8_9,
                        MIGRATION_9_10,
                        MIGRATION_10_11,
                        MIGRATION_11_12,
                        MIGRATION_12_13
                    )
                    .fallbackToDestructiveMigrationOnDowngrade(dropAllTables = false)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
