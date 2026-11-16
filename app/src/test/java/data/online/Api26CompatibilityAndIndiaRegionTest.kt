package data.online

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Build
import androidx.test.core.app.ApplicationProvider
import com.example.data.SonoraPreferences
import data.preferences.OnlineRegionPreferences
import data.scanner.FolderScannerHelper
import domain.online.AudioQualityPreference
import domain.online.DiscoverMusicLanguage
import domain.online.DiscoverSectionState
import domain.online.DiscoverSectionType
import domain.online.OnlineItemKind
import domain.online.OnlineMusicProvider
import domain.online.OnlineRegionCatalog
import domain.online.OnlineResult
import domain.online.OnlineSearchFilter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowNetworkCapabilities
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.downloader.Request
import org.schabi.newpipe.extractor.localization.ContentCountry
import org.schabi.newpipe.extractor.localization.Localization
import org.schabi.newpipe.extractor.utils.Utils
import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets

/**
 * Verifies:
 * 1. API 26 and API 33+ (36) runtime compatibility for patched NewPipeExtractor v0.27.1-sonora.1
 *    URLDecoder/URLEncoder calls and FolderScannerHelper SAF URI decoding.
 * 2. India (IN) as default ContentCountry & English (en-IN) Localization initialized once cleanly.
 * 3. Independent Country vs Language DataStore persistence & Discover language filter persistence.
 * 4. All 5 Discover sections, search, fallback recovery, and stream resolution across API 26 & 36.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [26, 36])
class Api26CompatibilityAndIndiaRegionTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences("sonora_prefs", Context.MODE_PRIVATE).edit().clear().commit()
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val activeNetwork = cm.activeNetwork
        if (activeNetwork != null) {
            val caps = ShadowNetworkCapabilities.newInstance()
            Shadows.shadowOf(caps).addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            Shadows.shadowOf(cm).setNetworkCapabilities(activeNetwork, caps)
        }
    }

    @Test
    fun `patched NewPipe Utils encodeUrlUtf8 and decodeUrlUtf8 work across multilingual inputs on API 26 and 36`() {
        val samples = listOf(
            "English" to "Hello World Music",
            "Hindi" to "केसरिया तेरा इश्क़",
            "Odia" to "ରଙ୍ଗବତୀ ଓଡିଆ ଗୀତ",
            "Tamil" to "நெஞ்சமே இளையராஜா",
            "SpecialChars" to "!@#$%^&*()_+=[]{}|;:',<>?",
            "Spaces" to "Arijit Singh & Shreya Ghoshal Live",
            "UnicodeEmoji" to "SONORA 🎵 Café naïve"
        )

        for ((label, input) in samples) {
            val encoded = Utils.encodeUrlUtf8(input)
            assertFalse("$label encoded string should not be blank", encoded.isBlank())
            val decoded = Utils.decodeUrlUtf8(encoded)
            assertEquals("Round-trip URL UTF-8 encode/decode failed for $label on API ${Build.VERSION.SDK_INT}", input, decoded)
        }

        // Percent-encoded URL containing spaces, ampersand, and Odia UTF-8 sequences
        val rawPercentEncodedUrl =
            "https://music.youtube.com/search?q=Hindi%20Songs%20%26%20%E0%AC%B0%E0%AC%99%E0%AD%8D%E0%AC%97%E0%AC%AC%E0%AC%A4%E0%AD%80"
        val decodedUrl = Utils.decodeUrlUtf8(rawPercentEncodedUrl)
        assertEquals(
            "https://music.youtube.com/search?q=Hindi Songs & ରଙ୍ଗବତୀ",
            decodedUrl
        )
    }

    @Test
    fun `patched NewPipe Utils bytecode contains zero java_nio_charset_Charset URLDecoder or URLEncoder calls`() {
        val classPath = "/org/schabi/newpipe/extractor/utils/Utils.class"
        val bytes = Utils::class.java.getResourceAsStream(classPath)?.use { input ->
            val out = ByteArrayOutputStream()
            input.copyTo(out)
            out.toByteArray()
        }
        assertNotNull("Patched Utils.class must be present on classpath", bytes)
        val ascii = String(bytes!!, StandardCharsets.ISO_8859_1)
        assertFalse(
            "Utils.class must NOT reference Charset overload of URLDecoder.decode",
            ascii.contains("URLDecoder") && ascii.contains("(Ljava/lang/String;Ljava/nio/charset/Charset;)Ljava/lang/String;")
        )
        assertTrue(
            "Utils.class must use String-charset overload (Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;",
            ascii.contains("(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;")
        )
    }

    @Test
    fun `FolderScannerHelper decodes SAF document URIs safely on API 26 and API 36`() {
        val treeUri = Uri.parse(
            "content://com.android.externalstorage.documents/tree/primary%3AMusic%2FHindi%20%26%20Odia%20%E0%AC%97%E0%AD%80%E0%AC%A4"
        )
        val info = FolderScannerHelper.resolveUriToFolderInfo(context, treeUri)
        assertTrue("Resolved path must include decoded folder hierarchy", info.path.endsWith("/Music/Hindi & Odia ଗୀତ"))
        assertEquals("Hindi & Odia ଗୀତ", info.name)
    }

    @Test
    fun `NewPipeInitializer initializes once with India country and English India localization by default`() {
        val prevDownloader = NewPipe.getDownloader()
        val prevLoc = NewPipe.getPreferredLocalization() ?: Localization("en", "IN")
        val prevCountry = NewPipe.getPreferredContentCountry() ?: ContentCountry("IN")
        try {
            NewPipeInitializer.resetForTesting()
            NewPipeInitializer.ensureInitialized(context)
            val firstDownloader = NewPipeInitializer.getDownloader()

            assertEquals("IN", NewPipe.getPreferredContentCountry().countryCode)
            assertEquals("en", NewPipe.getPreferredLocalization().languageCode)
            assertEquals("IN", NewPipe.getPreferredLocalization().countryCode)
            assertEquals("IN", NewPipeInitializer.currentCountryCode())
            assertEquals("en", NewPipeInitializer.currentLanguageCode())

            // Calling ensureInitialized again must be a no-op keeping single downloader instance
            NewPipeInitializer.ensureInitialized(context)
            assertTrue(NewPipeInitializer.getDownloader() === firstDownloader)

            // Verify NewPipeDownloader injects en-IN Accept-Language when caller does not specify Accept-Language
            var capturedAcceptLanguage: String? = null
            val captureClient = OkHttpClient.Builder()
                .addInterceptor { chain ->
                    capturedAcceptLanguage = chain.request().header("Accept-Language")
                    okhttp3.Response.Builder()
                        .request(chain.request())
                        .protocol(Protocol.HTTP_1_1)
                        .code(200)
                        .message("OK")
                        .body("{}".toResponseBody("application/json".toMediaType()))
                        .build()
                }
                .build()
            val downloader = NewPipeDownloader(captureClient)
            downloader.execute(Request.Builder().get("https://music.youtube.com/browse").build())
            assertEquals("en-IN", capturedAcceptLanguage)
        } finally {
            NewPipe.init(prevDownloader, prevLoc, prevCountry)
        }
    }

    @Test
    fun `OnlineRegionPreferences persists India default and keeps Country and Language strictly independent`() = runTest {
        val prefs = OnlineRegionPreferences(context)
        // Ensure initial clean state
        prefs.setContentCountry(OnlineRegionCatalog.DEFAULT_COUNTRY_CODE)
        prefs.setContentLanguage(OnlineRegionCatalog.DEFAULT_LANGUAGE_CODE)
        prefs.setDiscoverLanguage(DiscoverMusicLanguage.ALL)

        assertEquals("IN", prefs.contentCountryFlow.first())
        assertEquals("en", prefs.contentLanguageFlow.first())
        assertEquals(DiscoverMusicLanguage.ALL, prefs.discoverLanguageFlow.first())

        // Changing language must NOT alter Country
        prefs.setContentLanguage("hi")
        assertEquals("hi", prefs.contentLanguageFlow.first())
        assertEquals("IN", prefs.contentCountryFlow.first())

        // Changing Country must NOT alter Language
        prefs.setContentCountry("GB")
        assertEquals("GB", prefs.contentCountryFlow.first())
        assertEquals("hi", prefs.contentLanguageFlow.first())

        // Changing Discover language filter persists independently
        prefs.setDiscoverLanguage(DiscoverMusicLanguage.ODIA)
        val reloaded = OnlineRegionPreferences(context)
        assertEquals("GB", reloaded.getInitialCountryCode())
        assertEquals("hi", reloaded.getInitialLanguageCode())
        assertEquals(DiscoverMusicLanguage.ODIA, reloaded.discoverLanguageFlow.first())

        // Restore India defaults so subsequent tests remain clean
        prefs.setContentCountry("IN")
        prefs.setContentLanguage("en")
        prefs.setDiscoverLanguage(DiscoverMusicLanguage.ALL)
    }

    @Test
    fun `Discover all 5 sections, India language filters, search, and fallback execute cleanly on API 26 and 36`() = runTest {
        val prevDownloader = NewPipe.getDownloader()
        val prevLoc = NewPipe.getPreferredLocalization() ?: Localization("en", "IN")
        val prevCountry = NewPipe.getPreferredContentCountry() ?: ContentCountry("IN")
        try {
            NewPipeInitializer.ensureInitialized(context)
            NewPipeInitializer.updateLocalization("en", "IN")

            val capturedQueries = mutableListOf<String>()
            val capturedAcceptLanguages = mutableListOf<String?>()

            val mockClientVersionHtml = """
                <html><script>var ytInitialData = {"responseContext":{"serviceTrackingParams":[{"service":"CSI","params":[{"key":"cver","value":"2.20260101.00.00"}]}]}};</script></html>
            """.trimIndent()
            val suggestionsPayload = "{\"contents\":[{\"padding\":\"${"x".repeat(600)}\"}]}"

            val musicSongSearchJson = buildMockYtMusicSongSearchJson(
                videoId = "kJQP7kiw5Fk",
                title = "Rangabati (Odia Folk Remix)",
                artist = "Sona Mohapatra",
                album = "India Indie Hits"
            )
            val musicAlbumSearchJson = buildMockYtMusicBrowseItemSearchJson(
                browseId = "MPREb_IndiaAlbum01",
                pageType = "MUSIC_PAGE_TYPE_ALBUM",
                title = "Aashiqui 2 (Original Motion Picture Soundtrack)",
                subtitleRuns = """{"text": "Album"},{"text": " • "},{"text": "Mithoon, Arijit Singh"},{"text": " • "},{"text": "2013"}"""
            )
            val musicPlaylistSearchJson = buildMockYtMusicBrowseItemSearchJson(
                browseId = "VLPL_IndiaChill01",
                pageType = "MUSIC_PAGE_TYPE_PLAYLIST",
                title = "India Indie & Bollywood Acoustic",
                subtitleRuns = """{"text": "Playlist"},{"text": " • "},{"text": "YT Music India"},{"text": " • "},{"text": "45 songs"}"""
            )
            val channelSearchJson = buildMockYtChannelSearchJson(
                channelId = "UC_ArijitSinghOfficial",
                name = "Arijit Singh Official"
            )
            val emptySearchJson = buildMockYtMusicEmptySearchJson()

            var returnEmptyForSpecificLanguageQuery = false

            val mockClient = OkHttpClient.Builder()
                .addInterceptor { chain ->
                    val req = chain.request()
                    val urlStr = req.url.toString()
                    capturedAcceptLanguages.add(req.header("Accept-Language"))
                    val bodyStr = req.body?.let { body ->
                        val buf = okio.Buffer()
                        body.writeTo(buf)
                        buf.readUtf8()
                    }.orEmpty()
                    if (urlStr.contains("/youtubei/v1/visitor_id")) {
                        okhttp3.Response.Builder()
                            .request(req)
                            .protocol(Protocol.HTTP_1_1)
                            .code(200)
                            .message("OK")
                            .header("Content-Type", "application/json; charset=utf-8")
                            .body("""{"responseContext":{"visitorData":"v_in_test_visitor_data_padding_over_50_bytes_ok"}}""".toResponseBody("application/json; charset=utf-8".toMediaType()))
                            .build()
                    } else if (urlStr.contains("/youtubei/v1/search")) {
                        val queryMatch = Regex("\"query\"\\s*:\\s*\"([^\"]+)\"").find(bodyStr)?.groupValues?.getOrNull(1)
                        if (queryMatch != null) capturedQueries.add(queryMatch)

                        val isEmptyForLang = returnEmptyForSpecificLanguageQuery &&
                            queryMatch?.contains("Tamil", ignoreCase = true) == true
                        val json = when {
                            isEmptyForLang -> emptySearchJson
                            !urlStr.contains("music.youtube.com") -> channelSearchJson
                            queryMatch?.contains("releases", ignoreCase = true) == true -> musicAlbumSearchJson
                            queryMatch?.contains("playlist", ignoreCase = true) == true ||
                                queryMatch?.contains("mix", ignoreCase = true) == true -> musicPlaylistSearchJson
                            else -> musicSongSearchJson
                        }
                        okhttp3.Response.Builder()
                            .request(req)
                            .protocol(Protocol.HTTP_1_1)
                            .code(200)
                            .message("OK")
                            .header("Content-Type", "application/json; charset=utf-8")
                            .body(json.toResponseBody("application/json; charset=utf-8".toMediaType()))
                            .build()
                    } else if (urlStr.contains("charts.youtube.com") || urlStr.contains("/browse")) {
                        // Force kiosk fallback to exercise search-backed India charts path as well
                        okhttp3.Response.Builder()
                            .request(req)
                            .protocol(Protocol.HTTP_1_1)
                            .code(500)
                            .message("Unavailable")
                            .body("".toResponseBody("application/json".toMediaType()))
                            .build()
                    } else if (urlStr.contains("/get_search_suggestions")) {
                        okhttp3.Response.Builder()
                            .request(req)
                            .protocol(Protocol.HTTP_1_1)
                            .code(200)
                            .message("OK")
                            .header("Content-Type", "application/json; charset=utf-8")
                            .body(suggestionsPayload.toResponseBody("application/json; charset=utf-8".toMediaType()))
                            .build()
                    } else {
                        okhttp3.Response.Builder()
                            .request(req)
                            .protocol(Protocol.HTTP_1_1)
                            .code(200)
                            .message("OK")
                            .header("Content-Type", "text/html; charset=utf-8")
                            .body(mockClientVersionHtml.toResponseBody("text/html; charset=utf-8".toMediaType()))
                            .build()
                    }
                }
                .build()

            NewPipe.init(NewPipeDownloader(mockClient), Localization("en", "IN"), ContentCountry("IN"))
            val monitor = NetworkConnectivityMonitor(context)
            val searchDataSource = OnlineSearchDataSource(monitor)
            val resolver = OnlineStreamResolver(monitor)
            val ytMusic = NewPipeInitializer.discoverProviders()
                .first { it.key == OnlineMusicProvider.KEY_YOUTUBE_MUSIC }

            // 1) Search with multilingual Hindi + Odia + spaces + special chars exercises patched Utils.encodeUrlUtf8
            val searchRes = searchDataSource.search(
                query = "केसरिया & ରଙ୍ଗବତୀ Arijit",
                provider = ytMusic,
                filter = OnlineSearchFilter.SONGS
            )
            assertTrue("Multilingual online search must succeed on API ${Build.VERSION.SDK_INT}: $searchRes", searchRes is OnlineResult.Success)
            val searchItems = (searchRes as OnlineResult.Success).value.items
            assertEquals("Rangabati (Odia Folk Remix)", searchItems.first().title)

            // 2) All 5 Discover sections load independently with India (IN) context
            for (section in DiscoverSectionType.entries) {
                val state = resolver.fetchDiscoverSection(
                    sectionType = section,
                    provider = ytMusic,
                    searchDataSource = searchDataSource,
                    countryCode = "IN",
                    languageCode = "en",
                    discoverLanguage = DiscoverMusicLanguage.HINDI,
                    forceRefresh = true
                )
                assertTrue(
                    "Discover section $section must load for India Hindi filter, got $state",
                    state is DiscoverSectionState.Success
                )
                assertTrue((state as DiscoverSectionState.Success).items.isNotEmpty())
            }
            assertTrue(
                "India Hindi queries must include Hindi Bollywood context",
                capturedQueries.any { it.contains("Hindi Bollywood", ignoreCase = true) }
            )

            // 3) Odia language filter query executes cleanly
            val odiaState = resolver.fetchDiscoverSection(
                sectionType = DiscoverSectionType.POPULAR_SONGS,
                provider = ytMusic,
                searchDataSource = searchDataSource,
                countryCode = "IN",
                languageCode = "en",
                discoverLanguage = DiscoverMusicLanguage.ODIA,
                forceRefresh = true
            )
            assertTrue(odiaState is DiscoverSectionState.Success)
            assertTrue(capturedQueries.any { it.contains("Odia", ignoreCase = true) })

            // 4) Graceful fallback when Tamil-specific query returns empty on a provider
            returnEmptyForSpecificLanguageQuery = true
            val tamilFallbackState = resolver.fetchDiscoverSection(
                sectionType = DiscoverSectionType.POPULAR_SONGS,
                provider = ytMusic,
                searchDataSource = searchDataSource,
                countryCode = "IN",
                languageCode = "en",
                discoverLanguage = DiscoverMusicLanguage.TAMIL,
                forceRefresh = true
            )
            assertTrue("Expected fallback Success when regional query is empty: $tamilFallbackState", tamilFallbackState is DiscoverSectionState.Success)
            assertTrue((tamilFallbackState as DiscoverSectionState.Success).sourceLabel.contains("Fallback"))
        } finally {
            NewPipe.init(prevDownloader, prevLoc, prevCountry)
        }
    }

    @Test
    fun `OnlineStreamResolver resolves audio stream from YouTube player payload without URLDecoder error on API 26 and 36`() = runTest {
        val prevDownloader = NewPipe.getDownloader()
        val prevLoc = NewPipe.getPreferredLocalization() ?: Localization("en", "IN")
        val prevCountry = NewPipe.getPreferredContentCountry() ?: ContentCountry("IN")
        try {
            NewPipeInitializer.ensureInitialized(context)
            NewPipeInitializer.updateLocalization("en", "IN")

            val mockPlayerJson = """
                {
                  "responseContext": {"visitorData": "v_in_test"},
                  "playabilityStatus": {"status": "OK"},
                  "videoDetails": {
                    "videoId": "kJQP7kiw5Fk",
                    "title": "Kesariya (India Mix)",
                    "lengthSeconds": "215",
                    "channelId": "UC_indian_music",
                    "author": "Pritam",
                    "viewCount": "12500000",
                    "thumbnail": {
                      "thumbnails": [
                        {"url": "https://i.ytimg.com/vi/kJQP7kiw5Fk/hqdefault.jpg", "width": 480, "height": 360}
                      ]
                    }
                  },
                  "microformat": {
                    "playerMicroformatRenderer": {
                      "category": "Music",
                      "uploadDate": "2026-01-15",
                      "publishDate": "2026-01-15"
                    }
                  },
                  "streamingData": {
                    "expiresInSeconds": "21540",
                    "adaptiveFormats": [
                      {
                        "itag": 251,
                        "url": "https://rr1---sn-cvh7knzl.googlevideo.com/videoplayback?itag=251&mime=audio%2Fwebm&title=%E0%A4%95%E0%A5%87%E0%A4%B8%E0%A4%B0%E0%A4%BF%E0%A4%AF%E0%A4%BE",
                        "mimeType": "audio/webm; codecs=\"opus\"",
                        "bitrate": 160000,
                        "averageBitrate": 148000,
                        "approxDurationMs": "215000",
                        "audioQuality": "AUDIO_QUALITY_MEDIUM",
                        "audioSampleRate": "48000",
                        "audioChannels": 2,
                        "contentLength": "3977500"
                      },
                      {
                        "itag": 140,
                        "url": "https://rr1---sn-cvh7knzl.googlevideo.com/videoplayback?itag=140&mime=audio%2Fmp4",
                        "mimeType": "audio/mp4; codecs=\"mp4a.40.2\"",
                        "bitrate": 129000,
                        "averageBitrate": 128000,
                        "approxDurationMs": "215000",
                        "audioQuality": "AUDIO_QUALITY_MEDIUM",
                        "audioSampleRate": "44100",
                        "audioChannels": 2,
                        "contentLength": "3450000"
                      }
                    ]
                  }
                }
            """.trimIndent()

            val mockNextJson = """
                {
                  "responseContext": {"visitorData": "v_in_test"},
                  "contents": {
                    "twoColumnWatchNextResults": {
                      "results": {
                        "results": {
                          "contents": [
                            {
                              "videoPrimaryInfoRenderer": {
                                "title": {"runs": [{"text": "Kesariya (India Mix)"}]},
                                "dateText": {"simpleText": "Jan 15, 2026"}
                              }
                            },
                            {
                              "videoSecondaryInfoRenderer": {
                                "owner": {
                                  "videoOwnerRenderer": {
                                    "title": {"runs": [{"text": "Pritam", "navigationEndpoint": {"browseEndpoint": {"browseId": "UC_indian_music"}}}]}
                                  }
                                }
                              }
                            }
                          ]
                        }
                      }
                    }
                  }
                }
            """.trimIndent()

            val mockWatchHtml = """
                <html><script>var ytInitialData = {"responseContext":{"serviceTrackingParams":[{"service":"CSI","params":[{"key":"cver","value":"2.20260101.00.00"}]}]}};</script><script src="/s/player/abcdef12/player_ias.vflset/en_US/base.js"></script></html>
            """.trimIndent()

            val mockBaseJs = "var _yt_player={};"

            val mockClient = OkHttpClient.Builder()
                .addInterceptor { chain ->
                    val req = chain.request()
                    val urlStr = req.url.toString()
                    val (code, contentType, body) = when {
                        urlStr.contains("/youtubei/v1/visitor_id") ->
                            Triple(200, "application/json; charset=utf-8", """{"responseContext":{"visitorData":"v_in_test_visitor_data_padding_over_50_bytes_ok"}}""")
                        urlStr.contains("/youtubei/v1/player") -> Triple(200, "application/json; charset=utf-8", mockPlayerJson)
                        urlStr.contains("/youtubei/v1/next") -> Triple(200, "application/json; charset=utf-8", mockNextJson)
                        urlStr.endsWith("base.js") -> Triple(200, "application/javascript; charset=utf-8", mockBaseJs)
                        else -> Triple(200, "text/html; charset=utf-8", mockWatchHtml)
                    }
                    okhttp3.Response.Builder()
                        .request(req)
                        .protocol(Protocol.HTTP_1_1)
                        .code(code)
                        .message("OK")
                        .header("Content-Type", contentType)
                        .body(body.toResponseBody(contentType.toMediaType()))
                        .build()
                }
                .build()

            NewPipe.init(NewPipeDownloader(mockClient), Localization("en", "IN"), ContentCountry("IN"))
            val resolver = OnlineStreamResolver(NetworkConnectivityMonitor(context))
            val resolved = resolver.resolveStream(
                serviceId = 0,
                providerKey = OnlineMusicProvider.KEY_YOUTUBE_MUSIC,
                providerName = "YT Music",
                contentUrl = "https://music.youtube.com/watch?v=kJQP7kiw5Fk",
                qualityPreference = AudioQualityPreference.HIGHEST,
                forceRefresh = true
            )

            assertTrue("Stream resolution must succeed cleanly on API ${Build.VERSION.SDK_INT}: $resolved", resolved is OnlineResult.Success)
            val value = (resolved as OnlineResult.Success).value
            assertEquals("Kesariya (India Mix)", value.title)
            assertEquals("Pritam", value.artist)
            assertTrue("Resolved audio stream URL must be populated", value.selectedStream.url.startsWith("https://"))
            assertTrue(value.availableStreams.isNotEmpty())
        } finally {
            NewPipe.init(prevDownloader, prevLoc, prevCountry)
        }
    }

    private fun buildMockYtMusicSongSearchJson(
        videoId: String,
        title: String,
        artist: String,
        album: String
    ): String {
        return """
            {
              "responseContext": {"visitorData": "v_in"},
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
                                  "playlistItemData": {"videoId": "$videoId"},
                                  "thumbnail": {
                                    "musicThumbnailRenderer": {
                                      "thumbnail": {
                                        "thumbnails": [{
                                          "url": "https://i.ytimg.com/vi/$videoId/hqdefault.jpg",
                                          "width": 480,
                                          "height": 360
                                        }]
                                      }
                                    }
                                  },
                                  "flexColumns": [
                                    {
                                      "musicResponsiveListItemFlexColumnRenderer": {
                                        "text": {"runs": [{"text": "$title"}]}
                                      }
                                    },
                                    {
                                      "musicResponsiveListItemFlexColumnRenderer": {
                                        "text": {
                                          "runs": [
                                            {"text": "$artist"},
                                            {"text": " • "},
                                            {"text": "$album"},
                                            {"text": " • "},
                                            {"text": "3:45"}
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
    }

    private fun buildMockYtMusicBrowseItemSearchJson(
        browseId: String,
        pageType: String,
        title: String,
        subtitleRuns: String
    ): String {
        return """
            {
              "responseContext": {"visitorData": "v_in"},
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
                                  "overlay": {
                                    "musicItemThumbnailOverlayRenderer": {
                                      "content": {
                                        "musicPlayButtonRenderer": {
                                          "playNavigationEndpoint": {
                                            "watchPlaylistEndpoint": {
                                              "playlistId": "OLAK5uy_india_$browseId"
                                            }
                                          }
                                        }
                                      }
                                    }
                                  },
                                  "navigationEndpoint": {
                                    "browseEndpoint": {
                                      "browseId": "$browseId",
                                      "browseEndpointContextSupportedConfigs": {
                                        "browseEndpointContextMusicConfig": {
                                          "pageType": "$pageType"
                                        }
                                      }
                                    }
                                  },
                                  "thumbnail": {
                                    "musicThumbnailRenderer": {
                                      "thumbnail": {
                                        "thumbnails": [{
                                          "url": "https://i.ytimg.com/vi/kJQP7kiw5Fk/hqdefault.jpg",
                                          "width": 480,
                                          "height": 360
                                        }]
                                      }
                                    }
                                  },
                                  "flexColumns": [
                                    {
                                      "musicResponsiveListItemFlexColumnRenderer": {
                                        "text": {"runs": [{"text": "$title"}]}
                                      }
                                    },
                                    {
                                      "musicResponsiveListItemFlexColumnRenderer": {
                                        "text": {
                                          "runs": [$subtitleRuns]
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
    }

    private fun buildMockYtChannelSearchJson(
        channelId: String,
        name: String
    ): String {
        return """
            {
              "responseContext": {"visitorData": "v_in"},
              "contents": {
                "twoColumnSearchResultsRenderer": {
                  "primaryContents": {
                    "sectionListRenderer": {
                      "contents": [{
                        "itemSectionRenderer": {
                          "contents": [{
                            "channelRenderer": {
                              "channelId": "$channelId",
                              "title": {"simpleText": "$name"},
                              "navigationEndpoint": {
                                "browseEndpoint": {"browseId": "$channelId"}
                              },
                              "thumbnail": {
                                "thumbnails": [{
                                  "url": "https://yt3.googleusercontent.com/test_avatar=s176",
                                  "width": 176,
                                  "height": 176
                                }]
                              },
                              "subscriberCountText": {"simpleText": "110M subscribers"},
                              "descriptionSnippet": {"runs": [{"text": "Indian playback singer"}]}
                            }
                          }]
                        }
                      }]
                    }
                  }
                }
              }
            }
        """.trimIndent()
    }

    private fun buildMockYtMusicEmptySearchJson(): String {
        return """
            {
              "responseContext": {"visitorData": "v_in"},
              "contents": {
                "tabbedSearchResultsRenderer": {
                  "tabs": [{
                    "tabRenderer": {
                      "content": {
                        "sectionListRenderer": {
                          "contents": []
                        }
                      }
                    }
                  }]
                }
              }
            }
        """.trimIndent()
    }
}
