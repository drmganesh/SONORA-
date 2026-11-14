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
}
