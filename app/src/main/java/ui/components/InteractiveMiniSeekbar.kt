package ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
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
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.PlaybackInfo
import com.example.ui.theme.PlusJakartaFontFamily
import ui.motion.LocalEffectiveMotionState
import ui.motion.rememberMotionAwareHaptic
import ui.player.formatDurationMs
import ui.player.seekbar.scrubSeekGestures

/**
 * Shared seek state & callbacks passed down from [FloatingMiniPlayer] so every one of the
 * 20 mini-player styles (and the expanded mini-player card) supports conflict-free drag-to-seek
 * backed by the single live [PlaybackInfo] source of truth.
 */
@Immutable
data class MiniSeekController(
    val playbackInfo: PlaybackInfo = PlaybackInfo(),
    val onSeekTo: (Long) -> Unit = {},
    val onScrubbingChanged: (Boolean) -> Unit = {}
)

val LocalMiniSeekController = compositionLocalOf { MiniSeekController() }

/**
 * Compact interactive drag-to-seek progress bar for all SONORA Mini Player styles.
 *
 * - Smoothly tracks playback progress with lightweight animation.
 * - Dragging forward/backward previews the target position in real time with a crisp floating
 *   timestamp pill (`MM:SS / MM:SS`) and expanded thumb indicator.
 * - Commits [onSeekTo] exactly once on drag release (no ExoPlayer seek spam).
 * - Automatically disables drag and shows a `LIVE` badge for live / non-seekable streams.
 * - Consumes horizontal gestures and notifies [onScrubbingChanged] so parent mini-player
 *   swipe-to-skip / swipe-to-dismiss gestures never conflict.
 */
