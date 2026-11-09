package com.example.ui.screens

import android.media.audiofx.PresetReverb
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.geometry.Offset
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Balance
import androidx.compose.material.icons.filled.BluetoothConnected
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.SpatialAudio
import androidx.compose.material.icons.filled.Speaker
import androidx.compose.material.icons.filled.SurroundSound
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ElevatedButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.EqualizerBand
import com.example.model.SpatialMode
import com.example.ui.components.SpatialSoundstageView
import com.example.ui.theme.SonoraCardBorder
import com.example.ui.theme.SonoraCyan
import com.example.ui.theme.SonoraCyanDark
import com.example.ui.theme.SonoraGold
import com.example.ui.theme.SonoraGoldDark
import com.example.ui.theme.SonoraOnGold
import com.example.ui.theme.SonoraSurfaceDark
import com.example.ui.theme.SonoraSurfaceElevated
import com.example.ui.theme.SonoraTextMuted
import com.example.ui.theme.SonoraTextPrimary
import com.example.ui.theme.SonoraTextSecondary
import com.example.viewmodel.SonoraViewModel

@Composable
fun EqualizerSpatialScreen(
    viewModel: SonoraViewModel,
    modifier: Modifier = Modifier
) {
    var selectedTab by remember { mutableStateOf(0) }
    val tabs = listOf("15-Band Equalizer", "360° Spatial Audio")

    Column(
        modifier = modifier
            .fillMaxSize()
            .testTag("equalizer_spatial_screen")
    ) {
        // Mode Tabs
        TabRow(
            selectedTabIndex = selectedTab,
            containerColor = SonoraSurfaceDark,
            contentColor = SonoraGold,
            indicator = { tabPositions ->
                TabRowDefaults.SecondaryIndicator(
                    modifier = Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                    color = SonoraGold,
                    height = 3.dp
                )
            },
            divider = {}
        ) {
            tabs.forEachIndexed { index, title ->
                Tab(
                    selected = selectedTab == index,
                    onClick = { selectedTab = index },
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = if (index == 0) Icons.Default.GraphicEq else Icons.Default.SpatialAudio,
                                contentDescription = null,
                                tint = if (selectedTab == index) SonoraGold else SonoraTextSecondary,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = title,
                                color = if (selectedTab == index) SonoraGold else SonoraTextSecondary,
                                fontWeight = if (selectedTab == index) FontWeight.Bold else FontWeight.Medium,
                                fontSize = 13.sp
                            )
                        }
                    }
                )
            }
        }

        if (selectedTab == 0) {
            EqualizerContent(viewModel = viewModel)
        } else {
            SpatialAudioContent(viewModel = viewModel)
        }
    }
}

