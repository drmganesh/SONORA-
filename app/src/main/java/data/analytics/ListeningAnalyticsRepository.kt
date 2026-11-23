package data.analytics

import com.example.data.SonoraDao
import kotlinx.coroutines.flow.Flow

/**
 * Repository encapsulating local listening analytics events and playback session restoration state.
 * Completely independent from downloaded music and playlists.
 */
class ListeningAnalyticsRepository(
    private val dao: SonoraDao
) {
    val allEventsFlow: Flow<List<ListeningEventEntity>> = dao.getAllListeningEventsFlow()

    suspend fun recordEvent(event: ListeningEventEntity) {
        if (event.listenedMs < 1_000L) return
        dao.insertListeningEvent(event)
    }

    suspend fun getAllEventsOnce(): List<ListeningEventEntity> = dao.getAllListeningEventsOnce()

    /**
     * Clears listening history analytics ONLY. Never deletes downloaded tracks or playlists.
     */
    suspend fun clearListeningHistory() {
        dao.clearAllListeningEvents()
    }

    suspend fun savePlaybackSession(snapshot: PlaybackSessionSnapshotEntity) {
        dao.upsertPlaybackSessionSnapshot(snapshot)
    }

    suspend fun getPlaybackSession(): PlaybackSessionSnapshotEntity? {
        return dao.getPlaybackSessionSnapshot()
    }
}
