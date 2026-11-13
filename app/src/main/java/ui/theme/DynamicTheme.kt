package ui.theme

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import com.example.model.ThemeMode
import com.example.ui.theme.SonoraTextPrimary
import com.example.ui.theme.SonoraTextSecondary
import com.example.ui.theme.Typography

/**
 * CompositionLocal providing access to the current active DynamicColorPalette.
 */
val LocalDynamicColorPalette = staticCompositionLocalOf {
    DynamicColorPalette.default()
}

/**
 * Animated Dynamic Theme wrapper for SONORA.
 * Smoothly interpolates all interface colors when track or album art changes.
 */
@Composable
fun DynamicTheme(
    palette: DynamicColorPalette,
    themeMode: ThemeMode = ThemeMode.AMOLED_BLACK,
    animationDurationMs: Int = 500,
    content: @Composable () -> Unit
) {
    val animSpec = tween<Color>(durationMillis = animationDurationMs, easing = FastOutSlowInEasing)

    // Smoothly animate every color in the palette
    val animPrimary by animateColorAsState(targetValue = palette.primary, animationSpec = animSpec, label = "dynPrimary")
    val animOnPrimary by animateColorAsState(targetValue = palette.onPrimary, animationSpec = animSpec, label = "dynOnPrimary")
    val animSecondary by animateColorAsState(targetValue = palette.secondary, animationSpec = animSpec, label = "dynSecondary")
    val animOnSecondary by animateColorAsState(targetValue = palette.onSecondary, animationSpec = animSpec, label = "dynOnSecondary")
    val animAccent by animateColorAsState(targetValue = palette.accent, animationSpec = animSpec, label = "dynAccent")
    val animBackground by animateColorAsState(targetValue = palette.background, animationSpec = animSpec, label = "dynBackground")
    val animOnBackground by animateColorAsState(targetValue = palette.onBackground, animationSpec = animSpec, label = "dynOnBackground")
    val animSurface by animateColorAsState(targetValue = palette.surface, animationSpec = animSpec, label = "dynSurface")
    val animOnSurface by animateColorAsState(targetValue = palette.onSurface, animationSpec = animSpec, label = "dynOnSurface")
    val animSurfaceVariant by animateColorAsState(targetValue = palette.surfaceVariant, animationSpec = animSpec, label = "dynSurfaceVariant")
    val animCardBg by animateColorAsState(targetValue = palette.cardBackground, animationSpec = animSpec, label = "dynCardBg")
    val animCardBorder by animateColorAsState(targetValue = palette.cardBorder, animationSpec = animSpec, label = "dynCardBorder")
    val animProgressBar by animateColorAsState(targetValue = palette.progressBar, animationSpec = animSpec, label = "dynProgressBar")
    val animSeekbarPlayed by animateColorAsState(targetValue = palette.seekbarPlayed, animationSpec = animSpec, label = "dynSeekbarPlayed")
    val animSeekbarRemaining by animateColorAsState(targetValue = palette.seekbarRemaining, animationSpec = animSpec, label = "dynSeekbarRemaining")
    val animPlaybackButton by animateColorAsState(targetValue = palette.playbackButton, animationSpec = animSpec, label = "dynPlaybackButton")
    val animOnPlaybackButton by animateColorAsState(targetValue = palette.onPlaybackButton, animationSpec = animSpec, label = "dynOnPlaybackButton")
    val animGlowColor by animateColorAsState(targetValue = palette.glowColor, animationSpec = animSpec, label = "dynGlowColor")
    val animGradTop by animateColorAsState(targetValue = palette.gradientTop, animationSpec = animSpec, label = "dynGradTop")
    val animGradMid by animateColorAsState(targetValue = palette.gradientMiddle, animationSpec = animSpec, label = "dynGradMid")
    val animGradBot by animateColorAsState(targetValue = palette.gradientBottom, animationSpec = animSpec, label = "dynGradBot")
    val animSubtleTint by animateColorAsState(targetValue = palette.subtleTint, animationSpec = animSpec, label = "dynSubtleTint")

    val animatedPalette = DynamicColorPalette(
        primary = animPrimary,
        onPrimary = animOnPrimary,
        secondary = animSecondary,
        onSecondary = animOnSecondary,
        accent = animAccent,
        background = animBackground,
        onBackground = animOnBackground,
        surface = animSurface,
        onSurface = animOnSurface,
        surfaceVariant = animSurfaceVariant,
        cardBackground = animCardBg,
        cardBorder = animCardBorder,
        progressBar = animProgressBar,
        seekbarPlayed = animSeekbarPlayed,
        seekbarRemaining = animSeekbarRemaining,
        playbackButton = animPlaybackButton,
        onPlaybackButton = animOnPlaybackButton,
        glowColor = animGlowColor,
        gradientTop = animGradTop,
        gradientMiddle = animGradMid,
        gradientBottom = animGradBot,
        textPrimary = SonoraTextPrimary,
        textSecondary = SonoraTextSecondary,
        textMuted = palette.textMuted,
        subtleTint = animSubtleTint
    )

    // Build Material 3 ColorScheme reflecting the dynamic album colors
    val m3ColorScheme: ColorScheme = if (themeMode == ThemeMode.LIGHT) {
        lightColorScheme(
            primary = animPrimary,
            onPrimary = animOnPrimary,
            secondary = animSecondary,
            onSecondary = animOnSecondary,
            tertiary = animAccent,
            background = Color(0xFFF7F8FA),
            onBackground = Color(0xFF14161C),
            surface = Color.White,
            onSurface = Color(0xFF14161C),
            surfaceVariant = Color(0xFFEEF0F5),
            onSurfaceVariant = Color(0xFF484E5B),
            outline = animCardBorder
        )
    } else {
        darkColorScheme(
            primary = animPrimary,
            onPrimary = animOnPrimary,
            primaryContainer = animCardBg,
            onPrimaryContainer = animSecondary,
            secondary = animSecondary,
            onSecondary = animOnSecondary,
            tertiary = animAccent,
            background = animBackground,
            onBackground = animOnBackground,
            surface = animSurface,
            onSurface = animOnSurface,
            surfaceVariant = animSurfaceVariant,
            onSurfaceVariant = SonoraTextSecondary,
            surfaceContainer = animCardBg,
            outline = animCardBorder
        )
    }

    CompositionLocalProvider(LocalDynamicColorPalette provides animatedPalette) {
        MaterialTheme(
            colorScheme = m3ColorScheme,
            typography = Typography,
            content = content
        )
    }
}
