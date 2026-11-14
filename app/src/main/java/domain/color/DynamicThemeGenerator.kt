package domain.color

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import com.example.model.DynamicAppearanceMode
import com.example.model.DynamicColorIntensity
import com.example.model.DynamicThemeMode
import com.example.ui.theme.SonoraCharcoal
import com.example.ui.theme.SonoraCharcoalBorder
import com.example.ui.theme.SonoraDustyRose
import com.example.ui.theme.SonoraDustyRoseLight
import com.example.ui.theme.SonoraTextMuted
import com.example.ui.theme.SonoraTextPrimary
import com.example.ui.theme.SonoraTextSecondary
import ui.theme.DynamicColorPalette
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Generates an intelligent, coordinated, WCAG-compliant [DynamicColorPalette] across
 * all SONORA appearance modes and color intensities.
 */
object DynamicThemeGenerator {

    fun generate(
        rawPalette: AlbumArtPalette,
        mode: DynamicThemeMode,
        enabled: Boolean = true
    ): DynamicColorPalette = generate(
        rawPalette = rawPalette,
        mode = mode,
        enabled = enabled,
        appearanceMode = when (mode) {
            DynamicThemeMode.AMOLED_DYNAMIC -> DynamicAppearanceMode.AMOLED_DARK
            DynamicThemeMode.STATIC_THEME -> DynamicAppearanceMode.FIXED_ACCENT
            else -> DynamicAppearanceMode.FOLLOW_ALBUM_ART
        },
        intensity = when (mode) {
            DynamicThemeMode.SUBTLE_DYNAMIC -> DynamicColorIntensity.SUBTLE
            else -> DynamicColorIntensity.BALANCED
        },
        gradientEnabled = true,
        customAccentColor = null,
        systemIsDark = true
    )

