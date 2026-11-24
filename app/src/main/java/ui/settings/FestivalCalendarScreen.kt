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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.CloudDone
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.BuildConfig
import com.example.ui.theme.OutfitFontFamily
import com.example.ui.theme.PlusJakartaFontFamily
import com.example.viewmodel.SonoraViewModel
import data.festival.FestivalCalendarSyncCoordinator
import data.festival.FestivalSyncStatus
import data.festival.FestivalSyncUiState
import data.festival.PublicFestivalCalendarDataSource
import data.preferences.FestivalAtmosphereSettings
import domain.festival.FestivalRecord
import domain.festival.FestivalRegionMode
import ui.theme.EditorialColors
import ui.theme.LocalEditorialColors
import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeFormatter

private val LAST_SYNCED_DATE_TIME_FORMATTER: DateTimeFormatter =
    DateTimeFormatter.ofPattern("dd MMM yyyy, hh:mm a")

private val UPCOMING_FESTIVAL_DATE_FORMATTER: DateTimeFormatter =
    DateTimeFormatter.ofPattern("EEE, dd MMM yyyy")

private val UPCOMING_FESTIVAL_SHORT_DATE_FORMATTER: DateTimeFormatter =
    DateTimeFormatter.ofPattern("dd MMM")

/**
 * Settings → Appearance → Ambient Effects → Festival Calendar
 * Clean Material 3 status & schedule screen for SONORA's Public Festival Calendar.
 * Never renders raw JSON, stack traces, response bodies, or internal database entities.
 */
@Composable
fun FestivalCalendarScreen(
    viewModel: SonoraViewModel,
    onBack: () -> Unit,
    onOpenFestivalAtmosphere: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val colors = LocalEditorialColors.current
    val settings by viewModel.festivalAtmosphereSettings.collectAsState()
    val syncStatus by viewModel.festivalSyncStatus.collectAsState()
    val syncUiState by viewModel.festivalSyncUiState.collectAsState()
    val upcomingFestivals by viewModel.upcomingCachedFestivals.collectAsState()

    FestivalCalendarContent(
        settings = settings,
        syncStatus = syncStatus,
        syncUiState = syncUiState,
        upcomingFestivals = upcomingFestivals,
        colors = colors,
        onSyncNow = { viewModel.refreshFestivalCalendarNow(force = true) },
        onToggleAutoDailySync = { enabled ->
            viewModel.updateFestivalAtmosphereSettings { it.copy(automaticDailySyncEnabled = enabled) }
        },
        onBack = onBack,
        onOpenFestivalAtmosphere = onOpenFestivalAtmosphere,
        showDebugDiagnostics = BuildConfig.DEBUG,
        modifier = modifier
    )
}

