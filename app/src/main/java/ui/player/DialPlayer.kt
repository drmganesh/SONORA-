package ui.player

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.PlaybackInfo
import com.example.model.PlayerAppearanceConfig
import com.example.model.PlayerGestureConfig
import com.example.model.Song
import com.example.ui.components.ArtworkAlbumView
import com.example.ui.theme.OutfitFontFamily
import com.example.ui.theme.PlusJakartaFontFamily
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import ui.components.PlaybackControls
import ui.player.seekbar.SeekbarRenderer
import ui.player.seekbar.SeekbarStyle
import ui.theme.LocalEditorialColors

/**
 * Style 8: Dial
 * Circular vinyl artwork hub surrounded by an interactive rotary progress dial.
 */
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
    onArtworkLongPress: () -> Unit,
    seekbarStyle: SeekbarStyle = SeekbarStyle.DEFAULT,
    appearance: PlayerAppearanceConfig = PlayerAppearanceConfig.DEFAULT,
    gestureConfig: PlayerGestureConfig = PlayerGestureConfig.DEFAULT,
    gestureController: PlayerGestureController = rememberPlayerGestureController(),
    liveWaveform: FloatArray? = null,
    onToggleFavorite: (() -> Unit)? = null,
    onCyclePlayerStyle: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val colors = LocalEditorialColors.current
    var isUserDraggingDial by remember { mutableStateOf(false) }
    var dialDragAngle by remember { mutableFloatStateOf(0f) }
    var showLinearSeeker by remember { mutableStateOf(false) }
    val scale = appearance.typographyScale.coerceIn(0.85f, 1.25f)

    val currentPos = if (isUserDraggingDial) {
        val frac = (dialDragAngle / 360f).coerceIn(0f, 1f)
        (frac * playbackInfo.durationMs).toLong()
    } else {
        playbackInfo.currentPositionMs
    }
    val duration = (playbackInfo.durationMs).coerceAtLeast(song?.durationMs ?: 0L)
    val progressSweep = if (duration > 0) (currentPos.toFloat() / duration * 360f).coerceIn(0f, 360f) else 0f

    Box(
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding(),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 600.dp)
                .padding(horizontal = 20.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onClose,
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("collapse_now_playing_button")
                ) {
                    Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Collapse", tint = colors.textPrimary, modifier = Modifier.size(28.dp))
                }
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = colors.accentPill,
                    border = BorderStroke(1.dp, colors.cardBorder)
                ) {
                    Text(
                        text = "ROTARY DIAL",
                        fontFamily = OutfitFontFamily,
                        color = colors.onAccentPill,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.2.sp,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp)
                    )
                }
                Row {
                    IconButton(onClick = { showLinearSeeker = !showLinearSeeker }, modifier = Modifier.size(48.dp)) {
                        Icon(Icons.Default.Tune, contentDescription = "Linear Seeker", tint = if (showLinearSeeker) colors.accentStrong else colors.textSecondary, modifier = Modifier.size(20.dp))
                    }
                    IconButton(
                        onClick = onOpenQueue,
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("open_queue_button")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.QueueMusic, contentDescription = "Queue", tint = colors.textSecondary, modifier = Modifier.size(22.dp))
                    }
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth(0.90f)
                    .aspectRatio(1f),
                contentAlignment = Alignment.Center
            ) {
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
                    val strokeW = (10f * appearance.seekbarThicknessScale).coerceIn(6f, 16f).dp.toPx()
                    val radius = (size.minDimension / 2f) - (strokeW / 2f) - 6.dp.toPx()

                    drawCircle(
                        color = colors.cardBorder,
                        radius = radius,
                        style = Stroke(width = strokeW)
                    )

                    for (i in 0 until 12) {
                        val deg = (i * 30f) - 90f
                        val rad = Math.toRadians(deg.toDouble())
                        val innerR = radius - 14.dp.toPx()
                        val outerR = radius - 6.dp.toPx()
                        val p1 = Offset(center.x + (innerR * cos(rad)).toFloat(), center.y + (innerR * sin(rad)).toFloat())
                        val p2 = Offset(center.x + (outerR * cos(rad)).toFloat(), center.y + (outerR * sin(rad)).toFloat())
                        drawLine(
                            color = colors.textMuted.copy(alpha = 0.45f),
                            start = p1,
                            end = p2,
                            strokeWidth = 2.dp.toPx()
                        )
                    }

                    val currentSweep = if (isUserDraggingDial) dialDragAngle else progressSweep
                    drawArc(
                        brush = Brush.sweepGradient(
                            listOf(colors.accentStrong, colors.onAccentPill, colors.accentStrong)
                        ),
                        startAngle = -90f,
                        sweepAngle = currentSweep,
                        useCenter = false,
                        style = Stroke(width = strokeW, cap = StrokeCap.Round),
                        topLeft = Offset(center.x - radius, center.y - radius),
                        size = Size(radius * 2, radius * 2)
                    )

                    val thumbRad = Math.toRadians((currentSweep - 90f).toDouble())
                    val thumbPos = Offset(
                        center.x + (radius * cos(thumbRad)).toFloat(),
                        center.y + (radius * sin(thumbRad)).toFloat()
                    )
                    drawCircle(
                        color = colors.accentStrong,
                        radius = 9.dp.toPx(),
                        center = thumbPos
                    )
                    drawCircle(
                        color = colors.surface,
                        radius = 4.5.dp.toPx(),
                        center = thumbPos
                    )
                }

                Box(
                    modifier = Modifier
                        .fillMaxSize(0.68f)
                        .graphicsLayer {
                            scaleX = gestureController.artworkZoomScale
                            scaleY = gestureController.artworkZoomScale
                        }
                        .clip(CircleShape)
                        .border(3.dp, colors.accentStrong.copy(alpha = 0.55f), CircleShape)
                        .playerArtworkGestures(
                            config = gestureConfig,
                            controller = gestureController,
                            currentPositionMs = playbackInfo.currentPositionMs,
                            durationMs = duration,
                            isPlaying = playbackInfo.isPlaying,
                            onTogglePlayPause = onTogglePlayPause,
                            onPlayNext = onPlayNext,
                            onPlayPrevious = onPlayPrevious,
                            onSeekTo = onSeekTo,
                            onOpenQueue = onOpenQueue,
                            onClose = onClose,
                            onArtworkLongPress = onArtworkLongPress,
                            onToggleFavorite = onToggleFavorite,
                            onCyclePlayerStyle = onCyclePlayerStyle
                        )
                        .testTag("player_artwork_box"),
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

            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = colors.surface,
                    border = BorderStroke(1.dp, colors.cardBorder)
                ) {
                    Text(
                        text = "${formatDurationMs(currentPos)}  /  ${formatDurationMs(duration)}",
                        fontFamily = PlusJakartaFontFamily,
                        color = colors.accentStrong,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                    )
                }
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = song?.title ?: "No Song Selected",
                    fontFamily = OutfitFontFamily,
                    color = colors.textPrimary,
                    fontSize = (20f * scale).sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = song?.artist ?: "Unknown Artist",
                    fontFamily = PlusJakartaFontFamily,
                    color = colors.textSecondary,
                    fontSize = (14f * scale).sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            if (showLinearSeeker) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp)
                ) {
                    SeekbarRenderer(
                        currentPositionMs = playbackInfo.currentPositionMs,
                        durationMs = duration,
                        onSeekTo = onSeekTo,
                        isPlaying = playbackInfo.isPlaying,
                        style = seekbarStyle,
                        showTimestamps = false,
                        liveWaveform = liveWaveform,
                        thicknessScale = appearance.seekbarThicknessScale,
                        hapticFeedbackEnabled = gestureConfig.hapticFeedbackEnabled,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            PlaybackControls(
                playbackInfo = playbackInfo,
                onTogglePlayPause = onTogglePlayPause,
                onPlayNext = onPlayNext,
                onPlayPrevious = onPlayPrevious,
                onToggleShuffle = onToggleShuffle,
                onCycleRepeat = onCycleRepeat,
                appearance = appearance,
                accentColor = colors.accentStrong,
                onAccentColor = colors.onAccentStrong,
                primaryButtonSize = 66.dp
            )
        }
    }
}
