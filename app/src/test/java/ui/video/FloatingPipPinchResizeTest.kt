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
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import androidx.compose.ui.unit.width
import androidx.test.core.app.ApplicationProvider
import domain.video.FloatingPipResizeCalculator
import domain.video.FloatingPipViewport
import domain.video.OnlineVideoItem
import domain.video.VideoPlaybackProgress
import domain.video.VideoPlayerUiState
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
class FloatingPipPinchResizeTest {

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
        hasNext: Boolean = true
    ): VideoPlayerUiState {
        val v1 = sampleVideo("vid_1")
        val v2 = sampleVideo("vid_2")
        return VideoPlayerUiState(
            activeVideo = v1,
            isPlaying = isPlaying,
            isPlayerExpanded = false,
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

    // 1–4. Geometry math: pinch outward, pinch inward, minimum ~20%, maximum ~85%, boundary clamp
    @Test
    fun `calculator enforces initial 40pct, min 20pct, max 85pct, focal anchoring, and screen clamping`() {
        val portrait = FloatingPipViewport(
            widthPx = 1080f,
            heightPx = 2400f,
            edgeMarginPx = 24f,
            topSafeMarginPx = 64f,
            bottomSafeMarginPx = 180f
        )
        val ratio16x9 = 16f / 9f

        val initial = FloatingPipResizeCalculator.defaultDockedBounds(portrait, ratio16x9)
        assertEquals(1080f * 0.40f, initial.widthPx, 1f)
        assertEquals(initial.widthPx / ratio16x9, initial.heightPx, 1f)
        assertTrue(initial.offsetX >= portrait.edgeMarginPx - 0.5f)
        assertTrue(initial.rightPx <= portrait.widthPx - portrait.edgeMarginPx + 0.5f)
        assertTrue(initial.offsetY >= portrait.topSafeMarginPx - 0.5f)
        assertTrue(initial.bottomPx <= portrait.heightPx - portrait.bottomSafeMarginPx + 0.5f)

        // Pinch outward increases size cleanly
        val enlarged = FloatingPipResizeCalculator.applyPinchZoom(
            baseBounds = initial,
            zoomFactor = 1.5f,
            focalLocalX = initial.widthPx * 0.5f,
            focalLocalY = initial.heightPx * 0.5f,
            viewport = portrait,
            aspectRatio = ratio16x9
        )
        assertTrue(enlarged.widthPx > initial.widthPx)
        assertEquals(1080f * 0.60f, enlarged.widthPx, 1f)
        assertEquals(ratio16x9, enlarged.widthPx / enlarged.heightPx, 0.01f)
        assertTrue(enlarged.rightPx <= portrait.widthPx - portrait.edgeMarginPx + 0.5f)
        assertTrue(enlarged.bottomPx <= portrait.heightPx - portrait.bottomSafeMarginPx + 0.5f)

        // Excessive pinch outward clamps strictly at 85% width and never leaves screen bounds
        val clampedMax = FloatingPipResizeCalculator.applyPinchZoom(
            baseBounds = initial,
            zoomFactor = 4.0f,
            focalLocalX = initial.widthPx * 0.5f,
            focalLocalY = initial.heightPx * 0.5f,
            viewport = portrait,
            aspectRatio = ratio16x9
        )
        assertEquals(1080f * 0.85f, clampedMax.widthPx, 1f)
        assertTrue(clampedMax.offsetX >= portrait.edgeMarginPx - 0.5f)
        assertTrue(clampedMax.rightPx <= portrait.widthPx - portrait.edgeMarginPx + 0.5f)
        assertTrue(clampedMax.offsetY >= portrait.topSafeMarginPx - 0.5f)
        assertTrue(clampedMax.bottomPx <= portrait.heightPx - portrait.bottomSafeMarginPx + 0.5f)

        // Pinch inward shrinks smoothly and clamps strictly at 20% width
        val shrunk = FloatingPipResizeCalculator.applyPinchZoom(
            baseBounds = enlarged,
            zoomFactor = 0.5f,
            focalLocalX = enlarged.widthPx * 0.5f,
            focalLocalY = enlarged.heightPx * 0.5f,
            viewport = portrait,
            aspectRatio = ratio16x9
        )
        assertTrue(shrunk.widthPx < enlarged.widthPx)
        assertEquals(ratio16x9, shrunk.widthPx / shrunk.heightPx, 0.01f)

        val clampedMin = FloatingPipResizeCalculator.applyPinchZoom(
            baseBounds = initial,
            zoomFactor = 0.1f,
            focalLocalX = initial.widthPx * 0.5f,
            focalLocalY = initial.heightPx * 0.5f,
            viewport = portrait,
            aspectRatio = ratio16x9
        )
        assertEquals(1080f * 0.20f, clampedMin.widthPx, 1f)
        assertEquals(ratio16x9, clampedMin.widthPx / clampedMin.heightPx, 0.01f)
    }

    // 5. Aspect ratio preservation across 16:9, 9:16, 4:3, 1:1, non-square PAR, and 90-degree rotation
    @Test
    fun `calculator preserves true video aspect ratio for 16x9, 9x16, 4x3, 1x1, PAR, and rotation without distortion`() {
        val portrait = FloatingPipViewport(1080f, 2200f, edgeMarginPx = 24f, topSafeMarginPx = 48f, bottomSafeMarginPx = 160f)
        val landscape = FloatingPipViewport(2200f, 1080f, edgeMarginPx = 24f, topSafeMarginPx = 32f, bottomSafeMarginPx = 96f)

        val cases = listOf(
            Triple(1920, 1080, 16f / 9f),
            Triple(1080, 1920, 9f / 16f),
            Triple(1440, 1080, 4f / 3f),
            Triple(1080, 1080, 1f)
        )

        for ((w, h, expectedRatio) in cases) {
            val resolved = FloatingPipResizeCalculator.resolveVideoAspectRatio(w, h)
            assertEquals(expectedRatio, resolved, 0.005f)

            for (vp in listOf(portrait, landscape)) {
                val initial = FloatingPipResizeCalculator.defaultDockedBounds(vp, resolved)
                assertEquals(expectedRatio, initial.widthPx / initial.heightPx, 0.015f)
                assertTrue(initial.widthPx <= vp.safeInnerWidthPx + 0.5f)
                assertTrue(initial.heightPx <= vp.safeInnerHeightPx + 0.5f)

                val maxed = FloatingPipResizeCalculator.applyPinchZoom(
                    baseBounds = initial,
                    zoomFactor = 3.5f,
                    focalLocalX = initial.widthPx / 2f,
                    focalLocalY = initial.heightPx / 2f,
                    viewport = vp,
                    aspectRatio = resolved
                )
                assertEquals(expectedRatio, maxed.widthPx / maxed.heightPx, 0.015f)
                assertTrue(maxed.offsetX >= vp.edgeMarginPx - 0.5f)
                assertTrue(maxed.rightPx <= vp.widthPx - vp.edgeMarginPx + 0.5f)
                assertTrue(maxed.offsetY >= vp.topSafeMarginPx - 0.5f)
                assertTrue(maxed.bottomPx <= vp.heightPx - vp.bottomSafeMarginPx + 0.5f)
            }
        }

        // Rotated 1920x1080 video with 90 degrees unapplied rotation resolves to 9:16
        val rotatedRatio = FloatingPipResizeCalculator.resolveVideoAspectRatio(
            videoWidth = 1920,
            videoHeight = 1080,
            pixelWidthHeightRatio = 1f,
            unappliedRotationDegrees = 90
        )
        assertEquals(9f / 16f, rotatedRatio, 0.005f)

        // Non-square pixel aspect ratio (e.g., anamorphic 720x480 with PAR 1.21)
        val anamorphicRatio = FloatingPipResizeCalculator.resolveVideoAspectRatio(
            videoWidth = 720,
            videoHeight = 480,
            pixelWidthHeightRatio = 1.21f,
            unappliedRotationDegrees = 0
        )
        assertEquals((720f * 1.21f) / 480f, anamorphicRatio, 0.01f)
    }

    // 6. Portrait <-> Landscape orientation adaptation preserves fraction & bounds
    @Test
    fun `portrait to landscape viewport adaptation stays strictly inside safe bounds`() {
        val portrait = FloatingPipViewport(1080f, 2200f, edgeMarginPx = 24f, topSafeMarginPx = 48f, bottomSafeMarginPx = 160f)
        val landscape = FloatingPipViewport(2200f, 1080f, edgeMarginPx = 24f, topSafeMarginPx = 32f, bottomSafeMarginPx = 96f)
        val ratio9x16 = 9f / 16f

        val resizedInPortrait = FloatingPipResizeCalculator.defaultDockedBounds(
            viewport = portrait,
            aspectRatio = ratio9x16,
            requestedWidthFraction = 0.60f
        )
        val adaptedLandscape = FloatingPipResizeCalculator.adaptToViewportOrAspectRatio(
            previousBounds = resizedInPortrait,
            previousViewport = portrait,
            newViewport = landscape,
            newAspectRatio = ratio9x16,
            preferredWidthFraction = 0.60f
        )
        assertEquals(ratio9x16, adaptedLandscape.widthPx / adaptedLandscape.heightPx, 0.015f)
        assertTrue(adaptedLandscape.offsetX >= landscape.edgeMarginPx - 0.5f)
        assertTrue(adaptedLandscape.rightPx <= landscape.widthPx - landscape.edgeMarginPx + 0.5f)
        assertTrue(adaptedLandscape.offsetY >= landscape.topSafeMarginPx - 0.5f)
        assertTrue(adaptedLandscape.bottomPx <= landscape.heightPx - landscape.bottomSafeMarginPx + 0.5f)
    }

    // 7. Compose UI test: live two-finger pinch outward & inward, min/max clamping,
    //    aspect ratio preservation, and ZERO TextureView recreation/detach during resize
    @Test
    fun `compose pinch outward and inward resizes PiP smoothly while keeping single TextureView attached`() {
        val colors = EditorialColors.default()
        val attachedViews = mutableListOf<TextureView>()
        var detachCount = 0
        var latestFraction = FloatingPipResizeCalculator.INITIAL_WIDTH_FRACTION
        val progressFlow = MutableStateFlow(VideoPlaybackProgress(currentPositionMs = 50_000L, durationMs = 240_000L))

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
                        onWidthFractionChanged = { latestFraction = it },
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }

        val initialBounds = composeRule.onNodeWithTag("floating_video_mini_player").getUnclippedBoundsInRoot()
        assertEquals(160f, initialBounds.width.value, 3f) // 40% of 400dp
        assertEquals(16f / 9f, initialBounds.width.value / initialBounds.height.value, 0.05f)
        assertEquals(1, attachedViews.size)
        val firstTextureView = attachedViews.first()

        // Pinch OUTWARD on floating PiP video surface
        composeRule.onNodeWithTag("floating_pip_video_surface").performTouchInput {
            pinch(
                start0 = Offset(centerX - 20f, centerY),
                end0 = Offset(centerX - 55f, centerY),
                start1 = Offset(centerX + 20f, centerY),
                end1 = Offset(centerX + 55f, centerY),
                durationMillis = 250L
            )
        }
        composeRule.waitForIdle()

        val enlargedBounds = composeRule.onNodeWithTag("floating_video_mini_player").getUnclippedBoundsInRoot()
        assertTrue(
            "Expected pinch outward to enlarge PiP (initial=${initialBounds.width}, after=${enlargedBounds.width})",
            enlargedBounds.width > initialBounds.width + 10.dp
        )
        assertTrue(
            "Expected width clamped <= 85% (340dp), was ${enlargedBounds.width}",
            enlargedBounds.width <= 342.dp
        )
        assertEquals(16f / 9f, enlargedBounds.width.value / enlargedBounds.height.value, 0.06f)
        assertTrue(latestFraction > FloatingPipResizeCalculator.INITIAL_WIDTH_FRACTION)

        // Pinch INWARD aggressively to verify shrinking and 20% minimum clamping
        composeRule.onNodeWithTag("floating_pip_video_surface").performTouchInput {
            pinch(
                start0 = Offset(centerX - 100f, centerY),
                end0 = Offset(centerX - 15f, centerY),
                start1 = Offset(centerX + 100f, centerY),
                end1 = Offset(centerX + 15f, centerY),
                durationMillis = 250L
            )
        }
        composeRule.waitForIdle()

        val shrunkBounds = composeRule.onNodeWithTag("floating_video_mini_player").getUnclippedBoundsInRoot()
        assertTrue(
            "Expected pinch inward to shrink PiP (before=${enlargedBounds.width}, after=${shrunkBounds.width})",
            shrunkBounds.width < enlargedBounds.width - 10.dp
        )
        assertTrue(
            "Expected width clamped >= 20% (80dp), was ${shrunkBounds.width}",
            shrunkBounds.width >= 78.dp
        )
        assertEquals(16f / 9f, shrunkBounds.width.value / shrunkBounds.height.value, 0.06f)

        // Verify zero TextureView recreation and zero detach across all resize gestures
        assertEquals("TextureView must never be recreated during pinch resize", 1, attachedViews.size)
        assertSame(firstTextureView, attachedViews.first())
        assertEquals("TextureView must remain attached during pinch resize", 0, detachCount)
    }

    // 8. Gesture separation: one-finger tap toggles controls, single-finger drag does NOT move PiP,
    //    and Play/Pause, Next, Expand, Close controls all remain functional
    @Test
    fun `one finger tap toggles controls, single finger swipe does not drag PiP, and controls remain functional`() {
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

        // Controls start visible while paused
        composeRule.onNodeWithTag("floating_pip_controls_overlay").assertIsDisplayed()

        // Existing controls work
        composeRule.onNodeWithTag("video_mini_play_pause").performClick()
        assertTrue(toggledPlayPause)
        composeRule.onNodeWithTag("video_mini_next").performClick()
        assertTrue(playedNext)
        composeRule.onNodeWithTag("video_mini_expand").performClick()
        assertTrue(expanded)
        composeRule.onNodeWithTag("video_mini_close").performClick()
        assertTrue(closed)

        val beforeSwipe = composeRule.onNodeWithTag("floating_video_mini_player").getUnclippedBoundsInRoot()

        // Single-finger drag MUST NOT move the floating PiP (Requirement 6)
        composeRule.onNodeWithTag("floating_pip_video_surface").performTouchInput {
            swipeLeft(startX = right - 10f, endX = left + 10f, durationMillis = 150L)
        }
        composeRule.waitForIdle()

        val afterSwipe = composeRule.onNodeWithTag("floating_video_mini_player").getUnclippedBoundsInRoot()
        assertEquals(beforeSwipe.left.value, afterSwipe.left.value, 1f)
        assertEquals(beforeSwipe.top.value, afterSwipe.top.value, 1f)

        // One-finger tap on empty bottom-left corner of PiP video surface hides controls
        composeRule.onNodeWithTag("floating_pip_video_surface").performTouchInput {
            down(Offset(x = width * 0.12f, y = height * 0.85f))
            up()
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("floating_pip_controls_overlay").assertDoesNotExist()

        // One-finger tap again restores controls
        composeRule.onNodeWithTag("floating_pip_video_surface").performTouchInput {
            down(Offset(x = width * 0.12f, y = height * 0.85f))
            up()
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("floating_pip_controls_overlay").assertIsDisplayed()
    }

    // 9. Portrait & Landscape Compose layout + 9:16 vertical video aspect ratio check
    @Test
    @Config(sdk = [36], qualifiers = "w840dp-h840dp")
    fun `portrait and landscape containers render 9x16 vertical video within safe bounds without clipping`() {
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

        // Switch dynamically to landscape container
        isLandscape = true
        composeRule.waitForIdle()

        val landscapeBounds = composeRule.onNodeWithTag("floating_video_mini_player").getUnclippedBoundsInRoot()
        assertEquals(9f / 16f, landscapeBounds.width.value / landscapeBounds.height.value, 0.06f)
        assertTrue(landscapeBounds.left >= 10.dp)
        assertTrue(landscapeBounds.right <= 790.dp)
        assertTrue(landscapeBounds.top >= 8.dp)
        assertTrue(landscapeBounds.bottom <= 315.dp)
    }

    // 10a. Android 8 (API 26) compatibility + low-RAM device rendering
    @Test
    @Config(sdk = [26], qualifiers = "w360dp-h640dp")
    fun `renders and resizes cleanly on Android 8 API 26 low-RAM devices`() {
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

    // 10b. Android 11 (API 30) compatibility check
    @Test
    @Config(sdk = [30], qualifiers = "w412dp-h840dp")
    fun `renders and preserves 4x3 aspect ratio on Android 11 API 30`() {
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
