package ui.audiostudio

import android.media.audiofx.PresetReverb
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.BluetoothAudio
import androidx.compose.material.icons.rounded.Equalizer
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.SpatialAudio
import androidx.compose.material.icons.rounded.Speaker
import androidx.compose.material.icons.rounded.SurroundSound
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.SpatialMode
import com.example.ui.theme.OutfitFontFamily
import com.example.ui.theme.PlusJakartaFontFamily
import com.example.viewmodel.SonoraViewModel
import ui.theme.LocalEditorialColors
import kotlin.math.cos
import kotlin.math.sin

private data class ReverbPresetItem(
    val preset: Short,
    val label: String
)

private val ReverbPresets = listOf(
    ReverbPresetItem(PresetReverb.PRESET_NONE, "Dry / None"),
    ReverbPresetItem(PresetReverb.PRESET_SMALLROOM, "Small Room"),
    ReverbPresetItem(PresetReverb.PRESET_MEDIUMROOM, "Studio Room"),
    ReverbPresetItem(PresetReverb.PRESET_LARGEROOM, "Concert Room"),
    ReverbPresetItem(PresetReverb.PRESET_MEDIUMHALL, "Medium Hall"),
    ReverbPresetItem(PresetReverb.PRESET_LARGEHALL, "Arena Hall"),
    ReverbPresetItem(PresetReverb.PRESET_PLATE, "Plate")
)

/**
 * SONORA Audio Effects Studio:
 * Dedicated DSP soundstage console backed directly by SonoraAudioEngine (single playback instance).
 * Includes Global Bypass, Cinematic 8D Slow-Orbit spatial panning, 360° Dynamic soundstage,
 * Bass Boost, Binaural TWS Virtualizer, Preset Reverb, and honest device capability status.
 */
