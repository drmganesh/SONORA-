package ui.motion

import android.app.Application
import android.net.Uri
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import androidx.compose.ui.test.swipeUp
import androidx.test.core.app.ApplicationProvider
import com.example.model.MiniPlayerStyle
import com.example.model.PlaybackInfo
import com.example.model.Song
import com.example.ui.screens.SettingsScreen
import com.example.viewmodel.SonoraViewModel
import data.preferences.HomeModePreferences
import data.preferences.MotionPreferences
import domain.home.HomeMusicMode
import domain.motion.GestureSensitivityLevel
import domain.motion.GlobalMotionConfig
import domain.motion.MotionIntensity
import domain.motion.ScreenTransitionStyle
import domain.motion.resolveEffectiveMotionState
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
import ui.components.FloatingMiniPlayer
import ui.home.HomeScreen
import ui.home.animations.HomeAnimationLevel
import ui.settings.MotionGesturesSettingsScreen
import ui.theme.DynamicColorPalette
import ui.theme.EditorialColors
import ui.theme.HomeThemeMode
import ui.theme.LocalEditorialColors
import ui.theme.editorialColorsFor

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class MotionAndSwipeSystemTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun testSong(id: Long = 101L, title: String = "Midnight Resonance"): Song = Song(
        id = id,
        title = title,
        artist = "Prateek Kuhad",
        album = "Silhouettes",
        albumId = 202L,
        durationMs = 215_000L,
        uri = Uri.parse("content://media/external/audio/media/$id"),
        dataPath = "/storage/emulated/0/Music/track_$id.flac",
        mimeType = "audio/flac",
        sizeBytes = 18_000_000L
    )

    @Before
    @After
    fun resetPreferences() = runBlocking {
        val app = ApplicationProvider.getApplicationContext<Application>()
        MotionPreferences(app).resetToDefaults()
        HomeModePreferences(app).setHomeMusicMode(HomeMusicMode.OFFLINE)
        com.example.data.SonoraPreferences(app).reduceAnimations = false
    }

    @Test
    fun `resolveEffectiveMotionState honors Off intensity and system reduce-motion scale`() {
        val balanced = resolveEffectiveMotionState(GlobalMotionConfig.DEFAULT, systemAnimatorScale = 1f)
        assertTrue(balanced.animationsEnabled)
        assertTrue(balanced.artworkTransitionsEnabled)
        assertTrue(balanced.parallaxAndScaleEnabled)
        assertEquals(280, balanced.durationMillis(280))

        val systemDisabled = resolveEffectiveMotionState(GlobalMotionConfig.DEFAULT, systemAnimatorScale = 0f)
        assertFalse(systemDisabled.animationsEnabled)
        assertFalse(systemDisabled.artworkTransitionsEnabled)
        assertFalse(systemDisabled.parallaxAndScaleEnabled)
        assertEquals(0, systemDisabled.durationMillis(280))
        assertEquals(0f, systemDisabled.distanceScale, 0.001f)

        val offConfig = resolveEffectiveMotionState(
            GlobalMotionConfig(intensity = MotionIntensity.OFF, swipeNavigationEnabled = true),
            systemAnimatorScale = 1f
        )
        assertFalse(offConfig.animationsEnabled)
        assertTrue(offConfig.swipeNavigationEnabled)
        assertEquals(0, offConfig.durationMillis(300))

        val expressive = resolveEffectiveMotionState(
            GlobalMotionConfig(
                intensity = MotionIntensity.EXPRESSIVE,
                gestureSensitivity = GestureSensitivityLevel.HIGH
            ),
            systemAnimatorScale = 1f
        )
        assertTrue(expressive.animationsEnabled)
        assertEquals(1.25f, expressive.distanceScale, 0.001f)
        assertEquals(44, expressive.swipeThresholdDp)
        assertTrue(expressive.shouldTriggerSwipe(dragPx = 50f, velocityPxPerSec = 0f, thresholdPx = 44f, velocityPxThreshold = 240f))
        assertTrue(expressive.shouldTriggerSwipe(dragPx = 10f, velocityPxPerSec = 300f, thresholdPx = 44f, velocityPxThreshold = 240f))
    }

    @Test
    fun `MotionPreferences persists and resets GlobalMotionConfig cleanly`() = runBlocking {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val prefs = MotionPreferences(app)

        val custom = GlobalMotionConfig(
            intensity = MotionIntensity.EXPRESSIVE,
            transitionStyle = ScreenTransitionStyle.DYNAMIC,
            gestureSensitivity = GestureSensitivityLevel.HIGH,
            swipeNavigationEnabled = false,
            artworkTransitionsEnabled = false,
            hapticFeedbackEnabled = false,
            parallaxEffectsEnabled = false,
            animatedAlbumColorsEnabled = false
        )
        prefs.saveConfig(custom)
        assertEquals(custom, prefs.getInitialConfig())
        assertEquals(custom, prefs.motionConfigFlow.first())

        val reset = prefs.resetToDefaults()
        assertEquals(GlobalMotionConfig.DEFAULT, reset)
        assertEquals(GlobalMotionConfig.DEFAULT, prefs.getInitialConfig())
    }

    @Test
    fun `SonoraViewModel syncs motion intensity, gesture sensitivity, and track transition direction`() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val vm = SonoraViewModel(app)

        vm.setMotionIntensity(MotionIntensity.OFF)
        assertTrue(vm.reduceAnimations.value)
        assertEquals(HomeAnimationLevel.OFF, vm.homeAnimationLevel.value)

        vm.setMotionIntensity(MotionIntensity.EXPRESSIVE)
        assertFalse(vm.reduceAnimations.value)
        assertEquals(HomeAnimationLevel.EXPRESSIVE, vm.homeAnimationLevel.value)

        vm.setScreenTransitionStyle(ScreenTransitionStyle.DYNAMIC)
        assertEquals(ScreenTransitionStyle.DYNAMIC, vm.motionConfig.value.transitionStyle)

        vm.setGestureSensitivityLevel(GestureSensitivityLevel.HIGH)
        assertEquals(44, vm.playerGestureConfig.value.swipeThresholdDp)
        assertEquals(1.38f, vm.playerGestureConfig.value.swipeSensitivity, 0.01f)

        val queue = listOf(testSong(1L, "Track 1"), testSong(2L, "Track 2"))
        vm.playCollection(queue, shuffle = false)
        vm.playNext()
        assertEquals(1, vm.trackTransitionDirection.value)
        vm.playPrevious()
        assertEquals(-1, vm.trackTransitionDirection.value)

        vm.resetMotionConfigToDefaults()
        assertEquals(GlobalMotionConfig.DEFAULT, vm.motionConfig.value)
    }

    @Test
    fun `MotionGesturesSettingsScreen updates ViewModel config and resets on click`() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val vm = SonoraViewModel(app)
        val colors = editorialColorsFor(
            mode = HomeThemeMode.WARM_CREAM,
            customAccentArgb = null,
            dynamicPalette = DynamicColorPalette.default()
        )

        composeRule.setContent {
            CompositionLocalProvider(LocalEditorialColors provides colors) {
                MotionGesturesSettingsScreen(
                    viewModel = vm,
                    onBack = {}
                )
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("motion_gestures_settings_screen").assertIsDisplayed()
        composeRule.onNodeWithTag("motion_preview_card").assertIsDisplayed().performClick()
        composeRule.onNodeWithTag("motion_settings_lazy_column")
            .performScrollToNode(hasTestTag("motion_intensity_expressive"))
        composeRule.onNodeWithTag("motion_intensity_expressive").performScrollTo().performClick()

        composeRule.onNodeWithTag("motion_settings_lazy_column")
            .performScrollToNode(hasTestTag("motion_transition_dynamic"))
        composeRule.onNodeWithTag("motion_transition_dynamic").performScrollTo().performClick()

        composeRule.onNodeWithTag("motion_settings_lazy_column")
            .performScrollToNode(hasTestTag("motion_sensitivity_high"))
        composeRule.onNodeWithTag("motion_sensitivity_high").performScrollTo().performClick()
        composeRule.waitForIdle()

        assertEquals(MotionIntensity.EXPRESSIVE, vm.motionConfig.value.intensity)
        assertEquals(ScreenTransitionStyle.DYNAMIC, vm.motionConfig.value.transitionStyle)
        assertEquals(GestureSensitivityLevel.HIGH, vm.motionConfig.value.gestureSensitivity)

        composeRule.onNodeWithTag("motion_settings_lazy_column")
            .performScrollToNode(hasTestTag("motion_reset_button"))
        composeRule.onNodeWithTag("motion_reset_button").performScrollTo().performClick()
        composeRule.waitForIdle()
        assertEquals(GlobalMotionConfig.DEFAULT, vm.motionConfig.value)
    }

    @Test
    fun `FloatingMiniPlayer ignores swipe gestures while tap and playback buttons remain functional`() {
        val colors = editorialColorsFor(
            mode = HomeThemeMode.WARM_CREAM,
            customAccentArgb = null,
            dynamicPalette = DynamicColorPalette.default()
        )
        val song = testSong()
        var playPauseCount = 0
        var nextCount = 0
        var prevCount = 0
        var clickCount = 0

        composeRule.setContent {
            CompositionLocalProvider(
                LocalEditorialColors provides colors,
                LocalEffectiveMotionState provides resolveEffectiveMotionState(GlobalMotionConfig.DEFAULT)
            ) {
                FloatingMiniPlayer(
                    song = song,
                    playbackInfo = PlaybackInfo(isPlaying = true, durationMs = 200_000L, currentPositionMs = 45_000L),
                    colors = colors,
                    onTogglePlayPause = { playPauseCount++ },
                    onPlayNext = { nextCount++ },
                    onPlayPrevious = { prevCount++ },
                    onClick = { clickCount++ },
                    miniPlayerStyle = MiniPlayerStyle.COMPACT_BAR,
                    trackDirection = 1
                )
            }
        }
        composeRule.waitForIdle()

        // Horizontal and vertical swipes on mini player must NOT trigger skip or expand
        composeRule.onNodeWithTag("mini_player").performTouchInput { swipeLeft() }
        composeRule.onNodeWithTag("mini_player").performTouchInput { swipeRight() }
        composeRule.onNodeWithTag("mini_player").performTouchInput { swipeUp() }
        composeRule.waitForIdle()
        assertEquals(0, nextCount)
        assertEquals(0, prevCount)
        assertEquals(0, clickCount)

        // Normal tap-to-open and explicit Play/Pause + Next buttons must work
        composeRule.onNodeWithTag("mini_play_pause_button").performClick()
        composeRule.onNodeWithTag("mini_skip_next_button").performClick()
        composeRule.onNodeWithTag("mini_player").performClick()
        composeRule.waitForIdle()
        assertEquals(1, playPauseCount)
        assertEquals(1, nextCount)
        assertEquals(1, clickCount)
    }

    @Test
    fun `HomeScreen horizontal swipe switches between Offline and Online Home modes`() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val vm = SonoraViewModel(app)
        vm.setReduceAnimations(true)
        val colors = editorialColorsFor(
            mode = HomeThemeMode.WARM_CREAM,
            customAccentArgb = null,
            dynamicPalette = DynamicColorPalette.default()
        )

        composeRule.setContent {
            CompositionLocalProvider(
                LocalEditorialColors provides colors,
                LocalEffectiveMotionState provides resolveEffectiveMotionState(GlobalMotionConfig.DEFAULT)
            ) {
                HomeScreen(
                    viewModel = vm,
                    colors = colors,
                    onOpenSettings = {},
                    onNavigateToSearch = {},
                    onNavigateToLibrary = {},
                    onOpenNowPlaying = {}
                )
            }
        }
        composeRule.waitForIdle()

        assertEquals(HomeMusicMode.OFFLINE, vm.homeMusicMode.value)
        composeRule.onNodeWithTag("home_offline_content").assertIsDisplayed()

        composeRule.onNodeWithTag("home_offline_content").performTouchInput {
            swipe(
                start = Offset(width * 0.85f, height * 0.20f),
                end = Offset(width * 0.15f, height * 0.20f),
                durationMillis = 180L
            )
        }
        composeRule.waitForIdle()
        assertEquals(HomeMusicMode.ONLINE, vm.homeMusicMode.value)

        composeRule.onNodeWithTag("home_online_content").assertIsDisplayed()
        composeRule.onNodeWithTag("home_online_content").performTouchInput {
            swipe(
                start = Offset(width * 0.15f, height * 0.20f),
                end = Offset(width * 0.85f, height * 0.20f),
                durationMillis = 180L
            )
        }
        composeRule.waitForIdle()
        assertEquals(HomeMusicMode.OFFLINE, vm.homeMusicMode.value)
    }

    @Test
    fun `SettingsScreen opens Motion & Gestures smoothly with AnimatedVisibility overlay`() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val vm = SonoraViewModel(app)
        val colors = editorialColorsFor(
            mode = HomeThemeMode.WARM_CREAM,
            customAccentArgb = null,
            dynamicPalette = DynamicColorPalette.default()
        )

        composeRule.setContent {
            CompositionLocalProvider(
                LocalEditorialColors provides colors,
                LocalEffectiveMotionState provides resolveEffectiveMotionState(GlobalMotionConfig.DEFAULT)
            ) {
                SettingsScreen(viewModel = vm)
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("settings_screen").assertIsDisplayed()
        composeRule.onNodeWithTag("settings_lazy_column")
            .performScrollToNode(hasTestTag("settings_motion_gestures_row"))
        composeRule.onNodeWithTag("settings_motion_gestures_row").performScrollTo().performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("motion_gestures_settings_screen").assertIsDisplayed()
    }
}
