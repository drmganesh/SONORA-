package ui.theme

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance

/**
 * Unified 9-theme system for the entire SONORA AESTHETIC UI.
 * Kept named [HomeThemeMode] for seamless preference compatibility across the app,
 * with [SonoraThemeStyle] provided as a semantic typealias.
 */
enum class HomeThemeMode(
    val id: String,
    val displayName: String,
    val subtitle: String,
    val previewColorArgb: Long,
    val previewAccentArgb: Long,
    val isDarkDefault: Boolean
) {
    FIXED_LAVENDER(
        id = "fixed_lavender",
        displayName = "Lavender Editorial",
        subtitle = "Soft pastel lavender & crisp charcoal ink",
        previewColorArgb = 0xFFF3F1FA,
        previewAccentArgb = 0xFFDCE6FF,
        isDarkDefault = false
    ),
    AMOLED_MIDNIGHT(
        id = "amoled_midnight",
        displayName = "AMOLED Midnight",
        subtitle = "Deep midnight navy & luminous slate surfaces",
        previewColorArgb = 0xFF040711,
        previewAccentArgb = 0xFF6B93FF,
        isDarkDefault = true
    ),
    AMOLED_DARK(
        id = "amoled_dark",
        displayName = "Pure Black",
        subtitle = "True OLED pitch black with high-contrast accents",
        previewColorArgb = 0xFF000000,
        previewAccentArgb = 0xFFE5C158,
        isDarkDefault = true
    ),
    WARM_CREAM(
        id = "warm_cream",
        displayName = "Warm Cream",
        subtitle = "Editorial paper parchment & espresso typography",
        previewColorArgb = 0xFFFAF6EE,
        previewAccentArgb = 0xFFEADBC4,
        isDarkDefault = false
    ),
    SOFT_ROSE(
        id = "soft_rose",
        displayName = "Soft Rose",
        subtitle = "Warm blush petals with plum editorial ink",
        previewColorArgb = 0xFFFDF2F4,
        previewAccentArgb = 0xFFF9D6DE,
        isDarkDefault = false
    ),
    OCEAN_BLUE(
        id = "ocean_blue",
        displayName = "Ocean Blue",
        subtitle = "Airy coastal sky blue & deep marine contrast",
        previewColorArgb = 0xFFEFF6FC,
        previewAccentArgb = 0xFFCFE6FA,
        isDarkDefault = false
    ),
    MINT_GREEN(
        id = "mint_green",
        displayName = "Mint Green",
        subtitle = "Calm botanical sage & forest ink clarity",
        previewColorArgb = 0xFFEFFAF4,
        previewAccentArgb = 0xFFC8EFE0,
        isDarkDefault = false
    ),
    DYNAMIC_ALBUM_ART(
        id = "dynamic_album_art",
        displayName = "Dynamic Album Art",
        subtitle = "Pastels & accents adapt to playing artwork",
        previewColorArgb = 0xFFF7F5FC,
        previewAccentArgb = 0xFFB8A9F5,
        isDarkDefault = false
    ),
    CUSTOM_ACCENT(
        id = "custom_accent",
        displayName = "Custom Accent Theme",
        subtitle = "Editorial canvas tinted with your signature hue",
        previewColorArgb = 0xFFF5F4FA,
        previewAccentArgb = 0xFFD99890,
        isDarkDefault = false
    );

    companion object {
        val DEFAULT = FIXED_LAVENDER

        fun fromId(id: String?): HomeThemeMode {
            if (id == null) return DEFAULT
            if (id.equals("light_minimal", ignoreCase = true)) return WARM_CREAM
            return entries.firstOrNull { it.id.equals(id, ignoreCase = true) } ?: DEFAULT
        }
    }
}

typealias SonoraThemeStyle = HomeThemeMode

/**
 * Curated accent color swatches for user customization across all themes.
 */
object EditorialAccentSwatches {
    val presets: List<Pair<String, Long>> = listOf(
        "Periwinkle" to 0xFFD9E3FF,
        "Studio Gold" to 0xFFFFBF36,
        "Dusty Rose" to 0xFFD99890,
        "Electric Cyan" to 0xFF38D4FF,
        "Soft Violet" to 0xFFC5B3FF,
        "Sage Mint" to 0xFF8CD9B6,
        "Terracotta" to 0xFFE07A5F,
        "Royal Indigo" to 0xFF5C6BC0
    )
}

