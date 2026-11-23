package ui.profile

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.CloudQueue
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Equalizer
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.OutfitFontFamily
import com.example.ui.theme.PlusJakartaFontFamily
import com.example.viewmodel.SonoraViewModel
import domain.analytics.AnalyticsTimeRange
import domain.analytics.ListeningAnalyticsCalculator
import domain.analytics.ListeningChartPoint
import domain.analytics.ListeningTimelineItem
import domain.analytics.RankedStatItem
import ui.theme.EditorialColors
import ui.theme.LocalEditorialColors
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Full-screen Music Listening Analytics dashboard accessible from Profile.
 * Displays real statistics computed from local playback history (excluding paused time).
 */
@Composable
fun MusicAnalyticsScreen(
    viewModel: SonoraViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler(onBack = onBack)

    val colors = LocalEditorialColors.current
    val dashboard by viewModel.listeningAnalyticsDashboard.collectAsState()
    val selectedRange by viewModel.analyticsTimeRange.collectAsState()

    var selectedChartTab by remember { mutableIntStateOf(0) } // 0=Daily, 1=Weekly, 2=Monthly
    var selectedRankingTab by remember { mutableIntStateOf(0) } // 0=Top Tracks, 1=Top Artists, 2=Top Albums
    var showClearDialog by remember { mutableStateOf(false) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(colors.backgroundBrush)
            .statusBarsPadding()
            .navigationBarsPadding()
            .testTag("music_analytics_screen"),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 760.dp)
        ) {
            // Top App Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
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
                        .testTag("analytics_back_button")
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = "Back",
                            tint = colors.textPrimary,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Music Analytics",
                        fontFamily = OutfitFontFamily,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 22.sp,
                        color = colors.textPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "Local listening activity & trends",
                        fontFamily = PlusJakartaFontFamily,
                        fontSize = 12.sp,
                        color = colors.textSecondary
                    )
                }
                IconButton(
                    onClick = { showClearDialog = true },
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("analytics_clear_history_button")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.DeleteOutline,
                        contentDescription = "Clear Listening History",
                        tint = colors.textSecondary
                    )
                }
            }

            // Time Range Filter Chips
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                AnalyticsTimeRange.entries.forEach { range ->
                    val selected = range == selectedRange
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = if (selected) colors.accentStrong else colors.surface,
                        border = BorderStroke(
                            1.dp,
                            if (selected) colors.accentStrong else colors.cardBorder
                        ),
                        modifier = Modifier
                            .clip(RoundedCornerShape(14.dp))
                            .clickable { viewModel.setAnalyticsTimeRange(range) }
                            .testTag("analytics_filter_${range.name.lowercase(Locale.ROOT)}")
                    ) {
                        Text(
                            text = range.label,
                            fontFamily = PlusJakartaFontFamily,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                            fontSize = 12.sp,
                            color = if (selected) Color.White else colors.textPrimary,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                        )
                    }
                }
            }

            if (dashboard.isEmpty) {
                AnalyticsEmptyState(
                    rangeLabel = selectedRange.label,
                    colors = colors,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp)
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 36.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // 1. Summary KPI Bento Grid
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                AnalyticsKpiCard(
                                    label = "Total Listening",
                                    value = ListeningAnalyticsCalculator.formatListeningDuration(dashboard.totalListeningMs),
                                    icon = Icons.Rounded.Schedule,
                                    colors = colors,
                                    modifier = Modifier.weight(1f)
                                )
                                AnalyticsKpiCard(
                                    label = "Songs Played",
                                    value = dashboard.totalSongsPlayed.toString(),
                                    icon = Icons.Rounded.MusicNote,
                                    colors = colors,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                AnalyticsKpiCard(
                                    label = "Unique Tracks",
                                    value = dashboard.uniqueTracksPlayed.toString(),
                                    icon = Icons.Rounded.LibraryMusic,
                                    colors = colors,
                                    modifier = Modifier.weight(1f)
                                )
                                AnalyticsKpiCard(
                                    label = "Top Genre",
                                    value = dashboard.mostPlayedGenre?.title ?: "N/A",
                                    icon = Icons.Rounded.Equalizer,
                                    colors = colors,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }

                    // 2. Offline vs Online Listening Breakdown
                    item {
                        OfflineOnlineSplitCard(
                            offlineMs = dashboard.offlineListeningMs,
                            onlineMs = dashboard.onlineListeningMs,
                            colors = colors
                        )
                    }

                    // 3. Most Played Spotlights (Song, Artist, Album, Genre)
                    item {
                        MostPlayedHighlightsCard(
                            mostPlayedSong = dashboard.mostPlayedSong,
                            mostPlayedArtist = dashboard.mostPlayedArtist,
                            mostPlayedAlbum = dashboard.mostPlayedAlbum,
                            mostPlayedGenre = dashboard.mostPlayedGenre,
                            colors = colors
                        )
                    }

                    // 4. Listening Activity Charts (Daily / Weekly / Monthly)
                    item {
                        val chartPoints = when (selectedChartTab) {
                            0 -> dashboard.dailyActivity
                            1 -> dashboard.weeklyActivity
                            else -> dashboard.monthlyActivity
                        }
                        val chartTitle = when (selectedChartTab) {
                            0 -> "Daily Listening Time"
                            1 -> "Weekly Listening Time"
                            else -> "Monthly Listening Time"
                        }
                        ListeningActivityChartCard(
                            title = chartTitle,
                            selectedTab = selectedChartTab,
                            onSelectTab = { selectedChartTab = it },
                            points = chartPoints,
                            colors = colors
                        )
                    }

                    // 5. Rankings (Top Tracks / Top Artists / Top Albums)
                    item {
                        val rankedItems = when (selectedRankingTab) {
                            0 -> dashboard.topTracks
                            1 -> dashboard.topArtists
                            else -> dashboard.topAlbums
                        }
                        TopRankingsCard(
                            selectedTab = selectedRankingTab,
                            onSelectTab = { selectedRankingTab = it },
                            items = rankedItems,
                            colors = colors
                        )
                    }

                    // 6. Listening Activity Timeline
                    if (dashboard.timeline.isNotEmpty()) {
                        item {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(top = 4.dp, start = 4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.History,
                                    contentDescription = null,
                                    tint = colors.accentStrong,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Listening Activity Timeline",
                                    fontFamily = OutfitFontFamily,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp,
                                    color = colors.textPrimary
                                )
                            }
                        }

                        itemsIndexed(
                            items = dashboard.timeline,
                            key = { idx, item -> "${item.id}_${item.endedAtMs}_$idx" }
                        ) { _, entry ->
                            TimelineRowCard(entry = entry, colors = colors)
                        }
                    }
                }
            }
        }
    }

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            containerColor = colors.surface,
            titleContentColor = colors.textPrimary,
            textContentColor = colors.textSecondary,
            title = {
                Text(
                    text = "Clear Listening History?",
                    fontFamily = OutfitFontFamily,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = "This resets your listening statistics and activity timeline. Downloaded music and playlists will remain untouched.",
                    fontFamily = PlusJakartaFontFamily,
                    fontSize = 13.sp,
                    lineHeight = 19.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showClearDialog = false
                        viewModel.clearListeningHistory()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = colors.accentStrong),
                    modifier = Modifier.testTag("analytics_confirm_clear_button")
                ) {
                    Text("Clear History", color = Color.White, fontFamily = PlusJakartaFontFamily)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) {
                    Text("Cancel", color = colors.textSecondary, fontFamily = PlusJakartaFontFamily)
                }
            }
        )
    }
}

