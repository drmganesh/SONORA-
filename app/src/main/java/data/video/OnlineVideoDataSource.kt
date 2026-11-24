package data.video

import android.util.Log
import android.util.LruCache
import data.online.NetworkConnectivityMonitor
import data.online.NewPipeDownloader
import data.online.NewPipeInitializer
import data.online.OnlineStreamResolver
import data.online.OnlineTrackMapper
import data.online.YoutubeFallbackPlayerClient
import domain.online.OnlineErrorKind
import domain.online.OnlineResult
import domain.online.StreamDeliveryType
import domain.video.ChannelDetailUtils
import domain.video.ChannelTabType
import domain.video.OnlineVideoChannel
import domain.video.OnlineVideoItem
import domain.video.OnlineVideoPlaylistSummary
import domain.video.ResolvedVideoDetails
import domain.video.VideoAudioOption
import domain.video.VideoCommentItem
import domain.video.VideoHomeSectionType
import domain.video.VideoRegionalLanguage
import domain.video.VideoSearchFilter
import domain.video.VideoStreamOption
import domain.video.VideoSubtitleOption
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.InfoItem
import org.schabi.newpipe.extractor.Page
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.channel.ChannelInfo
import org.schabi.newpipe.extractor.channel.ChannelInfoItem
import org.schabi.newpipe.extractor.channel.tabs.ChannelTabInfo
import org.schabi.newpipe.extractor.exceptions.AgeRestrictedContentException
import org.schabi.newpipe.extractor.exceptions.ContentNotAvailableException
import org.schabi.newpipe.extractor.exceptions.GeographicRestrictionException
import org.schabi.newpipe.extractor.exceptions.PaidContentException
import org.schabi.newpipe.extractor.exceptions.SignInConfirmNotBotException
import org.schabi.newpipe.extractor.linkhandler.ListLinkHandler
import org.schabi.newpipe.extractor.playlist.PlaylistInfo
import org.schabi.newpipe.extractor.playlist.PlaylistInfoItem
import org.schabi.newpipe.extractor.search.SearchInfo
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import java.io.IOException
import java.util.Locale
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlin.coroutines.coroutineContext

data class VideoFeedPage(
    val items: List<OnlineVideoItem>,
    val nextPageToken: String?,
    val sourceLabel: String
)

data class VideoSearchPage(
    val videos: List<OnlineVideoItem>,
    val channels: List<OnlineVideoChannel>,
    val playlists: List<OnlineVideoPlaylistSummary>,
    val nextPageToken: String?
)

data class ChannelTabPageResult(
    val tab: ChannelTabType,
    val videos: List<OnlineVideoItem> = emptyList(),
    val playlists: List<OnlineVideoPlaylistSummary> = emptyList(),
    val nextPageToken: String? = null
)

data class ChannelProfileResult(
    val channel: OnlineVideoChannel,
    val initialTab: ChannelTabType,
    val initialTabPage: ChannelTabPageResult?
)

data class ExternalPlaylistPageResult(
    val playlist: OnlineVideoPlaylistSummary,
    val nextPageToken: String? = null
)

/**
 * Real NewPipe Extractor data source for SONORA Online Video discovery, search, channel browsing,
 * and video/audio/subtitle/comment extraction.
 *
 * Prioritizes Indian content (default IN / en) and supports Hindi, Odia, Tamil, Telugu, Malayalam,
 * Kannada, Bengali, and Punjabi without fabricated metadata or fake view counts.
 */
