package data.online

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.test.core.app.ApplicationProvider
import domain.online.AudioQualityPreference
import domain.online.OnlineErrorKind
import domain.online.OnlineItemKind
import domain.online.OnlineMusicProvider
import domain.online.OnlineTrack
import domain.online.StreamDeliveryType
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runTest
import okhttp3.Cookie
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowNetworkCapabilities
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request
import org.schabi.newpipe.extractor.downloader.Response
import org.schabi.newpipe.extractor.exceptions.SignInConfirmNotBotException
import org.schabi.newpipe.extractor.exceptions.YoutubeMusicPremiumContentException
import org.schabi.newpipe.extractor.localization.ContentCountry
import org.schabi.newpipe.extractor.localization.Localization
import java.nio.charset.StandardCharsets
import java.util.concurrent.atomic.AtomicInteger

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class YoutubeBotFallbackAndRecoveryTest {

    @Test
    fun `mapThrowableToError and isTransientFailure classify SignInConfirmNotBotException and YoutubeMusicPremiumContentException`() {
        val botEx = SignInConfirmNotBotException(
            "YouTube probably temporarily blocked anonymous watch access with this IP , got error LOGIN_REQUIRED: \"Sign in to confirm that you're not a bot\""
        )
        assertFalse(
            "SignInConfirmNotBotException must not trigger blind transient HTTP retries",
            OnlineSearchDataSource.isTransientFailure(botEx)
        )
        val botErr = OnlineSearchDataSource.mapThrowableToError(
            t = botEx,
            providerName = "YT Music",
            operation = "resolve_stream",
            requestType = OnlineMusicProvider.KEY_YOUTUBE_MUSIC
        )
        assertEquals(OnlineErrorKind.RATE_LIMITED_OR_CAPTCHA, botErr.kind)
        assertTrue(botErr.isRetryable)
        assertTrue(botErr.message.contains("anti-bot", ignoreCase = true) || botErr.message.contains("blocked", ignoreCase = true))

        val premiumEx = YoutubeMusicPremiumContentException()
        assertFalse(OnlineSearchDataSource.isTransientFailure(premiumEx))
        val premiumErr = OnlineSearchDataSource.mapThrowableToError(
            t = premiumEx,
            providerName = "YT Music",
            operation = "resolve_stream",
            requestType = OnlineMusicProvider.KEY_YOUTUBE_MUSIC
        )
        assertEquals(OnlineErrorKind.UNSUPPORTED, premiumErr.kind)
        assertFalse(premiumErr.isRetryable)
    }

    @Test
    fun `YoutubeFallbackPlayerClient extracts video IDs from watch, music, short, and embed URLs`() {
        assertEquals(
            "dQw4w9WgXcQ",
            YoutubeFallbackPlayerClient.extractVideoId("https://music.youtube.com/watch?v=dQw4w9WgXcQ")
        )
        assertEquals(
            "dQw4w9WgXcQ",
            YoutubeFallbackPlayerClient.extractVideoId("https://www.youtube.com/watch?v=dQw4w9WgXcQ&list=RD")
        )
        assertEquals(
            "dQw4w9WgXcQ",
            YoutubeFallbackPlayerClient.extractVideoId("https://youtu.be/dQw4w9WgXcQ")
        )
        assertEquals(
            "dQw4w9WgXcQ",
            YoutubeFallbackPlayerClient.extractVideoId("dQw4w9WgXcQ")
        )
        assertNull(YoutubeFallbackPlayerClient.extractVideoId("https://soundcloud.com/artist/track"))
    }

    @Test
    fun `YoutubeFallbackPlayerClient falls back from bot-blocked client to succeeding Innertube client`() {
        val attemptedClients = mutableListOf<String>()
        val fakeDownloader = object : Downloader() {
            override fun execute(request: Request): Response {
                val url = request.url()
                if (url.contains("visitor_id")) {
                    return Response(
                        200,
                        "OK",
                        emptyMap(),
                        """{"responseContext":{"visitorData":"CgtVisitor123"}}""",
                        url
                    )
                }
                val body = request.dataToSend()?.toString(StandardCharsets.UTF_8).orEmpty()
                val isVr = body.contains("\"ANDROID_VR\"")
                val isIos = body.contains("\"IOS\"")
                attemptedClients.add(
                    when {
                        isVr -> "ANDROID_VR"
                        isIos -> "IOS"
                        else -> "OTHER"
                    }
                )
                return if (isVr) {
                    Response(
                        200,
                        "OK",
                        emptyMap(),
                        """
                        {
                          "playabilityStatus": {
                            "status": "LOGIN_REQUIRED",
                            "reason": "Sign in to confirm that you're not a bot"
                          }
                        }
                        """.trimIndent(),
                        url
                    )
                } else {
                    Response(
                        200,
                        "OK",
                        emptyMap(),
                        """
                        {
                          "playabilityStatus": { "status": "OK" },
                          "videoDetails": {
                            "videoId": "abcDEF12345",
                            "title": "Kesariya",
                            "author": "Arijit Singh",
                            "channelId": "UCartist123",
                            "lengthSeconds": "268",
                            "viewCount": "5000000",
                            "isLiveContent": false,
                            "thumbnail": {
                              "thumbnails": [
                                { "url": "https://i.ytimg.com/vi/abcDEF12345/hqdefault.jpg", "width": 480, "height": 360 }
                              ]
                            }
                          },
                          "streamingData": {
                            "adaptiveFormats": [
                              {
                                "itag": 140,
                                "url": "https://rr1---sn-example.googlevideo.com/videoplayback?itag=140",
                                "mimeType": "audio/mp4; codecs=\"mp4a.40.2\"",
                                "bitrate": 128000,
                                "averageBitrate": 128000
                              }
                            ],
                            "hlsManifestUrl": "https://manifest.googlevideo.com/api/manifest/hls_variant/playlist.m3u8"
                          }
                        }
                        """.trimIndent(),
                        url
                    )
                }
            }
        }

        val client = YoutubeFallbackPlayerClient()
        val result = client.resolveWithFallbackClients(
            downloader = fakeDownloader,
            contentUrl = "https://music.youtube.com/watch?v=abcDEF12345",
            localization = Localization("en", "IN"),
            country = ContentCountry("IN"),
            profiles = YoutubeFallbackPlayerClient.FALLBACK_PROFILES.take(2)
        )

        assertTrue("Expected Success after ANDROID_VR bot check fell back to IOS", result is YoutubeFallbackPlayerClient.FallbackAttemptResult.Success)
        val payload = (result as YoutubeFallbackPlayerClient.FallbackAttemptResult.Success).payload
        assertEquals("IOS", payload.clientNameUsed)
        assertEquals("Kesariya", payload.title)
        assertEquals("Arijit Singh", payload.artist)
        assertEquals(268_000L, payload.durationMs)
        assertEquals(listOf("ANDROID_VR", "IOS"), attemptedClients)
        assertTrue(payload.options.any { it.deliveryType == StreamDeliveryType.PROGRESSIVE_HTTP && it.averageBitrateKbps == 128 })
        assertTrue(payload.options.any { it.deliveryType == StreamDeliveryType.HLS })
        assertTrue(payload.options.first().headers["User-Agent"]?.contains("ios.youtube") == true)
    }

    @Test
    fun `YoutubeFallbackPlayerClient reports sawBotCheck when all fallback clients return LOGIN_REQUIRED`() {
        val alwaysBotDownloader = object : Downloader() {
            override fun execute(request: Request): Response {
                val url = request.url()
                if (url.contains("visitor_id")) {
                    return Response(200, "OK", emptyMap(), """{"responseContext":{"visitorData":"v1"}}""", url)
                }
                return Response(
                    200,
                    "OK",
                    emptyMap(),
                    """{"playabilityStatus":{"status":"LOGIN_REQUIRED","reason":"Sign in to confirm that you're not a bot"}}""",
                    url
                )
            }
        }

        val client = YoutubeFallbackPlayerClient()
        val result = client.resolveWithFallbackClients(
            downloader = alwaysBotDownloader,
            contentUrl = "https://music.youtube.com/watch?v=abcDEF12345",
            localization = Localization("en", "IN"),
            country = ContentCountry("IN"),
            profiles = YoutubeFallbackPlayerClient.FALLBACK_PROFILES.take(2)
        )

        assertTrue(result is YoutubeFallbackPlayerClient.FallbackAttemptResult.Failure)
        val failure = result as YoutubeFallbackPlayerClient.FallbackAttemptResult.Failure
        assertTrue(failure.sawBotCheck)
        assertTrue(failure.lastThrowable is SignInConfirmNotBotException)
    }

    @Test
    fun `selectVerifiedSoundCloudMatch selects matching full track and rejects 30s Go+ previews and unwanted remixes`() {
        val preview30s = OnlineTrack(
            stableId = -1L,
            serviceId = 1,
            providerKey = OnlineMusicProvider.KEY_SOUNDCLOUD,
            providerName = "SoundCloud",
            contentUrl = "https://soundcloud.com/arijit/kesariya-preview",
            title = "Kesariya",
            artist = "Arijit Singh",
            durationMs = 30_000L,
            kind = OnlineItemKind.TRACK
        )
        val unwantedRemix = OnlineTrack(
            stableId = -2L,
            serviceId = 1,
            providerKey = OnlineMusicProvider.KEY_SOUNDCLOUD,
            providerName = "SoundCloud",
            contentUrl = "https://soundcloud.com/dj/kesariya-slowed-reverb",
            title = "Kesariya (Slowed + Reverb)",
            artist = "DJ Example",
            durationMs = 265_000L,
            kind = OnlineItemKind.TRACK
        )
        val exactFullTrack = OnlineTrack(
            stableId = -3L,
            serviceId = 1,
            providerKey = OnlineMusicProvider.KEY_SOUNDCLOUD,
            providerName = "SoundCloud",
            contentUrl = "https://soundcloud.com/arijit/kesariya",
            title = "Kesariya - Brahmastra",
            artist = "Arijit Singh",
            durationMs = 268_000L,
            kind = OnlineItemKind.TRACK
        )

        val matched = OnlineStreamResolver.selectVerifiedSoundCloudMatch(
            candidates = listOf(preview30s, unwantedRemix, exactFullTrack),
            targetTitle = "Kesariya (Official Video)",
            targetArtist = "Arijit Singh",
            expectedDurationMs = 268_000L
        )
        assertNotNull(matched)
        assertEquals("https://soundcloud.com/arijit/kesariya", matched?.contentUrl)

        val onlyPreviewsOrWrong = OnlineStreamResolver.selectVerifiedSoundCloudMatch(
            candidates = listOf(preview30s, unwantedRemix),
            targetTitle = "Kesariya",
            targetArtist = "Arijit Singh",
            expectedDurationMs = 268_000L
        )
        assertNull("30s preview snippets and unrequested remixes must be rejected", onlyPreviewsOrWrong)
    }

    @Test
    fun `SessionCookieJar saves valid cookies for host and prunes expired cookies`() {
        val jar = SessionCookieJar()
        val ytUrl = "https://www.youtube.com/youtubei/v1/player".toHttpUrl()
        val futureCookie = Cookie.Builder()
            .name("SOCS")
            .value("CAESHAgCEhJnd3NfMjAyNDAxMDEtMF9SQzEaAmVuIAEaBgiA")
            .domain("youtube.com")
            .path("/")
            .expiresAt(System.currentTimeMillis() + 60_000L)
            .build()
        val expiredCookie = Cookie.Builder()
            .name("OLD_COOKIE")
            .value("stale")
            .domain("youtube.com")
            .path("/")
            .expiresAt(System.currentTimeMillis() - 10_000L)
            .build()

        jar.saveFromResponse(ytUrl, listOf(futureCookie, expiredCookie))
        val loaded = jar.loadForRequest(ytUrl)
        assertEquals(1, loaded.size)
        assertEquals("SOCS", loaded.first().name)
    }

    @Test
    fun `selectVerifiedSoundCloudMatch preserves Indic Unicode scripts and strips YouTube pipe credits and label names`() {
        val normalizedHindi = OnlineStreamResolver.normalizeText("केसरिया (Official Video)")
        assertTrue("Hindi Unicode characters and matras must be preserved", normalizedHindi.contains("केसरिया"))

        val hindiCandidate = OnlineTrack(
            stableId = -10L,
            serviceId = 1,
            providerKey = OnlineMusicProvider.KEY_SOUNDCLOUD,
            providerName = "SoundCloud",
            contentUrl = "https://soundcloud.com/arijit/kesariya-hindi",
            title = "केसरिया - Arijit Singh",
            artist = "Arijit Singh",
            durationMs = 268_000L,
            kind = OnlineItemKind.TRACK
        )
        val hindiMatched = OnlineStreamResolver.selectVerifiedSoundCloudMatch(
            candidates = listOf(hindiCandidate),
            targetTitle = "केसरिया (From \"Brahmastra\") | Arijit Singh | Ranbir Kapoor",
            targetArtist = "Sony Music India",
            expectedDurationMs = 268_000L
        )
        assertNotNull("Indic Unicode song title with pipe credits and label uploader should match", hindiMatched)

        val tamilCandidate = OnlineTrack(
            stableId = -11L,
            serviceId = 1,
            providerKey = OnlineMusicProvider.KEY_SOUNDCLOUD,
            providerName = "SoundCloud",
            contentUrl = "https://soundcloud.com/anirudh/arabic-kuthu-tamil",
            title = "அரபிக் குத்து",
            artist = "Anirudh Ravichander",
            durationMs = 280_000L,
            kind = OnlineItemKind.TRACK
        )
        val tamilMatched = OnlineStreamResolver.selectVerifiedSoundCloudMatch(
            candidates = listOf(tamilCandidate),
            targetTitle = "அரபிக் குத்து (Lyric Video)",
            targetArtist = "Sun TV",
            expectedDurationMs = 280_000L
        )
        assertNotNull("Tamil Unicode song title should match", tamilMatched)

        val queries = OnlineStreamResolver.buildSoundCloudFallbackQueries(
            trackTitle = "Sahiba (Full Video) Jasleen Royal, Stebin Ben | Vijay Deverakonda | Priya Saraiya",
            trackArtist = "T-Series"
        )
        assertTrue("Primary query must not contain generic label T-Series", queries.none { it.contains("T-Series", ignoreCase = true) })
        assertTrue("Primary query must drop actor credits after pipe", queries.none { it.contains("Deverakonda", ignoreCase = true) })
        assertTrue("Fallback queries must include concise song title", queries.any { it.contains("Sahiba", ignoreCase = true) })

        val conciseScTrack = OnlineTrack(
            stableId = -12L,
            serviceId = 1,
            providerKey = OnlineMusicProvider.KEY_SOUNDCLOUD,
            providerName = "SoundCloud",
            contentUrl = "https://soundcloud.com/jasleen/sahiba",
            title = "Sahiba - Jasleen Royal, Stebin Ben",
            artist = "Jasleen Royal",
            durationMs = 215_000L,
            kind = OnlineItemKind.TRACK
        )
        val pipeMatched = OnlineStreamResolver.selectVerifiedSoundCloudMatch(
            candidates = listOf(conciseScTrack),
            targetTitle = "Sahiba (Full Video) | Jasleen Royal, Stebin Ben | Vijay Deverakonda",
            targetArtist = "T-Series",
            expectedDurationMs = 215_000L
        )
        assertNotNull("Long YouTube title with pipe credits and label channel should match concise SoundCloud upload", pipeMatched)
        assertEquals("https://soundcloud.com/jasleen/sahiba", pipeMatched?.contentUrl)
    }

    @Test
    fun `OnlineStreamResolver coalesces concurrent resolveStream calls for identical URL`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val activeNetwork = cm.activeNetwork
        if (activeNetwork != null) {
            val caps = ShadowNetworkCapabilities.newInstance()
            Shadows.shadowOf(caps).addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            Shadows.shadowOf(cm).setNetworkCapabilities(activeNetwork, caps)
        }

        NewPipeInitializer.ensureInitialized(context)
        val previousDownloader = NewPipe.getDownloader()
        val previousLoc = NewPipe.getPreferredLocalization() ?: Localization("en", "IN")
        val previousCountry = NewPipe.getPreferredContentCountry() ?: ContentCountry("IN")

        val playerCalls = AtomicInteger(0)
        try {
            val mockClient = OkHttpClient.Builder()
                .addInterceptor { chain ->
                    val url = chain.request().url.toString()
                    if (url.contains("/youtubei/v1/player")) {
                        val count = playerCalls.incrementAndGet()
                        Thread.sleep(90L)
                        // Simulate primary NewPipe client failing once or succeeding cleanly
                        val bodyStr = chain.request().headers["X-YouTube-Client-Name"]
                        val okJson = """
                            {
                              "playabilityStatus": { "status": "OK" },
                              "videoDetails": {
                                "videoId": "coalesce123",
                                "title": "Coalesced Track",
                                "author": "Test Artist",
                                "channelId": "UCcoalesce",
                                "lengthSeconds": "200",
                                "viewCount": "1000",
                                "isLiveContent": false
                              },
                              "streamingData": {
                                "adaptiveFormats": [
                                  {
                                    "itag": 140,
                                    "url": "https://rr1---sn-example.googlevideo.com/videoplayback?itag=140&c=$count&h=${bodyStr.orEmpty()}",
                                    "mimeType": "audio/mp4; codecs=\"mp4a.40.2\"",
                                    "bitrate": 128000,
                                    "averageBitrate": 128000
                                  }
                                ]
                              }
                            }
                        """.trimIndent()
                        okhttp3.Response.Builder()
                            .request(chain.request())
                            .protocol(Protocol.HTTP_1_1)
                            .code(200)
                            .message("OK")
                            .header("Content-Type", "application/json; charset=utf-8")
                            .body(okJson.toResponseBody("application/json; charset=utf-8".toMediaType()))
                            .build()
                    } else {
                        okhttp3.Response.Builder()
                            .request(chain.request())
                            .protocol(Protocol.HTTP_1_1)
                            .code(200)
                            .message("OK")
                            .header("Content-Type", "text/html; charset=utf-8")
                            .body("<html><script>var ytInitialData={};</script></html>".toResponseBody("text/html; charset=utf-8".toMediaType()))
                            .build()
                    }
                }
                .build()
            NewPipe.init(NewPipeDownloader(mockClient), Localization("en", "IN"), ContentCountry("IN"))

            val baselineResolver = OnlineStreamResolver(NetworkConnectivityMonitor(context))
            val url = "https://music.youtube.com/watch?v=coalesce123"
            val singleRes = baselineResolver.resolveStream(
                serviceId = 0,
                providerKey = OnlineMusicProvider.KEY_YOUTUBE_MUSIC,
                providerName = "YT Music",
                contentUrl = url,
                qualityPreference = AudioQualityPreference.AUTO
            )
            assertTrue("Baseline single resolution should succeed: $singleRes", singleRes is domain.online.OnlineResult.Success)
            val baselineCallCount = playerCalls.get()
            assertTrue("Baseline should perform at least 1 player call", baselineCallCount >= 1)

            playerCalls.set(0)
            val concurrentResolver = OnlineStreamResolver(NetworkConnectivityMonitor(context))
            val deferreds = listOf(
                async {
                    concurrentResolver.resolveStream(
                        serviceId = 0,
                        providerKey = OnlineMusicProvider.KEY_YOUTUBE_MUSIC,
                        providerName = "YT Music",
                        contentUrl = url,
                        qualityPreference = AudioQualityPreference.AUTO
                    )
                },
                async {
                    concurrentResolver.resolveStream(
                        serviceId = 0,
                        providerKey = OnlineMusicProvider.KEY_YOUTUBE_MUSIC,
                        providerName = "YT Music",
                        contentUrl = url,
                        qualityPreference = AudioQualityPreference.AUTO
                    )
                }
            )
            val results = deferreds.awaitAll()
            assertTrue("All coalesced calls should succeed: $results", results.all { it is domain.online.OnlineResult.Success })
            assertEquals(
                "Concurrent identical resolveStream calls must coalesce to the same player HTTP calls as a single resolution",
                baselineCallCount,
                playerCalls.get()
            )
        } finally {
            NewPipe.init(previousDownloader, previousLoc, previousCountry)
        }
    }
}
