package ui.video

import android.app.ActivityManager
import android.content.Context
import android.view.TextureView
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.pinch
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import androidx.compose.ui.unit.width
import androidx.test.core.app.ApplicationProvider
import domain.video.OnlineVideoItem
import domain.video.VideoAspectRatioCalculator
import domain.video.VideoPlaybackProgress
import domain.video.VideoPlayerUiState
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import ui.theme.EditorialColors
import ui.theme.LocalEditorialColors
import kotlin.math.abs

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w412dp-h840dp")
class FloatingVideoPipTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun sampleVideo(id: String = "vid_pip_1"): OnlineVideoItem = OnlineVideoItem(
        videoId = id,
        title = "SONORA Live Session",
        channelName = "SONORA Official",
        channelUrl = "https://www.youtube.com/channel/UC_SONORA",
        contentUrl = "https://www.youtube.com/watch?v=$id",
        thumbnailUrl = null,
        durationSeconds = 240L
    )

    private fun samplePlayerState(
        videoWidth: Int = 1920,
        videoHeight: Int = 1080,
        par: Float = 1f,
        rotation: Int = 0,
        isPlaying: Boolean = true,
        hasNext: Boolean = true,
        isInSystemPip: Boolean = false
    ): VideoPlayerUiState {
        val v1 = sampleVideo("vid_1")
        val v2 = sampleVideo("vid_2")
        return VideoPlayerUiState(
            activeVideo = v1,
            isPlaying = isPlaying,
            isPlayerExpanded = false,
            isInPictureInPicture = isInSystemPip,
            currentPositionMs = 45_000L,
            durationMs = 240_000L,
            queue = if (hasNext) listOf(v1, v2) else listOf(v1),
            queueIndex = 0,
            videoWidth = videoWidth,
            videoHeight = videoHeight,
            pixelWidthHeightRatio = par,
            unappliedRotationDegrees = rotation,
            hasKnownVideoDimensions = videoWidth > 0 && videoHeight > 0
        )
    }

    // 1. Pinching outward enlarges PiP, pinching inward shrinks PiP, preserves 16:9 aspect ratio,
    //    clamps inside safe bounds, and keeps single TextureView continuously attached with zero detach/recreation
    @Test
    fun `two finger pinch outward enlarges and inward shrinks PiP while preserving aspect ratio and TextureView`() {
        val colors = EditorialColors.default()
        val attachedViews = mutableListOf<TextureView>()
        var detachCount = 0
        val progressFlow = MutableStateFlow(
            VideoPlaybackProgress(currentPositionMs = 50_000L, durationMs = 240_000L)
        )

        composeRule.setContent {
            CompositionLocalProvider(LocalEditorialColors provides colors) {
                Box(modifier = Modifier.size(width = 400.dp, height = 800.dp)) {
                    FloatingVideoMiniPlayer(
                        playerState = samplePlayerState(videoWidth = 1920, videoHeight = 1080),
                        colors = colors,
                        onExpand = {},
                        onTogglePlayPause = {},
                        onPlayNext = {},
                        onClose = {},
                        playbackProgressFlow = progressFlow,
                        onAttachTextureView = { tv ->
                            if (attachedViews.none { it === tv }) {
                                attachedViews.add(tv)
                            }
                        },
                        onDetachTextureView = { detachCount++ },
                        bottomInsetClearance = 86.dp,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }

        val initialBounds = composeRule.onNodeWithTag("floating_video_mini_player").getUnclippedBoundsInRoot()
        assertEquals(160f, initialBounds.width.value, 3f) // Normal configured ~40% width
        assertEquals(16f / 9f, initialBounds.width.value / initialBounds.height.value, 0.05f)
        assertEquals(1, attachedViews.size)
        val firstTextureView = attachedViews.first()

        // Pinch OUTWARD -> enlarges PiP smoothly while preserving 16:9 aspect ratio & safe bounds
        composeRule.onNodeWithTag("floating_pip_video_surface").performTouchInput {
            pinch(
                start0 = Offset(centerX - 18f, centerY),
                end0 = Offset(centerX - 62f, centerY),
                start1 = Offset(centerX + 18f, centerY),
                end1 = Offset(centerX + 62f, centerY),
                durationMillis = 250L
            )
        }
        composeRule.waitForIdle()

        val afterPinchOut = composeRule.onNodeWithTag("floating_video_mini_player").getUnclippedBoundsInRoot()
        assertTrue(
            "Pinch outward should enlarge PiP (initial=${initialBounds.width}, after=${afterPinchOut.width})",
            afterPinchOut.width.value > initialBounds.width.value + 16f
        )
        assertTrue("PiP must not exceed 85% viewport width", afterPinchOut.width.value <= 345f)
        assertEquals(16f / 9f, afterPinchOut.width.value / afterPinchOut.height.value, 0.06f)
        assertTrue("PiP left bound must stay inside safe screen edge", afterPinchOut.left >= 10.dp)
        assertTrue("PiP right bound must stay inside safe screen edge", afterPinchOut.right <= 390.dp)
        assertTrue("PiP bottom bound must stay above bottom chrome", afterPinchOut.bottom <= 716.dp)

        // Pinch INWARD -> shrinks PiP smoothly down toward minimum 20% screen width
        composeRule.onNodeWithTag("floating_pip_video_surface").performTouchInput {
            pinch(
                start0 = Offset(centerX - 68f, centerY),
                end0 = Offset(centerX - 10f, centerY),
                start1 = Offset(centerX + 68f, centerY),
                end1 = Offset(centerX + 10f, centerY),
                durationMillis = 250L
            )
        }
        composeRule.waitForIdle()

        val afterPinchIn = composeRule.onNodeWithTag("floating_video_mini_player").getUnclippedBoundsInRoot()
        assertTrue(
            "Pinch inward should shrink PiP (enlarged=${afterPinchOut.width}, shrunk=${afterPinchIn.width})",
            afterPinchIn.width.value < afterPinchOut.width.value - 20f
        )
        assertTrue("PiP must not shrink below 20% viewport width", afterPinchIn.width.value >= 78f)
        assertEquals(16f / 9f, afterPinchIn.width.value / afterPinchIn.height.value, 0.06f)

        // Single TextureView remains continuously attached with zero detach/recreation
        assertEquals(1, attachedViews.size)
        assertSame(firstTextureView, attachedViews.first())
        assertEquals(0, detachCount)
    }

    // 2. Existing controls work (Play/Pause, Next, Expand, Close) and one-finger tap toggles controls
    @Test
    fun `existing playback controls work and one finger tap toggles controls visibility`() {
        val colors = EditorialColors.default()
        var expanded = false
        var toggledPlayPause = false
        var playedNext = false
        var closed = false

        composeRule.setContent {
            CompositionLocalProvider(LocalEditorialColors provides colors) {
                Box(modifier = Modifier.size(width = 412.dp, height = 820.dp)) {
                    FloatingVideoMiniPlayer(
                        playerState = samplePlayerState(isPlaying = false, hasNext = true),
                        colors = colors,
                        onExpand = { expanded = true },
                        onTogglePlayPause = { toggledPlayPause = true },
                        onPlayNext = { playedNext = true },
                        onClose = { closed = true },
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }

        composeRule.onNodeWithTag("floating_pip_controls_overlay").assertIsDisplayed()

        composeRule.onNodeWithTag("video_mini_play_pause").performClick()
        assertTrue(toggledPlayPause)
        composeRule.onNodeWithTag("video_mini_next").performClick()
        assertTrue(playedNext)
        composeRule.onNodeWithTag("video_mini_expand").performClick()
        assertTrue(expanded)
        composeRule.onNodeWithTag("video_mini_close").performClick()
        assertTrue(closed)

        // One-finger tap on empty corner hides controls
        composeRule.onNodeWithTag("floating_pip_video_surface").performTouchInput {
            down(Offset(x = width * 0.12f, y = height * 0.85f))
            up()
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("floating_pip_controls_overlay").assertDoesNotExist()

        // One-finger tap again shows controls
        composeRule.onNodeWithTag("floating_pip_video_surface").performTouchInput {
            down(Offset(x = width * 0.12f, y = height * 0.85f))
            up()
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("floating_pip_controls_overlay").assertIsDisplayed()
    }

    // 3. Portrait and landscape layouts preserve aspect ratio (9:16 vertical) and stay inside safe bounds
    @Test
    @Config(sdk = [36], qualifiers = "w840dp-h840dp")
    fun `portrait and landscape containers preserve 9x16 aspect ratio within safe bounds`() {
        val colors = EditorialColors.default()
        var isLandscape by mutableStateOf(false)

        composeRule.setContent {
            CompositionLocalProvider(LocalEditorialColors provides colors) {
                Box(
                    modifier = if (isLandscape) {
                        Modifier.size(width = 800.dp, height = 390.dp)
                    } else {
                        Modifier.size(width = 390.dp, height = 800.dp)
                    }
                ) {
                    FloatingVideoMiniPlayer(
                        playerState = samplePlayerState(videoWidth = 1080, videoHeight = 1920),
                        colors = colors,
                        onExpand = {},
                        onTogglePlayPause = {},
                        onPlayNext = {},
                        onClose = {},
                        bottomInsetClearance = 80.dp,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }

        val portraitBounds = composeRule.onNodeWithTag("floating_video_mini_player").getUnclippedBoundsInRoot()
        assertEquals(9f / 16f, portraitBounds.width.value / portraitBounds.height.value, 0.06f)
        assertTrue(portraitBounds.left >= 10.dp)
        assertTrue(portraitBounds.right <= 380.dp)
        assertTrue(portraitBounds.bottom <= 725.dp)

        isLandscape = true
        composeRule.waitForIdle()

        val landscapeBounds = composeRule.onNodeWithTag("floating_video_mini_player").getUnclippedBoundsInRoot()
        assertEquals(9f / 16f, landscapeBounds.width.value / landscapeBounds.height.value, 0.06f)
        assertTrue(landscapeBounds.left >= 10.dp)
        assertTrue(landscapeBounds.right <= 790.dp)
        assertTrue(landscapeBounds.top >= 8.dp)
        assertTrue(landscapeBounds.bottom <= 315.dp)
    }

    // 4. Android system PiP state & video aspect ratio calculation remain intact
    @Test
    fun `system PiP state and VideoAspectRatioCalculator aspect ratios remain intact`() {
        val pipState = samplePlayerState(
            videoWidth = 1920,
            videoHeight = 1080,
            isInSystemPip = true
        )
        assertTrue(pipState.isInPictureInPicture)
        assertEquals(16f / 9f, pipState.aspectRatio, 0.01f)

        val rotatedRatio = VideoAspectRatioCalculator.resolveDisplayAspectRatio(
            videoWidth = 1920,
            videoHeight = 1080,
            pixelWidthHeightRatio = 1f,
            unappliedRotationDegrees = 90
        )
        assertEquals(9f / 16f, rotatedRatio, 0.01f)
    }

    // 5a. Android 8 (API 26) low-RAM compatibility check (1:1 video)
    @Test
    @Config(sdk = [26], qualifiers = "w360dp-h640dp")
    fun `renders cleanly at normal size on Android 8 API 26 low-RAM devices`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        Shadows.shadowOf(am).setIsLowRamDevice(true)

        val colors = EditorialColors.default()
        composeRule.setContent {
            CompositionLocalProvider(LocalEditorialColors provides colors) {
                Box(modifier = Modifier.size(width = 360.dp, height = 640.dp)) {
                    FloatingVideoMiniPlayer(
                        playerState = samplePlayerState(videoWidth = 1080, videoHeight = 1080),
                        colors = colors,
                        onExpand = {},
                        onTogglePlayPause = {},
                        onPlayNext = {},
                        onClose = {},
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }

        val bounds = composeRule.onNodeWithTag("floating_video_mini_player").getUnclippedBoundsInRoot()
        assertEquals(1f, bounds.width.value / bounds.height.value, 0.05f)
        assertTrue(abs(bounds.width.value - 144f) < 4f) // 40% of 360dp
    }

    // 5b. Android 11 (API 30) compatibility check (4:3 video)
    @Test
    @Config(sdk = [30], qualifiers = "w412dp-h840dp")
    fun `renders cleanly and preserves 4x3 aspect ratio on Android 11 API 30`() {
        val colors = EditorialColors.default()
        composeRule.setContent {
            CompositionLocalProvider(LocalEditorialColors provides colors) {
                Box(modifier = Modifier.size(width = 400.dp, height = 800.dp)) {
                    FloatingVideoMiniPlayer(
                        playerState = samplePlayerState(videoWidth = 1440, videoHeight = 1080),
                        colors = colors,
                        onExpand = {},
                        onTogglePlayPause = {},
                        onPlayNext = {},
                        onClose = {},
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }

        val bounds = composeRule.onNodeWithTag("floating_video_mini_player").getUnclippedBoundsInRoot()
        assertEquals(4f / 3f, bounds.width.value / bounds.height.value, 0.05f)
        composeRule.onNodeWithTag("floating_video_mini_player").assertIsDisplayed()
    }
}
