package com.example

import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.test.core.app.ApplicationProvider
import com.example.data.SonoraPreferences
import com.example.model.UserProfile
import data.preferences.ExperiencePreferences
import data.preferences.ProfileNameValidationResult
import data.preferences.ProfileOnboardingState
import data.preferences.UserProfilePreferences
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import ui.onboarding.FirstLaunchProfileOnboardingScreen
import ui.theme.EditorialColors
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class SonoraFirstLaunchProfileAndSettingsTest {

    @get:Rule
    val composeRule = createComposeRule()

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
    }

    // =========================================================================
    // 1. HARMONIC TRACK MATCHING: OFF BY DEFAULT & MIGRATION SAFETY
    // =========================================================================

    @Test
    fun harmonicTrackMatching_defaultsToOffOnFreshInstall() = runBlocking {
        val sonoraPrefs = SonoraPreferences(context)
        val experiencePrefs = ExperiencePreferences(context)

        assertNull(
            "Fresh install should not have an explicit legacy preference",
            sonoraPrefs.getExplicitMatchNextSongPreference()
        )
        assertFalse(
            "SonoraPreferences.matchNextSongEnabled must default to OFF on fresh install",
            sonoraPrefs.matchNextSongEnabled
        )
        assertFalse(
            "DataStore harmonicTrackMatching must default to OFF on fresh install",
            experiencePrefs.getHarmonicTrackMatchingEnabled(sonoraPrefs.getExplicitMatchNextSongPreference())
        )
    }

    @Test
    fun harmonicTrackMatching_preservesExistingUserExplicitTrueAndFalse() = runBlocking {
        val sonoraPrefs = SonoraPreferences(context)
        val experiencePrefs = ExperiencePreferences(context)

        // Existing user explicitly enabled it in legacy SharedPreferences before update
        sonoraPrefs.matchNextSongEnabled = true
        assertEquals(true, sonoraPrefs.getExplicitMatchNextSongPreference())
        assertTrue(
            "Existing user who explicitly enabled Harmonic Track Matching must keep it ON",
            experiencePrefs.getHarmonicTrackMatchingEnabled(sonoraPrefs.getExplicitMatchNextSongPreference())
        )

        // Existing user toggles OFF via DataStore
        experiencePrefs.setHarmonicTrackMatchingEnabled(false)
        assertFalse(
            "DataStore explicit false must be preserved",
            experiencePrefs.getHarmonicTrackMatchingEnabled(legacyExplicitValue = true)
        )

        // User toggles ON via DataStore
        experiencePrefs.setHarmonicTrackMatchingEnabled(true)
        assertTrue(
            "DataStore explicit true must be preserved",
            experiencePrefs.getHarmonicTrackMatchingEnabled(legacyExplicitValue = false)
        )
    }

    // =========================================================================
    // 2. FIRST-LAUNCH ONBOARDING & LOCAL PROFILE CREATION
    // =========================================================================

    @Test
    fun welcomeMessageAndCtaStrings_matchExactSpecification() {
        val expectedWelcome =
            "Hey there! 💛\n\nWelcome to SONORA!\n\nYour music, your mood, your little world. 🎧\n\nLet's make this experience yours. What should we call you?"
        assertEquals(expectedWelcome, context.getString(R.string.onboarding_welcome_message))
        assertEquals("Let's Start Listening 🎵", context.getString(R.string.onboarding_start_listening_cta))
    }

    @Test
    fun freshInstall_opensOnboarding_andProcessDeathBeforeCompletionResumesOnboarding() = runBlocking {
        val profilePrefs = UserProfilePreferences(
            context = context,
            hasPreExistingInstallProvider = { false }
        )

        val initialState = profilePrefs.resolveInitialOnboardingState()
        assertEquals(
            "Fresh installation must require first-launch profile setup",
            ProfileOnboardingState.REQUIRED,
            initialState
        )

        // Simulate process death & relaunch BEFORE user completes onboarding (even if DB now exists)
        val relaunchedPrefs = UserProfilePreferences(
            context = context,
            hasPreExistingInstallProvider = { true }
        )
        assertEquals(
            "Incomplete onboarding must remain REQUIRED after process death",
            ProfileOnboardingState.REQUIRED,
            relaunchedPrefs.resolveInitialOnboardingState()
        )
    }

    @Test
    fun existingInstallation_bypassesOnboarding_andPreservesProfileAndSettings() = runBlocking {
        // Simulate an existing user with settings in SharedPreferences
        val sonoraPrefs = SonoraPreferences(context)
        sonoraPrefs.automixEnabled = false
        assertTrue(SonoraApplication.detectExistingInstallationData(context))

        val profilePrefs = UserProfilePreferences(
            context = context,
            hasPreExistingInstallProvider = { SonoraApplication.detectExistingInstallationData(context) }
        )
        val resolved = profilePrefs.resolveInitialOnboardingState()
        assertEquals(
            "Existing installations must NOT be forced through onboarding after updating",
            ProfileOnboardingState.COMPLETED,
            resolved
        )
        assertFalse("Existing user settings must remain intact", sonoraPrefs.automixEnabled)
    }

    @Test
    fun profileNameValidation_trimsSpaces_rejectsEmptyAndWhitespace_andEnforcesLimit() {
        assertEquals(ProfileNameValidationResult.Empty, UserProfilePreferences.validateProfileName(""))
        assertEquals(ProfileNameValidationResult.Empty, UserProfilePreferences.validateProfileName("     "))
        assertEquals(ProfileNameValidationResult.Empty, UserProfilePreferences.validateProfileName("\t\n  "))

        val valid = UserProfilePreferences.validateProfileName("   Aarav   ")
        assertEquals(ProfileNameValidationResult.Valid("Aarav"), valid)

        val tooLong = UserProfilePreferences.validateProfileName("A".repeat(33))
        assertEquals(
            ProfileNameValidationResult.TooLong(UserProfilePreferences.MAX_DISPLAY_NAME_LENGTH),
            tooLong
        )
    }

    @Test
    fun completeOnboarding_withSkippedPhoto_savesLocally_andRelaunchGoesDirectToHome() = runBlocking {
        val profilePrefs = UserProfilePreferences(
            context = context,
            hasPreExistingInstallProvider = { false }
        )
        assertEquals(ProfileOnboardingState.REQUIRED, profilePrefs.resolveInitialOnboardingState())

        // Blank name rejected
        val emptyResult = profilePrefs.completeFirstLaunchProfile("   ", null)
        assertTrue("Blank profile name must fail validation", emptyResult.isFailure)
        assertEquals(ProfileOnboardingState.REQUIRED, profilePrefs.onboardingState.value)

        // Valid name with skipped photo succeeds and uses default SONORA avatar (avatarUri == null)
        val success = profilePrefs.completeFirstLaunchProfile("  Ganeswar  ", null)
        assertTrue(success.isSuccess)
        val saved = profilePrefs.userProfileFlow.first()
        assertEquals("Ganeswar", saved.displayName)
        assertNull("Skipping photo should leave avatarUri null for default avatar", saved.avatarUri)
        assertFalse(saved.hasCustomPhoto)

        // Relaunch goes directly to Home (COMPLETED)
        val relaunched = UserProfilePreferences(
            context = context,
            hasPreExistingInstallProvider = { false }
        )
        assertEquals(ProfileOnboardingState.COMPLETED, relaunched.resolveInitialOnboardingState())
        assertEquals("Ganeswar", relaunched.userProfileFlow.first().displayName)
    }

    @Test
    fun profilePhotoSelection_editName_changeAndRemovePhoto_workLocally() = runBlocking {
        val profilePrefs = UserProfilePreferences(
            context = context,
            hasPreExistingInstallProvider = { false }
        )

        // Create a real local sample image file to simulate picked photo
        val pickedFile = File(context.cacheDir, "picked_avatar_sample.jpg").apply {
            writeBytes(byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte(), 1, 2, 3, 4))
        }
        val pickedUri = Uri.fromFile(pickedFile)

        val completed = profilePrefs.completeFirstLaunchProfile("Priya", pickedUri)
        assertTrue(completed.isSuccess)
        val withPhoto = profilePrefs.userProfileFlow.first()
        assertEquals("Priya", withPhoto.displayName)
        assertNotNull("Picked photo URI must be persisted locally", withPhoto.avatarUri)
        assertTrue(withPhoto.hasCustomPhoto)

        // Edit profile name in Settings -> Profile
        val renameResult = profilePrefs.updateDisplayName("  Priya Sharma  ")
        assertTrue(renameResult.isSuccess)
        assertEquals("Priya Sharma", profilePrefs.userProfileFlow.first().displayName)

        // Remove photo / restore default avatar
        profilePrefs.removeAvatar()
        val afterRemove = profilePrefs.userProfileFlow.first()
        assertNull("Removing photo must restore default avatar", afterRemove.avatarUri)
        assertFalse(afterRemove.hasCustomPhoto)
        assertEquals("Priya Sharma", afterRemove.displayName)
    }

    @Test
    fun onboardingUi_displaysWelcomeMessage_rejectsWhitespaceName_andSubmitsValidProfile() {
        var submittedName: String? = null
        var submittedPhoto: Uri? = Uri.parse("content://sentinel")

        composeRule.setContent {
            FirstLaunchProfileOnboardingScreen(
                colors = EditorialColors.default(),
                reduceAnimations = true,
                onCompleteProfile = { name, uri ->
                    submittedName = name
                    submittedPhoto = uri
                    Result.success(UserProfile(displayName = name, avatarUri = uri?.toString()))
                }
            )
        }

        composeRule.onNodeWithTag("onboarding_welcome_message")
            .performScrollTo()
            .assertIsDisplayed()
            .assertTextContains("Welcome to SONORA!", substring = true)

        // Try submitting with whitespace-only name
        composeRule.onNodeWithTag("onboarding_name_input")
            .performScrollTo()
            .performTextInput("   ")
        composeRule.onNodeWithTag("onboarding_start_listening_button")
            .performScrollTo()
            .performClick()
        composeRule.waitForIdle()
        assertNull("Whitespace-only name must not complete onboarding", submittedName)

        // Skip photo and enter a real name
        composeRule.onNodeWithTag("onboarding_skip_photo_button")
            .performScrollTo()
            .performClick()
        composeRule.onNodeWithTag("onboarding_name_input")
            .performScrollTo()
            .performTextReplacement("  Meera  ")
        composeRule.onNodeWithTag("onboarding_start_listening_button")
            .performScrollTo()
            .performClick()

        composeRule.waitForIdle()
        assertEquals("Meera", submittedName)
        assertNull("Skipped photo must submit null URI", submittedPhoto)
    }

    @Test
    fun profileSettingsUi_editsName_andRemovesPhotoToRestoreDefaultAvatar() {
        var savedName: String? = null
        var removedPhotoCount = 0

        composeRule.setContent {
            ui.settings.ProfileSettingsScreen(
                userProfile = UserProfile(
                    displayName = "Original Name",
                    avatarUri = "file:///tmp/custom_avatar.jpg"
                ),
                reduceAnimations = true,
                onUpdateProfileName = { newName ->
                    savedName = newName
                    Result.success(newName)
                },
                onPickProfilePhoto = {},
                onRemoveProfilePhoto = { removedPhotoCount += 1 },
                onBack = {},
                colors = EditorialColors.default()
            )
        }

        // Remove photo / restore default avatar
        composeRule.onNodeWithTag("profile_settings_remove_photo_button")
            .performScrollTo()
            .performClick()
        composeRule.waitForIdle()
        assertEquals(1, removedPhotoCount)

        // Edit profile name with surrounding whitespace -> trimmed & saved
        composeRule.onNodeWithTag("profile_settings_name_input")
            .performScrollTo()
            .performTextReplacement("  Rohan Das  ")
        composeRule.onNodeWithTag("profile_settings_save_name_button")
            .performScrollTo()
            .performClick()
        composeRule.waitForIdle()
        assertEquals("Rohan Das", savedName)
    }

    // =========================================================================
    // 3. APP UPDATE & TELEGRAM COMPLETELY REMOVED
    // =========================================================================

    @Test
    fun appUpdateAndTelegramResources_areCompletelyRemoved() {
        val res = context.resources
        val pkg = context.packageName
        assertEquals(0, res.getIdentifier("settings_app_update_section", "string", pkg))
        assertEquals(0, res.getIdentifier("settings_check_for_updates_title", "string", pkg))
        assertEquals(0, res.getIdentifier("settings_check_for_updates_subtitle", "string", pkg))
        assertEquals(0, res.getIdentifier("settings_update_link_unavailable", "string", pkg))
    }

    // =========================================================================
    // 4. ZERO NEW PERMISSIONS & API 26 COMPATIBILITY
    // =========================================================================

    @Test
    fun manifest_introducesZeroBroadImageOrQueryPermissions() {
        @Suppress("DEPRECATION")
        val pkgInfo = context.packageManager.getPackageInfo(
            context.packageName,
            PackageManager.GET_PERMISSIONS
        )
        val requested = pkgInfo.requestedPermissions?.toSet().orEmpty()
        assertFalse(
            "Must NOT request READ_MEDIA_IMAGES — use Android Photo Picker",
            requested.contains("android.permission.READ_MEDIA_IMAGES")
        )
        assertFalse(
            "Must NOT request READ_MEDIA_VIDEO",
            requested.contains("android.permission.READ_MEDIA_VIDEO")
        )
        assertFalse(
            "Must NOT request QUERY_ALL_PACKAGES",
            requested.contains("android.permission.QUERY_ALL_PACKAGES")
        )
    }

    @Test
    @Config(sdk = [26])
    fun api26Compatibility_profileAndHarmonicWorkCleanlyOnAndroid8() = runBlocking {
        val prefs = SonoraPreferences(context)
        assertFalse("Harmonic matching defaults to false on API 26", prefs.matchNextSongEnabled)

        val profilePrefs = UserProfilePreferences(
            context = context,
            hasPreExistingInstallProvider = { false }
        )
        assertEquals(ProfileOnboardingState.REQUIRED, profilePrefs.resolveInitialOnboardingState())
        assertTrue(profilePrefs.completeFirstLaunchProfile("API26 Listener", null).isSuccess)
        assertEquals("API26 Listener", profilePrefs.userProfileFlow.first().displayName)
    }
}
