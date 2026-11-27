package ui.splash

import android.app.ActivityManager
import android.app.Application
import android.content.Context
import android.os.PowerManager
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config

/**
 * Multi-SDK Robolectric verification (Android 8 / API 26, Android 11 / API 30, and API 36)
 * for SONORA intro animation performance optimizations:
 * - Zero per-frame host recompositions during the 5-phase splash sequence
 * - Pre-decoded and cached downsampled logo bitmap via [SplashLogoPreloader]
 * - Staged Home warm-up ([onPrepareHomeContent]) before transition & completion
 * - Battery Saver & Low-RAM adaptation
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [26, 30, 36])
class SonoraSplashPerformanceOptimizationTest {

    @get:Rule
    val composeRule = createComposeRule()

    private lateinit var app: Application

    @Before
    fun setUp() {
        app = ApplicationProvider.getApplicationContext()
        SplashLogoPreloader.clearCacheForTesting()
    }

    @After
    fun tearDown() {
        val pm = app.getSystemService(Context.POWER_SERVICE) as? PowerManager
        if (pm != null) {
            runCatching { Shadows.shadowOf(pm).setIsPowerSaveMode(false) }
        }
        val am = app.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        if (am != null) {
            runCatching { Shadows.shadowOf(am).setIsLowRamDevice(false) }
        }
        SplashLogoPreloader.clearCacheForTesting()
    }

    @Test
    fun logoPreloader_cachesSingleBitmapAndRespectsDownsampling() {
        assertEquals(1, SplashLogoPreloader.calculateInSampleSize(512, 512, 396, 396))
        assertEquals(4, SplashLogoPreloader.calculateInSampleSize(1024, 1024, 256, 256))

        val first = SplashLogoPreloader.getCachedOrDecode(app)
        assertNotNull("Official SONORA logo bitmap should decode cleanly", first)
        val second = SplashLogoPreloader.getCachedOrDecode(app)
        assertSame("Subsequent logo reads must reuse the cached ImageBitmap instance", first, second)
        assertSame("peekCached() must expose the warm cache hit", first, SplashLogoPreloader.peekCached())
    }

    @Test
    fun splashScreen_animatesWithMinimalRecompositions_andOrdersStagedHomeCallbacks() {
        var hostCompositions = 0
        val events = mutableListOf<String>()
        composeRule.mainClock.autoAdvance = false

        composeRule.setContent {
            SideEffect { hostCompositions += 1 }
            SonoraSplashScreen(
                appReduceAnimations = false,
                onPrepareHomeContent = { events += "prepare_home" },
                onTransitionToHome = { events += "transition_home" },
                onAnimationFinished = { events += "finished" }
            )
        }

        // Step frame-by-frame across the full ~1.85s intro sequence
        repeat(130) {
            composeRule.mainClock.advanceTimeByFrame()
            composeRule.waitForIdle()
        }

        assertEquals(
            "Staged intro callbacks must fire in exact order: prepare_home -> transition_home -> finished",
            listOf("prepare_home", "transition_home", "finished"),
            events
        )
        assertTrue(
            "Splash overlay must remove itself from composition once completed",
            composeRule.onAllNodesWithTag("sonora_splash_overlay").fetchSemanticsNodes().isEmpty()
        )
        assertTrue(
            "Host composable must not recompose per frame during draw/graphicsLayer animations (count=$hostCompositions)",
            hostCompositions <= 3
        )
    }

    @Test
    fun splashScreen_respectsBatterySaverAndLowRamMode() {
        val pm = app.getSystemService(Context.POWER_SERVICE) as? PowerManager
        if (pm != null) {
            Shadows.shadowOf(pm).setIsPowerSaveMode(true)
            assertTrue(isBatterySaverActive(app))
        }

        val am = app.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        if (am != null) {
            Shadows.shadowOf(am).setIsLowRamDevice(true)
            assertTrue(isLowRamDevice(app))
            assertTrue(SplashLogoPreloader.computeTargetSizePx(app) <= 256)
        }

        var prepared = false
        var finished = false
        composeRule.mainClock.autoAdvance = false

        composeRule.setContent {
            SonoraSplashScreen(
                appReduceAnimations = false,
                onPrepareHomeContent = { prepared = true },
                onAnimationFinished = { finished = true }
            )
        }

        composeRule.mainClock.advanceTimeBy(60L)
        composeRule.waitForIdle()
        assertTrue("Battery Saver mode should prepare Home immediately", prepared)
        composeRule.onNodeWithTag("sonora_splash_overlay").assertIsDisplayed()
        composeRule.onNodeWithText("MAYUR").assertIsDisplayed()
        assertTrue(
            "Waveform must be disabled under Battery Saver reduced motion",
            composeRule.onAllNodesWithTag("sonora_splash_waveform").fetchSemanticsNodes().isEmpty()
        )

        composeRule.mainClock.advanceTimeBy(600L)
        composeRule.waitForIdle()
        assertTrue("Battery Saver intro must finish swiftly", finished)
    }
}
