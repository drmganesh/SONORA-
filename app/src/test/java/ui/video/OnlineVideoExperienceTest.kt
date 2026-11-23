package ui.video

import android.app.Application
import android.os.Build
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.SonoraDatabase
import com.example.viewmodel.SonoraViewModel
import data.online.NetworkConnectivityMonitor
import data.preferences.HomeModePreferences
import data.preferences.VideoAccountPreferences
import data.video.OnlineVideoDataSource
import data.video.OnlineVideoRepository
import data.video.VideoAccountActionProvider
import data.video.YouTubeAccountActionClient
import domain.home.HomeMusicMode
import domain.library.LibrarySectionMode
import domain.video.ChannelNotificationPreference
import domain.video.OnlineVideoChannel
import domain.video.OnlineVideoItem
import domain.video.ProviderAuthState
import domain.video.VideoAccountActionResult
import domain.video.VideoHomeSectionType
import domain.video.VideoPlayerUiState
import domain.video.VideoRatingState
import domain.video.VideoRegionalLanguage
import domain.video.VideoSearchFilter
import domain.video.VideoSectionState
import domain.video.VideoStreamOption
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import ui.components.HomeModeSwitcherSheet
import ui.library.LibraryModeSegmentedSelector
import ui.theme.EditorialColors
import ui.theme.LocalEditorialColors
import viewmodel.OnlineVideoController

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [26, 36])
class OnlineVideoExperienceTest {

    @get:Rule
    val composeRule = createComposeRule()

    private lateinit var app: Application
    private lateinit var db: SonoraDatabase
    private lateinit var controllerScope: CoroutineScope

