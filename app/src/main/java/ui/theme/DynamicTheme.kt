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
import com.example.ui.theme.Typography

/**
 * CompositionLocal providing access to the current active DynamicColorPalette.
 */
val LocalDynamicColorPalette = staticCompositionLocalOf {
    DynamicColorPalette.default()
}

/**
 * Animated Dynamic Theme wrapper for SONORA.
 * Smoothly interpolates all interface colors (600ms default) when track or album art changes.
 */
@Composable
fun DynamicTheme(
    palette: DynamicColorPalette,
    themeMode: ThemeMode = ThemeMode.AMOLED_BLACK,
    animationDurationMs: Int = 600,
    content: @Composable () -> Unit
) {
    val animSpec = tween<Color>(durationMillis = animationDurationMs, easing = FastOutSlowInEasing)

    val animPrimary by animateColorAsState(targetValue = palette.primary, animationSpec = animSpec, label = "dynPrimary")
    val animOnPrimary by animateColorAsState(targetValue = palette.onPrimary, animationSpec = animSpec, label = "dynOnPrimary")
    val animSecondary by animateColorAsState(targetValue = palette.secondary, animationSpec = animSpec, label = "dynSecondary")
    val animOnSecondary by animateColorAsState(targetValue = palette.onSecondary, animationSpec = animSpec, label = "dynOnSecondary")
    val animTertiary by animateColorAsState(targetValue = palette.tertiary, animationSpec = animSpec, label = "dynTertiary")
    val animOnTertiary by animateColorAsState(targetValue = palette.onTertiary, animationSpec = animSpec, label = "dynOnTertiary")
    val animAccent by animateColorAsState(targetValue = palette.accent, animationSpec = animSpec, label = "dynAccent")
    val animBackground by animateColorAsState(targetValue = palette.background, animationSpec = animSpec, label = "dynBackground")
    val animOnBackground by animateColorAsState(targetValue = palette.onBackground, animationSpec = animSpec, label = "dynOnBackground")
    val animSurface by animateColorAsState(targetValue = palette.surface, animationSpec = animSpec, label = "dynSurface")
    val animOnSurface by animateColorAsState(targetValue = palette.onSurface, animationSpec = animSpec, label = "dynOnSurface")
    val animSurfaceVariant by animateColorAsState(targetValue = palette.surfaceVariant, animationSpec = animSpec, label = "dynSurfaceVariant")
    val animSecondarySurface by animateColorAsState(targetValue = palette.secondarySurface, animationSpec = animSpec, label = "dynSecSurface")
    val animElevatedSurface by animateColorAsState(targetValue = palette.elevatedSurface, animationSpec = animSpec, label = "dynElevSurface")
    val animCardBg by animateColorAsState(targetValue = palette.cardBackground, animationSpec = animSpec, label = "dynCardBg")
    val animCardBorder by animateColorAsState(targetValue = palette.cardBorder, animationSpec = animSpec, label = "dynCardBorder")
    val animOutline by animateColorAsState(targetValue = palette.outline, animationSpec = animSpec, label = "dynOutline")
    val animProgressBar by animateColorAsState(targetValue = palette.progressBar, animationSpec = animSpec, label = "dynProgressBar")
    val animSeekbarPlayed by animateColorAsState(targetValue = palette.seekbarPlayed, animationSpec = animSpec, label = "dynSeekbarPlayed")
    val animSeekbarRemaining by animateColorAsState(targetValue = palette.seekbarRemaining, animationSpec = animSpec, label = "dynSeekbarRemaining")
    val animPlaybackButton by animateColorAsState(targetValue = palette.playbackButton, animationSpec = animSpec, label = "dynPlaybackButton")
    val animOnPlaybackButton by animateColorAsState(targetValue = palette.onPlaybackButton, animationSpec = animSpec, label = "dynOnPlaybackButton")
    val animGlowColor by animateColorAsState(targetValue = palette.glowColor, animationSpec = animSpec, label = "dynGlowColor")
    val animGradTop by animateColorAsState(targetValue = palette.gradientTop, animationSpec = animSpec, label = "dynGradTop")
    val animGradMid by animateColorAsState(targetValue = palette.gradientMiddle, animationSpec = animSpec, label = "dynGradMid")
    val animGradBot by animateColorAsState(targetValue = palette.gradientBottom, animationSpec = animSpec, label = "dynGradBot")
    val animTextPrimary by animateColorAsState(targetValue = palette.textPrimary, animationSpec = animSpec, label = "dynTextPrimary")
    val animTextSecondary by animateColorAsState(targetValue = palette.textSecondary, animationSpec = animSpec, label = "dynTextSecondary")
    val animTextMuted by animateColorAsState(targetValue = palette.textMuted, animationSpec = animSpec, label = "dynTextMuted")
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
        textPrimary = animTextPrimary,
        textSecondary = animTextSecondary,
        textMuted = animTextMuted,
        subtleTint = animSubtleTint,
        tertiary = animTertiary,
        onTertiary = animOnTertiary,
        secondarySurface = animSecondarySurface,
        elevatedSurface = animElevatedSurface,
        outline = animOutline,
        isDarkPalette = palette.isDarkPalette,
        gradientEnabled = palette.gradientEnabled
    )

    val useLight = !palette.isDarkPalette || themeMode == ThemeMode.LIGHT
    val m3ColorScheme: ColorScheme = if (useLight) {
        lightColorScheme(
            primary = animPrimary,
            onPrimary = animOnPrimary,
            primaryContainer = animSecondarySurface,
            onPrimaryContainer = animTextPrimary,
            secondary = animSecondary,
            onSecondary = animOnSecondary,
            tertiary = animTertiary,
            onTertiary = animOnTertiary,
            background = animBackground,
            onBackground = animOnBackground,
            surface = animSurface,
            onSurface = animOnSurface,
            surfaceVariant = animSurfaceVariant,
            onSurfaceVariant = animTextSecondary,
            surfaceContainer = animElevatedSurface,
            outline = animOutline
        )
    } else {
        darkColorScheme(
            primary = animPrimary,
            onPrimary = animOnPrimary,
            primaryContainer = animCardBg,
            onPrimaryContainer = animSecondary,
            secondary = animSecondary,
            onSecondary = animOnSecondary,
            tertiary = animTertiary,
            onTertiary = animOnTertiary,
            background = animBackground,
            onBackground = animOnBackground,
            surface = animSurface,
            onSurface = animOnSurface,
            surfaceVariant = animSurfaceVariant,
            onSurfaceVariant = animTextSecondary,
            surfaceContainer = animElevatedSurface,
            outline = animOutline
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