@Composable
fun FestivalCalendarContent(
    settings: FestivalAtmosphereSettings,
    syncStatus: FestivalSyncStatus,
    syncUiState: FestivalSyncUiState,
    upcomingFestivals: List<FestivalRecord>,
    colors: EditorialColors,
    onSyncNow: () -> Unit,
    onToggleAutoDailySync: (Boolean) -> Unit,
    onBack: () -> Unit,
    onOpenFestivalAtmosphere: (() -> Unit)? = null,
    nowEpochMs: Long = System.currentTimeMillis(),
    showDebugDiagnostics: Boolean = BuildConfig.DEBUG,
    modifier: Modifier = Modifier
) {
    BackHandler(onBack = onBack)

    val isSyncing = syncUiState is FestivalSyncUiState.Syncing
    val syncedToday = remember(syncStatus.lastSuccessfulSyncAt, nowEpochMs) {
        FestivalCalendarSyncCoordinator.hasSyncedSuccessfullyToday(
            lastSuccessfulSyncEpochMs = syncStatus.lastSuccessfulSyncAt,
            nowEpochMs = nowEpochMs
        )
    }

    val lastSuccessfulSyncLabel = remember(syncStatus.lastSuccessfulSyncAt) {
        if (syncStatus.lastSuccessfulSyncAt > 0L) {
            val formatted = Instant.ofEpochMilli(syncStatus.lastSuccessfulSyncAt)
                .atZone(FestivalCalendarSyncCoordinator.INDIA_ZONE)
                .format(LAST_SYNCED_DATE_TIME_FORMATTER)
            "Last synced: $formatted IST"
        } else {
            "Last synced: Not synced online yet"
        }
    }

    val lastAttemptLabel = remember(syncStatus.lastAttemptAt, syncStatus.lastSuccessfulSyncAt) {
        if (syncStatus.lastAttemptAt > 0L && syncStatus.lastAttemptAt > syncStatus.lastSuccessfulSyncAt) {
            val formatted = Instant.ofEpochMilli(syncStatus.lastAttemptAt)
                .atZone(FestivalCalendarSyncCoordinator.INDIA_ZONE)
                .format(LAST_SYNCED_DATE_TIME_FORMATTER)
            "Last attempt: $formatted IST"
        } else {
            null
        }
    }

    val statusBannerText = remember(syncUiState, syncStatus.syncStatus, syncStatus.cachedFestivalCount, syncedToday) {
        when (syncUiState) {
            is FestivalSyncUiState.Syncing -> syncUiState.message
            is FestivalSyncUiState.Updated -> syncUiState.message
            is FestivalSyncUiState.AlreadyUpToDate -> syncUiState.message
            is FestivalSyncUiState.NoInternet -> syncUiState.message
            is FestivalSyncUiState.ServerError -> syncUiState.message
            is FestivalSyncUiState.InvalidResponse -> syncUiState.message
            is FestivalSyncUiState.DatabaseError -> syncUiState.message
            FestivalSyncUiState.Idle -> when (syncStatus.syncStatus) {
                "OFFLINE" -> FestivalSyncUiState.NoInternet.message
                "SERVER_ERROR" -> FestivalSyncUiState.ServerError().message
                "INVALID_RESPONSE", "FAILED" -> FestivalSyncUiState.InvalidResponse().message
                "DATABASE_ERROR" -> FestivalSyncUiState.DatabaseError().message
                "UPDATED" -> "Calendar updated successfully"
                "UP_TO_DATE" -> "Calendar is already up to date"
                else -> when {
                    syncedToday -> "Calendar is already up to date"
                    syncStatus.cachedFestivalCount > 0 -> "Verified local festival calendar ready"
                    else -> "Ready to sync public festival calendar"
                }
            }
        }
    }

    val isFailureDisplayed = syncUiState.isFailure || (
        syncUiState is FestivalSyncUiState.Idle &&
            syncStatus.syncStatus in setOf("OFFLINE", "SERVER_ERROR", "INVALID_RESPONSE", "DATABASE_ERROR", "FAILED")
        )

    val statusIcon = when {
        syncUiState is FestivalSyncUiState.NoInternet ||
            (syncUiState is FestivalSyncUiState.Idle && syncStatus.syncStatus == "OFFLINE") ->
            Icons.Rounded.CloudOff
        isFailureDisplayed -> Icons.Rounded.ErrorOutline
        syncUiState is FestivalSyncUiState.Updated ||
            syncUiState is FestivalSyncUiState.AlreadyUpToDate ||
            syncedToday ->
            Icons.Rounded.CloudDone
        else -> Icons.Rounded.CalendarMonth
    }

    // Debug builds may inspect a strictly sanitized, non-JSON technical summary.
    // Release builds display friendly messages only.
    val safeDebugDiagnostic = remember(showDebugDiagnostics, syncUiState, syncStatus.lastSyncError) {
        if (!showDebugDiagnostics) {
            null
        } else {
            val raw = syncUiState.debugDiagnostic
                ?: syncStatus.lastSyncError?.takeIf { isFailureDisplayed }
            PublicFestivalCalendarDataSource.sanitizeDiagnosticText(raw)
        }
    }

    val cleanUpcomingFestivals = remember(upcomingFestivals) {
        upcomingFestivals.filterNot {
            PublicFestivalCalendarDataSource.looksLikeRawJsonOrCode(it.canonicalName)
        }
    }

    val selectedRegionLabel = remember(settings.regionMode, settings.selectedState, settings.includeRegionalInAllIndia) {
        when (settings.regionMode) {
            FestivalRegionMode.ALL_INDIA ->
                if (settings.includeRegionalInAllIndia) "All India (Major + Regional)" else "All India (Major Festivals)"
            FestivalRegionMode.SPECIFIC_STATE ->
                "${settings.selectedState.displayName} (${settings.selectedState.shortCode})"
            FestivalRegionMode.ALL_INDIA_PLUS_STATE ->
                "All India + ${settings.selectedState.displayName}"
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(colors.backgroundBrush)
            .statusBarsPadding()
            .navigationBarsPadding()
            .testTag("festival_calendar_screen"),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 760.dp)
        ) {
            // Top Bar with Back button and small quick Sync action button
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
                        .testTag("festival_calendar_back_button")
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
                        text = "Festival Calendar",
                        fontFamily = OutfitFontFamily,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 23.sp,
                        color = colors.textPrimary
                    )
                    Text(
                        text = "Public Hindu festival sync & schedule",
                        fontFamily = PlusJakartaFontFamily,
                        fontSize = 12.sp,
                        color = colors.textSecondary
                    )
                }
                Surface(
                    shape = CircleShape,
                    color = colors.surface,
                    border = BorderStroke(0.75.dp, colors.cardBorder),
                    modifier = Modifier.size(48.dp)
                ) {
                    IconButton(
                        onClick = onSyncNow,
                        enabled = !isSyncing,
                        modifier = Modifier
                            .fillMaxSize()
                            .testTag("festival_calendar_top_sync_button")
                    ) {
                        if (isSyncing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = colors.accentStrong
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Rounded.Sync,
                                contentDescription = "Sync Festival Calendar",
                                tint = colors.accentStrong,
                                modifier = Modifier.size(21.dp)
                            )
                        }
                    }
                }
            }

            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("festival_calendar_lazy_column"),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // 1. Clean Sync Status Card (No raw JSON, no raw exceptions)
                item(key = "calendar_sync_status_card") {
                    Surface(
                        shape = RoundedCornerShape(22.dp),
                        color = colors.surface,
                        border = BorderStroke(0.85.dp, colors.cardBorder),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("festival_calendar_status_card")
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(18.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            // Live status indicator + timestamps
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Surface(
                                    shape = CircleShape,
                                    color = colors.accentPill,
                                    modifier = Modifier.size(42.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        if (isSyncing) {
                                            CircularProgressIndicator(
                                                modifier = Modifier.size(18.dp),
                                                strokeWidth = 2.dp,
                                                color = colors.onAccentPill
                                            )
                                        } else {
                                            Icon(
                                                imageVector = statusIcon,
                                                contentDescription = null,
                                                tint = colors.onAccentPill,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                    }
                                }
                                Column(
                                    modifier = Modifier.weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(2.dp)
                                ) {
                                    Text(
                                        text = statusBannerText,
                                        fontFamily = OutfitFontFamily,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 15.5.sp,
                                        color = colors.textPrimary,
                                        modifier = Modifier.testTag("festival_calendar_sync_status_text")
                                    )
                                    Text(
                                        text = lastSuccessfulSyncLabel,
                                        fontFamily = PlusJakartaFontFamily,
                                        fontSize = 12.sp,
                                        color = colors.textSecondary,
                                        modifier = Modifier.testTag("festival_calendar_last_synced_text")
                                    )
                                    if (lastAttemptLabel != null) {
                                        Text(
                                            text = lastAttemptLabel,
                                            fontFamily = PlusJakartaFontFamily,
                                            fontSize = 11.5.sp,
                                            color = colors.textSecondary,
                                            modifier = Modifier.testTag("festival_calendar_last_attempt_text")
                                        )
                                    }
                                }
                            }

                            // Clean Dataset Summary Strip
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = colors.surfaceTinted,
                                border = BorderStroke(0.65.dp, colors.cardBorder),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 14.dp, vertical = 11.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Text(
                                        text = "${syncStatus.cachedFestivalCount} cached festivals • Dataset ${syncStatus.datasetVersion}",
                                        fontFamily = PlusJakartaFontFamily,
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 12.5.sp,
                                        color = colors.textPrimary,
                                        modifier = Modifier.testTag("festival_calendar_dataset_meta")
                                    )
                                    Text(
                                        text = "Verified sources: ${syncStatus.sourceAttribution}",
                                        fontFamily = PlusJakartaFontFamily,
                                        fontSize = 11.5.sp,
                                        lineHeight = 16.sp,
                                        color = colors.textSecondary,
                                        maxLines = 3,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.testTag("festival_calendar_source_attribution")
                                    )
                                    if (safeDebugDiagnostic != null) {
                                        Text(
                                            text = "Diagnostic: $safeDebugDiagnostic",
                                            fontFamily = PlusJakartaFontFamily,
                                            fontSize = 11.sp,
                                            color = colors.textSecondary,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.testTag("festival_calendar_debug_diagnostic")
                                        )
                                    }
                                }
                            }

                            // Primary Manual Sync Now Button
                            Button(
                                onClick = onSyncNow,
                                enabled = !isSyncing,
                                shape = RoundedCornerShape(14.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = colors.accentStrong,
                                    contentColor = colors.onAccentStrong,
                                    disabledContainerColor = colors.surfaceTinted,
                                    disabledContentColor = colors.textSecondary
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                                    .testTag("festival_calendar_sync_now_button")
                            ) {
                                if (isSyncing) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(16.dp),
                                        strokeWidth = 2.dp,
                                        color = colors.textSecondary
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "Syncing...",
                                        fontFamily = PlusJakartaFontFamily,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp
                                    )
                                } else {
                                    Icon(
                                        imageVector = Icons.Rounded.Sync,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "Sync Now",
                                        fontFamily = PlusJakartaFontFamily,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp
                                    )
                                }
                            }

                            HorizontalDivider(color = colors.cardBorder)

                            // Automatic Daily Sync Status & Toggle
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Schedule,
                                    contentDescription = null,
                                    tint = colors.accentStrong,
                                    modifier = Modifier.size(20.dp)
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Automatic Daily Sync",
                                        fontFamily = OutfitFontFamily,
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 14.5.sp,
                                        color = colors.textPrimary
                                    )
                                    val autoStatusSubtitle = when {
                                        !settings.automaticDailySyncEnabled ->
                                            "Off • Manual sync only (cached calendar active)"
                                        syncedToday ->
                                            "Active • Synced today (Asia/Kolkata)"
                                        else ->
                                            "Active • Syncs once per local day on app open (Asia/Kolkata)"
                                    }
                                    Text(
                                        text = autoStatusSubtitle,
                                        fontFamily = PlusJakartaFontFamily,
                                        fontSize = 11.5.sp,
                                        color = colors.textSecondary,
                                        modifier = Modifier.testTag("festival_calendar_auto_sync_status")
                                    )
                                }
                                Switch(
                                    checked = settings.automaticDailySyncEnabled,
                                    onCheckedChange = onToggleAutoDailySync,
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = colors.onAccentStrong,
                                        checkedTrackColor = colors.accentStrong
                                    ),
                                    modifier = Modifier.testTag("festival_calendar_auto_daily_sync_toggle")
                                )
                            }
                        }
                    }
                }

                // 2. Selected Region & Privacy Card
                item(key = "calendar_region_source_card") {
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
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Public,
                                    contentDescription = null,
                                    tint = colors.accentStrong,
                                    modifier = Modifier.size(20.dp)
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Selected Region",
                                        fontFamily = PlusJakartaFontFamily,
                                        fontSize = 11.5.sp,
                                        color = colors.textSecondary
                                    )
                                    Text(
                                        text = selectedRegionLabel,
                                        fontFamily = OutfitFontFamily,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 15.5.sp,
                                        color = colors.textPrimary,
                                        modifier = Modifier.testTag("festival_calendar_selected_region_text")
                                    )
                                }
                                if (onOpenFestivalAtmosphere != null) {
                                    Surface(
                                        onClick = onOpenFestivalAtmosphere,
                                        shape = RoundedCornerShape(12.dp),
                                        color = colors.surfaceTinted,
                                        border = BorderStroke(0.75.dp, colors.cardBorder),
                                        modifier = Modifier
                                            .defaultMinSize(minHeight = 48.dp)
                                            .testTag("festival_calendar_change_region_button")
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Text(
                                                text = "Region & Effects",
                                                fontFamily = PlusJakartaFontFamily,
                                                fontWeight = FontWeight.SemiBold,
                                                fontSize = 12.sp,
                                                color = colors.accentStrong
                                            )
                                            Icon(
                                                imageVector = Icons.Rounded.ChevronRight,
                                                contentDescription = "Configure Festival Region and Effects",
                                                tint = colors.accentStrong,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
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
                                    text = "Zero location, GPS, IP geolocation, or calendar permissions. Synchronization runs silently in the background without interrupting music playback.",
                                    fontFamily = PlusJakartaFontFamily,
                                    fontSize = 11.5.sp,
                                    lineHeight = 16.sp,
                                    color = colors.textSecondary
                                )
                            }
                        }
                    }
                }

                // 3. Upcoming Festivals Header
                item(key = "calendar_upcoming_header") {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp)
                    ) {
                        Text(
                            text = "Upcoming Festivals",
                            fontFamily = OutfitFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            color = colors.textPrimary
                        )
                        Text(
                            text = "${cleanUpcomingFestivals.size} upcoming from local cached calendar ($selectedRegionLabel)",
                            fontFamily = PlusJakartaFontFamily,
                            fontSize = 12.sp,
                            color = colors.textSecondary
                        )
                    }
                }

                if (cleanUpcomingFestivals.isEmpty()) {
                    item(key = "calendar_upcoming_empty") {
                        Surface(
                            shape = RoundedCornerShape(18.dp),
                            color = colors.surface,
                            border = BorderStroke(0.75.dp, colors.cardBorder),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "No upcoming festivals found in the local cache for this region. Tap Sync Now to check for updates.",
                                fontFamily = PlusJakartaFontFamily,
                                fontSize = 12.5.sp,
                                color = colors.textSecondary,
                                modifier = Modifier.padding(16.dp)
                            )
                        }
                    }
                } else {
                    items(
                        items = cleanUpcomingFestivals,
                        key = { it.recordKey }
                    ) { festival ->
                        UpcomingFestivalCardItem(
                            festival = festival,
                            colors = colors
                        )
                    }
                }

                item(key = "festival_calendar_bottom_spacer") {
                    Spacer(modifier = Modifier.height(64.dp))
                }
            }
        }
    }
}

