package data.online

import android.util.Log
import android.util.LruCache
import domain.online.OnlineErrorKind
import domain.online.OnlineItemKind
import domain.online.OnlineMusicProvider
import domain.online.OnlineResult
import domain.online.OnlineSearchFilter
import domain.online.OnlineSearchPageResult
import domain.online.OnlineTrack
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.Page
import org.schabi.newpipe.extractor.exceptions.AgeRestrictedContentException
import org.schabi.newpipe.extractor.exceptions.ContentNotAvailableException
import org.schabi.newpipe.extractor.exceptions.ExtractionException
import org.schabi.newpipe.extractor.exceptions.GeographicRestrictionException
import org.schabi.newpipe.extractor.exceptions.PaidContentException
import org.schabi.newpipe.extractor.exceptions.ParsingException
import org.schabi.newpipe.extractor.exceptions.PrivateContentException
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException
import org.schabi.newpipe.extractor.exceptions.SignInConfirmNotBotException
import org.schabi.newpipe.extractor.exceptions.UnsupportedContentInCountryException
import org.schabi.newpipe.extractor.exceptions.YoutubeMusicPremiumContentException
import org.schabi.newpipe.extractor.linkhandler.SearchQueryHandler
import org.schabi.newpipe.extractor.search.SearchInfo
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.UUID

/**
 * Executes real online searches, suggestions, and pagination off the main thread via NewPipeExtractor.
 */
