package ui.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AcUnit
import androidx.compose.material.icons.rounded.BatterySaver
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.OutfitFontFamily
import com.example.ui.theme.PlusJakartaFontFamily
import com.example.viewmodel.SonoraViewModel
import data.preferences.SnowfallFlakeSize
import data.preferences.SnowfallIntensity
import data.preferences.SnowfallSettings
import data.preferences.SnowfallSpeed
import ui.components.SnowfallOverlay
import ui.theme.EditorialColors
import ui.theme.LocalEditorialColors
import kotlin.math.roundToInt

/**
 * Settings → Appearance → Ambient Effects
 * Configures SONORA's Weather-Synced Ambient Effects Engine (5 fixed Odisha locations,
 * Open-Meteo public weather, Automatic / Manual / Off modes, and visual/performance controls).
 */
@Composable
fun AmbientEffectsSettingsScreen(
    viewModel: SonoraViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalEditorialColors.current
    val weatherSettings by viewModel.weatherAmbientSettings.collectAsState()
    val weatherFetchState by viewModel.weatherFetchState.collectAsState()
    val locationSummaries by viewModel.locationWeatherSummaries.collectAsState()

    androidx.compose.runtime.LaunchedEffect(Unit) {
        viewModel.prefetchAllOdishaLocationWeather()
    }

    WeatherAmbientSettingsContent(
        settings = weatherSettings,
        fetchState = weatherFetchState,
        locationSummaries = locationSummaries,
        colors = colors,
        onSelectLocation = { location -> viewModel.selectWeatherLocation(location) },
        onUpdateSettings = { transform -> viewModel.updateWeatherAmbientSettings(transform) },
        onRefreshWeather = { viewModel.refreshSelectedWeather(force = true) },
        onResetSettings = { viewModel.resetWeatherAmbientSettings() },
        onBack = onBack,
        modifier = modifier
    )
}

