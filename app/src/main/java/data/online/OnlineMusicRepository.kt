package data.online

import com.example.data.OnlineSearchHistoryEntity
import com.example.data.SonoraDao
import com.example.model.Song
import domain.online.AudioQualityPreference
import domain.online.DiscoverSectionState
import domain.online.DiscoverSectionType
import domain.online.OnlineCollectionDetails
import domain.online.OnlineMusicProvider
import domain.online.OnlineResult
import domain.online.OnlineSearchFilter
import domain.online.OnlineSearchPageResult
import domain.online.OnlineTrack
import domain.online.ResolvedOnlineStreamResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/**
 * Single repository coordinating NewPipe search/extraction, connectivity state,
 * ephemeral stream caching, and permanent Room persistence of canonical online track metadata.
 */
class OnlineMusicRepository(
    private val dao: SonoraDao,
    val connectivityMonitor: NetworkConnectivityMonitor,
    private val searchDataSource: OnlineSearchDataSource = OnlineSearchDataSource(connectivityMonitor),
    private val streamResolver: OnlineStreamResolver = OnlineStreamResolver(connectivityMonitor)
) {

    val isOnline: Flow<Boolean> = connectivityMonitor.isOnline
    val isMeteredConnection: Flow<Boolean> = connectivityMonitor.isMeteredConnection

    fun getAvailableProviders(): List<OnlineMusicProvider> {
        return NewPipeInitializer.discoverProviders()
    }

    val allOnlineTracksFlow: Flow<List<Song>> =
        dao.getAllOnlineTracksFlow().map { list ->
            list.map { OnlineTrackMapper.toUnifiedSong(OnlineTrackMapper.mapEntityToOnlineTrack(it)) }
        }

    val savedOnlineTracksFlow: Flow<List<Song>> =
        dao.getSavedOnlineTracksFlow().map { list ->
            list.map { OnlineTrackMapper.toUnifiedSong(OnlineTrackMapper.mapEntityToOnlineTrack(it)) }
        }

    val recentlyPlayedOnlineTracksFlow: Flow<List<Song>> =
        dao.getRecentlyPlayedOnlineTracksFlow().map { list ->
            list.map { OnlineTrackMapper.toUnifiedSong(OnlineTrackMapper.mapEntityToOnlineTrack(it)) }
        }

    val favoriteOnlineTracksFlow: Flow<List<Song>> =
        dao.getFavoriteOnlineTracksFlow().map { list ->
            list.map { OnlineTrackMapper.toUnifiedSong(OnlineTrackMapper.mapEntityToOnlineTrack(it)) }
        }

    val searchHistoryFlow: Flow<List<String>> =
        dao.getOnlineSearchHistoryFlow().map { entries -> entries.map { it.query } }

    suspend fun search(
        query: String,
        provider: OnlineMusicProvider,
        filter: OnlineSearchFilter
    ): OnlineResult<OnlineSearchPageResult> {
        return searchDataSource.search(query, provider, filter)
    }

    suspend fun loadMoreSearchResults(nextPageToken: String): OnlineResult<OnlineSearchPageResult> {
        return searchDataSource.loadMore(nextPageToken)
    }

    suspend fun getSearchSuggestions(query: String, provider: OnlineMusicProvider): List<String> {
        return searchDataSource.getSuggestions(query, provider)
    }

    suspend fun fetchDiscoveryFeed(
        provider: OnlineMusicProvider,
        forceRefresh: Boolean = false
    ): OnlineResult<List<OnlineTrack>> {
        return streamResolver.fetchDiscoveryFeed(provider, forceRefresh)
    }

    suspend fun fetchDiscoverSection(
        sectionType: DiscoverSectionType,
        provider: OnlineMusicProvider,
        countryCode: String = NewPipeInitializer.currentCountryCode(),
        languageCode: String = NewPipeInitializer.currentLanguageCode(),
        discoverLanguage: domain.online.DiscoverMusicLanguage = domain.online.DiscoverMusicLanguage.ALL,
        recentArtistSeed: String? = null,
        recommendationProfile: domain.online.OnlineRecommendationProfile? = null,
        forceRefresh: Boolean = false
    ): DiscoverSectionState {
        return streamResolver.fetchDiscoverSection(
            sectionType = sectionType,
            provider = provider,
            searchDataSource = searchDataSource,
            countryCode = countryCode,
            languageCode = languageCode,
            discoverLanguage = discoverLanguage,
            recentArtistSeed = recentArtistSeed,
            recommendationProfile = recommendationProfile,
            forceRefresh = forceRefresh
        )
    }

    suspend fun loadMoreDiscoverSection(
        sectionType: DiscoverSectionType,
        provider: OnlineMusicProvider,
        previousToken: String
    ): OnlineResult<Pair<List<OnlineTrack>, String?>> {
        return streamResolver.loadMoreDiscoverSection(
            sectionType = sectionType,
            provider = provider,
            previousToken = previousToken,
            searchDataSource = searchDataSource
        )
    }

    fun clearDiscoverCache() {
        streamResolver.clearDiscoverCache()
    }

    suspend fun fetchCollectionDetails(item: OnlineTrack): OnlineResult<OnlineCollectionDetails> {
        return streamResolver.fetchCollectionDetails(item)
    }

    suspend fun resolvePlayableStream(
        song: Song,
        qualityPreference: AudioQualityPreference,
        manualStreamId: String? = null,
        forceRefresh: Boolean = false
    ): OnlineResult<ResolvedOnlineStreamResult> {
        return streamResolver.resolveStream(
            serviceId = song.serviceId,
            providerKey = song.providerKey.ifBlank { OnlineMusicProvider.KEY_YOUTUBE_MUSIC },
            providerName = song.providerName.ifBlank { "Online" },
            contentUrl = song.contentUrl.ifBlank { song.uri.toString() },
            qualityPreference = qualityPreference,
            manualStreamId = manualStreamId,
            forceRefresh = forceRefresh,
            trackTitleHint = song.title,
            trackArtistHint = song.artist,
            expectedDurationMs = song.durationMs
        )
    }

    fun invalidateStreamCache(contentUrl: String) {
        streamResolver.invalidateStreamCache(contentUrl)
    }

    suspend fun saveSearchQuery(query: String, providerKey: String) = withContext(Dispatchers.IO) {
        val clean = query.trim()
        if (clean.isNotBlank()) {
            dao.upsertOnlineSearchHistory(
                OnlineSearchHistoryEntity(
                    query = clean,
                    providerKey = providerKey,
                    timestamp = System.currentTimeMillis()
                )
            )
        }
    }

    suspend fun removeSearchQuery(query: String) = withContext(Dispatchers.IO) {
        dao.deleteOnlineSearchHistory(query)
    }

    suspend fun clearSearchHistory() = withContext(Dispatchers.IO) {
        dao.clearOnlineSearchHistory()
    }

    suspend fun recordOnlinePlay(song: Song) = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val existing = dao.getOnlineTrackById(song.id)
        if (existing == null) {
            val baseTrack = OnlineTrackMapper.toOnlineTrack(song).copy(
                playCount = 1,
                lastPlayedTimestamp = now
            )
            dao.upsertOnlineTrack(
                OnlineTrackMapper.mapOnlineTrackToEntity(baseTrack).copy(
                    playCount = 1,
                    lastPlayedTimestamp = now
                )
            )
        } else {
            dao.incrementOnlinePlayCount(song.id, now)
        }
    }

    suspend fun toggleSaveOnlineTrack(song: Song): Boolean = withContext(Dispatchers.IO) {
        val existing = dao.getOnlineTrackById(song.id)
        val newSaved = !(existing?.isSaved ?: false)
        val now = System.currentTimeMillis()
        if (existing == null) {
            val base = OnlineTrackMapper.toOnlineTrack(song).copy(isSaved = newSaved)
            dao.upsertOnlineTrack(
                OnlineTrackMapper.mapOnlineTrackToEntity(base).copy(
                    isSaved = newSaved,
                    savedTimestamp = if (newSaved) now else 0L
                )
            )
        } else {
            dao.updateOnlineTrackSaved(song.id, newSaved, if (newSaved) now else 0L)
        }
        newSaved
    }

    suspend fun toggleFavoriteOnlineTrack(song: Song): Boolean = withContext(Dispatchers.IO) {
        val existing = dao.getOnlineTrackById(song.id)
        val newFav = !(existing?.isFavorite ?: song.isFavorite)
        if (existing == null) {
            val base = OnlineTrackMapper.toOnlineTrack(song).copy(isFavorite = newFav)
            dao.upsertOnlineTrack(
                OnlineTrackMapper.mapOnlineTrackToEntity(base).copy(isFavorite = newFav)
            )
        } else {
            dao.updateOnlineTrackFavorite(song.id, newFav)
        }
        newFav
    }

    suspend fun isTrackSaved(stableId: Long): Boolean = withContext(Dispatchers.IO) {
        dao.getOnlineTrackById(stableId)?.isSaved ?: false
    }
}