@Composable
fun InteractiveMiniSeekbar(
    activeColor: Color,
    trackColor: Color,
    modifier: Modifier = Modifier,
    activeBrush: Brush? = null,
    trackHeight: Dp = 3.dp,
    touchHeight: Dp = 18.dp,
    rounded: Boolean = false,
    showTimestamps: Boolean = false,
    timestampColor: Color = activeColor,
    playbackInfo: PlaybackInfo = LocalMiniSeekController.current.playbackInfo,
    onSeekTo: (Long) -> Unit = LocalMiniSeekController.current.onSeekTo,
    onScrubbingChanged: (Boolean) -> Unit = LocalMiniSeekController.current.onScrubbingChanged
) {
    val motionState = LocalEffectiveMotionState.current
    val performHaptic = rememberMotionAwareHaptic()

    val canSeek = playbackInfo.canUserSeek
    val durationMs = playbackInfo.durationMs.coerceAtLeast(0L)
    val rawProgress = if (playbackInfo.isLiveStream) {
        1f
    } else if (durationMs > 0L) {
        (playbackInfo.currentPositionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
    } else {
        0f
    }

    var isDragging by remember { mutableStateOf(false) }
    var dragFraction by remember { mutableFloatStateOf(rawProgress) }

    DisposableEffect(Unit) {
        onDispose {
            onScrubbingChanged(false)
        }
    }

    val animatedProgress by animateFloatAsState(
        targetValue = if (isDragging) dragFraction else rawProgress,
        animationSpec = if (isDragging || !motionState.animationsEnabled) {
            snap()
        } else {
            tween(durationMillis = 160, easing = LinearEasing)
        },
        label = "miniSeekProgress"
    )
    val displayedProgress = if (isDragging) dragFraction else animatedProgress
    val displayedPositionMs = if (isDragging && durationMs > 0L) {
        (dragFraction * durationMs).toLong().coerceIn(0L, durationMs)
    } else {
        playbackInfo.currentPositionMs.coerceIn(0L, durationMs.coerceAtLeast(0L))
    }
    val remainingMs = (durationMs - displayedPositionMs).coerceAtLeast(0L)

    val animatedTrackHeight by animateDpAsState(
        targetValue = if (isDragging) (trackHeight + 2.5.dp).coerceAtLeast(5.dp) else trackHeight,
        animationSpec = tween(durationMillis = 120),
        label = "miniSeekTrackHeight"
    )

    Column(modifier = modifier.fillMaxWidth()) {
        // Floating preview timestamp pill while scrubbing
        AnimatedVisibility(
            visible = isDragging && canSeek,
            enter = fadeIn(tween(100)),
            exit = fadeOut(tween(120))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 2.dp),
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = activeColor,
                    shadowElevation = 4.dp,
                    modifier = Modifier.testTag("mini_seekbar_preview_timestamp")
                ) {
                    Text(
                        text = "${formatDurationMs(displayedPositionMs)} / ${formatDurationMs(durationMs)}",
                        fontFamily = PlusJakartaFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 10.sp,
                        color = if (isLightColor(activeColor)) Color(0xFF0E1117) else Color.White,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                    )
                }
            }
        }

        if (playbackInfo.isBuffering && !isDragging && !playbackInfo.isLiveStream) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(touchHeight)
                    .testTag("mini_player_seekbar"),
                contentAlignment = Alignment.BottomCenter
            ) {
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(trackHeight),
                    color = activeColor,
                    trackColor = trackColor
                )
            }
        } else {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(touchHeight)
                    .semantics(mergeDescendants = true) {
                        contentDescription = if (playbackInfo.isLiveStream) {
                            "Live stream playback"
                        } else if (!canSeek) {
                            "Playback progress, non-seekable"
                        } else {
                            "Mini player seekbar, ${formatDurationMs(displayedPositionMs)} of ${formatDurationMs(durationMs)}"
                        }
                    }
                    .scrubSeekGestures(
                        enabled = canSeek,
                        progress = displayedProgress,
                        onScrubStart = { fraction ->
                            isDragging = true
                            dragFraction = fraction
                            onScrubbingChanged(true)
                            performHaptic(HapticFeedbackType.TextHandleMove)
                        },
                        onScrubUpdate = { fraction ->
                            dragFraction = fraction
                        },
                        onScrubEnd = { finalFraction ->
                            isDragging = false
                            onScrubbingChanged(false)
                            if (durationMs > 0L) {
                                val targetMs = (finalFraction * durationMs).toLong().coerceIn(0L, durationMs)
                                onSeekTo(targetMs)
                            }
                        },
                        onScrubCancel = {
                            isDragging = false
                            onScrubbingChanged(false)
                        }
                    )
                    .testTag("mini_player_seekbar"),
                contentAlignment = Alignment.BottomCenter
            ) {
                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(if (isDragging) touchHeight else (animatedTrackHeight + 8.dp))
                ) {
                    val barHeightPx = animatedTrackHeight.toPx()
                    val yTop = if (isDragging) (size.height - barHeightPx) / 2f else (size.height - barHeightPx)
                    val corner = if (rounded || isDragging) CornerRadius(barHeightPx / 2f, barHeightPx / 2f) else CornerRadius.Zero
                    val activeWidth = (size.width * displayedProgress.coerceIn(0f, 1f)).coerceIn(0f, size.width)

                    // Background track
                    drawRoundRect(
                        color = trackColor,
                        topLeft = Offset(0f, yTop),
                        size = Size(size.width, barHeightPx),
                        cornerRadius = corner
                    )

                    // Active fill
                    if (activeWidth > 0f) {
                        if (activeBrush != null) {
                            drawRoundRect(
                                brush = activeBrush,
                                topLeft = Offset(0f, yTop),
                                size = Size(activeWidth, barHeightPx),
                                cornerRadius = corner
                            )
                        } else {
                            drawRoundRect(
                                color = if (playbackInfo.isLiveStream) Color(0xFFEF4444) else activeColor,
                                topLeft = Offset(0f, yTop),
                                size = Size(activeWidth, barHeightPx),
                                cornerRadius = corner
                            )
                        }
                    }

                    // Crisp scrubbing thumb dot while dragging
                    if (isDragging && canSeek) {
                        val thumbRadius = (barHeightPx * 1.15f).coerceIn(5.dp.toPx(), 7.dp.toPx())
                        val thumbCenter = Offset(
                            x = activeWidth.coerceIn(thumbRadius, (size.width - thumbRadius).coerceAtLeast(thumbRadius)),
                            y = yTop + barHeightPx / 2f
                        )
                        drawCircle(
                            color = activeColor.copy(alpha = 0.28f),
                            radius = thumbRadius * 1.6f,
                            center = thumbCenter
                        )
                        drawCircle(
                            color = activeColor,
                            radius = thumbRadius,
                            center = thumbCenter
                        )
                    }
                }
            }
        }

        if (showTimestamps || playbackInfo.isLiveStream) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 3.dp, start = 2.dp, end = 2.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (playbackInfo.isLiveStream) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.testTag("mini_seekbar_live_badge")
                    ) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(Color(0xFFEF4444))
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "LIVE",
                            fontFamily = PlusJakartaFontFamily,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 10.sp,
                            color = Color(0xFFEF4444)
                        )
                    }
                    Text(
                        text = "Live broadcast",
                        fontFamily = PlusJakartaFontFamily,
                        fontSize = 10.sp,
                        color = timestampColor.copy(alpha = 0.72f)
                    )
                } else {
                    Text(
                        text = formatDurationMs(displayedPositionMs),
                        fontFamily = PlusJakartaFontFamily,
                        fontWeight = if (isDragging) FontWeight.Bold else FontWeight.Medium,
                        fontSize = 10.sp,
                        color = if (isDragging) activeColor else timestampColor.copy(alpha = 0.78f),
                        modifier = Modifier.testTag("mini_seekbar_elapsed_time")
                    )
                    Text(
                        text = if (isDragging) "-${formatDurationMs(remainingMs)}" else formatDurationMs(durationMs),
                        fontFamily = PlusJakartaFontFamily,
                        fontWeight = FontWeight.Medium,
                        fontSize = 10.sp,
                        color = timestampColor.copy(alpha = 0.72f),
                        modifier = Modifier.testTag("mini_seekbar_total_time")
                    )
                }
            }
        }
    }
}

