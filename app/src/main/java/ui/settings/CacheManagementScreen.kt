package ui.settings

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.CleaningServices
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.OndemandVideo
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.SdStorage
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.OutfitFontFamily
import com.example.ui.theme.PlusJakartaFontFamily
import com.example.viewmodel.SonoraViewModel
import data.cache.CacheSettings
import data.cache.CacheStorageSnapshot
import data.cache.formatCacheLimitLabel
import data.cache.formatCacheSize
import ui.theme.EditorialColors
import ui.theme.LocalEditorialColors
import kotlin.math.roundToInt

/**
 * Exact cache policy informational message required by SONORA Cache Management.
 */
const val CACHE_MANAGEMENT_INFO_MESSAGE =
    "Music uses the storage limit above. Video and Shorts data clears five minutes after both players close. Turning caching off stops new writes on the next load. Downloads are kept separately. Crossfade may still prepare the next song when enabled."

/**
 * Settings → Storage & Cache → Cache Management
 */
@Composable
fun CacheManagementScreen(
    viewModel: SonoraViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalEditorialColors.current
    val settings by viewModel.cacheSettings.collectAsState()
    val snapshot by viewModel.cacheStorageSnapshot.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.refreshCacheStorageSnapshot()
    }

    CacheManagementContent(
        settings = settings,
        snapshot = snapshot,
        colors = colors,
        onCacheMusicChanged = { viewModel.setCacheMusicEnabled(it) },
        onCacheVideosChanged = { viewModel.setCacheVideosEnabled(it) },
        onPreloadUpcomingChanged = { viewModel.setPreloadUpcomingEnabled(it) },
        onMusicCacheLimitChanged = { limitBytes, clearExisting ->
            viewModel.setMusicCacheLimitBytes(limitBytes, clearExisting)
        },
        onClearMusicCache = { viewModel.clearMusicCache() },
        onClearVideoCache = { viewModel.clearVideoCache() },
        onRefreshStorage = { viewModel.refreshCacheStorageSnapshot() },
        onBack = onBack,
        modifier = modifier
    )
}

