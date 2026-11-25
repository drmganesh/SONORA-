package data.online

import com.example.model.Artist
import com.example.model.Playlist
import com.example.model.Song
import data.analytics.ListeningEventEntity
import data.preferences.CachedSonoraRecommendations
import data.preferences.SonoraRecommendationCachePreferences
import domain.library.FollowedOnlineArtist
import domain.library.OnlinePlaylist
import domain.onboarding.MusicTasteProfile
import domain.online.DiscoverMusicLanguage
import domain.online.DiscoverSectionState
import domain.online.DiscoverSectionType
import domain.online.OnlineItemKind
import domain.online.OnlineMusicProvider
import domain.online.OnlineResult
import domain.online.OnlineSearchFilter
import domain.online.OnlineTrack
import domain.online.RecommendedSongItem
import domain.online.RegionMusicEligibilityFilter
import domain.online.SonoraPersonalizedRecommendationEngine
import domain.online.SonoraRecommendUiState
import domain.online.SonoraRecommendWeights
import domain.online.SonoraUserTasteProfile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

/**
 * Snapshot of purely local SONORA user data used to build personalized recommendations.
 */
data class LocalPersonalizationSnapshot(
    val likedLocalSongs: List<Song>,
    val likedOnlineSongs: List<Song>,
    val followedOnlineArtists: List<FollowedOnlineArtist>,
    val favouriteLocalArtists: List<Artist>,
    val localPlaylists: List<Playlist>,
    val localPlaylistSongs: Map<Long, List<Song>>,
    val onlinePlaylists: List<OnlinePlaylist>,
    val listeningEvents: List<ListeningEventEntity>,
    val recentlyPlayedOnline: List<Song>,
    val recentlyPlayedLocal: List<Song>,
    val countryCode: String,
    val languageCode: String,
    val discoverLanguage: DiscoverMusicLanguage,
    val strictIndiaOnly: Boolean = RegionMusicEligibilityFilter.isIndiaRegion(countryCode),
    val musicTasteProfile: MusicTasteProfile = MusicTasteProfile()
)

/**
 * Coordinates real NewPipe Extractor candidate discovery, local-only taste profiling,
 * weighted ranking, caching, offline resilience, and unavailable-track filtering for
 * SONORA's "Sonora Recommend" Home section.
 */
