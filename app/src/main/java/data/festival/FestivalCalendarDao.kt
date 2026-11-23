package data.festival

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface FestivalCalendarDao {

    @Query("SELECT * FROM festival_calendar_entries ORDER BY startDateIso ASC, canonicalName ASC")
    fun observeAllEntries(): Flow<List<FestivalCalendarEntity>>

    @Query("SELECT * FROM festival_calendar_entries ORDER BY startDateIso ASC")
    suspend fun getAllEntriesOnce(): List<FestivalCalendarEntity>

    @Query("SELECT COUNT(*) FROM festival_calendar_entries")
    suspend fun countEntries(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertEntries(entries: List<FestivalCalendarEntity>)

    @Query("DELETE FROM festival_calendar_entries")
    suspend fun clearAllEntries()

    @Transaction
    suspend fun replaceAllEntries(entries: List<FestivalCalendarEntity>) {
        clearAllEntries()
        if (entries.isNotEmpty()) {
            upsertEntries(entries)
        }
    }

    @Query("SELECT * FROM festival_sync_meta WHERE id = 1")
    fun observeSyncMeta(): Flow<FestivalSyncMetaEntity?>

    @Query("SELECT * FROM festival_sync_meta WHERE id = 1")
    suspend fun getSyncMetaOnce(): FestivalSyncMetaEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSyncMeta(meta: FestivalSyncMetaEntity)
}