@Composable
fun CacheManagementContent(
    settings: CacheSettings,
    snapshot: CacheStorageSnapshot,
    colors: EditorialColors,
    onCacheMusicChanged: (Boolean) -> Unit,
    onCacheVideosChanged: (Boolean) -> Unit,
    onPreloadUpcomingChanged: (Boolean) -> Unit,
    onMusicCacheLimitChanged: (limitBytes: Long, clearExistingWhenZero: Boolean) -> Unit,
    onClearMusicCache: () -> Unit,
    onClearVideoCache: () -> Unit,
    onRefreshStorage: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler(onBack = onBack)

    val stops = CacheSettings.MUSIC_LIMIT_STOPS_BYTES
    val committedIndex = remember(settings.clampedMusicLimitBytes) {
        CacheSettings.nearestSliderIndex(settings.clampedMusicLimitBytes)
    }
    var sliderPosition by remember(committedIndex) { mutableFloatStateOf(committedIndex.toFloat()) }
    val previewStopIndex = sliderPosition.roundToInt().coerceIn(0, stops.lastIndex)
    val previewLimitBytes = stops[previewStopIndex]

    var showZeroLimitConfirmDialog by remember { mutableStateOf(false) }
    var showClearMusicDialog by remember { mutableStateOf(false) }
    var showClearVideoDialog by remember { mutableStateOf(false) }

    val usageFractionTarget = if (settings.clampedMusicLimitBytes > 0L) {
        (snapshot.musicCacheBytes.toFloat() / settings.clampedMusicLimitBytes.toFloat()).coerceIn(0f, 1f)
    } else {
        0f
    }
    val animatedUsageFraction by animateFloatAsState(
        targetValue = usageFractionTarget,
        label = "music_cache_usage_progress"
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(colors.backgroundBrush)
            .statusBarsPadding()
            .navigationBarsPadding()
            .testTag("cache_management_screen"),
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
                        .testTag("cache_management_back_button")
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
                        text = "Cache Management",
                        fontFamily = OutfitFontFamily,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 24.sp,
                        color = colors.textPrimary
                    )
                    Text(
                        text = "Streaming cache, storage limits & preloading",
                        fontFamily = PlusJakartaFontFamily,
                        fontSize = 12.sp,
                        color = colors.textSecondary
                    )
                }
                Surface(
                    shape = CircleShape,
                    color = colors.surface,
                    border = BorderStroke(0.75.dp, colors.cardBorder),
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .clickable(onClick = onRefreshStorage)
                        .testTag("cache_management_refresh_button")
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Rounded.Refresh,
                            contentDescription = "Refresh cache usage",
                            tint = colors.accentStrong,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("cache_management_list"),
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 36.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // 1. Storage Information Card (Music Cache + Live Video/Shorts Cache Summary)
                item(key = "music_cache_storage_card") {
                    Surface(
                        shape = RoundedCornerShape(24.dp),
                        color = colors.surface,
                        border = BorderStroke(0.85.dp, colors.cardBorder),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("music_cache_storage_card")
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(18.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(14.dp),
                                    color = colors.accentPill,
                                    modifier = Modifier.size(44.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = Icons.Rounded.SdStorage,
                                            contentDescription = null,
                                            tint = colors.onAccentPill,
                                            modifier = Modifier.size(22.dp)
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Music Cache",
                                        fontFamily = OutfitFontFamily,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 16.sp,
                                        color = colors.textPrimary
                                    )
                                    Text(
                                        text = if (snapshot.musicCachedEntryCount > 0) {
                                            "${snapshot.musicCachedEntryCount} cached audio ${if (snapshot.musicCachedEntryCount == 1) "stream" else "streams"} • Separate from downloads"
                                        } else {
                                            "Temporary streamed audio • Separate from downloads"
                                        },
                                        fontFamily = PlusJakartaFontFamily,
                                        fontSize = 12.sp,
                                        color = colors.textSecondary
                                    )
                                }
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = colors.surfaceTinted
                                ) {
                                    Text(
                                        text = formatCacheSize(snapshot.musicCacheBytes),
                                        fontFamily = OutfitFontFamily,
                                        fontWeight = FontWeight.ExtraBold,
                                        fontSize = 15.sp,
                                        color = colors.accentStrong,
                                        modifier = Modifier
                                            .padding(horizontal = 12.dp, vertical = 6.dp)
                                            .testTag("music_cache_size_text")
                                    )
                                }
                            }

                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                LinearProgressIndicator(
                                    progress = { animatedUsageFraction },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(8.dp)
                                        .clip(RoundedCornerShape(4.dp)),
                                    color = colors.accentStrong,
                                    trackColor = colors.surfaceTinted,
                                    strokeCap = StrokeCap.Round
                                )
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = "Used: ${formatCacheSize(snapshot.musicCacheBytes)}",
                                        fontFamily = PlusJakartaFontFamily,
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 11.5.sp,
                                        color = colors.textSecondary
                                    )
                                    Text(
                                        text = "Limit: ${formatCacheLimitLabel(settings.clampedMusicLimitBytes)}",
                                        fontFamily = PlusJakartaFontFamily,
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 11.5.sp,
                                        color = colors.textSecondary
                                    )
                                }
                            }

                            HorizontalDivider(color = colors.cardBorder)

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Temporary Video & Shorts Cache",
                                        fontFamily = PlusJakartaFontFamily,
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 12.5.sp,
                                        color = colors.textPrimary
                                    )
                                    Text(
                                        text = if (snapshot.videoCleanupScheduledAtMs != null && snapshot.totalVideoAndShortsBytes > 0L) {
                                            "Auto-clear scheduled (5 min after both players close)"
                                        } else {
                                            "Videos ${formatCacheSize(snapshot.videoCacheBytes)} • Shorts ${formatCacheSize(snapshot.shortsCacheBytes)}"
                                        },
                                        fontFamily = PlusJakartaFontFamily,
                                        fontSize = 11.5.sp,
                                        color = colors.textSecondary
                                    )
                                }
                                Text(
                                    text = formatCacheSize(snapshot.totalVideoAndShortsBytes),
                                    fontFamily = OutfitFontFamily,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.5.sp,
                                    color = colors.textPrimary,
                                    modifier = Modifier.testTag("video_cache_size_text")
                                )
                            }

                            if (snapshot.isLowStorage) {
                                Surface(
                                    shape = RoundedCornerShape(14.dp),
                                    color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.65f),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("cache_low_storage_banner")
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.WarningAmber,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.error,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Text(
                                            text = "Device storage is low (${formatCacheSize(snapshot.availableDeviceStorageBytes)} free). Background preloading and new temporary cache writes are paused automatically.",
                                            fontFamily = PlusJakartaFontFamily,
                                            fontSize = 11.5.sp,
                                            lineHeight = 16.sp,
                                            color = colors.textPrimary
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // 2. Cache Music Switch Card
                item(key = "cache_music_card") {
                    CacheToggleCard(
                        icon = Icons.Rounded.LibraryMusic,
                        title = "Cache Music",
                        description = "Keep streamed songs for faster replay and offline listening.",
                        checked = settings.cacheMusicEnabled,
                        onCheckedChange = onCacheMusicChanged,
                        colors = colors,
                        cardTag = "cache_music_card",
                        switchTag = "cache_music_switch"
                    )
                }

                // 3. Cache Videos Switch Card
                item(key = "cache_videos_card") {
                    CacheToggleCard(
                        icon = Icons.Rounded.OndemandVideo,
                        title = "Cache Videos",
                        description = "Keep temporary video data for seeking and replay.",
                        checked = settings.cacheVideosEnabled,
                        onCheckedChange = onCacheVideosChanged,
                        colors = colors,
                        cardTag = "cache_videos_card",
                        switchTag = "cache_videos_switch"
                    )
                }

                // 4. Preload Upcoming Media Switch Card
                item(key = "cache_preload_card") {
                    CacheToggleCard(
                        icon = Icons.Rounded.Bolt,
                        title = "Preload Upcoming Media",
                        description = "Prepare upcoming music and video before you play them.",
                        checked = settings.preloadUpcomingEnabled,
                        onCheckedChange = onPreloadUpcomingChanged,
                        colors = colors,
                        cardTag = "cache_preload_card",
                        switchTag = "cache_preload_switch"
                    )
                }

                // 5. Music Cache Limit Slider Card (0 MB to 10 GB)
                item(key = "music_cache_limit_card") {
                    Surface(
                        shape = RoundedCornerShape(22.dp),
                        color = colors.surface,
                        border = BorderStroke(0.75.dp, colors.cardBorder),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("music_cache_limit_card")
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
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = colors.surfaceTinted,
                                    modifier = Modifier.size(40.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = Icons.Rounded.Storage,
                                            contentDescription = null,
                                            tint = colors.accentStrong,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Music Cache Limit",
                                        fontFamily = OutfitFontFamily,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 15.sp,
                                        color = colors.textPrimary
                                    )
                                    Text(
                                        text = "Actual usage: ${formatCacheSize(snapshot.musicCacheBytes)}",
                                        fontFamily = PlusJakartaFontFamily,
                                        fontSize = 12.sp,
                                        color = colors.textSecondary
                                    )
                                }
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = colors.accentPill
                                ) {
                                    Text(
                                        text = formatCacheLimitLabel(previewLimitBytes),
                                        fontFamily = OutfitFontFamily,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp,
                                        color = colors.onAccentPill,
                                        modifier = Modifier
                                            .padding(horizontal = 10.dp, vertical = 5.dp)
                                            .testTag("music_cache_limit_value")
                                    )
                                }
                            }

                            Slider(
                                value = sliderPosition,
                                onValueChange = { sliderPosition = it },
                                onValueChangeFinished = {
                                    val targetIdx = sliderPosition.roundToInt().coerceIn(0, stops.lastIndex)
                                    val targetBytes = stops[targetIdx]
                                    sliderPosition = targetIdx.toFloat()
                                    if (targetBytes == 0L && (settings.clampedMusicLimitBytes > 0L || snapshot.musicCacheBytes > 0L)) {
                                        showZeroLimitConfirmDialog = true
                                    } else {
                                        onMusicCacheLimitChanged(targetBytes, false)
                                    }
                                },
                                valueRange = 0f..stops.lastIndex.toFloat(),
                                steps = (stops.size - 2).coerceAtLeast(0),
                                colors = SliderDefaults.colors(
                                    thumbColor = colors.accentStrong,
                                    activeTrackColor = colors.accentStrong,
                                    inactiveTrackColor = colors.surfaceTinted
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("music_cache_limit_slider")
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "0 MB",
                                    fontFamily = PlusJakartaFontFamily,
                                    fontSize = 11.sp,
                                    color = colors.textSecondary
                                )
                                Text(
                                    text = "512 MB",
                                    fontFamily = PlusJakartaFontFamily,
                                    fontSize = 11.sp,
                                    color = colors.textSecondary
                                )
                                Text(
                                    text = "2 GB",
                                    fontFamily = PlusJakartaFontFamily,
                                    fontSize = 11.sp,
                                    color = colors.textSecondary
                                )
                                Text(
                                    text = "10 GB",
                                    fontFamily = PlusJakartaFontFamily,
                                    fontSize = 11.sp,
                                    color = colors.textSecondary
                                )
                            }
                        }
                    }
                }

                // 6. Explanatory Cache Information Card (Exact Required Message)
                item(key = "cache_info_message_card") {
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = colors.surfaceTinted,
                        border = BorderStroke(0.75.dp, colors.cardBorder),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("cache_info_message_card")
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Info,
                                contentDescription = null,
                                tint = colors.accentStrong,
                                modifier = Modifier
                                    .padding(top = 2.dp)
                                    .size(20.dp)
                            )
                            Text(
                                text = CACHE_MANAGEMENT_INFO_MESSAGE,
                                fontFamily = PlusJakartaFontFamily,
                                fontSize = 12.5.sp,
                                lineHeight = 19.sp,
                                color = colors.textSecondary,
                                modifier = Modifier.testTag("cache_info_message_text")
                            )
                        }
                    }
                }

                // 7. Clear Music Cache Action Card
                item(key = "clear_music_cache_card") {
                    CacheActionCard(
                        icon = Icons.Rounded.CleaningServices,
                        title = "Clear Music Cache",
                        description = "Remove temporarily stored songs; keep downloads.",
                        sizeBadge = formatCacheSize(snapshot.musicCacheBytes),
                        colors = colors,
                        actionTag = "clear_music_cache_button",
                        onClick = { showClearMusicDialog = true }
                    )
                }

                // 8. Clear Video Cache Action Card
                item(key = "clear_video_cache_card") {
                    CacheActionCard(
                        icon = Icons.Rounded.DeleteSweep,
                        title = "Clear Video Cache",
                        description = "Remove temporary video data; keep downloads.",
                        sizeBadge = formatCacheSize(snapshot.totalVideoAndShortsBytes),
                        colors = colors,
                        actionTag = "clear_video_cache_button",
                        onClick = { showClearVideoDialog = true }
                    )
                }
            }
        }
    }

    // Confirmation Dialog: Music Cache Limit set to 0 MB
    if (showZeroLimitConfirmDialog) {
        AlertDialog(
            onDismissRequest = {
                sliderPosition = committedIndex.toFloat()
                showZeroLimitConfirmDialog = false
            },
            containerColor = colors.surface,
            modifier = Modifier.testTag("confirm_zero_limit_dialog"),
            title = {
                Text(
                    text = "Set Music Cache Limit to 0 MB?",
                    fontFamily = OutfitFontFamily,
                    fontWeight = FontWeight.Bold,
                    color = colors.textPrimary
                )
            },
            text = {
                Text(
                    text = "Setting the limit to 0 MB will stop new music cache writes and remove ${formatCacheSize(snapshot.musicCacheBytes)} of temporarily cached songs. Your permanent downloads will not be affected.",
                    fontFamily = PlusJakartaFontFamily,
                    fontSize = 13.sp,
                    lineHeight = 19.sp,
                    color = colors.textSecondary
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onMusicCacheLimitChanged(0L, true)
                        showZeroLimitConfirmDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = colors.accentStrong,
                        contentColor = colors.onAccentStrong
                    ),
                    modifier = Modifier.testTag("confirm_zero_limit_button")
                ) {
                    Text("Set to 0 MB", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        sliderPosition = committedIndex.toFloat()
                        showZeroLimitConfirmDialog = false
                    },
                    modifier = Modifier.testTag("cancel_zero_limit_button")
                ) {
                    Text("Cancel", color = colors.textSecondary)
                }
            }
        )
    }

    // Confirmation Dialog: Clear Music Cache
    if (showClearMusicDialog) {
        AlertDialog(
            onDismissRequest = { showClearMusicDialog = false },
            containerColor = colors.surface,
            modifier = Modifier.testTag("confirm_clear_music_cache_dialog"),
            title = {
                Text(
                    text = "Clear Music Cache?",
                    fontFamily = OutfitFontFamily,
                    fontWeight = FontWeight.Bold,
                    color = colors.textPrimary
                )
            },
            text = {
                Text(
                    text = "Remove ${formatCacheSize(snapshot.musicCacheBytes)} of temporarily cached music streams? Downloaded songs and playlists will remain untouched.",
                    fontFamily = PlusJakartaFontFamily,
                    fontSize = 13.sp,
                    lineHeight = 19.sp,
                    color = colors.textSecondary
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onClearMusicCache()
                        showClearMusicDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    ),
                    modifier = Modifier.testTag("confirm_clear_music_cache_button")
                ) {
                    Text("Clear Music Cache", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearMusicDialog = false }) {
                    Text("Cancel", color = colors.textSecondary)
                }
            }
        )
    }

    // Confirmation Dialog: Clear Video Cache
    if (showClearVideoDialog) {
        AlertDialog(
            onDismissRequest = { showClearVideoDialog = false },
            containerColor = colors.surface,
            modifier = Modifier.testTag("confirm_clear_video_cache_dialog"),
            title = {
                Text(
                    text = "Clear Video Cache?",
                    fontFamily = OutfitFontFamily,
                    fontWeight = FontWeight.Bold,
                    color = colors.textPrimary
                )
            },
            text = {
                Text(
                    text = "Remove ${formatCacheSize(snapshot.totalVideoAndShortsBytes)} of temporary video and Shorts cache data? Permanent authorized downloads will remain untouched.",
                    fontFamily = PlusJakartaFontFamily,
                    fontSize = 13.sp,
                    lineHeight = 19.sp,
                    color = colors.textSecondary
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onClearVideoCache()
                        showClearVideoDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    ),
                    modifier = Modifier.testTag("confirm_clear_video_cache_button")
                ) {
                    Text("Clear Video Cache", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearVideoDialog = false }) {
                    Text("Cancel", color = colors.textSecondary)
                }
            }
        )
    }
}

