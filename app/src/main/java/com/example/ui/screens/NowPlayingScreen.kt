package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import ui.player.PlayerScreen
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.BluetoothConnected
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.filled.Image
import androidx.compose.runtime.LaunchedEffect
import com.example.model.RepeatMode
import com.example.model.ShuffleMode
import com.example.model.Song
import com.example.ui.components.ArtworkAlbumView
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
import com.example.util.LyricLine
import com.example.util.LyricsExtractor
import com.example.viewmodel.SonoraViewModel
import kotlinx.coroutines.launch

@Composable
fun NowPlayingScreen(
    viewModel: SonoraViewModel,
    onClose: () -> Unit,
    onNavigateToEqualizer: () -> Unit,
    modifier: Modifier = Modifier
) {
    PlayerScreen(
        viewModel = viewModel,
        onClose = onClose,
        onNavigateToEqualizer = onNavigateToEqualizer,
        modifier = modifier.testTag("now_playing_screen")
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Suppress("UNUSED_PARAMETER")
@Composable
private fun LegacyNowPlayingScreen(
    viewModel: SonoraViewModel,
    onClose: () -> Unit,
    onNavigateToEqualizer: () -> Unit,
    modifier: Modifier = Modifier
) {
    val currentSong by viewModel.currentSong.collectAsState()
    val playbackInfo by viewModel.playbackInfo.collectAsState()
    val queue by viewModel.queue.collectAsState()
    val queueIndex by viewModel.queueIndex.collectAsState()
    val spatialConfig by viewModel.spatialConfig.collectAsState()
    val isTwsConnected by viewModel.isTwsConnected.collectAsState()
    val currentTrackLyrics by viewModel.currentTrackLyrics.collectAsState()

    val gesturesEnabled by viewModel.gesturesEnabled.collectAsState()
    val swipeArtworkToSkip by viewModel.swipeArtworkToSkip.collectAsState()
    val doubleTapToSeek by viewModel.doubleTapToSeek.collectAsState()
    val swipeDownToCollapse by viewModel.swipeDownToCollapse.collectAsState()

    var gestureFeedbackText by remember { mutableStateOf<String?>(null) }

    var showQueueSheet by remember { mutableStateOf(false) }
    var showLyricsSheet by remember { mutableStateOf(false) }
    var showSleepTimerDialog by remember { mutableStateOf(false) }
    var isUserSeeking by remember { mutableStateOf(false) }
    var userSeekPos by remember { mutableFloatStateOf(0f) }
    var isLyricsMode by remember { mutableStateOf(false) }

    val currentPosition = if (isUserSeeking) userSeekPos.toLong() else playbackInfo.currentPositionMs
    val duration = (playbackInfo.durationMs).coerceAtLeast(currentSong?.durationMs ?: 0L)

    val effectiveLyrics = currentTrackLyrics ?: currentSong?.lyrics
    val parsedLrcLines = remember(effectiveLyrics) { LyricsExtractor.parseLrcLines(effectiveLyrics) }
    val activeLineIdx = remember(parsedLrcLines, currentPosition) {
        LyricsExtractor.findActiveLineIndex(parsedLrcLines, currentPosition)
    }

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

            Spacer(modifier = Modifier.height(8.dp))

            // Switcher: Cover Art / Embedded Lyrics
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(SonoraSurfaceElevated.copy(alpha = 0.7f))
                    .border(1.dp, SonoraCardBorder, RoundedCornerShape(20.dp))
                    .padding(3.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .background(if (!isLyricsMode) SonoraGold else Color.Transparent)
                        .clickable { isLyricsMode = false }
                        .padding(horizontal = 14.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Image,
                        contentDescription = null,
                        tint = if (!isLyricsMode) SonoraOnGold else SonoraTextMuted,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "ALBUM ART",
                        color = if (!isLyricsMode) SonoraOnGold else SonoraTextSecondary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .background(if (isLyricsMode) SonoraGold else Color.Transparent)
                        .clickable { isLyricsMode = true }
                        .padding(horizontal = 14.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Lyrics,
                        contentDescription = null,
                        tint = if (isLyricsMode) SonoraOnGold else SonoraTextMuted,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "EMBEDDED LYRICS",
                        color = if (isLyricsMode) SonoraOnGold else SonoraTextSecondary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Hero Main Presentation: Album Artwork or Synced Embedded Lyrics
            if (!isLyricsMode) {
                var totalDragX by remember { mutableFloatStateOf(0f) }
                var totalDragY by remember { mutableFloatStateOf(0f) }

                Box(
                    modifier = Modifier
                        .size(290.dp)
                        .clip(RoundedCornerShape(28.dp))
                        .pointerInput(gesturesEnabled, swipeArtworkToSkip, doubleTapToSeek, swipeDownToCollapse) {
                            if (!gesturesEnabled) return@pointerInput

                            detectTapGestures(
                                onDoubleTap = { offset ->
                                    if (doubleTapToSeek) {
                                        val width = size.width
                                        if (offset.x < width * 0.45f) {
                                            // Double tap left -> -10 seconds
                                            val newPos = (currentPosition - 10000L).coerceAtLeast(0L)
                                            viewModel.seekTo(newPos)
                                            gestureFeedbackText = "« -10s"
                                        } else if (offset.x > width * 0.55f) {
                                            // Double tap right -> +10 seconds
                                            val newPos = (currentPosition + 10000L).coerceAtMost(duration)
                                            viewModel.seekTo(newPos)
                                            gestureFeedbackText = "+10s »"
                                        } else {
                                            // Center double tap -> toggle play/pause
                                            viewModel.togglePlayPause()
                                            gestureFeedbackText = if (playbackInfo.isPlaying) "PAUSED" else "PLAYING"
                                        }
                                    }
                                }
                            )
                        }
                        .pointerInput(gesturesEnabled, swipeArtworkToSkip, swipeDownToCollapse) {
                            if (!gesturesEnabled) return@pointerInput

                            detectHorizontalDragGestures(
                                onDragStart = { totalDragX = 0f },
                                onDragEnd = {
                                    if (swipeArtworkToSkip) {
                                        if (totalDragX < -70f) {
                                            // Swiped left -> Next track
                                            viewModel.playNext()
                                            gestureFeedbackText = "NEXT TRACK »"
                                        } else if (totalDragX > 70f) {
                                            // Swiped right -> Previous track
                                            viewModel.playPrevious()
                                            gestureFeedbackText = "« PREVIOUS TRACK"
                                        }
                                    }
                                    totalDragX = 0f
                                },
                                onDragCancel = { totalDragX = 0f },
                                onHorizontalDrag = { change, dragAmount ->
                                    change.consume()
                                    totalDragX += dragAmount
                                }
                            )
                        }
                        .pointerInput(gesturesEnabled, swipeDownToCollapse) {
                            if (!gesturesEnabled || !swipeDownToCollapse) return@pointerInput

                            detectVerticalDragGestures(
                                onDragStart = { totalDragY = 0f },
                                onDragEnd = {
                                    if (totalDragY > 100f) {
                                        // Swiped down -> Collapse Now Playing
                                        onClose()
                                    }
                                    totalDragY = 0f
                                },
                                onDragCancel = { totalDragY = 0f },
                                onVerticalDrag = { change, dragAmount ->
                                    change.consume()
                                    totalDragY += dragAmount
                                }
                            )
                        },
                    contentAlignment = Alignment.Center
                ) {
                    ArtworkAlbumView(
                        song = currentSong,
                        modifier = Modifier.fillMaxSize(),
                        cornerRadius = 28.dp,
                        isPlaying = playbackInfo.isPlaying,
                        isVinylRotating = false
                    )

                    // Overlay feedback pill when a gesture is triggered
                    LaunchedEffect(gestureFeedbackText) {
                        if (gestureFeedbackText != null) {
                            kotlinx.coroutines.delay(1200)
                            gestureFeedbackText = null
                        }
                    }

                    if (gestureFeedbackText != null) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(16.dp))
                                .background(SonoraObsidian.copy(alpha = 0.85f))
                                .border(1.5.dp, SonoraGold, RoundedCornerShape(16.dp))
                                .padding(horizontal = 18.dp, vertical = 10.dp)
                        ) {
                            Text(
                                text = gestureFeedbackText ?: "",
                                color = SonoraGold,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                // Sleek active lyric preview bar if lyrics are available
                if (parsedLrcLines.isNotEmpty() && activeLineIdx in parsedLrcLines.indices) {
                    Spacer(modifier = Modifier.height(14.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(SonoraSurfaceElevated.copy(alpha = 0.7f))
                            .border(1.dp, SonoraGold.copy(alpha = 0.3f), RoundedCornerShape(14.dp))
                            .clickable { isLyricsMode = true }
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Lyrics,
                            contentDescription = null,
                            tint = SonoraGold,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = parsedLrcLines[activeLineIdx].text,
                            color = SonoraGold,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                } else {
                    Spacer(modifier = Modifier.height(14.dp))
                }
            } else {
                // Interactive Embedded Lyrics View in Hero Area
                HeroEmbeddedLyricsCard(
                    song = currentSong,
                    parsedLines = parsedLrcLines,
                    activeLineIndex = activeLineIdx,
                    rawLyrics = effectiveLyrics,
                    onSeekTo = { viewModel.seekTo(it) },
                    onExpandSheet = { showLyricsSheet = true },
                    onCloseLyrics = { isLyricsMode = false },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(310.dp)
                )
                Spacer(modifier = Modifier.height(14.dp))
            }

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

                val automixOn by viewModel.automixEnabled.collectAsState()
                val nextMatched by viewModel.nextMatchedSong.collectAsState()
                if (automixOn) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Box(
                        modifier = Modifier
                            .background(SonoraCyan.copy(alpha = 0.15f), RoundedCornerShape(8.dp))
                            .border(1.dp, SonoraCyan.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Text(
                            text = if (nextMatched != null) "AUTOMIX: ${nextMatched?.title}" else "AUTOMIX ON",
                            color = SonoraCyan,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
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

                // TWS Wide Stereo Toggle (Shows when TWS earbuds are connected)
                if (isTwsConnected) {
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (spatialConfig.twsWideStereoEnabled) SonoraCyan.copy(alpha = 0.18f) else Color.Transparent)
                            .clickable {
                                viewModel.toggleTwsWideStereo(!spatialConfig.twsWideStereoEnabled)
                            }
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.BluetoothConnected,
                            contentDescription = null,
                            tint = if (spatialConfig.twsWideStereoEnabled) SonoraCyan else SonoraTextMuted,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (spatialConfig.twsWideStereoEnabled) "TWS WIDE" else "TWS",
                            color = if (spatialConfig.twsWideStereoEnabled) SonoraCyan else SonoraTextMuted,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
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
                    lyrics = effectiveLyrics,
                    currentPositionMs = currentPosition,
                    onSeekTo = { viewModel.seekTo(it) },
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
fun HeroEmbeddedLyricsCard(
    song: Song?,
    parsedLines: List<LyricLine>,
    activeLineIndex: Int,
    rawLyrics: String?,
    onSeekTo: (Long) -> Unit,
    onExpandSheet: () -> Unit,
    onCloseLyrics: () -> Unit,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()

    LaunchedEffect(activeLineIndex) {
        if (activeLineIndex >= 0 && parsedLines.isNotEmpty()) {
            listState.animateScrollToItem((activeLineIndex - 1).coerceAtLeast(0))
        }
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(28.dp))
            .background(SonoraSurfaceElevated.copy(alpha = 0.85f))
            .border(1.dp, SonoraGold.copy(alpha = 0.4f), RoundedCornerShape(28.dp))
            .padding(16.dp)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Lyrics,
                        contentDescription = null,
                        tint = SonoraGold,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (parsedLines.isNotEmpty()) "SYNCED LYRICS" else "EMBEDDED LYRICS",
                        color = SonoraGold,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = onExpandSheet) {
                        Text("Expand", color = SonoraCyan, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                    IconButton(onClick = onCloseLyrics, modifier = Modifier.size(24.dp)) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close lyrics view",
                            tint = SonoraTextMuted,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            if (parsedLines.isNotEmpty()) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(vertical = 16.dp)
                ) {
                    itemsIndexed(parsedLines) { index, item ->
                        val isActive = index == activeLineIndex
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isActive) SonoraGold.copy(alpha = 0.15f) else Color.Transparent)
                                .clickable { onSeekTo(item.timestampMs) }
                                .padding(horizontal = 8.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = item.text,
                                color = if (isActive) SonoraGold else SonoraTextSecondary.copy(alpha = 0.7f),
                                fontSize = if (isActive) 17.sp else 14.sp,
                                fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
                                lineHeight = if (isActive) 22.sp else 18.sp
                            )
                        }
                    }
                }
            } else if (!rawLyrics.isNullOrBlank()) {
                val plainLines = rawLyrics.lines()
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(vertical = 12.dp)
                ) {
                    itemsIndexed(plainLines) { _, line ->
                        Text(
                            text = line,
                            color = SonoraTextPrimary,
                            fontSize = 14.sp,
                            lineHeight = 22.sp,
                            modifier = Modifier.padding(vertical = 3.dp)
                        )
                    }
                }
            } else {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Lyrics,
                            contentDescription = null,
                            tint = SonoraTextMuted,
                            modifier = Modifier.size(36.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "No Embedded Lyrics Detected",
                            color = SonoraTextPrimary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "SONORA automatically reads embedded ID3v2 USLT frames, FLAC Vorbis comments, or .lrc sidecars.",
                            color = SonoraTextMuted,
                            fontSize = 12.sp,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun LyricsSheetContent(
    song: Song?,
    lyrics: String?,
    currentPositionMs: Long,
    onSeekTo: (Long) -> Unit,
    onClose: () -> Unit
) {
    val parsedLines = remember(lyrics) { LyricsExtractor.parseLrcLines(lyrics) }
    val activeLineIdx = remember(parsedLines, currentPositionMs) {
        LyricsExtractor.findActiveLineIndex(parsedLines, currentPositionMs)
    }
    val listState = rememberLazyListState()

    LaunchedEffect(activeLineIdx) {
        if (activeLineIdx >= 0 && parsedLines.isNotEmpty()) {
            listState.animateScrollToItem((activeLineIdx - 2).coerceAtLeast(0))
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(550.dp)
            .padding(horizontal = 20.dp, vertical = 16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (parsedLines.isNotEmpty()) "Synchronized Lyrics" else "Embedded Lyrics",
                    color = SonoraTextPrimary,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "${song?.title} • ${song?.artist}",
                    color = SonoraTextSecondary,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            IconButton(onClick = onClose) {
                Icon(Icons.Default.Close, contentDescription = "Close", tint = SonoraTextPrimary)
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        if (parsedLines.isNotEmpty()) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(vertical = 16.dp)
            ) {
                itemsIndexed(parsedLines) { index, item ->
                    val isActive = index == activeLineIdx
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (isActive) SonoraGold.copy(alpha = 0.18f) else Color.Transparent)
                            .clickable { onSeekTo(item.timestampMs) }
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = item.text,
                            color = if (isActive) SonoraGold else SonoraTextPrimary.copy(alpha = 0.65f),
                            fontSize = if (isActive) 18.sp else 15.sp,
                            fontWeight = if (isActive) FontWeight.Bold else FontWeight.Medium,
                            lineHeight = if (isActive) 24.sp else 20.sp
                        )
                    }
                }
            }
        } else if (!lyrics.isNullOrBlank()) {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                val lines = lyrics.lines()
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
        } else {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.Lyrics,
                        contentDescription = null,
                        tint = SonoraTextMuted,
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "No Embedded Lyrics Found",
                        color = SonoraTextPrimary,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "SONORA extracts ID3v2 USLT frames, Vorbis tags, and sidecar .lrc files automatically.",
                        color = SonoraTextMuted,
                        textAlign = TextAlign.Center,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(horizontal = 24.dp)
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

fun formatMs(ms: Long): String {
    val totalSec = (ms / 1000).coerceAtLeast(0)
    val minutes = totalSec / 60
    val seconds = totalSec % 60
    return "%d:%02d".format(minutes, seconds)
}
