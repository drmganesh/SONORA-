package data.online

import android.util.Log
import android.util.LruCache
import domain.online.AudioQualityPreference
import domain.online.DiscoverMusicLanguage
import domain.online.DiscoverSectionState
import domain.online.DiscoverSectionType
import domain.online.OnlineAudioStreamOption
import domain.online.OnlineCollectionDetails
import domain.online.OnlineErrorKind
import domain.online.OnlineItemKind
import domain.online.OnlineMusicProvider
import domain.online.OnlineResult
import domain.online.OnlineSearchFilter
import domain.online.OnlineTrack
import domain.online.ResolvedOnlineStreamResult
import domain.online.StreamDeliveryType
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.StreamingService
import org.schabi.newpipe.extractor.channel.ChannelInfo
import org.schabi.newpipe.extractor.channel.tabs.ChannelTabInfo
import org.schabi.newpipe.extractor.kiosk.KioskExtractor
import org.schabi.newpipe.extractor.kiosk.KioskInfo
import org.schabi.newpipe.extractor.playlist.PlaylistInfo
import org.schabi.newpipe.extractor.stream.StreamInfo
import java.util.concurrent.TimeUnit

/**
 * Resolves fresh playable audio streams, collection tracklists (artists/albums/playlists),
 * and independent discovery feeds via NewPipeExtractor on IO dispatchers.
 * Maintains only a short-lived in-memory TTL cache for extracted streams (never persisted to disk).
 */