    fun generate(
        rawPalette: AlbumArtPalette,
        mode: DynamicThemeMode = DynamicThemeMode.DEFAULT,
        enabled: Boolean = true,
        appearanceMode: DynamicAppearanceMode = DynamicAppearanceMode.DEFAULT,
        intensity: DynamicColorIntensity = DynamicColorIntensity.DEFAULT,
        gradientEnabled: Boolean = true,
        customAccentColor: Color? = null,
        systemIsDark: Boolean = true
    ): DynamicColorPalette {
        if (!enabled || mode == DynamicThemeMode.STATIC_THEME) {
            val base = DynamicColorPalette.default().copy(gradientEnabled = gradientEnabled)
            if (appearanceMode == DynamicAppearanceMode.LIGHT_THEME) {
                return buildLightPalette(
                    seedPrimary = customAccentColor ?: SonoraDustyRose,
                    seedSecondary = SonoraDustyRoseLight,
                    seedTertiary = Color(0xFF4A7C8C),
                    intensity = intensity,
                    gradientEnabled = gradientEnabled
                )
            }
            if (appearanceMode == DynamicAppearanceMode.FIXED_ACCENT && customAccentColor != null) {
                return buildDarkPalette(
                    seedPrimary = customAccentColor,
                    seedSecondary = blend(customAccentColor, Color.White, 0.25f),
                    seedTertiary = shiftHue(customAccentColor, 36f),
                    dominantSeed = darkenToBackground(customAccentColor, 0.80f),
                    amoled = false,
                    intensity = intensity,
                    gradientEnabled = gradientEnabled
                )
            }
            return base
        }

        val resolveAmoled = appearanceMode == DynamicAppearanceMode.AMOLED_DARK ||
            mode == DynamicThemeMode.AMOLED_DYNAMIC

        val resolveLight = when (appearanceMode) {
            DynamicAppearanceMode.LIGHT_THEME -> true
            DynamicAppearanceMode.AMOLED_DARK -> false
            DynamicAppearanceMode.AUTO_THEME -> !systemIsDark
            DynamicAppearanceMode.FIXED_ACCENT -> false
            DynamicAppearanceMode.FOLLOW_ALBUM_ART -> !rawPalette.isDarkArtwork
        }

        // FIXED_ACCENT mode locks accents to the user's chosen hue across the entire app
        if (appearanceMode == DynamicAppearanceMode.FIXED_ACCENT) {
            val fixedSeed = customAccentColor ?: SonoraDustyRose
            val secondarySeed = blend(fixedSeed, Color.White, 0.28f)
            val tertiarySeed = shiftHue(fixedSeed, 38f)
            return if (systemIsDark) {
                buildDarkPalette(
                    seedPrimary = fixedSeed,
                    seedSecondary = secondarySeed,
                    seedTertiary = tertiarySeed,
                    dominantSeed = darkenToBackground(fixedSeed, 0.82f),
                    amoled = false,
                    intensity = intensity,
                    gradientEnabled = gradientEnabled
                )
            } else {
                buildLightPalette(
                    seedPrimary = fixedSeed,
                    seedSecondary = secondarySeed,
                    seedTertiary = tertiarySeed,
                    intensity = intensity,
                    gradientEnabled = gradientEnabled
                )
            }
        }

        // Monochrome / B&W album artwork -> sleek platinum/graphite theme
        if (rawPalette.isMonochrome) {
            return generateMonochromePalette(
                isLight = resolveLight && !resolveAmoled,
                amoled = resolveAmoled,
                gradientEnabled = gradientEnabled
            )
        }

        if (mode == DynamicThemeMode.BACKGROUND_ONLY) {
            val def = DynamicColorPalette.default()
            val darkDom = darkenToBackground(rawPalette.dominantColor, 0.78f)
            val glow = rawPalette.vibrantColor.copy(alpha = 0.50f)
            return def.copy(
                glowColor = glow,
                gradientTop = if (gradientEnabled) darkDom else Color.Black,
                gradientMiddle = if (gradientEnabled) darkenToBackground(darkDom, 0.52f) else Color.Black,
                gradientBottom = Color.Black,
                cardBorder = SonoraCharcoalBorder,
                gradientEnabled = gradientEnabled
            )
        }

        val rawPrimary = if (mode == DynamicThemeMode.SUBTLE_DYNAMIC) rawPalette.mutedColor else rawPalette.vibrantColor
        val rawSecondary = if (mode == DynamicThemeMode.SUBTLE_DYNAMIC) rawPalette.lightMutedColor else rawPalette.lightVibrantColor
        val rawTertiary = rawPalette.tertiaryColor
        val domSeed = if (mode == DynamicThemeMode.SUBTLE_DYNAMIC) rawPalette.darkMutedColor else rawPalette.dominantColor

        val effIntensity = if (mode == DynamicThemeMode.SUBTLE_DYNAMIC && intensity == DynamicColorIntensity.BALANCED) {
            DynamicColorIntensity.SUBTLE
        } else {
            intensity
        }

        return if (resolveLight) {
            buildLightPalette(
                seedPrimary = rawPrimary,
                seedSecondary = rawSecondary,
                seedTertiary = rawTertiary,
                intensity = effIntensity,
                gradientEnabled = gradientEnabled
            )
        } else {
            buildDarkPalette(
                seedPrimary = rawPrimary,
                seedSecondary = rawSecondary,
                seedTertiary = rawTertiary,
                dominantSeed = domSeed,
                amoled = resolveAmoled,
                intensity = effIntensity,
                gradientEnabled = gradientEnabled
            )
        }
    }

