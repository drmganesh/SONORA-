package ui.lyrics

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.OutfitFontFamily
import com.example.ui.theme.PlusJakartaFontFamily
import domain.lyrics.LyricsAppearanceConfig
import domain.lyrics.LyricsFontFamilyOption
import domain.lyrics.LyricsPreset
import domain.lyrics.LyricsScrollSpeed
import ui.theme.EditorialColors

private val LyricsHighlightSwatches = listOf(
    0xFFFFD54FL, // Warm Gold
    0xFFFF6B9DL, // Rose Neon
    0xFF4DD0E1L, // Cyan Glow
    0xFFA5D6A7L, // Mint Pastel
    0xFFCE93D8L, // Lavender
    0xFFFFAB91L, // Coral Amber
    0xFFFFFFFFL  // Pure White
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LyricsAppearanceSheet(
    config: LyricsAppearanceConfig,
    dynamicAccentColor: Color,
    colors: EditorialColors,
    onApplyPreset: (LyricsPreset) -> Unit,
    onUpdateConfig: ((LyricsAppearanceConfig) -> LyricsAppearanceConfig) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val effectiveAccent = if (config.useDynamicHighlightColor) {
        dynamicAccentColor
    } else {
        Color(config.customHighlightColorArgb)
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = colors.surface,
        contentColor = colors.textPrimary,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 8.dp)
                .testTag("lyrics_appearance_sheet")
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Palette,
                        contentDescription = null,
                        tint = effectiveAccent,
                        modifier = Modifier.size(22.dp)
                    )
                    Column {
                        Text(
                            text = "LYRICS APPEARANCE",
                            fontFamily = OutfitFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            letterSpacing = 1.2.sp,
                            color = effectiveAccent
                        )
                        Text(
                            text = "Typography, stage glow & scroll feel",
                            fontFamily = PlusJakartaFontFamily,
                            fontSize = 13.sp,
                            color = colors.textSecondary
                        )
                    }
                }
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("lyrics_appearance_close_button")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = "Close appearance settings",
                        tint = colors.textPrimary
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Presets: Minimal, Cinematic, Karaoke, Classic
            SectionLabel("PRESETS", colors)
            Spacer(modifier = Modifier.height(8.dp))
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(end = 8.dp)
            ) {
                items(LyricsPreset.entries) { preset ->
                    val selected = config.preset == preset
                    Surface(
                        onClick = { onApplyPreset(preset) },
                        shape = RoundedCornerShape(16.dp),
                        color = if (selected) effectiveAccent.copy(alpha = 0.18f) else colors.surfaceElevated,
                        border = BorderStroke(
                            width = if (selected) 1.5.dp else 1.dp,
                            color = if (selected) effectiveAccent else colors.cardBorder
                        ),
                        modifier = Modifier
                            .heightIn(min = 56.dp)
                            .testTag("lyrics_preset_${preset.name.lowercase()}")
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                        ) {
                            Text(
                                text = preset.displayName,
                                fontFamily = OutfitFontFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.5.sp,
                                color = if (selected) effectiveAccent else colors.textPrimary
                            )
                            Text(
                                text = preset.subtitle,
                                fontFamily = PlusJakartaFontFamily,
                                fontSize = 11.sp,
                                color = colors.textSecondary
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(18.dp))
            HorizontalDivider(color = colors.cardBorder.copy(alpha = 0.5f))
            Spacer(modifier = Modifier.height(14.dp))

            // Font Family
            SectionLabel("FONT FAMILY", colors)
            Spacer(modifier = Modifier.height(8.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(LyricsFontFamilyOption.entries) { option ->
                    val selected = config.fontFamily == option
                    Surface(
                        onClick = { onUpdateConfig { it.copy(fontFamily = option) } },
                        shape = RoundedCornerShape(50),
                        color = if (selected) effectiveAccent else colors.surfaceElevated,
                        border = BorderStroke(1.dp, if (selected) effectiveAccent else colors.cardBorder),
                        modifier = Modifier
                            .heightIn(min = 40.dp)
                            .testTag("lyrics_font_${option.name.lowercase()}")
                    ) {
                        Box(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = option.displayName,
                                fontFamily = PlusJakartaFontFamily,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 12.sp,
                                color = if (selected) Color(0xFF101014) else colors.textPrimary
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Typography Sliders
            AppearanceSliderRow(
                label = "Font Size",
                valueLabel = "${config.fontSizeSp.toInt()} sp",
                value = config.fontSizeSp,
                valueRange = LyricsAppearanceConfig.MIN_FONT_SP..LyricsAppearanceConfig.MAX_FONT_SP,
                accentColor = effectiveAccent,
                colors = colors,
                testTag = "lyrics_font_size_slider",
                onValueChange = { v -> onUpdateConfig { it.copy(fontSizeSp = v) } }
            )

            AppearanceSliderRow(
                label = "Line Spacing",
                valueLabel = String.format(java.util.Locale.US, "%.2fx", config.lineSpacingMultiplier),
                value = config.lineSpacingMultiplier,
                valueRange = LyricsAppearanceConfig.MIN_LINE_SPACING..LyricsAppearanceConfig.MAX_LINE_SPACING,
                accentColor = effectiveAccent,
                colors = colors,
                testTag = "lyrics_line_spacing_slider",
                onValueChange = { v -> onUpdateConfig { it.copy(lineSpacingMultiplier = v) } }
            )

            AppearanceSliderRow(
                label = "Active Line Scale",
                valueLabel = "${(config.currentLineScale * 100).toInt()}%",
                value = config.currentLineScale,
                valueRange = LyricsAppearanceConfig.MIN_SCALE..LyricsAppearanceConfig.MAX_SCALE,
                accentColor = effectiveAccent,
                colors = colors,
                testTag = "lyrics_scale_slider",
                onValueChange = { v -> onUpdateConfig { it.copy(currentLineScale = v) } }
            )

            Spacer(modifier = Modifier.height(10.dp))
            HorizontalDivider(color = colors.cardBorder.copy(alpha = 0.5f))
            Spacer(modifier = Modifier.height(14.dp))

            // Highlight Color (Dynamic Album Art Default + Curated Swatches)
            SectionLabel("HIGHLIGHT COLOUR", colors)
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Surface(
                    onClick = { onUpdateConfig { it.copy(useDynamicHighlightColor = true) } },
                    shape = RoundedCornerShape(50),
                    color = if (config.useDynamicHighlightColor) dynamicAccentColor.copy(alpha = 0.2f) else colors.surfaceElevated,
                    border = BorderStroke(
                        if (config.useDynamicHighlightColor) 1.5.dp else 1.dp,
                        if (config.useDynamicHighlightColor) dynamicAccentColor else colors.cardBorder
                    ),
                    modifier = Modifier
                        .heightIn(min = 42.dp)
                        .testTag("lyrics_color_dynamic")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.AutoAwesome,
                            contentDescription = null,
                            tint = dynamicAccentColor,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "Album Art",
                            fontFamily = PlusJakartaFontFamily,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 12.sp,
                            color = colors.textPrimary
                        )
                    }
                }

                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(LyricsHighlightSwatches) { argb ->
                        val swatchColor = Color(argb)
                        val isSelected = !config.useDynamicHighlightColor && config.customHighlightColorArgb == argb
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(swatchColor)
                                .border(
                                    width = if (isSelected) 2.5.dp else 1.dp,
                                    color = if (isSelected) colors.textPrimary else colors.cardBorder,
                                    shape = CircleShape
                                )
                                .clickable {
                                    onUpdateConfig {
                                        it.copy(
                                            useDynamicHighlightColor = false,
                                            customHighlightColorArgb = argb
                                        )
                                    }
                                }
                                .testTag("lyrics_color_swatch_$argb"),
                            contentAlignment = Alignment.Center
                        ) {
                            if (isSelected) {
                                Icon(
                                    imageVector = Icons.Rounded.Check,
                                    contentDescription = "Selected highlight colour",
                                    tint = Color(0xFF101014),
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Background Controls
            AppearanceSliderRow(
                label = "Background Blur",
                valueLabel = "${config.backgroundBlurDp.toInt()} dp",
                value = config.backgroundBlurDp,
                valueRange = LyricsAppearanceConfig.MIN_BLUR_DP..LyricsAppearanceConfig.MAX_BLUR_DP,
                accentColor = effectiveAccent,
                colors = colors,
                testTag = "lyrics_blur_slider",
                onValueChange = { v -> onUpdateConfig { it.copy(backgroundBlurDp = v) } }
            )

            AppearanceSliderRow(
                label = "Background Overlay Opacity",
                valueLabel = "${(config.backgroundOverlayOpacity * 100).toInt()}%",
                value = config.backgroundOverlayOpacity,
                valueRange = LyricsAppearanceConfig.MIN_OPACITY..LyricsAppearanceConfig.MAX_OPACITY,
                accentColor = effectiveAccent,
                colors = colors,
                testTag = "lyrics_opacity_slider",
                onValueChange = { v -> onUpdateConfig { it.copy(backgroundOverlayOpacity = v) } }
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Auto-scroll speed selector
            SectionLabel("AUTO-SCROLL SPEED", colors)
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                LyricsScrollSpeed.entries.forEach { speed ->
                    val selected = config.scrollSpeed == speed
                    Surface(
                        onClick = { onUpdateConfig { it.copy(scrollSpeed = speed) } },
                        shape = RoundedCornerShape(14.dp),
                        color = if (selected) effectiveAccent.copy(alpha = 0.2f) else colors.surfaceElevated,
                        border = BorderStroke(1.dp, if (selected) effectiveAccent else colors.cardBorder),
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 44.dp)
                            .testTag("lyrics_scroll_speed_${speed.name.lowercase()}")
                    ) {
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(vertical = 10.dp)) {
                            Text(
                                text = speed.displayName,
                                fontFamily = PlusJakartaFontFamily,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 12.5.sp,
                                color = if (selected) effectiveAccent else colors.textPrimary
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))
            HorizontalDivider(color = colors.cardBorder.copy(alpha = 0.5f))
            Spacer(modifier = Modifier.height(8.dp))

            // Toggle Switches
            AppearanceToggleRow(
                title = "Word-by-Word Karaoke Highlight",
                subtitle = "Active when Enhanced LRC word timestamps are present",
                checked = config.wordHighlightEnabled,
                accentColor = effectiveAccent,
                colors = colors,
                testTag = "lyrics_word_highlight_switch",
                onCheckedChange = { enabled -> onUpdateConfig { it.copy(wordHighlightEnabled = enabled) } }
            )

            AppearanceToggleRow(
                title = "AMOLED Pure Black Mode",
                subtitle = "Deep true-black backdrop for OLED contrast & battery",
                checked = config.amoledDarkBackground,
                accentColor = effectiveAccent,
                colors = colors,
                testTag = "lyrics_amoled_switch",
                onCheckedChange = { enabled -> onUpdateConfig { it.copy(amoledDarkBackground = enabled) } }
            )

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun SectionLabel(text: String, colors: EditorialColors) {
    Text(
        text = text,
        fontFamily = OutfitFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 11.sp,
        letterSpacing = 1.1.sp,
        color = colors.textMuted
    )
}

@Composable
private fun AppearanceSliderRow(
    label: String,
    valueLabel: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    accentColor: Color,
    colors: EditorialColors,
    testTag: String,
    onValueChange: (Float) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                fontFamily = PlusJakartaFontFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp,
                color = colors.textPrimary
            )
            Text(
                text = valueLabel,
                fontFamily = OutfitFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 12.5.sp,
                color = accentColor
            )
        }
        Slider(
            value = value.coerceIn(valueRange.start, valueRange.endInclusive),
            onValueChange = onValueChange,
            valueRange = valueRange,
            colors = SliderDefaults.colors(
                thumbColor = accentColor,
                activeTrackColor = accentColor,
                inactiveTrackColor = colors.cardBorder
            ),
            modifier = Modifier
                .fillMaxWidth()
                .testTag(testTag)
        )
    }
}

@Composable
private fun AppearanceToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    accentColor: Color,
    colors: EditorialColors,
    testTag: String,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
            Text(
                text = title,
                fontFamily = PlusJakartaFontFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.5.sp,
                color = colors.textPrimary
            )
            Text(
                text = subtitle,
                fontFamily = PlusJakartaFontFamily,
                fontSize = 11.5.sp,
                color = colors.textSecondary
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color(0xFF101014),
                checkedTrackColor = accentColor
            ),
            modifier = Modifier.testTag(testTag)
        )
    }
}