@Composable
fun AmbientEffectsSettingsContent(
    settings: SnowfallSettings,
    colors: EditorialColors,
    onUpdateSettings: ((SnowfallSettings) -> SnowfallSettings) -> Unit,
    onResetSettings: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler(onBack = onBack)

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(colors.backgroundBrush)
            .statusBarsPadding()
            .navigationBarsPadding()
            .testTag("ambient_effects_settings_screen"),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 760.dp)
        ) {
            // Top Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = CircleShape,
                    color = colors.surface,
                    shadowElevation = 2.dp,
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .clickable(onClick = onBack)
                        .testTag("ambient_effects_back_button")
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = "Back to Settings",
                            tint = colors.textPrimary
                        )
                    }
                }
                Spacer(modifier = Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Ambient Effects",
                        fontFamily = OutfitFontFamily,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 24.sp,
                        color = colors.textPrimary
                    )
                    Text(
                        text = "Atmospheric snowfall for Music Home",
                        fontFamily = PlusJakartaFontFamily,
                        fontSize = 12.sp,
                        color = colors.textSecondary
                    )
                }
                TextButton(
                    onClick = onResetSettings,
                    modifier = Modifier.testTag("ambient_effects_reset_button")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Refresh,
                        contentDescription = null,
                        tint = colors.accentStrong,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Reset",
                        fontFamily = PlusJakartaFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.5.sp,
                        color = colors.accentStrong
                    )
                }
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Live atmospheric preview card
                item(key = "snowfall_preview") {
                    Surface(
                        shape = RoundedCornerShape(24.dp),
                        color = colors.surface,
                        border = BorderStroke(0.75.dp, colors.cardBorder),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(164.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(colors.backgroundBrush)
                        ) {
                            SnowfallOverlay(
                                isActive = settings.enabled,
                                settings = settings,
                                colors = colors,
                                screenVisible = true,
                                testTagName = "snowfall_settings_preview_canvas"
                            )
                            Column(
                                modifier = Modifier
                                    .align(Alignment.BottomStart)
                                    .padding(18.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = colors.accentPill.copy(alpha = 0.88f)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.AcUnit,
                                            contentDescription = null,
                                            tint = colors.onAccentPill,
                                            modifier = Modifier.size(13.dp)
                                        )
                                        Text(
                                            text = if (settings.enabled) "Live Preview • ${settings.intensity.label}" else "Snowfall Off",
                                            fontFamily = PlusJakartaFontFamily,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 11.sp,
                                            color = colors.onAccentPill
                                        )
                                    }
                                }
                                Text(
                                    text = "SONORA Music Home",
                                    fontFamily = OutfitFontFamily,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 18.sp,
                                    color = colors.textPrimary
                                )
                                Text(
                                    text = "Offline Music: Always gentle • Online Music: While playing • Online Video: Off",
                                    fontFamily = PlusJakartaFontFamily,
                                    fontSize = 11.5.sp,
                                    color = colors.textSecondary
                                )
                            }
                        }
                    }
                }

                // Master Toggle & Controls Card
                item(key = "snowfall_controls") {
                    Surface(
                        shape = RoundedCornerShape(22.dp),
                        color = colors.surface,
                        border = BorderStroke(0.75.dp, colors.cardBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(18.dp),
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Snowfall Effect",
                                        fontFamily = OutfitFontFamily,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 16.sp,
                                        color = colors.textPrimary
                                    )
                                    Text(
                                        text = "Soft multi-depth snowflakes behind Music Home",
                                        fontFamily = PlusJakartaFontFamily,
                                        fontSize = 12.sp,
                                        color = colors.textSecondary
                                    )
                                }
                                Switch(
                                    checked = settings.enabled,
                                    onCheckedChange = { enabled ->
                                        onUpdateSettings { it.copy(enabled = enabled) }
                                    },
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = colors.onAccentStrong,
                                        checkedTrackColor = colors.accentStrong
                                    ),
                                    modifier = Modifier.testTag("ambient_snowfall_toggle")
                                )
                            }

                            HorizontalDivider(color = colors.cardBorder)

                            // Intensity
                            SnowfallOptionRow(
                                title = "Intensity",
                                subtitle = "Number of drifting snowflakes",
                                options = SnowfallIntensity.entries.map { it to it.label },
                                selected = settings.intensity,
                                enabled = settings.enabled,
                                tagPrefix = "snowfall_intensity",
                                colors = colors,
                                onSelect = { value ->
                                    onUpdateSettings { it.copy(intensity = value) }
                                }
                            )

                            // Snowflake Size
                            SnowfallOptionRow(
                                title = "Snowflake Size",
                                subtitle = "Visual scale across depth layers",
                                options = SnowfallFlakeSize.entries.map { it to it.label },
                                selected = settings.flakeSize,
                                enabled = settings.enabled,
                                tagPrefix = "snowfall_size",
                                colors = colors,
                                onSelect = { value ->
                                    onUpdateSettings { it.copy(flakeSize = value) }
                                }
                            )

                            // Fall Speed
                            SnowfallOptionRow(
                                title = "Fall Speed",
                                subtitle = "Gentle descent and breeze drift speed",
                                options = SnowfallSpeed.entries.map { it to it.label },
                                selected = settings.speed,
                                enabled = settings.enabled,
                                tagPrefix = "snowfall_speed",
                                colors = colors,
                                onSelect = { value ->
                                    onUpdateSettings { it.copy(speed = value) }
                                }
                            )

                            // Opacity Slider
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Opacity",
                                        fontFamily = OutfitFontFamily,
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 14.sp,
                                        color = colors.textPrimary
                                    )
                                    Text(
                                        text = "${(settings.clampedOpacity * 100f).roundToInt()}%",
                                        fontFamily = PlusJakartaFontFamily,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.5.sp,
                                        color = colors.accentStrong
                                    )
                                }
                                Slider(
                                    value = settings.clampedOpacity,
                                    onValueChange = { nextAlpha ->
                                        onUpdateSettings { it.copy(opacity = nextAlpha.coerceIn(0.12f, 0.85f)) }
                                    },
                                    valueRange = 0.12f..0.85f,
                                    enabled = settings.enabled,
                                    colors = SliderDefaults.colors(
                                        thumbColor = colors.accentStrong,
                                        activeTrackColor = colors.accentStrong,
                                        inactiveTrackColor = colors.cardBorder
                                    ),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("snowfall_opacity_slider")
                                )
                            }

                            HorizontalDivider(color = colors.cardBorder)

                            // Reduce animations for battery saving
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.BatterySaver,
                                    contentDescription = null,
                                    tint = colors.accentStrong,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Reduce Animations for Battery Saving",
                                        fontFamily = OutfitFontFamily,
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 14.sp,
                                        color = colors.textPrimary
                                    )
                                    Text(
                                        text = "Lowers flake density, drift speed, and frame cadence",
                                        fontFamily = PlusJakartaFontFamily,
                                        fontSize = 11.5.sp,
                                        color = colors.textSecondary
                                    )
                                }
                                Switch(
                                    checked = settings.reduceAnimationsForBattery,
                                    onCheckedChange = { reduce ->
                                        onUpdateSettings { it.copy(reduceAnimationsForBattery = reduce) }
                                    },
                                    enabled = settings.enabled,
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = colors.onAccentStrong,
                                        checkedTrackColor = colors.accentStrong
                                    ),
                                    modifier = Modifier.testTag("ambient_snowfall_battery_toggle")
                                )
                            }
                        }
                    }
                }

                item(key = "ambient_spacer") {
                    Spacer(modifier = Modifier.height(64.dp))
                }
            }
        }
    }
}

@Composable
private fun <T> SnowfallOptionRow(
    title: String,
    subtitle: String,
    options: List<Pair<T, String>>,
    selected: T,
    enabled: Boolean,
    tagPrefix: String,
    colors: EditorialColors,
    onSelect: (T) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Column {
            Text(
                text = title,
                fontFamily = OutfitFontFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp,
                color = if (enabled) colors.textPrimary else colors.textMuted
            )
            Text(
                text = subtitle,
                fontFamily = PlusJakartaFontFamily,
                fontSize = 11.5.sp,
                color = colors.textSecondary
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            options.forEach { (value, label) ->
                val isSelected = value == selected
                Surface(
                    onClick = { if (enabled) onSelect(value) },
                    enabled = enabled,
                    shape = RoundedCornerShape(14.dp),
                    color = when {
                        !enabled -> colors.surfaceTinted.copy(alpha = 0.45f)
                        isSelected -> colors.accentPill
                        else -> colors.surfaceTinted
                    },
                    border = BorderStroke(
                        width = if (isSelected && enabled) 1.dp else 0.75.dp,
                        color = if (isSelected && enabled) colors.accentStrong else colors.cardBorder
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .height(42.dp)
                        .testTag("${tagPrefix}_${label.lowercase()}")
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = label,
                            fontFamily = PlusJakartaFontFamily,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            fontSize = 12.5.sp,
                            color = when {
                                !enabled -> colors.textMuted
                                isSelected -> colors.onAccentPill
                                else -> colors.textSecondary
                            }
                        )
                    }
                }
            }
        }
    }
}
