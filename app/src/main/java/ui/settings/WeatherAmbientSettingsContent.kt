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
import androidx.compose.material.icons.rounded.Air
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.BatterySaver
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.CloudQueue
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Thunderstorm
import androidx.compose.material.icons.rounded.WaterDrop
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.OutfitFontFamily
import com.example.ui.theme.PlusJakartaFontFamily
import data.preferences.WeatherAmbientSettings
import domain.weather.AtmosphereIntensityPreset
import domain.weather.ManualAtmospherePreset
import domain.weather.OdishaWeatherLocation
import domain.weather.SolarPositionCalculator
import domain.weather.WeatherAtmosphereEngine
import domain.weather.WeatherEffectMode
import domain.weather.WeatherFetchState
import domain.weather.WeatherSnapshot
import domain.weather.WeatherSurfaceRole
import ui.components.WeatherAtmosphereOverlay
import ui.components.atmosphereIconForPrimary
import ui.components.weatherIconForCode
import ui.theme.EditorialColors
import java.time.Instant
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

private val IST_TIME_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("hh:mm a")

/**
 * Settings → Appearance → Ambient Effects
 * Complete Weather-Synced Ambient Effects Engine configuration UI for SONORA.
 * Supports 5 fixed Odisha locations (zero GPS, zero location permissions),
 * live Open-Meteo public weather status, Automatic / Manual / Off modes,
 * granular intensity/opacity/sunlight/fireplace threshold controls, and individual toggles.
 */
@Composable
fun WeatherAmbientSettingsContent(
    settings: WeatherAmbientSettings,
    fetchState: WeatherFetchState,
    locationSummaries: Map<String, WeatherSnapshot>,
    colors: EditorialColors,
    onSelectLocation: (OdishaWeatherLocation) -> Unit,
    onUpdateSettings: ((WeatherAmbientSettings) -> WeatherAmbientSettings) -> Unit,
    onRefreshWeather: () -> Unit,
    onResetSettings: () -> Unit,
    onBack: () -> Unit,
    onOpenFestivalAtmosphere: (() -> Unit)? = null,
    festivalAtmosphereSubtitle: String = "Auto • Pan-India Hindu festival effects",
    modifier: Modifier = Modifier
) {
    BackHandler(onBack = onBack)

    val latestSnapshot = fetchState.latestSnapshot
    val previewPlan = remember(settings, latestSnapshot) {
        WeatherAtmosphereEngine.resolvePlan(
            settings = settings,
            snapshot = latestSnapshot,
            surfaceRole = WeatherSurfaceRole.SettingsPreview
        )
    }

    val isEnabled = settings.mode != WeatherEffectMode.OFF

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(colors.backgroundBrush)
            .statusBarsPadding()
            .navigationBarsPadding()
            .testTag("weather_ambient_settings_screen"),
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
                        text = "Weather-synced Odisha atmosphere",
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
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("weather_settings_lazy_column"),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // 1. Live Preview & Weather Status Card
                item(key = "weather_preview_status") {
                    WeatherPreviewStatusCard(
                        settings = settings,
                        fetchState = fetchState,
                        previewPlan = previewPlan,
                        colors = colors,
                        onRefreshWeather = onRefreshWeather
                    )
                }

                // 1b. Festival Atmosphere Entry Card (coexists alongside Weather Atmosphere Engine)
                if (onOpenFestivalAtmosphere != null) {
                    item(key = "festival_atmosphere_entry") {
                        Surface(
                            onClick = onOpenFestivalAtmosphere,
                            shape = RoundedCornerShape(22.dp),
                            color = colors.surface,
                            border = BorderStroke(0.9.dp, colors.accentStrong.copy(alpha = 0.40f)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("open_festival_atmosphere_settings")
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 18.dp, vertical = 16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Surface(
                                    shape = CircleShape,
                                    color = colors.accentPill,
                                    modifier = Modifier.size(44.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text(text = "🪔", fontSize = 20.sp)
                                    }
                                }
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Festival Atmosphere",
                                        fontFamily = OutfitFontFamily,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 16.sp,
                                        color = colors.textPrimary
                                    )
                                    Text(
                                        text = festivalAtmosphereSubtitle,
                                        fontFamily = PlusJakartaFontFamily,
                                        fontSize = 12.sp,
                                        color = colors.textSecondary,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                Icon(
                                    imageVector = Icons.Rounded.AutoAwesome,
                                    contentDescription = "Open Festival Atmosphere Settings",
                                    tint = colors.accentStrong,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }

                // 2. Weather Mode Selector (Automatic / Manual / Off)
                item(key = "weather_mode_card") {
                    WeatherModeCard(
                        selectedMode = settings.mode,
                        colors = colors,
                        onSelectMode = { mode ->
                            onUpdateSettings { it.copy(mode = mode) }
                        }
                    )
                }

                // 3. Manual Atmosphere Presets (shown when Manual mode selected)
                if (settings.mode == WeatherEffectMode.MANUAL) {
                    item(key = "manual_presets_card") {
                        ManualAtmospherePresetsCard(
                            selectedPreset = settings.manualPreset,
                            colors = colors,
                            onSelectPreset = { preset ->
                                onUpdateSettings { it.copy(manualPreset = preset) }
                            }
                        )
                    }
                }

                // 4. Weather Location (5 Fixed Odisha Locations)
                item(key = "weather_locations_header") {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp)
                            .testTag("weather_locations_section")
                    ) {
                        Text(
                            text = "Weather Location",
                            fontFamily = OutfitFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            color = colors.textPrimary
                        )
                        Text(
                            text = "Select one of five fixed Odisha towns (no GPS or location tracking)",
                            fontFamily = PlusJakartaFontFamily,
                            fontSize = 12.sp,
                            color = colors.textSecondary
                        )
                    }
                }
                OdishaWeatherLocation.entries.forEach { location ->
                    item(key = "weather_location_${location.id}") {
                        OdishaWeatherLocationCardItem(
                            location = location,
                            isSelected = settings.selectedLocation == location,
                            summary = locationSummaries[location.id],
                            colors = colors,
                            onSelectLocation = { onSelectLocation(location) }
                        )
                    }
                }

                // 5. Visual & Performance Controls
                item(key = "visual_performance_card") {
                    VisualAndPerformanceCard(
                        settings = settings,
                        enabled = isEnabled,
                        colors = colors,
                        onUpdateSettings = onUpdateSettings
                    )
                }

                // 6. Individual Weather Effects Toggles
                item(key = "individual_effects_card") {
                    IndividualEffectsCard(
                        settings = settings,
                        enabled = isEnabled,
                        colors = colors,
                        onUpdateSettings = onUpdateSettings
                    )
                }

                // 7. Attribution & Privacy Guarantee Footer
                item(key = "weather_attribution_card") {
                    WeatherAttributionPrivacyCard(colors = colors)
                }

                item(key = "ambient_bottom_spacer") {
                    Spacer(modifier = Modifier.height(64.dp))
                }
            }
        }
    }
}

