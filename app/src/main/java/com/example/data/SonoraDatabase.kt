package com.example.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        SongMetadataEntity::class,
        PlaylistEntity::class,
        PlaylistSongCrossRef::class,
        CustomEqPresetEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class SonoraDatabase : RoomDatabase() {
    abstract fun dao(): SonoraDao

    companion object {
        @Volatile
        private var INSTANCE: SonoraDatabase? = null

        fun getDatabase(context: Context): SonoraDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    SonoraDatabase::class.java,
                    "sonora_database"
                ).fallbackToDestructiveMigration(dropAllTables = false).build()
                INSTANCE = instance
                instance
            }
        }
    }
}
