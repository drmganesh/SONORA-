package ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import com.example.ui.theme.SonoraCharcoal
import com.example.ui.theme.SonoraCharcoalBorder
import com.example.ui.theme.SonoraCyan
import com.example.ui.theme.SonoraDustyRose
import com.example.ui.theme.SonoraDustyRoseLight
import com.example.ui.theme.SonoraTextMuted
import com.example.ui.theme.SonoraTextPrimary
import com.example.ui.theme.SonoraTextSecondary

/**
 * Coordinated Dynamic UI Theme Palette derived from album artwork.
 * Provides accessible, WCAG-compliant colors for every section of SONORA:
 * main background, elevated & secondary surfaces, primary/secondary/tertiary accents,
 * readable typography, outline, progress indicators, and tonal gradients.
 */
@Immutable
data class DynamicColorPalette(
    val primary: Color,
    val onPrimary: Color,
    val secondary: Color,
    val onSecondary: Color,
    val accent: Color,
    val background: Color,
    val onBackground: Color,
    val surface: Color,
    val onSurface: Color,
    val surfaceVariant: Color,
    val cardBackground: Color,
    val cardBorder: Color,
    val progressBar: Color,
    val seekbarPlayed: Color,
    val seekbarRemaining: Color,
    val playbackButton: Color,
    val onPlaybackButton: Color,
    val glowColor: Color,
    val gradientTop: Color,
    val gradientMiddle: Color,
    val gradientBottom: Color,
    val textPrimary: Color = SonoraTextPrimary,
    val textSecondary: Color = SonoraTextSecondary,
    val textMuted: Color = SonoraTextMuted,
    val subtleTint: Color = SonoraDustyRose.copy(alpha = 0.15f),
    val tertiary: Color = secondary,
    val onTertiary: Color = onSecondary,
    val secondarySurface: Color = surfaceVariant,
    val elevatedSurface: Color = surfaceVariant,
    val outline: Color = cardBorder,
    val isDarkPalette: Boolean = true,
    val gradientEnabled: Boolean = true
) {
    /**
     * Vertical tonal background gradient (or flat solid fill when Dynamic Gradient is OFF).
     */
    val backgroundGradientBrush: Brush
        get() = if (gradientEnabled) {
            Brush.verticalGradient(
                colors = listOf(
                    gradientTop,
                    gradientMiddle,
                    gradientBottom
                )
            )
        } else {
            SolidColor(background)
        }

    /**
     * Radial glow brush for ambient lighting behind album artwork.
     */
    val ambientGlowBrush: Brush
        get() = Brush.radialGradient(
            colors = listOf(
                glowColor.copy(alpha = 0.55f),
                glowColor.copy(alpha = 0.22f),
                Color.Transparent
            )
        )

    /**
     * Linear seekbar played gradient brush.
     */
    val seekbarGradientBrush: Brush
        get() = Brush.horizontalGradient(
            colors = listOf(
                primary,
                accent
            )
        )

    companion object {
        /**
         * Standard SONORA Hi-Fi studio default palette (Dusty Rose & Charcoal).
         */
        fun default(): DynamicColorPalette {
            return DynamicColorPalette(
                primary = SonoraDustyRose,
                onPrimary = Color.Black,
                secondary = SonoraDustyRoseLight,
                onSecondary = Color.Black,
                accent = SonoraDustyRose,
                background = Color(0xFF000000),
                onBackground = SonoraTextPrimary,
                surface = SonoraCharcoal,
                onSurface = SonoraTextPrimary,
                surfaceVariant = Color(0xFF181B22),
                cardBackground = SonoraCharcoal,
                cardBorder = SonoraCharcoalBorder,
                progressBar = SonoraDustyRose,
                seekbarPlayed = SonoraDustyRose,
                seekbarRemaining = Color.Black.copy(alpha = 0.5f),
                playbackButton = SonoraDustyRose,
                onPlaybackButton = Color.Black,
                glowColor = SonoraDustyRose.copy(alpha = 0.45f),
                gradientTop = Color(0xFF1E1514),
                gradientMiddle = Color(0xFF0E0B0B),
                gradientBottom = Color(0xFF000000),
                textPrimary = SonoraTextPrimary,
                textSecondary = SonoraTextSecondary,
                textMuted = SonoraTextMuted,
                subtleTint = SonoraDustyRose.copy(alpha = 0.15f),
                tertiary = SonoraCyan.copy(alpha = 0.85f),
                onTertiary = Color.Black,
                secondarySurface = Color(0xFF1A1719),
                elevatedSurface = Color(0xFF1F1D21),
                outline = SonoraCharcoalBorder,
                isDarkPalette = true,
                gradientEnabled = true
            )
        }
    }
}