class SonoraRecommendationCoordinator(
    private val cachePreferences: SonoraRecommendationCachePreferences,
    private val searchCandidates: suspend (
        query: String,
        provider: OnlineMusicProvider,
        countryCode: String,
        discoverLanguage: DiscoverMusicLanguage
    ) -> OnlineResult<List<OnlineTrack>>,
    private val fetchFollowedArtistTracks: suspend (OnlineTrack) -> OnlineResult<List<OnlineTrack>> = {
        OnlineResult.Success(emptyList())
    },
    private val weights: SonoraRecommendWeights = SonoraRecommendWeights()
) {
    companion object {
        private const val CACHE_TTL_MS = 20L * 60L * 1000L // 20 minutes before automatic refresh
        private const val MAX_PARALLEL_SEED_QUERIES = 3

        fun fromRepository(
            cachePreferences: SonoraRecommendationCachePreferences,
            onlineRepository: OnlineMusicRepository,
            weights: SonoraRecommendWeights = SonoraRecommendWeights()
        ): SonoraRecommendationCoordinator {
            return SonoraRecommendationCoordinator(
                cachePreferences = cachePreferences,
                searchCandidates = { query, provider, countryCode, discoverLanguage ->
                    onlineRepository.searchRegionRecommendations(
                        query = query,
                        provider = provider,
                        filter = OnlineSearchFilter.SONGS,
                        countryCode = countryCode,
                        discoverLanguage = discoverLanguage
                    )
                },
                fetchFollowedArtistTracks = { artistItem ->
                    when (val res = onlineRepository.fetchCollectionDetails(artistItem)) {
                        is OnlineResult.Success -> OnlineResult.Success(
                            res.value.tracks.filter { it.kind == OnlineItemKind.TRACK }.take(8)
                        )
                        is OnlineResult.Error -> res
                    }
                },
                weights = weights
            )
        }
    }

    private val unavailableTrackUrls: MutableSet<String> = ConcurrentHashMap.newKeySet()

    fun markTrackUnavailable(contentUrl: String) {
        val norm = SonoraPersonalizedRecommendationEngine.normalizeTrackUrl(contentUrl)
        if (norm.isNotBlank()) {
            unavailableTrackUrls.add(norm)
        }
    }

    fun getUnavailableUrlsSnapshot(): Set<String> = unavailableTrackUrls.toSet()

    /**
     * Loads or refreshes personalized recommendations for Online Music Home.
     *
     * - Returns fresh cached recommendations if valid and [forceRefresh] is false.
     * - When offline, gracefully returns cached region-compatible recommendations without crashing.
     * - When online, combines already-loaded discover sections with up to 3 targeted NewPipe queries
     *   (plus followed artist tracks when available), ranks with [SonoraPersonalizedRecommendationEngine],
     *   and caches metadata locally.
     */
    suspend fun loadRecommendations(
        snapshot: LocalPersonalizationSnapshot,
        provider: OnlineMusicProvider,
        existingDiscoverSections: Map<DiscoverSectionType, DiscoverSectionState>,
        isNetworkOnline: Boolean,
        forceRefresh: Boolean,
        refreshGeneration: Int,
        nowMs: Long = System.currentTimeMillis()
    ): SonoraRecommendUiState = withContext(Dispatchers.IO) {
        val interactions = cachePreferences.getInteractionSignalOnce()
        val profile = SonoraPersonalizedRecommendationEngine.buildUserTasteProfile(
            likedLocalSongs = snapshot.likedLocalSongs,
            likedOnlineSongs = snapshot.likedOnlineSongs,
            followedOnlineArtists = snapshot.followedOnlineArtists,
            favouriteLocalArtists = snapshot.favouriteLocalArtists,
            localPlaylists = snapshot.localPlaylists,
            localPlaylistSongs = snapshot.localPlaylistSongs,
            onlinePlaylists = snapshot.onlinePlaylists,
            listeningEvents = snapshot.listeningEvents,
            recentlyPlayedOnline = snapshot.recentlyPlayedOnline,
            recentlyPlayedLocal = snapshot.recentlyPlayedLocal,
            interactions = interactions,
            countryCode = snapshot.countryCode,
            languageCode = snapshot.languageCode,
            discoverLanguage = snapshot.discoverLanguage,
            strictIndiaOnly = snapshot.strictIndiaOnly,
            weights = weights,
            nowMs = nowMs,
            musicTasteProfile = snapshot.musicTasteProfile
        )

        val cached = cachePreferences.getCachedRecommendationsOnce()
        val cacheMatchesContext = cached != null &&
            cached.countryCode.equals(profile.countryCode, ignoreCase = true) &&
            cached.discoverLanguageId.equals(profile.discoverLanguage.id, ignoreCase = true) &&
            (cached.providerKey.isBlank() || cached.providerKey.equals(provider.key, ignoreCase = true))

        // Offline mode: serve cached recommendations cleanly, never crash
        if (!isNetworkOnline) {
            val cachedRegionEligible = filterCachedForCurrentRules(cached, profile, interactions)
            return@withContext if (cachedRegionEligible.isNotEmpty()) {
                SonoraRecommendUiState(
                    items = cachedRegionEligible,
                    isLoading = false,
                    isRefreshing = false,
                    isColdStartDiscovery = !profile.hasPersonalizationSignals,
                    isServingCachedOffline = true,
                    errorMessage = null,
                    profileSummary = profile.summarySubtitle,
                    lastUpdatedAtMs = cached?.cachedAtMs ?: 0L,
                    refreshGeneration = refreshGeneration
                )
            } else {
                SonoraRecommendUiState(
                    items = emptyList(),
                    isLoading = false,
                    isRefreshing = false,
                    isColdStartDiscovery = !profile.hasPersonalizationSignals,
                    isServingCachedOffline = false,
                    errorMessage = "You're offline. Reconnect to refresh Sonora Recommend.",
                    profileSummary = profile.summarySubtitle,
                    lastUpdatedAtMs = 0L,
                    refreshGeneration = refreshGeneration
                )
            }
        }

        // Reuse valid warm cache when not forcing a refresh and no newer interaction invalidated it
        if (!forceRefresh && cacheMatchesContext && cached != null) {
            val isFresh = (nowMs - cached.cachedAtMs) in 0L..CACHE_TTL_MS
            val interactedAfterCache = interactions.lastUpdatedAtMs > cached.cachedAtMs ||
                snapshot.musicTasteProfile.lastUpdatedAtMs > cached.cachedAtMs
            if (isFresh && !interactedAfterCache) {
                val validCached = filterCachedForCurrentRules(cached, profile, interactions)
                if (validCached.isNotEmpty()) {
                    return@withContext SonoraRecommendUiState(
                        items = validCached,
                        isLoading = false,
                        isRefreshing = false,
                        isColdStartDiscovery = !profile.hasPersonalizationSignals,
                        isServingCachedOffline = false,
                        errorMessage = null,
                        profileSummary = profile.summarySubtitle,
                        lastUpdatedAtMs = cached.cachedAtMs,
                        refreshGeneration = refreshGeneration
                    )
                }
            }
        }

        // Gather real candidates from existing discover sections (zero extra network overhead)
        val candidatePool = mutableListOf<OnlineTrack>()
        existingDiscoverSections.values
            .filterIsInstance<DiscoverSectionState.Success>()
            .flatMap { it.items }
            .filter { it.kind == OnlineItemKind.TRACK }
            .forEach { candidatePool.add(it) }

        // Select up to MAX_PARALLEL_SEED_QUERIES rotated by refreshGeneration so Refresh discovers new tracks
        val allSeeds = profile.seedQueries
        val rotatedSeeds = if (allSeeds.isNotEmpty() && refreshGeneration > 0) {
            val offset = refreshGeneration % allSeeds.size
            (allSeeds.drop(offset) + allSeeds.take(offset)).take(MAX_PARALLEL_SEED_QUERIES)
        } else {
            allSeeds.take(MAX_PARALLEL_SEED_QUERIES)
        }

        var lastNetworkError: String? = null
        coroutineScope {
            val searchJobs = rotatedSeeds.map { seed ->
                async {
                    searchCandidates(
                        seed.query,
                        provider,
                        profile.countryCode,
                        profile.discoverLanguage
                    )
                }
            }

            // Also pull latest/popular tracks from the highest-priority followed artist when available
            val followedJob = snapshot.followedOnlineArtists
                .firstOrNull { artist ->
                    artist.contentUrl.isNotBlank() &&
                        (!profile.strictIndiaOnly ||
                            RegionMusicEligibilityFilter.isVerifiedIndianMusic(artist.name, artist.name))
                }
                ?.let { followed ->
                    async {
                        fetchFollowedArtistTracks(
                            OnlineTrack(
                                stableId = followed.stableId,
                                serviceId = followed.serviceId,
                                providerKey = followed.providerKey.ifBlank { provider.key },
                                providerName = followed.providerName.ifBlank { provider.displayName },
                                contentUrl = followed.contentUrl,
                                title = followed.name,
                                artist = followed.name,
                                artistUrl = followed.contentUrl,
                                artworkUrl = followed.artworkUrl,
                                kind = OnlineItemKind.ARTIST
                            )
                        )
                    }
                }

            searchJobs.awaitAll().forEach { result ->
                when (result) {
                    is OnlineResult.Success -> candidatePool.addAll(result.value)
                    is OnlineResult.Error -> lastNetworkError = result.message
                }
            }
            followedJob?.await()?.let { result ->
                if (result is OnlineResult.Success) {
                    candidatePool.addAll(result.value)
                }
            }
        }

        val ranked = SonoraPersonalizedRecommendationEngine.rankRecommendations(
            candidates = candidatePool,
            profile = profile,
            interactions = interactions,
            unavailableTrackUrls = unavailableTrackUrls,
            weights = weights,
            refreshGeneration = refreshGeneration
        )

        if (ranked.isNotEmpty()) {
            cachePreferences.saveCachedRecommendations(
                CachedSonoraRecommendations(
                    countryCode = profile.countryCode,
                    providerKey = provider.key,
                    discoverLanguageId = profile.discoverLanguage.id,
                    profileSummary = profile.summarySubtitle,
                    isColdStartDiscovery = !profile.hasPersonalizationSignals,
                    cachedAtMs = nowMs,
                    items = ranked
                )
            )
            cachePreferences.recordShownTracks(ranked.map { it.track.contentUrl })
            return@withContext SonoraRecommendUiState(
                items = ranked,
                isLoading = false,
                isRefreshing = false,
                isColdStartDiscovery = !profile.hasPersonalizationSignals,
                isServingCachedOffline = false,
                errorMessage = null,
                profileSummary = profile.summarySubtitle,
                lastUpdatedAtMs = nowMs,
                refreshGeneration = refreshGeneration
            )
        }

        // Fallback to valid cached recommendations if live network extraction yielded empty/error
        val fallbackCached = filterCachedForCurrentRules(cached, profile, interactions)
        if (fallbackCached.isNotEmpty()) {
            return@withContext SonoraRecommendUiState(
                items = fallbackCached,
                isLoading = false,
                isRefreshing = false,
                isColdStartDiscovery = !profile.hasPersonalizationSignals,
                isServingCachedOffline = true,
                errorMessage = null,
                profileSummary = profile.summarySubtitle,
                lastUpdatedAtMs = cached?.cachedAtMs ?: 0L,
                refreshGeneration = refreshGeneration
            )
        }

        SonoraRecommendUiState(
            items = emptyList(),
            isLoading = false,
            isRefreshing = false,
            isColdStartDiscovery = !profile.hasPersonalizationSignals,
            isServingCachedOffline = false,
            errorMessage = lastNetworkError ?: RegionMusicEligibilityFilter.emptyRegionRecommendationsMessage(
                sectionTitle = "Sonora Recommend songs",
                countryCode = profile.countryCode,
                providerName = provider.displayName
            ),
            profileSummary = profile.summarySubtitle,
            lastUpdatedAtMs = 0L,
            refreshGeneration = refreshGeneration
        )
    }

    private fun filterCachedForCurrentRules(
        cached: CachedSonoraRecommendations?,
        profile: SonoraUserTasteProfile,
        interactions: domain.online.UserMusicInteractionSignal
    ): List<RecommendedSongItem> {
        if (cached == null) return emptyList()
        if (!cached.countryCode.equals(profile.countryCode, ignoreCase = true)) return emptyList()
        val dislikedUrls = interactions.dislikedTrackUrls
            .map { SonoraPersonalizedRecommendationEngine.normalizeTrackUrl(it) }
            .toSet()
        return cached.items.filter { item ->
            val track = item.track
            val normUrl = SonoraPersonalizedRecommendationEngine.normalizeTrackUrl(track.contentUrl)
            if (!SonoraPersonalizedRecommendationEngine.isCandidateMetadataPlayable(track)) return@filter false
            if (normUrl in unavailableTrackUrls) return@filter false
            if (track.stableId in interactions.dislikedTrackIds || normUrl in dislikedUrls) return@filter false
            if (profile.strictIndiaOnly && !RegionMusicEligibilityFilter.isVerifiedIndianTrack(track)) return@filter false
            true
        }
    }

}
