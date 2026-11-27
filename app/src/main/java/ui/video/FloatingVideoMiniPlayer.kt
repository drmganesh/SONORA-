package ui.video

import android.app.ActivityManager
import android.content.Context
import android.graphics.Rect
import android.view.TextureView
import android.view.ViewGroup
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.OpenInFull
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
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
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.theme.OutfitFontFamily
import com.example.ui.theme.PlusJakartaFontFamily
import domain.video.VideoAspectRatioCalculator
import domain.video.VideoPlaybackProgress
import domain.video.VideoPlayerUiState
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import ui.motion.FloatingPipLayoutMath
import ui.motion.FloatingPipRectPx
import ui.theme.EditorialColors
import kotlin.math.hypot
import kotlin.math.roundToInt

/**
 * In-app floating PiP video player for SONORA Online Video mode.
 * Renders the active video at its natural aspect ratio (16:9, 9:16, 4:3, 1:1, rotated, etc.)
 * using the existing single Media3 ExoPlayer TextureView, cleanly clamped inside safe screen bounds.
 *
 * Gesture model (in-app floating PiP only; native Android system PiP remains OS-managed):
 * - One-finger tap on the video surface shows or hides compact playback controls.
 * - One-finger drag moves the floating PiP smoothly anywhere inside safe screen boundaries.
 * - Two-finger pinch outward/inward smoothly enlarges/shrinks the floating PiP within adaptive
 *   limits (20%–85% screen width) while preserving exact video aspect ratio and keeping the
 *   single TextureView continuously attached with zero playback restart.
 */
