package data.queue

import com.example.data.SonoraDao
import com.example.model.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicLong

data class RestoredMusicQueue(
    val items: List<QueuedMusicItem>,
    val currentIndex: Int,
    val playbackPositionMs: Long
)

/**
 * Unlimited Room-backed music queue repository.
 *
 * Architectural guarantees:
 * - Zero arbitrary cap on queue size (supports 10,000+ tracks via chunked Room transactions).
 * - Assigns strictly unique monotonic [Long] `queueItemId`s so duplicate tracks in a queue
 *   can be independently reordered, swiped away, or jumped to cleanly.
 * - Persists both offline local songs and online streaming songs along with active index
 *   and exact playback position across app restarts and rotations.
 */
class MusicQueueRepository(
    private val dao: SonoraDao
) {
    private val idGenerator = AtomicLong(1L)

    suspend fun initializeIdGenerator() = withContext(Dispatchers.IO) {
        val maxId = dao.getMaxMusicQueueItemId()
        idGenerator.set((maxId + 1L).coerceAtLeast(1L))
    }

    fun nextQueueItemId(): Long = idGenerator.getAndIncrement()

    fun wrapSongs(songs: List<Song>): List<QueuedMusicItem> =
        songs.map { QueuedMusicItem(queueItemId = nextQueueItemId(), song = it) }

    suspend fun persistQueue(
        items: List<QueuedMusicItem>,
        currentIndex: Int,
        playbackPositionMs: Long
    ) = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val entities = items.mapIndexed { idx, item ->
            MusicQueueItemEntity.fromSong(
                queueItemId = item.queueItemId,
                orderIndex = idx.toLong(),
                song = item.song,
                addedAtMs = now
            )
        }
        dao.replaceMusicQueueChunked(entities)
        val safeIndex = if (items.isEmpty()) 0 else currentIndex.coerceIn(0, items.lastIndex)
        val activeItemId = items.getOrNull(safeIndex)?.queueItemId ?: -1L
        dao.upsertMusicQueueMeta(
            MusicQueueMetaEntity(
                id = 1,
                currentQueueItemId = activeItemId,
                currentIndex = safeIndex,
                playbackPositionMs = playbackPositionMs.coerceAtLeast(0L),
                updatedAtMs = now
            )
        )
    }

    suspend fun persistPlaybackPosition(
        currentQueueItemId: Long,
        currentIndex: Int,
        playbackPositionMs: Long
    ) = withContext(Dispatchers.IO) {
        dao.upsertMusicQueueMeta(
            MusicQueueMetaEntity(
                id = 1,
                currentQueueItemId = currentQueueItemId,
                currentIndex = currentIndex.coerceAtLeast(0),
                playbackPositionMs = playbackPositionMs.coerceAtLeast(0L),
                updatedAtMs = System.currentTimeMillis()
            )
        )
    }

    suspend fun restoreQueue(): RestoredMusicQueue = withContext(Dispatchers.IO) {
        val entities = dao.getAllMusicQueueItems()
        val maxId = entities.maxOfOrNull { it.queueItemId } ?: 0L
        if (maxId >= idGenerator.get()) {
            idGenerator.set(maxId + 1L)
        }
        val items = entities.map { entity ->
            QueuedMusicItem(
                queueItemId = entity.queueItemId,
                song = entity.toSong()
            )
        }
        val meta = dao.getMusicQueueMeta()
        val resolvedIndex = when {
            items.isEmpty() -> 0
            meta != null && meta.currentQueueItemId > 0L -> {
                val byId = items.indexOfFirst { it.queueItemId == meta.currentQueueItemId }
                if (byId >= 0) byId else meta.currentIndex.coerceIn(0, items.lastIndex)
            }
            meta != null -> meta.currentIndex.coerceIn(0, items.lastIndex)
            else -> 0
        }
        RestoredMusicQueue(
            items = items,
            currentIndex = resolvedIndex,
            playbackPositionMs = meta?.playbackPositionMs?.coerceAtLeast(0L) ?: 0L
        )
    }
}
