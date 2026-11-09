package ui.player

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Lyrics
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.PlayerStyle
import com.example.model.Song
import com.example.ui.components.ArtworkAlbumView
import com.example.ui.theme.SonoraCardBorder
import com.example.ui.theme.SonoraCharcoal
import com.example.ui.theme.SonoraCharcoalBorder
import com.example.ui.theme.SonoraDustyRose
import com.example.ui.theme.SonoraTextMuted
import com.example.ui.theme.SonoraTextPrimary
import com.example.ui.theme.SonoraTextSecondary
import com.example.util.LyricsExtractor
import com.example.viewmodel.SonoraViewModel
import kotlinx.coroutines.launch

/**
 * Unified PlayerScreen Coordinator
 * Hosts all 9 customizable Player Styles with seamless animated transitions,
 * Quick Style Switching (Style Wheel on artwork long-press), and shared Queue / Lyrics sheets.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerScreen(
    viewModel: SonoraViewModel,
    onClose: () -> Unit,
    onNavigateToEqualizer: () -> Unit,
    modifier: Modifier = Modifier
) {
    val currentStyle by viewModel.playerStyle.collectAsState()
    val currentSeekbarStyle by viewModel.seekbarStyle.collectAsState()
    val currentSong by viewModel.currentSong.collectAsState()
    val playbackInfo by viewModel.playbackInfo.collectAsState()
    val queue by viewModel.queue.collectAsState()
    val queueIndex by viewModel.queueIndex.collectAsState()
    val currentTrackLyrics by viewModel.currentTrackLyrics.collectAsState()

    var showStyleWheel by remember { mutableStateOf(false) }
    var showQueueSheet by remember { mutableStateOf(false) }
    var showLyricsSheet by remember { mutableStateOf(false) }
    var showSleepTimerDialog by remember { mutableStateOf(false) }

    val coroutineScope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // Dynamic Player Style Layout with Smooth Animated Transitions
        AnimatedContent(
            targetState = currentStyle,
            transitionSpec = {
                (fadeIn(animationSpec = tween(280)) + scaleIn(initialScale = 0.97f, animationSpec = tween(280)))
                    .togetherWith(fadeOut(animationSpec = tween(200)))
            },
            label = "playerStyleSwitch"
        ) { targetStyle ->
            when (targetStyle) {
                PlayerStyle.CLASSIC -> ClassicPlayer(
                    song = currentSong,
                    playbackInfo = playbackInfo,
                    onTogglePlayPause = { viewModel.togglePlayPause() },
                    onPlayNext = { viewModel.playNext() },
                    onPlayPrevious = { viewModel.playPrevious() },
                    onSeekTo = { viewModel.seekTo(it) },
                    onToggleShuffle = { viewModel.toggleShuffle() },
                    onCycleRepeat = { viewModel.cycleRepeatMode() },
                    onOpenQueue = { showQueueSheet = true },
                    onOpenLyrics = { showLyricsSheet = true },
                    onOpenSleepTimer = { showSleepTimerDialog = true },
                    onClose = onClose,
                    onNavigateToEqualizer = onNavigateToEqualizer,
                    onArtworkLongPress = { showStyleWheel = true },
                    seekbarStyle = currentSeekbarStyle
                )
                PlayerStyle.GESTURE -> GesturePlayer(
                    song = currentSong,
                    playbackInfo = playbackInfo,
                    onTogglePlayPause = { viewModel.togglePlayPause() },
                    onPlayNext = { viewModel.playNext() },
                    onPlayPrevious = { viewModel.playPrevious() },
                    onSeekTo = { viewModel.seekTo(it) },
                    onToggleShuffle = { viewModel.toggleShuffle() },
                    onCycleRepeat = { viewModel.cycleRepeatMode() },
                    onOpenQueue = { showQueueSheet = true },
                    onOpenLyrics = { showLyricsSheet = true },
                    onOpenSleepTimer = { showSleepTimerDialog = true },
                    onClose = onClose,
                    onNavigateToEqualizer = onNavigateToEqualizer,
                    onArtworkLongPress = { showStyleWheel = true },
                    seekbarStyle = currentSeekbarStyle
                )
                PlayerStyle.EDITORIAL -> EditorialPlayer(
                    song = currentSong,
                    playbackInfo = playbackInfo,
                    onTogglePlayPause = { viewModel.togglePlayPause() },
                    onPlayNext = { viewModel.playNext() },
                    onPlayPrevious = { viewModel.playPrevious() },
                    onSeekTo = { viewModel.seekTo(it) },
                    onToggleShuffle = { viewModel.toggleShuffle() },
                    onCycleRepeat = { viewModel.cycleRepeatMode() },
                    onOpenQueue = { showQueueSheet = true },
                    onOpenLyrics = { showLyricsSheet = true },
                    onOpenSleepTimer = { showSleepTimerDialog = true },
                    onClose = onClose,
                    onNavigateToEqualizer = onNavigateToEqualizer,
                    onArtworkLongPress = { showStyleWheel = true },
                    seekbarStyle = currentSeekbarStyle
                )
                PlayerStyle.CANVAS -> CanvasPlayer(
                    song = currentSong,
                    playbackInfo = playbackInfo,
                    onTogglePlayPause = { viewModel.togglePlayPause() },
                    onPlayNext = { viewModel.playNext() },
                    onPlayPrevious = { viewModel.playPrevious() },
                    onSeekTo = { viewModel.seekTo(it) },
                    onToggleShuffle = { viewModel.toggleShuffle() },
                    onCycleRepeat = { viewModel.cycleRepeatMode() },
                    onOpenQueue = { showQueueSheet = true },
                    onOpenLyrics = { showLyricsSheet = true },
                    onOpenSleepTimer = { showSleepTimerDialog = true },
                    onClose = onClose,
                    onNavigateToEqualizer = onNavigateToEqualizer,
                    onArtworkLongPress = { showStyleWheel = true },
                    seekbarStyle = currentSeekbarStyle
                )
                PlayerStyle.BENTO -> BentoPlayer(
                    song = currentSong,
                    playbackInfo = playbackInfo,
                    onTogglePlayPause = { viewModel.togglePlayPause() },
                    onPlayNext = { viewModel.playNext() },
                    onPlayPrevious = { viewModel.playPrevious() },
                    onSeekTo = { viewModel.seekTo(it) },
                    onToggleShuffle = { viewModel.toggleShuffle() },
                    onCycleRepeat = { viewModel.cycleRepeatMode() },
                    onOpenQueue = { showQueueSheet = true },
                    onOpenLyrics = { showLyricsSheet = true },
                    onOpenSleepTimer = { showSleepTimerDialog = true },
                    onClose = onClose,
                    onNavigateToEqualizer = onNavigateToEqualizer,
                    onArtworkLongPress = { showStyleWheel = true },
                    seekbarStyle = currentSeekbarStyle
                )
                PlayerStyle.STICKER -> StickerPlayer(
                    song = currentSong,
                    playbackInfo = playbackInfo,
                    onTogglePlayPause = { viewModel.togglePlayPause() },
                    onPlayNext = { viewModel.playNext() },
                    onPlayPrevious = { viewModel.playPrevious() },
                    onSeekTo = { viewModel.seekTo(it) },
                    onToggleShuffle = { viewModel.toggleShuffle() },
                    onCycleRepeat = { viewModel.cycleRepeatMode() },
                    onOpenQueue = { showQueueSheet = true },
                    onOpenLyrics = { showLyricsSheet = true },
                    onOpenSleepTimer = { showSleepTimerDialog = true },
                    onClose = onClose,
                    onNavigateToEqualizer = onNavigateToEqualizer,
                    onArtworkLongPress = { showStyleWheel = true },
                    seekbarStyle = currentSeekbarStyle
                )
                PlayerStyle.MORPH -> MorphPlayer(
                    song = currentSong,
                    playbackInfo = playbackInfo,
                    onTogglePlayPause = { viewModel.togglePlayPause() },
                    onPlayNext = { viewModel.playNext() },
                    onPlayPrevious = { viewModel.playPrevious() },
                    onSeekTo = { viewModel.seekTo(it) },
                    onToggleShuffle = { viewModel.toggleShuffle() },
                    onCycleRepeat = { viewModel.cycleRepeatMode() },
                    onOpenQueue = { showQueueSheet = true },
                    onOpenLyrics = { showLyricsSheet = true },
                    onOpenSleepTimer = { showSleepTimerDialog = true },
                    onClose = onClose,
                    onNavigateToEqualizer = onNavigateToEqualizer,
                    onArtworkLongPress = { showStyleWheel = true },
                    seekbarStyle = currentSeekbarStyle
                )
                PlayerStyle.DIAL -> DialPlayer(
                    song = currentSong,
                    playbackInfo = playbackInfo,
                    onTogglePlayPause = { viewModel.togglePlayPause() },
                    onPlayNext = { viewModel.playNext() },
                    onPlayPrevious = { viewModel.playPrevious() },
                    onSeekTo = { viewModel.seekTo(it) },
                    onToggleShuffle = { viewModel.toggleShuffle() },
                    onCycleRepeat = { viewModel.cycleRepeatMode() },
                    onOpenQueue = { showQueueSheet = true },
                    onOpenLyrics = { showLyricsSheet = true },
                    onOpenSleepTimer = { showSleepTimerDialog = true },
                    onClose = onClose,
                    onNavigateToEqualizer = onNavigateToEqualizer,
                    onArtworkLongPress = { showStyleWheel = true },
                    seekbarStyle = currentSeekbarStyle
                )
                PlayerStyle.HERO -> HeroPlayer(
                    song = currentSong,
                    playbackInfo = playbackInfo,
                    onTogglePlayPause = { viewModel.togglePlayPause() },
                    onPlayNext = { viewModel.playNext() },
                    onPlayPrevious = { viewModel.playPrevious() },
                    onSeekTo = { viewModel.seekTo(it) },
                    onToggleShuffle = { viewModel.toggleShuffle() },
                    onCycleRepeat = { viewModel.cycleRepeatMode() },
                    onOpenQueue = { showQueueSheet = true },
                    onOpenLyrics = { showLyricsSheet = true },
                    onOpenSleepTimer = { showSleepTimerDialog = true },
                    onClose = onClose,
                    onNavigateToEqualizer = onNavigateToEqualizer,
                    onArtworkLongPress = { showStyleWheel = true },
                    seekbarStyle = currentSeekbarStyle
                )
            }
        }

        // Quick Style Switching Modal (Style Wheel)
        if (showStyleWheel) {
            PlayerStyleWheel(
                currentStyle = currentStyle,
                onSelectStyle = { newStyle ->
                    viewModel.setPlayerStyle(newStyle)
                },
                onDismiss = { showStyleWheel = false }
            )
        }

        // Shared Queue Modal Bottom Sheet
        if (showQueueSheet) {
            ModalBottomSheet(
                onDismissRequest = { showQueueSheet = false },
                sheetState = sheetState,
                containerColor = SonoraCharcoal,
                contentColor = SonoraTextPrimary,
                shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "PLAYING QUEUE",
                                color = SonoraDustyRose,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp
                            )
                            Text(
                                text = "${queue.size} tracks in line",
                                color = SonoraTextSecondary,
                                fontSize = 13.sp
                            )
                        }
                        TextButton(onClick = { viewModel.clearQueue() }) {
                            Text("Clear", color = SonoraDustyRose)
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(360.dp),
                        contentPadding = PaddingValues(vertical = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        itemsIndexed(queue) { index, qSong ->
                            val isCurrent = index == queueIndex
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable {
                                        viewModel.playSong(qSong, queue)
                                    },
                                color = if (isCurrent) SonoraDustyRose.copy(alpha = 0.2f) else Color.White.copy(alpha = 0.04f)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "${index + 1}",
                                        color = if (isCurrent) SonoraDustyRose else SonoraTextMuted,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.width(28.dp)
                                    )
                                    ArtworkAlbumView(
                                        song = qSong,
                                        modifier = Modifier.size(38.dp),
                                        cornerRadius = 8.dp,
                                        isPlaying = isCurrent && playbackInfo.isPlaying
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = qSong.title,
                                            color = if (isCurrent) SonoraDustyRose else SonoraTextPrimary,
                                            fontSize = 14.sp,
                                            fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Medium,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = qSong.artist,
                                            color = SonoraTextSecondary,
                                            fontSize = 12.sp,
                                            maxLines = 1
                                        )
                                    }
                                    IconButton(
                                        onClick = { viewModel.removeFromQueue(index) },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(Icons.Default.Delete, contentDescription = "Remove", tint = SonoraTextMuted, modifier = Modifier.size(18.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Shared Lyrics Modal Bottom Sheet
        if (showLyricsSheet) {
            val lyrics = currentTrackLyrics ?: currentSong?.lyrics
            ModalBottomSheet(
                onDismissRequest = { showLyricsSheet = false },
                containerColor = SonoraCharcoal,
                contentColor = SonoraTextPrimary,
                shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "TRACK LYRICS",
                            color = SonoraDustyRose,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )
                        IconButton(onClick = { showLyricsSheet = false }, modifier = Modifier.size(36.dp)) {
                            Icon(Icons.Default.Close, contentDescription = "Close", tint = SonoraTextPrimary)
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    if (lyrics.isNullOrBlank()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(200.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "No lyrics found for this song.\nYou can add lyrics from Song Options.",
                                color = SonoraTextMuted,
                                textAlign = TextAlign.Center,
                                fontSize = 14.sp
                            )
                        }
                    } else {
                        val parsedLines = remember(lyrics) { LyricsExtractor.parseLrcLines(lyrics) }
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(380.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            if (parsedLines.isNotEmpty()) {
                                itemsIndexed(parsedLines) { _, line ->
                                    val isCurrentLine = playbackInfo.currentPositionMs in line.timestampMs..(line.timestampMs + 4000L)
                                    Text(
                                        text = line.text,
                                        color = if (isCurrentLine) SonoraDustyRose else SonoraTextSecondary,
                                        fontSize = if (isCurrentLine) 18.sp else 15.sp,
                                        fontWeight = if (isCurrentLine) FontWeight.Bold else FontWeight.Normal,
                                        lineHeight = 22.sp
                                    )
                                }
                            } else {
                                item {
                                    Text(
                                        text = lyrics,
                                        color = SonoraTextSecondary,
                                        fontSize = 15.sp,
                                        lineHeight = 24.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Shared Sleep Timer Dialog
        if (showSleepTimerDialog) {
            val timerOptions = listOf(15, 30, 45, 60, 90)
            AlertDialog(
                onDismissRequest = { showSleepTimerDialog = false },
                title = { Text("Sleep Timer", color = SonoraTextPrimary, fontWeight = FontWeight.Bold) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        timerOptions.forEach { minutes ->
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .clickable {
                                        viewModel.setSleepTimer(minutes)
                                        showSleepTimerDialog = false
                                    },
                                color = SonoraDustyRose.copy(alpha = 0.15f)
                            ) {
                                Text(
                                    text = "$minutes minutes",
                                    color = SonoraDustyRose,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.padding(12.dp)
                                )
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        viewModel.setSleepTimer(0)
                        showSleepTimerDialog = false
                    }) {
                        Text("Turn Off", color = SonoraDustyRose)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showSleepTimerDialog = false }) {
                        Text("Cancel", color = SonoraTextMuted)
                    }
                },
                containerColor = SonoraCharcoal
            )
        }
    }
}
