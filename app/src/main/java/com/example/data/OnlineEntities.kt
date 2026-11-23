package com.example.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import domain.library.FollowedOnlineArtist
import domain.library.SavedOnlineAlbum

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

/**
 * Stores saved online albums using canonical provider identifiers (never temporary stream URLs).
 */
@Entity(
    tableName = "saved_online_albums",
    indices = [Index(value = ["savedTimestamp"])]
)
data class SavedOnlineAlbumEntity(
    @PrimaryKey val stableId: Long,
    val serviceId: Int,
    val providerKey: String,
    val providerName: String,
    val contentUrl: String,
    val title: String,
    val artist: String,
    val artworkUrl: String? = null,
    val trackCount: Long = -1L,
    val savedTimestamp: Long = System.currentTimeMillis()
) {
    fun toDomain(): SavedOnlineAlbum = SavedOnlineAlbum(
        stableId = stableId,
        serviceId = serviceId,
        providerKey = providerKey,
        providerName = providerName,
        contentUrl = contentUrl,
        title = title,
        artist = artist,
        artworkUrl = artworkUrl,
        trackCount = trackCount,
        savedTimestamp = savedTimestamp
    )
}

/**
 * Stores followed online artists/channels using canonical provider URLs.
 */
@Entity(
    tableName = "followed_online_artists",
    indices = [Index(value = ["followedTimestamp"])]
)
data class FollowedOnlineArtistEntity(
    @PrimaryKey val stableId: Long,
    val serviceId: Int,
    val providerKey: String,
    val providerName: String,
    val contentUrl: String,
    val name: String,
    val artworkUrl: String? = null,
    val subscriberCount: Long = -1L,
    val followedTimestamp: Long = System.currentTimeMillis()
) {
    fun toDomain(): FollowedOnlineArtist = FollowedOnlineArtist(
        stableId = stableId,
        serviceId = serviceId,
        providerKey = providerKey,
        providerName = providerName,
        contentUrl = contentUrl,
        name = name,
        artworkUrl = artworkUrl,
        subscriberCount = subscriberCount,
        followedTimestamp = followedTimestamp
    )
}

/**
 * Dedicated Online Library playlists, kept independent from local offline playlists.
 */
@Entity(
    tableName = "online_playlists",
    indices = [Index(value = ["updatedAt"])]
)
data class OnlinePlaylistEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val artworkUrl: String? = null,
    val externalContentUrl: String? = null,
    val providerKey: String = "",
    val providerName: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "online_playlist_tracks",
    primaryKeys = ["playlistId", "trackStableId"],
    indices = [Index(value = ["playlistId", "orderIndex"])]
)
data class OnlinePlaylistTrackCrossRef(
    val playlistId: Long,
    val trackStableId: Long,
    val orderIndex: Int = 0,
    val addedAt: Long = System.currentTimeMillis()
)
