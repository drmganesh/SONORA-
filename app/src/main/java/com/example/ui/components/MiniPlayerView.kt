package com.example.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.PlaybackInfo
import com.example.model.Song
import com.example.ui.theme.SonoraCardBorder
import com.example.ui.theme.SonoraCyan
import com.example.ui.theme.SonoraGold
import com.example.ui.theme.SonoraGoldDark
import com.example.ui.theme.SonoraOnGold
import com.example.ui.theme.SonoraSurfaceDark
import com.example.ui.theme.SonoraSurfaceElevated
import com.example.ui.theme.SonoraTextMuted
import com.example.ui.theme.SonoraTextPrimary
import com.example.ui.theme.SonoraTextSecondary
import kotlinx.coroutines.launch
import ui.motion.GestureAxisLock
import ui.motion.MiniPlayerGestureAction
import ui.motion.MiniPlayerGestureMath

@Composable
fun MiniPlayerView(
    song: Song?,
    playbackInfo: PlaybackInfo,
    onTogglePlayPause: () -> Unit,
    onPlayNext: () -> Unit,
    onPlayPrevious: () -> Unit = {},
    onClick: () -> Unit,
    onSwipeUpExpand: () -> Unit = onClick,
    onSwipeDownDismiss: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    if (song == null) return

    val progress = if (playbackInfo.durationMs > 0) {
        (playbackInfo.currentPositionMs.toFloat() / playbackInfo.durationMs.toFloat()).coerceIn(0f, 1f)
    } else 0f

    val palette = ui.theme.LocalDynamicColorPalette.current
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val dragOffsetX = remember { Animatable(0f) }
    val dragOffsetY = remember { Animatable(0f) }
    val horizontalThresholdPx = with(density) { 56.dp.toPx() }
    val verticalUpThresholdPx = with(density) { 42.dp.toPx() }
    val verticalDownThresholdPx = with(density) { 44.dp.toPx() }
    val velocityThresholdPx = with(density) { 560.dp.toPx() }
    val maxHorizontalDragPx = with(density) { 78.dp.toPx() }
    val maxVerticalDragPx = with(density) { 62.dp.toPx() }

    val latestOnPlayNext by rememberUpdatedState(onPlayNext)
    val latestOnPlayPrevious by rememberUpdatedState(onPlayPrevious)
    val latestOnSwipeUpExpand by rememberUpdatedState(onSwipeUpExpand)
    val latestOnSwipeDownDismiss by rememberUpdatedState(onSwipeDownDismiss)

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .graphicsLayer {
                translationX = dragOffsetX.value
                translationY = dragOffsetY.value
            }
            .shadow(12.dp, RoundedCornerShape(20.dp), spotColor = palette.glowColor.copy(alpha = 0.35f))
            .border(1.dp, palette.cardBorder, RoundedCornerShape(20.dp))
            .clip(RoundedCornerShape(20.dp))
            .clickable { onClick() }
            .pointerInput(horizontalThresholdPx, verticalUpThresholdPx, verticalDownThresholdPx) {
                val touchSlop = viewConfiguration.touchSlop
                var lastTriggerUptimeMs = Long.MIN_VALUE

                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = true)
                    var totalDx = 0f
                    var totalDy = 0f
                    var axisLock = GestureAxisLock.NONE
                    var cancelled = false
                    var lastUptimeMs = down.uptimeMillis
                    val velocityTracker = VelocityTracker()
                    velocityTracker.addPosition(down.uptimeMillis, down.position)

                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Main)
                        if (event.changes.count { it.pressed } >= 2) {
                            cancelled = true
                            break
                        }
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        lastUptimeMs = change.uptimeMillis
                        if (!change.pressed) break

                        val delta = change.positionChange()
                        totalDx += delta.x
                        totalDy += delta.y

                        if (axisLock == GestureAxisLock.NONE) {
                            axisLock = MiniPlayerGestureMath.resolveAxisLock(totalDx, totalDy, touchSlop)
                        }

                        if (axisLock != GestureAxisLock.NONE) {
                            change.consume()
                            velocityTracker.addPosition(change.uptimeMillis, change.position)
                            if (axisLock == GestureAxisLock.HORIZONTAL) {
                                val dampedX = MiniPlayerGestureMath.dampedOffset(totalDx, maxHorizontalDragPx)
                                scope.launch { dragOffsetX.snapTo(dampedX) }
                            } else {
                                val dampedY = MiniPlayerGestureMath.dampedOffset(totalDy, maxVerticalDragPx)
                                scope.launch { dragOffsetY.snapTo(dampedY) }
                            }
                        }
                    }

                    val velocity = velocityTracker.calculateVelocity()
                    val cooldownElapsed = lastTriggerUptimeMs == Long.MIN_VALUE ||
                        (lastUptimeMs - lastTriggerUptimeMs) >= 180L
                    val decision = if (!cancelled && cooldownElapsed && axisLock != GestureAxisLock.NONE) {
                        MiniPlayerGestureMath.resolveDecision(
                            axisLock = axisLock,
                            totalDx = totalDx,
                            totalDy = totalDy,
                            velocityX = velocity.x,
                            velocityY = velocity.y,
                            horizontalThresholdPx = horizontalThresholdPx,
                            verticalUpThresholdPx = verticalUpThresholdPx,
                            verticalDownThresholdPx = verticalDownThresholdPx,
                            velocityThresholdPx = velocityThresholdPx,
                            allowExpandUp = true,
                            allowCollapseOrDismissDown = latestOnSwipeDownDismiss != null
                        )
                    } else {
                        MiniPlayerGestureAction.NONE
                    }

                    if (decision != MiniPlayerGestureAction.NONE) {
                        lastTriggerUptimeMs = lastUptimeMs
                    }

                    when (decision) {
                        MiniPlayerGestureAction.SKIP_NEXT -> {
                            latestOnPlayNext()
                            scope.launch { dragOffsetX.animateTo(0f, spring(stiffness = Spring.StiffnessMedium)) }
                        }
                        MiniPlayerGestureAction.SKIP_PREVIOUS -> {
                            latestOnPlayPrevious()
                            scope.launch { dragOffsetX.animateTo(0f, spring(stiffness = Spring.StiffnessMedium)) }
                        }
                        MiniPlayerGestureAction.EXPAND_FULL_PLAYER -> {
                            scope.launch { dragOffsetY.snapTo(0f) }
                            latestOnSwipeUpExpand()
                        }
                        MiniPlayerGestureAction.COLLAPSE_OR_DISMISS -> {
                            scope.launch { dragOffsetY.snapTo(0f) }
                            latestOnSwipeDownDismiss?.invoke()
                        }
                        MiniPlayerGestureAction.NONE -> {
                            scope.launch {
                                launch { dragOffsetX.animateTo(0f, spring(stiffness = Spring.StiffnessMedium)) }
                                launch { dragOffsetY.animateTo(0f, spring(stiffness = Spring.StiffnessMedium)) }
                            }
                        }
                    }
                }
            }
            .testTag("mini_player"),
        color = palette.cardBackground,
        tonalElevation = 6.dp
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            val containerWidth = maxWidth
            val fontScale = LocalDensity.current.fontScale
            val showCodecBadge = containerWidth >= 370.dp && fontScale <= 1.15f
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = if (containerWidth < 340.dp) 8.dp else 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Artwork thumbnail
                ArtworkAlbumView(
                    song = song,
                    modifier = Modifier.size(44.dp).aspectRatio(1f),
                    cornerRadius = 10.dp,
                    isPlaying = playbackInfo.isPlaying
                )

                Spacer(modifier = Modifier.width(10.dp))

                // Title and Artist
                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = song.title,
                        color = SonoraTextPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = song.artist,
                            color = SonoraTextSecondary,
                            fontSize = 12.sp,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Box(
                            modifier = Modifier
                                .wrapContentWidth()
                                .background(palette.accent.copy(alpha = 0.14f), RoundedCornerShape(4.dp))
                                .border(0.5.dp, palette.accent.copy(alpha = 0.35f), RoundedCornerShape(4.dp))
                                .padding(horizontal = 5.dp, vertical = 1.dp)
                                .testTag("mini_player_source_badge")
                        ) {
                            Text(
                                text = if (song.isOnline) "ONLINE" else "LOCAL",
                                color = palette.accent,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        if (showCodecBadge) {
                            Spacer(modifier = Modifier.width(4.dp))
                            Box(
                                modifier = Modifier
                                    .wrapContentWidth()
                                    .background(SonoraCyan.copy(alpha = 0.15f), RoundedCornerShape(4.dp))
                                    .border(0.5.dp, SonoraCyan.copy(alpha = 0.4f), RoundedCornerShape(4.dp))
                                    .padding(horizontal = 5.dp, vertical = 1.dp)
                            ) {
                                Text(
                                    text = if (song.isOnline) {
                                        song.streamCodec.ifBlank { "STREAM" }.uppercase()
                                    } else if (song.mimeType.contains("flac")) {
                                        "FLAC"
                                    } else {
                                        "HI-RES"
                                    },
                                    color = SonoraCyan,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    softWrap = false,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))

                // Play / Pause Button with glowing circle
                IconButton(
                    onClick = onTogglePlayPause,
                    modifier = Modifier
                        .size(42.dp)
                        .background(
                            Brush.linearGradient(listOf(palette.playbackButton, palette.accent)),
                            CircleShape
                        )
                        .testTag("mini_play_pause_button")
                ) {
                    Icon(
                        imageVector = if (playbackInfo.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (playbackInfo.isPlaying) "Pause" else "Play",
                        tint = palette.onPlaybackButton,
                        modifier = Modifier.size(24.dp)
                    )
                }

                Spacer(modifier = Modifier.width(4.dp))

                // Skip Next Button
                IconButton(
                    onClick = onPlayNext,
                    modifier = Modifier
                        .size(38.dp)
                        .testTag("mini_skip_next_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.SkipNext,
                        contentDescription = "Next Track",
                        tint = SonoraTextPrimary,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }

            // Slim progress bar across bottom of mini player
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.5.dp)
                    .background(Color.Black.copy(alpha = 0.5f))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(progress)
                        .height(2.5.dp)
                        .background(palette.progressBar)
                )
            }
        }
        }
    }
}
