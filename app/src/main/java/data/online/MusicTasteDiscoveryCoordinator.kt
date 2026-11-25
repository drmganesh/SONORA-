package data.online

import com.example.model.Song
import domain.library.FollowedOnlineArtist
import domain.onboarding.MusicTasteArtist
import domain.onboarding.MusicTasteCatalog
import domain.onboarding.MusicTasteDiscoveryTrack
import domain.online.DiscoverMusicLanguage
import domain.online.OnlineItemKind
import domain.online.OnlineMusicProvider
import domain.online.OnlineResult
import domain.online.OnlineSearchFilter
import domain.online.OnlineTrack
import domain.online.RegionMusicEligibilityFilter
import domain.online.SonoraPersonalizedRecommendationEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext

/**
 * Coordinates real artist discovery, artist search, related artists expansion, and finite
 * real song discovery sessions (up to 18 playable songs) using SONORA's existing
 * NewPipe Extractor online discovery layer and cached real metadata.
 *
 * Strictly forbids fabricated artists or fake songs.
 */
class MusicTasteDiscoveryCoordinator(
    private val searchOnline: suspend (
        query: String,
        provider: OnlineMusicProvider,
        filter: OnlineSearchFilter
    ) -> OnlineResult<List<OnlineTrack>>,
    private val fetchDiscoveryFeed: suspend (
        provider: OnlineMusicProvider,
        countryCode: String
    ) -> OnlineResult<List<OnlineTrack>>
) {
    companion object {
        fun fromRepository(repository: OnlineMusicRepository): MusicTasteDiscoveryCoordinator {
            return MusicTasteDiscoveryCoordinator(
                searchOnline = { query, provider, filter ->
                    when (val res = repository.search(query, provider, filter)) {
                        is OnlineResult.Success -> OnlineResult.Success(res.value.items)
                        is OnlineResult.Error -> res
                    }
                },
                fetchDiscoveryFeed = { provider, countryCode ->
                    repository.fetchDiscoveryFeed(
                        provider = provider,
                        forceRefresh = false,
                        countryCode = countryCode
                    )
                }
            )
        }
    }

    /**
     * Loads real artists from existing cached/followed library data and live online discovery.
     * Deduplicates strictly by normalized artist name and filters out placeholder names.
     */
    suspend fun loadInitialArtists(
        provider: OnlineMusicProvider,
        countryCode: String,
        discoverLanguage: DiscoverMusicLanguage,
        selectedArtists: List<MusicTasteArtist>,
        followedArtists: List<FollowedOnlineArtist>,
        cachedOnlineTracks: List<Song>,
        isNetworkOnline: Boolean
    ): Result<List<MusicTasteArtist>> = withContext(Dispatchers.IO) {
        val collected = LinkedHashMap<String, MusicTasteArtist>()

        fun addArtist(artist: MusicTasteArtist) {
            if (!SonoraPersonalizedRecommendationEngine.isValidArtistName(artist.name)) return
            val key = artist.normalizedKey
            if (key.isBlank()) return
            val existing = collected[key]
            if (existing == null) {
                collected[key] = artist
            } else if (existing.imageUrl.isNullOrBlank() && !artist.imageUrl.isNullOrBlank()) {
                collected[key] = existing.copy(
                    imageUrl = artist.imageUrl,
                    channelUrl = existing.channelUrl ?: artist.channelUrl
                )
            }
        }

        // Preserve already-selected artists at the top
        selectedArtists.forEach { addArtist(it) }

        // Reuse real followed artists & cached online track artists (works offline too)
        followedArtists.forEach { followed ->
            addArtist(
                MusicTasteArtist(
                    id = followed.contentUrl.ifBlank { MusicTasteArtist.normalizeArtistName(followed.name) },
                    name = followed.name.trim(),
                    imageUrl = followed.artworkUrl,
                    channelUrl = followed.contentUrl.takeIf { it.isNotBlank() },
                    providerKey = followed.providerKey.ifBlank { provider.key },
                    serviceId = followed.serviceId
                )
            )
        }
        cachedOnlineTracks.take(40).forEach { song ->
            if (SonoraPersonalizedRecommendationEngine.isValidArtistName(song.artist)) {
                addArtist(
                    MusicTasteArtist(
                        id = MusicTasteArtist.normalizeArtistName(song.artist),
                        name = song.artist.trim(),
                        imageUrl = song.artworkUrl,
                        providerKey = song.providerKey.ifBlank { provider.key },
                        serviceId = song.serviceId
                    )
                )
            }
        }

        if (!isNetworkOnline) {
            return@withContext Result.success(collected.values.toList())
        }

        val regionSeed = if (RegionMusicEligibilityFilter.isIndiaRegion(countryCode)) {
            if (discoverLanguage != DiscoverMusicLanguage.ALL) {
                "${discoverLanguage.searchKeyword} popular singers artists"
            } else {
                "popular Indian singers music artists"
            }
        } else {
            "popular music artists singers $countryCode"
        }

        var lastError: String? = null
        coroutineScope {
            val artistSearchJob = async {
                searchOnline(regionSeed, provider, OnlineSearchFilter.ARTISTS)
            }
            val discoveryFeedJob = async {
                fetchDiscoveryFeed(provider, countryCode)
            }
            val globalArtistsJob = async {
                val secondaryQuery = if (RegionMusicEligibilityFilter.isIndiaRegion(countryCode)) {
                    "top Bollywood Indie Punjabi Tamil music artists"
                } else {
                    "top trending music artists"
                }
                searchOnline(secondaryQuery, provider, OnlineSearchFilter.ARTISTS)
            }

            listOf(artistSearchJob, globalArtistsJob).awaitAll().forEach { res ->
                when (res) {
                    is OnlineResult.Success -> {
                        extractArtistsFromItems(res.value, provider).forEach { addArtist(it) }
                    }
                    is OnlineResult.Error -> lastError = res.message
                }
            }

            when (val feedRes = discoveryFeedJob.await()) {
                is OnlineResult.Success -> {
                    extractArtistsFromItems(feedRes.value, provider).forEach { addArtist(it) }
                }
                is OnlineResult.Error -> if (lastError == null) lastError = feedRes.message
            }
        }

        if (collected.isEmpty() && lastError != null) {
            Result.failure(IllegalStateException(lastError))
        } else {
            Result.success(collected.values.take(36))
        }
    }

    /**
     * Searches real artists via NewPipe Extractor using the existing online music repository.
     */
    suspend fun searchArtists(
        query: String,
        provider: OnlineMusicProvider,
        isNetworkOnline: Boolean,
        cachedCandidates: List<MusicTasteArtist> = emptyList()
    ): Result<List<MusicTasteArtist>> = withContext(Dispatchers.IO) {
        val clean = query.trim()
        if (clean.isBlank()) return@withContext Result.success(emptyList())

        if (!isNetworkOnline) {
            val localMatches = cachedCandidates.filter {
                it.name.contains(clean, ignoreCase = true)
            }.distinctBy { it.normalizedKey }
            return@withContext Result.success(localMatches)
        }

        val collected = LinkedHashMap<String, MusicTasteArtist>()
        fun addArtist(artist: MusicTasteArtist) {
            if (!SonoraPersonalizedRecommendationEngine.isValidArtistName(artist.name)) return
            val key = artist.normalizedKey
            if (key.isBlank()) return
            val existing = collected[key]
            if (existing == null || (existing.imageUrl.isNullOrBlank() && !artist.imageUrl.isNullOrBlank())) {
                collected[key] = artist
            }
        }

        when (val artistResult = searchOnline(clean, provider, OnlineSearchFilter.ARTISTS)) {
            is OnlineResult.Success -> {
                extractArtistsFromItems(artistResult.value, provider).forEach { addArtist(it) }
            }
            is OnlineResult.Error -> {
                if (collected.isEmpty()) {
                    // Fallback to song search to derive real artist entries if artist filter failed
                    val songsRes = searchOnline("$clean music", provider, OnlineSearchFilter.SONGS)
                    if (songsRes is OnlineResult.Success) {
                        extractArtistsFromItems(songsRes.value, provider).forEach { addArtist(it) }
                    } else {
                        return@withContext Result.failure(IllegalStateException(artistResult.message))
                    }
                }
            }
        }

        // Enrich sparse results with real artist metadata from songs matching the query
        if (collected.size < 6) {
            when (val songsResult = searchOnline(clean, provider, OnlineSearchFilter.SONGS)) {
                is OnlineResult.Success -> {
                    extractArtistsFromItems(songsResult.value, provider)
                        .filter { it.name.contains(clean, ignoreCase = true) || collected.size < 10 }
                        .forEach { addArtist(it) }
                }
                is OnlineResult.Error -> Unit
            }
        }

        Result.success(collected.values.take(24))
    }

    /**
     * Fetches real related artists when the user taps/selects an artist in Step 1.
     */
    suspend fun loadRelatedArtists(
        seedArtist: MusicTasteArtist,
        provider: OnlineMusicProvider,
        existingArtistKeys: Set<String>,
        isNetworkOnline: Boolean
    ): List<MusicTasteArtist> = withContext(Dispatchers.IO) {
        if (!isNetworkOnline) return@withContext emptyList()
        val seedKey = seedArtist.normalizedKey
        if (seedKey.isBlank()) return@withContext emptyList()

        val found = LinkedHashMap<String, MusicTasteArtist>()
        fun tryAdd(candidate: MusicTasteArtist) {
            if (!SonoraPersonalizedRecommendationEngine.isValidArtistName(candidate.name)) return
            val key = candidate.normalizedKey
            if (key.isBlank() || key == seedKey || key in existingArtistKeys) return
            if (key !in found) {
                found[key] = candidate.copy(relatedToArtistName = seedArtist.name)
            }
        }

        coroutineScope {
            val similarArtistsJob = async {
                searchOnline("artists similar to ${seedArtist.name}", provider, OnlineSearchFilter.ARTISTS)
            }
            val collabSongsJob = async {
                searchOnline("${seedArtist.name} collaboration similar singers songs", provider, OnlineSearchFilter.SONGS)
            }

            when (val r1 = similarArtistsJob.await()) {
                is OnlineResult.Success -> extractArtistsFromItems(r1.value, provider).forEach { tryAdd(it) }
                is OnlineResult.Error -> Unit
            }
            when (val r2 = collabSongsJob.await()) {
                is OnlineResult.Success -> extractArtistsFromItems(r2.value, provider).forEach { tryAdd(it) }
                is OnlineResult.Error -> Unit
            }
        }

        found.values.take(8)
    }

    /**
     * Builds a finite discovery session (up to [targetCount] = 18 real playable songs)
     * tailored to the user's selected artists and genres.
     *
     * Excludes:
     * - Duplicate tracks (URL, stableId, or normalized title|artist)
     * - Already-liked tracks
     * - Tracks already decided in prior sessions
     * - Unplayable / live / deleted / unavailable tracks
     *
     * Never fabricates placeholder songs or fake artwork.
     */
    suspend fun buildDiscoverySession(
        selectedArtists: List<MusicTasteArtist>,
        selectedGenres: List<String>,
        provider: OnlineMusicProvider,
        countryCode: String,
        discoverLanguage: DiscoverMusicLanguage,
        existingLikedKeys: Set<String>,
        alreadyDecidedUrls: Set<String>,
        cachedOnlineTracks: List<Song>,
        isNetworkOnline: Boolean,
        targetCount: Int = MusicTasteCatalog.DEFAULT_DISCOVERY_SESSION_SIZE
    ): Result<List<MusicTasteDiscoveryTrack>> = withContext(Dispatchers.IO) {
        val seenUrls = HashSet<String>(alreadyDecidedUrls)
        val seenIds = HashSet<Long>()
        val seenTitleArtist = HashSet<String>()
        val accepted = mutableListOf<MusicTasteDiscoveryTrack>()
        val artistCounts = HashMap<String, Int>()

        fun tryAccept(track: OnlineTrack, reasonLabel: String): Boolean {
            if (accepted.size >= targetCount) return false
            if (!SonoraPersonalizedRecommendationEngine.isCandidateMetadataPlayable(track)) return false
            val normUrl = SonoraPersonalizedRecommendationEngine.normalizeTrackUrl(track.contentUrl)
            val titleArtistKey = SonoraPersonalizedRecommendationEngine.buildTrackIdentityKey(track.title, track.artist)
            if (normUrl.isBlank()) return false
            if (normUrl in seenUrls || track.stableId in seenIds || titleArtistKey in seenTitleArtist) return false
            if (normUrl in existingLikedKeys || titleArtistKey in existingLikedKeys) return false

            val artistKey = MusicTasteArtist.normalizeArtistName(track.artist)
            val maxPerArtist = if (selectedArtists.size <= 2) 3 else 2
            if (artistKey.isNotBlank() && (artistCounts[artistKey] ?: 0) >= maxPerArtist) return false

            seenUrls.add(normUrl)
            seenIds.add(track.stableId)
            seenTitleArtist.add(titleArtistKey)
            if (artistKey.isNotBlank()) {
                artistCounts[artistKey] = (artistCounts[artistKey] ?: 0) + 1
            }

            accepted.add(
                MusicTasteDiscoveryTrack(
                    stableId = track.stableId,
                    serviceId = track.serviceId,
                    providerKey = track.providerKey.ifBlank { provider.key },
                    providerName = track.providerName.ifBlank { provider.displayName },
                    contentUrl = track.contentUrl,
                    title = track.title.trim(),
                    artist = track.artist.trim().ifBlank { provider.displayName },
                    album = track.album.trim(),
                    artworkUrl = track.artworkUrl,
                    durationMs = track.durationMs,
                    reasonLabel = reasonLabel
                )
            )
            return true
        }

        if (!isNetworkOnline) {
            cachedOnlineTracks.forEach { song ->
                if (!song.isFavorite && song.contentUrl.isNotBlank()) {
                    val matchingArtist = selectedArtists.firstOrNull {
                        it.normalizedKey == MusicTasteArtist.normalizeArtistName(song.artist)
                    }
                    val reason = matchingArtist?.let { "Because you picked ${it.name}" }
                        ?: "From your cached discovery"
                    tryAccept(OnlineTrackMapper.toOnlineTrack(song), reason)
                }
            }
            return@withContext Result.success(accepted)
        }

        // Build targeted real queries from selected artists + selected genres (or region fallback)
        val queries = mutableListOf<Pair<String, String>>()
        selectedArtists.take(4).forEach { artist ->
            queries.add("${artist.name} best songs" to "Because you picked ${artist.name}")
        }
        selectedGenres.take(4).forEach { genre ->
            queries.add("$genre popular hit songs" to "Because you like $genre")
        }
        if (queries.isEmpty()) {
            val fallbackQuery = if (RegionMusicEligibilityFilter.isIndiaRegion(countryCode)) {
                if (discoverLanguage != DiscoverMusicLanguage.ALL) {
                    "${discoverLanguage.searchKeyword} popular hit songs"
                } else {
                    "Indian popular hit melody songs"
                }
            } else {
                "popular hit songs $countryCode"
            }
            queries.add(fallbackQuery to "Popular picks for you")
            queries.add("fresh indie acoustic pop songs" to "Fresh discovery")
        }

        var lastError: String? = null
        val queryBatches = queries.take(6)
        val fetchedGroups: List<Pair<String, List<OnlineTrack>>> = coroutineScope {
            queryBatches.map { (query, reason) ->
                async {
                    when (val res = searchOnline(query, provider, OnlineSearchFilter.SONGS)) {
                        is OnlineResult.Success -> reason to res.value
                        is OnlineResult.Error -> {
                            lastError = res.message
                            reason to emptyList()
                        }
                    }
                }
            }.awaitAll()
        }

        // Round-robin interleave tracks across artists/genres for natural variety
        val maxGroupSize = fetchedGroups.maxOfOrNull { it.second.size } ?: 0
        for (i in 0 until maxGroupSize) {
            if (accepted.size >= targetCount) break
            for ((reason, tracks) in fetchedGroups) {
                if (accepted.size >= targetCount) break
                val candidate = tracks.getOrNull(i) ?: continue
                tryAccept(candidate, reason)
            }
        }

        // Top up with real cached tracks if network returned fewer than targetCount
        if (accepted.size < targetCount) {
            cachedOnlineTracks.forEach { song ->
                if (accepted.size >= targetCount) return@forEach
                if (!song.isFavorite && song.contentUrl.isNotBlank()) {
                    tryAccept(OnlineTrackMapper.toOnlineTrack(song), "Recommended for you")
                }
            }
        }

        if (accepted.isEmpty() && lastError != null) {
            Result.failure(IllegalStateException(lastError))
        } else {
            Result.success(accepted)
        }
    }

    private fun extractArtistsFromItems(
        items: List<OnlineTrack>,
        provider: OnlineMusicProvider
    ): List<MusicTasteArtist> {
        val result = LinkedHashMap<String, MusicTasteArtist>()
        items.forEach { item ->
            val rawName = when (item.kind) {
                OnlineItemKind.ARTIST -> item.title.ifBlank { item.artist }
                else -> item.artist
            }
            // Split primary artist cleanly when multi-artist credits use separators
            val cleanName = rawName
                .substringBefore(" feat.")
                .substringBefore(" ft.")
                .substringBefore(" & ")
                .substringBefore(",")
                .replace(" - Topic", "", ignoreCase = true)
                .replace("VEVO", "", ignoreCase = true)
                .trim()

            if (!SonoraPersonalizedRecommendationEngine.isValidArtistName(cleanName)) return@forEach
            val key = MusicTasteArtist.normalizeArtistName(cleanName)
            if (key.isBlank()) return@forEach

            val candidate = MusicTasteArtist(
                id = if (item.kind == OnlineItemKind.ARTIST && item.contentUrl.isNotBlank()) {
                    item.contentUrl
                } else {
                    key
                },
                name = cleanName,
                imageUrl = item.artworkUrl?.takeIf { it.isNotBlank() },
                channelUrl = when {
                    item.kind == OnlineItemKind.ARTIST && item.contentUrl.isNotBlank() -> item.contentUrl
                    !item.artistUrl.isNullOrBlank() -> item.artistUrl
                    else -> null
                },
                providerKey = item.providerKey.ifBlank { provider.key },
                serviceId = item.serviceId
            )

            val existing = result[key]
            if (existing == null) {
                result[key] = candidate
            } else if (existing.imageUrl.isNullOrBlank() && !candidate.imageUrl.isNullOrBlank()) {
                result[key] = existing.copy(
                    imageUrl = candidate.imageUrl,
                    channelUrl = existing.channelUrl ?: candidate.channelUrl
                )
            }
        }
        return result.values.toList()
    }
}