@Composable
private fun AnalyticsEmptyState(
    rangeLabel: String,
    colors: EditorialColors,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Surface(
            shape = CircleShape,
            color = colors.accentPill,
            modifier = Modifier.size(72.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Rounded.BarChart,
                    contentDescription = null,
                    tint = colors.accentStrong,
                    modifier = Modifier.size(34.dp)
                )
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "No Listening Activity Yet",
            fontFamily = OutfitFontFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 18.sp,
            color = colors.textPrimary
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "Play your offline or online tracks and SONORA will build real listening charts and rankings for $rangeLabel.",
            fontFamily = PlusJakartaFontFamily,
            fontSize = 13.sp,
            color = colors.textSecondary,
            textAlign = TextAlign.Center,
            lineHeight = 19.sp
        )
    }
}

@Composable
private fun AnalyticsKpiCard(
    label: String,
    value: String,
    icon: ImageVector,
    colors: EditorialColors,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = colors.surface,
        border = BorderStroke(1.dp, colors.cardBorder),
        modifier = modifier
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = colors.accentPill,
                modifier = Modifier.size(34.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = colors.accentStrong,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = value,
                fontFamily = OutfitFontFamily,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 20.sp,
                color = colors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = label,
                fontFamily = PlusJakartaFontFamily,
                fontSize = 12.sp,
                color = colors.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun OfflineOnlineSplitCard(
    offlineMs: Long,
    onlineMs: Long,
    colors: EditorialColors
) {
    val total = (offlineMs + onlineMs).coerceAtLeast(1L)
    val offlineRatio = (offlineMs.toFloat() / total.toFloat()).coerceIn(0f, 1f)
    val onlineRatio = (onlineMs.toFloat() / total.toFloat()).coerceIn(0f, 1f)
    val offlinePct = (offlineRatio * 100f).toInt()
    val onlinePct = 100 - offlinePct

    Surface(
        shape = RoundedCornerShape(20.dp),
        color = colors.surface,
        border = BorderStroke(1.dp, colors.cardBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Offline vs. Online Listening",
                fontFamily = OutfitFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                color = colors.textPrimary
            )
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(10.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(colors.surfaceTinted)
            ) {
                if (offlineRatio > 0f) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .weight(offlineRatio.coerceAtLeast(0.02f))
                            .background(colors.accentStrong)
                    )
                }
                if (onlineRatio > 0f) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .weight(onlineRatio.coerceAtLeast(0.02f))
                            .background(colors.accentStrong.copy(alpha = 0.45f))
                    )
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Rounded.PhoneAndroid,
                        contentDescription = null,
                        tint = colors.accentStrong,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Column {
                        Text(
                            text = "Offline ($offlinePct%)",
                            fontFamily = PlusJakartaFontFamily,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 12.sp,
                            color = colors.textPrimary
                        )
                        Text(
                            text = ListeningAnalyticsCalculator.formatListeningDuration(offlineMs),
                            fontFamily = PlusJakartaFontFamily,
                            fontSize = 11.sp,
                            color = colors.textSecondary
                        )
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Rounded.CloudQueue,
                        contentDescription = null,
                        tint = colors.accentStrong.copy(alpha = 0.7f),
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = "Online ($onlinePct%)",
                            fontFamily = PlusJakartaFontFamily,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 12.sp,
                            color = colors.textPrimary
                        )
                        Text(
                            text = ListeningAnalyticsCalculator.formatListeningDuration(onlineMs),
                            fontFamily = PlusJakartaFontFamily,
                            fontSize = 11.sp,
                            color = colors.textSecondary
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MostPlayedHighlightsCard(
    mostPlayedSong: RankedStatItem?,
    mostPlayedArtist: RankedStatItem?,
    mostPlayedAlbum: RankedStatItem?,
    mostPlayedGenre: RankedStatItem?,
    colors: EditorialColors
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = colors.surface,
        border = BorderStroke(1.dp, colors.cardBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = "Most Played Highlights",
                fontFamily = OutfitFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                color = colors.textPrimary
            )
            mostPlayedSong?.let {
                HighlightRow(
                    icon = Icons.Rounded.MusicNote,
                    category = "Most Played Song",
                    title = it.title,
                    meta = "${it.subtitle} • ${ListeningAnalyticsCalculator.formatListeningDuration(it.listenedMs)}",
                    colors = colors
                )
            }
            mostPlayedArtist?.let {
                HighlightRow(
                    icon = Icons.Rounded.Person,
                    category = "Most Played Artist",
                    title = it.title,
                    meta = "${it.playCount} plays • ${ListeningAnalyticsCalculator.formatListeningDuration(it.listenedMs)}",
                    colors = colors
                )
            }
            mostPlayedAlbum?.let {
                HighlightRow(
                    icon = Icons.Rounded.Album,
                    category = "Most Played Album",
                    title = it.title,
                    meta = "${it.subtitle} • ${ListeningAnalyticsCalculator.formatListeningDuration(it.listenedMs)}",
                    colors = colors
                )
            }
            mostPlayedGenre?.let {
                HighlightRow(
                    icon = Icons.Rounded.Equalizer,
                    category = "Most Played Genre",
                    title = it.title,
                    meta = "${it.playCount} plays • ${ListeningAnalyticsCalculator.formatListeningDuration(it.listenedMs)}",
                    colors = colors
                )
            }
        }
    }
}

@Composable
private fun HighlightRow(
    icon: ImageVector,
    category: String,
    title: String,
    meta: String,
    colors: EditorialColors
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(colors.surfaceTinted)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            shape = CircleShape,
            color = colors.accentPill,
            modifier = Modifier.size(36.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = null, tint = colors.accentStrong, modifier = Modifier.size(18.dp))
            }
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = category.uppercase(Locale.ROOT),
                fontFamily = PlusJakartaFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 10.sp,
                letterSpacing = 0.8.sp,
                color = colors.accentStrong
            )
            Text(
                text = title,
                fontFamily = OutfitFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                color = colors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = meta,
                fontFamily = PlusJakartaFontFamily,
                fontSize = 11.sp,
                color = colors.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun ListeningActivityChartCard(
    title: String,
    selectedTab: Int,
    onSelectTab: (Int) -> Unit,
    points: List<ListeningChartPoint>,
    colors: EditorialColors
) {
    val maxMs = (points.maxOfOrNull { it.listenedMs } ?: 0L).coerceAtLeast(1L)
    val accentTop = colors.accentStrong
    val accentBottom = colors.accentStrong.copy(alpha = 0.35f)
    val trackColor = colors.surfaceTinted

    Surface(
        shape = RoundedCornerShape(20.dp),
        color = colors.surface,
        border = BorderStroke(1.dp, colors.cardBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    fontFamily = OutfitFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = colors.textPrimary
                )
                Text(
                    text = "Peak ${ListeningAnalyticsCalculator.formatListeningDuration(maxMs)}",
                    fontFamily = PlusJakartaFontFamily,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 11.sp,
                    color = colors.accentStrong
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("Daily", "Weekly", "Monthly").forEachIndexed { idx, label ->
                    val active = idx == selectedTab
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = if (active) colors.accentPill else colors.surfaceTinted,
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { onSelectTab(idx) }
                            .testTag("analytics_chart_tab_${label.lowercase(Locale.ROOT)}")
                    ) {
                        Text(
                            text = label,
                            fontFamily = PlusJakartaFontFamily,
                            fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
                            fontSize = 12.sp,
                            color = if (active) colors.onAccentPill else colors.textSecondary,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))

            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(150.dp)
            ) {
                if (points.isEmpty()) return@Canvas
                val count = points.size
                val slotWidth = size.width / count.toFloat()
                val barWidth = (slotWidth * 0.56f).coerceIn(8.dp.toPx(), 28.dp.toPx())
                val chartHeight = size.height - 8.dp.toPx()

                points.forEachIndexed { idx, pt ->
                    val left = idx * slotWidth + (slotWidth - barWidth) / 2f
                    // Background track
                    drawRoundRect(
                        color = trackColor,
                        topLeft = Offset(left, 0f),
                        size = Size(barWidth, chartHeight),
                        cornerRadius = CornerRadius(6.dp.toPx(), 6.dp.toPx())
                    )
                    val fraction = (pt.listenedMs.toFloat() / maxMs.toFloat()).coerceIn(0f, 1f)
                    if (fraction > 0f) {
                        val barHeight = (chartHeight * fraction).coerceAtLeast(6.dp.toPx())
                        val top = chartHeight - barHeight
                        drawRoundRect(
                            brush = Brush.verticalGradient(
                                colors = listOf(accentTop, accentBottom),
                                startY = top,
                                endY = chartHeight
                            ),
                            topLeft = Offset(left, top),
                            size = Size(barWidth, barHeight),
                            cornerRadius = CornerRadius(6.dp.toPx(), 6.dp.toPx())
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                val step = if (points.size > 8) 2 else 1
                points.forEachIndexed { idx, pt ->
                    Text(
                        text = if (idx % step == 0 || idx == points.lastIndex) pt.label else "",
                        fontFamily = PlusJakartaFontFamily,
                        fontSize = 10.sp,
                        color = colors.textMuted,
                        maxLines = 1,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
private fun TopRankingsCard(
    selectedTab: Int,
    onSelectTab: (Int) -> Unit,
    items: List<RankedStatItem>,
    colors: EditorialColors
) {
    val maxMs = (items.maxOfOrNull { it.listenedMs } ?: 1L).coerceAtLeast(1L)

    Surface(
        shape = RoundedCornerShape(20.dp),
        color = colors.surface,
        border = BorderStroke(1.dp, colors.cardBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Top Rankings",
                fontFamily = OutfitFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                color = colors.textPrimary
            )
            Spacer(modifier = Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("Top Tracks", "Top Artists", "Top Albums").forEachIndexed { idx, label ->
                    val active = idx == selectedTab
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = if (active) colors.accentPill else colors.surfaceTinted,
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { onSelectTab(idx) }
                            .testTag("analytics_ranking_tab_$idx")
                    ) {
                        Text(
                            text = label,
                            fontFamily = PlusJakartaFontFamily,
                            fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
                            fontSize = 12.sp,
                            color = if (active) colors.onAccentPill else colors.textSecondary,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            if (items.isEmpty()) {
                Text(
                    text = "Not enough metadata in this period yet.",
                    fontFamily = PlusJakartaFontFamily,
                    fontSize = 12.sp,
                    color = colors.textMuted
                )
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    items.take(8).forEachIndexed { index, item ->
                        val ratio = (item.listenedMs.toFloat() / maxMs.toFloat()).coerceIn(0.05f, 1f)
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "#${index + 1}",
                                    fontFamily = OutfitFontFamily,
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 13.sp,
                                    color = if (index < 3) colors.accentStrong else colors.textMuted,
                                    modifier = Modifier.width(28.dp)
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = item.title,
                                        fontFamily = OutfitFontFamily,
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 14.sp,
                                        color = colors.textPrimary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "${item.subtitle} • ${item.playCount} ${if (item.playCount == 1) "play" else "plays"}",
                                        fontFamily = PlusJakartaFontFamily,
                                        fontSize = 11.sp,
                                        color = colors.textSecondary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                Text(
                                    text = ListeningAnalyticsCalculator.formatListeningDuration(item.listenedMs),
                                    fontFamily = PlusJakartaFontFamily,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp,
                                    color = colors.accentStrong
                                )
                            }
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(start = 28.dp)
                                    .height(5.dp)
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(colors.surfaceTinted)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth(ratio)
                                        .fillMaxHeight()
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(colors.accentStrong.copy(alpha = 0.85f))
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
private fun TimelineRowCard(
    entry: ListeningTimelineItem,
    colors: EditorialColors
) {
    val timeFmt = remember { SimpleDateFormat("d MMM, h:mm a", Locale.getDefault()) }
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = colors.surface,
        border = BorderStroke(1.dp, colors.cardBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = CircleShape,
                color = colors.accentPill,
                modifier = Modifier.size(38.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = if (entry.isOnline) Icons.Rounded.CloudQueue else Icons.Rounded.MusicNote,
                        contentDescription = null,
                        tint = colors.accentStrong,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = entry.title,
                    fontFamily = OutfitFontFamily,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                    color = colors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${entry.artist} • ${timeFmt.format(Date(entry.endedAtMs))}",
                    fontFamily = PlusJakartaFontFamily,
                    fontSize = 11.sp,
                    color = colors.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = colors.surfaceTinted,
                modifier = Modifier.border(1.dp, colors.cardBorder, RoundedCornerShape(10.dp))
            ) {
                Text(
                    text = ListeningAnalyticsCalculator.formatListeningDuration(entry.listenedMs),
                    fontFamily = PlusJakartaFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp,
                    color = colors.accentStrong,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }
    }
}
