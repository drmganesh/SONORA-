package ui.branding

import android.app.NotificationManager
import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.core.content.ContextCompat
import androidx.test.core.app.ApplicationProvider
import com.example.R
import com.example.SonoraApplication
import com.example.model.Song
import com.example.service.SonoraNotificationManager
import data.online.MusicDiscoveryBadge
import data.online.SongRadioSession
import domain.branding.AppBranding
import domain.update.SonoraGitHubUpdateConfig
import domain.update.UpdateCheckOutcome
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import ui.onboarding.FirstLaunchProfileOnboardingScreen
import ui.player.NowPlayingTopHeader
import ui.settings.SettingsAboutContent
import ui.settings.readInstalledAppVersion
import ui.splash.SonoraSplashScreen
import ui.splash.SplashLogoPreloader
import ui.theme.EditorialColors
import ui.theme.LocalEditorialColors
import java.util.Calendar

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [26, 36], application = SonoraApplication::class)
class MayurRebrandingVerificationTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `app identity strings, tagline, branded features, and official MAYUR icons resolve cleanly`() {
        val context = ApplicationProvider.getApplicationContext<Context>()

        // Core app name, official tagline & centralized AppBranding
        assertEquals("MAYUR", context.getString(R.string.app_name))
        assertEquals("MAYUR", AppBranding.APP_NAME)
        assertEquals("MAYUR", AppBranding.appName(context))
        assertEquals("Where Sound Becomes Art.", context.getString(R.string.splash_brand_tagline))
        assertEquals("Where Sound Becomes Art.", AppBranding.TAGLINE)
        assertEquals("Where Sound Becomes Art.", AppBranding.tagline(context))
        assertEquals("MAYUR Mix", MusicDiscoveryBadge.SONORA_GENERATED.label)
        assertEquals("MAYUR Radio", SongRadioSession().seedLabel)
        assertEquals("MAYUR Recommendations", context.getString(R.string.sonora_recommend_title))
        assertEquals("MAYUR Recommendations", AppBranding.RECOMMEND_NAME)
        assertEquals("MAYUR Sound Lab", AppBranding.SOUND_LAB_NAME)
        assertEquals("MAYUR is up to date.", UpdateCheckOutcome.UpToDate().reason)

        // Official MAYUR drawables & launcher mipmaps resolve cleanly
        assertNotNull(ContextCompat.getDrawable(context, R.mipmap.ic_launcher))
        assertNotNull(ContextCompat.getDrawable(context, R.mipmap.ic_launcher_round))
        assertNotNull(ContextCompat.getDrawable(context, R.drawable.ic_launcher_foreground))
        assertNotNull(ContextCompat.getDrawable(context, R.drawable.ic_launcher_background))
        assertNotNull(ContextCompat.getDrawable(context, R.drawable.ic_launcher_monochrome))
        assertNotNull(ContextCompat.getDrawable(context, R.drawable.mayur_official_logo))
        assertNotNull(ContextCompat.getDrawable(context, R.drawable.ic_splash_logo))
        assertNotNull(ContextCompat.getDrawable(context, R.drawable.ic_notif_sonora_logo))

        // Foreground & official logo decode as valid non-empty bitmaps
        val fgBitmap = BitmapFactory.decodeResource(context.resources, AppBranding.LAUNCHER_FOREGROUND_RES)
        assertNotNull("MAYUR launcher foreground bitmap should decode cleanly", fgBitmap)
        assertTrue(fgBitmap.width > 0 && fgBitmap.height > 0)

