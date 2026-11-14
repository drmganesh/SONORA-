package ui.player

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.AnimationIntensity
import com.example.model.ArtworkAnimationOption
import com.example.model.ArtworkSizeOption
import com.example.model.ButtonShapeOption
import com.example.model.ControlArrangement
import com.example.model.CornerRadiusOption
import com.example.model.LockScreenStyle
import com.example.model.MiniPlayerStyle
import com.example.model.PlayerBackgroundStyle
import com.example.ui.theme.OutfitFontFamily
import com.example.ui.theme.PlusJakartaFontFamily
import com.example.viewmodel.SonoraViewModel
import ui.player.components.PlayerStyleMiniPreview
import ui.theme.LocalEditorialColors
import kotlin.math.roundToInt

/**
 * Full-featured Now Playing Customization Screen:
 * Lets the user sculpt Artwork Size, Corner Radius, Background Style, Control Arrangement,
 * Button Shape, Dynamic Album Accent, Artwork Animations, Typography & Seekbar Scaling,
 * Metadata Visibility, Mini-Player Style, and Lock-Screen Style with instant Reset to Default.
 */
@Composable
fun PlayerCustomizationScreen(
    viewModel: SonoraViewModel,
    onBack: () -> Unit,
    onOpenPlayer: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val colors = LocalEditorialColors.current
    val appearance by viewModel.playerAppearance.collectAsState()
    val currentStyle by viewModel.playerStyle.collectAsState()
    val currentSong by viewModel.currentSong.collectAsState()
    val miniPlayerStyle by viewModel.miniPlayerStyle.collectAsState()
    val lockScreenStyle by viewModel.lockScreenStyle.collectAsState()

    BackHandler(onBack = onBack)

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(colors.backgroundBrush)
            .statusBarsPadding()
            .navigationBarsPadding()
            .testTag("player_customization_screen"),
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
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
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
                            .testTag("customization_back_button")
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back", tint = colors.textPrimary)
                        }
                    }
                    Spacer(modifier = Modifier.width(14.dp))
                    Column {
                        Text(
                            text = "Player Appearance",
                            fontFamily = OutfitFontFamily,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 22.sp,
                            color = colors.textPrimary
                        )
                        Text(
                            text = "Fine-tune artwork, controls & motion",
                            fontFamily = PlusJakartaFontFamily,
                            fontSize = 12.sp,
                            color = colors.textSecondary
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = colors.surfaceTinted,
                    border = BorderStroke(0.75.dp, colors.cardBorder),
                    modifier = Modifier
                        .clip(RoundedCornerShape(14.dp))
                        .clickable { viewModel.resetPlayerAppearanceToDefault() }
                        .testTag("reset_appearance_top_button")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Rounded.RestartAlt, contentDescription = "Reset", tint = colors.accentStrong, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Reset",
                            fontFamily = PlusJakartaFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            color = colors.accentStrong
                        )
                    }
                }
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 90.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Live Preview Header Card
                item {
                    Surface(
                        shape = RoundedCornerShape(24.dp),
                        color = colors.surface,
                        border = BorderStroke(0.75.dp, colors.cardBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            PlayerStyleMiniPreview(
                                style = currentStyle,
                                isSelected = true,
                                song = currentSong,
                                modifier = Modifier.size(88.dp)
                            )
                            Spacer(modifier = Modifier.width(14.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "ACTIVE STYLE: ${currentStyle.displayName.uppercase()}",
                                    fontFamily = PlusJakartaFontFamily,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 10.sp,
                                    letterSpacing = 1.sp,
                                    color = colors.accentStrong
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "${appearance.artworkSize.label} Art • ${appearance.cornerRadius.label}",
                                    fontFamily = OutfitFontFamily,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp,
                                    color = colors.textPrimary
                                )
                                Text(
                                    text = "${appearance.backgroundStyle.label} • ${appearance.controlArrangement.label}",
                                    fontFamily = PlusJakartaFontFamily,
                                    fontSize = 12.sp,
                                    color = colors.textSecondary
                                )
                            }
                        }
                    }
                }

                // Artwork Geometry & Atmosphere
                item {
                    CustomizationSectionCard(title = "ARTWORK & BACKDROP", colors = colors) {
                        ChipOptionRow(
                            label = "Artwork Size",
                            options = ArtworkSizeOption.entries,
                            selected = appearance.artworkSize,
                            labelProvider = { it.label },
                            onSelect = { opt -> viewModel.updatePlayerAppearance { it.copy(artworkSize = opt) } }
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        ChipOptionRow(
                            label = "Corner Radius",
                            options = CornerRadiusOption.entries,
                            selected = appearance.cornerRadius,
                            labelProvider = { it.label },
                            onSelect = { opt -> viewModel.updatePlayerAppearance { it.copy(cornerRadius = opt) } }
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        ChipOptionRow(
                            label = "Player Background",
                            options = PlayerBackgroundStyle.entries,
                            selected = appearance.backgroundStyle,
                            labelProvider = { it.label },
                            onSelect = { opt -> viewModel.updatePlayerAppearance { it.copy(backgroundStyle = opt) } }
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        HorizontalDivider(color = colors.cardBorder)
                        Spacer(modifier = Modifier.height(6.dp))
                        CustomizationSwitchRow(
                            title = "Use Dynamic Album Accent",
                            subtitle = "Automatically tint controls & seekbar from cover colors",
                            checked = appearance.useDynamicAlbumAccent,
                            onCheckedChange = { checked ->
                                viewModel.updatePlayerAppearance { it.copy(useDynamicAlbumAccent = checked) }
                            }
                        )
                    }
                }

                // Controls & Button Geometry
                item {
                    CustomizationSectionCard(title = "CONTROLS & TYPOGRAPHY", colors = colors) {
                        ChipOptionRow(
                            label = "Control Arrangement",
                            options = ControlArrangement.entries,
                            selected = appearance.controlArrangement,
                            labelProvider = { it.label },
                            onSelect = { opt -> viewModel.updatePlayerAppearance { it.copy(controlArrangement = opt) } }
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        ChipOptionRow(
                            label = "Play Button Shape",
                            options = ButtonShapeOption.entries,
                            selected = appearance.buttonShape,
                            labelProvider = { it.label },
                            onSelect = { opt -> viewModel.updatePlayerAppearance { it.copy(buttonShape = opt) } }
                        )
                        Spacer(modifier = Modifier.height(14.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Typography Scale", fontFamily = OutfitFontFamily, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = colors.textPrimary)
                            Text("${(appearance.typographyScale * 100).roundToInt()}%", fontFamily = PlusJakartaFontFamily, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = colors.accentStrong)
                        }
                        Slider(
                            value = appearance.typographyScale,
                            onValueChange = { valNext ->
                                val snapped = ((valNext * 20f).roundToInt() / 20f).coerceIn(0.85f, 1.25f)
                                viewModel.updatePlayerAppearance { it.copy(typographyScale = snapped) }
                            },
                            valueRange = 0.85f..1.25f,
                            colors = SliderDefaults.colors(thumbColor = colors.accentStrong, activeTrackColor = colors.accentStrong)
                        )

                        Spacer(modifier = Modifier.height(6.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Seekbar Thickness", fontFamily = OutfitFontFamily, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = colors.textPrimary)
                            Text("${(appearance.seekbarThicknessScale * 100).roundToInt()}%", fontFamily = PlusJakartaFontFamily, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = colors.accentStrong)
                        }
                        Slider(
                            value = appearance.seekbarThicknessScale,
                            onValueChange = { valNext ->
                                val snapped = ((valNext * 10f).roundToInt() / 10f).coerceIn(0.7f, 1.5f)
                                viewModel.updatePlayerAppearance { it.copy(seekbarThicknessScale = snapped) }
                            },
                            valueRange = 0.7f..1.5f,
                            colors = SliderDefaults.colors(thumbColor = colors.accentStrong, activeTrackColor = colors.accentStrong)
                        )
                    }
                }

                // Motion & Artwork Animation
                item {
                    CustomizationSectionCard(title = "MOTION & ARTWORK ANIMATION", colors = colors) {
                        ChipOptionRow(
                            label = "Artwork Motion",
                            options = ArtworkAnimationOption.entries,
                            selected = appearance.artworkAnimation,
                            labelProvider = { it.label },
                            onSelect = { opt -> viewModel.updatePlayerAppearance { it.copy(artworkAnimation = opt) } }
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        ChipOptionRow(
                            label = "Animation Intensity",
                            options = AnimationIntensity.entries,
                            selected = appearance.animationIntensity,
                            labelProvider = { it.label },
                            onSelect = { opt -> viewModel.updatePlayerAppearance { it.copy(animationIntensity = opt) } }
                        )
                    }
                }

                // Metadata & Surface Variants
                item {
                    CustomizationSectionCard(title = "METADATA & MULTI-SURFACE", colors = colors) {
                        CustomizationSwitchRow(
                            title = "Show Audio Codec & Hi-Res Tag",
                            subtitle = "Display FLAC / sample rate / bitrate details",
                            checked = appearance.showAudioFormatMetadata,
                            onCheckedChange = { checked -> viewModel.updatePlayerAppearance { it.copy(showAudioFormatMetadata = checked) } }
                        )
                        CustomizationSwitchRow(
                            title = "Show Album Name",
                            subtitle = "Include album title beneath artist name",
                            checked = appearance.showAlbumMetadata,
                            onCheckedChange = { checked -> viewModel.updatePlayerAppearance { it.copy(showAlbumMetadata = checked) } }
                        )
                        CustomizationSwitchRow(
                            title = "Show Lyrics Shortcut",
                            subtitle = "Keep synced lyrics button accessible on player",
                            checked = appearance.showLyricsShortcut,
                            onCheckedChange = { checked -> viewModel.updatePlayerAppearance { it.copy(showLyricsShortcut = checked) } }
                        )
                        CustomizationSwitchRow(
                            title = "Show Queue Shortcut",
                            subtitle = "Display quick queue button on track card",
                            checked = appearance.showQueueShortcut,
                            onCheckedChange = { checked -> viewModel.updatePlayerAppearance { it.copy(showQueueShortcut = checked) } }
                        )

                        Spacer(modifier = Modifier.height(10.dp))
                        HorizontalDivider(color = colors.cardBorder)
                        Spacer(modifier = Modifier.height(10.dp))

                        ChipOptionRow(
                            label = "Mini-Player Style",
                            options = MiniPlayerStyle.entries,
                            selected = miniPlayerStyle,
                            labelProvider = { it.displayName },
                            onSelect = { viewModel.setMiniPlayerStyle(it) }
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        ChipOptionRow(
                            label = "Lock-Screen Media Style",
                            options = LockScreenStyle.entries,
                            selected = lockScreenStyle,
                            labelProvider = { it.displayName },
                            onSelect = {
                                viewModel.setLockScreenStyle(it)
                                viewModel.syncNotification()
                            }
                        )
                    }
                }

                // Bottom Action Buttons
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OutlinedButton(
                            onClick = { viewModel.resetPlayerAppearanceToDefault() },
                            shape = RoundedCornerShape(18.dp),
                            border = BorderStroke(1.dp, colors.cardBorder),
                            modifier = Modifier
                                .weight(1f)
                                .height(50.dp)
                                .testTag("reset_appearance_bottom_button")
                        ) {
                            Text("Reset to Default", fontFamily = OutfitFontFamily, fontWeight = FontWeight.Bold, color = colors.textPrimary)
                        }

                        if (onOpenPlayer != null) {
                            Button(
                                onClick = onOpenPlayer,
                                colors = ButtonDefaults.buttonColors(containerColor = colors.accentStrong, contentColor = colors.onAccentStrong),
                                shape = RoundedCornerShape(18.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(50.dp)
                                    .testTag("apply_open_player_button")
                            ) {
                                Icon(Icons.Rounded.PlayCircle, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Open Player", fontFamily = OutfitFontFamily, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CustomizationSectionCard(
    title: String,
    colors: ui.theme.EditorialColors,
    content: @Composable () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(22.dp),
        color = colors.surface,
        border = BorderStroke(0.75.dp, colors.cardBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Text(
                text = title,
                fontFamily = PlusJakartaFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 11.sp,
                letterSpacing = 1.2.sp,
                color = colors.accentStrong
            )
            Spacer(modifier = Modifier.height(12.dp))
            content()
        }
    }
}

@Composable
private fun <T> ChipOptionRow(
    label: String,
    options: List<T>,
    selected: T,
    labelProvider: (T) -> String,
    onSelect: (T) -> Unit
) {
    val colors = LocalEditorialColors.current
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = label,
            fontFamily = OutfitFontFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = 14.sp,
            color = colors.textPrimary
        )
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            options.forEach { option ->
                val isSelected = option == selected
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = if (isSelected) colors.accentStrong else colors.surfaceTinted,
                    border = BorderStroke(0.75.dp, if (isSelected) colors.accentStrong else colors.cardBorder),
                    modifier = Modifier
                        .clip(RoundedCornerShape(14.dp))
                        .clickable { onSelect(option) }
                ) {
                    Text(
                        text = labelProvider(option),
                        fontFamily = PlusJakartaFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        color = if (isSelected) colors.onAccentStrong else colors.textSecondary,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun CustomizationSwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    val colors = LocalEditorialColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
            Text(
                text = title,
                fontFamily = OutfitFontFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp,
                color = colors.textPrimary
            )
            Text(
                text = subtitle,
                fontFamily = PlusJakartaFontFamily,
                fontSize = 11.sp,
                color = colors.textSecondary
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = colors.onAccentStrong,
                checkedTrackColor = colors.accentStrong
            )
        )
    }
}