@Immutable
data class EditorialColors(
    val isDark: Boolean,
    val background: Color,
    val surface: Color,
    val surfaceElevated: Color,
    val surfaceTinted: Color,
    val accentPill: Color,
    val onAccentPill: Color,
    val accentStrong: Color,
    val onAccentStrong: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textMuted: Color,
    val cardBorder: Color,
    val floatingNavBackground: Color,
    val floatingNavIndicator: Color,
    val floatingNavActiveTint: Color,
    val floatingNavInactiveTint: Color,
    val miniPlayerBackground: Color,
    val shadowColor: Color
) {
    companion object {
        fun default(): EditorialColors = editorialColorsFor(
            mode = HomeThemeMode.DEFAULT,
            customAccentArgb = null,
            dynamicPalette = DynamicColorPalette.default()
        )
    }
}

val LocalEditorialColors = staticCompositionLocalOf { EditorialColors.default() }
val LocalReduceAnimations = staticCompositionLocalOf { false }

private fun blendColors(c1: Color, c2: Color, ratio: Float): Color {
    val r = ratio.coerceIn(0f, 1f)
    val inv = 1f - r
    return Color(
        red = c1.red * inv + c2.red * r,
        green = c1.green * inv + c2.green * r,
        blue = c1.blue * inv + c2.blue * r,
        alpha = 1f
    )
}

private fun lightEditorialTheme(
    bg: Color,
    surface: Color,
    surfaceElev: Color,
    surfaceTintBase: Color,
    defaultPill: Color,
    defaultStrong: Color,
    textPrimary: Color,
    textSecondary: Color,
    textMuted: Color,
    borderColor: Color,
    shadowBase: Color,
    customColor: Color?
): EditorialColors {
    val pill = customColor?.let {
        if (it.luminance() < 0.35f) blendColors(Color.White, it, 0.28f) else it
    } ?: defaultPill
    val strong = customColor?.let {
        if (it.luminance() > 0.60f) blendColors(it, textPrimary, 0.58f) else it
    } ?: defaultStrong

    return EditorialColors(
        isDark = false,
        background = bg,
        surface = surface,
        surfaceElevated = surfaceElev,
        surfaceTinted = blendColors(surfaceTintBase, pill, 0.22f),
        accentPill = pill,
        onAccentPill = if (pill.luminance() > 0.45f) textPrimary else Color.White,
        accentStrong = strong,
        onAccentStrong = if (strong.luminance() > 0.55f) textPrimary else Color.White,
        textPrimary = textPrimary,
        textSecondary = textSecondary,
        textMuted = textMuted,
        cardBorder = borderColor,
        floatingNavBackground = surface,
        floatingNavIndicator = pill,
        floatingNavActiveTint = textPrimary,
        floatingNavInactiveTint = textSecondary,
        miniPlayerBackground = surface,
        shadowColor = shadowBase.copy(alpha = 0.20f)
    )
}

/**
 * Pure non-composable theme palette resolver for all 9 SONORA AESTHETIC UI themes.
 */
