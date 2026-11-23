package ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.model.PlaybackInfo
import com.example.model.Song
import com.example.ui.components.ArtworkAlbumView
import data.artwork.ArtworkDisplayRole
import data.preferences.CircularAnimationIntensity
import data.preferences.CircularFloatingSide
import domain.player.CircularMiniPlayerGestureOutcome
import domain.player.MiniPlayerGestureController
import kotlinx.coroutines.launch
import ui.motion.rememberMotionAwareHaptic
import ui.theme.EditorialColors
import ui.theme.LocalDynamicColorPalette
import ui.theme.LocalReduceAnimations
import kotlin.math.hypot
import kotlin.math.roundToInt

/**
 * Draggable, AMOLED-friendly circular floating music mini player with Stage 2 swipe-down dismissal.
 *
 * Interactions powered by the shared [MiniPlayerGestureController]:
 * - Tap disc: expand back to the normal mini player.
 * - Tap play/pause badge: toggle playback without expanding.
 * - Drag horizontally / upward / freely: smoothly reposition within safe app bounds (above bottom
 *   navigation and below status bar), snapping cleanly to Left or Right edge on release.
 * - Swipe down (Stage 2): smoothly slides downward and fades out, dismissing the floating circle
 *   completely while continuing playback uninterrupted in the background.
 * - Accessibility custom actions provide non-gesture alternatives for Expand and Dismiss.
 */
