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
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
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
import domain.video.VideoAspectRatioCalculator
import domain.video.VideoPlaybackProgress
import domain.video.VideoPlayerUiState
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import ui.theme.EditorialColors
import kotlin.math.roundToInt

private const val DEFAULT_FLOATING_PIP_WIDTH_FRACTION = 0.40f
private const val MAX_FLOATING_PIP_HEIGHT_FRACTION = 0.72f

/**
 * In-app floating PiP video player for SONORA Online Video mode.
 * Renders the active video at its natural aspect ratio (16:9, 9:16, 4:3, 1:1, etc.)
 * using the existing single Media3 ExoPlayer TextureView, docked cleanly inside safe screen bounds.
 *
 * - One-finger tap on the video surface shows or hides compact playback controls.
 * - Native Android system PiP continues to be managed by Android OS via MainActivity.
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
    onVideoViewportBoundsChanged: ((Rect?) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val video = playerState.activeVideo ?: return
    val latestOnVideoViewportBoundsChanged by rememberUpdatedState(onVideoViewportBoundsChanged)
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

    // Auto-hide controls shortly after inactivity while playing smoothly
    LaunchedEffect(
        controlsVisible,
        playerState.isPlaying,
        playerState.isResolving,
        playerState.isBuffering,
        controlsInteractionNonce
    ) {
        if (controlsVisible && playerState.isPlaying && !playerState.isResolving && !playerState.isBuffering) {
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

        // Fixed configured floating PiP size (~40% of viewport width) preserving exact video aspect ratio
        val safeInnerW = (viewportWidthPx - (edgeMarginPx * 2f)).coerceAtLeast(1f)
        val safeInnerH = (viewportHeightPx - topMarginPx - bottomMarginPx).coerceAtLeast(1f)
        val maxHeightAllowed = (viewportHeightPx * MAX_FLOATING_PIP_HEIGHT_FRACTION).coerceAtMost(safeInnerH)
        val maxWidthForHeight = (maxHeightAllowed * videoAspectRatio).coerceAtMost(safeInnerW)
        val targetWidthPx = (viewportWidthPx * DEFAULT_FLOATING_PIP_WIDTH_FRACTION)
            .coerceAtMost(safeInnerW)
            .coerceAtMost(maxWidthForHeight)
            .coerceAtLeast(1f)
        val pipHeightPx = (targetWidthPx / videoAspectRatio).coerceAtMost(safeInnerH).coerceAtLeast(1f)
        val pipWidthPx = (pipHeightPx * videoAspectRatio).coerceAtMost(safeInnerW).coerceAtLeast(1f)

        val pipOffsetX = (viewportWidthPx - edgeMarginPx - pipWidthPx).coerceAtLeast(edgeMarginPx)
        val pipOffsetY = if (hasBoundedHeight) {
            (viewportHeightPx - bottomMarginPx - pipHeightPx).coerceAtLeast(topMarginPx)
        } else {
            edgeMarginPx
        }

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
                            x = pipOffsetX.roundToInt(),
                            y = pipOffsetY.roundToInt()
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
                            "Floating video player: ${video.title}. Tap to toggle controls."
                    }
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onTap = {
                                controlsVisible = !controlsVisible
                                controlsInteractionNonce++
                            }
                        )
                    }
            ) {
                val cornerShape = RoundedCornerShape(16.dp)
                val elevation = if (isLowRamDevice) 0.dp else 8.dp

                Surface(
                    shape = cornerShape,
                    color = Color.Black,
                    border = BorderStroke(
                        width = 1.dp,
                        color = colors.accentStrong.copy(alpha = 0.50f)
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

                        // 3. Compact playback controls (toggled via one-finger tap)
                        AnimatedVisibility(
                            visible = controlsVisible,
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
 * Isolated TextureView holder so control state recompositions never recreate the view
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
