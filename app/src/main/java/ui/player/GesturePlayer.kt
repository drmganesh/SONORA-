package ui.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Lyrics
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.PlaybackInfo
import com.example.model.Song
import com.example.ui.components.ArtworkAlbumView
import com.example.ui.theme.SonoraCharcoal
import com.example.ui.theme.SonoraCharcoalBorder
import com.example.ui.theme.SonoraDustyRose
import com.example.ui.theme.SonoraTextMuted
import com.example.ui.theme.SonoraTextPrimary
import com.example.ui.theme.SonoraTextSecondary
import kotlinx.coroutines.delay
import ui.player.seekbar.SeekbarRenderer
import ui.player.seekbar.SeekbarStyle

/**
 * Style 2: Gesture
 * Large album artwork occupying most of the screen.
 * Swipe left/right to change tracks, double-tap to seek, with minimal buttons and touch-friendly interface.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun GesturePlayer(
    song: Song?,
    playbackInfo: PlaybackInfo,
    onTogglePlayPause: () -> Unit,
    onPlayNext: () -> Unit,
    onPlayPrevious: () -> Unit,
    onSeekTo: (Long) -> Unit,
    onToggleShuffle: () -> Unit,
    onCycleRepeat: () -> Unit,
    onOpenQueue: () -> Unit,
    onOpenLyrics: () -> Unit,
    onOpenSleepTimer: () -> Unit,
    onClose: () -> Unit,
    onNavigateToEqualizer: () -> Unit,
    onArtworkLongPress: () -> Unit,
    seekbarStyle: SeekbarStyle = SeekbarStyle.DEFAULT,
    modifier: Modifier = Modifier
) {
    var gestureCue by remember { mutableStateOf<String?>(null) }
    var totalDragX by remember { mutableFloatStateOf(0f) }
    var totalDragY by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(gestureCue) {
        if (gestureCue != null) {
            delay(1200)
            gestureCue = null
        }
    }

    val currentPos = playbackInfo.currentPositionMs
    val duration = (playbackInfo.durationMs).coerceAtLeast(song?.durationMs ?: 0L)
    val progressFraction = if (duration > 0) (currentPos.toFloat() / duration).coerceIn(0f, 1f) else 0f

    Box(
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = 16.dp)
        ) {
            // Top minimal header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onClose, modifier = Modifier.size(44.dp)) {
                    Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Collapse", tint = SonoraTextPrimary, modifier = Modifier.size(28.dp))
                }
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = SonoraCharcoal.copy(alpha = 0.8f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, SonoraCharcoalBorder)
                ) {
                    Text(
                        text = "GESTURE MODE",
                        color = SonoraDustyRose,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.2.sp,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onNavigateToEqualizer, modifier = Modifier.size(40.dp)) {
                        Icon(Icons.Default.GraphicEq, contentDescription = "Equalizer", tint = SonoraTextSecondary, modifier = Modifier.size(20.dp))
                    }
                    IconButton(onClick = onOpenQueue, modifier = Modifier.size(40.dp)) {
                        Icon(Icons.AutoMirrored.Filled.QueueMusic, contentDescription = "Queue", tint = SonoraTextSecondary, modifier = Modifier.size(20.dp))
                    }
                }
            }

            // Massive Artwork Container with Drag and Tap Gestures
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(horizontal = 20.dp, vertical = 6.dp)
                    .clip(RoundedCornerShape(32.dp))
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onTap = { onTogglePlayPause() },
                            onLongPress = { onArtworkLongPress() },
                            onDoubleTap = { offset ->
                                val width = size.width
                                if (offset.x < width / 2) {
                                    val newPos = (currentPos - 10000L).coerceAtLeast(0L)
                                    onSeekTo(newPos)
                                    gestureCue = "⏪ Rewind 10s"
                                } else {
                                    val newPos = (currentPos + 10000L).coerceAtMost(duration)
                                    onSeekTo(newPos)
                                    gestureCue = "⏩ Fast Forward 10s"
                                }
                            }
                        )
                    }
                    .pointerInput(Unit) {
                        detectHorizontalDragGestures(
                            onDragStart = { totalDragX = 0f },
                            onDragEnd = {
                                if (totalDragX > 80f) {
                                    onPlayPrevious()
                                    gestureCue = "⏮ Previous Track"
                                } else if (totalDragX < -80f) {
                                    onPlayNext()
                                    gestureCue = "⏭ Next Track"
                                }
                            },
                            onHorizontalDrag = { change, dragAmount ->
                                change.consume()
                                totalDragX += dragAmount
                            }
                        )
                    }
                    .pointerInput(Unit) {
                        detectVerticalDragGestures(
                            onDragStart = { totalDragY = 0f },
                            onDragEnd = {
                                if (totalDragY > 120f) {
                                    onClose()
                                } else if (totalDragY < -100f) {
                                    onOpenQueue()
                                    gestureCue = "📑 Queue Opened"
                                }
                            },
                            onVerticalDrag = { change, dragAmount ->
                                change.consume()
                                totalDragY += dragAmount
                            }
                        )
                    },
                contentAlignment = Alignment.Center
            ) {
                ArtworkAlbumView(
                    song = song,
                    modifier = Modifier.fillMaxSize(),
                    cornerRadius = 32.dp,
                    isPlaying = playbackInfo.isPlaying
                )

                // Subtle gesture hint indicators on sides
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.35f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.ChevronLeft, contentDescription = "Prev", tint = Color.White.copy(alpha = 0.7f), modifier = Modifier.size(24.dp))
                    }
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.35f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.ChevronRight, contentDescription = "Next", tint = Color.White.copy(alpha = 0.7f), modifier = Modifier.size(24.dp))
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Floating Minimal Glass Pill at Bottom
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .clip(RoundedCornerShape(24.dp)),
                color = SonoraCharcoal,
                tonalElevation = 6.dp,
                border = androidx.compose.foundation.BorderStroke(1.dp, SonoraCharcoalBorder)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp)
                ) {
                    // Universal Seekbar in Gesture Player
                    SeekbarRenderer(
                        currentPositionMs = currentPos,
                        durationMs = duration,
                        onSeekTo = onSeekTo,
                        isPlaying = playbackInfo.isPlaying,
                        style = seekbarStyle,
                        showTimestamps = false
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = song?.title ?: "No Song Selected",
                                color = SonoraTextPrimary,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "${song?.artist ?: "Unknown"} • ${formatDurationMs(currentPos)} / ${formatDurationMs(duration)}",
                                color = SonoraTextSecondary,
                                fontSize = 12.sp,
                                maxLines = 1
                            )
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = onOpenLyrics, modifier = Modifier.size(36.dp)) {
                                Icon(Icons.Default.Lyrics, contentDescription = "Lyrics", tint = SonoraDustyRose, modifier = Modifier.size(18.dp))
                            }
                            Spacer(modifier = Modifier.width(6.dp))
                            Surface(
                                onClick = onTogglePlayPause,
                                modifier = Modifier.size(46.dp),
                                shape = CircleShape,
                                color = SonoraDustyRose
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = if (playbackInfo.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                        contentDescription = "Play/Pause",
                                        tint = Color.Black,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // On-screen Gesture Feedback HUD
        AnimatedVisibility(
            visible = gestureCue != null,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.Center)
        ) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = Color.Black.copy(alpha = 0.85f),
                border = androidx.compose.foundation.BorderStroke(1.dp, SonoraDustyRose.copy(alpha = 0.6f)),
                shadowElevation = 12.dp
            ) {
                Text(
                    text = gestureCue ?: "",
                    color = SonoraDustyRose,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)
                )
            }
        }
    }
}
