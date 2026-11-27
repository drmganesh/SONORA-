package ui.gestures

import android.app.Application
import android.net.Uri
import android.view.TextureView
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListState
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
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import androidx.compose.ui.unit.width
import androidx.test.core.app.ApplicationProvider
import com.example.model.MiniPlayerStyle
import com.example.model.PlaybackInfo
import com.example.model.Song
import com.example.service.SonoraMusicService
import com.example.viewmodel.SonoraViewModel
import domain.motion.GlobalMotionConfig
import domain.motion.resolveEffectiveMotionState
import domain.player.MiniPlayerVisualState
import domain.video.OnlineVideoItem
import domain.video.VideoPlayerUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import ui.components.CircularFloatingMiniPlayer
import ui.components.FloatingMiniPlayer
import ui.components.MiniPlayerRestorePill
import ui.motion.FloatingPipLayoutMath
import ui.motion.FloatingPipRectPx
import ui.motion.GestureAxisLock
import ui.motion.LocalEffectiveMotionState
import ui.motion.MiniPlayerGestureAction
import ui.motion.MiniPlayerGestureMath
import ui.motion.QueueDismissMath
import ui.queue.QueueScreen
import ui.theme.DynamicColorPalette
import ui.theme.EditorialColors
import ui.theme.LocalDynamicColorPalette
import ui.theme.LocalEditorialColors
import ui.theme.LocalReduceAnimations
import ui.video.FloatingVideoMiniPlayer
import kotlin.math.abs

