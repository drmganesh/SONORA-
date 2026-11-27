package ui.settings

import android.content.Context
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import ui.theme.DynamicColorPalette
import ui.theme.EditorialColors
import ui.theme.HomeThemeMode
import ui.theme.LocalEditorialColors
import ui.theme.editorialColorsFor

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class SettingsAboutSectionTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `readInstalledAppVersion resolves actual PackageInfo without throwing`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val info = readInstalledAppVersion(context)
        assertTrue(info.versionName.isNotBlank())
        assertTrue(info.versionCode >= 1L)
        assertTrue(info.displayLabel.contains(info.versionName))
    }

    @Test
    fun `SettingsAboutContent displays SONORA details and exact Developer Profile in light theme`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val versionInfo = readInstalledAppVersion(context)
        val lightColors = editorialColorsFor(
            mode = HomeThemeMode.WARM_CREAM,
            customAccentArgb = null,
            dynamicPalette = DynamicColorPalette.default()
        )

        composeRule.setContent {
            CompositionLocalProvider(LocalEditorialColors provides lightColors) {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    SettingsAboutContent(
                        appVersion = versionInfo,
                        colors = lightColors,
                        reduceAnimations = true,
                        linksContent = {}
                    )
                }
            }
        }

        composeRule.waitForIdle()
        composeRule.onNodeWithTag("about_app_logo").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("about_app_name").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("MAYUR").assertIsDisplayed()
        composeRule.onNodeWithText("Where Sound Becomes Art.").assertIsDisplayed()
        composeRule.onNodeWithText("Premium Music Player").assertIsDisplayed()
        composeRule.onNodeWithText(versionInfo.displayLabel).assertIsDisplayed()

        composeRule.onNodeWithTag("about_developer_card").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("about_developer_portrait").assertIsDisplayed()
        composeRule.onNodeWithText("🩺 Dr. M. GANESWAR").assertIsDisplayed()
        composeRule.onNodeWithText("@drmganeswar").assertIsDisplayed()
        composeRule.onNodeWithText("(BAMS Final Year)").assertIsDisplayed()

        composeRule.onNodeWithTag("about_copyright_text").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun `SettingsAboutContent and fallback avatar render cleanly in dark theme when portrait model is null`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val versionInfo = readInstalledAppVersion(context)
        val darkColors: EditorialColors = editorialColorsFor(
            mode = HomeThemeMode.AMOLED_MIDNIGHT,
            customAccentArgb = null,
            dynamicPalette = DynamicColorPalette.default()
        )

        composeRule.setContent {
            CompositionLocalProvider(LocalEditorialColors provides darkColors) {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    SettingsAboutContent(
                        appVersion = versionInfo,
                        colors = darkColors,
                        reduceAnimations = true,
                        developerPortraitModel = null,
                        linksContent = {}
                    )
                }
            }
        }

        composeRule.waitForIdle()
        composeRule.onNodeWithTag("about_app_logo").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("about_developer_card").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("about_developer_portrait").assertIsDisplayed()
        composeRule.onNodeWithTag("about_developer_avatar_fallback", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithText("🩺 Dr. M. GANESWAR").assertIsDisplayed()
        composeRule.onNodeWithText("@drmganeswar").assertIsDisplayed()
        composeRule.onNodeWithText("(BAMS Final Year)").assertIsDisplayed()
        composeRule.onNodeWithTag("about_copyright_text").performScrollTo().assertIsDisplayed()
    }
}
