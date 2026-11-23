package ui.downloads

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Cancel
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ClearAll
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import domain.download.DownloadMediaType
import domain.download.DownloadState
import domain.download.DownloadTask
import domain.download.formatByteCount
import domain.download.formatDownloadSpeed
import domain.download.formatEtaDuration
import ui.online.OnlineTrackThumbnail
import ui.theme.EditorialColors
import ui.theme.LocalEditorialColors

@Composable
fun DownloadManagerScreen(
    viewModel: SonoraViewModel,
    onBack: () -> Unit,
    onOpenLibraryDownloads: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val colors = LocalEditorialColors.current
    val tasks by viewModel.downloadTasks.collectAsState()
    val downloadedSongs by viewModel.downloadedSongs.collectAsState()
    val downloadedVideos by viewModel.downloadedVideos.collectAsState()
    val summary by viewModel.downloadStorageSummary.collectAsState()
    var showClearHistoryDialog by remember { mutableStateOf(false) }
    var selectedTab by remember { mutableStateOf(DownloadManagerTab.DOWNLOADING) }

    val hasTerminalHistory = remember(tasks) { tasks.any { it.state.isTerminal } }
    val downloadingTasks = remember(tasks) {
        tasks.filter {
            it.state == DownloadState.DOWNLOADING ||
                it.state == DownloadState.RESOLVING ||
                it.state == DownloadState.WAITING
        }
    }
    val completedTasks = remember(tasks) { tasks.filter { it.state == DownloadState.COMPLETED } }
    val failedTasks = remember(tasks) {
        tasks.filter { it.state == DownloadState.FAILED || it.state == DownloadState.CANCELLED }
    }
    val pausedTasks = remember(tasks) { tasks.filter { it.state == DownloadState.PAUSED } }
    val visibleTasks = when (selectedTab) {
        DownloadManagerTab.DOWNLOADING -> downloadingTasks
        DownloadManagerTab.COMPLETED -> completedTasks
        DownloadManagerTab.FAILED -> failedTasks
        DownloadManagerTab.PAUSED -> pausedTasks
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(colors.backgroundBrush)
            .statusBarsPadding()
            .navigationBarsPadding()
            .testTag("download_manager_screen"),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 760.dp)
        ) {
            // Top Bar
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
                        .testTag("download_manager_back_button")
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
                        text = "Download Manager",
                        fontFamily = OutfitFontFamily,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 22.sp,
                        color = colors.textPrimary
                    )
                    Text(
                        text = "${tasks.count { it.state.isActive || it.state == DownloadState.PAUSED }} active • ${summary.trackCount} downloaded",
                        fontFamily = PlusJakartaFontFamily,
                        fontSize = 12.sp,
                        color = colors.textSecondary
                    )
                }
                if (hasTerminalHistory) {
                    IconButton(
                        onClick = { showClearHistoryDialog = true },
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("clear_download_history_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.ClearAll,
                            contentDescription = "Clear Download History",
                            tint = colors.accentStrong
                        )
                    }
                }
            }

            // Storage & SAF Folder Card
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = colors.surface,
                border = BorderStroke(0.75.dp, colors.cardBorder),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 4.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .background(colors.accentPill, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.FolderOpen,
                            contentDescription = null,
                            tint = colors.accentStrong,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = summary.folderLabel,
                            fontFamily = OutfitFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = colors.textPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        val sizeText = formatByteCount(summary.totalBytes).ifBlank { "0 B" }
                        Text(
                            text = "${summary.trackCount} files • $sizeText used",
                            fontFamily = PlusJakartaFontFamily,
                            fontSize = 12.sp,
                            color = colors.textSecondary
                        )
                    }
                    TextButton(
                        onClick = { viewModel.requestChangeDownloadFolder() },
                        modifier = Modifier.testTag("manager_change_folder_button")
                    ) {
                        Text(
                            text = "Change",
                            fontFamily = PlusJakartaFontFamily,
                            fontWeight = FontWeight.Bold,
                            color = colors.accentStrong
                        )
                    }
                    TextButton(
                        onClick = onOpenLibraryDownloads,
                        modifier = Modifier.testTag("manager_open_library_downloads_button")
                    ) {
                        Text(
                            text = "Library",
                            fontFamily = PlusJakartaFontFamily,
                            fontWeight = FontWeight.Bold,
                            color = colors.textPrimary
                        )
                    }
                }
            }

            // 4-Tab Filter Row: Downloading, Completed, Failed, Paused
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 8.dp)
                    .testTag("download_manager_tabs"),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                DownloadManagerTab.entries.forEach { tab ->
                    val count = when (tab) {
                        DownloadManagerTab.DOWNLOADING -> downloadingTasks.size
                        DownloadManagerTab.COMPLETED -> completedTasks.size
                        DownloadManagerTab.FAILED -> failedTasks.size
                        DownloadManagerTab.PAUSED -> pausedTasks.size
                    }
                    val active = tab == selectedTab
                    Surface(
                        onClick = { selectedTab = tab },
                        shape = RoundedCornerShape(16.dp),
                        color = if (active) colors.accentStrong else colors.surface,
                        border = BorderStroke(0.75.dp, if (active) colors.accentStrong else colors.cardBorder),
                        modifier = Modifier
                            .height(38.dp)
                            .testTag("download_tab_${tab.name.lowercase()}")
                    ) {
                        Box(
                            modifier = Modifier.padding(horizontal = 14.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = if (count > 0) "${tab.label} ($count)" else tab.label,
                                fontFamily = PlusJakartaFontFamily,
                                fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
                                fontSize = 12.sp,
                                color = if (active) colors.onAccentStrong else colors.textSecondary
                            )
                        }
                    }
                }
            }

            if (visibleTasks.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(
                            modifier = Modifier
                                .size(68.dp)
                                .background(colors.accentPill, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Download,
                                contentDescription = null,
                                tint = colors.accentStrong,
                                modifier = Modifier.size(32.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(14.dp))
                        Text(
                            text = "No ${selectedTab.label} Downloads",
                            fontFamily = OutfitFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            color = colors.textPrimary
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Tap Download on permitted online videos, songs, search results, or Now Playing to save media for offline playback.",
                            fontFamily = PlusJakartaFontFamily,
                            fontSize = 13.sp,
                            color = colors.textSecondary,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(visibleTasks, key = { it.taskId }) { task ->
                        DownloadTaskCard(
                            task = task,
                            colors = colors,
                            onPause = { viewModel.pauseDownload(task.taskId) },
                            onResume = { viewModel.resumeDownload(task.taskId) },
                            onCancel = { viewModel.cancelDownload(task.taskId) },
                            onRetry = { viewModel.retryDownload(task.taskId) },
                            onRemove = { viewModel.removeDownloadTask(task.taskId) },
                            onPlayOffline = {
                                if (task.mediaType == DownloadMediaType.VIDEO) {
                                    val matchingVideo = downloadedVideos.firstOrNull {
                                        it.sourceKey == task.sourceKey || it.localFileUri == task.outputFileUri
                                    }
                                    if (matchingVideo != null) {
                                        viewModel.playDownloadedVideo(matchingVideo)
                                    } else {
                                        onOpenLibraryDownloads()
                                    }
                                } else {
                                    val matchingSong = downloadedSongs.firstOrNull {
                                        it.contentUrl == task.contentUrl || it.dataPath == task.outputFileUri
                                    }
                                    if (matchingSong != null) {
                                        viewModel.playSong(matchingSong, downloadedSongs)
                                    } else {
                                        onOpenLibraryDownloads()
                                    }
                                }
                            }
                        )
                    }
                }
            }
        }
    }

    if (showClearHistoryDialog) {
        AlertDialog(
            onDismissRequest = { showClearHistoryDialog = false },
            containerColor = colors.surface,
            title = {
                Text(
                    text = "Clear Download History?",
                    fontFamily = OutfitFontFamily,
                    fontWeight = FontWeight.Bold,
                    color = colors.textPrimary
                )
            },
            text = {
                Text(
                    text = "Removes finished, failed, and cancelled items from Download Manager. Your downloaded audio files will not be deleted.",
                    fontFamily = PlusJakartaFontFamily,
                    fontSize = 14.sp,
                    color = colors.textSecondary
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.clearDownloadHistory()
                        showClearHistoryDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = colors.accentStrong,
                        contentColor = colors.onAccentStrong
                    )
                ) {
                    Text("Clear History", fontFamily = PlusJakartaFontFamily, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearHistoryDialog = false }) {
                    Text("Cancel", fontFamily = PlusJakartaFontFamily, color = colors.textMuted)
                }
            }
        )
    }
}

