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
import domain.online.OnlineRecommendationEngine
import domain.online.OnlineRecommendationProfile
import domain.online.OnlineResult
import domain.online.OnlineSearchFilter
import domain.online.OnlineTrack
import domain.online.ResolvedOnlineStreamResult
import domain.online.StreamDeliveryType
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.Page
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.StreamingService
import org.schabi.newpipe.extractor.channel.ChannelInfo
import org.schabi.newpipe.extractor.channel.tabs.ChannelTabInfo
import org.schabi.newpipe.extractor.exceptions.ExtractionException
import org.schabi.newpipe.extractor.exceptions.SignInConfirmNotBotException
import org.schabi.newpipe.extractor.kiosk.KioskExtractor
import org.schabi.newpipe.extractor.kiosk.KioskInfo
import org.schabi.newpipe.extractor.localization.ContentCountry
import org.schabi.newpipe.extractor.localization.Localization
import org.schabi.newpipe.extractor.playlist.PlaylistInfo
import org.schabi.newpipe.extractor.search.SearchInfo
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import java.text.Normalizer
import java.util.Locale
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import kotlin.coroutines.coroutineContext
import kotlin.math.abs

/**
 * Resolves fresh playable audio streams, collection tracklists (artists/albums/playlists),
 * and independent discovery feeds via NewPipeExtractor on IO dispatchers.
 * Maintains only a short-lived in-memory TTL cache for extracted streams (never persisted to disk).
 */