    private fun buildDarkPalette(
        seedPrimary: Color,
        seedSecondary: Color,
        seedTertiary: Color,
        dominantSeed: Color,
        amoled: Boolean,
        intensity: DynamicColorIntensity,
        gradientEnabled: Boolean
    ): DynamicColorPalette {
        val scaledPrimary = tuneChroma(seedPrimary, intensity.saturationScale, minLum = 0.28f, maxLum = 0.78f)
        val scaledSecondary = tuneChroma(seedSecondary, intensity.saturationScale * 0.9f, minLum = 0.34f, maxLum = 0.84f)
        val scaledTertiary = tuneChroma(seedTertiary, intensity.saturationScale * 0.85f, minLum = 0.30f, maxLum = 0.80f)

        val domSafe = clampBackgroundSaturation(dominantSeed, maxSat = if (amoled) 0.20f else 0.32f)
        val tintScale = intensity.surfaceTintScale

        val bg = if (amoled) {
            Color(0xFF000000)
        } else {
            blend(Color(0xFF07080C), domSafe, (0.28f * tintScale).coerceIn(0.10f, 0.42f))
        }
        val surface = if (amoled) {
            blend(Color(0xFF08090D), domSafe, (0.14f * tintScale).coerceIn(0.05f, 0.24f))
        } else {
            blend(SonoraCharcoal, domSafe, (0.42f * tintScale).coerceIn(0.18f, 0.56f))
        }
        val secondarySurface = if (amoled) {
            blend(Color(0xFF101218), domSafe, 0.16f)
        } else {
            blend(surface, scaledPrimary, (0.14f * tintScale).coerceIn(0.06f, 0.22f))
        }
        val elevatedSurface = blend(surface, Color.White, if (amoled) 0.06f else 0.09f)

        val primary = ensureDarkBgAccent(scaledPrimary, bg)
        val secondary = ensureDarkBgAccent(scaledSecondary, bg)
        val tertiary = ensureDarkBgAccent(scaledTertiary, bg)

        val onPrim = bestContrastingForeground(primary)
        val onSec = bestContrastingForeground(secondary)
        val onTert = bestContrastingForeground(tertiary)

        val textPrim = ensureWcagTextContrast(Color(0xFFF4F6FB), bg, minRatio = 7.0f)
        val textSec = ensureWcagTextContrast(Color(0xFFB5BCD0), bg, minRatio = 4.6f)
        val textMute = Color(0xFF7D859B)

        val gradTop = if (gradientEnabled) {
            blend(bg, domSafe, if (amoled) 0.30f else (0.48f * tintScale).coerceIn(0.22f, 0.62f))
        } else bg
        val gradMid = if (gradientEnabled) blend(bg, gradTop, 0.45f) else bg
        val gradBot = bg

        return DynamicColorPalette(
            primary = primary,
            onPrimary = onPrim,
            secondary = secondary,
            onSecondary = onSec,
            accent = primary,
            background = bg,
            onBackground = textPrim,
            surface = surface,
            onSurface = textPrim,
            surfaceVariant = secondarySurface,
            cardBackground = surface,
            cardBorder = primary.copy(alpha = if (amoled) 0.24f else 0.30f),
            progressBar = primary,
            seekbarPlayed = primary,
            seekbarRemaining = Color.White.copy(alpha = 0.17f),
            playbackButton = primary,
            onPlaybackButton = onPrim,
            glowColor = primary.copy(alpha = 0.55f),
            gradientTop = gradTop,
            gradientMiddle = gradMid,
            gradientBottom = gradBot,
            textPrimary = textPrim,
            textSecondary = textSec,
            textMuted = textMute,
            subtleTint = primary.copy(alpha = 0.16f),
            tertiary = tertiary,
            onTertiary = onTert,
            secondarySurface = secondarySurface,
            elevatedSurface = elevatedSurface,
            outline = primary.copy(alpha = 0.26f),
            isDarkPalette = true,
            gradientEnabled = gradientEnabled
        )
    }