class OnlineSearchDataSource(
    private val connectivityMonitor: NetworkConnectivityMonitor
) {

    private data class ContinuationState(
        val serviceId: Int,
        val providerKey: String,
        val providerName: String,
        val query: String,
        val filter: OnlineSearchFilter,
        val queryHandler: SearchQueryHandler,
        val nextPage: Page
    )

    private val continuationCache = LruCache<String, ContinuationState>(30)

    suspend fun search(
        query: String,
        provider: OnlineMusicProvider,
        filter: OnlineSearchFilter
    ): OnlineResult<OnlineSearchPageResult> = withContext(Dispatchers.IO) {
        val cleanQuery = query.trim()
        if (cleanQuery.isBlank()) {
            return@withContext OnlineResult.Success(
                OnlineSearchPageResult(
                    query = "",
                    providerKey = provider.key,
                    filter = filter,
                    items = emptyList(),
                    nextPageToken = null,
                    hasNextPage = false
                )
            )
        }
        if (!connectivityMonitor.isCurrentlyOnline()) {
            return@withContext OnlineResult.Error(
                kind = OnlineErrorKind.OFFLINE,
                message = "No internet connection. Connect to Wi-Fi or mobile data to search ${provider.displayName}.",
                isRetryable = true
            )
        }

        try {
            runWithTransientRetry(
                providerName = provider.displayName,
                operation = "search",
                requestInfo = "filter=${filter.name}"
            ) {
                NewPipeInitializer.ensureInitialized(null)
                val service = NewPipeInitializer.getServiceForProviderKey(provider.key)
                val contentFilters = NewPipeInitializer.resolveNewPipeContentFilters(
                    providerKey = provider.key,
                    filter = filter,
                    service = service
                )
                val queryHandler = service.searchQHFactory.fromQuery(cleanQuery, contentFilters, "")
                val searchInfo = SearchInfo.getInfo(service, queryHandler)

                val mapped = mapSearchItems(
                    items = searchInfo.relatedItems.orEmpty(),
                    serviceId = provider.serviceId,
                    providerKey = provider.key,
                    providerName = provider.displayName,
                    filter = filter
                )

                val nextPage = searchInfo.nextPage
                val token = if (searchInfo.hasNextPage() && nextPage != null) {
                    UUID.randomUUID().toString().also { key ->
                        continuationCache.put(
                            key,
                            ContinuationState(
                                serviceId = provider.serviceId,
                                providerKey = provider.key,
                                providerName = provider.displayName,
                                query = cleanQuery,
                                filter = filter,
                                queryHandler = queryHandler,
                                nextPage = nextPage
                            )
                        )
                    }
                } else {
                    null
                }

                OnlineResult.Success(
                    OnlineSearchPageResult(
                        query = cleanQuery,
                        providerKey = provider.key,
                        filter = filter,
                        items = mapped,
                        nextPageToken = token,
                        hasNextPage = token != null,
                        correctedSuggestion = searchInfo.searchSuggestion?.takeIf { it.isNotBlank() }
                    )
                )
            }
        } catch (ce: CancellationException) {
            throw ce
        } catch (t: Throwable) {
            mapThrowableToError(
                t = t,
                providerName = provider.displayName,
                operation = "search",
                requestType = filter.name
            )
        }
    }

    suspend fun loadMore(previousToken: String): OnlineResult<OnlineSearchPageResult> = withContext(Dispatchers.IO) {
        val state = continuationCache.get(previousToken)
            ?: return@withContext OnlineResult.Error(
                kind = OnlineErrorKind.EXTRACTION_FAILED,
                message = "Search page expired. Run search again.",
                isRetryable = true
            )

        if (!connectivityMonitor.isCurrentlyOnline()) {
            return@withContext OnlineResult.Error(
                kind = OnlineErrorKind.OFFLINE,
                message = "You appear to be offline.",
                isRetryable = true
            )
        }

        try {
            runWithTransientRetry(
                providerName = state.providerName,
                operation = "search_load_more",
                requestInfo = "filter=${state.filter.name}"
            ) {
                NewPipeInitializer.ensureInitialized(null)
                val service = NewPipeInitializer.getServiceForProviderKey(state.providerKey)
                val more = SearchInfo.getMoreItems(service, state.queryHandler, state.nextPage)
                val mapped = mapSearchItems(
                    items = more.items.orEmpty(),
                    serviceId = state.serviceId,
                    providerKey = state.providerKey,
                    providerName = state.providerName,
                    filter = state.filter
                )

                val nextPage = more.nextPage
                val nextToken = if (more.hasNextPage() && nextPage != null) {
                    UUID.randomUUID().toString().also { key ->
                        continuationCache.put(key, state.copy(nextPage = nextPage))
                    }
                } else {
                    null
                }

                OnlineResult.Success(
                    OnlineSearchPageResult(
                        query = state.query,
                        providerKey = state.providerKey,
                        filter = state.filter,
                        items = mapped,
                        nextPageToken = nextToken,
                        hasNextPage = nextToken != null
                    )
                )
            }
        } catch (ce: CancellationException) {
            throw ce
        } catch (t: Throwable) {
            mapThrowableToError(
                t = t,
                providerName = state.providerName,
                operation = "search_load_more",
                requestType = state.filter.name
            )
        }
    }

    suspend fun getSuggestions(query: String, provider: OnlineMusicProvider): List<String> =
        withContext(Dispatchers.IO) {
            val clean = query.trim()
            if (clean.length < 2 || !provider.supportsSuggestions || !connectivityMonitor.isCurrentlyOnline()) {
                return@withContext emptyList()
            }
            try {
                NewPipeInitializer.ensureInitialized(null)
                val service = NewPipeInitializer.getServiceForProviderKey(provider.key)
                service.getSuggestionExtractor()
                    ?.suggestionList(clean)
                    .orEmpty()
                    .filter { it.isNotBlank() }
                    .distinct()
                    .take(8)
            } catch (ce: CancellationException) {
                throw ce
            } catch (t: Throwable) {
                Log.w(TAG, "Suggestion lookup failed service=${provider.displayName} op=suggestions type=${t::class.java.simpleName}")
                emptyList()
            }
        }

    private fun mapSearchItems(
        items: List<org.schabi.newpipe.extractor.InfoItem>,
        serviceId: Int,
        providerKey: String,
        providerName: String,
        filter: OnlineSearchFilter
    ): List<OnlineTrack> {
        val raw = items.mapNotNull {
            OnlineTrackMapper.mapInfoItem(
                item = it,
                serviceId = serviceId,
                providerKey = providerKey,
                providerName = providerName
            )
        }
        return when (filter) {
            OnlineSearchFilter.SONGS, OnlineSearchFilter.VIDEOS ->
                raw.filter { it.kind == OnlineItemKind.TRACK }.ifEmpty { raw }
            OnlineSearchFilter.ARTISTS ->
                raw.filter { it.kind == OnlineItemKind.ARTIST }.ifEmpty { raw }
            OnlineSearchFilter.ALBUMS ->
                raw.filter { it.kind == OnlineItemKind.ALBUM || it.kind == OnlineItemKind.PLAYLIST }.ifEmpty { raw }
            OnlineSearchFilter.PLAYLISTS ->
                raw.filter { it.kind == OnlineItemKind.PLAYLIST || it.kind == OnlineItemKind.ALBUM }.ifEmpty { raw }
            OnlineSearchFilter.ALL -> raw
        }
    }

    companion object {
        private const val TAG = "OnlineSearchDataSource"

        fun isTransientFailure(t: Throwable): Boolean {
            return when (t) {
                is SocketTimeoutException -> true
                is HttpTransportException -> t.statusCode in 500..599
                is UnknownHostException -> false
                is ReCaptchaException,
                is SignInConfirmNotBotException -> false
                is ContentNotAvailableException,
                is AgeRestrictedContentException,
                is GeographicRestrictionException,
                is PaidContentException,
                is PrivateContentException,
                is YoutubeMusicPremiumContentException,
                is UnsupportedContentInCountryException -> false
                is IOException -> true
                else -> false
            }
        }

        suspend fun <T> runWithTransientRetry(
            providerName: String,
            operation: String,
            requestInfo: String = "",
            maxAttempts: Int = 3,
            initialBackoffMs: Long = 280L,
            block: suspend () -> T
        ): T {
            var attempt = 0
            var delayMs = initialBackoffMs
            while (true) {
                attempt++
                try {
                    return block()
                } catch (ce: CancellationException) {
                    throw ce
                } catch (t: Throwable) {
                    if (attempt >= maxAttempts || !isTransientFailure(t)) {
                        throw t
                    }
                    Log.w(
                        TAG,
                        "Transient failure on service=$providerName op=$operation ($requestInfo) attempt=$attempt/$maxAttempts: ${t::class.java.simpleName}"
                    )
                    delay(delayMs)
                    delayMs = (delayMs * 2L).coerceAtMost(2_000L)
                }
            }
        }

        fun mapThrowableToError(
            t: Throwable,
            providerName: String,
            operation: String = "extraction",
            requestType: String = ""
        ): OnlineResult.Error {
            val topFrame = t.stackTrace.firstOrNull()?.let { "${it.className.substringAfterLast('.')}:${it.lineNumber}" } ?: "unknown"
            val logMsg = "Extraction failure service=$providerName op=$operation req=$requestType exc=${t::class.java.simpleName} at=$topFrame msg=${t.message}"
            val isExpectedUpstreamRestriction = t is SignInConfirmNotBotException ||
                t is ReCaptchaException ||
                t is YoutubeMusicPremiumContentException ||
                t is GeographicRestrictionException ||
                t is AgeRestrictedContentException ||
                t is PaidContentException ||
                t is PrivateContentException ||
                t is UnsupportedContentInCountryException
            if (isExpectedUpstreamRestriction) {
                Log.w(TAG, logMsg)
            } else {
                Log.e(TAG, logMsg)
            }

            return when (t) {
                is ReCaptchaException -> OnlineResult.Error(
                    kind = OnlineErrorKind.RATE_LIMITED_OR_CAPTCHA,
                    message = "$providerName temporarily rate-limited requests (HTTP 429). Try again shortly or switch provider.",
                    cause = t,
                    isRetryable = true
                )
                is SignInConfirmNotBotException -> OnlineResult.Error(
                    kind = OnlineErrorKind.RATE_LIMITED_OR_CAPTCHA,
                    message = "$providerName blocked anonymous playback on this IP (anti-bot check). Try SoundCloud or switch network/VPN and retry.",
                    cause = t,
                    isRetryable = true
                )
                is UnsupportedContentInCountryException -> OnlineResult.Error(
                    kind = OnlineErrorKind.UNSUPPORTED,
                    message = "$providerName charts/feed are not supported in your current region.",
                    cause = t,
                    isRetryable = false
                )
                is UnsupportedOperationException -> OnlineResult.Error(
                    kind = OnlineErrorKind.UNSUPPORTED,
                    message = "This operation is not supported by $providerName.",
                    cause = t,
                    isRetryable = false
                )
                is AgeRestrictedContentException -> OnlineResult.Error(
                    kind = OnlineErrorKind.AGE_OR_GEO_RESTRICTED,
                    message = "This track is age-restricted on $providerName and cannot be streamed anonymously.",
                    cause = t,
                    isRetryable = false
                )
                is GeographicRestrictionException -> OnlineResult.Error(
                    kind = OnlineErrorKind.AGE_OR_GEO_RESTRICTED,
                    message = "This content is not available in your region on $providerName.",
                    cause = t,
                    isRetryable = false
                )
                is YoutubeMusicPremiumContentException -> OnlineResult.Error(
                    kind = OnlineErrorKind.UNSUPPORTED,
                    message = "This track requires a YouTube Music Premium membership and cannot be streamed anonymously.",
                    cause = t,
                    isRetryable = false
                )
                is PaidContentException, is PrivateContentException -> OnlineResult.Error(
                    kind = OnlineErrorKind.UNSUPPORTED,
                    message = "This content requires a paid membership or is private on $providerName.",
                    cause = t,
                    isRetryable = false
                )
                is ContentNotAvailableException -> OnlineResult.Error(
                    kind = OnlineErrorKind.UNSUPPORTED,
                    message = "This content is unavailable on $providerName (${t.message ?: "removed"}).",
                    cause = t,
                    isRetryable = false
                )
                is HttpTransportException -> {
                    val isForbidden = t.statusCode == 403
                    OnlineResult.Error(
                        kind = if (isForbidden) OnlineErrorKind.UNSUPPORTED else OnlineErrorKind.TIMEOUT,
                        message = if (isForbidden) {
                            "$providerName blocked this request (HTTP 403). Try searching or switching provider."
                        } else {
                            "$providerName server error (HTTP ${t.statusCode}). Please try again."
                        },
                        cause = t,
                        isRetryable = !isForbidden
                    )
                }
                is SocketTimeoutException -> OnlineResult.Error(
                    kind = OnlineErrorKind.TIMEOUT,
                    message = "Connection to $providerName timed out. Tap retry to try again.",
                    cause = t,
                    isRetryable = true
                )
                is UnknownHostException -> OnlineResult.Error(
                    kind = OnlineErrorKind.OFFLINE,
                    message = "Cannot reach $providerName. Check your network connection.",
                    cause = t,
                    isRetryable = true
                )
                is IOException -> OnlineResult.Error(
                    kind = OnlineErrorKind.OFFLINE,
                    message = "Network error communicating with $providerName: ${t.message ?: "connection interrupted"}.",
                    cause = t,
                    isRetryable = true
                )
                is NullPointerException -> OnlineResult.Error(
                    kind = OnlineErrorKind.EXTRACTION_FAILED,
                    message = "$providerName returned an incomplete or updated response structure. Tap retry or use Search.",
                    cause = t,
                    isRetryable = true
                )
                is ParsingException -> OnlineResult.Error(
                    kind = OnlineErrorKind.EXTRACTION_FAILED,
                    message = "Could not parse $providerName response (${t.message?.take(80) ?: "format changed"}).",
                    cause = t,
                    isRetryable = true
                )
                is ExtractionException -> OnlineResult.Error(
                    kind = OnlineErrorKind.EXTRACTION_FAILED,
                    message = "Could not extract content from $providerName (${t.message ?: "extraction error"}).",
                    cause = t,
                    isRetryable = true
                )
                else -> OnlineResult.Error(
                    kind = OnlineErrorKind.EXTRACTION_FAILED,
                    message = t.message?.takeIf { it.isNotBlank() } ?: "Unexpected error from $providerName",
                    cause = t,
                    isRetryable = true
                )
            }
        }
    }
}
