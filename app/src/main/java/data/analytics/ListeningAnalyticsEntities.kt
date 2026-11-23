package data.analytics

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Room entity storing measured active listening intervals (excluding paused time).
 * Stored independently from downloaded music and playlists so clearing listening history
 * never touches downloads or playlists.
 */
@Entity(
    tableName = "listening_events",
    indices = [
        Index(value = ["startedAtMs"]),
        Index(value = ["dayEpoch"]),
        Index(value = ["songId"]),
        Index(value = ["artist"]),
        Index(value = ["isOnline"])
    ]
)
data class ListeningEventEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val sessionToken: String,
    val songId: Long,
    val title: String,
    val artist: String,
    val album: String,
    val genre: String,
    val isOnline: Boolean,
    val providerKey: String,
    val artworkUrl: String?,
    val listenedMs: Long,
    val startedAtMs: Long,
    val endedAtMs: Long,
    val dayEpoch: Long,
    val isPlayStart: Boolean
)

/**
 * Single-row snapshot storing full playback session state across app restarts,
 * background interruptions, Recents removal, and explicit user stops.
 */
@Entity(tableName = "playback_session_snapshot")
data class PlaybackSessionSnapshotEntity(
    @PrimaryKey
    val id: Int = 1,
    val lastTrackId: Long = -1L,
    val isOnline: Boolean = false,
    val positionMs: Long = 0L,
    val queueIndex: Int = 0,
    val repeatMode: String = "OFF",
    val isShuffle: Boolean = false,
    val playbackSpeed: Float = 1.0f,
    val volume: Float = 1.0f,
    val contextType: String = "QUEUE",
    val contextLabel: String = "",
    val lastPlayerStyle: String = "",
    val wasPlayingWhenSaved: Boolean = false,
    val explicitlyStopped: Boolean = false,
    val savedAtMs: Long = 0L
)
