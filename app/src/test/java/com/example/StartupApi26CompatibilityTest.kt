package com.example

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.core.app.ApplicationProvider
import com.example.model.UserProfile
import com.example.service.SonoraMusicService
import data.preferences.FolderFilterPreferences
import data.preferences.ThemePreferences
import data.preferences.UserProfilePreferences
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.FixMethodOrder
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.MethodSorters
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import ui.components.UserProfileAvatar
import ui.theme.LocalEditorialColors
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [26])
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class StartupApi26CompatibilityTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun a_corruptedDataStoreFilesRecoverSafelyWithDefaults() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val datastoreDir = File(context.filesDir, "datastore").apply { mkdirs() }
        File(datastoreDir, "sonora_user_profile.preferences_pb").writeBytes(
            byteArrayOf(0x00, 0xFF.toByte(), 0x7A, 0x13, 0x42, 0x99.toByte())
        )
        File(datastoreDir, "sonora_theme_preferences.preferences_pb").writeBytes(
            byteArrayOf(0x11, 0x22, 0x33, 0xFF.toByte(), 0xEE.toByte())
        )
        File(datastoreDir, "sonora_folder_filter_preferences.preferences_pb").writeBytes(
            byteArrayOf(0x55, 0x66, 0x77, 0x88.toByte())
        )

        val profilePrefs = UserProfilePreferences(context)
        val recoveredProfile = profilePrefs.userProfileFlow.first()
        assertEquals(UserProfile.DEFAULT_DISPLAY_NAME, recoveredProfile.displayName)

        val themePrefs = ThemePreferences(context)
        assertTrue(themePrefs.dynamicColorsEnabledFlow.first())

        val folderPrefs = FolderFilterPreferences(context)
        val recoveredFolders = folderPrefs.settingsFlow.first()
        assertTrue(recoveredFolders.whitelistedFolders.isNotEmpty())
    }

    @Test
    fun b_userProfileAvatarHandlesMissingAndMalformedUrisGracefully() {
        composeRule.setContent {
            val colors = LocalEditorialColors.current
            UserProfileAvatar(
                userProfile = UserProfile(
                    displayName = "Listener",
                    avatarUri = "file:///nonexistent_dir/missing_avatar.jpg"
                ),
                colors = colors
            )
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("user_profile_avatar").assertIsDisplayed()
    }

    @Test
    fun c_mainActivityAndMusicServiceLaunchCleanlyOnApi26() {
        val activityController = Robolectric.buildActivity(MainActivity::class.java)
            .create()
            .start()
            .resume()
            .visible()
        val activity = activityController.get()
        assertNotNull(activity)

        val serviceController = Robolectric.buildService(SonoraMusicService::class.java)
            .create()
            .startCommand(0, 0)
            .bind()
        val service = serviceController.get()
        assertNotNull(service.audioEngine)
        assertNotNull(service.notificationManager)

        serviceController.unbind().destroy()
        activityController.pause().stop().destroy()
    }
}
