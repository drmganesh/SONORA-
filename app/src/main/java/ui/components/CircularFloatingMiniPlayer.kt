package ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.PlaybackInfo
import com.example.model.Song
import com.example.ui.theme.PlusJakartaFontFamily
import kotlinx.coroutines.launch
import ui.motion.GestureAxisLock
import ui.motion.LocalEffectiveMotionState
import ui.motion.MiniPlayerGestureMath
import ui.motion.rememberMotionAwareHaptic
import ui.theme.EditorialColors
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Stage 1 collapsed circular floating music mini-player.
 *
 * Interaction model:
 * - Tap: opens the full music player (or restores controls) without interrupting playback.
 * - Swipe up: restores the full horizontal mini-player bar.
 * - Swipe down (Stage 2): smoothly dismisses the mini-player UI completely while keeping
 *   audio playback, MediaSession, foreground service, and queue active.
 * - Drag sideways/freely: repositions inside safe screen bounds and springs cleanly to the
 *   nearest safe horizontal edge so it never covers bottom navigation or screen chrome.
 */
@Composable
fun CircularFloatingMiniPlayer(
    song: Song?,
    playbackInfo: PlaybackInfo,
    colors: EditorialColors,
    onTapExpand: () -> Unit,
    onRestoreBar: () -> Unit,
    onDismiss: () -> Unit,
    onTogglePlayPause: () -> Unit,
    bottomInsetClearance: Dp = 92.dp,
    modifier: Modifier = Modifier
) {
    if (song == null) return

    val density = LocalDensity.current
    val motionState = LocalEffectiveMotionState.current
    val performHaptic = rememberMotionAwareHaptic()
    val scope = rememberCoroutineScope()

    val latestOnTapExpand by rememberUpdatedState(onTapExpand)
    val latestOnRestoreBar by rememberUpdatedState(onRestoreBar)
    val latestOnDismiss by rememberUpdatedState(onDismiss)
    val latestOnTogglePlayPause by rememberUpdatedState(onTogglePlayPause)
    val latestMotionState by rememberUpdatedState(motionState)

    val progress = if (playbackInfo.durationMs > 0L) {
        (playbackInfo.currentPositionMs.toFloat() / playbackInfo.durationMs.toFloat()).coerceIn(0f, 1f)
    } else 0f

    val medallionSizeDp = 62.dp
    val medallionSizePx = with(density) { medallionSizeDp.toPx() }
    val edgeMarginPx = with(density) { 14.dp.toPx() }
    val topMarginPx = with(density) { 14.dp.toPx() }
    val bottomMarginPx = with(density) { bottomInsetClearance.toPx() }

    val dismissThresholdPx = with(density) { 44.dp.toPx() }
    val restoreThresholdPx = with(density) { 44.dp.toPx() }
    val velocityThresholdPx = with(density) { 520.dp.toPx() }

    // Persist normalized anchor across orientation/tab changes (1f, 1f = bottom-end default)
    var normalizedX by rememberSaveable { mutableFloatStateOf(1f) }
    var normalizedY by rememberSaveable { mutableFloatStateOf(1f) }

    val offsetXAnim = remember { Animatable(0f) }
    val offsetYAnim = remember { Animatable(0f) }
    val dismissDragY = remember { Animatable(0f) }
    var isInitialized by remember { mutableStateOf(false) }
    var isDraggingDownToDismiss by remember { mutableStateOf(false) }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val viewportW = constraints.maxWidth.toFloat().coerceAtLeast(medallionSizePx + edgeMarginPx * 2f)
        val viewportH = constraints.maxHeight.toFloat().coerceAtLeast(medallionSizePx + topMarginPx + bottomMarginPx)
        val minX = edgeMarginPx
        val maxX = (viewportW - edgeMarginPx - medallionSizePx).coerceAtLeast(minX)
        val minY = topMarginPx
        val maxY = (viewportH - bottomMarginPx - medallionSizePx).coerceAtLeast(minY)

        LaunchedEffect(minX, maxX, minY, maxY) {
            val targetX = minX + (maxX - minX) * normalizedX.coerceIn(0f, 1f)
            val targetY = minY + (maxY - minY) * normalizedY.coerceIn(0f, 1f)
            if (!isInitialized) {
                offsetXAnim.snapTo(targetX)
                offsetYAnim.snapTo(targetY)
                isInitialized = true
            } else {
                offsetXAnim.snapTo(targetX.coerceIn(minX, maxX))
                offsetYAnim.snapTo(targetY.coerceIn(minY, maxY))
            }
        }

        val dismissFraction = (dismissDragY.value / (dismissThresholdPx * 1.45f)).coerceIn(0f, 1f)

        Box(
            modifier = Modifier
                .offset {
                    IntOffset(
                        x = offsetXAnim.value.roundToInt(),
                        y = (offsetYAnim.value + dismissDragY.value).roundToInt()
                    )
                }
                .size(medallionSizeDp)
                .graphicsLayer {
                    val scale = 1f - (dismissFraction * 0.16f)
                    scaleX = scale
                    scaleY = scale
                    alpha = (1f - dismissFraction * 0.55f).coerceIn(0.35f, 1f)
                }
                .testTag("circular_mini_player")
                .semantics {
                    contentDescription =
                        "Circular mini-player: ${song.title} by ${song.artist}. Tap to expand, swipe up to restore bar, swipe down to dismiss."
                    customActions = listOf(
                        CustomAccessibilityAction("Expand full player") {
                            latestOnTapExpand()
                            true
                        },
                        CustomAccessibilityAction("Restore mini-player bar") {
                            latestOnRestoreBar()
                            true
                        },
                        CustomAccessibilityAction("Dismiss mini-player") {
                            latestOnDismiss()
                            true
                        },
                        CustomAccessibilityAction(if (playbackInfo.isPlaying) "Pause" else "Play") {
                            latestOnTogglePlayPause()
                            true
                        }
                    )
                }
                .pointerInput(minX, maxX, minY, maxY, dismissThresholdPx, restoreThresholdPx) {
                    val touchSlop = viewConfiguration.touchSlop
                    var lastActionUptimeMs = Long.MIN_VALUE

                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = true)
                        var totalDx = 0f
                        var totalDy = 0f
                        var gestureMode = 0 // 0=undecided/tap, 1=dismissDown, 2=restoreUp, 3=reposition
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
                            velocityTracker.addPosition(change.uptimeMillis, change.position)

                            if (gestureMode == 0) {
                                val dist = kotlin.math.hypot(totalDx.toDouble(), totalDy.toDouble()).toFloat()
                                if (dist > touchSlop) {
                                    val absX = abs(totalDx)
                                    val absY = abs(totalDy)
                                    gestureMode = when {
                                        totalDy > 0f && absY >= absX * 1.20f -> {
                                            isDraggingDownToDismiss = true
                                            1
                                        }
                                        totalDy < 0f && absY >= absX * 1.45f -> 2
                                        else -> 3
                                    }
                                }
                            }

                            when (gestureMode) {
                                1 -> {
                                    change.consume()
                                    val clampedPull = MiniPlayerGestureMath.dampedOffset(
                                        rawPx = totalDy.coerceAtLeast(0f),
                                        maxTravelPx = dismissThresholdPx * 2.2f
                                    )
                                    scope.launch { dismissDragY.snapTo(clampedPull) }
                                }
                                2 -> {
                                    change.consume()
                                    val clampedUp = MiniPlayerGestureMath.dampedOffset(
                                        rawPx = totalDy.coerceAtMost(0f),
                                        maxTravelPx = restoreThresholdPx * 1.4f
                                    )
                                    scope.launch { dismissDragY.snapTo(clampedUp) }
                                }
                                3 -> {
                                    change.consume()
                                    val nextX = (offsetXAnim.value + delta.x).coerceIn(minX, maxX)
                                    val nextY = (offsetYAnim.value + delta.y).coerceIn(minY, maxY)
                                    scope.launch {
                                        offsetXAnim.snapTo(nextX)
                                        offsetYAnim.snapTo(nextY)
                                    }
                                }
                            }
                        }

                        isDraggingDownToDismiss = false
                        val velocity = velocityTracker.calculateVelocity()
                        val cooldownReady = lastActionUptimeMs == Long.MIN_VALUE ||
                            (lastUptimeMs - lastActionUptimeMs) >= 180L

                        when {
                            cancelled -> {
                                scope.launch { dismissDragY.animateTo(0f, spring(stiffness = Spring.StiffnessMediumLow)) }
                            }
                            gestureMode == 0 -> {
                                if (cooldownReady) {
                                    lastActionUptimeMs = lastUptimeMs
                                    performHaptic(HapticFeedbackType.TextHandleMove)
                                    latestOnTapExpand()
                                }
                            }
                            gestureMode == 1 -> {
                                val shouldDismiss = totalDy >= dismissThresholdPx ||
                                    (velocity.y >= velocityThresholdPx && totalDy >= dismissThresholdPx * 0.35f)
                                if (shouldDismiss && cooldownReady) {
                                    lastActionUptimeMs = lastUptimeMs
                                    performHaptic(HapticFeedbackType.LongPress)
                                    scope.launch {
                                        if (latestMotionState.animationsEnabled) {
                                            dismissDragY.animateTo(dismissThresholdPx * 1.8f, tween(130))
                                        }
                                        dismissDragY.snapTo(0f)
                                        latestOnDismiss()
                                    }
                                } else {
                                    scope.launch {
                                        dismissDragY.animateTo(
                                            0f,
                                            spring(dampingRatio = 0.78f, stiffness = Spring.StiffnessMedium)
                                        )
                                    }
                                }
                            }
                            gestureMode == 2 -> {
                                val shouldRestore = -totalDy >= restoreThresholdPx ||
                                    (-velocity.y >= velocityThresholdPx && -totalDy >= restoreThresholdPx * 0.35f)
                                scope.launch { dismissDragY.animateTo(0f, spring(stiffness = Spring.StiffnessMedium)) }
                                if (shouldRestore && cooldownReady) {
                                    lastActionUptimeMs = lastUptimeMs
                                    performHaptic(HapticFeedbackType.TextHandleMove)
                                    latestOnRestoreBar()
                                }
                            }
                            gestureMode == 3 -> {
                                // Snap smoothly to nearest left or right safe edge while keeping clamped Y
                                val midX = (minX + maxX) / 2f
                                val targetEdgeX = if (offsetXAnim.value >= midX) maxX else minX
                                val clampedY = offsetYAnim.value.coerceIn(minY, maxY)
                                val spanX = (maxX - minX).coerceAtLeast(1f)
                                val spanY = (maxY - minY).coerceAtLeast(1f)
                                normalizedX = ((targetEdgeX - minX) / spanX).coerceIn(0f, 1f)
                                normalizedY = ((clampedY - minY) / spanY).coerceIn(0f, 1f)
                                scope.launch {
                                    if (latestMotionState.animationsEnabled) {
                                        offsetXAnim.animateTo(
                                            targetEdgeX,
                                            spring(dampingRatio = 0.80f, stiffness = Spring.StiffnessMedium)
                                        )
                                    } else {
                                        offsetXAnim.snapTo(targetEdgeX)
                                    }
                                }
                            }
                        }
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            Surface(
                shape = CircleShape,
                color = colors.miniPlayerBackground,
                shadowElevation = 10.dp,
                border = BorderStroke(1.dp, colors.accentStrong.copy(alpha = 0.55f)),
                modifier = Modifier
                    .fillMaxSize()
                    .shadow(10.dp, CircleShape, ambientColor = colors.shadowColor, spotColor = colors.shadowColor)
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    // Radial playback progress ring
                    Canvas(modifier = Modifier.fillMaxSize().padding(2.dp)) {
                        val strokePx = 3.dp.toPx()
                        val inset = strokePx / 2f
                        val arcSize = Size(size.width - strokePx, size.height - strokePx)
                        drawArc(
                            color = colors.cardBorder.copy(alpha = 0.45f),
                            startAngle = -90f,
                            sweepAngle = 360f,
                            useCenter = false,
                            topLeft = Offset(inset, inset),
                            size = arcSize,
                            style = Stroke(width = strokePx, cap = StrokeCap.Round)
                        )
                        drawArc(
                            color = colors.accentStrong,
                            startAngle = -90f,
                            sweepAngle = 360f * progress,
                            useCenter = false,
                            topLeft = Offset(inset, inset),
                            size = arcSize,
                            style = Stroke(width = strokePx, cap = StrokeCap.Round)
                        )
                    }

                    CircularArtwork(
                        song = song,
                        isPlaying = playbackInfo.isPlaying,
                        accentColor = colors.accentStrong,
                        shadowElevation = 2.dp,
                        modifier = Modifier.size(48.dp)
                    )

                    // Subtle play/pause status or swipe-down dismiss indicator badge
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(2.dp)
                            .size(20.dp)
                            .clip(CircleShape)
                            .background(
                                if (isDraggingDownToDismiss) Color(0xFFD94A4A) else colors.accentStrong
                            )
                            .border(1.dp, colors.miniPlayerBackground, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = when {
                                isDraggingDownToDismiss -> Icons.Rounded.KeyboardArrowDown
                                playbackInfo.isPlaying -> Icons.Rounded.Pause
                                else -> Icons.Rounded.PlayArrow
                            },
                            contentDescription = null,
                            tint = colors.onAccentStrong,
                            modifier = Modifier.size(12.dp)
                        )
                    }
                }
            }
        }
    }
}

