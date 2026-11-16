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
        OnlineSearchHistoryEntity::class
    ],
    version = 2,
    exportSchema = false
)
abstract class SonoraDatabase : RoomDatabase() {
    abstract fun dao(): SonoraDao

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

        fun getDatabase(context: Context): SonoraDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    SonoraDatabase::class.java,
                    "sonora_database"
                )
                    .addMigrations(MIGRATION_1_2)
                    .fallbackToDestructiveMigration(dropAllTables = false)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
