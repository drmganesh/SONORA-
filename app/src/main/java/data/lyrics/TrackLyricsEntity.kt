package data.lyrics

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Local Room persistence for cached/imported/edited track lyrics and per-track timing offsets.
 */
@Entity(
    tableName = "track_lyrics",
    indices = [
        Index(value = ["songId"]),
        Index(value = ["updatedAt"])
    ]
)
data class TrackLyricsEntity(
    @PrimaryKey val trackKey: String,
    val songId: Long,
    val title: String,
    val artist: String,
    val rawLyrics: String,
    val sourceType: String,
    val sourceLabel: String,
    val syncType: String,
    val timingOffsetMs: Long = 0L,
    val hasCustomOffset: Boolean = false,
    val isUserEdited: Boolean = false,
    val isInstrumental: Boolean = false,
    val updatedAt: Long = System.currentTimeMillis()
)