@Composable
private fun CacheToggleCard(
    icon: ImageVector,
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    colors: EditorialColors,
    cardTag: String,
    switchTag: String
) {
    Surface(
        shape = RoundedCornerShape(22.dp),
        color = colors.surface,
        border = BorderStroke(0.75.dp, colors.cardBorder),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .clickable { onCheckedChange(!checked) }
            .testTag(cardTag)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = if (checked) colors.accentPill else colors.surfaceTinted,
                modifier = Modifier.size(42.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = if (checked) colors.onAccentPill else colors.textSecondary,
                        modifier = Modifier.size(21.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    fontFamily = OutfitFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = colors.textPrimary
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = description,
                    fontFamily = PlusJakartaFontFamily,
                    fontSize = 12.sp,
                    lineHeight = 17.sp,
                    color = colors.textSecondary
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = colors.onAccentStrong,
                    checkedTrackColor = colors.accentStrong,
                    uncheckedThumbColor = colors.textSecondary,
                    uncheckedTrackColor = colors.surfaceTinted
                ),
                modifier = Modifier.testTag(switchTag)
            )
        }
    }
}

@Composable
private fun CacheActionCard(
    icon: ImageVector,
    title: String,
    description: String,
    sizeBadge: String,
    colors: EditorialColors,
    actionTag: String,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(22.dp),
        color = colors.surface,
        border = BorderStroke(0.75.dp, colors.cardBorder),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .clickable(onClick = onClick)
            .testTag(actionTag)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.55f),
                modifier = Modifier.size(42.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    fontFamily = OutfitFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = colors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = description,
                    fontFamily = PlusJakartaFontFamily,
                    fontSize = 12.sp,
                    lineHeight = 17.sp,
                    color = colors.textSecondary
                )
            }
            Spacer(modifier = Modifier.width(10.dp))
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = colors.surfaceTinted
            ) {
                Text(
                    text = sizeBadge,
                    fontFamily = PlusJakartaFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    color = Color(MaterialTheme.colorScheme.error.value),
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                )
            }
        }
    }
}
