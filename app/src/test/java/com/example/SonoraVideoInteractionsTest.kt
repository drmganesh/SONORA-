package com.example

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.SonoraDatabase
import data.online.NetworkConnectivityMonitor
import data.video.ChannelUploadDiffDetector
import data.video.OnlineVideoDataSource
import data.video.OnlineVideoRepository
import data.video.VideoFeedbackType
import domain.video.ChannelNotificationPreference
import domain.video.OnlineVideoChannel
import domain.video.OnlineVideoItem
import domain.video.ResolvedVideoDetails
import domain.video.VideoCommentItem
import domain.video.VideoPlayerUiState
import domain.video.VideoRatingState
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import ui.theme.LocalEditorialColors
import ui.video.VideoPlayerScreen

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class SonoraVideoInteractionsTest {

    @get:Rule
    val composeRule = createComposeRule()

    private lateinit var db: SonoraDatabase
    private lateinit var repository: OnlineVideoRepository

    private val sampleVideo = OnlineVideoItem(
        videoId = "vid_sonora_001",
        contentUrl = "https://www.youtube.com/watch?v=vid_sonora_001",
        title = "Studio Acoustic Live Session",
        channelName = "Sonora Sessions",
        channelUrl = "https://www.youtube.com/channel/UCsonora123",
        durationSeconds = 265L,
        viewCount = 125_400L,
        uploadDateText = "2 days ago"
    )

    private val sampleChannel = OnlineVideoChannel(
        channelId = "UCsonora123",
        channelUrl = "https://www.youtube.com/channel/UCsonora123",
        name = "Sonora Sessions",
        subscriberCount = 84_000L
    )

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, SonoraDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val dataSource = OnlineVideoDataSource(NetworkConnectivityMonitor(context))
        repository = OnlineVideoRepository(db.dao(), dataSource)
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `SONORA local Like and Dislike persist in Room v8, stay mutually exclusive, and survive pruning`() = runBlocking {
        // Default rating is NONE
        assertEquals(VideoRatingState.NONE, repository.getLocalVideoRating(sampleVideo.videoId))

        // Activate Like
        repository.setLocalVideoRating(sampleVideo, VideoRatingState.LIKED)
        assertEquals(VideoRatingState.LIKED, repository.getLocalVideoRating(sampleVideo.videoId))

        // Prune must NOT delete locally rated videos
        db.dao().pruneUnreferencedVideos()
        assertEquals(VideoRatingState.LIKED, repository.getLocalVideoRating(sampleVideo.videoId))

        // Verify recommendation feedback synced cleanly
        val likedSignals = db.dao().getAllVideoRecommendationFeedbackFlow().first()
        assertTrue(likedSignals.any { it.feedbackType == VideoFeedbackType.LIKED_VIDEO && it.targetValue == sampleVideo.videoId })
        assertFalse(likedSignals.any { it.feedbackType == VideoFeedbackType.DISLIKED_VIDEO && it.targetValue == sampleVideo.videoId })

        // Switching to Dislike automatically removes Like (mutually exclusive)
        repository.setLocalVideoRating(sampleVideo, VideoRatingState.DISLIKED)
        assertEquals(VideoRatingState.DISLIKED, repository.getLocalVideoRating(sampleVideo.videoId))

        val dislikedSignals = db.dao().getAllVideoRecommendationFeedbackFlow().first()
        assertFalse(dislikedSignals.any { it.feedbackType == VideoFeedbackType.LIKED_VIDEO && it.targetValue == sampleVideo.videoId })
        assertTrue(dislikedSignals.any { it.feedbackType == VideoFeedbackType.DISLIKED_VIDEO && it.targetValue == sampleVideo.videoId })

        // Tapping Dislike again clears back to NONE
        repository.setLocalVideoRating(sampleVideo, VideoRatingState.NONE)
        assertEquals(VideoRatingState.NONE, repository.getLocalVideoRating(sampleVideo.videoId))
        val clearedSignals = db.dao().getAllVideoRecommendationFeedbackFlow().first()
        assertTrue(clearedSignals.none { it.targetValue == sampleVideo.videoId })
    }

    @Test
    fun `SONORA local channel subscription, notification preference, and upload check state persist across restarts`() = runBlocking {
        // Subscribe locally in SONORA (defaults to Notifications Off / NONE)
        repository.saveLocalChannelBookmark(
            channel = sampleChannel,
            providerSynced = false,
            initialLastKnownVideoId = "vid_baseline_01"
        )

        val followedAfterSub = repository.subscribedChannelsFlow.first()
        assertEquals(1, followedAfterSub.size)
        assertEquals(sampleChannel.channelId, followedAfterSub.first().channelId)
        assertEquals(ChannelNotificationPreference.NONE, followedAfterSub.first().notificationPreference)
        assertEquals(false, db.dao().getSubscribedVideoChannelById(sampleChannel.channelId)?.isProviderSynced)

        // Enable All Notifications for this followed channel
        repository.updateLocalChannelNotificationPreference(
            channelId = sampleChannel.channelId,
            preference = ChannelNotificationPreference.ALL
        )
        val afterAll = repository.subscribedChannelsFlow.first()
        assertEquals(ChannelNotificationPreference.ALL, afterAll.first().notificationPreference)

        // Update background upload check state (last known video ID, timestamp, notified IDs)
        val checkedTs = 1_760_000_000_000L
        db.dao().updateChannelUploadCheckState(
            channelId = sampleChannel.channelId,
            lastKnownVideoId = "vid_new_02",
            lastCheckedAt = checkedTs,
            notifiedVideoIdsCsv = "vid_new_02,vid_baseline_01"
        )
        val storedEntity = db.dao().getSubscribedVideoChannelById(sampleChannel.channelId)
        assertNotNull(storedEntity)
        assertEquals("vid_new_02", storedEntity?.lastKnownVideoId)
        assertEquals(checkedTs, storedEntity?.lastCheckedAt)
        assertEquals("vid_new_02,vid_baseline_01", storedEntity?.notifiedVideoIdsCsv)

        // Unsubscribe removes from followed channels cleanly
        repository.removeLocalChannelBookmark(sampleChannel.channelId)
        assertNull(db.dao().getSubscribedVideoChannelById(sampleChannel.channelId))
        assertTrue(repository.subscribedChannelsFlow.first().isEmpty())
    }

    @Test
    fun `ChannelUploadDiffDetector seeds baseline without spam, detects new uploads, and prevents duplicate alerts`() {
        fun video(id: String) = OnlineVideoItem(
            videoId = id,
            contentUrl = "https://www.youtube.com/watch?v=$id",
            title = "Video $id",
            channelName = "Sonora Sessions"
        )

        // 1. First-ever baseline sync (lastKnownVideoId == null): seed without notifying historical uploads
        val initialFeed = listOf(video("v2"), video("v1"))
        val baseline = ChannelUploadDiffDetector.detectNewUploads(
            latestVideos = initialFeed,
            lastKnownVideoId = null,
            previouslyNotifiedIds = emptySet()
        )
        assertTrue(baseline.newUploadsToNotify.isEmpty())
        assertEquals("v2", baseline.updatedLastKnownVideoId)
        assertTrue("v2" in baseline.updatedNotifiedIds)

        // 2. Subsequent check with two brand-new uploads ahead of v2
        val updatedFeed = listOf(video("v4"), video("v3"), video("v2"), video("v1"))
        val diff = ChannelUploadDiffDetector.detectNewUploads(
            latestVideos = updatedFeed,
            lastKnownVideoId = baseline.updatedLastKnownVideoId,
            previouslyNotifiedIds = baseline.updatedNotifiedIds
        )
        assertEquals(listOf("v4", "v3"), diff.newUploadsToNotify.map { it.videoId })
        assertEquals("v4", diff.updatedLastKnownVideoId)
        assertTrue("v4" in diff.updatedNotifiedIds && "v3" in diff.updatedNotifiedIds)

        // 3. Repeated check with identical feed emits zero duplicates
        val repeat = ChannelUploadDiffDetector.detectNewUploads(
            latestVideos = updatedFeed,
            lastKnownVideoId = diff.updatedLastKnownVideoId,
            previouslyNotifiedIds = diff.updatedNotifiedIds
        )
        assertTrue(repeat.newUploadsToNotify.isEmpty())

        // 4. Even if feed order shifts or v4 disappears temporarily, already-notified v3 is never re-notified
        val shiftedFeed = listOf(video("v3"), video("v2"), video("v1"))
        val shifted = ChannelUploadDiffDetector.detectNewUploads(
            latestVideos = shiftedFeed,
            lastKnownVideoId = "v4",
            previouslyNotifiedIds = diff.updatedNotifiedIds
        )
        assertTrue(shifted.newUploadsToNotify.isEmpty())
    }

    @Test
    fun `VideoPlayerScreen shows SONORA interaction bar without counts, disables bell until subscribed, and completely omits comments`() {
        val detailsWithProviderCountsAndComments = ResolvedVideoDetails(
            video = sampleVideo,
            description = "Recorded live in analog stereo for SONORA listeners.",
            likeCount = 987_654L,
            dislikeCount = 4_321L,
            viewCount = 125_400L,
            uploadDateText = "2 days ago",
            channelSubscriberCount = 84_000L,
            comments = listOf(
                VideoCommentItem(
                    id = "c1",
                    authorName = "External User",
                    text = "Provider comment that must never be rendered"
                )
            )
        )

        var state by mutableStateOf(
            VideoPlayerUiState(
                activeVideo = sampleVideo,
                resolvedDetails = detailsWithProviderCountsAndComments,
                ratingState = VideoRatingState.NONE,
                isSubscribedLocally = false,
                notificationPreference = ChannelNotificationPreference.NONE
            )
        )

        var likeClicks = 0
        var dislikeClicks = 0

        composeRule.setContent {
            VideoPlayerScreen(
                playerState = state,
                watchLaterIds = emptySet(),
                savedVideoIds = emptySet(),
                videoPlaylists = emptyList(),
                colors = LocalEditorialColors.current,
                onAttachSurface = {},
                onDetachSurface = {},
                onTogglePlayPause = {},
                onSeekTo = {},
                onSeekBy = {},
                onPlayNext = {},
                onPlayPrevious = {},
                onSelectStream = {},
                onSelectAudio = {},
                onSelectSubtitle = {},
                onSelectSpeed = {},
                onToggleFullScreen = {},
                onMinimize = {},
                onRetry = {},
                onToggleLike = {
                    likeClicks++
                    state = state.copy(
                        ratingState = if (state.ratingState == VideoRatingState.LIKED) {
                            VideoRatingState.NONE
                        } else {
                            VideoRatingState.LIKED
                        }
                    )
                },
                onToggleDislike = {
                    dislikeClicks++
                    state = state.copy(
                        ratingState = if (state.ratingState == VideoRatingState.DISLIKED) {
                            VideoRatingState.NONE
                        } else {
                            VideoRatingState.DISLIKED
                        }
                    )
                },
                onToggleSubscribe = {
                    val nowSub = !state.isSubscribedLocally
                    state = state.copy(
                        isSubscribedLocally = nowSub,
                        notificationPreference = if (nowSub) state.notificationPreference else ChannelNotificationPreference.NONE
                    )
                },
                onSelectNotificationPref = { _, pref ->
                    state = state.copy(notificationPreference = pref)
                },
                onToggleWatchLater = {},
                onToggleSaveVideo = {},
                onAddVideoToPlaylist = { _, _, _ -> },
                onCreateVideoPlaylist = { _, _ -> },
                onPlayRelatedVideo = { _, _ -> },
                onRemoveFromQueue = {},
                onOpenChannel = { _, _ -> },
                onVideoMoreClick = {}
            )
        }

        composeRule.waitForIdle()

        // 1. Custom SONORA Interaction Bar elements are present with NO like/dislike counts
        composeRule.onNodeWithTag("sonora_interaction_bar").assertIsDisplayed()
        composeRule.onNodeWithTag("sonora_like_button").assertIsDisplayed()
        composeRule.onNodeWithTag("sonora_dislike_button").assertIsDisplayed()
        composeRule.onNodeWithTag("sonora_share_button").assertIsDisplayed()
        composeRule.onNodeWithTag("sonora_save_button").assertIsDisplayed()

        composeRule.onNodeWithText("Like").assertIsDisplayed()
        composeRule.onNodeWithText("Dislike").assertIsDisplayed()
        composeRule.onNodeWithText("Share").assertIsDisplayed()
        composeRule.onNodeWithText("Save").assertIsDisplayed()

        // Provider like count ("987.7K") and dislike count ("4.3K") must NEVER be displayed
        assertTrue(composeRule.onAllNodesWithText("987.7K", substring = true).fetchSemanticsNodes().isEmpty())
        assertTrue(composeRule.onAllNodesWithText("987654", substring = true).fetchSemanticsNodes().isEmpty())
        assertTrue(composeRule.onAllNodesWithText("4.3K", substring = true).fetchSemanticsNodes().isEmpty())

        // 2. Comments section is completely removed even if details.comments is populated
        assertTrue(composeRule.onAllNodesWithText("Comments", substring = true).fetchSemanticsNodes().isEmpty())
        assertTrue(
            composeRule.onAllNodesWithText("Provider comment that must never be rendered", substring = true)
                .fetchSemanticsNodes().isEmpty()
        )

        // 3. Bell is disabled when not subscribed, then becomes enabled after subscribing
        composeRule.onNodeWithTag("sonora_subscribe_button").assertIsDisplayed()
        composeRule.onNodeWithText("Subscribe").assertIsDisplayed()
        composeRule.onNodeWithTag("sonora_notification_bell_button").assertIsNotEnabled()

        composeRule.onNodeWithTag("sonora_subscribe_button").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Subscribed").assertIsDisplayed()
        composeRule.onNodeWithTag("sonora_notification_bell_button").assertIsEnabled()

        // 4. Like and Dislike mutual exclusion interaction
        composeRule.onNodeWithTag("sonora_like_button").performClick()
        composeRule.waitForIdle()
        assertEquals(1, likeClicks)
        assertEquals(VideoRatingState.LIKED, state.ratingState)

        composeRule.onNodeWithTag("sonora_dislike_button").performClick()
        composeRule.waitForIdle()
        assertEquals(1, dislikeClicks)
        assertEquals(VideoRatingState.DISLIKED, state.ratingState)
    }
}