class OnlineStreamResolver(
    private val connectivityMonitor: NetworkConnectivityMonitor
) {

    private data class CachedStreamExtraction(
        val serviceId: Int,
        val providerKey: String,
        val providerName: String,
        val contentUrl: String,
        val title: String,
        val artist: String,
        val artistUrl: String?,
        val durationMs: Long,
        val artworkUrl: String?,
        val options: List<OnlineAudioStreamOption>,
        val isLive: Boolean,
        val viewCount: Long,
        val relatedTracks: List<OnlineTrack>,
        val extractedAtMs: Long
    )

    private val streamExtractionCache = LruCache<String, CachedStreamExtraction>(40)
    private val discoveryCache = LruCache<String, Pair<Long, List<OnlineTrack>>>(12)
    private val discoverSectionCache = LruCache<String, Pair<Long, List<OnlineTrack>>>(32)

    companion object {
        private const val TAG = "OnlineStreamResolver"
        private val STREAM_CACHE_TTL_MS = TimeUnit.MINUTES.toMillis(20)
        private val DISCOVERY_CACHE_TTL_MS = TimeUnit.MINUTES.toMillis(15)

        private val EXCLUDED_YOUTUBE_NON_MUSIC_KIOSKS = setOf(
            "trending_gaming",
            "trending_podcasts_episodes",
            "trending_movies_and_shows",
            "live"
        )

        /**
         * Ranks available NewPipe kiosk IDs deterministically so HashMap iteration order
         * never promotes gaming/podcast/movie desktop kiosks ahead of music charts.
         */
        fun rankPreferredKioskIds(availableKiosks: Collection<String>, providerKey: String): List<String> {
            if (availableKiosks.isEmpty()) return emptyList()
            val clean = availableKiosks.filter { it.isNotBlank() }.distinct()
            val isYouTube = providerKey == OnlineMusicProvider.KEY_YOUTUBE_MUSIC ||
                providerKey == OnlineMusicProvider.KEY_YOUTUBE

            val candidates = if (isYouTube) {
                clean.filterNot { id ->
                    EXCLUDED_YOUTUBE_NON_MUSIC_KIOSKS.any { it.equals(id, ignoreCase = true) }
                }
            } else {
                clean
            }

            val pool = candidates.ifEmpty { clean }
            val ranked = mutableListOf<String>()

            // Tier 1: Explicit trending_music or any music kiosk
            pool.firstOrNull { it.equals("trending_music", ignoreCase = true) }?.let { ranked.add(it) }
            pool.filter { it.contains("music", ignoreCase = true) }
                .sorted()
                .forEach { if (it !in ranked) ranked.add(it) }

            // Tier 2: Top / Featured / Trending kiosks
            pool.filter {
                it.equals("Trending", ignoreCase = true) ||
                    it.contains("top", ignoreCase = true) ||
                    it.contains("chart", ignoreCase = true) ||
                    it.contains("trending", ignoreCase = true)
            }.sorted()
                .forEach { if (it !in ranked) ranked.add(it) }

            // Tier 3: Remaining provider kiosks (for SoundCloud / PeerTube / MediaCCC)
            if (!isYouTube) {
                pool.sorted().forEach { if (it !in ranked) ranked.add(it) }
            }

            return ranked
        }

        /**
         * Safe wrapper around NewPipe's KioskExtractor + KioskInfo:
         * ALWAYS invokes [KioskExtractor.fetchPage] before [KioskInfo.getInfo] so extractors backed
         * by YoutubeDesktopBaseKioskExtractor initialize `responseData` (`ChannelResponseData`)
         * before `getName()` or `getInitialPage()` dereferences `responseData.jsonResponse`.
         */
        fun fetchKioskInfoSafely(extractor: KioskExtractor<*>): KioskInfo {
            extractor.fetchPage()
            return KioskInfo.getInfo(extractor)
        }
    }

    fun invalidateStreamCache(contentUrl: String) {
        streamExtractionCache.remove(contentUrl)
    }

    fun clearDiscoverCache() {
        discoveryCache.evictAll()
        discoverSectionCache.evictAll()
    }

    fun selectBestAudioStream(
        options: List<OnlineAudioStreamOption>,
        preference: AudioQualityPreference,
        isMeteredConnection: Boolean,
        manualStreamId: String? = null
    ): OnlineAudioStreamOption? {
        if (options.isEmpty()) return null

        if (!manualStreamId.isNullOrBlank()) {
            options.firstOrNull { it.id == manualStreamId }?.let { return it }
        }

        val audioOnly = options.filter { it.isAudioOnly }
        val pool = if (audioOnly.isNotEmpty()) audioOnly else options

        val progressivePreferred = pool.filter {
            it.deliveryType == StreamDeliveryType.PROGRESSIVE_HTTP ||
                it.deliveryType == StreamDeliveryType.DASH ||
                it.deliveryType == StreamDeliveryType.HLS
        }.ifEmpty { pool }

        return when (preference) {
            AudioQualityPreference.HIGHEST -> {
                progressivePreferred.maxWithOrNull(
                    compareBy<OnlineAudioStreamOption> { it.isAudioOnly }
                        .thenBy { it.averageBitrateKbps }
                        .thenBy { it.deliveryType == StreamDeliveryType.PROGRESSIVE_HTTP }
                        .thenBy { it.codec.contains("opus", ignoreCase = true) }
                )
            }

            AudioQualityPreference.DATA_SAVER -> {
                val withBitrate = progressivePreferred.filter { it.averageBitrateKbps > 0 }
                if (withBitrate.isNotEmpty()) {
                    withBitrate.filter { it.averageBitrateKbps >= 48 }
                        .minByOrNull { it.averageBitrateKbps }
                        ?: withBitrate.minByOrNull { it.averageBitrateKbps }
                } else {
                    progressivePreferred.lastOrNull()
                }
            }

            AudioQualityPreference.AUTO -> {
                val withBitrate = progressivePreferred.filter { it.averageBitrateKbps > 0 }
                if (withBitrate.isEmpty()) {
                    progressivePreferred.firstOrNull()
                } else if (isMeteredConnection) {
                    withBitrate.minByOrNull { kotlin.math.abs(it.averageBitrateKbps - 128) }
                } else {
                    withBitrate.maxWithOrNull(
                        compareBy<OnlineAudioStreamOption> { it.deliveryType == StreamDeliveryType.PROGRESSIVE_HTTP }
                            .thenBy { it.averageBitrateKbps }
                            .thenBy { it.codec.contains("opus", ignoreCase = true) }
                    )
                }
            }
        }
    }

    suspend fun resolveStream(
        serviceId: Int,
        providerKey: String,
        providerName: String,
        contentUrl: String,
        qualityPreference: AudioQualityPreference,
        manualStreamId: String? = null,
        forceRefresh: Boolean = false
    ): OnlineResult<ResolvedOnlineStreamResult> = withContext(Dispatchers.IO) {
        if (contentUrl.isBlank()) {
            return@withContext OnlineResult.Error(
                kind = OnlineErrorKind.UNSUPPORTED,
                message = "Track URL is empty.",
                isRetryable = false
            )
        }

        val now = System.currentTimeMillis()
        val cached = if (!forceRefresh) streamExtractionCache.get(contentUrl) else null
        if (cached != null && (now - cached.extractedAtMs) < STREAM_CACHE_TTL_MS) {
            val picked = selectBestAudioStream(
                options = cached.options,
                preference = qualityPreference,
                isMeteredConnection = connectivityMonitor.isCurrentlyMetered(),
                manualStreamId = manualStreamId
            )
            if (picked != null) {
                return@withContext OnlineResult.Success(
                    ResolvedOnlineStreamResult(
                        serviceId = cached.serviceId,
                        providerKey = cached.providerKey,
                        providerName = cached.providerName,
                        contentUrl = cached.contentUrl,
                        title = cached.title,
                        artist = cached.artist,
                        artistUrl = cached.artistUrl,
                        durationMs = cached.durationMs,
                        artworkUrl = cached.artworkUrl,
                        selectedStream = picked,
                        availableStreams = cached.options,
                        isLive = cached.isLive,
                        viewCount = cached.viewCount,
                        relatedTracks = cached.relatedTracks,
                        extractedAtMs = cached.extractedAtMs
                    )
                )
            }
        }

        if (!connectivityMonitor.isCurrentlyOnline()) {
            return@withContext OnlineResult.Error(
                kind = OnlineErrorKind.OFFLINE,
                message = "You are offline. Connect to the internet to stream from $providerName.",
                isRetryable = true
            )
        }

        try {
            OnlineSearchDataSource.runWithTransientRetry(
                providerName = providerName,
                operation = "resolve_stream"
            ) {
                NewPipeInitializer.ensureInitialized(null)
                val service = NewPipeInitializer.getServiceById(serviceId)
                    ?: NewPipeInitializer.getServiceForProviderKey(providerKey)
                val streamInfo = StreamInfo.getInfo(service, contentUrl)

                val audioOptions = streamInfo.audioStreams
                    .orEmpty()
                    .mapIndexedNotNull { idx, stream -> OnlineTrackMapper.mapAudioStream(stream, idx) }
                    .sortedByDescending { it.averageBitrateKbps }

                val effectiveOptions = if (audioOptions.isNotEmpty()) {
                    audioOptions
                } else {
                    streamInfo.videoStreams
                        .orEmpty()
                        .mapIndexedNotNull { idx, stream -> OnlineTrackMapper.mapFallbackVideoStream(stream, idx) }
                }

                if (effectiveOptions.isEmpty()) {
                    return@runWithTransientRetry OnlineResult.Error(
                        kind = OnlineErrorKind.NO_PLAYABLE_AUDIO_STREAM,
                        message = "No playable audio stream was exposed for this track on $providerName.",
                        isRetryable = false
                    )
                }

                val selected = selectBestAudioStream(
                    options = effectiveOptions,
                    preference = qualityPreference,
                    isMeteredConnection = connectivityMonitor.isCurrentlyMetered(),
                    manualStreamId = manualStreamId
                ) ?: effectiveOptions.first()

                val artworkUrl = OnlineTrackMapper.selectBestArtworkUrl(streamInfo.thumbnails)
                val durationMs = if (streamInfo.duration > 0L) streamInfo.duration * 1000L else 0L
                val related = OnlineTrackMapper.streamInfoRelatedTracks(
                    streamInfo = streamInfo,
                    serviceId = service.serviceId,
                    providerKey = providerKey,
                    providerName = providerName
                )

                val extraction = CachedStreamExtraction(
                    serviceId = service.serviceId,
                    providerKey = providerKey,
                    providerName = providerName,
                    contentUrl = contentUrl,
                    title = streamInfo.name.orEmpty().ifBlank { "Untitled Track" },
                    artist = streamInfo.uploaderName.orEmpty().ifBlank { providerName },
                    artistUrl = streamInfo.uploaderUrl,
                    durationMs = durationMs,
                    artworkUrl = artworkUrl,
                    options = effectiveOptions,
                    isLive = streamInfo.streamType == org.schabi.newpipe.extractor.stream.StreamType.LIVE_STREAM ||
                        streamInfo.streamType == org.schabi.newpipe.extractor.stream.StreamType.AUDIO_LIVE_STREAM,
                    viewCount = streamInfo.viewCount,
                    relatedTracks = related,
                    extractedAtMs = System.currentTimeMillis()
                )
                streamExtractionCache.put(contentUrl, extraction)

                OnlineResult.Success(
                    ResolvedOnlineStreamResult(
                        serviceId = extraction.serviceId,
                        providerKey = extraction.providerKey,
                        providerName = extraction.providerName,
                        contentUrl = extraction.contentUrl,
                        title = extraction.title,
                        artist = extraction.artist,
                        artistUrl = extraction.artistUrl,
                        durationMs = extraction.durationMs,
                        artworkUrl = extraction.artworkUrl,
                        selectedStream = selected,
                        availableStreams = effectiveOptions,
                        isLive = extraction.isLive,
                        viewCount = extraction.viewCount,
                        relatedTracks = related,
                        extractedAtMs = extraction.extractedAtMs
                    )
                )
            }
        } catch (ce: CancellationException) {
            throw ce
        } catch (t: Throwable) {
            OnlineSearchDataSource.mapThrowableToError(
                t = t,
                providerName = providerName,
                operation = "resolve_stream",
                requestType = providerKey
            )
        }
    }

    suspend fun fetchDiscoveryFeed(
        provider: OnlineMusicProvider,
        forceRefresh: Boolean = false
    ): OnlineResult<List<OnlineTrack>> = withContext(Dispatchers.IO) {
        val cacheKey = provider.key
        val now = System.currentTimeMillis()
        if (!forceRefresh) {
            val cached = discoveryCache.get(cacheKey)
            if (cached != null && (now - cached.first) < DISCOVERY_CACHE_TTL_MS) {
                return@withContext OnlineResult.Success(cached.second)
            }
        }

        if (!connectivityMonitor.isCurrentlyOnline()) {
            return@withContext OnlineResult.Error(
                kind = OnlineErrorKind.OFFLINE,
                message = "Connect to the internet to load ${provider.displayName} discovery feeds.",
                isRetryable = true
            )
        }

        try {
            val items = extractKioskTracksWithCandidates(provider)
            if (items.isNotEmpty()) {
                discoveryCache.put(cacheKey, now to items)
            }
            OnlineResult.Success(items)
        } catch (ce: CancellationException) {
            throw ce
        } catch (t: Throwable) {
            OnlineSearchDataSource.mapThrowableToError(
                t = t,
                providerName = provider.displayName,
                operation = "discovery_kiosk",
                requestType = provider.key
            )
        }
    }

    suspend fun fetchDiscoverSection(
        sectionType: DiscoverSectionType,
        provider: OnlineMusicProvider,
        searchDataSource: OnlineSearchDataSource,
        countryCode: String = NewPipeInitializer.currentCountryCode(),
        languageCode: String = NewPipeInitializer.currentLanguageCode(),
        discoverLanguage: DiscoverMusicLanguage = DiscoverMusicLanguage.ALL,
        recentArtistSeed: String? = null,
        forceRefresh: Boolean = false
    ): DiscoverSectionState = withContext(Dispatchers.IO) {
        val cleanCountry = countryCode.uppercase()
        val cleanLang = languageCode.lowercase()
        val cacheKey = "${provider.key}|$cleanCountry|$cleanLang|${discoverLanguage.id}|${sectionType.name}|${recentArtistSeed.orEmpty()}"
        val now = System.currentTimeMillis()
        if (!forceRefresh) {
            val cached = discoverSectionCache.get(cacheKey)
            if (cached != null && (now - cached.first) < DISCOVERY_CACHE_TTL_MS) {
                return@withContext if (cached.second.isEmpty()) {
                    DiscoverSectionState.Empty("No items available right now on ${provider.displayName}.")
                } else {
                    DiscoverSectionState.Success(cached.second, provider.displayName)
                }
            }
        }

        if (!connectivityMonitor.isCurrentlyOnline()) {
            return@withContext DiscoverSectionState.Error(
                kind = OnlineErrorKind.OFFLINE,
                message = "Connect to the internet to load ${sectionType.title}.",
                isRetryable = true
            )
        }

        val querySpec = buildDiscoverSearchQuery(
            sectionType = sectionType,
            countryCode = cleanCountry,
            languageCode = cleanLang,
            discoverLanguage = discoverLanguage,
            recentArtistSeed = recentArtistSeed
        )

        return@withContext try {
            when (sectionType) {
                DiscoverSectionType.TRENDING_MUSIC -> {
                    val useKioskFirst = discoverLanguage == DiscoverMusicLanguage.ALL && provider.supportsDiscoveryFeed
                    val kioskItems = if (useKioskFirst) {
                        runCatching {
                            extractKioskTracksWithCandidates(provider)
                        }.onFailure { err ->
                            if (err is CancellationException) throw err
                            Log.w(
                                TAG,
                                "Primary kiosk extraction failed for service=${provider.displayName} country=$cleanCountry, falling back to search: ${err::class.java.simpleName}: ${err.message}"
                            )
                        }.getOrNull()
                    } else {
                        null
                    }

                    if (!kioskItems.isNullOrEmpty()) {
                        discoverSectionCache.put(cacheKey, now to kioskItems)
                        DiscoverSectionState.Success(kioskItems, "${provider.displayName} Charts ($cleanCountry)")
                    } else {
                        val fallbackFilter = if (OnlineSearchFilter.SONGS in provider.supportedFilters) {
                            OnlineSearchFilter.SONGS
                        } else {
                            provider.defaultFilter
                        }
                        searchBackedSectionWithFallback(
                            sectionType = sectionType,
                            primaryQuery = querySpec.primaryQuery,
                            broaderFallbackQuery = querySpec.broaderFallbackQuery,
                            preferredFilters = listOf(fallbackFilter, OnlineSearchFilter.ALL),
                            provider = provider,
                            searchDataSource = searchDataSource,
                            cacheKey = cacheKey,
                            sourceSuffix = if (discoverLanguage != DiscoverMusicLanguage.ALL) {
                                "${provider.displayName} • ${discoverLanguage.label}"
                            } else {
                                "${provider.displayName} ($cleanCountry)"
                            }
                        )
                    }
                }

                DiscoverSectionType.POPULAR_SONGS -> {
                    searchBackedSectionWithFallback(
                        sectionType = sectionType,
                        primaryQuery = querySpec.primaryQuery,
                        broaderFallbackQuery = querySpec.broaderFallbackQuery,
                        preferredFilters = listOf(OnlineSearchFilter.SONGS, OnlineSearchFilter.ALL),
                        provider = provider,
                        searchDataSource = searchDataSource,
                        cacheKey = cacheKey,
                        sourceSuffix = formatSourceLabel(provider.displayName, cleanCountry, discoverLanguage)
                    )
                }

                DiscoverSectionType.NEW_RELEASES -> {
                    searchBackedSectionWithFallback(
                        sectionType = sectionType,
                        primaryQuery = querySpec.primaryQuery,
                        broaderFallbackQuery = querySpec.broaderFallbackQuery,
                        preferredFilters = listOf(OnlineSearchFilter.ALBUMS, OnlineSearchFilter.SONGS, OnlineSearchFilter.ALL),
                        provider = provider,
                        searchDataSource = searchDataSource,
                        cacheKey = cacheKey,
                        sourceSuffix = formatSourceLabel(provider.displayName, cleanCountry, discoverLanguage)
                    )
                }

                DiscoverSectionType.MUSIC_RECOMMENDATIONS -> {
                    searchBackedSectionWithFallback(
                        sectionType = sectionType,
                        primaryQuery = querySpec.primaryQuery,
                        broaderFallbackQuery = querySpec.broaderFallbackQuery,
                        preferredFilters = listOf(OnlineSearchFilter.PLAYLISTS, OnlineSearchFilter.SONGS, OnlineSearchFilter.ALL),
                        provider = provider,
                        searchDataSource = searchDataSource,
                        cacheKey = cacheKey,
                        sourceSuffix = formatSourceLabel(provider.displayName, cleanCountry, discoverLanguage)
                    )
                }

                DiscoverSectionType.POPULAR_ARTISTS -> {
                    searchBackedSectionWithFallback(
                        sectionType = sectionType,
                        primaryQuery = querySpec.primaryQuery,
                        broaderFallbackQuery = querySpec.broaderFallbackQuery,
                        preferredFilters = listOf(OnlineSearchFilter.ARTISTS, OnlineSearchFilter.ALL),
                        provider = provider,
                        searchDataSource = searchDataSource,
                        cacheKey = cacheKey,
                        sourceSuffix = formatSourceLabel(provider.displayName, cleanCountry, discoverLanguage)
                    )
                }
            }
        } catch (ce: CancellationException) {
            throw ce
        } catch (t: Throwable) {
            val mapped = OnlineSearchDataSource.mapThrowableToError(
                t = t,
                providerName = provider.displayName,
                operation = "discover_${sectionType.name.lowercase()}",
                requestType = provider.key
            )
            DiscoverSectionState.Error(
                kind = mapped.kind,
                message = mapped.message,
                isRetryable = mapped.isRetryable
            )
        }
    }

    private data class DiscoverQuerySpec(
        val primaryQuery: String,
        val broaderFallbackQuery: String?
    )

    private fun formatSourceLabel(
        providerName: String,
        countryCode: String,
        discoverLanguage: DiscoverMusicLanguage
    ): String {
        return if (discoverLanguage != DiscoverMusicLanguage.ALL) {
            "$providerName • ${discoverLanguage.label}"
        } else {
            "$providerName ($countryCode)"
        }
    }

    private fun buildDiscoverSearchQuery(
        sectionType: DiscoverSectionType,
        countryCode: String,
        languageCode: String,
        discoverLanguage: DiscoverMusicLanguage,
        recentArtistSeed: String?
    ): DiscoverQuerySpec {
        val cleanSeed = recentArtistSeed?.trim()?.takeIf { it.length >= 2 }

        // Map preferred UI/content language code to optional music context when language != "en"
        val languageHint = when {
            discoverLanguage != DiscoverMusicLanguage.ALL -> discoverLanguage.searchKeyword
            languageCode == "hi" -> "Hindi Bollywood"
            languageCode == "or" -> "Odia"
            languageCode == "ta" -> "Tamil"
            languageCode == "te" -> "Telugu"
            languageCode == "ml" -> "Malayalam"
            languageCode == "kn" -> "Kannada"
            languageCode == "bn" -> "Bengali"
            languageCode == "pa" -> "Punjabi"
            languageCode == "mr" -> "Marathi"
            countryCode == "IN" -> "Indian Hindi Bollywood"
            else -> ""
        }

        val prefix = languageHint.trim()
        val hasLocaleFocus = prefix.isNotBlank()

        return when (sectionType) {
            DiscoverSectionType.TRENDING_MUSIC -> DiscoverQuerySpec(
                primaryQuery = if (hasLocaleFocus) "trending $prefix music hits" else "trending music hits",
                broaderFallbackQuery = if (hasLocaleFocus) "trending music hits" else null
            )
            DiscoverSectionType.POPULAR_SONGS -> DiscoverQuerySpec(
                primaryQuery = if (hasLocaleFocus) "popular $prefix songs" else "popular music songs",
                broaderFallbackQuery = if (hasLocaleFocus) "popular music songs" else null
            )
            DiscoverSectionType.NEW_RELEASES -> DiscoverQuerySpec(
                primaryQuery = if (hasLocaleFocus) "new $prefix music releases" else "new music releases",
                broaderFallbackQuery = if (hasLocaleFocus) "new music releases" else null
            )
            DiscoverSectionType.MUSIC_RECOMMENDATIONS -> {
                val primary = when {
                    discoverLanguage != DiscoverMusicLanguage.ALL -> "$prefix chill music playlist mix"
                    cleanSeed != null -> "$cleanSeed mix"
                    hasLocaleFocus -> "$prefix indie Bollywood chill playlist"
                    else -> "chill music mix playlist"
                }
                DiscoverQuerySpec(
                    primaryQuery = primary,
                    broaderFallbackQuery = if (hasLocaleFocus || cleanSeed != null) "chill music mix playlist" else null
                )
            }
            DiscoverSectionType.POPULAR_ARTISTS -> DiscoverQuerySpec(
                primaryQuery = if (hasLocaleFocus) "popular $prefix music artists singers" else "popular music artists",
                broaderFallbackQuery = if (hasLocaleFocus) "popular music artists" else null
            )
        }
    }

    private suspend fun searchBackedSectionWithFallback(
        sectionType: DiscoverSectionType,
        primaryQuery: String,
        broaderFallbackQuery: String?,
        preferredFilters: List<OnlineSearchFilter>,
        provider: OnlineMusicProvider,
        searchDataSource: OnlineSearchDataSource,
        cacheKey: String,
        sourceSuffix: String
    ): DiscoverSectionState {
        val chosenFilter = preferredFilters.firstOrNull { it in provider.supportedFilters }
            ?: provider.defaultFilter
        return when (val res = searchDataSource.search(primaryQuery, provider, chosenFilter)) {
            is OnlineResult.Success -> {
                val items = res.value.items.take(20)
                if (items.isNotEmpty()) {
                    discoverSectionCache.put(cacheKey, System.currentTimeMillis() to items)
                    DiscoverSectionState.Success(items, sourceSuffix)
                } else if (!broaderFallbackQuery.isNullOrBlank() && !broaderFallbackQuery.equals(primaryQuery, ignoreCase = true)) {
                    // Graceful fallback when a provider (e.g., Bandcamp/PeerTube) has no results for a specific language/region query
                    when (val fallbackRes = searchDataSource.search(broaderFallbackQuery, provider, chosenFilter)) {
                        is OnlineResult.Success -> {
                            val fallbackItems = fallbackRes.value.items.take(20)
                            if (fallbackItems.isEmpty()) {
                                DiscoverSectionState.Empty("No ${sectionType.title.lowercase()} available for this filter on ${provider.displayName}.")
                            } else {
                                discoverSectionCache.put(cacheKey, System.currentTimeMillis() to fallbackItems)
                                DiscoverSectionState.Success(fallbackItems, "${provider.displayName} Global Fallback")
                            }
                        }
                        is OnlineResult.Error -> {
                            DiscoverSectionState.Empty("No ${sectionType.title.lowercase()} available for this filter on ${provider.displayName}.")
                        }
                    }
                } else {
                    DiscoverSectionState.Empty("No ${sectionType.title.lowercase()} found on ${provider.displayName}.")
                }
            }
            is OnlineResult.Error -> {
                DiscoverSectionState.Error(
                    kind = res.kind,
                    message = res.message,
                    isRetryable = res.isRetryable
                )
            }
        }
    }

    private suspend fun extractKioskTracksWithCandidates(provider: OnlineMusicProvider): List<OnlineTrack> {
        NewPipeInitializer.ensureInitialized(null)
        val service: StreamingService = NewPipeInitializer.getServiceForProviderKey(provider.key)
        val kioskList = service.kioskList ?: return emptyList()
        val available = kioskList.availableKiosks?.toList().orEmpty()
        if (available.isEmpty()) return emptyList()

        val rankedKiosks = rankPreferredKioskIds(available, provider.key)
        var lastError: Throwable? = null

        for (kioskId in rankedKiosks) {
            try {
                val tracks = OnlineSearchDataSource.runWithTransientRetry(
                    providerName = provider.displayName,
                    operation = "kiosk_$kioskId",
                    requestInfo = provider.key
                ) {
                    val extractor = kioskList.getExtractorById(kioskId, null)
                    val kioskInfo = fetchKioskInfoSafely(extractor)
                    kioskInfo.relatedItems
                        .orEmpty()
                        .mapNotNull { item ->
                            OnlineTrackMapper.mapInfoItem(
                                item = item,
                                serviceId = provider.serviceId,
                                providerKey = provider.key,
                                providerName = provider.displayName
                            )
                        }
                        .filter { it.kind == OnlineItemKind.TRACK }
                }
                if (tracks.isNotEmpty()) {
                    return tracks
                }
            } catch (ce: CancellationException) {
                throw ce
            } catch (t: Throwable) {
                lastError = t
                Log.w(
                    TAG,
                    "Kiosk candidate failed service=${provider.displayName} kiosk=$kioskId exc=${t::class.java.simpleName}: ${t.message}"
                )
            }
        }

        if (lastError != null) {
            throw lastError
        }
        return emptyList()
    }

    suspend fun fetchCollectionDetails(
        item: OnlineTrack
    ): OnlineResult<OnlineCollectionDetails> = withContext(Dispatchers.IO) {
        if (item.contentUrl.isBlank() ||
            (!item.contentUrl.startsWith("http://", ignoreCase = true) &&
                !item.contentUrl.startsWith("https://", ignoreCase = true))
        ) {
            return@withContext OnlineResult.Error(
                kind = OnlineErrorKind.UNSUPPORTED,
                message = "Invalid or missing ${item.providerName} URL for ${item.title}.",
                isRetryable = false
            )
        }

        if (!connectivityMonitor.isCurrentlyOnline()) {
            return@withContext OnlineResult.Error(
                kind = OnlineErrorKind.OFFLINE,
                message = "Connect to the internet to open ${item.title}.",
                isRetryable = true
            )
        }

        try {
            NewPipeInitializer.ensureInitialized(null)
            val service = NewPipeInitializer.getServiceById(item.serviceId)
                ?: NewPipeInitializer.getServiceForProviderKey(item.providerKey)

            when (item.kind) {
                OnlineItemKind.ARTIST -> {
                    OnlineSearchDataSource.runWithTransientRetry(
                        providerName = item.providerName,
                        operation = "channel_info",
                        requestInfo = item.providerKey
                    ) {
                        val channelInfo = ChannelInfo.getInfo(service, item.contentUrl)
                        val candidateTabs = channelInfo.tabs.orEmpty().take(3)
                        var extractedTabTracks: List<OnlineTrack> = emptyList()

                        for (tab in candidateTabs) {
                            val tabResult = runCatching {
                                ChannelTabInfo.getInfo(service, tab)
                                    .relatedItems
                                    .orEmpty()
                                    .mapNotNull {
                                        OnlineTrackMapper.mapInfoItem(
                                            item = it,
                                            serviceId = item.serviceId,
                                            providerKey = item.providerKey,
                                            providerName = item.providerName
                                        )
                                    }
                            }.onFailure { tabErr ->
                                if (tabErr is CancellationException) throw tabErr
                                Log.w(
                                    TAG,
                                    "Channel tab extraction failed service=${item.providerName} tab=${tab.id} exc=${tabErr::class.java.simpleName}"
                                )
                            }.getOrNull()

                            if (!tabResult.isNullOrEmpty()) {
                                extractedTabTracks = tabResult
                                break
                            }
                        }

                        val avatar = OnlineTrackMapper.selectBestArtworkUrl(channelInfo.avatars)
                            ?: item.artworkUrl
                        val subLabel = if (channelInfo.subscriberCount > 0L) {
                            "${item.providerName} • ${formatCompactCount(channelInfo.subscriberCount)} subscribers"
                        } else {
                            item.providerName
                        }
                        OnlineResult.Success(
                            OnlineCollectionDetails(
                                title = channelInfo.name.orEmpty().ifBlank { item.title },
                                subtitle = subLabel,
                                providerName = item.providerName,
                                contentUrl = item.contentUrl,
                                artworkUrl = avatar,
                                tracks = extractedTabTracks
                            )
                        )
                    }
                }

                OnlineItemKind.ALBUM, OnlineItemKind.PLAYLIST -> {
                    OnlineSearchDataSource.runWithTransientRetry(
                        providerName = item.providerName,
                        operation = "playlist_info",
                        requestInfo = item.providerKey
                    ) {
                        val playlistInfo = PlaylistInfo.getInfo(service, item.contentUrl)
                        val tracks = playlistInfo.relatedItems
                            .orEmpty()
                            .mapNotNull {
                                OnlineTrackMapper.mapInfoItem(
                                    item = it,
                                    serviceId = item.serviceId,
                                    providerKey = item.providerKey,
                                    providerName = item.providerName
                                )
                            }
                        val art = OnlineTrackMapper.selectBestArtworkUrl(playlistInfo.thumbnails)
                            ?: item.artworkUrl
                        val subtitle = playlistInfo.uploaderName?.takeIf { it.isNotBlank() }
                            ?: item.artist.ifBlank { item.providerName }
                        OnlineResult.Success(
                            OnlineCollectionDetails(
                                title = playlistInfo.name.orEmpty().ifBlank { item.title },
                                subtitle = "$subtitle • ${tracks.size} items",
                                providerName = item.providerName,
                                contentUrl = item.contentUrl,
                                artworkUrl = art,
                                tracks = tracks
                            )
                        )
                    }
                }

                OnlineItemKind.TRACK -> {
                    OnlineResult.Success(
                        OnlineCollectionDetails(
                            title = item.title,
                            subtitle = item.artist,
                            providerName = item.providerName,
                            contentUrl = item.contentUrl,
                            artworkUrl = item.artworkUrl,
                            tracks = listOf(item)
                        )
                    )
                }
            }
        } catch (ce: CancellationException) {
            throw ce
        } catch (t: Throwable) {
            OnlineSearchDataSource.mapThrowableToError(
                t = t,
                providerName = item.providerName,
                operation = "collection_${item.kind.name.lowercase()}",
                requestType = item.providerKey
            )
        }
    }

    private fun formatCompactCount(count: Long): String {
        return when {
            count >= 1_000_000L -> String.format("%.1fM", count / 1_000_000.0)
            count >= 1_000L -> String.format("%.1fK", count / 1_000.0)
            else -> count.toString()
        }
    }
}
