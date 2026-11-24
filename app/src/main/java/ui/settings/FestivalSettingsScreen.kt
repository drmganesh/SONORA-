package ui.settings

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
import androidx.compose.foundation.layout.defaultMinSize
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.BatterySaver
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.CloudDone
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
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
import data.festival.FestivalSyncStatus
import data.festival.FestivalSyncUiState
import data.preferences.FestivalAtmosphereSettings
import domain.festival.ActiveFestivalResolution
import domain.festival.FestivalAtmosphereEngine
import domain.festival.FestivalDensityLevel
import domain.festival.FestivalEffectId
import domain.festival.FestivalEffectRegistry
import domain.festival.FestivalIntensityLevel
import domain.festival.FestivalRegionMode
import domain.festival.FestivalSurfaceRole
import domain.festival.IndianFestivalRegion
import domain.festival.RegionalObservanceRule
import ui.components.FestivalAnimationRenderer
import ui.theme.EditorialColors
import ui.theme.LocalEditorialColors
import java.time.Instant
import java.time.format.DateTimeFormatter

private val FESTIVAL_SYNC_FORMATTER: DateTimeFormatter =
    DateTimeFormatter.ofPattern("dd MMM, hh:mm a")

/**
 * Settings → Appearance → Ambient Effects → Festival Atmosphere
 */