@Composable
fun CircularFloatingMiniPlayer(
    song: Song,
    playbackInfo: PlaybackInfo,
    colors: EditorialColors,
    floatingSide: CircularFloatingSide,
    verticalBias: Float,
    animationIntensity: CircularAnimationIntensity,
    onExpandToMiniPlayer: () -> Unit,
    onTogglePlayPause: () -> Unit,
    onPositionCommitted: (CircularFloatingSide, Float) -> Unit,
    swipeDownDismissEnabled: Boolean = true,
    onSwipeDownDismiss: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val dynamicPalette = LocalDynamicColorPalette.current
    val reduceAnimations = LocalReduceAnimations.current
    val performHaptic = rememberMotionAwareHaptic()
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()

    val expandActionLabel = stringResource(id = R.string.mini_player_action_expand)
    val dismissActionLabel = stringResource(id = R.string.mini_player_action_dismiss)

    val latestOnPositionCommitted by rememberUpdatedState(onPositionCommitted)
    val latestOnSwipeDownDismiss by rememberUpdatedState(onSwipeDownDismiss)
    val latestSwipeDownDismissEnabled by rememberUpdatedState(swipeDownDismissEnabled)
    val latestOnExpand by rememberUpdatedState(onExpandToMiniPlayer)

    val discSizeDp = 64.dp
    val containerSizeDp = 76.dp

    val ringPrimary = dynamicPalette.primary.takeIf { it != Color.Unspecified } ?: colors.accentStrong
    val ringSecondary = dynamicPalette.secondary.takeIf { it != Color.Unspecified } ?: colors.accentStrong.copy(alpha = 0.72f)
    val progress = if (playbackInfo.durationMs > 0L) {
        (playbackInfo.currentPositionMs.toFloat() / playbackInfo.durationMs.toFloat()).coerceIn(0f, 1f)
    } else {
        0f
    }

    // Smooth angle rotation that freezes cleanly at its current angle when paused
    var rotationDegrees by remember(song.id) { mutableFloatStateOf(0f) }
    val effectiveDurationMs = if (reduceAnimations) 0 else animationIntensity.rotationDurationMs
    LaunchedEffect(playbackInfo.isPlaying, effectiveDurationMs, song.id) {
        if (!playbackInfo.isPlaying || effectiveDurationMs <= 0) return@LaunchedEffect
        var lastNanos = 0L
        val degreesPerNs = 360f / (effectiveDurationMs * 1_000_000f)
        while (true) {
            withFrameNanos { frameNanos ->
                if (lastNanos != 0L) {
                    val deltaNs = (frameNanos - lastNanos).coerceIn(0L, 100_000_000L)
                    rotationDegrees = (rotationDegrees + deltaNs * degreesPerNs) % 360f
                }
                lastNanos = frameNanos
            }
        }
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(top = 64.dp, bottom = 92.dp, start = 12.dp, end = 12.dp)
    ) {
        val maxWidthPx = with(density) { maxWidth.toPx() }
        val maxHeightPx = with(density) { maxHeight.toPx() }
        val itemSizePx = with(density) { containerSizeDp.toPx() }
        val dismissDistancePx = with(density) { 84.dp.toPx() }
        val dismissVelocityPx = with(density) { 680.dp.toPx() }
        val dismissSlideOutPx = with(density) { 128.dp.toPx() }
        val downwardOvershootPx = with(density) { 72.dp.toPx() }

        val minX = 0f
        val maxX = (maxWidthPx - itemSizePx).coerceAtLeast(0f)
        val minY = 0f
        val maxY = (maxHeightPx - itemSizePx).coerceAtLeast(0f)

        val targetEdgeX = if (floatingSide == CircularFloatingSide.LEFT) minX else maxX
        val targetRestY = (maxY * verticalBias.coerceIn(0f, 1f)).coerceIn(minY, maxY)

        val offsetX = remember { Animatable(targetEdgeX) }
        val offsetY = remember { Animatable(targetRestY) }
        val dismissAlpha = remember { Animatable(1f) }
        val dismissScale = remember { Animatable(1f) }
        var isDragging by remember { mutableStateOf(false) }
        var isDismissing by remember { mutableStateOf(false) }

        LaunchedEffect(targetEdgeX, targetRestY, maxWidthPx, maxHeightPx) {
            if (!isDragging && !isDismissing) {
                offsetX.snapTo(targetEdgeX)
                offsetY.snapTo(targetRestY)
            }
        }

        fun snapBackToEdge(commitPosition: Boolean) {
            val snappedSide = if (offsetX.value + itemSizePx / 2f < maxWidthPx / 2f) {
                CircularFloatingSide.LEFT
            } else {
                CircularFloatingSide.RIGHT
            }
            val destX = if (snappedSide == CircularFloatingSide.LEFT) minX else maxX
            val clampedY = offsetY.value.coerceIn(minY, maxY)
            if (commitPosition) {
                val bias = if (maxY > 1f) (clampedY / maxY).coerceIn(0.12f, 0.96f) else 0.85f
                latestOnPositionCommitted(snappedSide, bias)
            }
            scope.launch {
                launch {
                    offsetX.animateTo(
                        destX,
                        spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium)
                    )
                }
                launch {
                    offsetY.animateTo(
                        clampedY,
                        spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMedium)
                    )
                }
                launch {
                    dismissAlpha.animateTo(1f, tween(durationMillis = 150))
                }
                launch {
                    dismissScale.animateTo(1f, tween(durationMillis = 150))
                }
            }
        }

        fun performAnimatedStage2Dismiss() {
            if (isDismissing) return
            isDismissing = true
            performHaptic(HapticFeedbackType.TextHandleMove)
            if (reduceAnimations) {
                latestOnSwipeDownDismiss()
                return
            }
            scope.launch {
                val startY = offsetY.value
                val exitJob = launch {
                    offsetY.animateTo(
                        targetValue = startY + dismissSlideOutPx,
                        animationSpec = tween(durationMillis = 200, easing = FastOutSlowInEasing)
                    )
                }
                val fadeJob = launch {
                    dismissAlpha.animateTo(
                        targetValue = 0f,
                        animationSpec = tween(durationMillis = 190, easing = FastOutSlowInEasing)
                    )
                }
                val scaleJob = launch {
                    dismissScale.animateTo(
                        targetValue = 0.72f,
                        animationSpec = tween(durationMillis = 190, easing = FastOutSlowInEasing)
                    )
                }
                exitJob.join()
                fadeJob.join()
                scaleJob.join()
                latestOnSwipeDownDismiss()
            }
        }

        Box(
            modifier = Modifier
                .offset { IntOffset(offsetX.value.roundToInt(), offsetY.value.roundToInt()) }
                .size(containerSizeDp)
                .graphicsLayer {
                    alpha = dismissAlpha.value
                    scaleX = dismissScale.value
                    scaleY = dismissScale.value
                }
                .pointerInput(maxX, maxY, dismissDistancePx, dismissVelocityPx) {
                    val touchSlop = viewConfiguration.touchSlop
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        if (isDismissing) return@awaitEachGesture
                        val pointerId = down.id
                        var accumX = 0f
                        var accumY = 0f
                        var dragStarted = false
                        val tracker = VelocityTracker()
                        tracker.addPosition(down.uptimeMillis, down.position)

                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == pointerId } ?: break
                            if (!change.pressed) {
                                if (dragStarted) {
                                    isDragging = false
                                    val velocity = tracker.calculateVelocity()
                                    val outcome = MiniPlayerGestureController.classifyCircularGesture(
                                        dragX = accumX,
                                        dragY = accumY,
                                        velocityX = velocity.x,
                                        velocityY = velocity.y,
                                        dismissDistancePx = dismissDistancePx,
                                        dismissVelocityPx = dismissVelocityPx,
                                        touchSlopPx = touchSlop,
                                        swipeDownDismissEnabled = latestSwipeDownDismissEnabled
                                    )
                                    when (outcome) {
                                        CircularMiniPlayerGestureOutcome.SWIPE_DOWN_DISMISS -> {
                                            performAnimatedStage2Dismiss()
                                        }
                                        CircularMiniPlayerGestureOutcome.REPOSITION_DRAG -> {
                                            snapBackToEdge(commitPosition = true)
                                        }
                                        CircularMiniPlayerGestureOutcome.NONE -> {
                                            snapBackToEdge(commitPosition = false)
                                        }
                                    }
                                }
                                break
                            }

                            val delta = change.positionChange()
                            accumX += delta.x
                            accumY += delta.y
                            tracker.addPosition(change.uptimeMillis, change.position)

                            if (!dragStarted && hypot(accumX, accumY) > touchSlop) {
                                dragStarted = true
                                isDragging = true
                            }

                            if (dragStarted) {
                                change.consume()
                                val previewProgress = if (latestSwipeDownDismissEnabled) {
                                    MiniPlayerGestureController.circularDismissPreviewProgress(
                                        dragX = accumX,
                                        dragY = accumY,
                                        dismissDistancePx = dismissDistancePx,
                                        touchSlopPx = touchSlop
                                    )
                                } else 0f
                                val maxAllowedY = if (latestSwipeDownDismissEnabled && previewProgress > 0f) {
                                    maxY + downwardOvershootPx
                                } else {
                                    maxY
                                }
                                scope.launch {
                                    offsetX.snapTo((offsetX.value + delta.x).coerceIn(minX, maxX))
                                    offsetY.snapTo((offsetY.value + delta.y).coerceIn(minY, maxAllowedY))
                                    dismissAlpha.snapTo((1f - previewProgress * 0.45f).coerceIn(0.52f, 1f))
                                    dismissScale.snapTo((1f - previewProgress * 0.14f).coerceIn(0.84f, 1f))
                                }
                            }
                        }

                        if (isDragging && !isDismissing) {
                            isDragging = false
                            snapBackToEdge(commitPosition = false)
                        }
                    }
                }
                .testTag("circular_floating_mini_player"),
            contentAlignment = Alignment.Center
        ) {
            // Main circular disc (tap to expand normal mini player; swipe down to dismiss)
            Box(
                modifier = Modifier
                    .size(discSizeDp)
                    .shadow(
                        elevation = if (colors.isDark) 10.dp else 8.dp,
                        shape = CircleShape,
                        ambientColor = ringPrimary.copy(alpha = 0.45f),
                        spotColor = ringPrimary.copy(alpha = 0.55f)
                    )
                    .clip(CircleShape)
                    .background(if (colors.isDark) Color(0xFF0A0A0D) else colors.surface)
                    .clickable(
                        role = Role.Button,
                        onClick = onExpandToMiniPlayer
                    )
                    .semantics {
                        contentDescription = "Expand mini player for ${song.title}. Swipe down to dismiss."
                        val actions = mutableListOf(
                            CustomAccessibilityAction(expandActionLabel) {
                                latestOnExpand()
                                true
                            }
                        )
                        if (latestSwipeDownDismissEnabled) {
                            actions.add(
                                CustomAccessibilityAction(dismissActionLabel) {
                                    performAnimatedStage2Dismiss()
                                    true
                                }
                            )
                        }
                        customActions = actions
                    }
                    .testTag("circular_mini_player_expand"),
                contentAlignment = Alignment.Center
            ) {
                // Circular Progress Ring + Dynamic Album-Art Halo
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val strokePx = 3.5.dp.toPx()
                    val inset = strokePx / 2f + 1.dp.toPx()
                    val arcSize = Size(size.width - inset * 2f, size.height - inset * 2f)
                    val topLeft = Offset(inset, inset)

                    drawArc(
                        color = ringPrimary.copy(alpha = if (colors.isDark) 0.24f else 0.18f),
                        startAngle = 0f,
                        sweepAngle = 360f,
                        useCenter = false,
                        topLeft = topLeft,
                        size = arcSize,
                        style = Stroke(width = strokePx, cap = StrokeCap.Round)
                    )
                    if (progress > 0.002f) {
                        drawArc(
                            brush = Brush.sweepGradient(
                                colors = listOf(ringPrimary, ringSecondary, ringPrimary)
                            ),
                            startAngle = -90f,
                            sweepAngle = 360f * progress,
                            useCenter = false,
                            topLeft = topLeft,
                            size = arcSize,
                            style = Stroke(width = strokePx, cap = StrokeCap.Round)
                        )
                    }
                }

                // Rotating Circular Artwork
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .clip(CircleShape)
                        .graphicsLayer {
                            rotationZ = rotationDegrees
                        }
                        .border(
                            width = 1.dp,
                            color = Color.White.copy(alpha = if (colors.isDark) 0.14f else 0.3f),
                            shape = CircleShape
                        )
                ) {
                    ArtworkAlbumView(
                        song = song,
                        modifier = Modifier.fillMaxSize(),
                        cornerRadius = 26.dp,
                        isPlaying = playbackInfo.isPlaying,
                        artworkRole = ArtworkDisplayRole.THUMBNAIL
                    )
                }
            }

            // Compact Play/Pause Control Badge (accessible touch target with clear visual pill)
            Surface(
                shape = CircleShape,
                color = ringPrimary,
                shadowElevation = 4.dp,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .size(28.dp)
                    .border(
                        width = 1.5.dp,
                        color = if (colors.isDark) Color(0xFF0A0A0D) else colors.surface,
                        shape = CircleShape
                    )
                    .clip(CircleShape)
                    .clickable(
                        role = Role.Button,
                        onClick = onTogglePlayPause
                    )
                    .semantics {
                        contentDescription = if (playbackInfo.isPlaying) "Pause music" else "Play music"
                    }
                    .testTag("circular_mini_player_play_pause")
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = if (playbackInfo.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        contentDescription = if (playbackInfo.isPlaying) "Pause" else "Play",
                        tint = Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}
