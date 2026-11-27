package ui.settings

import androidx.activity.compose.BackHandler
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
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.Celebration
import androidx.compose.material.icons.rounded.CloudQueue
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.TouchApp
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.OutfitFontFamily
import com.example.ui.theme.PlusJakartaFontFamily
import com.example.viewmodel.SonoraViewModel
import domain.festival.ActiveFestivalResolution
import domain.pet.MayurAnimationQuality
import domain.pet.MayurDanceIntensity
import domain.pet.MayurPetSettings
import domain.weather.WeatherAtmosphereEngine
import domain.weather.WeatherAtmospherePlan
import domain.weather.WeatherSurfaceRole
import ui.pet.MayurVirtualPet
import ui.theme.EditorialColors
import ui.theme.LocalEditorialColors

/**
 * Full settings screen for `Settings -> Virtual Pet -> Mayur`.
 */
@Composable
fun MayurVirtualPetSettingsScreen(
    viewModel: SonoraViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val settings by viewModel.virtualPetSettings.collectAsState()
    val currentSong by viewModel.currentSong.collectAsState()
    val playbackInfo by viewModel.playbackStatusSummary.collectAsState()
    val weatherSettings by viewModel.weatherAmbientSettings.collectAsState()
    val weatherFetchState by viewModel.weatherFetchState.collectAsState()
    val activeFestival by viewModel.activeFestivalResolution.collectAsState()
    val reduceAnimations by viewModel.reduceAnimations.collectAsState()

    val previewWeatherPlan = remember(weatherSettings, weatherFetchState.latestSnapshot) {
        WeatherAtmosphereEngine.resolvePlan(
            settings = weatherSettings,
            snapshot = weatherFetchState.latestSnapshot,
            surfaceRole = WeatherSurfaceRole.SettingsPreview
        )
    }

    MayurVirtualPetSettingsContent(
        settings = settings,
        currentSong = currentSong,
        isPlaying = playbackInfo.isPlaying,
        visualizerFrequenciesFlow = viewModel.visualizerFrequencies,
        waveformSamplesFlow = viewModel.waveformSamples,
        weatherPlan = previewWeatherPlan,
        weatherSnapshot = weatherFetchState.latestSnapshot,
        activeFestival = activeFestival,
        reduceAnimations = reduceAnimations,
        onUpdateSettings = { transform -> viewModel.updateVirtualPetSettings(transform) },
        onResetDefaults = { viewModel.resetVirtualPetSettings() },
        onBack = onBack,
        modifier = modifier
    )
}