/**
 * Compact, non-intrusive persistent restore pill displayed when the user dismisses the
 * mini-player UI (Stage 2) while audio playback continues in the background/service.
 * Tap restores the standard mini-player bar; swipe up opens the full player directly.
 */
@Composable
fun MiniPlayerRestorePill(
    song: Song?,
    isPlaying: Boolean,
    colors: EditorialColors,
    onRestoreMiniPlayer: () -> Unit,
    onOpenFullPlayer: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (song == null) return

    val density = LocalDensity.current
    val performHaptic = rememberMotionAwareHaptic()
    val latestRestore by rememberUpdatedState(onRestoreMiniPlayer)
    val latestOpenFull by rememberUpdatedState(onOpenFullPlayer)
    val swipeUpThresholdPx = with(density) { 36.dp.toPx() }

    Box(
        modifier = modifier
            .heightIn(min = 48.dp)
            .semantics {
                contentDescription = "Restore mini-player for ${song.title}. Tap to restore, swipe up to open full player."
                customActions = listOf(
                    CustomAccessibilityAction("Restore mini-player") {
                        latestRestore()
                        true
                    },
                    CustomAccessibilityAction("Open full music player") {
                        latestOpenFull()
                        true
                    }
                )
            }
            .pointerInput(swipeUpThresholdPx) {
                val touchSlop = viewConfiguration.touchSlop
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = true)
                    var totalDx = 0f
                    var totalDy = 0f
                    var lockedUp = false
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Main)
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (!change.pressed) break
                        val delta = change.positionChange()
                        totalDx += delta.x
                        totalDy += delta.y
                        if (!lockedUp && -totalDy > touchSlop && abs(totalDy) > abs(totalDx) * 1.25f) {
                            lockedUp = true
                        }
                        if (lockedUp) {
                            change.consume()
                        }
                    }
                    if (lockedUp && -totalDy >= swipeUpThresholdPx) {
                        performHaptic(HapticFeedbackType.TextHandleMove)
                        latestOpenFull()
                    }
                }
            }
            .testTag("mini_player_restore_pill"),
        contentAlignment = Alignment.CenterEnd
    ) {
        Surface(
            shape = RoundedCornerShape(999.dp),
            color = colors.miniPlayerBackground.copy(alpha = 0.94f),
            border = BorderStroke(0.9.dp, colors.accentStrong.copy(alpha = 0.55f)),
            shadowElevation = 6.dp,
            modifier = Modifier
                .clip(RoundedCornerShape(999.dp))
                .clickable {
                    performHaptic(HapticFeedbackType.TextHandleMove)
                    onRestoreMiniPlayer()
                }
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.GraphicEq,
                    contentDescription = null,
                    tint = colors.accentStrong,
                    modifier = Modifier.size(15.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = if (isPlaying) "Playing" else "Paused",
                    fontFamily = PlusJakartaFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.5.sp,
                    color = colors.textPrimary,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
