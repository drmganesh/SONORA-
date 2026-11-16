package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Stores saved and recently-played online tracks using ONLY permanent metadata and the
 * canonical contentUrl (never ephemeral/expiring raw audio stream URLs).
 */
@Entity(tableName = "online_tracks")
data class OnlineTrackEntity(
    @PrimaryKey val stableId: Long,
    val serviceId: Int,
    val providerKey: String,
    val providerName: String,
    val contentUrl: String,
    val title: String,
    val artist: String,
    val artistUrl: String? = null,
    val album: String = "",
    val durationMs: Long = 0L,
    val artworkUrl: String? = null,
    val isSaved: Boolean = false,
    val isFavorite: Boolean = false,
    val playCount: Int = 0,
    val lastPlayedTimestamp: Long = 0L,
    val savedTimestamp: Long = 0L
)

@Entity(tableName = "online_search_history")
data class OnlineSearchHistoryEntity(
    @PrimaryKey val query: String,
    val providerKey: String,
    val timestamp: Long = System.currentTimeMillis()
)