/**
 * Interactive micro-waveform progress bar for [com.example.model.MiniPlayerStyle.WAVE_MINI].
 * Allows dragging directly across the mini waveform bars with live timestamp preview.
 */
@Composable
fun InteractiveMiniWaveformSeekbar(
    activeColor: Color,
    trackColor: Color,
    modifier: Modifier = Modifier,
    playbackInfo: PlaybackInfo = LocalMiniSeekController.current.playbackInfo,
    onSeekTo: (Long) -> Unit = LocalMiniSeekController.current.onSeekTo,
    onScrubbingChanged: (Boolean) -> Unit = LocalMiniSeekController.current.onScrubbingChanged
) {
    val performHaptic = rememberMotionAwareHaptic()
    val canSeek = playbackInfo.canUserSeek
    val durationMs = playbackInfo.durationMs.coerceAtLeast(0L)
    val rawProgress = if (playbackInfo.isLiveStream) {
        1f
    } else if (durationMs > 0L) {
        (playbackInfo.currentPositionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
    } else {
        0f
    }

    var isDragging by remember { mutableStateOf(false) }
    var dragFraction by remember { mutableFloatStateOf(rawProgress) }

    DisposableEffect(Unit) {
        onDispose { onScrubbingChanged(false) }
    }

    val displayedProgress = if (isDragging) dragFraction else rawProgress
    val displayedPositionMs = if (isDragging && durationMs > 0L) {
        (dragFraction * durationMs).toLong().coerceIn(0L, durationMs)
    } else {
        playbackInfo.currentPositionMs.coerceIn(0L, durationMs.coerceAtLeast(0L))
    }

    val barHeights = remember {
        listOf(0.35f, 0.7f, 0.55f, 0.9f, 0.45f, 0.8f, 0.6f, 1.0f, 0.5f, 0.75f, 0.4f, 0.85f)
    }

    Column(modifier = modifier.fillMaxWidth()) {
        if (isDragging && canSeek) {
            Text(
                text = "${formatDurationMs(displayedPositionMs)} / ${formatDurationMs(durationMs)}",
                fontFamily = PlusJakartaFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 9.sp,
                color = activeColor,
                modifier = Modifier
                    .padding(bottom = 2.dp)
                    .testTag("mini_seekbar_preview_timestamp")
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(20.dp)
                .semantics(mergeDescendants = true) {
                    contentDescription = if (playbackInfo.isLiveStream) {
                        "Live stream waveform"
                    } else {
                        "Mini waveform seekbar, ${formatDurationMs(displayedPositionMs)} of ${formatDurationMs(durationMs)}"
                    }
                }
                .scrubSeekGestures(
                    enabled = canSeek,
                    progress = displayedProgress,
                    onScrubStart = { fraction ->
                        isDragging = true
                        dragFraction = fraction
                        onScrubbingChanged(true)
                        performHaptic(HapticFeedbackType.TextHandleMove)
                    },
                    onScrubUpdate = { fraction -> dragFraction = fraction },
                    onScrubEnd = { finalFraction ->
                        isDragging = false
                        onScrubbingChanged(false)
                        if (durationMs > 0L) {
                            onSeekTo((finalFraction * durationMs).toLong().coerceIn(0L, durationMs))
                        }
                    },
                    onScrubCancel = {
                        isDragging = false
                        onScrubbingChanged(false)
                    }
                )
                .testTag("mini_player_seekbar"),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val bars = 24
            for (i in 0 until bars) {
                val barPos = i.toFloat() / bars.toFloat()
                val played = barPos <= displayedProgress
                val hFraction = barHeights[i % barHeights.size]
                val maxBarHeight = if (isDragging) 14.dp else 10.dp
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 1.dp)
                        .height((maxBarHeight * hFraction).coerceAtLeast(3.dp))
                        .clip(CircleShape)
                        .background(if (played) activeColor else trackColor)
                )
            }
        }
    }
}

private fun isLightColor(color: Color): Boolean {
    val luminance = 0.299f * color.red + 0.587f * color.green + 0.114f * color.blue
    return luminance > 0.62f
}