@Composable
private fun WeatherPreviewStatusCard(
    settings: WeatherAmbientSettings,
    fetchState: WeatherFetchState,
    previewPlan: domain.weather.WeatherAtmospherePlan,
    colors: EditorialColors,
    onRefreshWeather: () -> Unit
) {
    val snapshot = fetchState.latestSnapshot
    val isLoading = fetchState is WeatherFetchState.Loading
    val isOfflineCache = (fetchState as? WeatherFetchState.Success)?.isOfflineCache == true ||
        snapshot?.isFromCache == true

    val badgeLabel = when (settings.mode) {
        WeatherEffectMode.OFF -> "Effects Off"
        WeatherEffectMode.MANUAL -> "Simulated • Manual Atmosphere"
        WeatherEffectMode.AUTOMATIC -> when {
            isOfflineCache && snapshot != null -> "Cached Offline Weather"
            snapshot != null -> "Live Weather • Open-Meteo"
            isLoading -> "Updating Live Weather..."
            else -> "Waiting for Weather"
        }
    }

    val badgeIcon = when (settings.mode) {
        WeatherEffectMode.OFF -> Icons.Rounded.CloudOff
        WeatherEffectMode.MANUAL -> Icons.Rounded.AutoAwesome
        WeatherEffectMode.AUTOMATIC -> if (snapshot != null) {
            weatherIconForCode(snapshot.weatherCode, snapshot.isDay)
        } else {
            Icons.Rounded.CloudQueue
        }
    }

    val updatedText = remember(snapshot?.fetchedAtEpochMs) {
        snapshot?.let { snap ->
            val zoned = Instant.ofEpochMilli(snap.fetchedAtEpochMs)
                .atZone(SolarPositionCalculator.INDIA_ZONE)
            IST_TIME_FORMATTER.format(zoned) + " IST"
        } ?: "--"
    }

    Surface(
        shape = RoundedCornerShape(24.dp),
        color = colors.surface,
        border = BorderStroke(0.75.dp, colors.cardBorder),
        modifier = Modifier
            .fillMaxWidth()
            .height(204.dp)
            .testTag("weather_preview_card")
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(colors.backgroundBrush)
        ) {
            WeatherAtmosphereOverlay(
                plan = previewPlan,
                settings = settings,
                colors = colors,
                surfaceRole = WeatherSurfaceRole.SettingsPreview,
                isActive = previewPlan.isActive,
                screenVisible = true,
                modifier = Modifier.fillMaxSize(),
                testTagName = "weather_settings_preview_canvas"
            )

            // Readability scrim at bottom for crisp typography across all weather states
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Transparent,
                                colors.surface.copy(alpha = 0.25f),
                                colors.surface.copy(alpha = 0.82f)
                            )
                        )
                    )
            )

            // Top Row: Badge + Refresh button
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = colors.accentPill.copy(alpha = 0.92f)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = badgeIcon,
                            contentDescription = null,
                            tint = colors.onAccentPill,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = badgeLabel,
                            fontFamily = PlusJakartaFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            color = colors.onAccentPill,
                            modifier = Modifier.testTag("weather_mode_status_badge")
                        )
                    }
                }

                Surface(
                    shape = CircleShape,
                    color = colors.surface.copy(alpha = 0.85f),
                    border = BorderStroke(0.5.dp, colors.cardBorder),
                    modifier = Modifier.size(48.dp)
                ) {
                    IconButton(
                        onClick = onRefreshWeather,
                        enabled = !isLoading,
                        modifier = Modifier
                            .fillMaxSize()
                            .testTag("weather_refresh_button")
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = colors.accentStrong
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Rounded.Refresh,
                                contentDescription = "Refresh weather data",
                                tint = colors.textPrimary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }

            // Bottom Weather Status Summary
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp, vertical = 16.dp)
                    .testTag("weather_status_summary"),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.LocationOn,
                        contentDescription = null,
                        tint = colors.accentStrong,
                        modifier = Modifier.size(15.dp)
                    )
                    Text(
                        text = settings.selectedLocation.fullDisplayName,
                        fontFamily = OutfitFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 17.sp,
                        color = colors.textPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                val primaryStatusLine = when (settings.mode) {
                    WeatherEffectMode.OFF ->
                        "Ambient weather atmosphere is turned off"
                    WeatherEffectMode.MANUAL ->
                        "${settings.manualPreset.label} • Simulated atmosphere"
                    WeatherEffectMode.AUTOMATIC -> when {
                        snapshot != null ->
                            "${snapshot.temperatureC.roundToInt()}°C (Feels ${snapshot.apparentTemperatureC.roundToInt()}°C) • ${snapshot.conditionLabel}"
                        fetchState is WeatherFetchState.Error ->
                            "Weather unavailable offline • Effects paused safely"
                        else ->
                            "Fetching live weather from Open-Meteo..."
                    }
                }
                Text(
                    text = primaryStatusLine,
                    fontFamily = PlusJakartaFontFamily,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp,
                    color = colors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                val detailStatusLine = if (snapshot != null) {
                    "Wind ${snapshot.windSpeedKmh.roundToInt()} km/h • Humidity ${snapshot.relativeHumidityPct}% • Updated $updatedText"
                } else {
                    "Active on Offline Music, Playing Online Music & Now Playing • Always off in Online Video"
                }
                Text(
                    text = detailStatusLine,
                    fontFamily = PlusJakartaFontFamily,
                    fontSize = 11.5.sp,
                    color = colors.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun WeatherModeCard(
    selectedMode: WeatherEffectMode,
    colors: EditorialColors,
    onSelectMode: (WeatherEffectMode) -> Unit
) {
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
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Column {
                Text(
                    text = "Weather Mode",
                    fontFamily = OutfitFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = colors.textPrimary
                )
                Text(
                    text = "Sync automatically with real Odisha weather or pick a simulated preset",
                    fontFamily = PlusJakartaFontFamily,
                    fontSize = 12.sp,
                    color = colors.textSecondary
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val modeItems = listOf(
                    Triple(WeatherEffectMode.AUTOMATIC, "Automatic", "weather_mode_automatic"),
                    Triple(WeatherEffectMode.MANUAL, "Manual", "weather_mode_manual"),
                    Triple(WeatherEffectMode.OFF, "Off", "weather_mode_off")
                )
                modeItems.forEach { (mode, shortLabel, tag) ->
                    val isSelected = selectedMode == mode
                    Surface(
                        onClick = { onSelectMode(mode) },
                        shape = RoundedCornerShape(14.dp),
                        color = if (isSelected) colors.accentPill else colors.surfaceTinted,
                        border = BorderStroke(
                            width = if (isSelected) 1.25.dp else 0.75.dp,
                            color = if (isSelected) colors.accentStrong else colors.cardBorder
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag(tag)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = shortLabel,
                                fontFamily = PlusJakartaFontFamily,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                fontSize = 13.sp,
                                color = if (isSelected) colors.onAccentPill else colors.textSecondary
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ManualAtmospherePresetsCard(
    selectedPreset: ManualAtmospherePreset,
    colors: EditorialColors,
    onSelectPreset: (ManualAtmospherePreset) -> Unit
) {
    Surface(
        shape = RoundedCornerShape(22.dp),
        color = colors.surface,
        border = BorderStroke(0.75.dp, colors.cardBorder),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("manual_atmosphere_presets_card")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Column {
                Text(
                    text = "Manual Atmosphere (Simulated)",
                    fontFamily = OutfitFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = colors.textPrimary
                )
                Text(
                    text = "Simulated visual atmosphere independent of live weather data",
                    fontFamily = PlusJakartaFontFamily,
                    fontSize = 12.sp,
                    color = colors.textSecondary
                )
            }

            val rows = remember { ManualAtmospherePreset.entries.chunked(3) }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                rows.forEach { rowPresets ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        rowPresets.forEach { preset ->
                            val isSelected = selectedPreset == preset
                            val icon = iconForManualPreset(preset)
                            Surface(
                                onClick = { onSelectPreset(preset) },
                                shape = RoundedCornerShape(16.dp),
                                color = if (isSelected) colors.accentPill else colors.surfaceTinted,
                                border = BorderStroke(
                                    width = if (isSelected) 1.25.dp else 0.75.dp,
                                    color = if (isSelected) colors.accentStrong else colors.cardBorder
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(68.dp)
                                    .testTag("manual_preset_${preset.name.lowercase()}")
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(horizontal = 6.dp, vertical = 8.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    Icon(
                                        imageVector = icon,
                                        contentDescription = preset.label,
                                        tint = if (isSelected) colors.onAccentPill else colors.accentStrong,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = preset.label,
                                        fontFamily = PlusJakartaFontFamily,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        fontSize = 11.5.sp,
                                        color = if (isSelected) colors.onAccentPill else colors.textPrimary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun OdishaWeatherLocationCardItem(
    location: OdishaWeatherLocation,
    isSelected: Boolean,
    summary: WeatherSnapshot?,
    colors: EditorialColors,
    onSelectLocation: () -> Unit
) {
    val icon = if (summary != null) {
        weatherIconForCode(summary.weatherCode, summary.isDay)
    } else {
        Icons.Rounded.WbSunny
    }
    val tempText = summary?.let { "${it.temperatureC.roundToInt()}°C" } ?: "--"

    Surface(
        onClick = onSelectLocation,
        shape = RoundedCornerShape(18.dp),
        color = if (isSelected) colors.accentPill.copy(alpha = 0.28f) else colors.surface,
        border = BorderStroke(
            width = if (isSelected) 1.5.dp else 0.75.dp,
            color = if (isSelected) colors.accentStrong else colors.cardBorder
        ),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("weather_location_card_${location.id}")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = CircleShape,
                color = if (isSelected) colors.accentStrong.copy(alpha = 0.18f) else colors.surfaceTinted,
                modifier = Modifier.size(42.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = summary?.conditionLabel ?: location.townName,
                        tint = colors.accentStrong,
                        modifier = Modifier.size(21.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = location.townName,
                    fontFamily = OutfitFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.5.sp,
                    color = colors.textPrimary
                )
                Text(
                    text = "${location.district} District, Odisha" +
                        (summary?.let { " • ${it.conditionLabel}" } ?: ""),
                    fontFamily = PlusJakartaFontFamily,
                    fontSize = 11.5.sp,
                    color = colors.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = tempText,
                fontFamily = OutfitFontFamily,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 16.sp,
                color = if (isSelected) colors.accentStrong else colors.textPrimary
            )
            if (isSelected) {
                Spacer(modifier = Modifier.width(8.dp))
                Icon(
                    imageVector = Icons.Rounded.CheckCircle,
                    contentDescription = "Selected location",
                    tint = colors.accentStrong,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
private fun VisualAndPerformanceCard(
    settings: WeatherAmbientSettings,
    enabled: Boolean,
    colors: EditorialColors,
    onUpdateSettings: ((WeatherAmbientSettings) -> WeatherAmbientSettings) -> Unit
) {
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
            Text(
                text = "Appearance & Performance",
                fontFamily = OutfitFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                color = colors.textPrimary
            )

            // Effect Intensity (Low / Medium / High)
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Effect Intensity",
                    fontFamily = OutfitFontFamily,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                    color = if (enabled) colors.textPrimary else colors.textMuted
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    AtmosphereIntensityPreset.entries.forEach { preset ->
                        val isSelected = settings.effectIntensity == preset
                        Surface(
                            onClick = { if (enabled) onUpdateSettings { it.copy(effectIntensity = preset) } },
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
                                .height(44.dp)
                                .testTag("weather_intensity_${preset.name.lowercase()}")
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = preset.label,
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

            WeatherSliderRow(
                label = "Particle Density",
                valueLabel = "${(settings.clampedParticleDensity * 100f).roundToInt()}%",
                value = settings.clampedParticleDensity,
                valueRange = 0.30f..1.40f,
                enabled = enabled,
                colors = colors,
                testTagName = "weather_particle_density_slider",
                onValueChange = { v -> onUpdateSettings { it.copy(particleDensity = v.coerceIn(0.30f, 1.40f)) } }
            )

            WeatherSliderRow(
                label = "Animation Speed",
                valueLabel = "${(settings.clampedAnimationSpeed * 100f).roundToInt()}%",
                value = settings.clampedAnimationSpeed,
                valueRange = 0.40f..1.60f,
                enabled = enabled,
                colors = colors,
                testTagName = "weather_animation_speed_slider",
                onValueChange = { v -> onUpdateSettings { it.copy(animationSpeed = v.coerceIn(0.40f, 1.60f)) } }
            )

            WeatherSliderRow(
                label = "Background Opacity",
                valueLabel = "${(settings.clampedBackgroundOpacity * 100f).roundToInt()}%",
                value = settings.clampedBackgroundOpacity,
                valueRange = 0.15f..0.95f,
                enabled = enabled,
                colors = colors,
                testTagName = "weather_opacity_slider",
                onValueChange = { v -> onUpdateSettings { it.copy(backgroundOpacity = v.coerceIn(0.15f, 0.95f)) } }
            )

            WeatherSliderRow(
                label = "Sunlight Intensity",
                valueLabel = "${(settings.clampedSunlightIntensity * 100f).roundToInt()}%",
                value = settings.clampedSunlightIntensity,
                valueRange = 0.20f..1.20f,
                enabled = enabled,
                colors = colors,
                testTagName = "weather_sunlight_intensity_slider",
                onValueChange = { v -> onUpdateSettings { it.copy(sunlightIntensity = v.coerceIn(0.20f, 1.20f)) } }
            )

            WeatherSliderRow(
                label = "Cold-Night Fireplace Threshold",
                valueLabel = "≤ ${settings.clampedFireplaceThresholdC}°C",
                value = settings.clampedFireplaceThresholdC.toFloat(),
                valueRange = 4f..22f,
                enabled = enabled && settings.enableFireplace,
                colors = colors,
                testTagName = "weather_fireplace_threshold_slider",
                onValueChange = { v -> onUpdateSettings { it.copy(coldFireplaceThresholdC = v.roundToInt().coerceIn(4, 22)) } }
            )

            HorizontalDivider(color = colors.cardBorder)

            WeatherToggleRow(
                icon = Icons.Rounded.MusicNote,
                title = "Mini-Player Effects",
                subtitle = "Subtle weather backdrop on compatible mini-player styles",
                checked = settings.enableMiniPlayerEffects,
                enabled = enabled,
                colors = colors,
                testTagName = "weather_mini_player_toggle",
                onCheckedChange = { checked -> onUpdateSettings { it.copy(enableMiniPlayerEffects = checked) } }
            )

            WeatherToggleRow(
                icon = Icons.Rounded.BatterySaver,
                title = "Battery Saver Mode",
                subtitle = "Reduces particle counts, drift speed, and caps animation to 30 fps",
                checked = settings.batterySaverMode,
                enabled = enabled,
                colors = colors,
                testTagName = "weather_battery_saver_toggle",
                onCheckedChange = { checked -> onUpdateSettings { it.copy(batterySaverMode = checked) } }
            )
        }
    }
}

@Composable
private fun IndividualEffectsCard(
    settings: WeatherAmbientSettings,
    enabled: Boolean,
    colors: EditorialColors,
    onUpdateSettings: ((WeatherAmbientSettings) -> WeatherAmbientSettings) -> Unit
) {
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
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Column {
                Text(
                    text = "Individual Effects",
                    fontFamily = OutfitFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = colors.textPrimary
                )
                Text(
                    text = "Enable or disable specific weather layers while keeping others active",
                    fontFamily = PlusJakartaFontFamily,
                    fontSize = 12.sp,
                    color = colors.textSecondary
                )
            }

            WeatherToggleRow(
                icon = Icons.Rounded.WaterDrop,
                title = "Rain & Showers",
                subtitle = "Precipitation-scaled rainfall & glass window droplets",
                checked = settings.enableRain,
                enabled = enabled,
                colors = colors,
                testTagName = "weather_effect_toggle_rain",
                onCheckedChange = { c -> onUpdateSettings { it.copy(enableRain = c) } }
            )
            WeatherToggleRow(
                icon = Icons.Rounded.Air,
                title = "Wind & Breeze",
                subtitle = "Wind-direction streaks and blowing particles",
                checked = settings.enableWind,
                enabled = enabled,
                colors = colors,
                testTagName = "weather_effect_toggle_wind",
                onCheckedChange = { c -> onUpdateSettings { it.copy(enableWind = c) } }
            )
            WeatherToggleRow(
                icon = Icons.Rounded.WbSunny,
                title = "Astronomical Sunlight",
                subtitle = "Real sun angle from sunrise to sunset over Odisha",
                checked = settings.enableSunlight,
                enabled = enabled,
                colors = colors,
                testTagName = "weather_effect_toggle_sunlight",
                onCheckedChange = { c -> onUpdateSettings { it.copy(enableSunlight = c) } }
            )
            WeatherToggleRow(
                icon = Icons.Rounded.Cloud,
                title = "Clouds & Overcast",
                subtitle = "Slow drifting sky haze matching real cloud cover",
                checked = settings.enableClouds,
                enabled = enabled,
                colors = colors,
                testTagName = "weather_effect_toggle_clouds",
                onCheckedChange = { c -> onUpdateSettings { it.copy(enableClouds = c) } }
            )
            WeatherToggleRow(
                icon = Icons.Rounded.CloudQueue,
                title = "Fog & Mist",
                subtitle = "Layered soft mist during foggy or hazy conditions",
                checked = settings.enableFog,
                enabled = enabled,
                colors = colors,
                testTagName = "weather_effect_toggle_fog",
                onCheckedChange = { c -> onUpdateSettings { it.copy(enableFog = c) } }
            )
            WeatherToggleRow(
                icon = Icons.Rounded.AcUnit,
                title = "Snowfall",
                subtitle = "Only activates on real snowfall data or Manual Snow",
                checked = settings.enableSnow,
                enabled = enabled,
                colors = colors,
                testTagName = "weather_effect_toggle_snow",
                onCheckedChange = { c -> onUpdateSettings { it.copy(enableSnow = c) } }
            )
            WeatherToggleRow(
                icon = Icons.Rounded.LocalFireDepartment,
                title = "Cold-Night Fireplace",
                subtitle = "Warm hearth glow & embers on chilly nights (≤ threshold)",
                checked = settings.enableFireplace,
                enabled = enabled,
                colors = colors,
                testTagName = "weather_effect_toggle_fireplace",
                onCheckedChange = { c -> onUpdateSettings { it.copy(enableFireplace = c) } }
            )
            WeatherToggleRow(
                icon = Icons.Rounded.Thunderstorm,
                title = "Thunderstorm Atmosphere",
                subtitle = "Storm darkening with occasional gentle distant lightning",
                checked = settings.enableThunderstorm,
                enabled = enabled,
                colors = colors,
                testTagName = "weather_effect_toggle_thunderstorm",
                onCheckedChange = { c -> onUpdateSettings { it.copy(enableThunderstorm = c) } }
            )
        }
    }
}

@Composable
private fun WeatherAttributionPrivacyCard(colors: EditorialColors) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = colors.surfaceTinted,
        border = BorderStroke(0.75.dp, colors.cardBorder),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("weather_attribution_privacy_card")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Shield,
                    contentDescription = null,
                    tint = colors.accentStrong,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = "Privacy-First Weather Sync",
                    fontFamily = OutfitFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.5.sp,
                    color = colors.textPrimary
                )
            }
            Text(
                text = "SONORA never uses GPS, never detects your device location, and never requests location permissions. Only your chosen Odisha town coordinates are sent to fetch public weather.",
                fontFamily = PlusJakartaFontFamily,
                fontSize = 11.5.sp,
                lineHeight = 16.sp,
                color = colors.textSecondary
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Public,
                    contentDescription = null,
                    tint = colors.textSecondary,
                    modifier = Modifier.size(14.dp)
                )
                Text(
                    text = "Public weather data provided by Open-Meteo (open-meteo.com)",
                    fontFamily = PlusJakartaFontFamily,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 11.sp,
                    color = colors.textSecondary
                )
            }
        }
    }
}

@Composable
private fun WeatherSliderRow(
    label: String,
    valueLabel: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    enabled: Boolean,
    colors: EditorialColors,
    testTagName: String,
    onValueChange: (Float) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                fontFamily = OutfitFontFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.5.sp,
                color = if (enabled) colors.textPrimary else colors.textMuted
            )
            Text(
                text = valueLabel,
                fontFamily = PlusJakartaFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp,
                color = if (enabled) colors.accentStrong else colors.textMuted
            )
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            enabled = enabled,
            colors = SliderDefaults.colors(
                thumbColor = colors.accentStrong,
                activeTrackColor = colors.accentStrong,
                inactiveTrackColor = colors.cardBorder
            ),
            modifier = Modifier
                .fillMaxWidth()
                .testTag(testTagName)
        )
    }
}

@Composable
private fun WeatherToggleRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    enabled: Boolean,
    colors: EditorialColors,
    testTagName: String,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (enabled) colors.accentStrong else colors.textMuted,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
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
        Spacer(modifier = Modifier.width(8.dp))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled,
            colors = SwitchDefaults.colors(
                checkedThumbColor = colors.onAccentStrong,
                checkedTrackColor = colors.accentStrong
            ),
            modifier = Modifier.testTag(testTagName)
        )
    }
}

private fun iconForManualPreset(preset: ManualAtmospherePreset): ImageVector = when (preset) {
    ManualAtmospherePreset.RAIN -> Icons.Rounded.WaterDrop
    ManualAtmospherePreset.HEAVY_RAIN -> Icons.Rounded.WaterDrop
    ManualAtmospherePreset.WIND -> Icons.Rounded.Air
    ManualAtmospherePreset.SUNNY -> Icons.Rounded.WbSunny
    ManualAtmospherePreset.CLOUDS -> Icons.Rounded.Cloud
    ManualAtmospherePreset.FOG -> Icons.Rounded.CloudQueue
    ManualAtmospherePreset.SNOW -> Icons.Rounded.AcUnit
    ManualAtmospherePreset.FIREPLACE -> Icons.Rounded.LocalFireDepartment
    ManualAtmospherePreset.THUNDERSTORM -> Icons.Rounded.Thunderstorm
}
