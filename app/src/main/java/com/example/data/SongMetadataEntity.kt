package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "song_metadata")
data class SongMetadataEntity(
    @PrimaryKey val id: Long,
    val isFavorite: Boolean = false,
    val playCount: Int = 0,
    val lastPlayedTimestamp: Long = 0L,
    val customTitle: String? = null,
    val customArtist: String? = null,
    val customAlbum: String? = null,
    val customGenre: String? = null,
    val customLyrics: String? = null
)
