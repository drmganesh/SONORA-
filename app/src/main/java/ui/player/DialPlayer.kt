package ui.player

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Lyrics
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.PlaybackInfo
import com.example.model.RepeatMode
import com.example.model.ShuffleMode
import com.example.model.Song
import com.example.ui.components.ArtworkAlbumView
import com.example.ui.theme.SonoraCharcoal
import com.example.ui.theme.SonoraCharcoalBorder
import com.example.ui.theme.SonoraDustyRose
import com.example.ui.theme.SonoraDustyRoseLight
import com.example.ui.theme.SonoraTextMuted
import com.example.ui.theme.SonoraTextPrimary
import com.example.ui.theme.SonoraTextSecondary
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/**
 * Style 8: Dial
 * Unique circular rotary interface.
 * Center circular artwork surrounded by an interactive rotary ring.
 * Rotate or drag along the ring to seek through the track with continuous progress arc feedback.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DialPlayer(
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
    seekbarStyle: ui.player.seekbar.SeekbarStyle = ui.player.seekbar.SeekbarStyle.DEFAULT,
    modifier: Modifier = Modifier
) {
    var isUserDraggingDial by remember { mutableStateOf(false) }
    var dialDragAngle by remember { mutableFloatStateOf(0f) }
    var showLinearSeeker by remember { mutableStateOf(false) }

    val currentPos = if (isUserDraggingDial) {
        val frac = (dialDragAngle / 360f).coerceIn(0f, 1f)
        (frac * playbackInfo.durationMs).toLong()
    } else {
        playbackInfo.currentPositionMs
    }
    val duration = (playbackInfo.durationMs).coerceAtLeast(song?.durationMs ?: 0L)
    val progressSweep = if (duration > 0) (currentPos.toFloat() / duration * 360f).coerceIn(0f, 360f) else 0f

    Column(
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(horizontal = 20.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // Top Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onClose, modifier = Modifier.size(44.dp)) {
                Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Collapse", tint = SonoraTextPrimary, modifier = Modifier.size(28.dp))
            }
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = SonoraCharcoal,
                border = androidx.compose.foundation.BorderStroke(1.dp, SonoraCharcoalBorder)
            ) {
                Text(
                    text = "ROTARY DIAL",
                    color = SonoraDustyRose,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.2.sp,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp)
                )
            }
            Row {
                IconButton(onClick = { showLinearSeeker = !showLinearSeeker }, modifier = Modifier.size(40.dp)) {
                    Icon(Icons.Default.Tune, contentDescription = "Linear Seeker", tint = if (showLinearSeeker) SonoraDustyRose else SonoraTextSecondary, modifier = Modifier.size(20.dp))
                }
                IconButton(onClick = onOpenQueue, modifier = Modifier.size(40.dp)) {
                    Icon(Icons.AutoMirrored.Filled.QueueMusic, contentDescription = "Queue", tint = SonoraTextSecondary, modifier = Modifier.size(20.dp))
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Center Rotary Ring Container
        Box(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .aspectRatio(1f),
            contentAlignment = Alignment.Center
        ) {
            // Interactive Touch-Seeking Canvas Ring
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(duration) {
                        detectDragGestures(
                            onDragStart = { offset ->
                                isUserDraggingDial = true
                                val center = Offset(size.width / 2f, size.height / 2f)
                                val dx = offset.x - center.x
                                val dy = offset.y - center.y
                                var angle = Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())).toFloat() + 90f
                                if (angle < 0f) angle += 360f
                                dialDragAngle = angle
                            },
                            onDragEnd = {
                                isUserDraggingDial = false
                                val frac = (dialDragAngle / 360f).coerceIn(0f, 1f)
                                onSeekTo((frac * duration).toLong())
                            },
                            onDrag = { change, _ ->
                                change.consume()
                                val center = Offset(size.width / 2f, size.height / 2f)
                                val dx = change.position.x - center.x
                                val dy = change.position.y - center.y
                                var angle = Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())).toFloat() + 90f
                                if (angle < 0f) angle += 360f
                                dialDragAngle = angle
                            }
                        )
                    }
            ) {
                val center = Offset(size.width / 2f, size.height / 2f)
                val strokeW = 10.dp.toPx()
                val radius = (size.minDimension / 2f) - (strokeW / 2f) - 6.dp.toPx()

                // Background Ring Track
                drawCircle(
                    color = Color.White.copy(alpha = 0.08f),
                    radius = radius,
                    style = Stroke(width = strokeW)
                )

                // Rotary Tick Marks (Every 30 degrees)
                for (i in 0 until 12) {
                    val deg = (i * 30f) - 90f
                    val rad = Math.toRadians(deg.toDouble())
                    val innerR = radius - 14.dp.toPx()
                    val outerR = radius - 6.dp.toPx()
                    val p1 = Offset(center.x + (innerR * cos(rad)).toFloat(), center.y + (innerR * sin(rad)).toFloat())
                    val p2 = Offset(center.x + (outerR * cos(rad)).toFloat(), center.y + (outerR * sin(rad)).toFloat())
                    drawLine(
                        color = Color.White.copy(alpha = 0.25f),
                        start = p1,
                        end = p2,
                        strokeWidth = 2.dp.toPx()
                    )
                }

                // Glowing Active Rotary Progress Arc
                val currentSweep = if (isUserDraggingDial) dialDragAngle else progressSweep
                drawArc(
                    brush = Brush.sweepGradient(
                        listOf(SonoraDustyRoseLight, SonoraDustyRose, SonoraDustyRoseLight)
                    ),
                    startAngle = -90f,
                    sweepAngle = currentSweep,
                    useCenter = false,
                    style = Stroke(width = strokeW, cap = StrokeCap.Round),
                    topLeft = Offset(center.x - radius, center.y - radius),
                    size = Size(radius * 2, radius * 2)
                )

                // Dial Thumb Knob Handle
                val thumbRad = Math.toRadians((currentSweep - 90f).toDouble())
                val thumbPos = Offset(
                    center.x + (radius * cos(thumbRad)).toFloat(),
                    center.y + (radius * sin(thumbRad)).toFloat()
                )
                drawCircle(
                    color = Color.White,
                    radius = 9.dp.toPx(),
                    center = thumbPos
                )
                drawCircle(
                    color = SonoraDustyRose,
                    radius = 5.dp.toPx(),
                    center = thumbPos
                )
            }

            // Circular Center Artwork Hub
            Box(
                modifier = Modifier
                    .fillMaxSize(0.68f)
                    .clip(CircleShape)
                    .border(3.dp, SonoraDustyRose.copy(alpha = 0.4f), CircleShape)
                    .combinedClickable(
                        onClick = onTogglePlayPause,
                        onLongClick = onArtworkLongPress
                    ),
                contentAlignment = Alignment.Center
            ) {
                ArtworkAlbumView(
                    song = song,
                    modifier = Modifier.fillMaxSize(),
                    isVinylRotating = true,
                    isPlaying = playbackInfo.isPlaying
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Digital Time Display & Song Info
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = SonoraCharcoal,
                border = androidx.compose.foundation.BorderStroke(1.dp, SonoraCharcoalBorder)
            ) {
                Text(
                    text = "${formatDurationMs(currentPos)}  /  ${formatDurationMs(duration)}",
                    color = SonoraDustyRose,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = song?.title ?: "No Song Selected",
                color = SonoraTextPrimary,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = song?.artist ?: "Unknown Artist",
                color = SonoraTextSecondary,
                fontSize = 14.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        // Optional Linear Seeker Drawer (For accessibility)
        if (showLinearSeeker) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp)
            ) {
                ui.player.seekbar.SeekbarRenderer(
                    currentPositionMs = playbackInfo.currentPositionMs,
                    durationMs = duration,
                    onSeekTo = onSeekTo,
                    isPlaying = playbackInfo.isPlaying,
                    style = seekbarStyle,
                    showTimestamps = false,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        // Rotary Control Bar
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onToggleShuffle, modifier = Modifier.size(46.dp)) {
                Icon(
                    Icons.Default.Shuffle,
                    contentDescription = "Shuffle",
                    tint = if (playbackInfo.shuffleMode == ShuffleMode.ON) SonoraDustyRose else SonoraTextMuted,
                    modifier = Modifier.size(22.dp)
                )
            }

            IconButton(onClick = onPlayPrevious, modifier = Modifier.size(50.dp)) {
                Icon(Icons.Default.SkipPrevious, contentDescription = "Previous", tint = SonoraTextPrimary, modifier = Modifier.size(32.dp))
            }

            Surface(
                onClick = onTogglePlayPause,
                modifier = Modifier.size(66.dp),
                shape = CircleShape,
                color = SonoraDustyRose,
                shadowElevation = 8.dp
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = if (playbackInfo.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = "Play/Pause",
                        tint = Color.Black,
                        modifier = Modifier.size(34.dp)
                    )
                }
            }

            IconButton(onClick = onPlayNext, modifier = Modifier.size(50.dp)) {
                Icon(Icons.Default.SkipNext, contentDescription = "Next", tint = SonoraTextPrimary, modifier = Modifier.size(32.dp))
            }

            IconButton(onClick = onCycleRepeat, modifier = Modifier.size(46.dp)) {
                val icon = if (playbackInfo.repeatMode == RepeatMode.ONE) Icons.Default.RepeatOne else Icons.Default.Repeat
                val tint = if (playbackInfo.repeatMode != RepeatMode.OFF) SonoraDustyRose else SonoraTextMuted
                Icon(icon, contentDescription = "Repeat", tint = tint, modifier = Modifier.size(22.dp))
            }
        }
    }
}