@Composable
fun FestivalSettingsScreen(
    viewModel: SonoraViewModel,
    onBack: () -> Unit,
    onOpenFestivalCalendar: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val colors = LocalEditorialColors.current
    val settings by viewModel.festivalAtmosphereSettings.collectAsState()
    val resolution by viewModel.activeFestivalResolution.collectAsState()
    val syncStatus by viewModel.festivalSyncStatus.collectAsState()
    val syncUiState by viewModel.festivalSyncUiState.collectAsState()

    FestivalSettingsContent(
        settings = settings,
        resolution = resolution,
        syncStatus = syncStatus,
        syncUiState = syncUiState,
        colors = colors,
        onUpdateSettings = { transform -> viewModel.updateFestivalAtmosphereSettings(transform) },
        onRefreshCalendar = { viewModel.refreshFestivalCalendarNow(force = true) },
        onResetSettings = { viewModel.resetFestivalAtmosphereSettings() },
        onOpenFestivalCalendar = onOpenFestivalCalendar,
        onBack = onBack,
        modifier = modifier
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FestivalSettingsContent(
    settings: FestivalAtmosphereSettings,
    resolution: ActiveFestivalResolution,
    syncStatus: FestivalSyncStatus,
    colors: EditorialColors,
    onUpdateSettings: ((FestivalAtmosphereSettings) -> FestivalAtmosphereSettings) -> Unit,
    onRefreshCalendar: () -> Unit,
    onResetSettings: () -> Unit,
    onBack: () -> Unit,
    syncUiState: FestivalSyncUiState = FestivalSyncUiState.Idle,
    onOpenFestivalCalendar: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    BackHandler(onBack = onBack)

    var previewEffectId by remember { mutableStateOf<FestivalEffectId?>(null) }
    var showStatePickerSheet by remember { mutableStateOf(false) }

    val previewPlan = remember(settings, resolution, previewEffectId) {
        FestivalAtmosphereEngine.resolvePlan(
            settings = settings,
            resolution = resolution,
            surfaceRole = FestivalSurfaceRole.SettingsPreview,
            previewOverrideEffect = previewEffectId
        )
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(colors.backgroundBrush)
            .statusBarsPadding()
            .navigationBarsPadding()
            .testTag("festival_settings_screen"),
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
                        .testTag("festival_settings_back_button")
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = "Back to Ambient Effects",
                            tint = colors.textPrimary
                        )
                    }
                }
                Spacer(modifier = Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Festival Atmosphere",
                        fontFamily = OutfitFontFamily,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 23.sp,
                        color = colors.textPrimary
                    )
                    Text(
                        text = "Pan-India Hindu festival calendar & effects",
                        fontFamily = PlusJakartaFontFamily,
                        fontSize = 12.sp,
                        color = colors.textSecondary
                    )
                }
                TextButton(
                    onClick = {
                        previewEffectId = null
                        onResetSettings()
                    },
                    modifier = Modifier.testTag("festival_settings_reset_button")
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
                    .testTag("festival_settings_lazy_column"),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // 1. Live Preview Card
                item(key = "festival_preview_card") {
                    Surface(
                        shape = RoundedCornerShape(24.dp),
                        color = colors.surface,
                        border = BorderStroke(0.85.dp, colors.cardBorder),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(184.dp)
                            .testTag("festival_preview_card")
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(colors.backgroundBrush)
                        ) {
                            FestivalAnimationRenderer(
                                plan = previewPlan,
                                colors = colors,
                                screenVisible = true,
                                modifier = Modifier.fillMaxSize(),
                                testTagName = "festival_settings_preview_canvas"
                            )

                            Column(
                                modifier = Modifier
                                    .align(Alignment.BottomStart)
                                    .padding(18.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = colors.accentPill.copy(alpha = 0.90f)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.AutoAwesome,
                                            contentDescription = null,
                                            tint = colors.onAccentPill,
                                            modifier = Modifier.size(13.dp)
                                        )
                                        val badgeLabel = when {
                                            previewEffectId != null -> "Preview Only • ${FestivalEffectRegistry.get(previewEffectId!!).displayName}"
                                            !settings.enabled -> "Festival Atmosphere Off"
                                            resolution.primary != null -> "Active Today • ${resolution.primary.displayNameForRegion}"
                                            else -> "Automatic • Ready for Next Festival"
                                        }
                                        Text(
                                            text = badgeLabel,
                                            fontFamily = PlusJakartaFontFamily,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 11.sp,
                                            color = colors.onAccentPill,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }

                                Text(
                                    text = when {
                                        previewEffectId != null -> FestivalEffectRegistry.get(previewEffectId!!).displayName
                                        resolution.primary != null -> "${resolution.primary.record.iconGlyph} ${resolution.primary.displayNameForRegion}"
                                        else -> "No Festival Today (${settings.regionMode.label})"
                                    },
                                    fontFamily = OutfitFontFamily,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 18.sp,
                                    color = colors.textPrimary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )

                                Text(
                                    text = if (previewPlan.isActive) {
                                        val fps = if (previewPlan.isStaticReducedMotion) {
                                            "Static (Reduce Motion)"
                                        } else {
                                            "${(1_000_000_000L / previewPlan.minFrameIntervalNanos).coerceAtMost(60)} fps cap"
                                        }
                                        "Max ${previewPlan.particleLimit} particles • $fps • Coexists with Weather"
                                    } else {
                                        "Pick any effect below for a live preview • Zero GPS or personal permissions"
                                    },
                                    fontFamily = PlusJakartaFontFamily,
                                    fontSize = 11.5.sp,
                                    color = colors.textSecondary
                                )
                            }
                        }
                    }
                }

                // 2. Core Controls Card
                item(key = "festival_core_toggles") {
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
                            FestivalToggleRow(
                                title = "Festival Atmosphere",
                                subtitle = "Enable festive visual atmosphere alongside Weather effects",
                                checked = settings.enabled,
                                enabled = true,
                                testTagName = "festival_enabled_toggle",
                                colors = colors,
                                onCheckedChange = { checked ->
                                    onUpdateSettings { it.copy(enabled = checked) }
                                }
                            )

                            HorizontalDivider(color = colors.cardBorder)

                            FestivalToggleRow(
                                title = "Automatic Festival Detection",
                                subtitle = "Activate verified festival atmospheres automatically on matching dates",
                                checked = settings.automaticDetection,
                                enabled = settings.enabled,
                                testTagName = "festival_auto_toggle",
                                colors = colors,
                                onCheckedChange = { checked ->
                                    onUpdateSettings { it.copy(automaticDetection = checked) }
                                }
                            )

                            HorizontalDivider(color = colors.cardBorder)

                            FestivalToggleRow(
                                title = "Show Home Festival Indicator",
                                subtitle = "Display compact festive badge on Home screen only on festival days",
                                checked = settings.showHomeIndicator,
                                enabled = settings.enabled,
                                testTagName = "festival_home_indicator_toggle",
                                colors = colors,
                                onCheckedChange = { checked ->
                                    onUpdateSettings { it.copy(showHomeIndicator = checked) }
                                }
                            )
                        }
                    }
                }

                // 3. Festival Region Card (All 28 States + 8 UTs + All India)
                item(key = "festival_region_card") {
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
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Public,
                                    contentDescription = null,
                                    tint = colors.accentStrong,
                                    modifier = Modifier.size(20.dp)
                                )
                                Column {
                                    Text(
                                        text = "Festival Region",
                                        fontFamily = OutfitFontFamily,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 16.sp,
                                        color = colors.textPrimary
                                    )
                                    Text(
                                        text = "Manual regional selection • No GPS or location access",
                                        fontFamily = PlusJakartaFontFamily,
                                        fontSize = 11.5.sp,
                                        color = colors.textSecondary
                                    )
                                }
                            }

                            FestivalRegionMode.entries.forEach { mode ->
                                val isSelected = settings.regionMode == mode
                                Surface(
                                    onClick = {
                                        onUpdateSettings { it.copy(regionMode = mode) }
                                    },
                                    shape = RoundedCornerShape(14.dp),
                                    color = if (isSelected) colors.accentPill.copy(alpha = 0.22f) else colors.surfaceTinted,
                                    border = BorderStroke(
                                        width = if (isSelected) 1.25.dp else 0.75.dp,
                                        color = if (isSelected) colors.accentStrong else colors.cardBorder
                                    ),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .defaultMinSize(minHeight = 48.dp)
                                        .testTag("festival_region_mode_${mode.id}")
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 14.dp, vertical = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = mode.label,
                                                fontFamily = OutfitFontFamily,
                                                fontWeight = FontWeight.SemiBold,
                                                fontSize = 14.sp,
                                                color = colors.textPrimary
                                            )
                                            Text(
                                                text = mode.subtitle,
                                                fontFamily = PlusJakartaFontFamily,
                                                fontSize = 11.5.sp,
                                                color = colors.textSecondary
                                            )
                                        }
                                        if (isSelected) {
                                            Icon(
                                                imageVector = Icons.Rounded.CheckCircle,
                                                contentDescription = "Selected",
                                                tint = colors.accentStrong,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                    }
                                }
                            }

                            if (settings.regionMode == FestivalRegionMode.ALL_INDIA) {
                                HorizontalDivider(color = colors.cardBorder)
                                FestivalToggleRow(
                                    title = "Include Regional Celebrations",
                                    subtitle = "Also show major regional festivals (e.g., Rath Yatra, Onam, Pongal, Bihu, Nuakhai) in All India mode",
                                    checked = settings.includeRegionalInAllIndia,
                                    enabled = settings.enabled,
                                    testTagName = "festival_include_regional_toggle",
                                    colors = colors,
                                    onCheckedChange = { checked ->
                                        onUpdateSettings { it.copy(includeRegionalInAllIndia = checked) }
                                    }
                                )
                            } else {
                                HorizontalDivider(color = colors.cardBorder)
                                Surface(
                                    onClick = { showStatePickerSheet = true },
                                    shape = RoundedCornerShape(14.dp),
                                    color = colors.surfaceTinted,
                                    border = BorderStroke(0.85.dp, colors.accentStrong.copy(alpha = 0.45f)),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .defaultMinSize(minHeight = 52.dp)
                                        .testTag("festival_state_picker_row")
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 14.dp, vertical = 12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = "Selected State / Union Territory",
                                                fontFamily = PlusJakartaFontFamily,
                                                fontSize = 11.5.sp,
                                                color = colors.textSecondary
                                            )
                                            Text(
                                                text = "${settings.selectedState.displayName} (${settings.selectedState.shortCode})",
                                                fontFamily = OutfitFontFamily,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 15.sp,
                                                color = colors.textPrimary
                                            )
                                        }
                                        Icon(
                                            imageVector = Icons.Rounded.ChevronRight,
                                            contentDescription = "Choose State or UT",
                                            tint = colors.accentStrong
                                        )
                                    }
                                }
                            }

                            HorizontalDivider(color = colors.cardBorder)

                            FestivalSegmentedOptionRow(
                                title = "Regional Date Rule",
                                subtitle = "Used when state Panchangs and national calendars observe a festival on different dates",
                                options = RegionalObservanceRule.entries.map {
                                    it to if (it == RegionalObservanceRule.PREFER_REGIONAL_PANCHANG) "State Panchang" else "Rashtriya"
                                },
                                selected = settings.observanceRule,
                                enabled = settings.enabled,
                                tagPrefix = "festival_observance_rule",
                                colors = colors,
                                onSelect = { rule ->
                                    onUpdateSettings { it.copy(observanceRule = rule) }
                                }
                            )
                        }
                    }
                }

                // 4. Animation & Battery Efficiency Card
                item(key = "festival_performance_card") {
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
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.BatterySaver,
                                    contentDescription = null,
                                    tint = colors.accentStrong,
                                    modifier = Modifier.size(20.dp)
                                )
                                Column {
                                    Text(
                                        text = "Visuals & Battery Saver",
                                        fontFamily = OutfitFontFamily,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 16.sp,
                                        color = colors.textPrimary
                                    )
                                    Text(
                                        text = "Single-Canvas pooled particles • Pauses automatically in background",
                                        fontFamily = PlusJakartaFontFamily,
                                        fontSize = 11.5.sp,
                                        color = colors.textSecondary
                                    )
                                }
                            }

                            FestivalSegmentedOptionRow(
                                title = "Animation Intensity",
                                subtitle = "Controls atmospheric glow, speed, and subtle opacity",
                                options = FestivalIntensityLevel.entries.map { it to it.label },
                                selected = settings.animationIntensity,
                                enabled = settings.enabled,
                                tagPrefix = "festival_intensity",
                                colors = colors,
                                onSelect = { level ->
                                    onUpdateSettings { it.copy(animationIntensity = level) }
                                }
                            )

                            FestivalSegmentedOptionRow(
                                title = "Particle Density",
                                subtitle = "Low (8–15) • Medium (15–25) • High (25–40) strict battery caps",
                                options = FestivalDensityLevel.entries.map { it to it.label },
                                selected = settings.particleDensity,
                                enabled = settings.enabled,
                                tagPrefix = "festival_density",
                                colors = colors,
                                onSelect = { density ->
                                    onUpdateSettings { it.copy(particleDensity = density) }
                                }
                            )

                            HorizontalDivider(color = colors.cardBorder)

                            FestivalToggleRow(
                                title = "Battery Saver",
                                subtitle = "Caps frame cadence to 30 fps (20 fps on low-RAM) and lowers particle count",
                                checked = settings.batterySaver,
                                enabled = settings.enabled,
                                testTagName = "festival_battery_saver_toggle",
                                colors = colors,
                                onCheckedChange = { checked ->
                                    onUpdateSettings { it.copy(batterySaver = checked) }
                                }
                            )

                            HorizontalDivider(color = colors.cardBorder)

                            FestivalToggleRow(
                                title = "Reduce Motion",
                                subtitle = "Shows serene static festival accents with zero continuous animation loop",
                                checked = settings.reduceMotion,
                                enabled = settings.enabled,
                                testTagName = "festival_reduce_motion_toggle",
                                colors = colors,
                                onCheckedChange = { checked ->
                                    onUpdateSettings { it.copy(reduceMotion = checked) }
                                }
                            )
                        }
                    }
                }

                // 5. Festival Effects Preview Selector (Preview Only)
                item(key = "festival_preview_selector_card") {
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
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Visibility,
                                    contentDescription = null,
                                    tint = colors.accentStrong,
                                    modifier = Modifier.size(20.dp)
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Festival Effects Preview",
                                        fontFamily = OutfitFontFamily,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 16.sp,
                                        color = colors.textPrimary
                                    )
                                    Text(
                                        text = "Preview Only — never overrides automatic date detection on Home/Player",
                                        fontFamily = PlusJakartaFontFamily,
                                        fontSize = 11.5.sp,
                                        color = colors.textSecondary
                                    )
                                }
                                if (previewEffectId != null) {
                                    TextButton(
                                        onClick = { previewEffectId = null },
                                        modifier = Modifier.testTag("festival_preview_clear_button")
                                    ) {
                                        Text(
                                            text = "Clear",
                                            fontFamily = PlusJakartaFontFamily,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 12.sp,
                                            color = colors.accentStrong
                                        )
                                    }
                                }
                            }

                            val previewEffects = remember { FestivalEffectRegistry.allPreviewable() }
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                previewEffects.forEach { descriptor ->
                                    val selected = previewEffectId == descriptor.effectId
                                    Surface(
                                        onClick = {
                                            previewEffectId = if (selected) null else descriptor.effectId
                                        },
                                        shape = RoundedCornerShape(14.dp),
                                        color = if (selected) colors.accentPill else colors.surfaceTinted,
                                        border = BorderStroke(
                                            width = if (selected) 1.dp else 0.75.dp,
                                            color = if (selected) colors.accentStrong else colors.cardBorder
                                        ),
                                        modifier = Modifier
                                            .defaultMinSize(minHeight = 48.dp)
                                            .testTag("festival_preview_chip_${descriptor.effectId.id}")
                                    ) {
                                        Box(
                                            contentAlignment = Alignment.Center,
                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
                                        ) {
                                            Text(
                                                text = descriptor.displayName,
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

                // 6. Calendar Data Cache & Attribution Card
                item(key = "festival_sources_card") {
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
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = if (syncStatus.isOfflineFallback) Icons.Rounded.CloudOff else Icons.Rounded.CloudDone,
                                    contentDescription = null,
                                    tint = colors.accentStrong,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Public Festival Calendar",
                                        fontFamily = OutfitFontFamily,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 15.5.sp,
                                        color = colors.textPrimary
                                    )
                                    val updatedLabel = when {
                                        syncUiState is FestivalSyncUiState.Syncing -> syncUiState.message
                                        syncUiState !is FestivalSyncUiState.Idle -> syncUiState.message
                                        syncStatus.lastSyncedEpochMs > 0L -> {
                                            val formatted = Instant.ofEpochMilli(syncStatus.lastSyncedEpochMs)
                                                .atZone(domain.weather.SolarPositionCalculator.INDIA_ZONE)
                                                .format(FESTIVAL_SYNC_FORMATTER)
                                            "Last synced: $formatted IST • ${syncStatus.recordCount} entries"
                                        }
                                        else -> "Local Pan-India Panchang cache active (${syncStatus.recordCount} entries)"
                                    }
                                    Text(
                                        text = updatedLabel,
                                        fontFamily = PlusJakartaFontFamily,
                                        fontSize = 11.5.sp,
                                        color = colors.textSecondary
                                    )
                                }
                                TextButton(
                                    onClick = onRefreshCalendar,
                                    enabled = syncUiState !is FestivalSyncUiState.Syncing,
                                    modifier = Modifier.testTag("festival_calendar_refresh_button")
                                ) {
                                    Text(
                                        text = if (syncUiState is FestivalSyncUiState.Syncing) "Syncing..." else "Sync Now",
                                        fontFamily = PlusJakartaFontFamily,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.5.sp,
                                        color = colors.accentStrong
                                    )
                                }
                            }

                            if (onOpenFestivalCalendar != null) {
                                Surface(
                                    onClick = onOpenFestivalCalendar,
                                    shape = RoundedCornerShape(14.dp),
                                    color = colors.surfaceTinted,
                                    border = BorderStroke(0.75.dp, colors.cardBorder),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .defaultMinSize(minHeight = 48.dp)
                                        .testTag("festival_open_calendar_screen_button")
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 14.dp, vertical = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = "Open Festival Calendar & Sync Schedule",
                                                fontFamily = OutfitFontFamily,
                                                fontWeight = FontWeight.SemiBold,
                                                fontSize = 13.5.sp,
                                                color = colors.textPrimary
                                            )
                                            Text(
                                                text = "View upcoming festivals, sync history & automatic daily sync",
                                                fontFamily = PlusJakartaFontFamily,
                                                fontSize = 11.5.sp,
                                                color = colors.textSecondary
                                            )
                                        }
                                        Icon(
                                            imageVector = Icons.Rounded.ChevronRight,
                                            contentDescription = "Open Festival Calendar",
                                            tint = colors.accentStrong
                                        )
                                    }
                                }
                            }

                            HorizontalDivider(color = colors.cardBorder)

                            Row(
                                verticalAlignment = Alignment.Top,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Shield,
                                    contentDescription = null,
                                    tint = colors.accentStrong,
                                    modifier = Modifier
                                        .padding(top = 2.dp)
                                        .size(16.dp)
                                )
                                Text(
                                    text = "Zero personal permissions: No GPS, location, contacts, calendar, or IP geolocation. " +
                                        "Calendar dates prioritize IMD / PAC Rashtriya Panchang & regional state Panjis, " +
                                        "supplemented by public Hindu Festivals JSON (MIT License) and open Panchang references (CC BY 4.0).",
                                    fontFamily = PlusJakartaFontFamily,
                                    fontSize = 11.5.sp,
                                    lineHeight = 16.sp,
                                    color = colors.textSecondary
                                )
                            }
                        }
                    }
                }

                item(key = "festival_bottom_spacer") {
                    Spacer(modifier = Modifier.height(64.dp))
                }
            }
        }
    }

    if (showStatePickerSheet) {
        ModalBottomSheet(
            onDismissRequest = { showStatePickerSheet = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = colors.surface,
            contentColor = colors.textPrimary,
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
            modifier = Modifier.testTag("festival_state_picker_sheet")
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 20.dp)
                    .navigationBarsPadding()
            ) {
                Text(
                    text = "Select Indian State or Union Territory",
                    fontFamily = OutfitFontFamily,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 19.sp,
                    color = colors.textPrimary
                )
                Text(
                    text = "All 28 States & 8 Union Territories supported with regional festival dates",
                    fontFamily = PlusJakartaFontFamily,
                    fontSize = 12.sp,
                    color = colors.textSecondary,
                    modifier = Modifier.padding(top = 2.dp, bottom = 12.dp)
                )

                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(420.dp)
                        .testTag("festival_state_picker_list"),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(
                        items = IndianFestivalRegion.STATES_AND_UTS,
                        key = { it.id }
                    ) { region ->
                        val selected = settings.selectedState == region
                        Surface(
                            onClick = {
                                onUpdateSettings { it.copy(selectedState = region) }
                                showStatePickerSheet = false
                            },
                            shape = RoundedCornerShape(14.dp),
                            color = if (selected) colors.accentPill.copy(alpha = 0.24f) else colors.surfaceTinted,
                            border = BorderStroke(
                                width = if (selected) 1.dp else 0.75.dp,
                                color = if (selected) colors.accentStrong else colors.cardBorder
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .defaultMinSize(minHeight = 48.dp)
                                .testTag("festival_state_option_${region.id}")
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = region.displayName,
                                        fontFamily = PlusJakartaFontFamily,
                                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                        fontSize = 14.sp,
                                        color = colors.textPrimary
                                    )
                                    Text(
                                        text = if (region.isUnionTerritory) "Union Territory • ${region.shortCode}" else "State • ${region.shortCode}",
                                        fontFamily = PlusJakartaFontFamily,
                                        fontSize = 11.sp,
                                        color = colors.textSecondary
                                    )
                                }
                                if (selected) {
                                    Icon(
                                        imageVector = Icons.Rounded.CheckCircle,
                                        contentDescription = "Selected",
                                        tint = colors.accentStrong,
                                        modifier = Modifier.size(20.dp)
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
private fun FestivalToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    enabled: Boolean,
    testTagName: String,
    colors: EditorialColors,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontFamily = OutfitFontFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.5.sp,
                color = if (enabled) colors.textPrimary else colors.textMuted
            )
            Text(
                text = subtitle,
                fontFamily = PlusJakartaFontFamily,
                fontSize = 11.5.sp,
                color = colors.textSecondary
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
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

@Composable
private fun <T> FestivalSegmentedOptionRow(
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
                val cleanTag = label.lowercase().replace(" ", "_")
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
                        .defaultMinSize(minHeight = 48.dp)
                        .testTag("${tagPrefix}_$cleanTag")
                ) {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 6.dp)) {
                        Text(
                            text = label,
                            fontFamily = PlusJakartaFontFamily,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            fontSize = 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
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
