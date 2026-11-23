package ui.video

import android.app.Application
import android.os.Build
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.doubleClick
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeUp
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.SonoraDatabase
import data.online.NetworkConnectivityMonitor
import data.preferences.VideoAccountPreferences
import data.video.OnlineVideoDataSource
import data.video.OnlineVideoRepository
import domain.video.OnlineVideoItem
import domain.video.VideoAspectRatioMode
import domain.video.VideoPlayerGestureSettings
import domain.video.VideoPlayerUiState
import domain.video.VideoStreamOption
import domain.video.VideoSwipeSensitivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
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
import ui.settings.OnlineVideoPlayerGesturesScreen
import ui.theme.EditorialColors
import ui.theme.LocalEditorialColors
import viewmodel.OnlineVideoController

/**
 * Robolectric Android UI, DataStore, and lifecycle verification across Android 8.0 (API 26)
 * and Android 16 (API 36) covering scenarios 9–15:
 * - Portrait central swipe up -> landscape fullscreen (playback uninterrupted)
 * - Landscape central swipe down -> portrait restore (never closes video)
 * - Double-tap configurable seek & horizontal swipe conflict prevention
 * - Aspect Ratio Sheet live switching without pausing playback
 * - DataStore persistence for all 6 Player Gestures settings
 * - Background/PiP lifecycle & Settings -> Online Video -> Player Gestures UI
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [26, 36])
class VideoAspectRatioAndGesturesTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun sampleVideo(id: String = "vid_sonora_169"): OnlineVideoItem = OnlineVideoItem(
        videoId = id,
        contentUrl = "https://www.youtube.com/watch?v=$id",
        title = "SONORA Live Studio Session 4K",
        channelName = "SONORA Studio India",
        channelUrl = "https://www.youtube.com/channel/UC1234567890",
        channelAvatarUrl = null,
        thumbnailUrl = null,
        durationSeconds = 300L,
        viewCount = 980_000L,
        uploadDateText = "1 day ago"
    )

    @Test
    fun test11_12_composePlayerSwipeFullscreenDoubleTapSeekAndAspectRatioSelector() {
        assertTrue(Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
        var playerState by mutableStateOf(
            VideoPlayerUiState(
                activeVideo = sampleVideo(),
                isPlaying = true,
                currentPositionMs = 45_000L,
                durationMs = 300_000L,
                videoWidth = 1920,
                videoHeight = 1080,
                hasKnownVideoDimensions = true,
                isPlayerExpanded = true,
                isFullScreenLandscape = false,
                selectedVideoStream = VideoStreamOption(
                    id = "720p",
                    url = "https://example.com/720.mp4",
                    resolutionLabel = "720p",
                    heightPx = 720,
                    fps = 30,
                    mimeType = "video/mp4",
                    formatLabel = "MP4",
                    isVideoOnly = false,
                    bitrateKbps = 2000
                ),
                gestureSettings = VideoPlayerGestureSettings(
                    swipeFullscreenEnabled = true,
                    autoAspectRatioEnabled = true,
                    defaultAspectRatio = VideoAspectRatioMode.AUTO,
                    swipeSensitivity = VideoSwipeSensitivity.HIGH,
                    doubleTapSeekSeconds = 15,
                    autoOrientationEnabled = true
                )
            )
        )
        var lastSeekDeltaMs by mutableLongStateOf(0L)
        var minimizeCount by mutableIntStateOf(0)
        val colors = EditorialColors.default()

        composeRule.setContent {
            CompositionLocalProvider(LocalEditorialColors provides colors) {
                VideoPlayerScreen(
                    playerState = playerState,
                    watchLaterIds = emptySet(),
                    savedVideoIds = emptySet(),
                    videoPlaylists = emptyList(),
                    colors = colors,
                    onAttachSurface = {},
                    onDetachSurface = {},
                    onTogglePlayPause = { playerState = playerState.copy(isPlaying = !playerState.isPlaying) },
                    onSeekTo = { pos -> playerState = playerState.copy(currentPositionMs = pos) },
                    onSeekBy = { delta ->
                        lastSeekDeltaMs = delta
                        playerState = playerState.copy(currentPositionMs = (playerState.currentPositionMs + delta).coerceAtLeast(0L))
                    },
                    onPlayNext = {},
                    onPlayPrevious = {},
                    onSelectStream = {},
                    onSelectAudio = {},
                    onSelectSubtitle = {},
                    onSelectSpeed = {},
                    onSelectAspectRatio = { mode -> playerState = playerState.copy(aspectRatioMode = mode) },
                    onToggleFullScreen = { fs -> playerState = playerState.copy(isFullScreenLandscape = fs) },
                    onMinimize = { minimizeCount++ },
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
        }

        // Open Aspect Ratio Sheet from quick top-bar pill and select Fill Screen without stopping playback
        composeRule.onNodeWithTag("video_aspect_ratio_chip").assertIsDisplayed().performClick()
        composeRule.onNodeWithTag("video_aspect_ratio_sheet").assertIsDisplayed()
        composeRule.onNodeWithTag("aspect_ratio_option_fill").performClick()
        composeRule.waitForIdle()
        assertEquals(VideoAspectRatioMode.FILL, playerState.aspectRatioMode)
        assertTrue("Changing aspect ratio must never stop playback", playerState.isPlaying)

        // Horizontal swipe across video must NOT enter fullscreen
        composeRule.onNodeWithTag("video_surface_container").performTouchInput {
            swipeLeft(startX = width * 0.7f, endX = width * 0.3f, durationMillis = 120L)
        }
        composeRule.waitForIdle()
        assertFalse(playerState.isFullScreenLandscape)

        // Central vertical swipe UP in portrait -> enters landscape fullscreen while preserving playback
        composeRule.onNodeWithTag("video_surface_container").performTouchInput {
            swipeUp(startY = height * 0.72f, endY = height * 0.18f, durationMillis = 150L)
        }
        composeRule.waitForIdle()
        assertTrue("Central swipe up should enter landscape fullscreen", playerState.isFullScreenLandscape)
        assertTrue("Playback must continue uninterrupted entering fullscreen", playerState.isPlaying)
        assertEquals(45_000L, playerState.currentPositionMs)

        // Double-tap right in landscape -> forwards by configured 15s (15_000ms)
        composeRule.onNodeWithTag("video_surface_container").performTouchInput {
            doubleClick(Offset(width * 0.80f, height * 0.50f))
        }
        composeRule.waitForIdle()
        assertEquals(15_000L, lastSeekDeltaMs)
        assertEquals(60_000L, playerState.currentPositionMs)

        // Central vertical swipe DOWN in landscape -> returns smoothly to portrait, NEVER closes/minimizes video
        composeRule.onNodeWithTag("video_surface_container").performTouchInput {
            swipeDown(startY = height * 0.25f, endY = height * 0.78f, durationMillis = 150L)
        }
        composeRule.waitForIdle()
        assertFalse("Central swipe down should exit landscape fullscreen back to portrait", playerState.isFullScreenLandscape)
        assertEquals("Landscape swipe down must NOT close or minimize the video", 0, minimizeCount)
        assertTrue("Playback must remain active returning to portrait", playerState.isPlaying)
        assertEquals(60_000L, playerState.currentPositionMs)
    }

    @Test
    fun test13_14_dataStorePersistenceAndControllerLifecycle() = runBlocking {
        assertTrue(Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
        val app = ApplicationProvider.getApplicationContext<Application>()
        val prefs = VideoAccountPreferences(app)
        prefs.resetPlayerGestureSettings()
        val db = Room.inMemoryDatabaseBuilder(app, SonoraDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

        try {
            val repo = OnlineVideoRepository(
                dao = db.dao(),
                dataSource = OnlineVideoDataSource(NetworkConnectivityMonitor(app))
            )
            val controller = OnlineVideoController(
                context = app,
                scope = scope,
                repository = repo,
                accountPreferences = prefs,
                getCountryCode = { "IN" },
                getLanguageCode = { "en" },
                onPauseMusicForVideo = {}
            )

            assertEquals(VideoAspectRatioMode.AUTO, controller.gestureSettings.first().defaultAspectRatio)

            controller.setSwipeFullscreenEnabled(false)
            controller.setAutoAspectRatioEnabled(false)
            controller.setDefaultAspectRatio(VideoAspectRatioMode.RATIO_4_3)
            controller.setSwipeSensitivity(VideoSwipeSensitivity.LOW)
            controller.setDoubleTapSeekSeconds(20)
            controller.setAutoOrientationEnabled(false)

            val stored = withTimeout(5_000L) {
                prefs.gestureSettingsFlow.first {
                    !it.swipeFullscreenEnabled &&
                        !it.autoAspectRatioEnabled &&
                        it.defaultAspectRatio == VideoAspectRatioMode.RATIO_4_3 &&
                        it.swipeSensitivity == VideoSwipeSensitivity.LOW &&
                        it.doubleTapSeekSeconds == 20 &&
                        !it.autoOrientationEnabled
                }
            }
            assertFalse(stored.swipeFullscreenEnabled)
            assertFalse(stored.autoAspectRatioEnabled)
            assertEquals(VideoAspectRatioMode.RATIO_4_3, stored.defaultAspectRatio)
            assertEquals(VideoSwipeSensitivity.LOW, stored.swipeSensitivity)
            assertEquals(20, stored.doubleTapSeekSeconds)
            assertFalse(stored.autoOrientationEnabled)

            controller.setFullScreenLandscape(true)
            assertTrue(controller.playerState.value.isFullScreenLandscape)
            controller.setVideoAspectRatioMode(VideoAspectRatioMode.FILL)
            assertEquals(VideoAspectRatioMode.FILL, controller.playerState.value.aspectRatioMode)
            assertTrue(controller.playerState.value.isFullScreenLandscape)

            controller.onPictureInPictureModeChanged(true)
            assertTrue(controller.playerState.value.isInPictureInPicture)
            controller.onAppBackgrounded()
            controller.onPictureInPictureModeChanged(false)
            assertFalse(controller.playerState.value.isInPictureInPicture)

            controller.release()
        } finally {
            scope.coroutineContext[Job]?.cancelAndJoin()
            prefs.resetPlayerGestureSettings()
            db.close()
        }
    }

    @Test
    fun test15_settingsOnlineVideoPlayerGesturesUiOnApi26And36() {
        assertTrue(Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
        var uiSettings by mutableStateOf(VideoPlayerGestureSettings())
        composeRule.setContent {
            CompositionLocalProvider(LocalEditorialColors provides EditorialColors.default()) {
                OnlineVideoPlayerGesturesScreen(
                    settings = uiSettings,
                    onSwipeFullscreenChanged = { uiSettings = uiSettings.copy(swipeFullscreenEnabled = it) },
                    onAutoAspectRatioChanged = { uiSettings = uiSettings.copy(autoAspectRatioEnabled = it) },
                    onDefaultAspectRatioChanged = { uiSettings = uiSettings.copy(defaultAspectRatio = it) },
                    onSwipeSensitivityChanged = { uiSettings = uiSettings.copy(swipeSensitivity = it) },
                    onDoubleTapSeekSecondsChanged = { uiSettings = uiSettings.copy(doubleTapSeekSeconds = it) },
                    onAutoOrientationChanged = { uiSettings = uiSettings.copy(autoOrientationEnabled = it) },
                    onResetToDefaults = { uiSettings = VideoPlayerGestureSettings() },
                    onBack = {}
                )
            }
        }

        composeRule.onNodeWithTag("online_video_player_gestures_screen").assertIsDisplayed()
        composeRule.onNodeWithText("Player Gestures").assertIsDisplayed()
        composeRule.onNodeWithTag("video_gestures_list")
            .performScrollToNode(hasTestTag("setting_video_sensitivity_high"))
        composeRule.onNodeWithTag("setting_video_sensitivity_high").performClick()
        assertEquals(VideoSwipeSensitivity.HIGH, uiSettings.swipeSensitivity)

        composeRule.onNodeWithTag("video_gestures_list")
            .performScrollToNode(hasTestTag("setting_video_default_ratio_16_9"))
        composeRule.onNodeWithTag("setting_video_default_ratio_16_9").performClick()
        assertEquals(VideoAspectRatioMode.RATIO_16_9, uiSettings.defaultAspectRatio)

        composeRule.onNodeWithTag("video_gestures_list")
            .performScrollToNode(hasTestTag("setting_video_seek_15s"))
        composeRule.onNodeWithTag("setting_video_seek_15s").performClick()
        assertEquals(15, uiSettings.doubleTapSeekSeconds)
    }
}