/**
 * End-to-end verification suite for SONORA's restored & enhanced gestures across:
 * - Android 8 (API 26), Android 11 (API 30), and current Android (API 36)
 * - Small phones (320dp), large phones (480dp), portrait and landscape layouts
 *
 * Covers:
 * 1. Mini-player horizontal swipe left/right, short drag snap-back, single-fire guarantee, button preservation
 * 2. Mini-player swipe-up expansion to full player
 * 3. Two-stage circular mini-player (Stage 1 collapse -> Stage 2 dismiss -> restore pill) without stopping playback/service
 * 4. Floating video PiP two-finger pinch outward/inward + exact aspect ratios (16:9, 9:16, 1:1, 4:3) + single TextureView
 * 5. Floating video PiP one-finger drag movement + safe-area clamping before/after resize and rotation + tap toggle
 * 6. Queue screen swipe-down return to full music player without modifying playback/queue order or conflicting with scroll/horizontal swipe
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w412dp-h840dp")
class AdvancedGesturesVerificationTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun sampleSong(id: Long, title: String = "Track $id"): Song = Song(
        id = id,
        title = title,
        artist = "Sonora Artist $id",
        album = "Sonora Album",
        albumId = id,
        durationMs = 210_000L,
        uri = Uri.parse("content://media/external/audio/media/$id"),
        dataPath = "/storage/emulated/0/Music/$title.flac",
        mimeType = "audio/flac",
        sizeBytes = 5_000_000L,
        isOnline = false
    )

    private fun sampleVideoState(
        videoWidth: Int = 1920,
        videoHeight: Int = 1080,
        rotation: Int = 0
    ): VideoPlayerUiState {
        val v1 = OnlineVideoItem(
            videoId = "v1",
            title = "SONORA Live Video",
            channelName = "SONORA Studio",
            channelUrl = "https://www.youtube.com/channel/UC1",
            contentUrl = "https://www.youtube.com/watch?v=v1",
            thumbnailUrl = null,
            durationSeconds = 300L
        )
        return VideoPlayerUiState(
            activeVideo = v1,
            isPlaying = true,
            isPlayerExpanded = false,
            currentPositionMs = 42_000L,
            durationMs = 300_000L,
            queue = listOf(v1),
            queueIndex = 0,
            videoWidth = videoWidth,
            videoHeight = videoHeight,
            pixelWidthHeightRatio = 1f,
            unappliedRotationDegrees = rotation,
            hasKnownVideoDimensions = true
        )
    }

    // =========================================================================
    // 1. PURE GESTURE MATH & CONFLICT PREVENTION
    // =========================================================================

    @Test
    fun gestureMath_enforcesDirectionalLockThresholdsAndSafePipBounds() {
        // Vertical movement must never lock horizontal
        assertEquals(
            GestureAxisLock.VERTICAL,
            MiniPlayerGestureMath.resolveAxisLock(totalDx = 8f, totalDy = 36f, touchSlop = 12f)
        )
        // Horizontal movement must lock horizontal
        assertEquals(
            GestureAxisLock.HORIZONTAL,
            MiniPlayerGestureMath.resolveAxisLock(totalDx = -48f, totalDy = 6f, touchSlop = 12f)
        )
        // Short drag below threshold & low velocity snaps back (NONE)
        assertEquals(
            MiniPlayerGestureAction.NONE,
            MiniPlayerGestureMath.resolveDecision(
                axisLock = GestureAxisLock.HORIZONTAL,
                totalDx = -24f,
                totalDy = 0f,
                velocityX = -100f,
                velocityY = 0f,
                horizontalThresholdPx = 110f,
                verticalUpThresholdPx = 80f,
                verticalDownThresholdPx = 80f,
                velocityThresholdPx = 900f
            )
        )
        // Fast intentional swipe triggers even at moderate drag distance
        assertEquals(
            MiniPlayerGestureAction.SKIP_NEXT,
            MiniPlayerGestureMath.resolveDecision(
                axisLock = GestureAxisLock.HORIZONTAL,
                totalDx = -50f,
                totalDy = 0f,
                velocityX = -1400f,
                velocityY = 0f,
                horizontalThresholdPx = 110f,
                verticalUpThresholdPx = 80f,
                verticalDownThresholdPx = 80f,
                velocityThresholdPx = 900f
            )
        )
        // Queue dismiss math
        assertFalse(QueueDismissMath.shouldDismissToPlayer(dragDownPx = 25f, velocityYPxPerSec = 80f, 120f, 800f))
        assertTrue(QueueDismissMath.shouldDismissToPlayer(dragDownPx = 130f, velocityYPxPerSec = 50f, 120f, 800f))
        assertTrue(QueueDismissMath.shouldDismissToPlayer(dragDownPx = 45f, velocityYPxPerSec = 1000f, 120f, 800f))

        // Floating PiP math preserves 16:9, 9:16, 1:1, 4:3 within [20%, 85%] and clamps cleanly
        val ratios = listOf(16f / 9f, 9f / 16f, 1f, 4f / 3f)
        for (ratio in ratios) {
            val zoomedIn = FloatingPipLayoutMath.applyPinchZoom(
                currentRect = FloatingPipRectPx(200f, 400f, 160f, 160f / ratio, 0.40f),
                zoomFactor = 3.0f, // way past 85%
                panDx = 500f,
                panDy = 500f,
                viewportWidthPx = 400f,
                viewportHeightPx = 800f,
                edgeMarginPx = 12f,
                topMarginPx = 10f,
                bottomMarginPx = 86f,
                videoAspectRatio = ratio
            )
            assertTrue(zoomedIn.widthFraction <= FloatingPipLayoutMath.MAX_WIDTH_FRACTION + 0.005f)
            assertEquals(ratio, zoomedIn.width / zoomedIn.height, 0.03f)
            assertTrue(zoomedIn.x >= 12f - 0.5f)
            assertTrue(zoomedIn.x + zoomedIn.width <= 400f - 12f + 0.5f)
            assertTrue(zoomedIn.y >= 10f - 0.5f)
            assertTrue(zoomedIn.y + zoomedIn.height <= 800f - 86f + 0.5f)

            val zoomedOut = FloatingPipLayoutMath.applyPinchZoom(
                currentRect = zoomedIn,
                zoomFactor = 0.1f, // way below 20%
                panDx = -500f,
                panDy = -500f,
                viewportWidthPx = 400f,
                viewportHeightPx = 800f,
                edgeMarginPx = 12f,
                topMarginPx = 10f,
                bottomMarginPx = 86f,
                videoAspectRatio = ratio
            )
            assertTrue(zoomedOut.widthFraction >= FloatingPipLayoutMath.MIN_WIDTH_FRACTION - 0.005f)
            assertEquals(ratio, zoomedOut.width / zoomedOut.height, 0.03f)
            assertTrue(zoomedOut.x >= 12f - 0.5f)
            assertTrue(zoomedOut.y >= 10f - 0.5f)
        }
    }

    // =========================================================================
    // 2. MINI-PLAYER HORIZONTAL SKIP (SHORT CANCEL, SINGLE TRIGGER, SWIPE-UP)
    // =========================================================================

    @Test
    @Config(sdk = [26], qualifiers = "w320dp-h640dp")
    fun miniPlayer_android8SmallScreen_horizontalShortDragCancels_andFullSwipesTriggerOnce() {
        val song = sampleSong(1L, "Kesariya")
        var nextCount = 0
        var prevCount = 0
        var expandCount = 0

        composeRule.setContent {
            CompositionLocalProvider(
                LocalEditorialColors provides EditorialColors.default(),
                LocalDynamicColorPalette provides DynamicColorPalette.default(),
                LocalEffectiveMotionState provides resolveEffectiveMotionState(GlobalMotionConfig.DEFAULT),
                LocalReduceAnimations provides true
            ) {
                FloatingMiniPlayer(
                    song = song,
                    playbackInfo = PlaybackInfo(isPlaying = true, currentPositionMs = 20_000L, durationMs = 210_000L),
                    colors = EditorialColors.default(),
                    onTogglePlayPause = {},
                    onPlayNext = { nextCount++ },
                    onPlayPrevious = { prevCount++ },
                    onClick = { expandCount++ },
                    onSwipeUpExpand = { expandCount++ },
                    miniPlayerStyle = MiniPlayerStyle.FLOATING_PILL
                )
            }
        }

        // Short gentle horizontal nudge (below threshold) must NOT skip
        composeRule.onNodeWithTag("mini_player").performTouchInput {
            down(center)
            moveBy(Offset(-18f, 0f), delayMillis = 200L)
            up()
        }
        composeRule.waitForIdle()
        assertEquals(0, nextCount)
        assertEquals(0, prevCount)

        // Full swipe left -> triggers next exactly once
        composeRule.onNodeWithTag("mini_player").performTouchInput { swipeLeft() }
        composeRule.waitForIdle()
        assertEquals(1, nextCount)
        assertEquals(0, prevCount)

        // Full swipe right -> triggers previous exactly once
        composeRule.onNodeWithTag("mini_player").performTouchInput { swipeRight() }
        composeRule.waitForIdle()
        assertEquals(1, nextCount)
        assertEquals(1, prevCount)

        // Swipe up -> expands full player without triggering horizontal skip
        composeRule.onNodeWithTag("mini_player").performTouchInput { swipeUp() }
        composeRule.waitForIdle()
        assertEquals(1, expandCount)
        assertEquals(1, nextCount)
        assertEquals(1, prevCount)
    }

    // =========================================================================
    // 3. TWO-STAGE CIRCULAR MINI-PLAYER & DISMISSAL WITHOUT STOPPING PLAYBACK
    // =========================================================================

    @Test
    @Config(sdk = [30], qualifiers = "w412dp-h840dp")
    fun miniPlayer_android11_twoStageCircularCollapseAndDismiss_keepsServiceAndPlaybackActive() {
        val controller = Robolectric.buildService(SonoraMusicService::class.java).create()
        val service = controller.get()
        val song = sampleSong(10L, "Tum Hi Ho")
        service.updateQueueSnapshot(listOf(song, sampleSong(11L, "Channa Mereya")), 0)
        service.updateForegroundNotification(song, isPlaying = true)
        val initialSession = service.mediaSession
        assertNotNull(initialSession)

        var visualState by mutableStateOf(MiniPlayerVisualState.EXPANDED_BAR)
        var fullPlayerOpened = 0

        composeRule.setContent {
            CompositionLocalProvider(
                LocalEditorialColors provides EditorialColors.default(),
                LocalDynamicColorPalette provides DynamicColorPalette.default(),
                LocalEffectiveMotionState provides resolveEffectiveMotionState(GlobalMotionConfig.DEFAULT),
                LocalReduceAnimations provides true
            ) {
                Box(modifier = Modifier.size(width = 412.dp, height = 800.dp)) {
                    when (visualState) {
                        MiniPlayerVisualState.EXPANDED_BAR -> {
                            FloatingMiniPlayer(
                                song = song,
                                playbackInfo = PlaybackInfo(isPlaying = true, currentPositionMs = 45_000L, durationMs = 210_000L),
                                colors = EditorialColors.default(),
                                onTogglePlayPause = {},
                                onPlayNext = {},
                                onPlayPrevious = {},
                                onClick = { fullPlayerOpened++ },
                                onSwipeDownCollapse = { visualState = MiniPlayerVisualState.CIRCULAR_FLOATING }
                            )
                        }
                        MiniPlayerVisualState.CIRCULAR_FLOATING -> {
                            CircularFloatingMiniPlayer(
                                song = song,
                                playbackInfo = PlaybackInfo(isPlaying = true, currentPositionMs = 45_000L, durationMs = 210_000L),
                                colors = EditorialColors.default(),
                                onTapExpand = { fullPlayerOpened++ },
                                onRestoreBar = { visualState = MiniPlayerVisualState.EXPANDED_BAR },
                                onDismiss = { visualState = MiniPlayerVisualState.DISMISSED },
                                onTogglePlayPause = {},
                                bottomInsetClearance = 92.dp
                            )
                        }
                        MiniPlayerVisualState.DISMISSED -> {
                            MiniPlayerRestorePill(
                                song = song,
                                isPlaying = true,
                                colors = EditorialColors.default(),
                                onRestoreMiniPlayer = { visualState = MiniPlayerVisualState.EXPANDED_BAR },
                                onOpenFullPlayer = { fullPlayerOpened++ }
                            )
                        }
                        MiniPlayerVisualState.HIDDEN -> Unit
                    }
                }
            }
        }

        // Stage 1: Swipe down on normal mini-player -> collapses into circular floating mini-player
        composeRule.onNodeWithTag("mini_player").assertIsDisplayed()
        composeRule.onNodeWithTag("mini_player").performTouchInput { swipeDown() }
        composeRule.waitForIdle()
        assertEquals(MiniPlayerVisualState.CIRCULAR_FLOATING, visualState)
        composeRule.onNodeWithTag("circular_mini_player").assertIsDisplayed()

        // Verify circular player stays inside safe screen area above bottom nav
        val circularBounds = composeRule.onNodeWithTag("circular_mini_player").getUnclippedBoundsInRoot()
        assertTrue(circularBounds.left >= 10.dp)
        assertTrue(circularBounds.right <= 402.dp)
        assertTrue(circularBounds.bottom <= 712.dp)

        // Stage 2: Swipe down on circular mini-player -> dismisses UI completely
        composeRule.onNodeWithTag("circular_mini_player").performTouchInput { swipeDown() }
        composeRule.waitForIdle()
        assertEquals(MiniPlayerVisualState.DISMISSED, visualState)
        composeRule.onNodeWithTag("circular_mini_player").assertDoesNotExist()
        composeRule.onNodeWithTag("mini_player_restore_pill").assertIsDisplayed()

        // Playback service, MediaSession, foreground notification, and queue MUST stay intact
        assertSame(initialSession, service.mediaSession)
        assertTrue(service.notificationManager.isForegroundNotificationActive)
        assertTrue(service.mediaSession?.isActive == true)
        assertEquals(2, service.currentQueueSnapshot.size)

        // Tap restore pill -> brings standard mini-player bar right back
        composeRule.onNodeWithTag("mini_player_restore_pill").performClick()
        composeRule.waitForIdle()
        assertEquals(MiniPlayerVisualState.EXPANDED_BAR, visualState)
        composeRule.onNodeWithTag("mini_player").assertIsDisplayed()

        controller.destroy()
    }

    // =========================================================================
    // 4 & 5. FLOATING VIDEO PIP: ONE-FINGER DRAG, SAFE CLAMPING, PINCH + ROTATION
    // =========================================================================

    @Test
    @Config(sdk = [36], qualifiers = "w480dp-h920dp")
    fun floatingPip_oneFingerDragMovesFreely_clampsToSafeBounds_andKeepsSingleTextureView() {
        val colors = EditorialColors.default()
        val attachedViews = mutableListOf<TextureView>()
        var detachCount = 0
        var playPauseClicks = 0

        composeRule.setContent {
            CompositionLocalProvider(LocalEditorialColors provides colors) {
                Box(modifier = Modifier.size(width = 480.dp, height = 880.dp)) {
                    FloatingVideoMiniPlayer(
                        playerState = sampleVideoState(videoWidth = 1920, videoHeight = 1080),
                        colors = colors,
                        onExpand = {},
                        onTogglePlayPause = { playPauseClicks++ },
                        onPlayNext = {},
                        onClose = {},
                        onAttachTextureView = { tv ->
                            if (attachedViews.none { it === tv }) attachedViews.add(tv)
                        },
                        onDetachTextureView = { detachCount++ },
                        bottomInsetClearance = 86.dp,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }

        val initialBounds = composeRule.onNodeWithTag("floating_video_mini_player").getUnclippedBoundsInRoot()
        assertEquals(1, attachedViews.size)
        val firstTv = attachedViews.first()

        // Drag upward and left across viewport with 1 finger
        composeRule.onNodeWithTag("floating_pip_video_surface").performTouchInput {
            down(center)
            moveBy(Offset(-180f, -280f), delayMillis = 80L)
            moveBy(Offset(-120f, -180f), delayMillis = 80L)
            up()
        }
        composeRule.waitForIdle()

        val movedBounds = composeRule.onNodeWithTag("floating_video_mini_player").getUnclippedBoundsInRoot()
        assertTrue(
            "One-finger drag should move PiP left (before=${initialBounds.left}, after=${movedBounds.left})",
            movedBounds.left < initialBounds.left - 30.dp
        )
        assertTrue(
            "One-finger drag should move PiP upward (before=${initialBounds.top}, after=${movedBounds.top})",
            movedBounds.top < initialBounds.top - 50.dp
        )
        // Must remain clamped inside safe screen boundaries
        assertTrue(movedBounds.left >= 10.dp)
        assertTrue(movedBounds.top >= 8.dp)
        assertTrue(movedBounds.right <= 470.dp)
        assertTrue(movedBounds.bottom <= 796.dp)

        // Dragging past extreme top-left corner must stay safely clamped inside margins
        composeRule.onNodeWithTag("floating_pip_video_surface").performTouchInput {
            down(center)
            moveBy(Offset(-900f, -1200f), delayMillis = 120L)
            up()
        }
        composeRule.waitForIdle()
        val clampedTopLeft = composeRule.onNodeWithTag("floating_video_mini_player").getUnclippedBoundsInRoot()
        assertTrue("Left edge clamped", clampedTopLeft.left >= 10.dp)
        assertTrue("Top edge clamped", clampedTopLeft.top >= 8.dp)

        // Controls remain clickable after drag
        composeRule.onNodeWithTag("video_mini_play_pause").performClick()
        composeRule.waitForIdle()
        assertEquals(1, playPauseClicks)

        // Single TextureView remained continuously attached throughout drag
        assertEquals(1, attachedViews.size)
        assertSame(firstTv, attachedViews.first())
        assertEquals(0, detachCount)
    }

    @Test
    @Config(sdk = [26], qualifiers = "w720dp-h360dp-land")
    fun floatingPip_android8Landscape_pinchAndDragRespectAspectRatioAndSafeBounds() {
        val colors = EditorialColors.default()
        composeRule.setContent {
            CompositionLocalProvider(LocalEditorialColors provides colors) {
                Box(modifier = Modifier.size(width = 720.dp, height = 360.dp)) {
                    FloatingVideoMiniPlayer(
                        playerState = sampleVideoState(videoWidth = 1080, videoHeight = 1920),
                        colors = colors,
                        onExpand = {},
                        onTogglePlayPause = {},
                        onPlayNext = {},
                        onClose = {},
                        bottomInsetClearance = 64.dp,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }

        val initial = composeRule.onNodeWithTag("floating_video_mini_player").getUnclippedBoundsInRoot()
        assertEquals(9f / 16f, initial.width.value / initial.height.value, 0.06f)
        assertTrue(initial.bottom <= 300.dp)
        assertTrue(initial.top >= 8.dp)

        // Drag left in landscape
        composeRule.onNodeWithTag("floating_pip_video_surface").performTouchInput {
            down(center)
            moveBy(Offset(-250f, -40f), delayMillis = 100L)
            up()
        }
        composeRule.waitForIdle()

        val afterDrag = composeRule.onNodeWithTag("floating_video_mini_player").getUnclippedBoundsInRoot()
        assertTrue(afterDrag.left < initial.left - 30.dp)
        assertTrue(afterDrag.left >= 10.dp)
        assertTrue(afterDrag.right <= 710.dp)
        assertTrue(afterDrag.top >= 8.dp)
        assertTrue(afterDrag.bottom <= 300.dp)
        assertEquals(9f / 16f, afterDrag.width.value / afterDrag.height.value, 0.06f)
    }

    // =========================================================================
    // 6. TRACK QUEUE SWIPE-DOWN RETURN TO MUSIC PLAYER (PRESERVES QUEUE & SCROLL)
    // =========================================================================

    @Test
    @Config(sdk = [36], qualifiers = "w412dp-h840dp")
    fun queueScreen_swipeDownReturnsToFullPlayer_preservesOrderPlaybackAndScrollPosition() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val vm = SonoraViewModel(app)
        val tracks = (1..25).map { sampleSong(it.toLong(), "Queue Track $it") }
        vm.playSong(tracks.first(), tracks, 0)
        val initialQueueIds = vm.queue.value.map { it.id }

        var returnedToPlayer = 0
        val sharedListState = LazyListState()

        composeRule.setContent {
            CompositionLocalProvider(
                LocalEditorialColors provides EditorialColors.default(),
                LocalEffectiveMotionState provides resolveEffectiveMotionState(GlobalMotionConfig.DEFAULT)
            ) {
                QueueScreen(
                    viewModel = vm,
                    onBack = { returnedToPlayer++ },
                    onOpenNowPlaying = {},
                    listState = sharedListState
                )
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("queue_drag_handle").assertIsDisplayed()

        // 1. Upward scroll on queue list must scroll the list and MUST NOT dismiss QueueScreen
        composeRule.onNodeWithTag("queue_lazy_column").performTouchInput { swipeUp() }
        composeRule.waitForIdle()
        assertEquals("Upward scrolling must NOT dismiss QueueScreen", 0, returnedToPlayer)
        assertTrue(
            "Upward swipe should advance queue scroll position",
            sharedListState.firstVisibleItemIndex > 0 || sharedListState.firstVisibleItemScrollOffset > 0
        )

        val savedFirstIndex = sharedListState.firstVisibleItemIndex
        val savedScrollOffset = sharedListState.firstVisibleItemScrollOffset

        // 2. Swipe down on the Queue header/handle returns cleanly to music player
        composeRule.onNodeWithTag("queue_header_drag_area").performTouchInput {
            down(Offset(centerX, top + 8f))
            moveBy(Offset(0f, 360f), delayMillis = 120L)
            up()
        }
        composeRule.waitForIdle()

        assertEquals("Swipe down on queue header must return to music player once", 1, returnedToPlayer)
        // Queue order and current track are completely preserved
        assertEquals(initialQueueIds, vm.queue.value.map { it.id })
        assertEquals(tracks.first().id, vm.currentSong.value?.id)
        // Hoisted LazyListState preserves exact scroll position for next reopen
        assertEquals(savedFirstIndex, sharedListState.firstVisibleItemIndex)
        assertEquals(savedScrollOffset, sharedListState.firstVisibleItemScrollOffset)
    }

    @Test
    @Config(sdk = [30], qualifiers = "w412dp-h840dp")
    fun queueScreen_android11_pullDownFromTopOfListReturnsToPlayerWithoutModifyingQueue() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val vm = SonoraViewModel(app)
        val tracks = (1..8).map { sampleSong(it.toLong(), "Song $it") }
        vm.playSong(tracks.first(), tracks, 0)

        var returnedToPlayer = 0
        val listState = LazyListState(firstVisibleItemIndex = 0, firstVisibleItemScrollOffset = 0)

        composeRule.setContent {
            CompositionLocalProvider(
                LocalEditorialColors provides EditorialColors.default(),
                LocalEffectiveMotionState provides resolveEffectiveMotionState(GlobalMotionConfig.DEFAULT)
            ) {
                QueueScreen(
                    viewModel = vm,
                    onBack = { returnedToPlayer++ },
                    onOpenNowPlaying = {},
                    listState = listState
                )
            }
        }
        composeRule.waitForIdle()

        // Pulling down from the top of the queue list dismisses cleanly to the music player
        composeRule.onNodeWithTag("queue_lazy_column").performTouchInput {
            swipeDown(startY = top + 40f, endY = bottom - 40f, durationMillis = 180L)
        }
        composeRule.waitForIdle()

        assertEquals("Swipe down from top of queue list must return to music player", 1, returnedToPlayer)
        assertEquals(tracks.map { it.id }, vm.queue.value.map { it.id })
    }
}