        SplashLogoPreloader.clearCacheForTesting()
        val splashLogo = SplashLogoPreloader.getCachedOrDecode(context)
        assertNotNull("MAYUR splash logo should decode cleanly", splashLogo)
    }

    @Test
    fun `notification channels use MAYUR branding while preserving internal IDs and update compatibility`() {
        val context = ApplicationProvider.getApplicationContext<Context>()

        // Preserve internal IDs so existing installations upgrade seamlessly with zero data loss
        assertEquals("sonora_playback_channel", SonoraApplication.PLAYBACK_CHANNEL_ID)
        assertEquals("sonora_downloads_channel", SonoraApplication.DOWNLOADS_CHANNEL_ID)
        assertEquals("sonora_channel_uploads", SonoraApplication.CHANNEL_UPLOADS_CHANNEL_ID)
        assertEquals("com.example.sonora.ACTION_PLAY", SonoraNotificationManager.ACTION_PLAY)
        assertEquals("SONORA-", SonoraGitHubUpdateConfig.REPOSITORY_NAME)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = context.getSystemService(NotificationManager::class.java)
            val playback = nm.getNotificationChannel(SonoraApplication.PLAYBACK_CHANNEL_ID)
            val downloads = nm.getNotificationChannel(SonoraApplication.DOWNLOADS_CHANNEL_ID)
            val uploads = nm.getNotificationChannel(SonoraApplication.CHANNEL_UPLOADS_CHANNEL_ID)
            assertNotNull(playback)
            assertNotNull(downloads)
            assertNotNull(uploads)
            assertTrue(playback.name.toString().contains("MAYUR"))
            assertTrue(downloads.name.toString().contains("MAYUR"))
            assertTrue(uploads.name.toString().contains("MAYUR"))
            assertFalse(playback.name.toString().contains("SONORA", ignoreCase = true))
            assertFalse(playback.name.toString().contains("GITA", ignoreCase = true))
        }
    }

    @Test
    fun `about and player header display MAYUR branding, tagline in about, and preserve developer credit`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val versionInfo = readInstalledAppVersion(context)
        val currentYear = Calendar.getInstance().get(Calendar.YEAR)
        val colors = EditorialColors.default()

        composeRule.setContent {
            CompositionLocalProvider(LocalEditorialColors provides colors) {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    NowPlayingTopHeader(
                        song = Song(
                            id = 1L,
                            title = "Aasa Kooda",
                            artist = "Sai Abhyankkar",
                            album = "Single",
                            albumId = 1L,
                            durationMs = 215_000L,
                            uri = Uri.EMPTY,
                            dataPath = "",
                            mimeType = "audio/opus",
                            sizeBytes = 3_200_000L,
                            isOnline = true,
                            providerName = "YT Music"
                        ),
                        onClose = {},
                        onOpenQueue = {},
                        onOpenLyrics = {},
                        onOpenSleepTimer = {},
                        onOpenStyleWheel = {},
                        onToggleVisualizer = {},
                        isVisualizerActive = false
                    )
                    SettingsAboutContent(
                        appVersion = versionInfo,
                        colors = colors,
                        reduceAnimations = true,
                        linksContent = {}
                    )
                }
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("now_playing_brand_label")
            .assertIsDisplayed()
            .assertTextContains("MAYUR")
        composeRule.onNodeWithTag("about_app_name")
            .performScrollTo()
            .assertIsDisplayed()
            .assertTextContains("MAYUR")
        composeRule.onNodeWithTag("about_app_tagline")
            .performScrollTo()
            .assertIsDisplayed()
            .assertTextContains("Where Sound Becomes Art.")
        composeRule.onNodeWithTag("about_copyright_text")
            .performScrollTo()
            .assertIsDisplayed()
            .assertTextContains("© $currentYear MAYUR • All rights reserved")
        composeRule.onNodeWithText("🩺 Dr. M. GANESWAR")
            .performScrollTo()
            .assertIsDisplayed()
    }

    @Test
    fun `splash screen reveals MAYUR title and official tagline cleanly`() {
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            SonoraSplashScreen(
                appReduceAnimations = true,
                onAnimationFinished = {}
            )
        }
        composeRule.mainClock.advanceTimeBy(80L)
        composeRule.waitForIdle()
        composeRule.onNodeWithText("MAYUR").assertIsDisplayed()
        composeRule.onNodeWithText("Where Sound Becomes Art.").assertIsDisplayed()
    }

    @Test
    fun `onboarding displays MAYUR title and official tagline cleanly`() {
        val colors = EditorialColors.default()
        composeRule.setContent {
            CompositionLocalProvider(LocalEditorialColors provides colors) {
                FirstLaunchProfileOnboardingScreen(
                    colors = colors,
                    reduceAnimations = true,
                    onCompleteProfile = { _, _ -> Result.failure(IllegalStateException("noop")) }
                )
            }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithText("MAYUR").assertIsDisplayed()
        composeRule.onNodeWithTag("onboarding_brand_tagline")
            .assertIsDisplayed()
            .assertTextContains("Where Sound Becomes Art.")
    }
}