@Composable
fun AudioEffectsStudioScreen(
    viewModel: SonoraViewModel,
    onOpenEqualizer: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalEditorialColors.current
    val globalBypass by viewModel.globalEffectsBypass.collectAsState()
    val orbitEnabled by viewModel.orbit8dEnabled.collectAsState()
    val orbitSpeed by viewModel.orbit8dSpeed.collectAsState()
    val orbitDepth by viewModel.orbit8dDepth.collectAsState()
    val orbitAzimuth by viewModel.orbitAzimuthRad.collectAsState()
    val spatialConfig by viewModel.spatialConfig.collectAsState()
    val bassBoost by viewModel.bassBoost.collectAsState()
    val virtualizer by viewModel.virtualizer.collectAsState()
    val reverbPreset by viewModel.reverbPreset.collectAsState()
    val connectedDevice by viewModel.connectedAudioDeviceName.collectAsState()
    val isTws by viewModel.isTwsConnected.collectAsState()

    val isBassSupported by viewModel.isBassBoostSupported.collectAsState()
    val isVirtSupported by viewModel.isVirtualizerSupported.collectAsState()
    val isReverbSupported by viewModel.isReverbSupported.collectAsState()
    val dspDiagnostic by viewModel.dspDiagnosticMessage.collectAsState()

    val activeEngineCount = listOf(
        !globalBypass && orbitEnabled,
        !globalBypass && spatialConfig.isEnabled,
        !globalBypass && bassBoost > 0,
        !globalBypass && virtualizer > 0,
        !globalBypass && reverbPreset != PresetReverb.PRESET_NONE
    ).count { it }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background)
            .statusBarsPadding()
            .navigationBarsPadding()
            .testTag("audio_effects_studio_screen"),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 760.dp)
                .fillMaxSize()
        ) {
            // Editorial Top Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("audio_studio_back_button")
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
                                imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                                contentDescription = "Back",
                                tint = colors.textPrimary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Column {
                        Text(
                            text = "Audio Studio",
                            fontFamily = OutfitFontFamily,
                            fontWeight = FontWeight.Black,
                            fontSize = 26.sp,
                            letterSpacing = (-0.5).sp,
                            color = colors.textPrimary
                        )
                        Text(
                            text = "$connectedDevice • $activeEngineCount effects active",
                            fontFamily = PlusJakartaFontFamily,
                            fontSize = 12.sp,
                            color = colors.textSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                // Quick Equalizer button
                Surface(
                    onClick = onOpenEqualizer,
                    shape = RoundedCornerShape(20.dp),
                    color = colors.accentPill,
                    modifier = Modifier
                        .height(40.dp)
                        .testTag("studio_to_eq_button")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Equalizer,
                            contentDescription = "Open 15-Band Equalizer",
                            tint = colors.onAccentPill,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "15-Band EQ",
                            fontFamily = PlusJakartaFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            color = colors.onAccentPill
                        )
                    }
                }
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // 1. Global Audio Effects Bypass Card
                item(key = "global_bypass") {
                    Surface(
                        shape = RoundedCornerShape(24.dp),
                        color = if (globalBypass) colors.surfaceTinted else colors.surface,
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            if (globalBypass) colors.cardBorder else colors.accentStrong.copy(alpha = 0.4f)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 18.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                Box(
                                    modifier = Modifier
                                        .size(44.dp)
                                        .clip(CircleShape)
                                        .background(if (globalBypass) colors.surfaceElevated else colors.accentPill),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.PowerSettingsNew,
                                        contentDescription = null,
                                        tint = if (globalBypass) colors.textMuted else colors.onAccentPill
                                    )
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(
                                        text = "All Effects Bypassed",
                                        fontFamily = OutfitFontFamily,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 16.sp,
                                        color = colors.textPrimary
                                    )
                                    Text(
                                        text = if (globalBypass) "ON: Clean original audio plays (DSP bypassed)"
                                        else "OFF: Individually enabled DSP effects applied",
                                        fontFamily = PlusJakartaFontFamily,
                                        fontSize = 12.sp,
                                        color = colors.textSecondary
                                    )
                                }
                            }
                            Switch(
                                checked = globalBypass,
                                onCheckedChange = { bypass -> viewModel.setGlobalEffectsBypass(bypass) },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = colors.onAccentStrong,
                                    checkedTrackColor = colors.accentStrong
                                ),
                                modifier = Modifier.testTag("studio_global_master_switch")
                            )
                        }
                    }
                }

                // 2. Cinematic Audio (8D) & Dynamic 360 Slow-Orbit Visualizer Card
                item(key = "orbit_8d_card") {
                    Surface(
                        shape = RoundedCornerShape(28.dp),
                        color = colors.surface,
                        border = androidx.compose.foundation.BorderStroke(0.75.dp, colors.cardBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(20.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Cinematic 8D & Dynamic 360°",
                                        fontFamily = OutfitFontFamily,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 18.sp,
                                        color = colors.textPrimary
                                    )
                                    Text(
                                        text = "Slow orbital acoustic azimuth panning with safe dual-earbud floor",
                                        fontFamily = PlusJakartaFontFamily,
                                        fontSize = 12.sp,
                                        color = colors.textSecondary
                                    )
                                }
                                Switch(
                                    checked = orbitEnabled && !globalBypass,
                                    enabled = !globalBypass,
                                    onCheckedChange = { viewModel.setOrbit8dConfig(it) },
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = colors.onAccentStrong,
                                        checkedTrackColor = colors.accentStrong
                                    ),
                                    modifier = Modifier.testTag("studio_8d_orbit_switch")
                                )
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            // Orbital Soundstage Radar Canvas
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(156.dp)
                                    .clip(RoundedCornerShape(22.dp))
                                    .background(colors.surfaceTinted),
                                contentAlignment = Alignment.Center
                            ) {
                                val accentColor = colors.accentStrong
                                val trackRingColor = colors.cardBorder
                                val listenerBg = colors.accentPill
                                Canvas(modifier = Modifier.size(136.dp)) {
                                    val center = Offset(size.width / 2f, size.height / 2f)
                                    val outerR = size.minDimension * 0.44f
                                    val innerR = outerR * 0.56f

                                    drawCircle(
                                        color = trackRingColor,
                                        radius = outerR,
                                        center = center,
                                        style = Stroke(width = 2.dp.toPx())
                                    )
                                    drawCircle(
                                        color = trackRingColor.copy(alpha = 0.6f),
                                        radius = innerR,
                                        center = center,
                                        style = Stroke(width = 1.5.dp.toPx())
                                    )
                                    drawCircle(
                                        brush = Brush.radialGradient(
                                            colors = listOf(listenerBg, listenerBg.copy(alpha = 0.25f)),
                                            center = center,
                                            radius = 24.dp.toPx()
                                        ),
                                        radius = 20.dp.toPx(),
                                        center = center
                                    )

                                    val activeRadius = outerR * (0.45f + 0.55f * orbitDepth)
                                    val orbX = center.x + sin(orbitAzimuth.toDouble()).toFloat() * activeRadius
                                    val orbY = center.y - cos(orbitAzimuth.toDouble()).toFloat() * activeRadius

                                    if (orbitEnabled && !globalBypass) {
                                        drawLine(
                                            color = accentColor.copy(alpha = 0.5f),
                                            start = center,
                                            end = Offset(orbX, orbY),
                                            strokeWidth = 2.5.dp.toPx(),
                                            cap = StrokeCap.Round
                                        )
                                        drawCircle(
                                            color = accentColor.copy(alpha = 0.22f),
                                            radius = 16.dp.toPx(),
                                            center = Offset(orbX, orbY)
                                        )
                                        drawCircle(
                                            color = accentColor,
                                            radius = 8.dp.toPx(),
                                            center = Offset(orbX, orbY)
                                        )
                                    }
                                }

                                Icon(
                                    imageVector = Icons.Rounded.Headphones,
                                    contentDescription = "Listener center",
                                    tint = colors.onAccentPill,
                                    modifier = Modifier.size(20.dp)
                                )
                            }

                            AnimatedVisibility(visible = orbitEnabled && !globalBypass) {
                                Column {
                                    Spacer(modifier = Modifier.height(14.dp))
                                    StudioSliderRow(
                                        label = "Slow Orbit Speed",
                                        valueLabel = "${String.format(java.util.Locale.US, "%.2f", orbitSpeed)}x",
                                        value = orbitSpeed,
                                        valueRange = 0.1f..2.0f,
                                        colors = colors,
                                        onValueChange = { viewModel.setOrbit8dConfig(true, speed = it, depth = orbitDepth) }
                                    )
                                    StudioSliderRow(
                                        label = "8D Spatial Depth",
                                        valueLabel = "${(orbitDepth * 100).toInt()}%",
                                        value = orbitDepth,
                                        valueRange = 0.1f..1.0f,
                                        colors = colors,
                                        onValueChange = { viewModel.setOrbit8dConfig(true, speed = orbitSpeed, depth = it) }
                                    )
                                }
                            }
                        }
                    }
                }

                // 3. Spatial Routing & TWS Wide Stereo Card
                item(key = "spatial_routing_card") {
                    Surface(
                        shape = RoundedCornerShape(26.dp),
                        color = colors.surface,
                        border = androidx.compose.foundation.BorderStroke(0.75.dp, colors.cardBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(20.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = if (isTws) Icons.Rounded.BluetoothAudio else Icons.Rounded.SpatialAudio,
                                        contentDescription = null,
                                        tint = colors.accentStrong
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "360° Soundstage Mode",
                                        fontFamily = OutfitFontFamily,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 17.sp,
                                        color = colors.textPrimary
                                    )
                                }
                                Switch(
                                    checked = spatialConfig.isEnabled && !globalBypass,
                                    enabled = !globalBypass,
                                    onCheckedChange = {
                                        viewModel.updateSpatialConfig(spatialConfig.copy(isEnabled = it))
                                    },
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = colors.onAccentStrong,
                                        checkedTrackColor = colors.accentStrong
                                    )
                                )
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                val modes = listOf(
                                    Triple(SpatialMode.HEADPHONES, "Binaural", Icons.Rounded.Headphones),
                                    Triple(SpatialMode.CINEMA_360, "Dynamic", Icons.Rounded.SurroundSound),
                                    Triple(SpatialMode.SPEAKERS, "Speakers", Icons.Rounded.Speaker)
                                )
                                modes.forEach { (mode, title, icon) ->
                                    val selected = spatialConfig.mode == mode
                                    Surface(
                                        onClick = { viewModel.updateSpatialConfig(spatialConfig.copy(mode = mode, isEnabled = true)) },
                                        enabled = !globalBypass,
                                        shape = RoundedCornerShape(16.dp),
                                        color = if (selected) colors.accentPill else colors.surfaceTinted,
                                        border = androidx.compose.foundation.BorderStroke(
                                            1.dp,
                                            if (selected) colors.accentStrong else colors.cardBorder
                                        ),
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(48.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 8.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.Center
                                        ) {
                                            Icon(
                                                imageVector = icon,
                                                contentDescription = null,
                                                tint = if (selected) colors.onAccentPill else colors.textSecondary,
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(
                                                text = title,
                                                fontFamily = PlusJakartaFontFamily,
                                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                                fontSize = 11.sp,
                                                color = if (selected) colors.onAccentPill else colors.textSecondary,
                                                maxLines = 1
                                            )
                                        }
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(14.dp))

                            StudioSliderRow(
                                label = "Soundstage Width",
                                valueLabel = "${(spatialConfig.soundstageWidth * 100).toInt()}%",
                                value = spatialConfig.soundstageWidth,
                                valueRange = 0f..1f,
                                enabled = !globalBypass && spatialConfig.isEnabled,
                                colors = colors,
                                onValueChange = { viewModel.updateSpatialConfig(spatialConfig.copy(soundstageWidth = it)) }
                            )

                            StudioSliderRow(
                                label = "Stereo Widening",
                                valueLabel = "${(spatialConfig.stereoWidening * 100).toInt()}%",
                                value = spatialConfig.stereoWidening,
                                valueRange = 0f..1f,
                                enabled = !globalBypass && spatialConfig.isEnabled,
                                colors = colors,
                                onValueChange = { viewModel.updateSpatialConfig(spatialConfig.copy(stereoWidening = it)) }
                            )

                            Spacer(modifier = Modifier.height(6.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "TWS Dual-Earbud Protection",
                                        fontFamily = PlusJakartaFontFamily,
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 13.sp,
                                        color = colors.textPrimary
                                    )
                                    Text(
                                        text = "Forces binaural mode so wireless earbuds keep equal L/R signal",
                                        fontFamily = PlusJakartaFontFamily,
                                        fontSize = 11.sp,
                                        color = colors.textSecondary
                                    )
                                }
                                Button(
                                    onClick = { viewModel.forceBalanceDualEarbuds() },
                                    shape = RoundedCornerShape(16.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = colors.accentPill,
                                        contentColor = colors.onAccentPill
                                    ),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                    modifier = Modifier.height(40.dp)
                                ) {
                                    Icon(Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.size(15.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Balance", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }

                // 4. Hardware DSP Dynamics: Bass Boost & Virtualizer
                item(key = "hw_dynamics_card") {
                    Surface(
                        shape = RoundedCornerShape(26.dp),
                        color = colors.surface,
                        border = androidx.compose.foundation.BorderStroke(0.75.dp, colors.cardBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(20.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Rounded.GraphicEq, contentDescription = null, tint = colors.accentStrong)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Bass & Virtualizer DSP",
                                    fontFamily = OutfitFontFamily,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 17.sp,
                                    color = colors.textPrimary
                                )
                            }

                            Spacer(modifier = Modifier.height(14.dp))

                            StudioSliderRow(
                                label = if (isBassSupported) "Bass Boost" else "Bass Boost (Unsupported on device)",
                                valueLabel = "${bassBoost / 10}%",
                                value = bassBoost.toFloat(),
                                valueRange = 0f..1000f,
                                enabled = !globalBypass && isBassSupported,
                                colors = colors,
                                onValueChange = { viewModel.setBassBoost(it.toInt()) }
                            )

                            StudioSliderRow(
                                label = if (isVirtSupported) "Surround Virtualizer" else "Virtualizer (Unsupported on device)",
                                valueLabel = "${virtualizer / 10}%",
                                value = virtualizer.toFloat(),
                                valueRange = 0f..1000f,
                                enabled = !globalBypass && isVirtSupported,
                                colors = colors,
                                onValueChange = { viewModel.setVirtualizer(it.toInt()) }
                            )
                        }
                    }
                }

                // 5. Acoustic Reverb Presets
                item(key = "reverb_card") {
                    Surface(
                        shape = RoundedCornerShape(26.dp),
                        color = colors.surface,
                        border = androidx.compose.foundation.BorderStroke(0.75.dp, colors.cardBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(20.dp)) {
                            Text(
                                text = "Acoustic Room Reverb",
                                fontFamily = OutfitFontFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = 17.sp,
                                color = colors.textPrimary
                            )
                            Text(
                                text = if (isReverbSupported) "Simulate architectural reflections cleanly"
                                else "PresetReverb unavailable in current AudioFlinger session",
                                fontFamily = PlusJakartaFontFamily,
                                fontSize = 12.sp,
                                color = colors.textSecondary
                            )

                            Spacer(modifier = Modifier.height(12.dp))

                            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                items(ReverbPresets, key = { it.preset }) { item ->
                                    val selected = reverbPreset == item.preset
                                    Surface(
                                        onClick = { viewModel.setReverb(item.preset) },
                                        enabled = !globalBypass,
                                        shape = RoundedCornerShape(18.dp),
                                        color = if (selected) colors.accentPill else colors.surfaceTinted,
                                        border = androidx.compose.foundation.BorderStroke(
                                            1.dp,
                                            if (selected) colors.accentStrong else colors.cardBorder
                                        ),
                                        modifier = Modifier.height(42.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier.padding(horizontal = 14.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = item.label,
                                                fontFamily = PlusJakartaFontFamily,
                                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                                fontSize = 12.sp,
                                                color = if (selected) colors.onAccentPill else colors.textPrimary
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // 6. Real-Time Engine & Safety Fallback Status
                item(key = "capability_note") {
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = colors.surfaceTinted,
                        border = androidx.compose.foundation.BorderStroke(0.75.dp, colors.cardBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Info,
                                contentDescription = null,
                                tint = colors.accentStrong,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = dspDiagnostic ?: "Real-time stereo floating-point PCM DSP active on SONORA's single audio pipeline with automatic phase-cancellation protection and seamless bypass.",
                                fontFamily = PlusJakartaFontFamily,
                                fontSize = 12.sp,
                                color = colors.textSecondary
                            )
                        }
                    }
                }

                item { Spacer(modifier = Modifier.height(24.dp)) }
            }
        }
    }
}

@Composable
private fun StudioSliderRow(
    label: String,
    valueLabel: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    colors: ui.theme.EditorialColors,
    enabled: Boolean = true,
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
                color = if (enabled) colors.textPrimary else colors.textMuted
            )
            Text(
                text = valueLabel,
                fontFamily = OutfitFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
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
