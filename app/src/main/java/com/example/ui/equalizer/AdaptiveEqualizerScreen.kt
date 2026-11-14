package com.example.ui.equalizer

import android.view.HapticFeedbackConstants
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.BluetoothAudio
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.equalizer.EqualizerMode
import com.example.equalizer.MacroProfile
import com.example.ui.theme.OutfitFontFamily
import com.example.ui.theme.PlusJakartaFontFamily
import com.example.viewmodel.SonoraViewModel
import ui.theme.LocalEditorialColors
import ui.theme.LocalReduceAnimations
import kotlin.math.roundToInt

/**
 * Adaptive 15-Band Equalizer Screen — SONORA AESTHETIC UI.
 * Supports all 9 light/dark editorial themes, Room custom presets,
 * Studio Bass/Treble shelves, Channel Balance, Playback Tempo, and real hardware EQ DSP.
 */
@Composable
fun AdaptiveEqualizerScreen(
    viewModel: SonoraViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler { onBack() }

    val view = LocalView.current
    val colors = LocalEditorialColors.current
    val reduceAnimations = LocalReduceAnimations.current
    val scrollState = rememberScrollState()

    val settings by viewModel.equalizerSettings.collectAsState()
    val responseCurve by viewModel.equalizerResponseCurve.collectAsState()
    val customPresets by viewModel.customEqPresets.collectAsState()
    val spatialConfig by viewModel.spatialConfig.collectAsState()
    val playbackSpeed by viewModel.playbackSpeed.collectAsState()
    val connectedDevice by viewModel.connectedAudioDeviceName.collectAsState()
    val isHardwareSupported by viewModel.isHardwareEqSupported.collectAsState()
    val globalBypass by viewModel.globalEffectsBypass.collectAsState()

    val isEnabled = settings.isEnabled && !globalBypass
    val currentMode = settings.mode
    val accentColor = if (isEnabled) colors.accentStrong else colors.textMuted

    var showSavePresetDialog by remember { mutableStateOf(false) }
    var presetNameInput by remember { mutableStateOf("") }

    val bassAvg = remember(settings.manualBands, settings.mode, settings.tilt, settings.contour) {
        val eff = settings.getEffectiveGains()
        ((eff[0] + eff[1]) / 2f).coerceIn(-12f, 12f)
    }
    val trebleAvg = remember(settings.manualBands, settings.mode, settings.tilt, settings.contour) {
        val eff = settings.getEffectiveGains()
        ((eff[13] + eff[14]) / 2f).coerceIn(-12f, 12f)
    }
    var bassShelf by remember(bassAvg) { mutableFloatStateOf(bassAvg) }
    var trebleShelf by remember(trebleAvg) { mutableFloatStateOf(trebleAvg) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(colors.backgroundBrush)
            .statusBarsPadding()
            .navigationBarsPadding()
            .testTag("adaptive_equalizer_screen"),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 760.dp)
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(horizontal = 20.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // 1. Editorial Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier.size(48.dp).testTag("eq_back_button")
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(colors.surface)
                            .border(0.75.dp, colors.cardBorder, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = "Back",
                            tint = colors.textPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "Equalizer",
                            fontFamily = OutfitFontFamily,
                            color = colors.textPrimary,
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Black
                        )
                        if (isEnabled) {
                            Spacer(modifier = Modifier.width(8.dp))
                            Box(
                                modifier = Modifier
                                    .size(9.dp)
                                    .background(accentColor, CircleShape)
                            )
                        }
                    }
                    Text(
                        text = when {
                            globalBypass -> "Bypassed via Global Studio Switch"
                            isEnabled -> "15-Band Adaptive DSP • $connectedDevice"
                            else -> "Bypassed • Clean Direct Output"
                        },
                        fontFamily = PlusJakartaFontFamily,
                        color = if (isEnabled) colors.accentStrong else colors.textMuted,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                IconButton(
                    onClick = {
                        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                        viewModel.resetEqualizerToNeutral()
                    },
                    enabled = isEnabled,
                    modifier = Modifier.size(48.dp).testTag("eq_reset_button")
                ) {
                    Icon(
                        Icons.Rounded.Refresh,
                        contentDescription = "Reset to Flat",
                        tint = if (isEnabled) colors.textPrimary else colors.textMuted.copy(alpha = 0.4f),
                        modifier = Modifier.size(20.dp)
                    )
                }

                Switch(
                    checked = settings.isEnabled,
                    onCheckedChange = { checked ->
                        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                        viewModel.setEqualizerMasterEnabled(checked)
                    },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = colors.onAccentStrong,
                        checkedTrackColor = colors.accentStrong,
                        uncheckedThumbColor = colors.textMuted,
                        uncheckedTrackColor = colors.surfaceTinted
                    ),
                    modifier = Modifier.testTag("eq_master_switch")
                )
            }

            if (!isHardwareSupported) {
                Spacer(modifier = Modifier.height(10.dp))
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = colors.surfaceTinted,
                    border = androidx.compose.foundation.BorderStroke(0.75.dp, colors.cardBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Info, contentDescription = null, tint = colors.accentStrong, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Start a track or connect audio output to attach the hardware EQ session.",
                            fontFamily = PlusJakartaFontFamily,
                            fontSize = 11.sp,
                            color = colors.textSecondary
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // 2. Large Rounded Segmented Control: DYNAMIC | MANUAL
            Surface(
                shape = RoundedCornerShape(22.dp),
                color = colors.surfaceTinted,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .border(0.75.dp, colors.cardBorder, RoundedCornerShape(22.dp))
            ) {
                Row(
                    modifier = Modifier.fillMaxSize().padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    val isDynamic = currentMode == EqualizerMode.DYNAMIC
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxSize()
                            .clip(RoundedCornerShape(18.dp))
                            .background(if (isDynamic) colors.accentPill else Color.Transparent)
                            .clickable(enabled = isEnabled) {
                                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                viewModel.setEqualizerMode(EqualizerMode.DYNAMIC)
                            }
                            .testTag("eq_mode_dynamic"),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "DYNAMIC",
                            fontFamily = OutfitFontFamily,
                            color = if (isDynamic) colors.onAccentPill else colors.textSecondary,
                            fontSize = 13.sp,
                            fontWeight = if (isDynamic) FontWeight.Bold else FontWeight.Medium,
                            letterSpacing = 0.8.sp
                        )
                    }

                    val isManual = currentMode == EqualizerMode.MANUAL
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxSize()
                            .clip(RoundedCornerShape(18.dp))
                            .background(if (isManual) colors.accentPill else Color.Transparent)
                            .clickable(enabled = isEnabled) {
                                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                viewModel.setEqualizerMode(EqualizerMode.MANUAL)
                            }
                            .testTag("eq_mode_manual"),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "MANUAL (15-BAND)",
                            fontFamily = OutfitFontFamily,
                            color = if (isManual) colors.onAccentPill else colors.textSecondary,
                            fontSize = 13.sp,
                            fontWeight = if (isManual) FontWeight.Bold else FontWeight.Medium,
                            letterSpacing = 0.6.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // 3. Live Frequency Response Graph
            FrequencyResponseGraph(
                responseDb = responseCurve,
                isEnabled = isEnabled,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(16.dp))

            // 4. Mode-Dependent Controls
            AnimatedContent(
                targetState = currentMode,
                transitionSpec = {
                    val dur = if (reduceAnimations) 0 else 220
                    fadeIn(animationSpec = tween(dur)) togetherWith fadeOut(animationSpec = tween(dur))
                },
                label = "eqModeTransition"
            ) { targetMode ->
                when (targetMode) {
                    EqualizerMode.DYNAMIC -> {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            DynamicEqualizerPad(
                                tilt = settings.tilt,
                                contour = settings.contour,
                                isEnabled = isEnabled,
                                onPositionChanged = { newTilt, newContour ->
                                    viewModel.setEqualizerDynamicPad(newTilt, newContour)
                                },
                                onReset = {
                                    viewModel.setEqualizerDynamicPad(0f, 0f)
                                },
                                modifier = Modifier.fillMaxWidth()
                            )

                            Spacer(modifier = Modifier.height(16.dp))

                            MacroProfileControl(
                                currentMacro = settings.macroProfile,
                                isEnabled = isEnabled,
                                onMacroSelected = { viewModel.setEqualizerMacroProfile(it) }
                            )

                            Spacer(modifier = Modifier.height(16.dp))

                            SoundProfileSelector(
                                selectedProfileId = settings.selectedProfileId,
                                isEnabled = isEnabled,
                                onSelectProfile = { viewModel.selectEqualizerSoundProfile(it) }
                            )
                        }
                    }

                    EqualizerMode.MANUAL -> {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            ManualEqualizerScreen(
                                bandGains = settings.manualBands,
                                isEnabled = isEnabled,
                                onGainChanged = { index, gainDb ->
                                    viewModel.setEqualizerBandGain(index, gainDb)
                                },
                                onResetAllBands = {
                                    viewModel.resetEqualizerToNeutral()
                                }
                            )

                            Spacer(modifier = Modifier.height(16.dp))

                            MacroProfileControl(
                                currentMacro = settings.macroProfile,
                                isEnabled = isEnabled,
                                onMacroSelected = { viewModel.setEqualizerMacroProfile(it) }
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 5. Custom Presets Manager (Room Persisted)
            Surface(
                shape = RoundedCornerShape(22.dp),
                color = colors.surface,
                border = androidx.compose.foundation.BorderStroke(0.75.dp, colors.cardBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "CUSTOM PRESETS",
                                fontFamily = PlusJakartaFontFamily,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.8.sp,
                                color = colors.textMuted
                            )
                            Text(
                                text = "Save & recall your tuned 15-band curves",
                                fontFamily = PlusJakartaFontFamily,
                                fontSize = 12.sp,
                                color = colors.textSecondary
                            )
                        }
                        Button(
                            onClick = { showSavePresetDialog = true },
                            enabled = isEnabled,
                            shape = RoundedCornerShape(16.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = colors.accentPill,
                                contentColor = colors.onAccentPill
                            ),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            modifier = Modifier.height(40.dp).testTag("eq_save_custom_preset_button")
                        ) {
                            Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Save", fontFamily = PlusJakartaFontFamily, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    }

                    if (customPresets.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(10.dp))
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(customPresets, key = { it.name }) { preset ->
                                val selected = settings.selectedProfileId == "user_${preset.name}"
                                Surface(
                                    onClick = { viewModel.applyCustomEqPreset(preset) },
                                    shape = RoundedCornerShape(16.dp),
                                    color = if (selected) colors.accentPill else colors.surfaceTinted,
                                    border = androidx.compose.foundation.BorderStroke(
                                        1.dp,
                                        if (selected) colors.accentStrong else colors.cardBorder
                                    ),
                                    modifier = Modifier.height(40.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(start = 12.dp, end = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = preset.name,
                                            fontFamily = PlusJakartaFontFamily,
                                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                            fontSize = 12.sp,
                                            color = if (selected) colors.onAccentPill else colors.textPrimary
                                        )
                                        IconButton(
                                            onClick = { viewModel.deleteCustomEqPreset(preset.name) },
                                            modifier = Modifier.size(28.dp)
                                        ) {
                                            Icon(
                                                Icons.Rounded.Close,
                                                contentDescription = "Delete preset ${preset.name}",
                                                tint = colors.textMuted,
                                                modifier = Modifier.size(14.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 6. Studio Tone Shaping: Bass Shelf, Treble Shelf, Balance & Tempo
            Surface(
                shape = RoundedCornerShape(22.dp),
                color = colors.surface,
                border = androidx.compose.foundation.BorderStroke(0.75.dp, colors.cardBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "TONE & PLAYBACK SHAPING",
                            fontFamily = PlusJakartaFontFamily,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.8.sp,
                            color = colors.textMuted
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.BluetoothAudio, contentDescription = null, tint = colors.accentStrong, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = connectedDevice,
                                fontFamily = PlusJakartaFontFamily,
                                fontSize = 11.sp,
                                color = colors.textSecondary
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Bass Shelf
                    EqStudioSlider(
                        label = "Bass Shelf (25–100 Hz)",
                        valueText = "${if (bassShelf > 0) "+" else ""}${String.format("%.1f", bassShelf)} dB",
                        value = bassShelf,
                        valueRange = -12f..12f,
                        enabled = isEnabled,
                        colors = colors,
                        onValueChange = {
                            bassShelf = it
                            viewModel.applyStudioEqShelf(isBass = true, levelDb = it)
                        }
                    )

                    // Treble Shelf
                    EqStudioSlider(
                        label = "Treble Shelf (6.3–16 kHz)",
                        valueText = "${if (trebleShelf > 0) "+" else ""}${String.format("%.1f", trebleShelf)} dB",
                        value = trebleShelf,
                        valueRange = -12f..12f,
                        enabled = isEnabled,
                        colors = colors,
                        onValueChange = {
                            trebleShelf = it
                            viewModel.applyStudioEqShelf(isBass = false, levelDb = it)
                        }
                    )

                    // Channel Balance L/R
                    val balPct = (spatialConfig.channelBalance * 100f).roundToInt()
                    val balLabel = when {
                        balPct < -4 -> "L ${-balPct}%"
                        balPct > 4 -> "R $balPct%"
                        else -> "Center"
                    }
                    EqStudioSlider(
                        label = "Channel Balance (L / R)",
                        valueText = balLabel,
                        value = spatialConfig.channelBalance,
                        valueRange = -1f..1f,
                        enabled = !globalBypass,
                        colors = colors,
                        onValueChange = {
                            val snapped = if (kotlin.math.abs(it) < 0.06f) 0f else it
                            viewModel.updateSpatialConfig(spatialConfig.copy(channelBalance = snapped))
                        }
                    )

                    // Playback Tempo / Speed
                    EqStudioSlider(
                        label = "Playback Speed",
                        valueText = "${String.format(java.util.Locale.US, "%.2f", playbackSpeed)}x",
                        value = playbackSpeed,
                        valueRange = 0.5f..2.0f,
                        enabled = true,
                        colors = colors,
                        onValueChange = { viewModel.setPlaybackSpeed(it) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 7. Volume Boost Preamp Slider
            VolumeBoostSlider(
                boostDb = settings.volumeBoostDb,
                isEnabled = isEnabled,
                onBoostChanged = { viewModel.setEqualizerVolumeBoost(it) }
            )

            Spacer(modifier = Modifier.height(24.dp))
        }
    }

    if (showSavePresetDialog) {
        AlertDialog(
            onDismissRequest = { showSavePresetDialog = false },
            containerColor = colors.surface,
            titleContentColor = colors.textPrimary,
            textContentColor = colors.textSecondary,
            title = {
                Text(
                    text = "Save Custom EQ Preset",
                    fontFamily = OutfitFontFamily,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                OutlinedTextField(
                    value = presetNameInput,
                    onValueChange = { presetNameInput = it },
                    placeholder = { Text("e.g., Warm IEM Studio") },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val name = presetNameInput.trim().ifBlank { "Custom ${customPresets.size + 1}" }
                        viewModel.saveCustomEqPreset(name)
                        presetNameInput = ""
                        showSavePresetDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = colors.accentStrong,
                        contentColor = colors.onAccentStrong
                    )
                ) {
                    Text("Save Preset")
                }
            },
            dismissButton = {
                TextButton(onClick = { showSavePresetDialog = false }) {
                    Text("Cancel", color = colors.textSecondary)
                }
            }
        )
    }
}

@Composable
private fun EqStudioSlider(
    label: String,
    valueText: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    enabled: Boolean,
    colors: ui.theme.EditorialColors,
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
                fontSize = 12.sp,
                color = if (enabled) colors.textPrimary else colors.textMuted
            )
            Text(
                text = valueText,
                fontFamily = OutfitFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp,
                color = if (enabled) colors.accentStrong else colors.textMuted
            )
        }
        Slider(
            value = value.coerceIn(valueRange.start, valueRange.endInclusive),
            onValueChange = onValueChange,
            valueRange = valueRange,
            enabled = enabled,
            colors = SliderDefaults.colors(
                thumbColor = colors.accentStrong,
                activeTrackColor = colors.accentStrong,
                inactiveTrackColor = colors.cardBorder
            )
        )
    }
}

@Composable
private fun MacroProfileControl(
    currentMacro: MacroProfile,
    isEnabled: Boolean,
    onMacroSelected: (MacroProfile) -> Unit
) {
    val view = LocalView.current
    val colors = LocalEditorialColors.current

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(colors.surface)
            .border(0.75.dp, colors.cardBorder, RoundedCornerShape(22.dp))
            .padding(16.dp)
    ) {
        Text(
            text = "TONAL MACRO PROFILE",
            fontFamily = PlusJakartaFontFamily,
            color = colors.textMuted,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.8.sp
        )
        Text(
            text = if (currentMacro == MacroProfile.BROAD) "Broader Q • Smooth natural instrumental staging"
            else "Focused Q • Enhanced midrange clarity & definition",
            fontFamily = PlusJakartaFontFamily,
            color = colors.textSecondary,
            fontSize = 12.sp
        )

        Spacer(modifier = Modifier.height(10.dp))

        Surface(
            shape = RoundedCornerShape(16.dp),
            color = colors.surfaceTinted,
            modifier = Modifier
                .fillMaxWidth()
                .height(44.dp)
                .border(0.75.dp, colors.cardBorder, RoundedCornerShape(16.dp))
        ) {
            Row(
                modifier = Modifier.fillMaxSize().padding(3.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                val isBroad = currentMacro == MacroProfile.BROAD
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxSize()
                        .clip(RoundedCornerShape(13.dp))
                        .background(if (isBroad && isEnabled) colors.accentPill else Color.Transparent)
                        .clickable(enabled = isEnabled) {
                            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                            onMacroSelected(MacroProfile.BROAD)
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "BROAD",
                        fontFamily = OutfitFontFamily,
                        color = if (isBroad && isEnabled) colors.onAccentPill else colors.textSecondary,
                        fontSize = 12.sp,
                        fontWeight = if (isBroad) FontWeight.Bold else FontWeight.Medium,
                        letterSpacing = 0.8.sp
                    )
                }

                val isFocused = currentMacro == MacroProfile.FOCUSED
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxSize()
                        .clip(RoundedCornerShape(13.dp))
                        .background(if (isFocused && isEnabled) colors.accentPill else Color.Transparent)
                        .clickable(enabled = isEnabled) {
                            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                            onMacroSelected(MacroProfile.FOCUSED)
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "FOCUSED",
                        fontFamily = OutfitFontFamily,
                        color = if (isFocused && isEnabled) colors.onAccentPill else colors.textSecondary,
                        fontSize = 12.sp,
                        fontWeight = if (isFocused) FontWeight.Bold else FontWeight.Medium,
                        letterSpacing = 0.8.sp
                    )
                }
            }
        }
    }
}
