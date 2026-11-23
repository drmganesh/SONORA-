package data.library

import com.example.data.FollowedOnlineArtistEntity
import com.example.data.OnlinePlaylistEntity
import com.example.data.OnlinePlaylistTrackCrossRef
import com.example.data.SavedOnlineAlbumEntity
import com.example.data.SonoraDao
import com.example.model.Song
import data.online.OnlineMusicRepository
import data.online.OnlineTrackMapper
import domain.library.FollowedOnlineArtist
import domain.library.OnlineLibrarySearchResults
import domain.library.OnlineLibrarySortOption
import domain.library.OnlinePlaylist
import domain.library.SavedOnlineAlbum
import domain.online.OnlineCollectionDetails
import domain.online.OnlineItemKind
import domain.online.OnlineTrack
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/**
 * Dedicated repository for the Online Library.
 * Stores ONLY permanent metadata and canonical provider content URLs in Room—never temporary
 * streaming URLs—and supports zero-network local search across all saved online content.
 */
class OnlineLibraryRepository(
    private val dao: SonoraDao,
    private val onlineMusicRepository: OnlineMusicRepository
) {

    val savedTracksFlow: Flow<List<Song>> = onlineMusicRepository.savedOnlineTracksFlow

    val favoriteTracksFlow: Flow<List<Song>> = onlineMusicRepository.favoriteOnlineTracksFlow

    val recentlyPlayedTracksFlow: Flow<List<Song>> = onlineMusicRepository.recentlyPlayedOnlineTracksFlow

    val listeningHistoryFlow: Flow<List<Song>> =
        dao.getOnlineListeningHistoryFlow(limit = 200).map { list ->
            list.map { OnlineTrackMapper.toUnifiedSong(OnlineTrackMapper.mapEntityToOnlineTrack(it)) }
        }

    val savedAlbumsFlow: Flow<List<SavedOnlineAlbum>> =
        dao.getSavedOnlineAlbumsFlow().map { list -> list.map { it.toDomain() } }

    val followedArtistsFlow: Flow<List<FollowedOnlineArtist>> =
        dao.getFollowedOnlineArtistsFlow().map { list -> list.map { it.toDomain() } }

    val onlinePlaylistsFlow: Flow<List<OnlinePlaylist>> = combine(
        dao.getAllOnlinePlaylistsFlow(),
        dao.getAllOnlinePlaylistTrackCrossRefsFlow(),
        dao.getAllOnlineTracksFlow()
    ) { playlists, crossRefs, tracks ->
        val tracksById = tracks.associate { entity ->
            entity.stableId to OnlineTrackMapper.toUnifiedSong(OnlineTrackMapper.mapEntityToOnlineTrack(entity))
        }
        val refsByPlaylist = crossRefs.groupBy { it.playlistId }
        playlists.map { entity ->
            val orderedTracks = refsByPlaylist[entity.id]
                .orEmpty()
                .sortedWith(compareBy<OnlinePlaylistTrackCrossRef> { it.orderIndex }.thenBy { it.addedAt })
                .mapNotNull { tracksById[it.trackStableId] }
            OnlinePlaylist(
                id = entity.id,
                name = entity.name,
                artworkUrl = entity.artworkUrl,
                externalContentUrl = entity.externalContentUrl,
                providerKey = entity.providerKey,
                providerName = entity.providerName,
                createdAt = entity.createdAt,
                updatedAt = entity.updatedAt,
                tracks = orderedTracks
            )
        }
    }

    suspend fun toggleSaveTrack(song: Song): Result<Boolean> = withContext(Dispatchers.IO) {
        if (!song.isOnline) {
            return@withContext Result.failure(
                IllegalStateException("Only online streaming tracks can be saved to Online Library.")
            )
        }
        val canonicalUrl = song.contentUrl.trim()
        if (canonicalUrl.isBlank()) {
            return@withContext Result.failure(
                IllegalStateException("This provider did not supply a permanent track link to save.")
            )
        }
        val saved = onlineMusicRepository.toggleSaveOnlineTrack(song)
        Result.success(saved)
    }

    suspend fun removeSavedTrack(stableId: Long) = withContext(Dispatchers.IO) {
        dao.updateOnlineTrackSaved(stableId, isSaved = false, savedTimestamp = 0L)
    }

    suspend fun toggleFavoriteTrack(song: Song): Result<Boolean> = withContext(Dispatchers.IO) {
        if (!song.isOnline) {
            return@withContext Result.failure(IllegalStateException("Track is not an online stream."))
        }
        Result.success(onlineMusicRepository.toggleFavoriteOnlineTrack(song))
    }

    suspend fun clearListeningHistory() = withContext(Dispatchers.IO) {
        dao.clearOnlineListeningHistory()
    }

    suspend fun toggleSaveAlbum(
        item: OnlineTrack,
        details: OnlineCollectionDetails? = null
    ): Result<Boolean> = withContext(Dispatchers.IO) {
        val canonicalUrl = (details?.contentUrl ?: item.contentUrl).trim()
        if (canonicalUrl.isBlank()) {
            return@withContext Result.failure(
                IllegalStateException("${item.providerName.ifBlank { "This provider" }} does not provide a permanent album link to save.")
            )
        }
        val providerKey = item.providerKey.ifBlank { "online" }
        val stableId = OnlineTrackMapper.computeStableId(providerKey, canonicalUrl)
        val existing = dao.getSavedOnlineAlbumById(stableId)
        if (existing != null) {
            dao.deleteSavedOnlineAlbum(stableId)
            return@withContext Result.success(false)
        }
        val title = details?.title?.takeIf { it.isNotBlank() } ?: item.title
        val artist = item.artist.takeIf { it.isNotBlank() }
            ?: details?.subtitle?.substringBefore("•")?.trim()?.takeIf { it.isNotBlank() }
            ?: item.providerName
        val trackCount = when {
            details != null && details.tracks.isNotEmpty() -> details.tracks.size.toLong()
            item.trackCount > 0L -> item.trackCount
            else -> -1L
        }
        dao.upsertSavedOnlineAlbum(
            SavedOnlineAlbumEntity(
                stableId = stableId,
                serviceId = item.serviceId,
                providerKey = providerKey,
                providerName = item.providerName.ifBlank { "Online" },
                contentUrl = canonicalUrl,
                title = title,
                artist = artist,
                artworkUrl = details?.artworkUrl ?: item.artworkUrl,
                trackCount = trackCount,
                savedTimestamp = System.currentTimeMillis()
            )
        )
        Result.success(true)
    }

    suspend fun removeSavedAlbum(stableId: Long) = withContext(Dispatchers.IO) {
        dao.deleteSavedOnlineAlbum(stableId)
    }

    suspend fun toggleFollowArtist(
        item: OnlineTrack,
        details: OnlineCollectionDetails? = null
    ): Result<Boolean> = withContext(Dispatchers.IO) {
        val canonicalUrl = when {
            item.kind == OnlineItemKind.ARTIST && item.contentUrl.isNotBlank() -> item.contentUrl.trim()
            !item.artistUrl.isNullOrBlank() -> item.artistUrl.trim()
            details != null && details.contentUrl.isNotBlank() && item.kind == OnlineItemKind.ARTIST -> details.contentUrl.trim()
            else -> ""
        }
        if (canonicalUrl.isBlank()) {
            return@withContext Result.failure(
                IllegalStateException("${item.providerName.ifBlank { "This provider" }} does not expose an artist profile link for \"${item.artist}\".")
            )
        }
        val providerKey = item.providerKey.ifBlank { "online" }
        val stableId = OnlineTrackMapper.computeStableId(providerKey, canonicalUrl)
        val existing = dao.getFollowedOnlineArtistById(stableId)
        if (existing != null) {
            dao.deleteFollowedOnlineArtist(stableId)
            return@withContext Result.success(false)
        }
        val name = when {
            item.kind == OnlineItemKind.ARTIST -> details?.title?.takeIf { it.isNotBlank() } ?: item.title
            else -> item.artist.ifBlank { item.title }
        }
        dao.upsertFollowedOnlineArtist(
            FollowedOnlineArtistEntity(
                stableId = stableId,
                serviceId = item.serviceId,
                providerKey = providerKey,
                providerName = item.providerName.ifBlank { "Online" },
                contentUrl = canonicalUrl,
                name = name,
                artworkUrl = details?.artworkUrl ?: item.artworkUrl,
                subscriberCount = item.subscriberCount,
                followedTimestamp = System.currentTimeMillis()
            )
        )
        Result.success(true)
    }

    suspend fun unfollowArtist(stableId: Long) = withContext(Dispatchers.IO) {
        dao.deleteFollowedOnlineArtist(stableId)
    }

    suspend fun createOnlinePlaylist(name: String): Result<Long> = withContext(Dispatchers.IO) {
        val clean = name.trim()
        if (clean.isEmpty()) {
            return@withContext Result.failure(IllegalArgumentException("Playlist name cannot be empty."))
        }
        val now = System.currentTimeMillis()
        val id = dao.insertOnlinePlaylist(
            OnlinePlaylistEntity(
                name = clean,
                createdAt = now,
                updatedAt = now
            )
        )
        Result.success(id)
    }

    suspend fun renameOnlinePlaylist(playlistId: Long, newName: String) = withContext(Dispatchers.IO) {
        val clean = newName.trim()
        if (clean.isNotEmpty()) {
            dao.renameOnlinePlaylist(playlistId, clean)
        }
    }

    suspend fun deleteOnlinePlaylist(playlistId: Long) = withContext(Dispatchers.IO) {
        dao.clearOnlinePlaylistTracks(playlistId)
        dao.deleteOnlinePlaylist(playlistId)
    }

    suspend fun addTrackToOnlinePlaylist(playlistId: Long, song: Song): Result<Unit> = withContext(Dispatchers.IO) {
        if (!song.isOnline || song.contentUrl.isBlank()) {
            return@withContext Result.failure(
                IllegalStateException("Only online tracks with a valid provider link can be added to an Online Playlist.")
            )
        }
        ensureOnlineTrackStored(song)
        val nextOrder = dao.getMaxOnlinePlaylistOrderIndex(playlistId) + 1
        dao.insertOnlinePlaylistTrack(
            OnlinePlaylistTrackCrossRef(
                playlistId = playlistId,
                trackStableId = song.id,
                orderIndex = nextOrder,
                addedAt = System.currentTimeMillis()
            )
        )
        val playlist = dao.getOnlinePlaylistById(playlistId)
        if (playlist != null && playlist.artworkUrl.isNullOrBlank() && !song.artworkUrl.isNullOrBlank()) {
            dao.updateOnlinePlaylistArtwork(playlistId, song.artworkUrl)
        }
        Result.success(Unit)
    }

    suspend fun removeTrackFromOnlinePlaylist(playlistId: Long, trackStableId: Long) = withContext(Dispatchers.IO) {
        dao.removeTrackFromOnlinePlaylist(playlistId, trackStableId)
    }

    /**
     * Saves an extracted external online playlist (from YouTube Music, YouTube, SoundCloud, Bandcamp)
     * into Online Library -> Online Playlists, preventing duplicates by externalContentUrl.
     */
    suspend fun toggleSaveExternalPlaylist(
        item: OnlineTrack,
        details: OnlineCollectionDetails?
    ): Result<Boolean> = withContext(Dispatchers.IO) {
        val canonicalUrl = (details?.contentUrl ?: item.contentUrl).trim()
        if (canonicalUrl.isBlank()) {
            return@withContext Result.failure(
                IllegalStateException("${item.providerName.ifBlank { "This provider" }} does not expose a permanent playlist URL.")
            )
        }
        val existing = dao.getOnlinePlaylistByExternalUrl(canonicalUrl)
        if (existing != null) {
            dao.clearOnlinePlaylistTracks(existing.id)
            dao.deleteOnlinePlaylist(existing.id)
            return@withContext Result.success(false)
        }

        val now = System.currentTimeMillis()
        val playlistId = dao.insertOnlinePlaylist(
            OnlinePlaylistEntity(
                name = details?.title?.takeIf { it.isNotBlank() } ?: item.title,
                artworkUrl = details?.artworkUrl ?: item.artworkUrl,
                externalContentUrl = canonicalUrl,
                providerKey = item.providerKey,
                providerName = item.providerName,
                createdAt = now,
                updatedAt = now
            )
        )
        val tracks = details?.tracks.orEmpty().filter { it.kind == OnlineItemKind.TRACK && it.contentUrl.isNotBlank() }
        val crossRefs = tracks.mapIndexed { idx, track ->
            val song = OnlineTrackMapper.toUnifiedSong(track)
            ensureOnlineTrackStored(song)
            OnlinePlaylistTrackCrossRef(
                playlistId = playlistId,
                trackStableId = song.id,
                orderIndex = idx,
                addedAt = now
            )
        }
        if (crossRefs.isNotEmpty()) {
            dao.insertOnlinePlaylistTracks(crossRefs)
        }
        Result.success(true)
    }

    private suspend fun ensureOnlineTrackStored(song: Song) {
        val existing = dao.getOnlineTrackById(song.id)
        if (existing == null) {
            val baseTrack = OnlineTrackMapper.toOnlineTrack(song)
            dao.upsertOnlineTrack(OnlineTrackMapper.mapOnlineTrackToEntity(baseTrack))
        }
    }

    /**
     * Purely local metadata search over saved online songs, albums, artists, playlists,
     * history, and favourites. Never invokes network extraction.
     */
    fun filterAndSortSavedTracks(
        tracks: List<Song>,
        query: String,
        providerFilterKey: String?,
        sortOption: OnlineLibrarySortOption
    ): List<Song> {
        val trimmed = query.trim()
        val byProvider = if (providerFilterKey.isNullOrBlank()) {
            tracks
        } else {
            tracks.filter { it.providerKey.equals(providerFilterKey, ignoreCase = true) }
        }
        val searched = if (trimmed.isEmpty()) {
            byProvider
        } else {
            byProvider.filter {
                it.title.contains(trimmed, ignoreCase = true) ||
                    it.artist.contains(trimmed, ignoreCase = true) ||
                    it.album.contains(trimmed, ignoreCase = true) ||
                    it.providerName.contains(trimmed, ignoreCase = true)
            }
        }
        return when (sortOption) {
            OnlineLibrarySortOption.RECENTLY_SAVED -> searched // preserve DAO DESC order
            OnlineLibrarySortOption.TITLE_ASC -> searched.sortedBy { it.title.lowercase() }
            OnlineLibrarySortOption.ARTIST_ASC -> searched.sortedBy { it.artist.lowercase() }
            OnlineLibrarySortOption.PROVIDER_ASC -> searched.sortedWith(
                compareBy<Song> { it.providerName.lowercase() }.thenBy { it.title.lowercase() }
            )
            OnlineLibrarySortOption.DURATION_DESC -> searched.sortedByDescending { it.durationMs }
        }
    }

    fun searchSavedContent(
        query: String,
        providerFilterKey: String?,
        sortOption: OnlineLibrarySortOption,
        savedTracks: List<Song>,
        savedAlbums: List<SavedOnlineAlbum>,
        followedArtists: List<FollowedOnlineArtist>,
        onlinePlaylists: List<OnlinePlaylist>,
        listeningHistory: List<Song>,
        favorites: List<Song>
    ): OnlineLibrarySearchResults {
        val trimmed = query.trim()
        val filteredTracks = filterAndSortSavedTracks(savedTracks, trimmed, providerFilterKey, sortOption)
        val filteredHistory = filterAndSortSavedTracks(listeningHistory, trimmed, providerFilterKey, sortOption)
        val filteredFavorites = filterAndSortSavedTracks(favorites, trimmed, providerFilterKey, sortOption)

        val filteredAlbums = savedAlbums.filter { album ->
            (providerFilterKey.isNullOrBlank() || album.providerKey.equals(providerFilterKey, ignoreCase = true)) &&
                (trimmed.isEmpty() ||
                    album.title.contains(trimmed, ignoreCase = true) ||
                    album.artist.contains(trimmed, ignoreCase = true) ||
                    album.providerName.contains(trimmed, ignoreCase = true))
        }

        val filteredArtists = followedArtists.filter { artist ->
            (providerFilterKey.isNullOrBlank() || artist.providerKey.equals(providerFilterKey, ignoreCase = true)) &&
                (trimmed.isEmpty() ||
                    artist.name.contains(trimmed, ignoreCase = true) ||
                    artist.providerName.contains(trimmed, ignoreCase = true))
        }

        val filteredPlaylists = onlinePlaylists.filter { playlist ->
            val providerMatch = providerFilterKey.isNullOrBlank() ||
                playlist.providerKey.equals(providerFilterKey, ignoreCase = true) ||
                playlist.tracks.any { it.providerKey.equals(providerFilterKey, ignoreCase = true) }
            val queryMatch = trimmed.isEmpty() ||
                playlist.name.contains(trimmed, ignoreCase = true) ||
                playlist.providerName.contains(trimmed, ignoreCase = true) ||
                playlist.tracks.any {
                    it.title.contains(trimmed, ignoreCase = true) ||
                        it.artist.contains(trimmed, ignoreCase = true)
                }
            providerMatch && queryMatch
        }

        return OnlineLibrarySearchResults(
            query = trimmed,
            tracks = filteredTracks,
            albums = filteredAlbums,
            artists = filteredArtists,
            playlists = filteredPlaylists,
            history = filteredHistory,
            favorites = filteredFavorites
        )
    }
}
