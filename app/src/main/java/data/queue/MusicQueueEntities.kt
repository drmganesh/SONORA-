package data.queue

import android.net.Uri
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.model.Song

/**
 * Room-backed unlimited music queue item.
 * Each item carries a strictly unique monotonic [queueItemId] so duplicate songs in the queue
 * remain individually addressable for drag-reorder, swipe-remove, and jump-to-track without ID collisions.
 */
@Entity(
    tableName = "music_queue_items",
    indices = [
        Index(value = ["orderIndex"]),
        Index(value = ["songId"])
    ]
)
data class MusicQueueItemEntity(
    @PrimaryKey
    val queueItemId: Long,
    val orderIndex: Long,
    val songId: Long,
    val title: String,
    val artist: String,
    val album: String,
    val albumId: Long,
    val durationMs: Long,
    val uriString: String,
    val dataPath: String,
    val mimeType: String,
    val sizeBytes: Long,
    val bitrateKbps: Int,
    val sampleRateHz: Int,
    val isOnline: Boolean,
    val sourceType: String,
    val serviceId: Int,
    val providerKey: String,
    val providerName: String,
    val contentUrl: String?,
    val artworkUrl: String?,
    val streamCodec: String,
    val streamQualityLabel: String,
    val genre: String,
    val addedAtMs: Long
) {
    fun toSong(): Song = Song(
        id = songId,
        title = title,
        artist = artist,
        album = album,
        albumId = albumId,
        durationMs = durationMs,
        uri = runCatching { Uri.parse(uriString) }.getOrDefault(Uri.EMPTY),
        dataPath = dataPath,
        mimeType = mimeType,
        sizeBytes = sizeBytes,
        bitrateKbps = bitrateKbps,
        sampleRateHz = sampleRateHz,
        isOnline = isOnline,
        sourceType = sourceType,
        serviceId = serviceId,
        providerKey = providerKey,
        providerName = providerName,
        contentUrl = contentUrl.orEmpty(),
        artworkUrl = artworkUrl,
        streamCodec = streamCodec,
        streamQualityLabel = streamQualityLabel,
        genre = genre
    )

    companion object {
        fun fromSong(
            queueItemId: Long,
            orderIndex: Long,
            song: Song,
            addedAtMs: Long = System.currentTimeMillis()
        ): MusicQueueItemEntity = MusicQueueItemEntity(
            queueItemId = queueItemId,
            orderIndex = orderIndex,
            songId = song.id,
            title = song.title,
            artist = song.artist,
            album = song.album,
            albumId = song.albumId,
            durationMs = song.durationMs,
            uriString = song.uri.toString(),
            dataPath = song.dataPath,
            mimeType = song.mimeType,
            sizeBytes = song.sizeBytes,
            bitrateKbps = song.bitrateKbps,
            sampleRateHz = song.sampleRateHz,
            isOnline = song.isOnline,
            sourceType = song.sourceType,
            serviceId = song.serviceId,
            providerKey = song.providerKey,
            providerName = song.providerName,
            contentUrl = song.contentUrl,
            artworkUrl = song.artworkUrl,
            streamCodec = song.streamCodec,
            streamQualityLabel = song.streamQualityLabel,
            genre = song.genre,
            addedAtMs = addedAtMs
        )
    }
}

/**
 * Single-row snapshot capturing active queue pointer and exact playback position across restarts.
 */
@Entity(tableName = "music_queue_meta")
data class MusicQueueMetaEntity(
    @PrimaryKey
    val id: Int = 1,
    val currentQueueItemId: Long = -1L,
    val currentIndex: Int = 0,
    val playbackPositionMs: Long = 0L,
    val updatedAtMs: Long = System.currentTimeMillis()
)

/**
 * Lightweight domain wrapper pairing a [Song] with its unique Room [queueItemId]
 * so Compose LazyColumn keys and drag/swipe gestures remain O(1) and collision-free.
 */
data class QueuedMusicItem(
    val queueItemId: Long,
    val song: Song
)
