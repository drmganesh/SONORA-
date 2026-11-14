package ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EditorialThemeTest {

    @Test
    fun `all 9 HomeThemeMode themes resolve cleanly with proper dark polarity and readable contrast`() {
        val modes = HomeThemeMode.entries
        assertEquals(9, modes.size)

        val dynamicPalette = DynamicColorPalette.default().copy(
            primary = Color(0xFF5D7CF2),
            secondary = Color(0xFF90A7FF),
            accent = Color(0xFFE09F98)
        )

        modes.forEach { mode ->
            val colors = editorialColorsFor(
                mode = mode,
                customAccentArgb = if (mode == HomeThemeMode.CUSTOM_ACCENT) 0xFF38D4FF else null,
                dynamicPalette = dynamicPalette
            )

            // Dark/Light flag matches mode expectations
            assertEquals("Mode ${mode.id} isDark mismatch", mode.isDarkDefault, colors.isDark)

            // Text foreground has clear luminance separation from canvas background
            val lumDiff = abs(colors.textPrimary.luminance() - colors.background.luminance())
            assertTrue(
                "Mode ${mode.id} foreground/background contrast too low ($lumDiff)",
                lumDiff > 0.45f
            )

            // Secondary text differs visibly from background
            val secDiff = abs(colors.textSecondary.luminance() - colors.background.luminance())
            assertTrue(
                "Mode ${mode.id} secondary text contrast too low ($secDiff)",
                secDiff > 0.20f
            )

            // Accent text on strong accent button has separation from accentStrong
            val accentButtonDiff = abs(colors.onAccentStrong.luminance() - colors.accentStrong.luminance())
            assertTrue(
                "Mode ${mode.id} accent button label contrast too low ($accentButtonDiff)",
                accentButtonDiff > 0.25f
            )
        }
    }

    @Test
    fun `legacy light_minimal id migrates to WARM_CREAM and defaults to DYNAMIC_ALBUM_ART`() {
        assertEquals(HomeThemeMode.DYNAMIC_ALBUM_ART, HomeThemeMode.DEFAULT)
        assertEquals(HomeThemeMode.WARM_CREAM, HomeThemeMode.fromId("light_minimal"))
        assertEquals(HomeThemeMode.WARM_CREAM, HomeThemeMode.fromId("LIGHT_MINIMAL"))
        assertEquals(HomeThemeMode.AMOLED_MIDNIGHT, HomeThemeMode.fromId("amoled_midnight"))
        assertEquals(HomeThemeMode.DYNAMIC_ALBUM_ART, HomeThemeMode.fromId("nonexistent"))
        assertEquals(HomeThemeMode.DYNAMIC_ALBUM_ART, HomeThemeMode.fromId(null))
    }

    @Test
    fun `AMOLED themes keep deep black or midnight backgrounds while light editorial themes stay light`() {
        val dynamic = DynamicColorPalette.default()
        val amoledPure = editorialColorsFor(HomeThemeMode.AMOLED_DARK, null, dynamic)
        val amoledMidnight = editorialColorsFor(HomeThemeMode.AMOLED_MIDNIGHT, null, dynamic)
        val lavender = editorialColorsFor(HomeThemeMode.FIXED_LAVENDER, null, dynamic)

        assertTrue(amoledPure.isDark)
        assertEquals(Color(0xFF000000), amoledPure.background)
        assertTrue(amoledMidnight.isDark)
        assertTrue(amoledMidnight.background.luminance() < 0.02f)

        assertFalse(lavender.isDark)
        assertTrue(lavender.background.luminance() > 0.85f)
        assertNotEquals(amoledPure.background, lavender.background)
    }
}
