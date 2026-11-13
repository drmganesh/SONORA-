package domain.color

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import com.example.model.DynamicThemeMode
import com.example.ui.theme.SonoraCharcoal
import com.example.ui.theme.SonoraCharcoalBorder
import com.example.ui.theme.SonoraDustyRose
import com.example.ui.theme.SonoraTextMuted
import com.example.ui.theme.SonoraTextPrimary
import com.example.ui.theme.SonoraTextSecondary
import ui.theme.DynamicColorPalette

/**
 * Generates an intelligent, accessible, WCAG-compliant DynamicColorPalette
 * tailored to the user's chosen DynamicThemeMode.
 */
object DynamicThemeGenerator {

    fun generate(
        rawPalette: AlbumArtPalette,
        mode: DynamicThemeMode,
        enabled: Boolean = true
    ): DynamicColorPalette {
        if (!enabled || mode == DynamicThemeMode.STATIC_THEME) {
            return DynamicColorPalette.default()
        }

        if (rawPalette.isMonochrome) {
            return generateMonochromePalette(rawPalette, mode)
        }

        return when (mode) {
            DynamicThemeMode.FULL_DYNAMIC -> generateFullDynamicPalette(rawPalette)
            DynamicThemeMode.AMOLED_DYNAMIC -> generateAmoledDynamicPalette(rawPalette)
            DynamicThemeMode.SUBTLE_DYNAMIC -> generateSubtleDynamicPalette(rawPalette)
            DynamicThemeMode.BACKGROUND_ONLY -> generateBackgroundOnlyPalette(rawPalette)
            DynamicThemeMode.STATIC_THEME -> DynamicColorPalette.default()
        }
    }

    private fun generateFullDynamicPalette(raw: AlbumArtPalette): DynamicColorPalette {
        val primary = ensureReadableAccent(raw.vibrantColor)
        val secondary = ensureReadableAccent(raw.lightVibrantColor)
        val accent = ensureReadableAccent(raw.vibrantColor)

        val darkDom = darkenToBackground(raw.dominantColor)
        val surfaceColor = blend(SonoraCharcoal, darkDom, 0.45f)
        val cardBg = blend(SonoraCharcoal, darkDom, 0.35f)
        val cardBorder = accent.copy(alpha = 0.28f)

        val onBtn = if (primary.luminance() > 0.45f) Color.Black else Color.White

        return DynamicColorPalette(
            primary = primary,
            onPrimary = onBtn,
            secondary = secondary,
            onSecondary = Color.Black,
            accent = accent,
            background = Color.Black,
            onBackground = SonoraTextPrimary,
            surface = surfaceColor,
            onSurface = SonoraTextPrimary,
            surfaceVariant = blend(surfaceColor, Color.White, 0.08f),
            cardBackground = cardBg,
            cardBorder = cardBorder,
            progressBar = primary,
            seekbarPlayed = primary,
            seekbarRemaining = Color.White.copy(alpha = 0.16f),
            playbackButton = primary,
            onPlaybackButton = onBtn,
            glowColor = primary.copy(alpha = 0.6f),
            gradientTop = darkDom,
            gradientMiddle = darkenToBackground(darkDom, 0.6f),
            gradientBottom = Color.Black,
            textPrimary = SonoraTextPrimary,
            textSecondary = SonoraTextSecondary,
            textMuted = SonoraTextMuted,
            subtleTint = primary.copy(alpha = 0.18f)
        )
    }

    private fun generateAmoledDynamicPalette(raw: AlbumArtPalette): DynamicColorPalette {
        val primary = ensureReadableAccent(raw.vibrantColor)
        val secondary = ensureReadableAccent(raw.lightVibrantColor)
        val accent = ensureReadableAccent(raw.vibrantColor)

        val onBtn = if (primary.luminance() > 0.45f) Color.Black else Color.White

        return DynamicColorPalette(
            primary = primary,
            onPrimary = onBtn,
            secondary = secondary,
            onSecondary = Color.Black,
            accent = accent,
            background = Color.Black,
            onBackground = SonoraTextPrimary,
            surface = Color(0xFF08090C),
            onSurface = SonoraTextPrimary,
            surfaceVariant = Color(0xFF121418),
            cardBackground = Color(0xFF101217),
            cardBorder = accent.copy(alpha = 0.22f),
            progressBar = primary,
            seekbarPlayed = primary,
            seekbarRemaining = Color.White.copy(alpha = 0.15f),
            playbackButton = primary,
            onPlaybackButton = onBtn,
            glowColor = primary.copy(alpha = 0.55f),
            gradientTop = blend(Color.Black, raw.dominantColor, 0.25f),
            gradientMiddle = blend(Color.Black, raw.dominantColor, 0.10f),
            gradientBottom = Color.Black,
            textPrimary = SonoraTextPrimary,
            textSecondary = SonoraTextSecondary,
            textMuted = SonoraTextMuted,
            subtleTint = primary.copy(alpha = 0.15f)
        )
    }