@Composable
fun FloatingVideoMiniPlayer(
    playerState: VideoPlayerUiState,
    colors: EditorialColors,
    onExpand: () -> Unit,
    onTogglePlayPause: () -> Unit,
    onPlayNext: () -> Unit,
    onClose: () -> Unit,
    playbackProgressFlow: StateFlow<VideoPlaybackProgress>? = null,
    onAttachTextureView: ((TextureView) -> Unit)? = null,
    onDetachTextureView: ((TextureView) -> Unit)? = null,
    bottomInsetClearance: Dp = 12.dp,
    initialWidthFraction: Float = FloatingPipLayoutMath.DEFAULT_WIDTH_FRACTION,
    initialNormalizedX: Float = 1f,
    initialNormalizedY: Float = 1f,
    onPipLayoutChanged: ((widthFraction: Float, normalizedX: Float, normalizedY: Float) -> Unit)? = null,
    onVideoViewportBoundsChanged: ((Rect?) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val video = playerState.activeVideo ?: return
    val latestOnVideoViewportBoundsChanged by rememberUpdatedState(onVideoViewportBoundsChanged)
    val latestOnPipLayoutChanged by rememberUpdatedState(onPipLayoutChanged)
    val latestOnExpand by rememberUpdatedState(onExpand)

    DisposableEffect(Unit) {
        onDispose {
            latestOnVideoViewportBoundsChanged?.invoke(null)
        }
    }

    val context = LocalContext.current
    val density = LocalDensity.current

    val isLowRamDevice = remember(context) {
        runCatching {
            val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
            am?.isLowRamDevice == true
        }.getOrDefault(false)
    }

    val videoAspectRatio = remember(
        playerState.videoWidth,
        playerState.videoHeight,
        playerState.pixelWidthHeightRatio,
        playerState.unappliedRotationDegrees
    ) {
        VideoAspectRatioCalculator.resolveDisplayAspectRatio(
            videoWidth = playerState.videoWidth,
            videoHeight = playerState.videoHeight,
            pixelWidthHeightRatio = playerState.pixelWidthHeightRatio,
            unappliedRotationDegrees = playerState.unappliedRotationDegrees
        ).coerceIn(0.25f, 4.0f)
    }

    var controlsVisible by rememberSaveable { mutableStateOf(true) }
    var controlsInteractionNonce by remember { mutableIntStateOf(0) }

    // Persist interactive size & normalized anchor across recompositions and orientation changes
    var widthFraction by rememberSaveable {
        mutableFloatStateOf(
            initialWidthFraction.coerceIn(
                FloatingPipLayoutMath.MIN_WIDTH_FRACTION,
                FloatingPipLayoutMath.MAX_WIDTH_FRACTION
            )
        )
    }
    var normalizedX by rememberSaveable { mutableFloatStateOf(initialNormalizedX.coerceIn(0f, 1f)) }
    var normalizedY by rememberSaveable { mutableFloatStateOf(initialNormalizedY.coerceIn(0f, 1f)) }
    var isPinching by remember { mutableStateOf(false) }
    var isDragging by remember { mutableStateOf(false) }

    // Auto-hide controls shortly after inactivity while playing smoothly (pause timer while dragging/pinching)
    LaunchedEffect(
        controlsVisible,
        playerState.isPlaying,
        playerState.isResolving,
        playerState.isBuffering,
        isPinching,
        isDragging,
        controlsInteractionNonce
    ) {
        if (
            controlsVisible &&
            playerState.isPlaying &&
            !playerState.isResolving &&
            !playerState.isBuffering &&
            !isPinching &&
            !isDragging
        ) {
            delay(3_500L)
            controlsVisible = false
        }
    }

    BoxWithConstraints(modifier = modifier) {
        val hasBoundedHeight = constraints.hasBoundedHeight
        val viewportWidthPx = constraints.maxWidth.toFloat().coerceAtLeast(1f)
        val viewportHeightPx = if (hasBoundedHeight) {
            constraints.maxHeight.toFloat().coerceAtLeast(1f)
        } else {
            (viewportWidthPx * 1.8f).coerceAtLeast(320f)
        }
        val edgeMarginPx = with(density) { 12.dp.toPx() }
        val topMarginPx = if (hasBoundedHeight) with(density) { 10.dp.toPx() } else edgeMarginPx
        val bottomMarginPx = if (hasBoundedHeight) with(density) { bottomInsetClearance.toPx() } else edgeMarginPx

        // Resolve aspect-ratio-preserving adaptive PiP size & safe clamped offset
        val resolvedSize = FloatingPipLayoutMath.resolveSizePx(
            viewportWidthPx = viewportWidthPx,
            viewportHeightPx = viewportHeightPx,
            edgeMarginPx = edgeMarginPx,
            topMarginPx = topMarginPx,
            bottomMarginPx = bottomMarginPx,
            videoAspectRatio = videoAspectRatio,
            requestedWidthFraction = widthFraction
        )
        val pipWidthPx = resolvedSize.widthPx
        val pipHeightPx = resolvedSize.heightPx

        val (resolvedOffsetX, resolvedOffsetY) = if (hasBoundedHeight) {
            FloatingPipLayoutMath.fromNormalizedPosition(
                normalizedX = normalizedX,
                normalizedY = normalizedY,
                widthPx = pipWidthPx,
                heightPx = pipHeightPx,
                viewportWidthPx = viewportWidthPx,
                viewportHeightPx = viewportHeightPx,
                edgeMarginPx = edgeMarginPx,
                topMarginPx = topMarginPx,
                bottomMarginPx = bottomMarginPx
            )
        } else {
            val x = (viewportWidthPx - edgeMarginPx - pipWidthPx).coerceAtLeast(edgeMarginPx)
            x to edgeMarginPx
        }

        // Latest layout geometry exposed to long-lived pointerInput without restarting active gestures
        val latestViewportW by rememberUpdatedState(viewportWidthPx)
        val latestViewportH by rememberUpdatedState(viewportHeightPx)
        val latestEdgeMargin by rememberUpdatedState(edgeMarginPx)
        val latestTopMargin by rememberUpdatedState(topMarginPx)
        val latestBottomMargin by rememberUpdatedState(bottomMarginPx)
        val latestAspectRatio by rememberUpdatedState(videoAspectRatio)
        val latestPipWidth by rememberUpdatedState(pipWidthPx)
        val latestPipHeight by rememberUpdatedState(pipHeightPx)
        val latestOffsetX by rememberUpdatedState(resolvedOffsetX)
        val latestOffsetY by rememberUpdatedState(resolvedOffsetY)
        val latestHasBoundedHeight by rememberUpdatedState(hasBoundedHeight)

        val pipWidthDp = with(density) { pipWidthPx.toDp() }
        val pipHeightDp = with(density) { pipHeightPx.toDp() }

        val containerModifier = if (hasBoundedHeight) {
            Modifier.fillMaxSize()
        } else {
            Modifier
                .fillMaxWidth()
                .height(pipHeightDp + 24.dp)
        }

        Box(modifier = containerModifier) {
            Box(
                modifier = Modifier
                    .offset {
                        IntOffset(
                            x = resolvedOffsetX.roundToInt(),
                            y = resolvedOffsetY.roundToInt()
                        )
                    }
                    .size(width = pipWidthDp, height = pipHeightDp)
                    .onGloballyPositioned { coords ->
                        val cb = latestOnVideoViewportBoundsChanged ?: return@onGloballyPositioned
                        val b = coords.boundsInWindow()
                        val l = b.left.roundToInt()
                        val t = b.top.roundToInt()
                        val r = b.right.roundToInt()
                        val bt = b.bottom.roundToInt()
                        if (r > l && bt > t) {
                            cb(Rect(l, t, r, bt))
                        }
                    }
                    .testTag("floating_pip_video_surface")
                    .semantics {
                        contentDescription =
                            "Floating video player: ${video.title}. Tap to toggle controls, drag with one finger to move, pinch with two fingers to resize."
                        customActions = listOf(
                            CustomAccessibilityAction("Enlarge floating video") {
                                val enlarged = (widthFraction + 0.15f).coerceIn(
                                    FloatingPipLayoutMath.MIN_WIDTH_FRACTION,
                                    FloatingPipLayoutMath.MAX_WIDTH_FRACTION
                                )
                                widthFraction = enlarged
                                latestOnPipLayoutChanged?.invoke(enlarged, normalizedX, normalizedY)
                                true
                            },
                            CustomAccessibilityAction("Shrink floating video") {
                                val shrunk = (widthFraction - 0.15f).coerceIn(
                                    FloatingPipLayoutMath.MIN_WIDTH_FRACTION,
                                    FloatingPipLayoutMath.MAX_WIDTH_FRACTION
                                )
                                widthFraction = shrunk
                                latestOnPipLayoutChanged?.invoke(shrunk, normalizedX, normalizedY)
                                true
                            },
                            CustomAccessibilityAction("Toggle controls") {
                                controlsVisible = !controlsVisible
                                controlsInteractionNonce++
                                true
                            },
                            CustomAccessibilityAction("Expand video player") {
                                latestOnExpand()
                                true
                            }
                        )
                    }
                    .pointerInput(video.videoId) {
                        val touchSlop = viewConfiguration.touchSlop

                        awaitEachGesture {
                            val firstDown = awaitFirstDown(requireUnconsumed = false)
                            val firstDownConsumedByChild = firstDown.isConsumed
                            var mode = 0 // 0=undecided/tap, 1=oneFingerDrag, 2=twoFingerPinch
                            var accumDx = 0f
                            var accumDy = 0f

                            // Live working coordinates for smooth multi-frame drag/pinch without drift
                            var workingX = latestOffsetX
                            var workingY = latestOffsetY
                            var workingW = latestPipWidth
                            var workingH = latestPipHeight
                            var workingFraction = widthFraction

                            while (true) {
                                val event = awaitPointerEvent(PointerEventPass.Main)
                                val activeChanges = event.changes.filter { it.pressed }
                                if (activeChanges.isEmpty()) break

                                if (activeChanges.size >= 2) {
                                    // Two-finger pinch-to-resize ALWAYS takes precedence over 1-finger drag/tap
                                    if (mode != 2) {
                                        mode = 2
                                        isDragging = false
                                        isPinching = true
                                        workingX = latestOffsetX
                                        workingY = latestOffsetY
                                        workingW = latestPipWidth
                                        workingH = latestPipHeight
                                        workingFraction = widthFraction
                                    }

                                    val zoom = event.calculateZoom()
                                    val pan = event.calculatePan()
                                    if (zoom != 1f || pan.x != 0f || pan.y != 0f) {
                                        val updated = FloatingPipLayoutMath.applyPinchZoom(
                                            currentRect = FloatingPipRectPx(
                                                x = workingX,
                                                y = workingY,
                                                width = workingW,
                                                height = workingH,
                                                widthFraction = workingFraction
                                            ),
                                            zoomFactor = zoom,
                                            panDx = pan.x,
                                            panDy = if (latestHasBoundedHeight) pan.y else 0f,
                                            viewportWidthPx = latestViewportW,
                                            viewportHeightPx = latestViewportH,
                                            edgeMarginPx = latestEdgeMargin,
                                            topMarginPx = latestTopMargin,
                                            bottomMarginPx = latestBottomMargin,
                                            videoAspectRatio = latestAspectRatio
                                        )
                                        workingX = updated.x
                                        workingY = updated.y
                                        workingW = updated.width
                                        workingH = updated.height
                                        workingFraction = updated.widthFraction

                                        widthFraction = workingFraction
                                        if (latestHasBoundedHeight) {
                                            val (nx, ny) = FloatingPipLayoutMath.toNormalizedPosition(
                                                x = workingX,
                                                y = workingY,
                                                widthPx = workingW,
                                                heightPx = workingH,
                                                viewportWidthPx = latestViewportW,
                                                viewportHeightPx = latestViewportH,
                                                edgeMarginPx = latestEdgeMargin,
                                                topMarginPx = latestTopMargin,
                                                bottomMarginPx = latestBottomMargin
                                            )
                                            normalizedX = nx
                                            normalizedY = ny
                                        }
                                    }
                                    event.changes.forEach { it.consume() }
                                } else {
                                    // Single active finger
                                    if (mode == 2) {
                                        // Stay locked out of 1-finger drag until all fingers lift after a pinch
                                        event.changes.forEach { it.consume() }
                                        continue
                                    }

                                    val change = event.changes.firstOrNull { it.id == firstDown.id }
                                        ?: activeChanges.first()
                                    val delta = change.positionChange()
                                    accumDx += delta.x
                                    accumDy += delta.y

                                    if (mode == 0) {
                                        val moved = hypot(accumDx.toDouble(), accumDy.toDouble()).toFloat()
                                        if (moved > touchSlop) {
                                            mode = 1
                                            isDragging = true
                                            workingX = latestOffsetX
                                            workingY = latestOffsetY
                                            workingW = latestPipWidth
                                            workingH = latestPipHeight
                                        }
                                    }

                                    if (mode == 1) {
                                        change.consume()
                                        val (clampedX, clampedY) = FloatingPipLayoutMath.clampOffsetPx(
                                            x = workingX + delta.x,
                                            y = if (latestHasBoundedHeight) workingY + delta.y else workingY,
                                            widthPx = workingW,
                                            heightPx = workingH,
                                            viewportWidthPx = latestViewportW,
                                            viewportHeightPx = latestViewportH,
                                            edgeMarginPx = latestEdgeMargin,
                                            topMarginPx = latestTopMargin,
                                            bottomMarginPx = latestBottomMargin
                                        )
                                        workingX = clampedX
                                        workingY = clampedY

                                        if (latestHasBoundedHeight) {
                                            val (nx, ny) = FloatingPipLayoutMath.toNormalizedPosition(
                                                x = workingX,
                                                y = workingY,
                                                widthPx = workingW,
                                                heightPx = workingH,
                                                viewportWidthPx = latestViewportW,
                                                viewportHeightPx = latestViewportH,
                                                edgeMarginPx = latestEdgeMargin,
                                                topMarginPx = latestTopMargin,
                                                bottomMarginPx = latestBottomMargin
                                            )
                                            normalizedX = nx
                                            normalizedY = ny
                                        }
                                    }
                                }
                            }

                            val finishedMode = mode
                            isPinching = false
                            isDragging = false

                            when (finishedMode) {
                                0 -> {
                                    // Tap on video surface (not on a child IconButton) toggles controls
                                    if (!firstDownConsumedByChild) {
                                        controlsVisible = !controlsVisible
                                        controlsInteractionNonce++
                                    }
                                }
                                1, 2 -> {
                                    latestOnPipLayoutChanged?.invoke(widthFraction, normalizedX, normalizedY)
                                }
                            }
                        }
                    }
            ) {
                val cornerShape = RoundedCornerShape(16.dp)
                val elevation = if (isLowRamDevice) 0.dp else 8.dp

                Surface(
                    shape = cornerShape,
                    color = Color.Black,
                    border = BorderStroke(
                        width = if (isPinching || isDragging) 1.5.dp else 1.dp,
                        color = colors.accentStrong.copy(alpha = if (isPinching || isDragging) 0.85f else 0.50f)
                    ),
                    shadowElevation = elevation,
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(cornerShape)
                        .testTag("floating_video_mini_player")
                ) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        // 1. Thumbnail backdrop prevents black frames while resolving or in headless tests
                        val showBackdropThumbnail =
                            onAttachTextureView == null ||
                                playerState.isResolving ||
                                !playerState.hasKnownVideoDimensions
                        if (showBackdropThumbnail) {
                            VideoThumbnailBox(
                                video = video,
                                colors = colors,
                                compactBadge = true,
                                modifier = Modifier.fillMaxSize()
                            )
                        }

                        // 2. Live Media3 ExoPlayer TextureView — created ONCE and kept continuously attached
                        if (onAttachTextureView != null) {
                            FloatingPipLiveVideoSurface(
                                onAttachTextureView = onAttachTextureView,
                                onDetachTextureView = onDetachTextureView,
                                modifier = Modifier.fillMaxSize()
                            )
                        }

                        // 3. Compact playback controls (toggled via one-finger tap; hidden while pinching)
                        AnimatedVisibility(
                            visible = controlsVisible && !isPinching,
                            enter = fadeIn(tween(durationMillis = 140)),
                            exit = fadeOut(tween(durationMillis = 140)),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            FloatingPipControlsOverlay(
                                title = video.title,
                                channelName = if (playerState.isResolving) {
                                    "Resolving stream..."
                                } else {
                                    video.channelName
                                },
                                isPlaying = playerState.isPlaying,
                                isBusy = playerState.isResolving || playerState.isBuffering,
                                hasNextInQueue = playerState.hasNextInQueue,
                                containerWidthDp = pipWidthDp,
                                colors = colors,
                                onExpand = onExpand,
                                onTogglePlayPause = {
                                    controlsInteractionNonce++
                                    onTogglePlayPause()
                                },
                                onPlayNext = {
                                    controlsInteractionNonce++
                                    onPlayNext()
                                },
                                onClose = onClose
                            )
                        }

                        // 3b. Subtle live scale badge visible only while two-finger pinching
                        AnimatedVisibility(
                            visible = isPinching,
                            enter = fadeIn(tween(durationMillis = 90)),
                            exit = fadeOut(tween(durationMillis = 110)),
                            modifier = Modifier.align(Alignment.Center)
                        ) {
                            Surface(
                                shape = RoundedCornerShape(999.dp),
                                color = Color.Black.copy(alpha = 0.72f),
                                border = BorderStroke(0.75.dp, colors.accentStrong.copy(alpha = 0.75f)),
                                modifier = Modifier.testTag("floating_pip_resize_badge")
                            ) {
                                Text(
                                    text = "${(resolvedSize.effectiveWidthFraction * 100f).roundToInt()}%",
                                    fontFamily = PlusJakartaFontFamily,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp,
                                    color = Color.White,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                )
                            }
                        }

                        // 4. Isolated live progress strip (collects 500ms ticks without recomposing TextureView)
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .fillMaxWidth()
                        ) {
                            MiniPlayerProgressBar(
                                fallbackPositionMs = playerState.currentPositionMs,
                                fallbackDurationMs = playerState.durationMs,
                                playbackProgressFlow = playbackProgressFlow,
                                colors = colors
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Isolated TextureView holder so control state, drag, or pinch recompositions never recreate
 * the view or detach/reattach the active ExoPlayer video surface.
 */
@Composable
private fun FloatingPipLiveVideoSurface(
    onAttachTextureView: (TextureView) -> Unit,
    onDetachTextureView: ((TextureView) -> Unit)?,
    modifier: Modifier = Modifier
) {
    val latestAttach by rememberUpdatedState(onAttachTextureView)
    val latestDetach by rememberUpdatedState(onDetachTextureView)

    AndroidView(
        factory = { ctx ->
            TextureView(ctx).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
                isOpaque = false
                latestAttach(this)
            }
        },
        update = { view ->
            // Idempotent safeguard in VideoPlaybackController ensures no re-attach churn
            latestAttach(view)
        },
        onRelease = { view ->
            latestDetach?.invoke(view)
        },
        modifier = modifier
    )
}

@Composable
private fun FloatingPipControlsOverlay(
    title: String,
    channelName: String,
    isPlaying: Boolean,
    isBusy: Boolean,
    hasNextInQueue: Boolean,
    containerWidthDp: Dp,
    colors: EditorialColors,
    onExpand: () -> Unit,
    onTogglePlayPause: () -> Unit,
    onPlayNext: () -> Unit,
    onClose: () -> Unit
) {
    val isUltraCompact = containerWidthDp < 136.dp
    val showTitleText = containerWidthDp >= 204.dp
    val showNextButton = hasNextInQueue && containerWidthDp >= 124.dp
    val topIconButtonSize = if (isUltraCompact) 32.dp else 36.dp
    val topIconSize = if (isUltraCompact) 16.dp else 18.dp
    val centerButtonSize = if (isUltraCompact) 34.dp else 42.dp
    val centerIconSize = if (isUltraCompact) 18.dp else 22.dp

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color.Black.copy(alpha = 0.62f),
                        Color.Black.copy(alpha = 0.28f),
                        Color.Black.copy(alpha = 0.56f)
                    )
                )
            )
            .testTag("floating_pip_controls_overlay")
    ) {
        // Top Row: Optional title (when wide enough) + Expand + Close
        Row(
            modifier = Modifier
                .align(Alignment.TopStart)
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (showTitleText) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 6.dp, end = 4.dp)
                ) {
                    Text(
                        text = title,
                        fontFamily = OutfitFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.5.sp,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = channelName,
                        fontFamily = PlusJakartaFontFamily,
                        fontSize = 10.sp,
                        color = Color.White.copy(alpha = 0.78f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            } else {
                IconButton(
                    onClick = onExpand,
                    modifier = Modifier
                        .size(topIconButtonSize)
                        .testTag("video_mini_expand")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.OpenInFull,
                        contentDescription = "Expand video player",
                        tint = Color.White,
                        modifier = Modifier.size(topIconSize)
                    )
                }
                Spacer(modifier = Modifier.weight(1f))
            }

            if (showTitleText) {
                IconButton(
                    onClick = onExpand,
                    modifier = Modifier
                        .size(topIconButtonSize)
                        .testTag("video_mini_expand")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.OpenInFull,
                        contentDescription = "Expand video player",
                        tint = Color.White,
                        modifier = Modifier.size(topIconSize)
                    )
                }
            }

            IconButton(
                onClick = onClose,
                modifier = Modifier
                    .size(topIconButtonSize)
                    .testTag("video_mini_close")
            ) {
                Icon(
                    imageVector = Icons.Rounded.Close,
                    contentDescription = "Close video player",
                    tint = Color.White,
                    modifier = Modifier.size(topIconSize)
                )
            }
        }

        // Center Row: Play/Pause (or Buffering) + Next
        Row(
            modifier = Modifier.align(Alignment.Center),
            horizontalArrangement = Arrangement.spacedBy(if (isUltraCompact) 4.dp else 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (isBusy) {
                Box(
                    modifier = Modifier.size(centerButtonSize),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        color = colors.accentStrong,
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(centerIconSize)
                    )
                }
            } else {
                IconButton(
                    onClick = onTogglePlayPause,
                    modifier = Modifier
                        .size(centerButtonSize)
                        .testTag("video_mini_play_pause")
                ) {
                    Box(
                        modifier = Modifier
                            .size(centerButtonSize - 4.dp)
                            .clip(CircleShape)
                            .background(colors.accentStrong.copy(alpha = 0.92f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                            contentDescription = if (isPlaying) "Pause video" else "Play video",
                            tint = colors.onAccentStrong,
                            modifier = Modifier.size(centerIconSize)
                        )
                    }
                }
            }

            if (showNextButton) {
                IconButton(
                    onClick = onPlayNext,
                    modifier = Modifier
                        .size(if (isUltraCompact) 30.dp else 36.dp)
                        .testTag("video_mini_next")
                ) {
                    Box(
                        modifier = Modifier
                            .size(if (isUltraCompact) 26.dp else 32.dp)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.55f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.SkipNext,
                            contentDescription = "Next video",
                            tint = Color.White,
                            modifier = Modifier.size(if (isUltraCompact) 16.dp else 18.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MiniPlayerProgressBar(
    fallbackPositionMs: Long,
    fallbackDurationMs: Long,
    playbackProgressFlow: StateFlow<VideoPlaybackProgress>?,
    colors: EditorialColors
) {
    val progressState = playbackProgressFlow?.collectAsStateWithLifecycle()?.value
    val positionMs = progressState?.currentPositionMs ?: fallbackPositionMs
    val durationMs = (progressState?.durationMs?.takeIf { it > 0L }) ?: fallbackDurationMs
    val progress = if (durationMs > 0L) {
        (positionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
    } else 0f

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(2.5.dp)
            .background(Color.White.copy(alpha = 0.28f))
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(progress)
                .height(2.5.dp)
                .background(colors.accentStrong)
        )
    }
}