@Composable
private fun UpcomingFestivalCardItem(
    festival: FestivalRecord,
    colors: EditorialColors
) {
    val formattedDate = remember(festival.startDateIso, festival.endDateIso) {
        val start = runCatching { LocalDate.parse(festival.startDateIso) }.getOrNull()
        val end = runCatching { LocalDate.parse(festival.endDateIso) }.getOrNull()
        when {
            start == null -> festival.startDateIso
            end == null || end == start -> start.format(UPCOMING_FESTIVAL_DATE_FORMATTER)
            else -> "${start.format(UPCOMING_FESTIVAL_SHORT_DATE_FORMATTER)} – ${end.format(UPCOMING_FESTIVAL_DATE_FORMATTER)}"
        }
    }

    val regionScope = remember(festival.isPanIndia, festival.regions) {
        if (festival.isPanIndia) {
            "Pan-India"
        } else {
            val names = festival.regions.map { it.displayName }
            names.take(3).joinToString(", ") +
                if (names.size > 3) " +${names.size - 3}" else ""
        }
    }

    Surface(
        shape = RoundedCornerShape(18.dp),
        color = colors.surface,
        border = BorderStroke(0.75.dp, colors.cardBorder),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("upcoming_festival_item_${festival.festivalSlug}")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Surface(
                shape = CircleShape,
                color = colors.accentPill.copy(alpha = 0.72f),
                modifier = Modifier.size(42.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = festival.iconGlyph,
                        fontSize = 19.sp
                    )
                }
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = festival.canonicalName,
                    fontFamily = OutfitFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = colors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = formattedDate + (festival.tithi?.let { " • $it" } ?: ""),
                    fontFamily = PlusJakartaFontFamily,
                    fontWeight = FontWeight.Medium,
                    fontSize = 12.sp,
                    color = colors.accentStrong,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                val secondaryLine = buildString {
                    append(regionScope)
                    val cleanAlts = festival.alternateRegionalNames.filterNot {
                        PublicFestivalCalendarDataSource.looksLikeRawJsonOrCode(it)
                    }
                    if (cleanAlts.isNotEmpty()) {
                        append(" • ")
                        append(cleanAlts.take(2).joinToString(", "))
                    }
                }
                Text(
                    text = secondaryLine,
                    fontFamily = PlusJakartaFontFamily,
                    fontSize = 11.5.sp,
                    color = colors.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Icon(
                imageVector = Icons.Rounded.CheckCircle,
                contentDescription = festival.verificationStatus.label,
                tint = colors.accentStrong.copy(alpha = 0.78f),
                modifier = Modifier.size(16.dp)
            )
        }
    }
}