fun editorialColorsFor(
    mode: HomeThemeMode,
    customAccentArgb: Long?,
    dynamicPalette: DynamicColorPalette
): EditorialColors {
    val customColor = customAccentArgb?.takeIf { it != -1L }?.let { Color(it) }

    return when (mode) {
        HomeThemeMode.FIXED_LAVENDER -> lightEditorialTheme(
            bg = Color(0xFFF3F1FA),
            surface = Color(0xFFFFFFFF),
            surfaceElev = Color(0xFFFAF9FE),
            surfaceTintBase = Color(0xFFECEAF6),
            defaultPill = Color(0xFFDCE6FF),
            defaultStrong = Color(0xFF2B4D76),
            textPrimary = Color(0xFF15151A),
            textSecondary = Color(0xFF535361),
            textMuted = Color(0xFF818091),
            borderColor = Color(0xFFE2DFF0),
            shadowBase = Color(0xFF7B74A8),
            customColor = customColor
        )

        HomeThemeMode.WARM_CREAM -> lightEditorialTheme(
            bg = Color(0xFFFAF6EE),
            surface = Color(0xFFFFFDF9),
            surfaceElev = Color(0xFFF5EFE3),
            surfaceTintBase = Color(0xFFF0E8D8),
            defaultPill = Color(0xFFEADBC4),
            defaultStrong = Color(0xFF6B4C2E),
            textPrimary = Color(0xFF1F1A14),
            textSecondary = Color(0xFF5E5346),
            textMuted = Color(0xFF8B7F70),
            borderColor = Color(0xFFE7DEC9),
            shadowBase = Color(0xFF8D7858),
            customColor = customColor
        )

        HomeThemeMode.SOFT_ROSE -> lightEditorialTheme(
            bg = Color(0xFFFDF2F4),
            surface = Color(0xFFFFFFFF),
            surfaceElev = Color(0xFFFCF6F7),
            surfaceTintBase = Color(0xFFF8E6EA),
            defaultPill = Color(0xFFF9D6DE),
            defaultStrong = Color(0xFF8A2E4B),
            textPrimary = Color(0xFF221419),
            textSecondary = Color(0xFF664B54),
            textMuted = Color(0xFF947781),
            borderColor = Color(0xFFF0D7DE),
            shadowBase = Color(0xFFB56E82),
            customColor = customColor
        )

        HomeThemeMode.OCEAN_BLUE -> lightEditorialTheme(
            bg = Color(0xFFEFF6FC),
            surface = Color(0xFFFFFFFF),
            surfaceElev = Color(0xFFF7FBFF),
            surfaceTintBase = Color(0xFFE3EEF8),
            defaultPill = Color(0xFFCFE6FA),
            defaultStrong = Color(0xFF1A5276),
            textPrimary = Color(0xFF111B24),
            textSecondary = Color(0xFF475C6E),
            textMuted = Color(0xFF788FA3),
            borderColor = Color(0xFFD6E6F4),
            shadowBase = Color(0xFF5386B5),
            customColor = customColor
        )

        HomeThemeMode.MINT_GREEN -> lightEditorialTheme(
            bg = Color(0xFFEFFAF4),
            surface = Color(0xFFFFFFFF),
            surfaceElev = Color(0xFFF7FCF9),
            surfaceTintBase = Color(0xFFE1F3E9),
            defaultPill = Color(0xFFC8EFE0),
            defaultStrong = Color(0xFF1D6249),
            textPrimary = Color(0xFF111F19),
            textSecondary = Color(0xFF476357),
            textMuted = Color(0xFF769286),
            borderColor = Color(0xFFD0EBDD),
            shadowBase = Color(0xFF4F9D7E),
            customColor = customColor
        )

        HomeThemeMode.DYNAMIC_ALBUM_ART -> {
            val artAccent = customColor ?: dynamicPalette.primary
            val pastelPill = blendColors(Color.White, artAccent, 0.28f)
            val pastelBg = blendColors(Color(0xFFF7F5FC), artAccent, 0.09f)
            val strong = blendColors(artAccent, Color(0xFF181C2E), 0.44f)
            EditorialColors(
                isDark = false,
                background = pastelBg,
                surface = Color(0xFFFFFFFF),
                surfaceElevated = blendColors(Color.White, artAccent, 0.05f),
                surfaceTinted = blendColors(Color(0xFFF1EFF9), artAccent, 0.14f),
                accentPill = pastelPill,
                onAccentPill = if (pastelPill.luminance() > 0.45f) Color(0xFF15151C) else Color.White,
                accentStrong = strong,
                onAccentStrong = if (strong.luminance() > 0.55f) Color(0xFF15151C) else Color.White,
                textPrimary = Color(0xFF15151C),
                textSecondary = Color(0xFF545364),
                textMuted = Color(0xFF828095),
                cardBorder = artAccent.copy(alpha = 0.22f),
                floatingNavBackground = Color(0xFFFFFFFF),
                floatingNavIndicator = pastelPill,
                floatingNavActiveTint = Color(0xFF15151C),
                floatingNavInactiveTint = Color(0xFF4A4B59),
                miniPlayerBackground = Color(0xFFFFFFFF),
                shadowColor = artAccent.copy(alpha = 0.26f)
            )
        }

        HomeThemeMode.CUSTOM_ACCENT -> {
            val seed = customColor ?: Color(EditorialAccentSwatches.presets[2].second)
            val bg = blendColors(Color(0xFFF7F6FC), seed, 0.11f)
            val surfaceTint = blendColors(Color(0xFFF1F0F8), seed, 0.22f)
            val pill = blendColors(Color.White, seed, 0.34f)
            val strong = if (seed.luminance() > 0.55f) blendColors(seed, Color(0xFF181924), 0.52f) else seed
            EditorialColors(
                isDark = false,
                background = bg,
                surface = Color(0xFFFFFFFF),
                surfaceElevated = blendColors(Color.White, seed, 0.05f),
                surfaceTinted = surfaceTint,
                accentPill = pill,
                onAccentPill = if (pill.luminance() > 0.45f) Color(0xFF15151C) else Color.White,
                accentStrong = strong,
                onAccentStrong = if (strong.luminance() > 0.55f) Color(0xFF15151C) else Color.White,
                textPrimary = Color(0xFF16161D),
                textSecondary = Color(0xFF525360),
                textMuted = Color(0xFF808191),
                cardBorder = seed.copy(alpha = 0.26f),
                floatingNavBackground = Color(0xFFFFFFFF),
                floatingNavIndicator = pill,
                floatingNavActiveTint = Color(0xFF15151C),
                floatingNavInactiveTint = Color(0xFF494B57),
                miniPlayerBackground = Color(0xFFFFFFFF),
                shadowColor = seed.copy(alpha = 0.24f)
            )
        }

        HomeThemeMode.AMOLED_MIDNIGHT -> {
            val accent = customColor ?: Color(0xFF6B93FF)
            val strong = blendColors(accent, Color.White, 0.18f)
            EditorialColors(
                isDark = true,
                background = Color(0xFF040711),
                surface = Color(0xFF0D1326),
                surfaceElevated = Color(0xFF141C36),
                surfaceTinted = Color(0xFF111830),
                accentPill = accent.copy(alpha = 0.24f),
                onAccentPill = strong,
                accentStrong = accent,
                onAccentStrong = if (accent.luminance() > 0.45f) Color(0xFF060913) else Color.White,
                textPrimary = Color(0xFFF3F6FF),
                textSecondary = Color(0xFFA8B3D1),
                textMuted = Color(0xFF6C7899),
                cardBorder = Color(0xFF1E2949),
                floatingNavBackground = Color(0xFF0E152B),
                floatingNavIndicator = accent.copy(alpha = 0.24f),
                floatingNavActiveTint = strong,
                floatingNavInactiveTint = Color(0xFF8B98BD),
                miniPlayerBackground = Color(0xFF0E152B),
                shadowColor = accent.copy(alpha = 0.28f)
            )
        }

        HomeThemeMode.AMOLED_DARK -> {
            val accent = customColor ?: dynamicPalette.primary
            val strong = blendColors(accent, Color.White, 0.15f)
            EditorialColors(
                isDark = true,
                background = Color(0xFF000000),
                surface = Color(0xFF111319),
                surfaceElevated = Color(0xFF181B24),
                surfaceTinted = Color(0xFF161A23),
                accentPill = accent.copy(alpha = 0.22f),
                onAccentPill = strong,
                accentStrong = accent,
                onAccentStrong = if (accent.luminance() > 0.45f) Color(0xFF0B0C10) else Color.White,
                textPrimary = Color(0xFFF5F6FA),
                textSecondary = Color(0xFFA9B0C0),
                textMuted = Color(0xFF6E7588),
                cardBorder = Color(0xFF272C3A),
                floatingNavBackground = Color(0xFF141720),
                floatingNavIndicator = accent.copy(alpha = 0.22f),
                floatingNavActiveTint = strong,
                floatingNavInactiveTint = Color(0xFF939AB0),
                miniPlayerBackground = Color(0xFF13161F),
                shadowColor = accent.copy(alpha = 0.25f)
            )
        }
    }
}

