package data.library

import com.example.data.SonoraDao
import com.example.model.Song
import data.download.DownloadManagerCoordinator
import domain.download.DownloadSortOption
import domain.download.DownloadStorageSummary
import domain.download.DownloadTask
import domain.download.DownloadedTrack
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

/**
 * Dedicated DownloadRepository separating local downloaded audio files from online streaming bookmarks.
 * Delegates storage, SAF permissions, and WorkManager execution to [DownloadManagerCoordinator].
 */
class DownloadRepository(
    val coordinator: DownloadManagerCoordinator,
    private val dao: SonoraDao
) {
    val downloadedTracksFlow: Flow<List<DownloadedTrack>> = coordinator.downloadedTracksFlow

    val downloadTasksFlow: Flow<List<DownloadTask>> = coordinator.allTasksFlow

    val activeDownloadTasksFlow: Flow<List<DownloadTask>> = coordinator.activeTasksFlow

    val storageSummaryFlow: Flow<DownloadStorageSummary> = coordinator.storageSummaryFlow

    val downloadedSongsFlow: Flow<List<Song>> = combine(
        coordinator.downloadedTracksFlow,
        dao.getAllMetadataFlow()
    ) { tracks, metadataList ->
        val metaById = metadataList.associateBy { it.id }
        tracks.map { track ->
            val baseSong = track.toSong()
            val meta = metaById[track.id]
            if (meta != null) {
                baseSong.copy(
                    isFavorite = meta.isFavorite,
                    playCount = meta.playCount,
                    lastPlayedTimestamp = meta.lastPlayedTimestamp,
                    lyrics = meta.customLyrics ?: baseSong.lyrics
                )
            } else {
                baseSong
            }
        }
    }

    fun filterAndSortDownloads(
        tracks: List<DownloadedTrack>,
        query: String,
        sortOption: DownloadSortOption
    ): List<DownloadedTrack> {
        val trimmed = query.trim()
        val filtered = if (trimmed.isEmpty()) {
            tracks
        } else {
            tracks.filter {
                it.title.contains(trimmed, ignoreCase = true) ||
                    it.artist.contains(trimmed, ignoreCase = true) ||
                    it.album.contains(trimmed, ignoreCase = true) ||
                    it.formatLabel.contains(trimmed, ignoreCase = true)
            }
        }
        return when (sortOption) {
            DownloadSortOption.RECENTLY_DOWNLOADED -> filtered.sortedByDescending { it.downloadedAt }
            DownloadSortOption.ARTIST_ASC -> filtered.sortedBy { it.artist.lowercase() }
            DownloadSortOption.ALBUM_ASC -> filtered.sortedBy { it.album.lowercase() }
            DownloadSortOption.TITLE_ASC -> filtered.sortedBy { it.title.lowercase() }
        }
    }

    suspend fun deleteDownloadedTracks(trackIds: List<Long>) {
        coordinator.deleteDownloadedTracks(trackIds)
    }

    suspend fun clearDownloadHistory() {
        coordinator.clearDownloadHistory()
    }
}
