package com.example

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.example.data.SonoraPreferences
import com.example.model.DynamicAppearanceMode
import com.example.model.DynamicColorIntensity
import com.example.model.DynamicThemeMode
import com.example.model.UserProfile
import data.cache.AlbumColorCache
import data.preferences.ThemePreferences
import data.preferences.UserProfilePreferences
import domain.color.AlbumArtColorExtractor
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class DynamicThemeAndProfileRobolectricTest {

    private fun solidBitmap(colorInt: Int, width: Int = 64, height: Int = 64): Bitmap {
        return Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply {
            eraseColor(colorInt)
        }
    }

    @Test
    fun `extractPalette handles vibrant, bright, monochrome, transparent, and null bitmaps`() = runTest {
        AlbumColorCache.clear()

        val vibrantBmp = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888).apply {
            eraseColor(android.graphics.Color.rgb(22, 118, 224))
            for (y in 0 until 32) {
                for (x in 0 until 32) {
                    setPixel(x, y, android.graphics.Color.rgb(16, 48, 128))
                }
            }
        }
        val blueVibrant = AlbumArtColorExtractor.extractPalette(
            bitmap = vibrantBmp,
            artworkIdentifier = "test_vibrant_blue"
        )
        assertFalse("Electric blue artwork should not be marked monochrome", blueVibrant.isMonochrome)
        assertTrue("Blue artwork should be dark artwork", blueVibrant.isDarkArtwork)
        assertNotNull(AlbumColorCache.get("test_vibrant_blue"))

        val brightPastel = AlbumArtColorExtractor.extractPalette(
            bitmap = solidBitmap(android.graphics.Color.rgb(252, 232, 215)),
            artworkIdentifier = "test_bright_pastel"
        )
        assertFalse("High-key pastel artwork should report isDarkArtwork = false", brightPastel.isDarkArtwork)

        val monoBmp = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888).apply {
            eraseColor(android.graphics.Color.rgb(42, 42, 42))
            for (y in 0 until 32) {
                for (x in 0 until 32) {
                    setPixel(x, y, android.graphics.Color.rgb(210, 210, 210))
                }
            }
        }
        val monochrome = AlbumArtColorExtractor.extractPalette(monoBmp, "test_bw_art")
        assertTrue("Pure greyscale artwork must be detected as monochrome", monochrome.isMonochrome)

        val transparentBmp = Bitmap.createBitmap(48, 48, Bitmap.Config.ARGB_8888).apply {
            eraseColor(android.graphics.Color.TRANSPARENT)
        }
        val flatFallback = AlbumArtColorExtractor.extractPalette(transparentBmp, "test_transparent")
        assertNotNull(flatFallback)

        val missingFallback = AlbumArtColorExtractor.extractPalette(null, "test_missing")
        assertEquals("test_missing", missingFallback.artworkIdentifier)
    }

    @Test
    fun `SonoraPreferences and ThemePreferences persist dynamic appearance settings`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefs = SonoraPreferences(context)

        prefs.dynamicColorsEnabled = false
        prefs.dynamicThemeMode = DynamicThemeMode.AMOLED_DYNAMIC
        prefs.dynamicAppearanceMode = DynamicAppearanceMode.LIGHT_THEME
        prefs.dynamicColorIntensity = DynamicColorIntensity.VIBRANT
        prefs.dynamicGradientEnabled = false

        val reloaded = SonoraPreferences(context)
        assertFalse(reloaded.dynamicColorsEnabled)
        assertEquals(DynamicThemeMode.AMOLED_DYNAMIC, reloaded.dynamicThemeMode)
        assertEquals(DynamicAppearanceMode.LIGHT_THEME, reloaded.dynamicAppearanceMode)
        assertEquals(DynamicColorIntensity.VIBRANT, reloaded.dynamicColorIntensity)
        assertFalse(reloaded.dynamicGradientEnabled)

        val themeStore = ThemePreferences(context)
        themeStore.resetToDefault()
        assertTrue(themeStore.dynamicColorsEnabledFlow.first())
        assertEquals(DynamicAppearanceMode.FOLLOW_ALBUM_ART, themeStore.dynamicAppearanceModeFlow.first())
        assertEquals(DynamicColorIntensity.BALANCED, themeStore.dynamicColorIntensityFlow.first())
        assertTrue(themeStore.dynamicGradientEnabledFlow.first())
    }

    @Test
    fun `UserProfilePreferences persists display name, username, internal copied avatar, and removal`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val profilePrefs = UserProfilePreferences(context)

        profilePrefs.saveProfileDetails("  Nadia Reyes ", "@nadiasound")
        val afterDetails = profilePrefs.userProfileFlow.first()
        assertEquals("Nadia Reyes", afterDetails.displayName)
        assertEquals("nadiasound", afterDetails.formattedUsername)
        assertNull("Profile photo must start empty rather than falling back to album art", afterDetails.avatarUri)

        // Create a temp image source file simulating a picked photo URI
        val tempPickedFile = File(context.cacheDir, "picked_sample.jpg").apply {
            writeBytes(byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte(), 0x11, 0x22))
        }
        val persistedUri = profilePrefs.persistPickedAvatar(Uri.fromFile(tempPickedFile))
        assertNotNull(persistedUri)
        val afterPhoto = profilePrefs.userProfileFlow.first()
        assertTrue(afterPhoto.hasCustomPhoto)
        assertEquals(persistedUri, afterPhoto.avatarUri)

        // Copied file should exist inside filesDir
        val copiedPath = Uri.parse(persistedUri).path
        assertNotNull(copiedPath)
        assertTrue(File(copiedPath!!).exists())

        // Removing photo clears DataStore URI and cleans up copied avatar file
        profilePrefs.removeAvatar()
        val afterRemove = profilePrefs.userProfileFlow.first()
        assertFalse(afterRemove.hasCustomPhoto)
        assertNull(afterRemove.avatarUri)
        assertFalse(File(copiedPath).exists())

        // Saving blank display name falls back cleanly to DEFAULT_DISPLAY_NAME
        profilePrefs.saveProfileDetails("   ", "")
        assertEquals(UserProfile.DEFAULT_DISPLAY_NAME, profilePrefs.userProfileFlow.first().displayName)
    }
}