@Composable
fun DownloadTaskCard(
    task: DownloadTask,
    colors: EditorialColors,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onCancel: () -> Unit,
    onRetry: () -> Unit,
    onRemove: () -> Unit,
    onPlayOffline: () -> Unit = {}
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = colors.surface,
        border = BorderStroke(0.75.dp, colors.cardBorder),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("download_task_card_${task.taskId}")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OnlineTrackThumbnail(
                    artworkUrl = task.artworkUrl,
                    title = task.title,
                    colors = colors,
                    size = 50.dp,
                    cornerRadius = 12.dp
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = task.title,
                        fontFamily = OutfitFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = colors.textPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = task.artist,
                        fontFamily = PlusJakartaFontFamily,
                        fontSize = 12.sp,
                        color = colors.textSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    val formatSpec = buildString {
                        append(task.formatLabel)
                        if (task.bitrateKbps > 0) append(" • ${task.bitrateKbps} kbps")
                        if (task.totalBytes > 0L) append(" • ${formatByteCount(task.totalBytes)}")
                    }
                    Text(
                        text = formatSpec,
                        fontFamily = PlusJakartaFontFamily,
                        fontWeight = FontWeight.Medium,
                        fontSize = 11.sp,
                        color = colors.accentStrong,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(modifier = Modifier.width(6.dp))
                DownloadStateBadge(state = task.state, colors = colors)
            }

            if (task.state == DownloadState.DOWNLOADING ||
                task.state == DownloadState.RESOLVING ||
                task.state == DownloadState.WAITING ||
                task.state == DownloadState.PAUSED
            ) {
                Spacer(modifier = Modifier.height(10.dp))
                if (task.state == DownloadState.RESOLVING || (task.state == DownloadState.DOWNLOADING && task.totalBytes <= 0L)) {
                    LinearProgressIndicator(
                        color = colors.accentStrong,
                        trackColor = colors.cardBorder,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(5.dp)
                            .clip(RoundedCornerShape(3.dp))
                    )
                } else {
                    LinearProgressIndicator(
                        progress = { task.progressFraction },
                        color = colors.accentStrong,
                        trackColor = colors.cardBorder,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(5.dp)
                            .clip(RoundedCornerShape(3.dp))
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))

                val telemetryText = buildString {
                    if (task.downloadedBytes > 0L) {
                        append(formatByteCount(task.downloadedBytes))
                        if (task.totalBytes > 0L) {
                            append(" / ")
                            append(formatByteCount(task.totalBytes))
                        }
                    } else if (task.state == DownloadState.RESOLVING) {
                        append("Resolving stream URL...")
                    } else if (task.state == DownloadState.WAITING) {
                        append("Waiting in queue...")
                    }
                    val speedStr = formatDownloadSpeed(task.speedBytesPerSec)
                    if (speedStr.isNotBlank()) {
                        if (isNotEmpty()) append(" • ")
                        append(speedStr)
                    }
                    val etaStr = formatEtaDuration(task.etaSeconds)
                    if (etaStr.isNotBlank()) {
                        if (isNotEmpty()) append(" • ")
                        append(etaStr)
                    }
                }
                if (telemetryText.isNotBlank()) {
                    Text(
                        text = telemetryText,
                        fontFamily = PlusJakartaFontFamily,
                        fontSize = 11.sp,
                        color = colors.textSecondary
                    )
                }
            }

            if (task.state == DownloadState.FAILED && !task.failureReason.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Rounded.ErrorOutline,
                        contentDescription = null,
                        tint = colors.accentStrong,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = task.failureReason,
                        fontFamily = PlusJakartaFontFamily,
                        fontSize = 12.sp,
                        color = colors.textSecondary,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                when (task.state) {
                    DownloadState.WAITING,
                    DownloadState.RESOLVING,
                    DownloadState.DOWNLOADING -> {
                        TextButton(
                            onClick = onPause,
                            modifier = Modifier.testTag("pause_download_${task.taskId}")
                        ) {
                            Icon(Icons.Rounded.Pause, contentDescription = null, modifier = Modifier.size(16.dp), tint = colors.accentStrong)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Pause", fontFamily = PlusJakartaFontFamily, fontWeight = FontWeight.Bold, color = colors.accentStrong)
                        }
                        TextButton(
                            onClick = onCancel,
                            modifier = Modifier.testTag("cancel_download_${task.taskId}")
                        ) {
                            Icon(Icons.Rounded.Cancel, contentDescription = null, modifier = Modifier.size(16.dp), tint = colors.textMuted)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Cancel", fontFamily = PlusJakartaFontFamily, color = colors.textMuted)
                        }
                    }

                    DownloadState.PAUSED -> {
                        TextButton(
                            onClick = onResume,
                            modifier = Modifier.testTag("resume_download_${task.taskId}")
                        ) {
                            Icon(Icons.Rounded.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp), tint = colors.accentStrong)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Resume", fontFamily = PlusJakartaFontFamily, fontWeight = FontWeight.Bold, color = colors.accentStrong)
                        }
                        TextButton(
                            onClick = onCancel,
                            modifier = Modifier.testTag("cancel_download_${task.taskId}")
                        ) {
                            Icon(Icons.Rounded.Cancel, contentDescription = null, modifier = Modifier.size(16.dp), tint = colors.textMuted)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Cancel", fontFamily = PlusJakartaFontFamily, color = colors.textMuted)
                        }
                    }

                    DownloadState.FAILED,
                    DownloadState.CANCELLED -> {
                        TextButton(
                            onClick = onRetry,
                            modifier = Modifier.testTag("retry_download_${task.taskId}")
                        ) {
                            Icon(Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.size(16.dp), tint = colors.accentStrong)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Retry", fontFamily = PlusJakartaFontFamily, fontWeight = FontWeight.Bold, color = colors.accentStrong)
                        }
                        TextButton(
                            onClick = onRemove,
                            modifier = Modifier.testTag("remove_download_${task.taskId}")
                        ) {
                            Icon(Icons.Rounded.DeleteOutline, contentDescription = null, modifier = Modifier.size(16.dp), tint = colors.textMuted)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Remove", fontFamily = PlusJakartaFontFamily, color = colors.textMuted)
                        }
                    }

                    DownloadState.COMPLETED -> {
                        Icon(
                            imageVector = Icons.Rounded.CheckCircle,
                            contentDescription = "Completed",
                            tint = colors.accentStrong,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Saved offline",
                            fontFamily = PlusJakartaFontFamily,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = colors.accentStrong,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(
                            onClick = onPlayOffline,
                            modifier = Modifier.testTag("play_download_${task.taskId}")
                        ) {
                            Icon(Icons.Rounded.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp), tint = colors.accentStrong)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Play", fontFamily = PlusJakartaFontFamily, fontWeight = FontWeight.Bold, color = colors.accentStrong)
                        }
                        TextButton(
                            onClick = onRemove,
                            modifier = Modifier.testTag("remove_download_${task.taskId}")
                        ) {
                            Text("Clear", fontFamily = PlusJakartaFontFamily, color = colors.textMuted)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DownloadStateBadge(state: DownloadState, colors: EditorialColors) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = if (state == DownloadState.DOWNLOADING || state == DownloadState.COMPLETED) {
            colors.accentPill
        } else {
            colors.surfaceTinted
        },
        border = BorderStroke(0.75.dp, colors.cardBorder)
    ) {
        Text(
            text = state.displayLabel,
            fontFamily = PlusJakartaFontFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 10.sp,
            color = if (state == DownloadState.DOWNLOADING || state == DownloadState.COMPLETED) {
                colors.onAccentPill
            } else {
                colors.textSecondary
            },
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}

enum class DownloadManagerTab(val label: String) {
    DOWNLOADING("Downloading"),
    COMPLETED("Completed"),
    FAILED("Failed"),
    PAUSED("Paused")
}
