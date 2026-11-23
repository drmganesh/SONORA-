package com.example

import android.content.Context
import android.view.SurfaceView
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.media3.common.Player
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.SonoraDatabase
import com.example.model.UserProfile
import data.online.NetworkConnectivityMonitor
import data.preferences.VideoExperienceSettings
import data.video.ChannelUploadSyncEngine
import data.video.OnlineVideoDataSource
import data.video.OnlineVideoRepository
import data.video.SubscribedVideoChannelEntity
import domain.home.HomeMusicMode
import domain.video.ChannelNotificationPreference
import domain.video.OnlineVideoItem
import domain.video.VideoAdaptiveQualitySelector
import domain.video.VideoNotificationItem
import domain.video.VideoNotificationType
import domain.video.VideoPlaybackProgress
import domain.video.VideoPlayerUiState
import domain.video.VideoStreamOption
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import playback.VideoLoadControlProfile
import playback.VideoPlaybackDiagnostics
import ui.home.GreetingHeader
import ui.theme.LocalEditorialColors
import ui.video.VideoNotificationsPanelContent
import ui.video.VideoPlayerScreen

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class SonoraNotificationsAndPerformanceTest {

    @get:Rule
    val composeRule = createComposeRule()

    private lateinit var context: Context
    private lateinit var db: SonoraDatabase
    private lateinit var repository: OnlineVideoRepository

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
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
    @Config(sdk = [26])
    fun `top bar notification bell appears ONLY in Online Video mode on API 26 and shows unread badge`() {
        var mode by mutableStateOf(HomeMusicMode.OFFLINE)
        var unreadCount by mutableStateOf(3)
        var bellTapped = 0

        composeRule.setContent {
            val colors = LocalEditorialColors.current
            GreetingHeader(
                greeting = "Good Evening",
                userProfile = UserProfile(displayName = "Listener"),
                colors = colors,
                activeMode = mode,
                reduceAnimations = true,
                unreadVideoNotificationsCount = unreadCount,
                onVideoNotificationsClick = { bellTapped++ },
                onAvatarClick = {},
                onDiscoveryClick = {},
                onSettingsClick = {}
            )
        }

        // 1. Offline Music mode -> Bell completely hidden
        assertEquals(0, composeRule.onAllNodesWithTag("online_video_notifications_bell").fetchSemanticsNodes().size)

        // 2. Online Music mode -> Bell completely hidden
        composeRule.runOnIdle { mode = HomeMusicMode.ONLINE }
        composeRule.waitForIdle()
        assertEquals(0, composeRule.onAllNodesWithTag("online_video_notifications_bell").fetchSemanticsNodes().size)

        // 3. Online Video mode -> Bell visible with unread indicator badge
        composeRule.runOnIdle { mode = HomeMusicMode.ONLINE_VIDEO }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("online_video_notifications_bell").assertIsDisplayed()
        composeRule.onNodeWithTag("online_video_notifications_badge", useUnmergedTree = true).assertIsDisplayed()

        // Tap opens panel
        composeRule.onNodeWithTag("online_video_notifications_bell").performClick()
        assertEquals(1, bellTapped)

        // When unread drops to 0, unread dot disappears while bell remains
        composeRule.runOnIdle { unreadCount = 0 }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("online_video_notifications_bell").assertIsDisplayed()
        assertEquals(0, composeRule.onAllNodesWithTag("online_video_notifications_badge", useUnmergedTree = true).fetchSemanticsNodes().size)

        // Switching back to Offline Music hides the bell again
        composeRule.runOnIdle { mode = HomeMusicMode.OFFLINE }
        composeRule.waitForIdle()
        assertEquals(0, composeRule.onAllNodesWithTag("online_video_notifications_bell").fetchSemanticsNodes().size)
    }

    @Test
    fun `All Notifications panel renders empty state and supports open video, open channel, mark read, and clear`() {
        val videoNotif = VideoNotificationItem(
            notificationId = "video:vid_101",
            type = VideoNotificationType.NEW_VIDEO,
            channelId = "UC_alpha",
            channelName = "Sonora Acoustic",
            channelUrl = "https://www.youtube.com/channel/UC_alpha",
            videoId = "vid_101",
            videoUrl = "https://www.youtube.com/watch?v=vid_101",
            videoTitle = "Live Unplugged Session 4K",
            uploadDateText = "2 hours ago",
            message = "Live Unplugged Session 4K",
            isRead = false
        )
        val channelNotif = VideoNotificationItem(
            notificationId = "channel:UC_beta:1",
            type = VideoNotificationType.CHANNEL_UPDATE,
            channelId = "UC_beta",
            channelName = "Indie Visuals",
            channelUrl = "https://www.youtube.com/channel/UC_beta",
            message = "New channel tour schedule announced",
            isRead = true
        )

        var notifications by mutableStateOf(listOf(videoNotif, channelNotif))
        var openedVideoId: String? = null
        var openedChannelId: String? = null
        var markedReadId: String? = null
        var markAllReadCalled = false
        var clearedId: String? = null
        var clearAllCalled = false

        composeRule.setContent {
            VideoNotificationsPanelContent(
                notifications = notifications,
                isSyncing = false,
                colors = LocalEditorialColors.current,
                onOpenVideo = { openedVideoId = it.videoId },
                onOpenChannel = { openedChannelId = it.channelId },
                onMarkAsRead = { markedReadId = it },
                onMarkAllRead = { markAllReadCalled = true },
                onDeleteNotification = { clearedId = it },
                onClearAll = { clearAllCalled = true },
                onCheckNow = {}
            )
        }

        // Title and items shown
        composeRule.onNodeWithText("All Notifications").assertIsDisplayed()
        composeRule.onNodeWithText("Live Unplugged Session 4K").assertIsDisplayed()
        composeRule.onNodeWithText("Sonora Acoustic").assertIsDisplayed()
        composeRule.onNodeWithTag("video_notification_unread_dot_video:vid_101", useUnmergedTree = true).assertIsDisplayed()

        // Tap video notification -> opens video
        composeRule.onNodeWithTag("video_notification_item_video:vid_101").performClick()
        assertEquals("vid_101", openedVideoId)

        // Tap channel notification -> opens channel detail
        composeRule.onNodeWithTag("video_notification_item_channel:UC_beta:1").performClick()
        assertEquals("UC_beta", openedChannelId)

        // Mark individual as read, Mark all as read, Clear individual, Clear all
        composeRule.onNodeWithTag("video_notification_mark_read_video:vid_101").performClick()
        assertEquals("video:vid_101", markedReadId)

        composeRule.onNodeWithTag("video_notifications_mark_all_read").performClick()
        assertTrue(markAllReadCalled)

        composeRule.onNodeWithTag("video_notification_clear_video:vid_101").performClick()
        assertEquals("video:vid_101", clearedId)

        composeRule.onNodeWithTag("video_notifications_clear_all").performClick()
        assertTrue(clearAllCalled)

        // Empty state message
        composeRule.runOnIdle { notifications = emptyList() }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("video_notifications_empty_state").assertIsDisplayed()
        composeRule.onNodeWithText("You're all caught up").assertIsDisplayed()
    }

    @Test
    fun `Room v9 video_notifications deduplicates by videoId, preserves read state, and skips disabled channels`() = runBlocking {
        // Insert a followed channel with notifications DISABLED (NONE)
        db.dao().upsertSubscribedVideoChannel(
            SubscribedVideoChannelEntity(
                channelId = "UC_muted",
                stableId = 1001L,
                channelUrl = "https://www.youtube.com/channel/UC_muted",
                name = "Muted Channel",
                avatarUrl = null,
                subscriberCount = 12_000L,
                notificationPref = ChannelNotificationPreference.NONE.id
            )
        )
        // Sync must strictly skip channels with notifications disabled
        val syncSkipped = ChannelUploadSyncEngine.syncEnabledChannels(
            dao = db.dao(),
            dataSource = OnlineVideoDataSource(NetworkConnectivityMonitor(context)),
            forceRefresh = false
        )
        assertEquals(0, syncSkipped.enabledChannelCount)
        assertEquals(0, syncSkipped.checkedChannelCount)
        assertTrue(syncSkipped.newNotifications.isEmpty())

        // Insert notification records via repository
        val enabledChannel = SubscribedVideoChannelEntity(
            channelId = "UC_active",
            stableId = 1002L,
            channelUrl = "https://www.youtube.com/channel/UC_active",
            name = "Active Channel",
            avatarUrl = null,
            subscriberCount = 48_000L,
            notificationPref = ChannelNotificationPreference.ALL.id
        )
        val upload1 = OnlineVideoItem(
            videoId = "stable_vid_001",
            contentUrl = "https://www.youtube.com/watch?v=stable_vid_001",
            title = "First New Upload",
            channelName = "Active Channel",
            channelUrl = enabledChannel.channelUrl,
            uploadDateText = "1 hour ago"
        )
        val upload2 = OnlineVideoItem(
            videoId = "stable_vid_002",
            contentUrl = "https://www.youtube.com/watch?v=stable_vid_002",
            title = "Second New Upload",
            channelName = "Active Channel",
            channelUrl = enabledChannel.channelUrl,
            uploadDateText = "10 minutes ago"
        )

        val entity1 = ChannelUploadSyncEngine.buildNotificationEntity(enabledChannel, upload1, null, 1_000L)
        val entity2 = ChannelUploadSyncEngine.buildNotificationEntity(enabledChannel, upload2, null, 2_000L)
        repository.insertVideoNotifications(listOf(entity1, entity2))

        assertEquals(2, repository.unreadVideoNotificationCountFlow.first())
        assertEquals(2, repository.videoNotificationsFlow.first().size)

        // Mark upload1 read, then attempt duplicate insert -> must stay deduplicated AND keep isRead = true!
        repository.markVideoNotificationRead(entity1.notificationId)
        assertEquals(1, repository.unreadVideoNotificationCountFlow.first())

        val duplicateEntity1 = ChannelUploadSyncEngine.buildNotificationEntity(enabledChannel, upload1, null, 9_999L)
        repository.insertVideoNotifications(listOf(duplicateEntity1))

        val afterDup = repository.videoNotificationsFlow.first()
        assertEquals(2, afterDup.size)
        assertTrue(afterDup.first { it.videoId == "stable_vid_001" }.isRead)
        assertEquals(1, repository.unreadVideoNotificationCountFlow.first())

        // Mark all read -> unread becomes 0 while history stays intact
        repository.markAllVideoNotificationsRead()
        assertEquals(0, repository.unreadVideoNotificationCountFlow.first())
        assertEquals(2, repository.videoNotificationsFlow.first().size)

        // Clear individual notification
        repository.deleteVideoNotification(entity1.notificationId)
        assertEquals(1, repository.videoNotificationsFlow.first().size)

        // Clear all notifications
        repository.clearAllVideoNotifications()
        assertTrue(repository.videoNotificationsFlow.first().isEmpty())
    }

    @Test
    fun `VideoLoadControlProfile adapts for low-memory vs standard devices and diagnostics track playback KPIs`() {
        val lowRamProfile = VideoLoadControlProfile.forDeviceMemory(
            isLowRamDevice = true,
            totalMemoryBytes = 2L * 1024L * 1024L * 1024L
        )
        assertTrue(lowRamProfile.isLowMemoryProfile)
        assertEquals(10_000, lowRamProfile.minBufferMs)
        assertEquals(25_000, lowRamProfile.maxBufferMs)
        assertEquals(16 * 1024 * 1024, lowRamProfile.targetBufferBytes)
        assertTrue(lowRamProfile.prioritizeTimeOverSizeThresholds)
        // Verify Media3 LoadControl builds cleanly without throwing
        lowRamProfile.buildLoadControl()

        val standardProfile = VideoLoadControlProfile.forDeviceMemory(
            isLowRamDevice = false,
            totalMemoryBytes = 6L * 1024L * 1024L * 1024L
        )
        assertFalse(standardProfile.isLowMemoryProfile)
        assertEquals(15_000, standardProfile.minBufferMs)
        assertEquals(45_000, standardProfile.maxBufferMs)
        assertEquals(5_000, standardProfile.backBufferDurationMs)
        standardProfile.buildLoadControl()

        // Diagnostics tracking with deterministic clock
        var clockMs = 1_000L
        val diagnostics = VideoPlaybackDiagnostics(elapsedRealtimeProvider = { clockMs })
        diagnostics.markVideoLoadStarted(videoId = "perf_vid_1", extractionDurationMs = 340L)

        clockMs = 1_450L
        diagnostics.recordStateTransition(Player.STATE_READY)
        diagnostics.recordVideoFormat(1920, 1080, "avc1.640028", "video/mp4", 4_200_000)

        clockMs = 1_520L
        diagnostics.recordFirstFrameRendered()

        // Simulate mid-playback rebuffer stall (600ms)
        clockMs = 5_000L
        diagnostics.recordStateTransition(Player.STATE_BUFFERING)
        clockMs = 5_600L
        diagnostics.recordStateTransition(Player.STATE_READY)

        // Simulate dropped frames & a decoder failure
        diagnostics.recordDroppedFrames(18)
        diagnostics.recordPlaybackError("ERROR_CODE_DECODER_INIT_FAILED", isDecoderFailure = true)

        val snap = diagnostics.currentSnapshot()
        assertEquals("perf_vid_1", snap.videoId)
        assertEquals(340L, snap.extractionDurationMs)
        assertEquals(450L, snap.initialBufferingDurationMs)
        assertEquals(520L, snap.timeToFirstFrameMs)
        assertEquals(1, snap.rebufferCount)
        assertEquals(600L, snap.totalRebufferDurationMs)
        assertEquals("1920x1080", snap.selectedResolution)
        assertEquals("avc1.640028", snap.selectedCodec)
        assertEquals(18L, snap.droppedFramesTotal)
        assertEquals(1, snap.decoderInitFailureCount)
        assertEquals(1, snap.playbackErrorCount)
    }

    @Test
    fun `VideoAdaptiveQualitySelector prefers hardware H264 over software AV1 and applies hysteresis`() {
        val av1Stream = VideoStreamOption(
            id = "av1_1080",
            url = "https://cdn.example/av1.mp4",
            resolutionLabel = "1080p",
            heightPx = 1080,
            fps = 30,
            mimeType = "video/mp4",
            formatLabel = "MP4",
            codec = "av01.0.08M.08",
            isVideoOnly = true
        )
        val h264Stream = VideoStreamOption(
            id = "avc_1080",
            url = "https://cdn.example/avc.mp4",
            resolutionLabel = "1080p",
            heightPx = 1080,
            fps = 30,
            mimeType = "video/mp4",
            formatLabel = "MP4",
            codec = "avc1.640028",
            isVideoOnly = true
        )

        val decision = VideoAdaptiveQualitySelector.selectBestQuality(
            availableStreams = listOf(av1Stream, h264Stream),
            settings = VideoExperienceSettings(autoVideoQualityEnabled = true, preferredWifiQualityHeight = 1080),
            isMeteredMobile = false,
            estimatedBandwidthBps = 12_000_000L,
            bufferedAheadMs = 15_000L,
            rebufferCount = 0
        )
        assertEquals("avc_1080", decision.selectedStream?.id)

        // Hysteresis blocks rapid upward oscillation before 20s cooldown or with low buffer
        assertFalse(
            VideoAdaptiveQualitySelector.shouldSwitchQualityWithHysteresis(
                currentHeightPx = 720,
                candidateHeightPx = 1080,
                elapsedSinceLastSwitchMs = 9_000L,
                bufferedAheadMs = 15_000L,
                rebufferCount = 0,
                networkTypeChanged = false
            )
        )
        assertFalse(
            VideoAdaptiveQualitySelector.shouldSwitchQualityWithHysteresis(
                currentHeightPx = 720,
                candidateHeightPx = 1080,
                elapsedSinceLastSwitchMs = 25_000L,
                bufferedAheadMs = 4_000L,
                rebufferCount = 0,
                networkTypeChanged = false
            )
        )
        // Upgrade permitted when cooldown >= 20s, buffer >= 10s, and zero stalls
        assertTrue(
            VideoAdaptiveQualitySelector.shouldSwitchQualityWithHysteresis(
                currentHeightPx = 720,
                candidateHeightPx = 1080,
                elapsedSinceLastSwitchMs = 22_000L,
                bufferedAheadMs = 12_000L,
                rebufferCount = 0,
                networkTypeChanged = false
            )
        )
        // Downgrade allowed immediately on repeated stalls even before 8s cooldown
        assertTrue(
            VideoAdaptiveQualitySelector.shouldSwitchQualityWithHysteresis(
                currentHeightPx = 1080,
                candidateHeightPx = 720,
                elapsedSinceLastSwitchMs = 2_000L,
                bufferedAheadMs = 1_500L,
                rebufferCount = 2,
                networkTypeChanged = false
            )
        )
        // Network type transition (e.g., Wi-Fi -> mobile) switches immediately
        assertTrue(
            VideoAdaptiveQualitySelector.shouldSwitchQualityWithHysteresis(
                currentHeightPx = 1080,
                candidateHeightPx = 480,
                elapsedSinceLastSwitchMs = 1_000L,
                bufferedAheadMs = 5_000L,
                rebufferCount = 0,
                networkTypeChanged = true
            )
        )
    }

    @Test
    fun `VideoPlayerScreen attaches SurfaceView once and updates isolated timeline without re-attaching surface`() {
        val sampleVideo = OnlineVideoItem(
            videoId = "vid_perf_ui",
            contentUrl = "https://www.youtube.com/watch?v=vid_perf_ui",
            title = "Smooth 60fps Concert",
            channelName = "Sonora Live",
            channelUrl = "https://www.youtube.com/channel/UClive",
            durationSeconds = 180L
        )
        val progressFlow = MutableStateFlow(
            VideoPlaybackProgress(
                currentPositionMs = 5_000L,
                durationMs = 180_000L
            )
        )
        var surfaceAttachCount = 0
        var attachedSurfaceView: SurfaceView? = null

        composeRule.setContent {
            VideoPlayerScreen(
                playerState = VideoPlayerUiState(
                    activeVideo = sampleVideo,
                    isPlayerExpanded = true,
                    isPlaying = false,
                    durationMs = 180_000L,
                    currentPositionMs = 0L
                ),
                watchLaterIds = emptySet(),
                savedVideoIds = emptySet(),
                videoPlaylists = emptyList(),
                colors = LocalEditorialColors.current,
                onAttachSurface = {},
                onDetachSurface = {},
                onAttachSurfaceView = { sv ->
                    surfaceAttachCount++
                    attachedSurfaceView = sv
                },
                onDetachSurfaceView = {},
                playbackProgressFlow = progressFlow,
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
                onToggleLike = {},
                onToggleDislike = {},
                onToggleSubscribe = {},
                onSelectNotificationPref = { _, _ -> },
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
        // Hardware-composited SurfaceView attached exactly once
        assertEquals(1, surfaceAttachCount)
        assertTrue(attachedSurfaceView != null)
        composeRule.onNodeWithTag("video_surface_view").assertIsDisplayed()
        composeRule.onNodeWithText("0:05 / 3:00").assertIsDisplayed()

        // Simulate multiple 500ms progress ticks — timeline updates smoothly, SurfaceView never re-attaches!
        progressFlow.value = VideoPlaybackProgress(currentPositionMs = 65_000L, durationMs = 180_000L)
        composeRule.waitForIdle()
        composeRule.onNodeWithText("1:05 / 3:00").assertIsDisplayed()
        assertEquals(1, surfaceAttachCount)

        progressFlow.value = VideoPlaybackProgress(currentPositionMs = 125_000L, durationMs = 180_000L)
        composeRule.waitForIdle()
        composeRule.onNodeWithText("2:05 / 3:00").assertIsDisplayed()
        assertEquals(1, surfaceAttachCount)
    }
}
