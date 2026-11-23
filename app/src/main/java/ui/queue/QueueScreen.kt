package ui.queue

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AddToQueue
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.BookmarkAdd
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.DownloadDone
import androidx.compose.material.icons.rounded.DragHandle
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Radio
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.VerticalAlignTop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.OutfitFontFamily
import com.example.ui.theme.PlusJakartaFontFamily
import com.example.viewmodel.SonoraViewModel
import data.queue.QueuedMusicItem
import ui.components.CircularArtwork
import ui.theme.LocalEditorialColors

/**
 * Editorial Unlimited Now Playing Queue Screen:
 * Backed by Room-persisted QueuedMusicItem records with unique monotonic queueItemId keys
 * so arbitrarily large queues (10,000+ songs, including duplicate songs) render lazily without stutter.
 * Supports: Current Playing (with Song Radio status), Up Next with swipe-to-remove,
 * drag-to-reorder handle + accessible move up/down, long-press options sheet, tap-to-play,
 * Recently Played, Shuffle Queue, Clear Queue, and Save Queue as Playlist.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun QueueScreen(
    viewModel: SonoraViewModel,
    onBack: () -> Unit,
    onOpenNowPlaying: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalEditorialColors.current
    val haptic = LocalHapticFeedback.current
    val density = LocalDensity.current
    val rowDragStepPx = with(density) { 64.dp.toPx() }

    val queue by viewModel.queue.collectAsState()
    val queuedItems by viewModel.queuedItems.collectAsState()
    val queueIndex by viewModel.queueIndex.collectAsState()
    val currentSong by viewModel.currentSong.collectAsState()
    val playbackInfo by viewModel.playbackInfo.collectAsState()
    val recentlyPlayed by viewModel.recentlyPlayedSongs.collectAsState()
    val songRadioSession by viewModel.songRadioSession.collectAsState()

    var showSaveDialog by remember { mutableStateOf(false) }
    var playlistNameInput by remember { mutableStateOf("") }
    var recentExpanded by remember { mutableStateOf(true) }
    var longPressTarget by remember { mutableStateOf<Pair<Int, QueuedMusicItem>?>(null) }

    val effectiveItems = remember(queue, queuedItems) {
        if (queuedItems.size == queue.size && queuedItems.isNotEmpty()) {
            queuedItems
        } else {
            queue.mapIndexed { idx, song -> QueuedMusicItem(queueItemId = -(idx.toLong() + 1L), song = song) }
        }
    }
    val safeIndex = queueIndex.coerceIn(0, (effectiveItems.size - 1).coerceAtLeast(0))
    val upNext = remember(effectiveItems, safeIndex) {
        if (effectiveItems.isNotEmpty() && safeIndex + 1 < effectiveItems.size) {
            effectiveItems.subList(safeIndex + 1, effectiveItems.size)
        } else emptyList()
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(colors.backgroundBrush)
            .statusBarsPadding()
            .navigationBarsPadding()
            .testTag("queue_screen"),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 760.dp)
                .fillMaxSize()
        ) {
            // Header
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
                            .testTag("queue_back_button")
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
                            text = "Up Next",
                            fontFamily = OutfitFontFamily,
                            fontWeight = FontWeight.Black,
                            fontSize = 26.sp,
                            color = colors.textPrimary
                        )
                        Text(
                            text = "${effectiveItems.size} tracks in unlimited queue",
                            fontFamily = PlusJakartaFontFamily,
                            fontSize = 12.sp,
                            color = colors.textSecondary
                        )
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    IconButton(
                        onClick = { viewModel.shuffleRemainingQueue() },
                        enabled = upNext.size > 1,
                        modifier = Modifier.size(48.dp).testTag("queue_shuffle_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Shuffle,
                            contentDescription = "Shuffle Upcoming Queue",
                            tint = colors.accentStrong
                        )
                    }
                    IconButton(
                        onClick = { showSaveDialog = true },
                        enabled = effectiveItems.isNotEmpty(),
                        modifier = Modifier.size(48.dp).testTag("queue_save_playlist_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.BookmarkAdd,
                            contentDescription = "Save Queue as Playlist",
                            tint = colors.textPrimary
                        )
                    }
                    IconButton(
                        onClick = { viewModel.clearQueue() },
                        enabled = upNext.isNotEmpty(),
                        modifier = Modifier.size(48.dp).testTag("queue_clear_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.DeleteSweep,
                            contentDescription = "Clear Upcoming Queue",
                            tint = colors.textSecondary
                        )
                    }
                }
            }

            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("queue_lazy_column"),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Current Playing Section
                currentSong?.let { active ->
                    item(key = "now_playing_queue_header") {
                        Text(
                            text = "CURRENT PLAYING",
                            fontFamily = PlusJakartaFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            letterSpacing = 1.sp,
                            color = colors.textMuted,
                            modifier = Modifier.padding(top = 4.dp, bottom = 4.dp)
                        )
                    }
                    item(key = "now_playing_queue_card") {
                        Surface(
                            onClick = onOpenNowPlaying,
                            shape = RoundedCornerShape(22.dp),
                            color = colors.accentPill.copy(alpha = 0.65f),
                            border = BorderStroke(1.dp, colors.accentStrong),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("queue_current_playing_card")
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    CircularArtwork(
                                        song = active,
                                        isPlaying = playbackInfo.isPlaying,
                                        accentColor = colors.accentStrong,
                                        modifier = Modifier.size(52.dp)
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = active.title,
                                            fontFamily = OutfitFontFamily,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 16.sp,
                                            color = colors.onAccentPill,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        val activeMeta = if (active.isOnline && active.providerName.isNotBlank()) {
                                            "${active.artist} • ${active.providerName}"
                                        } else {
                                            "${active.artist} • ${active.album}"
                                        }
                                        Text(
                                            text = activeMeta,
                                            fontFamily = PlusJakartaFontFamily,
                                            fontSize = 12.sp,
                                            color = colors.onAccentPill.copy(alpha = 0.78f),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    Icon(
                                        imageVector = Icons.Rounded.GraphicEq,
                                        contentDescription = "Playing",
                                        tint = colors.accentStrong
                                    )
                                }

                                if (songRadioSession.isActive) {
                                    Spacer(modifier = Modifier.height(10.dp))
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(14.dp))
                                            .background(colors.surface.copy(alpha = 0.6f))
                                            .padding(horizontal = 10.dp, vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                            Icon(
                                                imageVector = Icons.Rounded.Radio,
                                                contentDescription = null,
                                                tint = colors.accentStrong,
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = songRadioSession.seedLabel,
                                                fontFamily = PlusJakartaFontFamily,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 11.5.sp,
                                                color = colors.textPrimary,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                        TextButton(
                                            onClick = { viewModel.stopSongRadio() },
                                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                                            modifier = Modifier.testTag("queue_stop_radio_button")
                                        ) {
                                            Text(
                                                text = "Stop Radio",
                                                fontFamily = PlusJakartaFontFamily,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 11.5.sp,
                                                color = colors.accentStrong
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Up Next Section Header
                item(key = "up_next_label") {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 10.dp, bottom = 2.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (upNext.isEmpty()) "UP NEXT (END OF QUEUE)" else "UP NEXT (${upNext.size})",
                            fontFamily = PlusJakartaFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            letterSpacing = 1.sp,
                            color = colors.textMuted
                        )
                        if (upNext.isNotEmpty()) {
                            Text(
                                text = "Swipe to remove • Hold handle to drag",
                                fontFamily = PlusJakartaFontFamily,
                                fontSize = 10.5.sp,
                                color = colors.textMuted
                            )
                        }
                    }
                }

                if (upNext.isEmpty()) {
                    item(key = "empty_up_next") {
                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = colors.surfaceTinted,
                            border = BorderStroke(0.75.dp, colors.cardBorder),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(18.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Text(
                                    text = "No upcoming tracks queued. Tap any song, album, or Start Radio to keep music flowing.",
                                    fontFamily = PlusJakartaFontFamily,
                                    fontSize = 13.sp,
                                    color = colors.textSecondary
                                )
                                currentSong?.let { active ->
                                    Button(
                                        onClick = { viewModel.startSongRadio(active, autoPlayFirstAdded = false) },
                                        shape = RoundedCornerShape(14.dp),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = colors.accentStrong,
                                            contentColor = colors.onAccentStrong
                                        ),
                                        modifier = Modifier.testTag("queue_empty_start_radio_button")
                                    ) {
                                        Icon(Icons.Rounded.Radio, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = "Start Song Radio",
                                            fontFamily = PlusJakartaFontFamily,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 12.5.sp
                                        )
                                    }
                                }
                            }
                        }
                    }
                } else {
                    itemsIndexed(
                        items = upNext,
                        key = { _, item -> "q_${item.queueItemId}" }
                    ) { offsetIdx, queuedItem ->
                        val song = queuedItem.song
                        val actualQueueIndex = safeIndex + 1 + offsetIdx
                        var dragOffsetY by remember(queuedItem.queueItemId) { mutableFloatStateOf(0f) }

                        val dismissState = rememberSwipeToDismissBoxState(
                            confirmValueChange = { value ->
                                if (value == SwipeToDismissBoxValue.EndToStart || value == SwipeToDismissBoxValue.StartToEnd) {
                                    viewModel.removeFromQueue(actualQueueIndex)
                                    true
                                } else {
                                    false
                                }
                            }
                        )

                        SwipeToDismissBox(
                            state = dismissState,
                            backgroundContent = {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .clip(RoundedCornerShape(20.dp))
                                        .background(MaterialTheme.colorScheme.errorContainer)
                                        .padding(horizontal = 20.dp),
                                    contentAlignment = Alignment.CenterEnd
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Delete,
                                        contentDescription = "Remove from queue",
                                        tint = MaterialTheme.colorScheme.onErrorContainer
                                    )
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .graphicsLayer { translationY = dragOffsetY.coerceIn(-48f, 48f) }
                                .testTag("queue_item_${queuedItem.queueItemId}")
                        ) {
                            Surface(
                                shape = RoundedCornerShape(20.dp),
                                color = colors.surface,
                                border = BorderStroke(0.75.dp, colors.cardBorder),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(20.dp))
                                    .combinedClickable(
                                        onClick = { viewModel.playSong(song, queue, actualQueueIndex) },
                                        onLongClick = {
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            longPressTarget = actualQueueIndex to queuedItem
                                        }
                                    )
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // Drag-to-reorder handle
                                    Box(
                                        modifier = Modifier
                                            .size(36.dp)
                                            .pointerInput(actualQueueIndex, upNext.size) {
                                                var currentIdx = actualQueueIndex
                                                var accum = 0f
                                                detectDragGesturesAfterLongPress(
                                                    onDragStart = {
                                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                        accum = 0f
                                                        dragOffsetY = 0f
                                                    },
                                                    onDragEnd = {
                                                        accum = 0f
                                                        dragOffsetY = 0f
                                                    },
                                                    onDragCancel = {
                                                        accum = 0f
                                                        dragOffsetY = 0f
                                                    },
                                                    onDrag = { change, dragAmount ->
                                                        change.consume()
                                                        accum += dragAmount.y
                                                        dragOffsetY = accum
                                                        if (accum >= rowDragStepPx && currentIdx < queue.lastIndex) {
                                                            viewModel.reorderQueue(currentIdx, currentIdx + 1)
                                                            currentIdx += 1
                                                            accum -= rowDragStepPx
                                                            dragOffsetY = accum
                                                        } else if (accum <= -rowDragStepPx && currentIdx > safeIndex + 1) {
                                                            viewModel.reorderQueue(currentIdx, currentIdx - 1)
                                                            currentIdx -= 1
                                                            accum += rowDragStepPx
                                                            dragOffsetY = accum
                                                        }
                                                    }
                                                )
                                            },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.DragHandle,
                                            contentDescription = "Drag to reorder",
                                            tint = colors.textMuted,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }

                                    CircularArtwork(
                                        song = song,
                                        accentColor = colors.accentPill,
                                        modifier = Modifier.size(42.dp)
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = song.title,
                                            fontFamily = OutfitFontFamily,
                                            fontWeight = FontWeight.SemiBold,
                                            fontSize = 14.sp,
                                            color = colors.textPrimary,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        val rowMeta = if (song.isOnline && song.providerName.isNotBlank()) {
                                            "${song.artist} • ${song.durationFormatted} • ${song.providerName}"
                                        } else {
                                            "${song.artist} • ${song.durationFormatted}"
                                        }
                                        Text(
                                            text = rowMeta,
                                            fontFamily = PlusJakartaFontFamily,
                                            fontSize = 12.sp,
                                            color = colors.textSecondary,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }

                                    if (song.isOnline) {
                                        val isDownloaded = viewModel.isSongDownloaded(song)
                                        IconButton(
                                            onClick = { viewModel.requestDownloadSong(song) },
                                            modifier = Modifier
                                                .size(40.dp)
                                                .testTag("queue_download_btn_${song.id}")
                                        ) {
                                            Icon(
                                                imageVector = if (isDownloaded) Icons.Rounded.DownloadDone else Icons.Rounded.Download,
                                                contentDescription = if (isDownloaded) "Downloaded Offline" else "Download Track",
                                                tint = if (isDownloaded) colors.accentStrong else colors.textSecondary,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }

                                    if (offsetIdx > 0) {
                                        IconButton(
                                            onClick = { viewModel.reorderQueue(actualQueueIndex, actualQueueIndex - 1) },
                                            modifier = Modifier.size(36.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Rounded.ArrowUpward,
                                                contentDescription = "Move Up",
                                                tint = colors.textSecondary,
                                                modifier = Modifier.size(17.dp)
                                            )
                                        }
                                    }
                                    if (offsetIdx < upNext.lastIndex) {
                                        IconButton(
                                            onClick = { viewModel.reorderQueue(actualQueueIndex, actualQueueIndex + 1) },
                                            modifier = Modifier.size(36.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Rounded.ArrowDownward,
                                                contentDescription = "Move Down",
                                                tint = colors.textSecondary,
                                                modifier = Modifier.size(17.dp)
                                            )
                                        }
                                    }
                                    IconButton(
                                        onClick = { viewModel.removeFromQueue(actualQueueIndex) },
                                        modifier = Modifier.size(36.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.Close,
                                            contentDescription = "Remove from queue",
                                            tint = colors.textMuted,
                                            modifier = Modifier.size(17.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Recently Played Section (Collapsible)
                if (recentlyPlayed.isNotEmpty()) {
                    item(key = "recent_history_header") {
                        Surface(
                            onClick = { recentExpanded = !recentExpanded },
                            shape = RoundedCornerShape(14.dp),
                            color = colors.surfaceTinted,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 12.dp)
                                .testTag("queue_recently_played_toggle")
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Rounded.History,
                                        contentDescription = null,
                                        tint = colors.accentStrong,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "RECENTLY PLAYED (${recentlyPlayed.size.coerceAtMost(15)})",
                                        fontFamily = PlusJakartaFontFamily,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.sp,
                                        letterSpacing = 1.sp,
                                        color = colors.textMuted
                                    )
                                }
                                Icon(
                                    imageVector = if (recentExpanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                                    contentDescription = if (recentExpanded) "Collapse Recently Played" else "Expand Recently Played",
                                    tint = colors.textSecondary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }

                    if (recentExpanded) {
                        itemsIndexed(recentlyPlayed.take(15), key = { idx, song -> "hist_${song.id}_$idx" }) { _, song ->
                            Surface(
                                onClick = { viewModel.playSong(song, recentlyPlayed) },
                                shape = RoundedCornerShape(18.dp),
                                color = colors.surfaceTinted,
                                border = BorderStroke(0.75.dp, colors.cardBorder),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    CircularArtwork(
                                        song = song,
                                        accentColor = colors.accentPill,
                                        modifier = Modifier.size(40.dp)
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = song.title,
                                            fontFamily = OutfitFontFamily,
                                            fontWeight = FontWeight.SemiBold,
                                            fontSize = 14.sp,
                                            color = colors.textPrimary,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = if (song.isOnline && song.providerName.isNotBlank()) "${song.artist} • ${song.providerName}" else song.artist,
                                            fontFamily = PlusJakartaFontFamily,
                                            fontSize = 12.sp,
                                            color = colors.textSecondary,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    IconButton(
                                        onClick = { viewModel.addToQueue(song) },
                                        modifier = Modifier.size(40.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.AddToQueue,
                                            contentDescription = "Add to Queue",
                                            tint = colors.accentStrong,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                item { Spacer(modifier = Modifier.height(24.dp)) }
            }
        }
    }

    // Long-press Queue Item Options Sheet
    longPressTarget?.let { (targetIdx, queuedItem) ->
        val song = queuedItem.song
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { longPressTarget = null },
            sheetState = sheetState,
            containerColor = colors.surface,
            contentColor = colors.textPrimary,
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 24.dp)
                    .testTag("queue_item_options_sheet"),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = song.title,
                    fontFamily = OutfitFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp,
                    color = colors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = song.artist,
                    fontFamily = PlusJakartaFontFamily,
                    fontSize = 12.5.sp,
                    color = colors.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(8.dp))
                HorizontalDivider(color = colors.cardBorder)
                Spacer(modifier = Modifier.height(4.dp))

                QueueSheetAction(
                    icon = Icons.Rounded.PlayArrow,
                    label = "Play Now",
                    colors = colors,
                    onClick = {
                        viewModel.playSong(song, queue, targetIdx)
                        longPressTarget = null
                    }
                )
                if (targetIdx > safeIndex + 1) {
                    QueueSheetAction(
                        icon = Icons.Rounded.VerticalAlignTop,
                        label = "Move to Play Next",
                        colors = colors,
                        onClick = {
                            viewModel.reorderQueue(targetIdx, safeIndex + 1)
                            longPressTarget = null
                        }
                    )
                }
                QueueSheetAction(
                    icon = Icons.Rounded.Radio,
                    label = "Start Song Radio",
                    colors = colors,
                    onClick = {
                        viewModel.startSongRadio(song, autoPlayFirstAdded = false)
                        longPressTarget = null
                    }
                )
                if (song.isOnline) {
                    QueueSheetAction(
                        icon = Icons.Rounded.Download,
                        label = "Download Track",
                        colors = colors,
                        onClick = {
                            viewModel.requestDownloadSong(song)
                            longPressTarget = null
                        }
                    )
                }
                QueueSheetAction(
                    icon = Icons.Rounded.Delete,
                    label = "Remove from Queue",
                    colors = colors,
                    onClick = {
                        viewModel.removeFromQueue(targetIdx)
                        longPressTarget = null
                    }
                )
            }
        }
    }

    if (showSaveDialog) {
        AlertDialog(
            onDismissRequest = { showSaveDialog = false },
            containerColor = colors.surface,
            titleContentColor = colors.textPrimary,
            textContentColor = colors.textSecondary,
            title = {
                Text(
                    text = "Save Queue as Playlist",
                    fontFamily = OutfitFontFamily,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                OutlinedTextField(
                    value = playlistNameInput,
                    onValueChange = { playlistNameInput = it },
                    placeholder = { Text("e.g., Late Night Session") },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val name = playlistNameInput.trim().ifBlank { "Queue Mix" }
                        viewModel.saveQueueAsPlaylist(name)
                        playlistNameInput = ""
                        showSaveDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = colors.accentStrong,
                        contentColor = colors.onAccentStrong
                    )
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { showSaveDialog = false }) {
                    Text("Cancel", color = colors.textSecondary)
                }
            }
        )
    }
}

@Composable
private fun QueueSheetAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    colors: ui.theme.EditorialColors,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        color = colors.surface,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = colors.accentStrong,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = label,
                fontFamily = PlusJakartaFontFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp,
                color = colors.textPrimary
            )
        }
    }
}
