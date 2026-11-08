package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.example.model.ThemeMode

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

@Composable
fun SonoraTheme(
    themeMode: ThemeMode = ThemeMode.AMOLED_BLACK,
    content: @Composable () -> Unit
) {
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

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