@Composable
fun rememberEditorialColors(
    mode: HomeThemeMode,
    customAccentArgb: Long?,
    dynamicPalette: DynamicColorPalette,
    reduceAnimations: Boolean = false
): EditorialColors {
    val target = editorialColorsFor(mode, customAccentArgb, dynamicPalette)

    if (reduceAnimations) {
        return target
    }

    val spec = tween<Color>(durationMillis = 360, easing = FastOutSlowInEasing)
    val bg by animateColorAsState(target.background, spec, label = "edBg")
    val surface by animateColorAsState(target.surface, spec, label = "edSurface")
    val surfaceElevated by animateColorAsState(target.surfaceElevated, spec, label = "edSurfaceElev")
    val surfaceTinted by animateColorAsState(target.surfaceTinted, spec, label = "edSurfaceTint")
    val accentPill by animateColorAsState(target.accentPill, spec, label = "edAccentPill")
    val onAccentPill by animateColorAsState(target.onAccentPill, spec, label = "edOnAccentPill")
    val accentStrong by animateColorAsState(target.accentStrong, spec, label = "edAccentStrong")
    val onAccentStrong by animateColorAsState(target.onAccentStrong, spec, label = "edOnAccentStrong")
    val textPrimary by animateColorAsState(target.textPrimary, spec, label = "edTextPrim")
    val textSecondary by animateColorAsState(target.textSecondary, spec, label = "edTextSec")
    val textMuted by animateColorAsState(target.textMuted, spec, label = "edTextMuted")
    val navBg by animateColorAsState(target.floatingNavBackground, spec, label = "edNavBg")
    val navInd by animateColorAsState(target.floatingNavIndicator, spec, label = "edNavInd")

    return target.copy(
        background = bg,
        surface = surface,
        surfaceElevated = surfaceElevated,
        surfaceTinted = surfaceTinted,
        accentPill = accentPill,
        onAccentPill = onAccentPill,
        accentStrong = accentStrong,
        onAccentStrong = onAccentStrong,
        textPrimary = textPrimary,
        textSecondary = textSecondary,
        textMuted = textMuted,
        floatingNavBackground = navBg,
        floatingNavIndicator = navInd
    )
}
