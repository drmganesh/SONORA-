package ui.video

import android.app.ActivityManager
import android.content.Context
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
import androidx.compose.foundation.gestures.calculateCentroid
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
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
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
import domain.video.FloatingPipBounds
import domain.video.FloatingPipResizeCalculator
import domain.video.FloatingPipViewport
import domain.video.VideoAspectRatioCalculator
import domain.video.VideoPlaybackProgress
import domain.video.VideoPlayerUiState
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import ui.theme.EditorialColors
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.roundToInt

/**
 * In-app floating PiP video player for SONORA Online Video mode with smooth two-finger
 * pinch-to-resize, natural aspect-ratio preservation (16:9, 9:16, 4:3, 1:1, etc.),
 * adaptive screen-boundary clamping, and uninterrupted single-ExoPlayer TextureView rendering.
 *
 * Gesture separation:
 * - Two-finger pinch on the floating PiP video surface: resize continuously around focal point.
 * - One-finger tap on the floating PiP video surface: show or hide compact playback controls.
 * - Single-finger drag is intentionally NOT enabled (floating position adapts only via pinch
 *   focal-point anchoring and screen-boundary clamping).
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
    initialWidthFraction: Float = FloatingPipResizeCalculator.INITIAL_WIDTH_FRACTION,
    onWidthFractionChanged: ((Float) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val video = playerState.activeVideo ?: return
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
        FloatingPipResizeCalculator.resolveVideoAspectRatio(
            videoWidth = playerState.videoWidth,
            videoHeight = playerState.videoHeight,
            pixelWidthHeightRatio = playerState.pixelWidthHeightRatio,
            unappliedRotationDegrees = playerState.unappliedRotationDegrees
        )
    }
    val aspectRatioLabel = remember(videoAspectRatio) {
        VideoAspectRatioCalculator.detectRatioLabel(videoAspectRatio)
    }

    // Persisted normalized preferences survive orientation changes & configuration updates
    var preferredWidthFraction by rememberSaveable {
        mutableFloatStateOf(
            initialWidthFraction.coerceIn(
                FloatingPipResizeCalculator.MIN_WIDTH_FRACTION,
                FloatingPipResizeCalculator.MAX_WIDTH_FRACTION
            )
        )
    }
    var anchorNormX by rememberSaveable { mutableFloatStateOf(1f) } // 1f = docked to end edge
    var anchorNormY by rememberSaveable { mutableFloatStateOf(1f) } // 1f = docked to bottom safe edge

    var controlsVisible by rememberSaveable { mutableStateOf(true) }
    var controlsInteractionNonce by remember { mutableIntStateOf(0) }
    var isPinching by remember { mutableStateOf(false) }
    var livePreviewBounds by remember { mutableStateOf<FloatingPipBounds?>(null) }

    // Auto-hide controls shortly after inactivity while playing smoothly
    LaunchedEffect(
        controlsVisible,
        playerState.isPlaying,
        playerState.isResolving,
        playerState.isBuffering,
        isPinching,
        controlsInteractionNonce
    ) {
        if (controlsVisible && playerState.isPlaying && !playerState.isResolving && !playerState.isBuffering && !isPinching) {
            delay(3_500L)
            controlsVisible = false
        }
    }

    BoxWithConstraints(modifier = modifier) {
        val hasBoundedHeight = constraints.hasBoundedHeight
        val viewportWidthPx = constraints.maxWidth.toFloat().coerceAtLeast(1f)
        val edgeMarginPx = with(density) { 12.dp.toPx() }
        val topMarginPx = with(density) { 10.dp.toPx() }
        val bottomMarginPx = with(density) { bottomInsetClearance.toPx() }
        val minVisibleWidthPx = with(density) { 64.dp.toPx() }

        val viewport = remember(
            viewportWidthPx,
            constraints.maxHeight,
            hasBoundedHeight,
            edgeMarginPx,
            topMarginPx,
            bottomMarginPx,
            minVisibleWidthPx
        ) {
            if (hasBoundedHeight) {
                FloatingPipViewport(
                    widthPx = viewportWidthPx,
                    heightPx = constraints.maxHeight.toFloat().coerceAtLeast(1f),
                    edgeMarginPx = edgeMarginPx,
                    topSafeMarginPx = topMarginPx,
                    bottomSafeMarginPx = bottomMarginPx,
                    minVisibleWidthPx = minVisibleWidthPx
                )
            } else {
                FloatingPipViewport(
                    widthPx = viewportWidthPx,
                    heightPx = (viewportWidthPx * 1.8f).coerceAtLeast(320f),
                    edgeMarginPx = edgeMarginPx,
                    topSafeMarginPx = edgeMarginPx,
                    bottomSafeMarginPx = edgeMarginPx,
                    minVisibleWidthPx = minVisibleWidthPx
                )
            }
        }

        // Pure synchronous layout bounds derived from stable state (zero frame lag on rotation)
        val committedBounds = remember(
            viewport,
            videoAspectRatio,
            preferredWidthFraction,
            anchorNormX,
            anchorNormY
        ) {
            val (w, h, effFrac) = FloatingPipResizeCalculator.computeConstrainedSize(
                viewport = viewport,
                aspectRatio = videoAspectRatio,
                requestedWidthFraction = preferredWidthFraction
            )
            val minX = viewport.edgeMarginPx
            val maxX = (viewport.widthPx - viewport.edgeMarginPx - w).coerceAtLeast(minX)
            val minY = viewport.topSafeMarginPx
            val maxY = (viewport.heightPx - viewport.bottomSafeMarginPx - h).coerceAtLeast(minY)
            val x = minX + anchorNormX.coerceIn(0f, 1f) * (maxX - minX)
            val y = minY + anchorNormY.coerceIn(0f, 1f) * (maxY - minY)
            val (clampedX, clampedY) = FloatingPipResizeCalculator.clampPosition(
                offsetX = x,
                offsetY = y,
                widthPx = w,
                heightPx = h,
                viewport = viewport
            )
            FloatingPipBounds(
                widthPx = w,
                heightPx = h,
                offsetX = clampedX,
                offsetY = clampedY,
                widthFraction = effFrac,
                aspectRatio = videoAspectRatio
            )
        }

        val latestCommittedBounds by rememberUpdatedState(committedBounds)
        val latestViewport by rememberUpdatedState(viewport)
        val latestAspectRatio by rememberUpdatedState(videoAspectRatio)
        val latestOnWidthFractionChanged by rememberUpdatedState(onWidthFractionChanged)

        val committedWidthDp = with(density) { committedBounds.widthPx.toDp() }
        val committedHeightDp = with(density) { committedBounds.heightPx.toDp() }
        val displayBounds = livePreviewBounds ?: committedBounds

        // GPU-accelerated transform delta during active pinch (zero relayout / zero TextureView recreation)
        val liveScale = if (isPinching && livePreviewBounds != null && committedBounds.widthPx > 0f) {
            (displayBounds.widthPx / committedBounds.widthPx).coerceIn(0.2f, 5f)
        } else {
            1f
        }
        val liveTranslationX = if (isPinching && livePreviewBounds != null) {
            displayBounds.offsetX - committedBounds.offsetX
        } else {
            0f
        }
        val liveTranslationY = if (isPinching && livePreviewBounds != null) {
            displayBounds.offsetY - committedBounds.offsetY
        } else {
            0f
        }

        val containerModifier = if (hasBoundedHeight) {
            Modifier.fillMaxSize()
        } else {
            Modifier
                .fillMaxWidth()
                .height(committedHeightDp + 24.dp)
        }

        val pipOffsetX = if (hasBoundedHeight) committedBounds.offsetX else committedBounds.offsetX
        val pipOffsetY = if (hasBoundedHeight) committedBounds.offsetY else with(density) { 12.dp.toPx() }

        Box(modifier = containerModifier) {
            // Outer positioned PiP anchor box: pointerInput lives BEFORE graphicsLayer so local
            // touch coordinates remain 100% stable and oscillation-free during GPU pinch scaling.
            Box(
                modifier = Modifier
                    .offset {
                        IntOffset(
                            x = pipOffsetX.roundToInt(),
                            y = pipOffsetY.roundToInt()
                        )
                    }
                    .size(width = committedWidthDp, height = committedHeightDp)
                    .testTag("floating_pip_video_surface")
                    .semantics {
                        contentDescription =
                            "Floating video player: ${video.title}. Pinch with two fingers to resize, tap to toggle controls."
                    }
                    .pointerInput(Unit) {
                        val touchSlop = viewConfiguration.touchSlop
                        awaitEachGesture {
                            val firstDown = awaitFirstDown(requireUnconsumed = false)
                            val downPos = firstDown.position
                            var hadMultiTouch = false
                            var movedPastSlop = false
                            var wasConsumedByChild = firstDown.isConsumed
                            var pinchActive = false
                            var gestureBase: FloatingPipBounds? = null
                            var cumulativeZoom = 1f
                            var startCentroid = Offset.Zero

                            do {
                                val event = awaitPointerEvent(PointerEventPass.Main)
                                var pressedCount = 0
                                var singlePointerPos = downPos
                                val changes = event.changes
                                for (i in 0 until changes.size) {
                                    val c = changes[i]
                                    if (c.pressed) {
                                        pressedCount++
                                        singlePointerPos = c.position
                                    }
                                    if (c.isConsumed) {
                                        wasConsumedByChild = true
                                    }
                                }

                                if (pressedCount >= 2) {
                                    hadMultiTouch = true
                                    val zoomChange = event.calculateZoom()
                                    if (!pinchActive) {
                                        pinchActive = true
                                        gestureBase = latestCommittedBounds
                                        cumulativeZoom = 1f
                                        startCentroid = event.calculateCentroid(useCurrent = true)
                                        isPinching = true
                                        livePreviewBounds = gestureBase
                                    }
                                    if (zoomChange.isFinite() && zoomChange > 0f && abs(zoomChange - 1f) > 0.0005f) {
                                        cumulativeZoom = (cumulativeZoom * zoomChange).coerceIn(0.15f, 6.0f)
                                        val base = gestureBase ?: latestCommittedBounds
                                        livePreviewBounds = FloatingPipResizeCalculator.applyPinchZoom(
                                            baseBounds = base,
                                            zoomFactor = cumulativeZoom,
                                            focalLocalX = startCentroid.x,
                                            focalLocalY = startCentroid.y,
                                            viewport = latestViewport,
                                            aspectRatio = latestAspectRatio
                                        )
                                    }
                                    for (i in 0 until changes.size) {
                                        changes[i].consume()
                                    }
                                } else if (hadMultiTouch) {
                                    // Keep consuming trailing pointer lifts after a pinch so child buttons never fire
                                    for (i in 0 until changes.size) {
                                        changes[i].consume()
                                    }
                                } else if (pressedCount == 1 && !movedPastSlop) {
                                    val dx = singlePointerPos.x - downPos.x
                                    val dy = singlePointerPos.y - downPos.y
                                    if (hypot(dx.toDouble(), dy.toDouble()) > touchSlop) {
                                        movedPastSlop = true
                                    }
                                }
                            } while (event.changes.any { it.pressed })

                            if (pinchActive) {
                                val finalBounds = livePreviewBounds
                                if (finalBounds != null) {
                                    val vp = latestViewport
                                    val clampedFraction = finalBounds.widthFraction.coerceIn(
                                        FloatingPipResizeCalculator.MIN_WIDTH_FRACTION,
                                        FloatingPipResizeCalculator.MAX_WIDTH_FRACTION
                                    )
                                    val minX = vp.edgeMarginPx
                                    val maxX = (vp.widthPx - vp.edgeMarginPx - finalBounds.widthPx)
                                        .coerceAtLeast(minX)
                                    val minY = vp.topSafeMarginPx
                                    val maxY = (vp.heightPx - vp.bottomSafeMarginPx - finalBounds.heightPx)
                                        .coerceAtLeast(minY)
                                    val rangeX = maxX - minX
                                    val rangeY = maxY - minY
                                    anchorNormX = if (rangeX > 1f) {
                                        ((finalBounds.offsetX - minX) / rangeX).coerceIn(0f, 1f)
                                    } else {
                                        1f
                                    }
                                    anchorNormY = if (rangeY > 1f) {
                                        ((finalBounds.offsetY - minY) / rangeY).coerceIn(0f, 1f)
                                    } else {
                                        1f
                                    }
                                    preferredWidthFraction = clampedFraction
                                    latestOnWidthFractionChanged?.invoke(clampedFraction)
                                }
                                livePreviewBounds = null
                                isPinching = false
                            } else if (!hadMultiTouch && !movedPastSlop && !wasConsumedByChild) {
                                controlsVisible = !controlsVisible
                                controlsInteractionNonce++
                            }
                        }
                    }
                    .graphicsLayer {
                        transformOrigin = TransformOrigin(0f, 0f)
                        scaleX = liveScale
                        scaleY = liveScale
                        translationX = liveTranslationX
                        translationY = liveTranslationY
                    }
            ) {
                val cornerShape = RoundedCornerShape(16.dp)
                val borderAlpha = if (isPinching) 0.90f else 0.50f
                val elevation = if (isPinching || isLowRamDevice) 0.dp else 8.dp

                Surface(
                    shape = cornerShape,
                    color = Color.Black,
                    border = BorderStroke(
                        width = if (isPinching) 1.5.dp else 1.dp,
                        color = colors.accentStrong.copy(alpha = borderAlpha)
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

                        // 2. Live Media3 ExoPlayer TextureView — created ONCE and kept attached across resizes
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
                                containerWidthDp = committedWidthDp,
                                colors = colors,
                                onExpand = {
                                    if (!isPinching) onExpand()
                                },
                                onTogglePlayPause = {
                                    if (!isPinching) {
                                        controlsInteractionNonce++
                                        onTogglePlayPause()
                                    }
                                },
                                onPlayNext = {
                                    if (!isPinching) {
                                        controlsInteractionNonce++
                                        onPlayNext()
                                    }
                                },
                                onClose = {
                                    if (!isPinching) onClose()
                                }
                            )
                        }

                        // 4. Lightweight size & aspect-ratio indicator pill shown only while actively pinching
                        if (isPinching) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.Center)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(Color.Black.copy(alpha = 0.68f))
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                                    .testTag("floating_pip_resize_badge")
                            ) {
                                Text(
                                    text = "$aspectRatioLabel • ${displayBounds.widthPercent}%",
                                    fontFamily = PlusJakartaFontFamily,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 11.sp,
                                    color = Color.White,
                                    maxLines = 1
                                )
                            }
                        }

                        // 5. Isolated live progress strip (collects 500ms ticks without recomposing TextureView)
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
 * Isolated TextureView holder so resize/control state recompositions never recreate the view
 * or detach/reattach the active ExoPlayer video surface.
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