    private fun buildLightPalette(
        seedPrimary: Color,
        seedSecondary: Color,
        seedTertiary: Color,
        intensity: DynamicColorIntensity,
        gradientEnabled: Boolean
    ): DynamicColorPalette {
        val tintScale = intensity.surfaceTintScale
        // Cap background saturation strictly so light backgrounds look like fine pastel paper, never neon
        val softSeed = clampBackgroundSaturation(seedPrimary, maxSat = 0.45f)
        val bg = blend(Color(0xFFF8F7FC), softSeed, (0.10f * tintScale).coerceIn(0.04f, 0.18f))
        val surface = Color(0xFFFFFFFF)
        val elevated = blend(Color(0xFFFFFFFF), softSeed, (0.05f * tintScale).coerceIn(0.02f, 0.10f))
        val secondarySurface = blend(Color(0xFFF0EEF7), softSeed, (0.16f * tintScale).coerceIn(0.08f, 0.28f))

        // Strong readable accent for buttons, icons, sliders on light paper
        val scaledStrong = tuneChroma(seedPrimary, intensity.saturationScale, minLum = 0.14f, maxLum = 0.46f)
        val primary = ensureLightBgAccent(scaledStrong, bg)
        val secondary = ensureLightBgAccent(
            tuneChroma(seedSecondary, intensity.saturationScale * 0.9f, minLum = 0.18f, maxLum = 0.52f),
            bg
        )
        val tertiary = ensureLightBgAccent(
            tuneChroma(seedTertiary, intensity.saturationScale * 0.85f, minLum = 0.18f, maxLum = 0.50f),
            bg
        )

        val onPrim = bestContrastingForeground(primary)
        val onSec = bestContrastingForeground(secondary)
        val onTert = bestContrastingForeground(tertiary)

        val textPrim = ensureWcagTextContrast(Color(0xFF14151D), bg, minRatio = 7.0f)
        val textSec = ensureWcagTextContrast(Color(0xFF4F5162), bg, minRatio = 4.6f)
        val textMute = Color(0xFF7B7D90)

        val gradTop = if (gradientEnabled) blend(Color(0xFFFFFFFF), softSeed, (0.19f * tintScale).coerceIn(0.07f, 0.28f)) else bg
        val gradMid = if (gradientEnabled) bg else bg
        val gradBot = if (gradientEnabled) blend(Color(0xFFFFFFFF), softSeed, (0.06f * tintScale).coerceIn(0.03f, 0.12f)) else bg

        return DynamicColorPalette(
            primary = primary,
            onPrimary = onPrim,
            secondary = secondary,
            onSecondary = onSec,
            accent = primary,
            background = bg,
            onBackground = textPrim,
            surface = surface,
            onSurface = textPrim,
            surfaceVariant = secondarySurface,
            cardBackground = surface,
            cardBorder = primary.copy(alpha = 0.22f),
            progressBar = primary,
            seekbarPlayed = primary,
            seekbarRemaining = primary.copy(alpha = 0.18f),
            playbackButton = primary,
            onPlaybackButton = onPrim,
            glowColor = seedPrimary.copy(alpha = 0.34f),
            gradientTop = gradTop,
            gradientMiddle = gradMid,
            gradientBottom = gradBot,
            textPrimary = textPrim,
            textSecondary = textSec,
            textMuted = textMute,
            subtleTint = primary.copy(alpha = 0.12f),
            tertiary = tertiary,
            onTertiary = onTert,
            secondarySurface = secondarySurface,
            elevatedSurface = elevated,
            outline = primary.copy(alpha = 0.20f),
            isDarkPalette = false,
            gradientEnabled = gradientEnabled
        )
    }

