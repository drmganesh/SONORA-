package data.library

import android.app.RecoverableSecurityException
import android.content.Context
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import com.example.data.LocalMusicScanner
import com.example.data.PlaylistEntity
import com.example.data.PlaylistSongCrossRef
import com.example.data.SonoraDao
import com.example.model.Album
import com.example.model.Artist
import com.example.model.MusicFolder
import com.example.model.Playlist
import com.example.model.Song
import com.example.model.SortOption
import data.preferences.FolderFilterPreferences
import domain.filter.MusicFolderFilter
import domain.library.LocalSongDeleteResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Dedicated repository for Offline Library data (device audio via MediaStore,
 * folder filtering, local playlists, offline search/sort, and safe file removal).
 */
class OfflineLibraryRepository(
    private val context: Context,
    private val scanner: LocalMusicScanner,
    private val dao: SonoraDao,
    private val folderFilterPreferences: FolderFilterPreferences
) {
    companion object {
        private const val TAG = "OfflineLibraryRepository"
    }

    suspend fun scanLocalSongs(): List<Song> = withContext(Dispatchers.IO) {
        scanner.scanSongs()
    }

    fun applyFolderFilters(rawSongs: List<Song>): List<Song> {
        return MusicFolderFilter.filterSongs(rawSongs, folderFilterPreferences.settings.value)
    }

    fun filterAndSortSongs(
        songs: List<Song>,
        query: String,
        sortOption: SortOption
    ): List<Song> {
        val trimmed = query.trim()
        val searched = if (trimmed.isEmpty()) {
            songs
        } else {
            songs.filter {
                it.title.contains(trimmed, ignoreCase = true) ||
                    it.artist.contains(trimmed, ignoreCase = true) ||
                    it.album.contains(trimmed, ignoreCase = true) ||
                    it.genre.contains(trimmed, ignoreCase = true) ||
                    it.dataPath.contains(trimmed, ignoreCase = true)
            }
        }
        return when (sortOption) {
            SortOption.TITLE_ASC -> searched.sortedBy { it.title.lowercase() }
            SortOption.TITLE_DESC -> searched.sortedByDescending { it.title.lowercase() }
            SortOption.ARTIST_ASC -> searched.sortedBy { it.artist.lowercase() }
            SortOption.ALBUM_ASC -> searched.sortedBy { it.album.lowercase() }
            SortOption.DURATION_DESC -> searched.sortedByDescending { it.durationMs }
            SortOption.DATE_ADDED_DESC -> searched.sortedByDescending { it.dateAdded }
        }
    }

    fun filterAlbums(albums: List<Album>, query: String): List<Album> {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return albums
        return albums.filter {
            it.title.contains(trimmed, ignoreCase = true) ||
                it.artist.contains(trimmed, ignoreCase = true)
        }
    }

    fun filterArtists(artists: List<Artist>, query: String): List<Artist> {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return artists
        return artists.filter { it.name.contains(trimmed, ignoreCase = true) }
    }

    fun filterFolders(folders: List<MusicFolder>, query: String): List<MusicFolder> {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return folders
        return folders.filter {
            it.name.contains(trimmed, ignoreCase = true) ||
                it.path.contains(trimmed, ignoreCase = true)
        }
    }

    fun filterPlaylists(playlists: List<Playlist>, query: String): List<Playlist> {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return playlists
        return playlists.filter { it.name.contains(trimmed, ignoreCase = true) }
    }

    suspend fun createLocalPlaylist(name: String): Long = withContext(Dispatchers.IO) {
        val clean = name.trim()
        if (clean.isEmpty()) return@withContext -1L
        dao.insertPlaylist(PlaylistEntity(name = clean))
    }

    suspend fun renameLocalPlaylist(playlistId: Long, newName: String) = withContext(Dispatchers.IO) {
        val clean = newName.trim()
        if (clean.isNotEmpty()) {
            dao.renamePlaylist(playlistId, clean)
        }
    }

    suspend fun deleteLocalPlaylist(playlistId: Long) = withContext(Dispatchers.IO) {
        dao.clearPlaylistSongs(playlistId)
        dao.deletePlaylist(playlistId)
    }

    suspend fun addSongsToLocalPlaylist(playlistId: Long, songIds: List<Long>) = withContext(Dispatchers.IO) {
        val refs = songIds.distinct().mapIndexed { index, id ->
            PlaylistSongCrossRef(playlistId = playlistId, songId = id, orderIndex = index)
        }
        if (refs.isNotEmpty()) {
            dao.insertPlaylistSongs(refs)
        }
    }

    /**
     * Safely deletes local MediaStore audio files (excluding built-in demo tracks & online tracks).
     * On Android 11+ (API 30+), uses MediaStore.createDeleteRequest when scoped-storage consent is needed.
     * On Android 10 (API 29), captures RecoverableSecurityException to prompt the user cleanly.
     * On Android 8–9 (API 26–28) or app-owned files, deletes directly via ContentResolver / File.
     */
    suspend fun deleteLocalAudioTracks(songs: List<Song>): LocalSongDeleteResult = withContext(Dispatchers.IO) {
        val localSongs = songs.filter { !it.isOnline && it.sourceType != "DOWNLOADED" }
        if (localSongs.isEmpty()) {
            return@withContext LocalSongDeleteResult.Failed("No removable local device tracks selected.")
        }
        if (localSongs.all { it.isDemoTrack }) {
            return@withContext LocalSongDeleteResult.Failed("Built-in sample audio tracks cannot be deleted from device storage.")
        }

        val removable = localSongs.filter { !it.isDemoTrack }

        // On Android 11+ (API 30+), request MediaStore system delete dialog for shared MediaStore content URIs
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val mediaStoreUris = removable
                .map { it.uri }
                .filter { it.scheme == "content" && it.authority == MediaStore.AUTHORITY }
            if (mediaStoreUris.isNotEmpty()) {
                try {
                    val pendingIntent = MediaStore.createDeleteRequest(context.contentResolver, mediaStoreUris)
                    return@withContext LocalSongDeleteResult.RequiresSystemConfirmation(
                        intentSender = pendingIntent.intentSender,
                        pendingSongIds = removable.map { it.id }
                    )
                } catch (t: Throwable) {
                    Log.w(TAG, "MediaStore.createDeleteRequest unavailable, trying direct ContentResolver delete: ${t.message}")
                }
            }
        }

        var deletedCount = 0
        val deletedIds = mutableListOf<Long>()

        for (song in removable) {
            try {
                val removed = when (song.uri.scheme) {
                    "content" -> context.contentResolver.delete(song.uri, null, null) > 0
                    "file" -> song.uri.path?.let { File(it).takeIf { f -> f.exists() }?.delete() } == true
                    else -> song.dataPath.takeIf { it.isNotBlank() }?.let { File(it).takeIf { f -> f.exists() }?.delete() } == true
                }
                if (removed) {
                    deletedCount++
                    deletedIds.add(song.id)
                }
            } catch (sec: SecurityException) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && sec is RecoverableSecurityException) {
                    return@withContext LocalSongDeleteResult.RequiresSystemConfirmation(
                        intentSender = sec.userAction.actionIntent.intentSender,
                        pendingSongIds = listOf(song.id)
                    )
                }
                Log.w(TAG, "Permission denied deleting local track ${song.title}: ${sec.message}")
                return@withContext LocalSongDeleteResult.Failed(
                    "Android denied permission to delete \"${song.title}\". Use your file manager or grant write access."
                )
            } catch (t: Throwable) {
                Log.e(TAG, "Error deleting local track ${song.title}", t)
                return@withContext LocalSongDeleteResult.Failed("Could not delete \"${song.title}\": ${t.message}")
            }
        }

        if (deletedIds.isNotEmpty()) {
            dao.removeSongsFromAllLocalPlaylists(deletedIds)
        }

        if (deletedCount > 0) {
            LocalSongDeleteResult.Deleted(deletedCount)
        } else {
            LocalSongDeleteResult.Failed("No files were removed from storage.")
        }
    }

    suspend fun cleanupDeletedSongReferences(songIds: List<Long>) = withContext(Dispatchers.IO) {
        if (songIds.isNotEmpty()) {
            dao.removeSongsFromAllLocalPlaylists(songIds)
        }
    }
}