    private fun generateSubtleDynamicPalette(raw: AlbumArtPalette): DynamicColorPalette {
        val primary = ensureReadableAccent(raw.mutedColor)
        val secondary = ensureReadableAccent(raw.lightMutedColor)
        val accent = ensureReadableAccent(raw.mutedColor)

        val darkDom = darkenToBackground(raw.darkMutedColor)
        val surfaceColor = blend(SonoraCharcoal, darkDom, 0.35f)
        val cardBg = blend(SonoraCharcoal, darkDom, 0.25f)
        val cardBorder = accent.copy(alpha = 0.22f)

        val onBtn = if (primary.luminance() > 0.45f) Color.Black else Color.White

        return DynamicColorPalette(
            primary = primary,
            onPrimary = onBtn,
            secondary = secondary,
            onSecondary = Color.Black,
            accent = accent,
            background = Color.Black,
            onBackground = SonoraTextPrimary,
            surface = surfaceColor,
            onSurface = SonoraTextPrimary,
            surfaceVariant = blend(surfaceColor, Color.White, 0.05f),
            cardBackground = cardBg,
            cardBorder = cardBorder,
            progressBar = primary,
            seekbarPlayed = primary,
            seekbarRemaining = Color.White.copy(alpha = 0.15f),
            playbackButton = primary,
            onPlaybackButton = onBtn,
            glowColor = primary.copy(alpha = 0.4f),
            gradientTop = darkDom,
            gradientMiddle = darkenToBackground(darkDom, 0.5f),
            gradientBottom = Color.Black,
            textPrimary = SonoraTextPrimary,
            textSecondary = SonoraTextSecondary,
            textMuted = SonoraTextMuted,
            subtleTint = primary.copy(alpha = 0.12f)
        )
    }

    private fun generateBackgroundOnlyPalette(raw: AlbumArtPalette): DynamicColorPalette {
        val def = DynamicColorPalette.default()
        val glow = raw.vibrantColor.copy(alpha = 0.5f)
        val darkDom = darkenToBackground(raw.dominantColor)

        return def.copy(
            glowColor = glow,
            gradientTop = darkDom,
            gradientMiddle = darkenToBackground(darkDom, 0.55f),
            gradientBottom = Color.Black,
            cardBorder = SonoraCharcoalBorder
        )
    }

    private fun generateMonochromePalette(raw: AlbumArtPalette, mode: DynamicThemeMode): DynamicColorPalette {
        // High-end silver/platinum monochrome palette
        val silverAccent = Color(0xFFD6D9E0)
        val platinum = Color(0xFFF2F4F8)
        val darkSilver = Color(0xFF8E939E)

        return DynamicColorPalette(
            primary = silverAccent,
            onPrimary = Color.Black,
            secondary = platinum,
            onSecondary = Color.Black,
            accent = silverAccent,
            background = Color.Black,
            onBackground = SonoraTextPrimary,
            surface = Color(0xFF121418),
            onSurface = SonoraTextPrimary,
            surfaceVariant = Color(0xFF1B1E24),
            cardBackground = Color(0xFF14171D),
            cardBorder = Color(0xFF333844),
            progressBar = silverAccent,
            seekbarPlayed = silverAccent,
            seekbarRemaining = Color.White.copy(alpha = 0.18f),
            playbackButton = silverAccent,
            onPlaybackButton = Color.Black,
            glowColor = silverAccent.copy(alpha = 0.35f),
            gradientTop = Color(0xFF1C1E24),
            gradientMiddle = Color(0xFF0F1014),
            gradientBottom = Color.Black,
            textPrimary = SonoraTextPrimary,
            textSecondary = SonoraTextSecondary,
            textMuted = SonoraTextMuted,
            subtleTint = silverAccent.copy(alpha = 0.15f)
        )
    }

    private fun ensureReadableAccent(color: Color): Color {
        // Ensure color has sufficient luminance and vibrancy so it does not blend into pitch black
        val lum = color.luminance()
        return when {
            lum < 0.22f -> blend(color, Color.White, 0.38f)
            lum > 0.88f -> blend(color, Color.Black, 0.20f)
            else -> color
        }
    }

    private fun darkenToBackground(color: Color, factor: Float = 0.82f): Color {
        return Color(
            red = (color.red * (1f - factor)).coerceIn(0f, 1f),
            green = (color.green * (1f - factor)).coerceIn(0f, 1f),
            blue = (color.blue * (1f - factor)).coerceIn(0f, 1f),
            alpha = 1f
        )
    }

    private fun blend(color1: Color, color2: Color, ratio: Float): Color {
        val inverseRatio = 1f - ratio
        return Color(
            red = (color1.red * inverseRatio + color2.red * ratio).coerceIn(0f, 1f),
            green = (color1.green * inverseRatio + color2.green * ratio).coerceIn(0f, 1f),
            blue = (color1.blue * inverseRatio + color2.blue * ratio).coerceIn(0f, 1f),
            alpha = 1f
        )
    }
}
