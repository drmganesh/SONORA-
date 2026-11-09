package com.example.model

import android.net.Uri

data class Song(
    val id: Long,
    val title: String,
    val artist: String,
    val album: String,
    val albumId: Long,
    val durationMs: Long,
    val uri: Uri,
    val dataPath: String,
    val mimeType: String,
    val sizeBytes: Long,
    val trackNumber: Int = 0,
    val year: Int = 0,
    val genre: String = "Unknown Genre",
    val composer: String = "Unknown",
    val bitrateKbps: Int = 320,
    val sampleRateHz: Int = 44100,
    val bitDepth: Int = 16,
    val dateAdded: Long = 0L,
    val isFavorite: Boolean = false,
    val playCount: Int = 0,
    val lastPlayedTimestamp: Long = 0L,
    val lyrics: String? = null,
    val isDemoTrack: Boolean = false
) {
    val durationFormatted: String
        get() {
            val totalSeconds = (durationMs / 1000).coerceAtLeast(0)
            val minutes = totalSeconds / 60
            val seconds = totalSeconds % 60
            return String.format("%d:%02d", minutes, seconds)
        }

    val audioFormatInfo: String
        get() {
            val ext = when {
                mimeType.contains("flac", ignoreCase = true) || dataPath.endsWith(".flac", ignoreCase = true) -> "FLAC"
                mimeType.contains("wav", ignoreCase = true) || dataPath.endsWith(".wav", ignoreCase = true) -> "WAV"
                mimeType.contains("ogg", ignoreCase = true) || dataPath.endsWith(".ogg", ignoreCase = true) -> "OGG"
                mimeType.contains("opus", ignoreCase = true) || dataPath.endsWith(".opus", ignoreCase = true) -> "OPUS"
                mimeType.contains("aac", ignoreCase = true) || dataPath.endsWith(".aac", ignoreCase = true) -> "AAC"
                mimeType.contains("m4a", ignoreCase = true) || dataPath.endsWith(".m4a", ignoreCase = true) -> "M4A"
                else -> "MP3"
            }
            val sampleKhz = if (sampleRateHz >= 1000) "${sampleRateHz / 1000}kHz" else "${sampleRateHz}Hz"
            return "$ext • $bitrateKbps kbps • $sampleKhz • ${bitDepth}-bit"
        }
}

data class Album(
    val id: Long,
    val title: String,
    val artist: String,
    val songCount: Int,
    val year: Int,
    val artworkUri: Uri?
)

data class Artist(
    val id: Long,
    val name: String,
    val songCount: Int,
    val albumCount: Int
)

data class Genre(
    val name: String,
    val songCount: Int
)

data class MusicFolder(
    val path: String,
    val name: String,
    val songCount: Int
)

data class Playlist(
    val id: Long,
    val name: String,
    val songCount: Int,
    val createdAt: Long,
    val coverArtUri: String? = null,
    val isSmart: Boolean = false
)

enum class SortOption {
    TITLE_ASC,
    TITLE_DESC,
    ARTIST_ASC,
    ALBUM_ASC,
    DURATION_DESC,
    DATE_ADDED_DESC
}