class OnlineVideoDataSource(
    private val connectivityMonitor: NetworkConnectivityMonitor
) {

    private data class CachedSection(
        val fetchedAtMs: Long,
        val page: VideoFeedPage
    )

    private data class ContinuationState(
        val isKiosk: Boolean,
        val kioskUrl: String? = null,
        val query: String? = null,
        val contentFilters: List<String> = emptyList(),
        val nextPage: Page
    )

    private data class CachedChannelBundle(
        val fetchedAtMs: Long,
        val channel: OnlineVideoChannel,
        val tabHandlers: Map<ChannelTabType, ListLinkHandler>
    )

    private data class CachedChannelTabPage(
        val fetchedAtMs: Long,
        val page: ChannelTabPageResult
    )

    private data class ChannelTabContinuation(
        val channelKey: String,
        val channelName: String,
        val channelUrl: String,
        val channelAvatarUrl: String?,
        val tabType: ChannelTabType,
        val tabHandler: ListLinkHandler,
        val nextPage: Page
    )

    private data class CachedExternalPlaylist(
        val fetchedAtMs: Long,
        val page: ExternalPlaylistPageResult
    )

    private data class ExternalPlaylistContinuation(
        val playlistUrl: String,
        val nextPage: Page
    )

    private val sectionCache = LruCache<String, CachedSection>(32)
    private val detailsCache = LruCache<String, ResolvedVideoDetails>(24)
    private val continuationCache = LruCache<String, ContinuationState>(32)
    private val channelCache = LruCache<String, CachedChannelBundle>(24)
    private val channelTabCache = LruCache<String, CachedChannelTabPage>(48)
    private val channelTabContinuations = LruCache<String, ChannelTabContinuation>(32)
    private val externalPlaylistCache = LruCache<String, CachedExternalPlaylist>(24)
    private val externalPlaylistContinuations = LruCache<String, ExternalPlaylistContinuation>(32)

    companion object {
        private const val TAG = "OnlineVideoDataSource"
        private val SECTION_TTL_MS = TimeUnit.MINUTES.toMillis(12)
        private val CHANNEL_TTL_MS = TimeUnit.MINUTES.toMillis(10)
        private val STREAM_TTL_MS = TimeUnit.MINUTES.toMillis(15)
        private val SIGNED_URL_EXPIRE_REGEX = Regex("[?&](?:expire|expires)=(\\d{9,12})(?:&|$)")

        fun extractVideoIdFromUrl(url: String): String {
            return YoutubeFallbackPlayerClient.extractVideoId(url)
                ?: url.trim().hashCode().toUInt().toString(16)
        }

        fun extractChannelIdFromUrl(url: String?): String {
            if (url.isNullOrBlank()) return ""
            val clean = url.trim().substringBefore("?").trimEnd('/')
            val lastSeg = clean.substringAfterLast('/')
            return lastSeg.ifBlank { clean.hashCode().toUInt().toString(16) }
        }

        internal fun buildSectionSearchQuery(
            section: VideoHomeSectionType,
            countryCode: String,
            regionalLanguage: VideoRegionalLanguage,
            recentWatchHint: String?
        ): String {
            val isIndia = countryCode.equals("IN", ignoreCase = true)
            val regionName = if (isIndia) "India" else Locale("", countryCode).displayCountry.ifBlank { countryCode }
            val langPrefix = if (regionalLanguage == VideoRegionalLanguage.ALL_INDIA) {
                if (isIndia) "Indian" else regionName
            } else {
                regionalLanguage.queryKeyword
            }

            return when (section) {
                VideoHomeSectionType.RECOMMENDED -> {
                    val hint = recentWatchHint?.trim()?.takeIf { it.length >= 3 }
                    if (hint != null) "$hint videos" else "best $langPrefix trending videos $regionName"
                }
                VideoHomeSectionType.TRENDING -> "trending videos $regionName today"
                VideoHomeSectionType.POPULAR_MUSIC_VIDEOS -> "$langPrefix official music videos hits"
                VideoHomeSectionType.RECENTLY_UPLOADED -> "latest $langPrefix new videos 2025"
                VideoHomeSectionType.POPULAR_INDIAN -> "popular Indian videos viral Hindi creators"
                VideoHomeSectionType.MUSIC_VIDEOS -> "$langPrefix music video official 4K"
                VideoHomeSectionType.GAMING -> "$langPrefix gaming gameplay BGMI esports"
                VideoHomeSectionType.ENTERTAINMENT -> "$langPrefix entertainment comedy trailer show"
                VideoHomeSectionType.EDUCATIONAL -> "$langPrefix educational science coding explainer"
                VideoHomeSectionType.TECHNOLOGY -> "$langPrefix tech review smartphone AI gadgets"
                VideoHomeSectionType.REGIONAL -> {
                    if (regionalLanguage == VideoRegionalLanguage.ALL_INDIA) {
                        "Hindi Odia Tamil Telugu Punjabi Malayalam Kannada Bengali popular videos"
                    } else {
                        "${regionalLanguage.queryKeyword} popular videos movies music"
                    }
                }
                VideoHomeSectionType.RECENTLY_WATCHED -> ""
            }
        }
    }

    fun clearDiscoveryCaches() {
        sectionCache.evictAll()
        continuationCache.evictAll()
    }

    fun clearDetailsCache(videoId: String? = null) {
        if (videoId == null) detailsCache.evictAll() else detailsCache.remove(videoId)
    }

    suspend fun fetchHomeSection(
        section: VideoHomeSectionType,
        countryCode: String,
        languageCode: String,
        regionalLanguage: VideoRegionalLanguage,
        recentWatchHint: String? = null,
        forceRefresh: Boolean = false
    ): OnlineResult<VideoFeedPage> = withContext(Dispatchers.IO) {
        if (!connectivityMonitor.isCurrentlyOnline()) {
            return@withContext OnlineResult.Error(
                kind = OnlineErrorKind.OFFLINE,
                message = "No internet connection. Connect to browse Online Videos.",
                isRetryable = true
            )
        }

        val cacheKey = "${section.id}|$countryCode|$languageCode|${regionalLanguage.id}|${recentWatchHint.orEmpty()}"
        if (!forceRefresh) {
            val cached = sectionCache.get(cacheKey)
            if (cached != null && (System.currentTimeMillis() - cached.fetchedAtMs) < SECTION_TTL_MS) {
                return@withContext OnlineResult.Success(cached.page)
            }
        }

        try {
            NewPipeInitializer.ensureInitialized(null)
            NewPipeInitializer.updateLocalization(languageCode, countryCode)
            coroutineContext.ensureActive()

            val service = ServiceList.YouTube
            val preferredKioskId: String? = when (section) {
                VideoHomeSectionType.TRENDING -> "Trending"
                VideoHomeSectionType.POPULAR_MUSIC_VIDEOS -> "trending_music"
                VideoHomeSectionType.GAMING -> "trending_gaming"
                else -> null
            }

            // Try official YouTube kiosk first for Trending / Music / Gaming when available
            if (preferredKioskId != null && regionalLanguage == VideoRegionalLanguage.ALL_INDIA) {
                val kioskPage = runCatching {
                    val kioskList = service.kioskList
                    val available = kioskList.availableKiosks
                    val matchedId = available.firstOrNull { it.equals(preferredKioskId, ignoreCase = true) }
                    if (matchedId != null) {
                        val extractor = kioskList.getExtractorById(matchedId, null)
                        val info = OnlineStreamResolver.fetchKioskInfoSafely(extractor)
                        val videos = info.relatedItems
                            .filterIsInstance<StreamInfoItem>()
                            .mapNotNull { mapStreamInfoItem(it) }
                            .distinctBy { it.videoId }
                        if (videos.isNotEmpty()) {
                            val token = info.nextPage?.let { page ->
                                UUID.randomUUID().toString().also { id ->
                                    continuationCache.put(
                                        id,
                                        ContinuationState(isKiosk = true, kioskUrl = info.url, nextPage = page)
                                    )
                                }
                            }
                            VideoFeedPage(
                                items = videos,
                                nextPageToken = token,
                                sourceLabel = info.name.takeIf { !it.isNullOrBlank() } ?: "YouTube"
                            )
                        } else null
                    } else null
                }.getOrNull()

                if (kioskPage != null) {
                    sectionCache.put(cacheKey, CachedSection(System.currentTimeMillis(), kioskPage))
                    return@withContext OnlineResult.Success(kioskPage)
                }
            }

            // Real NewPipe YouTube video search for localized / category / regional sections
            val query = buildSectionSearchQuery(section, countryCode, regionalLanguage, recentWatchHint)
            if (query.isBlank()) {
                return@withContext OnlineResult.Success(VideoFeedPage(emptyList(), null, "YouTube"))
            }

            coroutineContext.ensureActive()
            val filters = listOf("videos")
            val searchQH = service.searchQHFactory.fromQuery(query, filters, "")
            val searchInfo = SearchInfo.getInfo(service, searchQH)
            coroutineContext.ensureActive()

            val videos = searchInfo.relatedItems
                .filterIsInstance<StreamInfoItem>()
                .mapNotNull { mapStreamInfoItem(it) }
                .distinctBy { it.videoId }

            val token = searchInfo.nextPage?.let { page ->
                UUID.randomUUID().toString().also { id ->
                    continuationCache.put(
                        id,
                        ContinuationState(
                            isKiosk = false,
                            query = query,
                            contentFilters = filters,
                            nextPage = page
                        )
                    )
                }
            }

            val resultPage = VideoFeedPage(
                items = videos,
                nextPageToken = token,
                sourceLabel = if (regionalLanguage != VideoRegionalLanguage.ALL_INDIA) {
                    "${regionalLanguage.label} • YouTube"
                } else {
                    "YouTube ($countryCode)"
                }
            )
            if (videos.isNotEmpty()) {
                sectionCache.put(cacheKey, CachedSection(System.currentTimeMillis(), resultPage))
            }
            OnlineResult.Success(resultPage)
        } catch (ce: CancellationException) {
            throw ce
        } catch (t: Throwable) {
            Log.w(TAG, "Failed loading video section ${section.id}", t)
            mapThrowableToError(t)
        }
    }

    suspend fun loadMoreHomeSection(nextPageToken: String): OnlineResult<VideoFeedPage> = withContext(Dispatchers.IO) {
        if (!connectivityMonitor.isCurrentlyOnline()) {
            return@withContext OnlineResult.Error(
                kind = OnlineErrorKind.OFFLINE,
                message = "No internet connection to load more videos.",
                isRetryable = true
            )
        }
        val state = continuationCache.get(nextPageToken)
            ?: return@withContext OnlineResult.Success(VideoFeedPage(emptyList(), null, "YouTube"))

        try {
            NewPipeInitializer.ensureInitialized(null)
            val service = ServiceList.YouTube
            val more = if (state.isKiosk && !state.kioskUrl.isNullOrBlank()) {
                val kioskPage = org.schabi.newpipe.extractor.kiosk.KioskInfo.getMoreItems(service, state.kioskUrl, state.nextPage)
                val items = kioskPage.items.filterIsInstance<StreamInfoItem>().mapNotNull { mapStreamInfoItem(it) }
                val next = kioskPage.nextPage?.let { np ->
                    UUID.randomUUID().toString().also { id ->
                        continuationCache.put(id, state.copy(nextPage = np))
                    }
                }
                VideoFeedPage(items = items, nextPageToken = next, sourceLabel = "YouTube")
            } else {
                val searchQH = service.searchQHFactory.fromQuery(state.query.orEmpty(), state.contentFilters, "")
                val morePage = SearchInfo.getMoreItems(service, searchQH, state.nextPage)
                val items = morePage.items.filterIsInstance<StreamInfoItem>().mapNotNull { mapStreamInfoItem(it) }
                val next = morePage.nextPage?.let { np ->
                    UUID.randomUUID().toString().also { id ->
                        continuationCache.put(id, state.copy(nextPage = np))
                    }
                }
                VideoFeedPage(items = items, nextPageToken = next, sourceLabel = "YouTube")
            }
            OnlineResult.Success(more)
        } catch (ce: CancellationException) {
            throw ce
        } catch (t: Throwable) {
            Log.w(TAG, "loadMoreHomeSection failed", t)
            mapThrowableToError(t)
        }
    }

    suspend fun getVideoSuggestions(query: String): List<String> = withContext(Dispatchers.IO) {
        val trimmed = query.trim()
        if (trimmed.length < 2 || !connectivityMonitor.isCurrentlyOnline()) return@withContext emptyList()
        runCatching {
            NewPipeInitializer.ensureInitialized(null)
            ServiceList.YouTube.suggestionExtractor.suggestionList(trimmed).take(8)
        }.getOrElse { emptyList() }
    }

    suspend fun searchOnlineVideos(
        query: String,
        filter: VideoSearchFilter
    ): OnlineResult<VideoSearchPage> = withContext(Dispatchers.IO) {
        val trimmed = query.trim()
        if (trimmed.isBlank()) {
            return@withContext OnlineResult.Success(VideoSearchPage(emptyList(), emptyList(), emptyList(), null))
        }
        if (!connectivityMonitor.isCurrentlyOnline()) {
            return@withContext OnlineResult.Error(
                kind = OnlineErrorKind.OFFLINE,
                message = "No internet connection for video search.",
                isRetryable = true
            )
        }
        try {
            NewPipeInitializer.ensureInitialized(null)
            val service = ServiceList.YouTube
            val filters = listOf(filter.newPipeFilter)
            val searchQH = service.searchQHFactory.fromQuery(trimmed, filters, "")
            val info = SearchInfo.getInfo(service, searchQH)
            coroutineContext.ensureActive()

            val mapped = partitionSearchItems(info.relatedItems)
            val nextToken = info.nextPage?.let { np ->
                UUID.randomUUID().toString().also { id ->
                    continuationCache.put(
                        id,
                        ContinuationState(isKiosk = false, query = trimmed, contentFilters = filters, nextPage = np)
                    )
                }
            }
            OnlineResult.Success(mapped.copy(nextPageToken = nextToken))
        } catch (ce: CancellationException) {
            throw ce
        } catch (t: Throwable) {
            Log.w(TAG, "searchOnlineVideos failed for '$trimmed'", t)
            mapThrowableToError(t)
        }
    }

    suspend fun loadMoreVideoSearch(nextPageToken: String): OnlineResult<VideoSearchPage> = withContext(Dispatchers.IO) {
        val state = continuationCache.get(nextPageToken)
            ?: return@withContext OnlineResult.Success(VideoSearchPage(emptyList(), emptyList(), emptyList(), null))
        try {
            NewPipeInitializer.ensureInitialized(null)
            val service = ServiceList.YouTube
            val searchQH = service.searchQHFactory.fromQuery(state.query.orEmpty(), state.contentFilters, "")
            val more = SearchInfo.getMoreItems(service, searchQH, state.nextPage)
            val mapped = partitionSearchItems(more.items)
            val next = more.nextPage?.let { np ->
                UUID.randomUUID().toString().also { id ->
                    continuationCache.put(id, state.copy(nextPage = np))
                }
            }
            OnlineResult.Success(mapped.copy(nextPageToken = next))
        } catch (ce: CancellationException) {
            throw ce
        } catch (t: Throwable) {
            mapThrowableToError(t)
        }
    }

    suspend fun resolveVideoDetails(
        video: OnlineVideoItem,
        forceRefresh: Boolean = false
    ): OnlineResult<ResolvedVideoDetails> = withContext(Dispatchers.IO) {
        if (!forceRefresh) {
            val cached = detailsCache.get(video.videoId)
            if (cached != null && !cached.isExpired()) {
                return@withContext OnlineResult.Success(cached)
            }
        }
        if (!connectivityMonitor.isCurrentlyOnline()) {
            return@withContext OnlineResult.Error(
                kind = OnlineErrorKind.OFFLINE,
                message = "Connect to the internet to play this online video.",
                isRetryable = true
            )
        }

        try {
            NewPipeInitializer.ensureInitialized(null)
            coroutineContext.ensureActive()
            val service = ServiceList.YouTube
            val streamInfo = StreamInfo.getInfo(service, video.contentUrl)
            coroutineContext.ensureActive()

            val headers = mapOf("User-Agent" to NewPipeDownloader.DEFAULT_USER_AGENT)

            val muxedStreams = streamInfo.videoStreams.orEmpty().mapNotNull { vs ->
                val url = vs.content?.takeIf { util.SecureUrlValidator.isSafeHttpsUrl(it, allowHttp = true, allowPrivateHosts = false) } ?: return@mapNotNull null
                val height = parseResolutionHeight(vs.resolution)
                val width = runCatching { vs.width }.getOrDefault(0).coerceAtLeast(0)
                VideoStreamOption(
                    id = "mux_${vs.itag}_${height}",
                    url = url,
                    resolutionLabel = vs.resolution.takeIf { !it.isNullOrBlank() } ?: "${height}p",
                    heightPx = height,
                    widthPx = width,
                    fps = vs.fps.coerceAtLeast(24),
                    mimeType = vs.format?.mimeType ?: "video/mp4",
                    formatLabel = vs.format?.name ?: "MP4",
                    isVideoOnly = false,
                    bitrateKbps = (vs.bitrate / 1000).coerceAtLeast(0),
                    codec = vs.codec.orEmpty(),
                    deliveryType = StreamDeliveryType.PROGRESSIVE_HTTP,
                    headers = headers
                )
            }

            val adaptiveVideoOnly = streamInfo.videoOnlyStreams.orEmpty().mapNotNull { vs ->
                val url = vs.content?.takeIf { util.SecureUrlValidator.isSafeHttpsUrl(it, allowHttp = true, allowPrivateHosts = false) } ?: return@mapNotNull null
                val height = parseResolutionHeight(vs.resolution)
                val width = runCatching { vs.width }.getOrDefault(0).coerceAtLeast(0)
                VideoStreamOption(
                    id = "vo_${vs.itag}_${height}_${vs.fps}",
                    url = url,
                    resolutionLabel = vs.resolution.takeIf { !it.isNullOrBlank() } ?: "${height}p",
                    heightPx = height,
                    widthPx = width,
                    fps = vs.fps.coerceAtLeast(24),
                    mimeType = vs.format?.mimeType ?: "video/mp4",
                    formatLabel = "${vs.format?.name ?: "MP4"} Adaptive",
                    isVideoOnly = true,
                    bitrateKbps = (vs.bitrate / 1000).coerceAtLeast(0),
                    codec = vs.codec.orEmpty(),
                    deliveryType = StreamDeliveryType.PROGRESSIVE_HTTP,
                    headers = headers
                )
            }

            val audioStreams = streamInfo.audioStreams.orEmpty().mapNotNull { audio ->
                val url = audio.content?.takeIf { util.SecureUrlValidator.isSafeHttpsUrl(it, allowHttp = true, allowPrivateHosts = false) } ?: return@mapNotNull null
                val localeName = audio.audioLocale?.displayLanguage?.takeIf { it.isNotBlank() }
                    ?: audio.audioTrackName?.takeIf { it.isNotBlank() }
                    ?: "Original Audio"
                VideoAudioOption(
                    id = "aud_${audio.itag}_${audio.averageBitrate}",
                    url = url,
                    languageLabel = localeName,
                    locale = audio.audioLocale?.toLanguageTag(),
                    mimeType = audio.format?.mimeType ?: "audio/mp4",
                    formatLabel = audio.format?.name ?: "M4A",
                    bitrateKbps = audio.averageBitrate.coerceAtLeast(0),
                    headers = headers
                )
            }.sortedByDescending { it.bitrateKbps }

            // Preserve original highest-quality streams first: resolution -> fps -> bitrate,
            // only tie-breaking software AV1 below hardware-accelerated H.264/VP9 at the same tier,
            // and never demoting higher-bitrate adaptive video streams below low-bitrate muxed streams.
            val combinedStreams = (muxedStreams + adaptiveVideoOnly)
                .filter { (!it.isVideoOnly || audioStreams.isNotEmpty()) && it.heightPx > 0 }
                .sortedWith(
                    compareByDescending<VideoStreamOption> { it.heightPx }
                        .thenByDescending { it.fps }
                        .thenByDescending { it.bitrateKbps }
                        .thenBy { it.isAv1Codec }
                        .thenByDescending { it.isVp9Codec || it.isAvcH264Codec }
                )
                .distinctBy { "${it.heightPx}_${it.fps}_${it.codecFamily}_${it.isVideoOnly}" }

            val subtitles = streamInfo.subtitles.orEmpty().mapNotNull { sub ->
                val url = sub.content?.takeIf { util.SecureUrlValidator.isSafeHttpsUrl(it, allowHttp = true, allowPrivateHosts = false) } ?: return@mapNotNull null
                VideoSubtitleOption(
                    id = "sub_${sub.languageTag}_${sub.isAutoGenerated}",
                    url = url,
                    languageTag = sub.languageTag.orEmpty().ifBlank { "und" },
                    displayName = buildString {
                        append(sub.displayLanguageName?.takeIf { it.isNotBlank() } ?: sub.languageTag ?: "Subtitles")
                        if (sub.isAutoGenerated) append(" (Auto)")
                    },
                    mimeType = sub.format?.mimeType ?: "text/vtt",
                    isAutoGenerated = sub.isAutoGenerated
                )
            }.distinctBy { "${it.languageTag}_${it.isAutoGenerated}" }

            val hlsUrl = streamInfo.hlsUrl?.takeIf { util.SecureUrlValidator.isSafeHttpsUrl(it, allowHttp = true, allowPrivateHosts = false) }
            val dashUrl = streamInfo.dashMpdUrl?.takeIf { util.SecureUrlValidator.isSafeHttpsUrl(it, allowHttp = true, allowPrivateHosts = false) }

            if (combinedStreams.isEmpty() && hlsUrl == null && dashUrl == null) {
                return@withContext OnlineResult.Error(
                    kind = OnlineErrorKind.UNSUPPORTED,
                    message = "No compatible playable video stream was returned for this video.",
                    isRetryable = true
                )
            }

            val related = streamInfo.relatedItems.orEmpty()
                .filterIsInstance<StreamInfoItem>()
                .mapNotNull { mapStreamInfoItem(it) }
                .distinctBy { it.videoId }
                .filter { it.videoId != video.videoId }

            // Comments are intentionally omitted from SONORA's Online Video Detail Screen
            // to avoid unnecessary provider network overhead.
            val comments: List<VideoCommentItem> = emptyList()

            val thumb = streamInfo.thumbnails.orEmpty().maxByOrNull { it.width }?.url ?: video.thumbnailUrl
            val avatar = streamInfo.uploaderAvatars.orEmpty().maxByOrNull { it.width }?.url ?: video.channelAvatarUrl
            val updatedVideo = video.copy(
                title = streamInfo.name?.takeIf { it.isNotBlank() } ?: video.title,
                channelName = streamInfo.uploaderName?.takeIf { it.isNotBlank() } ?: video.channelName,
                channelUrl = streamInfo.uploaderUrl?.takeIf { it.isNotBlank() } ?: video.channelUrl,
                channelAvatarUrl = avatar,
                thumbnailUrl = thumb,
                durationSeconds = streamInfo.duration.takeIf { it > 0L } ?: video.durationSeconds,
                viewCount = streamInfo.viewCount.takeIf { it >= 0L } ?: video.viewCount,
                uploadDateText = streamInfo.textualUploadDate?.takeIf { it.isNotBlank() } ?: video.uploadDateText,
                isLive = streamInfo.streamType.name.contains("LIVE", ignoreCase = true)
            )

            val nowMs = System.currentTimeMillis()
            val expiresAt = computeExpiry(combinedStreams.map { it.url }, nowMs)
            val resolved = ResolvedVideoDetails(
                video = updatedVideo,
                description = streamInfo.description?.content?.takeIf { it.isNotBlank() },
                likeCount = streamInfo.likeCount.takeIf { it >= 0L },
                dislikeCount = streamInfo.dislikeCount.takeIf { it >= 0L },
                viewCount = streamInfo.viewCount.takeIf { it >= 0L },
                uploadDateText = streamInfo.textualUploadDate?.takeIf { it.isNotBlank() },
                channelSubscriberCount = streamInfo.uploaderSubscriberCount.takeIf { it >= 0L },
                channelAvatarUrl = avatar,
                videoStreams = combinedStreams,
                audioStreams = audioStreams,
                subtitles = subtitles,
                hlsUrl = hlsUrl,
                dashMpdUrl = dashUrl,
                relatedVideos = related,
                comments = comments,
                extractedAtMs = nowMs,
                expiresAtMs = expiresAt
            )
            detailsCache.put(video.videoId, resolved)
            OnlineResult.Success(resolved)
        } catch (ce: CancellationException) {
            throw ce
        } catch (t: Throwable) {
            Log.w(TAG, "resolveVideoDetails failed for ${video.videoId}", t)
            mapThrowableToError(t)
        }
    }

    fun clearChannelCache(channelUrlOrId: String? = null) {
        if (channelUrlOrId.isNullOrBlank()) {
            channelCache.evictAll()
            channelTabCache.evictAll()
            channelTabContinuations.evictAll()
        } else {
            val key = ChannelDetailUtils.canonicalChannelKey(channelUrlOrId)
            channelCache.remove(key)
            channelTabCache.snapshot().keys.filter { it.startsWith("$key|") }.forEach { channelTabCache.remove(it) }
        }
    }

    suspend fun fetchChannelProfile(channelUrl: String, fallbackName: String): OnlineResult<OnlineVideoChannel> {
        return when (val detailed = fetchChannelProfileDetailed(channelUrl, fallbackName, forceRefresh = false)) {
            is OnlineResult.Success -> OnlineResult.Success(detailed.value.channel)
            is OnlineResult.Error -> detailed
        }
    }

    suspend fun fetchChannelProfileDetailed(
        channelUrl: String,
        fallbackName: String,
        forceRefresh: Boolean = false
    ): OnlineResult<ChannelProfileResult> =
        withContext(Dispatchers.IO) {
            val lookupKey = ChannelDetailUtils.canonicalChannelKey(channelUrl)
            val now = System.currentTimeMillis()
            if (!forceRefresh) {
                channelCache.get(lookupKey)?.takeIf { now - it.fetchedAtMs < CHANNEL_TTL_MS }?.let { cached ->
                    val defaultTab = cached.channel.availableTabs.firstOrNull() ?: ChannelTabType.ABOUT
                    val cachedPage = channelTabCache.get("${ChannelDetailUtils.canonicalChannelKey(cached.channel.channelUrl, cached.channel.channelId)}|${defaultTab.id}")
                        ?.takeIf { now - it.fetchedAtMs < CHANNEL_TTL_MS }
                        ?.page
                    if (cachedPage != null || defaultTab == ChannelTabType.ABOUT) {
                        return@withContext OnlineResult.Success(
                            ChannelProfileResult(
                                channel = cached.channel,
                                initialTab = defaultTab,
                                initialTabPage = cachedPage
                            )
                        )
                    }
                }
            }

            if (!connectivityMonitor.isCurrentlyOnline()) {
                return@withContext OnlineResult.Error(
                    kind = OnlineErrorKind.OFFLINE,
                    message = "Connect to the internet to view this channel.",
                    isRetryable = true
                )
            }
            try {
                NewPipeInitializer.ensureInitialized(null)
                val service = ServiceList.YouTube
                val info = ChannelInfo.getInfo(service, channelUrl)
                coroutineContext.ensureActive()

                val resolvedUrl = info.url?.takeIf { it.isNotBlank() } ?: channelUrl
                val channelId = info.id?.takeIf { it.isNotBlank() } ?: extractChannelIdFromUrl(resolvedUrl)
                val channelName = info.name?.takeIf { it.isNotBlank() } ?: fallbackName
                val avatar = info.avatars.orEmpty().maxByOrNull { it.width }?.url
                val banner = info.banners.orEmpty().maxByOrNull { it.width }?.url
                val description = info.description?.takeIf { it.isNotBlank() }
                val handle = ChannelDetailUtils.extractHandleFromUrl(resolvedUrl)
                    ?: ChannelDetailUtils.extractHandleFromUrl(channelUrl)
                val tags = info.tags.orEmpty().map { it.trim() }.filter { it.isNotBlank() }.distinct().take(12)
                val externalLinks = ChannelDetailUtils.extractExternalLinks(description)

                // Map real extractor-provided channel tabs — never fabricate tabs not supported by the channel.
                val tabHandlers = linkedMapOf<ChannelTabType, ListLinkHandler>()
                val rawTabs = info.tabs.orEmpty()
                rawTabs.forEach { handler ->
                    val filters = handler.contentFilters.orEmpty().map { it.lowercase(Locale.US) }
                    when {
                        filters.any { it == "videos" || it.contains("video") } ->
                            tabHandlers.putIfAbsent(ChannelTabType.VIDEOS, handler)
                        filters.any { it == "shorts" || it.contains("short") } ->
                            tabHandlers.putIfAbsent(ChannelTabType.SHORTS, handler)
                        filters.any { it == "playlists" || it.contains("playlist") } ->
                            tabHandlers.putIfAbsent(ChannelTabType.PLAYLISTS, handler)
                    }
                }
                if (ChannelTabType.VIDEOS !in tabHandlers && rawTabs.isNotEmpty()) {
                    // Preserve primary uploads tab when NewPipe returns an unnamed default videos handler.
                    val fallbackTab = rawTabs.firstOrNull { tab ->
                        tabHandlers.values.none { it == tab }
                    }
                    if (fallbackTab != null) {
                        tabHandlers[ChannelTabType.VIDEOS] = fallbackTab
                    }
                }

                val availableTabs = ChannelDetailUtils.resolveAvailableTabs(tabHandlers.keys)
                val initialTab = availableTabs.firstOrNull() ?: ChannelTabType.ABOUT
                val canonicalKey = ChannelDetailUtils.canonicalChannelKey(resolvedUrl, channelId)

                val initialTabPage = tabHandlers[initialTab]?.let { handler ->
                    runCatching {
                        extractChannelTabPage(
                            service = service,
                            channelKey = canonicalKey,
                            channelName = channelName,
                            channelUrl = resolvedUrl,
                            channelAvatarUrl = avatar,
                            tabType = initialTab,
                            tabHandler = handler
                        )
                    }.getOrNull()
                }

                val initialVideos = if (initialTab == ChannelTabType.VIDEOS) {
                    initialTabPage?.videos.orEmpty()
                } else {
                    emptyList()
                }

                val channel = OnlineVideoChannel(
                    channelId = channelId,
                    stableId = OnlineTrackMapper.computeStableId("youtube_channel", resolvedUrl),
                    channelUrl = resolvedUrl,
                    name = channelName,
                    avatarUrl = avatar,
                    bannerUrl = banner,
                    subscriberCount = info.subscriberCount.takeIf { it >= 0L },
                    description = description,
                    isVerified = info.isVerified,
                    videos = initialVideos,
                    handle = handle,
                    tags = tags,
                    externalLinks = externalLinks,
                    availableTabs = availableTabs
                )

                val bundle = CachedChannelBundle(
                    fetchedAtMs = System.currentTimeMillis(),
                    channel = channel,
                    tabHandlers = tabHandlers
                )
                channelCache.put(canonicalKey, bundle)
                channelCache.put(lookupKey, bundle)
                if (initialTabPage != null) {
                    channelTabCache.put(
                        "$canonicalKey|${initialTab.id}",
                        CachedChannelTabPage(
                            fetchedAtMs = System.currentTimeMillis(),
                            page = initialTabPage
                        )
                    )
                }

                OnlineResult.Success(
                    ChannelProfileResult(
                        channel = channel,
                        initialTab = initialTab,
                        initialTabPage = initialTabPage
                    )
                )
            } catch (ce: CancellationException) {
                throw ce
            } catch (t: Throwable) {
                Log.w(TAG, "fetchChannelProfileDetailed failed for $channelUrl", t)
                mapThrowableToError(t)
            }
        }

    suspend fun fetchChannelTab(
        channelUrl: String,
        channelId: String,
        tabType: ChannelTabType,
        forceRefresh: Boolean = false
    ): OnlineResult<ChannelTabPageResult> = withContext(Dispatchers.IO) {
        if (tabType == ChannelTabType.ABOUT) {
            return@withContext OnlineResult.Success(ChannelTabPageResult(tab = ChannelTabType.ABOUT))
        }
        val canonicalKey = ChannelDetailUtils.canonicalChannelKey(channelUrl, channelId)
        val cacheKey = "$canonicalKey|${tabType.id}"
        val now = System.currentTimeMillis()
        if (!forceRefresh) {
            channelTabCache.get(cacheKey)?.takeIf { now - it.fetchedAtMs < CHANNEL_TTL_MS }?.let { cached ->
                return@withContext OnlineResult.Success(cached.page)
            }
        }
        if (!connectivityMonitor.isCurrentlyOnline()) {
            return@withContext OnlineResult.Error(
                kind = OnlineErrorKind.OFFLINE,
                message = "Connect to the internet to load ${tabType.label.lowercase(Locale.US)}.",
                isRetryable = true
            )
        }
        try {
            NewPipeInitializer.ensureInitialized(null)
            val service = ServiceList.YouTube
            var bundle = channelCache.get(canonicalKey)
                ?: channelCache.get(ChannelDetailUtils.canonicalChannelKey(channelUrl))
            if (bundle == null) {
                when (val reload = fetchChannelProfileDetailed(channelUrl, "Channel", forceRefresh = true)) {
                    is OnlineResult.Success -> {
                        bundle = channelCache.get(canonicalKey)
                            ?: channelCache.get(ChannelDetailUtils.canonicalChannelKey(reload.value.channel.channelUrl, reload.value.channel.channelId))
                    }
                    is OnlineResult.Error -> return@withContext reload
                }
            }
            val resolvedBundle = bundle ?: return@withContext OnlineResult.Error(
                kind = OnlineErrorKind.UNSUPPORTED,
                message = "Channel tab metadata is currently unavailable.",
                isRetryable = true
            )
            val tabHandler = resolvedBundle.tabHandlers[tabType] ?: return@withContext OnlineResult.Error(
                kind = OnlineErrorKind.UNSUPPORTED,
                message = "This channel does not provide a ${tabType.label.lowercase(Locale.US)} tab.",
                isRetryable = false
            )
            val page = extractChannelTabPage(
                service = service,
                channelKey = canonicalKey,
                channelName = resolvedBundle.channel.name,
                channelUrl = resolvedBundle.channel.channelUrl,
                channelAvatarUrl = resolvedBundle.channel.avatarUrl,
                tabType = tabType,
                tabHandler = tabHandler
            )
            channelTabCache.put(
                cacheKey,
                CachedChannelTabPage(
                    fetchedAtMs = System.currentTimeMillis(),
                    page = page
                )
            )
            OnlineResult.Success(page)
        } catch (ce: CancellationException) {
            throw ce
        } catch (t: Throwable) {
            Log.w(TAG, "fetchChannelTab failed for $channelId / $tabType", t)
            mapThrowableToError(t)
        }
    }

    suspend fun loadMoreChannelTab(nextPageToken: String): OnlineResult<ChannelTabPageResult> =
        withContext(Dispatchers.IO) {
            if (!connectivityMonitor.isCurrentlyOnline()) {
                return@withContext OnlineResult.Error(
                    kind = OnlineErrorKind.OFFLINE,
                    message = "Connect to the internet to load more items.",
                    isRetryable = true
                )
            }
            val continuation = channelTabContinuations.get(nextPageToken)
                ?: return@withContext OnlineResult.Success(
                    ChannelTabPageResult(tab = ChannelTabType.VIDEOS, nextPageToken = null)
                )
            try {
                NewPipeInitializer.ensureInitialized(null)
                val service = ServiceList.YouTube
                val more = ChannelTabInfo.getMoreItems(service, continuation.tabHandler, continuation.nextPage)
                coroutineContext.ensureActive()

                val nextToken = more.nextPage?.takeIf { Page.isValid(it) }?.let { nextPage ->
                    val token = "chtab_${UUID.randomUUID()}"
                    channelTabContinuations.put(token, continuation.copy(nextPage = nextPage))
                    token
                }
                channelTabContinuations.remove(nextPageToken)

                val mapped = mapChannelTabItems(
                    items = more.items.orEmpty(),
                    tabType = continuation.tabType,
                    channelName = continuation.channelName,
                    channelUrl = continuation.channelUrl,
                    channelAvatarUrl = continuation.channelAvatarUrl,
                    nextPageToken = nextToken
                )
                OnlineResult.Success(mapped)
            } catch (ce: CancellationException) {
                throw ce
            } catch (t: Throwable) {
                Log.w(TAG, "loadMoreChannelTab failed for ${continuation.channelKey} / ${continuation.tabType}", t)
                mapThrowableToError(t)
            }
        }

    private fun extractChannelTabPage(
        service: org.schabi.newpipe.extractor.StreamingService,
        channelKey: String,
        channelName: String,
        channelUrl: String,
        channelAvatarUrl: String?,
        tabType: ChannelTabType,
        tabHandler: ListLinkHandler
    ): ChannelTabPageResult {
        val tabInfo = ChannelTabInfo.getInfo(service, tabHandler)
        val nextToken = tabInfo.nextPage?.takeIf { Page.isValid(it) }?.let { page ->
            val token = "chtab_${UUID.randomUUID()}"
            channelTabContinuations.put(
                token,
                ChannelTabContinuation(
                    channelKey = channelKey,
                    channelName = channelName,
                    channelUrl = channelUrl,
                    channelAvatarUrl = channelAvatarUrl,
                    tabType = tabType,
                    tabHandler = tabHandler,
                    nextPage = page
                )
            )
            token
        }
        return mapChannelTabItems(
            items = tabInfo.relatedItems.orEmpty(),
            tabType = tabType,
            channelName = channelName,
            channelUrl = channelUrl,
            channelAvatarUrl = channelAvatarUrl,
            nextPageToken = nextToken
        )
    }

    private fun mapChannelTabItems(
        items: List<InfoItem>,
        tabType: ChannelTabType,
        channelName: String,
        channelUrl: String,
        channelAvatarUrl: String?,
        nextPageToken: String?
    ): ChannelTabPageResult {
        if (tabType == ChannelTabType.PLAYLISTS) {
            val playlists = items.filterIsInstance<PlaylistInfoItem>()
                .mapNotNull { item ->
                    val url = item.url?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                    OnlineVideoPlaylistSummary(
                        externalUrl = url,
                        name = item.name?.takeIf { it.isNotBlank() } ?: "Playlist",
                        channelName = item.uploaderName?.takeIf { it.isNotBlank() } ?: channelName,
                        thumbnailUrl = item.thumbnails.orEmpty().maxByOrNull { it.width }?.url,
                        videoCount = item.streamCount.takeIf { it >= 0L } ?: 0L
                    )
                }
                .distinctBy { it.externalUrl ?: it.name }
            return ChannelTabPageResult(
                tab = tabType,
                playlists = playlists,
                nextPageToken = nextPageToken
            )
        }

        val isShortsTab = tabType == ChannelTabType.SHORTS
        val videos = items.filterIsInstance<StreamInfoItem>()
            .mapNotNull { item ->
                mapStreamInfoItem(item)?.let { mapped ->
                    mapped.copy(
                        channelName = mapped.channelName.takeIf { it != "Unknown Channel" } ?: channelName,
                        channelUrl = mapped.channelUrl ?: channelUrl,
                        channelAvatarUrl = mapped.channelAvatarUrl ?: channelAvatarUrl,
                        isShort = mapped.isShort || isShortsTab
                    )
                }
            }
            .distinctBy { it.videoId }
        return ChannelTabPageResult(
            tab = tabType,
            videos = videos,
            nextPageToken = nextPageToken
        )
    }

    suspend fun fetchExternalPlaylist(playlistUrl: String, fallbackTitle: String): OnlineResult<OnlineVideoPlaylistSummary> {
        return when (val detailed = fetchExternalPlaylistDetailed(playlistUrl, fallbackTitle, forceRefresh = false)) {
            is OnlineResult.Success -> OnlineResult.Success(detailed.value.playlist)
            is OnlineResult.Error -> detailed
        }
    }

    suspend fun fetchExternalPlaylistDetailed(
        playlistUrl: String,
        fallbackTitle: String,
        forceRefresh: Boolean = false
    ): OnlineResult<ExternalPlaylistPageResult> =
        withContext(Dispatchers.IO) {
            val cacheKey = playlistUrl.trim()
            val now = System.currentTimeMillis()
            if (!forceRefresh) {
                externalPlaylistCache.get(cacheKey)?.takeIf { now - it.fetchedAtMs < CHANNEL_TTL_MS }?.let { cached ->
                    return@withContext OnlineResult.Success(cached.page)
                }
            }
            if (!connectivityMonitor.isCurrentlyOnline()) {
                return@withContext OnlineResult.Error(
                    kind = OnlineErrorKind.OFFLINE,
                    message = "Connect to the internet to load this playlist.",
                    isRetryable = true
                )
            }
            try {
                NewPipeInitializer.ensureInitialized(null)
                val service = ServiceList.YouTube
                val info = PlaylistInfo.getInfo(service, playlistUrl)
                coroutineContext.ensureActive()
                val resolvedUrl = info.url?.takeIf { it.isNotBlank() } ?: playlistUrl
                val videos = info.relatedItems.orEmpty()
                    .filterIsInstance<StreamInfoItem>()
                    .mapNotNull { mapStreamInfoItem(it) }
                    .distinctBy { it.videoId }
                val thumb = info.thumbnails.orEmpty().maxByOrNull { it.width }?.url
                    ?: videos.firstOrNull()?.thumbnailUrl
                val nextToken = info.nextPage?.takeIf { Page.isValid(it) }?.let { page ->
                    val token = "pl_${UUID.randomUUID()}"
                    externalPlaylistContinuations.put(
                        token,
                        ExternalPlaylistContinuation(
                            playlistUrl = resolvedUrl,
                            nextPage = page
                        )
                    )
                    token
                }
                val pageResult = ExternalPlaylistPageResult(
                    playlist = OnlineVideoPlaylistSummary(
                        externalUrl = resolvedUrl,
                        name = info.name?.takeIf { it.isNotBlank() } ?: fallbackTitle,
                        channelName = info.uploaderName?.takeIf { it.isNotBlank() },
                        thumbnailUrl = thumb,
                        videoCount = info.streamCount.takeIf { it >= 0L } ?: videos.size.toLong(),
                        videos = videos
                    ),
                    nextPageToken = nextToken
                )
                externalPlaylistCache.put(
                    cacheKey,
                    CachedExternalPlaylist(
                        fetchedAtMs = System.currentTimeMillis(),
                        page = pageResult
                    )
                )
                OnlineResult.Success(pageResult)
            } catch (ce: CancellationException) {
                throw ce
            } catch (t: Throwable) {
                mapThrowableToError(t)
            }
        }

    suspend fun loadMoreExternalPlaylist(nextPageToken: String): OnlineResult<Pair<List<OnlineVideoItem>, String?>> =
        withContext(Dispatchers.IO) {
            if (!connectivityMonitor.isCurrentlyOnline()) {
                return@withContext OnlineResult.Error(
                    kind = OnlineErrorKind.OFFLINE,
                    message = "Connect to the internet to load more playlist videos.",
                    isRetryable = true
                )
            }
            val continuation = externalPlaylistContinuations.get(nextPageToken)
                ?: return@withContext OnlineResult.Success(emptyList<OnlineVideoItem>() to null)
            try {
                NewPipeInitializer.ensureInitialized(null)
                val service = ServiceList.YouTube
                val more = PlaylistInfo.getMoreItems(service, continuation.playlistUrl, continuation.nextPage)
                coroutineContext.ensureActive()
                val videos = more.items.orEmpty()
                    .filterIsInstance<StreamInfoItem>()
                    .mapNotNull { mapStreamInfoItem(it) }
                    .distinctBy { it.videoId }
                val nextToken = more.nextPage?.takeIf { Page.isValid(it) }?.let { page ->
                    val token = "pl_${UUID.randomUUID()}"
                    externalPlaylistContinuations.put(token, continuation.copy(nextPage = page))
                    token
                }
                externalPlaylistContinuations.remove(nextPageToken)
                OnlineResult.Success(videos to nextToken)
            } catch (ce: CancellationException) {
                throw ce
            } catch (t: Throwable) {
                mapThrowableToError(t)
            }
        }

    private fun partitionSearchItems(items: List<InfoItem>): VideoSearchPage {
        val videos = mutableListOf<OnlineVideoItem>()
        val channels = mutableListOf<OnlineVideoChannel>()
        val playlists = mutableListOf<OnlineVideoPlaylistSummary>()

        items.forEach { item ->
            when (item) {
                is StreamInfoItem -> mapStreamInfoItem(item)?.let { videos.add(it) }
                is ChannelInfoItem -> {
                    val url = item.url?.takeIf { it.isNotBlank() } ?: return@forEach
                    val channelId = extractChannelIdFromUrl(url)
                    channels.add(
                        OnlineVideoChannel(
                            channelId = channelId,
                            stableId = OnlineTrackMapper.computeStableId("youtube_channel", url),
                            channelUrl = url,
                            name = item.name?.takeIf { it.isNotBlank() } ?: "Channel",
                            avatarUrl = item.thumbnails.orEmpty().maxByOrNull { it.width }?.url,
                            subscriberCount = item.subscriberCount.takeIf { it >= 0L },
                            description = item.description?.takeIf { it.isNotBlank() },
                            isVerified = item.isVerified
                        )
                    )
                }
                is PlaylistInfoItem -> {
                    val url = item.url?.takeIf { it.isNotBlank() } ?: return@forEach
                    playlists.add(
                        OnlineVideoPlaylistSummary(
                            externalUrl = url,
                            name = item.name?.takeIf { it.isNotBlank() } ?: "Playlist",
                            channelName = item.uploaderName?.takeIf { it.isNotBlank() },
                            thumbnailUrl = item.thumbnails.orEmpty().maxByOrNull { it.width }?.url,
                            videoCount = item.streamCount.takeIf { it >= 0L } ?: 0L
                        )
                    )
                }
            }
        }
        return VideoSearchPage(
            videos = videos.distinctBy { it.videoId },
            channels = channels.distinctBy { it.channelId },
            playlists = playlists.distinctBy { it.externalUrl ?: it.name },
            nextPageToken = null
        )
    }

    private fun mapStreamInfoItem(item: StreamInfoItem): OnlineVideoItem? {
        val url = item.url?.takeIf { it.isNotBlank() } ?: return null
        val title = item.name?.takeIf { it.isNotBlank() } ?: return null
        val videoId = extractVideoIdFromUrl(url)
        val thumb = item.thumbnails.orEmpty().maxByOrNull { it.width }?.url
        val avatar = item.uploaderAvatars.orEmpty().maxByOrNull { it.width }?.url
        return OnlineVideoItem(
            videoId = videoId,
            stableId = OnlineTrackMapper.computeStableId("youtube_video", url),
            contentUrl = url,
            title = title,
            channelName = item.uploaderName?.takeIf { it.isNotBlank() } ?: "Unknown Channel",
            channelUrl = item.uploaderUrl?.takeIf { it.isNotBlank() },
            channelAvatarUrl = avatar,
            thumbnailUrl = thumb,
            durationSeconds = item.duration.coerceAtLeast(0L),
            viewCount = item.viewCount.takeIf { it >= 0L },
            uploadDateText = item.textualUploadDate?.takeIf { it.isNotBlank() },
            isLive = item.streamType?.name?.contains("LIVE", ignoreCase = true) == true,
            isShort = item.isShortFormContent
        )
    }

    private fun parseResolutionHeight(resolution: String?): Int {
        if (resolution.isNullOrBlank()) return 0
        return Regex("(\\d{3,4})p", RegexOption.IGNORE_CASE)
            .find(resolution)?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 0
    }

    private fun computeExpiry(urls: List<String>, extractedAtMs: Long): Long {
        val fallback = extractedAtMs + STREAM_TTL_MS
        val earliestSigned = urls.asSequence()
            .mapNotNull { u -> SIGNED_URL_EXPIRE_REGEX.find(u)?.groupValues?.getOrNull(1)?.toLongOrNull()?.times(1000L) }
            .filter { it > extractedAtMs }
            .minOrNull()
        return if (earliestSigned != null) {
            minOf(fallback, (earliestSigned - 45_000L).coerceAtLeast(extractedAtMs + 60_000L))
        } else {
            fallback
        }
    }

    private fun mapThrowableToError(t: Throwable): OnlineResult.Error {
        val msg = t.message.orEmpty()
        return when {
            t is SignInConfirmNotBotException || msg.contains("429") -> OnlineResult.Error(
                kind = OnlineErrorKind.RATE_LIMITED_OR_CAPTCHA,
                message = "Provider temporarily rate-limited requests (HTTP 429 / verification). Retry shortly or open in official app.",
                isRetryable = true
            )
            msg.contains("403") -> OnlineResult.Error(
                kind = OnlineErrorKind.STREAM_EXPIRED,
                message = "Video stream access returned HTTP 403. Try refreshing or selecting another resolution.",
                isRetryable = true
            )
            t is GeographicRestrictionException -> OnlineResult.Error(
                kind = OnlineErrorKind.AGE_OR_GEO_RESTRICTED,
                message = "This video is restricted in your current region.",
                isRetryable = false
            )
            t is AgeRestrictedContentException || t is PaidContentException -> OnlineResult.Error(
                kind = OnlineErrorKind.AGE_OR_GEO_RESTRICTED,
                message = "This video requires provider account verification, age confirmation, or paid membership and cannot be bypassed.",
                isRetryable = false
            )
            t is ContentNotAvailableException -> OnlineResult.Error(
                kind = OnlineErrorKind.EXTRACTION_FAILED,
                message = t.message?.takeIf { it.isNotBlank() } ?: "This video is currently unavailable.",
                isRetryable = false
            )
            t is IOException -> OnlineResult.Error(
                kind = OnlineErrorKind.TIMEOUT,
                message = "Network error while loading online video data. Check your connection and retry.",
                isRetryable = true
            )
            else -> OnlineResult.Error(
                kind = OnlineErrorKind.EXTRACTION_FAILED,
                message = t.message?.takeIf { it.isNotBlank() } ?: "Unable to extract video data right now.",
                isRetryable = true
            )
        }
    }
}
