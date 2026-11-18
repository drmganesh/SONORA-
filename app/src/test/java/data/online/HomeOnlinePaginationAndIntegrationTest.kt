package data.online

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.test.core.app.ApplicationProvider
import domain.online.DiscoverMusicLanguage
import domain.online.DiscoverSectionState
import domain.online.DiscoverSectionType
import domain.online.OnlineMusicProvider
import domain.online.OnlineRecommendationProfile
import domain.online.OnlineResult
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowNetworkCapabilities
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.localization.ContentCountry
import org.schabi.newpipe.extractor.localization.Localization

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class HomeOnlinePaginationAndIntegrationTest {

    private fun buildSearchJson(videoId: String, title: String, artist: String, continuationToken: String? = null): String {
        val continuationBlock = if (continuationToken != null) {
            """,
            "continuations": [{
              "nextContinuationData": {
                "continuation": "$continuationToken",
                "clickTrackingParams": "mock_click_tracking"
              }
            }]"""
        } else {
            ""
        }
        return """
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
                                            {"text": "Single"},
                                            {"text": " • "},
                                            {"text": "3:45"}
                                          ]
                                        }
                                      }
                                    }
                                  ]
                                }
                              }]$continuationBlock
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

    private fun buildContinuationSearchJson(videoId: String, title: String, artist: String): String {
        return """
            {
              "responseContext": {"visitorData": "v_test"},
              "continuationContents": {
                "musicShelfContinuation": {
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
                                {"text": "Single"},
                                {"text": " • "},
                                {"text": "4:02"}
                              ]
                            }
                          }
                        }
                      ]
                    }
                  }]
                }
              }
            }
        """.trimIndent()
    }

    @Test
    fun `fetchDiscoverSection uses recommendationProfile query and supports real NewPipe continuation loadMoreDiscoverSection`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        cm.activeNetwork?.let { active ->
            val caps = ShadowNetworkCapabilities.newInstance()
            Shadows.shadowOf(caps).addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            Shadows.shadowOf(cm).setNetworkCapabilities(active, caps)
        }

        NewPipeInitializer.ensureInitialized(context)
        val monitor = NetworkConnectivityMonitor(context)
        val resolver = OnlineStreamResolver(monitor)
        val searchDataSource = OnlineSearchDataSource(monitor)
        val ytMusic = NewPipeInitializer.discoverProviders().first { it.key == OnlineMusicProvider.KEY_YOUTUBE_MUSIC }

        val capturedQueries = mutableListOf<String>()
        val previousDownloader = NewPipe.getDownloader()
        val previousLoc = NewPipe.getPreferredLocalization() ?: Localization("en", "IN")
        val previousCountry = NewPipe.getPreferredContentCountry() ?: ContentCountry("IN")

        try {
            val mockClientVersionHtml = """
                <html><script>var ytInitialData = {"responseContext":{"serviceTrackingParams":[{"service":"CSI","params":[{"key":"cver","value":"2.20260101.00.00"}]}]}};</script></html>
            """.trimIndent()
            val firstPageJson = buildSearchJson(
                videoId = "pageOneVid1",
                title = "Apna Bana Le",
                artist = "Arijit Singh",
                continuationToken = "MOCK_CONT_TOKEN_2"
            )
            val secondPageJson = buildContinuationSearchJson(
                videoId = "pageTwoVid2",
                title = "Channa Mereya",
                artist = "Arijit Singh"
            )

            val mockClient = OkHttpClient.Builder()
                .addInterceptor { chain ->
                    val req = chain.request()
                    val urlStr = req.url.toString()
                    when {
                        urlStr.contains("/youtubei/v1/search") && urlStr.contains("ctoken=") -> {
                            okhttp3.Response.Builder()
                                .request(req)
                                .protocol(Protocol.HTTP_1_1)
                                .code(200)
                                .message("OK")
                                .header("Content-Type", "application/json; charset=utf-8")
                                .body(secondPageJson.toResponseBody("application/json; charset=utf-8".toMediaType()))
                                .build()
                        }
                        urlStr.contains("/youtubei/v1/search") -> {
                            val buf = okio.Buffer()
                            req.body?.writeTo(buf)
                            val bodyStr = buf.readUtf8()
                            capturedQueries.add(bodyStr)
                            val isContinuation = bodyStr.contains("MOCK_CONT_TOKEN_2")
                            val json = if (isContinuation) secondPageJson else firstPageJson
                            okhttp3.Response.Builder()
                                .request(req)
                                .protocol(Protocol.HTTP_1_1)
                                .code(200)
                                .message("OK")
                                .header("Content-Type", "application/json; charset=utf-8")
                                .body(json.toResponseBody("application/json; charset=utf-8".toMediaType()))
                                .build()
                        }
                        else -> {
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
                }
                .build()

            NewPipe.init(NewPipeDownloader(mockClient), Localization("en", "IN"), ContentCountry("IN"))

            val profile = OnlineRecommendationProfile(
                seedQuery = "Arijit Singh Hindi songs",
                fallbackQuery = "Hindi Bollywood music mix",
                subtitle = "Inspired by Arijit Singh • India",
                primaryArtistSeed = "Arijit Singh",
                regionCode = "IN",
                languageId = DiscoverMusicLanguage.HINDI.id,
                dayKey = "2026-11-16"
            )

            val initial = resolver.fetchDiscoverSection(
                sectionType = DiscoverSectionType.MUSIC_RECOMMENDATIONS,
                provider = ytMusic,
                searchDataSource = searchDataSource,
                countryCode = "IN",
                languageCode = "en",
                discoverLanguage = DiscoverMusicLanguage.HINDI,
                recommendationProfile = profile,
                forceRefresh = true
            )

            assertTrue("Expected Success state, got $initial", initial is DiscoverSectionState.Success)
            val success = initial as DiscoverSectionState.Success
            assertEquals("Apna Bana Le", success.items.first().title)
            assertTrue("Expected query body to contain Arijit Singh seed", capturedQueries.any { it.contains("Arijit Singh Hindi songs") })
            assertTrue("Expected nextPageToken from NewPipe continuation", success.hasMore && !success.nextPageToken.isNullOrBlank())

            val moreRes = resolver.loadMoreDiscoverSection(
                sectionType = DiscoverSectionType.MUSIC_RECOMMENDATIONS,
                provider = ytMusic,
                previousToken = success.nextPageToken!!,
                searchDataSource = searchDataSource
            )
            assertTrue("Expected Success on loadMoreDiscoverSection, got $moreRes", moreRes is OnlineResult.Success)
            val moreItems = (moreRes as OnlineResult.Success).value.first
            assertEquals(1, moreItems.size)
            assertEquals("Channa Mereya", moreItems.first().title)
        } finally {
            NewPipe.init(previousDownloader, previousLoc, previousCountry)
        }
    }
}