class OnlineStreamResolver(
    private val connectivityMonitor: NetworkConnectivityMonitor,
    private val youtubeFallbackClient: YoutubeFallbackPlayerClient = YoutubeFallbackPlayerClient()
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

    private data class CachedDiscoverSection(
        val fetchedAtMs: Long,
        val items: List<OnlineTrack>,
        val sourceLabel: String,
        val nextPageToken: String?
    )

    private data class KioskContinuationState(
        val providerKey: String,
        val providerName: String,
        val serviceId: Int,
        val kioskUrl: String,
        val nextPage: Page
    )

    private data class KioskExtractionResult(
        val items: List<OnlineTrack>,
        val nextPageToken: String?
    )

    private val streamExtractionCache = LruCache<String, CachedStreamExtraction>(40)
    private val inFlightStreamResolutions =
        ConcurrentHashMap<String, CompletableDeferred<OnlineResult<ResolvedOnlineStreamResult>>>()
    private val discoveryCache = LruCache<String, Pair<Long, List<OnlineTrack>>>(12)
    private val discoverSectionCache = LruCache<String, CachedDiscoverSection>(36)
    private val kioskContinuationCache = LruCache<String, KioskContinuationState>(20)

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

        private val TITLE_NOISE_REGEX = Regex(
            "\\s*[(\\[](?:official|audio|video|lyric|lyrical|lyrics|mv|hd|4k|8k|remaster|visualizer|explicit|full(?:\\s+(?:song|video|audio))?|from\\s+[^)\\]]*|feat\\.?[^)\\]]*|ft\\.?[^)\\]]*|with\\s+lyrics|teaser|promo|jukebox|theme|ost|original\\s+motion\\s+picture).*?[)\\]]",
            RegexOption.IGNORE_CASE
        )
        private val TRAILING_NOISE_REGEX = Regex(
            "\\s*(?:-|–|—)\\s*(?:official\\s*(?:music\\s*)?(?:video|audio)|lyrical(?:\\s+video)?|full\\s*(?:song|video|audio)|music\\s*video|lyric\\s*video)\\s*$",
            RegexOption.IGNORE_CASE
        )
        private val FEATURED_ARTIST_IN_TITLE_REGEX = Regex(
            "[(\\[]\\s*(?:feat\\.?|ft\\.?)\\s+([^)\\]]+)[)\\]]",
            RegexOption.IGNORE_CASE
        )
        private val NON_ALNUM_UNICODE_REGEX = Regex("[^\\p{L}\\p{M}\\p{N}\\s]+")
        private val VARIANT_MODIFIERS = setOf(
            "remix", "slowed", "reverb", "nightcore", "8d", "karaoke",
            "instrumental", "cover", "sped", "mashup", "lofi"
        )
        private val MATCH_STOP_WORDS = setOf(
            "from", "the", "and", "feat", "ft", "song", "full", "video",
            "audio", "official", "lyric", "lyrics", "lyrical", "music", "hd", "4k"
        )
        private val GENERIC_LABEL_NAMES = setOf(
            "yt music", "youtube", "unknown", "<unknown>", "online",
            "t series", "tseries", "zee music company", "zee music",
            "sony music india", "sony music", "sony music south",
            "saregama music", "saregama", "yrf", "yash raj films",
            "tips official", "tips music", "speed records", "times music",
            "venus", "shemaroo", "shemaroo filmi gaane", "desimusicfactory",
            "desi music factory", "white hill music", "geet mp3",
            "jatt records", "aditya music", "lahari music", "think music india",
            "junglee music", "ishtar music", "eros now", "eros now music",
            "t series apna punjab", "t series bhakti sagar"
        )

        internal fun cleanTitleForCrossSearch(raw: String): String {
            val withoutParens = TITLE_NOISE_REGEX.replace(raw, " ")
            val primarySegment = withoutParens.substringBefore('|').trim().ifBlank { withoutParens.trim() }
            return TRAILING_NOISE_REGEX.replace(primarySegment, "")
                .replace(Regex("\\s+"), " ")
                .trim()
        }

        internal fun extractEffectiveArtistHint(rawTitle: String, rawArtist: String): String {
            val cleanedUploader = rawArtist
                .replace(Regex("\\s*-\\s*Topic$", RegexOption.IGNORE_CASE), "")
                .replace(Regex("\\s+"), " ")
                .trim()
            val normalizedUploader = normalizeText(cleanedUploader)
            if (cleanedUploader.isNotBlank() && normalizedUploader !in GENERIC_LABEL_NAMES) {
                return cleanedUploader
            }

            // Fall back to explicit feat./ft. credits inside title when uploader is a record label
            // (Avoid pipe segments because YouTube India titles commonly list actors/directors after '|')
            val featMatch = FEATURED_ARTIST_IN_TITLE_REGEX.find(rawTitle)?.groupValues?.getOrNull(1)?.trim()
            if (!featMatch.isNullOrBlank()) {
                return featMatch.substringBefore(',').trim()
            }
            return ""
        }

        internal fun buildSoundCloudFallbackQueries(trackTitle: String, trackArtist: String): List<String> {
            val coreDisplay = cleanTitleForCrossSearch(trackTitle)
            val normCore = normalizeText(coreDisplay)
            val artistHint = extractEffectiveArtistHint(trackTitle, trackArtist)
            val normArtist = normalizeText(artistHint)

            val queries = linkedSetOf<String>()
            if (normCore.isNotBlank() && normArtist.isNotBlank()) {
                val artistWords = normArtist.split(' ').filter { it.isNotBlank() }.take(3).joinToString(" ")
                if (artistWords.isNotBlank() && !normCore.contains(artistWords)) {
                    queries.add("$coreDisplay $artistWords".trim())
                }
            }
            if (coreDisplay.isNotBlank()) {
                queries.add(coreDisplay)
            }
            val beforeDash = coreDisplay.substringBefore(" - ").substringBefore(" – ").trim()
            if (beforeDash.length >= 2 && beforeDash != coreDisplay) {
                queries.add(beforeDash)
            }
            if (queries.isEmpty()) {
                val rawNorm = normalizeText(trackTitle)
                if (rawNorm.isNotBlank()) queries.add(rawNorm)
            }
            return queries.toList()
        }

        internal fun selectVerifiedSoundCloudMatch(
            candidates: List<OnlineTrack>,
            targetTitle: String,
            targetArtist: String,
            expectedDurationMs: Long
        ): OnlineTrack? {
            val cleanCoreDisplay = cleanTitleForCrossSearch(targetTitle)
            val cleanTargetNorm = normalizeText(cleanCoreDisplay)
            val fullTargetNorm = normalizeText(TITLE_NOISE_REGEX.replace(targetTitle, " "))
            if (cleanTargetNorm.isBlank()) return null

            val targetTokens = tokenizeForMatch(cleanTargetNorm)
            if (targetTokens.isEmpty()) return null

            val effectiveArtist = extractEffectiveArtistHint(targetTitle, targetArtist)
            val cleanTargetArtist = normalizeText(effectiveArtist)
                .takeIf { it.isNotBlank() && it !in GENERIC_LABEL_NAMES }
                .orEmpty()
            val artistTokens = tokenizeForMatch(cleanTargetArtist)

            val expectedModifiers = VARIANT_MODIFIERS.filter { fullTargetNorm.contains(it) }.toSet()

            return candidates
                .asSequence()
                .filter { it.kind == OnlineItemKind.TRACK && it.contentUrl.isNotBlank() }
                .mapNotNull { candidate ->
                    // Reject SoundCloud Go+ ~30s preview clips when a full-length song is expected
                    if (expectedDurationMs >= 60_000L && candidate.durationMs in 1L..35_000L) {
                        return@mapNotNull null
                    }
                    if (expectedDurationMs > 0L && candidate.durationMs > 0L) {
                        val toleranceMs = maxOf(45_000L, (expectedDurationMs * 0.35).toLong())
                        if (abs(candidate.durationMs - expectedDurationMs) > toleranceMs) {
                            return@mapNotNull null
                        }
                    }

                    val candCleanNorm = normalizeText(cleanTitleForCrossSearch(candidate.title))
                    val candFullNorm = normalizeText(TITLE_NOISE_REGEX.replace(candidate.title, " "))
                    val candTokens = tokenizeForMatch(candCleanNorm).ifEmpty { tokenizeForMatch(candFullNorm) }
                    if (candTokens.isEmpty()) return@mapNotNull null

                    // Reject unwanted remixes/covers/8D/slowed variants when not in original query
                    val candModifiers = VARIANT_MODIFIERS.filter { candFullNorm.contains(it) }.toSet()
                    if ((candModifiers - expectedModifiers).isNotEmpty()) {
                        return@mapNotNull null
                    }

                    val intersection = targetTokens.intersect(candTokens).size.toFloat()
                    if (intersection == 0f) return@mapNotNull null

                    val forwardOverlap = intersection / targetTokens.size.coerceAtLeast(1).toFloat()
                    val reverseOverlap = intersection / candTokens.size.coerceAtLeast(1).toFloat()
                    val containsFull = candFullNorm.contains(cleanTargetNorm) ||
                        cleanTargetNorm.contains(candFullNorm) ||
                        candCleanNorm.contains(cleanTargetNorm) ||
                        cleanTargetNorm.contains(candCleanNorm)

                    val titleAccepted = containsFull ||
                        forwardOverlap >= 0.55f ||
                        (intersection >= 2f && reverseOverlap >= 0.70f)
                    if (!titleAccepted) {
                        return@mapNotNull null
                    }

                    val candArtist = normalizeText(candidate.artist)
                    val candAllText = "$candFullNorm $candArtist"
                    val artistHits = if (artistTokens.isNotEmpty()) {
                        artistTokens.count { candAllText.contains(it) }.toFloat() / artistTokens.size.toFloat()
                    } else {
                        0f
                    }
                    val durationCloseEnough = expectedDurationMs > 0L &&
                        candidate.durationMs > 0L &&
                        abs(candidate.durationMs - expectedDurationMs) <= maxOf(20_000L, (expectedDurationMs * 0.20).toLong())

                    if (artistTokens.isNotEmpty() &&
                        artistHits == 0f &&
                        !containsFull &&
                        forwardOverlap < 0.80f &&
                        !(durationCloseEnough && forwardOverlap >= 0.60f)
                    ) {
                        return@mapNotNull null
                    }

                    // For very short single-token Latin titles, require either artist hit or close duration match
                    val isSingleShortLatinToken = targetTokens.size == 1 &&
                        cleanTargetNorm.length < 5 &&
                        cleanTargetNorm.all { it.code < 128 }
                    if (isSingleShortLatinToken && artistHits == 0f && !durationCloseEnough) {
                        return@mapNotNull null
                    }

                    val durationBonus = if (expectedDurationMs > 0L && candidate.durationMs > 0L) {
                        1f - (abs(candidate.durationMs - expectedDurationMs).toFloat() / expectedDurationMs.toFloat()).coerceIn(0f, 1f)
                    } else {
                        0.4f
                    }
                    val totalScore = (forwardOverlap * 3f) +
                        (reverseOverlap * 1.5f) +
                        (artistHits * 2f) +
                        durationBonus +
                        (if (containsFull) 1.5f else 0f)
                    candidate to totalScore
                }
                .maxByOrNull { it.second }
                ?.first
        }

        private fun tokenizeForMatch(normalized: String): Set<String> {
            val all = normalized.split(' ').filter { it.isNotBlank() }
            if (all.isEmpty()) return emptySet()
            val meaningful = all.filter { token ->
                token !in MATCH_STOP_WORDS && (token.length >= 2 || token.any { it.code > 127 })
            }.toSet()
            return meaningful.ifEmpty {
                all.filter { it.length >= 2 || it.any { ch -> ch.code > 127 } }.toSet()
            }.ifEmpty { all.toSet() }
        }

        internal fun normalizeText(raw: String): String {
            val nfc = Normalizer.normalize(raw, Normalizer.Form.NFC)
            return NON_ALNUM_UNICODE_REGEX.replace(nfc.lowercase(Locale.ROOT), " ")
                .replace(Regex("\\s+"), " ")
                .trim()
        }
    }

    fun invalidateStreamCache(contentUrl: String) {
        streamExtractionCache.remove(contentUrl)
    }

    fun clearDiscoverCache() {
        discoveryCache.evictAll()
        discoverSectionCache.evictAll()
        kioskContinuationCache.evictAll()
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
        forceRefresh: Boolean = false,
        trackTitleHint: String = "",
        trackArtistHint: String = "",
        expectedDurationMs: Long = 0L
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

        val useSingleFlight = !forceRefresh && manualStreamId.isNullOrBlank()
        var ownerDeferred: CompletableDeferred<OnlineResult<ResolvedOnlineStreamResult>>? = null

        if (useSingleFlight) {
            val existing = inFlightStreamResolutions[contentUrl]
            if (existing != null && !existing.isCompleted) {
                try {
                    return@withContext existing.await()
                } catch (ce: CancellationException) {
                    coroutineContext.ensureActive()
                }
            }

            val created = CompletableDeferred<OnlineResult<ResolvedOnlineStreamResult>>()
            val raced = inFlightStreamResolutions.putIfAbsent(contentUrl, created)
            if (raced != null && !raced.isCompleted) {
                try {
                    return@withContext raced.await()
                } catch (ce: CancellationException) {
                    coroutineContext.ensureActive()
                }
            }
            if (raced == null) {
                ownerDeferred = created
            } else if (inFlightStreamResolutions.replace(contentUrl, raced, created)) {
                ownerDeferred = created
            }
        }

        try {
            val result = executeResolveStream(
                serviceId = serviceId,
                providerKey = providerKey,
                providerName = providerName,
                contentUrl = contentUrl,
                qualityPreference = qualityPreference,
                manualStreamId = manualStreamId,
                trackTitleHint = trackTitleHint,
                trackArtistHint = trackArtistHint,
                expectedDurationMs = expectedDurationMs
            )
            ownerDeferred?.complete(result)
            return@withContext result
        } catch (ce: CancellationException) {
            ownerDeferred?.cancel(ce)
            throw ce
        } catch (t: Throwable) {
            val mapped = OnlineSearchDataSource.mapThrowableToError(
                t = t,
                providerName = providerName,
                operation = "resolve_stream",
                requestType = providerKey
            )
            ownerDeferred?.complete(mapped)
            return@withContext mapped
        } finally {
            if (ownerDeferred != null) {
                inFlightStreamResolutions.remove(contentUrl, ownerDeferred)
            }
        }
    }

    private suspend fun executeResolveStream(
        serviceId: Int,
        providerKey: String,
        providerName: String,
        contentUrl: String,
        qualityPreference: AudioQualityPreference,
        manualStreamId: String?,
        trackTitleHint: String,
        trackArtistHint: String,
        expectedDurationMs: Long
    ): OnlineResult<ResolvedOnlineStreamResult> {
        val isYouTube = providerKey == OnlineMusicProvider.KEY_YOUTUBE_MUSIC ||
            providerKey == OnlineMusicProvider.KEY_YOUTUBE ||
            serviceId == ServiceList.YouTube.serviceId

        var primaryFailure: Throwable? = null

        try {
            val primaryAttempt = OnlineSearchDataSource.runWithTransientRetry(
                providerName = providerName,
                operation = "resolve_stream",
                requestInfo = providerKey
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
                    return@runWithTransientRetry null
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

            if (primaryAttempt != null) {
                return primaryAttempt
            }
            if (!isYouTube) {
                return OnlineResult.Error(
                    kind = OnlineErrorKind.NO_PLAYABLE_AUDIO_STREAM,
                    message = "No playable audio stream was exposed for this track on $providerName.",
                    isRetryable = false
                )
            }
            primaryFailure = ExtractionException("VISIONOS client returned no playable streams")
        } catch (ce: CancellationException) {
            throw ce
        } catch (t: Throwable) {
            primaryFailure = t
        }

        coroutineContext.ensureActive()

        // Recover YouTube / YT Music playback when VISIONOS triggers IP anti-bot verification or fails playability
        val recoverableFailure = primaryFailure
        val shouldTryYoutubeFallbacks = isYouTube && (
            recoverableFailure is SignInConfirmNotBotException ||
                recoverableFailure is ExtractionException ||
                recoverableFailure?.message?.contains("a bot", ignoreCase = true) == true ||
                recoverableFailure?.message?.contains("LOGIN_REQUIRED", ignoreCase = true) == true
            )

        var sawBotCheck = recoverableFailure is SignInConfirmNotBotException ||
            recoverableFailure?.message?.contains("a bot", ignoreCase = true) == true ||
            recoverableFailure?.message?.contains("LOGIN_REQUIRED", ignoreCase = true) == true
        var effectiveThrowable: Throwable = recoverableFailure ?: ExtractionException("Extraction failed")

        if (shouldTryYoutubeFallbacks) {
            val localization = runCatching { NewPipe.getPreferredLocalization() }
                .getOrNull()
                ?: Localization(
                    domain.online.OnlineRegionCatalog.DEFAULT_LANGUAGE_CODE,
                    domain.online.OnlineRegionCatalog.DEFAULT_COUNTRY_CODE
                )
            val country = runCatching { NewPipe.getPreferredContentCountry() }
                .getOrNull()
                ?: ContentCountry(domain.online.OnlineRegionCatalog.DEFAULT_COUNTRY_CODE)

            when (
                val fallback = youtubeFallbackClient.resolveWithFallbackClients(
                    downloader = runCatching { NewPipe.getDownloader() }.getOrNull() ?: NewPipeInitializer.getDownloader(),
                    contentUrl = contentUrl,
                    localization = localization,
                    country = country
                )
            ) {
                is YoutubeFallbackPlayerClient.FallbackAttemptResult.Success -> {
                    coroutineContext.ensureActive()
                    val payload = fallback.payload
                    val selected = selectBestAudioStream(
                        options = payload.options,
                        preference = qualityPreference,
                        isMeteredConnection = connectivityMonitor.isCurrentlyMetered(),
                        manualStreamId = manualStreamId
                    ) ?: payload.options.first()

                    val extraction = CachedStreamExtraction(
                        serviceId = serviceId,
                        providerKey = providerKey,
                        providerName = providerName,
                        contentUrl = contentUrl,
                        title = payload.title,
                        artist = payload.artist,
                        artistUrl = payload.artistUrl,
                        durationMs = payload.durationMs,
                        artworkUrl = payload.artworkUrl,
                        options = payload.options,
                        isLive = payload.isLive,
                        viewCount = payload.viewCount,
                        relatedTracks = emptyList(),
                        extractedAtMs = System.currentTimeMillis()
                    )
                    streamExtractionCache.put(contentUrl, extraction)

                    return OnlineResult.Success(
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
                            availableStreams = payload.options,
                            isLive = extraction.isLive,
                            viewCount = extraction.viewCount,
                            relatedTracks = emptyList(),
                            extractedAtMs = extraction.extractedAtMs
                        )
                    )
                }

                is YoutubeFallbackPlayerClient.FallbackAttemptResult.Failure -> {
                    if (fallback.sawBotCheck) sawBotCheck = true
                    if (recoverableFailure !is SignInConfirmNotBotException && fallback.lastThrowable != null) {
                        effectiveThrowable = fallback.lastThrowable
                    }
                }
            }

            coroutineContext.ensureActive()

            // Tier 2: When all YouTube Innertube clients reject the emulator/cloud IP as a bot,
            // locate the exact verified track on SoundCloud using multi-query NewPipe extraction.
            if (sawBotCheck && manualStreamId.isNullOrBlank() && trackTitleHint.isNotBlank()) {
                val crossProvider = resolveVerifiedSoundCloudFallback(
                    cacheKeyContentUrl = contentUrl,
                    trackTitle = trackTitleHint,
                    trackArtist = trackArtistHint,
                    expectedDurationMs = expectedDurationMs,
                    qualityPreference = qualityPreference
                )
                if (crossProvider != null) {
                    return OnlineResult.Success(crossProvider)
                }
            }
        }

        coroutineContext.ensureActive()
        return OnlineSearchDataSource.mapThrowableToError(
            t = effectiveThrowable,
            providerName = providerName,
            operation = "resolve_stream",
            requestType = providerKey
        )
    }

    private suspend fun resolveVerifiedSoundCloudFallback(
        cacheKeyContentUrl: String,
        trackTitle: String,
        trackArtist: String,
        expectedDurationMs: Long,
        qualityPreference: AudioQualityPreference
    ): ResolvedOnlineStreamResult? {
        return try {
            NewPipeInitializer.ensureInitialized(null)
            val soundCloud = ServiceList.SoundCloud
            val queries = buildSoundCloudFallbackQueries(trackTitle, trackArtist)
            if (queries.isEmpty()) return null

            val filters = NewPipeInitializer.resolveNewPipeContentFilters(
                providerKey = OnlineMusicProvider.KEY_SOUNDCLOUD,
                filter = OnlineSearchFilter.SONGS,
                service = soundCloud
            )

            var matched: OnlineTrack? = null
            for (query in queries) {
                coroutineContext.ensureActive()
                val candidates = runCatching {
                    val qh = soundCloud.searchQHFactory.fromQuery(query, filters, "")
                    val searchInfo = SearchInfo.getInfo(soundCloud, qh)
                    searchInfo.relatedItems.orEmpty().mapNotNull { item ->
                        OnlineTrackMapper.mapInfoItem(
                            item = item,
                            serviceId = soundCloud.serviceId,
                            providerKey = OnlineMusicProvider.KEY_SOUNDCLOUD,
                            providerName = "SoundCloud"
                        )
                    }
                }.onFailure { searchErr ->
                    if (searchErr is CancellationException) throw searchErr
                    Log.w(TAG, "SoundCloud search fallback query='$query' failed: ${searchErr.message}")
                }.getOrDefault(emptyList())

                if (candidates.isNotEmpty()) {
                    matched = selectVerifiedSoundCloudMatch(
                        candidates = candidates,
                        targetTitle = trackTitle,
                        targetArtist = trackArtist,
                        expectedDurationMs = expectedDurationMs
                    )
                    if (matched != null) break
                }
            }

            if (matched == null) return null
            coroutineContext.ensureActive()

            val scInfo = StreamInfo.getInfo(soundCloud, matched.contentUrl)
            val scOptions = scInfo.audioStreams
                .orEmpty()
                .mapIndexedNotNull { idx, stream -> OnlineTrackMapper.mapAudioStream(stream, idx) }
                .sortedByDescending { it.averageBitrateKbps }
            if (scOptions.isEmpty()) return null

            val selected = selectBestAudioStream(
                options = scOptions,
                preference = qualityPreference,
                isMeteredConnection = connectivityMonitor.isCurrentlyMetered(),
                manualStreamId = null
            ) ?: scOptions.first()

            val artworkUrl = OnlineTrackMapper.selectBestArtworkUrl(scInfo.thumbnails) ?: matched.artworkUrl
            val durationMs = if (scInfo.duration > 0L) scInfo.duration * 1000L else matched.durationMs
            val fallbackProviderLabel = "SoundCloud (YT Fallback)"

            val extraction = CachedStreamExtraction(
                serviceId = soundCloud.serviceId,
                providerKey = OnlineMusicProvider.KEY_SOUNDCLOUD,
                providerName = fallbackProviderLabel,
                contentUrl = cacheKeyContentUrl,
                title = scInfo.name.orEmpty().ifBlank { trackTitle },
                artist = scInfo.uploaderName.orEmpty().ifBlank { matched.artist },
                artistUrl = scInfo.uploaderUrl ?: matched.artistUrl,
                durationMs = durationMs,
                artworkUrl = artworkUrl,
                options = scOptions,
                isLive = false,
                viewCount = scInfo.viewCount,
                relatedTracks = emptyList(),
                extractedAtMs = System.currentTimeMillis()
            )
            streamExtractionCache.put(cacheKeyContentUrl, extraction)
            Log.i(TAG, "Recovered YT IP-blocked track '$trackTitle' via verified SoundCloud stream '${matched.title}'")

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
                availableStreams = scOptions,
                isLive = false,
                viewCount = extraction.viewCount,
                relatedTracks = emptyList(),
                extractedAtMs = extraction.extractedAtMs
            )
        } catch (ce: CancellationException) {
            throw ce
        } catch (t: Throwable) {
            Log.w(TAG, "SoundCloud cross-provider fallback failed for '$trackTitle': ${t.message}")
            null
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
            val result = extractKioskTracksWithCandidates(provider)
            if (result.items.isNotEmpty()) {
                discoveryCache.put(cacheKey, now to result.items)
            }
            OnlineResult.Success(result.items)
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
        recommendationProfile: OnlineRecommendationProfile? = null,
        forceRefresh: Boolean = false
    ): DiscoverSectionState = withContext(Dispatchers.IO) {
        val cleanCountry = countryCode.uppercase()
        val cleanLang = languageCode.lowercase()
        val recommendationSeed = recommendationProfile?.seedQuery?.takeIf {
            sectionType == DiscoverSectionType.MUSIC_RECOMMENDATIONS
        }
        val cacheKey = "${provider.key}|$cleanCountry|$cleanLang|${discoverLanguage.id}|${sectionType.name}|${recommendationSeed ?: recentArtistSeed.orEmpty()}"
        val now = System.currentTimeMillis()
        if (!forceRefresh) {
            val cached = discoverSectionCache.get(cacheKey)
            if (cached != null && (now - cached.fetchedAtMs) < DISCOVERY_CACHE_TTL_MS) {
                return@withContext if (cached.items.isEmpty()) {
                    DiscoverSectionState.Empty("No items available right now on ${provider.displayName}.")
                } else {
                    DiscoverSectionState.Success(
                        items = cached.items,
                        sourceLabel = cached.sourceLabel,
                        nextPageToken = cached.nextPageToken,
                        hasMore = !cached.nextPageToken.isNullOrBlank(),
                        isLoadingMore = false
                    )
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
            recentArtistSeed = recentArtistSeed,
            recommendationProfile = recommendationProfile
        )

        return@withContext try {
            when (sectionType) {
                DiscoverSectionType.TRENDING_MUSIC -> {
                    val useKioskFirst = discoverLanguage == DiscoverMusicLanguage.ALL && provider.supportsDiscoveryFeed
                    val kioskResult = if (useKioskFirst) {
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

                    if (kioskResult != null && kioskResult.items.isNotEmpty()) {
                        val sourceLabel = "${provider.displayName} Charts ($cleanCountry)"
                        discoverSectionCache.put(
                            cacheKey,
                            CachedDiscoverSection(
                                fetchedAtMs = now,
                                items = kioskResult.items,
                                sourceLabel = sourceLabel,
                                nextPageToken = kioskResult.nextPageToken
                            )
                        )
                        DiscoverSectionState.Success(
                            items = kioskResult.items,
                            sourceLabel = sourceLabel,
                            nextPageToken = kioskResult.nextPageToken,
                            hasMore = !kioskResult.nextPageToken.isNullOrBlank(),
                            isLoadingMore = false
                        )
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
                            sourceSuffix = formatSourceLabel(provider.displayName, cleanCountry, discoverLanguage)
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
                    val recFilters = if (!recommendationProfile?.primaryArtistSeed.isNullOrBlank()) {
                        listOf(OnlineSearchFilter.SONGS, OnlineSearchFilter.PLAYLISTS, OnlineSearchFilter.ALL)
                    } else {
                        listOf(OnlineSearchFilter.PLAYLISTS, OnlineSearchFilter.ALL)
                    }
                    searchBackedSectionWithFallback(
                        sectionType = sectionType,
                        primaryQuery = querySpec.primaryQuery,
                        broaderFallbackQuery = querySpec.broaderFallbackQuery,
                        preferredFilters = recFilters,
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

                DiscoverSectionType.ONLINE_PLAYLISTS -> {
                    searchBackedSectionWithFallback(
                        sectionType = sectionType,
                        primaryQuery = querySpec.primaryQuery,
                        broaderFallbackQuery = querySpec.broaderFallbackQuery,
                        preferredFilters = listOf(OnlineSearchFilter.PLAYLISTS, OnlineSearchFilter.ALL),
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

    suspend fun loadMoreDiscoverSection(
        sectionType: DiscoverSectionType,
        provider: OnlineMusicProvider,
        previousToken: String,
        searchDataSource: OnlineSearchDataSource
    ): OnlineResult<Pair<List<OnlineTrack>, String?>> = withContext(Dispatchers.IO) {
        if (previousToken.isBlank()) {
            return@withContext OnlineResult.Success(emptyList<OnlineTrack>() to null)
        }
        if (!connectivityMonitor.isCurrentlyOnline()) {
            return@withContext OnlineResult.Error(
                kind = OnlineErrorKind.OFFLINE,
                message = "Connect to the internet to load more ${sectionType.title}.",
                isRetryable = true
            )
        }

        if (previousToken.startsWith("kiosk:")) {
            val state = kioskContinuationCache.get(previousToken)
                ?: return@withContext OnlineResult.Success(emptyList<OnlineTrack>() to null)
            return@withContext try {
                OnlineSearchDataSource.runWithTransientRetry(
                    providerName = state.providerName,
                    operation = "kiosk_load_more",
                    requestInfo = state.providerKey
                ) {
                    NewPipeInitializer.ensureInitialized(null)
                    val service = NewPipeInitializer.getServiceForProviderKey(state.providerKey)
                    val more = KioskInfo.getMoreItems(service, state.kioskUrl, state.nextPage)
                    val mapped = more.items
                        .orEmpty()
                        .mapNotNull { item ->
                            OnlineTrackMapper.mapInfoItem(
                                item = item,
                                serviceId = state.serviceId,
                                providerKey = state.providerKey,
                                providerName = state.providerName
                            )
                        }
                        .filter { it.kind == OnlineItemKind.TRACK }
                    val nextPage = more.nextPage
                    val nextToken = if (more.hasNextPage() && nextPage != null) {
                        "kiosk:${UUID.randomUUID()}".also { key ->
                            kioskContinuationCache.put(key, state.copy(nextPage = nextPage))
                        }
                    } else {
                        null
                    }
                    OnlineResult.Success(mapped to nextToken)
                }
            } catch (ce: CancellationException) {
                throw ce
            } catch (t: Throwable) {
                OnlineSearchDataSource.mapThrowableToError(
                    t = t,
                    providerName = provider.displayName,
                    operation = "kiosk_load_more",
                    requestType = provider.key
                )
            }
        }

        return@withContext when (val res = searchDataSource.loadMore(previousToken)) {
            is OnlineResult.Success -> OnlineResult.Success(res.value.items to res.value.nextPageToken)
            is OnlineResult.Error -> res
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
        recentArtistSeed: String?,
        recommendationProfile: OnlineRecommendationProfile?
    ): DiscoverQuerySpec {
        val cleanSeed = recentArtistSeed?.trim()?.takeIf { it.length >= 2 }
        val languageHint = OnlineRecommendationEngine.resolveLocaleMusicHint(
            countryCode = countryCode,
            languageCode = languageCode,
            discoverLanguage = discoverLanguage
        )

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
                if (recommendationProfile != null && recommendationProfile.seedQuery.isNotBlank()) {
                    DiscoverQuerySpec(
                        primaryQuery = recommendationProfile.seedQuery,
                        broaderFallbackQuery = recommendationProfile.fallbackQuery
                            .takeIf { !it.equals(recommendationProfile.seedQuery, ignoreCase = true) }
                    )
                } else {
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
            }
            DiscoverSectionType.POPULAR_ARTISTS -> DiscoverQuerySpec(
                primaryQuery = if (hasLocaleFocus) "popular $prefix music artists singers" else "popular music artists",
                broaderFallbackQuery = if (hasLocaleFocus) "popular music artists" else null
            )
            DiscoverSectionType.ONLINE_PLAYLISTS -> DiscoverQuerySpec(
                primaryQuery = if (hasLocaleFocus) "$prefix curated music playlists hits" else "top music playlists mix",
                broaderFallbackQuery = if (hasLocaleFocus) "top music playlists mix" else null
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
                val nextToken = res.value.nextPageToken
                if (items.isNotEmpty()) {
                    discoverSectionCache.put(
                        cacheKey,
                        CachedDiscoverSection(
                            fetchedAtMs = System.currentTimeMillis(),
                            items = items,
                            sourceLabel = sourceSuffix,
                            nextPageToken = nextToken
                        )
                    )
                    DiscoverSectionState.Success(
                        items = items,
                        sourceLabel = sourceSuffix,
                        nextPageToken = nextToken,
                        hasMore = !nextToken.isNullOrBlank(),
                        isLoadingMore = false
                    )
                } else if (!broaderFallbackQuery.isNullOrBlank() && !broaderFallbackQuery.equals(primaryQuery, ignoreCase = true)) {
                    // Graceful fallback when a provider (e.g., Bandcamp/PeerTube) has no results for a specific language/region query
                    when (val fallbackRes = searchDataSource.search(broaderFallbackQuery, provider, chosenFilter)) {
                        is OnlineResult.Success -> {
                            val fallbackItems = fallbackRes.value.items.take(20)
                            val fallbackToken = fallbackRes.value.nextPageToken
                            if (fallbackItems.isEmpty()) {
                                DiscoverSectionState.Empty("No ${sectionType.title.lowercase()} available for this filter on ${provider.displayName}.")
                            } else {
                                val fallbackLabel = "${provider.displayName} Global Fallback"
                                discoverSectionCache.put(
                                    cacheKey,
                                    CachedDiscoverSection(
                                        fetchedAtMs = System.currentTimeMillis(),
                                        items = fallbackItems,
                                        sourceLabel = fallbackLabel,
                                        nextPageToken = fallbackToken
                                    )
                                )
                                DiscoverSectionState.Success(
                                    items = fallbackItems,
                                    sourceLabel = fallbackLabel,
                                    nextPageToken = fallbackToken,
                                    hasMore = !fallbackToken.isNullOrBlank(),
                                    isLoadingMore = false
                                )
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

    private suspend fun extractKioskTracksWithCandidates(provider: OnlineMusicProvider): KioskExtractionResult {
        NewPipeInitializer.ensureInitialized(null)
        val service: StreamingService = NewPipeInitializer.getServiceForProviderKey(provider.key)
        val kioskList = service.kioskList ?: return KioskExtractionResult(emptyList(), null)
        val available = kioskList.availableKiosks?.toList().orEmpty()
        if (available.isEmpty()) return KioskExtractionResult(emptyList(), null)

        val rankedKiosks = rankPreferredKioskIds(available, provider.key)
        var lastError: Throwable? = null

        for (kioskId in rankedKiosks) {
            try {
                val extracted = OnlineSearchDataSource.runWithTransientRetry(
                    providerName = provider.displayName,
                    operation = "kiosk_$kioskId",
                    requestInfo = provider.key
                ) {
                    val extractor = kioskList.getExtractorById(kioskId, null)
                    val kioskInfo = fetchKioskInfoSafely(extractor)
                    val mapped = kioskInfo.relatedItems
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
                    val nextPage = kioskInfo.nextPage
                    val nextToken = if (kioskInfo.hasNextPage() && nextPage != null && !kioskInfo.url.isNullOrBlank()) {
                        "kiosk:${UUID.randomUUID()}".also { key ->
                            kioskContinuationCache.put(
                                key,
                                KioskContinuationState(
                                    providerKey = provider.key,
                                    providerName = provider.displayName,
                                    serviceId = provider.serviceId,
                                    kioskUrl = kioskInfo.url,
                                    nextPage = nextPage
                                )
                            )
                        }
                    } else {
                        null
                    }
                    KioskExtractionResult(mapped, nextToken)
                }
                if (extracted.items.isNotEmpty()) {
                    return extracted
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
        return KioskExtractionResult(emptyList(), null)
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
