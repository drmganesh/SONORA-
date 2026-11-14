package com.example.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.example.model.ThemeMode
import ui.theme.DynamicColorPalette
import ui.theme.EditorialColors
import ui.theme.LocalDynamicColorPalette
import ui.theme.LocalEditorialColors
import ui.theme.LocalReduceAnimations

private val SonoraAmoledColorScheme = darkColorScheme(
    primary = SonoraGold,
    onPrimary = SonoraOnGold,
    primaryContainer = SonoraGoldDark,
    onPrimaryContainer = SonoraGoldLight,
    secondary = SonoraCyan,
    onSecondary = Color.Black,
    secondaryContainer = SonoraCyanDark,
    onSecondaryContainer = SonoraCyanGlow,
    tertiary = SonoraAmber,
    onTertiary = Color.Black,
    background = SonoraBlack,
    onBackground = SonoraTextPrimary,
    surface = SonoraBlack,
    onSurface = SonoraTextPrimary,
    surfaceVariant = SonoraSurfaceDark,
    onSurfaceVariant = SonoraTextSecondary,
    surfaceContainer = SonoraSurfaceElevated,
    surfaceContainerHigh = SonoraSurfaceHighlight,
    outline = SonoraCardBorder
)

private val SonoraDarkColorScheme = darkColorScheme(
    primary = SonoraGold,
    onPrimary = SonoraOnGold,
    primaryContainer = SonoraGoldDark,
    onPrimaryContainer = SonoraGoldLight,
    secondary = SonoraCyan,
    onSecondary = Color.Black,
    secondaryContainer = SonoraCyanDark,
    onSecondaryContainer = SonoraCyanGlow,
    tertiary = SonoraAmber,
    onTertiary = Color.Black,
    background = SonoraObsidian,
    onBackground = SonoraTextPrimary,
    surface = SonoraSurfaceDark,
    onSurface = SonoraTextPrimary,
    surfaceVariant = SonoraSurfaceElevated,
    onSurfaceVariant = SonoraTextSecondary,
    surfaceContainer = SonoraSurfaceHighlight,
    outline = SonoraCardBorder
)

private val SonoraLightColorScheme = lightColorScheme(
    primary = SonoraGoldDark,
    onPrimary = Color.White,
    secondary = SonoraCyanDark,
    onSecondary = Color.White,
    background = Color(0xFFF7F8FA),
    surface = Color(0xFFFFFFFF),
    onBackground = Color(0xFF14161C),
    onSurface = Color(0xFF14161C),
    surfaceVariant = Color(0xFFEEF0F5),
    onSurfaceVariant = Color(0xFF484E5B)
)

private fun editorialToColorScheme(colors: EditorialColors): ColorScheme {
    return if (colors.isDark) {
        darkColorScheme(
            primary = colors.accentStrong,
            onPrimary = colors.onAccentStrong,
            primaryContainer = colors.accentPill,
            onPrimaryContainer = colors.onAccentPill,
            secondary = colors.accentStrong,
            onSecondary = colors.onAccentStrong,
            secondaryContainer = colors.secondarySurface,
            onSecondaryContainer = colors.textPrimary,
            tertiary = colors.tertiaryAccent,
            onTertiary = colors.onAccentStrong,
            background = colors.background,
            onBackground = colors.textPrimary,
            surface = colors.surface,
            onSurface = colors.textPrimary,
            surfaceVariant = colors.secondarySurface,
            onSurfaceVariant = colors.textSecondary,
            surfaceContainer = colors.surfaceElevated,
            surfaceContainerHigh = colors.surfaceTinted,
            outline = colors.cardBorder,
            outlineVariant = colors.cardBorder
        )
    } else {
        lightColorScheme(
            primary = colors.accentStrong,
            onPrimary = colors.onAccentStrong,
            primaryContainer = colors.accentPill,
            onPrimaryContainer = colors.onAccentPill,
            secondary = colors.accentStrong,
            onSecondary = colors.onAccentStrong,
            secondaryContainer = colors.secondarySurface,
            onSecondaryContainer = colors.textPrimary,
            tertiary = colors.tertiaryAccent,
            onTertiary = colors.onAccentStrong,
            background = colors.background,
            onBackground = colors.textPrimary,
            surface = colors.surface,
            onSurface = colors.textPrimary,
            surfaceVariant = colors.secondarySurface,
            onSurfaceVariant = colors.textSecondary,
            surfaceContainer = colors.surfaceElevated,
            surfaceContainerHigh = colors.surfaceTinted,
            outline = colors.cardBorder,
            outlineVariant = colors.cardBorder
        )
    }
}

@Composable
fun SonoraTheme(
    themeMode: ThemeMode = ThemeMode.AMOLED_BLACK,
    dynamicPalette: DynamicColorPalette? = null,
    editorialColors: EditorialColors? = null,
    reduceAnimations: Boolean = false,
    content: @Composable () -> Unit
) {
    val activeEditorial = editorialColors ?: EditorialColors.default()
    val view = LocalView.current

    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window
            if (window != null) {
                val controller = WindowCompat.getInsetsController(window, view)
                val lightIcons = if (editorialColors != null) !editorialColors.isDark else (themeMode == ThemeMode.LIGHT)
                controller.isAppearanceLightStatusBars = lightIcons
                controller.isAppearanceLightNavigationBars = lightIcons
            }
        }
    }

    if (editorialColors != null) {
        val m3Scheme = editorialToColorScheme(editorialColors)
        ui.theme.DynamicTheme(
            palette = dynamicPalette ?: DynamicColorPalette.default(),
            themeMode = if (editorialColors.isDark) ThemeMode.AMOLED_BLACK else ThemeMode.LIGHT,
            animationDurationMs = if (reduceAnimations) 0 else 600
        ) {
            CompositionLocalProvider(
                LocalEditorialColors provides editorialColors,
                LocalReduceAnimations provides reduceAnimations
            ) {
                MaterialTheme(
                    colorScheme = m3Scheme,
                    typography = Typography,
                    content = content
                )
            }
        }
        return
    }

    if (dynamicPalette != null) {
        ui.theme.DynamicTheme(
            palette = dynamicPalette,
            themeMode = themeMode,
            content = {
                CompositionLocalProvider(
                    LocalEditorialColors provides activeEditorial,
                    LocalReduceAnimations provides reduceAnimations
                ) {
                    content()
                }
            }
        )
        return
    }

    val context = LocalContext.current
    val colorScheme = when (themeMode) {
        ThemeMode.AMOLED_BLACK -> SonoraAmoledColorScheme
        ThemeMode.DARK -> SonoraDarkColorScheme
        ThemeMode.LIGHT -> SonoraLightColorScheme
        ThemeMode.DYNAMIC_YOU -> {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (isSystemInDarkTheme()) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
            } else {
                SonoraDarkColorScheme
            }
        }
    }

    CompositionLocalProvider(
        LocalDynamicColorPalette provides DynamicColorPalette.default(),
        LocalEditorialColors provides activeEditorial,
        LocalReduceAnimations provides reduceAnimations
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            content = content
        )
    }
}
