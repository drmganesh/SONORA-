package ui.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ColorLens
import androidx.compose.material.icons.rounded.Contrast
import androidx.compose.material.icons.rounded.Gradient
import androidx.compose.material.icons.rounded.InvertColors
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.DynamicThemeMode
import com.example.ui.components.ArtworkAlbumView
import com.example.ui.theme.OutfitFontFamily
import com.example.ui.theme.PlusJakartaFontFamily
import com.example.viewmodel.SonoraViewModel
import ui.theme.EditorialColors
import ui.theme.LocalDynamicColorPalette
import ui.theme.LocalEditorialColors

@Composable
fun DynamicThemeSettingsScreen(
    viewModel: SonoraViewModel,
    onBack: () -> Unit,
    onOpenPlayer: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val colors = LocalEditorialColors.current
    val dynamicEnabled by viewModel.dynamicColorsEnabled.collectAsState()
    val activeMode by viewModel.dynamicThemeMode.collectAsState()
    val currentSong by viewModel.currentSong.collectAsState()
    val playbackInfo by viewModel.playbackInfo.collectAsState()
    val palette = LocalDynamicColorPalette.current

    BackHandler(onBack = onBack)

    val modes = listOf(
        DynamicThemeOption(
            mode = DynamicThemeMode.FULL_DYNAMIC,
            title = "Full Dynamic Theme",
            subtitle = "Adapt pastel surfaces, accents, and Now Playing gradients",
            icon = Icons.Rounded.Palette
        ),
        DynamicThemeOption(
            mode = DynamicThemeMode.AMOLED_DYNAMIC,
            title = "AMOLED Dynamic",
            subtitle = "Keep backgrounds pitch black while tinting accents",
            icon = Icons.Rounded.Contrast
        ),
        DynamicThemeOption(
            mode = DynamicThemeMode.SUBTLE_DYNAMIC,
            title = "Subtle Dynamic Theme",
            subtitle = "Use softly muted album artwork tones",
            icon = Icons.Rounded.InvertColors
        ),
        DynamicThemeOption(
            mode = DynamicThemeMode.BACKGROUND_ONLY,
            title = "Dynamic Background Only",
            subtitle = "Adapt ambient glow and subtle canvas washes",
            icon = Icons.Rounded.Gradient
        ),
        DynamicThemeOption(
            mode = DynamicThemeMode.STATIC_THEME,
            title = "Static Theme",
            subtitle = "Lock interface to your chosen editorial theme",
            icon = Icons.Rounded.ColorLens
        )
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background)
            .statusBarsPadding()
            .testTag("dynamic_theme_settings_screen"),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 760.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Surface(
                        shape = CircleShape,
                        color = colors.surface,
                        shadowElevation = 2.dp,
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .clickable { onBack() }
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back", tint = colors.textPrimary)
                        }
                    }
                    Spacer(modifier = Modifier.width(14.dp))
                    Column {
                        Text(
                            text = "Dynamic Album Colors",
                            fontFamily = OutfitFontFamily,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 22.sp,
                            color = colors.textPrimary
                        )
                        Text(
                            text = "Real-time album art palette extraction",
                            fontFamily = PlusJakartaFontFamily,
                            fontSize = 12.sp,
                            color = colors.accentStrong
                        )
                    }
                }

                IconButton(
                    onClick = { viewModel.resetDynamicThemeToDefault() },
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(Icons.Rounded.RestartAlt, contentDescription = "Reset to Default", tint = colors.textSecondary)
                }
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp).let {
                    PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 100.dp)
                },
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Live Preview Card
                item {
                    Surface(
                        shape = RoundedCornerShape(24.dp),
                        color = colors.surface,
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(0.75.dp, colors.cardBorder, RoundedCornerShape(24.dp))
                            .clip(RoundedCornerShape(24.dp))
                            .clickable { onOpenPlayer() }
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(18.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Rounded.AutoAwesome, contentDescription = null, tint = colors.accentStrong, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "LIVE ARTWORK PALETTE",
                                        fontFamily = PlusJakartaFontFamily,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.sp,
                                        letterSpacing = 1.sp,
                                        color = colors.accentStrong
                                    )
                                }
                                Text("Tap to preview", fontFamily = PlusJakartaFontFamily, fontSize = 11.sp, color = colors.textMuted)
                            }

                            Spacer(modifier = Modifier.height(14.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                ArtworkAlbumView(
                                    song = currentSong,
                                    modifier = Modifier.size(56.dp),
                                    cornerRadius = 14.dp,
                                    isPlaying = playbackInfo.isPlaying
                                )
                                Spacer(modifier = Modifier.width(14.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = currentSong?.title ?: "No Track Playing",
                                        fontFamily = OutfitFontFamily,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 16.sp,
                                        color = colors.textPrimary,
                                        maxLines = 1
                                    )
                                    Text(
                                        text = currentSong?.artist ?: "Play any local track",
                                        fontFamily = PlusJakartaFontFamily,
                                        fontSize = 13.sp,
                                        color = colors.textSecondary,
                                        maxLines = 1
                                    )
                                }
                                Surface(
                                    onClick = onOpenPlayer,
                                    shape = CircleShape,
                                    color = colors.accentStrong,
                                    modifier = Modifier.size(44.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(Icons.Rounded.PlayArrow, contentDescription = "Open Player", tint = colors.onAccentStrong)
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                PaletteSwatch(label = "Primary", color = palette.primary, colors = colors)
                                PaletteSwatch(label = "Secondary", color = palette.secondary, colors = colors)
                                PaletteSwatch(label = "Accent", color = palette.accent, colors = colors)
                                PaletteSwatch(label = "Surface", color = palette.surface, colors = colors)
                                PaletteSwatch(label = "Glow", color = palette.glowColor, colors = colors)
                            }
                        }
                    }
                }

                // Master Switch
                item {
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = colors.surface,
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(0.75.dp, colors.cardBorder, RoundedCornerShape(20.dp))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Dynamic Album Colors", fontFamily = OutfitFontFamily, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = colors.textPrimary)
                                Text("Extract palette colors from playing artwork", fontFamily = PlusJakartaFontFamily, fontSize = 12.sp, color = colors.textSecondary)
                            }
                            Switch(
                                checked = dynamicEnabled,
                                onCheckedChange = { viewModel.setDynamicColorsEnabled(it) },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = colors.onAccentStrong,
                                    checkedTrackColor = colors.accentStrong
                                )
                            )
                        }
                    }
                }

                item {
                    Text(
                        text = "ADAPTATION MODES",
                        fontFamily = PlusJakartaFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        letterSpacing = 1.1.sp,
                        color = colors.textMuted,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }

                items(modes, key = { it.mode.name }) { option ->
                    val isSelected = dynamicEnabled && activeMode == option.mode
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = if (isSelected) colors.accentPill.copy(alpha = 0.45f) else colors.surface,
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(
                                width = if (isSelected) 1.5.dp else 0.75.dp,
                                color = if (isSelected) colors.accentStrong else colors.cardBorder,
                                shape = RoundedCornerShape(20.dp)
                            )
                            .clip(RoundedCornerShape(20.dp))
                            .clickable {
                                if (!dynamicEnabled) viewModel.setDynamicColorsEnabled(true)
                                viewModel.setDynamicThemeMode(option.mode)
                            }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .background(if (isSelected) colors.accentStrong else colors.surfaceTinted, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = option.icon,
                                    contentDescription = null,
                                    tint = if (isSelected) colors.onAccentStrong else colors.textPrimary,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(14.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(option.title, fontFamily = OutfitFontFamily, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = colors.textPrimary)
                                Text(option.subtitle, fontFamily = PlusJakartaFontFamily, fontSize = 12.sp, color = colors.textSecondary)
                            }
                            if (isSelected) {
                                Box(
                                    modifier = Modifier
                                        .size(26.dp)
                                        .background(colors.accentStrong, CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Rounded.Check, contentDescription = "Selected", tint = colors.onAccentStrong, modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private data class DynamicThemeOption(
    val mode: DynamicThemeMode,
    val title: String,
    val subtitle: String,
    val icon: ImageVector
)

@Composable
private fun PaletteSwatch(label: String, color: Color, colors: EditorialColors) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .shadow(3.dp, CircleShape)
                .background(color, CircleShape)
                .border(1.dp, colors.cardBorder, CircleShape)
        )
        Text(text = label, fontFamily = PlusJakartaFontFamily, fontSize = 10.sp, color = colors.textMuted)
    }
}
