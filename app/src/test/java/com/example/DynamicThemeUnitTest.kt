package com.example

import androidx.compose.ui.graphics.Color
import com.example.model.DynamicAppearanceMode
import com.example.model.DynamicColorIntensity
import com.example.model.DynamicThemeMode
import com.example.model.UserProfile
import domain.color.AlbumArtColorExtractor
import domain.color.AlbumArtPalette
import domain.color.DynamicThemeGenerator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DynamicThemeUnitTest {

    private fun vibrantBlueDarkArtwork() = AlbumArtPalette(
        dominantColor = Color(0xFF142850),
        vibrantColor = Color(0xFF277DA1),
        darkVibrantColor = Color(0xFF0B132B),
        lightVibrantColor = Color(0xFF64DFDF),
        mutedColor = Color(0xFF3A506B),
        darkMutedColor = Color(0xFF1C2541),
        lightMutedColor = Color(0xFF8FB8DE),
        isMonochrome = false,
        artworkIdentifier = "blue_dark",
        tertiaryColor = Color(0xFFE07A5F),
        averageLuminance = 0.16f,
        isDarkArtwork = true
    )

    private fun brightPastelArtwork() = AlbumArtPalette(
        dominantColor = Color(0xFFF6D6AD),
        vibrantColor = Color(0xFFE86A92),
        darkVibrantColor = Color(0xFF9E3B59),
        lightVibrantColor = Color(0xFFFFB5C5),
        mutedColor = Color(0xFFD9B8C4),
        darkMutedColor = Color(0xFF8C6B78),
        lightMutedColor = Color(0xFFFCEFE9),
        isMonochrome = false,
        artworkIdentifier = "bright_pastel",
        tertiaryColor = Color(0xFF4D9DE0),
        averageLuminance = 0.78f,
        isDarkArtwork = false
    )

    @Test
    fun appearanceModesAndIntensitiesRoundtripCleanly() {
        assertEquals(5, DynamicAppearanceMode.entries.size)
        assertEquals(DynamicAppearanceMode.FOLLOW_ALBUM_ART, DynamicAppearanceMode.fromId("follow_album_art"))
        assertEquals(DynamicAppearanceMode.FIXED_ACCENT, DynamicAppearanceMode.fromId("fixed_accent"))
        assertEquals(DynamicAppearanceMode.AMOLED_DARK, DynamicAppearanceMode.fromId("AMOLED_DARK"))
        assertEquals(DynamicAppearanceMode.LIGHT_THEME, DynamicAppearanceMode.fromId("light_theme"))
        assertEquals(DynamicAppearanceMode.AUTO_THEME, DynamicAppearanceMode.fromId("auto_theme"))
        assertEquals(DynamicAppearanceMode.DEFAULT, DynamicAppearanceMode.fromId("invalid_id"))

        assertEquals(3, DynamicColorIntensity.entries.size)
        assertEquals(DynamicColorIntensity.SUBTLE, DynamicColorIntensity.fromId("subtle"))
        assertEquals(DynamicColorIntensity.BALANCED, DynamicColorIntensity.fromId("balanced"))
        assertEquals(DynamicColorIntensity.VIBRANT, DynamicColorIntensity.fromId("VIBRANT"))
        assertEquals(DynamicColorIntensity.DEFAULT, DynamicColorIntensity.fromId(null))
    }

    @Test
    fun followAlbumArtAdaptsDarkAndLightAndPreservesWcagContrast() {
        val darkGenerated = DynamicThemeGenerator.generate(
            rawPalette = vibrantBlueDarkArtwork(),
            mode = DynamicThemeMode.FULL_DYNAMIC,
            enabled = true,
            appearanceMode = DynamicAppearanceMode.FOLLOW_ALBUM_ART,
            intensity = DynamicColorIntensity.BALANCED,
            gradientEnabled = true
        )
        assertTrue(darkGenerated.isDarkPalette)
        assertTrue(
            "Primary text contrast on dark bg must be >= 7.0",
            DynamicThemeGenerator.contrastRatio(darkGenerated.textPrimary, darkGenerated.background) >= 7.0f
        )
        assertTrue(
            "Secondary text contrast on dark bg must be >= 4.5",
            DynamicThemeGenerator.contrastRatio(darkGenerated.textSecondary, darkGenerated.background) >= 4.5f
        )

        val lightGenerated = DynamicThemeGenerator.generate(
            rawPalette = brightPastelArtwork(),
            mode = DynamicThemeMode.FULL_DYNAMIC,
            enabled = true,
            appearanceMode = DynamicAppearanceMode.FOLLOW_ALBUM_ART,
            intensity = DynamicColorIntensity.BALANCED,
            gradientEnabled = true
        )
        assertFalse(lightGenerated.isDarkPalette)
        assertTrue(
            "Primary text contrast on light bg must be >= 7.0",
            DynamicThemeGenerator.contrastRatio(lightGenerated.textPrimary, lightGenerated.background) >= 7.0f
        )
        assertTrue(
            "Secondary text contrast on light bg must be >= 4.5",
            DynamicThemeGenerator.contrastRatio(lightGenerated.textSecondary, lightGenerated.background) >= 4.5f
        )
    }

    @Test
    fun amoledFixedAutoAndGradientToggleBehaveAccurately() {
        val amoled = DynamicThemeGenerator.generate(
            rawPalette = brightPastelArtwork(),
            appearanceMode = DynamicAppearanceMode.AMOLED_DARK,
            intensity = DynamicColorIntensity.VIBRANT
        )
        assertTrue(amoled.isDarkPalette)
        assertEquals(Color(0xFF000000), amoled.background)

        val customHue = Color(0xFF00B4D8)
        val fixed = DynamicThemeGenerator.generate(
            rawPalette = vibrantBlueDarkArtwork(),
            appearanceMode = DynamicAppearanceMode.FIXED_ACCENT,
            customAccentColor = customHue,
            systemIsDark = false
        )
        assertFalse(fixed.isDarkPalette)
        assertNotEquals(Color(0xFF277DA1), fixed.primary)

        val autoLight = DynamicThemeGenerator.generate(
            rawPalette = vibrantBlueDarkArtwork(),
            appearanceMode = DynamicAppearanceMode.AUTO_THEME,
            systemIsDark = false
        )
        assertFalse(autoLight.isDarkPalette)

        val flatGradient = DynamicThemeGenerator.generate(
            rawPalette = vibrantBlueDarkArtwork(),
            appearanceMode = DynamicAppearanceMode.FOLLOW_ALBUM_ART,
            gradientEnabled = false
        )
        assertEquals(flatGradient.background, flatGradient.gradientTop)
        assertEquals(flatGradient.background, flatGradient.gradientMiddle)
        assertEquals(flatGradient.background, flatGradient.gradientBottom)
    }

    @Test
    fun monochromeArtworkAndMuddyFilteringProduceCleanPalettes() {
        val monochromeRaw = AlbumArtPalette.default("bw").copy(isMonochrome = true, isDarkArtwork = true)
        val monoDark = DynamicThemeGenerator.generate(
            rawPalette = monochromeRaw,
            appearanceMode = DynamicAppearanceMode.FOLLOW_ALBUM_ART
        )
        assertTrue(monoDark.isDarkPalette)
        assertTrue(DynamicThemeGenerator.contrastRatio(monoDark.textPrimary, monoDark.background) >= 7.0f)

        // Muddy olive-brown / low-chroma grey must be filtered out
        assertTrue(AlbumArtColorExtractor.isMuddyOrOverlyGray(floatArrayOf(0f, 0.08f, 0.5f)))
        assertTrue(AlbumArtColorExtractor.isMuddyOrOverlyGray(floatArrayOf(38f, 0.25f, 0.20f)))
        // Crisp saturated cobalt blue must pass cleanly
        assertFalse(AlbumArtColorExtractor.isMuddyOrOverlyGray(floatArrayOf(215f, 0.72f, 0.50f)))
    }

    @Test
    fun userProfileModelDefaultsAndUsernameNormalization() {
        val defaultProfile = UserProfile()
        assertEquals(UserProfile.DEFAULT_DISPLAY_NAME, defaultProfile.displayName)
        assertFalse(defaultProfile.hasCustomPhoto)
        assertEquals("", defaultProfile.formattedUsername)

        val customized = UserProfile(
            displayName = "Aria Vance",
            username = "  @ariavance ",
            avatarUri = "file:///data/user/0/avatar.jpg"
        )
        assertTrue(customized.hasCustomPhoto)
        assertEquals("ariavance", customized.formattedUsername)
    }
}
