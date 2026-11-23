package com.example

import android.app.Application
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import com.example.data.SonoraPreferences
import com.example.model.MiniPlayerStyle
import com.example.model.PlayerStyle
import data.preferences.AmbientEffectsPreferences
import data.preferences.SnowfallSettings
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class SonoraMusicPlayerConsolidationTest {

    @get:Rule
    val composeRule = createEmptyComposeRule()

    @Test
    fun settingsHasSingleMusicPlayerSectionAndConsolidatedHubWorksEndToEnd() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val prefs = SonoraPreferences(app)
        // Disable continuous ambient snowfall animation so Compose idling completes deterministically
        runBlocking {
            AmbientEffectsPreferences(app).saveSettings(SnowfallSettings.DEFAULT.copy(enabled = false))
        }
        // Pre-populate legacy saved styles before launch to verify zero reset on startup
        prefs.playerStyle = PlayerStyle.BENTO
        prefs.landscapePlayerStyle = PlayerStyle.ORBIT
        prefs.miniPlayerStyle = MiniPlayerStyle.EXPANDABLE_CARD

        ActivityScenario.launch(MainActivity::class.java).use {
            composeRule.waitForIdle()
            // Verify previously saved legacy selections remained intact on launch
            assertEquals(PlayerStyle.BENTO, prefs.playerStyle)
            assertEquals(PlayerStyle.ORBIT, prefs.landscapePlayerStyle)
            assertEquals(MiniPlayerStyle.EXPANDABLE_CARD, prefs.miniPlayerStyle)

            // Open Settings from Home
            composeRule.onNodeWithTag("header_settings_button").performClick()
            composeRule.waitForIdle()
            composeRule.onNodeWithTag("settings_screen").assertIsDisplayed()

            // Scroll to Music Player row under Appearance (inside the tall Appearance card)
            composeRule.onNodeWithTag("settings_lazy_column")
                .performScrollToNode(hasTestTag("settings_music_player_row"))
            composeRule.onNodeWithTag("settings_music_player_row").performScrollTo()
            composeRule.waitForIdle()

            // Verify single Music Player section exists and duplicate entries are removed
            composeRule.onNodeWithTag("settings_music_player_row").assertIsDisplayed()
            composeRule.onNodeWithText("Player Style", substring = false).assertDoesNotExist()
            composeRule.onNodeWithText("Player Appearance & Layout", substring = false).assertDoesNotExist()
            composeRule.onNodeWithText("Mini-Player Style", substring = false).assertDoesNotExist()

            // Tap Music Player -> opens consolidated Music Player hub
            composeRule.onNodeWithTag("settings_music_player_row").performClick()
            composeRule.waitForIdle()
            composeRule.onNodeWithTag("player_appearance_hub_screen").assertIsDisplayed()

            // Verify all 6 customization sections are accessible via horizontal scroll
            listOf(
                "hub_section_tab_now_playing",
                "hub_section_tab_mini_player",
                "hub_section_tab_artwork",
                "hub_section_tab_animation",
                "hub_section_tab_visualizer",
                "hub_section_tab_controls"
            ).forEach { tabTag ->
                composeRule.onNodeWithTag(tabTag).performScrollTo().assertIsDisplayed()
            }

            // Return to Now Playing tab and verify Landscape Player Style selector & Live Preview + Apply
            composeRule.onNodeWithTag("hub_section_tab_now_playing").performScrollTo().performClick()
            composeRule.waitForIdle()
            composeRule.onNodeWithTag("appearance_hub_lazy_column")
                .performScrollToNode(hasTestTag("hub_landscape_style_selector"))
            composeRule.onNodeWithTag("hub_landscape_style_selector").assertIsDisplayed()

            composeRule.onNodeWithTag("appearance_hub_lazy_column")
                .performScrollToNode(hasTestTag("hub_preview_active_style_button"))
            composeRule.onNodeWithTag("hub_preview_active_style_button").performClick()
            composeRule.waitForIdle()
            composeRule.onNodeWithTag("live_style_preview_overlay").assertIsDisplayed()
            composeRule.onNodeWithTag("live_preview_apply_landscape_button").performClick()
            composeRule.waitForIdle()
            assertEquals(PlayerStyle.BENTO, prefs.landscapePlayerStyle)
            composeRule.onNodeWithTag("live_preview_apply_button").performClick()
            composeRule.waitForIdle()
            composeRule.onNodeWithTag("live_style_preview_overlay").assertDoesNotExist()

            // Open Mini Player tab and apply a mini player style
            composeRule.onNodeWithTag("hub_section_tab_mini_player").performScrollTo().performClick()
            composeRule.waitForIdle()
            composeRule.onNodeWithTag("appearance_hub_lazy_column")
                .performScrollToNode(hasTestTag("hub_mini_style_card_vinyl_mini"))
            composeRule.onNodeWithTag("hub_mini_style_card_vinyl_mini").performClick()
            composeRule.waitForIdle()
            assertEquals(MiniPlayerStyle.VINYL_MINI, prefs.miniPlayerStyle)
            // Portrait selection stayed preserved
            assertEquals(PlayerStyle.BENTO, prefs.playerStyle)

            // Verify back navigation closes hub and returns cleanly to Settings
            composeRule.onNodeWithTag("appearance_hub_back_button").performClick()
            composeRule.waitForIdle()
            composeRule.onNodeWithTag("settings_screen").assertIsDisplayed()
            assertEquals(40, PlayerStyle.entries.size)
            assertEquals(20, MiniPlayerStyle.entries.size)
        }
    }
}
