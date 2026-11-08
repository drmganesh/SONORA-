package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Lyrics
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.SpatialAudio
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.RepeatMode
import com.example.model.ShuffleMode
import com.example.model.Song
import com.example.model.VisualizerStyle
import com.example.ui.components.ArtworkAlbumView
import com.example.ui.components.AudioVisualizerView
import com.example.ui.theme.SonoraCardBorder
import com.example.ui.theme.SonoraCyan
import com.example.ui.theme.SonoraGold
import com.example.ui.theme.SonoraGoldDark
import com.example.ui.theme.SonoraObsidian
import com.example.ui.theme.SonoraOnGold
import com.example.ui.theme.SonoraSurfaceDark
import com.example.ui.theme.SonoraSurfaceElevated
import com.example.ui.theme.SonoraTextMuted
import com.example.ui.theme.SonoraTextPrimary
import com.example.ui.theme.SonoraTextSecondary
import com.example.viewmodel.SonoraViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NowPlayingScreen(
    viewModel: SonoraViewModel,
    onClose: () -> Unit,
    onNavigateToEqualizer: () -> Unit,
    modifier: Modifier = Modifier
) {
    val currentSong by viewModel.currentSong.collectAsState()
    val playbackInfo by viewModel.playbackInfo.collectAsState()
    val queue by viewModel.queue.collectAsState()
    val queueIndex by viewModel.queueIndex.collectAsState()
    val visualizerFrequencies by viewModel.visualizerFrequencies.collectAsState()
    val waveformSamples by viewModel.waveformSamples.collectAsState()
    val visualizerStyle by viewModel.visualizerStyle.collectAsState()
    val spatialConfig by viewModel.spatialConfig.collectAsState()

    var showQueueSheet by remember { mutableStateOf(false) }
    var showLyricsSheet by remember { mutableStateOf(false) }
    var showSleepTimerDialog by remember { mutableStateOf(false) }
    var showVisualizerPicker by remember { mutableStateOf(false) }
    var isUserSeeking by remember { mutableStateOf(false) }
    var userSeekPos by remember { mutableFloatStateOf(0f) }

    val currentPosition = if (isUserSeeking) userSeekPos.toLong() else playbackInfo.currentPositionMs
    val duration = (playbackInfo.durationMs).coerceAtLeast(currentSong?.durationMs ?: 0L)

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(
                        SonoraSurfaceDark,
                        SonoraObsidian,
                        Color.Black
                    )
                )
            )
            .testTag("now_playing_screen")
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Top App Bar Controls
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp, bottom = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onClose, modifier = Modifier.testTag("collapse_now_playing_button")) {
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowDown,
                        contentDescription = "Collapse",
                        tint = SonoraTextPrimary,
                        modifier = Modifier.size(32.dp)
                    )
                }

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "PLAYING FROM QUEUE",
                        color = SonoraTextMuted,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                    Text(
                        text = "${queueIndex + 1} of ${queue.size.coerceAtLeast(1)}",
                        color = SonoraGold,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                // Top right actions: Sleep timer indicator & Queue drawer button
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { showSleepTimerDialog = true }) {
                        Icon(
                            imageVector = Icons.Default.Bedtime,
                            contentDescription = "Sleep Timer",
                            tint = if (playbackInfo.sleepTimerMinutesRemaining != null) SonoraGold else SonoraTextSecondary,
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    IconButton(onClick = { showQueueSheet = true }, modifier = Modifier.testTag("open_queue_button")) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.QueueMusic,
                            contentDescription = "Queue",
                            tint = SonoraTextPrimary,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Large Hero Album Artwork Card (Glass-like aesthetic)
            ArtworkAlbumView(
                song = currentSong,
                modifier = Modifier
                    .size(280.dp)
                    .clip(RoundedCornerShape(28.dp)),
                cornerRadius = 28.dp,
                isPlaying = playbackInfo.isPlaying,
                isVinylRotating = false
            )

            Spacer(modifier = Modifier.height(20.dp))

            // Real-Time Audio Visualizer Strip (Tappable to cycle modes)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(SonoraSurfaceElevated.copy(alpha = 0.6f))
                    .border(1.dp, SonoraCardBorder, RoundedCornerShape(16.dp))
                    .clickable { showVisualizerPicker = true }
                    .padding(horizontal = 12.dp, vertical = 6.dp)
                    .testTag("live_visualizer_strip"),
                contentAlignment = Alignment.Center
            ) {
                AudioVisualizerView(
                    frequencies = visualizerFrequencies,
                    waveform = waveformSamples,
                    style = visualizerStyle,
                    modifier = Modifier.fillMaxSize()
                )

                // Subtitle indicating visualizer mode
                Text(
                    text = "VISUALIZER: ${visualizerStyle.name.replace("_", " ")} (TAP TO CHANGE)",
                    color = SonoraGold.copy(alpha = 0.5f),
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(2.dp)
                )
            }

            Spacer(modifier = Modifier.height(18.dp))

            // Track Title, Artist, and Favorite Button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = currentSong?.title ?: "No Track Selected",
                        color = SonoraTextPrimary,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.ExtraBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = currentSong?.artist ?: "Unknown Artist",
                        color = SonoraTextSecondary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = currentSong?.album ?: "Unknown Album",
                        color = SonoraTextMuted,
                        fontSize = 13.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                IconButton(
                    onClick = { currentSong?.let { viewModel.toggleFavorite(it) } },
                    modifier = Modifier.testTag("favorite_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Favorite,
                        contentDescription = "Favorite",
                        tint = if (currentSong?.isFavorite == true) SonoraGold else SonoraTextMuted.copy(alpha = 0.4f),
                        modifier = Modifier.size(28.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Format Badge & Audio Engine Indicators
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .background(SonoraCyan.copy(alpha = 0.15f), RoundedCornerShape(8.dp))
                        .border(1.dp, SonoraCyan.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = currentSong?.audioFormatInfo ?: "STUDIO MASTER",
                        color = SonoraCyan,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                if (spatialConfig.isEnabled) {
                    Box(
                        modifier = Modifier
                            .background(SonoraGold.copy(alpha = 0.15f), RoundedCornerShape(8.dp))
                            .border(1.dp, SonoraGold.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Text(
                            text = "360° SPATIAL",
                            color = SonoraGold,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Progress Slider
            Slider(
                value = currentPosition.toFloat(),
                onValueChange = {
                    isUserSeeking = true
                    userSeekPos = it
                },
                onValueChangeFinished = {
                    viewModel.seekTo(userSeekPos.toLong())
                    isUserSeeking = false
                },
                valueRange = 0f..duration.toFloat().coerceAtLeast(1f),
                colors = SliderDefaults.colors(
                    thumbColor = SonoraGold,
                    activeTrackColor = SonoraGold,
                    inactiveTrackColor = SonoraSurfaceElevated
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("progress_slider")
            )

            // Elapsed and Remaining Time
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = formatMs(currentPosition),
                    color = SonoraTextSecondary,
                    fontSize = 12.sp
                )
                Text(
                    text = "-${formatMs((duration - currentPosition).coerceAtLeast(0))}",
                    color = SonoraTextSecondary,
                    fontSize = 12.sp
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Core Playback Controls (Shuffle, Prev, Play/Pause, Next, Repeat)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Shuffle Button
                IconButton(
                    onClick = { viewModel.toggleShuffle() },
                    modifier = Modifier.testTag("shuffle_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Shuffle,
                        contentDescription = "Shuffle",
                        tint = if (playbackInfo.shuffleMode == ShuffleMode.ON) SonoraGold else SonoraTextMuted,
                        modifier = Modifier.size(26.dp)
                    )
                }

                // Previous Button
                IconButton(
                    onClick = { viewModel.playPrevious() },
                    modifier = Modifier.testTag("previous_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.SkipPrevious,
                        contentDescription = "Previous",
                        tint = SonoraTextPrimary,
                        modifier = Modifier.size(36.dp)
                    )
                }

                // Master Play/Pause Button
                IconButton(
                    onClick = { viewModel.togglePlayPause() },
                    modifier = Modifier
                        .size(72.dp)
                        .shadow(16.dp, CircleShape, spotColor = SonoraGold)
                        .background(
                            Brush.linearGradient(listOf(SonoraGold, SonoraGoldDark)),
                            CircleShape
                        )
                        .testTag("play_pause_button")
                ) {
                    Icon(
                        imageVector = if (playbackInfo.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (playbackInfo.isPlaying) "Pause" else "Play",
                        tint = SonoraOnGold,
                        modifier = Modifier.size(38.dp)
                    )
                }

                // Next Button
                IconButton(
                    onClick = { viewModel.playNext() },
                    modifier = Modifier.testTag("next_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.SkipNext,
                        contentDescription = "Next",
                        tint = SonoraTextPrimary,
                        modifier = Modifier.size(36.dp)
                    )
                }

                // Repeat Button
                IconButton(
                    onClick = { viewModel.cycleRepeatMode() },
                    modifier = Modifier.testTag("repeat_button")
                ) {
                    Icon(
                        imageVector = if (playbackInfo.repeatMode == RepeatMode.ONE) Icons.Default.RepeatOne else Icons.Default.Repeat,
                        contentDescription = "Repeat",
                        tint = if (playbackInfo.repeatMode != RepeatMode.OFF) SonoraGold else SonoraTextMuted,
                        modifier = Modifier.size(26.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Quick Shortcut Floating Toolbar (Equalizer, 360 Spatial, Lyrics)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(SonoraSurfaceElevated, RoundedCornerShape(18.dp))
                    .border(1.dp, SonoraCardBorder, RoundedCornerShape(18.dp))
                    .padding(vertical = 8.dp, horizontal = 16.dp),
                horizontalArrangement = Arrangement.SpaceAround,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Open Equalizer
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .clickable { onNavigateToEqualizer() }
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.GraphicEq, contentDescription = null, tint = SonoraGold, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("EQ", color = SonoraTextPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }

                // 360 Spatial Toggle
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .clickable {
                            viewModel.updateSpatialConfig(spatialConfig.copy(isEnabled = !spatialConfig.isEnabled))
                        }
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.SpatialAudio,
                        contentDescription = null,
                        tint = if (spatialConfig.isEnabled) SonoraCyan else SonoraTextMuted,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (spatialConfig.isEnabled) "360° ON" else "360° OFF",
                        color = if (spatialConfig.isEnabled) SonoraCyan else SonoraTextMuted,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                // Lyrics Sheet Toggle
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .clickable { showLyricsSheet = true }
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Lyrics, contentDescription = null, tint = SonoraGold, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Lyrics", color = SonoraTextPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }

        // Queue Bottom Sheet
        if (showQueueSheet) {
            ModalBottomSheet(
                onDismissRequest = { showQueueSheet = false },
                containerColor = SonoraSurfaceDark,
                sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
            ) {
                QueueSheetContent(
                    queue = queue,
                    currentIndex = queueIndex,
                    onPlaySongAt = { idx ->
                        viewModel.playSong(queue[idx], queue, idx)
                        showQueueSheet = false
                    },
                    onRemoveSongAt = { idx -> viewModel.removeFromQueue(idx) },
                    onClearQueue = { viewModel.clearQueue() },
                    onSaveQueue = { name -> viewModel.saveQueueAsPlaylist(name) },
                    onClose = { showQueueSheet = false }
                )
            }
        }

        // Lyrics Bottom Sheet
        if (showLyricsSheet) {
            ModalBottomSheet(
                onDismissRequest = { showLyricsSheet = false },
                containerColor = SonoraSurfaceDark,
                sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
            ) {
                LyricsSheetContent(
                    song = currentSong,
                    onClose = { showLyricsSheet = false }
                )
            }
        }

        // Sleep Timer Dialog
        if (showSleepTimerDialog) {
            SleepTimerDialog(
                currentTimerMinutes = playbackInfo.sleepTimerMinutesRemaining,
                onDismiss = { showSleepTimerDialog = false },
                onSetTimer = { minutes ->
                    viewModel.setSleepTimer(minutes)
                    showSleepTimerDialog = false
                }
            )
        }

        // Visualizer Picker Dialog
        if (showVisualizerPicker) {
            VisualizerPickerDialog(
                currentStyle = visualizerStyle,
                onDismiss = { showVisualizerPicker = false },
                onSelectStyle = {
                    viewModel.setVisualizerStyle(it)
                    showVisualizerPicker = false
                }
            )
        }
    }
}

@Composable
fun QueueSheetContent(
    queue: List<Song>,
    currentIndex: Int,
    onPlaySongAt: (Int) -> Unit,
    onRemoveSongAt: (Int) -> Unit,
    onClearQueue: () -> Unit,
    onSaveQueue: (String) -> Unit,
    onClose: () -> Unit
) {
    var showSaveDialog by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(520.dp)
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("Play Queue", color = SonoraTextPrimary, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Text("${queue.size} songs queued", color = SonoraTextSecondary, fontSize = 12.sp)
            }

            Row {
                TextButton(onClick = { showSaveDialog = true }) {
                    Text("Save Playlist", color = SonoraGold, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
                TextButton(onClick = onClearQueue) {
                    Text("Clear", color = Color(0xFFFF5252), fontSize = 12.sp)
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        LazyColumn(modifier = Modifier.fillMaxSize()) {
            itemsIndexed(queue) { index, song ->
                val isCur = index == currentIndex
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (isCur) SonoraSurfaceElevated else Color.Transparent)
                        .clickable { onPlaySongAt(index) }
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${index + 1}",
                        color = if (isCur) SonoraGold else SonoraTextMuted,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.width(28.dp)
                    )

                    ArtworkAlbumView(
                        song = song,
                        modifier = Modifier.size(40.dp),
                        cornerRadius = 8.dp,
                        isPlaying = isCur
                    )

                    Spacer(modifier = Modifier.width(12.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = song.title,
                            color = if (isCur) SonoraGold else SonoraTextPrimary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "${song.artist} • ${song.durationFormatted}",
                            color = SonoraTextSecondary,
                            fontSize = 11.sp,
                            maxLines = 1
                        )
                    }

                    IconButton(onClick = { onRemoveSongAt(index) }) {
                        Icon(Icons.Default.Close, contentDescription = "Remove", tint = SonoraTextMuted, modifier = Modifier.size(18.dp))
                    }
                }
            }
        }
    }

    if (showSaveDialog) {
        var playlistName by remember { mutableStateOf("Queue Playlist") }
        AlertDialog(
            onDismissRequest = { showSaveDialog = false },
            containerColor = SonoraSurfaceDark,
            title = { Text("Save Queue as Playlist", color = SonoraTextPrimary) },
            text = {
                OutlinedTextField(
                    value = playlistName,
                    onValueChange = { playlistName = it },
                    label = { Text("Playlist Name") },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = SonoraTextPrimary,
                        unfocusedTextColor = SonoraTextPrimary
                    )
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onSaveQueue(playlistName.trim())
                        showSaveDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = SonoraGold, contentColor = SonoraOnGold)
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { showSaveDialog = false }) { Text("Cancel", color = SonoraTextSecondary) }
            }
        )
    }
}

@Composable
fun LyricsSheetContent(
    song: Song?,
    onClose: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(500.dp)
            .padding(20.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("Lyrics", color = SonoraTextPrimary, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Text("${song?.title} - ${song?.artist}", color = SonoraTextSecondary, fontSize = 12.sp)
            }
            IconButton(onClick = onClose) {
                Icon(Icons.Default.Close, contentDescription = "Close", tint = SonoraTextPrimary)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        val lyricsText = song?.lyrics
        if (lyricsText.isNullOrBlank()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = "No lyrics found for this track.\nYou can add lyrics via 'Edit Metadata' in Library.",
                    color = SonoraTextMuted,
                    textAlign = TextAlign.Center,
                    fontSize = 14.sp
                )
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                val lines = lyricsText.lines()
                itemsIndexed(lines) { _, line ->
                    Text(
                        text = line,
                        color = SonoraTextPrimary,
                        fontSize = 16.sp,
                        lineHeight = 24.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun SleepTimerDialog(
    currentTimerMinutes: Int?,
    onDismiss: () -> Unit,
    onSetTimer: (Int) -> Unit
) {
    val options = listOf(
        "Off" to 0,
        "15 minutes" to 15,
        "30 minutes" to 30,
        "45 minutes" to 45,
        "60 minutes" to 60,
        "90 minutes" to 90
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SonoraSurfaceDark,
        title = {
            Text("Sleep Timer", color = SonoraTextPrimary, fontWeight = FontWeight.Bold)
        },
        text = {
            Column {
                options.forEach { (label, minutes) ->
                    val isSel = (currentTimerMinutes ?: 0) == minutes
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSetTimer(minutes) }
                            .padding(vertical = 12.dp, horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = label,
                            color = if (isSel) SonoraGold else SonoraTextPrimary,
                            fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal,
                            fontSize = 15.sp,
                            modifier = Modifier.weight(1f)
                        )
                        if (isSel) {
                            Icon(Icons.Default.Bedtime, contentDescription = null, tint = SonoraGold, modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = SonoraTextSecondary) }
        }
    )
}

@Composable
fun VisualizerPickerDialog(
    currentStyle: VisualizerStyle,
    onDismiss: () -> Unit,
    onSelectStyle: (VisualizerStyle) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SonoraSurfaceDark,
        title = {
            Text("Choose Visualizer Style", color = SonoraTextPrimary, fontWeight = FontWeight.Bold)
        },
        text = {
            Column {
                VisualizerStyle.values().forEach { style ->
                    val isSel = currentStyle == style
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelectStyle(style) }
                            .padding(vertical = 12.dp, horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = style.name.replace("_", " "),
                            color = if (isSel) SonoraGold else SonoraTextPrimary,
                            fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal,
                            fontSize = 14.sp,
                            modifier = Modifier.weight(1f)
                        )
                        if (isSel) {
                            Icon(Icons.Default.Equalizer, contentDescription = null, tint = SonoraGold, modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = SonoraTextSecondary) }
        }
    )
}

fun formatMs(ms: Long): String {
    val totalSec = (ms / 1000).coerceAtLeast(0)
    val minutes = totalSec / 60
    val seconds = totalSec % 60
    return "%d:%02d".format(minutes, seconds)
}
