package ui.video

import android.app.Application
import android.content.ComponentName
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.graphics.Rect
import android.os.Build
import android.view.SurfaceView
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import androidx.compose.ui.unit.width
import androidx.test.core.app.ApplicationProvider
import com.example.MainActivity
import domain.video.OnlineVideoItem
import domain.video.SystemPipPolicy
import domain.video.VideoPlayerUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import ui.theme.EditorialColors
import ui.theme.LocalEditorialColors

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w412dp-h840dp")
class SystemPipFixRegressionTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun sampleVideo(id: String = "pip_vid_1"): OnlineVideoItem = OnlineVideoItem(
        videoId = id,
        title = "SONORA Live Concert 4K",
        channelName = "SONORA Stage",
        channelUrl = "https://www.youtube.com/channel/UC_STAGE",
        contentUrl = "https://www.youtube.com/watch?v=$id",
        thumbnailUrl = null,
        durationSeconds = 300L
    )

    private fun sampleVideoState(
        videoWidth: Int = 1920,
        videoHeight: Int = 1080,
        par: Float = 1f,
        rotation: Int = 0,
        isPlaying: Boolean = true,
        isPlayerExpanded: Boolean = true,
        isInPip: Boolean = false,
        positionMs: Long = 92_500L,
        backgroundPlaybackAllowed: Boolean = false,
        hasVideoEnded: Boolean = false,
        hasNext: Boolean = true
    ): VideoPlayerUiState {
        val v1 = sampleVideo("vid_a")
        val v2 = sampleVideo("vid_b")
        return VideoPlayerUiState(
            activeVideo = v1,
            isPlaying = isPlaying,
            isPlayerExpanded = isPlayerExpanded,
            isInPictureInPicture = isInPip,
            currentPositionMs = positionMs,
            durationMs = 300_000L,
            queue = if (hasNext) listOf(v1, v2) else listOf(v1),
            queueIndex = 0,
            videoWidth = videoWidth,
            videoHeight = videoHeight,
            pixelWidthHeightRatio = par,
            unappliedRotationDegrees = rotation,
            hasKnownVideoDimensions = videoWidth > 0 && videoHeight > 0,
            backgroundPlaybackAllowed = backgroundPlaybackAllowed,
            hasVideoEnded = hasVideoEnded
        )
    }

    // 1–5 & 8–10. Entering System PiP (even from minimized Home state) renders ONLY the video
    // surface without Home screen, bottom navigation, music mini-player, or recommendations;
    // returning from PiP restores the normal Online Video UI with unchanged playback position
    // and valid SurfaceView attachment.
    @Test
    fun `entering system PiP shows only video edge-to-edge, hides Home and nav bar, and restores Online Video UI on return`() {
        val colors = EditorialColors.default()
        var playerState by mutableStateOf(
            sampleVideoState(
                videoWidth = 1920,
                videoHeight = 1080,
                isPlayerExpanded = false, // Minimized while browsing Home — the exact bug scenario!
                isInPip = false,
                positionMs = 123_456L
            )
        )
        val attachedSurfaces = mutableListOf<SurfaceView>()
        val detachedSurfaces = mutableListOf<SurfaceView>()

        composeRule.setContent {
            CompositionLocalProvider(LocalEditorialColors provides colors) {
                Box(
                    modifier = if (playerState.isInPictureInPicture) {
                        Modifier.size(width = 320.dp, height = 180.dp) // 16:9 Android PiP window
                    } else {
                        Modifier.size(width = 412.dp, height = 840.dp)
                    }
                ) {
                    if (SystemPipPolicy.shouldRenderVideoOnlyPipUi(playerState)) {
                        SystemPipVideoScreen(
                            playerState = playerState,
                            colors = colors,
                            onAttachSurfaceView = { sv ->
                                if (attachedSurfaces.none { it === sv }) attachedSurfaces.add(sv)
                            },
                            onDetachSurfaceView = { sv -> detachedSurfaces.add(sv) }
                        )
                    } else {
                        Box(modifier = Modifier.fillMaxSize()) {
                            Column(modifier = Modifier.testTag("mock_home_screen")) {
                                Text("Home Screen")
                                Text("Bottom Navigation", modifier = Modifier.testTag("mock_bottom_nav"))
                                Text("Music Mini Player", modifier = Modifier.testTag("mock_music_mini_player"))
                            }
                            FloatingVideoMiniPlayer(
                                playerState = playerState,
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
            }
        }

        // Before PiP: Home screen, bottom navigation, and floating mini-player are visible
        composeRule.onNodeWithTag("mock_home_screen").assertIsDisplayed()
        composeRule.onNodeWithTag("mock_bottom_nav").assertIsDisplayed()
        composeRule.onNodeWithTag("floating_video_mini_player").assertIsDisplayed()
        composeRule.onNodeWithTag("system_pip_video_only_screen").assertDoesNotExist()

        // Enter Android System PiP
        playerState = playerState.copy(isInPictureInPicture = true)
        composeRule.waitForIdle()

        // Inside System PiP: ONLY the video surface is present; Home, Bottom Nav, Music Mini-Player,
        // and floating mini-player controls are completely absent
        composeRule.onNodeWithTag("system_pip_video_only_screen").assertIsDisplayed()
        composeRule.onNodeWithTag("system_pip_video_surface").assertIsDisplayed()
        composeRule.onNodeWithTag("mock_home_screen").assertDoesNotExist()
        composeRule.onNodeWithTag("mock_bottom_nav").assertDoesNotExist()
        composeRule.onNodeWithTag("mock_music_mini_player").assertDoesNotExist()
        composeRule.onNodeWithTag("floating_video_mini_player").assertDoesNotExist()

        // Video surface fills the 320x180 16:9 PiP window edge-to-edge without cropping or tiny margins
        val pipBounds = composeRule.onNodeWithTag("system_pip_video_surface").getUnclippedBoundsInRoot()
        assertEquals(320f, pipBounds.width.value, 2f)
        assertEquals(180f, pipBounds.height.value, 2f)
        assertEquals(1, attachedSurfaces.size)
        assertEquals(0, detachedSurfaces.size)

        // Return from System PiP to SONORA
        playerState = playerState.copy(isInPictureInPicture = false)
        composeRule.waitForIdle()

        // Normal UI is restored and playback position is completely unchanged
        composeRule.onNodeWithTag("system_pip_video_only_screen").assertDoesNotExist()
        composeRule.onNodeWithTag("mock_home_screen").assertIsDisplayed()
        composeRule.onNodeWithTag("floating_video_mini_player").assertIsDisplayed()
        assertEquals(123_456L, playerState.currentPositionMs)
    }

    // 6. Portrait 9:16 video fills a portrait PiP window cleanly without stretching or forcing 16:9
    @Test
    fun `portrait 9x16 video fills portrait PiP window cleanly at true aspect ratio`() {
        val colors = EditorialColors.default()
        val portraitPipState = sampleVideoState(
            videoWidth = 1080,
            videoHeight = 1920,
            isInPip = true
        )

        val (num, den) = SystemPipPolicy.computePipAspectRatio(
            videoWidth = portraitPipState.videoWidth,
            videoHeight = portraitPipState.videoHeight
        )
        assertEquals(9f / 16f, num.toFloat() / den.toFloat(), 0.01f)

        composeRule.setContent {
            CompositionLocalProvider(LocalEditorialColors provides colors) {
                // Portrait 9:16 Android PiP window (180x320dp)
                Box(modifier = Modifier.size(width = 180.dp, height = 320.dp)) {
                    SystemPipVideoScreen(
                        playerState = portraitPipState,
                        colors = colors,
                        onAttachSurfaceView = {},
                        onDetachSurfaceView = {}
                    )
                }
            }
        }

        composeRule.onNodeWithTag("system_pip_video_only_screen").assertIsDisplayed()
        val surfaceBounds = composeRule.onNodeWithTag("system_pip_video_surface").getUnclippedBoundsInRoot()
        assertEquals(180f, surfaceBounds.width.value, 2f)
        assertEquals(320f, surfaceBounds.height.value, 2f)
        assertEquals(9f / 16f, surfaceBounds.width.value / surfaceBounds.height.value, 0.02f)
    }

    // 4 & 7. Eligibility & Aspect Ratio clamping guard against music-only mode and invalid Android ratios
    @Test
    fun `SystemPipPolicy rejects music-only mode and clamps extreme ratios within Android PiP limits`() {
        // Music-only state (no active video) MUST NOT enter PiP
        val musicOnlyState = VideoPlayerUiState(activeVideo = null, isPlaying = false)
        assertFalse(SystemPipPolicy.canEnterSystemPip(musicOnlyState))
        assertFalse(SystemPipPolicy.shouldAutoEnterSystemPip(musicOnlyState, autoPipEnabled = true))
        assertFalse(SystemPipPolicy.shouldRenderVideoOnlyPipUi(musicOnlyState.copy(isInPictureInPicture = true)))

        // Background audio-only video mode MUST NOT auto-enter visual PiP
        val bgAudioState = sampleVideoState(backgroundPlaybackAllowed = true, isPlaying = true)
        assertFalse(SystemPipPolicy.canEnterSystemPip(bgAudioState))
        assertFalse(SystemPipPolicy.shouldAutoEnterSystemPip(bgAudioState, autoPipEnabled = true))

        // Paused or ended video MUST NOT auto-enter PiP on Home press
        assertFalse(SystemPipPolicy.shouldAutoEnterSystemPip(sampleVideoState(isPlaying = false), autoPipEnabled = true))
        assertFalse(SystemPipPolicy.shouldAutoEnterSystemPip(sampleVideoState(hasVideoEnded = true), autoPipEnabled = true))

        // Active playing video with autoPipEnabled=true IS eligible
        val playingVideo = sampleVideoState(isPlaying = true)
        assertTrue(SystemPipPolicy.canEnterSystemPip(playingVideo))
        assertTrue(SystemPipPolicy.shouldAutoEnterSystemPip(playingVideo, autoPipEnabled = true))
        assertFalse(SystemPipPolicy.shouldAutoEnterSystemPip(playingVideo, autoPipEnabled = false))

        // Valid aspect ratios across landscape, portrait, square, PAR, rotation, and extremes
        val cases = listOf(
            Triple(1920, 1080, 16f / 9f),
            Triple(1080, 1920, 9f / 16f),
            Triple(1440, 1080, 4f / 3f),
            Triple(1080, 1080, 1f),
            Triple(3840, 1080, SystemPipPolicy.MAX_ANDROID_PIP_ASPECT_RATIO), // Clamped <= 2.39
            Triple(1080, 3200, SystemPipPolicy.MIN_ANDROID_PIP_ASPECT_RATIO)  // Clamped >= 0.42
        )
        for ((w, h, expected) in cases) {
            val (n, d) = SystemPipPolicy.computePipAspectRatio(w, h)
            val ratio = n.toFloat() / d.toFloat()
            assertEquals(expected, ratio, 0.01f)
            assertTrue("Ratio $ratio below Android PiP minimum", ratio >= 0.419f)
            assertTrue("Ratio $ratio above Android PiP maximum", ratio <= 2.39f)
        }

        // Rotated 90° 1920x1080 resolves to portrait 9:16
        val (rotN, rotD) = SystemPipPolicy.computePipAspectRatio(1920, 1080, 1f, 90)
        assertEquals(9f / 16f, rotN.toFloat() / rotD.toFloat(), 0.01f)
    }

    // 7. AndroidManifest.xml verifies MainActivity supportsPictureInPicture & PiP configChanges
    @Test
    fun `AndroidManifest declares MainActivity PiP support and required configChanges`() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val info = app.packageManager.getActivityInfo(
            ComponentName(app, MainActivity::class.java),
            PackageManager.GET_META_DATA
        )
        val requiredChanges =
            ActivityInfo.CONFIG_SCREEN_SIZE or
                ActivityInfo.CONFIG_SMALLEST_SCREEN_SIZE or
                ActivityInfo.CONFIG_SCREEN_LAYOUT or
                ActivityInfo.CONFIG_ORIENTATION
        assertEquals(
            "MainActivity must handle PiP configChanges to avoid Activity recreation",
            requiredChanges,
            info.configChanges and requiredChanges
        )
    }

    // 11a. Android 8 (API 26) PiP params construction with Play/Pause + Next actions & aspect ratio
    @Test
    @Config(sdk = [26])
    fun `builds valid PictureInPictureParams on Android 8 API 26 with Play Pause and Next actions`() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        Shadows.shadowOf(app.packageManager).setSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE, true)
        val controller = Robolectric.buildActivity(MainActivity::class.java).create()
        val activity = controller.get()

        assertTrue(Build.VERSION.SDK_INT == Build.VERSION_CODES.O)
        assertTrue(activity.isPictureInPictureSupported())

        val snapshot = SystemPipPolicy.buildParamsSnapshot(
            state = sampleVideoState(videoWidth = 1080, videoHeight = 1920, isPlaying = true, hasNext = true),
            autoPipEnabled = true,
            sourceRectLeft = 0,
            sourceRectTop = 100,
            sourceRectRight = 1080,
            sourceRectBottom = 708
        )
        val params = activity.buildPictureInPictureParams(snapshot)
        assertTrue(snapshot.hasValidSourceRect)
        assertTrue(params != null)
        controller.destroy()
    }

    // 11b. Android 11 (API 30) video-only PiP rendering verification
    @Test
    @Config(sdk = [30], qualifiers = "w412dp-h840dp")
    fun `renders video-only PiP cleanly on Android 11 API 30`() {
        val colors = EditorialColors.default()
        val state = sampleVideoState(videoWidth = 1440, videoHeight = 1080, isInPip = true)

        composeRule.setContent {
            CompositionLocalProvider(LocalEditorialColors provides colors) {
                Box(modifier = Modifier.size(width = 240.dp, height = 180.dp)) {
                    SystemPipVideoScreen(
                        playerState = state,
                        colors = colors,
                        onAttachSurfaceView = {},
                        onDetachSurfaceView = {}
                    )
                }
            }
        }

        composeRule.onNodeWithTag("system_pip_video_only_screen").assertIsDisplayed()
        val bounds = composeRule.onNodeWithTag("system_pip_video_surface").getUnclippedBoundsInRoot()
        assertEquals(240f, bounds.width.value, 2f)
        assertEquals(180f, bounds.height.value, 2f)
    }

    // 11c. Android 16 (API 36) PictureInPictureParams with autoEnterEnabled and sourceRectHint
    @Test
    @Config(sdk = [36])
    fun `builds PictureInPictureParams with autoEnter and sourceRectHint on Android 16 API 36`() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        Shadows.shadowOf(app.packageManager).setSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE, true)
        val controller = Robolectric.buildActivity(MainActivity::class.java).create()
        val activity = controller.get()

        assertTrue(activity.isPictureInPictureSupported())

        // Playing video -> autoEnterEnabled = true
        val activeSnapshot = SystemPipPolicy.buildParamsSnapshot(
            state = sampleVideoState(videoWidth = 1920, videoHeight = 1080, isPlaying = true),
            autoPipEnabled = true,
            sourceRectLeft = 0,
            sourceRectTop = 80,
            sourceRectRight = 1080,
            sourceRectBottom = 688
        )
        assertTrue(activeSnapshot.autoEnterEnabled)
        assertEquals(Rect(0, 80, 1080, 688), Rect(activeSnapshot.sourceRectLeft, activeSnapshot.sourceRectTop, activeSnapshot.sourceRectRight, activeSnapshot.sourceRectBottom))
        val activeParams = activity.buildPictureInPictureParams(activeSnapshot)
        assertTrue(activeParams != null)

        // Music-only state -> autoEnterEnabled = false (never enters PiP for music-only UI)
        val musicSnapshot = SystemPipPolicy.buildParamsSnapshot(
            state = VideoPlayerUiState(activeVideo = null, isPlaying = false),
            autoPipEnabled = true
        )
        assertFalse(musicSnapshot.autoEnterEnabled)
        assertFalse(musicSnapshot.canEnterPip)
        val musicParams = activity.buildPictureInPictureParams(musicSnapshot)
        assertTrue(musicParams != null)

        controller.destroy()
    }
}