@Composable
fun MayurVirtualPetSettingsContent(
    settings: MayurPetSettings,
    currentSong: com.example.model.Song?,
    isPlaying: Boolean,
    visualizerFrequenciesFlow: kotlinx.coroutines.flow.StateFlow<FloatArray>?,
    waveformSamplesFlow: kotlinx.coroutines.flow.StateFlow<FloatArray>?,
    weatherPlan: WeatherAtmospherePlan,
    weatherSnapshot: domain.weather.WeatherSnapshot?,
    activeFestival: ActiveFestivalResolution,
    reduceAnimations: Boolean,
    onUpdateSettings: ((MayurPetSettings) -> MayurPetSettings) -> Unit,
    onResetDefaults: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler(onBack = onBack)
    val colors = LocalEditorialColors.current
    var previewReactionHint by remember {
        mutableStateOf("Drag to spin Mayur 360° • Tap head, body or tail • Double-tap or long-press")
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(colors.backgroundBrush)
            .statusBarsPadding()
            .navigationBarsPadding()
            .testTag("mayur_virtual_pet_settings_screen"),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 720.dp)
        ) {
            // Top bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("mayur_pet_settings_back")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = "Back",
                        tint = colors.textPrimary
                    )
                }
                Spacer(modifier = Modifier.width(6.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Mayur Virtual Pet",
                        fontFamily = OutfitFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp,
                        color = colors.textPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "Interactive Home companion • 100% local & private",
                        fontFamily = PlusJakartaFontFamily,
                        fontSize = 12.sp,
                        color = colors.textSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("mayur_pet_settings_list"),
                contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 4.dp, bottom = 36.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Live interactive preview card
                item(key = "pet_preview_card") {
                    Surface(
                        shape = RoundedCornerShape(24.dp),
                        color = colors.surface.copy(alpha = 0.88f),
                        border = BorderStroke(1.dp, colors.cardBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            if (settings.enabled) {
                                MayurVirtualPet(
                                    settings = settings,
                                    currentSong = currentSong,
                                    isPlaying = isPlaying,
                                    visualizerFrequenciesFlow = visualizerFrequenciesFlow,
                                    waveformSamplesFlow = waveformSamplesFlow,
                                    weatherPlan = weatherPlan,
                                    weatherSnapshot = weatherSnapshot,
                                    activeFestival = activeFestival,
                                    reduceAnimations = reduceAnimations,
                                    screenVisible = true,
                                    size = 152.dp,
                                    showStatusPill = true,
                                    onReactionTriggered = { event ->
                                        previewReactionHint = "Reaction: ${event.expression.label} (${event.zone.name.lowercase().replaceFirstChar { it.uppercase() }})"
                                    }
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = previewReactionHint,
                                    fontFamily = PlusJakartaFontFamily,
                                    fontSize = 12.sp,
                                    color = colors.textSecondary,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            } else {
                                Text(
                                    text = "Mayur is hidden on the Home screen",
                                    fontFamily = OutfitFontFamily,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 15.sp,
                                    color = colors.textPrimary,
                                    modifier = Modifier.padding(vertical = 18.dp)
                                )
                            }
                        }
                    }
                }

                // Core Controls card
                item(key = "pet_core_controls") {
                    PetSettingsCard(colors = colors) {
                        PetSwitchItem(
                            title = "Enable Mayur",
                            subtitle = "Show Mayur on the Home screen in Your Mix",
                            checked = settings.enabled,
                            onCheckedChange = { enabled -> onUpdateSettings { it.copy(enabled = enabled) } },
                            colors = colors,
                            testTag = "mayur_setting_enable_switch"
                        )
                        HorizontalDivider(color = colors.cardBorder, modifier = Modifier.padding(vertical = 8.dp))
                        PetSwitchItem(
                            title = "Touch Reactions",
                            subtitle = "Respond to taps on head, headphones, tail, long press & gentle drag",
                            checked = settings.touchReactionsEnabled,
                            enabled = settings.enabled,
                            onCheckedChange = { enabled -> onUpdateSettings { it.copy(touchReactionsEnabled = enabled) } },
                            colors = colors,
                            testTag = "mayur_setting_touch_switch"
                        )
                    }
                }

                // Music-Reactive Dancing card
                item(key = "pet_music_dancing") {
                    PetSettingsCard(colors = colors) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Rounded.MusicNote,
                                contentDescription = null,
                                tint = colors.accentStrong,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Music-Reactive Dancing",
                                fontFamily = OutfitFontFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                color = colors.textPrimary
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        PetSwitchItem(
                            title = "Dance to Music",
                            subtitle = "React smoothly to active playback tempo & rhythm (no microphone used)",
                            checked = settings.musicDancingEnabled,
                            enabled = settings.enabled,
                            onCheckedChange = { enabled -> onUpdateSettings { it.copy(musicDancingEnabled = enabled) } },
                            colors = colors,
                            testTag = "mayur_setting_dancing_switch"
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "Dance Intensity",
                            fontFamily = PlusJakartaFontFamily,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 12.5.sp,
                            color = colors.textSecondary
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            MayurDanceIntensity.entries.forEach { intensity ->
                                val selected = settings.danceIntensity == intensity
                                PetOptionChip(
                                    label = intensity.label,
                                    selected = selected,
                                    enabled = settings.enabled && settings.musicDancingEnabled,
                                    colors = colors,
                                    onClick = { onUpdateSettings { it.copy(danceIntensity = intensity) } },
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("mayur_dance_intensity_${intensity.id}")
                                )
                            }
                        }
                    }
                }

                // Nighttime Sleep Schedule card
                item(key = "pet_sleep_schedule") {
                    PetSettingsCard(colors = colors) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Rounded.Bedtime,
                                contentDescription = null,
                                tint = colors.accentStrong,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Nighttime Sleep",
                                fontFamily = OutfitFontFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                color = colors.textPrimary
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        PetSwitchItem(
                            title = "Nighttime Sleep Behavior",
                            subtitle = "Mayur grows sleepy after bedtime and wakes briefly if disturbed",
                            checked = settings.nighttimeSleepEnabled,
                            enabled = settings.enabled,
                            onCheckedChange = { enabled -> onUpdateSettings { it.copy(nighttimeSleepEnabled = enabled) } },
                            colors = colors,
                            testTag = "mayur_setting_sleep_switch"
                        )

                        if (settings.nighttimeSleepEnabled) {
                            HorizontalDivider(color = colors.cardBorder, modifier = Modifier.padding(vertical = 8.dp))
                            TimeStepperRow(
                                label = "Bedtime",
                                formattedTime = MayurPetSettings.formatMinutesAsClock(settings.bedtimeMinutes),
                                enabled = settings.enabled,
                                colors = colors,
                                onDecrease = {
                                    onUpdateSettings {
                                        val next = (it.bedtimeMinutes - 30 + MayurPetSettings.MINUTES_PER_DAY) % MayurPetSettings.MINUTES_PER_DAY
                                        it.copy(bedtimeMinutes = next)
                                    }
                                },
                                onIncrease = {
                                    onUpdateSettings {
                                        val next = (it.bedtimeMinutes + 30) % MayurPetSettings.MINUTES_PER_DAY
                                        it.copy(bedtimeMinutes = next)
                                    }
                                },
                                valueTestTag = "mayur_bedtime_value"
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            TimeStepperRow(
                                label = "Wake-Up Time",
                                formattedTime = MayurPetSettings.formatMinutesAsClock(settings.wakeUpMinutes),
                                enabled = settings.enabled,
                                colors = colors,
                                onDecrease = {
                                    onUpdateSettings {
                                        val next = (it.wakeUpMinutes - 30 + MayurPetSettings.MINUTES_PER_DAY) % MayurPetSettings.MINUTES_PER_DAY
                                        it.copy(wakeUpMinutes = next)
                                    }
                                },
                                onIncrease = {
                                    onUpdateSettings {
                                        val next = (it.wakeUpMinutes + 30) % MayurPetSettings.MINUTES_PER_DAY
                                        it.copy(wakeUpMinutes = next)
                                    }
                                },
                                valueTestTag = "mayur_wakeup_value"
                            )
                        }
                    }
                }

                // Dynamic Costumes card
                item(key = "pet_costumes") {
                    PetSettingsCard(colors = colors) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Rounded.Celebration,
                                contentDescription = null,
                                tint = colors.accentStrong,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Dynamic Costumes",
                                fontFamily = OutfitFontFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                color = colors.textPrimary
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        PetSwitchItem(
                            title = "Weather Costumes",
                            subtitle = "Match existing Ambient Effects weather (rain, sunny, cold, wind, snow)",
                            checked = settings.weatherCostumesEnabled,
                            enabled = settings.enabled,
                            onCheckedChange = { enabled -> onUpdateSettings { it.copy(weatherCostumesEnabled = enabled) } },
                            colors = colors,
                            testTag = "mayur_setting_weather_costumes_switch"
                        )
                        HorizontalDivider(color = colors.cardBorder, modifier = Modifier.padding(vertical = 8.dp))
                        PetSwitchItem(
                            title = "Festival Costumes",
                            subtitle = "Celebrate Diwali, Holi, Rath Yatra, Dussehra, Kartik Purnima & more",
                            checked = settings.festivalCostumesEnabled,
                            enabled = settings.enabled,
                            onCheckedChange = { enabled -> onUpdateSettings { it.copy(festivalCostumesEnabled = enabled) } },
                            colors = colors,
                            testTag = "mayur_setting_festival_costumes_switch"
                        )
                    }
                }

                // Animation Quality card
                item(key = "pet_animation_quality") {
                    PetSettingsCard(colors = colors) {
                        Text(
                            text = "Animation Quality",
                            fontFamily = OutfitFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = colors.textPrimary
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = settings.animationQuality.subtitle,
                            fontFamily = PlusJakartaFontFamily,
                            fontSize = 12.sp,
                            color = colors.textSecondary
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            MayurAnimationQuality.entries.forEach { quality ->
                                PetOptionChip(
                                    label = quality.label,
                                    selected = settings.animationQuality == quality,
                                    enabled = settings.enabled,
                                    colors = colors,
                                    onClick = { onUpdateSettings { it.copy(animationQuality = quality) } },
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("mayur_quality_${quality.id}")
                                )
                            }
                        }
                    }
                }

                // Reset defaults button
                item(key = "pet_reset") {
                    OutlinedButton(
                        onClick = onResetDefaults,
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(1.dp, colors.cardBorder),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .testTag("mayur_pet_reset_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.RestartAlt,
                            contentDescription = null,
                            tint = colors.accentStrong,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Reset Mayur to Defaults",
                            fontFamily = PlusJakartaFontFamily,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.5.sp,
                            color = colors.accentStrong
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PetSettingsCard(
    colors: EditorialColors,
    content: @Composable () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(22.dp),
        color = colors.surface.copy(alpha = 0.88f),
        border = BorderStroke(0.85.dp, colors.cardBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp)
        ) {
            content()
        }
    }
}

@Composable
private fun PetSwitchItem(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    colors: EditorialColors,
    testTag: String,
    enabled: Boolean = true
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(end = 12.dp)
        ) {
            Text(
                text = title,
                fontFamily = OutfitFontFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.5.sp,
                color = if (enabled) colors.textPrimary else colors.textSecondary
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitle,
                fontFamily = PlusJakartaFontFamily,
                fontSize = 12.sp,
                color = colors.textSecondary
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled,
            colors = SwitchDefaults.colors(
                checkedThumbColor = colors.onAccentStrong,
                checkedTrackColor = colors.accentStrong
            ),
            modifier = Modifier.testTag(testTag)
        )
    }
}

@Composable
private fun PetOptionChip(
    label: String,
    selected: Boolean,
    enabled: Boolean,
    colors: EditorialColors,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = if (selected) colors.accentStrong else colors.surfaceTinted,
        modifier = modifier
            .height(48.dp)
            .border(
                width = if (selected) 1.5.dp else 0.75.dp,
                color = if (selected) colors.accentStrong else colors.cardBorder,
                shape = RoundedCornerShape(14.dp)
            )
            .clip(RoundedCornerShape(14.dp))
            .clickable(enabled = enabled, onClick = onClick)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.padding(horizontal = 6.dp)
        ) {
            Text(
                text = label,
                fontFamily = OutfitFontFamily,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold,
                fontSize = 12.sp,
                color = if (selected) colors.onAccentStrong else colors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun TimeStepperRow(
    label: String,
    formattedTime: String,
    enabled: Boolean,
    colors: EditorialColors,
    onDecrease: () -> Unit,
    onIncrease: () -> Unit,
    valueTestTag: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            fontFamily = OutfitFontFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = 14.sp,
            color = if (enabled) colors.textPrimary else colors.textSecondary
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Surface(
                onClick = onDecrease,
                enabled = enabled,
                shape = CircleShape,
                color = colors.surfaceTinted,
                border = BorderStroke(0.75.dp, colors.cardBorder),
                modifier = Modifier.size(48.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Rounded.Remove,
                        contentDescription = "Decrease $label by 30 minutes",
                        tint = colors.textPrimary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
            Text(
                text = formattedTime,
                fontFamily = OutfitFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                color = colors.accentStrong,
                modifier = Modifier
                    .widthIn(min = 76.dp)
                    .testTag(valueTestTag)
            )
            Surface(
                onClick = onIncrease,
                enabled = enabled,
                shape = CircleShape,
                color = colors.surfaceTinted,
                border = BorderStroke(0.75.dp, colors.cardBorder),
                modifier = Modifier.size(48.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Rounded.Add,
                        contentDescription = "Increase $label by 30 minutes",
                        tint = colors.textPrimary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}
