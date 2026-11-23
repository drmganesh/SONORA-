package ui.video

import android.app.Application
import android.os.Build
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.SonoraDatabase
import data.online.NetworkConnectivityMonitor
import data.video.OnlineVideoDataSource
import data.video.OnlineVideoRepository
import domain.video.ChannelExternalLink
import domain.video.ChannelPlaylistDetailState
import domain.video.ChannelTabPageState
import domain.video.ChannelTabType
import domain.video.OnlineVideoChannel
import domain.video.OnlineVideoItem
import domain.video.OnlineVideoPlaylistSummary
import domain.video.ProviderAuthState
import domain.video.VideoChannelUiState
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import ui.theme.EditorialColors
import ui.theme.LocalEditorialColors

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [Build.VERSION_CODES.O, 36])
class ChannelDetailScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun sampleChannel(
        availableTabs: List<ChannelTabType> = listOf(
            ChannelTabType.VIDEOS,
            ChannelTabType.SHORTS,
            ChannelTabType.PLAYLISTS,
            ChannelTabType.ABOUT
        ),
        isFollowedLocal: Boolean = false,
        isSubscribedProvider: Boolean = false
    ) = OnlineVideoChannel(
        channelId = "UC_sonora_live_01",
        stableId = 101L,
        channelUrl = "https://www.youtube.com/@SonoraLiveSessions",
        name = "Sonora Live Sessions",
        avatarUrl = null,
        bannerUrl = null,
        subscriberCount = 1_250_000L,
        description = "Acoustic sessions recorded live in Mumbai.\nhttps://sonoralive.example.com",
        isVerified = true,
        isSubscribedLocal = isFollowedLocal,
        isSubscribedOnProvider = isSubscribedProvider,
        handle = "@SonoraLiveSessions",
        tags = listOf("Acoustic", "Live"),
        externalLinks = listOf(ChannelExternalLink("sonoralive.example.com", "https://sonoralive.example.com")),
        availableTabs = availableTabs
    )

    private fun sampleVideos() = listOf(
        OnlineVideoItem(
            videoId = "vid_ch_1",
            contentUrl = "https://www.youtube.com/watch?v=vid_ch_1",
            title = "Monsoon Raga Unplugged",
            channelName = "Sonora Live Sessions",
            channelUrl = "https://www.youtube.com/@SonoraLiveSessions",
            durationSeconds = 320L,
            viewCount = 480_000L,
            uploadDateText = "2 days ago"
        ),
        OnlineVideoItem(
            videoId = "vid_ch_2",
            contentUrl = "https://www.youtube.com/watch?v=vid_ch_2",
            title = "Midnight Sitar Improvisation",
            channelName = "Sonora Live Sessions",
            channelUrl = "https://www.youtube.com/@SonoraLiveSessions",
            durationSeconds = 410L,
            viewCount = 125_000L,
            uploadDateText = "1 week ago"
        )
    )

    private fun sampleShorts() = listOf(
        OnlineVideoItem(
            videoId = "short_1",
            contentUrl = "https://www.youtube.com/shorts/short_1",
            title = "60-Second Tabla Groove",
            channelName = "Sonora Live Sessions",
            channelUrl = "https://www.youtube.com/@SonoraLiveSessions",
            durationSeconds = 58L,
            viewCount = 92_000L,
            isShort = true
        )
    )

    private fun samplePlaylists() = listOf(
        OnlineVideoPlaylistSummary(
            externalUrl = "https://www.youtube.com/playlist?list=PL_sonora_01",
            name = "Full Studio Performances",
            channelName = "Sonora Live Sessions",
            videoCount = 14L
        )
    )

    @Test
    fun channelDetailScreen_unauthenticatedShowsHonestLocalFollowAndQuickActions() {
        val colors = EditorialColors.default()
        var followToggled = 0
        var playAllCalled = 0
        var backgroundCalled = 0
        var popupCalled = 0
        var playedVideoId: String? = null

        val videos = sampleVideos()
        val channel = sampleChannel(isFollowedLocal = false)
        val uiState = VideoChannelUiState.Success(
            channel = channel.copy(videos = videos),
            selectedTab = ChannelTabType.VIDEOS,
            availableTabs = channel.availableTabs,
            tabStates = mapOf(
                ChannelTabType.VIDEOS to ChannelTabPageState.Success(videos = videos, nextPageToken = "page_2"),
                ChannelTabType.ABOUT to ChannelTabPageState.Success()
            )
        )

        composeRule.setContent {
            CompositionLocalProvider(LocalEditorialColors provides colors) {
                ChannelDetailScreen(
                    state = uiState,
                    authState = ProviderAuthState.Disconnected,
                    supportsNotificationApi = false,
                    activeVideoId = null,
                    watchProgressByVideoId = mapOf("vid_ch_1" to 0.55f),
                    hasBottomMiniPlayer = false,
                    colors = colors,
                    onBack = {},
                    onRetryChannel = { _, _ -> },
                    onRefreshChannel = {},
                    onSelectTab = {},
                    onRetryTab = {},
                    onLoadMoreTab = {},
                    onPlayVideo = { v, _ -> playedVideoId = v.videoId },
                    onPlayAll = { playAllCalled++ },
                    onPlayBackground = { _, _ -> backgroundCalled++ },
                    onPlayPopup = { popupCalled++ },
                    onOpenPlaylist = {},
                    onToggleFollowLocal = { followToggled++ },
                    onToggleSubscribeProvider = {},
                    onSelectNotificationPref = { _, _ -> },
                    onVideoMoreClick = {}
                )
            }
        }

        // Banner fallback, metadata, and honest local Follow button (NOT fake YouTube Subscribe)
        composeRule.onNodeWithTag("channel_banner_box").assertIsDisplayed()
        composeRule.onNodeWithTag("channel_subscriber_meta").assertIsDisplayed()
        composeRule.onNodeWithTag("channel_follow_button").assertIsDisplayed().performClick()
        assertEquals(1, followToggled)
        composeRule.onNodeWithTag("channel_subscribe_button").assertDoesNotExist()
        composeRule.onNodeWithTag("channel_notification_bell_button").assertDoesNotExist()

        // Quick Actions Bar: Background, Play All, Popup
        composeRule.onNodeWithTag("channel_action_background").performClick()
        composeRule.onNodeWithTag("channel_action_play_all").performClick()
        composeRule.onNodeWithTag("channel_action_popup").performClick()
        assertEquals(1, backgroundCalled)
        assertEquals(1, playAllCalled)
        assertEquals(1, popupCalled)

        // Scroll down past the channel header to verify watch progress indicator and video click
        composeRule.onNodeWithTag("channel_tab_content_videos")
            .performScrollToNode(hasTestTag("compact_video_row_vid_ch_1"))
        composeRule.onNodeWithTag("video_progress_bar_vid_ch_1", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithTag("compact_video_row_vid_ch_1").performClick()
        assertEquals("vid_ch_1", playedVideoId)
    }

    @Test
    fun channelDetailScreen_authenticatedShowsSubscribeAndNotificationBell() {
        val colors = EditorialColors.default()
        var subscribeClicks = 0
        val channel = sampleChannel(isSubscribedProvider = true)
        val uiState = VideoChannelUiState.Success(
            channel = channel,
            selectedTab = ChannelTabType.VIDEOS,
            availableTabs = listOf(ChannelTabType.VIDEOS, ChannelTabType.ABOUT),
            tabStates = mapOf(ChannelTabType.VIDEOS to ChannelTabPageState.Success(videos = sampleVideos()))
        )

        composeRule.setContent {
            CompositionLocalProvider(LocalEditorialColors provides colors) {
                ChannelDetailScreen(
                    state = uiState,
                    authState = ProviderAuthState.Connected(
                        accountLabel = "user@example.com",
                        accessToken = "valid_token",
                        expiresAtMs = System.currentTimeMillis() + 3_600_000L,
                        scopes = setOf("youtube")
                    ),
                    supportsNotificationApi = true,
                    activeVideoId = null,
                    watchProgressByVideoId = emptyMap(),
                    hasBottomMiniPlayer = false,
                    colors = colors,
                    onBack = {},
                    onRetryChannel = { _, _ -> },
                    onRefreshChannel = {},
                    onSelectTab = {},
                    onRetryTab = {},
                    onLoadMoreTab = {},
                    onPlayVideo = { _, _ -> },
                    onPlayAll = {},
                    onPlayBackground = { _, _ -> },
                    onPlayPopup = {},
                    onOpenPlaylist = {},
                    onToggleFollowLocal = {},
                    onToggleSubscribeProvider = { subscribeClicks++ },
                    onSelectNotificationPref = { _, _ -> },
                    onVideoMoreClick = {}
                )
            }
        }

        // Only supported tabs are shown (SHORTS and PLAYLISTS hidden when unavailable)
        composeRule.onNodeWithTag("channel_tab_videos").assertIsDisplayed()
        composeRule.onNodeWithTag("channel_tab_about").assertIsDisplayed()
        composeRule.onNodeWithTag("channel_tab_shorts").assertDoesNotExist()
        composeRule.onNodeWithTag("channel_tab_playlists").assertDoesNotExist()

        // Subscribe + Notification bell visible for authenticated sessions
        composeRule.onNodeWithTag("channel_subscribe_button").assertIsDisplayed().performClick()
        assertEquals(1, subscribeClicks)
        composeRule.onNodeWithTag("channel_notification_bell_button").assertIsDisplayed()
    }

    @Test
    fun channelDetailScreen_tabSwitchingRendersShortsPlaylistsAndAbout() {
        val colors = EditorialColors.default()
        val channel = sampleChannel()
        val videos = sampleVideos()
        val shorts = sampleShorts()
        val playlists = samplePlaylists()
        var openedPlaylistName: String? = null

        var uiState by mutableStateOf(
            VideoChannelUiState.Success(
                channel = channel,
                selectedTab = ChannelTabType.VIDEOS,
                availableTabs = channel.availableTabs,
                tabStates = mapOf(
                    ChannelTabType.VIDEOS to ChannelTabPageState.Success(videos = videos),
                    ChannelTabType.SHORTS to ChannelTabPageState.Success(videos = shorts),
                    ChannelTabType.PLAYLISTS to ChannelTabPageState.Success(playlists = playlists),
                    ChannelTabType.ABOUT to ChannelTabPageState.Success()
                )
            )
        )

        composeRule.setContent {
            CompositionLocalProvider(LocalEditorialColors provides colors) {
                ChannelDetailScreen(
                    state = uiState,
                    authState = ProviderAuthState.Disconnected,
                    supportsNotificationApi = false,
                    activeVideoId = null,
                    watchProgressByVideoId = emptyMap(),
                    hasBottomMiniPlayer = false,
                    colors = colors,
                    onBack = {},
                    onRetryChannel = { _, _ -> },
                    onRefreshChannel = {},
                    onSelectTab = { tab -> uiState = uiState.copy(selectedTab = tab) },
                    onRetryTab = {},
                    onLoadMoreTab = {},
                    onPlayVideo = { _, _ -> },
                    onPlayAll = {},
                    onPlayBackground = { _, _ -> },
                    onPlayPopup = {},
                    onOpenPlaylist = { openedPlaylistName = it.name },
                    onToggleFollowLocal = {},
                    onToggleSubscribeProvider = {},
                    onSelectNotificationPref = { _, _ -> },
                    onVideoMoreClick = {}
                )
            }
        }

        // Switch to SHORTS tab
        composeRule.onNodeWithTag("channel_tab_shorts").performScrollTo().performClick()
        composeRule.onNodeWithTag("channel_tab_content_shorts")
            .performScrollToNode(hasTestTag("channel_short_card_short_1"))
        composeRule.onNodeWithTag("channel_short_card_short_1").assertIsDisplayed()

        // Switch to PLAYLISTS tab and tap a playlist
        composeRule.onNodeWithTag("channel_tab_playlists").performScrollTo().performClick()
        composeRule.onNodeWithTag("channel_tab_content_playlists")
            .performScrollToNode(hasTestTag("channel_playlist_card_Full Studio Performances"))
        composeRule.onNodeWithTag("channel_playlist_card_Full Studio Performances").assertIsDisplayed().performClick()
        assertEquals("Full Studio Performances", openedPlaylistName)

        // Switch to ABOUT tab
        composeRule.onNodeWithTag("channel_tab_about").performScrollTo().performClick()
        composeRule.onNodeWithTag("channel_tab_content_about").assertIsDisplayed()
        composeRule.onNodeWithText("sonoralive.example.com").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun channelDetailScreen_errorStateShowsRetryButtonAndInvokesCallback() {
        val colors = EditorialColors.default()
        var retriedUrl: String? = null

        composeRule.setContent {
            CompositionLocalProvider(LocalEditorialColors provides colors) {
                ChannelDetailScreen(
                    state = VideoChannelUiState.Error(
                        message = "Network timeout while loading channel.",
                        channelUrl = "https://www.youtube.com/@SonoraLiveSessions",
                        channelName = "Sonora Live Sessions"
                    ),
                    authState = ProviderAuthState.Disconnected,
                    supportsNotificationApi = false,
                    activeVideoId = null,
                    watchProgressByVideoId = emptyMap(),
                    hasBottomMiniPlayer = false,
                    colors = colors,
                    onBack = {},
                    onRetryChannel = { url, _ -> retriedUrl = url },
                    onRefreshChannel = {},
                    onSelectTab = {},
                    onRetryTab = {},
                    onLoadMoreTab = {},
                    onPlayVideo = { _, _ -> },
                    onPlayAll = {},
                    onPlayBackground = { _, _ -> },
                    onPlayPopup = {},
                    onOpenPlaylist = {},
                    onToggleFollowLocal = {},
                    onToggleSubscribeProvider = {},
                    onSelectNotificationPref = { _, _ -> },
                    onVideoMoreClick = {}
                )
            }
        }

        composeRule.onNodeWithTag("channel_error_view").assertIsDisplayed()
        composeRule.onNodeWithTag("channel_retry_button").performClick()
        assertEquals("https://www.youtube.com/@SonoraLiveSessions", retriedUrl)
    }

    @Test
    fun videoCards_clickingChannelNameTriggersChannelNavigation() {
        val colors = EditorialColors.default()
        val video = sampleVideos().first()
        var channelClickCount = 0

        composeRule.setContent {
            CompositionLocalProvider(LocalEditorialColors provides colors) {
                CompactVideoRowCard(
                    video = video,
                    colors = colors,
                    onClick = {},
                    onChannelClick = { channelClickCount++ },
                    onMoreClick = {}
                )
            }
        }

        composeRule.onNodeWithTag("compact_channel_name_vid_ch_1").assertIsDisplayed().performClick()
        assertEquals(1, channelClickCount)
    }

    @Test
    fun localFollowChannel_persistsInRoomWithoutPretendingProviderSubscription() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val db = Room.inMemoryDatabaseBuilder(app, SonoraDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        try {
            val repo = OnlineVideoRepository(
                dao = db.dao(),
                dataSource = OnlineVideoDataSource(NetworkConnectivityMonitor(app))
            )
            val channel = sampleChannel()

            runBlocking {
                repo.saveLocalChannelBookmark(channel, providerSynced = false)
                val followed = withTimeout(5_000L) {
                    repo.subscribedChannelsFlow.first { it.any { c -> c.channelId == channel.channelId } }
                }
                val saved = followed.first { it.channelId == channel.channelId }
                assertTrue(saved.isSubscribedLocal)
                assertFalse(saved.isSubscribedOnProvider)
                assertEquals(channel.channelUrl, saved.channelUrl)

                repo.removeLocalChannelBookmark(channel.channelId)
                val afterUnfollow = withTimeout(5_000L) {
                    repo.subscribedChannelsFlow.first { list -> list.none { it.channelId == channel.channelId } }
                }
                assertTrue(afterUnfollow.isEmpty())
            }
        } finally {
            db.close()
        }
    }
}
