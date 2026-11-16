package data.online

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.test.core.app.ApplicationProvider
import domain.online.DiscoverSectionState
import domain.online.DiscoverSectionType
import domain.online.OnlineErrorKind
import domain.online.OnlineItemKind
import domain.online.OnlineMusicProvider
import domain.online.OnlineResult
import domain.online.OnlineSearchFilter
import domain.online.OnlineTrack
import kotlinx.coroutines.test.runTest
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowNetworkCapabilities
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.downloader.Request
import org.schabi.newpipe.extractor.exceptions.ContentNotAvailableException
import org.schabi.newpipe.extractor.exceptions.ExtractionException
import org.schabi.newpipe.extractor.exceptions.ParsingException
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException
import org.schabi.newpipe.extractor.exceptions.UnsupportedContentInCountryException
import org.schabi.newpipe.extractor.kiosk.KioskInfo
import org.schabi.newpipe.extractor.localization.ContentCountry
import org.schabi.newpipe.extractor.localization.Localization
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.atomic.AtomicInteger

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class DiscoverAndErrorHandlingTest {

    @Test
    fun `rankPreferredKioskIds puts trending_music before Trending and excludes non-music YouTube kiosks`() {
        // Simulate worst-case HashMap iteration order where gaming/podcasts come first
        val shuffledYouTubeKiosks = listOf(
            "trending_podcasts_episodes",
            "trending_gaming",
            "trending_movies_and_shows",
            "live",
            "Trending",
            "trending_music"
        )

        val ranked = OnlineStreamResolver.rankPreferredKioskIds(
            availableKiosks = shuffledYouTubeKiosks,
            providerKey = OnlineMusicProvider.KEY_YOUTUBE_MUSIC
        )

        assertEquals("trending_music", ranked.first())
        assertTrue("Trending fallback should still be present", "Trending" in ranked)
        assertFalse("trending_gaming must not appear in music discovery candidates", "trending_gaming" in ranked)
        assertFalse("trending_podcasts_episodes must not appear in music discovery candidates", "trending_podcasts_episodes" in ranked)
        assertFalse("trending_movies_and_shows must not appear in music discovery candidates", "trending_movies_and_shows" in ranked)
    }

    @Test
    fun `unfetched DesktopBaseKiosk reproduces ChannelResponseData NPE while fetchKioskInfoSafely invokes fetchPage first`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        NewPipeInitializer.ensureInitialized(context)

        val kioskList = ServiceList.YouTube.kioskList
        val desktopExtractor = kioskList.getExtractorById("trending_gaming", null)

        // Confirm pre-fix failure mode: calling KioskInfo.getInfo(extractor) without fetchPage() throws NPE on ChannelResponseData.jsonResponse
        var npeMessage: String? = null
        try {
            KioskInfo.getInfo(desktopExtractor)
        } catch (npe: NullPointerException) {
            npeMessage = npe.message
        }
        assertNotNull("Expected ChannelResponseData NPE when fetchPage() is skipped", npeMessage)
        assertTrue(
            "Reproduced NPE should reference ChannelResponseData.jsonResponse",
            npeMessage!!.contains("ChannelResponseData") || npeMessage.contains("jsonResponse")
        )

        // Now verify fetchKioskInfoSafely invokes fetchPage() before getName() by supplying a minimal valid browse JSON via mocked OkHttp
        val mockBrowseJson = """
            {
              "responseContext": {"visitorData": "test_visitor_data"},
              "header": {"c4TabbedHeaderRenderer": {"title": "Trending"}},
              "contents": {
                "twoColumnBrowseResultsRenderer": {
                  "tabs": [{
                    "tabRenderer": {
                      "content": {
                        "richGridRenderer": {
                          "contents": []
                        }
                      }
                    }
                  }]
                }
              }
            }
        """.trimIndent()

        val mockClientVersionHtml = """
            <html><script>var ytInitialData = {"responseContext":{"serviceTrackingParams":[{"service":"CSI","params":[{"key":"cver","value":"2.20260101.00.00"}]}]}};</script></html>
        """.trimIndent()

        val previousDownloader = NewPipe.getDownloader()
        val previousLoc = NewPipe.getPreferredLocalization() ?: Localization("en", "IN")
        val previousCountry = NewPipe.getPreferredContentCountry() ?: ContentCountry("IN")
        try {
            val mockClient = OkHttpClient.Builder()
                .addInterceptor { chain ->
                    val reqUrl = chain.request().url.toString()
                    if (reqUrl.contains("/youtubei/v1/")) {
                        okhttp3.Response.Builder()
                            .request(chain.request())
                            .protocol(Protocol.HTTP_1_1)
                            .code(200)
                            .message("OK")
                            .header("Content-Type", "application/json; charset=utf-8")
                            .body(mockBrowseJson.toResponseBody("application/json; charset=utf-8".toMediaType()))
                            .build()
                    } else {
                        okhttp3.Response.Builder()
                            .request(chain.request())
                            .protocol(Protocol.HTTP_1_1)
                            .code(200)
                            .message("OK")
                            .header("Content-Type", "text/html; charset=utf-8")
                            .body(mockClientVersionHtml.toResponseBody("text/html; charset=utf-8".toMediaType()))
                            .build()
                    }
                }
                .build()
            NewPipe.init(NewPipeDownloader(mockClient), Localization("en", "US"), ContentCountry("US"))

            val safeExtractor = ServiceList.YouTube.kioskList.getExtractorById("trending_gaming", null)
            val info = OnlineStreamResolver.fetchKioskInfoSafely(safeExtractor)
            assertNotNull(info)
            assertEquals("Trending", info.name)
        } finally {
            NewPipe.init(previousDownloader, previousLoc, previousCountry)
        }
    }

    @Test
    fun `NewPipeDownloader preserves Content-Type, strips Accept-Encoding, injects Accept-Language, and handles HTTP 5xx and 403`() {
        var capturedContentType: String? = null
        var capturedAcceptEncoding: String? = null
        var capturedAcceptLanguage: String? = null
        var nextCode = 200
        var nextBody = "{\"ok\":true}"

        val client = OkHttpClient.Builder()
            .addInterceptor(Interceptor { chain ->
                val req = chain.request()
                capturedContentType = req.body?.contentType()?.toString()
                capturedAcceptEncoding = req.header("Accept-Encoding")
                capturedAcceptLanguage = req.header("Accept-Language")
                okhttp3.Response.Builder()
                    .request(req)
                    .protocol(Protocol.HTTP_1_1)
                    .code(nextCode)
                    .message(if (nextCode == 200) "OK" else "Error")
                    .body(nextBody.toResponseBody("application/json; charset=utf-8".toMediaType()))
                    .build()
            })
            .build()

        val downloader = NewPipeDownloader(client)
        val postReq = Request.Builder()
            .post("https://example.org/youtubei/v1/browse", "{\"browseId\":\"FEtrending\"}".toByteArray())
            .headers(
                mapOf(
                    "Content-Type" to listOf("application/json"),
                    "Accept-Encoding" to listOf("gzip")
                )
            )
            .localization(Localization("en", "US"))
            .build()

        val res = downloader.execute(postReq)
        assertEquals(200, res.responseCode())
        assertTrue("Content-Type must preserve application/json", capturedContentType?.contains("application/json") == true)
        assertEquals("Caller Accept-Encoding must be stripped so OkHttp handles compression transparently", null, capturedAcceptEncoding)
        assertEquals("en-US, en;q=0.9", capturedAcceptLanguage)

        // HTTP 503 with empty body must throw HttpTransportException
        nextCode = 503
        nextBody = ""
        var caught503: HttpTransportException? = null
        try {
            downloader.execute(postReq)
        } catch (e: HttpTransportException) {
            caught503 = e
        }
        assertNotNull("Empty HTTP 503 must raise HttpTransportException", caught503)
        assertEquals(503, caught503?.statusCode)

        // HTTP 403 returns response cleanly for extractor inspection without crashing Downloader
        nextCode = 403
        nextBody = "{\"error\":\"forbidden\"}"
        val res403 = downloader.execute(postReq)
        assertEquals(403, res403.responseCode())
    }

    @Test
    fun `mapThrowableToError classifies NPE, ParsingException, UnsupportedContentInCountryException, HTTP 403, 5xx, 429, and network failures safely`() {
        val npeErr = OnlineSearchDataSource.mapThrowableToError(
            NullPointerException("Attempt to read jsonResponse on null"),
            "YT Music",
            "discovery"
        )
        assertEquals(OnlineErrorKind.EXTRACTION_FAILED, npeErr.kind)
        assertTrue(npeErr.isRetryable)

        val parseErr = OnlineSearchDataSource.mapThrowableToError(ParsingException("Malformed JSON"), "YT Music")
        assertEquals(OnlineErrorKind.EXTRACTION_FAILED, parseErr.kind)
        assertTrue(parseErr.isRetryable)

        val countryErr = OnlineSearchDataSource.mapThrowableToError(
            UnsupportedContentInCountryException("Not available"),
            "YT Music"
        )
        assertEquals(OnlineErrorKind.UNSUPPORTED, countryErr.kind)
        assertFalse("Unsupported country charts must not be auto-retried blindly", countryErr.isRetryable)

        val forbiddenErr = OnlineSearchDataSource.mapThrowableToError(
            HttpTransportException(403, "Forbidden", "youtube.com"),
            "YT Music"
        )
        assertEquals(OnlineErrorKind.UNSUPPORTED, forbiddenErr.kind)
        assertFalse(forbiddenErr.isRetryable)

        val server502Err = OnlineSearchDataSource.mapThrowableToError(
            HttpTransportException(502, "Bad Gateway", "youtube.com"),
            "YT Music"
        )
        assertEquals(OnlineErrorKind.TIMEOUT, server502Err.kind)
        assertTrue(server502Err.isRetryable)

        assertEquals(
            OnlineErrorKind.RATE_LIMITED_OR_CAPTCHA,
            OnlineSearchDataSource.mapThrowableToError(ReCaptchaException("429", "https://youtube.com"), "YT Music").kind
        )
        assertEquals(
            OnlineErrorKind.TIMEOUT,
            OnlineSearchDataSource.mapThrowableToError(SocketTimeoutException("read timed out"), "YT Music").kind
        )
        assertEquals(
            OnlineErrorKind.OFFLINE,
            OnlineSearchDataSource.mapThrowableToError(UnknownHostException("dns failed"), "YT Music").kind
        )
        assertEquals(
            OnlineErrorKind.OFFLINE,
            OnlineSearchDataSource.mapThrowableToError(IOException("reset"), "YT Music").kind
        )
        assertEquals(
            OnlineErrorKind.EXTRACTION_FAILED,
            OnlineSearchDataSource.mapThrowableToError(ExtractionException("extract failed"), "YT Music").kind
        )
    }

    @Test
    fun `runWithTransientRetry retries temporary 5xx and IOException but does not retry permanent failures`() = runTest {
        val transientAttempts = AtomicInteger(0)
        val ok = OnlineSearchDataSource.runWithTransientRetry(
            providerName = "YT Music",
            operation = "test_transient",
            maxAttempts = 2,
            initialBackoffMs = 10L
        ) {
            if (transientAttempts.incrementAndGet() == 1) {
                throw HttpTransportException(503, "Unavailable", "www.youtube.com")
            }
            "recovered"
        }
        assertEquals("recovered", ok)
        assertEquals(2, transientAttempts.get())

        val permanentAttempts = AtomicInteger(0)
        var failedPermanent = false
        try {
            OnlineSearchDataSource.runWithTransientRetry(
                providerName = "YT Music",
                operation = "test_permanent",
                maxAttempts = 3,
                initialBackoffMs = 10L
            ) {
                permanentAttempts.incrementAndGet()
                throw ContentNotAvailableException("Removed")
            }
        } catch (_: ContentNotAvailableException) {
            failedPermanent = true
        }
        assertTrue(failedPermanent)
        assertEquals("Permanent errors must never be retried automatically", 1, permanentAttempts.get())
    }

    @Test
    fun `fetchCollectionDetails rejects invalid channel URLs safely and handles malformed channel responses without crashing`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        NewPipeInitializer.ensureInitialized(context)
        val resolver = OnlineStreamResolver(NetworkConnectivityMonitor(context))

        val blankArtist = OnlineTrack(
            stableId = -1001L,
            serviceId = 0,
            providerKey = OnlineMusicProvider.KEY_YOUTUBE_MUSIC,
            providerName = "YT Music",
            contentUrl = "",
            title = "Empty Channel",
            artist = "YT Music",
            kind = OnlineItemKind.ARTIST
        )
        val blankRes = resolver.fetchCollectionDetails(blankArtist)
        assertTrue(blankRes is OnlineResult.Error)
        assertEquals(OnlineErrorKind.UNSUPPORTED, (blankRes as OnlineResult.Error).kind)
        assertFalse(blankRes.isRetryable)

        val previousDownloader = NewPipe.getDownloader()
        val previousLoc = NewPipe.getPreferredLocalization() ?: Localization("en", "IN")
        val previousCountry = NewPipe.getPreferredContentCountry() ?: ContentCountry("IN")
        try {
            val malformedClient = OkHttpClient.Builder()
                .addInterceptor { chain ->
                    okhttp3.Response.Builder()
                        .request(chain.request())
                        .protocol(Protocol.HTTP_1_1)
                        .code(200)
                        .message("OK")
                        .body("{\"unexpected\":true}".toResponseBody("application/json".toMediaType()))
                        .build()
                }
                .build()
            NewPipe.init(NewPipeDownloader(malformedClient), Localization("en", "US"), ContentCountry("US"))

            val invalidChannel = blankArtist.copy(contentUrl = "https://www.youtube.com/channel/UC_invalid_test_id")
            val errRes = resolver.fetchCollectionDetails(invalidChannel)
            assertTrue("Malformed YouTube response must map to OnlineResult.Error cleanly", errRes is OnlineResult.Error)
        } finally {
            NewPipe.init(previousDownloader, previousLoc, previousCountry)
        }
    }

    @Test
    fun `Discover sections load independently and fall back cleanly when YouTube charts fail`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val activeNetwork = cm.activeNetwork
        if (activeNetwork != null) {
            val caps = ShadowNetworkCapabilities.newInstance()
            Shadows.shadowOf(caps).addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            Shadows.shadowOf(cm).setNetworkCapabilities(activeNetwork, caps)
        }

        NewPipeInitializer.ensureInitialized(context)
        val monitor = NetworkConnectivityMonitor(context)
        val resolver = OnlineStreamResolver(monitor)
        val searchDataSource = OnlineSearchDataSource(monitor)
        val ytMusic = NewPipeInitializer.discoverProviders()
            .first { it.key == OnlineMusicProvider.KEY_YOUTUBE_MUSIC }

        val previousDownloader = NewPipe.getDownloader()
        val previousLoc = NewPipe.getPreferredLocalization() ?: Localization("en", "IN")
        val previousCountry = NewPipe.getPreferredContentCountry() ?: ContentCountry("IN")
        try {
            val mockClientVersionHtml = """
                <html><script>var ytInitialData = {"responseContext":{"serviceTrackingParams":[{"service":"CSI","params":[{"key":"cver","value":"2.20260101.00.00"}]}]}};</script></html>
            """.trimIndent()

            val suggestionsPayload = "{\"contents\":[{\"padding\":\"${"x".repeat(600)}\"}]}"

            // Force charts/browse endpoints to return HTTP 500 while YT Music search endpoint succeeds with a valid song item
            val searchPayload = """
                {
                  "responseContext": {"visitorData": "v_test"},
                  "contents": {
                    "tabbedSearchResultsRenderer": {
                      "tabs": [{
                        "tabRenderer": {
                          "content": {
                            "sectionListRenderer": {
                              "contents": [{
                                "musicShelfRenderer": {
                                  "contents": [{
                                    "musicResponsiveListItemRenderer": {
                                      "playlistItemData": {"videoId": "dQw4w9WgXcQ"},
                                      "thumbnail": {
                                        "musicThumbnailRenderer": {
                                          "thumbnail": {
                                            "thumbnails": [{
                                              "url": "https://i.ytimg.com/vi/dQw4w9WgXcQ/hqdefault.jpg",
                                              "width": 480,
                                              "height": 360
                                            }]
                                          }
                                        }
                                      },
                                      "flexColumns": [
                                        {
                                          "musicResponsiveListItemFlexColumnRenderer": {
                                            "text": {"runs": [{"text": "Recovered Trending Track"}]}
                                          }
                                        },
                                        {
                                          "musicResponsiveListItemFlexColumnRenderer": {
                                            "text": {
                                              "runs": [
                                                {"text": "Verified Artist"},
                                                {"text": " • "},
                                                {"text": "Greatest Hits"},
                                                {"text": " • "},
                                                {"text": "3:32"}
                                              ]
                                            }
                                          }
                                        }
                                      ]
                                    }
                                  }]
                                }
                              }]
                            }
                          }
                        }
                      }]
                    }
                  }
                }
            """.trimIndent()

            val mockClient = OkHttpClient.Builder()
                .addInterceptor { chain ->
                    val urlStr = chain.request().url.toString()
                    when {
                        urlStr.contains("charts.youtube.com") || urlStr.contains("/browse") -> {
                            okhttp3.Response.Builder()
                                .request(chain.request())
                                .protocol(Protocol.HTTP_1_1)
                                .code(500)
                                .message("Internal Server Error")
                                .body("".toResponseBody("application/json".toMediaType()))
                                .build()
                        }
                        urlStr.contains("/get_search_suggestions") -> {
                            okhttp3.Response.Builder()
                                .request(chain.request())
                                .protocol(Protocol.HTTP_1_1)
                                .code(200)
                                .message("OK")
                                .header("Content-Type", "application/json; charset=utf-8")
                                .body(suggestionsPayload.toResponseBody("application/json; charset=utf-8".toMediaType()))
                                .build()
                        }
                        urlStr.contains("/youtubei/v1/search") -> {
                            okhttp3.Response.Builder()
                                .request(chain.request())
                                .protocol(Protocol.HTTP_1_1)
                                .code(200)
                                .message("OK")
                                .header("Content-Type", "application/json; charset=utf-8")
                                .body(searchPayload.toResponseBody("application/json; charset=utf-8".toMediaType()))
                                .build()
                        }
                        else -> {
                            okhttp3.Response.Builder()
                                .request(chain.request())
                                .protocol(Protocol.HTTP_1_1)
                                .code(200)
                                .message("OK")
                                .header("Content-Type", "text/html; charset=utf-8")
                                .body(mockClientVersionHtml.toResponseBody("text/html; charset=utf-8".toMediaType()))
                                .build()
                        }
                    }
                }
                .build()

            NewPipe.init(NewPipeDownloader(mockClient), Localization("en", "US"), ContentCountry("US"))

            // TRENDING_MUSIC falls back cleanly from failed charts/kiosk to NewPipe search
            val trendingState = resolver.fetchDiscoverSection(
                sectionType = DiscoverSectionType.TRENDING_MUSIC,
                provider = ytMusic,
                searchDataSource = searchDataSource,
                forceRefresh = true
            )
            assertTrue("Trending Music must recover via search fallback when charts fail: $trendingState", trendingState is DiscoverSectionState.Success)
            val trendingTracks = (trendingState as DiscoverSectionState.Success).items
            assertTrue(trendingTracks.isNotEmpty())
            assertEquals("Recovered Trending Track", trendingTracks.first().title)

            // POPULAR_SONGS succeeds independently
            val songsState = resolver.fetchDiscoverSection(
                sectionType = DiscoverSectionType.POPULAR_SONGS,
                provider = ytMusic,
                searchDataSource = searchDataSource,
                forceRefresh = true
            )
            assertTrue("Popular Songs should succeed: $songsState", songsState is DiscoverSectionState.Success)
        } finally {
            NewPipe.init(previousDownloader, previousLoc, previousCountry)
        }
    }

    @Test
    fun `Discover section returns offline Error cleanly when network is unavailable`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val activeNetwork = cm.activeNetwork
        if (activeNetwork != null) {
            // Strip NET_CAPABILITY_INTERNET to simulate offline device
            val emptyCaps = ShadowNetworkCapabilities.newInstance()
            Shadows.shadowOf(cm).setNetworkCapabilities(activeNetwork, emptyCaps)
        }

        NewPipeInitializer.ensureInitialized(context)
        val monitor = NetworkConnectivityMonitor(context)
        val resolver = OnlineStreamResolver(monitor)
        val searchDataSource = OnlineSearchDataSource(monitor)
        val ytMusic = NewPipeInitializer.discoverProviders()
            .first { it.key == OnlineMusicProvider.KEY_YOUTUBE_MUSIC }

        val state = resolver.fetchDiscoverSection(
            sectionType = DiscoverSectionType.NEW_RELEASES,
            provider = ytMusic,
            searchDataSource = searchDataSource,
            forceRefresh = true
        )
        assertTrue("Expected offline Error state, got: $state", state is DiscoverSectionState.Error)
        val err = state as DiscoverSectionState.Error
        assertEquals(OnlineErrorKind.OFFLINE, err.kind)
        assertTrue(err.isRetryable)
    }
}