    private fun generateMonochromePalette(
        isLight: Boolean,
        amoled: Boolean,
        gradientEnabled: Boolean
    ): DynamicColorPalette {
        return if (isLight) {
            val bg = Color(0xFFF6F7FA)
            val ink = Color(0xFF161820)
            val accent = Color(0xFF343846)
            DynamicColorPalette(
                primary = accent,
                onPrimary = Color.White,
                secondary = Color(0xFF565C6E),
                onSecondary = Color.White,
                accent = accent,
                background = bg,
                onBackground = ink,
                surface = Color.White,
                onSurface = ink,
                surfaceVariant = Color(0xFFECEDF2),
                cardBackground = Color.White,
                cardBorder = Color(0xFFDADCE4),
                progressBar = accent,
                seekbarPlayed = accent,
                seekbarRemaining = Color(0xFF161820).copy(alpha = 0.14f),
                playbackButton = accent,
                onPlaybackButton = Color.White,
                glowColor = Color(0xFF8B90A0).copy(alpha = 0.25f),
                gradientTop = if (gradientEnabled) Color(0xFFECEEF4) else bg,
                gradientMiddle = bg,
                gradientBottom = if (gradientEnabled) Color(0xFFF9FAFC) else bg,
                textPrimary = ink,
                textSecondary = Color(0xFF515564),
                textMuted = Color(0xFF7D8292),
                subtleTint = accent.copy(alpha = 0.10f),
                tertiary = Color(0xFF6B7280),
                onTertiary = Color.White,
                secondarySurface = Color(0xFFEEF0F5),
                elevatedSurface = Color(0xFFFAFAFC),
                outline = Color(0xFFD5D8E2),
                isDarkPalette = false,
                gradientEnabled = gradientEnabled
            )
        } else {
            val silver = Color(0xFFDCE0EA)
            val platinum = Color(0xFFF4F6FB)
            val bg = if (amoled) Color.Black else Color(0xFF0A0B0F)
            DynamicColorPalette(
                primary = silver,
                onPrimary = Color.Black,
                secondary = platinum,
                onSecondary = Color.Black,
                accent = silver,
                background = bg,
                onBackground = SonoraTextPrimary,
                surface = Color(0xFF13151B),
                onSurface = SonoraTextPrimary,
                surfaceVariant = Color(0xFF1C1F27),
                cardBackground = Color(0xFF14171F),
                cardBorder = Color(0xFF333844),
                progressBar = silver,
                seekbarPlayed = silver,
                seekbarRemaining = Color.White.copy(alpha = 0.18f),
                playbackButton = silver,
                onPlaybackButton = Color.Black,
                glowColor = silver.copy(alpha = 0.35f),
                gradientTop = if (gradientEnabled) Color(0xFF1D2028) else bg,
                gradientMiddle = if (gradientEnabled) Color(0xFF101218) else bg,
                gradientBottom = bg,
                textPrimary = SonoraTextPrimary,
                textSecondary = SonoraTextSecondary,
                textMuted = SonoraTextMuted,
                subtleTint = silver.copy(alpha = 0.15f),
                tertiary = Color(0xFF9AA3B5),
                onTertiary = Color.Black,
                secondarySurface = Color(0xFF181B22),
                elevatedSurface = Color(0xFF20242E),
                outline = Color(0xFF383E4C),
                isDarkPalette = true,
                gradientEnabled = gradientEnabled
            )
        }
    }

    /**
     * WCAG 2.1 contrast ratio between [foreground] and [background].
     */
    fun contrastRatio(foreground: Color, background: Color): Float {
        val l1 = foreground.luminance()
        val l2 = background.luminance()
        val lighter = max(l1, l2)
        val darker = min(l1, l2)
        return (lighter + 0.05f) / (darker + 0.05f)
    }

    private fun bestContrastingForeground(bg: Color): Color {
        val whiteContrast = contrastRatio(Color.White, bg)
        val darkContrast = contrastRatio(Color(0xFF11131A), bg)
        return if (darkContrast >= whiteContrast) Color(0xFF11131A) else Color.White
    }

    private fun ensureWcagTextContrast(text: Color, bg: Color, minRatio: Float): Color {
        if (contrastRatio(text, bg) >= minRatio) return text
        val whiteC = contrastRatio(Color.White, bg)
        val darkC = contrastRatio(Color(0xFF101218), bg)
        val target = if (whiteC >= darkC) Color.White else Color(0xFF101218)
        var out = text
        for (step in 1..6) {
            out = blend(text, target, step * 0.16f)
            if (contrastRatio(out, bg) >= minRatio) return out
        }
        return target
    }