@Composable
fun EqualizerContent(viewModel: SonoraViewModel) {
    val isEnabled by viewModel.equalizerEnabled.collectAsState()
    val bands by viewModel.equalizerBands.collectAsState()
    val presets by viewModel.presets.collectAsState()
    val currentPresetIdx by viewModel.currentPresetIndex.collectAsState()
    val bassBoost by viewModel.bassBoost.collectAsState()
    val virtualizer by viewModel.virtualizer.collectAsState()
    val reverbPreset by viewModel.reverbPreset.collectAsState()
    val loudnessGainMb by viewModel.loudnessGainMb.collectAsState()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 120.dp)
    ) {
        // Equalizer Header & Master Switch
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Hi-Fi Graphic Equalizer",
                        color = SonoraTextPrimary,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Poweramp-grade DSP precision filters",
                        color = SonoraTextSecondary,
                        fontSize = 12.sp
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = if (isEnabled) "ACTIVE" else "BYPASS",
                        color = if (isEnabled) SonoraGold else SonoraTextMuted,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Switch(
                        checked = isEnabled,
                        onCheckedChange = { viewModel.setEqualizerEnabled(it) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = SonoraGold,
                            checkedTrackColor = SonoraGoldDark.copy(alpha = 0.5f),
                            uncheckedThumbColor = SonoraTextMuted,
                            uncheckedTrackColor = SonoraSurfaceDark
                        ),
                        modifier = Modifier.testTag("eq_master_switch")
                    )
                }
            }
        }

        // Preset Selector Chips Carousel
        item {
            Text(
                text = "DSP PRESETS",
                color = SonoraTextMuted,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp,
                modifier = Modifier.padding(top = 10.dp, bottom = 6.dp)
            )

            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(vertical = 4.dp)
            ) {
                itemsIndexed(presets) { index, preset ->
                    val isSelected = index == currentPresetIdx
                    Surface(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { viewModel.selectPreset(index) },
                        color = if (isSelected) SonoraGold else SonoraSurfaceElevated,
                        tonalElevation = 2.dp
                    ) {
                        Text(
                            text = preset.name,
                            color = if (isSelected) SonoraOnGold else SonoraTextPrimary,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                        )
                    }
                }
            }
        }

        // 15-Band Studio Equalizer Sliders
        item {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp)
                    .clip(RoundedCornerShape(20.dp)),
                color = SonoraSurfaceElevated,
                tonalElevation = 3.dp
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, SonoraCardBorder, RoundedCornerShape(20.dp))
                        .padding(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "15 FREQUENCY BANDS (25Hz - 16kHz)",
                                color = SonoraCyan,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp
                            )
                            Text(
                                text = "ISO 1/3-octave studio grade precision",
                                color = SonoraTextMuted,
                                fontSize = 10.sp
                            )
                        }
                        IconButton(
                            onClick = { viewModel.selectPreset(0) },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(Icons.Default.RestartAlt, contentDescription = "Reset", tint = SonoraGold, modifier = Modifier.size(18.dp))
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Real-time Frequency Response Curve Canvas
                    EqualizerCurveCanvas(
                        bands = bands,
                        isEnabled = isEnabled
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    // 15 Frequency Band Gain Sliders
                    bands.forEach { band ->
                        BandSliderRow(
                            band = band,
                            enabled = isEnabled,
                            onGainChanged = { gainMb ->
                                viewModel.setBandGain(band.bandIndex, gainMb)
                            }
                        )
                    }
                }
            }
        }

        // Studio DSP FX Sliders (Bass Boost, Virtualizer, Loudness, Reverb)
        item {
            Text(
                text = "STUDIO AUDIO PROCESSORS",
                color = SonoraTextMuted,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp,
                modifier = Modifier.padding(top = 10.dp, bottom = 8.dp)
            )

            // Bass Boost Slider
            DspEffectCard(
                title = "Bass Enhancement",
                valueText = "${bassBoost / 10}%",
                sliderValue = bassBoost / 1000f,
                onValueChange = { viewModel.setBassBoost((it * 1000).toInt()) },
                enabled = isEnabled,
                accentColor = SonoraGold
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Virtualizer Slider
            DspEffectCard(
                title = "Stereo Virtualizer",
                valueText = "${virtualizer / 10}%",
                sliderValue = virtualizer / 1000f,
                onValueChange = { viewModel.setVirtualizer((it * 1000).toInt()) },
                enabled = isEnabled,
                accentColor = SonoraCyan
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Loudness Preamp Gain
            DspEffectCard(
                title = "Preamp / Loudness Enhancer",
                valueText = "+${loudnessGainMb / 100} dB",
                sliderValue = loudnessGainMb / 1000f,
                onValueChange = { viewModel.setLoudness((it * 1000).toInt()) },
                enabled = isEnabled,
                accentColor = SonoraGold
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Reverb Room Presets Selector
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp)),
                color = SonoraSurfaceElevated
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, SonoraCardBorder, RoundedCornerShape(16.dp))
                        .padding(14.dp)
                ) {
                    Text(
                        text = "Acoustic Reverb Chamber",
                        color = SonoraTextPrimary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    val reverbTypes = listOf(
                        "Off" to PresetReverb.PRESET_NONE,
                        "Small Room" to PresetReverb.PRESET_SMALLROOM,
                        "Medium Room" to PresetReverb.PRESET_MEDIUMROOM,
                        "Large Hall" to PresetReverb.PRESET_LARGEHALL,
                        "Plate Studio" to PresetReverb.PRESET_PLATE
                    )

                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        itemsIndexed(reverbTypes) { _, (label, presetVal) ->
                            val isSel = reverbPreset == presetVal
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(if (isSel) SonoraCyan else SonoraSurfaceDark)
                                    .clickable { viewModel.setReverb(presetVal) }
                                    .padding(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Text(
                                    text = label,
                                    color = if (isSel) Color.Black else SonoraTextSecondary,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun BandSliderRow(
    band: EqualizerBand,
    enabled: Boolean,
    onGainChanged: (Short) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Frequency label
        Text(
            text = band.centerFreqFormatted,
            color = SonoraTextPrimary,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.width(44.dp)
        )

        // Gain Slider
        val min = band.minLevelMb.toFloat()
        val max = band.maxLevelMb.toFloat()
        val current = band.currentLevelMb.toFloat().coerceIn(min, max)

        Slider(
            value = current,
            onValueChange = { onGainChanged(it.toInt().toShort()) },
            valueRange = min..max,
            enabled = enabled,
            colors = SliderDefaults.colors(
                thumbColor = SonoraGold,
                activeTrackColor = SonoraGold,
                inactiveTrackColor = SonoraSurfaceDark
            ),
            modifier = Modifier
                .weight(1f)
                .testTag("band_slider_${band.centerFreqHz}")
        )

        // Decibels readout
        val db = band.currentGainDb
        val dbStr = if (db >= 0) "+%.1f dB".format(db) else "%.1f dB".format(db)
        Text(
            text = dbStr,
            color = if (db > 0) SonoraGold else if (db < 0) SonoraCyan else SonoraTextMuted,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.End,
            modifier = Modifier.width(52.dp)
        )
    }
}

@Composable
fun EqualizerCurveCanvas(
    bands: List<EqualizerBand>,
    isEnabled: Boolean,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(96.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(SonoraSurfaceDark)
            .border(1.dp, SonoraCardBorder, RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val width = size.width
            val height = size.height
            val midY = height / 2f

            // Reference grid lines (-12dB, 0dB, +12dB)
            drawLine(
                color = SonoraTextMuted.copy(alpha = 0.2f),
                start = Offset(0f, height * 0.15f),
                end = Offset(width, height * 0.15f),
                strokeWidth = 1f
            )
            drawLine(
                color = SonoraCyan.copy(alpha = 0.35f),
                start = Offset(0f, midY),
                end = Offset(width, midY),
                strokeWidth = 1.5f
            )
            drawLine(
                color = SonoraTextMuted.copy(alpha = 0.2f),
                start = Offset(0f, height * 0.85f),
                end = Offset(width, height * 0.85f),
                strokeWidth = 1f
            )

            if (bands.isEmpty()) return@Canvas

            val numBands = bands.size
            val stepX = if (numBands > 1) width / (numBands - 1) else width

            val points = bands.mapIndexed { index, band ->
                val x = index * stepX
                val normalizedGain = if (isEnabled && band.maxLevelMb > band.minLevelMb) {
                    val range = (band.maxLevelMb - band.minLevelMb).toFloat()
                    val cur = (band.currentLevelMb - band.minLevelMb).toFloat()
                    (cur / range).coerceIn(0f, 1f)
                } else {
                    0.5f
                }
                val y = height * 0.85f - (normalizedGain * (height * 0.7f))
                Offset(x, y)
            }

            // Build smooth curved path through points
            val path = Path().apply {
                moveTo(points.first().x, points.first().y)
                for (i in 0 until points.size - 1) {
                    val p0 = points[i]
                    val p1 = points[i + 1]
                    val cx = (p0.x + p1.x) / 2f
                    cubicTo(cx, p0.y, cx, p1.y, p1.x, p1.y)
                }
            }

            // Gradient fill path
            val fillPath = Path().apply {
                addPath(path)
                lineTo(width, height)
                lineTo(0f, height)
                close()
            }

            drawPath(
                path = fillPath,
                brush = Brush.verticalGradient(
                    colors = if (isEnabled) {
                        listOf(SonoraGold.copy(alpha = 0.35f), SonoraCyan.copy(alpha = 0.05f), Color.Transparent)
                    } else {
                        listOf(Color.White.copy(alpha = 0.05f), Color.Transparent)
                    }
                )
            )

            // Draw curve stroke
            drawPath(
                path = path,
                color = if (isEnabled) SonoraGold else SonoraTextMuted,
                style = Stroke(width = 3.dp.toPx())
            )

            // Draw band points
            points.forEach { pt ->
                drawCircle(
                    color = if (isEnabled) SonoraCyan else SonoraTextMuted,
                    radius = 3.5.dp.toPx(),
                    center = pt
                )
            }
        }
    }
}

@Composable
fun DspEffectCard(
    title: String,
    valueText: String,
    sliderValue: Float,
    onValueChange: (Float) -> Unit,
    enabled: Boolean,
    accentColor: Color
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp)),
        color = SonoraSurfaceElevated
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, SonoraCardBorder, RoundedCornerShape(16.dp))
                .padding(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(title, color = SonoraTextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                Text(valueText, color = accentColor, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
            Slider(
                value = sliderValue.coerceIn(0f, 1f),
                onValueChange = onValueChange,
                enabled = enabled,
                colors = SliderDefaults.colors(
                    thumbColor = accentColor,
                    activeTrackColor = accentColor,
                    inactiveTrackColor = SonoraSurfaceDark
                )
            )
        }
    }
}

@Composable
fun SpatialAudioContent(viewModel: SonoraViewModel) {
    val spatialConfig by viewModel.spatialConfig.collectAsState()
    val isTwsConnected by viewModel.isTwsConnected.collectAsState()
    val connectedAudioDeviceName by viewModel.connectedAudioDeviceName.collectAsState()
    var balanceFixedToast by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 120.dp)
    ) {
        // Master 360 Spatial Toggle
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "360° Virtual Spatial Engine",
                        color = SonoraTextPrimary,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Immersive soundstage for stereo tracks",
                        color = SonoraTextSecondary,
                        fontSize = 12.sp
                    )
                }

                Switch(
                    checked = spatialConfig.isEnabled,
                    onCheckedChange = {
                        viewModel.updateSpatialConfig(spatialConfig.copy(isEnabled = it))
                    },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = SonoraCyan,
                        checkedTrackColor = SonoraCyanDark.copy(alpha = 0.5f)
                    ),
                    modifier = Modifier.testTag("spatial_master_switch")
                )
            }
        }

        // Dedicated TWS & Bluetooth Ultra-Wide Audio & Balance Card
        item {
            Spacer(modifier = Modifier.height(10.dp))
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp)),
                color = SonoraSurfaceElevated
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(
                            1.dp,
                            if (isTwsConnected) SonoraCyan else SonoraCardBorder,
                            RoundedCornerShape(20.dp)
                        )
                        .padding(16.dp)
                ) {
                    // Header with device detection status
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = if (isTwsConnected) Icons.Default.BluetoothConnected else Icons.Default.Headphones,
                                contentDescription = null,
                                tint = if (isTwsConnected) SonoraCyan else SonoraGold,
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = if (isTwsConnected) "TWS EARBUDS CONNECTED" else "TWS DUAL-STEREO ENGINE",
                                    color = if (isTwsConnected) SonoraCyan else SonoraTextPrimary,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = connectedAudioDeviceName,
                                    color = SonoraTextSecondary,
                                    fontSize = 11.sp
                                )
                            }
                        }

                        Switch(
                            checked = spatialConfig.twsWideStereoEnabled,
                            onCheckedChange = { viewModel.toggleTwsWideStereo(it) },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.Black,
                                checkedTrackColor = SonoraCyan,
                                uncheckedThumbColor = SonoraTextMuted,
                                uncheckedTrackColor = SonoraSurfaceDark
                            )
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Text(
                        text = "Wider Stereo Audio on TWS: Expands soundstage width using binaural crossfeed. Prevents single-sided audio drops & guarantees 100% balanced stereo output to both earbuds.",
                        color = SonoraTextSecondary,
                        fontSize = 12.sp,
                        lineHeight = 16.sp
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // TWS Stereo Widening Boost Slider
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "TWS Stereo Widening Width",
                            color = SonoraTextPrimary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "${(spatialConfig.twsStereoBoost * 100).toInt()}%",
                            color = SonoraCyan,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Slider(
                        value = spatialConfig.twsStereoBoost.coerceIn(0.5f, 1.5f),
                        onValueChange = { viewModel.setTwsStereoBoost(it) },
                        valueRange = 0.5f..1.5f,
                        enabled = spatialConfig.twsWideStereoEnabled,
                        colors = SliderDefaults.colors(
                            thumbColor = SonoraCyan,
                            activeTrackColor = SonoraCyan,
                            inactiveTrackColor = SonoraSurfaceDark
                        )
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // Quick-Fix Button: Force Balance Left & Right Audio
                    ElevatedButton(
                        onClick = {
                            viewModel.forceBalanceDualEarbuds()
                            balanceFixedToast = true
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.elevatedButtonColors(
                            containerColor = if (balanceFixedToast) SonoraCyan else SonoraGold.copy(alpha = 0.15f),
                            contentColor = if (balanceFixedToast) Color.Black else SonoraGold
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(
                            imageVector = if (balanceFixedToast) Icons.Default.CheckCircle else Icons.Default.Balance,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (balanceFixedToast) "Both Earbuds Balanced & Synchronized!" else "Fix Audio: Force Balanced Left & Right (100%)",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        // Spatial Audio Presets Carousel (11 Audiophile Soundstage Profiles)
        item {
            Text(
                text = "SPATIAL ACOUSTIC PRESETS",
                color = SonoraTextMuted,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp,
                modifier = Modifier.padding(top = 10.dp, bottom = 6.dp)
            )

            val spatialPresets = viewModel.spatialPresets
            val selectedSpatialIdx by viewModel.selectedSpatialPresetIndex.collectAsState()

            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(vertical = 4.dp)
            ) {
                itemsIndexed(spatialPresets) { index, preset ->
                    val isSelected = index == selectedSpatialIdx
                    Surface(
                        modifier = Modifier
                            .width(180.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .clickable { viewModel.selectSpatialPreset(index) },
                        color = if (isSelected) SonoraCyan else SonoraSurfaceElevated,
                        tonalElevation = 2.dp
                    ) {
                        Column(
                            modifier = Modifier
                                .border(1.dp, if (isSelected) SonoraCyan else SonoraCardBorder, RoundedCornerShape(14.dp))
                                .padding(12.dp)
                        ) {
                            Text(
                                text = preset.name,
                                color = if (isSelected) Color.Black else SonoraTextPrimary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                maxLines = 1,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.height(3.dp))
                            Text(
                                text = preset.description,
                                color = if (isSelected) Color.Black.copy(alpha = 0.8f) else SonoraTextSecondary,
                                fontSize = 10.sp,
                                lineHeight = 14.sp,
                                maxLines = 2,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }

        // Listening Mode Selector Chips
        item {
            Text(
                text = "LISTENING PROFILE",
                color = SonoraTextMuted,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp,
                modifier = Modifier.padding(top = 14.dp, bottom = 8.dp)
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                SpatialModeChip(
                    label = "Headphones",
                    icon = Icons.Default.Headphones,
                    isSelected = spatialConfig.mode == SpatialMode.HEADPHONES,
                    onClick = {
                        viewModel.updateSpatialConfig(spatialConfig.copy(mode = SpatialMode.HEADPHONES))
                    },
                    modifier = Modifier.weight(1f)
                )

                SpatialModeChip(
                    label = "Studio Monitors",
                    icon = Icons.Default.Speaker,
                    isSelected = spatialConfig.mode == SpatialMode.SPEAKERS,
                    onClick = {
                        viewModel.updateSpatialConfig(spatialConfig.copy(mode = SpatialMode.SPEAKERS))
                    },
                    modifier = Modifier.weight(1f)
                )

                SpatialModeChip(
                    label = "Cinema 360°",
                    icon = Icons.Default.SurroundSound,
                    isSelected = spatialConfig.mode == SpatialMode.CINEMA_360,
                    onClick = {
                        viewModel.updateSpatialConfig(spatialConfig.copy(mode = SpatialMode.CINEMA_360))
                    },
                    modifier = Modifier.weight(1f)
                )
            }
        }

        // Interactive 360° Circular Soundstage Radar
        item {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp)
                    .clip(RoundedCornerShape(24.dp)),
                color = SonoraSurfaceElevated,
                tonalElevation = 4.dp
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, SonoraCardBorder, RoundedCornerShape(24.dp))
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "INTERACTIVE SOUNDSTAGE RADAR",
                            color = SonoraCyan,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )

                        IconButton(
                            onClick = {
                                viewModel.updateSpatialConfig(spatialConfig.copy(listenerX = 0f, listenerY = 0f))
                            },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(Icons.Default.CenterFocusStrong, contentDescription = "Center Listener", tint = SonoraGold, modifier = Modifier.size(20.dp))
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Drag the golden listener icon to reposition within the virtual acoustic field",
                        color = SonoraTextMuted,
                        fontSize = 11.sp,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    SpatialSoundstageView(
                        spatialConfig = spatialConfig,
                        onPositionChanged = { newX, newY ->
                            viewModel.updateSpatialConfig(spatialConfig.copy(listenerX = newX, listenerY = newY))
                        },
                        modifier = Modifier.size(240.dp)
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Telemetry
                    val widthDeg = (spatialConfig.soundstageWidth * 360).toInt()
                    Text(
                        text = "Soundfield Width: ${widthDeg}° • Pos: (${"%.2f".format(spatialConfig.listenerX)}, ${"%.2f".format(spatialConfig.listenerY)})",
                        color = SonoraTextSecondary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }

        // Spatial Tuning Sliders
        item {
            Text(
                text = "ACOUSTIC SPATIAL CONTROLS",
                color = SonoraTextMuted,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp,
                modifier = Modifier.padding(top = 8.dp, bottom = 8.dp)
            )

            // Soundstage Width Slider
            DspEffectCard(
                title = "Virtual Soundstage Width",
                valueText = "${(spatialConfig.soundstageWidth * 100).toInt()}%",
                sliderValue = spatialConfig.soundstageWidth,
                onValueChange = {
                    viewModel.updateSpatialConfig(spatialConfig.copy(soundstageWidth = it))
                },
                enabled = spatialConfig.isEnabled,
                accentColor = SonoraCyan
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Spatial Intensity Slider
            DspEffectCard(
                title = "Spatial Immersion Intensity",
                valueText = "${(spatialConfig.intensity * 100).toInt()}%",
                sliderValue = spatialConfig.intensity,
                onValueChange = {
                    viewModel.updateSpatialConfig(spatialConfig.copy(intensity = it))
                },
                enabled = spatialConfig.isEnabled,
                accentColor = SonoraGold
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Stereo Widening Slider
            DspEffectCard(
                title = "Stereo Widening (Cross-Feed Matrix)",
                valueText = "${(spatialConfig.stereoWidening * 100).toInt()}%",
                sliderValue = spatialConfig.stereoWidening,
                onValueChange = {
                    viewModel.updateSpatialConfig(spatialConfig.copy(stereoWidening = it))
                },
                enabled = spatialConfig.isEnabled,
                accentColor = SonoraCyan
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Left / Right Channel Balance
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp)),
                color = SonoraSurfaceElevated
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, SonoraCardBorder, RoundedCornerShape(16.dp))
                        .padding(14.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Left / Right Balance", color = SonoraTextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        val bal = spatialConfig.channelBalance
                        val balStr = when {
                            bal < -0.05f -> "L ${(bal * -100).toInt()}%"
                            bal > 0.05f -> "R ${(bal * 100).toInt()}%"
                            else -> "CENTER"
                        }
                        Text(balStr, color = SonoraGold, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }

                    Slider(
                        value = spatialConfig.channelBalance,
                        onValueChange = {
                            viewModel.updateSpatialConfig(spatialConfig.copy(channelBalance = it))
                        },
                        valueRange = -1.0f..1.0f,
                        enabled = spatialConfig.isEnabled,
                        colors = SliderDefaults.colors(
                            thumbColor = SonoraGold,
                            activeTrackColor = SonoraGold,
                            inactiveTrackColor = SonoraSurfaceDark
                        )
                    )
                }
            }
        }
    }
}

@Composable
fun SpatialModeChip(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .clickable { onClick() },
        color = if (isSelected) SonoraCyan else SonoraSurfaceElevated,
        tonalElevation = 2.dp
    ) {
        Column(
            modifier = Modifier
                .border(1.dp, if (isSelected) SonoraCyan else SonoraCardBorder, RoundedCornerShape(14.dp))
                .padding(vertical = 12.dp, horizontal = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = if (isSelected) Color.Black else SonoraTextSecondary,
                modifier = Modifier.size(22.dp)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = label,
                color = if (isSelected) Color.Black else SonoraTextPrimary,
                fontSize = 11.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                textAlign = TextAlign.Center
            )
        }
    }
}