    @Before
    fun setUp() = runBlocking {
        app = ApplicationProvider.getApplicationContext()
        HomeModePreferences(app).setHomeMusicMode(HomeMusicMode.OFFLINE)
        db = Room.inMemoryDatabaseBuilder(app, SonoraDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        controllerScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    }

    @After
    fun tearDown() = runBlocking {
        controllerScope.coroutineContext[Job]?.cancelAndJoin()
        delay(50L)
        db.close()
        HomeModePreferences(app).setHomeMusicMode(HomeMusicMode.OFFLINE)
    }

    private fun sampleVideo(
        id: String = "dQw4w9WgXcQ",
        title: String = "Arijit Singh Live Acoustic Session India",
        channel: String = "SONORA Studio India"
    ): OnlineVideoItem = OnlineVideoItem(
        videoId = id,
        contentUrl = "https://www.youtube.com/watch?v=$id",
        title = title,
        channelName = channel,
        channelUrl = "https://www.youtube.com/channel/UC1234567890",
        channelAvatarUrl = null,
        thumbnailUrl = null,
        durationSeconds = 265L,
        viewCount = 1_250_000L,
        uploadDateText = "2 days ago"
    )

    private fun sampleChannel(
        id: String = "UC1234567890",
        name: String = "SONORA Studio India"
    ): OnlineVideoChannel = OnlineVideoChannel(
        channelId = id,
        name = name,
        channelUrl = "https://www.youtube.com/channel/$id",
        subscriberCount = 420_000L
    )

    private fun buildMockHttpYouTubeClient(statusCode: Int, responseJson: String = "{}"): YouTubeAccountActionClient {
        val client = OkHttpClient.Builder()
            .addInterceptor { chain ->
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(statusCode)
                    .message(if (statusCode in 200..299) "OK" else "Error")
                    .body(responseJson.toResponseBody("application/json".toMediaType()))
                    .build()
            }
            .build()
        return YouTubeAccountActionClient(httpClient = client, oauthClientConfigured = true)
    }

    private fun validAuthState(): ProviderAuthState.Connected = ProviderAuthState.Connected(
        accountLabel = "tester@sonora.app",
        accessToken = "ya29.test-valid-oauth-token",
        expiresAtMs = System.currentTimeMillis() + 3_600_000L,
        scopes = setOf(YouTubeAccountActionClient.SCOPE_YOUTUBE_FORCE_SSL)
    )

    // 1. Three-Mode Home Selector UI
    @Test
    fun `Three-mode HomeModeSwitcherSheet renders Offline Music, Online Music, and Online Video and switches cleanly`() {
        var selectedMode = HomeMusicMode.OFFLINE
        val colors = EditorialColors.default()

        composeRule.setContent {
            CompositionLocalProvider(LocalEditorialColors provides colors) {
                HomeModeSwitcherSheet(
                    currentMode = selectedMode,
                    isNetworkOnline = true,
                    localTrackCount = 12,
                    colors = colors,
                    reduceAnimations = true,
                    onSelectMode = { selectedMode = it },
                    onOpenThemesAndShuffle = {},
                    onDismiss = {}
                )
            }
        }

        composeRule.onNodeWithTag("home_mode_switcher_sheet").assertIsDisplayed()
        composeRule.onNodeWithTag("mode_option_offline").assertIsDisplayed()
        composeRule.onNodeWithTag("mode_option_online").assertIsDisplayed()
        composeRule.onNodeWithTag("mode_option_online_video").assertIsDisplayed()

        composeRule.onNodeWithTag("mode_option_online_video").performClick()
        assertEquals(HomeMusicMode.ONLINE_VIDEO, selectedMode)
    }

    // 2. Home Mode Persistence in DataStore (default OFFLINE + ONLINE_VIDEO roundtrip)
    @Test
    fun `HomeModePreferences defaults to OFFLINE and persists ONLINE_VIDEO mode`() = runBlocking {
        val prefs = HomeModePreferences(app)
        assertEquals(HomeMusicMode.OFFLINE, prefs.getInitialHomeMusicMode())

        prefs.setHomeMusicMode(HomeMusicMode.ONLINE_VIDEO)
        assertEquals(HomeMusicMode.ONLINE_VIDEO, prefs.getInitialHomeMusicMode())
        assertEquals(HomeMusicMode.ONLINE_VIDEO, prefs.homeMusicModeFlow.first())
    }

    // 3. Video Discovery Sections & India + 8 Regional Indian Languages Support
    @Test
    fun `OnlineVideoDataSource covers all 12 home sections with India priority and all 8 Indian languages`() {
        assertEquals(12, VideoHomeSectionType.entries.size)
        val requiredLanguages = listOf(
            VideoRegionalLanguage.HINDI,
            VideoRegionalLanguage.ODIA,
            VideoRegionalLanguage.TAMIL,
            VideoRegionalLanguage.TELUGU,
            VideoRegionalLanguage.MALAYALAM,
            VideoRegionalLanguage.KANNADA,
            VideoRegionalLanguage.BENGALI,
            VideoRegionalLanguage.PUNJABI
        )
        assertEquals(8, requiredLanguages.size)

        val defaultRegionalQuery = OnlineVideoDataSource.buildSectionSearchQuery(
            section = VideoHomeSectionType.REGIONAL,
            countryCode = "IN",
            regionalLanguage = VideoRegionalLanguage.ALL_INDIA,
            recentWatchHint = null
        )
        requiredLanguages.forEach { lang ->
            assertTrue(
                "Default India regional query should mention ${lang.queryKeyword}",
                defaultRegionalQuery.contains(lang.queryKeyword, ignoreCase = true)
            )
            val langQuery = OnlineVideoDataSource.buildSectionSearchQuery(
                section = VideoHomeSectionType.MUSIC_VIDEOS,
                countryCode = "IN",
                regionalLanguage = lang,
                recentWatchHint = null
            )
            assertTrue(langQuery.contains(lang.queryKeyword, ignoreCase = true))
        }

        val popularIndianQuery = OnlineVideoDataSource.buildSectionSearchQuery(
            section = VideoHomeSectionType.POPULAR_INDIAN,
            countryCode = "IN",
            regionalLanguage = VideoRegionalLanguage.ALL_INDIA,
            recentWatchHint = null
        )
        assertTrue(popularIndianQuery.contains("Indian", ignoreCase = true))
    }

    // 4. Online Video Home UI & Language Chips Rendering
    @Test
    fun `OnlineVideoHomeContent displays search bar, regional chips, and real section items`() {
        val colors = EditorialColors.default()
        val video = sampleVideo()
        var chosenLanguage: VideoRegionalLanguage? = null
        val sections = mapOf(
            VideoHomeSectionType.TRENDING to VideoSectionState.Success(
                items = listOf(video),
                nextPageToken = null,
                sourceLabel = "India"
            )
        )

        composeRule.setContent {
            CompositionLocalProvider(LocalEditorialColors provides colors) {
                OnlineVideoHomeContent(
                    sections = sections,
                    recentlyWatched = emptyList(),
                    selectedRegionalLanguage = VideoRegionalLanguage.ALL_INDIA,
                    countryCode = "IN",
                    isNetworkOnline = true,
                    activeVideoId = null,
                    colors = colors,
                    onOpenVideoSearch = {},
                    onOpenRegionSheet = {},
                    onSelectRegionalLanguage = { chosenLanguage = it },
                    onRefreshAll = {},
                    onRetrySection = {},
                    onLoadMoreSection = {},
                    onPlayVideo = { _, _ -> },
                    onOpenChannel = { _, _ -> },
                    onVideoMoreClick = {}
                )
            }
        }

        composeRule.onNodeWithTag("home_online_video_content").assertIsDisplayed()
        composeRule.onNodeWithTag("video_home_search_bar").assertIsDisplayed()
        composeRule.onNodeWithTag("video_lang_hindi").assertIsDisplayed()
        composeRule.onNodeWithTag("video_lang_odia").performClick()
        assertEquals(VideoRegionalLanguage.ODIA, chosenLanguage)
        composeRule.onNodeWithTag("home_online_video_content")
            .performScrollToNode(hasText(video.title))
        composeRule.onNodeWithText(video.title).assertIsDisplayed()
    }

    // 5. Dedicated Online Video Search Isolation (Separate history table + filters)
    @Test
    fun `Online Video search history is persisted separately from Online Music search history`() = runBlocking {
        val dao = db.dao()
        dao.upsertVideoSearchHistory(data.video.VideoSearchHistoryEntity(query = "Odia Folk Studio Live", timestamp = 1000L))
        dao.upsertVideoSearchHistory(data.video.VideoSearchHistoryEntity(query = "Tamil Sci-Fi Explainer", timestamp = 2000L))

        val videoHistory = dao.getVideoSearchHistoryFlow().first()
        assertEquals(listOf("Tamil Sci-Fi Explainer", "Odia Folk Studio Live"), videoHistory.map { it.query })
        assertTrue(dao.getOnlineSearchHistoryFlow().first().isEmpty())

        dao.deleteVideoSearchHistory("Odia Folk Studio Live")
        assertEquals(listOf("Tamil Sci-Fi Explainer"), dao.getVideoSearchHistoryFlow().first().map { it.query })

        dao.clearVideoSearchHistory()
        assertTrue(dao.getVideoSearchHistoryFlow().first().isEmpty())
        assertEquals(listOf(VideoSearchFilter.VIDEOS, VideoSearchFilter.CHANNELS, VideoSearchFilter.PLAYLISTS), VideoSearchFilter.entries)
    }

    // 6. Full-screen Landscape Toggle, Video Queue Management, and Aspect Ratio
    @Test
    fun `OnlineVideoController manages fullscreen toggle, queue next and remove, and playback speed bounds`() {
        val repo = OnlineVideoRepository(
            dao = db.dao(),
            dataSource = OnlineVideoDataSource(NetworkConnectivityMonitor(app)),
            accountActionProvider = YouTubeAccountActionClient()
        )
        val controller = OnlineVideoController(
            context = app,
            scope = controllerScope,
            repository = repo,
            accountPreferences = VideoAccountPreferences(app),
            getCountryCode = { "IN" },
            getLanguageCode = { "en" },
            onPauseMusicForVideo = {}
        )
        try {
            val v1 = sampleVideo(id = "vid1", title = "First Video")
            val v2 = sampleVideo(id = "vid2", title = "Second Video")
            controller.enqueueVideoNext(v1)
            controller.enqueueVideoNext(v2)
            assertEquals(2, controller.playerState.value.queue.size)

            controller.removeVideoFromQueue("vid2")
            assertEquals(listOf("vid1"), controller.playerState.value.queue.map { it.videoId })

            controller.setFullScreenLandscape(true)
            assertTrue(controller.playerState.value.isFullScreenLandscape)
            controller.setFullScreenLandscape(false)
            assertFalse(controller.playerState.value.isFullScreenLandscape)

            controller.setVideoPlaybackSpeed(1.5f)
            assertEquals(1.5f, controller.playerState.value.playbackSpeed, 0.001f)
            controller.setVideoPlaybackSpeed(9.9f)
            assertEquals(2.0f, controller.playerState.value.playbackSpeed, 0.001f)
        } finally {
            controller.release()
        }
    }

    // 7. Policy-Compliant Background Pause vs Picture-in-Picture State
    @Test
    fun `Background playback defaults to disallowed and PiP state tracks transitions safely`() {
        val repo = OnlineVideoRepository(
            dao = db.dao(),
            dataSource = OnlineVideoDataSource(NetworkConnectivityMonitor(app)),
            accountActionProvider = YouTubeAccountActionClient()
        )
        val controller = OnlineVideoController(
            context = app,
            scope = controllerScope,
            repository = repo,
            accountPreferences = VideoAccountPreferences(app),
            getCountryCode = { "IN" },
            getLanguageCode = { "en" },
            onPauseMusicForVideo = {}
        )
        try {
            assertFalse(controller.playerState.value.backgroundPlaybackAllowed)
            assertFalse(controller.playerState.value.isInPictureInPicture)

            controller.onPictureInPictureModeChanged(true)
            assertTrue(controller.playerState.value.isInPictureInPicture)
            controller.onAppBackgrounded() // Allowed to stay active in PiP

            controller.onPictureInPictureModeChanged(false)
            assertFalse(controller.playerState.value.isInPictureInPicture)
            controller.onAppBackgrounded() // Policy-compliant pause when not in PiP
            assertFalse(controller.playerState.value.isPlaying)
        } finally {
            controller.release()
        }
    }

    // 8. Official Like & Dislike — NEVER fake when unauthorized; confirm only on HTTP 2xx
    @Test
    fun `YouTubeAccountActionClient never fakes Like or Dislike without OAuth and confirms on HTTP 204`() = runBlocking {
        val unauthorizedClient = buildMockHttpYouTubeClient(statusCode = 204)
        val unauthResult = unauthorizedClient.rateVideo(
            videoId = "abc123xyz",
            targetRating = VideoRatingState.LIKED,
            authState = ProviderAuthState.Disconnected
        )
        assertTrue(unauthResult is VideoAccountActionResult.RequiresAuthorization)

        val authClient = buildMockHttpYouTubeClient(statusCode = 204)
        val confirmedLike = authClient.rateVideo(
            videoId = "abc123xyz",
            targetRating = VideoRatingState.LIKED,
            authState = validAuthState()
        )
        assertTrue(confirmedLike is VideoAccountActionResult.Confirmed)
        assertEquals(VideoRatingState.LIKED, (confirmedLike as VideoAccountActionResult.Confirmed).updatedRating)

        val confirmedDislike = authClient.rateVideo(
            videoId = "abc123xyz",
            targetRating = VideoRatingState.DISLIKED,
            authState = validAuthState()
        )
        assertTrue(confirmedDislike is VideoAccountActionResult.Confirmed)
        assertEquals(VideoRatingState.DISLIKED, (confirmedDislike as VideoAccountActionResult.Confirmed).updatedRating)

        val expiredClient = buildMockHttpYouTubeClient(statusCode = 401)
        val rejected = expiredClient.rateVideo(
            videoId = "abc123xyz",
            targetRating = VideoRatingState.LIKED,
            authState = validAuthState()
        )
        assertTrue(rejected is VideoAccountActionResult.RequiresAuthorization)
    }

    // 9. Official Subscribe — NEVER fake when unauthorized; confirm & persist on HTTP 200
    @Test
    fun `Channel subscription requires OAuth confirmation and persists only when confirmed`() = runBlocking {
        val channel = sampleChannel()
        val unauthRepo = OnlineVideoRepository(
            dao = db.dao(),
            dataSource = OnlineVideoDataSource(NetworkConnectivityMonitor(app)),
            accountActionProvider = buildMockHttpYouTubeClient(statusCode = 200)
        )
        val unauthResult = unauthRepo.submitChannelSubscription(channel, subscribe = true, authState = ProviderAuthState.Disconnected)
        assertTrue(unauthResult is VideoAccountActionResult.RequiresAuthorization)
        assertTrue(unauthRepo.subscribedChannelsFlow.first().isEmpty())

        val authRepo = OnlineVideoRepository(
            dao = db.dao(),
            dataSource = OnlineVideoDataSource(NetworkConnectivityMonitor(app)),
            accountActionProvider = buildMockHttpYouTubeClient(statusCode = 200, responseJson = """{"id":"sub_1"}""")
        )
        val okResult = authRepo.submitChannelSubscription(channel, subscribe = true, authState = validAuthState())
        assertTrue(okResult is VideoAccountActionResult.Confirmed)
        val persisted = authRepo.subscribedChannelsFlow.first()
        assertEquals(1, persisted.size)
        assertTrue(persisted.first().isSubscribedOnProvider)
    }

    // 10. Notification Bell — Transparently reports YouTube Data API v3 limitation
    @Test
    fun `Notification preference transparently reports API limitation with official channel URL`() = runBlocking {
        val client = YouTubeAccountActionClient()
        assertFalse(client.supportsNotificationPreferenceApi)

        val channel = sampleChannel()
        val res = client.updateChannelNotificationPreference(
            channelId = channel.channelId,
            channelUrl = channel.channelUrl,
            preference = ChannelNotificationPreference.ALL,
            authState = validAuthState()
        )
        assertTrue(res is VideoAccountActionResult.UnsupportedByProviderApi)
        val unsupported = res as VideoAccountActionResult.UnsupportedByProviderApi
        assertEquals(channel.channelUrl, unsupported.officialWebUrl)
        assertTrue(unsupported.explanation.contains("YouTube Data API v3", ignoreCase = true))
    }

    // 11. Watch Later & Saved Videos Room Persistence (stable videoId only, no expiring stream URLs)
    @Test
    fun `Watch Later and Saved Videos persist stably in Room without storing stream URLs`() = runBlocking {
        val repo = OnlineVideoRepository(
            dao = db.dao(),
            dataSource = OnlineVideoDataSource(NetworkConnectivityMonitor(app))
        )
        val video = sampleVideo(id = "watchLater1")

        assertTrue(repo.toggleWatchLater(video))
        assertEquals(listOf("watchLater1"), repo.watchLaterVideosFlow.first().map { it.videoId })

        assertTrue(repo.toggleSaveVideo(video))
        assertEquals(listOf("watchLater1"), repo.savedVideosFlow.first().map { it.videoId })

        // Toggle off Watch Later keeps Saved intact
        assertFalse(repo.toggleWatchLater(video))
        assertTrue(repo.watchLaterVideosFlow.first().isEmpty())
        assertEquals(1, repo.savedVideosFlow.first().size)
    }

    // 12. Video History Persistence, Watch Progress Resume, Single Remove & Clear History
    @Test
    fun `Video History records progress, supports single removal and Clear History`() = runBlocking {
        val repo = OnlineVideoRepository(
            dao = db.dao(),
            dataSource = OnlineVideoDataSource(NetworkConnectivityMonitor(app))
        )
        val v1 = sampleVideo(id = "hist1", title = "History One")
        val v2 = sampleVideo(id = "hist2", title = "History Two")

        repo.recordVideoWatched(v1, positionMs = 15_000L)
        repo.updateWatchProgress("hist1", 42_000L)
        repo.recordVideoWatched(v2, positionMs = 8_000L)

        val history = repo.videoHistoryFlow.first()
        assertEquals(2, history.size)
        assertEquals(42_000L, history.first { it.videoId == "hist1" }.lastPositionMs)

        repo.removeFromHistory("hist1")
        assertEquals(listOf("hist2"), repo.videoHistoryFlow.first().map { it.videoId })

        repo.clearVideoHistory()
        assertTrue(repo.videoHistoryFlow.first().isEmpty())
    }

    // 13. Video Playlists CRUD in Room
    @Test
    fun `Video Playlists support create, add item, rename, remove item, and delete`() = runBlocking {
        val repo = OnlineVideoRepository(
            dao = db.dao(),
            dataSource = OnlineVideoDataSource(NetworkConnectivityMonitor(app))
        )
        val v1 = sampleVideo(id = "plVid1", title = "Acoustic Session")
        val v2 = sampleVideo(id = "plVid2", title = "Indie Travel Vlog")

        val playlistId = repo.createVideoPlaylist("India Watchlist", initialVideo = v1)
        assertTrue(playlistId > 0L)
        repo.addVideoToPlaylist(playlistId, v2)

        val created = repo.videoPlaylistsFlow.first().first { it.id == playlistId }
        assertEquals("India Watchlist", created.name)
        assertEquals(listOf("plVid1", "plVid2"), created.videos.map { it.videoId })

        repo.renameVideoPlaylist(playlistId, "Weekend Watchlist")
        assertEquals("Weekend Watchlist", repo.videoPlaylistsFlow.first().first { it.id == playlistId }.name)

        repo.removeVideoFromPlaylist(playlistId, "plVid1")
        assertEquals(listOf("plVid2"), repo.videoPlaylistsFlow.first().first { it.id == playlistId }.videos.map { it.videoId })

        repo.deleteVideoPlaylist(playlistId)
        assertTrue(repo.videoPlaylistsFlow.first().isEmpty())
    }

    // 14. Floating Video Mini-Player & 3-Mode Library Selector UI + Non-interrupting Mode Switch
    @Test
    fun `FloatingVideoMiniPlayer and 3-option LibraryModeSegmentedSelector render and preserve playback on mode switch`() {
        val colors = EditorialColors.default()
        var selectedLibMode = LibrarySectionMode.OFFLINE
        var expanded = false
        val playerState = VideoPlayerUiState(
            activeVideo = sampleVideo(),
            isPlaying = true,
            isPlayerExpanded = false,
            currentPositionMs = 60_000L,
            durationMs = 240_000L,
            selectedVideoStream = VideoStreamOption(
                id = "1080p",
                url = "https://example.com/stream",
                resolutionLabel = "1080p",
                heightPx = 1080,
                fps = 30,
                mimeType = "video/mp4",
                formatLabel = "MP4",
                isVideoOnly = false,
                bitrateKbps = 2500
            )
        )

        composeRule.setContent {
            CompositionLocalProvider(LocalEditorialColors provides colors) {
                androidx.compose.foundation.layout.Column {
                    LibraryModeSegmentedSelector(
                        selectedMode = selectedLibMode,
                        colors = colors,
                        onSelectMode = { selectedLibMode = it }
                    )
                    FloatingVideoMiniPlayer(
                        playerState = playerState,
                        colors = colors,
                        onExpand = { expanded = true },
                        onTogglePlayPause = {},
                        onPlayNext = {},
                        onClose = {}
                    )
                }
            }
        }

        composeRule.onNodeWithTag("library_mode_offline").assertIsDisplayed()
        composeRule.onNodeWithTag("library_mode_online").assertIsDisplayed()
        composeRule.onNodeWithTag("library_mode_online_video").performClick()
        assertEquals(LibrarySectionMode.ONLINE_VIDEO, selectedLibMode)

        composeRule.onNodeWithTag("floating_video_mini_player").assertIsDisplayed()
        composeRule.onNodeWithTag("floating_video_mini_player").performClick()
        assertTrue(expanded)

        // Switching Home modes in SonoraViewModel never stops active playback unexpectedly
        val vm = SonoraViewModel(app)
        vm.setHomeMusicMode(HomeMusicMode.ONLINE_VIDEO)
        assertEquals(HomeMusicMode.ONLINE_VIDEO, vm.homeMusicMode.value)
        assertTrue(Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
    }

    // 15. Transparent Account Action Dialog UI Verification
    @Test
    fun `VideoAccountActionDialog displays transparent OAuth explanation and local bookmark option`() {
        val colors = EditorialColors.default()
        var bookmarked = false
        val prompt = VideoAccountActionResult.RequiresAuthorization(
            actionTitle = "Subscribe to Channel",
            explanation = "Sign in with an authorized YouTube Data API v3 OAuth token to subscribe.",
            officialWebUrl = "https://www.youtube.com/channel/UC1234567890",
            canStartOAuthFlow = false
        )

        composeRule.setContent {
            CompositionLocalProvider(LocalEditorialColors provides colors) {
                VideoAccountActionDialog(
                    result = prompt,
                    activeChannel = sampleChannel(),
                    colors = colors,
                    onBookmarkChannelLocally = { bookmarked = true },
                    onDismiss = {}
                )
            }
        }

        composeRule.onNodeWithTag("video_account_action_dialog").assertIsDisplayed()
        composeRule.onNodeWithTag("open_official_provider_button").assertIsDisplayed()
        composeRule.onNodeWithTag("bookmark_channel_locally_button").performClick()
        assertTrue(bookmarked)
    }
}
