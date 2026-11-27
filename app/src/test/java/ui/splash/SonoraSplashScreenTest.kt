package ui.splash

import android.content.Context
import android.provider.Settings
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.core.content.ContextCompat
import androidx.test.core.app.ApplicationProvider
import com.example.R
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class SonoraSplashScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `official SONORA branding drawables and launcher mipmaps resolve cleanly`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        assertNotNull(ContextCompat.getDrawable(context, R.mipmap.ic_launcher))
        assertNotNull(ContextCompat.getDrawable(context, R.mipmap.ic_launcher_round))
        assertNotNull(ContextCompat.getDrawable(context, R.drawable.mayur_official_logo))
        assertNotNull(ContextCompat.getDrawable(context, R.drawable.sonora_official_logo))
        assertNotNull(ContextCompat.getDrawable(context, R.drawable.ic_splash_logo))
        assertNotNull(ContextCompat.getDrawable(context, R.drawable.ic_notif_sonora_logo))
        assertNotNull(ContextCompat.getDrawable(context, R.drawable.ic_launcher_monochrome))
    }

    @Test
    fun `SonoraSplashScreen plays full 5-phase sequence and transitions cleanly to Home`() {
        var finished = false
        composeRule.mainClock.autoAdvance = false

        composeRule.setContent {
            SonoraSplashScreen(
                appReduceAnimations = false,
                onAnimationFinished = { finished = true }
            )
        }

        // Phase 1 & 2: Dark intro -> Logo reveal
        composeRule.mainClock.advanceTimeBy(250L)
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("sonora_splash_overlay").assertIsDisplayed()
        composeRule.onNodeWithTag("sonora_splash_logo").assertIsDisplayed()
        assertFalse(finished)

        // Phase 3: Waveform active
        composeRule.mainClock.advanceTimeBy(350L)
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("sonora_splash_waveform", useUnmergedTree = true).assertIsDisplayed()

        // Phase 4: Reach brand reveal (780ms) and advance through AnimatedVisibility fadeIn (360ms)
        composeRule.mainClock.advanceTimeBy(250L)
        composeRule.waitForIdle()
        composeRule.mainClock.advanceTimeBy(450L)
        composeRule.waitForIdle()
        val expectedTagline = ApplicationProvider.getApplicationContext<Context>()
            .getString(R.string.splash_brand_tagline)
        composeRule.onNodeWithText("MAYUR").assertIsDisplayed()
        composeRule.onNodeWithText(expectedTagline).assertIsDisplayed()

        // Phase 5: Transition & Completion (~1.85s total)
        composeRule.mainClock.advanceTimeBy(1200L)
        composeRule.waitForIdle()
        assertTrue(finished)
    }

    @Test
    fun `SonoraSplashScreen respects Reduce Motion accessibility and completes quickly`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        Settings.Global.putFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 0f)
        assertTrue(isSystemReduceMotionActive(context))

        var finished = false
        composeRule.mainClock.autoAdvance = false

        composeRule.setContent {
            SonoraSplashScreen(
                appReduceAnimations = true,
                onAnimationFinished = { finished = true }
            )
        }

        composeRule.mainClock.advanceTimeBy(60L)
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("sonora_splash_overlay").assertIsDisplayed()
        composeRule.onNodeWithText("MAYUR").assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.splash_brand_tagline)).assertIsDisplayed()
        assertTrue(composeRule.onAllNodesWithTag("sonora_splash_waveform").fetchSemanticsNodes().isEmpty())

        composeRule.mainClock.advanceTimeBy(600L)
        composeRule.waitForIdle()
        assertTrue(finished)

        Settings.Global.putFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
    }
}
