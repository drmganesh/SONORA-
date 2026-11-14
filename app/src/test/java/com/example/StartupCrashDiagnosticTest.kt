package com.example

import android.content.Intent
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performSemanticsAction
import com.example.service.SonoraMusicService
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class StartupCrashDiagnosticTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun mainActivityStartsCleanlyAndNavigatesAllCoreScreens() {
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("home_screen").assertIsDisplayed()
        composeRule.onNodeWithTag("greeting_header").assertIsDisplayed()
        composeRule.onNodeWithTag("header_profile_avatar").assertIsDisplayed()

        // 1. Open UserProfileSheet -> Dynamic Colours -> Back
        composeRule.onNodeWithTag("header_profile_avatar").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("user_profile_sheet").assertIsDisplayed()
        composeRule.onNodeWithTag("profile_display_name_input").assertIsDisplayed()

        composeRule.onNodeWithTag("profile_dynamic_colors_row").performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("dynamic_theme_settings_screen").assertIsDisplayed()
        composeRule.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("home_screen").assertIsDisplayed()

        // 2. Open UserProfileSheet -> Player Style Selector -> Back
        composeRule.onNodeWithTag("header_profile_avatar").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("profile_player_style_row").performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("player_style_selector").assertIsDisplayed()
        composeRule.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        composeRule.waitForIdle()

        // 3. Open Settings -> Back
        composeRule.onNodeWithTag("header_settings_button").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("settings_screen").assertIsDisplayed()
        composeRule.onNodeWithTag("settings_back_button").performClick()
        composeRule.waitForIdle()

        // 4. Switch bottom navigation tabs: Search -> Library -> Home
        composeRule.onNodeWithTag("nav_tab_search").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("search_screen").assertIsDisplayed()

        composeRule.onNodeWithTag("nav_tab_library").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("library_screen").assertIsDisplayed()

        composeRule.onNodeWithTag("nav_tab_home").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("home_screen").assertIsDisplayed()

        // 5. Wait for demo/library tracks to populate and open 15-Band EQ & Audio Studio overlays
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodes(hasTestTag("your_mix_section")).fetchSemanticsNodes().isNotEmpty()
        }
        val verticalScrollable = SemanticsMatcher.keyIsDefined(SemanticsProperties.VerticalScrollAxisRange)
        composeRule.onNode(verticalScrollable).performScrollToNode(hasTestTag("home_quick_tools_strip"))
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("quick_tool_15_band_eq").performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("adaptive_equalizer_screen").assertIsDisplayed()
        composeRule.onNodeWithTag("eq_back_button").performClick()
        composeRule.waitForIdle()

        composeRule.onNode(verticalScrollable).performScrollToNode(hasTestTag("home_quick_tools_strip"))
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("home_quick_tools_strip").performScrollToNode(hasTestTag("quick_tool_audio_studio"))
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("quick_tool_audio_studio").performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("audio_effects_studio_screen").assertIsDisplayed()
        composeRule.onNodeWithTag("audio_studio_back_button").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("home_screen").assertIsDisplayed()
    }

    @Test
    fun sonoraMusicServiceStartsAndBindsSafely() {
        val controller = Robolectric.buildService(SonoraMusicService::class.java)
            .create()
            .startCommand(0, 0)
            .bind()
        val service = controller.get()
        assertNotNull(service.audioEngine)
        assertNotNull(service.notificationManager)

        // Dispatch a control intent safely
        val pauseIntent = Intent(service, SonoraMusicService::class.java).apply {
            action = SonoraMusicService.ACTION_PAUSE
        }
        service.onStartCommand(pauseIntent, 0, 1)
        assertNotNull(service.audioEngine.adaptiveEqualizer)
        assertNotNull(service.audioEngine.dspProcessor)

        controller.unbind().destroy()
    }
}