    private fun ensureDarkBgAccent(color: Color, bg: Color): Color {
        var cur = color
        var step = 0
        while (contrastRatio(cur, bg) < 3.6f && step < 5) {
            cur = blend(cur, Color.White, 0.22f)
            step++
        }
        return cur
    }

    private fun ensureLightBgAccent(color: Color, bg: Color): Color {
        var cur = color
        var step = 0
        while (contrastRatio(cur, bg) < 3.8f && step < 6) {
            cur = blend(cur, Color(0xFF12141E), 0.20f)
            step++
        }
        return cur
    }

    private fun tuneChroma(
        color: Color,
        saturationScale: Float,
        minLum: Float,
        maxLum: Float
    ): Color {
        val (h, s, l) = rgbToHsl(color)
        val tunedS = (s * saturationScale).coerceIn(0.18f, 0.88f)
        val tunedL = l.coerceIn(minLum, maxLum)
        return hslToRgb(h, tunedS, tunedL)
    }

    private fun clampBackgroundSaturation(color: Color, maxSat: Float): Color {
        val (h, s, l) = rgbToHsl(color)
        return hslToRgb(h, s.coerceAtMost(maxSat), l.coerceIn(0.14f, 0.58f))
    }

    private fun shiftHue(color: Color, degrees: Float): Color {
        val (h, s, l) = rgbToHsl(color)
        return hslToRgb((h + degrees + 360f) % 360f, s.coerceIn(0.28f, 0.82f), l.coerceIn(0.35f, 0.72f))
    }

    private fun rgbToHsl(c: Color): Triple<Float, Float, Float> {
        val r = c.red.coerceIn(0f, 1f)
        val g = c.green.coerceIn(0f, 1f)
        val b = c.blue.coerceIn(0f, 1f)
        val max = max(r, max(g, b))
        val min = min(r, min(g, b))
        val l = (max + min) * 0.5f
        if (abs(max - min) < 1e-5f) {
            return Triple(0f, 0f, l)
        }
        val d = max - min
        val s = if (l > 0.5f) d / (2f - max - min) else d / (max + min)
        val h = when (max) {
            r -> ((g - b) / d + (if (g < b) 6f else 0f)) * 60f
            g -> ((b - r) / d + 2f) * 60f
            else -> ((r - g) / d + 4f) * 60f
        }
        return Triple(h.coerceIn(0f, 360f), s.coerceIn(0f, 1f), l.coerceIn(0f, 1f))
    }

    private fun hslToRgb(h: Float, s: Float, l: Float): Color {
        if (s <= 1e-4f) return Color(l, l, l, 1f)
        val c = (1f - abs(2f * l - 1f)) * s
        val x = c * (1f - abs((h / 60f) % 2f - 1f))
        val m = l - c * 0.5f
        val (r1, g1, b1) = when {
            h < 60f -> Triple(c, x, 0f)
            h < 120f -> Triple(x, c, 0f)
            h < 180f -> Triple(0f, c, x)
            h < 240f -> Triple(0f, x, c)
            h < 300f -> Triple(x, 0f, c)
            else -> Triple(c, 0f, x)
        }
        return Color(
            red = (r1 + m).coerceIn(0f, 1f),
            green = (g1 + m).coerceIn(0f, 1f),
            blue = (b1 + m).coerceIn(0f, 1f),
            alpha = 1f
        )
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
        val r = ratio.coerceIn(0f, 1f)
        val inverseRatio = 1f - r
        return Color(
            red = (color1.red * inverseRatio + color2.red * r).coerceIn(0f, 1f),
            green = (color1.green * inverseRatio + color2.green * r).coerceIn(0f, 1f),
            blue = (color1.blue * inverseRatio + color2.blue * r).coerceIn(0f, 1f),
            alpha = 1f
        )
    }
}
